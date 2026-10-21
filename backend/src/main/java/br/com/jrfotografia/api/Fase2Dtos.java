package br.com.jrfotografia.api;

import br.com.jrfotografia.dominio.Enums.FormaPagamento;
import br.com.jrfotografia.dominio.Enums.StatusOrcamento;
import br.com.jrfotografia.dominio.Fase2;
import br.com.jrfotografia.dominio.Orcamento;
import br.com.jrfotografia.dominio.OrcamentoItem;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Formatos de entrada e saída da fase 2. */
public final class Fase2Dtos {

    private Fase2Dtos() {}

    // ---------- agenda
    public record BloqueioForm(@NotNull LocalDateTime inicio, @NotNull LocalDateTime fim, @NotBlank @Size(max = 120) String motivo) {}

    public record Conflitos(List<String> avisos) {}

    // ---------- orçamentos
    public record ItemForm(@NotBlank String descricao, @Min(1) int quantidade, @NotNull @PositiveOrZero BigDecimal valorUnit) {}

    public record OrcamentoForm(Long clienteId, Long pedidoId, @Size(max = 120) String nomeCliente, String telefone,
                                @Email String email, Long pacoteId, String tipo, LocalDateTime dataPrevista,
                                String localPrevisto, List<ItemForm> itens, @PositiveOrZero BigDecimal desconto,
                                FormaPagamento formaPagamento, @PositiveOrZero BigDecimal valorSinal,
                                @Min(1) @Max(12) Integer numParcelas, LocalDate validade, String observacoes) {}

    public record ItemDto(String descricao, int quantidade, BigDecimal valorUnit, BigDecimal total) {
        static ItemDto de(OrcamentoItem i) {
            return new ItemDto(i.getDescricao(), i.getQuantidade(), i.getValorUnit(), i.total());
        }
    }

    public record OrcamentoDto(Long id, int numero, String token, Long clienteId, Long pedidoId, String nomeCliente,
                               String telefone, String email, Long pacoteId, String pacoteNome, String tipo,
                               LocalDateTime dataPrevista, String localPrevisto, List<ItemDto> itens, BigDecimal subtotal,
                               BigDecimal desconto, BigDecimal valorTotal, FormaPagamento formaPagamento,
                               BigDecimal valorSinal, Integer numParcelas, LocalDate validade, String observacoes,
                               StatusOrcamento status, LocalDateTime enviadoEm, LocalDateTime respondidoEm,
                               Long trabalhoId, LocalDateTime criadoEm) {
        static OrcamentoDto de(Orcamento o) {
            return new OrcamentoDto(o.getId(), o.getNumero(), o.getToken(), o.getClienteId(), o.getPedidoId(),
                    o.getNomeCliente(), o.getTelefone(), o.getEmail(),
                    o.getPacote() != null ? o.getPacote().getId() : null, o.getPacote() != null ? o.getPacote().getNome() : null,
                    o.getTipo(), o.getDataPrevista(), o.getLocalPrevisto(), o.getItens().stream().map(ItemDto::de).toList(),
                    o.subtotal(), o.getDesconto(), o.getValorTotal(), o.getFormaPagamento(), o.getValorSinal(),
                    o.getNumParcelas(), o.getValidade(), o.getObservacoes(), o.statusAtual(), o.getEnviadoEm(),
                    o.getRespondidoEm(), o.getTrabalhoId(), o.getCriadoEm());
        }
    }

    /** O que o cliente vê na página pública do orçamento (sem ids internos). */
    public record OrcamentoPublico(int numero, String nomeCliente, String estudioNome, String estudioWhatsapp,
                                   String estudioCidade, String pacoteNome, List<String> incluso, Integer prazoEntregaDias,
                                   BigDecimal precoFotoExtra, String tipo, LocalDateTime dataPrevista, String localPrevisto,
                                   List<ItemDto> itens, BigDecimal subtotal, BigDecimal desconto, BigDecimal valorTotal,
                                   String condicoes, LocalDate validade, String observacoes, StatusOrcamento status,
                                   boolean contratoDisponivel) {}

    // ---------- financeiro
    public record DespesaForm(@NotBlank @Size(max = 120) String descricao, @NotBlank @Size(max = 40) String categoria,
                              @NotNull @Positive BigDecimal valor, @NotNull LocalDate vencimento, LocalDate pagoEm,
                              Long trabalhoId) {}

    public record DespesaDto(Long id, String descricao, String categoria, BigDecimal valor, LocalDate vencimento,
                             LocalDate pagoEm, Long trabalhoId, boolean vencida) {
        static DespesaDto de(Fase2.Despesa d) {
            return new DespesaDto(d.getId(), d.getDescricao(), d.getCategoria(), d.getValor(), d.getVencimento(),
                    d.getPagoEm(), d.getTrabalhoId(), d.getPagoEm() == null && d.getVencimento().isBefore(LocalDate.now()));
        }
    }

    // ---------- ajustes
    public record ItemAvulsoForm(@NotBlank @Size(max = 80) String nome, @NotNull @PositiveOrZero BigDecimal preco, Boolean ativo) {}

    public record ItemAvulsoDto(Long id, String nome, BigDecimal preco, boolean ativo) {
        static ItemAvulsoDto de(Fase2.ItemAvulso i) {
            return new ItemAvulsoDto(i.getId(), i.getNome(), i.getPreco(), i.isAtivo());
        }
    }

    public record ContratoModelo(String texto, boolean padrao, String campos, String responsavel, String documento) {}

    public record ContratoForm(String texto, @Size(max = 120) String responsavel, @Size(max = 20) String documento) {}
}
