package br.com.jrfotografia;

import br.com.jrfotografia.servico.EmailServico;
import br.com.jrfotografia.servico.EmailServico.Mensagem;
import br.com.jrfotografia.servico.Lembretes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** E-mails automáticos (modo memória): configuração, avisos do agendamento, galeria, lembretes e histórico. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmailTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired EmailServico email;
    @Autowired Lembretes lembretes;
    String painel;

    @BeforeEach
    void entrar() throws Exception {
        painel = ler(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@teste.local\",\"senha\":\"senha-de-teste-123\"}")).andReturn()).get("token").asText();
        email.caixa().clear();
    }

    @AfterEach
    void desligar() throws Exception {
        mvc.perform(auth(put("/api/estudio/email")).content("{\"ativo\":false,\"avisos\":true,\"lembretes\":true}"));
    }

    @Test
    void configuracaoAvisosGaleriaELembretes() throws Exception {
        // Sem senha não liga; a senha guardada nunca volta para a tela
        mvc.perform(auth(put("/api/estudio/email")).content("{\"ativo\":true,\"host\":\"smtp.gmail.com\",\"usuario\":\"jr@gmail.com\",\"avisos\":true,\"lembretes\":true}"))
                .andExpect(status().isUnprocessableEntity());
        JsonNode cfg = ler(mvc.perform(auth(put("/api/estudio/email")).content("""
                {"ativo":true,"host":"smtp.gmail.com","porta":587,"usuario":"jr@gmail.com","senha":"abcd efgh ijkl mnop",
                 "seguranca":"STARTTLS","avisos":true,"lembretes":true}""")).andExpect(status().isOk()).andReturn());
        assertThat(cfg.get("temSenha").asBoolean()).isTrue();
        assertThat(cfg.toString()).doesNotContain("abcd");
        mvc.perform(auth(post("/api/estudio/email/teste")).content("{\"para\":\"joel@teste.local\"}")).andExpect(status().isNoContent());
        assertThat(ultimo("TESTE").para()).isEqualTo("joel@teste.local");

        // E-mail do estúdio recebe os avisos
        JsonNode est = ler(mvc.perform(auth(get("/api/estudio"))).andReturn());
        ((com.fasterxml.jackson.databind.node.ObjectNode) est).put("email", "estudio@teste.local");
        mvc.perform(auth(put("/api/estudio")).content(est.toString())).andExpect(status().isOk());

        // Mini-sessão: pré-reserva → e-mail para a cliente (com link e Pix) e aviso para o estúdio
        LocalDate dia = LocalDate.now().plusDays(1);
        String slug = ler(mvc.perform(auth(post("/api/campanhas")).content("""
                {"titulo":"Mini Email","preco":150,"duracaoMin":30,"intervaloMin":0,
                 "dias":[{"data":"%s","inicio":"14:00","fim":"16:00"}]}""".formatted(dia))).andReturn()).get("slug").asText();
        JsonNode r = ler(mvc.perform(post("/api/publico/campanhas/" + slug + "/reservas").header("X-Real-IP", "10.77.0.1")
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"inicio":"%sT14:00:00","nome":"Elisa Email","telefone":"98912340000","email":"elisa@teste.local","consentimento":true}"""
                        .formatted(dia))).andExpect(status().isCreated()).andReturn());
        Mensagem pre = ultimo("PRE_RESERVA");
        assertThat(pre.para()).isEqualTo("elisa@teste.local");
        assertThat(pre.html()).contains("http://teste.local/reserva/" + r.get("token").asText()).contains("Elisa");
        assertThat(ultimo("AVISO_PRE_RESERVA").para()).isEqualTo("estudio@teste.local");

        long reservaId = 0;
        for (JsonNode x : ler(mvc.perform(auth(get("/api/reservas"))).andReturn())) if (x.get("token").asText().equals(r.get("token").asText())) reservaId = x.get("id").asLong();
        mvc.perform(auth(post("/api/reservas/" + reservaId + "/confirmacao"))).andExpect(status().isOk());
        Mensagem conf = ultimo("RESERVA_CONFIRMADA");
        assertThat(conf.html()).contains("http://teste.local/meu/").contains("confirmado");

        // Galeria por e-mail (com a senha) e lembrete do ensaio amanhã (uma vez só)
        String areaToken = ler(mvc.perform(get("/api/publico/reservas/" + r.get("token").asText())).andReturn()).get("areaCliente").asText();
        long trabalhoId = 0;
        for (JsonNode t : ler(mvc.perform(auth(get("/api/trabalhos"))).andReturn())) if (t.get("clienteNome").asText().equals("Elisa Email")) trabalhoId = t.get("id").asLong();
        mvc.perform(auth(post("/api/trabalhos/" + trabalhoId + "/galeria/email")).content("{\"senha\":\"123456\"}")).andExpect(status().isOk());
        assertThat(ultimo("GALERIA_SELECAO").html()).contains("123456").contains("http://teste.local/g/");
        int antes = email.caixa().size();
        lembretes.executar(LocalDate.now());
        Mensagem lemb = ultimo("LEMBRETE_ENSAIO");
        assertThat(lemb.assunto()).contains("amanhã");
        assertThat(lemb.html()).contains("http://teste.local/meu/" + areaToken);
        lembretes.executar(LocalDate.now());
        assertThat(email.caixa().stream().filter(m -> m.tipo().equals("LEMBRETE_ENSAIO")).count()).isEqualTo(1);
        assertThat(email.caixa().size()).isGreaterThan(antes);

        // Histórico no painel
        JsonNode hist = ler(mvc.perform(auth(get("/api/estudio/email/historico"))).andReturn());
        assertThat(hist.toString()).contains("PRE_RESERVA").contains("ENVIADO");

        // Desligado: nada sai
        mvc.perform(auth(put("/api/estudio/email")).content("{\"ativo\":false,\"avisos\":true,\"lembretes\":true}")).andExpect(status().isOk());
        email.caixa().clear();
        mvc.perform(auth(post("/api/trabalhos/" + trabalhoId + "/galeria/email")).content("{\"senha\":\"123456\"}")).andExpect(status().isUnprocessableEntity());
        lembretes.executar(LocalDate.now().minusDays(5));
        assertThat(email.caixa()).isEmpty();
    }

    private Mensagem ultimo(String tipo) {
        List<Mensagem> l = email.caixa().stream().filter(m -> m.tipo().equals(tipo)).toList();
        assertThat(l).as("e-mail " + tipo).isNotEmpty();
        return l.get(l.size() - 1);
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b) {
        return b.header("Authorization", "Bearer " + painel).contentType(MediaType.APPLICATION_JSON);
    }

    private JsonNode ler(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsByteArray());
    }
}
