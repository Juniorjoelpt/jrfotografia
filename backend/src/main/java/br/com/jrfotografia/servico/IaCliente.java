package br.com.jrfotografia.servico;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Conversa com um modelo de linguagem pelo formato "chat completions" da OpenAI, que o Ollama (/v1),
 * a Groq, a OpenRouter e outros falam. Assim o mesmo código serve para um modelo aberto na própria VPS
 * ou hospedado por terceiros: muda só o endereço, o modelo e a chave.
 */
@Component
public class IaCliente {

    private static final Logger log = LoggerFactory.getLogger(IaCliente.class);

    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public IaCliente(ObjectMapper json) {
        this.json = json;
    }

    /** Falha ao falar com o modelo, já com uma mensagem para mostrar no painel. */
    public static class FalhaIa extends RuntimeException {
        public FalhaIa(String mensagem) {
            super(mensagem);
        }
    }

    public record Conexao(String url, String chave, String modelo) {}

    /**
     * Manda as mensagens e devolve o texto da resposta. Com {@code pedirJson}, pede resposta em JSON
     * (response_format); se o serviço não aceitar esse campo, tenta de novo sem ele.
     */
    public String conversar(Conexao c, String sistema, String usuario, boolean pedirJson, Duration limite) {
        try {
            return enviar(c, sistema, usuario, pedirJson, limite);
        } catch (FormatoRecusado ex) {
            return enviar(c, sistema, usuario, false, limite);
        }
    }

    /** Pergunta curtinha para conferir endereço, modelo e chave. */
    public String testar(Conexao c) {
        return conversar(c, "Responda em português do Brasil, em uma frase curta.", "Diga olá para um fotógrafo.", false, Duration.ofSeconds(90));
    }

    private static class FormatoRecusado extends RuntimeException {}

    private String enviar(Conexao c, String sistema, String usuario, boolean pedirJson, Duration limite) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("model", c.modelo());
        corpo.put("messages", List.of(Map.of("role", "system", "content", sistema), Map.of("role", "user", "content", usuario)));
        corpo.put("temperature", 0.8);
        corpo.put("stream", false);
        if (pedirJson) corpo.put("response_format", Map.of("type", "json_object"));
        HttpRequest.Builder b;
        try {
            b = HttpRequest.newBuilder(URI.create(base(c.url()) + "/chat/completions"));
        } catch (IllegalArgumentException ex) {
            throw new FalhaIa("O endereço do assistente não é válido. Confira em Ajustes › Assistente.");
        }
        b.timeout(limite)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(escrever(corpo), StandardCharsets.UTF_8));
        if (c.chave() != null && !c.chave().isBlank()) b.header("Authorization", "Bearer " + c.chave());
        HttpResponse<String> resp;
        try {
            resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (java.net.http.HttpTimeoutException ex) {
            throw new FalhaIa("O assistente demorou demais para responder. Num modelo rodando sem placa de vídeo isso acontece: tente um modelo menor.");
        } catch (IOException ex) {
            log.warn("Assistente de IA fora do ar: {}", ex.getMessage());
            throw new FalhaIa("Não foi possível falar com o assistente em " + base(c.url()) + ". Confira se o serviço está ligado e o endereço.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new FalhaIa("A conversa com o assistente foi interrompida.");
        }
        int st = resp.statusCode();
        if (st == 401 || st == 403) throw new FalhaIa("A chave do assistente foi recusada. Confira a chave em Ajustes › Assistente.");
        if (st == 404) throw new FalhaIa("Modelo ou endereço não encontrado (" + c.modelo() + "). Confira o nome do modelo.");
        if (st == 429) throw new FalhaIa("O serviço do assistente pediu para esperar (limite de uso). Tente de novo em um minuto.");
        if (st == 400 && pedirJson && resp.body() != null && resp.body().contains("response_format")) throw new FormatoRecusado();
        if (st >= 400) {
            log.warn("Assistente respondeu {}: {}", st, resumo(resp.body()));
            throw new FalhaIa("O assistente respondeu com erro (" + st + "). " + resumo(mensagemDeErro(resp.body())));
        }
        try {
            JsonNode r = json.readTree(resp.body());
            String texto = r.path("choices").path(0).path("message").path("content").asText("");
            if (texto.isBlank()) throw new FalhaIa("O assistente respondeu vazio. Tente de novo.");
            return texto;
        } catch (IOException ex) {
            throw new FalhaIa("A resposta do assistente veio num formato inesperado.");
        }
    }

    static String base(String url) {
        String u = url == null ? "" : url.trim().replaceAll("/+$", "");
        return u.endsWith("/chat/completions") ? u.substring(0, u.length() - "/chat/completions".length()) : u;
    }

    private String mensagemDeErro(String corpo) {
        try {
            JsonNode r = json.readTree(corpo);
            String m = r.path("error").path("message").asText(r.path("error").asText(""));
            return m.isBlank() ? corpo : m;
        } catch (Exception ex) {
            return corpo;
        }
    }

    private static String resumo(String s) {
        if (s == null) return "";
        s = s.replaceAll("\\s+", " ").trim();
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }

    private String escrever(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
