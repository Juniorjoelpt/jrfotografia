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
    private LocalDateTime criadoEm;
}
