package br.com.jrfotografia.servico;

import br.com.jrfotografia.config.Erros.NaoEncontrado;
import br.com.jrfotografia.config.Erros.RegraNegocio;
import br.com.jrfotografia.dominio.*;
import br.com.jrfotografia.dominio.Enums.FormaPagamento;
import br.com.jrfotografia.dominio.Enums.Origem;
import br.com.jrfotografia.dominio.Enums.StatusOrcamento;
import br.com.jrfotografia.dominio.Enums.StatusPedido;
import br.com.jrfotografia.dominio.Repositorios.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

/**
 * Orçamentos: montados no painel, enviados por link e aceitos pelo cliente.
 * O aceite cria (ou reaproveita) o cliente e gera o trabalho com o plano de pagamento combinado.
 */
@Service
public class OrcamentoServico {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final OrcamentoRepo orcamentos;
    private final PacoteRepo pacotes;
    private final ClienteRepo clientes;
    private final PedidoRepo pedidos;
    private final TrabalhoServico trabalhoServico;
    private final TrabalhoRepo trabalhos;

    public OrcamentoServico(OrcamentoRepo orcamentos, PacoteRepo pacotes, ClienteRepo clientes, PedidoRepo pedidos,
                            TrabalhoServico trabalhoServico, TrabalhoRepo trabalhos) {
        this.orcamentos = orcamentos;
        this.pacotes = pacotes;
        this.clientes = clientes;
        this.pedidos = pedidos;
        this.trabalhoServico = trabalhoServico;
        this.trabalhos = trabalhos;
    }

    public record Item(String descricao, int quantidade, BigDecimal valorUnit) {}

    public record Dados(Long clienteId, Long pedidoId, String nomeCliente, String telefone, String email, Long pacoteId,
                        String tipo, LocalDateTime dataPrevista, String localPrevisto, List<Item> itens,
                        BigDecimal desconto, FormaPagamento formaPagamento, BigDecimal valorSinal, Integer numParcelas,
                        LocalDate validade, String observacoes) {}

    public Orcamento buscar(Long estudioId, Long id) {
        return orcamentos.findByIdAndEstudioId(id, estudioId).orElseThrow(() -> new NaoEncontrado("Orçamento não encontrado"));
    }

    public Orcamento porToken(String token) {
        return orcamentos.findByToken(token).orElseThrow(() -> new NaoEncontrado("Orçamento não encontrado."));
    }

    @Transactional
    public Orcamento criar(Long estudioId, Dados d) {
        Orcamento o = new Orcamento();
        o.setEstudioId(estudioId);
        o.setNumero(orcamentos.ultimoNumero(estudioId) + 1);
        o.setToken(token());
        preencher(estudioId, o, d);
        return orcamentos.save(o);
    }

    @Transactional
    public Orcamento editar(Long estudioId, Long id, Dados d) {
        Orcamento o = buscar(estudioId, id);
        if (o.getStatus() == StatusOrcamento.ACEITO) throw new RegraNegocio("Orçamento aceito não pode ser alterado.");
        preencher(estudioId, o, d);
        return o;
    }

    private void preencher(Long estudioId, Orcamento o, Dados d) {
        Cliente cliente = d.clienteId() == null ? null : clientes.findByIdAndEstudioId(d.clienteId(), estudioId)
                .orElseThrow(() -> new NaoEncontrado("Cliente não encontrado"));
        Pacote pacote = d.pacoteId() == null ? null : pacotes.findByIdAndEstudioId(d.pacoteId(), estudioId)
                .orElseThrow(() -> new NaoEncontrado("Pacote não encontrado"));
        String nome = cliente != null ? cliente.getNome() : d.nomeCliente();
        if (nome == null || nome.isBlank()) throw new RegraNegocio("Informe o nome do cliente.");

        o.setClienteId(cliente != null ? cliente.getId() : null);
        o.setPedidoId(d.pedidoId());
        o.setNomeCliente(nome.trim());
        o.setTelefone(cliente != null && cliente.getTelefone() != null ? cliente.getTelefone() : soDigitos(d.telefone()));
        o.setEmail(cliente != null && cliente.getEmail() != null ? cliente.getEmail() : d.email());
        o.setPacote(pacote);
        o.setTipo(d.tipo());
        o.setDataPrevista(d.dataPrevista());
        o.setLocalPrevisto(d.localPrevisto());
        o.setObservacoes(d.observacoes());
        o.setValidade(d.validade() != null ? d.validade() : LocalDate.now().plusDays(7));
        o.setFormaPagamento(d.formaPagamento() != null ? d.formaPagamento() : FormaPagamento.AVISTA);
        o.setValorSinal(o.getFormaPagamento() == FormaPagamento.PARCELADO ? d.valorSinal() : null);
        o.setNumParcelas(o.getFormaPagamento() == FormaPagamento.PARCELADO ? d.numParcelas() : null);

        o.getItens().clear();
        int ordem = 0;
        if (pacote != null) {
            o.getItens().add(item(o, "Pacote " + pacote.getNome()
                    + (pacote.getMaterialEntregue() != null ? " — " + pacote.getMaterialEntregue() : ""), 1, pacote.getPreco(), ordem++));
        }
        if (d.itens() != null) {
            for (Item i : d.itens()) {
                if (i.descricao() == null || i.descricao().isBlank() || i.quantidade() < 1 || i.valorUnit() == null) continue;
                o.getItens().add(item(o, i.descricao().trim(), i.quantidade(), i.valorUnit(), ordem++));
            }
        }
        if (o.getItens().isEmpty()) throw new RegraNegocio("Inclua um pacote ou pelo menos um item.");
        BigDecimal desconto = d.desconto() != null ? d.desconto().max(BigDecimal.ZERO) : BigDecimal.ZERO;
        BigDecimal total = o.subtotal().subtract(desconto);
        if (total.signum() < 0) throw new RegraNegocio("O desconto é maior que o valor do orçamento.");
        if (o.getValorSinal() != null && o.getValorSinal().compareTo(total) > 0) {
            throw new RegraNegocio("O sinal não pode ser maior que o total.");
        }
        o.setDesconto(desconto);
        o.setValorTotal(total);
    }

    private OrcamentoItem item(Orcamento o, String descricao, int qtd, BigDecimal valor, int ordem) {
        OrcamentoItem i = new OrcamentoItem();
        i.setOrcamento(o);
        i.setDescricao(descricao.length() > 160 ? descricao.substring(0, 160) : descricao);
        i.setQuantidade(qtd);
        i.setValorUnit(valor);
        i.setOrdem(ordem);
        return i;
    }

    /** Marca como enviado (o envio em si é pelo WhatsApp, com o link pronto). */
    @Transactional
    public Orcamento marcarEnviado(Long estudioId, Long id) {
        Orcamento o = buscar(estudioId, id);
        if (o.getStatus() == StatusOrcamento.RASCUNHO || o.getStatus() == StatusOrcamento.EXPIRADO) {
            o.setStatus(StatusOrcamento.ENVIADO);
            o.setEnviadoEm(LocalDateTime.now());
            if (o.getValidade().isBefore(LocalDate.now())) o.setValidade(LocalDate.now().plusDays(7));
        }
        if (o.getPedidoId() != null) {
            pedidos.findByIdAndEstudioId(o.getPedidoId(), estudioId)
                    .filter(p -> p.getStatus() == StatusPedido.NOVO)
                    .ifPresent(p -> p.setStatus(StatusPedido.CONTATADO));
        }
        return o;
    }

    /** O cliente aceita pelo link: vira cliente (se ainda não for) e o trabalho nasce com as parcelas. */
    @Transactional
    public Orcamento aceitar(String token) {
        Orcamento o = porToken(token);
        if (o.getStatus() == StatusOrcamento.ACEITO) return o;
        StatusOrcamento atual = o.statusAtual();
        if (atual == StatusOrcamento.EXPIRADO) throw new RegraNegocio("Este orçamento venceu. Fale com o fotógrafo para atualizar.");
        if (atual == StatusOrcamento.RECUSADO) throw new RegraNegocio("Este orçamento foi recusado.");

        Cliente cliente = o.getClienteId() != null ? clientes.findById(o.getClienteId()).orElse(null) : null;
        if (cliente == null) {
            cliente = new Cliente();
            cliente.setEstudioId(o.getEstudioId());
            cliente.setNome(o.getNomeCliente());
            cliente.setTelefone(o.getTelefone());
            cliente.setEmail(o.getEmail());
            cliente.setOrigem(origemDoPedido(o));
            clientes.save(cliente);
            o.setClienteId(cliente.getId());
        }
        Trabalho t = trabalhoServico.criar(o.getEstudioId(), new TrabalhoServico.NovoTrabalho(
                cliente.getId(), o.getPacote() != null ? o.getPacote().getId() : null, null, o.getTipo(),
                o.getDataPrevista(), o.getLocalPrevisto(), o.getValorTotal(), o.getFormaPagamento(),
                o.getValorSinal(), o.getNumParcelas(), null,
                "Criado a partir do orçamento nº " + o.getNumero() + (o.getObservacoes() != null ? "\n" + o.getObservacoes() : "")));
        t.setOrcamentoId(o.getId());
        trabalhos.save(t);

        o.setStatus(StatusOrcamento.ACEITO);
        o.setRespondidoEm(LocalDateTime.now());
        o.setTrabalhoId(t.getId());
        if (o.getPedidoId() != null) {
            pedidos.findById(o.getPedidoId()).ifPresent(p -> { p.setStatus(StatusPedido.FECHADO); p.setClienteId(o.getClienteId()); });
        }
        return o;
    }

    @Transactional
    public Orcamento recusar(String token) {
        Orcamento o = porToken(token);
        if (o.getStatus() == StatusOrcamento.ACEITO) throw new RegraNegocio("Este orçamento já foi aceito.");
        o.setStatus(StatusOrcamento.RECUSADO);
        o.setRespondidoEm(LocalDateTime.now());
        return o;
    }

    private Origem origemDoPedido(Orcamento o) {
        if (o.getPedidoId() == null) return Origem.WHATSAPP;
        return pedidos.findById(o.getPedidoId()).map(PedidoOrcamento::getOrigem).orElse(Origem.SITE);
    }

    private static String soDigitos(String s) {
        if (s == null) return null;
        String d = s.replaceAll("\\D", "");
        return d.isEmpty() ? null : d;
    }

    private static String token() {
        byte[] b = new byte[16];
        ALEATORIO.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }
}
