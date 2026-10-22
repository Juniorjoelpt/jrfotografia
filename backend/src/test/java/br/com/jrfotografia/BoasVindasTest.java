package br.com.jrfotografia;

import br.com.jrfotografia.servico.EmailServico;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
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

/**
 * As quatro portas para criar trabalho fazem o mesmo (contrato + área + e-mail de boas-vindas),
 * "Fechou: criar trabalho" fecha o pedido, e a área da cliente abre a galeria sem pedir a senha.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BoasVindasTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired EmailServico email;
    @Autowired br.com.jrfotografia.dominio.Repositorios.EstudioRepo estudios;
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
        estudios.findAll().forEach(e -> { e.setSmtpSenha(null); estudios.save(e); });
    }

    @Test
    void pedidoFechadoViraTrabalhoComBoasVindasEGaleriaPelaArea() throws Exception {
        mvc.perform(auth(put("/api/estudio/email")).content("""
                {"ativo":true,"host":"smtp.teste.local","porta":587,"usuario":"jr@teste.local","senha":"x","seguranca":"STARTTLS","avisos":true,"lembretes":true}"""))
                .andExpect(status().isOk());
        mvc.perform(auth(put("/api/estudio/contrato")).content("{\"texto\":\"\",\"responsavel\":\"Joel Júnior\",\"documento\":\"12.345.678/0001-90\"}"))
                .andExpect(status().isOk());

        // Pedido pelo site
        mvc.perform(post("/api/publico/orcamento").header("X-Real-IP", "10.91.0.1").contentType(MediaType.APPLICATION_JSON).content("""
                {"nome":"Fernanda Fechou","telefone":"98911223344","email":"fernanda@teste.local","tipoTrabalho":"Ensaio","consentimento":true}"""))
                .andExpect(status().is2xxSuccessful());
        long pedidoId = 0;
        for (JsonNode p : ler(mvc.perform(auth(get("/api/pedidos"))).andReturn())) if (p.get("nome").asText().equals("Fernanda Fechou")) pedidoId = p.get("id").asLong();
        long clienteId = ler(mvc.perform(auth(post("/api/pedidos/" + pedidoId + "/cliente")).content("{}")).andReturn()).get("id").asLong();
        long pacote = 0;
        for (JsonNode p : ler(mvc.perform(auth(get("/api/pacotes"))).andReturn())) if (p.get("nome").asText().equals("Sessão 10")) pacote = p.get("id").asLong();

        // "Fechou: criar trabalho" → Novo trabalho com o pedido
        JsonNode t = ler(mvc.perform(auth(post("/api/trabalhos")).content("""
                {"clienteId":%d,"pacoteId":%d,"tipo":"Ensaio","dataTrabalho":"%sT10:00:00","valorTotal":300,"formaPagamento":"PARCELADO",
                 "valorSinal":100,"numParcelas":2,"primeiroVencimento":"%s","pedidoId":%d}"""
                .formatted(clienteId, pacote, LocalDate.now().plusDays(20), LocalDate.now(), pedidoId))).andExpect(status().isCreated()).andReturn());
        long trabalhoId = t.get("resumo").get("id").asLong();
        String area = t.get("areaCliente").asText();

        // O pedido passa a Fechado (conta na conversão)
        for (JsonNode p : ler(mvc.perform(auth(get("/api/pedidos"))).andReturn())) {
            if (p.get("id").asLong() == pedidoId) assertThat(p.get("status").asText()).isEqualTo("FECHADO");
        }
        // Contrato pronto para assinar, como nas outras portas
        assertThat(ler(mvc.perform(auth(get("/api/trabalhos/" + trabalhoId + "/assinatura"))).andReturn()).get("status").asText()).isEqualTo("PENDENTE");
        // E-mail de boas-vindas com a área e o contrato
        EmailServico.Mensagem m = email.caixa().stream().filter(x -> x.tipo().equals("TRABALHO_CRIADO")).findFirst().orElseThrow();
        assertThat(m.para()).isEqualTo("fernanda@teste.local");
        assertThat(m.html()).contains("http://teste.local/meu/" + area).contains("http://teste.local/assinar/");

        // Galeria pela área: antes das fotos, não abre; na seleção, abre sem senha
        mvc.perform(post("/api/publico/cliente/" + area + "/galeria")).andExpect(status().isNotFound());
        var envio = multipart("/api/trabalhos/" + trabalhoId + "/fotos");
        envio.file(foto("A.jpg")).file(foto("B.jpg"));
        mvc.perform(envio.header("Authorization", "Bearer " + painel)).andExpect(status().isCreated());
        mvc.perform(auth(patch("/api/trabalhos/" + trabalhoId + "/status")).content("{\"status\":\"SELECAO\"}")).andExpect(status().isOk());
        JsonNode g = ler(mvc.perform(post("/api/publico/cliente/" + area + "/galeria")).andExpect(status().isOk()).andReturn());
        assertThat(g.get("galeria").asText()).isEqualTo(t.get("galeriaToken").asText());
        mvc.perform(get("/api/galeria/" + g.get("galeria").asText()).header("Authorization", "Bearer " + g.get("acesso").asText()))
                .andExpect(status().isOk());
        // Link de área inexistente não abre nada
        mvc.perform(post("/api/publico/cliente/nao-existe/galeria")).andExpect(status().isNotFound());
    }

    private static MockMultipartFile foto(String nome) throws Exception {
        BufferedImage img = new BufferedImage(600, 400, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return new MockMultipartFile("arquivos", nome, "image/jpeg", out.toByteArray());
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b) {
        return b.header("Authorization", "Bearer " + painel).contentType(MediaType.APPLICATION_JSON);
    }

    private JsonNode ler(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsByteArray());
    }
}
