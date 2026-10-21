package br.com.jrfotografia.dominio;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** Fase 3 · bloco 5: campanhas/mini-sessões, cupons de desconto e indicações. */
public final class Marketing {

    private Marketing() {}

    public enum TipoCupom { PERCENTUAL, VALOR }

    public enum StatusIndicacao { CONFIRMADA, RECOMPENSADA, CANCELADA }

    /** Mini-sessão (Dia das Mães, Natal…): página própria, dias e horários fixos, preço próprio. */
    @Entity(name = "Campanha")
    @Table(name = "campanha")
    @Getter
    @Setter
    public static class Campanha {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private Long estudioId;
        /** Pacote interno (fora do site) que o trabalho usa: fotos, extra e prazo de entrega. */
        private Long pacoteId;
        private String slug;
        private String titulo;
        private String descricao;
        private BigDecimal preco;
        private int sinalPercentual = 50;
        private int duracaoMin = 20;
        private int intervaloMin = 10;
        private Integer qtdFotos;
        private BigDecimal precoFotoExtra;
        private int prazoEntregaDias = 10;
        private String capa;
        private boolean ativa = true;
        private LocalDateTime criadoEm = LocalDateTime.now();
    }

    @Entity(name = "CampanhaDia")
    @Table(name = "campanha_dia")
    @Getter
    @Setter
    public static class CampanhaDia {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private Long campanhaId;
        private LocalDate data;
        private LocalTime inicio;
        private LocalTime fim;
        private String local;
    }

    @Entity(name = "Cupom")
    @Table(name = "cupom")
    @Getter
    @Setter
    public static class Cupom {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private Long estudioId;
        private String codigo;
        private String descricao;

        @Enumerated(EnumType.STRING)
        private TipoCupom tipo;

        private BigDecimal valor;
        private LocalDate validade;
        private Integer usosMax;
        private int usos;
        private Long campanhaId;
        private boolean ativo = true;
        private LocalDateTime criadoEm = LocalDateTime.now();
    }

    @Entity(name = "Indicacao")
    @Table(name = "indicacao")
    @Getter
    @Setter
    public static class Indicacao {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private Long estudioId;
        private Long indicadorClienteId;
        private Long reservaId;
        private Long trabalhoId;
        private String indicadoNome;
        private BigDecimal recompensa;

        @Enumerated(EnumType.STRING)
        private StatusIndicacao status = StatusIndicacao.CONFIRMADA;

        private LocalDateTime criadoEm = LocalDateTime.now();
        private LocalDateTime recompensadaEm;
    }
}
