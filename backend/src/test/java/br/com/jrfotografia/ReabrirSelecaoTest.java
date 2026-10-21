package br.com.jrfotografia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Reabrir a seleção: cliente escolhe mais fotos e as extras são recalculadas sem cobrar duas vezes. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReabrirSelecaoTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    String painel;

    @BeforeEach
    void entrar() throws Exception {
        painel = ler(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@teste.local\",\"senha\":\"senha-de-teste-123\"}")).andReturn()).get("token").asText();
    }

    @Test
    void reabreEscolheMaisEExtrasSaoRecalculadas() throws Exception {
        long cliente = ler(mvc.perform(post("/api/clientes").header("Authorization", "Bearer " + painel)
                .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Rita Reabre\"}")).andReturn()).get("id").asLong();
        long pacote = 0;
        for (JsonNode p : ler(mvc.perform(get("/api/pacotes").header("Authorization", "Bearer " + painel)).andReturn())) {
            if (p.get("nome").asText().equals("Sessão 10")) pacote = p.get("id").asLong(); // 10 fotos, extra R$ 35
        }
        JsonNode t = ler(mvc.perform(post("/api/trabalhos").header("Authorization", "Bearer " + painel).contentType(MediaType.APPLICATION_JSON)
                .content("{\"clienteId\":%d,\"pacoteId\":%d,\"formaPagamento\":\"AVISTA\",\"primeiroVencimento\":\"%s\"}"
                        .formatted(cliente, pacote, LocalDate.now()))).andExpect(status().isCreated()).andReturn());
        long id = t.get("resumo").get("id").asLong();
        String token = t.get("galeriaToken").asText();
        double base = t.get("resumo").get("valorTotal").asDouble();

        MockMultipartHttpServletRequestBuilder envio = multipart("/api/trabalhos/" + id + "/fotos");
        for (int i = 1; i <= 13; i++) envio.file(imagem("IMG_%04d.jpg".formatted(i)));
        mvc.perform(envio.header("Authorization", "Bearer " + painel)).andExpect(status().isCreated());

        // Antes de o cliente concluir, não há o que reabrir.
        mvc.perform(post("/api/trabalhos/" + id + "/reabrir-selecao").header("Authorization", "Bearer " + painel)).andExpect(status().isUnprocessableEntity());
        mudarStatus(id, "SELECAO");
        mvc.perform(post("/api/trabalhos/" + id + "/reabrir-selecao").header("Authorization", "Bearer " + painel)).andExpect(status().isUnprocessableEntity());

        String senha = ler(mvc.perform(post("/api/trabalhos/" + id + "/senha-galeria").header("Authorization", "Bearer " + painel)).andReturn()).get("senha").asText();
        String acesso = ler(mvc.perform(post("/api/galeria/" + token + "/acesso").contentType(MediaType.APPLICATION_JSON)
                .content("{\"senha\":\"" + senha + "\"}")).andReturn()).get("acesso").asText();
        List<Long> fotos = new ArrayList<>();
        for (JsonNode f : ler(mvc.perform(get("/api/galeria/" + token).header("Authorization", "Bearer " + acesso)).andReturn()).get("fotos")) {
            fotos.add(f.get("id").asLong());
        }

        // 1ª rodada: 11 fotos → 1 extra (R$ 35).
        escolher(token, acesso, fotos.subList(0, 11));
        fechar(token, acesso);
        JsonNode d = detalhe(id);
        assertThat(d.get("resumo").get("status").asText()).isEqualTo("EDICAO");
        assertThat(extras(d)).containsExactly("1 foto extra=35.0=false");

        // Reabre: volta para a seleção com as escolhas mantidas.
        JsonNode reaberto = ler(mvc.perform(post("/api/trabalhos/" + id + "/reabrir-selecao").header("Authorization", "Bearer " + painel))
                .andExpect(status().isOk()).andReturn());
        assertThat(reaberto.get("resumo").get("status").asText()).isEqualTo("SELECAO");
        assertThat(reaberto.get("selecaoFechadaEm").isNull()).isTrue();
        JsonNode g = ler(mvc.perform(get("/api/galeria/" + token).header("Authorization", "Bearer " + acesso)).andReturn());
        assertThat(g.get("selecaoAberta").asBoolean()).isTrue();
        assertThat(g.get("selecionadas").asInt()).isEqualTo(11);

        // 2ª rodada: 12 fotos → a extra não paga é refeita: 2 extras (R$ 70), total base + 70.
        escolher(token, acesso, fotos.subList(11, 12));
        fechar(token, acesso);
        d = detalhe(id);
        assertThat(extras(d)).containsExactly("2 fotos extras=70.0=false");
        assertThat(d.get("resumo").get("valorTotal").asDouble()).isEqualTo(base + 70);

        // Paga as extras, reabre e escolhe mais 1: só a diferença (1 foto) é cobrada.
        long extraId = 0;
        for (JsonNode p : d.get("parcelas")) if (p.get("tipo").asText().equals("EXTRA")) extraId = p.get("id").asLong();
        mvc.perform(post("/api/parcelas/" + extraId + "/pagamento").header("Authorization", "Bearer " + painel)).andExpect(status().isOk());
        mvc.perform(patch("/api/trabalhos/" + id + "/status").header("Authorization", "Bearer " + painel)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SELECAO\"}")).andExpect(status().isOk()); // mesmo efeito
        escolher(token, acesso, fotos.subList(12, 13));
        fechar(token, acesso);
        d = detalhe(id);
        assertThat(extras(d)).containsExactlyInAnyOrder("2 fotos extras=70.0=true", "1 foto extra=35.0=false");
        assertThat(d.get("resumo").get("valorTotal").asDouble()).isEqualTo(base + 105);
    }

    private List<String> extras(JsonNode d) {
        List<String> r = new ArrayList<>();
        for (JsonNode p : d.get("parcelas")) {
            if (p.get("tipo").asText().equals("EXTRA")) {
                r.add(p.get("descricao").asText() + "=" + p.get("valor").asDouble() + "=" + !p.get("pagoEm").isNull());
            }
        }
        return r;
    }

    private void escolher(String token, String acesso, List<Long> ids) throws Exception {
        for (long f : ids) {
            mvc.perform(put("/api/galeria/" + token + "/fotos/" + f + "/selecao").header("Authorization", "Bearer " + acesso)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"selecionada\":true}")).andExpect(status().isOk());
        }
    }

    private void fechar(String token, String acesso) throws Exception {
        mvc.perform(post("/api/galeria/" + token + "/fechar-selecao").header("Authorization", "Bearer " + acesso)).andExpect(status().isOk());
    }

    private JsonNode detalhe(long id) throws Exception {
        return ler(mvc.perform(get("/api/trabalhos/" + id).header("Authorization", "Bearer " + painel)).andReturn());
    }

    private void mudarStatus(long id, String s) throws Exception {
        mvc.perform(patch("/api/trabalhos/" + id + "/status").header("Authorization", "Bearer " + painel)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + s + "\"}")).andExpect(status().isOk());
    }

    private static MockMultipartFile imagem(String nome) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(600, 400, BufferedImage.TYPE_INT_RGB), "jpg", out);
        return new MockMultipartFile("arquivos", nome, "image/jpeg", out.toByteArray());
    }

    private JsonNode ler(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsByteArray());
    }
}
