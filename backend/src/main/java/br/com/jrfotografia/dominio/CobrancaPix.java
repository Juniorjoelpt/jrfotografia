package br.com.jrfotografia.dominio;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Um QR Code Pix gerado no PagBank: o sinal de uma pré-reserva ou uma parcela em aberto. */
@Entity
@Table(name = "cobranca_pix")
@Getter
@Setter
public class CobrancaPix {

    public static final String AGUARDANDO = "AGUARDANDO", PAGA = "PAGA", EXPIRADA = "EXPIRADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long estudioId;
    private Long reservaId;
    private Long parcelaId;
    private String descricao;
    /** Quem vai pagar (para o histórico no painel). */
    private String nome;
    private BigDecimal valor;
    /** Pedido no PagBank (ORDE_…). */
    private String pedidoId;
    /** Pix copia e cola (o QR Code é desenhado a partir dele). */
    private String texto;
    private boolean sandbox;
    private String status = AGUARDANDO;
    private LocalDateTime expiraEm;
    private LocalDateTime pagoEm;
    private BigDecimal valorPago;
    /** Pago, mas algo impediu a confirmação automática (ex.: horário ocupado). */
    private String erro;
    private LocalDateTime verificadoEm;
    private LocalDateTime criadoEm = LocalDateTime.now();
}
