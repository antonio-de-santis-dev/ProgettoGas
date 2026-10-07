package it.progettogas.gas;

import static it.progettogas.gas.Model.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class Examples {
  public record Example(
      String name, String expectedRaw, Bill bill, Offer offer, List<Profile> profiles) {}

  private final List<Example> examples;
  private final GasService service;
  private final GasEngine engine;

  public Examples(ObjectMapper mapper, GasService service, GasEngine engine) throws IOException {
    this.service = service;
    this.engine = engine;
    try (var in = getClass().getResourceAsStream("/examples.json")) {
      examples = mapper.readValue(in, new TypeReference<List<Example>>() {});
    }
  }

  public List<Map<String, String>> list() {
    return examples.stream()
        .map(
            e ->
                Map.of(
                    "name",
                    e.name(),
                    "expectedRaw",
                    e.expectedRaw(),
                    "note",
                    "Tariffe storiche da validare; cliente sintetico"))
        .toList();
  }

  @Transactional
  public Map<String, Object> load(String name) {
    var e =
        examples.stream()
            .filter(x -> x.name().equals(name))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Esempio non trovato"));
    List<Saved> profiles =
        e.profiles().stream().map(p -> service.save("parametri", null, p, null)).toList();
    List<Period> periods = new ArrayList<>();
    for (int i = 0; i < e.bill().periods().size(); i++) {
      var p = e.bill().periods().get(i);
      periods.add(
          new Period(
              p.month(),
              p.consumptionSmc(),
              p.fixedMonths(),
              profiles.get(i).id(),
              p.psv(),
              p.chosenBands(),
              p.overrides()));
    }
    var b = e.bill();
    var bill =
        service.save(
            "bollette",
            null,
            new Bill(
                b.client(),
                b.pdr(),
                b.partitaIva(),
                b.supplier(),
                b.reference(),
                b.previousTotal(),
                b.otherTaxable(),
                b.otherNonTaxable(),
                periods),
            null);
    var offer = service.save("offerte", null, e.offer(), null);
    var comparison = service.compare(new Calculate(bill.id(), offer.id(), profiles.get(0).id()));
    return Map.of("bill", bill, "offer", offer, "profiles", profiles, "comparison", comparison);
  }

  public Comparison preview() {
    var e = examples.get(0);
    Map<String, Profile> p = new LinkedHashMap<>();
    for (int i = 0; i < e.bill().periods().size(); i++)
      p.put(e.bill().periods().get(i).month(), e.profiles().get(i));
    return new Comparison(
        e.bill(), e.offer(), p, engine.calculate(e.bill(), e.offer(), p), "gas-1.0");
  }
}
