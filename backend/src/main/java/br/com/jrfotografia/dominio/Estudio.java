package br.com.jrfotografia.dominio;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "estudio")
@Getter
@Setter
public class Estudio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String slug;
    private String nome;
    private String dominio;
    private String cidade;
    private String whatsapp;
    private String instagram;
    private String email;
    private String chavePix;
    private String apresentacao;
    private String modeloContrato;
    private String responsavel;
    private String documento;

    // Agenda online (fase 3)
    private boolean agendaOnline;
    private int antecedenciaHoras = 24;
    private int horizonteDias = 60;
    private int intervaloMin = 30;
    private int passoMin = 60;
    private int sinalPercentual = 30;
    private int reservaHoras = 24;
    private int remarcacaoHoras = 48;
    private int remarcacoesMax = 1;
    private int reembolsoDias = 7;
    private LocalDateTime criadoEm;
}
