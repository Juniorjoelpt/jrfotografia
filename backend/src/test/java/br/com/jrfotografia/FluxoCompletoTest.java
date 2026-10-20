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
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Percorre o fluxo da versão 1 de ponta a ponta:
 * cliente → trabalho parcelado → envio de fotos → seleção com extras → entrega → regra de download.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FluxoCompletoTest {

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
    void fluxoDaSelecaoAteODownload() throws Exception {
        // Pacote "Sessão 10": 10 fotos, R$ 200, foto extra R$ 35.
        long pacoteId = pacotePorNome("Sessão 10");

        long clienteId = ler(mvc.perform(post("/api/clientes").header("Authorization", "Bearer " + painel)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Maria Souza\",\"telefone\":\"(98) 99999-0000\",\"origem\":\"INSTAGRAM\"}"))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();

        String trabalhoJson = """
                {"clienteId": %d, "pacoteId": %d, "tipo": "Ensaio feminino",
                 "dataTrabalho": "%s", "localTrabalho": "Praça da Matriz",
                 "formaPagamento": "PARCELADO", "valorSinal": 50, "numParcelas": 2,
                 "primeiroVencimento": "%s"}
                """.formatted(clienteId, pacoteId, LocalDateTime.now().minusDays(1).withNano(0), LocalDate.now());
        JsonNode trabalho = ler(mvc.perform(post("/api/trabalhos").header("Authorization", "Bearer " + painel)
                        .contentType(MediaType.APPLICATION_JSON).content(trabalhoJson))
                .andExpect(status().isCreated()).andReturn());
        long trabalhoId = trabalho.get("resumo").get("id").asLong();
        String token = trabalho.get("galeriaToken").asText();
        assertThat(trabalho.get("parcelas")).hasSize(3); // sinal + 2 parcelas
        assertThat(trabalho.get("parcelas").get(1).get("valor").decimalValue()).isEqualByComparingTo("75.00");

        // 12 fotos enviadas: a seleção vai passar do pacote em 2.
        var envio = multipart("/api/trabalhos/" + trabalhoId + "/fotos");
        for (int i = 1; i <= 12; i++) envio.file(foto("IMG_" + i + ".jpg"));
        mvc.perform(envio.header("Authorization", "Bearer " + painel)).andExpect(status().isCreated());

        String senha = ler(mvc.perform(post("/api/trabalhos/" + trabalhoId + "/senha-galeria")
                .header("Authorization", "Bearer " + painel)).andExpect(status().isOk()).andReturn()).get("senha").asText();
        mvc.perform(patch("/api/trabalhos/" + trabalhoId + "/status").header("Authorization", "Bearer " + painel)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SELECAO\"}")).andExpect(status().isOk());

        // Cliente entra com senha errada e depois com a certa.
        mvc.perform(post("/api/galeria/" + token + "/acesso").contentType(MediaType.APPLICATION_JSON)
                .content("{\"senha\":\"000000x\"}")).andExpect(status().isUnauthorized());
        String acesso = ler(mvc.perform(post("/api/galeria/" + token + "/acesso").contentType(MediaType.APPLICATION_JSON)
                .content("{\"senha\":\"" + senha + "\"}")).andExpect(status().isOk()).andReturn()).get("acesso").asText();

        JsonNode galeria = ler(mvc.perform(get("/api/galeria/" + token).header("Authorization", "Bearer " + acesso))
                .andExpect(status().isOk()).andReturn());
        assertThat(galeria.get("selecaoAberta").asBoolean()).isTrue();
        assertThat(galeria.get("fotosNoPacote").asInt()).isEqualTo(10);

        mvc.perform(get("/api/galeria/" + token + "/fotos/" + galeria.get("fotos").get(0).get("id").asLong()
                + "/miniatura").param("t", acesso)).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));

        for (JsonNode f : galeria.get("fotos")) {
            mvc.perform(put("/api/galeria/" + token + "/fotos/" + f.get("id").asLong() + "/selecao")
                    .header("Authorization", "Bearer " + acesso).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"selecionada\":true}")).andExpect(status().isOk());
        }
        JsonNode fechamento = ler(mvc.perform(post("/api/galeria/" + token + "/fechar-selecao")
                .header("Authorization", "Bearer " + acesso)).andExpect(status().isOk()).andReturn());
        assertThat(fechamento.get("extras").asInt()).isEqualTo(2);
        assertThat(fechamento.get("valorExtras").decimalValue()).isEqualByComparingTo("70.00");

        // Fotos editadas com o mesmo nome das originais, depois a entrega.
        var editadas = multipart("/api/trabalhos/" + trabalhoId + "/editadas");
        for (int i = 1; i <= 12; i++) editadas.file(foto("IMG_" + i + ".jpg"));
        JsonNode resEditadas = ler(mvc.perform(editadas.header("Authorization", "Bearer " + painel))
                .andExpect(status().isOk()).andReturn());
        assertThat(resEditadas.get("recebidas").asInt()).isEqualTo(12);

        JsonNode entregue = ler(mvc.perform(patch("/api/trabalhos/" + trabalhoId + "/status")
                .header("Authorization", "Bearer " + painel).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ENTREGUE\"}")).andExpect(status().isOk()).andReturn());
        assertThat(entregue.get("resumo").get("valorTotal").decimalValue()).isEqualByComparingTo("270.00");
        assertThat(entregue.get("galeriaExpiraEm").asText()).startsWith(LocalDate.now().plusDays(90).toString());

        // Parcelado: sem a 1ª parcela, o download continua bloqueado.
        mvc.perform(get("/api/galeria/" + token + "/download.zip").param("t", acesso)).andExpect(status().isForbidden());
        JsonNode parcelas = entregue.get("parcelas");
        pagar(parcelas.get(0).get("id").asLong()); // sinal
        mvc.perform(get("/api/galeria/" + token + "/download.zip").param("t", acesso)).andExpect(status().isForbidden());
        pagar(parcelas.get(1).get("id").asLong()); // 1ª parcela

        MvcResult zip = mvc.perform(get("/api/galeria/" + token + "/download.zip").param("t", acesso))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(zip)).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"));
    }

    @Test
    void sitePublicoMostraPacotesERecebePedido() throws Exception {
        JsonNode pacotes = ler(mvc.perform(get("/api/publico/pacotes")).andExpect(status().isOk()).andReturn());
        assertThat(pacotes).hasSize(4);
        assertThat(pacotes.get(3).get("qtdFotos").isNull()).isTrue(); // Eventos: todas as fotos

        mvc.perform(post("/api/publico/orcamento").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Ana\",\"telefone\":\"98 98888-1111\",\"tipoTrabalho\":\"Ensaio feminino\",\"consentimento\":true}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/publico/orcamento").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Ana\",\"telefone\":\"98 98888-1111\",\"consentimento\":false}"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/pedidos")).andExpect(status().isUnauthorized());
        JsonNode pedidos = ler(mvc.perform(get("/api/pedidos").header("Authorization", "Bearer " + painel))
                .andExpect(status().isOk()).andReturn());
        assertThat(pedidos.findValuesAsText("telefone")).contains("98988881111");
    }

    private void pagar(long parcelaId) throws Exception {
        mvc.perform(post("/api/parcelas/" + parcelaId + "/pagamento").header("Authorization", "Bearer " + painel)
                .contentType(MediaType.APPLICATION_JSON).content("{\"forma\":\"PIX\"}")).andExpect(status().isOk());
    }

    private long pacotePorNome(String nome) throws Exception {
        for (JsonNode p : ler(mvc.perform(get("/api/pacotes").header("Authorization", "Bearer " + painel)).andReturn())) {
            if (p.get("nome").asText().equals(nome)) return p.get("id").asLong();
        }
        throw new AssertionError("Pacote não encontrado: " + nome);
    }

    private JsonNode ler(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsByteArray());
    }

    private static MockMultipartFile foto(String nome) throws Exception {
        BufferedImage img = new BufferedImage(1200, 800, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setPaint(new GradientPaint(0, 0, new Color(40, 40, 38), 1200, 800, new Color(200, 120, 160)));
        g.fillRect(0, 0, 1200, 800);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return new MockMultipartFile("arquivos", nome, "image/jpeg", out.toByteArray());
    }
}
