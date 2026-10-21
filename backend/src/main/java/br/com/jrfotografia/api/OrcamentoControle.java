package br.com.jrfotografia.api;

import br.com.jrfotografia.servico.EmailAvisos;
import br.com.jrfotografia.api.Fase2Dtos.*;
import br.com.jrfotografia.config.Erros.RegraNegocio;
import br.com.jrfotografia.config.UsuarioLogado;
import br.com.jrfotografia.dominio.Enums.StatusOrcamento;
import br.com.jrfotografia.dominio.Estudio;
import br.com.jrfotografia.dominio.Orcamento;
import br.com.jrfotografia.dominio.Repositorios.*;
import br.com.jrfotografia.servico.OrcamentoServico;
import br.com.jrfotografia.servico.PdfServico;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Orçamentos no painel (só administrador) e a página pública que o cliente abre pelo link. */
@RestController
public class OrcamentoControle {

    private final EmailAvisos avisos;
    private final OrcamentoServico servico;
    private final OrcamentoRepo orcamentos;
    private final EstudioRepo estudios;
    private final TrabalhoRepo trabalhos;
    private final ParcelaRepo parcelas;
    private final PdfServico pdf;

    public OrcamentoControle(OrcamentoServico servico, OrcamentoRepo orcamentos, EstudioRepo estudios,
                             TrabalhoRepo trabalhos, ParcelaRepo parcelas, PdfServico pdf, EmailAvisos avisos) {
        this.avisos = avisos;
        this.servico = servico;
        this.orcamentos = orcamentos;
        this.estudios = estudios;
        this.trabalhos = trabalhos;
        this.parcelas = parcelas;
        this.pdf = pdf;
    }

    // ---------------------------------------------------------------- painel
    @GetMapping("/api/orcamentos")
    @PreAuthorize("hasRole('ADMIN')")
    public List<OrcamentoDto> listar() {
        return orcamentos.findByEstudioIdOrderByCriadoEmDesc(UsuarioLogado.estudio()).stream().map(OrcamentoDto::de).toList();
    }

    @GetMapping("/api/orcamentos/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public OrcamentoDto ver(@PathVariable Long id) {
        return OrcamentoDto.de(servico.buscar(UsuarioLogado.estudio(), id));
    }

    @PostMapping("/api/orcamentos")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public OrcamentoDto criar(@Valid @RequestBody OrcamentoForm f) {
        return OrcamentoDto.de(servico.criar(UsuarioLogado.estudio(), dados(f)));
    }

    @PutMapping("/api/orcamentos/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public OrcamentoDto editar(@PathVariable Long id, @Valid @RequestBody OrcamentoForm f) {
        return OrcamentoDto.de(servico.editar(UsuarioLogado.estudio(), id, dados(f)));
    }

    /** Marca como enviado; o envio em si sai pelo WhatsApp com o link pronto. */
    @PostMapping("/api/orcamentos/{id}/enviado")
    @PreAuthorize("hasRole('ADMIN')")
    public OrcamentoDto enviado(@PathVariable Long id) {
        return OrcamentoDto.de(servico.marcarEnviado(UsuarioLogado.estudio(), id));
    }

    @DeleteMapping("/api/orcamentos/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void apagar(@PathVariable Long id) {
        Orcamento o = servico.buscar(UsuarioLogado.estudio(), id);
        if (o.getStatus() == StatusOrcamento.ACEITO) throw new RegraNegocio("Orçamento aceito não pode ser apagado.");
        orcamentos.delete(o);
    }

    @GetMapping("/api/orcamentos/{id}/pdf")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> pdfPainel(@PathVariable Long id) {
        Orcamento o = servico.buscar(UsuarioLogado.estudio(), id);
        return pdfDe(o);
    }

    // ---------------------------------------------------------------- público (pelo link)
    @GetMapping("/api/publico/orcamentos/{token}")
    public OrcamentoPublico publico(@PathVariable String token) {
        return publicoDe(servico.porToken(token));
    }

    @PostMapping("/api/publico/orcamentos/{token}/aceite")
    public OrcamentoPublico aceitar(@PathVariable String token) {
        Orcamento o = servico.aceitar(token);
        avisos.orcamentoRespondido(o, true);
        return publicoDe(o);
    }

    @PostMapping("/api/publico/orcamentos/{token}/recusa")
    public OrcamentoPublico recusar(@PathVariable String token) {
        Orcamento o = servico.recusar(token);
        avisos.orcamentoRespondido(o, false);
        return publicoDe(o);
    }

    /** Manda o link do orçamento por e-mail (além do WhatsApp). */
    @PostMapping("/api/orcamentos/{id}/email")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public java.util.Map<String, Boolean> enviarPorEmail(@PathVariable Long id) {
        Orcamento o = orcamentos.findByIdAndEstudioId(id, br.com.jrfotografia.config.UsuarioLogado.estudio())
                .orElseThrow(() -> new br.com.jrfotografia.config.Erros.NaoEncontrado("Orçamento não encontrado"));
        if (o.getEmail() == null || o.getEmail().isBlank()) throw new br.com.jrfotografia.config.Erros.RegraNegocio("O orçamento não tem e-mail da cliente.");
        if (!estudios.findById(o.getEstudioId()).orElseThrow().isEmailAtivo()) {
            throw new br.com.jrfotografia.config.Erros.RegraNegocio("Os e-mails estão desligados. Ligue em Ajustes › E-mail.");
        }
        return java.util.Map.of("enviado", avisos.orcamentoParaCliente(o));
    }

    @GetMapping("/api/publico/orcamentos/{token}/pdf")
    public ResponseEntity<byte[]> pdfPublico(@PathVariable String token) {
        return pdfDe(servico.porToken(token));
    }

    /** Depois do aceite, o cliente baixa o contrato já preenchido. */
    @GetMapping("/api/publico/orcamentos/{token}/contrato.pdf")
    public ResponseEntity<byte[]> contratoPublico(@PathVariable String token) {
        Orcamento o = servico.porToken(token);
        if (o.getStatus() != StatusOrcamento.ACEITO || o.getTrabalhoId() == null) {
            throw new RegraNegocio("O contrato fica disponível depois do aceite.");
        }
        var t = trabalhos.findById(o.getTrabalhoId()).orElseThrow();
        byte[] corpo = pdf.contrato(t, estudio(o), parcelas.findByTrabalhoIdOrderByVencimentoAscIdAsc(t.getId()));
        return Pdfs.resposta(corpo, "contrato-" + Pdfs.nomeArquivo(o.getNomeCliente()) + ".pdf");
    }

    // ----------------------------------------------------------------
    private ResponseEntity<byte[]> pdfDe(Orcamento o) {
        return Pdfs.resposta(pdf.orcamento(o, estudio(o)),
                "orcamento-" + o.getNumero() + "-" + Pdfs.nomeArquivo(o.getNomeCliente()) + ".pdf");
    }

    private Estudio estudio(Orcamento o) {
        return estudios.findById(o.getEstudioId()).orElseThrow();
    }

    private OrcamentoPublico publicoDe(Orcamento o) {
        Estudio e = estudio(o);
        var p = o.getPacote();
        StatusOrcamento st = o.statusAtual();
        return new OrcamentoPublico(o.getNumero(), o.getNomeCliente(), e.getNome(), e.getWhatsapp(), e.getCidade(),
                p != null ? p.getNome() : null, p != null ? p.listaItens() : List.of(),
                p != null ? p.getPrazoEntregaDias() : null, p != null ? p.getPrecoFotoExtra() : null,
                o.getTipo(), o.getDataPrevista(), o.getLocalPrevisto(),
                o.getItens().stream().map(ItemDto::de).toList(), o.subtotal(), o.getDesconto(), o.getValorTotal(),
                PdfServico.condicoes(o.getFormaPagamento(), o.getValorTotal(), o.getValorSinal(), o.getNumParcelas()),
                o.getValidade(), o.getObservacoes(), st, st == StatusOrcamento.ACEITO && o.getTrabalhoId() != null);
    }

    private static OrcamentoServico.Dados dados(OrcamentoForm f) {
        List<OrcamentoServico.Item> itens = f.itens() == null ? List.of()
                : f.itens().stream().map(i -> new OrcamentoServico.Item(i.descricao(), i.quantidade(), i.valorUnit())).toList();
        return new OrcamentoServico.Dados(f.clienteId(), f.pedidoId(), f.nomeCliente(), f.telefone(), f.email(),
                f.pacoteId(), f.tipo(), f.dataPrevista(), f.localPrevisto(), itens, f.desconto(), f.formaPagamento(),
                f.valorSinal(), f.numParcelas(), f.validade(), f.observacoes());
    }
}
