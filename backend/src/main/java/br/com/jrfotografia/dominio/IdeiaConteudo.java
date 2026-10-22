package br.com.jrfotografia.dominio;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** Um pedido ao assistente de conteúdo e as ideias que voltaram (guardadas em JSON). */
@Entity
@Table(name = "ideia_conteudo")
@Getter
@Setter
public class IdeiaConteudo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long estudioId;
    /** SEMANA, TRABALHO, CAMPANHA, DEPOIMENTO ou LIVRE. */
    private String tipo;
    /** O que foi pedido, em poucas palavras (ex.: "Ideias para a semana de 22/10"). */
    private String titulo;
    /** O que o fotógrafo escreveu para orientar, se escreveu. */
    private String pedido;
    private String modelo;
    /** Lista de ideias em JSON. */
    private String resposta;
    private LocalDateTime criadoEm = LocalDateTime.now();
}
