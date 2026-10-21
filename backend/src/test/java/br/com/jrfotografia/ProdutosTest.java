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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Fase 3 · bloco 4: pedido de álbum com aprovação da diagramação pelo cliente, laboratório e estoque. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProdutosTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    String painel;

    @BeforeEach
    void entrar() throws Exception {
        painel = ler(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@teste.local\",\"senha\":\"senha-de-teste-123\"}")).andReturn()).get("token").asText();
    }

    @Test
    void albumComAprovacaoDoClienteELaboratorio() throws Exception {
        long trab = trabalho("Lia Album");
        JsonNode p = ler(mvc.perform(auth(post("/api/produtos/pedidos")).content(
                "{\"trabalhoId\":%d,\"tipo\":\"ALBUM\",\"descricao\":\"30x30 20 lâminas\",\"valorVenda\":900,\"custo\":350,\"laboratorio\":\"Lab Foto SP\",\"cobrar\":true}"
                        .formatted(trab))).andExpect(status().isOk()).andReturn());
        long id = p.get("id").asLong();
        assertThat(p.get("cobrado").asBoolean()).isTrue();
        assertThat(p.get("status").asText()).isEqualTo("NOVO");
        JsonNode det = ler(mvc.perform(auth(get("/api/trabalhos/" + trab))).andReturn());
        assertThat(det.get("parcelas").toString()).contains("PRODUTO").contains("Álbum 30x30 20 lâminas");

        mudar(id, "APROVACAO").andExpect(status().isUnprocessableEntity()); // sem lâminas
        laminas(id, "lam_02.jpg", "lam_01.jpg", "lam_03.jpg");
        p = pedido(id);
        assertThat(p.get("status").asText()).isEqualTo("DIAGRAMACAO");
        assertThat(p.get("laminas").get(0).get("nome").asText()).isEqualTo("lam_01.jpg");
        mudar(id, "LABORATORIO").andExpect(status().isUnprocessableEntity()); // não aprovado
        String token = ler(mudar(id, "APROVACAO").andExpect(status().isOk()).andReturn()).get("tokenAprovacao").asText();

        // Cliente vê a versão 1 e pede ajuste numa lâmina
        JsonNode pub = ler(mvc.perform(get("/api/publico/albuns/" + token)).andExpect(status().isOk()).andReturn());
        assertThat(pub.get("laminas")).hasSize(3);
        long l2 = pub.get("laminas").get(1).get("id").asLong();
        mvc.perform(get("/api/publico/albuns/" + token + "/laminas/" + l2 + "/mini")).andExpect(status().isOk());
        mvc.perform(post("/api/publico/albuns/" + token).contentType(MediaType.APPLICATION_JSON).content("{\"aprova\":false}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/publico/albuns/" + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"aprova\":false,\"comentarios\":{\"" + l2 + "\":\"Trocar a foto da direita\"}}")).andExpect(status().isOk());
        p = pedido(id);
        assertThat(p.get("aprovacao").asText()).isEqualTo("AJUSTES");
        assertThat(p.get("status").asText()).isEqualTo("DIAGRAMACAO");

        // Nova versão: as lâminas antigas com comentário ficam no histórico; a antiga não abre mais pelo link
        laminas(id, "v2_01.jpg", "v2_02.jpg");
        p = pedido(id);
        assertThat(p.get("versaoLayout").asInt()).isEqualTo(2);
        assertThat(p.get("laminas")).hasSize(2);
        assertThat(p.get("versoesAnteriores").get(0).get("comentario").asText()).isEqualTo("Trocar a foto da direita");
        mvc.perform(get("/api/publico/albuns/" + token + "/laminas/" + l2 + "/mini")).andExpect(status().isNotFound());
        mudar(id, "APROVACAO").andExpect(status().isOk());
        mvc.perform(post("/api/publico/albuns/" + token).contentType(MediaType.APPLICATION_JSON).content("{\"aprova\":true,\"comentario\":\"Perfeito!\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/publico/albuns/" + token).contentType(MediaType.APPLICATION_JSON).content("{\"aprova\":true}"))
                .andExpect(status().isUnprocessableEntity());
        assertThat(pedido(id).get("status").asText()).isEqualTo("APROVADO");

        // Laboratório: custo vira despesa; pronto; entregue
        mvc.perform(auth(patch("/api/produtos/pedidos/" + id + "/status")).content(
                "{\"status\":\"LABORATORIO\",\"previsaoLab\":\"" + LocalDate.now().plusDays(10) + "\"}")).andExpect(status().isOk());
        assertThat(pedido(id).get("custoLancado").asBoolean()).isTrue();
        assertThat(ler(mvc.perform(auth(get("/api/despesas"))).andReturn()).toString()).contains("Laboratório").contains("Lab Foto SP");
        mudar(id, "PRONTO").andExpect(status().isOk());
        assertThat(ler(mudar(id, "ENTREGUE").andReturn()).get("entregueEm").isNull()).isFalse();
    }

    @Test
    void cancelarTiraCobrancaEEstoqueControlaSaldo() throws Exception {
        long trab = trabalho("Mel Quadro");
        long id = ler(mvc.perform(auth(post("/api/produtos/pedidos")).content(
                "{\"trabalhoId\":%d,\"tipo\":\"QUADRO\",\"descricao\":\"60x90 canvas\",\"valorVenda\":450,\"cobrar\":true}".formatted(trab)))
                .andReturn()).get("id").asLong();
        mudar(id, "APROVACAO").andExpect(status().isUnprocessableEntity()); // só álbum
        mudar(id, "CANCELADO").andExpect(status().isOk());
        assertThat(ler(mvc.perform(auth(get("/api/trabalhos/" + trab))).andReturn()).get("parcelas").toString()).doesNotContain("PRODUTO");

        JsonNode m = ler(mvc.perform(auth(post("/api/estoque/materiais")).content(
                "{\"nome\":\"Pendrive 32GB\",\"unidade\":\"un\",\"minimo\":6,\"custoUnitario\":25,\"quantidadeInicial\":10}")).andReturn());
        long mat = m.get("id").asLong();
        assertThat(m.get("quantidade").asDouble()).isEqualTo(10.0);
        assertThat(m.get("baixo").asBoolean()).isFalse();
        JsonNode r = ler(mvc.perform(auth(post("/api/estoque/movimentos")).content(
                "{\"materialId\":%d,\"tipo\":\"SAIDA\",\"quantidade\":4,\"motivo\":\"Entrega evento\"}".formatted(mat))).andReturn());
        assertThat(r.get("quantidade").asDouble()).isEqualTo(6.0);
        assertThat(r.get("baixo").asBoolean()).isTrue();
        mvc.perform(auth(post("/api/estoque/movimentos")).content("{\"materialId\":%d,\"tipo\":\"SAIDA\",\"quantidade\":20}".formatted(mat)))
                .andExpect(status().isUnprocessableEntity());
        r = ler(mvc.perform(auth(post("/api/estoque/movimentos")).content("{\"materialId\":%d,\"tipo\":\"AJUSTE\",\"quantidade\":15}".formatted(mat))).andReturn());
        assertThat(r.get("quantidade").asDouble()).isEqualTo(15.0);
        JsonNode movs = ler(mvc.perform(auth(get("/api/estoque/movimentos?material=" + mat))).andReturn());
        assertThat(movs).hasSize(3);
        assertThat(movs.get(1).get("saldo").asDouble()).isEqualTo(6.0);
    }

    // ---------------------------------------------------------------- apoio

    private org.springframework.test.web.servlet.ResultActions mudar(long id, String s) throws Exception {
        return mvc.perform(auth(patch("/api/produtos/pedidos/" + id + "/status")).content("{\"status\":\"" + s + "\"}"));
    }

    private JsonNode pedido(long id) throws Exception {
        return ler(mvc.perform(auth(get("/api/produtos/pedidos/" + id))).andReturn());
    }

    private void laminas(long id, String... nomes) throws Exception {
        var b = multipart("/api/produtos/pedidos/" + id + "/laminas");
        for (String n : nomes) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(1200, 600, BufferedImage.TYPE_INT_RGB), "jpg", out);
            b.file(new MockMultipartFile("arquivos", n, "image/jpeg", out.toByteArray()));
        }
        mvc.perform(b.header("Authorization", "Bearer " + painel)).andExpect(status().isOk());
    }

    private long trabalho(String nome) throws Exception {
        long cli = ler(mvc.perform(auth(post("/api/clientes")).content("{\"nome\":\"" + nome + "\"}")).andReturn()).get("id").asLong();
        long pac = 0;
        for (JsonNode x : ler(mvc.perform(auth(get("/api/pacotes"))).andReturn())) if (x.get("nome").asText().equals("Sessão 10")) pac = x.get("id").asLong();
        return ler(mvc.perform(auth(post("/api/trabalhos")).content("{\"clienteId\":%d,\"pacoteId\":%d,\"formaPagamento\":\"AVISTA\",\"primeiroVencimento\":\"%s\"}"
                .formatted(cli, pac, LocalDate.now()))).andExpect(status().isCreated()).andReturn()).get("resumo").get("id").asLong();
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b) {
        return b.header("Authorization", "Bearer " + painel).contentType(MediaType.APPLICATION_JSON);
    }

    private JsonNode ler(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsByteArray());
    }
}
