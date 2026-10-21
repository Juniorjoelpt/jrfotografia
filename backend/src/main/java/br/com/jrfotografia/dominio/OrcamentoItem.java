package br.com.jrfotografia.dominio;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "orcamento_item")
@Getter
@Setter
public class OrcamentoItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orcamento_id")
    private Orcamento orcamento;

    private String descricao;
    private int quantidade;
    private BigDecimal valorUnit;
    private int ordem;

    public BigDecimal total() {
        return valorUnit.multiply(BigDecimal.valueOf(quantidade));
    }
}
