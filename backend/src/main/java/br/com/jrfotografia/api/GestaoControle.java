package br.com.jrfotografia.api;

import br.com.jrfotografia.api.Fase2Dtos.*;
import br.com.jrfotografia.config.Erros.NaoEncontrado;
import br.com.jrfotografia.config.Erros.RegraNegocio;
import br.com.jrfotografia.config.UsuarioLogado;
import br.com.jrfotografia.dominio.Estudio;
import br.com.jrfotografia.dominio.Fase2.BloqueioAgenda;
import br.com.jrfotografia.dominio.Fase2.Despesa;
import br.com.jrfotografia.dominio.Fase2.ItemAvulso;
import br.com.jrfotografia.dominio.Repositorios.*;
import br.com.jrfotografia.servico.AgendaServico;
import br.com.jrfotografia.servico.ContratoPadrao;
import br.com.jrfotografia.servico.PdfServico;
import br.com.jrfotografia.servico.RelatorioServico;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Agenda, despesas, fluxo de caixa, relatórios, recibos e ajustes da fase 2. */
@RestController
@RequestMapping("/api")
public class GestaoControle {

    private final AgendaServico agenda;
    private final BloqueioRepo bloqueios;
    private final DespesaRepo despesas;
    private final ItemAvulsoRepo itens;
    private final EstudioRepo estudios;
    private final ParcelaRepo parcelas;
    private final TrabalhoRepo trabalhos;
    private final RelatorioServico relatorios;
    private final PdfServico pdf;

    public GestaoControle(AgendaServico agenda, BloqueioRepo bloqueios, DespesaRepo despesas, ItemAvulsoRepo itens,
                          EstudioRepo estudios, ParcelaRepo parcelas, TrabalhoRepo trabalhos,
                          RelatorioServico relatorios, PdfServico pdf) {
        this.agenda = agenda;
        this.bloqueios = bloqueios;
        this.despesas = despesas;
        this.itens = itens;
        this.estudios = estudios;
        this.parcelas = parcelas;
        this.trabalhos = trabalhos;
        this.relatorios = relatorios;
        this.pdf = pdf;
    }

    // ---------------------------------------------------------------- agenda
    @GetMapping("/agenda")
    public List<AgendaServico.Evento> agenda(@RequestParam LocalDate de, @RequestParam LocalDate ate) {
        if (ate.isBefore(de) || de.plusDays(62).isBefore(ate)) throw new RegraNegocio("Período inválido.");
        return agenda.eventos(UsuarioLogado.estudio(), de.atStartOfDay(), ate.plusDays(1).atStartOfDay());
    }

    @GetMapping("/agenda/conflitos")
    public Conflitos conflitos(@RequestParam LocalDateTime inicio, @RequestParam(defaultValue = "240") int duracaoMin,
                               @RequestParam(required = false) Long ignorar) {
        return new Conflitos(agenda.conflitos(UsuarioLogado.estudio(), inicio, duracaoMin, ignorar));
    }

    @PostMapping("/agenda/bloqueios")
    @ResponseStatus(HttpStatus.CREATED)
    public AgendaServico.Evento bloquear(@Valid @RequestBody BloqueioForm f) {
        if (!f.fim().isAfter(f.inicio())) throw new RegraNegocio("O fim do bloqueio deve ser depois do início.");
        BloqueioAgenda b = new BloqueioAgenda();
        b.setEstudioId(UsuarioLogado.estudio());
        b.setInicio(f.inicio());
        b.setFim(f.fim());
        b.setMotivo(f.motivo().trim());
        bloqueios.save(b);
        return new AgendaServico.Evento(AgendaServico.TipoEvento.BLOQUEIO, b.getId(), b.getMotivo(), null,
                b.getInicio(), b.getFim(), null);
    }

    @DeleteMapping("/agenda/bloqueios/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desbloquear(@PathVariable Long id) {
        bloqueios.delete(bloqueios.findByIdAndEstudioId(id, UsuarioLogado.estudio())
                .orElseThrow(() -> new NaoEncontrado("Bloqueio não encontrado")));
    }

    // ---------------------------------------------------------------- despesas
    @GetMapping("/despesas")
    @PreAuthorize("hasRole('ADMIN')")
    public List<DespesaDto> despesas() {
        return despesas.findByEstudioIdOrderByVencimentoDesc(UsuarioLogado.estudio()).stream().map(DespesaDto::de).toList();
    }

    @PostMapping("/despesas")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public DespesaDto novaDespesa(@Valid @RequestBody DespesaForm f) {
        Despesa d = new Despesa();
        d.setEstudioId(UsuarioLogado.estudio());
        preencher(d, f);
        return DespesaDto.de(despesas.save(d));
    }

    @PutMapping("/despesas/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public DespesaDto editarDespesa(@PathVariable Long id, @Valid @RequestBody DespesaForm f) {
        Despesa d = despesa(id);
        preencher(d, f);
        return DespesaDto.de(d);
    }

    @PostMapping("/despesas/{id}/pagamento")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public DespesaDto pagarDespesa(@PathVariable Long id) {
        Despesa d = despesa(id);
        d.setPagoEm(d.getPagoEm() == null ? LocalDate.now() : null);
        return DespesaDto.de(d);
    }

    @DeleteMapping("/despesas/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void apagarDespesa(@PathVariable Long id) {
        despesas.delete(despesa(id));
    }

    private Despesa despesa(Long id) {
        return despesas.findByIdAndEstudioId(id, UsuarioLogado.estudio())
                .orElseThrow(() -> new NaoEncontrado("Despesa não encontrada"));
    }

    private void preencher(Despesa d, DespesaForm f) {
        if (f.trabalhoId() != null) {
            trabalhos.findByIdAndEstudioId(f.trabalhoId(), UsuarioLogado.estudio())
                    .orElseThrow(() -> new NaoEncontrado("Trabalho não encontrado"));
        }
        d.setDescricao(f.descricao().trim());
        d.setCategoria(f.categoria().trim());
        d.setValor(f.valor());
        d.setVencimento(f.vencimento());
        d.setPagoEm(f.pagoEm());
        d.setTrabalhoId(f.trabalhoId());
    }

    // ---------------------------------------------------------------- fluxo, relatórios e recibo
    @GetMapping("/financeiro/fluxo")
    @PreAuthorize("hasRole('ADMIN')")
    public RelatorioServico.Fluxo fluxo(@RequestParam(required = false) Integer ano) {
        return relatorios.fluxo(UsuarioLogado.estudio(), ano != null ? ano : LocalDate.now().getYear());
    }

    @GetMapping("/relatorios")
    @PreAuthorize("hasRole('ADMIN')")
    public RelatorioServico.Relatorio relatorio(@RequestParam(required = false) Integer ano) {
        return relatorios.relatorio(UsuarioLogado.estudio(), ano != null ? ano : LocalDate.now().getYear());
    }

    @GetMapping("/parcelas/{id}/recibo.pdf")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> recibo(@PathVariable Long id) {
        Long e = UsuarioLogado.estudio();
        var p = parcelas.findByIdAndEstudioId(id, e).orElseThrow(() -> new NaoEncontrado("Parcela não encontrada"));
        if (p.getPagoEm() == null) throw new RegraNegocio("O recibo só sai depois do pagamento registrado.");
        var t = trabalhos.findByIdAndEstudioId(p.getTrabalhoId(), e).orElseThrow();
        return Pdfs.resposta(pdf.recibo(p, t, estudio()), "recibo-" + p.getId() + "-" + Pdfs.nomeArquivo(t.getCliente().getNome()) + ".pdf");
    }

    // ---------------------------------------------------------------- itens avulsos
    @GetMapping("/itens-avulsos")
    public List<ItemAvulsoDto> itensAvulsos() {
        return itens.findByEstudioIdOrderByNome(UsuarioLogado.estudio()).stream().map(ItemAvulsoDto::de).toList();
    }

    @PostMapping("/itens-avulsos")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ItemAvulsoDto novoItem(@Valid @RequestBody ItemAvulsoForm f) {
        ItemAvulso i = new ItemAvulso();
        i.setEstudioId(UsuarioLogado.estudio());
        i.setNome(f.nome().trim());
        i.setPreco(f.preco());
        i.setAtivo(f.ativo() == null || f.ativo());
        return ItemAvulsoDto.de(itens.save(i));
    }

    @PutMapping("/itens-avulsos/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ItemAvulsoDto editarItem(@PathVariable Long id, @Valid @RequestBody ItemAvulsoForm f) {
        ItemAvulso i = item(id);
        i.setNome(f.nome().trim());
        i.setPreco(f.preco());
        if (f.ativo() != null) i.setAtivo(f.ativo());
        return ItemAvulsoDto.de(i);
    }

    @DeleteMapping("/itens-avulsos/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void apagarItem(@PathVariable Long id) {
        itens.delete(item(id));
    }

    private ItemAvulso item(Long id) {
        return itens.findByIdAndEstudioId(id, UsuarioLogado.estudio())
                .orElseThrow(() -> new NaoEncontrado("Item não encontrado"));
    }

    // ---------------------------------------------------------------- modelo de contrato
    @GetMapping("/estudio/contrato")
    @PreAuthorize("hasRole('ADMIN')")
    public ContratoModelo contrato() {
        Estudio e = estudio();
        boolean padrao = e.getModeloContrato() == null || e.getModeloContrato().isBlank();
        return new ContratoModelo(padrao ? ContratoPadrao.TEXTO : e.getModeloContrato(), padrao, ContratoPadrao.CAMPOS,
                e.getResponsavel(), e.getDocumento());
    }

    /** Texto vazio volta ao modelo padrão. */
    @PutMapping("/estudio/contrato")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ContratoModelo salvarContrato(@Valid @RequestBody ContratoForm f) {
        Estudio e = estudio();
        String texto = f.texto() == null || f.texto().isBlank() || f.texto().strip().equals(ContratoPadrao.TEXTO.strip())
                ? null : f.texto().strip();
        e.setModeloContrato(texto);
        e.setResponsavel(vazioNulo(f.responsavel()));
        e.setDocumento(vazioNulo(f.documento()));
        estudios.save(e);
        return contrato();
    }

    private Estudio estudio() {
        return estudios.findById(UsuarioLogado.estudio()).orElseThrow();
    }

    private static String vazioNulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
