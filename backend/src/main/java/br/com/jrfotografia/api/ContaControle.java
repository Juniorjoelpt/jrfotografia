package br.com.jrfotografia.api;

import br.com.jrfotografia.api.Dtos.TrocaSenha;
import br.com.jrfotografia.api.Dtos.UsuarioDto;
import br.com.jrfotografia.api.Dtos.UsuarioForm;
import br.com.jrfotografia.config.Erros.NaoEncontrado;
import br.com.jrfotografia.config.Erros.RegraNegocio;
import br.com.jrfotografia.config.UsuarioLogado;
import br.com.jrfotografia.dominio.Enums.Perfil;
import br.com.jrfotografia.dominio.Repositorios.UsuarioRepo;
import br.com.jrfotografia.dominio.Usuario;
import br.com.jrfotografia.servico.SegurancaServico;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** A própria senha (qualquer perfil) e o cadastro de quem acessa o painel (só administrador). */
@RestController
@RequestMapping("/api")
public class ContaControle {

    private final UsuarioRepo usuarios;
    private final PasswordEncoder senhas;
    private final SegurancaServico seguranca;
    private final br.com.jrfotografia.dominio.Repositorios.EstudioRepo estudios;
    private final br.com.jrfotografia.dominio.Repositorios.AuditoriaRepo auditorias;

    public ContaControle(UsuarioRepo usuarios, PasswordEncoder senhas, SegurancaServico seguranca,
                         br.com.jrfotografia.dominio.Repositorios.EstudioRepo estudios,
                         br.com.jrfotografia.dominio.Repositorios.AuditoriaRepo auditorias) {
        this.seguranca = seguranca;
        this.estudios = estudios;
        this.auditorias = auditorias;
        this.usuarios = usuarios;
        this.senhas = senhas;
    }

    @PutMapping("/conta/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void trocarSenha(@Valid @RequestBody TrocaSenha f) {
        Usuario u = usuarios.findById(UsuarioLogado.atual().usuarioId()).orElseThrow(() -> new NaoEncontrado("Usuário não encontrado"));
        if (!senhas.matches(f.senhaAtual(), u.getSenhaHash())) throw new RegraNegocio("A senha atual não confere.");
        validarNova(f.novaSenha());
        u.setSenhaHash(senhas.encode(f.novaSenha()));
    }

    @GetMapping("/usuarios")
    @PreAuthorize("hasRole('ADMIN')")
    public List<UsuarioDto> listar() {
        Long eu = UsuarioLogado.atual().usuarioId();
        return usuarios.findByEstudioIdOrderByNome(UsuarioLogado.estudio()).stream().map(u -> dto(u, eu)).toList();
    }

    @PostMapping("/usuarios")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioDto criar(@Valid @RequestBody UsuarioForm f) {
        if (f.senha() == null) throw new RegraNegocio("Defina uma senha provisória de pelo menos 8 caracteres.");
        validarNova(f.senha());
        String email = f.email().trim().toLowerCase();
        if (usuarios.findByEmailIgnoreCase(email).isPresent()) throw new RegraNegocio("Já existe alguém com este e-mail.");
        Usuario u = new Usuario();
        u.setEstudioId(UsuarioLogado.estudio());
        u.setNome(f.nome().trim());
        u.setEmail(email);
        u.setSenhaHash(senhas.encode(f.senha()));
        u.setPerfil(f.perfil() != null ? f.perfil() : Perfil.COLABORADOR);
        u.setAtivo(f.ativo() == null || f.ativo());
        return dto(usuarios.save(u), UsuarioLogado.atual().usuarioId());
    }

    /** Nome, e-mail, perfil e situação. Senha só muda se vier preenchida (redefinição pelo administrador). */
    @PutMapping("/usuarios/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UsuarioDto editar(@PathVariable Long id, @Valid @RequestBody UsuarioForm f) {
        Long eu = UsuarioLogado.atual().usuarioId();
        Usuario u = usuarios.findById(id).filter(x -> x.getEstudioId().equals(UsuarioLogado.estudio()))
                .orElseThrow(() -> new NaoEncontrado("Usuário não encontrado"));
        String email = f.email().trim().toLowerCase();
        usuarios.findByEmailIgnoreCase(email).filter(x -> !x.getId().equals(id))
                .ifPresent(x -> { throw new RegraNegocio("Já existe alguém com este e-mail."); });
        boolean ativo = f.ativo() == null || f.ativo();
        Perfil perfil = f.perfil() != null ? f.perfil() : u.getPerfil();
        if (id.equals(eu) && (!ativo || perfil != Perfil.ADMIN)) {
            throw new RegraNegocio("Você não pode desativar nem tirar o perfil de administrador da sua própria conta.");
        }
        u.setNome(f.nome().trim());
        u.setEmail(email);
        u.setPerfil(perfil);
        u.setAtivo(ativo);
        if (f.senha() != null && !f.senha().isBlank()) {
            validarNova(f.senha());
            u.setSenhaHash(senhas.encode(f.senha()));
        }
        return dto(u, eu);
    }

    private static void validarNova(String s) {
        if (s.length() < 8) throw new RegraNegocio("A senha precisa ter pelo menos 8 caracteres.");
        if (AuthControle.SENHA_PADRAO.equals(s)) throw new RegraNegocio("Escolha uma senha diferente da senha de fábrica.");
    }

    // ================================================================ verificação em duas etapas

    public record Situacao2fa(boolean ativo, int codigosRestantes, boolean configurando) {}

    public record Codigo2fa(@jakarta.validation.constraints.NotBlank String codigo) {}

    public record Desligar2fa(@jakarta.validation.constraints.NotBlank String senha, @jakarta.validation.constraints.NotBlank String codigo) {}

    public record Codigos(List<String> codigos) {}

    @GetMapping("/conta/2fa")
    public Situacao2fa situacao2fa() {
        Usuario u = usuarios.findById(UsuarioLogado.atual().usuarioId()).orElseThrow();
        return new Situacao2fa(u.isTotpAtivo(), seguranca.codigosRestantes(u), u.getTotpPendente() != null);
    }

    @PostMapping("/conta/2fa/iniciar")
    public SegurancaServico.Configuracao iniciar2fa() {
        String emissor = estudios.findById(UsuarioLogado.estudio()).map(e -> e.getNome()).orElse("JR Fotografia");
        return seguranca.iniciar(UsuarioLogado.atual().usuarioId(), emissor);
    }

    @PostMapping("/conta/2fa/ativar")
    public Codigos ativar2fa(@Valid @RequestBody Codigo2fa f) {
        return new Codigos(seguranca.ativar(UsuarioLogado.atual().usuarioId(), f.codigo()));
    }

    @PostMapping("/conta/2fa/desativar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desativar2fa(@Valid @RequestBody Desligar2fa f) {
        seguranca.desativar(UsuarioLogado.atual().usuarioId(), f.senha(), f.codigo());
    }

    @PostMapping("/conta/2fa/codigos")
    public Codigos novosCodigos(@Valid @RequestBody Codigo2fa f) {
        return new Codigos(seguranca.gerarCodigos(UsuarioLogado.atual().usuarioId(), f.codigo()));
    }

    @PostMapping("/usuarios/{id}/2fa/remover")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover2fa(@PathVariable Long id) {
        if (id.equals(UsuarioLogado.atual().usuarioId())) throw new RegraNegocio("Para a sua conta, use Minha conta › Desligar.");
        seguranca.remover(UsuarioLogado.estudio(), id);
    }

    // ================================================================ registro de atividades

    public record AtividadeDto(Long id, Long usuarioId, String usuario, String acao, String detalhe, String entidade, Long entidadeId,
                               String ip, java.time.LocalDateTime criadoEm) {}

    @GetMapping("/auditoria")
    @PreAuthorize("hasRole('ADMIN')")
    public List<AtividadeDto> auditoria(@RequestParam(required = false) Long usuario, @RequestParam(required = false) java.time.LocalDate de,
                                        @RequestParam(required = false) java.time.LocalDate ate) {
        Long est = UsuarioLogado.estudio();
        List<br.com.jrfotografia.dominio.Auditoria> lista;
        if (de != null || ate != null) {
            var ini = (de != null ? de : java.time.LocalDate.of(2000, 1, 1)).atStartOfDay();
            var fim = (ate != null ? ate : java.time.LocalDate.now()).plusDays(1).atStartOfDay();
            lista = auditorias.findTop300ByEstudioIdAndCriadoEmBetweenOrderByIdDesc(est, ini, fim);
            if (usuario != null) lista = lista.stream().filter(a -> usuario.equals(a.getUsuarioId())).toList();
        } else {
            lista = usuario != null ? auditorias.findTop300ByEstudioIdAndUsuarioIdOrderByIdDesc(est, usuario) : auditorias.findTop300ByEstudioIdOrderByIdDesc(est);
        }
        return lista.stream().map(a -> new AtividadeDto(a.getId(), a.getUsuarioId(), a.getUsuario(), a.getAcao(), a.getDetalhe(), a.getEntidade(),
                a.getEntidadeId(), a.getIp(), a.getCriadoEm())).toList();
    }

    private static UsuarioDto dto(Usuario u, Long eu) {
        return new UsuarioDto(u.getId(), u.getNome(), u.getEmail(), u.getPerfil(), u.isAtivo(), u.getId().equals(eu), u.isTotpAtivo());
    }
}
