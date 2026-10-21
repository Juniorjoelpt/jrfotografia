package br.com.jrfotografia.dominio;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Entidades simples da fase 2: itens avulsos, bloqueios de agenda e despesas. */
public final class Fase2 {

    private Fase2() {}

    @Entity(name = "ItemAvulso")
    @Table(name = "item_avulso")
    @Getter
    @Setter
    public static class ItemAvulso {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private Long estudioId;
        private String nome;
        private BigDecimal preco;
        private boolean ativo = true;
    }

    @Entity(name = "BloqueioAgenda")
    @Table(name = "bloqueio_agenda")
    @Getter
    @Setter
    public static class BloqueioAgenda {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private Long estudioId;
        private LocalDateTime inicio;
        private LocalDateTime fim;
        private String motivo;
    }

    @Entity(name = "Despesa")
    @Table(name = "despesa")
    @Getter
    @Setter
    public static class Despesa {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private Long estudioId;
        private String descricao;
        private String categoria;
        private BigDecimal valor;
        private LocalDate vencimento;
        private LocalDate pagoEm;
        private Long trabalhoId;
        private LocalDateTime criadoEm = LocalDateTime.now();
    }
}
