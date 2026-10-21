package br.com.jrfotografia.dominio;

import br.com.jrfotografia.dominio.Enums.FormaPagamento;
import br.com.jrfotografia.dominio.Enums.StatusOrcamento;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Orçamento enviado ao cliente por link. Aceito, vira trabalho com o plano de pagamento combinado. */
@Entity
@Table(name = "orcamento")
@Getter
@Setter
public class Orcamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long estudioId;
    private int numero;
    private String token;
    private Long clienteId;
    private Long pedidoId;
    private String nomeCliente;
    private String telefone;
    private String email;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "pacote_id")
    private Pacote pacote;

    private String tipo;
    private LocalDateTime dataPrevista;
    private String localPrevisto;
    private BigDecimal desconto = BigDecimal.ZERO;
    private BigDecimal valorTotal;

    @Enumerated(EnumType.STRING)
    private FormaPagamento formaPagamento;

    private BigDecimal valorSinal;
    private Integer numParcelas;
    private LocalDate validade;
    private String observacoes;

    @Enumerated(EnumType.STRING)
    private StatusOrcamento status = StatusOrcamento.RASCUNHO;

    private LocalDateTime enviadoEm;
    private LocalDateTime respondidoEm;
    private Long trabalhoId;
    private LocalDateTime criadoEm = LocalDateTime.now();

    @OneToMany(mappedBy = "orcamento", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("ordem ASC, id ASC")
    private List<OrcamentoItem> itens = new ArrayList<>();

    public BigDecimal subtotal() {
        return itens.stream().map(OrcamentoItem::total).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Vencida a validade sem resposta, o orçamento conta como expirado. */
    public StatusOrcamento statusAtual() {
        if ((status == StatusOrcamento.ENVIADO || status == StatusOrcamento.RASCUNHO)
                && validade != null && validade.isBefore(LocalDate.now())) {
            return StatusOrcamento.EXPIRADO;
        }
        return status;
    }
}
