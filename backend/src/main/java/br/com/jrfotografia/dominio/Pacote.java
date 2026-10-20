package br.com.jrfotografia.dominio;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "pacote")
@Getter
@Setter
public class Pacote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long estudioId;
    private String nome;

    /** Quantidade de fotos inclusas. Nulo = todas as fotos (pacote de eventos, sem seleção). */
    private Integer qtdFotos;

    private BigDecimal preco;
    private BigDecimal precoFotoExtra;
    private int prazoEntregaDias;

    /** Itens separados por "|". */
    private String itens;

    private String materialEntregue;
    private boolean publico = true;
    private boolean ativo = true;
    private int ordem;

    public boolean temSelecao() {
        return qtdFotos != null;
    }

    public List<String> listaItens() {
        if (itens == null || itens.isBlank()) return List.of();
        return Arrays.stream(itens.split("\\|")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
