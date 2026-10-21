package br.com.jrfotografia.servico;

import br.com.jrfotografia.config.AppProps;
import br.com.jrfotografia.config.Erros.NaoEncontrado;
import br.com.jrfotografia.config.Erros.RegraNegocio;
import br.com.jrfotografia.dominio.Repositorios.UsuarioRepo;
import br.com.jrfotografia.dominio.Usuario;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

/** Verificação em duas etapas: app autenticador (TOTP) e códigos de recuperação de uso único. */
@Service
public class SegurancaServico {

    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final char[] ALFABETO = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int QTD_CODIGOS = 10;

    private final UsuarioRepo usuarios;
    private final PasswordEncoder senhas;
    private final SecretKeySpec chave;

    public SegurancaServico(UsuarioRepo usuarios, PasswordEncoder senhas, AppProps props) {
        this.usuarios = usuarios;
        this.senhas = senhas;
        this.chave = new SecretKeySpec(sha256Bytes(("totp|" + props.jwt().segredo()).getBytes(StandardCharsets.UTF_8)), "AES");
    }

    public record Configuracao(String segredo, String otpauth) {}

    public enum Metodo { APP, RECUPERACAO }

    /** Começa a configuração: segredo novo, ainda pendente até o primeiro código conferir. */
    @Transactional
    public Configuracao iniciar(Long usuarioId, String emissor) {
        Usuario u = usuario(usuarioId);
        if (u.isTotpAtivo()) throw new RegraNegocio("A verificação em duas etapas já está ligada.");
        byte[] segredo = new byte[20];
        ALEATORIO.nextBytes(segredo);
        String b32 = Totp.base32(segredo);
        u.setTotpPendente(cifrar(b32));
        String rotulo = URLEncoder.encode(emissor + ":" + u.getEmail(), StandardCharsets.UTF_8).replace("+", "%20");
        String url = "otpauth://totp/" + rotulo + "?secret=" + b32 + "&issuer=" + URLEncoder.encode(emissor, StandardCharsets.UTF_8).replace("+", "%20")
                + "&digits=6&period=30";
        return new Configuracao(b32.replaceAll("(.{4})(?!$)", "$1 "), url);
    }

    /** Confere o primeiro código e liga. Devolve os códigos de recuperação (mostrados uma vez só). */
    @Transactional
    public List<String> ativar(Long usuarioId, String codigo) {
        Usuario u = usuario(usuarioId);
        if (u.getTotpPendente() == null) throw new RegraNegocio("Comece a configuração de novo.");
        byte[] segredo = Totp.deBase32(decifrar(u.getTotpPendente()));
        long passo = Totp.verificar(segredo, limpar(codigo), Instant.now(), null);
        if (passo < 0) throw new RegraNegocio("Código não confere. Confira se o horário do celular está certo e digite o código atual.");
        u.setTotpSegredo(u.getTotpPendente());
        u.setTotpPendente(null);
        u.setTotpAtivo(true);
        u.setTotpUltimoPasso(passo);
        return novosCodigos(u);
    }

    @Transactional
    public void desativar(Long usuarioId, String senha, String codigo) {
        Usuario u = usuario(usuarioId);
        if (!u.isTotpAtivo()) return;
        if (senha == null || !senhas.matches(senha, u.getSenhaHash())) throw new RegraNegocio("Senha não confere.");
        if (verificar(u, codigo).isEmpty()) throw new RegraNegocio("Código não confere.");
        limparTotp(u);
    }

    @Transactional
    public List<String> gerarCodigos(Long usuarioId, String codigo) {
        Usuario u = usuario(usuarioId);
        if (!u.isTotpAtivo()) throw new RegraNegocio("Ligue a verificação em duas etapas primeiro.");
        if (verificar(u, codigo).isEmpty()) throw new RegraNegocio("Código não confere.");
        return novosCodigos(u);
    }

    /** Administrador tira a verificação de outra pessoa (celular perdido). Ela configura de novo no próximo acesso. */
    @Transactional
    public void remover(Long estudioId, Long usuarioId) {
        Usuario u = usuarios.findById(usuarioId).filter(x -> x.getEstudioId().equals(estudioId))
                .orElseThrow(() -> new NaoEncontrado("Usuário não encontrado."));
        limparTotp(u);
    }

    /**
     * Confere o código do login: 6 dígitos do app (sem repetir um já usado) ou um código de recuperação (gasta o código).
     */
    @Transactional
    public Optional<Metodo> verificar(Usuario recebido, String digitado) {
        Usuario u = usuario(recebido.getId());
        if (!u.isTotpAtivo() || u.getTotpSegredo() == null) return Optional.empty();
        String c = limpar(digitado);
        if (c.matches("\\d{6}")) {
            long passo = Totp.verificar(Totp.deBase32(decifrar(u.getTotpSegredo())), c, Instant.now(), u.getTotpUltimoPasso());
            if (passo < 0) return Optional.empty();
            u.setTotpUltimoPasso(passo);
            return Optional.of(Metodo.APP);
        }
        String hash = sha256(c.toUpperCase());
        List<String> restantes = new ArrayList<>(codigosGuardados(u));
        if (!restantes.remove(hash)) return Optional.empty();
        u.setCodigosRecuperacao(String.join(",", restantes));
        return Optional.of(Metodo.RECUPERACAO);
    }

    public int codigosRestantes(Usuario u) {
        return codigosGuardados(u).size();
    }

    // ---------------------------------------------------------------- apoio

    private List<String> novosCodigos(Usuario u) {
        List<String> codigos = new ArrayList<>(), hashes = new ArrayList<>();
        for (int i = 0; i < QTD_CODIGOS; i++) {
            StringBuilder sb = new StringBuilder();
            for (int k = 0; k < 8; k++) sb.append(ALFABETO[ALEATORIO.nextInt(ALFABETO.length)]);
            codigos.add(sb.substring(0, 4) + "-" + sb.substring(4));
            hashes.add(sha256(sb.toString()));
        }
        u.setCodigosRecuperacao(String.join(",", hashes));
        return codigos;
    }

    private static List<String> codigosGuardados(Usuario u) {
        String s = u.getCodigosRecuperacao();
        return s == null || s.isBlank() ? List.of() : Arrays.asList(s.split(","));
    }

    private static void limparTotp(Usuario u) {
        u.setTotpAtivo(false);
        u.setTotpSegredo(null);
        u.setTotpPendente(null);
        u.setTotpUltimoPasso(null);
        u.setCodigosRecuperacao(null);
    }

    private static String limpar(String c) {
        return c == null ? "" : c.replaceAll("[\\s-]", "").trim();
    }

    private Usuario usuario(Long id) {
        return usuarios.findById(id).orElseThrow(() -> new NaoEncontrado("Usuário não encontrado."));
    }

    String cifrar(String texto) {
        try {
            byte[] iv = new byte[12];
            ALEATORIO.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(128, iv));
            byte[] cifrado = c.doFinal(texto.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    String decifrar(String guardado) {
        try {
            byte[] tudo = Base64.getDecoder().decode(guardado);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(128, tudo, 0, 12));
            return new String(c.doFinal(tudo, 12, tudo.length - 12), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Segredo da verificação em duas etapas inválido", e);
        }
    }

    private static byte[] sha256Bytes(byte[] b) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(b);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String sha256(String s) {
        return HexFormat.of().formatHex(sha256Bytes(s.getBytes(StandardCharsets.UTF_8)));
    }
}
