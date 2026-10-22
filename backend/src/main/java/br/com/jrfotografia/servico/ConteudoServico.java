package br.com.jrfotografia.servico;

import br.com.jrfotografia.config.Erros.NaoEncontrado;
import br.com.jrfotografia.config.Erros.RegraNegocio;
import br.com.jrfotografia.dominio.ContratoAssinatura;
import br.com.jrfotografia.dominio.Enums.StatusTrabalho;
import br.com.jrfotografia.dominio.Equipe.PesquisaSatisfacao;
import br.com.jrfotografia.dominio.Estudio;
import br.com.jrfotografia.dominio.IdeiaConteudo;
import br.com.jrfotografia.dominio.Marketing.Campanha;
import br.com.jrfotografia.dominio.Pacote;
import br.com.jrfotografia.dominio.Repositorios.*;
import br.com.jrfotografia.dominio.Trabalho;
import br.com.jrfotografia.servico.IaCliente.Conexao;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

import static br.com.jrfotografia.servico.Textos.vazio;

/**
 * Assistente de conteúdo para redes sociais. Junta o que o sistema sabe do estúdio (pacotes, mini-sessões,
 * agenda, ensaios entregues com uso de imagem autorizado, depoimentos autorizados, datas comemorativas)
 * e pede ideias de post a um modelo de linguagem.
 *
 * <p>Privacidade: nada de nome completo, telefone, e-mail, CPF ou valores pagos por clientes sai daqui.
 * Ensaios só entram se a cliente autorizou o uso de imagem no contrato; depoimentos, se autorizou o depoimento
 * (e só com o primeiro nome).</p>
 */
@Service
public class ConteudoServico {

    public static final List<String> TIPOS = List.of("SEMANA", "TRABALHO", "CAMPANHA", "DEPOIMENTO", "LIVRE");
    private static final Locale BR = Locale.of("pt", "BR");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM");

    private final EstudioRepo estudios;
    private final PacoteRepo pacotes;
    private final CampanhaRepo campanhas;
    private final CampanhaDiaRepo dias;
    private final TrabalhoRepo trabalhos;
    private final AssinaturaRepo assinaturas;
    private final PesquisaRepo pesquisas;
    private final IdeiaConteudoRepo ideias;
    private final IaCliente ia;
    private final Cifra cifra;
    private final EmailAvisos links;
    private final ObjectMapper json;

    public ConteudoServico(EstudioRepo estudios, PacoteRepo pacotes, CampanhaRepo campanhas, CampanhaDiaRepo dias, TrabalhoRepo trabalhos,
                           AssinaturaRepo assinaturas, PesquisaRepo pesquisas, IdeiaConteudoRepo ideias, IaCliente ia, Cifra cifra,
                           EmailAvisos links, ObjectMapper json) {
        this.estudios = estudios;
        this.pacotes = pacotes;
        this.campanhas = campanhas;
        this.dias = dias;
        this.trabalhos = trabalhos;
        this.assinaturas = assinaturas;
        this.pesquisas = pesquisas;
        this.ideias = ideias;
        this.ia = ia;
        this.cifra = cifra;
        this.links = links;
        this.json = json;
    }

    // ================================================================ tipos

    /** Uma ideia de post. Campos vazios são normais (ex.: stories não têm hashtags). */
    public record Ideia(String dia, String formato, String titulo, String roteiro, String legenda, List<String> hashtags) {}

    public record Resultado(Long id, String tipo, String titulo, String pedido, String modelo, LocalDateTime criadoEm,
                            List<Ideia> ideias, List<String> usado) {}

    /** O que dá para escolher na tela: só o que pode ir para as redes. */
    public record Opcao(Long id, String rotulo, String detalhe) {}

    public record Opcoes(List<Opcao> trabalhos, List<Opcao> campanhas, List<Opcao> depoimentos) {}

    // ================================================================ opções

    public Opcoes opcoes(Long estudioId) {
        List<Opcao> ts = trabalhosAutorizados(estudioId).stream()
                .map(t -> new Opcao(t.getId(), rotuloTrabalho(t), "Entregue em " + (t.getEntregueEm() != null ? t.getEntregueEm().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "—")))
                .toList();
        List<Opcao> cs = campanhas.findByEstudioIdOrderByIdDesc(estudioId).stream().filter(Campanha::isAtiva)
                .map(c -> new Opcao(c.getId(), c.getTitulo(), dinheiro(c.getPreco()))).toList();
        List<Opcao> ds = depoimentosAutorizados(estudioId).stream()
                .map(p -> new Opcao(p.getId(), "Nota " + p.getNota() + " · " + primeiroNome(p), corta(p.getComentario(), 90))).toList();
        return new Opcoes(ts, cs, ds);
    }

    // ================================================================ gerar

    public Resultado gerar(Long estudioId, String tipo, Long referencia, String pedido) {
        Estudio e = estudios.findById(estudioId).orElseThrow();
        if (!e.isIaAtivo() || vazio(e.getIaUrl()) || vazio(e.getIaModelo()))
            throw new RegraNegocio("Ligue o assistente em Ajustes › Assistente de conteúdo.");
        if (!TIPOS.contains(tipo)) throw new RegraNegocio("Tipo de pedido desconhecido.");
        pedido = pedido == null ? null : corta(pedido.trim(), 600);
        if ("LIVRE".equals(tipo) && vazio(pedido)) throw new RegraNegocio("Escreva o que você quer pedir ao assistente.");

        List<String> usado = new ArrayList<>();
        StringBuilder ctx = new StringBuilder(contextoDoEstudio(e, usado));
        String tarefa, titulo;
        LocalDate hoje = LocalDate.now();
        switch (tipo) {
            case "SEMANA" -> {
                titulo = "Ideias para a semana de " + hoje.format(DIA);
                ctx.append(contextoGeral(e, usado));
                List<String> proximos = new ArrayList<>();
                for (int i = 0; i < 7; i++) {
                    LocalDate d = hoje.plusDays(i);
                    proximos.add(d.getDayOfWeek().getDisplayName(TextStyle.FULL, BR) + " " + d.format(DIA));
                }
                tarefa = "Monte um plano de 7 posts, um para cada dia: " + String.join(", ", proximos)
                        + ". Varie os formatos (Reels, carrossel, foto, stories) e os objetivos (mostrar trabalho, bastidores, dica para a cliente, "
                        + "prova social, chamada para agendar). Aproveite as datas comemorativas e as mini-sessões que existirem. "
                        + "No campo \"dia\" ponha o dia da semana e a data.";
            }
            case "TRABALHO" -> {
                Trabalho t = trabalhosAutorizados(estudioId).stream().filter(x -> x.getId().equals(referencia)).findFirst()
                        .orElseThrow(() -> new NaoEncontrado("Escolha um ensaio entregue cuja cliente autorizou o uso de imagem."));
                titulo = "Post do ensaio: " + rotuloTrabalho(t);
                ctx.append("\nENSAIO PARA MOSTRAR (a cliente autorizou o uso de imagem no contrato; não cite o nome dela):\n- Tipo: ")
                        .append(rotuloTrabalho(t)).append("\n- Pacote: ").append(t.getPacote() != null ? t.getPacote().getNome() : "—")
                        .append("\n- Quando: ").append(t.getDataTrabalho() != null ? t.getDataTrabalho().getMonth().getDisplayName(TextStyle.FULL, BR) + " de " + t.getDataTrabalho().getYear() : "—")
                        .append(vazio(t.getLocalTrabalho()) ? "" : "\n- Local: " + t.getLocalTrabalho()).append('\n');
                usado.add("Ensaio entregue: " + rotuloTrabalho(t) + " (uso de imagem autorizado)");
                tarefa = "Crie 3 opções de post para mostrar este ensaio nas redes (por exemplo: carrossel com as melhores fotos, Reels de bastidores, "
                        + "foto única com história). Diga no roteiro quais fotos escolher (ex.: \"a mais espontânea\", \"um detalhe das mãos\"), "
                        + "sem inventar fatos que não estão aqui. Termine as legendas convidando para agendar.";
            }
            case "CAMPANHA" -> {
                Campanha c = campanhas.findByIdAndEstudioId(referencia, estudioId).filter(Campanha::isAtiva)
                        .orElseThrow(() -> new NaoEncontrado("Mini-sessão não encontrada ou desligada."));
                titulo = "Divulgar: " + c.getTitulo();
                ctx.append("\nMINI-SESSÃO PARA DIVULGAR:\n").append(descreverCampanha(e, c)).append('\n');
                usado.add("Mini-sessão: " + c.getTitulo());
                tarefa = "Crie 4 peças para divulgar esta mini-sessão: um post de lançamento, um carrossel explicando como funciona, "
                        + "uma sequência de stories (no roteiro, um item por story) e um post de últimos horários. Use o link da página da mini-sessão "
                        + "na chamada (\"link na bio\" no feed; no stories, o link mesmo). Não invente preço, data ou horário.";
            }
            case "DEPOIMENTO" -> {
                PesquisaSatisfacao p = depoimentosAutorizados(estudioId).stream().filter(x -> x.getId().equals(referencia)).findFirst()
                        .orElseThrow(() -> new NaoEncontrado("Escolha um depoimento que a cliente autorizou publicar."));
                titulo = "Depoimento de " + primeiroNome(p);
                ctx.append("\nDEPOIMENTO AUTORIZADO PARA PUBLICAR:\n- Cliente: ").append(primeiroNome(p))
                        .append("\n- Nota (0 a 10): ").append(p.getNota())
                        .append("\n- Ensaio: ").append(trabalhos.findById(p.getTrabalhoId()).map(this::rotuloTrabalho).orElse("—"))
                        .append("\n- Comentário: \"").append(corta(p.getComentario(), 500)).append("\"\n");
                usado.add("Depoimento de " + primeiroNome(p) + " (autorizado)");
                tarefa = "Crie 3 formas de publicar este depoimento: um post de feed, um story e um carrossel que una o depoimento a fotos do ensaio. "
                        + "Mantenha as palavras da cliente entre aspas, sem alterar o sentido.";
            }
            default -> {
                titulo = corta(pedido, 80);
                ctx.append(contextoGeral(e, usado));
                tarefa = "Atenda ao pedido do fotógrafo abaixo com 3 a 5 ideias de conteúdo.";
            }
        }
        if (!vazio(pedido)) ctx.append("\nO FOTÓGRAFO PEDIU TAMBÉM: ").append(pedido).append('\n');

        String sistema = """
                Você é um assistente de marketing de conteúdo para um estúdio de fotografia no Brasil.
                Escreva em português do Brasil, com tom acolhedor, próximo e profissional, como o próprio fotógrafo falando com as clientes.
                Regras:
                - Use só as informações dadas. Não invente preços, datas, horários, prêmios ou números.
                - Nunca escreva nome de cliente, a não ser o primeiro nome de um depoimento autorizado.
                - Legendas prontas para copiar, com no máximo 5 a 8 hashtags relevantes (algumas da cidade, se houver).
                - Emojis com moderação.
                Responda SOMENTE com um JSON neste formato:
                {"ideias":[{"dia":"","formato":"Reels | Carrossel | Foto | Stories","titulo":"","roteiro":"o que mostrar, passo a passo","legenda":"texto pronto","hashtags":["#exemplo"]}]}
                """;
        String usuario = ctx + "\nTAREFA: " + tarefa;

        Conexao con = new Conexao(e.getIaUrl(), e.getIaChave() == null ? null : cifra.decifrar(e.getIaChave()), e.getIaModelo());
        String texto = ia.conversar(con, sistema, usuario, true, Duration.ofSeconds(170));
        List<Ideia> lista = interpretar(texto);

        IdeiaConteudo salvo = new IdeiaConteudo();
        salvo.setEstudioId(estudioId);
        salvo.setTipo(tipo);
        salvo.setTitulo(corta(titulo, 200));
        salvo.setPedido(vazio(pedido) ? null : pedido);
        salvo.setModelo(corta(e.getIaModelo(), 120));
        salvo.setResposta(escrever(new Guardado(lista, usado)));
        ideias.save(salvo);
        return resultado(salvo);
    }

    record Guardado(List<Ideia> ideias, List<String> usado) {}

    public List<Resultado> historico(Long estudioId) {
        return ideias.findTop30ByEstudioIdOrderByIdDesc(estudioId).stream().map(this::resultado).toList();
    }

    public void apagar(Long estudioId, Long id) {
        ideias.delete(ideias.findByIdAndEstudioId(id, estudioId).orElseThrow(() -> new NaoEncontrado("Ideia não encontrada.")));
    }

    private Resultado resultado(IdeiaConteudo i) {
        Guardado g;
        try {
            g = json.readValue(i.getResposta(), Guardado.class);
        } catch (Exception ex) {
            g = new Guardado(List.of(new Ideia("", "Texto", "Resposta", "", i.getResposta(), List.of())), List.of());
        }
        return new Resultado(i.getId(), i.getTipo(), i.getTitulo(), i.getPedido(), i.getModelo(), i.getCriadoEm(), g.ideias(), g.usado());
    }

    // ================================================================ resposta

    /** Lê o JSON do modelo. Modelos pequenos às vezes cercam com ```json ou escrevem antes: pega do primeiro { ao último }. */
    List<Ideia> interpretar(String texto) {
        String t = texto.trim();
        int a = t.indexOf('{'), b = t.lastIndexOf('}');
        if (a >= 0 && b > a) {
            try {
                JsonNode r = json.readTree(t.substring(a, b + 1));
                JsonNode lista = r.isArray() ? r : r.path("ideias");
                if (!lista.isArray()) lista = r.path("posts");
                List<Ideia> out = new ArrayList<>();
                for (JsonNode n : lista) {
                    List<String> tags = new ArrayList<>();
                    JsonNode h = n.path("hashtags");
                    if (h.isArray()) h.forEach(x -> tags.add(x.asText()));
                    else if (h.isTextual()) for (String s : h.asText().split("[\\s,]+")) if (!s.isBlank()) tags.add(s);
                    out.add(new Ideia(txt(n, "dia"), txt(n, "formato"), txt(n, "titulo"), txt(n, "roteiro"), txt(n, "legenda"),
                            tags.stream().map(s -> s.startsWith("#") ? s : "#" + s).limit(12).toList()));
                }
                if (!out.isEmpty()) return out;
            } catch (Exception ignorado) {
                // cai no texto corrido abaixo
            }
        }
        return List.of(new Ideia("", "Texto", "Sugestões do assistente", "", t, List.of()));
    }

    private static String txt(JsonNode n, String campo) {
        JsonNode v = n.path(campo);
        if (v.isArray()) {
            List<String> p = new ArrayList<>();
            v.forEach(x -> p.add(x.asText()));
            return String.join("\n", p);
        }
        return v.isMissingNode() || v.isNull() ? "" : v.asText().trim();
    }

    // ================================================================ contexto

    private String contextoDoEstudio(Estudio e, List<String> usado) {
        StringBuilder s = new StringBuilder("ESTÚDIO:\n- Nome: ").append(e.getNome());
        if (!vazio(e.getFotografo())) s.append("\n- Fotógrafo: ").append(e.getFotografo());
        if (!vazio(e.getCidade())) s.append("\n- Cidade: ").append(e.getCidade());
        if (!vazio(e.getInstagram())) s.append("\n- Instagram: ").append(e.getInstagram());
        if (!vazio(e.getApresentacao())) s.append("\n- Apresentação: ").append(corta(e.getApresentacao(), 600));
        s.append("\n- Hoje: ").append(LocalDate.now().getDayOfWeek().getDisplayName(TextStyle.FULL, BR)).append(", ").append(LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        if (e.isAgendaOnline()) s.append("\n- Agenda online (a cliente escolhe o horário e paga o sinal): ").append(links.url(e, "/agendar"));
        if (e.isIndicacaoAtiva()) s.append("\n- Indicação: a amiga indicada ganha ").append(e.getIndicacaoDesconto()).append("% de desconto");
        List<Pacote> ps = pacotes.findByEstudioIdAndPublicoTrueAndAtivoTrueOrderByOrdem(e.getId());
        if (!ps.isEmpty()) {
            s.append("\n- Pacotes:");
            for (Pacote p : ps) s.append("\n  · ").append(p.getNome()).append(p.getPreco() != null ? " (" + dinheiro(p.getPreco()) + ")" : "")
                    .append(p.getQtdFotos() != null ? ", " + p.getQtdFotos() + " fotos" : "");
            usado.add(ps.size() + " pacote(s) do site");
        }
        return s.append('\n').toString();
    }

    /** Agenda, mini-sessões, ensaios e depoimentos autorizados e datas comemorativas: base para planos e pedidos livres. */
    private String contextoGeral(Estudio e, List<String> usado) {
        StringBuilder s = new StringBuilder();
        LocalDate hoje = LocalDate.now();
        List<Trabalho> marcados = trabalhos.findByEstudioIdAndDataTrabalhoBetweenOrderByDataTrabalho(e.getId(), hoje.atStartOfDay(), hoje.plusDays(30).atTime(23, 59))
                .stream().filter(t -> t.getStatus() != StatusTrabalho.CANCELADO).toList();
        Map<String, Long> porTipo = new TreeMap<>();
        marcados.forEach(t -> porTipo.merge(rotuloTrabalho(t), 1L, Long::sum));
        s.append("\nAGENDA DOS PRÓXIMOS 30 DIAS: ").append(marcados.size()).append(" ensaio(s) marcado(s)");
        if (!porTipo.isEmpty()) s.append(" (").append(String.join(", ", porTipo.entrySet().stream().map(x -> x.getValue() + " " + x.getKey()).toList())).append(")");
        s.append(marcados.size() < 4 ? ". A agenda tem espaço: vale chamar para agendar." : ".").append('\n');
        usado.add("Agenda dos próximos 30 dias: " + marcados.size() + " ensaio(s)");

        List<Campanha> cs = campanhas.findByEstudioIdOrderByIdDesc(e.getId()).stream().filter(Campanha::isAtiva).toList();
        for (Campanha c : cs) {
            String d = descreverCampanha(e, c);
            if (d.contains("Próximas datas")) {
                s.append("\nMINI-SESSÃO ABERTA:\n").append(d).append('\n');
                usado.add("Mini-sessão: " + c.getTitulo());
            }
        }

        List<Trabalho> recentes = trabalhosAutorizados(e.getId()).stream()
                .filter(t -> t.getEntregueEm() != null && t.getEntregueEm().isAfter(LocalDateTime.now().minusDays(90))).limit(6).toList();
        if (!recentes.isEmpty()) {
            s.append("\nENSAIOS ENTREGUES RECENTEMENTE COM USO DE IMAGEM AUTORIZADO (podem aparecer nas redes):\n");
            recentes.forEach(t -> s.append("- ").append(rotuloTrabalho(t)).append(", entregue em ").append(t.getEntregueEm().format(DIA)).append('\n'));
            usado.add(recentes.size() + " ensaio(s) entregue(s) com uso de imagem autorizado");
        }

        List<PesquisaSatisfacao> deps = depoimentosAutorizados(e.getId()).stream().limit(3).toList();
        if (!deps.isEmpty()) {
            s.append("\nDEPOIMENTOS AUTORIZADOS:\n");
            deps.forEach(p -> s.append("- ").append(primeiroNome(p)).append(" (nota ").append(p.getNota()).append("): \"").append(corta(p.getComentario(), 240)).append("\"\n"));
            usado.add(deps.size() + " depoimento(s) autorizado(s)");
        }

        List<String> datas = datasComemorativas(hoje, 45);
        if (!datas.isEmpty()) {
            s.append("\nDATAS COMEMORATIVAS PRÓXIMAS:\n");
            datas.forEach(d -> s.append("- ").append(d).append('\n'));
            usado.add("Datas: " + String.join("; ", datas.stream().limit(3).toList()));
        }
        return s.toString();
    }

    private String descreverCampanha(Estudio e, Campanha c) {
        StringBuilder s = new StringBuilder("- Nome: ").append(c.getTitulo());
        if (!vazio(c.getDescricao())) s.append("\n- Descrição: ").append(corta(c.getDescricao(), 400));
        s.append("\n- Preço: ").append(dinheiro(c.getPreco())).append(", sessão de ").append(c.getDuracaoMin()).append(" min");
        if (c.getQtdFotos() != null) s.append(", ").append(c.getQtdFotos()).append(" fotos");
        List<String> ds = dias.findByCampanhaIdOrderByDataAscInicioAsc(c.getId()).stream()
                .filter(d -> !d.getData().isBefore(LocalDate.now()))
                .map(d -> d.getData().format(DIA) + " das " + d.getInicio() + " às " + d.getFim() + (vazio(d.getLocal()) ? "" : " em " + d.getLocal())).toList();
        if (!ds.isEmpty()) s.append("\n- Próximas datas: ").append(String.join("; ", ds));
        s.append("\n- Página para reservar: ").append(links.url(e, "/c/" + c.getSlug()));
        return s.toString();
    }

    // ================================================================ o que pode ir para as redes

    /** Ensaios entregues cuja cliente marcou "sim" no uso de imagem do contrato assinado. */
    private List<Trabalho> trabalhosAutorizados(Long estudioId) {
        return trabalhos.findByEstudioIdAndStatusOrderByDataTrabalhoDesc(estudioId, StatusTrabalho.ENTREGUE).stream()
                .filter(t -> assinaturas.findByTrabalhoIdOrderByCriadoEmDesc(t.getId()).stream()
                        .anyMatch(a -> a.getStatus() == ContratoAssinatura.Status.ASSINADO && Boolean.TRUE.equals(a.getAutorizaImagem())))
                .sorted(Comparator.comparing((Trabalho t) -> t.getEntregueEm() == null ? LocalDateTime.MIN : t.getEntregueEm()).reversed())
                .toList();
    }

    /** Pesquisas respondidas com depoimento escrito e autorização para publicar. */
    private List<PesquisaSatisfacao> depoimentosAutorizados(Long estudioId) {
        return pesquisas.findByEstudioIdOrderByCriadoEmDesc(estudioId).stream()
                .filter(p -> p.getRespondidaEm() != null && Boolean.TRUE.equals(p.getAutorizaDepoimento()) && !vazio(p.getComentario()))
                .toList();
    }

    private String rotuloTrabalho(Trabalho t) {
        return !vazio(t.getTipo()) ? t.getTipo() : t.getPacote() != null ? t.getPacote().getNome() : "Ensaio";
    }

    private String primeiroNome(PesquisaSatisfacao p) {
        return trabalhos.findById(p.getTrabalhoId()).map(t -> t.getCliente().getNome().trim().split("\\s+")[0]).orElse("Cliente");
    }

    // ================================================================ datas comemorativas

    /** Datas que costumam render conteúdo para estúdio de fotografia, nos próximos {@code dias} dias. */
    public static List<String> datasComemorativas(LocalDate hoje, int dias) {
        List<String> out = new ArrayList<>();
        for (int ano = hoje.getYear(); ano <= hoje.getYear() + 1; ano++) {
            LocalDate pascoa = pascoa(ano);
            Map<LocalDate, String> m = new LinkedHashMap<>();
            m.put(LocalDate.of(ano, 1, 8), "Dia do Fotógrafo");
            m.put(pascoa.minusDays(47), "Carnaval");
            m.put(LocalDate.of(ano, 3, 8), "Dia Internacional da Mulher");
            m.put(pascoa, "Páscoa");
            m.put(LocalDate.of(ano, 5, 1).with(TemporalAdjusters.dayOfWeekInMonth(2, DayOfWeek.SUNDAY)), "Dia das Mães");
            m.put(LocalDate.of(ano, 6, 12), "Dia dos Namorados");
            m.put(LocalDate.of(ano, 6, 24), "São João");
            m.put(LocalDate.of(ano, 7, 26), "Dia dos Avós");
            m.put(LocalDate.of(ano, 8, 1).with(TemporalAdjusters.dayOfWeekInMonth(2, DayOfWeek.SUNDAY)), "Dia dos Pais");
            m.put(LocalDate.of(ano, 8, 19), "Dia Mundial da Fotografia");
            m.put(LocalDate.of(ano, 9, 15), "Dia do Cliente");
            m.put(LocalDate.of(ano, 10, 12), "Dia das Crianças");
            m.put(LocalDate.of(ano, 11, 1).with(TemporalAdjusters.dayOfWeekInMonth(4, DayOfWeek.FRIDAY)), "Black Friday");
            m.put(LocalDate.of(ano, 12, 25), "Natal");
            m.put(LocalDate.of(ano, 12, 31), "Réveillon");
            m.forEach((d, nome) -> {
                long faltam = ChronoUnit.DAYS.between(hoje, d);
                if (faltam >= 0 && faltam <= dias) out.add(nome + " (" + d.format(DIA) + ", daqui a " + faltam + " dia" + (faltam == 1 ? "" : "s") + ")");
            });
        }
        return out;
    }

    /** Domingo de Páscoa (algoritmo de Meeus/Jones/Butcher). */
    public static LocalDate pascoa(int ano) {
        int a = ano % 19, b = ano / 100, c = ano % 100, d = b / 4, e = b % 4, f = (b + 8) / 25, g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30, i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7, m = (a + 11 * h + 22 * l) / 451;
        int mes = (h + l - 7 * m + 114) / 31, dia = (h + l - 7 * m + 114) % 31 + 1;
        return LocalDate.of(ano, mes, dia);
    }

    // ================================================================ apoio

    private static String dinheiro(BigDecimal v) {
        return v == null ? "—" : NumberFormat.getCurrencyInstance(BR).format(v).replace(' ', ' ');
    }

    private static String corta(String s, int max) {
        if (s == null) return null;
        s = s.trim();
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }

    private String escrever(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
