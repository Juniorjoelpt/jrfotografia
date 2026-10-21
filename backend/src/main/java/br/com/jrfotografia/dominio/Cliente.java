package br.com.jrfotografia.dominio;

import br.com.jrfotografia.dominio.Enums.Origem;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "cliente")
@Getter
@Setter
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long estudioId;
    private String nome;
    private String telefone;
    private String email;
    private String cpf;
    private LocalDate dataNascimento;

    @Enumerated(EnumType.STRING)
    private Origem origem;

    private String observacoes;
    private LocalDateTime ultimoContato;
    /** Código que a cliente passa para amigas (programa de indicação). */
    private String codigoIndicacao;
    private LocalDateTime criadoEm = LocalDateTime.now();
}
