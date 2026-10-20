package br.com.jrfotografia.dominio;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "portfolio_foto")
@Getter
@Setter
public class PortfolioFoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long estudioId;
    private String categoria;
    private String titulo;
    private String arquivo;
    private String miniatura;
    private Integer largura;
    private Integer altura;
    private int ordem;
    private boolean destaque;
    private LocalDateTime criadoEm = LocalDateTime.now();
}
