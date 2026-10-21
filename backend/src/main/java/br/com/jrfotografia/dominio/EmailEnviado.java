package br.com.jrfotografia.dominio;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** Histórico de e-mails: o que foi para quem, e se deu certo. */
@Entity
@Table(name = "email_enviado")
@Getter
@Setter
public class EmailEnviado {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long estudioId;
    private String para;
    private String assunto;
    private String tipo;
    /** Evita mandar o mesmo e-mail duas vezes (ex.: lembrete da parcela 123). */
    private String referencia;
    private String status;
    private String erro;
    private LocalDateTime criadoEm = LocalDateTime.now();
    private LocalDateTime enviadoEm;
}
