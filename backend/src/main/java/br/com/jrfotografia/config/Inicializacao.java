package br.com.jrfotografia.config;

import br.com.jrfotografia.dominio.Enums.Perfil;
import br.com.jrfotografia.dominio.Repositorios.EstudioRepo;
import br.com.jrfotografia.dominio.Repositorios.UsuarioRepo;
import br.com.jrfotografia.dominio.Usuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Na primeira inicialização, cria o administrador do estúdio padrão
 * a partir de ADMIN_EMAIL e ADMIN_SENHA. Nenhuma senha fica no código.
 */
@Component
public class Inicializacao implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(Inicializacao.class);

    private final AppProps props;
    private final UsuarioRepo usuarios;
    private final EstudioRepo estudios;
    private final PasswordEncoder senhas;

    public Inicializacao(AppProps props, UsuarioRepo usuarios, EstudioRepo estudios, PasswordEncoder senhas) {
        this.props = props;
        this.usuarios = usuarios;
        this.estudios = estudios;
        this.senhas = senhas;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (usuarios.count() > 0) return;
        var admin = props.admin();
        if (admin.email() == null || admin.email().isBlank() || admin.senha() == null || admin.senha().length() < 8) {
            log.warn("Nenhum usuário cadastrado. Defina ADMIN_EMAIL e ADMIN_SENHA (mínimo 8 caracteres) e reinicie.");
            return;
        }
        var estudio = estudios.findBySlug(props.estudioPadrao())
                .orElseThrow(() -> new IllegalStateException("Estúdio padrão não encontrado: " + props.estudioPadrao()));
        Usuario u = new Usuario();
        u.setEstudioId(estudio.getId());
        u.setNome(admin.nome());
        u.setEmail(admin.email().trim().toLowerCase());
        u.setSenhaHash(senhas.encode(admin.senha()));
        u.setPerfil(Perfil.ADMIN);
        usuarios.save(u);
        log.info("Administrador {} criado para o estúdio {}", u.getEmail(), estudio.getNome());
    }
}
