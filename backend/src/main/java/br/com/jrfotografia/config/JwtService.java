package br.com.jrfotografia.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

/**
 * Emite e valida dois tipos de token:
 * "painel" (fotógrafo e colaboradores) e "galeria" (cliente com a senha da galeria).
 */
@Service
public class JwtService {

    public static final String TIPO_PAINEL = "painel";
    public static final String TIPO_GALERIA = "galeria";

    private final SecretKey chave;
    private final AppProps props;

    public JwtService(AppProps props) {
        this.props = props;
        byte[] bytes = props.jwt().segredo().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SEGREDO precisa ter pelo menos 32 caracteres");
        }
        this.chave = Keys.hmacShaKeyFor(bytes);
    }

    public String tokenPainel(Long usuarioId, Long estudioId, String perfil) {
        return emitir(String.valueOf(usuarioId),
                Map.of("tipo", TIPO_PAINEL, "estudio", estudioId, "perfil", perfil),
                Duration.ofHours(props.jwt().horasPainel()));
    }

    public String tokenGaleria(Long trabalhoId, String galeriaToken) {
        return emitir(String.valueOf(trabalhoId),
                Map.of("tipo", TIPO_GALERIA, "galeria", galeriaToken),
                Duration.ofHours(props.jwt().horasGaleria()));
    }

    public Optional<Claims> validar(String token) {
        try {
            return Optional.of(Jwts.parser().verifyWith(chave).build().parseSignedClaims(token).getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private String emitir(String sujeito, Map<String, Object> claims, Duration validade) {
        Instant agora = Instant.now();
        return Jwts.builder()
                .subject(sujeito)
                .claims(claims)
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(validade)))
                .signWith(chave)
                .compact();
    }
}
