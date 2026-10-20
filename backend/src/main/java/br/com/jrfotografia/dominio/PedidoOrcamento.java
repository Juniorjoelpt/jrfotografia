package br.com.jrfotografia.dominio;

import br.com.jrfotografia.dominio.Enums.Origem;
import br.com.jrfotografia.dominio.Enums.StatusPedido;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "pedido_orcamento")
@Getter
@Setter
public class PedidoOrcamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long estudioId;
    private String nome;
    private String telefone;
    private String email;
    private String tipoTrabalho;
    private Long pacoteId;
    private LocalDate dataDesejada;
    private String mensagem;

    @Enumerated(EnumType.STRING)
    private Origem origem;

    @Enumerated(EnumType.STRING)
    private StatusPedido status = StatusPedido.NOVO;

    private Long clienteId;
    private LocalDateTime criadoEm = LocalDateTime.now();
}
