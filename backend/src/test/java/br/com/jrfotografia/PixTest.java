package br.com.jrfotografia;

import br.com.jrfotografia.servico.EmailServico;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Pix automático pelo PagBank, contra um PagBank de mentira (servidor HTTP local que imita a API de Pedidos). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PixTest {

    static final String TOKEN = "token-de-teste-pagbank";
    static final ObjectMapper JSON = new ObjectMapper();
    /** Pedidos do PagBank falso: id → [valor em centavos, pago?]. */
    static final Map<String, long[]> PEDIDOS = new ConcurrentHashMap<>();
    static final Map<String, JsonNode> CORPOS = new ConcurrentHashMap<>();
    static final AtomicInteger SEQ = new AtomicInteger();
    static HttpServer falso;

    @DynamicPropertySource
    static void pagbank(DynamicPropertyRegistry r) throws IOException {
        falso = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        falso.createContext("/orders", troca -> {
            String auth = troca.getRequestHeaders().getFirst("Authorization");
            String resposta;
            int status = 200;
            if (!("Bearer " + TOKEN).equals(auth)) {
                status = 401;
                resposta = "{\"error_messages\":[{\"code\":\"UNAUTHORIZED\",\"description\":\"Invalid credential\"}]}";
            } else if (troca.getRequestMethod().equals("POST")) {
                JsonNode corpo = JSON.readTree(troca.getRequestBody());
                long valor = corpo.path("qr_codes").path(0).path("amount").path("value").asLong();
                String id = "ORDE_TESTE_" + SEQ.incrementAndGet();
                PEDIDOS.put(id, new long[]{valor, 0});
                CORPOS.put(id, corpo);
                status = 201;
                resposta = "{\"id\":\"" + id + "\",\"qr_codes\":[{\"id\":\"QRCO_1\",\"text\":\"00020101021226830014br.gov.bcb.pix" + id + "5204000053039865802BR\","
                        + "\"amount\":{\"value\":" + valor + "}}],\"charges\":[]}";
            } else {
                String id = troca.getRequestURI().getPath().substring("/orders/".length());
                long[] p = PEDIDOS.get(id);
                if (p == null) {
                    status = 404;
                    resposta = "{\"error_messages\":[{\"code\":\"40400\",\"description\":\"not found\"}]}";
                } else {
                    resposta = pedidoJson(id, p);
                }
            }
            byte[] b = resposta.getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json");
            troca.sendResponseHeaders(status, b.length);
            troca.getResponseBody().write(b);
            troca.close();
        });
        falso.start();
        r.add("app.pagbank.url", () -> "http://127.0.0.1:" + falso.getAddress().getPort());
    }

    static String pedidoJson(String id, long[] p) {
        return "{\"id\":\"" + id + "\",\"charges\":" + (p[1] == 1
                ? "[{\"id\":\"CHAR_1\",\"status\":\"PAID\",\"amount\":{\"value\":" + p[0] + ",\"summary\":{\"total\":" + p[0] + ",\"paid\":" + p[0] + "}},\"payment_method\":{\"type\":\"PIX\"}}]"
                : "[]") + "}";
    }

    @AfterAll
    static void parar() {
        if (falso != null) falso.stop(0);
    }

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
        mvc.perform(auth(put("/api/estudio/pagbank")).content("{\"ativo\":false,\"sandbox\":true}"));
        mvc.perform(auth(put("/api/estudio/email")).content("{\"ativo\":false,\"avisos\":true,\"lembretes\":true}"));
        // Volta ao estado inicial (os outros testes contam com o estúdio sem SMTP nem PagBank)
        estudios.findAll().forEach(e -> { e.setSmtpSenha(null); e.setPagbankToken(null); estudios.save(e); });
    }

    @Test
    void sinalEParcelaPagosPeloPix() throws Exception {
        // Token errado não é guardado; sem token não liga
        mvc.perform(auth(put("/api/estudio/pagbank")).content("{\"ativo\":true,\"token\":\"errado\",\"sandbox\":true}"))
                .andExpect(status().isUnprocessableEntity());
        JsonNode cfg = ler(mvc.perform(auth(put("/api/estudio/pagbank")).content("{\"ativo\":true,\"token\":\"" + TOKEN + "\",\"sandbox\":true}"))
                .andExpect(status().isOk()).andReturn());
        assertThat(cfg.get("temToken").asBoolean()).isTrue();
        assertThat(cfg.toString()).doesNotContain(TOKEN);
        assertThat(ler(mvc.perform(auth(post("/api/estudio/pagbank/teste"))).andReturn()).get("ok").asBoolean()).isTrue();
        mvc.perform(auth(put("/api/estudio/email")).content("""
                {"ativo":true,"host":"smtp.teste.local","porta":587,"usuario":"jr@teste.local","senha":"x","seguranca":"STARTTLS","avisos":true,"lembretes":true}"""))
                .andExpect(status().isOk());
        JsonNode est = ler(mvc.perform(auth(get("/api/estudio"))).andReturn());
        ((com.fasterxml.jackson.databind.node.ObjectNode) est).put("email", "estudio@teste.local");
        mvc.perform(auth(put("/api/estudio")).content(est.toString())).andExpect(status().isOk());

        // Pré-reserva de mini-sessão
        LocalDate dia = LocalDate.now().plusDays(17);
        String slug = ler(mvc.perform(auth(post("/api/campanhas")).content("""
                {"titulo":"Mini Pix","preco":200,"duracaoMin":30,"intervaloMin":0,
                 "dias":[{"data":"%s","inicio":"06:00","fim":"08:00"}]}""".formatted(dia))).andReturn()).get("slug").asText();
        JsonNode r = ler(mvc.perform(post("/api/publico/campanhas/" + slug + "/reservas").header("X-Real-IP", "10.88.0.1")
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"inicio":"%sT06:00:00","nome":"Paula Pix","telefone":"98987650000","email":"paula@teste.local","consentimento":true}"""
                        .formatted(dia))).andExpect(status().isCreated()).andReturn());
        String token = r.get("token").asText();
        assertThat(r.get("pixAutomatico").asBoolean()).isTrue();
        assertThat(email.caixa().stream().filter(m -> m.tipo().equals("PRE_RESERVA")).findFirst().orElseThrow().html()).contains("Pagar o sinal no Pix");
        long sinalCentavos = r.get("valorSinal").decimalValue().movePointRight(2).longValueExact();

        // CPF inválido não gera; válido gera o QR Code com o valor exato do sinal
        mvc.perform(publico(post("/api/publico/reservas/" + token + "/pix")).content("{\"cpf\":\"111.111.111-11\"}"))
                .andExpect(status().isUnprocessableEntity());
        JsonNode comPix = ler(mvc.perform(publico(post("/api/publico/reservas/" + token + "/pix")).content("{\"cpf\":\"529.982.247-25\"}"))
                .andExpect(status().isOk()).andReturn());
        JsonNode qr = comPix.get("pix");
        assertThat(qr.get("status").asText()).isEqualTo("AGUARDANDO");
        assertThat(qr.get("texto").asText()).startsWith("000201");
        String pedido = PEDIDOS.keySet().stream().filter(k -> qr.get("texto").asText().contains(k)).findFirst().orElseThrow();
        assertThat(PEDIDOS.get(pedido)[0]).isEqualTo(sinalCentavos);
        JsonNode enviado = CORPOS.get(pedido);
        assertThat(enviado.path("customer").path("tax_id").asText()).isEqualTo("52998224725");
        assertThat(enviado.path("notification_urls").isMissingNode()).as("sem https não manda notification_urls").isTrue();
        // Gerar de novo devolve o mesmo QR Code
        JsonNode deNovo = ler(mvc.perform(publico(post("/api/publico/reservas/" + token + "/pix")).content("{\"cpf\":\"52998224725\"}")).andReturn());
        assertThat(deNovo.get("pix").get("id").asLong()).isEqualTo(qr.get("id").asLong());
        assertThat(ler(mvc.perform(get("/api/publico/reservas/" + token + "/pix")).andReturn()).get("status").asText()).isEqualTo("PENDENTE");

        // Notificação falsa (assinatura errada) é recusada e não confirma nada
        PEDIDOS.get(pedido)[1] = 1;
        String corpo = pedidoJson(pedido, PEDIDOS.get(pedido));
        mvc.perform(post("/api/publico/pagbank/notificacao").contentType(MediaType.APPLICATION_JSON).content(corpo)
                .header("x-authenticity-token", "0".repeat(64))).andExpect(status().isUnauthorized());
        assertThat(ler(mvc.perform(get("/api/publico/reservas/" + token)).andReturn()).get("status").asText()).isEqualTo("PENDENTE");

        // Notificação verdadeira: confirma a reserva sozinha, sinal pago, e-mails para a cliente e para o estúdio
        mvc.perform(post("/api/publico/pagbank/notificacao").contentType(MediaType.APPLICATION_JSON).content(corpo)
                .header("x-authenticity-token", sha(TOKEN + "-" + corpo))).andExpect(status().isOk());
        JsonNode conf = ler(mvc.perform(get("/api/publico/reservas/" + token)).andReturn());
        assertThat(conf.get("status").asText()).isEqualTo("CONFIRMADA");
        assertThat(conf.get("pix").get("status").asText()).isEqualTo("PAGA");
        assertThat(conf.get("areaCliente").asText()).isNotBlank();
        assertThat(email.caixa().stream().map(EmailServico.Mensagem::tipo)).contains("RESERVA_CONFIRMADA", "AVISO_PIX");
        // Repetida, não faz nada duas vezes
        mvc.perform(post("/api/publico/pagbank/notificacao").contentType(MediaType.APPLICATION_JSON).content(corpo)
                .header("x-authenticity-token", sha(TOKEN + "-" + corpo))).andExpect(status().isOk());
        assertThat(email.caixa().stream().filter(m -> m.tipo().equals("AVISO_PIX")).count()).isEqualTo(1);

        // Área da cliente: sinal pago; o restante pode ser pago por Pix (CPF obrigatório na primeira vez)
        String area = conf.get("areaCliente").asText();
        JsonNode a = ler(mvc.perform(get("/api/publico/cliente/" + area)).andReturn());
        assertThat(a.get("pixAutomatico").asBoolean()).isTrue();
        assertThat(a.get("cpfSalvo").asBoolean()).isFalse();
        JsonNode restante = null;
        for (JsonNode p : a.get("parcelas")) {
            if (p.get("paga").asBoolean()) assertThat(p.get("valor").decimalValue().movePointRight(2).longValueExact()).isEqualTo(sinalCentavos);
            else restante = p;
        }
        assertThat(restante).isNotNull();
        String rotaParcela = "/api/publico/cliente/" + area + "/parcelas/" + restante.get("id").asLong() + "/pix";
        mvc.perform(publico(post(rotaParcela)).content("{}")).andExpect(status().isUnprocessableEntity());
        JsonNode pp = ler(mvc.perform(publico(post(rotaParcela)).content("{\"cpf\":\"52998224725\"}")).andExpect(status().isOk()).andReturn());
        assertThat(ler(mvc.perform(get("/api/publico/cliente/" + area)).andReturn()).get("cpfSalvo").asBoolean()).isTrue();
        String pedido2 = PEDIDOS.keySet().stream().filter(k -> pp.get("texto").asText().contains(k)).findFirst().orElseThrow();
        assertThat(ler(mvc.perform(get(rotaParcela)).andReturn()).get("status").asText()).isEqualTo("AGUARDANDO");
        // Sem notificação (computador de casa): a própria página descobre que foi pago
        PEDIDOS.get(pedido2)[1] = 1;
        Thread.sleep(8200);
        assertThat(ler(mvc.perform(get(rotaParcela)).andReturn()).get("status").asText()).isEqualTo("PAGA");
        JsonNode depois = ler(mvc.perform(get("/api/publico/cliente/" + area)).andReturn());
        for (JsonNode p : depois.get("parcelas")) assertThat(p.get("paga").asBoolean()).isTrue();

        // Painel: histórico com os dois Pix pagos
        JsonNode lista = ler(mvc.perform(auth(get("/api/estudio/pagbank/cobrancas"))).andReturn());
        assertThat(lista.toString()).contains(pedido).contains(pedido2);
        int pagas = 0;
        for (JsonNode c : lista) if (c.get("status").asText().equals("PAGA") && (c.get("pedidoId").asText().equals(pedido) || c.get("pedidoId").asText().equals(pedido2))) pagas++;
        assertThat(pagas).isEqualTo(2);
    }

    static String sha(String s) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
    }

    private MockHttpServletRequestBuilder publico(MockHttpServletRequestBuilder b) {
        return b.header("X-Real-IP", "10.88.0.2").contentType(MediaType.APPLICATION_JSON);
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b) {
        return b.header("Authorization", "Bearer " + painel).contentType(MediaType.APPLICATION_JSON);
    }

    private JsonNode ler(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsByteArray());
    }
}
