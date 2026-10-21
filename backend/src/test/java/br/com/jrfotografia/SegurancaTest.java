package br.com.jrfotografia;

import br.com.jrfotografia.servico.Totp;
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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Fase 3 · bloco 6: verificação em duas etapas (TOTP + códigos de recuperação) e registro de atividades. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SegurancaTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    String admin;

    @BeforeEach
    void entrar() throws Exception {
        admin = ler(login("admin@teste.local", "senha-de-teste-123").andReturn()).get("token").asText();
    }

    @Test
    void totpConfereComOVetorDaRfc6238() {
        byte[] segredo = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
        assertThat(Totp.codigo(segredo, 59 / 30)).isEqualTo("287082");
        assertThat(Totp.codigo(segredo, 1111111109L / 30)).isEqualTo("081804");
        assertThat(Totp.deBase32(Totp.base32(segredo))).isEqualTo(segredo);
        assertThat(Totp.base32(segredo)).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
    }

    @Test
    void duasEtapasComAppCodigoDeRecuperacaoERegistro() throws Exception {
        mvc.perform(post("/api/usuarios").header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Rafa Segura\",\"email\":\"rafa@teste.local\",\"senha\":\"senha-forte-1\"}")).andExpect(status().isCreated());
        String rafa = ler(login("rafa@teste.local", "senha-forte-1").andReturn()).get("token").asText();
        assertThat(ler(mvc.perform(get("/api/conta/2fa").header("Authorization", "Bearer " + rafa)).andReturn()).get("ativo").asBoolean()).isFalse();

        // Configura: segredo, código errado é recusado, código certo liga e devolve 10 códigos de recuperação
        JsonNode cfg = ler(mvc.perform(post("/api/conta/2fa/iniciar").header("Authorization", "Bearer " + rafa)).andExpect(status().isOk()).andReturn());
        assertThat(cfg.get("otpauth").asText()).startsWith("otpauth://totp/").contains("issuer=").contains("rafa%40teste.local");
        byte[] segredo = Totp.deBase32(cfg.get("segredo").asText());
        long passo = Totp.passo(Instant.now());
        mvc.perform(post("/api/conta/2fa/ativar").header("Authorization", "Bearer " + rafa).contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigo\":\"000000\"}")).andExpect(status().isUnprocessableEntity());
        JsonNode codigos = ler(mvc.perform(post("/api/conta/2fa/ativar").header("Authorization", "Bearer " + rafa).contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigo\":\"" + Totp.codigo(segredo, passo) + "\"}")).andExpect(status().isOk()).andReturn()).get("codigos");
        assertThat(codigos).hasSize(10);
        assertThat(codigos.get(0).asText()).matches("[A-Z2-9]{4}-[A-Z2-9]{4}");

        // Login agora pede o segundo passo; o desafio não abre o painel
        JsonNode l = ler(login("rafa@teste.local", "senha-forte-1").andExpect(status().isOk()).andReturn());
        assertThat(l.get("precisa2fa").asBoolean()).isTrue();
        assertThat(l.get("token").isNull()).isTrue();
        String desafio = l.get("desafio").asText();
        mvc.perform(get("/api/painel").header("Authorization", "Bearer " + desafio)).andExpect(status().isUnauthorized());
        segundo(desafio, "123456").andExpect(status().isUnauthorized());
        // O mesmo código usado para ligar não vale de novo (sem repetição); o do próximo intervalo vale
        segundo(desafio, Totp.codigo(segredo, passo)).andExpect(status().isUnauthorized());
        JsonNode s = ler(segundo(desafio, Totp.codigo(segredo, passo + 1)).andExpect(status().isOk()).andReturn());
        assertThat(s.get("doisFatores").asBoolean()).isTrue();
        mvc.perform(get("/api/painel").header("Authorization", "Bearer " + s.get("token").asText())).andExpect(status().isOk());

        // Código de recuperação: vale uma vez só
        String rec = codigos.get(3).asText();
        String d2 = ler(login("rafa@teste.local", "senha-forte-1").andReturn()).get("desafio").asText();
        segundo(d2, rec.toLowerCase().replace("-", " ")).andExpect(status().isOk());
        String d3 = ler(login("rafa@teste.local", "senha-forte-1").andReturn()).get("desafio").asText();
        segundo(d3, rec).andExpect(status().isUnauthorized());
        assertThat(ler(mvc.perform(get("/api/conta/2fa").header("Authorization", "Bearer " + s.get("token").asText())).andReturn())
                .get("codigosRestantes").asInt()).isEqualTo(9);

        // Celular perdido: o administrador remove; o próximo login é direto
        long rafaId = 0;
        for (JsonNode u : ler(mvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + admin)).andReturn())) {
            if (u.get("email").asText().equals("rafa@teste.local")) {
                rafaId = u.get("id").asLong();
                assertThat(u.get("doisFatores").asBoolean()).isTrue();
            }
        }
        mvc.perform(post("/api/usuarios/" + rafaId + "/2fa/remover").header("Authorization", "Bearer " + admin)).andExpect(status().isNoContent());
        assertThat(ler(login("rafa@teste.local", "senha-forte-1").andReturn()).get("token").isNull()).isFalse();

        // Registro de atividades: só o administrador vê
        login("rafa@teste.local", "errada-de-proposito").andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auditoria").header("Authorization", "Bearer " + s.get("token").asText())).andExpect(status().isForbidden());
        String hist = ler(mvc.perform(get("/api/auditoria?usuario=" + rafaId).header("Authorization", "Bearer " + admin)).andReturn()).toString();
        assertThat(hist).contains("Ligou a verificação em duas etapas", "Entrou no painel (duas etapas)", "Entrou no painel com código de recuperação",
                "Código de verificação errado", "Tentativa de entrar com senha errada", "Restam 9 códigos de recuperação");
        String todos = ler(mvc.perform(get("/api/auditoria").header("Authorization", "Bearer " + admin)).andReturn()).toString();
        assertThat(todos).contains("Removeu verificação em duas etapas de outro usuário", "Cadastrou usuário do painel");
    }

    @Test
    void alteracoesDoPainelFicamNoHistoricoComDetalhe() throws Exception {
        long cli = ler(mvc.perform(post("/api/clientes").header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Teo Historico\"}")).andReturn()).get("id").asLong();
        long pac = 0;
        for (JsonNode p : ler(mvc.perform(get("/api/pacotes").header("Authorization", "Bearer " + admin)).andReturn())) {
            if (p.get("nome").asText().equals("Sessão 10")) pac = p.get("id").asLong();
        }
        long t = ler(mvc.perform(post("/api/trabalhos").header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"clienteId\":%d,\"pacoteId\":%d,\"formaPagamento\":\"AVISTA\",\"primeiroVencimento\":\"%s\"}".formatted(cli, pac, LocalDate.now())))
                .andReturn()).get("resumo").get("id").asLong();
        mvc.perform(patch("/api/trabalhos/" + t + "/status").header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"FOTOGRAFADO\"}")).andExpect(status().isOk());
        JsonNode lista = ler(mvc.perform(get("/api/auditoria").header("Authorization", "Bearer " + admin)).andReturn());
        boolean achou = false;
        for (JsonNode a : lista) {
            if (a.get("acao").asText().equals("Mudou a etapa do trabalho") && a.get("entidadeId").asLong() == t) {
                assertThat(a.get("detalhe").asText()).isEqualTo("Para fotografado");
                achou = true;
            }
        }
        assertThat(achou).isTrue();
        assertThat(lista.toString()).contains("Cadastrou cliente", "Criou trabalho");
    }

    private ResultActions login(String email, String senha) throws Exception {
        return mvc.perform(post("/api/auth/login").header("X-Real-IP", "10.66.0.1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}"));
    }

    private ResultActions segundo(String desafio, String codigo) throws Exception {
        return mvc.perform(post("/api/auth/2fa").contentType(MediaType.APPLICATION_JSON)
                .content("{\"desafio\":\"" + desafio + "\",\"codigo\":\"" + codigo + "\"}"));
    }

    private JsonNode ler(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsByteArray());
    }
}
