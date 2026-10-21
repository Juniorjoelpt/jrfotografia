package br.com.jrfotografia.api;

import br.com.jrfotografia.api.Dtos.Login;
import br.com.jrfotografia.api.Dtos.Sessao;
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

    public AuthControle(UsuarioRepo usuarios, EstudioRepo estudios, PasswordEncoder senhas, JwtService jwt, Tentativas tentativas) {
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
            throw new NaoAutorizado("E-mail ou senha incorretos.");
        }
        tentativas.acerto(Tentativas.LOGIN_APARELHO, aparelho);
        tentativas.acerto(Tentativas.LOGIN_EMAIL, email);
        var estudio = estudios.findById(u.getEstudioId()).orElseThrow();
        return new Sessao(jwt.tokenPainel(u.getId(), u.getEstudioId(), u.getPerfil().name()),
                u.getNome(), u.getPerfil().name(), estudio.getNome(), senhas.matches(SENHA_PADRAO, u.getSenhaHash()));
    }
}
