package br.com.jrfotografia.servico;

import br.com.jrfotografia.config.AppProps;
import br.com.jrfotografia.dominio.Agendamento.Reserva;
import br.com.jrfotografia.dominio.ContratoAssinatura;
import br.com.jrfotografia.dominio.Estudio;
import br.com.jrfotografia.dominio.Orcamento;
import br.com.jrfotografia.dominio.Parcela;
import br.com.jrfotografia.dominio.PedidoOrcamento;
import br.com.jrfotografia.dominio.Produtos.PedidoProduto;
import br.com.jrfotografia.dominio.Repositorios.EstudioRepo;
import br.com.jrfotografia.dominio.Trabalho;
import br.com.jrfotografia.servico.EmailModelos.Botao;
import br.com.jrfotografia.servico.EmailModelos.Linha;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static br.com.jrfotografia.servico.EmailModelos.esc;
import static br.com.jrfotografia.servico.EmailModelos.layout;

/**
 * Os e-mails do sistema: para a cliente (pré-reserva, confirmação, contrato, fotos, lembretes) e
 * avisos para o fotógrafo (nova pré-reserva, comprovante, contrato assinado, seleção concluída…).
 * Cada método monta a mensagem e entrega ao {@link EmailServico}; nada aqui interrompe a operação principal.
 */
@Service
public class EmailAvisos {

    private static final Locale BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final EmailServico email;
    private final EstudioRepo estudios;
    private final AppProps props;

    public EmailAvisos(EmailServico email, EstudioRepo estudios, AppProps props) {
        this.email = email;
        this.estudios = estudios;
        this.props = props;
    }

    // ================================================================ apoio

    /** Endereço do site nos links: URL_PUBLICA, ou https://domínio do estúdio. */
    public String url(Estudio e, String caminho) {
        String base = props.email() != null && props.email().urlPublica() != null && !props.email().urlPublica().isBlank()
                ? props.email().urlPublica()
                : e.getDominio() != null && !e.getDominio().isBlank() ? "https://" + e.getDominio().replaceFirst("^https?://", "") : "http://localhost:5173";
        return base.replaceAll("/+$", "") + caminho;
    }

    static String quando(LocalDateTime d) {
        if (d == null) return "data a combinar";
        String sem = d.getDayOfWeek().getDisplayName(TextStyle.FULL, BR);
        String mes = d.getMonth().getDisplayName(TextStyle.FULL, BR);
        return sem + ", " + d.getDayOfMonth() + " de " + mes + " às " + DateTimeFormatter.ofPattern("HH:mm").format(d);
    }

    static String primeiro(String nome) {
        return nome == null ? "" : nome.trim().split("\\s+")[0];
    }

    static String moeda(BigDecimal v) {
        return PdfServico.moeda(v);
    }

    private Estudio estudio(Long id) {
        return estudios.findById(id).orElseThrow();
    }

    private String rodapeCliente(Estudio e) {
        StringBuilder s = new StringBuilder(esc(e.getNome()));
        if (e.getCidade() != null) s.append(" · ").append(esc(e.getCidade()));
        if (e.getWhatsapp() != null) s.append(" · WhatsApp ").append(esc(PdfServico.fone(e.getWhatsapp())));
        s.append("<br>Você recebe este e-mail porque tem um ensaio ou pedido com o estúdio. Para falar com a gente, é só responder.");
        return s.toString();
    }

    private boolean paraCliente(Estudio e, String para, String assunto, String titulo, List<String> paragrafos, List<Linha> resumo,
                                Botao botao, String tipo, String referencia, List<EmailServico.Anexo> anexos) {
        String html = layout(e.getNome(), titulo, paragrafos, resumo, botao, rodapeCliente(e));
        return email.enviar(e, para, assunto, html, tipo, referencia, anexos);
    }

    /** Aviso interno para o fotógrafo (e-mail do estúdio), se ele quiser receber. */
    private void paraEstudio(Estudio e, String assunto, String titulo, List<String> paragrafos, List<Linha> resumo, Botao botao, String tipo,
                             String referencia) {
        if (!e.isEmailAvisos() || e.getEmail() == null) return;
        String html = layout(e.getNome(), titulo, paragrafos, resumo, botao, "Aviso automático do painel do " + esc(e.getNome()) + ".");
        email.enviar(e, e.getEmail(), assunto, html, tipo, referencia, null);
    }

    // ================================================================ agendamento online

    public void preReservaRecebida(Reserva r) {
        Estudio e = estudio(r.getEstudioId());
        List<Linha> resumo = new ArrayList<>(List.of(new Linha("Ensaio", r.getPacote().getNome().replaceFirst("^Mini-sessão · ", "Mini-sessão ")),
                new Linha("Quando", quando(r.getInicio()))));
        if (r.getDesconto() != null) resumo.add(new Linha("Desconto (" + r.getCupomCodigo() + ")", "− " + moeda(r.getDesconto())));
        resumo.add(new Linha("Valor", moeda(r.getValorTotal())));
        resumo.add(new Linha("Sinal no Pix", moeda(r.getValorSinal())));
        if (e.getChavePix() != null) resumo.add(new Linha("Chave Pix", e.getChavePix()));
        paraCliente(e, r.getEmail(), "Seu horário está pré-reservado · " + e.getNome(), "Horário pré-reservado!",
                List.of("Olá, " + esc(primeiro(r.getNome())) + "! Recebi sua reserva. O horário fica guardado por <b>" + e.getReservaHoras()
                                + " horas</b> enquanto você paga o sinal no Pix e envia o comprovante pela página da reserva.",
                        "Assim que eu conferir o Pix, você recebe a confirmação."),
                resumo, new Botao("Enviar comprovante do Pix", url(e, "/reserva/" + r.getToken())), "PRE_RESERVA", "reserva:" + r.getId(), null);
        paraEstudio(e, "Nova pré-reserva: " + r.getNome() + " · " + quando(r.getInicio()), "Nova pré-reserva pelo site",
                List.of(esc(r.getNome()) + " reservou pelo site. O horário fica seguro por " + e.getReservaHoras() + "h esperando o comprovante do Pix."),
                List.of(new Linha("Ensaio", r.getPacote().getNome()), new Linha("Quando", quando(r.getInicio())),
                        new Linha("WhatsApp", PdfServico.fone(r.getTelefone())), new Linha("Valor", moeda(r.getValorTotal()))),
                new Botao("Ver no painel", url(e, "/painel/agenda#pre-reservas")), "AVISO_PRE_RESERVA", "reserva:" + r.getId());
    }

    public void comprovanteRecebido(Reserva r) {
        Estudio e = estudio(r.getEstudioId());
        paraEstudio(e, "Comprovante recebido: " + r.getNome(), "Comprovante do Pix recebido",
                List.of(esc(r.getNome()) + " enviou o comprovante do sinal (" + esc(moeda(r.getValorSinal())) + "). Confira e confirme o ensaio."),
                List.of(new Linha("Quando", quando(r.getInicio())), new Linha("Ensaio", r.getPacote().getNome())),
                new Botao("Conferir e confirmar", url(e, "/painel/agenda#pre-reservas")), "AVISO_COMPROVANTE", "reserva:" + r.getId() + ":" + r.getComprovanteEm());
    }

    public void reservaConfirmada(Reserva r, String tokenArea, String tokenContrato) {
        Estudio e = estudio(r.getEstudioId());
        List<String> p = new ArrayList<>(List.of("Olá, " + esc(primeiro(r.getNome())) + "! Recebi o sinal e seu ensaio está <b>confirmado</b>. 📸",
                "Na sua área você acompanha os pagamentos e, se precisar, remarca ou cancela dentro das regras do contrato."));
        if (tokenContrato != null) {
            p.add("Seu contrato já está pronto para assinar pelo celular: <a href=\"" + esc(url(e, "/assinar/" + tokenContrato))
                    + "\" style=\"color:#c62828\">ler e assinar o contrato</a>.");
        }
        paraCliente(e, r.getEmail(), "Ensaio confirmado · " + quando(r.getInicio()), "Ensaio confirmado!", p,
                List.of(new Linha("Quando", quando(r.getInicio())), new Linha("Sinal pago", moeda(r.getValorSinal())),
                        new Linha("Restante", moeda(r.getValorTotal().subtract(r.getValorSinal())))),
                tokenArea != null ? new Botao("Abrir minha área", url(e, "/meu/" + tokenArea)) : null, "RESERVA_CONFIRMADA", "reserva:" + r.getId(), null);
    }

    public void reservaRecusada(Reserva r) {
        Estudio e = estudio(r.getEstudioId());
        List<String> p = new ArrayList<>(List.of("Olá, " + esc(primeiro(r.getNome())) + ". Infelizmente não consegui confirmar sua reserva de "
                + esc(quando(r.getInicio())) + "."));
        if (r.getMotivoRecusa() != null) p.add("Motivo: " + esc(r.getMotivoRecusa()));
        p.add("Se quiser, escolha outro horário ou me chame no WhatsApp.");
        paraCliente(e, r.getEmail(), "Sobre sua reserva · " + e.getNome(), "Não foi possível confirmar", p, null,
                new Botao("Escolher outro horário", url(e, "/agendar")), "RESERVA_RECUSADA", "reserva:" + r.getId(), null);
    }

    // ================================================================ área da cliente

    public void remarcadoPelaCliente(Trabalho t, LocalDateTime antes) {
        Estudio e = estudio(t.getEstudioId());
        paraEstudio(e, "Ensaio remarcado: " + t.getCliente().getNome(), "Ensaio remarcado pela cliente",
                List.of(esc(t.getCliente().getNome()) + " remarcou o ensaio pela área da cliente."),
                List.of(new Linha("Antes", quando(antes)), new Linha("Agora", quando(t.getDataTrabalho()))),
                new Botao("Abrir trabalho", url(e, "/painel/trabalhos/" + t.getId())), "AVISO_REMARCACAO", null);
        paraCliente(e, t.getCliente().getEmail(), "Ensaio remarcado · " + quando(t.getDataTrabalho()), "Ensaio remarcado",
                List.of("Olá, " + esc(primeiro(t.getCliente().getNome())) + "! Seu ensaio foi remarcado. Te espero na nova data."),
                List.of(new Linha("Nova data", quando(t.getDataTrabalho()))),
                t.getTokenCliente() != null ? new Botao("Abrir minha área", url(e, "/meu/" + t.getTokenCliente())) : null,
                "REMARCACAO", null, null);
    }

    public void canceladoPelaCliente(Trabalho t, String texto) {
        Estudio e = estudio(t.getEstudioId());
        paraEstudio(e, "Ensaio cancelado: " + t.getCliente().getNome(), "Ensaio cancelado pela cliente",
                List.of(esc(t.getCliente().getNome()) + " cancelou o ensaio de " + esc(quando(t.getDataTrabalho())) + " pela área da cliente.",
                        t.getMotivoCancelamento() != null ? "Motivo: " + esc(t.getMotivoCancelamento()) : "", esc(texto == null ? "" : texto)),
                null, new Botao("Abrir trabalho", url(e, "/painel/trabalhos/" + t.getId())), "AVISO_CANCELAMENTO", "trabalho:" + t.getId());
        paraCliente(e, t.getCliente().getEmail(), "Ensaio cancelado · " + e.getNome(), "Ensaio cancelado",
                List.of("Olá, " + esc(primeiro(t.getCliente().getNome())) + ". Seu cancelamento foi registrado.", esc(texto == null ? "" : texto),
                        "Quando quiser remarcar, é só me chamar."),
                null, null, "CANCELAMENTO", "trabalho:" + t.getId(), null);
    }

    // ================================================================ contrato

    public boolean contratoParaAssinar(ContratoAssinatura a) {
        Trabalho t = a.getTrabalho();
        Estudio e = estudio(a.getEstudioId());
        return paraCliente(e, t.getCliente().getEmail(), "Seu contrato para assinar · " + e.getNome(), "Seu contrato está pronto",
                List.of("Olá, " + esc(primeiro(t.getCliente().getNome())) + "! Leia o contrato com calma e assine pelo celular. Leva um minuto:"
                        + " você confere seu nome, informa o CPF e desenha a assinatura na tela."),
                List.of(new Linha("Ensaio", t.getTitulo()), new Linha("Quando", quando(t.getDataTrabalho()))),
                new Botao("Ler e assinar o contrato", url(e, "/assinar/" + a.getToken())), "CONTRATO", "assinatura:" + a.getId(), null);
    }

    public void contratoAssinado(ContratoAssinatura a, byte[] pdf) {
        Trabalho t = a.getTrabalho();
        Estudio e = estudio(a.getEstudioId());
        String codigo = AssinaturaServico.formatarCodigo(a.getCodigo());
        paraCliente(e, t.getCliente().getEmail(), "Contrato assinado · " + e.getNome(), "Contrato assinado",
                List.of("Olá, " + esc(primeiro(t.getCliente().getNome())) + "! Seu contrato foi assinado. A via assinada vai em anexo, com a página de registro da assinatura.",
                        "Qualquer pessoa pode conferir a autenticidade com o código <b>" + esc(codigo) + "</b>."),
                List.of(new Linha("Assinado em", DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm").format(a.getAssinadoEm())),
                        new Linha("Código", codigo)),
                new Botao("Verificar contrato", url(e, "/verificar/" + codigo)), "CONTRATO_ASSINADO", "assinatura:" + a.getId(),
                List.of(new EmailServico.Anexo("contrato-assinado.pdf", "application/pdf", pdf)));
        paraEstudio(e, "Contrato assinado: " + t.getCliente().getNome(), "Contrato assinado",
                List.of(esc(a.getNomeAssinante()) + " assinou o contrato (" + esc(t.getTitulo()) + ")."),
                List.of(new Linha("CPF", a.getCpfAssinante()), new Linha("Código", codigo)),
                new Botao("Abrir trabalho", url(e, "/painel/trabalhos/" + t.getId())), "AVISO_CONTRATO_ASSINADO", "assinatura:" + a.getId());
    }

    // ================================================================ galeria e entrega

    /** Botão "Enviar por e-mail" da galeria: vai com a senha (que só existe na hora em que foi gerada). */
    public boolean galeria(Trabalho t, String senha) {
        Estudio e = estudio(t.getEstudioId());
        boolean entrega = t.getStatus() == br.com.jrfotografia.dominio.Enums.StatusTrabalho.ENTREGUE;
        List<Linha> resumo = new ArrayList<>(List.of(new Linha("Senha da galeria", senha)));
        if (!entrega && t.getPacote() != null && t.getPacote().getQtdFotos() != null) {
            resumo.add(new Linha("Fotos no pacote", String.valueOf(t.getPacote().getQtdFotos())));
            if (t.getPacote().getPrecoFotoExtra() != null) resumo.add(new Linha("Foto extra", moeda(t.getPacote().getPrecoFotoExtra())));
        }
        if (entrega && t.getGaleriaExpiraEm() != null) resumo.add(new Linha("Disponível até", DATA.format(t.getGaleriaExpiraEm())));
        return paraCliente(e, t.getCliente().getEmail(),
                entrega ? "Suas fotos estão prontas! · " + e.getNome() : "Escolha suas fotos · " + e.getNome(),
                entrega ? "Suas fotos estão prontas! 📸" : "Hora de escolher suas fotos",
                List.of("Olá, " + esc(primeiro(t.getCliente().getNome())) + "! " + (entrega
                        ? "As fotos editadas já estão na sua galeria."
                        : "As fotos do seu ensaio já estão na galeria. Toque no coração das suas favoritas e, no fim, conclua a seleção.")),
                resumo, new Botao(entrega ? "Ver minhas fotos" : "Abrir a galeria", url(e, "/g/" + t.getGaleriaToken())),
                entrega ? "GALERIA_ENTREGA" : "GALERIA_SELECAO", null, null);
    }

    public void selecaoConcluida(Trabalho t, long escolhidas, long extras, BigDecimal valorExtras) {
        Estudio e = estudio(t.getEstudioId());
        List<Linha> resumo = new ArrayList<>(List.of(new Linha("Fotos escolhidas", String.valueOf(escolhidas))));
        if (extras > 0) resumo.add(new Linha("Extras", extras + " · " + moeda(valorExtras)));
        paraEstudio(e, "Seleção concluída: " + t.getCliente().getNome(), "Seleção concluída",
                List.of(esc(t.getCliente().getNome()) + " concluiu a escolha das fotos. A tarefa de edição já está na lista."),
                resumo, new Botao("Abrir trabalho", url(e, "/painel/trabalhos/" + t.getId())), "AVISO_SELECAO", "selecao:" + t.getId() + ":" + t.getSelecaoFechadaEm());
    }

    /** Na entrega: aviso de fotos prontas (sem a senha) e convite para a pesquisa. */
    public void fotosEntregues(Trabalho t, String tokenPesquisa) {
        Estudio e = estudio(t.getEstudioId());
        List<String> p = new ArrayList<>(List.of("Olá, " + esc(primeiro(t.getCliente().getNome())) + "! Suas fotos editadas estão prontas na galeria. "
                + "Use a mesma senha que te enviei (se não tiver mais, é só me pedir)."));
        if (tokenPesquisa != null) {
            p.add("Se puder, me conta em 1 minuto como foi sua experiência: <a href=\"" + esc(url(e, "/pesquisa/" + tokenPesquisa))
                    + "\" style=\"color:#c62828\">responder a pesquisa</a>. Isso me ajuda muito!");
        }
        paraCliente(e, t.getCliente().getEmail(), "Suas fotos estão prontas! · " + e.getNome(), "Suas fotos estão prontas! 📸", p,
                t.getGaleriaExpiraEm() != null ? List.of(new Linha("Galeria disponível até", DATA.format(t.getGaleriaExpiraEm()))) : null,
                new Botao("Ver minhas fotos", url(e, "/g/" + t.getGaleriaToken())), "ENTREGA", "entrega:" + t.getId() + ":" + t.getEntregueEm(), null);
    }

    // ================================================================ vendas

    public void novoPedidoOrcamento(PedidoOrcamento p) {
        Estudio e = estudio(p.getEstudioId());
        List<Linha> resumo = new ArrayList<>(List.of(new Linha("WhatsApp", PdfServico.fone(p.getTelefone()))));
        if (p.getTipoTrabalho() != null) resumo.add(new Linha("Ensaio", p.getTipoTrabalho()));
        if (p.getDataDesejada() != null) resumo.add(new Linha("Data desejada", DATA.format(p.getDataDesejada())));
        List<String> par = new ArrayList<>(List.of(esc(p.getNome()) + " pediu um orçamento pelo site."));
        if (p.getMensagem() != null) par.add("“" + esc(p.getMensagem()) + "”");
        paraEstudio(e, "Pedido de orçamento: " + p.getNome(), "Novo pedido de orçamento", par, resumo,
                new Botao("Ver em Vendas", url(e, "/painel/vendas")), "AVISO_PEDIDO", "pedido:" + p.getId());
    }

    public boolean orcamentoParaCliente(Orcamento o) {
        Estudio e = estudio(o.getEstudioId());
        return paraCliente(e, o.getEmail(), "Seu orçamento nº " + o.getNumero() + " · " + e.getNome(), "Seu orçamento",
                List.of("Olá, " + esc(primeiro(o.getNomeCliente())) + "! Preparei seu orçamento. Pelo link você vê o que está incluso, baixa o PDF e,"
                        + " se estiver tudo certo, aceita com um toque."),
                List.of(new Linha("Orçamento", "nº " + o.getNumero()), new Linha("Total", moeda(o.getValorTotal())),
                        new Linha("Válido até", DATA.format(o.getValidade()))),
                new Botao("Ver orçamento", url(e, "/o/" + o.getToken())), "ORCAMENTO", null, null);
    }

    public void orcamentoRespondido(Orcamento o, boolean aceito) {
        Estudio e = estudio(o.getEstudioId());
        paraEstudio(e, (aceito ? "Orçamento aceito: " : "Orçamento recusado: ") + o.getNomeCliente(),
                aceito ? "Orçamento aceito! 🎉" : "Orçamento recusado",
                List.of(esc(o.getNomeCliente()) + (aceito ? " aceitou o orçamento nº " + o.getNumero() + ". O trabalho já foi criado."
                        : " recusou o orçamento nº " + o.getNumero() + ".")),
                List.of(new Linha("Total", moeda(o.getValorTotal()))),
                new Botao("Abrir orçamento", url(e, "/painel/orcamentos/" + o.getId())), "AVISO_ORCAMENTO", "orcamento:" + o.getId() + ":" + aceito);
    }

    // ================================================================ produtos

    public boolean albumParaAprovar(PedidoProduto p, Trabalho t) {
        Estudio e = estudio(p.getEstudioId());
        return paraCliente(e, t.getCliente().getEmail(), "A diagramação do seu álbum está pronta · " + e.getNome(), "Seu álbum está pronto para aprovar",
                List.of("Olá, " + esc(primeiro(t.getCliente().getNome())) + "! Veja as páginas do seu " + esc(ProdutosServico.rotulo(p).toLowerCase())
                        + ". Comente onde quiser mudar algo e, se estiver tudo certo, aprove."),
                null, new Botao("Ver o álbum", url(e, "/album/" + p.getTokenAprovacao())), "ALBUM", "album:" + p.getId() + ":v" + p.getVersaoLayout(), null);
    }

    public void albumRespondido(PedidoProduto p, Trabalho t) {
        Estudio e = estudio(p.getEstudioId());
        boolean aprovado = p.getAprovacao() == br.com.jrfotografia.dominio.Produtos.Aprovacao.APROVADO;
        List<String> par = new ArrayList<>(List.of(esc(t.getCliente().getNome()) + (aprovado ? " aprovou a diagramação. Já pode mandar para o laboratório."
                : " pediu ajustes na diagramação.")));
        if (p.getComentarioCliente() != null) par.add("“" + esc(p.getComentarioCliente()) + "”");
        paraEstudio(e, (aprovado ? "Álbum aprovado: " : "Ajustes no álbum: ") + t.getCliente().getNome(), aprovado ? "Álbum aprovado" : "Ajustes pedidos no álbum",
                par, null, new Botao("Abrir pedido", url(e, "/painel/produtos/" + p.getId())), "AVISO_ALBUM", "album:" + p.getId() + ":v" + p.getVersaoLayout());
    }

    // ================================================================ lembretes automáticos

    public void lembreteEnsaio(Trabalho t) {
        Estudio e = estudio(t.getEstudioId());
        if (!e.isEmailLembretes()) return;
        List<Linha> resumo = new ArrayList<>(List.of(new Linha("Quando", quando(t.getDataTrabalho()))));
        if (t.getLocalTrabalho() != null) resumo.add(new Linha("Local", t.getLocalTrabalho()));
        paraCliente(e, t.getCliente().getEmail(), "Lembrete: seu ensaio é amanhã · " + e.getNome(), "Seu ensaio é amanhã! 📸",
                List.of("Olá, " + esc(primeiro(t.getCliente().getNome())) + "! Passando para lembrar do seu ensaio. Qualquer dúvida, é só responder este e-mail."),
                resumo, t.getTokenCliente() != null ? new Botao("Abrir minha área", url(e, "/meu/" + t.getTokenCliente())) : null,
                "LEMBRETE_ENSAIO", "ensaio:" + t.getId() + ":" + t.getDataTrabalho().toLocalDate(), null);
    }

    public void lembreteParcela(Parcela p, Trabalho t, boolean vencida) {
        Estudio e = estudio(t.getEstudioId());
        if (!e.isEmailLembretes()) return;
        List<Linha> resumo = new ArrayList<>(List.of(new Linha(p.getDescricao(), moeda(p.getValor())),
                new Linha("Vencimento", DATA.format(p.getVencimento()))));
        if (e.getChavePix() != null) resumo.add(new Linha("Chave Pix", e.getChavePix()));
        paraCliente(e, t.getCliente().getEmail(),
                (vencida ? "Pagamento em aberto" : "Lembrete de pagamento") + " · " + e.getNome(),
                vencida ? "Pagamento em aberto" : "Lembrete de pagamento",
                List.of("Olá, " + esc(primeiro(t.getCliente().getNome())) + "! " + (vencida
                        ? "Consta em aberto o pagamento abaixo. Se já pagou, desconsidere e me mande o comprovante, por favor."
                        : "Passando para lembrar do pagamento abaixo. Depois de pagar, é só me mandar o comprovante.")),
                resumo, t.getTokenCliente() != null ? new Botao("Ver meus pagamentos", url(e, "/meu/" + t.getTokenCliente())) : null,
                vencida ? "COBRANCA_VENCIDA" : "COBRANCA_LEMBRETE", "parcela:" + p.getId() + ":" + p.getVencimento(), null);
    }

    // ================================================================ teste

    public void teste(Estudio e, String para) {
        String html = layout(e.getNome(), "E-mail configurado! ✅",
                List.of("Se você está lendo isto, o envio de e-mails do painel está funcionando.",
                        "Daqui para frente o sistema manda os avisos e lembretes automaticamente para clientes que informaram e-mail."),
                null, null, "Teste enviado pelo painel do " + esc(e.getNome()) + ".");
        email.testar(e, para, "Teste de e-mail · " + e.getNome(), html);
    }
}
