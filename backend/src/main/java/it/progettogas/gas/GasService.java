package it.progettogas.gas;

import static it.progettogas.gas.Model.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.progettogas.shared.*;
import jakarta.validation.Validator;
import java.util.*;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GasService {
  private final Records records;
  private final JsonCodec codec;
  private final ObjectMapper mapper;
  private final Validator validator;
  private final GasEngine engine;

  public GasService(
      Records records,
      JsonCodec codec,
      ObjectMapper mapper,
      Validator validator,
      GasEngine engine) {
    this.records = records;
    this.codec = codec;
    this.mapper = mapper;
    this.validator = validator;
    this.engine = engine;
  }

  public List<Saved> list(String kind) {
    checkKind(kind);
    return records.findByKindOrderByIdDesc(kind).stream().map(this::view).toList();
  }

  public Saved get(String kind, long id) {
    return view(entity(kind, id));
  }

  public <T> T read(String kind, long id, Class<T> cls) {
    return codec.leggi(entity(kind, id).payload, cls);
  }

  RecordEntity entity(String kind, long id) {
    return records
        .findById(id)
        .filter(r -> r.kind.equals(kind))
        .orElseThrow(() -> new NotFoundException("Dato non trovato"));
  }

  public Saved save(String kind, Long id, Object raw, Long version) {
    checkKind(kind);
    Object data = typed(kind, raw);
    var violations = validator.validate(data);
    if (!violations.isEmpty())
      throw new IllegalArgumentException(
          violations.stream()
              .map(v -> v.getPropertyPath() + ": " + v.getMessage())
              .sorted()
              .reduce((a, b) -> a + "; " + b)
              .orElse("Dati non validi"));
    if (data instanceof Bill b) GasEngine.validateBill(b);
    RecordEntity existing = id == null ? null : entity(kind, id);
    if (existing != null && !Objects.equals(existing.version, version))
      throw new ObjectOptimisticLockingFailureException(RecordEntity.class, id);
    if (data instanceof Profile p) {
      GasEngine.validateProfile(p);
      if (existing != null && read(kind, id, Profile.class).status() == Status.PUBLISHED)
        throw new IllegalArgumentException(
            "Profilo pubblicato immutabile: duplica per creare una nuova versione");
      if (p.status() == Status.PUBLISHED)
        for (var r : records.findByKindOrderByIdDesc(kind)) {
          if (Objects.equals(r.id, id)) continue;
          Profile other = codec.leggi(r.payload, Profile.class);
          if (other.status() == Status.PUBLISHED
              && other.provider().equals(p.provider())
              && other.area().equals(p.area())
              && (p.validTo() == null || !other.validFrom().isAfter(p.validTo()))
              && (other.validTo() == null || !p.validFrom().isAfter(other.validTo())))
            throw new IllegalArgumentException(
                "Validità sovrapposta a un profilo pubblicato dello stesso fornitore e area");
        }
    }
    if (existing == null) existing = new RecordEntity(kind, codec.scrivi(data));
    else {
      records.save(
          new RecordEntity(
              "audit",
              codec.scrivi(
                  Map.of(
                      "recordId",
                      id,
                      "kind",
                      kind,
                      "previous",
                      codec.leggi(existing.payload, Object.class),
                      "updated",
                      data))));
      existing.payload = codec.scrivi(data);
    }
    return view(records.saveAndFlush(existing));
  }

  public void delete(String kind, long id, long version) {
    RecordEntity r = entity(kind, id);
    if (!Objects.equals(r.version, version))
      throw new ObjectOptimisticLockingFailureException(RecordEntity.class, id);
    if (kind.equals("parametri")
        && codec.leggi(r.payload, Profile.class).status() == Status.PUBLISHED)
      throw new IllegalArgumentException("Profilo pubblicato immutabile");
    records.save(
        new RecordEntity(
            "audit",
            codec.scrivi(
                Map.of(
                    "recordId",
                    id,
                    "kind",
                    kind,
                    "deleted",
                    codec.leggi(r.payload, Object.class)))));
    records.delete(r);
  }

  public Saved duplicate(String kind, long id) {
    Object data = read(kind, id, Object.class);
    if (kind.equals("parametri")) {
      var p = read(kind, id, Profile.class);
      data =
          new Profile(
              p.name() + " · nuova versione",
              p.provider(),
              p.area(),
              p.validFrom(),
              p.validTo(),
              Status.DRAFT,
              p.mode(),
              p.vatRate(),
              p.source(),
              "",
              p.lines(),
              p.bands());
    }
    return save(kind, null, data, null);
  }

  public Comparison calculate(Calculate req) {
    Bill bill = read("bollette", req.billId(), Bill.class);
    Offer offer = read("offerte", req.offerId(), Offer.class);
    Map<String, Profile> profiles = new LinkedHashMap<>();
    for (var p : bill.periods())
      profiles.put(
          p.month(),
          read(
              "parametri", p.profileId() == null ? req.profileId() : p.profileId(), Profile.class));
    return new Comparison(
        bill, offer, profiles, engine.calculate(bill, offer, profiles), "gas-1.0");
  }

  public Saved compare(Calculate req) {
    return view(records.saveAndFlush(new RecordEntity("confronti", codec.scrivi(calculate(req)))));
  }

  public List<Object> audit(String kind, long id) {
    entity(kind, id);
    return records.findByKindOrderByIdDesc("audit").stream()
        .map(r -> codec.leggi(r.payload, Map.class))
        .filter(
            m ->
                String.valueOf(m.get("recordId")).equals(String.valueOf(id))
                    && m.get("kind").equals(kind))
        .map(m -> (Object) m)
        .toList();
  }

  private Saved view(RecordEntity r) {
    return new Saved(r.id, r.version, r.createdAt.toString(), codec.leggi(r.payload, Object.class));
  }

  private void checkKind(String kind) {
    if (!Set.of("offerte", "bollette", "parametri", "fonti", "pdf-settings", "confronti")
        .contains(kind)) throw new IllegalArgumentException("Sezione non valida");
  }

  private Object typed(String kind, Object raw) {
    Class<?> type =
        switch (kind) {
          case "offerte" -> Offer.class;
          case "bollette" -> Bill.class;
          case "parametri" -> Profile.class;
          case "fonti" -> Source.class;
          default -> it.progettogas.confronti.PdfPersonalizzazione.class;
        };
    return mapper.convertValue(raw, type);
  }

  public record Source(
      @jakarta.validation.constraints.NotBlank
          @jakarta.validation.constraints.Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])")
          String month,
      @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin("0")
          java.math.BigDecimal psv,
      @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 500)
          String source,
      @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 500)
          String reason) {}
}
