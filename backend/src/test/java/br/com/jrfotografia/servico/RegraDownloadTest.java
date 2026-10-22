package br.com.jrfotografia.servico;

import br.com.jrfotografia.dominio.Enums.FormaPagamento;
import br.com.jrfotografia.dominio.Enums.StatusTrabalho;
import br.com.jrfotografia.dominio.Enums.TipoParcela;
import br.com.jrfotografia.dominio.Parcela;
import br.com.jrfotografia.dominio.Trabalho;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RegraDownloadTest {

    @Test
    void aVistaSoLiberaDepoisDeQuitarIncluindoExtras() {
        Trabalho t = trabalho(FormaPagamento.AVISTA);
        Parcela avista = parcela(TipoParcela.AVISTA, true);
        Parcela extra = parcela(TipoParcela.EXTRA, false);
        assertThat(RegraDownload.avaliar(t, List.of(avista, extra)).liberado()).isFalse();
        extra.setPagoEm(LocalDate.now());
        assertThat(RegraDownload.avaliar(t, List.of(avista, extra)).liberado()).isTrue();
    }

    @Test
    void parceladoLiberaAposSinalEPrimeiraParcela() {
        Trabalho t = trabalho(FormaPagamento.PARCELADO);
        Parcela sinal = parcela(TipoParcela.SINAL, true);
        Parcela p1 = parcela(TipoParcela.PARCELA, false);
        Parcela p2 = parcela(TipoParcela.PARCELA, false);
        assertThat(RegraDownload.avaliar(t, List.of(sinal, p1, p2)).liberado()).isFalse();
        p1.setPagoEm(LocalDate.now());
        assertThat(RegraDownload.avaliar(t, List.of(sinal, p1, p2)).liberado()).isTrue();
    }

    @Test
    void mensagemDizOQueFaltaPagar() {
        Trabalho t = trabalho(FormaPagamento.PARCELADO);
        Parcela sinal = parcela(TipoParcela.SINAL, false);
        Parcela p1 = parcela(TipoParcela.PARCELA, false);
        assertThat(RegraDownload.avaliar(t, List.of(sinal, p1)).motivo()).contains("do sinal e da 1ª parcela");
        sinal.setPagoEm(LocalDate.now());
        assertThat(RegraDownload.avaliar(t, List.of(sinal, p1)).motivo()).contains("da 1ª parcela").doesNotContain("sinal");
        sinal.setPagoEm(null);
        p1.setPagoEm(LocalDate.now());
        assertThat(RegraDownload.avaliar(t, List.of(sinal, p1)).motivo()).endsWith("do sinal.");
    }

    @Test
    void mensagemUsaONomeDaParcelaQueAClienteVe() {
        Trabalho t = trabalho(FormaPagamento.PARCELADO);
        Parcela sinal = parcela(TipoParcela.SINAL, true);
        Parcela restante = parcela(TipoParcela.PARCELA, false);
        restante.setDescricao("Restante (no dia do ensaio)");
        assertThat(RegraDownload.avaliar(t, List.of(sinal, restante)).motivo())
                .isEqualTo("O download é liberado após o pagamento de “Restante (no dia do ensaio)”.");
    }

    @Test
    void produtoVendidoDepoisNaoSeguraODownload() {
        Trabalho t = trabalho(FormaPagamento.AVISTA);
        Parcela avista = parcela(TipoParcela.AVISTA, true);
        Parcela album = parcela(TipoParcela.PRODUTO, false);
        assertThat(RegraDownload.avaliar(t, List.of(avista, album)).liberado()).isTrue();
    }

    @Test
    void naoLiberaAntesDaEntrega() {
        Trabalho t = trabalho(FormaPagamento.AVISTA);
        t.setStatus(StatusTrabalho.EDICAO);
        assertThat(RegraDownload.avaliar(t, List.of(parcela(TipoParcela.AVISTA, true))).liberado()).isFalse();
    }

    @Test
    void prazoContaSoDiasUteis() {
        // Sexta-feira + 5 dias úteis = sexta-feira seguinte.
        assertThat(Prazos.somarDiasUteis(LocalDate.of(2026, 10, 16), 5)).isEqualTo(LocalDate.of(2026, 10, 23));
    }

    private static Trabalho trabalho(FormaPagamento forma) {
        Trabalho t = new Trabalho();
        t.setStatus(StatusTrabalho.ENTREGUE);
        t.setFormaPagamento(forma);
        t.setValorTotal(new BigDecimal("350.00"));
        return t;
    }

    private static Parcela parcela(TipoParcela tipo, boolean paga) {
        Parcela p = new Parcela();
        p.setTipo(tipo);
        p.setValor(new BigDecimal("100.00"));
        if (paga) p.setPagoEm(LocalDate.now());
        return p;
    }
}
