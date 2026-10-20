package br.com.jrfotografia.dominio;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "foto")
@Getter
@Setter
public class Foto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long estudioId;
    private Long trabalhoId;
    private String nomeOriginal;

    /** Arquivo enviado para a seleção (JPEG já exportado, nunca o bruto). */
    private String arquivo;

    /** Versão editada final; quando existe, é ela que o cliente baixa. */
    private String arquivoFinal;

    /** Prévia com marca d'água, até 1600px. */
    private String previa;

    /** Miniatura com marca d'água, até 600px. */
    private String miniatura;

    private Integer largura;
    private Integer altura;
    private int ordem;
    private boolean selecionada;
    private String comentario;
    private LocalDateTime criadoEm = LocalDateTime.now();

    public String arquivoParaEntrega() {
        return arquivoFinal != null ? arquivoFinal : arquivo;
    }
}
