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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Regra do estúdio: o cliente nunca baixa a original; na seleção e até o download ser liberado
 * tudo tem marca d'água; liberado o download, a galeria mostra a editada sem marca.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MarcaDaguaTest {

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
    void comSelecaoOriginalNuncaSaiEMarcaSoSaiAoLiberar() throws Exception {
        long cliente = criarCliente("Cliente Marca");
        JsonNode t = ler(mvc.perform(post("/api/trabalhos").header("Authorization", "Bearer " + painel).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteId\":%d,\"pacoteId\":%d,\"formaPagamento\":\"AVISTA\",\"primeiroVencimento\":\"%s\"}"
                                .formatted(cliente, pacote("Sessão 10"), LocalDate.now())))
                .andExpect(status().isCreated()).andReturn());
        long id = t.get("resumo").get("id").asLong();
        String token = t.get("galeriaToken").asText();

        // Originais: fundo preto. A marca d'água (branca) é o único ponto claro da prévia.
        mvc.perform(multipart("/api/trabalhos/" + id + "/fotos").file(imagem("IMG_1.jpg", Color.BLACK)).file(imagem("IMG_2.jpg", Color.BLACK))
                .header("Authorization", "Bearer " + painel)).andExpect(status().isCreated());
        mudarStatus(id, "SELECAO");
        String senha = ler(mvc.perform(post("/api/trabalhos/" + id + "/senha-galeria").header("Authorization", "Bearer " + painel))
                .andReturn()).get("senha").asText();
        String acesso = ler(mvc.perform(post("/api/galeria/" + token + "/acesso").contentType(MediaType.APPLICATION_JSON)
                .content("{\"senha\":\"" + senha + "\"}")).andReturn()).get("acesso").asText();
        JsonNode g = ler(mvc.perform(get("/api/galeria/" + token).header("Authorization", "Bearer " + acesso)).andReturn());
        long foto = g.get("fotos").get(0).get("id").asLong();

        // Na seleção: prévia e miniatura com marca; download bloqueado.
        assertThat(temMarca(imagemCliente(token, foto, "previa", acesso))).isTrue();
        assertThat(temMarca(imagemCliente(token, foto, "miniatura", acesso))).isTrue();
        mvc.perform(get("/api/galeria/" + token + "/fotos/" + foto + "/download").param("t", acesso)).andExpect(status().isForbidden());

        mvc.perform(put("/api/galeria/" + token + "/fotos/" + foto + "/selecao").header("Authorization", "Bearer " + acesso)
                .contentType(MediaType.APPLICATION_JSON).content("{\"selecionada\":true}")).andExpect(status().isOk());
        mvc.perform(post("/api/galeria/" + token + "/fechar-selecao").header("Authorization", "Bearer " + acesso)).andExpect(status().isOk());

        // Sem a editada, não entrega (a original não pode ir para o cliente).
        mvc.perform(patch("/api/trabalhos/" + id + "/status").header("Authorization", "Bearer " + painel)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ENTREGUE\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.erro").value(org.hamcrest.Matchers.containsString("versão editada")));

        // Editada: fundo cinza-escuro, para distinguir da original preta.
        mvc.perform(multipart("/api/trabalhos/" + id + "/editadas").file(imagem("IMG_1.jpg", new Color(60, 60, 60)))
                .header("Authorization", "Bearer " + painel)).andExpect(status().isOk());
        mudarStatus(id, "ENTREGUE");

        // Entregue mas não pago: ainda com marca e sem download.
        assertThat(temMarca(imagemCliente(token, foto, "previa", acesso))).isTrue();
        mvc.perform(get("/api/galeria/" + token + "/download.zip").param("t", acesso)).andExpect(status().isForbidden());

        // Pago: a marca sai e o download entrega a EDITADA (cinza), nunca a original (preta).
        long parcela = ler(mvc.perform(get("/api/trabalhos/" + id).header("Authorization", "Bearer " + painel)).andReturn())
                .get("parcelas").get(0).get("id").asLong();
        mvc.perform(post("/api/parcelas/" + parcela + "/pagamento").header("Authorization", "Bearer " + painel)).andExpect(status().isOk());
        byte[] previaLiberada = imagemCliente(token, foto, "previa", acesso);
        assertThat(temMarca(previaLiberada)).isFalse();
        assertThat(temMarca(imagemCliente(token, foto, "miniatura", acesso))).isFalse();
        assertThat(cinzaMedio(previaLiberada)).isBetween(45, 75);
        byte[] baixada = mvc.perform(get("/api/galeria/" + token + "/fotos/" + foto + "/download").param("t", acesso))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(cinzaMedio(baixada)).isBetween(45, 75);
    }

    @Test
    void pacoteSemSelecaoEntregaAsFotosEnviadasComoEditadas() throws Exception {
        long cliente = criarCliente("Cliente Evento");
        JsonNode t = ler(mvc.perform(post("/api/trabalhos").header("Authorization", "Bearer " + painel).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteId\":%d,\"pacoteId\":%d,\"formaPagamento\":\"AVISTA\"}".formatted(cliente, pacote("Eventos"))))
                .andExpect(status().isCreated()).andReturn());
        long id = t.get("resumo").get("id").asLong();
        String token = t.get("galeriaToken").asText();
        mvc.perform(multipart("/api/trabalhos/" + id + "/fotos").file(imagem("EVT_1.jpg", new Color(60, 60, 60)))
                .header("Authorization", "Bearer " + painel)).andExpect(status().isCreated());
        mudarStatus(id, "ENTREGUE");
        String senha = ler(mvc.perform(post("/api/trabalhos/" + id + "/senha-galeria").header("Authorization", "Bearer " + painel))
                .andReturn()).get("senha").asText();
        String acesso = ler(mvc.perform(post("/api/galeria/" + token + "/acesso").contentType(MediaType.APPLICATION_JSON)
                .content("{\"senha\":\"" + senha + "\"}")).andReturn()).get("acesso").asText();
        long foto = ler(mvc.perform(get("/api/galeria/" + token).header("Authorization", "Bearer " + acesso)).andReturn())
                .get("fotos").get(0).get("id").asLong();
        assertThat(temMarca(imagemCliente(token, foto, "previa", acesso))).isTrue();

        long parcela = ler(mvc.perform(get("/api/trabalhos/" + id).header("Authorization", "Bearer " + painel)).andReturn())
                .get("parcelas").get(0).get("id").asLong();
        mvc.perform(post("/api/parcelas/" + parcela + "/pagamento").header("Authorization", "Bearer " + painel)).andExpect(status().isOk());
        assertThat(temMarca(imagemCliente(token, foto, "previa", acesso))).isFalse();
        mvc.perform(get("/api/galeria/" + token + "/fotos/" + foto + "/download").param("t", acesso)).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ apoio
    private byte[] imagemCliente(String token, long foto, String tipo, String acesso) throws Exception {
        return mvc.perform(get("/api/galeria/" + token + "/fotos/" + foto + "/" + tipo).param("t", acesso))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }

    /** A marca é branca a 28%: sobre fundo uniforme escuro, aparecem pixels bem mais claros que a média. */
    private static boolean temMarca(byte[] jpeg) throws Exception {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(jpeg));
        int media = cinzaMedio(jpeg), claros = 0;
        for (int y = 0; y < img.getHeight(); y += 2)
            for (int x = 0; x < img.getWidth(); x += 2)
                if (cinza(img.getRGB(x, y)) > media + 40) claros++;
        return claros > 50;
    }

    private static int cinzaMedio(byte[] jpeg) throws Exception {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(jpeg));
        long soma = 0, n = 0;
        for (int y = 0; y < img.getHeight(); y += 4)
            for (int x = 0; x < img.getWidth(); x += 4) { soma += cinza(img.getRGB(x, y)); n++; }
        return (int) (soma / n);
    }

    private static int cinza(int rgb) {
        return (((rgb >> 16) & 255) + ((rgb >> 8) & 255) + (rgb & 255)) / 3;
    }

    private static MockMultipartFile imagem(String nome, Color cor) throws Exception {
        BufferedImage img = new BufferedImage(1200, 800, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(cor);
        g.fillRect(0, 0, 1200, 800);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return new MockMultipartFile("arquivos", nome, "image/jpeg", out.toByteArray());
    }

    private void mudarStatus(long id, String s) throws Exception {
        mvc.perform(patch("/api/trabalhos/" + id + "/status").header("Authorization", "Bearer " + painel)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + s + "\"}")).andExpect(status().isOk());
    }

    private long criarCliente(String nome) throws Exception {
        return ler(mvc.perform(post("/api/clientes").header("Authorization", "Bearer " + painel)
                .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"" + nome + "\"}")).andReturn()).get("id").asLong();
    }

    private long pacote(String nome) throws Exception {
        for (JsonNode p : ler(mvc.perform(get("/api/pacotes").header("Authorization", "Bearer " + painel)).andReturn())) {
            if (p.get("nome").asText().equals(nome)) return p.get("id").asLong();
        }
        throw new AssertionError(nome);
    }

    private JsonNode ler(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsByteArray());
    }
}
