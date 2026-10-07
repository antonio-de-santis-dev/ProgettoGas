package it.progettogas.gas;

import static it.progettogas.gas.Model.*;

import it.progettogas.confronti.*;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class GasController {
  private final GasService service;
  private final GasPdfRenderer pdf;
  private final Examples examples;

  public GasController(GasService service, GasPdfRenderer pdf, Examples examples) {
    this.service = service;
    this.pdf = pdf;
    this.examples = examples;
  }

  @GetMapping("/{kind:bollette|offerte|parametri|fonti|confronti}")
  public List<Saved> list(@PathVariable String kind) {
    return service.list(kind);
  }

  @GetMapping("/{kind:bollette|offerte|parametri|fonti|confronti}/{id}")
  public Saved get(@PathVariable String kind, @PathVariable long id) {
    return service.get(kind, id);
  }

  @PostMapping("/{kind:bollette|offerte|parametri|fonti}")
  @ResponseStatus(HttpStatus.CREATED)
  public Saved create(@PathVariable String kind, @Valid @RequestBody Write req) {
    return service.save(kind, null, req.data(), null);
  }

  @PutMapping("/{kind:bollette|offerte|parametri|fonti}/{id}")
  public Saved update(
      @PathVariable String kind, @PathVariable long id, @Valid @RequestBody Write req) {
    return service.save(kind, id, req.data(), req.version());
  }

  @DeleteMapping("/{kind:bollette|offerte|parametri|fonti}/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String kind, @PathVariable long id, @RequestParam long version) {
    service.delete(kind, id, version);
  }

  @PostMapping("/{kind:bollette|offerte|parametri}/{id}/duplica")
  @ResponseStatus(HttpStatus.CREATED)
  public Saved duplicate(@PathVariable String kind, @PathVariable long id) {
    return service.duplicate(kind, id);
  }

  @GetMapping("/{kind:bollette|offerte|parametri|fonti}/{id}/revisioni")
  public List<Object> revisions(@PathVariable String kind, @PathVariable long id) {
    return service.audit(kind, id);
  }

  @PostMapping("/confronti/anteprima")
  public Comparison calculate(@Valid @RequestBody Calculate req) {
    return service.calculate(req);
  }

  @PostMapping("/confronti")
  @ResponseStatus(HttpStatus.CREATED)
  public Saved compare(@Valid @RequestBody Calculate req) {
    return service.compare(req);
  }

  @PostMapping("/confronti/{id}/duplica")
  @ResponseStatus(HttpStatus.CREATED)
  public Saved duplicateComparison(@PathVariable long id) {
    return service.save(
        "bollette", null, service.read("confronti", id, Comparison.class).bill(), null);
  }

  @GetMapping("/esempi")
  public List<Map<String, String>> examples() {
    return examples.list();
  }

  @PostMapping("/esempi/{name}")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> load(@PathVariable String name) {
    return examples.load(name);
  }

  @GetMapping("/impostazioni-pdf")
  public Map<String, Object> settings() {
    var r = service.list("pdf-settings");
    if (r.isEmpty()) return Map.of("opzioni", PdfPersonalizzazione.predefinita(), "versione", 0);
    var last = r.get(0);
    return Map.of("opzioni", last.data(), "versione", last.version());
  }

  public record Settings(
      @Valid @jakarta.validation.constraints.NotNull PdfPersonalizzazione opzioni,
      @jakarta.validation.constraints.NotNull Long versione) {}

  @PutMapping("/impostazioni-pdf")
  public Map<String, Object> settings(@Valid @RequestBody Settings req) throws IOException {
    pdf.generate(0, examples.preview(), req.opzioni());
    var r = service.list("pdf-settings");
    service.save("pdf-settings", r.isEmpty() ? null : r.get(0).id(), req.opzioni(), req.versione());
    return settings();
  }

  @PostMapping("/impostazioni-pdf/anteprima")
  public PdfAnteprima preview(@Valid @RequestBody PdfPersonalizzazione opts) throws IOException {
    return PdfAnteprima.da(pdf.generate(0, examples.preview(), opts));
  }

  @GetMapping("/confronti/{id}/pdf")
  public ResponseEntity<byte[]> pdf(@PathVariable long id) throws IOException {
    var list = service.list("pdf-settings");
    var opts =
        list.isEmpty()
            ? PdfPersonalizzazione.predefinita()
            : service.read("pdf-settings", list.get(0).id(), PdfPersonalizzazione.class);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=confronto-gas-" + id + ".pdf")
        .contentType(MediaType.APPLICATION_PDF)
        .body(pdf.generate(id, service.read("confronti", id, Comparison.class), opts));
  }

  @GetMapping("/confronti/{id}/export")
  public ResponseEntity<?> export(@PathVariable long id, @RequestParam String format) {
    var comparison = service.read("confronti", id, Comparison.class);
    if (format.equals("json"))
      return ResponseEntity.ok()
          .header(
              HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=confronto-gas-" + id + ".json")
          .body(comparison);
    if (!format.equals("csv")) throw new IllegalArgumentException("Formato previsto: csv o json");
    StringBuilder s =
        new StringBuilder(
            "mese;codice;voce;categoria;unita;quantita;tariffa;importo_raw;iva_raw;scaglione;origine\r\n");
    for (var r : comparison.result().rows()) {
      var vals =
          List.of(
              r.month(),
              r.code(),
              r.label(),
              r.category().name(),
              r.unit(),
              r.quantity().toPlainString(),
              r.rate().toPlainString(),
              r.amount().toPlainString(),
              r.vat().toPlainString(),
              r.band() == null ? "" : r.band(),
              r.origin());
      s.append(vals.stream().map(GasController::csv).reduce((a, b) -> a + ";" + b).orElse(""))
          .append("\r\n");
    }
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=confronto-gas-" + id + ".csv")
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .body(s.toString());
  }

  static String csv(String value) {
    if (value.matches("^[=+@\\t\\r].*")) value = "'" + value;
    return "\"" + value.replace("\"", "\"\"") + "\"";
  }
}
