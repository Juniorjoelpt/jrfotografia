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

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Assinatura eletrônica do contrato: link, validações, PDF assinado, verificação e texto congelado. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AssinaturaContratoTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    String painel;

    @BeforeEach
    void entrar() throws Exception {
        painel = ler(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@teste.local\",\"senha\":\"senha-de-teste-123\"}"))
                .andExpect(status().isOk()).andReturn()).get("token").asText();
    }

    @Test
    void clienteAssinaPdfFicaRegistradoEVerificavel() throws Exception {
        long id = trabalho("Ana Assinatura");
        // O trabalho já nasce com o contrato para assinar (boas-vindas, igual em todas as portas)
        assertThat(ler(mvc.perform(get("/api/trabalhos/" + id + "/assinatura").header("Authorization", "Bearer " + painel))
                .andExpect(status().isOk()).andReturn()).get("status").asText()).isEqualTo("PENDENTE");

        // Assinatura do fotógrafo (vai no PDF final)
        mvc.perform(put("/api/estudio/assinatura").header("Authorization", "Bearer " + painel).contentType(MediaType.APPLICATION_JSON)
                .content("{\"assinatura\":\"" + desenho(true) + "\"}")).andExpect(status().isOk());

        JsonNode a = ler(mvc.perform(post("/api/trabalhos/" + id + "/assinatura").header("Authorization", "Bearer " + painel))
                .andExpect(status().isOk()).andReturn());
        String token = a.get("token").asText();
        assertThat(a.get("status").asText()).isEqualTo("PENDENTE");
        // Gerar de novo sem mudar nada devolve o mesmo link
        JsonNode b = ler(mvc.perform(post("/api/trabalhos/" + id + "/assinatura").header("Authorization", "Bearer " + painel)).andReturn());
        assertThat(b.get("token").asText()).isEqualTo(token);

        JsonNode pub = ler(mvc.perform(get("/api/publico/assinaturas/" + token)).andExpect(status().isOk()).andReturn());
        assertThat(pub.get("texto").asText()).contains("Ana Assinatura");
        assertThat(pub.get("codigo").isNull()).isTrue(); // código só aparece depois de assinado
        mvc.perform(get("/api/publico/assinaturas/" + token + "/contrato.pdf")).andExpect(status().isNotFound());

        // Validações
        assinar(token, "Ana", "529.982.247-25", desenho(true), true).andExpect(status().isUnprocessableEntity());
        assinar(token, "Ana Assinatura", "111.111.111-11", desenho(true), true).andExpect(status().isUnprocessableEntity());
        assinar(token, "Ana Assinatura", "529.982.247-25", desenho(false), true).andExpect(status().isUnprocessableEntity());
        assinar(token, "Ana Assinatura", "529.982.247-25", desenho(true), false).andExpect(status().isUnprocessableEntity());
        assertThat(pub.get("perguntaUsoImagem").asBoolean()).isTrue();
        assinar(token, "Ana Assinatura", "529.982.247-25", desenho(true), true, null).andExpect(status().isUnprocessableEntity());

        JsonNode ok = ler(assinar(token, "Ana  Assinatura ", "52998224725", desenho(true), true, false).andExpect(status().isOk()).andReturn());
        assertThat(ok.get("autorizaImagem").asBoolean()).isFalse();
        assertThat(ok.get("status").asText()).isEqualTo("ASSINADO");
        assertThat(ok.get("nomeAssinante").asText()).isEqualTo("Ana Assinatura");
        String codigo = ok.get("codigo").asText();
        assertThat(codigo).matches("[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{2}");
        assinar(token, "Ana Assinatura", "529.982.247-25", desenho(true), true).andExpect(status().isUnprocessableEntity());

        byte[] pdf = mvc.perform(get("/api/publico/assinaturas/" + token + "/contrato.pdf")).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF)).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        mvc.perform(get("/api/trabalhos/" + id + "/assinatura/contrato.pdf").header("Authorization", "Bearer " + painel)).andExpect(status().isOk());

        // Painel vê os dados da assinatura; CPF completou o cadastro do cliente
        JsonNode p = ler(mvc.perform(get("/api/trabalhos/" + id + "/assinatura").header("Authorization", "Bearer " + painel)).andReturn());
        assertThat(p.get("cpfAssinante").asText()).isEqualTo("529.982.247-25");
        assertThat(p.get("ip").asText()).isEqualTo("10.7.7.7");
        JsonNode det = ler(mvc.perform(get("/api/trabalhos/" + id).header("Authorization", "Bearer " + painel)).andReturn());
        assertThat(det.get("cliente").get("cpf").asText()).isEqualTo("529.982.247-25");

        // Verificação pública por código (com ou sem traços) e pelo próprio arquivo
        JsonNode v = ler(mvc.perform(get("/api/publico/verificar/" + codigo.replace("-", "").toLowerCase())).andReturn());
        assertThat(v.get("valido").asBoolean()).isTrue();
        assertThat(v.get("cpf").asText()).isEqualTo("***.982.247-**");
        JsonNode vf = ler(mvc.perform(multipart("/api/publico/verificar").file(new MockMultipartFile("arquivo", "c.pdf", "application/pdf", pdf))
                .param("codigo", codigo)).andReturn());
        assertThat(vf.get("arquivoConfere").asBoolean()).isTrue();
        byte[] alterado = pdf.clone();
        alterado[alterado.length / 2] ^= 1;
        JsonNode va = ler(mvc.perform(multipart("/api/publico/verificar").file(new MockMultipartFile("arquivo", "c.pdf", "application/pdf", alterado))
                .param("codigo", codigo)).andReturn());
        assertThat(va.get("valido").asBoolean()).isTrue();
        assertThat(va.get("arquivoConfere").asBoolean()).isFalse();
        assertThat(ler(mvc.perform(get("/api/publico/verificar/ZZZZ-ZZZZ-ZZ")).andReturn()).get("valido").asBoolean()).isFalse();

        // Já assinado: não gera outro link nem cancela
        mvc.perform(post("/api/trabalhos/" + id + "/assinatura").header("Authorization", "Bearer " + painel)).andExpect(status().isUnprocessableEntity());
        mvc.perform(delete("/api/trabalhos/" + id + "/assinatura").header("Authorization", "Bearer " + painel)).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void contratoAlteradoTrocaOLinkEOAntigoDeixaDeValer() throws Exception {
        long id = trabalho("Bia Mudanca");
        String antigo = ler(mvc.perform(post("/api/trabalhos/" + id + "/assinatura").header("Authorization", "Bearer " + painel)).andReturn())
                .get("token").asText();
        // Muda a data do ensaio: o texto do contrato muda
        mvc.perform(put("/api/trabalhos/" + id).header("Authorization", "Bearer " + painel).contentType(MediaType.APPLICATION_JSON)
                .content("{\"dataTrabalho\":\"" + LocalDate.now().plusDays(40) + "T15:00:00\"}")).andExpect(status().isOk());
        JsonNode atual = ler(mvc.perform(get("/api/trabalhos/" + id + "/assinatura").header("Authorization", "Bearer " + painel)).andReturn());
        assertThat(atual.get("desatualizado").asBoolean()).isTrue();
        {
            String novo = ler(mvc.perform(post("/api/trabalhos/" + id + "/assinatura").header("Authorization", "Bearer " + painel)).andReturn())
                    .get("token").asText();
            assertThat(novo).isNotEqualTo(antigo);
            mvc.perform(get("/api/publico/assinaturas/" + antigo)).andExpect(status().isNotFound());
            mvc.perform(get("/api/publico/assinaturas/" + novo)).andExpect(status().isOk());
        }
        // Cancelar o pedido pendente invalida o link
        String token = ler(mvc.perform(get("/api/trabalhos/" + id + "/assinatura").header("Authorization", "Bearer " + painel)).andReturn())
                .get("token").asText();
        mvc.perform(delete("/api/trabalhos/" + id + "/assinatura").header("Authorization", "Bearer " + painel)).andExpect(status().isNoContent());
        mvc.perform(get("/api/publico/assinaturas/" + token)).andExpect(status().isNotFound());
        // Cliente não acessa o painel de assinatura
        mvc.perform(get("/api/trabalhos/" + id + "/assinatura")).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- apoio

    private org.springframework.test.web.servlet.ResultActions assinar(String token, String nome, String cpf, String desenho, boolean aceite) throws Exception {
        return assinar(token, nome, cpf, desenho, aceite, true);
    }

    private org.springframework.test.web.servlet.ResultActions assinar(String token, String nome, String cpf, String desenho, boolean aceite,
                                                                       Boolean imagem) throws Exception {
        var corpo = new java.util.HashMap<String, Object>(java.util.Map.of("nome", nome, "cpf", cpf, "assinatura", desenho, "aceite", aceite));
        corpo.put("autorizaImagem", imagem);
        return mvc.perform(post("/api/publico/assinaturas/" + token).header("X-Real-IP", "10.7.7.7").header("User-Agent", "Teste/1.0")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corpo)));
    }

    /** PNG transparente com um rabisco (ou vazio). */
    private static String desenho(boolean comTraco) throws Exception {
        BufferedImage img = new BufferedImage(600, 200, BufferedImage.TYPE_INT_ARGB);
        if (comTraco) {
            Graphics2D g = img.createGraphics();
            g.setColor(new Color(20, 20, 20));
            g.setStroke(new BasicStroke(4));
            g.drawLine(40, 150, 200, 40);
            g.drawLine(200, 40, 320, 160);
            g.drawLine(320, 160, 540, 60);
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
    }

    private long trabalho(String nome) throws Exception {
        long cliente = ler(mvc.perform(post("/api/clientes").header("Authorization", "Bearer " + painel)
                .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"" + nome + "\"}")).andReturn()).get("id").asLong();
        long pacote = 0;
        for (JsonNode p : ler(mvc.perform(get("/api/pacotes").header("Authorization", "Bearer " + painel)).andReturn())) {
            if (p.get("nome").asText().equals("Sessão 10")) pacote = p.get("id").asLong();
        }
        return ler(mvc.perform(post("/api/trabalhos").header("Authorization", "Bearer " + painel).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteId\":%d,\"pacoteId\":%d,\"formaPagamento\":\"AVISTA\",\"primeiroVencimento\":\"%s\",\"dataTrabalho\":\"%sT09:00:00\"}"
                                .formatted(cliente, pacote, LocalDate.now(), LocalDate.now().plusDays(20))))
                .andExpect(status().isCreated()).andReturn()).get("resumo").get("id").asLong();
    }

    private JsonNode ler(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsByteArray());
    }
}
