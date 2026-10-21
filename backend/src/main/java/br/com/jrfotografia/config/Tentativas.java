package br.com.jrfotografia.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Freio contra adivinhar senhas: conta os erros por chave (ex.: login + aparelho, galeria + aparelho)
 * e bloqueia por um tempo quando passam do limite. Fica na memória: reiniciar o servidor zera a contagem.
 */
@Component
public class Tentativas {

    /** Regra de bloqueio: até {@code maximo} erros dentro de {@code janela}; depois disso, espera a janela acabar. */
    public record Regra(String nome, int maximo, Duration janela) {}

    public static final Regra LOGIN_APARELHO = new Regra("login", 5, Duration.ofMinutes(15));
    public static final Regra LOGIN_EMAIL = new Regra("login-email", 20, Duration.ofHours(1));
    /** Código do app autenticador errado, por usuário: 5 a cada 15 minutos. */
    public static final Regra LOGIN_2FA = new Regra("login-2fa", 5, Duration.ofMinutes(15));
    public static final Regra GALERIA_APARELHO = new Regra("galeria", 5, Duration.ofMinutes(15));
    public static final Regra GALERIA_TOTAL = new Regra("galeria-total", 30, Duration.ofHours(1));
    /** Pré-reservas do site por aparelho: evita alguém lotar a agenda com reservas falsas. */
    public static final Regra RESERVA_APARELHO = new Regra("reserva", 4, Duration.ofHours(6));
    public static final Regra COMPROVANTE_APARELHO = new Regra("comprovante", 10, Duration.ofHours(1));
    /** Tentativas de assinar com dados inválidos e consultas de verificação de contrato. */
    public static final Regra ASSINATURA_APARELHO = new Regra("assinatura", 10, Duration.ofHours(1));
    public static final Regra VERIFICACAO_APARELHO = new Regra("verificacao", 30, Duration.ofHours(1));

    private record Contagem(int erros, Instant inicio) {}

    private final Map<String, Contagem> mapa = new ConcurrentHashMap<>();
    private final Clock relogio;

    public Tentativas() { this(Clock.systemUTC()); }

    Tentativas(Clock relogio) { this.relogio = relogio; }

    /** Lança {@link Erros.MuitasTentativas} se a chave já passou do limite. */
    public void verificar(Regra r, String chave) {
        verificar(r, chave, "Muitas tentativas erradas.");
    }

    /** Igual a {@link #verificar(Regra, String)}, com o começo da mensagem próprio da situação. */
    public void verificar(Regra r, String chave, String inicioMensagem) {
        Contagem c = atual(r, chave);
        if (c != null && c.erros() >= r.maximo()) {
            long min = Math.max(1, Duration.between(relogio.instant(), c.inicio().plus(r.janela())).toMinutes() + 1);
            String espera = min >= 120 ? (min / 60) + " horas" : min + (min == 1 ? " minuto" : " minutos");
            throw new Erros.MuitasTentativas(inicioMensagem + " Aguarde " + espera + " e tente de novo.");
        }
    }

    public void erro(Regra r, String chave) {
        mapa.compute(r.nome() + ':' + chave, (k, c) -> {
            Instant agora = relogio.instant();
            if (c == null || agora.isAfter(c.inicio().plus(r.janela()))) return new Contagem(1, agora);
            return new Contagem(c.erros() + 1, c.inicio());
        });
        if (mapa.size() > 50_000) limpar();
    }

    public void acerto(Regra r, String chave) {
        mapa.remove(r.nome() + ':' + chave);
    }

    private Contagem atual(Regra r, String chave) {
        Contagem c = mapa.get(r.nome() + ':' + chave);
        if (c != null && relogio.instant().isAfter(c.inicio().plus(r.janela()))) {
            mapa.remove(r.nome() + ':' + chave);
            return null;
        }
        return c;
    }

    private void limpar() {
        Instant limite = relogio.instant().minus(Duration.ofHours(2));
        mapa.entrySet().removeIf(e -> e.getValue().inicio().isBefore(limite));
    }

    /**
     * Endereço de quem chamou. Atrás do nginx do docker-compose vem no X-Real-IP, que o nginx sobrescreve
     * (o X-Forwarded-For não é usado porque o visitante consegue preencher o começo dele).
     */
    public static String aparelho(HttpServletRequest req) {
        String ip = req.getHeader("X-Real-IP");
        return ip != null && !ip.isBlank() ? ip.trim() : req.getRemoteAddr();
    }
}
