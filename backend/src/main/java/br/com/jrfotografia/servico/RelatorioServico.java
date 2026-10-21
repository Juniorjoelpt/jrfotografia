package br.com.jrfotografia.servico;

import br.com.jrfotografia.dominio.Enums.Origem;
import br.com.jrfotografia.dominio.Enums.StatusOrcamento;
import br.com.jrfotografia.dominio.Enums.StatusPedido;
import br.com.jrfotografia.dominio.Enums.StatusTrabalho;
import br.com.jrfotografia.dominio.Fase2.Despesa;
import br.com.jrfotografia.dominio.Orcamento;
import br.com.jrfotografia.dominio.Parcela;
import br.com.jrfotografia.dominio.Repositorios.*;
import br.com.jrfotografia.dominio.Trabalho;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** Fluxo de caixa e relatórios de vendas, calculados na hora a partir das parcelas, despesas e trabalhos. */
@Service
public class RelatorioServico {

    private final ParcelaRepo parcelas;
    private final DespesaRepo despesas;
    private final TrabalhoRepo trabalhos;
    private final OrcamentoRepo orcamentos;
    private final PedidoRepo pedidos;

    public RelatorioServico(ParcelaRepo parcelas, DespesaRepo despesas, TrabalhoRepo trabalhos, OrcamentoRepo orcamentos,
                            PedidoRepo pedidos) {
        this.parcelas = parcelas;
        this.despesas = despesas;
        this.trabalhos = trabalhos;
        this.orcamentos = orcamentos;
        this.pedidos = pedidos;
    }

    // ---------------------------------------------------------------- fluxo de caixa
    public record MesCaixa(int mes, BigDecimal entrou, BigDecimal saiu, BigDecimal aEntrar, BigDecimal aSair, BigDecimal saldo) {}

    public record Fluxo(int ano, List<MesCaixa> meses, BigDecimal entrouAno, BigDecimal saiuAno, BigDecimal saldoAno,
                        BigDecimal aEntrarAno, BigDecimal aSairAno) {}

    /** Por mês: o que entrou e saiu de fato (pelas datas de pagamento) e o que está previsto (pelos vencimentos). */
    public Fluxo fluxo(Long estudioId, int ano) {
        LocalDate ini = LocalDate.of(ano, 1, 1), fim = LocalDate.of(ano, 12, 31);
        List<Parcela> todas = parcelas.findByEstudioId(estudioId);
        Set<Long> cancelados = trabalhos.findByEstudioIdAndStatusIn(estudioId, EnumSet.of(StatusTrabalho.CANCELADO))
                .stream().map(Trabalho::getId).collect(Collectors.toSet());
        List<Despesa> desp = despesas.findByEstudioIdAndVencimentoBetweenOrderByVencimento(estudioId, ini.minusYears(1), fim.plusYears(1));

        List<MesCaixa> meses = new ArrayList<>();
        BigDecimal ent = BigDecimal.ZERO, sai = BigDecimal.ZERO, aEnt = BigDecimal.ZERO, aSai = BigDecimal.ZERO;
        for (int m = 1; m <= 12; m++) {
            final int mes = m;
            BigDecimal entrou = soma(todas.stream().filter(p -> p.getPagoEm() != null && noMes(p.getPagoEm(), ano, mes)).map(Parcela::getValor));
            BigDecimal aEntrar = soma(todas.stream().filter(p -> p.getPagoEm() == null && !cancelados.contains(p.getTrabalhoId())
                    && p.getVencimento() != null && noMes(p.getVencimento(), ano, mes)).map(Parcela::getValor));
            BigDecimal saiu = soma(desp.stream().filter(d -> d.getPagoEm() != null && noMes(d.getPagoEm(), ano, mes)).map(Despesa::getValor));
            BigDecimal aSair = soma(desp.stream().filter(d -> d.getPagoEm() == null && noMes(d.getVencimento(), ano, mes)).map(Despesa::getValor));
            meses.add(new MesCaixa(m, entrou, saiu, aEntrar, aSair, entrou.subtract(saiu)));
            ent = ent.add(entrou); sai = sai.add(saiu); aEnt = aEnt.add(aEntrar); aSai = aSai.add(aSair);
        }
        return new Fluxo(ano, meses, ent, sai, ent.subtract(sai), aEnt, aSai);
    }

    // ---------------------------------------------------------------- relatórios de vendas
    public record Fatia(String nome, long quantidade, BigDecimal valor) {}

    public record Relatorio(int ano, BigDecimal[] faturamentoMensal, BigDecimal faturamentoAno, long trabalhos,
                            BigDecimal ticketMedio, List<Fatia> porTipo, List<Fatia> porOrigem,
                            long pedidos, long pedidosFechados, long orcamentosEnviados, long orcamentosAceitos,
                            BigDecimal conversaoOrcamentos, BigDecimal conversaoPedidos, List<Fatia> despesasPorCategoria) {}

    public Relatorio relatorio(Long estudioId, int ano) {
        Fluxo f = fluxo(estudioId, ano);
        BigDecimal[] mensal = f.meses().stream().map(MesCaixa::entrou).toArray(BigDecimal[]::new);

        LocalDateTime ini = LocalDate.of(ano, 1, 1).atStartOfDay(), fim = LocalDate.of(ano + 1, 1, 1).atStartOfDay();
        List<Trabalho> doAno = trabalhos.findByEstudioIdAndDataTrabalhoBetweenOrderByDataTrabalho(estudioId, ini, fim)
                .stream().filter(t -> t.getStatus() != StatusTrabalho.CANCELADO).toList();
        BigDecimal totalTrabalhos = soma(doAno.stream().map(Trabalho::getValorTotal));
        BigDecimal ticket = doAno.isEmpty() ? BigDecimal.ZERO : totalTrabalhos.divide(BigDecimal.valueOf(doAno.size()), 2, RoundingMode.HALF_UP);

        List<Fatia> porTipo = agrupar(doAno, t -> t.getPacote() != null ? t.getPacote().getNome() : (t.getTipo() != null ? t.getTipo() : "Sem pacote"));
        List<Fatia> porOrigem = agrupar(doAno, t -> nomeOrigem(t.getCliente().getOrigem()));

        var peds = pedidos.findByEstudioIdOrderByCriadoEmDesc(estudioId).stream()
                .filter(p -> p.getCriadoEm().getYear() == ano).toList();
        long fechados = peds.stream().filter(p -> p.getStatus() == StatusPedido.FECHADO).count();
        List<Orcamento> orcs = orcamentos.findByEstudioIdOrderByCriadoEmDesc(estudioId).stream()
                .filter(o -> o.getCriadoEm().getYear() == ano && o.getStatus() != StatusOrcamento.RASCUNHO).toList();
        long aceitos = orcs.stream().filter(o -> o.getStatus() == StatusOrcamento.ACEITO).count();

        List<Fatia> despCat = despesas.findByEstudioIdAndVencimentoBetweenOrderByVencimento(estudioId, ini.toLocalDate(), fim.toLocalDate().minusDays(1))
                .stream().collect(Collectors.groupingBy(Despesa::getCategoria, LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream().map(e -> new Fatia(e.getKey(), e.getValue().size(), soma(e.getValue().stream().map(Despesa::getValor))))
                .sorted(Comparator.comparing(Fatia::valor).reversed()).toList();

        return new Relatorio(ano, mensal, f.entrouAno(), doAno.size(), ticket, porTipo, porOrigem,
                peds.size(), fechados, orcs.size(), aceitos, pct(aceitos, orcs.size()), pct(fechados, peds.size()), despCat);
    }

    private static List<Fatia> agrupar(List<Trabalho> lista, java.util.function.Function<Trabalho, String> chave) {
        return lista.stream().collect(Collectors.groupingBy(chave, LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(e -> new Fatia(e.getKey(), e.getValue().size(), soma(e.getValue().stream().map(Trabalho::getValorTotal))))
                .sorted(Comparator.comparing(Fatia::valor).reversed()).toList();
    }

    private static String nomeOrigem(Origem o) {
        if (o == null) return "Não informado";
        return switch (o) {
            case INSTAGRAM -> "Instagram"; case WHATSAPP -> "WhatsApp"; case INDICACAO -> "Indicação";
            case FEIRA -> "Feira ou evento"; case SITE -> "Site"; case OUTRO -> "Outro";
        };
    }

    private static BigDecimal pct(long parte, long total) {
        return total == 0 ? null : BigDecimal.valueOf(parte * 100.0 / total).setScale(1, RoundingMode.HALF_UP);
    }

    private static boolean noMes(LocalDate d, int ano, int mes) {
        return d.getYear() == ano && d.getMonthValue() == mes;
    }

    private static BigDecimal soma(java.util.stream.Stream<BigDecimal> s) {
        return s.reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
