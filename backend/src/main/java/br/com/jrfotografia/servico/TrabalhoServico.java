package br.com.jrfotografia.servico;

import br.com.jrfotografia.config.AppProps;
import br.com.jrfotografia.config.Erros.NaoEncontrado;
import br.com.jrfotografia.config.Erros.RegraNegocio;
import br.com.jrfotografia.dominio.*;
import br.com.jrfotografia.dominio.Enums.FormaPagamento;
import br.com.jrfotografia.dominio.Enums.MeioPagamento;
import br.com.jrfotografia.dominio.Enums.StatusTrabalho;
import br.com.jrfotografia.dominio.Enums.TipoParcela;
import br.com.jrfotografia.dominio.Repositorios.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class TrabalhoServico {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final TrabalhoRepo trabalhos;
    private final ClienteRepo clientes;
    private final PacoteRepo pacotes;
    private final ParcelaRepo parcelas;
    private final FotoRepo fotos;
    private final Imagens imagens;
    private final Armazenamento armazenamento;
    private final PasswordEncoder senhas;
    private final AppProps props;

    public TrabalhoServico(TrabalhoRepo trabalhos, ClienteRepo clientes, PacoteRepo pacotes, ParcelaRepo parcelas,
                           FotoRepo fotos, Imagens imagens, Armazenamento armazenamento,
                           PasswordEncoder senhas, AppProps props) {
        this.trabalhos = trabalhos;
        this.clientes = clientes;
        this.pacotes = pacotes;
        this.parcelas = parcelas;
        this.fotos = fotos;
        this.imagens = imagens;
        this.armazenamento = armazenamento;
        this.senhas = senhas;
        this.props = props;
    }

    /** Dados para criar um trabalho e o plano de pagamento junto. */
    public record NovoTrabalho(
            Long clienteId, Long pacoteId, String titulo, String tipo, LocalDateTime dataTrabalho,
            String localTrabalho, BigDecimal valorTotal, FormaPagamento formaPagamento,
            BigDecimal valorSinal, Integer numParcelas, LocalDate primeiroVencimento, String observacoes) {}

    public Trabalho buscar(Long estudioId, Long id) {
        return trabalhos.findByIdAndEstudioId(id, estudioId)
                .orElseThrow(() -> new NaoEncontrado("Trabalho não encontrado"));
    }

    @Transactional
    public Trabalho criar(Long estudioId, NovoTrabalho d) {
        Cliente cliente = clientes.findByIdAndEstudioId(d.clienteId(), estudioId)
                .orElseThrow(() -> new NaoEncontrado("Cliente não encontrado"));
        Pacote pacote = d.pacoteId() == null ? null : pacotes.findByIdAndEstudioId(d.pacoteId(), estudioId)
                .orElseThrow(() -> new NaoEncontrado("Pacote não encontrado"));

        BigDecimal valor = d.valorTotal() != null ? d.valorTotal() : pacote != null ? pacote.getPreco() : null;
        if (valor == null || valor.signum() < 0) throw new RegraNegocio("Informe o valor do trabalho.");

        Trabalho t = new Trabalho();
        t.setEstudioId(estudioId);
        t.setCliente(cliente);
        t.setPacote(pacote);
        t.setTitulo(d.titulo() != null && !d.titulo().isBlank() ? d.titulo()
                : (pacote != null ? pacote.getNome() : "Ensaio") + " · " + cliente.getNome());
        t.setTipo(d.tipo());
        t.setDataTrabalho(d.dataTrabalho());
        t.setLocalTrabalho(d.localTrabalho());
        t.setValorTotal(valor);
        t.setFormaPagamento(d.formaPagamento() != null ? d.formaPagamento() : FormaPagamento.AVISTA);
        t.setStatus(StatusTrabalho.AGENDADO);
        t.setGaleriaToken(novoTokenGaleria());
        t.setTokenCliente(novoTokenGaleria());
        t.setObservacoes(d.observacoes());
        if (pacote != null && d.dataTrabalho() != null) {
            t.setPrazoEntrega(Prazos.somarDiasUteis(d.dataTrabalho().toLocalDate(), pacote.getPrazoEntregaDias()));
        }
        trabalhos.save(t);
        parcelas.saveAll(planoDePagamento(t, d));
        return t;
    }

    /** À vista: uma parcela. Parcelado: sinal opcional + N parcelas mensais iguais (centavos na última). */
    List<Parcela> planoDePagamento(Trabalho t, NovoTrabalho d) {
        List<Parcela> plano = new ArrayList<>();
        BigDecimal total = t.getValorTotal();
        if (total.signum() == 0) return plano;
        // Sem data informada: à vista e parcela sem sinal vencem hoje; com sinal, a 1ª parcela fica para o mês seguinte.
        boolean comSinal = d.valorSinal() != null && d.valorSinal().signum() > 0;
        LocalDate primeiro = d.primeiroVencimento() != null ? d.primeiroVencimento()
                : t.getFormaPagamento() == FormaPagamento.PARCELADO && comSinal ? LocalDate.now().plusMonths(1) : LocalDate.now();

        if (t.getFormaPagamento() == FormaPagamento.AVISTA) {
            plano.add(parcela(t, TipoParcela.AVISTA, "Pagamento à vista", total, primeiro));
            return plano;
        }
        BigDecimal sinal = d.valorSinal() != null ? d.valorSinal() : BigDecimal.ZERO;
        if (sinal.compareTo(total) > 0) throw new RegraNegocio("O sinal não pode ser maior que o valor total.");
        int n = d.numParcelas() != null && d.numParcelas() > 0 ? d.numParcelas() : 1;
        if (sinal.signum() > 0) plano.add(parcela(t, TipoParcela.SINAL, "Sinal", sinal, LocalDate.now()));

        BigDecimal resto = total.subtract(sinal);
        if (resto.signum() > 0) {
            BigDecimal cada = resto.divide(BigDecimal.valueOf(n), 2, RoundingMode.DOWN);
            BigDecimal ultima = resto.subtract(cada.multiply(BigDecimal.valueOf(n - 1)));
            for (int i = 0; i < n; i++) {
                plano.add(parcela(t, TipoParcela.PARCELA, "Parcela " + (i + 1) + "/" + n,
                        i == n - 1 ? ultima : cada, primeiro.plusMonths(i)));
            }
        }
        return plano;
    }

    private Parcela parcela(Trabalho t, TipoParcela tipo, String descricao, BigDecimal valor, LocalDate vencimento) {
        Parcela p = new Parcela();
        p.setEstudioId(t.getEstudioId());
        p.setTrabalhoId(t.getId());
        p.setTipo(tipo);
        p.setDescricao(descricao);
        p.setValor(valor);
        p.setVencimento(vencimento);
        return p;
    }

    @Transactional
    public Trabalho mudarStatus(Long estudioId, Long id, StatusTrabalho novo) {
        Trabalho t = buscar(estudioId, id);
        if (novo == StatusTrabalho.SELECAO && !t.exigeSelecao()) {
            throw new RegraNegocio("Este pacote entrega todas as fotos, sem seleção.");
        }
        if (novo == StatusTrabalho.SELECAO && fotos.countByTrabalhoId(t.getId()) == 0) {
            throw new RegraNegocio("Envie as fotos antes de abrir a seleção.");
        }
        if (novo == StatusTrabalho.ENTREGUE) {
            if (fotos.countByTrabalhoId(t.getId()) == 0) throw new RegraNegocio("Envie as fotos antes de entregar.");
            if (t.exigeSelecao() && t.getSelecaoFechadaEm() == null) {
                throw new RegraNegocio("O cliente ainda não fechou a seleção.");
            }
            // O cliente só recebe fotos editadas: nenhuma original sai do estúdio.
            long semEditada = fotos.findByTrabalhoIdOrderByOrdemAscIdAsc(t.getId()).stream()
                    .filter(f -> !t.exigeSelecao() || f.isSelecionada())
                    .filter(f -> f.getArquivoFinal() == null).count();
            if (semEditada > 0) {
                throw new RegraNegocio("Falta enviar a versão editada de " + semEditada + (semEditada == 1 ? " foto" : " fotos") + " antes de entregar.");
            }
            t.setEntregueEm(LocalDateTime.now());
            t.setGaleriaExpiraEm(LocalDateTime.now().plusDays(props.galeria().validadeDias()));
        }
        if (novo == StatusTrabalho.CANCELADO && t.getCanceladoEm() == null) {
            t.setCanceladoEm(LocalDateTime.now());
            t.setCanceladoPor("ESTUDIO");
        }
        t.setStatus(novo);
        return t;
    }

    /** Gera uma nova senha de 6 dígitos para a galeria. Ela só é mostrada nesta resposta. */
    @Transactional
    public String gerarSenhaGaleria(Long estudioId, Long id) {
        Trabalho t = buscar(estudioId, id);
        String senha = String.format("%06d", ALEATORIO.nextInt(1_000_000));
        t.setGaleriaSenhaHash(senhas.encode(senha));
        return senha;
    }

    /** Trabalhos antigos não tinham o link da área da cliente: cria na primeira vez que for pedido. */
    @Transactional
    public String garantirTokenCliente(Trabalho t) {
        if (t.getTokenCliente() == null) {
            Trabalho gravavel = trabalhos.findById(t.getId()).orElseThrow();
            gravavel.setTokenCliente(novoTokenGaleria());
            t.setTokenCliente(gravavel.getTokenCliente());
        }
        return t.getTokenCliente();
    }

    @Transactional
    public Parcela registrarPagamento(Long estudioId, Long parcelaId, LocalDate data, MeioPagamento meio) {
        Parcela p = parcelas.findByIdAndEstudioId(parcelaId, estudioId)
                .orElseThrow(() -> new NaoEncontrado("Parcela não encontrada"));
        p.setPagoEm(data != null ? data : LocalDate.now());
        p.setForma(meio != null ? meio : MeioPagamento.PIX);
        return p;
    }

    @Transactional
    public Parcela desfazerPagamento(Long estudioId, Long parcelaId) {
        Parcela p = parcelas.findByIdAndEstudioId(parcelaId, estudioId)
                .orElseThrow(() -> new NaoEncontrado("Parcela não encontrada"));
        p.setPagoEm(null);
        p.setForma(null);
        return p;
    }

    /** Recebe as fotos exportadas (JPEG), gera prévia e miniatura com marca d'água. */
    @Transactional
    public List<Foto> enviarFotos(Long estudioId, Long trabalhoId, List<MultipartFile> arquivos) {
        Trabalho t = buscar(estudioId, trabalhoId);
        int ordem = (int) fotos.countByTrabalhoId(t.getId());
        List<Foto> novas = new ArrayList<>();
        for (MultipartFile arq : arquivos) {
            byte[] bytes = ler(arq);
            Imagens.Gerado g = imagens.processarComMarca(bytes);
            String base = estudioId + "/trabalhos/" + t.getId() + "/" + UUID.randomUUID();
            Foto f = new Foto();
            f.setEstudioId(estudioId);
            f.setTrabalhoId(t.getId());
            f.setNomeOriginal(nomeSeguro(arq.getOriginalFilename()));
            f.setArquivo(base + extensao(arq.getOriginalFilename()));
            f.setPrevia(base + "_previa.jpg");
            f.setMiniatura(base + "_mini.jpg");
            f.setLargura(g.largura());
            f.setAltura(g.altura());
            f.setOrdem(ordem++);
            armazenamento.gravar(f.getArquivo(), bytes);
            armazenamento.gravar(f.getPrevia(), g.previa());
            armazenamento.gravar(f.getMiniatura(), g.miniatura());
            // Pacote sem seleção (Eventos): o que sobe já é a foto editada, pronta para entregar.
            if (!t.exigeSelecao()) {
                f.setArquivoFinal(f.getArquivo());
                gravarSemMarca(f, base, bytes);
            }
            novas.add(fotos.save(f));
        }
        if (t.getStatus() == StatusTrabalho.AGENDADO) t.setStatus(StatusTrabalho.FOTOGRAFADO);
        return novas;
    }

    /**
     * Envia as versões editadas. Cada arquivo é associado à foto de mesmo nome;
     * nomes sem correspondência voltam na lista de não encontrados.
     */
    @Transactional
    public List<String> enviarEditadas(Long estudioId, Long trabalhoId, List<MultipartFile> arquivos) {
        Trabalho t = buscar(estudioId, trabalhoId);
        List<Foto> existentes = fotos.findByTrabalhoIdOrderByOrdemAscIdAsc(t.getId());
        List<String> semPar = new ArrayList<>();
        for (MultipartFile arq : arquivos) {
            String nome = nomeSeguro(arq.getOriginalFilename());
            Foto f = existentes.stream().filter(x -> mesmoNome(x.getNomeOriginal(), nome)).findFirst().orElse(null);
            if (f == null) {
                semPar.add(nome);
                continue;
            }
            byte[] bytes = ler(arq);
            // A galeria passa a mostrar a versão editada (prévia e miniatura refeitas, ainda com a marca d'água).
            Imagens.Gerado g = imagens.processarComMarca(bytes);
            if (f.getArquivoFinal() != null && !f.getArquivoFinal().equals(f.getArquivo())) armazenamento.apagar(f.getArquivoFinal());
            String base = estudioId + "/trabalhos/" + t.getId() + "/" + UUID.randomUUID();
            String chave = base + "_final" + extensao(nome);
            armazenamento.gravar(chave, bytes);
            armazenamento.apagar(f.getPrevia());
            armazenamento.apagar(f.getMiniatura());
            f.setPrevia(base + "_previa.jpg");
            f.setMiniatura(base + "_mini.jpg");
            armazenamento.gravar(f.getPrevia(), g.previa());
            armazenamento.gravar(f.getMiniatura(), g.miniatura());
            f.setLargura(g.largura());
            f.setAltura(g.altura());
            f.setArquivoFinal(chave);
            gravarSemMarca(f, base, bytes);
        }
        return semPar;
    }

    @Transactional
    public void apagarFoto(Long estudioId, Long trabalhoId, Long fotoId) {
        Trabalho t = buscar(estudioId, trabalhoId);
        Foto f = fotos.findByIdAndTrabalhoId(fotoId, t.getId())
                .orElseThrow(() -> new NaoEncontrado("Foto não encontrada"));
        armazenamento.apagar(f.getArquivo());
        armazenamento.apagar(f.getArquivoFinal());
        armazenamento.apagar(f.getPrevia());
        armazenamento.apagar(f.getMiniatura());
        armazenamento.apagar(f.getPreviaLimpa());
        armazenamento.apagar(f.getMiniaturaLimpa());
        fotos.delete(f);
    }

    static boolean mesmoNome(String a, String b) {
        return semExtensao(a).equalsIgnoreCase(semExtensao(b));
    }

    private static String semExtensao(String nome) {
        int i = nome.lastIndexOf('.');
        return i > 0 ? nome.substring(0, i) : nome;
    }

    private static String extensao(String nome) {
        if (nome == null) return ".jpg";
        String n = nome.toLowerCase();
        if (n.endsWith(".png")) return ".png";
        if (n.endsWith(".jpeg")) return ".jpeg";
        return ".jpg";
    }

    private static String nomeSeguro(String nome) {
        if (nome == null || nome.isBlank()) return "foto.jpg";
        String n = nome.replace('\\', '/');
        return n.substring(n.lastIndexOf('/') + 1);
    }

    private static byte[] ler(MultipartFile arq) {
        try {
            return arq.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String novoTokenGaleria() {
        byte[] b = new byte[16];
        ALEATORIO.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    /** Prévia e miniatura sem marca d'água da foto editada, para quando o download for liberado. */
    void gravarSemMarca(Foto f, String base, byte[] editada) {
        Imagens.Gerado limpo = imagens.processarSemMarca(editada);
        armazenamento.apagar(f.getPreviaLimpa());
        armazenamento.apagar(f.getMiniaturaLimpa());
        f.setPreviaLimpa(base + "_previa_limpa.jpg");
        f.setMiniaturaLimpa(base + "_mini_limpa.jpg");
        armazenamento.gravar(f.getPreviaLimpa(), limpo.previa());
        armazenamento.gravar(f.getMiniaturaLimpa(), limpo.miniatura());
    }
}
