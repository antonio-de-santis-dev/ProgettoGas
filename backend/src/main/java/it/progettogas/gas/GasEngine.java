package it.progettogas.gas;

import static it.progettogas.gas.Model.*;

import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** All arithmetic is decimal; raw totals remain unrounded. */
@Component
public class GasEngine {
  private static final Set<String> BANDED =
      Set.of("DIST_VAR", "UG2_VAR", "EXCISE", "LOCAL_ADDITION");

  public Result calculate(Bill bill, Offer offer, Map<String, Profile> profiles) {
    validateBill(bill);
    if (!offer.active()) throw new IllegalArgumentException("Offerta disattivata");
    List<Row> rows = new ArrayList<>();
    List<Audit> audit = new ArrayList<>();
    Map<Category, BigDecimal> categories = new LinkedHashMap<>();
    for (var c : Category.values()) categories.put(c, BigDecimal.ZERO);
    BigDecimal taxable = BigDecimal.ZERO,
        vat = BigDecimal.ZERO,
        nonTax = bill.otherNonTaxable(),
        consumption = BigDecimal.ZERO;
    boolean unapproved = false;
    for (var p : bill.periods()) {
      Profile profile = profiles.get(p.month());
      if (profile == null || profile.status() == Status.ARCHIVED)
        throw new IllegalArgumentException("Profilo mancante o archiviato per " + p.month());
      validateProfile(profile);
      LocalDate at = YearMonth.parse(p.month()).atDay(1);
      if (at.isBefore(profile.validFrom())
          || (profile.validTo() != null
              && YearMonth.parse(p.month()).atEndOfMonth().isAfter(profile.validTo())))
        throw new IllegalArgumentException("Profilo fuori validità per " + p.month());
      unapproved |= profile.status() != Status.PUBLISHED;
      consumption = consumption.add(p.consumptionSmc());
      Set<String> codes = new HashSet<>();
      for (var line : profile.lines()) {
        codes.add(line.code());
        BigDecimal quantity =
            switch (line.basis()) {
              case SMC -> p.consumptionSmc();
              case FIXED_MONTH -> p.fixedMonths();
              case AMOUNT -> BigDecimal.ONE;
            };
        BigDecimal rate = line.rate();
        String origin = "Profilo: " + profile.name() + " · " + profile.source();
        String band = line.band();
        switch (line.code()) {
          case "COMMODITY" -> {
            if (offer.type() == OfferType.PSV && p.psv() == null)
              throw new IllegalArgumentException("PSV €/Smc mancante per " + p.month());
            rate = offer.type() == OfferType.PSV ? p.psv().add(offer.spread()) : offer.price();
            origin = "Offerta: " + offer.name();
          }
          case "QVD_FIXED" -> {
            rate = offer.qvdFixed();
            origin = "Offerta: " + offer.name();
          }
          case "CCR" -> {
            rate = offer.ccr();
            origin = "Offerta: " + offer.name();
          }
          case "QVD_VAR" -> {
            rate = offer.qvdVariable();
            origin = "Offerta: " + offer.name();
          }
          default -> {}
        }
        String chosen = p.chosenBands().get(line.code());
        if (chosen != null && !chosen.isBlank()) {
          var selected =
              profile.bands().stream()
                  .filter(b -> b.code().equals(line.code()) && b.label().equals(chosen))
                  .findFirst();
          if (selected.isEmpty())
            throw new IllegalArgumentException(
                "Scaglione non presente nel profilo: " + line.code());
          rate = selected.get().rate();
          band = chosen;
          origin += " · selezione manuale " + chosen;
        }
        if (BANDED.contains(line.code()) && (band == null || band.isBlank()))
          throw new IllegalArgumentException(
              "Scaglione/scelta manuale esplicita mancante: " + line.label());
        var override = p.overrides().get(line.code());
        if (override != null) {
          if (override.reason() == null
              || override.reason().isBlank()
              || override.operator() == null
              || override.operator().isBlank())
            throw new IllegalArgumentException("Override: motivo e operatore obbligatori");
          audit.add(
              new Audit(
                  p.month(),
                  line.code(),
                  rate,
                  override.rate(),
                  override.reason(),
                  override.operator(),
                  Instant.now().toString()));
          rate = override.rate();
          origin = "Override: " + override.reason() + " · " + override.operator();
        }
        var amount = quantity.multiply(rate);
        boolean exempt = line.vatTreatment() == Vat.NON_TAXABLE;
        var vatRate =
            profile.mode() == Mode.SOURCE_COMPATIBILITY
                ? new BigDecimal("22")
                : (line.vatTreatment() == Vat.LINE_RATE ? line.vatRate() : profile.vatRate());
        var lineVat = exempt ? BigDecimal.ZERO : amount.multiply(vatRate).movePointLeft(2);
        if (exempt) nonTax = nonTax.add(amount);
        else taxable = taxable.add(amount);
        vat = vat.add(lineVat);
        categories.merge(line.category(), amount, BigDecimal::add);
        rows.add(
            new Row(
                p.month(),
                line.code(),
                line.label(),
                line.category(),
                switch (line.basis()) {
                  case SMC -> "€/Smc";
                  case FIXED_MONTH -> "€/PDR/mese";
                  case AMOUNT -> "€";
                },
                quantity,
                rate,
                amount,
                lineVat,
                band,
                origin));
      }
      if (!codes.containsAll(p.overrides().keySet())
          || !codes.containsAll(p.chosenBands().keySet()))
        throw new IllegalArgumentException("Voce override/scaglione non presente nel profilo");
    }
    // Other invoice-wide taxable amounts use the first profile's global VAT.
    Profile first = profiles.get(bill.periods().get(0).month());
    var otherVat =
        bill.otherTaxable()
            .multiply(
                first.mode() == Mode.SOURCE_COMPATIBILITY ? new BigDecimal("22") : first.vatRate())
            .movePointLeft(2);
    taxable = taxable.add(bill.otherTaxable());
    vat = vat.add(otherVat);
    categories.merge(
        Category.OTHER, bill.otherTaxable().add(bill.otherNonTaxable()), BigDecimal::add);
    rows.add(
        new Row(
            "Periodo",
            "OTHER_TAXABLE",
            "Altre partite imponibili",
            Category.OTHER,
            "€",
            BigDecimal.ONE,
            bill.otherTaxable(),
            bill.otherTaxable(),
            otherVat,
            null,
            "Bolletta cliente"));
    rows.add(
        new Row(
            "Periodo",
            "OTHER_NON_TAXABLE",
            "Altre partite non soggette IVA",
            Category.OTHER,
            "€",
            BigDecimal.ONE,
            bill.otherNonTaxable(),
            bill.otherNonTaxable(),
            BigDecimal.ZERO,
            null,
            "Bolletta cliente"));
    BigDecimal total = taxable.add(vat).add(nonTax);
    if (total.signum() < 0) throw new IllegalArgumentException("Il totale non può essere negativo");
    BigDecimal diff = bill.previousTotal().subtract(total);
    BigDecimal annual =
        diff.multiply(new BigDecimal("12"))
            .divide(BigDecimal.valueOf(bill.periods().size()), 18, RoundingMode.HALF_UP);
    BigDecimal pct =
        bill.previousTotal().signum() == 0
            ? null
            : diff.multiply(new BigDecimal("100"))
                .divide(bill.previousTotal(), 2, RoundingMode.HALF_UP);
    return new Result(
        List.copyOf(rows),
        Collections.unmodifiableMap(categories),
        taxable,
        vat,
        nonTax,
        total,
        money(total),
        diff,
        money(diff),
        pct,
        annual,
        money(annual),
        consumption,
        bill.periods().size(),
        unapproved,
        List.copyOf(audit));
  }

  public static BigDecimal money(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_UP);
  }

  public static void validateBill(Bill bill) {
    YearMonth last = null;
    for (var p : bill.periods()) {
      YearMonth current;
      try {
        current = YearMonth.parse(p.month());
      } catch (RuntimeException e) {
        throw new IllegalArgumentException("Mese non valido");
      }
      if (last != null && !current.equals(last.plusMonths(1)))
        throw new IllegalArgumentException(
            "I mesi devono essere consecutivi, ordinati e senza duplicati");
      last = current;
    }
  }

  public static void validateProfile(Profile p) {
    if (p.validTo() != null && p.validTo().isBefore(p.validFrom()))
      throw new IllegalArgumentException("Fine validità anteriore all'inizio");
    if (p.mode() == Mode.SOURCE_COMPATIBILITY && p.vatRate().compareTo(new BigDecimal("22")) != 0)
      throw new IllegalArgumentException("Compatibilità Excel: IVA globale 22%");
    if (p.status() == Status.PUBLISHED && (p.approvedBy() == null || p.approvedBy().isBlank()))
      throw new IllegalArgumentException("Indica il responsabile che approva il profilo");
    Set<String> codes = new HashSet<>();
    for (var l : p.lines())
      if (!codes.add(l.code()))
        throw new IllegalArgumentException("Codice voce duplicato: " + l.code());
    if (!codes.containsAll(Set.of("COMMODITY", "QVD_FIXED", "CCR", "QVD_VAR")))
      throw new IllegalArgumentException("Mancano le quattro voci materia gas");
    Set<String> bands = new HashSet<>();
    for (var b : p.bands()) {
      if (!codes.contains(b.code())
          || !bands.add(b.code() + ":" + b.label())
          || (b.max() != null && b.max().compareTo(b.min()) < 0))
        throw new IllegalArgumentException("Scaglione non valido o duplicato");
    }
  }
}
