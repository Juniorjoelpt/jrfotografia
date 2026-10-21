package br.com.jrfotografia.api;

import br.com.jrfotografia.api.Dtos.*;
import br.com.jrfotografia.config.UsuarioLogado;
import br.com.jrfotografia.dominio.Enums.StatusTrabalho;
import br.com.jrfotografia.dominio.Parcela;
import br.com.jrfotografia.dominio.Repositorios.ParcelaRepo;
import br.com.jrfotografia.dominio.Repositorios.PedidoRepo;
import br.com.jrfotografia.dominio.Repositorios.TrabalhoRepo;
import br.com.jrfotografia.dominio.Trabalho;
import br.com.jrfotografia.servico.TrabalhoServico;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Recebimentos e o painel inicial. Valores em dinheiro só para o administrador. */
@RestController
@RequestMapping("/api")
public class FinanceiroControle {

    private final ParcelaRepo parcelas;
    private final TrabalhoRepo trabalhos;
    private final PedidoRepo pedidos;
    private final TrabalhoServico servico;

    public FinanceiroControle(ParcelaRepo parcelas, TrabalhoRepo trabalhos, PedidoRepo pedidos, TrabalhoServico servico) {
        this.parcelas = parcelas;
        this.trabalhos = trabalhos;
        this.pedidos = pedidos;
        this.servico = servico;
    }

    @PostMapping("/parcelas/{id}/pagamento")
    @PreAuthorize("hasRole('ADMIN')")
    public ParcelaDto pagar(@PathVariable Long id, @RequestBody(required = false) PagamentoForm f) {
        var p = servico.registrarPagamento(UsuarioLogado.estudio(), id,
                f == null ? null : f.data(), f == null ? null : f.forma());
        br.com.jrfotografia.servico.AuditoriaServico.detalhe(p.getDescricao() + " · R$ " + p.getValor());
        return ParcelaDto.de(p);
    }

    @DeleteMapping("/parcelas/{id}/pagamento")
    @PreAuthorize("hasRole('ADMIN')")
    public ParcelaDto desfazer(@PathVariable Long id) {
        var p = servico.desfazerPagamento(UsuarioLogado.estudio(), id);
        br.com.jrfotografia.servico.AuditoriaServico.detalhe(p.getDescricao() + " · R$ " + p.getValor());
        return ParcelaDto.de(p);
    }

    @GetMapping("/financeiro/a-receber")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ContaReceber> aReceber() {
        Long e = UsuarioLogado.estudio();
        var abertas = parcelas.findByEstudioIdAndPagoEmIsNullOrderByVencimento(e);
        Map<Long, Trabalho> porId = trabalhos.findAllById(abertas.stream().map(Parcela::getTrabalhoId).distinct().toList())
                .stream().collect(Collectors.toMap(Trabalho::getId, Function.identity()));
        LocalDate hoje = LocalDate.now();
        return abertas.stream()
                .filter(p -> porId.containsKey(p.getTrabalhoId())
                        && porId.get(p.getTrabalhoId()).getStatus() != StatusTrabalho.CANCELADO)
                .map(p -> {
                    Trabalho t = porId.get(p.getTrabalhoId());
                    return new ContaReceber(ParcelaDto.de(p), t.getTitulo(), t.getCliente().getNome(),
                            t.getCliente().getTelefone(), p.getVencimento() != null && p.getVencimento().isBefore(hoje));
                }).toList();
    }

    @GetMapping("/painel")
    public Painel painel() {
        Long e = UsuarioLogado.estudio();
        boolean admin = UsuarioLogado.atual().admin();
        LocalDate hoje = LocalDate.now();
        LocalDateTime inicioHoje = hoje.atStartOfDay();

        var ativos = trabalhos.findByEstudioIdAndStatusIn(e, EnumSet.of(StatusTrabalho.AGENDADO,
                StatusTrabalho.FOTOGRAFADO, StatusTrabalho.SELECAO, StatusTrabalho.EDICAO));
        var resumos = ativos.stream().map(TrabalhoResumo::de).toList();

        var proximos = resumos.stream()
                .filter(t -> t.dataTrabalho() != null && !t.dataTrabalho().isBefore(inicioHoje)
                        && t.dataTrabalho().isBefore(inicioHoje.plusDays(8)))
                .sorted(Comparator.comparing(TrabalhoResumo::dataTrabalho)).toList();
        var entregas = resumos.stream()
                .filter(t -> t.prazoEntrega() != null && !t.atrasado() && !t.prazoEntrega().isAfter(hoje.plusDays(3)))
                .sorted(Comparator.comparing(TrabalhoResumo::prazoEntrega)).toList();
        var atrasados = resumos.stream().filter(TrabalhoResumo::atrasado)
                .sorted(Comparator.comparing(TrabalhoResumo::prazoEntrega)).toList();

        BigDecimal aReceber = null, vencido = null, recebido = null;
        if (admin) {
            var abertas = parcelas.findByEstudioIdAndPagoEmIsNullOrderByVencimento(e);
            aReceber = soma(abertas);
            vencido = soma(abertas.stream().filter(p -> p.getVencimento() != null && p.getVencimento().isBefore(hoje)).toList());
            recebido = soma(parcelas.findByEstudioIdAndPagoEmBetween(e, hoje.withDayOfMonth(1), hoje));
        }
        return new Painel(proximos, entregas, atrasados,
                ativos.stream().filter(t -> t.getStatus() == StatusTrabalho.SELECAO).count(),
                ativos.stream().filter(t -> t.getStatus() == StatusTrabalho.EDICAO).count(),
                pedidos.countByEstudioIdAndStatus(e, br.com.jrfotografia.dominio.Enums.StatusPedido.NOVO),
                aReceber, vencido, recebido);
    }

    private static BigDecimal soma(List<Parcela> lista) {
        return lista.stream().map(Parcela::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
