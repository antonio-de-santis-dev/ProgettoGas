package it.progettogas.gas;

import static it.progettogas.gas.Model.*;
import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GasEngineTest {
  final GasEngine engine = new GasEngine();
  List<Examples.Example> examples;

  @BeforeEach
  void load() throws Exception {
    try (var in = getClass().getResourceAsStream("/examples.json")) {
      examples =
          JsonMapper.builder()
              .findAndAddModules()
              .build()
              .readValue(in, new TypeReference<List<Examples.Example>>() {});
    }
  }

  Map<String, Profile> profiles(Examples.Example e) {
    Map<String, Profile> map = new LinkedHashMap<>();
    for (int i = 0; i < e.bill().periods().size(); i++)
      map.put(e.bill().periods().get(i).month(), e.profiles().get(i));
    return map;
  }

  Bill withOther(Bill b, String taxable, String exempt) {
    return new Bill(
        b.client(),
        b.pdr(),
        b.partitaIva(),
        b.supplier(),
        b.reference(),
        b.previousTotal(),
        new BigDecimal(taxable),
        new BigDecimal(exempt),
        b.periods());
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2, 3})
  void replicatesFourExcelRawTotals(int index) {
    var e = examples.get(index);
    var r = engine.calculate(e.bill(), e.offer(), profiles(e));
    assertThat(r.totalRaw().subtract(new BigDecimal(e.expectedRaw())).abs())
        .isLessThanOrEqualTo(new BigDecimal("0.000000001"));
    assertThat(r.months()).isEqualTo(2);
    assertThat(r.rows()).hasSize(32);
    assertThat(r.unapproved()).isTrue();
    assertThat(r.annualRaw()).isEqualByComparingTo(r.differenceRaw().multiply(new BigDecimal("6")));
  }

  @Test
  void firstCategoriesAndNegativeUg2() {
    var e = examples.get(0);
    var r = engine.calculate(e.bill(), e.offer(), profiles(e));
    assertThat(r.categories().get(Category.COMMODITY)).isEqualByComparingTo("167.164951");
    assertThat(r.categories().get(Category.TRANSPORT).add(r.categories().get(Category.SYSTEM)))
        .isEqualByComparingTo("113.2461692");
    assertThat(r.categories().get(Category.TAX)).isEqualByComparingTo("41.39636");
    assertThat(r.rows().stream().filter(l -> l.code().equals("UG2_FIXED")).map(Row::amount))
        .allMatch(v -> v.signum() < 0);
    assertThat(r.audit()).isNotEmpty();
  }

  @Test
  void showsHigherCost() {
    var e = examples.get(3);
    var r = engine.calculate(e.bill(), e.offer(), profiles(e));
    assertThat(r.differenceRaw()).isEqualByComparingTo("-106.333269640");
    assertThat(r.total()).isEqualByComparingTo("659.77");
  }

  @Test
  void otherAmountsIncludedAfterVat() {
    var e = examples.get(0);
    var baseline = engine.calculate(e.bill(), e.offer(), profiles(e));
    var r = engine.calculate(withOther(e.bill(), "10", "7.5"), e.offer(), profiles(e));
    assertThat(r.totalRaw().subtract(baseline.totalRaw())).isEqualByComparingTo("19.70");
  }

  @Test
  void perLineVatAndExemption() {
    var e = examples.get(0);
    Map<String, Profile> profiles = profiles(e);
    profiles.replaceAll(
        (month, p) ->
            new Profile(
                p.name(),
                p.provider(),
                p.area(),
                p.validFrom(),
                p.validTo(),
                p.status(),
                Mode.CONFIGURED_RULES,
                p.vatRate(),
                p.source(),
                p.approvedBy(),
                p.lines().stream()
                    .map(
                        l ->
                            new Line(
                                l.code(),
                                l.label(),
                                l.category(),
                                l.basis(),
                                l.rate(),
                                l.code().equals("QVD_FIXED") ? Vat.NON_TAXABLE : Vat.LINE_RATE,
                                new BigDecimal("10"),
                                l.band()))
                    .toList(),
                p.bands()));
    var r = engine.calculate(e.bill(), e.offer(), profiles);
    assertThat(r.nonTaxable()).isEqualByComparingTo("10.56");
    assertThat(r.vat()).isEqualByComparingTo("31.124748020");
    assertThat(r.totalRaw()).isEqualByComparingTo("352.932228220");
  }

  @Test
  void missingPsvRejected() {
    var e = examples.get(0);
    var o = e.offer();
    var indexed =
        new Offer(
            o.name(),
            o.provider(),
            OfferType.PSV,
            o.price(),
            new BigDecimal("0.02"),
            o.qvdFixed(),
            o.ccr(),
            o.qvdVariable(),
            true,
            o.notes());
    assertThatThrownBy(() -> engine.calculate(e.bill(), indexed, profiles(e)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("PSV");
  }

  @Test
  void missingBandRejected() {
    var e = examples.get(0);
    var profiles = profiles(e);
    profiles.replaceAll(
        (month, p) ->
            new Profile(
                p.name(),
                p.provider(),
                p.area(),
                p.validFrom(),
                p.validTo(),
                p.status(),
                p.mode(),
                p.vatRate(),
                p.source(),
                p.approvedBy(),
                p.lines().stream()
                    .map(
                        l ->
                            new Line(
                                l.code(),
                                l.label(),
                                l.category(),
                                l.basis(),
                                l.rate(),
                                l.vatTreatment(),
                                l.vatRate(),
                                ""))
                    .toList(),
                p.bands()));
    assertThatThrownBy(() -> engine.calculate(e.bill(), e.offer(), profiles))
        .hasMessageContaining("Scaglione");
  }

  @Test
  void bandSelectionAndAudit() {
    var e = examples.get(0);
    var profiles = profiles(e);
    var month = e.bill().periods().get(0).month();
    var p = profiles.get(month);
    profiles.put(
        month,
        new Profile(
            p.name(),
            p.provider(),
            p.area(),
            p.validFrom(),
            p.validTo(),
            p.status(),
            p.mode(),
            p.vatRate(),
            p.source(),
            p.approvedBy(),
            p.lines(),
            List.of(
                new Band("DIST_VAR", "Manuale A", BigDecimal.ZERO, null, new BigDecimal("0.2")))));
    var original = e.bill().periods().get(0);
    var updated =
        new Period(
            month,
            original.consumptionSmc(),
            original.fixedMonths(),
            null,
            null,
            Map.of("DIST_VAR", "Manuale A"),
            Map.of(
                "DIST_VAR",
                new Model.Override(new BigDecimal("0.3"), "Verifica contratto", "Operatore Test")));
    var b = e.bill();
    var bill =
        new Bill(
            b.client(),
            b.pdr(),
            b.partitaIva(),
            b.supplier(),
            b.reference(),
            b.previousTotal(),
            b.otherTaxable(),
            b.otherNonTaxable(),
            List.of(updated, b.periods().get(1)));
    var r = engine.calculate(bill, e.offer(), profiles);
    assertThat(r.audit())
        .anyMatch(
            a ->
                a.code().equals("DIST_VAR")
                    && a.original().compareTo(new BigDecimal("0.2")) == 0
                    && a.updated().compareTo(new BigDecimal("0.3")) == 0);
  }
}
