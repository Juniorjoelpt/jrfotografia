package br.com.jrfotografia.api;

import br.com.jrfotografia.config.Erros.NaoEncontrado;
import br.com.jrfotografia.config.Erros.RegraNegocio;
import br.com.jrfotografia.config.Tentativas;
import br.com.jrfotografia.config.UsuarioLogado;
import br.com.jrfotografia.dominio.Agendamento.HorarioAtendimento;
import br.com.jrfotografia.dominio.Agendamento.Reserva;
import br.com.jrfotografia.dominio.Enums.StatusReserva;
import br.com.jrfotografia.dominio.Enums.StatusTrabalho;
import br.com.jrfotografia.dominio.Estudio;
import br.com.jrfotografia.dominio.Pacote;
import br.com.jrfotografia.dominio.Parcela;
import br.com.jrfotografia.dominio.Repositorios.*;
import br.com.jrfotografia.dominio.Trabalho;
import br.com.jrfotografia.servico.AgendaOnlineServico;
import br.com.jrfotografia.servico.AgendaOnlineServico.Dia;
import br.com.jrfotografia.servico.AgendaOnlineServico.Regras;
import br.com.jrfotografia.servico.Armazenamento;
import br.com.jrfotografia.servico.AssinaturaServico;
import br.com.jrfotografia.servico.MarketingServico;
import br.com.jrfotografia.dominio.Marketing.Campanha;
import br.com.jrfotografia.dominio.ContratoAssinatura;
import br.com.jrfotografia.servico.EstudioDoSite;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Agendamento online: rotas do site (pré-reserva, comprovante, área da cliente) e do painel (confirmar, ajustes). */
@RestController
public class AgendaOnlineControle {

    private final AgendaOnlineServico servico;
    private final EstudioDoSite site;
    private final EstudioRepo estudios;
    private final PacoteRepo pacotes;
    private final HorarioRepo horarios;
    private final ReservaRepo reservas;
    private final ParcelaRepo parcelas;
    private final Armazenamento armazenamento;
    private final Tentativas tentativas;
    private final AssinaturaServico assinaturas;
    private final MarketingServico marketing;
    private final CampanhaRepo campanhaRepo;

    public AgendaOnlineControle(AgendaOnlineServico servico, EstudioDoSite site, EstudioRepo estudios, PacoteRepo pacotes,
                                HorarioRepo horarios, ReservaRepo reservas, ParcelaRepo parcelas, Armazenamento armazenamento,
                                Tentativas tentativas, AssinaturaServico assinaturas, MarketingServico marketing,
                                CampanhaRepo campanhaRepo) {
        this.marketing = marketing;
        this.campanhaRepo = campanhaRepo;
        this.servico = servico;
        this.site = site;
        this.estudios = estudios;
        this.pacotes = pacotes;
        this.horarios = horarios;
        this.reservas = reservas;
        this.parcelas = parcelas;
        this.armazenamento = armazenamento;
        this.tentativas = tentativas;
        this.assinaturas = assinaturas;
    }

    // ================================================================ formatos

    public record PacoteOnline(Long id, String nome, BigDecimal preco, int duracaoMin, BigDecimal sinal, Integer qtdFotos) {}

    public record AgendaPublica(boolean ativo, int sinalPercentual, int reservaHoras, int remarcacaoHoras, int reembolsoDias,
                                List<PacoteOnline> pacotes) {}

    public record ReservaForm(@NotNull Long pacoteId, @NotNull LocalDateTime inicio, @NotBlank @Size(max = 120) String nome,
                              @NotBlank @Size(max = 20) String telefone, @Email @Size(max = 120) String email,
                              @Size(max = 60) String tipo, @Size(max = 2000) String mensagem, String origem,
                              @AssertTrue(message = "é preciso aceitar o uso dos dados para o agendamento") Boolean consentimento,
                              @Size(max = 30) String codigo) {}

    public record CampanhaReservaForm(@NotNull LocalDateTime inicio, @NotBlank @Size(max = 120) String nome,
                                      @NotBlank @Size(max = 20) String telefone, @Email @Size(max = 120) String email,
                                      @Size(max = 2000) String mensagem, String origem,
                                      @AssertTrue(message = "é preciso aceitar o uso dos dados para o agendamento") Boolean consentimento,
                                      @Size(max = 30) String codigo) {}

    public record DiaCampanha(LocalDate data, String local, List<LocalTime> horarios) {}

    public record CampanhaPublica(String slug, String titulo, String descricao, BigDecimal preco, BigDecimal sinal, int sinalPercentual,
                                  int duracaoMin, Integer qtdFotos, BigDecimal precoFotoExtra, int prazoEntregaDias, boolean capa,
                                  String estudioNome, String estudioWhatsapp, int reservaHoras, List<DiaCampanha> dias, boolean esgotada) {}

    public record CampanhaResumo(String slug, String titulo, BigDecimal preco, boolean capa, LocalDate primeiroDia) {}

    public record ReservaPublica(String token, String nome, String pacote, LocalDateTime inicio, LocalDateTime fim, BigDecimal valorTotal,
                                 BigDecimal valorSinal, StatusReserva status, LocalDateTime expiraEm, boolean comprovanteEnviado,
                                 String chavePix, String estudioNome, String estudioWhatsapp, String motivoRecusa, String areaCliente,
                                 String cupomCodigo, BigDecimal desconto, String campanhaSlug) {}

    public record ReservaDto(Long id, String token, String nome, String telefone, String email, Long pacoteId, String pacote,
                             String tipo, String mensagem, String origem, LocalDateTime inicio, LocalDateTime fim,
                             BigDecimal valorTotal, BigDecimal valorSinal, StatusReserva status, LocalDateTime expiraEm,
                             boolean comprovante, LocalDateTime comprovanteEm, String comprovanteTipo, String motivoRecusa,
                             Long trabalhoId, LocalDateTime criadoEm, String cupomCodigo, BigDecimal desconto, boolean campanha) {
        static ReservaDto de(Reserva r) {
            return new ReservaDto(r.getId(), r.getToken(), r.getNome(), r.getTelefone(), r.getEmail(), r.getPacote().getId(),
                    r.getPacote().getNome(), r.getTipo(), r.getMensagem(), r.getOrigem(), r.getInicio(), r.getFim(),
                    r.getValorTotal(), r.getValorSinal(), r.statusAtual(), r.getExpiraEm(), r.getComprovante() != null,
                    r.getComprovanteEm(), r.getComprovanteTipo(), r.getMotivoRecusa(), r.getTrabalhoId(), r.getCriadoEm(),
                    r.getCupomCodigo(), r.getDesconto(), r.getCampanhaId() != null);
        }
    }

    public record ParcelaCliente(String descricao, BigDecimal valor, LocalDate vencimento, boolean paga) {}

    public record AreaCliente(String nome, String titulo, String pacote, String tipo, LocalDateTime data, int duracaoMin,
                              String local, StatusTrabalho status, LocalDateTime canceladoEm, Regras regras,
                              List<ParcelaCliente> parcelas, String galeriaToken, boolean galeriaAberta, String estudioNome,
                              String estudioWhatsapp, String chavePix, int remarcacaoHoras, int reembolsoDias,
                              String contratoToken, String contratoStatus, Indicar indicacao) {}

    /** Programa de indicação na área da cliente: o código dela e o que cada lado ganha. */
    public record Indicar(String codigo, int descontoAmiga, BigDecimal recompensa, long indicacoes) {}

    public record RemarcarForm(@NotNull LocalDateTime inicio) {}

    public record CancelarForm(@Size(max = 300) String motivo) {}

    public record RecusaForm(@Size(max = 300) String motivo) {}

    public record HorarioForm(@Min(1) @Max(7) int diaSemana, @NotNull LocalTime inicio, @NotNull LocalTime fim) {}

    public record ConfigAgenda(boolean ativo, @Min(0) @Max(720) int antecedenciaHoras, @Min(1) @Max(365) int horizonteDias,
                               @Min(0) @Max(240) int intervaloMin, @Min(15) @Max(240) int passoMin,
                               @Min(0) @Max(100) int sinalPercentual, @Min(1) @Max(168) int reservaHoras,
                               @Min(0) @Max(720) int remarcacaoHoras, @Min(0) @Max(5) int remarcacoesMax,
                               @Min(0) @Max(90) int reembolsoDias, @Valid List<HorarioForm> horarios) {}

    // ================================================================ site (público)

    @GetMapping("/api/publico/agenda-online")
    public AgendaPublica agendaPublica(HttpServletRequest req) {
        Estudio e = site.resolver(req);
        List<PacoteOnline> lista = !e.isAgendaOnline() ? List.of()
                : pacotes.findByEstudioIdAndPublicoTrueAndAtivoTrueOrderByOrdem(e.getId()).stream().filter(Pacote::isAgendaOnline)
                .map(p -> new PacoteOnline(p.getId(), p.getNome(), p.getPreco(), p.getDuracaoMin(), AgendaOnlineServico.sinal(e, p.getPreco()), p.getQtdFotos()))
                .toList();
        return new AgendaPublica(e.isAgendaOnline() && !lista.isEmpty(), e.getSinalPercentual(), e.getReservaHoras(),
                e.getRemarcacaoHoras(), e.getReembolsoDias(), lista);
    }

    @GetMapping("/api/publico/agenda-online/vagas")
    public List<Dia> vagas(HttpServletRequest req, @RequestParam Long pacote, @RequestParam LocalDate de, @RequestParam LocalDate ate) {
        Estudio e = site.resolver(req);
        if (!e.isAgendaOnline()) return List.of();
        Pacote p = pacotes.findByIdAndEstudioId(pacote, e.getId()).filter(x -> x.isAtivo() && x.isPublico() && x.isAgendaOnline())
                .orElseThrow(() -> new NaoEncontrado("Pacote não disponível para agendamento online."));
        if (ate.isBefore(de) || de.plusDays(62).isBefore(ate)) throw new RegraNegocio("Período inválido.");
        return servico.vagas(e, p, de, ate, null);
    }

    @PostMapping("/api/publico/reservas")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservaPublica reservar(HttpServletRequest req, @Valid @RequestBody ReservaForm f) {
        Estudio e = site.resolver(req);
        String aparelho = Tentativas.aparelho(req);
        tentativas.verificar(Tentativas.RESERVA_APARELHO, aparelho, "Muitas pré-reservas feitas deste aparelho.");
        Reserva r = servico.reservar(e, new AgendaOnlineServico.NovaReserva(f.pacoteId(), f.inicio(), f.nome(), f.telefone(),
                f.email(), f.tipo(), f.mensagem(), f.origem(), f.codigo()));
        tentativas.erro(Tentativas.RESERVA_APARELHO, aparelho); // conta cada pré-reserva feita
        return publica(r, e);
    }

    @GetMapping("/api/publico/reservas/{token}")
    public ReservaPublica reserva(@PathVariable String token) {
        Reserva r = servico.porToken(token);
        return publica(r, estudios.findById(r.getEstudioId()).orElseThrow());
    }

    @PostMapping(value = "/api/publico/reservas/{token}/comprovante", consumes = "multipart/form-data")
    public ReservaPublica comprovante(HttpServletRequest req, @PathVariable String token, @RequestParam("arquivo") MultipartFile arquivo) {
        String aparelho = Tentativas.aparelho(req);
        tentativas.verificar(Tentativas.COMPROVANTE_APARELHO, aparelho, "Muitos envios seguidos.");
        tentativas.erro(Tentativas.COMPROVANTE_APARELHO, aparelho);
        Reserva r = servico.enviarComprovante(token, arquivo);
        return publica(r, estudios.findById(r.getEstudioId()).orElseThrow());
    }

    private ReservaPublica publica(Reserva r, Estudio e) {
        String area = null;
        if (r.getStatus() == StatusReserva.CONFIRMADA && r.getTrabalhoId() != null) {
            area = servico.trabalhoDaClienteOuNulo(r.getTrabalhoId());
        }
        return new ReservaPublica(r.getToken(), r.getNome(), r.getPacote().getNome(), r.getInicio(), r.getFim(), r.getValorTotal(), r.getValorSinal(),
                r.statusAtual(), r.getExpiraEm(), r.getComprovanteEm() != null, e.getChavePix(), e.getNome(), e.getWhatsapp(),
                r.getMotivoRecusa(), area, r.getCupomCodigo(), r.getDesconto(),
                r.getCampanhaId() != null ? campanhaRepo.findById(r.getCampanhaId()).map(Campanha::getSlug).orElse(null) : null);
    }

    // ---------------------------------------------------------------- cupom / indicação (antes de reservar)

    @GetMapping("/api/publico/descontos")
    public MarketingServico.Desconto validarCodigo(HttpServletRequest req, @RequestParam String codigo, @RequestParam(required = false) Long pacote,
                                                   @RequestParam(required = false) String campanha, @RequestParam(required = false) String telefone) {
        Estudio e = site.resolver(req);
        String aparelho = Tentativas.aparelho(req);
        tentativas.verificar(Tentativas.VERIFICACAO_APARELHO, aparelho, "Muitas tentativas.");
        BigDecimal preco;
        Long campanhaId = null;
        if (campanha != null) {
            Campanha c = marketing.porSlug(campanha);
            preco = c.getPreco();
            campanhaId = c.getId();
        } else {
            preco = pacotes.findByIdAndEstudioId(pacote, e.getId()).filter(Pacote::isAtivo).map(Pacote::getPreco)
                    .orElseThrow(() -> new NaoEncontrado("Pacote não encontrado."));
        }
        var d = marketing.avaliar(e, codigo, preco, campanhaId, telefone);
        if (!d.valido()) tentativas.erro(Tentativas.VERIFICACAO_APARELHO, aparelho);
        return d;
    }

    // ---------------------------------------------------------------- campanhas (página pública)

    @GetMapping("/api/publico/campanhas")
    public List<CampanhaResumo> campanhasAtivas(HttpServletRequest req) {
        Estudio e = site.resolver(req);
        // Só campanhas ativas com algum dia ainda por vir; mostra o próximo dia.
        LocalDate hoje = LocalDate.now();
        return marketing.campanhas(e.getId()).stream().filter(Campanha::isAtiva).map(c -> {
            LocalDate proximo = marketing.dias(c).stream().map(d -> d.getData()).filter(d -> !d.isBefore(hoje)).findFirst().orElse(null);
            return new CampanhaResumo(c.getSlug(), c.getTitulo(), c.getPreco(), c.getCapa() != null, proximo);
        }).filter(c -> c.primeiroDia() != null).toList();
    }

    @GetMapping("/api/publico/campanhas/{slug}")
    public CampanhaPublica campanha(@PathVariable String slug) {
        Campanha c = marketing.porSlug(slug);
        Estudio e = estudios.findById(c.getEstudioId()).orElseThrow();
        Map<LocalDate, String> locais = new HashMap<>();
        marketing.dias(c).forEach(d -> locais.putIfAbsent(d.getData(), d.getLocal()));
        List<DiaCampanha> dias = servico.vagasCampanha(e, c).stream().map(d -> new DiaCampanha(d.data(), locais.get(d.data()), d.horarios())).toList();
        BigDecimal sinal = c.getPreco().multiply(BigDecimal.valueOf(c.getSinalPercentual())).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        boolean futuro = marketing.dias(c).stream().anyMatch(d -> !d.getData().isBefore(LocalDate.now()));
        return new CampanhaPublica(c.getSlug(), c.getTitulo(), c.getDescricao(), c.getPreco(), sinal, c.getSinalPercentual(), c.getDuracaoMin(),
                c.getQtdFotos(), c.getPrecoFotoExtra(), c.getPrazoEntregaDias(), c.getCapa() != null, e.getNome(), e.getWhatsapp(),
                e.getReservaHoras(), dias, futuro && dias.isEmpty());
    }

    @GetMapping("/api/publico/campanhas/{slug}/capa")
    public ResponseEntity<Resource> capaCampanha(@PathVariable String slug) {
        Campanha c = marketing.porSlug(slug);
        if (c.getCapa() == null) throw new NaoEncontrado("Sem capa.");
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(java.time.Duration.ofHours(1))).contentType(MediaType.IMAGE_JPEG)
                .body(new FileSystemResource(armazenamento.caminho(c.getCapa())));
    }

    @PostMapping("/api/publico/campanhas/{slug}/reservas")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservaPublica reservarCampanha(HttpServletRequest req, @PathVariable String slug, @Valid @RequestBody CampanhaReservaForm f) {
        Campanha c = marketing.porSlug(slug);
        Estudio e = estudios.findById(c.getEstudioId()).orElseThrow();
        String aparelho = Tentativas.aparelho(req);
        tentativas.verificar(Tentativas.RESERVA_APARELHO, aparelho, "Muitas pré-reservas feitas deste aparelho.");
        Reserva r = servico.reservarCampanha(e, c, new AgendaOnlineServico.NovaReserva(null, f.inicio(), f.nome(), f.telefone(), f.email(),
                c.getTitulo(), f.mensagem(), f.origem(), f.codigo()));
        tentativas.erro(Tentativas.RESERVA_APARELHO, aparelho);
        return publica(r, e);
    }

    // ---------------------------------------------------------------- área da cliente

    @GetMapping("/api/publico/cliente/{token}")
    public AreaCliente areaCliente(@PathVariable String token) {
        Trabalho t = servico.trabalhoDaCliente(token);
        Estudio e = estudios.findById(t.getEstudioId()).orElseThrow();
        List<ParcelaCliente> ps = parcelas.findByTrabalhoIdOrderByVencimentoAscIdAsc(t.getId()).stream()
                .map(p -> new ParcelaCliente(p.getDescricao(), p.getValor(), p.getVencimento(), p.paga())).toList();
        boolean galeria = t.getStatus() == StatusTrabalho.SELECAO || t.getStatus() == StatusTrabalho.EDICAO
                || t.getStatus() == StatusTrabalho.ENTREGUE;
        ContratoAssinatura contrato = t.getStatus() == StatusTrabalho.CANCELADO ? null : assinaturas.atual(t.getId());
        return new AreaCliente(t.getCliente().getNome(), t.getTitulo(), t.getPacote() != null ? t.getPacote().getNome() : null,
                t.getTipo(), t.getDataTrabalho(), t.getDuracaoMin(), t.getLocalTrabalho(), t.getStatus(), t.getCanceladoEm(),
                servico.regras(t), ps, galeria ? t.getGaleriaToken() : null, galeria, e.getNome(), e.getWhatsapp(), e.getChavePix(),
                e.getRemarcacaoHoras(), e.getReembolsoDias(),
                contrato != null ? contrato.getToken() : null, contrato != null ? contrato.getStatus().name() : null,
                e.isIndicacaoAtiva() ? new Indicar(marketing.codigoDaCliente(t.getCliente()), e.getIndicacaoDesconto(), e.getIndicacaoRecompensa(),
                        marketing.indicacoesDaCliente(t.getCliente().getId()).size()) : null);
    }

    @GetMapping("/api/publico/cliente/{token}/vagas")
    public List<Dia> vagasRemarcar(@PathVariable String token, @RequestParam LocalDate de, @RequestParam LocalDate ate) {
        Trabalho t = servico.trabalhoDaCliente(token);
        if (!servico.regras(t).podeRemarcar() || t.getPacote() == null) return List.of();
        if (ate.isBefore(de) || de.plusDays(62).isBefore(ate)) throw new RegraNegocio("Período inválido.");
        return servico.vagas(estudios.findById(t.getEstudioId()).orElseThrow(), t.getPacote(), de, ate, t.getId());
    }

    @PostMapping("/api/publico/cliente/{token}/remarcar")
    public AreaCliente remarcar(@PathVariable String token, @Valid @RequestBody RemarcarForm f) {
        servico.remarcar(token, f.inicio());
        // Contrato ainda não assinado acompanha a nova data.
        Trabalho t = servico.trabalhoDaCliente(token);
        ContratoAssinatura c = assinaturas.atual(t.getId());
        if (c != null && c.getStatus() == ContratoAssinatura.Status.PENDENTE) assinaturas.gerar(t.getEstudioId(), t.getId());
        return areaCliente(token);
    }

    @PostMapping("/api/publico/cliente/{token}/cancelar")
    public AreaCliente cancelar(@PathVariable String token, @Valid @RequestBody(required = false) CancelarForm f) {
        servico.cancelar(token, f == null ? null : f.motivo());
        return areaCliente(token);
    }

    // ================================================================ painel

    @GetMapping("/api/reservas")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ReservaDto> listar() {
        return reservas.findByEstudioIdOrderByCriadoEmDesc(UsuarioLogado.estudio()).stream().map(ReservaDto::de)
                .sorted(Comparator.comparing((ReservaDto r) -> r.status() != StatusReserva.PENDENTE).thenComparing(ReservaDto::inicio))
                .toList();
    }

    @GetMapping("/api/reservas/{id}/comprovante")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Resource> verComprovante(@PathVariable Long id) {
        Reserva r = reservas.findByIdAndEstudioId(id, UsuarioLogado.estudio()).orElseThrow(() -> new NaoEncontrado("Reserva não encontrada"));
        if (r.getComprovante() == null) throw new NaoEncontrado("A cliente ainda não enviou o comprovante.");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(r.getComprovanteTipo()))
                .body(new FileSystemResource(armazenamento.caminho(r.getComprovante())));
    }

    @PostMapping("/api/reservas/{id}/confirmacao")
    @PreAuthorize("hasRole('ADMIN')")
    public ReservaDto confirmar(@PathVariable Long id) {
        Reserva r = servico.confirmar(UsuarioLogado.estudio(), id);
        assinaturas.gerarSePossivel(UsuarioLogado.estudio(), r.getTrabalhoId());
        return ReservaDto.de(r);
    }

    @PostMapping("/api/reservas/{id}/recusa")
    @PreAuthorize("hasRole('ADMIN')")
    public ReservaDto recusar(@PathVariable Long id, @Valid @RequestBody(required = false) RecusaForm f) {
        return ReservaDto.de(servico.recusar(UsuarioLogado.estudio(), id, f == null ? null : f.motivo()));
    }

    @GetMapping("/api/estudio/agenda-online")
    @PreAuthorize("hasRole('ADMIN')")
    public ConfigAgenda config() {
        Estudio e = estudios.findById(UsuarioLogado.estudio()).orElseThrow();
        return new ConfigAgenda(e.isAgendaOnline(), e.getAntecedenciaHoras(), e.getHorizonteDias(), e.getIntervaloMin(),
                e.getPassoMin(), e.getSinalPercentual(), e.getReservaHoras(), e.getRemarcacaoHoras(), e.getRemarcacoesMax(),
                e.getReembolsoDias(), horarios.findByEstudioIdOrderByDiaSemanaAscInicioAsc(e.getId()).stream()
                .map(h -> new HorarioForm(h.getDiaSemana(), h.getInicio(), h.getFim())).toList());
    }

    @PutMapping("/api/estudio/agenda-online")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ConfigAgenda salvarConfig(@Valid @RequestBody ConfigAgenda c) {
        Estudio e = estudios.findById(UsuarioLogado.estudio()).orElseThrow();
        List<HorarioForm> hs = c.horarios() == null ? List.of() : c.horarios();
        for (HorarioForm h : hs) {
            if (!h.fim().isAfter(h.inicio())) throw new RegraNegocio("Em cada horário de atendimento, o fim deve ser depois do início.");
        }
        if (c.ativo() && hs.isEmpty()) throw new RegraNegocio("Cadastre pelo menos um horário de atendimento para ligar a agenda online.");
        e.setAgendaOnline(c.ativo());
        e.setAntecedenciaHoras(c.antecedenciaHoras());
        e.setHorizonteDias(c.horizonteDias());
        e.setIntervaloMin(c.intervaloMin());
        e.setPassoMin(c.passoMin());
        e.setSinalPercentual(c.sinalPercentual());
        e.setReservaHoras(c.reservaHoras());
        e.setRemarcacaoHoras(c.remarcacaoHoras());
        e.setRemarcacoesMax(c.remarcacoesMax());
        e.setReembolsoDias(c.reembolsoDias());
        horarios.deleteByEstudioId(e.getId());
        horarios.flush();
        for (HorarioForm h : hs) {
            HorarioAtendimento novo = new HorarioAtendimento();
            novo.setEstudioId(e.getId());
            novo.setDiaSemana(h.diaSemana());
            novo.setInicio(h.inicio());
            novo.setFim(h.fim());
            horarios.save(novo);
        }
        estudios.save(e);
        return config();
    }
}
