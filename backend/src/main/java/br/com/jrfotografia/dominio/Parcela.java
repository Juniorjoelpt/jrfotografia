package br.com.jrfotografia.dominio;

import br.com.jrfotografia.dominio.Enums.MeioPagamento;
import br.com.jrfotografia.dominio.Enums.TipoParcela;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "parcela")
@Getter
@Setter
public class Parcela {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long estudioId;
    private Long trabalhoId;

    @Enumerated(EnumType.STRING)
    private TipoParcela tipo;

    private String descricao;
    private BigDecimal valor;
    private LocalDate vencimento;
    private LocalDate pagoEm;

    @Enumerated(EnumType.STRING)
    private MeioPagamento forma;

    public boolean paga() {
        return pagoEm != null;
    }
}
