package br.com.jrfotografia.servico;

import br.com.jrfotografia.dominio.Enums.FormaPagamento;
import br.com.jrfotografia.dominio.Enums.StatusTrabalho;
import br.com.jrfotografia.dominio.Enums.TipoParcela;
import br.com.jrfotografia.dominio.Parcela;
import br.com.jrfotografia.dominio.Trabalho;

import java.math.BigDecimal;
import java.util.List;

/**
 * Quando o cliente pode baixar as fotos em alta (decisão de 20/10/2026):
 * - o trabalho precisa estar ENTREGUE;
 * - à vista: só depois de quitar tudo, inclusive fotos extras;
 * - parcelado: depois que o sinal (se houver) e a 1ª parcela estiverem pagos.
 */
public final class RegraDownload {

    private RegraDownload() {}

    public record Situacao(boolean liberado, String motivo) {}

    public static Situacao avaliar(Trabalho t, List<Parcela> todas) {
        // Produtos vendidos depois (álbum, quadro) não seguram o download das fotos.
        List<Parcela> parcelas = todas.stream().filter(p -> p.getTipo() != TipoParcela.PRODUTO).toList();
        if (t.getStatus() != StatusTrabalho.ENTREGUE) {
            return new Situacao(false, "As fotos ainda estão em edição.");
        }
        if (parcelas.isEmpty() || t.getValorTotal().compareTo(BigDecimal.ZERO) == 0) {
            return new Situacao(true, null);
        }
        if (t.getFormaPagamento() == FormaPagamento.AVISTA) {
            boolean quitado = parcelas.stream().allMatch(Parcela::paga);
            return quitado
                    ? new Situacao(true, null)
                    : new Situacao(false, "O download é liberado assim que o pagamento for confirmado.");
        }
        boolean sinalOk = parcelas.stream()
                .filter(p -> p.getTipo() == TipoParcela.SINAL)
                .allMatch(Parcela::paga);
        boolean primeiraParcelaOk = parcelas.stream()
                .anyMatch(p -> p.getTipo() == TipoParcela.PARCELA && p.paga());
        boolean semParcelas = parcelas.stream().noneMatch(p -> p.getTipo() == TipoParcela.PARCELA);
        if (sinalOk && (primeiraParcelaOk || semParcelas)) return new Situacao(true, null);
        boolean temSinal = parcelas.stream().anyMatch(p -> p.getTipo() == TipoParcela.SINAL);
        // Usa o nome que a cliente vê na área (ex.: "Restante (no dia do ensaio)"), não "1ª parcela".
        String parcela = parcelas.stream().filter(p -> p.getTipo() == TipoParcela.PARCELA && !p.paga()).findFirst()
                .map(p -> p.getDescricao() == null || p.getDescricao().isBlank() ? "da 1ª parcela" : "de \u201c" + p.getDescricao() + "\u201d")
                .orElse("da 1ª parcela");
        String falta = !sinalOk && temSinal && !(primeiraParcelaOk || semParcelas) ? "do sinal e " + parcela
                : !sinalOk && temSinal ? "do sinal" : parcela;
        return new Situacao(false, "O download é liberado após o pagamento " + falta + ".");
    }
}
