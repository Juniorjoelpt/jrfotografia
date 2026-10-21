package br.com.jrfotografia.dominio;

import br.com.jrfotografia.dominio.Enums.Perfil;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "usuario")
@Getter
@Setter
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long estudioId;
    private String nome;
    private String email;
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    private Perfil perfil;

    private boolean ativo = true;

    /** Verificação em duas etapas (app autenticador). */
    private boolean totpAtivo;
    private String totpSegredo;
    private String totpPendente;
    private Long totpUltimoPasso;
    private String codigosRecuperacao;
    private LocalDateTime criadoEm = LocalDateTime.now();
}
