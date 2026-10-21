package br.com.jrfotografia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Fase 3 · bloco 5: mini-sessão com página própria, cupom limitado e programa de indicação. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MarketingTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    String painel;
    int ip = 1;

    @BeforeEach
    void entrar() throws Exception {
        painel = ler(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@teste.local\",\"senha\":\"senha-de-teste-123\"}")).andReturn()).get("token").asText();
    }

    @Test
    void miniSessaoCupomEIndicacao() throws Exception {
        LocalDate dia = LocalDate.now().plusDays(3);
        JsonNode c = ler(mvc.perform(auth(post("/api/campanhas")).content("""
                {"titulo":"Dia das Mães 2026","descricao":"Mini-sessão mãe e filhos","preco":200,"sinalPercentual":50,
                 "duracaoMin":20,"intervaloMin":10,"qtdFotos":8,"precoFotoExtra":25,"prazoEntregaDias":7,
                 "dias":[{"data":"%s","inicio":"09:00","fim":"11:00","local":"Praça da Matriz"}]}""".formatted(dia)))
                .andExpect(status().isOk()).andReturn());
        String slug = c.get("slug").asText();
        assertThat(slug).isEqualTo("dia-das-maes-2026");
        assertThat(ler(mvc.perform(auth(get("/api/pacotes"))).andReturn()).toString()).doesNotContain("Mini-sessão");

        JsonNode pub = ler(mvc.perform(get("/api/publico/campanhas/" + slug)).andExpect(status().isOk()).andReturn());
        assertThat(pub.get("dias").get(0).get("horarios").toString()).isEqualTo("[\"09:00:00\",\"09:30:00\",\"10:00:00\",\"10:30:00\"]");
        assertThat(pub.get("dias").get(0).get("local").asText()).isEqualTo("Praça da Matriz");
        assertThat(pub.get("sinal").asDouble()).isEqualTo(100.0);
        assertThat(ler(mvc.perform(get("/api/publico/campanhas")).andReturn()).toString()).contains(slug);

        // Cupom de 10% só para esta campanha e de uso único
        mvc.perform(auth(post("/api/cupons")).content("{\"codigo\":\"mães 10\",\"tipo\":\"PERCENTUAL\",\"valor\":10,\"usosMax\":1,\"campanhaId\":"
                + c.get("id").asLong() + "}")).andExpect(status().isOk());
        JsonNode d = ler(mvc.perform(get("/api/publico/descontos?codigo=maes10&campanha=" + slug)).andReturn());
        assertThat(d.get("valido").asBoolean()).isTrue();
        assertThat(d.get("valorFinal").asDouble()).isEqualTo(180.0);
        assertThat(ler(mvc.perform(get("/api/publico/descontos?codigo=XYZ123&campanha=" + slug)).andReturn()).get("valido").asBoolean()).isFalse();

        JsonNode r1 = ler(reservar(slug, dia + "T09:00:00", "Ana Mãe", "98970000001", "MAES10").andExpect(status().isCreated()).andReturn());
        assertThat(r1.get("valorTotal").asDouble()).isEqualTo(180.0);
        assertThat(r1.get("valorSinal").asDouble()).isEqualTo(90.0);
        assertThat(r1.get("desconto").asDouble()).isEqualTo(20.0);
        assertThat(r1.get("campanhaSlug").asText()).isEqualTo(slug);
        assertThat(ler(mvc.perform(get("/api/publico/campanhas/" + slug)).andReturn()).get("dias").get(0).get("horarios")).hasSize(3);
        reservar(slug, dia + "T09:00:00", "Outra", "98970000009", null).andExpect(status().isUnprocessableEntity()); // horário já tomado

        long reservaId = idReserva(r1.get("token").asText());
        mvc.perform(auth(post("/api/reservas/" + reservaId + "/confirmacao"))).andExpect(status().isOk());
        reservar(slug, dia + "T10:00:00", "Bia", "98970000002", "MAES10").andExpect(status().isUnprocessableEntity()); // cupom esgotado
        JsonNode cupons = ler(mvc.perform(auth(get("/api/cupons"))).andReturn());
        assertThat(cupons.get(0).get("usos").asInt()).isEqualTo(1);
        JsonNode camp = ler(mvc.perform(auth(get("/api/campanhas"))).andReturn()).get(0);
        assertThat(camp.get("confirmadas").asInt()).isEqualTo(1);
        assertThat(camp.get("faturamento").asDouble()).isEqualTo(180.0);

        // Indicação: Ana passa o código dela para a Carla
        mvc.perform(auth(put("/api/estudio/indicacao")).content("{\"ativa\":true,\"desconto\":15,\"recompensa\":40}")).andExpect(status().isOk());
        long anaId = 0;
        for (JsonNode cl : ler(mvc.perform(auth(get("/api/clientes"))).andReturn())) if (cl.get("nome").asText().equals("Ana Mãe")) anaId = cl.get("id").asLong();
        String codigo = ler(mvc.perform(auth(get("/api/clientes/" + anaId + "/codigo-indicacao"))).andReturn()).get("codigo").asText();
        assertThat(codigo).startsWith("ANA").hasSize(6);
        assertThat(ler(mvc.perform(get("/api/publico/descontos?codigo=" + codigo + "&campanha=" + slug + "&telefone=98970000001")).andReturn())
                .get("valido").asBoolean()).isFalse(); // a própria Ana não usa
        JsonNode r2 = ler(reservar(slug, dia + "T09:30:00", "Carla Amiga", "98970000003", codigo.toLowerCase()).andExpect(status().isCreated()).andReturn());
        assertThat(r2.get("valorTotal").asDouble()).isEqualTo(170.0);
        mvc.perform(auth(post("/api/reservas/" + idReserva(r2.get("token").asText()) + "/confirmacao"))).andExpect(status().isOk());
        JsonNode ind = ler(mvc.perform(auth(get("/api/indicacoes"))).andReturn()).get(0);
        assertThat(ind.get("indicador").asText()).isEqualTo("Ana Mãe");
        assertThat(ind.get("indicado").asText()).isEqualTo("Carla Amiga");
        assertThat(ind.get("recompensa").asDouble()).isEqualTo(40.0);
        mvc.perform(auth(patch("/api/indicacoes/" + ind.get("id").asLong())).content("{\"status\":\"RECOMPENSADA\"}")).andExpect(status().isNoContent());

        // Área da cliente mostra o código para indicar
        String areaAna = ler(mvc.perform(get("/api/publico/reservas/" + r1.get("token").asText())).andReturn()).get("areaCliente").asText();
        JsonNode area = ler(mvc.perform(get("/api/publico/cliente/" + areaAna)).andReturn());
        assertThat(area.get("indicacao").get("codigo").asText()).isEqualTo(codigo);
        assertThat(area.get("indicacao").get("indicacoes").asInt()).isEqualTo(1);
    }

    private ResultActions reservar(String slug, String inicio, String nome, String fone, String codigo) throws Exception {
        String cod = codigo == null ? "null" : "\"" + codigo + "\"";
        return mvc.perform(post("/api/publico/campanhas/" + slug + "/reservas").header("X-Real-IP", "10.55.0." + (ip++))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"inicio\":\"%s\",\"nome\":\"%s\",\"telefone\":\"%s\",\"consentimento\":true,\"codigo\":%s}".formatted(inicio, nome, fone, cod)));
    }

    private long idReserva(String token) throws Exception {
        for (JsonNode r : ler(mvc.perform(auth(get("/api/reservas"))).andReturn())) if (r.get("token").asText().equals(token)) return r.get("id").asLong();
        throw new AssertionError(token);
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b) {
        return b.header("Authorization", "Bearer " + painel).contentType(MediaType.APPLICATION_JSON);
    }

    private JsonNode ler(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsByteArray());
    }
}
