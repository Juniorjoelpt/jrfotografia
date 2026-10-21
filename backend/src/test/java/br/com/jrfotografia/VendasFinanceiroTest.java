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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Fase 2: orçamento → aceite pelo link → trabalho com parcelas; agenda; PDFs; despesas e fluxo de caixa. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VendasFinanceiroTest {

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
    void orcamentoAceitoViraTrabalhoComParcelas() throws Exception {
        long pacoteId = pacotePorNome("Sessão 20");
        LocalDateTime data = LocalDateTime.now().plusDays(20).withHour(15).withMinute(0).withSecond(0).withNano(0);

        // Pedido vindo do site, para medir a conversão.
        mvc.perform(post("/api/publico/orcamento").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Ana Lima\",\"telefone\":\"98988887777\",\"tipoTrabalho\":\"Gestante\",\"origem\":\"INDICACAO\",\"consentimento\":true}"))
                .andExpect(status().isCreated());
        long pedidoId = pedidoDe("Ana Lima").get("id").asLong();

        String corpo = """
                {"pedidoId": %d, "nomeCliente": "Ana Lima", "telefone": "(98) 98888-7777", "pacoteId": %d,
                 "tipo": "Gestante", "dataPrevista": "%s", "localPrevisto": "Estúdio",
                 "itens": [{"descricao": "Foto extra", "quantidade": 2, "valorUnit": 35}],
                 "desconto": 20, "formaPagamento": "PARCELADO", "valorSinal": 100, "numParcelas": 3}
                """.formatted(pedidoId, pacoteId, data);
        JsonNode orc = ler(mvc.perform(auth(post("/api/orcamentos")).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated()).andReturn());
        assertThat(orc.get("itens")).hasSize(2);
        assertThat(orc.get("status").asText()).isEqualTo("RASCUNHO");
        // 350 (Sessão 20) + 70 − 20
        assertThat(orc.get("valorTotal").decimalValue()).isEqualByComparingTo("400.00");
        long orcId = orc.get("id").asLong();
        String token = orc.get("token").asText();

        mvc.perform(auth(post("/api/orcamentos/" + orcId + "/enviado"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENVIADO"));

        // PDF no painel e no link público.
        assertPdf(mvc.perform(auth(get("/api/orcamentos/" + orcId + "/pdf"))).andReturn());
        assertPdf(mvc.perform(get("/api/publico/orcamentos/" + token + "/pdf")).andReturn());

        // O cliente abre o link, vê os valores e aceita.
        mvc.perform(get("/api/publico/orcamentos/" + token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeCliente").value("Ana Lima"))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.contratoDisponivel").value(false));
        mvc.perform(get("/api/publico/orcamentos/" + token + "/contrato.pdf")).andExpect(status().isUnprocessableEntity());
        JsonNode aceito = ler(mvc.perform(post("/api/publico/orcamentos/" + token + "/aceite")).andExpect(status().isOk()).andReturn());
        assertThat(aceito.get("status").asText()).isEqualTo("ACEITO");
        assertThat(aceito.get("contratoDisponivel").asBoolean()).isTrue();
        assertPdf(mvc.perform(get("/api/publico/orcamentos/" + token + "/contrato.pdf")).andReturn());

        // Aceitar duas vezes não cria outro trabalho; recusar depois do aceite não pode.
        mvc.perform(post("/api/publico/orcamentos/" + token + "/aceite")).andExpect(status().isOk());
        mvc.perform(post("/api/publico/orcamentos/" + token + "/recusa")).andExpect(status().isUnprocessableEntity());

        long trabalhoId = ler(mvc.perform(auth(get("/api/orcamentos/" + orcId))).andReturn()).get("trabalhoId").asLong();
        JsonNode trabalho = ler(mvc.perform(auth(get("/api/trabalhos/" + trabalhoId))).andExpect(status().isOk()).andReturn());
        assertThat(trabalho.get("resumo").get("valorTotal").decimalValue()).isEqualByComparingTo("400.00");
        assertThat(trabalho.get("parcelas")).hasSize(4); // sinal + 3
        // Sinal hoje; a 1ª parcela fica para o mês seguinte.
        assertThat(trabalho.get("parcelas").get(0).get("vencimento").asText()).isEqualTo(LocalDate.now().toString());
        assertThat(trabalho.get("parcelas").get(1).get("vencimento").asText()).isEqualTo(LocalDate.now().plusMonths(1).toString());
        assertThat(trabalho.get("orcamentoId").asLong()).isEqualTo(orcId);
        assertThat(trabalho.get("cliente").get("nome").asText()).isEqualTo("Ana Lima");
        assertThat(trabalho.get("cliente").get("origem").asText()).isEqualTo("INDICACAO");

        JsonNode pedido = pedidoDe("Ana Lima");
        assertThat(pedido.get("status").asText()).isEqualTo("FECHADO");

        // Contrato pelo painel e recibo do sinal depois de pago.
        assertPdf(mvc.perform(auth(get("/api/trabalhos/" + trabalhoId + "/contrato.pdf"))).andReturn());
        long sinalId = trabalho.get("parcelas").get(0).get("id").asLong();
        mvc.perform(auth(get("/api/parcelas/" + sinalId + "/recibo.pdf"))).andExpect(status().isUnprocessableEntity());
        mvc.perform(auth(post("/api/parcelas/" + sinalId + "/pagamento"))).andExpect(status().isOk());
        assertPdf(mvc.perform(auth(get("/api/parcelas/" + sinalId + "/recibo.pdf"))).andReturn());

        // Agenda mostra o ensaio e avisa conflito no mesmo horário.
        JsonNode eventos = ler(mvc.perform(auth(get("/api/agenda")
                .param("de", data.toLocalDate().toString()).param("ate", data.toLocalDate().toString()))).andReturn());
        assertThat(eventos.findValuesAsText("titulo")).contains("Ana Lima");
        JsonNode conflitos = ler(mvc.perform(auth(get("/api/agenda/conflitos")
                .param("inicio", data.plusHours(1).toString()).param("duracaoMin", "60"))).andReturn());
        assertThat(conflitos.get("avisos")).hasSize(1);

        // Bloqueio também conta como conflito.
        LocalDateTime outro = data.plusDays(3);
        long bloqueioId = ler(mvc.perform(auth(post("/api/agenda/bloqueios")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inicio\":\"%s\",\"fim\":\"%s\",\"motivo\":\"Viagem\"}".formatted(outro, outro.plusHours(8))))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
        assertThat(ler(mvc.perform(auth(get("/api/agenda/conflitos").param("inicio", outro.plusHours(2).toString()))).andReturn())
                .get("avisos").get(0).asText()).contains("Viagem");
        mvc.perform(auth(delete("/api/agenda/bloqueios/" + bloqueioId))).andExpect(status().isNoContent());

        // Orçamento aceito não pode mais ser alterado.
        mvc.perform(auth(put("/api/orcamentos/" + orcId)).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isUnprocessableEntity());

        // Relatório conta o orçamento aceito e o pedido fechado.
        JsonNode rel = ler(mvc.perform(auth(get("/api/relatorios").param("ano", String.valueOf(data.getYear())))).andReturn());
        assertThat(rel.get("orcamentosAceitos").asLong()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void despesasEntramNoFluxoDeCaixa() throws Exception {
        int ano = 2031; // ano isolado, sem dados de outros testes
        mvc.perform(auth(post("/api/despesas")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"descricao\":\"Aluguel do estúdio\",\"categoria\":\"Aluguel\",\"valor\":500,\"vencimento\":\"2031-03-10\",\"pagoEm\":\"2031-03-09\"}"))
                .andExpect(status().isCreated());
        long luz = ler(mvc.perform(auth(post("/api/despesas")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descricao\":\"Energia\",\"categoria\":\"Contas\",\"valor\":120.50,\"vencimento\":\"2031-03-20\"}"))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();

        JsonNode fluxo = ler(mvc.perform(auth(get("/api/financeiro/fluxo").param("ano", String.valueOf(ano)))).andReturn());
        JsonNode marco = fluxo.get("meses").get(2);
        assertThat(marco.get("saiu").decimalValue()).isEqualByComparingTo("500");
        assertThat(marco.get("aSair").decimalValue()).isEqualByComparingTo("120.50");
        assertThat(fluxo.get("saldoAno").decimalValue()).isEqualByComparingTo("-500");

        mvc.perform(auth(post("/api/despesas/" + luz + "/pagamento"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.pagoEm").value(LocalDate.now().toString()));
        mvc.perform(auth(delete("/api/despesas/" + luz))).andExpect(status().isNoContent());

        JsonNode rel = ler(mvc.perform(auth(get("/api/relatorios").param("ano", String.valueOf(ano)))).andReturn());
        assertThat(rel.get("despesasPorCategoria").get(0).get("nome").asText()).isEqualTo("Aluguel");
    }

    @Test
    void modeloDeContratoEditavelEItensAvulsos() throws Exception {
        JsonNode modelo = ler(mvc.perform(auth(get("/api/estudio/contrato"))).andExpect(status().isOk()).andReturn());
        assertThat(modelo.get("padrao").asBoolean()).isTrue();
        assertThat(modelo.get("texto").asText()).contains("{CONTRATANTE}");

        mvc.perform(auth(put("/api/estudio/contrato")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"Contrato simples de {CONTRATANTE}.\",\"responsavel\":\"Fulano\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.padrao").value(false));
        mvc.perform(auth(put("/api/estudio/contrato")).contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.padrao").value(true));

        JsonNode itens = ler(mvc.perform(auth(get("/api/itens-avulsos"))).andReturn());
        assertThat(itens.findValuesAsText("nome")).contains("Foto extra");
        mvc.perform(auth(post("/api/itens-avulsos")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Álbum 20x30\",\"preco\":250}")).andExpect(status().isCreated());
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b) {
        return b.header("Authorization", "Bearer " + painel);
    }

    private void assertPdf(MvcResult r) throws Exception {
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(r.getResponse().getContentType()).isEqualTo("application/pdf");
        byte[] b = r.getResponse().getContentAsByteArray();
        assertThat(new String(b, 0, 5)).isEqualTo("%PDF-");
        // Guarda uma cópia para conferência visual (target/pdfs).
        var pasta = java.nio.file.Path.of("target", "pdfs");
        java.nio.file.Files.createDirectories(pasta);
        String nome = r.getResponse().getHeader("Content-Disposition").replaceAll(".*filename=\"?([^\";]+).*", "$1");
        java.nio.file.Files.write(pasta.resolve(nome), b);
    }

    private long pacotePorNome(String nome) throws Exception {
        for (JsonNode p : ler(mvc.perform(auth(get("/api/pacotes"))).andReturn())) {
            if (p.get("nome").asText().equals(nome)) return p.get("id").asLong();
        }
        throw new AssertionError("Pacote não encontrado: " + nome);
    }

    private JsonNode pedidoDe(String nome) throws Exception {
        for (JsonNode p : ler(mvc.perform(auth(get("/api/pedidos"))).andReturn())) {
            if (p.get("nome").asText().equals(nome)) return p;
        }
        throw new AssertionError("Pedido não encontrado: " + nome);
    }

    private JsonNode ler(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsByteArray());
    }
}
