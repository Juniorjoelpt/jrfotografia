package br.com.jrfotografia.servico;

import br.com.jrfotografia.config.AppProps;
import br.com.jrfotografia.config.Erros.NaoEncontrado;
import br.com.jrfotografia.config.Erros.RegraNegocio;
import br.com.jrfotografia.dominio.Agendamento.Reserva;
import br.com.jrfotografia.dominio.Cliente;
import br.com.jrfotografia.dominio.CobrancaPix;
import br.com.jrfotografia.dominio.ContratoAssinatura;
import br.com.jrfotografia.dominio.Enums.MeioPagamento;
import br.com.jrfotografia.dominio.Enums.StatusReserva;
import br.com.jrfotografia.dominio.Enums.StatusTrabalho;
import br.com.jrfotografia.dominio.Estudio;
import br.com.jrfotografia.dominio.Parcela;
import br.com.jrfotografia.dominio.Repositorios.*;
import br.com.jrfotografia.dominio.Trabalho;
import br.com.jrfotografia.servico.PagBankCliente.FalhaPagBank;
import br.com.jrfotografia.servico.PagBankCliente.Situacao;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pix com confirmação automática pelo PagBank.
 * A cliente gera o QR Code (valor exato) na página da reserva ou na área dela; quando o PagBank avisa
 * (notificação) ou quando a consulta mostra o pedido pago, o sinal/parcela fica pago e a reserva é confirmada sozinha.
 * Três caminhos levam ao mesmo lugar: notificação do PagBank, a página da cliente perguntando, e a verificação a cada 2 minutos.
 */
@Service
public class PixServico {

    private static final Logger log = LoggerFactory.getLogger(PixServico.class);
    private static final ZoneId FUSO = ZoneId.of("America/Fortaleza");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final EstudioRepo estudios;
    private final ReservaRepo reservas;
    private final ParcelaRepo parcelas;
    private final TrabalhoRepo trabalhos;
    private final ClienteRepo clientes;
    private final CobrancaPixRepo cobrancas;
    private final Cifra cifra;
    private final PagBankCliente pagbank;
    private final AgendaOnlineServico agendaOnline;
    private final BoasVindasServico boasVindas;
    private final EmailAvisos avisos;
    private final AppProps props;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    private final Map<String, Object> travas = new ConcurrentHashMap<>();

    public PixServico(EstudioRepo estudios, ReservaRepo reservas, ParcelaRepo parcelas, TrabalhoRepo trabalhos, ClienteRepo clientes,
                      CobrancaPixRepo cobrancas, Cifra cifra, PagBankCliente pagbank, AgendaOnlineServico agendaOnline,
                      BoasVindasServico boasVindas, EmailAvisos avisos, AppProps props, ObjectMapper json,
                      PlatformTransactionManager transacoes) {
        this.estudios = estudios;
        this.reservas = reservas;
        this.parcelas = parcelas;
        this.trabalhos = trabalhos;
        this.clientes = clientes;
        this.cobrancas = cobrancas;
        this.cifra = cifra;
        this.pagbank = pagbank;
        this.agendaOnline = agendaOnline;
        this.boasVindas = boasVindas;
        this.avisos = avisos;
        this.props = props;
        this.json = json;
        this.tx = new TransactionTemplate(transacoes);
    }

    /** O que a página da cliente precisa para mostrar o QR Code. */
    public record PixDto(Long id, String texto, BigDecimal valor, LocalDateTime expiraEm, String status, boolean sandbox) {
        public static PixDto de(CobrancaPix c) {
            if (c == null) return null;
            String status = vencida(c) || CobrancaPix.CANCELADA.equals(c.getStatus()) ? CobrancaPix.EXPIRADA : c.getStatus();
            return new PixDto(c.getId(), CobrancaPix.AGUARDANDO.equals(c.getStatus()) ? c.getTexto() : null, c.getValor(), c.getExpiraEm(),
                    status, c.isSandbox());
        }
    }

    static boolean vencida(CobrancaPix c) {
        return CobrancaPix.AGUARDANDO.equals(c.getStatus()) && c.getExpiraEm().isBefore(LocalDateTime.now());
    }

    // ================================================================ gerar o QR Code

    /** Sinal da pré-reserva. Reaproveita o QR Code ainda válido em vez de criar outro. */
    public CobrancaPix cobrarReserva(Reserva r, String cpf) {
        Estudio e = estudios.findById(r.getEstudioId()).orElseThrow();
        exigirAtivo(e);
        if (r.statusAtual() != StatusReserva.PENDENTE) {
            throw new RegraNegocio(r.statusAtual() == StatusReserva.EXPIRADA ? "Esta pré-reserva venceu. Faça uma nova escolhendo o horário de novo."
                    : "Esta reserva não está mais aguardando pagamento.");
        }
        if (r.getValorSinal() == null || r.getValorSinal().signum() <= 0) throw new RegraNegocio("Esta reserva não tem sinal a pagar.");
        synchronized (travas.computeIfAbsent("r" + r.getId(), k -> new Object())) {
            CobrancaPix atual = reaproveitar(cobrancas.findFirstByReservaIdOrderByIdDesc(r.getId()).orElse(null), r.getValorSinal());
            if (atual != null) return atual;
            LocalDateTime minimo = LocalDateTime.now().plusMinutes(15);
            LocalDateTime expira = r.getComprovanteEm() == null && r.getExpiraEm().isAfter(minimo) ? r.getExpiraEm() : minimo;
            if (r.getComprovanteEm() != null) expira = LocalDateTime.now().plusHours(24);
            String descricao = "Sinal · " + r.getPacote().getNome();
            return criar(e, descricao, r.getValorSinal(), expira, r.getNome(), r.getEmail(), r.getTelefone(), cpf, "reserva-" + r.getId(),
                    c -> c.setReservaId(r.getId()));
        }
    }

    /** Parcela em aberto (área da cliente). */
    public CobrancaPix cobrarParcela(Trabalho t, Long parcelaId, String cpf) {
        Estudio e = estudios.findById(t.getEstudioId()).orElseThrow();
        exigirAtivo(e);
        Parcela p = parcelas.findById(parcelaId).filter(x -> x.getTrabalhoId().equals(t.getId()))
                .orElseThrow(() -> new NaoEncontrado("Pagamento não encontrado."));
        if (p.paga()) throw new RegraNegocio("Este pagamento já consta como pago.");
        if (t.getStatus() == StatusTrabalho.CANCELADO) throw new RegraNegocio("Este ensaio foi cancelado. Fale com o fotógrafo.");
        Cliente c = t.getCliente();
        String doc = cpf == null || cpf.isBlank() ? c.getCpf() : cpf;
        synchronized (travas.computeIfAbsent("p" + p.getId(), k -> new Object())) {
            CobrancaPix atual = reaproveitar(cobrancas.findFirstByParcelaIdOrderByIdDesc(p.getId()).orElse(null), p.getValor());
            if (atual != null) return atual;
            CobrancaPix nova = criar(e, p.getDescricao() + " · " + t.getTitulo(), p.getValor(), LocalDateTime.now().plusDays(3), c.getNome(),
                    c.getEmail(), c.getTelefone(), doc, "parcela-" + p.getId(), x -> x.setParcelaId(p.getId()));
            // O CPF informado completa o cadastro, se estava vazio.
            if (c.getCpf() == null || c.getCpf().isBlank()) {
                String d = doc.replaceAll("\\D", "");
                if (d.length() == 11) tx.executeWithoutResult(s -> clientes.findById(c.getId()).ifPresent(cl ->
                        cl.setCpf(d.substring(0, 3) + "." + d.substring(3, 6) + "." + d.substring(6, 9) + "-" + d.substring(9))));
            }
            return nova;
        }
    }

    public Optional<CobrancaPix> daReserva(Long reservaId) {
        return cobrancas.findFirstByReservaIdOrderByIdDesc(reservaId);
    }

    public Optional<CobrancaPix> daParcela(Long parcelaId) {
        return cobrancas.findFirstByParcelaIdOrderByIdDesc(parcelaId);
    }

    /** QR Code ainda bom (mesmo valor e com alguns minutos de folga)? Se o anterior foi pago, devolve ele. */
    private CobrancaPix reaproveitar(CobrancaPix c, BigDecimal valor) {
        if (c == null) return null;
        if (CobrancaPix.AGUARDANDO.equals(c.getStatus())) {
            if (c.getValor().compareTo(valor) == 0 && c.getExpiraEm().isAfter(LocalDateTime.now().plusMinutes(3))) return c;
            // Vai trocar de QR Code: antes confere se o anterior não foi pago.
            try {
                c = verificar(c, Duration.ZERO);
            } catch (FalhaPagBank ex) {
                throw new RegraNegocio(ex.getMessage());
            }
            if (CobrancaPix.PAGA.equals(c.getStatus())) return c;
            c.setStatus(CobrancaPix.EXPIRADA);
            cobrancas.save(c);
        }
        return CobrancaPix.PAGA.equals(c.getStatus()) ? c : null;
    }

    private CobrancaPix criar(Estudio e, String descricao, BigDecimal valor, LocalDateTime expira, String nome, String email, String telefone,
                              String cpf, String referencia, java.util.function.Consumer<CobrancaPix> vinculo) {
        String doc = cpf == null ? "" : cpf.replaceAll("\\D", "");
        if (!(doc.length() == 11 && AssinaturaServico.cpfValido(doc)) && doc.length() != 14) {
            throw new RegraNegocio("Informe o CPF de quem vai pagar (o PagBank pede para gerar o Pix).");
        }
        long centavos = valor.movePointRight(2).longValueExact();
        Map<String, Object> cliente = new LinkedHashMap<>();
        cliente.put("name", limitar(nome, 120));
        String mail = email != null && !email.isBlank() ? email : e.getEmail();
        if (mail != null && !mail.isBlank()) cliente.put("email", mail.trim());
        cliente.put("tax_id", doc);
        String fone = telefone == null ? "" : telefone.replaceAll("\\D", "");
        if (fone.length() >= 10 && fone.length() <= 11) {
            cliente.put("phones", List.of(Map.of("country", "55", "area", fone.substring(0, 2), "number", fone.substring(2), "type", "MOBILE")));
        }
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("reference_id", referencia);
        corpo.put("customer", cliente);
        corpo.put("items", List.of(Map.of("reference_id", referencia, "name", limitar(descricao, 64), "quantity", 1, "unit_amount", centavos)));
        corpo.put("qr_codes", List.of(Map.of("amount", Map.of("value", centavos), "expiration_date", ISO.format(expira.atZone(FUSO)))));
        String notificacao = urlNotificacao(e);
        // O PagBank só entrega notificação em endereço público com https (no computador de casa, a consulta automática resolve).
        if (notificacao.startsWith("https://")) corpo.put("notification_urls", List.of(notificacao));

        PagBankCliente.PedidoPix pedido;
        try {
            pedido = pagbank.criarPedido(token(e), e.isPagbankSandbox(), corpo, referencia + "-" + UUID.randomUUID());
        } catch (FalhaPagBank ex) {
            throw new RegraNegocio(ex.getMessage());
        }
        CobrancaPix c = new CobrancaPix();
        c.setEstudioId(e.getId());
        vinculo.accept(c);
        c.setDescricao(limitar(descricao, 160));
        c.setNome(limitar(nome, 120));
        c.setValor(valor);
        c.setPedidoId(pedido.id());
        c.setTexto(pedido.texto());
        c.setSandbox(e.isPagbankSandbox());
        c.setExpiraEm(expira);
        return cobrancas.save(c);
    }

    // ================================================================ saber se foi pago

    /** Pergunta ao PagBank (no máximo uma vez a cada {@code intervalo}) e, se pago, dá baixa. */
    public CobrancaPix verificar(CobrancaPix c, Duration intervalo) {
        if (!CobrancaPix.AGUARDANDO.equals(c.getStatus()) && !CobrancaPix.CANCELADA.equals(c.getStatus())) return c;
        if (c.getVerificadoEm() != null && c.getVerificadoEm().plus(intervalo).isAfter(LocalDateTime.now())) return c;
        Estudio e = estudios.findById(c.getEstudioId()).orElseThrow();
        if (e.getPagbankToken() == null) return c;
        c.setVerificadoEm(LocalDateTime.now());
        cobrancas.save(c);
        Situacao s = pagbank.consultar(token(e), c.isSandbox(), c.getPedidoId());
        if (s.pago()) return pago(c, s.valorPago());
        // Vencida há mais de meia hora e não paga: encerra.
        if (c.getExpiraEm().plusMinutes(30).isBefore(LocalDateTime.now())) {
            c.setStatus(CobrancaPix.EXPIRADA);
            cobrancas.save(c);
        }
        return c;
    }

    /** Página da cliente aberta esperando: verifica a cada poucos segundos, sem derrubar a página se o PagBank falhar. */
    public CobrancaPix verificarSemFalhar(CobrancaPix c) {
        try {
            return verificar(c, Duration.ofSeconds(8));
        } catch (FalhaPagBank ex) {
            return c;
        }
    }

    /**
     * Notificação do PagBank: confere o x-authenticity-token (SHA-256 de "token-corpo") e, valendo,
     * consulta o pedido na API (a fonte da verdade) antes de dar baixa. Devolve false se a assinatura não confere.
     */
    public boolean notificacao(byte[] corpo, String assinatura) {
        JsonNode n;
        try {
            n = json.readTree(corpo);
        } catch (Exception ex) {
            return false;
        }
        String pedidoId = n.path("id").asText("");
        if (!pedidoId.startsWith("ORDE_")) return true; // outro tipo de aviso: só confirma o recebimento
        CobrancaPix c = cobrancas.findByPedidoId(pedidoId).orElse(null);
        if (c == null) return true; // pedido que não é deste sistema
        Estudio e = estudios.findById(c.getEstudioId()).orElseThrow();
        if (e.getPagbankToken() == null || !PagBankCliente.assinaturaConfere(token(e), corpo, assinatura)) {
            log.warn("Notificação do PagBank com assinatura que não confere (pedido {}).", pedidoId);
            return false;
        }
        try {
            verificar(c, Duration.ZERO);
        } catch (FalhaPagBank ex) {
            // A consulta falhou: a verificação periódica tenta de novo.
            log.warn("Notificação recebida, mas a consulta ao PagBank falhou: {}", ex.getMessage());
        }
        return true;
    }

    /** A cada 2 minutos: Pix em aberto dos últimos 4 dias (caso a notificação não tenha chegado). */
    @Scheduled(fixedDelay = 120_000, initialDelay = 90_000)
    public void verificarPendentes() {
        if (props.pagbank() != null && Boolean.FALSE.equals(props.pagbank().verificarAuto())) return;
        for (CobrancaPix c : cobrancas.findByStatusInAndCriadoEmAfter(List.of(CobrancaPix.AGUARDANDO, CobrancaPix.CANCELADA), LocalDateTime.now().minusDays(4))) {
            try {
                verificar(c, Duration.ofSeconds(100));
            } catch (Exception ex) {
                log.debug("Verificação do Pix {} falhou: {}", c.getId(), ex.getMessage());
            }
        }
    }

    /**
     * O fotógrafo deu baixa à mão: o Pix aberto daquela parcela sai da área da cliente. Continua sendo consultado
     * até vencer; se ela tiver pago mesmo assim, o aviso de "pago duas vezes" chega ao fotógrafo.
     */
    public void encerrarDaParcela(Long parcelaId) {
        tx.executeWithoutResult(s -> cobrancas.findByParcelaIdAndStatus(parcelaId, CobrancaPix.AGUARDANDO).forEach(c -> {
            c.setStatus(CobrancaPix.CANCELADA);
            c.setErro("Parcela marcada como paga à mão no painel; o QR Code saiu da área da cliente.");
        }));
    }

    // ================================================================ deu baixa

    private CobrancaPix pago(CobrancaPix c, BigDecimal valorPago) {
        if (valorPago.compareTo(c.getValor()) < 0) {
            log.warn("Pix {} pago com valor menor ({} de {}).", c.getPedidoId(), valorPago, c.getValor());
            return c;
        }
        Integer n = tx.execute(s -> cobrancas.marcarPaga(c.getId(), LocalDateTime.now(), valorPago));
        CobrancaPix atual = cobrancas.findById(c.getId()).orElseThrow();
        if (n == null || n == 0) return atual; // outro caminho já deu baixa
        Estudio e = estudios.findById(c.getEstudioId()).orElseThrow();
        String problema = null;
        try {
            if (c.getReservaId() != null) problema = confirmarReserva(c);
            else if (c.getParcelaId() != null) problema = baixarParcela(c);
        } catch (RuntimeException ex) {
            problema = ex instanceof RegraNegocio ? ex.getMessage() : "Erro ao dar baixa: " + ex.getMessage();
            log.warn("Pix {} pago, mas a baixa automática falhou", c.getPedidoId(), ex);
        }
        if (problema != null) {
            String p = problema;
            tx.executeWithoutResult(s -> cobrancas.findById(c.getId()).ifPresent(x -> x.setErro(limitar(p, 300))));
            atual.setErro(limitar(p, 300));
        }
        avisos.pixRecebido(e, atual, problema);
        return atual;
    }

    /** Sinal pago: confirma a reserva como se o fotógrafo tivesse conferido o comprovante. */
    private String confirmarReserva(CobrancaPix c) {
        Reserva antes = reservas.findById(c.getReservaId()).orElseThrow();
        if (antes.getStatus() == StatusReserva.CONFIRMADA) {
            return "A reserva já tinha sido confirmada antes do Pix. Confira se o sinal não foi pago duas vezes.";
        }
        if (antes.getStatus() != StatusReserva.PENDENTE) {
            return "O Pix entrou, mas a reserva já estava " + antes.getStatus().name().toLowerCase() + ". Combine com a cliente (devolver ou remarcar).";
        }
        Reserva r;
        try {
            r = agendaOnline.confirmar(c.getEstudioId(), c.getReservaId());
        } catch (RegraNegocio ex) {
            return "O Pix entrou, mas não deu para confirmar sozinho: " + ex.getMessage() + " Combine com a cliente.";
        }
        Long trabalhoId = r.getTrabalhoId();
        tx.executeWithoutResult(s -> trabalhos.findById(trabalhoId).ifPresent(t -> t.setObservacoes(
                (t.getObservacoes() == null || t.getObservacoes().isBlank() ? "" : t.getObservacoes() + "\n")
                        + "Sinal pago pelo Pix (PagBank, pedido " + c.getPedidoId() + ") e confirmado automaticamente.")));
        var links = boasVindas.preparar(c.getEstudioId(), trabalhoId);
        avisos.reservaConfirmada(r, links.area(), links.contrato());
        return null;
    }

    private String baixarParcela(CobrancaPix c) {
        return tx.execute(s -> {
            Parcela p = parcelas.findById(c.getParcelaId()).orElseThrow();
            if (p.paga()) return "A parcela já estava marcada como paga. Confira se não foi paga duas vezes.";
            p.setPagoEm(LocalDate.now());
            p.setForma(MeioPagamento.PIX);
            return null;
        });
    }

    // ================================================================ apoio

    public String urlNotificacao(Estudio e) {
        return avisos.url(e, "/api/publico/pagbank/notificacao");
    }

    private static void exigirAtivo(Estudio e) {
        if (!e.pixAutomatico()) throw new RegraNegocio("O Pix automático não está ligado. Use a chave Pix e envie o comprovante.");
    }

    private String token(Estudio e) {
        return cifra.decifrar(e.getPagbankToken());
    }

    private static String limitar(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
