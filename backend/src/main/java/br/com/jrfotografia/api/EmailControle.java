package br.com.jrfotografia.api;

import static br.com.jrfotografia.servico.Textos.vazio;

import br.com.jrfotografia.config.Erros.RegraNegocio;
import br.com.jrfotografia.config.UsuarioLogado;
import br.com.jrfotografia.dominio.Estudio;
import br.com.jrfotografia.dominio.Repositorios.EmailEnviadoRepo;
import br.com.jrfotografia.dominio.Repositorios.EstudioRepo;
import br.com.jrfotografia.servico.Cifra;
import br.com.jrfotografia.servico.EmailAvisos;
import br.com.jrfotografia.servico.EmailServico;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/** Ajustes › E-mail: servidor SMTP, o que mandar, teste e histórico. Só administrador. */
@RestController
@RequestMapping("/api/estudio/email")
@PreAuthorize("hasRole('ADMIN')")
public class EmailControle {

    private final EstudioRepo estudios;
    private final EmailEnviadoRepo historico;
    private final Cifra cifra;
    private final EmailAvisos avisos;

    public EmailControle(EstudioRepo estudios, EmailEnviadoRepo historico, Cifra cifra, EmailAvisos avisos) {
        this.estudios = estudios;
        this.historico = historico;
        this.cifra = cifra;
        this.avisos = avisos;
    }

    /** A senha nunca volta: só diz se já existe uma guardada. */
    public record ConfigEmail(boolean ativo, String host, Integer porta, String usuario, boolean temSenha, String seguranca, String remetente,
                              boolean avisos, boolean lembretes, String emailEstudio) {}

    /** {@code senha} vazia mantém a guardada. */
    public record ConfigForm(boolean ativo, @Size(max = 120) String host, @Min(1) @Max(65535) Integer porta, @Size(max = 160) String usuario,
                             @Size(max = 200) String senha, @Pattern(regexp = "STARTTLS|SSL|NENHUMA") String seguranca,
                             @Size(max = 160) String remetente, boolean avisos, boolean lembretes) {}

    public record TesteForm(@NotBlank @Email String para) {}

    public record EmailDto(Long id, String para, String assunto, String tipo, String status, String erro, LocalDateTime criadoEm,
                           LocalDateTime enviadoEm) {}

    @GetMapping
    public ConfigEmail ler() {
        return dto(estudio());
    }

    @PutMapping
    @Transactional
    public ConfigEmail salvar(@Valid @RequestBody ConfigForm f) {
        Estudio e = estudio();
        e.setSmtpHost(vazio(f.host()) ? null : f.host().trim());
        e.setSmtpPorta(f.porta());
        e.setSmtpUsuario(vazio(f.usuario()) ? null : f.usuario().trim());
        // Gmail mostra a senha de app em grupos com espaço: tiramos os espaços.
        if (!vazio(f.senha())) e.setSmtpSenha(cifra.cifrar(f.senha().replace(" ", "")));
        e.setSmtpSeguranca(f.seguranca() == null ? "STARTTLS" : f.seguranca());
        e.setEmailRemetente(vazio(f.remetente()) ? null : f.remetente().trim());
        e.setEmailAvisos(f.avisos());
        e.setEmailLembretes(f.lembretes());
        if (f.ativo() && !EmailServico.configurado(e)) throw new RegraNegocio("Para ligar, preencha servidor, usuário e senha.");
        e.setEmailAtivo(f.ativo());
        return dto(e);
    }

    @PostMapping("/teste")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void testar(@Valid @RequestBody TesteForm f) {
        avisos.teste(estudio(), f.para());
    }

    @GetMapping("/historico")
    public List<EmailDto> historico() {
        return historico.findTop100ByEstudioIdOrderByIdDesc(UsuarioLogado.estudio()).stream()
                .map(m -> new EmailDto(m.getId(), m.getPara(), m.getAssunto(), m.getTipo(), m.getStatus(), m.getErro(), m.getCriadoEm(), m.getEnviadoEm()))
                .toList();
    }

    private Estudio estudio() {
        return estudios.findById(UsuarioLogado.estudio()).orElseThrow();
    }

    private static ConfigEmail dto(Estudio e) {
        return new ConfigEmail(e.isEmailAtivo(), e.getSmtpHost(), e.getSmtpPorta(), e.getSmtpUsuario(), e.getSmtpSenha() != null,
                e.getSmtpSeguranca() == null ? "STARTTLS" : e.getSmtpSeguranca(), e.getEmailRemetente(), e.isEmailAvisos(), e.isEmailLembretes(),
                e.getEmail());
    }
}
