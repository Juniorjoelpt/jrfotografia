package br.com.jrfotografia.servico;

import br.com.jrfotografia.config.AppProps;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Conversa com a API de Pedidos do PagBank (antigo PagSeguro): cria um pedido com QR Code Pix e consulta se foi pago.
 * Documentação: developer.pagbank.com.br (Pedidos › QR Code; Notificações › Confirmar autenticidade).
 */
@Component
public class PagBankCliente {

    private static final Logger log = LoggerFactory.getLogger(PagBankCliente.class);
    static final String PRODUCAO = "https://api.pagseguro.com";
    static final String SANDBOX = "https://sandbox.api.pagseguro.com";

    private final AppProps props;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public PagBankCliente(AppProps props, ObjectMapper json) {
        this.props = props;
        this.json = json;
    }

    /** Erro devolvido pelo PagBank (ou falha de conexão), já com uma mensagem para mostrar. */
    public static class FalhaPagBank extends RuntimeException {
        private final int status;

        public FalhaPagBank(int status, String mensagem) {
            super(mensagem);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }

    /** Pedido criado: o id (ORDE_…) e o Pix copia e cola. */
    public record PedidoPix(String id, String texto) {}

    /** Situação do pedido: pago (alguma cobrança PAID) e quanto entrou. */
    public record Situacao(String id, boolean pago, BigDecimal valorPago) {}

    String base(boolean sandbox) {
        String url = props.pagbank() != null ? props.pagbank().url() : null;
        if (url != null && !url.isBlank()) return url.replaceAll("/+$", "");
        return sandbox ? SANDBOX : PRODUCAO;
    }

    /** POST /orders com qr_codes. {@code chave} evita pedido duplicado se a mesma chamada for repetida. */
    public PedidoPix criarPedido(String token, boolean sandbox, Map<String, Object> corpo, String chave) {
        JsonNode r = chamar(HttpRequest.newBuilder(URI.create(base(sandbox) + "/orders"))
                .header("x-idempotency-key", chave != null ? chave : UUID.randomUUID().toString())
                .POST(HttpRequest.BodyPublishers.ofString(escrever(corpo), StandardCharsets.UTF_8)), token);
        JsonNode qr = r.path("qr_codes").path(0);
        String texto = qr.path("text").asText(null);
        if (r.path("id").asText(null) == null || texto == null) throw new FalhaPagBank(502, "O PagBank não devolveu o QR Code. Tente de novo.");
        return new PedidoPix(r.get("id").asText(), texto);
    }

    /** GET /orders/{id}. */
    public Situacao consultar(String token, boolean sandbox, String pedidoId) {
        return situacao(chamar(HttpRequest.newBuilder(URI.create(base(sandbox) + "/orders/" + pedidoId)).GET(), token));
    }

    /** Lê o pedido (resposta da API ou corpo da notificação): pago se alguma cobrança está PAID. */
    public static Situacao situacao(JsonNode pedido) {
        BigDecimal pago = BigDecimal.ZERO;
        boolean algum = false;
        for (JsonNode c : pedido.path("charges")) {
            if ("PAID".equalsIgnoreCase(c.path("status").asText())) {
                algum = true;
                JsonNode a = c.path("amount");
                long centavos = a.path("summary").path("paid").asLong(a.path("value").asLong(0));
                pago = pago.add(BigDecimal.valueOf(centavos, 2));
            }
        }
        return new Situacao(pedido.path("id").asText(null), algum, pago);
    }

    /**
     * Confere o token: consulta um pedido que não existe. 401/403 = token recusado;
     * qualquer outra resposta (404, 400) = o PagBank aceitou o token.
     */
    public void testarToken(String token, boolean sandbox) {
        try {
            chamar(HttpRequest.newBuilder(URI.create(base(sandbox) + "/orders/ORDE_00000000-0000-0000-0000-000000000000")).GET(), token);
        } catch (FalhaPagBank ex) {
            if (ex.status() == 401 || ex.status() == 403) throw ex;
            if (ex.status() == 0 || ex.status() >= 500) throw ex;
        }
    }

    /** SHA-256 de "token-corpo" em hexadecimal: o x-authenticity-token que o PagBank manda na notificação. */
    public static boolean assinaturaConfere(String token, byte[] corpo, String assinatura) {
        if (token == null || assinatura == null || assinatura.isBlank()) return false;
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update((token + "-").getBytes(StandardCharsets.UTF_8));
            sha.update(corpo);
            byte[] esperado = HexFormat.of().formatHex(sha.digest()).getBytes(StandardCharsets.US_ASCII);
            return MessageDigest.isEqual(esperado, assinatura.trim().toLowerCase().getBytes(StandardCharsets.US_ASCII));
        } catch (Exception ex) {
            return false;
        }
    }

    // ================================================================ apoio

    private JsonNode chamar(HttpRequest.Builder b, String token) {
        HttpRequest req = b.timeout(Duration.ofSeconds(25))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .build();
        HttpResponse<String> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            log.warn("PagBank fora do ar ou sem internet: {}", ex.getMessage());
            throw new FalhaPagBank(0, "Não foi possível falar com o PagBank agora. Tente de novo em instantes.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new FalhaPagBank(0, "Operação interrompida.");
        }
        JsonNode corpo = ler(resp.body());
        if (resp.statusCode() / 100 != 2) {
            log.warn("PagBank respondeu {} em {} {}: {}", resp.statusCode(), req.method(), req.uri().getPath(), resumo(resp.body()));
            throw new FalhaPagBank(resp.statusCode(), mensagem(resp.statusCode(), corpo));
        }
        return corpo;
    }

    /** Mensagem em português a partir de error_messages do PagBank. */
    static String mensagem(int status, JsonNode corpo) {
        if (status == 401 || status == 403) return "O PagBank recusou o token. Confira o token (e se ele é de produção ou de testes).";
        StringBuilder s = new StringBuilder();
        for (JsonNode e : corpo.path("error_messages")) {
            String d = e.path("description").asText(""), p = e.path("parameter_name").asText("");
            if (!s.isEmpty()) s.append("; ");
            s.append(d.isBlank() ? e.path("code").asText("erro") : d).append(p.isBlank() ? "" : " (" + p + ")");
        }
        return "O PagBank não aceitou o pedido" + (s.isEmpty() ? " (código " + status + ")." : ": " + s + ".");
    }

    private JsonNode ler(String s) {
        try {
            return s == null || s.isBlank() ? json.createObjectNode() : json.readTree(s);
        } catch (IOException ex) {
            return json.createObjectNode();
        }
    }

    private String escrever(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String resumo(String s) {
        if (s == null) return "";
        // Sem CPF nem e-mail no log
        String r = s.replaceAll("\"tax_id\"\\s*:\\s*\"\\d+\"", "\"tax_id\":\"***\"").replaceAll("\"email\"\\s*:\\s*\"[^\"]*\"", "\"email\":\"***\"");
        return r.length() > 600 ? r.substring(0, 600) + "…" : r;
    }
}
