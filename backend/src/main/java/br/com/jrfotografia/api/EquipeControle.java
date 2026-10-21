package br.com.jrfotografia.api;

import br.com.jrfotografia.config.UsuarioLogado;
import br.com.jrfotografia.dominio.Equipe.*;
import br.com.jrfotografia.dominio.Estudio;
import br.com.jrfotografia.dominio.Repositorios.*;
import br.com.jrfotografia.dominio.Trabalho;
import br.com.jrfotografia.dominio.Usuario;
import br.com.jrfotografia.servico.EquipeServico;
import br.com.jrfotografia.servico.EquipeServico.Inativa;
import br.com.jrfotografia.servico.EquipeServico.ProximaData;
import br.com.jrfotografia.servico.EquipeServico.ResumoPesquisas;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Fase 3 · bloco 3: relacionamento (datas, inativas, pesquisa) e equipe (freelancers, comissões, tarefas). */
@RestController
public class EquipeControle {

    private final EquipeServico servico;
    private final TrabalhoRepo trabalhos;
    private final UsuarioRepo usuarios;
    private final EstudioRepo estudios;

    public EquipeControle(EquipeServico servico, TrabalhoRepo trabalhos, UsuarioRepo usuarios, EstudioRepo estudios) {
        this.servico = servico;
        this.trabalhos = trabalhos;
        this.usuarios = usuarios;
        this.estudios = estudios;
    }

    // ---------------------------------------------------------------- DTOs
    public record DataForm(@NotBlank @Size(max = 80) String descricao, @Min(1) @Max(31) int dia, @Min(1) @Max(12) int mes, Integer ano) {}

    public record DataDto(Long id, String descricao, int dia, int mes, Integer ano) {
        static DataDto de(DataEspecial d) { return new DataDto(d.getId(), d.getDescricao(), d.getDia(), d.getMes(), d.getAno()); }
    }

    public record PesquisaPainel(Long id, Long trabalhoId, String trabalho, String cliente, String token, Integer nota, Integer atendimento,
                                 Integer fotos, Integer prazo, String comentario, Boolean autorizaDepoimento, LocalDateTime criadoEm,
                                 LocalDateTime respondidaEm) {}

    public record Pesquisas(ResumoPesquisas resumo, List<PesquisaPainel> lista) {}

    public record PesquisaPublica(String cliente, String estudioNome, String estudioInstagram, boolean respondida, Integer nota) {}

    public record RespostaForm(@NotNull @Min(0) @Max(10) Integer nota, @Min(1) @Max(5) Integer atendimento, @Min(1) @Max(5) Integer fotos,
                               @Min(1) @Max(5) Integer prazo, @Size(max = 2000) String comentario, Boolean autorizaDepoimento) {}

    public record FreelancerForm(@NotBlank @Size(max = 120) String nome, @Size(max = 20) String telefone, @Size(max = 60) String funcao,
                                 @Size(max = 120) String chavePix, @Size(max = 1000) String observacoes, Boolean ativo) {}

    public record FreelancerDto(Long id, String nome, String telefone, String funcao, String chavePix, String observacoes, boolean ativo,
                                BigDecimal aPagar, BigDecimal pago, long trabalhos) {}

    public record VinculoForm(@NotNull Long freelancerId, @Size(max = 60) String funcao, BigDecimal valor, BigDecimal percentual) {}

    public record VinculoDto(Long id, Long trabalhoId, String trabalho, Long freelancerId, String freelancer, String telefone, String chavePix,
                             String funcao, BigDecimal percentual, BigDecimal valor, LocalDate pagoEm) {}

    public record PagamentoForm(LocalDate data) {}

    public record TarefaForm(Long trabalhoId, @NotBlank @Size(max = 160) String titulo, @Size(max = 2000) String descricao,
                             Long responsavelUsuarioId, Long responsavelFreelancerId, LocalDate prazo) {}

    public record TarefaDto(Long id, Long trabalhoId, String trabalho, String titulo, String descricao, Long responsavelUsuarioId,
                            Long responsavelFreelancerId, String responsavel, LocalDate prazo, StatusTarefa status, boolean automatica,
                            boolean atrasada, LocalDateTime criadoEm, LocalDateTime concluidaEm) {}

    public record StatusForm(@NotNull StatusTarefa status) {}

    public record Pessoa(Long id, String nome, String tipo) {}

    // ================================================================ relacionamento

    @GetMapping("/api/relacionamento/datas")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ProximaData> datas(@RequestParam(defaultValue = "30") int dias) {
        return servico.proximasDatas(UsuarioLogado.estudio(), Math.max(0, Math.min(dias, 366)), LocalDate.now());
    }

    @GetMapping("/api/relacionamento/inativos")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Inativa> inativas(@RequestParam(defaultValue = "12") int meses) {
        return servico.inativas(UsuarioLogado.estudio(), Math.max(1, Math.min(meses, 120)), LocalDate.now());
    }

    @PostMapping("/api/clientes/{id}/contato")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> contato(@PathVariable Long id) {
        servico.registrarContato(UsuarioLogado.estudio(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/clientes/{id}/datas")
    public List<DataDto> datasDaCliente(@PathVariable Long id) {
        return servico.datasDaCliente(UsuarioLogado.estudio(), id).stream().map(DataDto::de).toList();
    }

    @PostMapping("/api/clientes/{id}/datas")
    public DataDto novaData(@PathVariable Long id, @Valid @RequestBody DataForm f) {
        return DataDto.de(servico.novaData(UsuarioLogado.estudio(), id, f.descricao(), f.dia(), f.mes(), f.ano()));
    }

    @DeleteMapping("/api/clientes/{id}/datas/{dataId}")
    public ResponseEntity<Void> apagarData(@PathVariable Long id, @PathVariable Long dataId) {
        servico.apagarData(UsuarioLogado.estudio(), id, dataId);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- pesquisa

    @GetMapping("/api/relacionamento/pesquisas")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public Pesquisas pesquisas() {
        List<PesquisaSatisfacao> lista = servico.pesquisas(UsuarioLogado.estudio());
        return new Pesquisas(EquipeServico.resumo(lista), lista.stream().map(this::pesquisaPainel).toList());
    }

    @PostMapping("/api/trabalhos/{id}/pesquisa")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public PesquisaPainel pesquisaDoTrabalho(@PathVariable Long id) {
        return pesquisaPainel(servico.pesquisaDoTrabalho(UsuarioLogado.estudio(), id));
    }

    @GetMapping("/api/publico/pesquisas/{token}")
    @Transactional(readOnly = true)
    public PesquisaPublica verPesquisa(@PathVariable String token) {
        return publica(servico.pesquisaPorToken(token));
    }

    @PostMapping("/api/publico/pesquisas/{token}")
    @Transactional
    public PesquisaPublica responder(@PathVariable String token, @Valid @RequestBody RespostaForm f) {
        return publica(servico.responder(token, f.nota(), f.atendimento(), f.fotos(), f.prazo(), f.comentario(), f.autorizaDepoimento()));
    }

    private PesquisaPublica publica(PesquisaSatisfacao p) {
        Trabalho t = trabalhos.findById(p.getTrabalhoId()).orElseThrow();
        Estudio e = estudios.findById(p.getEstudioId()).orElseThrow();
        String primeiro = t.getCliente().getNome().trim().split("\\s+")[0];
        return new PesquisaPublica(primeiro, e.getNome(), e.getInstagram(), p.getRespondidaEm() != null, p.getNota());
    }

    private PesquisaPainel pesquisaPainel(PesquisaSatisfacao p) {
        Trabalho t = trabalhos.findById(p.getTrabalhoId()).orElseThrow();
        return new PesquisaPainel(p.getId(), t.getId(), t.getTitulo(), t.getCliente().getNome(), p.getToken(), p.getNota(), p.getAtendimento(),
                p.getFotos(), p.getPrazo(), p.getComentario(), p.getAutorizaDepoimento(), p.getCriadoEm(), p.getRespondidaEm());
    }

    // ================================================================ freelancers

    @GetMapping("/api/freelancers")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<FreelancerDto> freelancers() {
        Long est = UsuarioLogado.estudio();
        Map<Long, BigDecimal[]> soma = new HashMap<>();
        Map<Long, Long> qtd = new HashMap<>();
        for (TrabalhoFreelancer v : servico.todosVinculos(est)) {
            BigDecimal[] s = soma.computeIfAbsent(v.getFreelancer().getId(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            if (v.getDespesaId() == null) s[0] = s[0].add(v.getValor()); else s[1] = s[1].add(v.getValor());
            qtd.merge(v.getFreelancer().getId(), 1L, Long::sum);
        }
        return servico.freelancers(est).stream().map(f -> {
            BigDecimal[] s = soma.getOrDefault(f.getId(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            return new FreelancerDto(f.getId(), f.getNome(), f.getTelefone(), f.getFuncao(), f.getChavePix(), f.getObservacoes(), f.isAtivo(),
                    s[0], s[1], qtd.getOrDefault(f.getId(), 0L));
        }).toList();
    }

    @PostMapping("/api/freelancers")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Long> novoFreelancer(@Valid @RequestBody FreelancerForm f) {
        return Map.of("id", servico.salvarFreelancer(UsuarioLogado.estudio(), null, f.nome(), f.telefone(), f.funcao(), f.chavePix(),
                f.observacoes(), f.ativo() == null || f.ativo()).getId());
    }

    @PutMapping("/api/freelancers/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Long> editarFreelancer(@PathVariable Long id, @Valid @RequestBody FreelancerForm f) {
        return Map.of("id", servico.salvarFreelancer(UsuarioLogado.estudio(), id, f.nome(), f.telefone(), f.funcao(), f.chavePix(),
                f.observacoes(), f.ativo() == null || f.ativo()).getId());
    }

    @GetMapping("/api/freelancers/{id}/extrato")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<VinculoDto> extrato(@PathVariable Long id) {
        return servico.extrato(UsuarioLogado.estudio(), id).stream().map(this::vinculo).toList();
    }

    @GetMapping("/api/trabalhos/{id}/freelancers")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<VinculoDto> doTrabalho(@PathVariable Long id) {
        return servico.freelancersDoTrabalho(UsuarioLogado.estudio(), id).stream().map(this::vinculo).toList();
    }

    @PostMapping("/api/trabalhos/{id}/freelancers")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public VinculoDto vincular(@PathVariable Long id, @Valid @RequestBody VinculoForm f) {
        return vinculo(servico.vincular(UsuarioLogado.estudio(), id, f.freelancerId(), f.funcao(), f.valor(), f.percentual()));
    }

    @DeleteMapping("/api/comissoes/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> desvincular(@PathVariable Long id) {
        servico.desvincular(UsuarioLogado.estudio(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/comissoes/{id}/pagamento")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public VinculoDto pagar(@PathVariable Long id, @RequestBody(required = false) PagamentoForm f) {
        return vinculo(servico.pagar(UsuarioLogado.estudio(), id, f == null ? null : f.data()));
    }

    @DeleteMapping("/api/comissoes/{id}/pagamento")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public VinculoDto desfazer(@PathVariable Long id) {
        return vinculo(servico.desfazerPagamento(UsuarioLogado.estudio(), id));
    }

    private VinculoDto vinculo(TrabalhoFreelancer v) {
        Trabalho t = trabalhos.findById(v.getTrabalhoId()).orElseThrow();
        Freelancer f = v.getFreelancer();
        return new VinculoDto(v.getId(), t.getId(), t.getTitulo(), f.getId(), f.getNome(), f.getTelefone(), f.getChavePix(), v.getFuncao(),
                v.getPercentual(), v.getValor(), servico.pagoEm(v).orElse(null));
    }

    // ================================================================ tarefas (fotógrafo e colaboradores)

    @GetMapping("/api/equipe/pessoas")
    @Transactional(readOnly = true)
    public List<Pessoa> pessoas() {
        Long est = UsuarioLogado.estudio();
        List<Pessoa> r = new java.util.ArrayList<>();
        for (Usuario u : usuarios.findByEstudioIdOrderByNome(est)) if (u.isAtivo()) r.add(new Pessoa(u.getId(), u.getNome(), "USUARIO"));
        for (Freelancer f : servico.freelancers(est)) if (f.isAtivo()) r.add(new Pessoa(f.getId(), f.getNome(), "FREELANCER"));
        return r;
    }

    @GetMapping("/api/tarefas")
    @Transactional(readOnly = true)
    public List<TarefaDto> tarefas(@RequestParam(required = false) Long trabalho) {
        Long est = UsuarioLogado.estudio();
        Nomes n = nomes(est);
        var lista = trabalho != null ? servico.tarefasDoTrabalho(est, trabalho) : servico.tarefas(est);
        return lista.stream().map(t -> tarefa(t, n)).toList();
    }

    @PostMapping("/api/tarefas")
    @Transactional
    public TarefaDto novaTarefa(@Valid @RequestBody TarefaForm f) {
        Long est = UsuarioLogado.estudio();
        return tarefa(servico.salvarTarefa(est, null, f.trabalhoId(), f.titulo(), f.descricao(), f.responsavelUsuarioId(),
                f.responsavelFreelancerId(), f.prazo()), nomes(est));
    }

    @PutMapping("/api/tarefas/{id}")
    @Transactional
    public TarefaDto editarTarefa(@PathVariable Long id, @Valid @RequestBody TarefaForm f) {
        Long est = UsuarioLogado.estudio();
        return tarefa(servico.salvarTarefa(est, id, f.trabalhoId(), f.titulo(), f.descricao(), f.responsavelUsuarioId(),
                f.responsavelFreelancerId(), f.prazo()), nomes(est));
    }

    @PatchMapping("/api/tarefas/{id}/status")
    @Transactional
    public TarefaDto statusTarefa(@PathVariable Long id, @Valid @RequestBody StatusForm f) {
        Long est = UsuarioLogado.estudio();
        return tarefa(servico.mudarStatusTarefa(est, id, f.status()), nomes(est));
    }

    @DeleteMapping("/api/tarefas/{id}")
    public ResponseEntity<Void> apagarTarefa(@PathVariable Long id) {
        servico.apagarTarefa(UsuarioLogado.estudio(), id);
        return ResponseEntity.noContent().build();
    }

    private record Nomes(Map<Long, String> usuarios, Map<Long, String> freelancers) {}

    private Nomes nomes(Long est) {
        Map<Long, String> u = new HashMap<>(), f = new HashMap<>();
        usuarios.findByEstudioIdOrderByNome(est).forEach(x -> u.put(x.getId(), x.getNome()));
        servico.freelancers(est).forEach(x -> f.put(x.getId(), x.getNome()));
        return new Nomes(u, f);
    }

    private TarefaDto tarefa(Tarefa t, Nomes n) {
        String trab = t.getTrabalhoId() == null ? null : trabalhos.findById(t.getTrabalhoId()).map(Trabalho::getTitulo).orElse(null);
        String resp = t.getResponsavelUsuarioId() != null ? n.usuarios().get(t.getResponsavelUsuarioId())
                : t.getResponsavelFreelancerId() != null ? n.freelancers().get(t.getResponsavelFreelancerId()) : null;
        boolean atrasada = t.getStatus() != StatusTarefa.FEITA && t.getPrazo() != null && t.getPrazo().isBefore(LocalDate.now());
        return new TarefaDto(t.getId(), t.getTrabalhoId(), trab, t.getTitulo(), t.getDescricao(), t.getResponsavelUsuarioId(),
                t.getResponsavelFreelancerId(), resp, t.getPrazo(), t.getStatus(), t.isAutomatica(), atrasada, t.getCriadoEm(), t.getConcluidaEm());
    }

}
