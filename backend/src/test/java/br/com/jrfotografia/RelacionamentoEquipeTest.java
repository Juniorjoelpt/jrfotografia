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

/** Fase 3 · bloco 3: aniversários e datas, inativas, freelancers e comissões, tarefas e pesquisa de satisfação. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RelacionamentoEquipeTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    String painel;

    @BeforeEach
    void entrar() throws Exception {
        painel = ler(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@teste.local\",\"senha\":\"senha-de-teste-123\"}")).andReturn()).get("token").asText();
    }

    @Test
    void aniversariosDatasEspeciaisEInativas() throws Exception {
        LocalDate hoje = LocalDate.now();
        LocalDate nasc = hoje.plusDays(5).minusYears(30);
        long ana = id(mvc.perform(auth(post("/api/clientes")).content(
                "{\"nome\":\"Ana Aniversario\",\"telefone\":\"98911112222\",\"dataNascimento\":\"" + nasc + "\"}")).andReturn());
        long bia = id(mvc.perform(auth(post("/api/clientes")).content("{\"nome\":\"Bia Datas\"}")).andReturn());
        LocalDate casamento = hoje.plusDays(10);
        mvc.perform(auth(post("/api/clientes/" + bia + "/datas")).content(
                "{\"descricao\":\"Bodas\",\"dia\":%d,\"mes\":%d,\"ano\":%d}".formatted(casamento.getDayOfMonth(), casamento.getMonthValue(), casamento.getYear() - 3)))
                .andExpect(status().isOk());
        mvc.perform(auth(post("/api/clientes/" + bia + "/datas")).content("{\"descricao\":\"X\",\"dia\":31,\"mes\":2}")).andExpect(status().isUnprocessableEntity());

        JsonNode datas = ler(mvc.perform(auth(get("/api/relacionamento/datas?dias=15"))).andReturn());
        assertThat(texto(datas)).contains("Ana Aniversario|ANIVERSARIO|5|30", "Bia Datas|DATA_ESPECIAL|10|3");
        assertThat(texto(ler(mvc.perform(auth(get("/api/relacionamento/datas?dias=7"))).andReturn()))).doesNotContain("Bia Datas");

        // Inativa: último ensaio há 14 meses; quem fotografou mês passado não aparece.
        trabalho(ana, hoje.minusMonths(14));
        long recente = id(mvc.perform(auth(post("/api/clientes")).content("{\"nome\":\"Carla Recente\"}")).andReturn());
        trabalho(recente, hoje.minusMonths(1));
        JsonNode inativas = ler(mvc.perform(auth(get("/api/relacionamento/inativos"))).andReturn());
        String nomes = inativas.toString();
        assertThat(nomes).contains("Ana Aniversario").doesNotContain("Carla Recente");
        mvc.perform(auth(post("/api/clientes/" + ana + "/contato"))).andExpect(status().isNoContent());
        for (JsonNode i : ler(mvc.perform(auth(get("/api/relacionamento/inativos"))).andReturn())) {
            if (i.get("clienteId").asLong() == ana) assertThat(i.get("ultimoContato").isNull()).isFalse();
        }
    }

    @Test
    void freelancerComissaoTarefasEPesquisa() throws Exception {
        long cli = id(mvc.perform(auth(post("/api/clientes")).content("{\"nome\":\"Dora Evento\"}")).andReturn());
        long eventos = pacote("Eventos"); // R$ 800, todas as fotos (sem seleção)
        JsonNode t = ler(mvc.perform(auth(post("/api/trabalhos")).content(
                "{\"clienteId\":%d,\"pacoteId\":%d,\"formaPagamento\":\"AVISTA\",\"primeiroVencimento\":\"%s\"}".formatted(cli, eventos, LocalDate.now())))
                .andExpect(status().isCreated()).andReturn());
        long trab = t.get("resumo").get("id").asLong();

        long free = id(mvc.perform(auth(post("/api/freelancers")).content(
                "{\"nome\":\"Paulo Segundo\",\"telefone\":\"(98) 99999-0000\",\"funcao\":\"Segundo fotógrafo\",\"chavePix\":\"paulo@pix\"}")).andReturn());
        mvc.perform(auth(post("/api/trabalhos/" + trab + "/freelancers")).content("{\"freelancerId\":" + free + "}")).andExpect(status().isUnprocessableEntity());
        JsonNode v = ler(mvc.perform(auth(post("/api/trabalhos/" + trab + "/freelancers")).content(
                "{\"freelancerId\":" + free + ",\"percentual\":10}")).andExpect(status().isOk()).andReturn());
        assertThat(v.get("valor").asDouble()).isEqualTo(80.0);
        assertThat(v.get("funcao").asText()).isEqualTo("Segundo fotógrafo");
        long vid = v.get("id").asLong();
        JsonNode pago = ler(mvc.perform(auth(post("/api/comissoes/" + vid + "/pagamento"))).andExpect(status().isOk()).andReturn());
        assertThat(pago.get("pagoEm").asText()).isEqualTo(LocalDate.now().toString());
        assertThat(ler(mvc.perform(auth(get("/api/despesas"))).andReturn()).toString()).contains("Paulo Segundo").contains("Freelancers");
        mvc.perform(auth(delete("/api/comissoes/" + vid))).andExpect(status().isUnprocessableEntity());
        JsonNode lista = ler(mvc.perform(auth(get("/api/freelancers"))).andReturn());
        for (JsonNode f : lista) if (f.get("id").asLong() == free) assertThat(f.get("pago").asDouble()).isEqualTo(80.0);
        mvc.perform(auth(delete("/api/comissoes/" + vid + "/pagamento"))).andExpect(status().isOk());
        assertThat(ler(mvc.perform(auth(get("/api/despesas"))).andReturn()).toString()).doesNotContain("Paulo Segundo");

        // Tarefa com responsável e prazo
        JsonNode tarefa = ler(mvc.perform(auth(post("/api/tarefas")).content(
                "{\"trabalhoId\":%d,\"titulo\":\"Tratar fotos do evento\",\"responsavelFreelancerId\":%d,\"prazo\":\"%s\"}"
                        .formatted(trab, free, LocalDate.now().minusDays(1)))).andExpect(status().isOk()).andReturn());
        assertThat(tarefa.get("responsavel").asText()).isEqualTo("Paulo Segundo");
        assertThat(tarefa.get("atrasada").asBoolean()).isTrue();
        mvc.perform(auth(patch("/api/tarefas/" + tarefa.get("id").asLong() + "/status")).content("{\"status\":\"FAZENDO\"}")).andExpect(status().isOk());

        // Entrega: tarefas abertas concluídas e pesquisa criada; galeria convida a responder.
        mvc.perform(multipart("/api/trabalhos/" + trab + "/fotos").file(imagem("EVT_1.jpg")).header("Authorization", "Bearer " + painel))
                .andExpect(status().isCreated());
        mvc.perform(auth(patch("/api/trabalhos/" + trab + "/status")).content("{\"status\":\"ENTREGUE\"}")).andExpect(status().isOk());
        JsonNode tarefas = ler(mvc.perform(auth(get("/api/tarefas?trabalho=" + trab))).andReturn());
        assertThat(tarefas.get(0).get("status").asText()).isEqualTo("FEITA");

        String token = ler(mvc.perform(auth(post("/api/trabalhos/" + trab + "/pesquisa"))).andReturn()).get("token").asText();
        String senha = ler(mvc.perform(auth(post("/api/trabalhos/" + trab + "/senha-galeria"))).andReturn()).get("senha").asText();
        String galeria = t.get("galeriaToken").asText();
        String acesso = ler(mvc.perform(post("/api/galeria/" + galeria + "/acesso").contentType(MediaType.APPLICATION_JSON)
                .content("{\"senha\":\"" + senha + "\"}")).andReturn()).get("acesso").asText();
        assertThat(ler(mvc.perform(get("/api/galeria/" + galeria).header("Authorization", "Bearer " + acesso)).andReturn())
                .get("pesquisaToken").asText()).isEqualTo(token);

        JsonNode pub = ler(mvc.perform(get("/api/publico/pesquisas/" + token)).andReturn());
        assertThat(pub.get("cliente").asText()).isEqualTo("Dora");
        mvc.perform(post("/api/publico/pesquisas/" + token).contentType(MediaType.APPLICATION_JSON).content("{\"nota\":11}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/publico/pesquisas/" + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nota\":10,\"atendimento\":5,\"fotos\":5,\"prazo\":4,\"comentario\":\"Amei!\",\"autorizaDepoimento\":true}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/publico/pesquisas/" + token).contentType(MediaType.APPLICATION_JSON).content("{\"nota\":3}"))
                .andExpect(status().isUnprocessableEntity());
        assertThat(ler(mvc.perform(get("/api/galeria/" + galeria).header("Authorization", "Bearer " + acesso)).andReturn())
                .get("pesquisaToken").isNull()).isTrue();
        JsonNode resumo = ler(mvc.perform(auth(get("/api/relacionamento/pesquisas"))).andReturn()).get("resumo");
        assertThat(resumo.get("respondidas").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(resumo.get("promotores").asLong()).isGreaterThanOrEqualTo(1);

        // Colaborador vê tarefas, mas não freelancers nem relacionamento
        mvc.perform(auth(post("/api/usuarios")).content("{\"nome\":\"Editora\",\"email\":\"editora@teste.local\",\"senha\":\"provisoria-9\"}"))
                .andExpect(status().isCreated());
        String colab = ler(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"editora@teste.local\",\"senha\":\"provisoria-9\"}")).andReturn()).get("token").asText();
        mvc.perform(get("/api/tarefas").header("Authorization", "Bearer " + colab)).andExpect(status().isOk());
        mvc.perform(get("/api/equipe/pessoas").header("Authorization", "Bearer " + colab)).andExpect(status().isOk());
        mvc.perform(get("/api/freelancers").header("Authorization", "Bearer " + colab)).andExpect(status().isForbidden());
        mvc.perform(get("/api/relacionamento/inativos").header("Authorization", "Bearer " + colab)).andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- apoio

    private String texto(JsonNode datas) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode d : datas) {
            sb.append(d.get("nome").asText()).append('|').append(d.get("tipo").asText()).append('|')
                    .append(d.get("diasFaltando").asLong()).append('|').append(d.get("anos").asText()).append('\n');
        }
        return sb.toString();
    }

    private void trabalho(long cliente, LocalDate data) throws Exception {
        mvc.perform(auth(post("/api/trabalhos")).content(
                "{\"clienteId\":%d,\"pacoteId\":%d,\"formaPagamento\":\"AVISTA\",\"primeiroVencimento\":\"%s\",\"dataTrabalho\":\"%sT10:00:00\"}"
                        .formatted(cliente, pacote("Sessão 10"), data, data))).andExpect(status().isCreated());
    }

    private long pacote(String nome) throws Exception {
        for (JsonNode p : ler(mvc.perform(auth(get("/api/pacotes"))).andReturn())) if (p.get("nome").asText().equals(nome)) return p.get("id").asLong();
        throw new AssertionError(nome);
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b) {
        return b.header("Authorization", "Bearer " + painel).contentType(MediaType.APPLICATION_JSON);
    }

    private static MockMultipartFile imagem(String nome) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(600, 400, BufferedImage.TYPE_INT_RGB), "jpg", out);
        return new MockMultipartFile("arquivos", nome, "image/jpeg", out.toByteArray());
    }

    private long id(MvcResult r) throws Exception {
        return ler(r).get("id").asLong();
    }

    private JsonNode ler(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsByteArray());
    }
}
