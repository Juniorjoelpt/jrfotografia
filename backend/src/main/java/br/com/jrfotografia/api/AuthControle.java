package br.com.jrfotografia.api;

import br.com.jrfotografia.api.Dtos.Login;
import br.com.jrfotografia.api.Dtos.Sessao;
import br.com.jrfotografia.config.Erros.NaoAutorizado;
import br.com.jrfotografia.config.JwtService;
import br.com.jrfotografia.dominio.Repositorios.EstudioRepo;
import br.com.jrfotografia.dominio.Repositorios.UsuarioRepo;
import jakarta.validation.Valid;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthControle {

    private final UsuarioRepo usuarios;
    private final EstudioRepo estudios;
    private final PasswordEncoder senhas;
    private final JwtService jwt;

    public AuthControle(UsuarioRepo usuarios, EstudioRepo estudios, PasswordEncoder senhas, JwtService jwt) {
        this.usuarios = usuarios;
        this.estudios = estudios;
        this.senhas = senhas;
        this.jwt = jwt;
    }

    @PostMapping("/login")
    public Sessao login(@Valid @RequestBody Login login) {
        var u = usuarios.findByEmailIgnoreCase(login.email().trim())
                .filter(x -> x.isAtivo() && senhas.matches(login.senha(), x.getSenhaHash()))
                .orElseThrow(() -> new NaoAutorizado("E-mail ou senha incorretos."));
        var estudio = estudios.findById(u.getEstudioId()).orElseThrow();
        return new Sessao(jwt.tokenPainel(u.getId(), u.getEstudioId(), u.getPerfil().name()),
                u.getNome(), u.getPerfil().name(), estudio.getNome());
    }
}
