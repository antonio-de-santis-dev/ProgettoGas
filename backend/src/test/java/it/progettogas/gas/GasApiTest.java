package it.progettogas.gas;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GasApiTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;

  JsonNode load() throws Exception {
    return mapper.readTree(
        mvc.perform(post("/api/esempi/GENNAIO-FEBBRAIO"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  @Test
  void snapshotExportsPdfAndSettings() throws Exception {
    var e = load();
    long id = e.path("comparison").path("id").asLong();
    assertThat(
            new java.math.BigDecimal(
                e.path("comparison").path("data").path("result").path("totalRaw").asText()))
        .isEqualByComparingTo("392.605125844");
    var offer = (ObjectNode) e.path("offer").path("data");
    offer.put("price", "0.99");
    mvc.perform(
            put("/api/offerte/" + e.path("offer").path("id"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    mapper.writeValueAsString(
                        java.util.Map.of(
                            "data", offer, "version", e.path("offer").path("version")))))
        .andExpect(status().isOk());
    var stored =
        mapper.readTree(
            mvc.perform(get("/api/confronti/" + id))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(stored.path("data").path("result").path("total").asText()).isEqualTo("392.61");
    assertThat(stored).isEqualTo(e.path("comparison"));
    var bytes =
        mvc.perform(get("/api/confronti/" + id + "/pdf"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    assertThat(new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII))
        .isEqualTo("%PDF-");
    mvc.perform(get("/api/confronti/" + id + "/export?format=csv"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("UG2_FIXED")));
    mvc.perform(get("/api/confronti/" + id + "/export?format=json")).andExpect(status().isOk());
    mvc.perform(post("/api/confronti/" + id + "/duplica")).andExpect(status().isCreated());
    String options =
        "{\"stile\":\"SINTESI\",\"colore\":\"#1E3A8A\",\"logo\":null,\"consulente\":{\"nome\":\"Studio"
            + " Gas Test\",\"ruolo\":\"Consulente\",\"email\":\"test@example.com\",\"telefono\":\"\",\"indirizzo\":\"\",\"dimostrativo\":false}}";
    mvc.perform(
            put("/api/impostazioni-pdf")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"opzioni\":" + options + ",\"versione\":0}"))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/impostazioni-pdf/anteprima")
                .contentType(MediaType.APPLICATION_JSON)
                .content(options))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pagine[0]").exists());
  }

  @Test
  void validationPublicationAndOptimisticLock() throws Exception {
    var e = load();
    long id = e.path("profiles").get(0).path("id").asLong();
    var p = (ObjectNode) e.path("profiles").get(0).path("data");
    p.put("status", "PUBLISHED")
        .put("approvedBy", "Responsabile Test")
        .put("provider", "Test pubblicazione " + id);
    String req = mapper.writeValueAsString(java.util.Map.of("data", p, "version", 0));
    mvc.perform(put("/api/parametri/" + id).contentType(MediaType.APPLICATION_JSON).content(req))
        .andExpect(status().isOk());
    mvc.perform(put("/api/parametri/" + id).contentType(MediaType.APPLICATION_JSON).content(req))
        .andExpect(status().isConflict());
    mvc.perform(
            put("/api/parametri/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(java.util.Map.of("data", p, "version", 1))))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/parametri")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(java.util.Map.of("data", p))))
        .andExpect(status().isBadRequest());
    mvc.perform(post("/api/parametri/" + id + "/duplica"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.status").value("DRAFT"));
    var b = (ObjectNode) e.path("bill").path("data");
    ((ObjectNode) b.path("periods").get(0)).put("consumptionSmc", "-1");
    mvc.perform(
            post("/api/bollette")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(java.util.Map.of("data", b))))
        .andExpect(status().isBadRequest());
  }
}
