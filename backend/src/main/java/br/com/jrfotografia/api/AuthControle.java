package br.com.jrfotografia.api;

import br.com.jrfotografia.api.Dtos.Login;
import br.com.jrfotografia.api.Dtos.SegundoPasso;
import br.com.jrfotografia.api.Dtos.Sessao;
import br.com.jrfotografia.dominio.Usuario;
import br.com.jrfotografia.servico.AuditoriaServico;
import br.com.jrfotografia.servico.SegurancaServico;
import br.com.jrfotografia.config.Erros.NaoAutorizado;
import br.com.jrfotografia.config.JwtService;
import br.com.jrfotografia.config.Tentativas;
import br.com.jrfotografia.dominio.Repositorios.EstudioRepo;
import br.com.jrfotografia.dominio.Repositorios.UsuarioRepo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthControle {

    /** Senha que vem de fábrica; enquanto estiver em uso, o painel pede para trocar. */
    public static final String SENHA_PADRAO = "trocar123";

    private final UsuarioRepo usuarios;
    private final EstudioRepo estudios;
    private final PasswordEncoder senhas;
    private final JwtService jwt;
    private final Tentativas tentativas;
    private final SegurancaServico seguranca;
    private final AuditoriaServico auditoria;

    public AuthControle(UsuarioRepo usuarios, EstudioRepo estudios, PasswordEncoder senhas, JwtService jwt, Tentativas tentativas,
                        SegurancaServico seguranca, AuditoriaServico auditoria) {
        this.seguranca = seguranca;
        this.auditoria = auditoria;
        this.usuarios = usuarios;
        this.estudios = estudios;
        this.senhas = senhas;
        this.jwt = jwt;
        this.tentativas = tentativas;
    }

    @PostMapping("/login")
    public Sessao login(@Valid @RequestBody Login login, HttpServletRequest req) {
        String email = login.email().trim().toLowerCase();
        String aparelho = Tentativas.aparelho(req) + '|' + email;
        tentativas.verificar(Tentativas.LOGIN_APARELHO, aparelho);
        tentativas.verificar(Tentativas.LOGIN_EMAIL, email);
        var u = usuarios.findByEmailIgnoreCase(email)
                .filter(x -> x.isAtivo() && senhas.matches(login.senha(), x.getSenhaHash()))
                .orElse(null);
        if (u == null) {
            tentativas.erro(Tentativas.LOGIN_APARELHO, aparelho);
            tentativas.erro(Tentativas.LOGIN_EMAIL, email);
            // Senha errada de um usuário que existe fica no histórico do estúdio dele.
            usuarios.findByEmailIgnoreCase(email).ifPresent(x -> registrar(x, "Tentativa de entrar com senha errada", null, req));
            throw new NaoAutorizado("E-mail ou senha incorretos.");
        }
        tentativas.acerto(Tentativas.LOGIN_APARELHO, aparelho);
        tentativas.acerto(Tentativas.LOGIN_EMAIL, email);
        if (u.isTotpAtivo()) {
            var estudio = estudios.findById(u.getEstudioId()).orElseThrow();
            return new Sessao(null, u.getNome(), u.getPerfil().name(), estudio.getNome(), false, true, jwt.tokenDesafio(u.getId()), true);
        }
        registrar(u, "Entrou no painel", null, req);
        return sessao(u, login.senha());
    }

    /** Segundo passo: o código de 6 dígitos do app (ou um código de recuperação). */
    @PostMapping("/2fa")
    public Sessao segundoPasso(@Valid @RequestBody SegundoPasso f, HttpServletRequest req) {
        var claims = jwt.validar(f.desafio()).filter(c -> JwtService.TIPO_DESAFIO.equals(c.get("tipo")))
                .orElseThrow(() -> new NaoAutorizado("O tempo para digitar o código acabou. Entre de novo com e-mail e senha."));
        Usuario u = usuarios.findById(Long.valueOf(claims.getSubject())).filter(Usuario::isAtivo)
                .orElseThrow(() -> new NaoAutorizado("Usuário inativo."));
        String chave = "u" + u.getId();
        tentativas.verificar(Tentativas.LOGIN_2FA, chave, "Muitos códigos errados.");
        var metodo = seguranca.verificar(u, f.codigo());
        if (metodo.isEmpty()) {
            tentativas.erro(Tentativas.LOGIN_2FA, chave);
            registrar(u, "Código de verificação errado", null, req);
            throw new NaoAutorizado("Código não confere. Use o código atual do app (ou um código de recuperação).");
        }
        tentativas.acerto(Tentativas.LOGIN_2FA, chave);
        boolean recuperacao = metodo.get() == SegurancaServico.Metodo.RECUPERACAO;
        Usuario atual = usuarios.findById(u.getId()).orElseThrow();
        registrar(u, recuperacao ? "Entrou no painel com código de recuperação" : "Entrou no painel (duas etapas)",
                recuperacao ? "Restam " + seguranca.codigosRestantes(atual) + " códigos de recuperação" : null, req);
        return sessao(u, null);
    }

    private Sessao sessao(Usuario u, String senhaDigitada) {
        var estudio = estudios.findById(u.getEstudioId()).orElseThrow();
        boolean padrao = senhaDigitada != null ? SENHA_PADRAO.equals(senhaDigitada) : senhas.matches(SENHA_PADRAO, u.getSenhaHash());
        return new Sessao(jwt.tokenPainel(u.getId(), u.getEstudioId(), u.getPerfil().name()),
                u.getNome(), u.getPerfil().name(), estudio.getNome(), padrao, false, null, u.isTotpAtivo());
    }

    /** "Chrome · Windows", "Safari · iPhone"… a partir do User-Agent. */
    static String navegador(String ua) {
        if (ua == null || ua.isBlank()) return null;
        String nav = ua.contains("Edg/") ? "Edge" : ua.contains("OPR/") ? "Opera" : ua.contains("SamsungBrowser") ? "Samsung Internet"
                : ua.contains("Firefox/") ? "Firefox" : ua.contains("Chrome/") || ua.contains("CriOS") ? "Chrome"
                : ua.contains("Safari/") ? "Safari" : "Navegador";
        String so = ua.contains("iPhone") ? "iPhone" : ua.contains("iPad") ? "iPad" : ua.contains("Android") ? "Android"
                : ua.contains("Windows") ? "Windows" : ua.contains("Mac OS X") ? "Mac" : ua.contains("Linux") ? "Linux" : null;
        return so != null ? nav + " · " + so : nav;
    }

    private void registrar(Usuario u, String acao, String detalhe, HttpServletRequest req) {
        String navegador = navegador(req.getHeader("User-Agent"));
        auditoria.registrar(new AuditoriaServico.Registro(u.getEstudioId(), u.getId(), u.getNome(), acao,
                detalhe != null ? detalhe + (navegador != null ? " · " + navegador : "") : navegador,
                "POST", req.getRequestURI(), "USUARIO", u.getId(), Tentativas.aparelho(req)));
    }
}
