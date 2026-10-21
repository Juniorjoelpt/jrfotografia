package br.com.jrfotografia.api;

import br.com.jrfotografia.config.UsuarioLogado;
import br.com.jrfotografia.dominio.Agendamento.Reserva;
import br.com.jrfotografia.dominio.Enums.StatusReserva;
import br.com.jrfotografia.dominio.Estudio;
import br.com.jrfotografia.dominio.Marketing.*;
import br.com.jrfotografia.dominio.Repositorios.*;
import br.com.jrfotografia.servico.MarketingServico;
import br.com.jrfotografia.servico.MarketingServico.CampanhaForm;
import br.com.jrfotografia.servico.MarketingServico.CupomForm;
import br.com.jrfotografia.servico.MarketingServico.DiaForm;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
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
import java.util.List;

/** Fase 3 · bloco 5 (painel, administrador): campanhas, cupons e indicações. */
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class MarketingControle {

    private final MarketingServico servico;
    private final ReservaRepo reservas;
    private final ClienteRepo clientes;
    private final EstudioRepo estudios;
    private final br.com.jrfotografia.servico.Armazenamento armazenamento;

    public MarketingControle(MarketingServico servico, ReservaRepo reservas, ClienteRepo clientes, EstudioRepo estudios,
                             br.com.jrfotografia.servico.Armazenamento armazenamento) {
        this.servico = servico;
        this.reservas = reservas;
        this.clientes = clientes;
        this.estudios = estudios;
        this.armazenamento = armazenamento;
    }

    // ---------------------------------------------------------------- DTOs
    public record DiaDto(LocalDate data, LocalTime inicio, LocalTime fim, String local) {}

    public record CampanhaEntrada(@NotBlank @Size(max = 120) String titulo, @Size(max = 80) String slug, @Size(max = 4000) String descricao,
                                  @NotNull BigDecimal preco, @Min(0) @Max(100) Integer sinalPercentual, @Min(5) @Max(480) Integer duracaoMin,
                                  @Min(0) @Max(120) Integer intervaloMin, Integer qtdFotos, BigDecimal precoFotoExtra,
                                  @Min(1) @Max(120) Integer prazoEntregaDias, Boolean ativa, @NotEmpty List<DiaDto> dias) {}

    public record CampanhaDto(Long id, String slug, String titulo, String descricao, BigDecimal preco, int sinalPercentual, int duracaoMin,
                              int intervaloMin, Integer qtdFotos, BigDecimal precoFotoExtra, int prazoEntregaDias, boolean capa, boolean ativa,
                              List<DiaDto> dias, long reservas, long confirmadas, BigDecimal faturamento) {}

    public record CupomEntrada(@NotBlank @Size(max = 30) String codigo, @Size(max = 120) String descricao, @NotNull TipoCupom tipo,
                               @NotNull BigDecimal valor, LocalDate validade, Integer usosMax, Long campanhaId, Boolean ativo) {}

    public record CupomDto(Long id, String codigo, String descricao, TipoCupom tipo, BigDecimal valor, LocalDate validade, Integer usosMax,
                           int usos, Long campanhaId, boolean ativo, boolean vencido) {
        static CupomDto de(Cupom c) {
            return new CupomDto(c.getId(), c.getCodigo(), c.getDescricao(), c.getTipo(), c.getValor(), c.getValidade(), c.getUsosMax(), c.getUsos(),
                    c.getCampanhaId(), c.isAtivo(), c.getValidade() != null && c.getValidade().isBefore(LocalDate.now()));
        }
    }

    public record IndicacaoDto(Long id, Long indicadorId, String indicador, String indicadorTelefone, String indicado, Long trabalhoId,
                               BigDecimal recompensa, StatusIndicacao status, LocalDateTime criadoEm, LocalDateTime recompensadaEm) {}

    public record ConfigIndicacao(boolean ativa, @Min(0) @Max(100) int desconto, @NotNull BigDecimal recompensa) {}

    public record StatusIndicacaoForm(@NotNull StatusIndicacao status) {}

    // ================================================================ campanhas

    @GetMapping("/api/campanhas")
    @Transactional(readOnly = true)
    public List<CampanhaDto> campanhas() {
        Long est = UsuarioLogado.estudio();
        List<Reserva> todas = reservas.findByEstudioIdOrderByCriadoEmDesc(est);
        return servico.campanhas(est).stream().map(c -> dto(c, todas)).toList();
    }

    @PostMapping("/api/campanhas")
    @Transactional
    public CampanhaDto nova(@Valid @RequestBody CampanhaEntrada f) {
        Long est = UsuarioLogado.estudio();
        return dto(servico.salvarCampanha(est, null, form(f)), reservas.findByEstudioIdOrderByCriadoEmDesc(est));
    }

    @PutMapping("/api/campanhas/{id}")
    @Transactional
    public CampanhaDto editar(@PathVariable Long id, @Valid @RequestBody CampanhaEntrada f) {
        Long est = UsuarioLogado.estudio();
        return dto(servico.salvarCampanha(est, id, form(f)), reservas.findByEstudioIdOrderByCriadoEmDesc(est));
    }

    @PostMapping(value = "/api/campanhas/{id}/capa", consumes = "multipart/form-data")
    @Transactional
    public CampanhaDto capa(@PathVariable Long id, @RequestParam("arquivo") MultipartFile arquivo) {
        Long est = UsuarioLogado.estudio();
        return dto(servico.salvarCapa(est, id, arquivo), reservas.findByEstudioIdOrderByCriadoEmDesc(est));
    }

    @GetMapping("/api/campanhas/{id}/capa")
    public ResponseEntity<Resource> verCapa(@PathVariable Long id) {
        Campanha c = servico.campanha(UsuarioLogado.estudio(), id);
        if (c.getCapa() == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_JPEG)
                .body(new FileSystemResource(armazenamento.caminho(c.getCapa())));
    }

    private static CampanhaForm form(CampanhaEntrada f) {
        return new CampanhaForm(f.titulo(), f.slug(), f.descricao(), f.preco(), f.sinalPercentual() == null ? 50 : f.sinalPercentual(),
                f.duracaoMin() == null ? 20 : f.duracaoMin(), f.intervaloMin() == null ? 10 : f.intervaloMin(), f.qtdFotos(),
                f.precoFotoExtra(), f.prazoEntregaDias() == null ? 10 : f.prazoEntregaDias(), f.ativa() == null || f.ativa(),
                f.dias().stream().map(d -> new DiaForm(d.data(), d.inicio(), d.fim(), d.local())).toList());
    }

    private CampanhaDto dto(Campanha c, List<Reserva> todas) {
        List<Reserva> daCampanha = todas.stream().filter(r -> c.getId().equals(r.getCampanhaId())).toList();
        long ativas = daCampanha.stream().filter(r -> r.statusAtual() == StatusReserva.PENDENTE || r.statusAtual() == StatusReserva.CONFIRMADA).count();
        List<Reserva> confirmadas = daCampanha.stream().filter(r -> r.getStatus() == StatusReserva.CONFIRMADA).toList();
        return new CampanhaDto(c.getId(), c.getSlug(), c.getTitulo(), c.getDescricao(), c.getPreco(), c.getSinalPercentual(), c.getDuracaoMin(),
                c.getIntervaloMin(), c.getQtdFotos(), c.getPrecoFotoExtra(), c.getPrazoEntregaDias(), c.getCapa() != null, c.isAtiva(),
                servico.dias(c).stream().map(d -> new DiaDto(d.getData(), d.getInicio(), d.getFim(), d.getLocal())).toList(),
                ativas, confirmadas.size(), confirmadas.stream().map(Reserva::getValorTotal).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    // ================================================================ cupons

    @GetMapping("/api/cupons")
    public List<CupomDto> cupons() {
        return servico.cupons(UsuarioLogado.estudio()).stream().map(CupomDto::de).toList();
    }

    @PostMapping("/api/cupons")
    @Transactional
    public CupomDto novoCupom(@Valid @RequestBody CupomEntrada f) {
        return CupomDto.de(servico.salvarCupom(UsuarioLogado.estudio(), null, cupom(f)));
    }

    @PutMapping("/api/cupons/{id}")
    @Transactional
    public CupomDto editarCupom(@PathVariable Long id, @Valid @RequestBody CupomEntrada f) {
        return CupomDto.de(servico.salvarCupom(UsuarioLogado.estudio(), id, cupom(f)));
    }

    private static CupomForm cupom(CupomEntrada f) {
        return new CupomForm(f.codigo(), f.descricao(), f.tipo(), f.valor(), f.validade(), f.usosMax(), f.campanhaId(), f.ativo() == null || f.ativo());
    }

    // ================================================================ indicações

    @GetMapping("/api/indicacoes")
    @Transactional(readOnly = true)
    public List<IndicacaoDto> indicacoes() {
        return servico.indicacoes(UsuarioLogado.estudio()).stream().map(i -> {
            var c = clientes.findById(i.getIndicadorClienteId()).orElse(null);
            return new IndicacaoDto(i.getId(), i.getIndicadorClienteId(), c != null ? c.getNome() : "?", c != null ? c.getTelefone() : null,
                    i.getIndicadoNome(), i.getTrabalhoId(), i.getRecompensa(), i.getStatus(), i.getCriadoEm(), i.getRecompensadaEm());
        }).toList();
    }

    @PatchMapping("/api/indicacoes/{id}")
    @Transactional
    public ResponseEntity<Void> mudarIndicacao(@PathVariable Long id, @Valid @RequestBody StatusIndicacaoForm f) {
        servico.mudarIndicacao(UsuarioLogado.estudio(), id, f.status());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/estudio/indicacao")
    public ConfigIndicacao configIndicacao() {
        Estudio e = estudios.findById(UsuarioLogado.estudio()).orElseThrow();
        return new ConfigIndicacao(e.isIndicacaoAtiva(), e.getIndicacaoDesconto(), e.getIndicacaoRecompensa());
    }

    @PutMapping("/api/estudio/indicacao")
    @Transactional
    public ConfigIndicacao salvarIndicacao(@Valid @RequestBody ConfigIndicacao f) {
        Estudio e = servico.configurarIndicacao(UsuarioLogado.estudio(), f.ativa(), f.desconto(), f.recompensa());
        return new ConfigIndicacao(e.isIndicacaoAtiva(), e.getIndicacaoDesconto(), e.getIndicacaoRecompensa());
    }

    @GetMapping("/api/clientes/{id}/codigo-indicacao")
    @Transactional
    public java.util.Map<String, String> codigoDaCliente(@PathVariable Long id) {
        var c = clientes.findByIdAndEstudioId(id, UsuarioLogado.estudio()).orElseThrow(() -> new br.com.jrfotografia.config.Erros.NaoEncontrado("Cliente não encontrado."));
        return java.util.Map.of("codigo", servico.codigoDaCliente(c));
    }
}
