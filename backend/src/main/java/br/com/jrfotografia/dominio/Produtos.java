package br.com.jrfotografia.dominio;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Fase 3 · bloco 4: pedidos de produtos, lâminas do álbum para aprovação e estoque de materiais. */
public final class Produtos {

    private Produtos() {}

    public enum TipoProduto { ALBUM, QUADRO, IMPRESSAO, OUTRO }

    /**
     * Novo → (álbum: Diagramação → Aprovação →) Aprovado → Laboratório → Pronto → Entregue; ou Cancelado.
     */
    public enum StatusPedidoProduto { NOVO, DIAGRAMACAO, APROVACAO, APROVADO, LABORATORIO, PRONTO, ENTREGUE, CANCELADO }

    public enum Aprovacao { PENDENTE, APROVADO, AJUSTES }

    public enum TipoMovimento { ENTRADA, SAIDA, AJUSTE }

    @Entity(name = "PedidoProduto")
    @Table(name = "pedido_produto")
    @Getter
    @Setter
    public static class PedidoProduto {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private Long estudioId;
        private Long trabalhoId;

        @Enumerated(EnumType.STRING)
        private TipoProduto tipo;

        private String descricao;
        private int quantidade = 1;
        private BigDecimal valorVenda = BigDecimal.ZERO;
        private BigDecimal custo;
        private String laboratorio;

        @Enumerated(EnumType.STRING)
        private StatusPedidoProduto status = StatusPedidoProduto.NOVO;

        private LocalDate prazo;
        private LocalDate previsaoLab;
        private LocalDateTime enviadoLabEm;
        private LocalDateTime recebidoEm;
        private LocalDateTime entregueEm;
        private String observacoes;
        private Long parcelaId;
        private Long despesaId;
        private String tokenAprovacao;
        private int versaoLayout;

        @Enumerated(EnumType.STRING)
        private Aprovacao aprovacao;

        private LocalDateTime aprovacaoEm;
        private String comentarioCliente;
        private LocalDateTime criadoEm = LocalDateTime.now();
    }

    @Entity(name = "AlbumLamina")
    @Table(name = "album_lamina")
    @Getter
    @Setter
    public static class AlbumLamina {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private Long pedidoId;
        private int versao;
        private int ordem;
        private String nome;
        private String arquivo;
        private String miniatura;
        private Integer largura;
        private Integer altura;
        private String comentario;
    }

    @Entity(name = "Material")
    @Table(name = "material")
    @Getter
    @Setter
    public static class Material {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private Long estudioId;
        private String nome;
        private String unidade;
        private BigDecimal quantidade = BigDecimal.ZERO;
        private BigDecimal minimo = BigDecimal.ZERO;
        private BigDecimal custoUnitario;
        private boolean ativo = true;
        private LocalDateTime criadoEm = LocalDateTime.now();
    }

    @Entity(name = "MovimentoMaterial")
    @Table(name = "movimento_material")
    @Getter
    @Setter
    public static class MovimentoMaterial {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private Long estudioId;
        private Long materialId;

        @Enumerated(EnumType.STRING)
        private TipoMovimento tipo;

        private BigDecimal quantidade;
        private BigDecimal saldo;
        private String motivo;
        private Long pedidoId;
        private String usuario;
        private LocalDateTime criadoEm = LocalDateTime.now();
    }
}
