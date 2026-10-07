package it.progettogas.shared;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class JsonCodec {
  private final ObjectMapper mapper;

  public JsonCodec(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public String scrivi(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Impossibile salvare lo snapshot", e);
    }
  }

  public <T> T leggi(String json, Class<T> type) {
    try {
      return mapper.readValue(json, type);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Snapshot non leggibile", e);
    }
  }
}
