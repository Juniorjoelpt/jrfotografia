package br.com.jrfotografia.servico;

import br.com.jrfotografia.config.Erros.NaoEncontrado;
import br.com.jrfotografia.config.Erros.RegraNegocio;
import br.com.jrfotografia.dominio.*;
import br.com.jrfotografia.dominio.Agendamento.HorarioAtendimento;
import br.com.jrfotografia.dominio.Agendamento.Reserva;
import br.com.jrfotografia.dominio.Enums.*;
import br.com.jrfotografia.dominio.Fase2.Despesa;
import br.com.jrfotografia.dominio.Repositorios.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agendamento online: a cliente escolhe um horário livre no site e faz uma pré-reserva.
 * O horário fica seguro por algumas horas (ou até o fotógrafo decidir, se ela mandou o comprovante do sinal).
 * Confirmada, vira cliente + trabalho com o sinal pago. Pela área da cliente, ela remarca ou cancela
 * conforme as regras do contrato.
 */
@Service
public class AgendaOnlineServico {

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");
    private static final Set<String> TIPOS_COMPROVANTE = Set.of("image/jpeg", "image/png", "image/webp", "application/pdf");

    private final EstudioRepo estudios;
    private final PacoteRepo pacotes;
    private final HorarioRepo horarios;
    private final ReservaRepo reservas;
    private final ClienteRepo clientes;
    private final TrabalhoRepo trabalhos;
    private final ParcelaRepo parcelas;
    private final DespesaRepo despesas;
    private final AgendaServico agenda;
    private final TrabalhoServico trabalhoServico;
    private final Armazenamento armazenamento;
    private final MarketingServico marketing;

    /** Uma trava por estúdio: duas clientes não pegam o mesmo horário ao mesmo tempo. */
    private final Map<Long, Object> travas = new ConcurrentHashMap<>();

    public AgendaOnlineServico(EstudioRepo estudios, PacoteRepo pacotes, HorarioRepo horarios, ReservaRepo reservas,
                               ClienteRepo clientes, TrabalhoRepo trabalhos, ParcelaRepo parcelas, DespesaRepo despesas,
                               AgendaServico agenda, TrabalhoServico trabalhoServico, Armazenamento armazenamento,
                               MarketingServico marketing) {
        this.marketing = marketing;
        this.estudios = estudios;
        this.pacotes = pacotes;
        this.horarios = horarios;
        this.reservas = reservas;
        this.clientes = clientes;
        this.trabalhos = trabalhos;
        this.parcelas = parcelas;
        this.despesas = despesas;
        this.agenda = agenda;
        this.trabalhoServico = trabalhoServico;
        this.armazenamento = armazenamento;
    }

    // ================================================================ horários livres

    public record Dia(LocalDate data, List<LocalTime> horarios) {}

    /** Horários livres para o pacote entre duas datas, respeitando antecedência, horizonte, folga e o que já está na agenda. */
    public List<Dia> vagas(Estudio e, Pacote p, LocalDate de, LocalDate ate, Long ignorarTrabalhoId) {
        LocalDateTime agora = LocalDateTime.now();
        LocalDate primeiro = max(de, agora.toLocalDate());
        LocalDate ultimo = min(ate, agora.toLocalDate().plusDays(e.getHorizonteDias()));
        if (ultimo.isBefore(primeiro)) return List.of();
        LocalDateTime minimo = agora.plusHours(e.getAntecedenciaHoras());
        int duracao = p.getDuracaoMin();
        int folga = e.getIntervaloMin();
        int passo = Math.max(15, e.getPassoMin());

        Map<Integer, List<HorarioAtendimento>> porDia = new HashMap<>();
        for (HorarioAtendimento h : horarios.findByEstudioIdOrderByDiaSemanaAscInicioAsc(e.getId())) {
            porDia.computeIfAbsent(h.getDiaSemana(), k -> new ArrayList<>()).add(h);
        }
        // O que já ocupa a agenda (com a folga entre ensaios)
        List<AgendaServico.Evento> ocupado = agenda.eventos(e.getId(), primeiro.atStartOfDay(), ultimo.plusDays(1).atStartOfDay())
                .stream().filter(ev -> !(ev.tipo() == AgendaServico.TipoEvento.TRABALHO && ev.id().equals(ignorarTrabalhoId))).toList();

        List<Dia> dias = new ArrayList<>();
        for (LocalDate d = primeiro; !d.isAfter(ultimo); d = d.plusDays(1)) {
            List<LocalTime> livres = new ArrayList<>();
            for (HorarioAtendimento h : porDia.getOrDefault(d.getDayOfWeek().getValue(), List.of())) {
                for (LocalTime t = h.getInicio(); !t.plusMinutes(duracao).isAfter(h.getFim()) && !t.plusMinutes(duracao).isBefore(t);
                     t = t.plusMinutes(passo)) {
                    LocalDateTime ini = d.atTime(t), fim = ini.plusMinutes(duracao);
                    if (ini.isBefore(minimo)) continue;
                    boolean choca = ocupado.stream().anyMatch(ev ->
                            ev.inicio().isBefore(fim.plusMinutes(folga)) && ev.fim().isAfter(ini.minusMinutes(folga)));
                    if (!choca && !livres.contains(t)) livres.add(t);
                    if (t.plusMinutes(passo).isBefore(t)) break; // virou a meia-noite
                }
            }
            livres.sort(Comparator.naturalOrder());
            if (!livres.isEmpty()) dias.add(new Dia(d, livres));
        }
        return dias;
    }

    private boolean horarioLivre(Estudio e, Pacote p, LocalDateTime inicio, Long ignorarTrabalhoId) {
        return vagas(e, p, inicio.toLocalDate(), inicio.toLocalDate(), ignorarTrabalhoId).stream()
                .anyMatch(d -> d.horarios().contains(inicio.toLocalTime()));
    }

    // ================================================================ pré-reserva (site)

    public record NovaReserva(Long pacoteId, LocalDateTime inicio, String nome, String telefone, String email,
                              String tipo, String mensagem, String origem, String codigo) {}

    @Transactional
    public Reserva reservar(Estudio e, NovaReserva d) {
        if (!e.isAgendaOnline()) throw new RegraNegocio("O agendamento online está desligado. Peça um orçamento pelo site.");
        Pacote p = pacotes.findByIdAndEstudioId(d.pacoteId(), e.getId())
                .filter(x -> x.isAtivo() && x.isPublico() && x.isAgendaOnline())
                .orElseThrow(() -> new NaoEncontrado("Pacote não disponível para agendamento online."));
        String fone = digitos(d.telefone());
        if (fone == null || fone.length() < 10) throw new RegraNegocio("Informe um WhatsApp com DDD.");
        if (d.nome() == null || d.nome().isBlank()) throw new RegraNegocio("Informe seu nome.");
        if (d.inicio() == null) throw new RegraNegocio("Escolha um horário.");
        synchronized (travas.computeIfAbsent(e.getId(), k -> new Object())) {
            if (!horarioLivre(e, p, d.inicio(), null)) {
                throw new RegraNegocio("Este horário acabou de ser ocupado. Escolha outro, por favor.");
            }
            Reserva r = new Reserva();
            r.setEstudioId(e.getId());
            r.setToken(TrabalhoServico.novoTokenGaleria());
            r.setPacote(p);
            r.setInicio(d.inicio());
            r.setFim(d.inicio().plusMinutes(p.getDuracaoMin()));
            r.setNome(d.nome().trim());
            r.setTelefone(fone);
            r.setEmail(vazioNulo(d.email()));
            r.setTipo(vazioNulo(d.tipo()));
            r.setMensagem(vazioNulo(d.mensagem()));
            r.setOrigem(d.origem() != null ? d.origem() : Origem.SITE.name());
            aplicarPreco(r, e, p.getPreco(), e.getSinalPercentual(), d.codigo(), null);
            r.setExpiraEm(LocalDateTime.now().plusHours(e.getReservaHoras()));
            return reservas.saveAndFlush(r);
        }
    }

    /** Preço com o desconto do cupom ou da indicação (se o código valer) e o sinal sobre o valor final. */
    private void aplicarPreco(Reserva r, Estudio e, BigDecimal preco, int sinalPercentual, String codigo, Long campanhaId) {
        BigDecimal total = preco;
        if (codigo != null && !codigo.isBlank()) {
            MarketingServico.Desconto d = marketing.avaliar(e, codigo, preco, campanhaId, r.getTelefone());
            if (!d.valido()) throw new RegraNegocio(d.mensagem());
            total = d.valorFinal();
            r.setCupomCodigo(d.codigo());
            r.setDesconto(d.desconto());
            r.setIndicadorClienteId(d.indicadorClienteId());
            if (d.origem() == MarketingServico.Origem.INDICACAO) r.setOrigem(Origem.INDICACAO.name());
        }
        r.setValorTotal(total);
        r.setValorSinal(total.multiply(BigDecimal.valueOf(sinalPercentual)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
    }

    // ================================================================ campanhas (mini-sessões)

    /** Horários livres da campanha: dias e janelas próprios, sessões de duração fixa com intervalo entre elas. */
    public List<Dia> vagasCampanha(Estudio e, br.com.jrfotografia.dominio.Marketing.Campanha c) {
        LocalDateTime minimo = LocalDateTime.now().plusHours(Math.min(e.getAntecedenciaHoras(), 2));
        var lista = marketing.dias(c);
        if (lista.isEmpty()) return List.of();
        LocalDate primeiro = lista.get(0).getData(), ultimo = lista.get(lista.size() - 1).getData();
        List<AgendaServico.Evento> ocupado = agenda.eventos(e.getId(), primeiro.atStartOfDay(), ultimo.plusDays(1).atStartOfDay());
        Map<LocalDate, List<LocalTime>> porData = new java.util.TreeMap<>();
        for (var d : lista) {
            int passo = c.getDuracaoMin() + c.getIntervaloMin();
            for (LocalTime t = d.getInicio(); !t.plusMinutes(c.getDuracaoMin()).isAfter(d.getFim()); t = t.plusMinutes(passo)) {
                LocalDateTime ini = d.getData().atTime(t), fim = ini.plusMinutes(c.getDuracaoMin());
                if (ini.isBefore(minimo)) continue;
                boolean choca = ocupado.stream().anyMatch(ev -> ev.inicio().isBefore(fim) && ev.fim().isAfter(ini));
                if (!choca) porData.computeIfAbsent(d.getData(), k -> new ArrayList<>()).add(t);
                if (t.plusMinutes(passo).isBefore(t)) break;
            }
        }
        List<Dia> r = new ArrayList<>();
        porData.forEach((d, hs) -> { hs.sort(Comparator.naturalOrder()); r.add(new Dia(d, hs)); });
        return r;
    }

    @Transactional
    public Reserva reservarCampanha(Estudio e, br.com.jrfotografia.dominio.Marketing.Campanha c, NovaReserva d) {
        String fone = digitos(d.telefone());
        if (fone == null || fone.length() < 10) throw new RegraNegocio("Informe um WhatsApp com DDD.");
        if (d.nome() == null || d.nome().isBlank()) throw new RegraNegocio("Informe seu nome.");
        if (d.inicio() == null) throw new RegraNegocio("Escolha um horário.");
        Pacote p = marketing.pacote(c);
        synchronized (travas.computeIfAbsent(e.getId(), k -> new Object())) {
            boolean livre = vagasCampanha(e, c).stream()
                    .anyMatch(x -> x.data().equals(d.inicio().toLocalDate()) && x.horarios().contains(d.inicio().toLocalTime()));
            if (!livre) throw new RegraNegocio("Este horário acabou de ser ocupado. Escolha outro, por favor.");
            Reserva r = new Reserva();
            r.setEstudioId(e.getId());
            r.setToken(TrabalhoServico.novoTokenGaleria());
            r.setPacote(p);
            r.setCampanhaId(c.getId());
            r.setInicio(d.inicio());
            r.setFim(d.inicio().plusMinutes(c.getDuracaoMin()));
            r.setNome(d.nome().trim());
            r.setTelefone(fone);
            r.setEmail(vazioNulo(d.email()));
            r.setTipo(c.getTitulo());
            r.setMensagem(vazioNulo(d.mensagem()));
            r.setOrigem(d.origem() != null ? d.origem() : Origem.SITE.name());
            aplicarPreco(r, e, c.getPreco(), c.getSinalPercentual(), d.codigo(), c.getId());
            r.setExpiraEm(LocalDateTime.now().plusHours(e.getReservaHoras()));
            return reservas.saveAndFlush(r);
        }
    }

    public static BigDecimal sinal(Estudio e, BigDecimal preco) {
        return preco.multiply(BigDecimal.valueOf(e.getSinalPercentual())).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    public Reserva porToken(String token) {
        return reservas.findByToken(token).orElseThrow(() -> new NaoEncontrado("Reserva não encontrada."));
    }

    /** A cliente manda a foto ou o PDF do comprovante do Pix; o horário fica seguro até o fotógrafo decidir. */
    @Transactional
    public Reserva enviarComprovante(String token, MultipartFile arquivo) {
        Reserva r = porToken(token);
        if (r.statusAtual() != StatusReserva.PENDENTE) {
            throw new RegraNegocio(r.statusAtual() == StatusReserva.EXPIRADA
                    ? "Esta pré-reserva venceu. Faça uma nova escolhendo o horário de novo."
                    : "Esta reserva não está mais aguardando comprovante.");
        }
        String tipo = arquivo.getContentType() == null ? "" : arquivo.getContentType().toLowerCase();
        if (!TIPOS_COMPROVANTE.contains(tipo)) throw new RegraNegocio("Envie o comprovante como imagem (JPG ou PNG) ou PDF.");
        if (arquivo.getSize() > 10L * 1024 * 1024) throw new RegraNegocio("Comprovante grande demais (máximo 10 MB).");
        byte[] bytes;
        try { bytes = arquivo.getBytes(); } catch (IOException ex) { throw new RegraNegocio("Não foi possível ler o arquivo."); }
        if (!assinaturaConfere(tipo, bytes)) throw new RegraNegocio("O arquivo não parece ser uma imagem ou PDF válido.");
        armazenamento.apagar(r.getComprovante());
        String ext = switch (tipo) { case "application/pdf" -> ".pdf"; case "image/png" -> ".png"; case "image/webp" -> ".webp"; default -> ".jpg"; };
        r.setComprovante(r.getEstudioId() + "/reservas/" + r.getToken() + "_comprovante" + ext);
        r.setComprovanteTipo(tipo);
        r.setComprovanteEm(LocalDateTime.now());
        armazenamento.gravar(r.getComprovante(), bytes);
        return r;
    }

    private static boolean assinaturaConfere(String tipo, byte[] b) {
        if (b.length < 8) return false;
        return switch (tipo) {
            case "application/pdf" -> b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F';
            case "image/png" -> (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
            case "image/webp" -> b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F';
            default -> (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8;
        };
    }

    // ================================================================ painel: confirmar ou recusar

    /** Confirma o sinal: cria (ou reaproveita) a cliente e o trabalho, com o sinal já pago e o restante para o dia do ensaio. */
    @Transactional
    public Reserva confirmar(Long estudioId, Long id) {
        Reserva r = reservas.findByIdAndEstudioId(id, estudioId).orElseThrow(() -> new NaoEncontrado("Reserva não encontrada"));
        if (r.getStatus() == StatusReserva.CONFIRMADA) return r;
        if (r.getStatus() != StatusReserva.PENDENTE) throw new RegraNegocio("Esta reserva já foi " + r.getStatus().name().toLowerCase() + ".");
        Estudio e = estudios.findById(estudioId).orElseThrow();
        // Vencida sem comprovante: só confirma se o horário ainda estiver livre.
        if (r.statusAtual() == StatusReserva.EXPIRADA) {
            boolean livre = agenda.eventos(estudioId, r.getInicio(), r.getFim()).stream()
                    .noneMatch(ev -> !(ev.tipo() == AgendaServico.TipoEvento.RESERVA && ev.id().equals(r.getId())));
            if (!livre) throw new RegraNegocio("A pré-reserva venceu e o horário já foi ocupado.");
        }
        Cliente c = clientes.findByEstudioIdAndTelefone(estudioId, r.getTelefone()).stream().findFirst().orElse(null);
        if (c == null) {
            c = new Cliente();
            c.setEstudioId(estudioId);
            c.setNome(r.getNome());
            c.setTelefone(r.getTelefone());
            c.setEmail(r.getEmail());
            c.setOrigem(origem(r.getOrigem()));
            clientes.save(c);
        }
        boolean temSinal = r.getValorSinal().signum() > 0 && r.getValorSinal().compareTo(r.getValorTotal()) < 0;
        Trabalho t = trabalhoServico.criar(estudioId, new TrabalhoServico.NovoTrabalho(
                c.getId(), r.getPacote().getId(), null, r.getTipo(), r.getInicio(), null, r.getValorTotal(),
                temSinal ? FormaPagamento.PARCELADO : FormaPagamento.AVISTA, temSinal ? r.getValorSinal() : null,
                temSinal ? 1 : null, r.getInicio().toLocalDate(),
                (r.getCampanhaId() != null ? "Mini-sessão agendada pelo site." : "Agendado pelo site.")
                        + (r.getCupomCodigo() != null ? "\nCódigo " + r.getCupomCodigo() + ": desconto de R$ " + r.getDesconto() : "")
                        + (r.getMensagem() != null ? "\n" + r.getMensagem() : "")));
        t.setDuracaoMin(r.getPacote().getDuracaoMin());
        // O sinal já foi pago (comprovante conferido pelo fotógrafo)
        for (Parcela p : parcelas.findByTrabalhoIdOrderByVencimentoAscIdAsc(t.getId())) {
            if (p.getTipo() == TipoParcela.SINAL || (!temSinal && r.getValorSinal().signum() > 0)) {
                p.setPagoEm(LocalDate.now());
                p.setForma(MeioPagamento.PIX);
            }
            if (p.getTipo() == TipoParcela.PARCELA) p.setDescricao("Restante (no dia do ensaio)");
        }
        r.setStatus(StatusReserva.CONFIRMADA);
        r.setClienteId(c.getId());
        r.setTrabalhoId(t.getId());
        marketing.aoConfirmar(r, t.getId());
        return r;
    }

    @Transactional
    public Reserva recusar(Long estudioId, Long id, String motivo) {
        Reserva r = reservas.findByIdAndEstudioId(id, estudioId).orElseThrow(() -> new NaoEncontrado("Reserva não encontrada"));
        if (r.getStatus() != StatusReserva.PENDENTE) throw new RegraNegocio("Só dá para recusar uma pré-reserva pendente.");
        r.setStatus(StatusReserva.RECUSADA);
        r.setMotivoRecusa(vazioNulo(motivo));
        return r;
    }

    // ================================================================ área da cliente: remarcar e cancelar

    public record Regras(boolean podeRemarcar, String motivoRemarcar, boolean podeCancelar, boolean reembolsaSinal,
                         String textoCancelamento, BigDecimal sinalPago) {}

    /** Link da área da cliente de um trabalho (criado na hora para trabalhos antigos). */
    public String trabalhoDaClienteOuNulo(Long trabalhoId) {
        return trabalhos.findById(trabalhoId).map(trabalhoServico::garantirTokenCliente).orElse(null);
    }

    public Trabalho trabalhoDaCliente(String token) {
        return trabalhos.findByTokenCliente(token).orElseThrow(() -> new NaoEncontrado("Link não encontrado. Confira com o fotógrafo."));
    }

    public Regras regras(Trabalho t) {
        Estudio e = estudios.findById(t.getEstudioId()).orElseThrow();
        LocalDateTime agora = LocalDateTime.now();
        BigDecimal sinalPago = parcelas.findByTrabalhoIdOrderByVencimentoAscIdAsc(t.getId()).stream()
                .filter(p -> p.getTipo() == TipoParcela.SINAL && p.paga()).map(Parcela::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (t.getStatus() != StatusTrabalho.AGENDADO || t.getDataTrabalho() == null) {
            return new Regras(false, t.getStatus() == StatusTrabalho.CANCELADO ? "Este ensaio foi cancelado." : "O ensaio já aconteceu.",
                    false, false, null, sinalPago);
        }
        String motivo = null;
        if (t.getRemarcacoes() >= e.getRemarcacoesMax()) {
            motivo = e.getRemarcacoesMax() == 0 ? "Remarcação só falando com o fotógrafo."
                    : "Você já usou " + (e.getRemarcacoesMax() == 1 ? "a remarcação sem custo" : "as remarcações sem custo") + ". Fale com o fotógrafo.";
        } else if (agora.isAfter(t.getDataTrabalho().minusHours(e.getRemarcacaoHoras()))) {
            motivo = "Pelo contrato, a remarcação precisa de pelo menos " + e.getRemarcacaoHoras() + " horas de antecedência. Fale com o fotógrafo.";
        }
        boolean reembolsa = !agora.isAfter(t.getDataTrabalho().minusDays(e.getReembolsoDias()));
        String texto = sinalPago.signum() == 0 ? "Não há sinal pago para este ensaio."
                : reembolsa ? "Cancelando agora, com " + e.getReembolsoDias() + " dias ou mais de antecedência, o sinal é devolvido."
                : "Pelo contrato, cancelando a menos de " + e.getReembolsoDias() + " dias do ensaio o sinal não é devolvido.";
        return new Regras(motivo == null, motivo, true, reembolsa && sinalPago.signum() > 0, texto, sinalPago);
    }

    @Transactional
    public Trabalho remarcar(String token, LocalDateTime novoInicio) {
        Trabalho t = trabalhoDaCliente(token);
        Regras r = regras(t);
        if (!r.podeRemarcar()) throw new RegraNegocio(r.motivoRemarcar());
        Estudio e = estudios.findById(t.getEstudioId()).orElseThrow();
        if (t.getPacote() == null) throw new RegraNegocio("Fale com o fotógrafo para remarcar este ensaio.");
        synchronized (travas.computeIfAbsent(e.getId(), k -> new Object())) {
            if (!horarioLivre(e, t.getPacote(), novoInicio, t.getId())) {
                throw new RegraNegocio("Este horário não está mais livre. Escolha outro, por favor.");
            }
            LocalDateTime antes = t.getDataTrabalho();
            t.setDataTrabalho(novoInicio);
            t.setRemarcacoes(t.getRemarcacoes() + 1);
            t.setPrazoEntrega(Prazos.somarDiasUteis(novoInicio.toLocalDate(), t.getPacote().getPrazoEntregaDias()));
            t.setObservacoes(nota(t.getObservacoes(), "Remarcado pela cliente de " + DATA_HORA.format(antes) + " para " + DATA_HORA.format(novoInicio) + "."));
            // O restante que vencia no dia do ensaio acompanha a nova data.
            for (Parcela p : parcelas.findByTrabalhoIdOrderByVencimentoAscIdAsc(t.getId())) {
                if (!p.paga() && p.getVencimento() != null && p.getVencimento().equals(antes.toLocalDate())) p.setVencimento(novoInicio.toLocalDate());
            }
            return t;
        }
    }

    @Transactional
    public Regras cancelar(String token, String motivo) {
        Trabalho t = trabalhoDaCliente(token);
        Regras r = regras(t);
        if (!r.podeCancelar()) throw new RegraNegocio(r.motivoRemarcar() != null ? r.motivoRemarcar() : "Este ensaio não pode mais ser cancelado por aqui.");
        t.setStatus(StatusTrabalho.CANCELADO);
        t.setCanceladoEm(LocalDateTime.now());
        t.setCanceladoPor("CLIENTE");
        t.setMotivoCancelamento(vazioNulo(motivo));
        t.setObservacoes(nota(t.getObservacoes(), "Cancelado pela cliente em " + DATA_HORA.format(LocalDateTime.now())
                + (r.reembolsaSinal() ? ". Sinal a devolver." : ".")));
        if (r.reembolsaSinal()) {
            // Entra em Contas a pagar para o fotógrafo não esquecer de devolver.
            Despesa d = new Despesa();
            d.setEstudioId(t.getEstudioId());
            d.setDescricao("Devolver sinal · " + t.getCliente().getNome());
            d.setCategoria("Devoluções");
            d.setValor(r.sinalPago());
            d.setVencimento(LocalDate.now().plusDays(2));
            d.setTrabalhoId(t.getId());
            despesas.save(d);
        }
        return r;
    }

    // ================================================================ apoio

    private static String nota(String atual, String nova) {
        return atual == null || atual.isBlank() ? nova : atual + "\n" + nova;
    }

    private static Origem origem(String o) {
        try { return o == null ? Origem.SITE : Origem.valueOf(o); } catch (IllegalArgumentException ex) { return Origem.SITE; }
    }

    private static String digitos(String s) {
        if (s == null) return null;
        String d = s.replaceAll("\\D", "");
        if (d.length() > 11 && d.startsWith("55")) d = d.substring(2);
        return d.isEmpty() ? null : d;
    }

    private static String vazioNulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static LocalDate max(LocalDate a, LocalDate b) { return a.isAfter(b) ? a : b; }

    private static LocalDate min(LocalDate a, LocalDate b) { return a.isBefore(b) ? a : b; }
}
