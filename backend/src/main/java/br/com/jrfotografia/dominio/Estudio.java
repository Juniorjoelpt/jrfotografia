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
    /** PNG da assinatura desenhada do fotógrafo (chave no armazenamento). */
    private String assinaturaImagem;

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
    /** Programa de indicação: desconto (%) para quem é indicada e recompensa (R$) para quem indicou. */
    private boolean indicacaoAtiva;
    /** E-mails por SMTP (a senha fica cifrada). */
    private boolean emailAtivo;
    private String smtpHost;
    private Integer smtpPorta;
    private String smtpUsuario;
    private String smtpSenha;
    private String smtpSeguranca;
    private String emailRemetente;
    private boolean emailAvisos = true;
    private boolean emailLembretes = true;
    /** Nome do fotógrafo no site e nas mensagens (ex.: "Joel Júnior"). */
    private String fotografo;
    /** Pix com confirmação automática pelo PagBank (o token fica cifrado). */
    private boolean pagbankAtivo;
    private String pagbankToken;
    private boolean pagbankSandbox;
    /** Assistente de conteúdo (API no formato OpenAI); a chave fica cifrada. */
    private boolean iaAtivo;
    private String iaUrl;
    private String iaModelo;
    private String iaChave;
    private int indicacaoDesconto = 10;
    private java.math.BigDecimal indicacaoRecompensa = java.math.BigDecimal.valueOf(50);
    private LocalDateTime criadoEm;

    /** O Pix da reserva e das parcelas sai pelo PagBank, com QR Code e confirmação automática. */
    public boolean pixAutomatico() {
        return pagbankAtivo && pagbankToken != null;
    }
}
