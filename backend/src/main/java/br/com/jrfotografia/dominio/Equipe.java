package br.com.jrfotografia.dominio;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Fase 3 · bloco 3: relacionamento com clientes e equipe (freelancers e tarefas). */
public final class Equipe {

    private Equipe() {}

    public enum StatusTarefa { A_FAZER, FAZENDO, FEITA }

    /** Data que a cliente quer lembrada: aniversário de casamento, do filho etc. (o aniversário dela fica no cadastro). */
    @Entity(name = "DataEspecial")
    @Table(name = "data_especial")
    @Getter
    @Setter
    public static class DataEspecial {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private Long estudioId;
        private Long clienteId;
        private String descricao;
        private int dia;
        private int mes;
        private Integer ano;
        private LocalDateTime criadoEm = LocalDateTime.now();
    }

    @Entity(name = "PesquisaSatisfacao")
    @Table(name = "pesquisa_satisfacao")
    @Getter
    @Setter
    public static class PesquisaSatisfacao {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private Long estudioId;
        private Long trabalhoId;
        private String token;
        /** 0 a 10: quanto recomendaria o estúdio (NPS). */
        private Integer nota;
        /** 1 a 5 estrelas. */
        private Integer atendimento;
        private Integer fotos;
        private Integer prazo;
        private String comentario;
        private Boolean autorizaDepoimento;
        private LocalDateTime criadoEm = LocalDateTime.now();
        private LocalDateTime respondidaEm;
    }

    @Entity(name = "Freelancer")
    @Table(name = "freelancer")
    @Getter
    @Setter
    public static class Freelancer {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private Long estudioId;
        private String nome;
        private String telefone;
        private String funcao;
        private String chavePix;
        private String observacoes;
        private boolean ativo = true;
        private LocalDateTime criadoEm = LocalDateTime.now();
    }

    /** Freelancer num trabalho, com o valor a pagar (fixo ou % do trabalho). Pago vira despesa. */
    @Entity(name = "TrabalhoFreelancer")
    @Table(name = "trabalho_freelancer")
    @Getter
    @Setter
    public static class TrabalhoFreelancer {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private Long estudioId;
        private Long trabalhoId;

        @ManyToOne(fetch = FetchType.EAGER, optional = false)
        @JoinColumn(name = "freelancer_id")
        private Freelancer freelancer;

        private String funcao;
        private BigDecimal percentual;
        private BigDecimal valor;
        private Long despesaId;
        private LocalDateTime criadoEm = LocalDateTime.now();
    }

    @Entity(name = "Tarefa")
    @Table(name = "tarefa")
    @Getter
    @Setter
    public static class Tarefa {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private Long estudioId;
        private Long trabalhoId;
        private String titulo;
        private String descricao;
        private Long responsavelUsuarioId;
        private Long responsavelFreelancerId;
        private LocalDate prazo;

        @Enumerated(EnumType.STRING)
        private StatusTarefa status = StatusTarefa.A_FAZER;

        private boolean automatica;
        private LocalDateTime criadoEm = LocalDateTime.now();
        private LocalDateTime concluidaEm;
    }
}
