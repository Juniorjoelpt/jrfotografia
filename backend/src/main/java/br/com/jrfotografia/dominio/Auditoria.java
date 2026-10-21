package br.com.jrfotografia.dominio;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** Registro de atividades: quem fez o quê, quando e de onde. */
@Entity
@Table(name = "auditoria")
@Getter
@Setter
public class Auditoria {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long estudioId;
    private Long usuarioId;
    private String usuario;
    private String acao;
    private String detalhe;
    private String metodo;
    private String caminho;
    private String entidade;
    private Long entidadeId;
    private String ip;
    private LocalDateTime criadoEm = LocalDateTime.now();
}
