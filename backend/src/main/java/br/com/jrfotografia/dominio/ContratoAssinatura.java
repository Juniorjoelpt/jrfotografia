package br.com.jrfotografia.dominio;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Pedido de assinatura eletrônica de um contrato. O texto é congelado na criação (com hash SHA-256);
 * ao assinar, guardamos nome, CPF, data, IP, navegador e o desenho da assinatura, e geramos o PDF final.
 */
@Entity
@Table(name = "contrato_assinatura")
@Getter
@Setter
public class ContratoAssinatura {

    public enum Status { PENDENTE, ASSINADO, CANCELADO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long estudioId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "trabalho_id")
    private Trabalho trabalho;

    private String token;
    private String codigo;

    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDENTE;

    private String texto;
    private String hashTexto;
    private LocalDateTime criadoEm;
    private LocalDateTime assinadoEm;
    private String nomeAssinante;
    private String cpfAssinante;
    private String ip;
    private String userAgent;
    private String imagemAssinatura;
    private String pdf;
    private String hashPdf;
    private LocalDateTime canceladoEm;
    /** Escolha do cliente sobre o uso das imagens no portfólio, quando o contrato pergunta. */
    private Boolean autorizaImagem;
}
