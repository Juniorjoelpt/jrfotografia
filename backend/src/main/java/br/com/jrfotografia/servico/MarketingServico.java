package br.com.jrfotografia.servico;

import br.com.jrfotografia.config.Erros.NaoEncontrado;
import br.com.jrfotografia.config.Erros.RegraNegocio;
import br.com.jrfotografia.dominio.Agendamento.Reserva;
import br.com.jrfotografia.dominio.Cliente;
import br.com.jrfotografia.dominio.Estudio;
import br.com.jrfotografia.dominio.Marketing.*;
import br.com.jrfotografia.dominio.Pacote;
import br.com.jrfotografia.dominio.Repositorios.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Campanhas (mini-sessões), cupons de desconto e programa de indicação. */
@Service
public class MarketingServico {

    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final char[] ALFABETO = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();

    private final CampanhaRepo campanhas;
    private final CampanhaDiaRepo dias;
    private final CupomRepo cupons;
    private final IndicacaoRepo indicacoes;
    private final ClienteRepo clientes;
    private final PacoteRepo pacotes;
    private final EstudioRepo estudios;
    private final Imagens imagens;
    private final Armazenamento armazenamento;

    public MarketingServico(CampanhaRepo campanhas, CampanhaDiaRepo dias, CupomRepo cupons, IndicacaoRepo indicacoes, ClienteRepo clientes,
                            PacoteRepo pacotes, EstudioRepo estudios, Imagens imagens, Armazenamento armazenamento) {
        this.campanhas = campanhas;
        this.dias = dias;
        this.cupons = cupons;
        this.indicacoes = indicacoes;
        this.clientes = clientes;
        this.pacotes = pacotes;
        this.estudios = estudios;
        this.imagens = imagens;
        this.armazenamento = armazenamento;
    }

    // ================================================================ campanhas

    public record DiaForm(LocalDate data, LocalTime inicio, LocalTime fim, String local) {}

    public record CampanhaForm(String titulo, String slug, String descricao, BigDecimal preco, int sinalPercentual, int duracaoMin,
                               int intervaloMin, Integer qtdFotos, BigDecimal precoFotoExtra, int prazoEntregaDias, boolean ativa,
                               List<DiaForm> dias) {}

    @Transactional(readOnly = true)
    public List<Campanha> campanhas(Long estudioId) {
        return campanhas.findByEstudioIdOrderByIdDesc(estudioId);
    }

    public Campanha campanha(Long estudioId, Long id) {
        return campanhas.findByIdAndEstudioId(id, estudioId).orElseThrow(() -> new NaoEncontrado("Campanha não encontrada."));
    }

    @Transactional(readOnly = true)
    public Campanha porSlug(String slug) {
        Campanha c = campanhas.findBySlug(slug).orElseThrow(() -> new NaoEncontrado("Campanha não encontrada."));
        if (!c.isAtiva()) throw new NaoEncontrado("Esta campanha foi encerrada.");
        return c;
    }

    public List<CampanhaDia> dias(Campanha c) {
        return dias.findByCampanhaIdOrderByDataAscInicioAsc(c.getId());
    }

    /** Pacotes internos das campanhas: não aparecem no site nem na lista de pacotes. */
    @Transactional(readOnly = true)
    public Set<Long> pacotesDeCampanha(Long estudioId) {
        return campanhas.findByEstudioIdOrderByIdDesc(estudioId).stream().map(Campanha::getPacoteId).collect(Collectors.toSet());
    }

    /** Cria ou atualiza a campanha, o pacote interno dela e os dias com horário. */
    @Transactional
    public Campanha salvarCampanha(Long estudioId, Long id, CampanhaForm f) {
        if (f.titulo() == null || f.titulo().isBlank()) throw new RegraNegocio("Dê um título à campanha.");
        if (f.preco() == null || f.preco().signum() <= 0) throw new RegraNegocio("Informe o preço da mini-sessão.");
        if (f.duracaoMin() < 5 || f.duracaoMin() > 480) throw new RegraNegocio("Duração entre 5 e 480 minutos.");
        if (f.intervaloMin() < 0 || f.intervaloMin() > 120) throw new RegraNegocio("Intervalo entre 0 e 120 minutos.");
        if (f.sinalPercentual() < 0 || f.sinalPercentual() > 100) throw new RegraNegocio("Sinal entre 0% e 100%.");
        if (f.dias() == null || f.dias().isEmpty()) throw new RegraNegocio("Inclua pelo menos um dia com horário.");
        for (DiaForm d : f.dias()) {
            if (d.data() == null || d.inicio() == null || d.fim() == null || !d.fim().isAfter(d.inicio())) {
                throw new RegraNegocio("Confira os dias: o fim precisa ser depois do início.");
            }
        }
        Campanha c = id == null ? new Campanha() : campanha(estudioId, id);
        String slug = slug(f.slug() != null && !f.slug().isBlank() ? f.slug() : f.titulo());
        if (slug.length() < 3) throw new RegraNegocio("Endereço da página muito curto.");
        campanhas.findBySlug(slug).filter(o -> !o.getId().equals(c.getId())).ifPresent(o -> {
            throw new RegraNegocio("Já existe uma campanha com o endereço /c/" + slug + ".");
        });
        c.setEstudioId(estudioId);
        c.setSlug(slug);
        c.setTitulo(f.titulo().trim());
        c.setDescricao(f.descricao() == null || f.descricao().isBlank() ? null : f.descricao().trim());
        c.setPreco(f.preco());
        c.setSinalPercentual(f.sinalPercentual());
        c.setDuracaoMin(f.duracaoMin());
        c.setIntervaloMin(f.intervaloMin());
        c.setQtdFotos(f.qtdFotos() != null && f.qtdFotos() > 0 ? f.qtdFotos() : null);
        c.setPrecoFotoExtra(f.precoFotoExtra());
        c.setPrazoEntregaDias(Math.max(1, f.prazoEntregaDias()));
        c.setAtiva(f.ativa());

        Pacote p = c.getPacoteId() == null ? new Pacote() : pacotes.findById(c.getPacoteId()).orElseGet(Pacote::new);
        p.setEstudioId(estudioId);
        p.setNome("Mini-sessão · " + c.getTitulo());
        p.setQtdFotos(c.getQtdFotos());
        p.setPreco(c.getPreco());
        p.setPrecoFotoExtra(c.getPrecoFotoExtra());
        p.setPrazoEntregaDias(c.getPrazoEntregaDias());
        p.setDuracaoMin(c.getDuracaoMin());
        p.setPublico(false);
        p.setAgendaOnline(false);
        p.setAtivo(true);
        p.setOrdem(900);
        if (p.getItens() == null) p.setItens("Mini-sessão " + c.getTitulo());
        c.setPacoteId(pacotes.save(p).getId());
        Campanha salva = campanhas.save(c);

        dias.deleteByCampanhaId(salva.getId());
        dias.flush();
        for (DiaForm d : f.dias()) {
            CampanhaDia x = new CampanhaDia();
            x.setCampanhaId(salva.getId());
            x.setData(d.data());
            x.setInicio(d.inicio());
            x.setFim(d.fim());
            x.setLocal(d.local() == null || d.local().isBlank() ? null : d.local().trim());
            dias.save(x);
        }
        return salva;
    }

    @Transactional
    public Campanha salvarCapa(Long estudioId, Long id, MultipartFile arquivo) {
        Campanha c = campanha(estudioId, id);
        byte[] b;
        try { b = arquivo.getBytes(); } catch (IOException ex) { throw new RegraNegocio("Não foi possível ler a imagem."); }
        var web = imagens.processarParaWeb(b, 1800);
        armazenamento.apagar(c.getCapa());
        c.setCapa(estudioId + "/campanhas/" + c.getId() + "_" + System.currentTimeMillis() + ".jpg");
        armazenamento.gravar(c.getCapa(), web.imagem());
        return c;
    }

    public Pacote pacote(Campanha c) {
        return pacotes.findById(c.getPacoteId()).orElseThrow();
    }

    static String slug(String texto) {
        String s = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase()
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return s.length() > 60 ? s.substring(0, 60).replaceAll("-$", "") : s;
    }

    // ================================================================ cupons

    public record CupomForm(String codigo, String descricao, TipoCupom tipo, BigDecimal valor, LocalDate validade, Integer usosMax,
                            Long campanhaId, boolean ativo) {}

    @Transactional(readOnly = true)
    public List<Cupom> cupons(Long estudioId) {
        return cupons.findByEstudioIdOrderByIdDesc(estudioId);
    }

    @Transactional
    public Cupom salvarCupom(Long estudioId, Long id, CupomForm f) {
        String codigo = normalizarCodigo(f.codigo());
        if (codigo.length() < 3) throw new RegraNegocio("Código com pelo menos 3 letras ou números.");
        if (f.tipo() == null || f.valor() == null || f.valor().signum() <= 0) throw new RegraNegocio("Informe o desconto.");
        if (f.tipo() == TipoCupom.PERCENTUAL && f.valor().compareTo(BigDecimal.valueOf(100)) > 0) throw new RegraNegocio("Desconto até 100%.");
        if (f.campanhaId() != null) campanha(estudioId, f.campanhaId());
        Cupom c = id == null ? new Cupom() : cupons.findByIdAndEstudioId(id, estudioId).orElseThrow(() -> new NaoEncontrado("Cupom não encontrado."));
        cupons.findByEstudioIdAndCodigo(estudioId, codigo).filter(o -> !o.getId().equals(c.getId())).ifPresent(o -> {
            throw new RegraNegocio("Já existe um cupom " + codigo + ".");
        });
        if (clientes.findByEstudioIdAndCodigoIndicacao(estudioId, codigo).isPresent()) {
            throw new RegraNegocio("Este código já é o código de indicação de uma cliente.");
        }
        c.setEstudioId(estudioId);
        c.setCodigo(codigo);
        c.setDescricao(f.descricao() == null || f.descricao().isBlank() ? null : f.descricao().trim());
        c.setTipo(f.tipo());
        c.setValor(f.valor());
        c.setValidade(f.validade());
        c.setUsosMax(f.usosMax() != null && f.usosMax() > 0 ? f.usosMax() : null);
        c.setCampanhaId(f.campanhaId());
        c.setAtivo(f.ativo());
        return cupons.save(c);
    }

    static String normalizarCodigo(String c) {
        return c == null ? "" : Normalizer.normalize(c, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase().replaceAll("[^A-Z0-9]", "");
    }

    // ================================================================ desconto na reserva (cupom ou indicação)

    public enum Origem { CUPOM, INDICACAO }

    public record Desconto(boolean valido, String mensagem, Origem origem, String codigo, BigDecimal desconto, BigDecimal valorFinal,
                           Long cupomId, Long indicadorClienteId) {
        static Desconto invalido(String m) { return new Desconto(false, m, null, null, BigDecimal.ZERO, null, null, null); }
    }

    /** Confere um código digitado no agendamento: primeiro cupom, depois código de indicação de uma cliente. */
    @Transactional(readOnly = true)
    public Desconto avaliar(Estudio e, String codigoDigitado, BigDecimal preco, Long campanhaId, String telefoneCliente) {
        String codigo = normalizarCodigo(codigoDigitado);
        if (codigo.isEmpty()) return Desconto.invalido("Digite o código.");
        var cupom = cupons.findByEstudioIdAndCodigo(e.getId(), codigo);
        if (cupom.isPresent()) {
            Cupom c = cupom.get();
            if (!c.isAtivo()) return Desconto.invalido("Este cupom não está mais ativo.");
            if (c.getValidade() != null && c.getValidade().isBefore(LocalDate.now())) return Desconto.invalido("Este cupom venceu.");
            if (c.getUsosMax() != null && c.getUsos() >= c.getUsosMax()) return Desconto.invalido("Este cupom já foi usado o máximo de vezes.");
            if (c.getCampanhaId() != null && !c.getCampanhaId().equals(campanhaId)) return Desconto.invalido("Este cupom vale só para outra campanha.");
            BigDecimal d = c.getTipo() == TipoCupom.PERCENTUAL
                    ? preco.multiply(c.getValor()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                    : c.getValor().min(preco);
            String msg = c.getTipo() == TipoCupom.PERCENTUAL ? c.getValor().stripTrailingZeros().toPlainString() + "% de desconto" : "Desconto aplicado";
            return new Desconto(true, msg, Origem.CUPOM, codigo, d, preco.subtract(d), c.getId(), null);
        }
        if (e.isIndicacaoAtiva()) {
            var indicadora = clientes.findByEstudioIdAndCodigoIndicacao(e.getId(), codigo);
            if (indicadora.isPresent()) {
                Cliente c = indicadora.get();
                String fone = telefoneCliente == null ? null : telefoneCliente.replaceAll("\\D", "");
                if (fone != null && fone.equals(c.getTelefone())) return Desconto.invalido("O código de indicação é para passar para amigas.");
                BigDecimal d = preco.multiply(BigDecimal.valueOf(e.getIndicacaoDesconto())).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                String primeiro = c.getNome().trim().split("\\s+")[0];
                return new Desconto(true, "Indicação de " + primeiro + ": " + e.getIndicacaoDesconto() + "% de desconto",
                        Origem.INDICACAO, codigo, d, preco.subtract(d), null, c.getId());
            }
        }
        return Desconto.invalido("Código não encontrado.");
    }

    /** Na confirmação da reserva: conta o uso do cupom e registra a indicação (recompensa para quem indicou). */
    @Transactional
    public void aoConfirmar(Reserva r, Long trabalhoId) {
        if (r.getCupomCodigo() == null) return;
        Estudio e = estudios.findById(r.getEstudioId()).orElseThrow();
        if (r.getIndicadorClienteId() != null) {
            Indicacao i = new Indicacao();
            i.setEstudioId(r.getEstudioId());
            i.setIndicadorClienteId(r.getIndicadorClienteId());
            i.setReservaId(r.getId());
            i.setTrabalhoId(trabalhoId);
            i.setIndicadoNome(r.getNome());
            i.setRecompensa(e.getIndicacaoRecompensa());
            indicacoes.save(i);
        } else {
            cupons.findByEstudioIdAndCodigo(r.getEstudioId(), r.getCupomCodigo()).ifPresent(c -> c.setUsos(c.getUsos() + 1));
        }
    }

    // ================================================================ indicações

    /** Código de indicação da cliente (criado na primeira vez): primeiro nome + 3 caracteres. */
    @Transactional
    public String codigoDaCliente(Cliente recebida) {
        Cliente c = clientes.findById(recebida.getId()).orElseThrow();
        if (c.getCodigoIndicacao() != null) return c.getCodigoIndicacao();
        String base = normalizarCodigo(c.getNome().trim().split("\\s+")[0]);
        base = base.length() > 8 ? base.substring(0, 8) : base;
        for (int i = 0; i < 20; i++) {
            StringBuilder sb = new StringBuilder(base);
            for (int k = 0; k < 3; k++) sb.append(ALFABETO[ALEATORIO.nextInt(ALFABETO.length)]);
            String codigo = sb.toString();
            if (clientes.findByEstudioIdAndCodigoIndicacao(c.getEstudioId(), codigo).isEmpty()
                    && cupons.findByEstudioIdAndCodigo(c.getEstudioId(), codigo).isEmpty()) {
                c.setCodigoIndicacao(codigo);
                return codigo;
            }
        }
        throw new IllegalStateException("Não foi possível gerar código de indicação");
    }

    @Transactional(readOnly = true)
    public List<Indicacao> indicacoes(Long estudioId) {
        return indicacoes.findByEstudioIdOrderByIdDesc(estudioId);
    }

    @Transactional(readOnly = true)
    public List<Indicacao> indicacoesDaCliente(Long clienteId) {
        return indicacoes.findByIndicadorClienteIdOrderByIdDesc(clienteId);
    }

    @Transactional
    public Indicacao mudarIndicacao(Long estudioId, Long id, StatusIndicacao status) {
        Indicacao i = indicacoes.findByIdAndEstudioId(id, estudioId).orElseThrow(() -> new NaoEncontrado("Indicação não encontrada."));
        i.setStatus(status);
        i.setRecompensadaEm(status == StatusIndicacao.RECOMPENSADA ? LocalDateTime.now() : null);
        return i;
    }

    @Transactional
    public Estudio configurarIndicacao(Long estudioId, boolean ativa, int desconto, BigDecimal recompensa) {
        if (desconto < 0 || desconto > 100) throw new RegraNegocio("Desconto entre 0% e 100%.");
        if (recompensa == null || recompensa.signum() < 0) throw new RegraNegocio("Recompensa inválida.");
        Estudio e = estudios.findById(estudioId).orElseThrow();
        e.setIndicacaoAtiva(ativa);
        e.setIndicacaoDesconto(desconto);
        e.setIndicacaoRecompensa(recompensa);
        return e;
    }
}
