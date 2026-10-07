package it.progettogas.shared;

import java.util.Map;
import java.util.TreeMap;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
  public record Errore(String message, Map<String, String> fields) {}

  @ExceptionHandler(NotFoundException.class)
  ResponseEntity<Errore> nonTrovato(NotFoundException e) {
    return risposta(404, e.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<Errore> dominio(IllegalArgumentException e) {
    return risposta(400, e.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<Errore> validazione(MethodArgumentNotValidException e) {
    Map<String, String> fields = new TreeMap<>();
    e.getBindingResult()
        .getFieldErrors()
        .forEach(f -> fields.put(f.getField(), f.getDefaultMessage()));
    return ResponseEntity.badRequest().body(new Errore("Controlla i dati inseriti", fields));
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<Errore> formato(Exception e) {
    return risposta(400, "Formato dei dati non valido");
  }

  @ExceptionHandler({
    jakarta.validation.ConstraintViolationException.class,
    org.springframework.web.method.annotation.HandlerMethodValidationException.class
  })
  ResponseEntity<Errore> parametriInvalidi(Exception e) {
    return risposta(400, "Parametri della richiesta non validi");
  }

  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  ResponseEntity<Errore> conflitto(Exception e) {
    return risposta(409, "Dati modificati da un'altra sessione. Ricarica prima di salvare.");
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<Errore> vincolo(Exception e) {
    return risposta(409, "Operazione incompatibile con i dati esistenti");
  }

  @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
  ResponseEntity<Errore> accesso(org.springframework.web.server.ResponseStatusException e) {
    return risposta(
        e.getStatusCode().value(),
        e.getReason() == null ? "Operazione non consentita" : e.getReason());
  }

  private ResponseEntity<Errore> risposta(int status, String message) {
    return ResponseEntity.status(status).body(new Errore(message, Map.of()));
  }
}
