package br.com.jrfotografia.servico;

import static br.com.jrfotografia.servico.Textos.vazio;

import br.com.jrfotografia.config.Erros.NaoEncontrado;
import br.com.jrfotografia.config.Erros.RegraNegocio;
import br.com.jrfotografia.dominio.Enums.TipoParcela;
import br.com.jrfotografia.dominio.Fase2.Despesa;
import br.com.jrfotografia.dominio.Parcela;
import br.com.jrfotografia.dominio.Produtos.*;
import br.com.jrfotografia.dominio.Repositorios.*;
import br.com.jrfotografia.dominio.Trabalho;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Pedidos de produtos (álbum, quadro, impressões) com o laboratório; diagramação do álbum aprovada pelo cliente
 * por link; estoque de materiais com entradas e saídas.
 */
@Service
public class ProdutosServico {

    private final PedidoProdutoRepo pedidos;
    private final AlbumLaminaRepo laminas;
    private final MaterialRepo materiais;
    private final MovimentoMaterialRepo movimentos;
    private final TrabalhoRepo trabalhos;
    private final ParcelaRepo parcelas;
    private final DespesaRepo despesas;
    private final Imagens imagens;
    private final Armazenamento armazenamento;

    public ProdutosServico(PedidoProdutoRepo pedidos, AlbumLaminaRepo laminas, MaterialRepo materiais, MovimentoMaterialRepo movimentos,
                           TrabalhoRepo trabalhos, ParcelaRepo parcelas, DespesaRepo despesas, Imagens imagens, Armazenamento armazenamento) {
        this.pedidos = pedidos;
        this.laminas = laminas;
        this.materiais = materiais;
        this.movimentos = movimentos;
        this.trabalhos = trabalhos;
        this.parcelas = parcelas;
        this.despesas = despesas;
        this.imagens = imagens;
        this.armazenamento = armazenamento;
    }

    // ================================================================ pedidos

    public record NovoPedido(Long trabalhoId, TipoProduto tipo, String descricao, int quantidade, BigDecimal valorVenda,
                             BigDecimal custo, String laboratorio, LocalDate prazo, String observacoes, boolean cobrar) {}

    @Transactional(readOnly = true)
    public List<PedidoProduto> pedidos(Long estudioId) {
        return pedidos.findByEstudioIdOrderByIdDesc(estudioId);
    }

    @Transactional(readOnly = true)
    public List<PedidoProduto> doTrabalho(Long estudioId, Long trabalhoId) {
        trabalho(estudioId, trabalhoId);
        return pedidos.findByTrabalhoIdOrderByIdAsc(trabalhoId);
    }

    public PedidoProduto pedido(Long estudioId, Long id) {
        return pedidos.findByIdAndEstudioId(id, estudioId).orElseThrow(() -> new NaoEncontrado("Pedido não encontrado."));
    }

    /** Cria o pedido. Com {@code cobrar}, o valor de venda vira uma parcela (tipo PRODUTO) do trabalho. */
    @Transactional
    public PedidoProduto criar(Long estudioId, NovoPedido f) {
        Trabalho t = trabalho(estudioId, f.trabalhoId());
        validar(f.quantidade(), f.valorVenda(), f.custo());
        PedidoProduto p = new PedidoProduto();
        p.setEstudioId(estudioId);
        p.setTrabalhoId(t.getId());
        aplicar(p, f);
        p = pedidos.save(p);
        if (f.cobrar() && p.getValorVenda().signum() > 0) {
            Parcela c = new Parcela();
            c.setEstudioId(estudioId);
            c.setTrabalhoId(t.getId());
            c.setTipo(TipoParcela.PRODUTO);
            c.setDescricao(rotulo(p));
            c.setValor(p.getValorVenda());
            c.setVencimento(f.prazo() != null ? f.prazo() : LocalDate.now());
            p.setParcelaId(parcelas.save(c).getId());
        }
        return p;
    }

    @Transactional
    public PedidoProduto editar(Long estudioId, Long id, NovoPedido f) {
        PedidoProduto p = pedido(estudioId, id);
        validar(f.quantidade(), f.valorVenda(), f.custo());
        aplicar(p, f);
        // Cobrança ainda não paga acompanha o novo valor; custo ainda não pago também.
        if (p.getParcelaId() != null) {
            parcelas.findById(p.getParcelaId()).filter(c -> !c.paga()).ifPresent(c -> {
                c.setValor(p.getValorVenda());
                c.setDescricao(rotulo(p));
            });
        }
        if (p.getDespesaId() != null) {
            despesas.findById(p.getDespesaId()).filter(d -> d.getPagoEm() == null).ifPresent(d -> {
                if (p.getCusto() != null && p.getCusto().signum() > 0) d.setValor(p.getCusto());
            });
        }
        return p;
    }

    private void aplicar(PedidoProduto p, NovoPedido f) {
        p.setTipo(f.tipo());
        p.setDescricao(f.descricao().trim());
        p.setQuantidade(f.quantidade());
        p.setValorVenda(f.valorVenda() != null ? f.valorVenda() : BigDecimal.ZERO);
        p.setCusto(f.custo());
        p.setLaboratorio(vazio(f.laboratorio()) ? null : f.laboratorio().trim());
        p.setPrazo(f.prazo());
        p.setObservacoes(vazio(f.observacoes()) ? null : f.observacoes().trim());
    }

    private static void validar(int qtd, BigDecimal venda, BigDecimal custo) {
        if (qtd < 1 || qtd > 1000) throw new RegraNegocio("Quantidade inválida.");
        if (venda != null && venda.signum() < 0) throw new RegraNegocio("Valor de venda inválido.");
        if (custo != null && custo.signum() < 0) throw new RegraNegocio("Custo inválido.");
    }

    /**
     * Avança o pedido. Ao mandar para o laboratório, o custo vira uma despesa a pagar (categoria Laboratório).
     */
    @Transactional
    public PedidoProduto mudarStatus(Long estudioId, Long id, StatusPedidoProduto novo, LocalDate previsaoLab) {
        PedidoProduto p = pedido(estudioId, id);
        if (p.getStatus() == StatusPedidoProduto.CANCELADO) throw new RegraNegocio("Pedido cancelado.");
        if (novo == StatusPedidoProduto.APROVACAO) {
            if (p.getTipo() != TipoProduto.ALBUM) throw new RegraNegocio("Aprovação de diagramação é só para álbum.");
            if (laminasAtuais(p).isEmpty()) throw new RegraNegocio("Envie as lâminas do álbum antes de pedir a aprovação.");
            return pedirAprovacao(p);
        }
        if (novo == StatusPedidoProduto.LABORATORIO) {
            if (p.getTipo() == TipoProduto.ALBUM && p.getAprovacao() != Aprovacao.APROVADO) {
                throw new RegraNegocio("O cliente ainda não aprovou a diagramação do álbum.");
            }
            p.setEnviadoLabEm(LocalDateTime.now());
            p.setPrevisaoLab(previsaoLab);
            if (p.getCusto() != null && p.getCusto().signum() > 0 && p.getDespesaId() == null) {
                Trabalho t = trabalhos.findById(p.getTrabalhoId()).orElseThrow();
                Despesa d = new Despesa();
                d.setEstudioId(estudioId);
                d.setDescricao(rotulo(p) + (p.getLaboratorio() != null ? " · " + p.getLaboratorio() : "") + " · " + t.getCliente().getNome());
                d.setCategoria("Laboratório");
                d.setValor(p.getCusto());
                d.setVencimento(previsaoLab != null ? previsaoLab : LocalDate.now());
                d.setTrabalhoId(t.getId());
                p.setDespesaId(despesas.save(d).getId());
            }
        }
        if (novo == StatusPedidoProduto.PRONTO) p.setRecebidoEm(LocalDateTime.now());
        if (novo == StatusPedidoProduto.ENTREGUE) p.setEntregueEm(LocalDateTime.now());
        if (novo == StatusPedidoProduto.CANCELADO) {
            // Cobrança e custo ainda não pagos somem junto.
            if (p.getParcelaId() != null) parcelas.findById(p.getParcelaId()).filter(c -> !c.paga()).ifPresent(c -> {
                parcelas.delete(c);
                p.setParcelaId(null);
            });
            if (p.getDespesaId() != null) despesas.findById(p.getDespesaId()).filter(d -> d.getPagoEm() == null).ifPresent(d -> {
                despesas.delete(d);
                p.setDespesaId(null);
            });
        }
        p.setStatus(novo);
        return p;
    }

    public static String rotulo(PedidoProduto p) {
        String tipo = switch (p.getTipo()) { case ALBUM -> "Álbum"; case QUADRO -> "Quadro"; case IMPRESSAO -> "Impressões"; case OUTRO -> "Produto"; };
        String d = p.getDescricao();
        return d.toLowerCase().startsWith(tipo.toLowerCase()) ? d : tipo + " " + d;
    }

    // ================================================================ diagramação do álbum

    public List<AlbumLamina> laminasAtuais(PedidoProduto p) {
        return laminas.findByPedidoIdAndVersaoOrderByOrdemAscIdAsc(p.getId(), Math.max(1, p.getVersaoLayout()));
    }

    public List<AlbumLamina> todasLaminas(PedidoProduto p) {
        return laminas.findByPedidoIdOrderByVersaoAscOrdemAsc(p.getId());
    }

    /**
     * Envia lâminas. Se a versão atual já foi enviada ao cliente (ou ele pediu ajustes), começa uma versão nova;
     * as lâminas novas entram na ordem do nome do arquivo.
     */
    @Transactional
    public PedidoProduto enviarLaminas(Long estudioId, Long id, List<MultipartFile> arquivos) {
        PedidoProduto p = pedido(estudioId, id);
        if (p.getTipo() != TipoProduto.ALBUM) throw new RegraNegocio("Lâminas são só para álbum.");
        if (p.getStatus() == StatusPedidoProduto.CANCELADO || p.getAprovacao() == Aprovacao.APROVADO) {
            throw new RegraNegocio("A diagramação já foi aprovada.");
        }
        if (arquivos == null || arquivos.isEmpty()) throw new RegraNegocio("Escolha as lâminas (JPEG).");
        boolean novaVersao = p.getVersaoLayout() == 0 || p.getAprovacao() != null;
        if (novaVersao) {
            p.setVersaoLayout(p.getVersaoLayout() + 1);
            p.setAprovacao(null);
            p.setAprovacaoEm(null);
            p.setComentarioCliente(null);
        }
        int ordem = laminasAtuais(p).size();
        List<MultipartFile> ordenados = arquivos.stream()
                .sorted(java.util.Comparator.comparing(a -> a.getOriginalFilename() == null ? "" : a.getOriginalFilename())).toList();
        for (MultipartFile a : ordenados) {
            byte[] bytes;
            try { bytes = a.getBytes(); } catch (IOException ex) { throw new RegraNegocio("Não foi possível ler " + a.getOriginalFilename()); }
            var grande = imagens.processarParaWeb(bytes, 2400);
            var mini = imagens.processarParaWeb(bytes, 600);
            String base = estudioId + "/albuns/" + p.getId() + "/v" + p.getVersaoLayout() + "_" + (ordem + 1) + "_" + System.nanoTime();
            armazenamento.gravar(base + ".jpg", grande.imagem());
            armazenamento.gravar(base + "_m.jpg", mini.imagem());
            AlbumLamina l = new AlbumLamina();
            l.setPedidoId(p.getId());
            l.setVersao(p.getVersaoLayout());
            l.setOrdem(++ordem);
            l.setNome(a.getOriginalFilename());
            l.setArquivo(base + ".jpg");
            l.setMiniatura(base + "_m.jpg");
            l.setLargura(grande.largura());
            l.setAltura(grande.altura());
            laminas.save(l);
        }
        if (p.getStatus() == StatusPedidoProduto.NOVO) p.setStatus(StatusPedidoProduto.DIAGRAMACAO);
        return p;
    }

    @Transactional
    public void apagarLamina(Long estudioId, Long pedidoId, Long laminaId) {
        PedidoProduto p = pedido(estudioId, pedidoId);
        AlbumLamina l = laminas.findByIdAndPedidoId(laminaId, p.getId()).orElseThrow(() -> new NaoEncontrado("Lâmina não encontrada."));
        if (l.getVersao() != p.getVersaoLayout() || p.getAprovacao() != null) throw new RegraNegocio("Esta versão já foi enviada ao cliente.");
        armazenamento.apagar(l.getArquivo());
        armazenamento.apagar(l.getMiniatura());
        laminas.delete(l);
        int i = 1;
        for (AlbumLamina x : laminasAtuais(p)) x.setOrdem(i++);
    }

    private PedidoProduto pedirAprovacao(PedidoProduto p) {
        if (p.getTokenAprovacao() == null) p.setTokenAprovacao(TrabalhoServico.novoTokenGaleria());
        p.setAprovacao(Aprovacao.PENDENTE);
        p.setAprovacaoEm(null);
        p.setStatus(StatusPedidoProduto.APROVACAO);
        return p;
    }

    @Transactional(readOnly = true)
    public PedidoProduto porToken(String token) {
        PedidoProduto p = pedidos.findByTokenAprovacao(token).orElseThrow(() -> new NaoEncontrado("Álbum não encontrado."));
        if (p.getStatus() == StatusPedidoProduto.CANCELADO) throw new NaoEncontrado("Este pedido foi cancelado.");
        return p;
    }

    /** O cliente aprova a diagramação, ou pede ajustes (com comentários por lâmina e/ou geral). */
    @Transactional
    public PedidoProduto responderAprovacao(String token, boolean aprova, String comentarioGeral, Map<Long, String> comentarios) {
        PedidoProduto p = porToken(token);
        if (p.getAprovacao() != Aprovacao.PENDENTE) throw new RegraNegocio("Esta versão do álbum já foi respondida.");
        List<AlbumLamina> atuais = laminasAtuais(p);
        int comComentario = 0;
        if (comentarios != null) {
            for (AlbumLamina l : atuais) {
                String c = comentarios.get(l.getId());
                if (c != null && !c.isBlank()) {
                    l.setComentario(c.trim().substring(0, Math.min(1000, c.trim().length())));
                    comComentario++;
                }
            }
        }
        String geral = vazio(comentarioGeral) ? null : comentarioGeral.trim();
        if (!aprova && geral == null && comComentario == 0) throw new RegraNegocio("Diga o que quer ajustar (em uma lâmina ou no comentário geral).");
        p.setComentarioCliente(geral == null ? null : geral.substring(0, Math.min(2000, geral.length())));
        p.setAprovacao(aprova ? Aprovacao.APROVADO : Aprovacao.AJUSTES);
        p.setAprovacaoEm(LocalDateTime.now());
        p.setStatus(aprova ? StatusPedidoProduto.APROVADO : StatusPedidoProduto.DIAGRAMACAO);
        return p;
    }

    public AlbumLamina lamina(PedidoProduto p, Long laminaId) {
        return laminas.findByIdAndPedidoId(laminaId, p.getId()).orElseThrow(() -> new NaoEncontrado("Lâmina não encontrada."));
    }

    // ================================================================ estoque

    @Transactional(readOnly = true)
    public List<Material> materiais(Long estudioId) {
        return materiais.findByEstudioIdOrderByAtivoDescNomeAsc(estudioId);
    }

    @Transactional
    public Material salvarMaterial(Long estudioId, Long id, String nome, String unidade, BigDecimal minimo, BigDecimal custo, boolean ativo,
                                   BigDecimal quantidadeInicial, String usuario) {
        Material m = id == null ? new Material() : material(estudioId, id);
        m.setEstudioId(estudioId);
        m.setNome(nome.trim());
        m.setUnidade(unidade.trim());
        m.setMinimo(minimo != null && minimo.signum() >= 0 ? minimo : BigDecimal.ZERO);
        m.setCustoUnitario(custo);
        m.setAtivo(ativo);
        m = materiais.save(m);
        if (id == null && quantidadeInicial != null && quantidadeInicial.signum() > 0) {
            movimentar(estudioId, m.getId(), TipoMovimento.ENTRADA, quantidadeInicial, "Estoque inicial", null, usuario);
        }
        return m;
    }

    /** Entrada soma, saída subtrai (não deixa ficar negativo), ajuste define a contagem real. */
    @Transactional
    public MovimentoMaterial movimentar(Long estudioId, Long materialId, TipoMovimento tipo, BigDecimal qtd, String motivo, Long pedidoId,
                                        String usuario) {
        Material m = material(estudioId, materialId);
        if (qtd == null || qtd.signum() < 0 || (tipo != TipoMovimento.AJUSTE && qtd.signum() == 0)) throw new RegraNegocio("Quantidade inválida.");
        if (pedidoId != null) pedido(estudioId, pedidoId);
        BigDecimal novo = switch (tipo) {
            case ENTRADA -> m.getQuantidade().add(qtd);
            case SAIDA -> m.getQuantidade().subtract(qtd);
            case AJUSTE -> qtd;
        };
        if (novo.signum() < 0) {
            throw new RegraNegocio("Estoque insuficiente: há " + m.getQuantidade().stripTrailingZeros().toPlainString() + " " + m.getUnidade() + ".");
        }
        m.setQuantidade(novo);
        MovimentoMaterial mv = new MovimentoMaterial();
        mv.setEstudioId(estudioId);
        mv.setMaterialId(m.getId());
        mv.setTipo(tipo);
        mv.setQuantidade(qtd);
        mv.setSaldo(novo);
        mv.setMotivo(vazio(motivo) ? null : motivo.trim());
        mv.setPedidoId(pedidoId);
        mv.setUsuario(usuario);
        return movimentos.save(mv);
    }

    @Transactional(readOnly = true)
    public List<MovimentoMaterial> movimentos(Long estudioId, Long materialId) {
        if (materialId != null) {
            material(estudioId, materialId);
            return movimentos.findByMaterialIdOrderByIdDesc(materialId);
        }
        return movimentos.findTop50ByEstudioIdOrderByIdDesc(estudioId);
    }

    public Material material(Long estudioId, Long id) {
        return materiais.findByIdAndEstudioId(id, estudioId).orElseThrow(() -> new NaoEncontrado("Material não encontrado."));
    }

    // ================================================================ apoio

    private Trabalho trabalho(Long estudioId, Long id) {
        if (id == null) throw new RegraNegocio("Escolha o trabalho do pedido.");
        return trabalhos.findByIdAndEstudioId(id, estudioId).orElseThrow(() -> new NaoEncontrado("Trabalho não encontrado."));
    }
}
