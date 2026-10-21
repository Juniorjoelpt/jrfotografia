package br.com.jrfotografia.dominio;

import br.com.jrfotografia.dominio.Enums.FormaPagamento;
import br.com.jrfotografia.dominio.Enums.StatusTrabalho;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "trabalho")
@Getter
@Setter
public class Trabalho {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long estudioId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "pacote_id")
    private Pacote pacote;

    private String titulo;
    private String tipo;
    private LocalDateTime dataTrabalho;
    private String localTrabalho;
    private BigDecimal valorTotal;

    @Enumerated(EnumType.STRING)
    private FormaPagamento formaPagamento;

    @Enumerated(EnumType.STRING)
    private StatusTrabalho status = StatusTrabalho.AGENDADO;

    private LocalDate prazoEntrega;
    private String galeriaToken;
    private String galeriaSenhaHash;
    private LocalDateTime galeriaExpiraEm;
    private LocalDateTime selecaoFechadaEm;
    private LocalDateTime entregueEm;
    private String observacoes;
    private int duracaoMin = 240;
    private Long orcamentoId;
    private LocalDateTime criadoEm = LocalDateTime.now();

    public LocalDateTime fim() {
        return dataTrabalho == null ? null : dataTrabalho.plusMinutes(duracaoMin);
    }

    /** Pacote com limite de fotos exige seleção do cliente; eventos entregam tudo. */
    public boolean exigeSelecao() {
        return pacote != null && pacote.temSelecao();
    }
}
