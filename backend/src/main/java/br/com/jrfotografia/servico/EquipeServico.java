package br.com.jrfotografia.servico;

import br.com.jrfotografia.config.Erros.NaoEncontrado;
import br.com.jrfotografia.config.Erros.RegraNegocio;
import br.com.jrfotografia.dominio.Cliente;
import br.com.jrfotografia.dominio.Enums.StatusTrabalho;
import br.com.jrfotografia.dominio.Equipe.*;
import br.com.jrfotografia.dominio.Fase2.Despesa;
import br.com.jrfotografia.dominio.Parcela;
import br.com.jrfotografia.dominio.Repositorios.*;
import br.com.jrfotografia.dominio.Trabalho;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.MonthDay;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Relacionamento com clientes (aniversários e datas especiais, inativas, pesquisa de satisfação)
 * e equipe (freelancers com comissão por trabalho e tarefas de edição).
 */
@Service
public class EquipeServico {

    private final ClienteRepo clientes;
    private final TrabalhoRepo trabalhos;
    private final ParcelaRepo parcelas;
    private final DespesaRepo despesas;
    private final DataEspecialRepo datas;
    private final PesquisaRepo pesquisas;
    private final FreelancerRepo freelancers;
    private final TrabalhoFreelancerRepo vinculos;
    private final TarefaRepo tarefas;
    private final UsuarioRepo usuarios;

    public EquipeServico(ClienteRepo clientes, TrabalhoRepo trabalhos, ParcelaRepo parcelas, DespesaRepo despesas,
                         DataEspecialRepo datas, PesquisaRepo pesquisas, FreelancerRepo freelancers,
                         TrabalhoFreelancerRepo vinculos, TarefaRepo tarefas, UsuarioRepo usuarios) {
        this.clientes = clientes;
        this.trabalhos = trabalhos;
        this.parcelas = parcelas;
        this.despesas = despesas;
        this.datas = datas;
        this.pesquisas = pesquisas;
        this.freelancers = freelancers;
        this.vinculos = vinculos;
        this.tarefas = tarefas;
        this.usuarios = usuarios;
    }

    // ================================================================ datas especiais

    public enum TipoData { ANIVERSARIO, DATA_ESPECIAL }

    public record ProximaData(Long clienteId, String nome, String telefone, TipoData tipo, String descricao, LocalDate data,
                              long diasFaltando, Integer anos, Long dataId) {}

    /** Aniversários e datas especiais nos próximos {@code dias} dias (inclui hoje). */
    @Transactional(readOnly = true)
    public List<ProximaData> proximasDatas(Long estudioId, int dias, LocalDate hoje) {
        Map<Long, Cliente> porId = new HashMap<>();
        List<ProximaData> r = new ArrayList<>();
        for (Cliente c : clientes.findByEstudioIdOrderByNome(estudioId)) {
            porId.put(c.getId(), c);
            if (c.getDataNascimento() == null) continue;
            LocalDate prox = proxima(MonthDay.from(c.getDataNascimento()), hoje);
            long falta = ChronoUnit.DAYS.between(hoje, prox);
            if (falta <= dias) {
                r.add(new ProximaData(c.getId(), c.getNome(), c.getTelefone(), TipoData.ANIVERSARIO, "Aniversário", prox, falta,
                        prox.getYear() - c.getDataNascimento().getYear(), null));
            }
        }
        for (DataEspecial d : datas.findByEstudioId(estudioId)) {
            Cliente c = porId.get(d.getClienteId());
            if (c == null) continue;
            LocalDate prox;
            try {
                prox = proxima(MonthDay.of(d.getMes(), d.getDia()), hoje);
            } catch (java.time.DateTimeException ex) {
                continue;
            }
            long falta = ChronoUnit.DAYS.between(hoje, prox);
            if (falta <= dias) {
                r.add(new ProximaData(c.getId(), c.getNome(), c.getTelefone(), TipoData.DATA_ESPECIAL, d.getDescricao(), prox, falta,
                        d.getAno() != null ? prox.getYear() - d.getAno() : null, d.getId()));
            }
        }
        r.sort(Comparator.comparingLong(ProximaData::diasFaltando).thenComparing(ProximaData::nome));
        return r;
    }

    /** Próxima ocorrência do dia/mês (29/02 cai em 28/02 nos anos comuns). */
    static LocalDate proxima(MonthDay md, LocalDate hoje) {
        LocalDate d = md.atYear(hoje.getYear());
        if (d.isBefore(hoje)) d = md.atYear(hoje.getYear() + 1);
        return d;
    }

    @Transactional(readOnly = true)
    public List<DataEspecial> datasDaCliente(Long estudioId, Long clienteId) {
        cliente(estudioId, clienteId);
        return datas.findByClienteIdOrderByMesAscDiaAsc(clienteId);
    }

    @Transactional
    public DataEspecial novaData(Long estudioId, Long clienteId, String descricao, int dia, int mes, Integer ano) {
        cliente(estudioId, clienteId);
        try {
            MonthDay.of(mes, dia);
        } catch (java.time.DateTimeException ex) {
            throw new RegraNegocio("Data inválida.");
        }
        if (ano != null && (ano < 1900 || ano > LocalDate.now().getYear())) throw new RegraNegocio("Ano inválido.");
        DataEspecial d = new DataEspecial();
        d.setEstudioId(estudioId);
        d.setClienteId(clienteId);
        d.setDescricao(descricao.trim());
        d.setDia(dia);
        d.setMes(mes);
        d.setAno(ano);
        return datas.save(d);
    }

    @Transactional
    public void apagarData(Long estudioId, Long clienteId, Long dataId) {
        cliente(estudioId, clienteId);
        datas.findByIdAndClienteId(dataId, clienteId).ifPresent(datas::delete);
    }

    // ================================================================ clientes inativas

    public record Inativa(Long clienteId, String nome, String telefone, LocalDate ultimoEnsaio, String ultimoTrabalho,
                          long totalTrabalhos, BigDecimal totalPago, long mesesSemEnsaio, LocalDateTime ultimoContato) {}

    /** Clientes cujo último ensaio foi há mais de {@code meses} meses (e sem ensaio marcado no futuro). */
    @Transactional(readOnly = true)
    public List<Inativa> inativas(Long estudioId, int meses, LocalDate hoje) {
        LocalDate limite = hoje.minusMonths(meses);
        Map<Long, List<Trabalho>> porCliente = new HashMap<>();
        for (Trabalho t : trabalhos.findByEstudioIdOrderByDataTrabalhoDesc(estudioId)) {
            if (t.getStatus() == StatusTrabalho.CANCELADO) continue;
            porCliente.computeIfAbsent(t.getCliente().getId(), k -> new ArrayList<>()).add(t);
        }
        Map<Long, BigDecimal> pago = new HashMap<>();
        for (List<Trabalho> lista : porCliente.values()) {
            for (Trabalho t : lista) {
                BigDecimal soma = parcelas.findByTrabalhoIdOrderByVencimentoAscIdAsc(t.getId()).stream()
                        .filter(Parcela::paga).map(Parcela::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
                pago.merge(t.getCliente().getId(), soma, BigDecimal::add);
            }
        }
        List<Inativa> r = new ArrayList<>();
        for (var e : porCliente.entrySet()) {
            Trabalho ultimo = e.getValue().stream()
                    .max(Comparator.comparing(t -> t.getDataTrabalho() != null ? t.getDataTrabalho() : t.getCriadoEm())).orElseThrow();
            LocalDate quando = (ultimo.getDataTrabalho() != null ? ultimo.getDataTrabalho() : ultimo.getCriadoEm()).toLocalDate();
            if (!quando.isBefore(limite)) continue;
            Cliente c = ultimo.getCliente();
            r.add(new Inativa(c.getId(), c.getNome(), c.getTelefone(), quando, ultimo.getTitulo(), e.getValue().size(),
                    pago.getOrDefault(c.getId(), BigDecimal.ZERO), ChronoUnit.MONTHS.between(quando, hoje), c.getUltimoContato()));
        }
        // Quem nunca foi chamada (ou foi chamada há mais tempo) e gastou mais aparece primeiro.
        r.sort(Comparator.comparing((Inativa i) -> i.ultimoContato() != null && i.ultimoContato().isAfter(hoje.minusMonths(3).atStartOfDay()))
                .thenComparing(Inativa::totalPago, Comparator.reverseOrder()));
        return r;
    }

    @Transactional
    public void registrarContato(Long estudioId, Long clienteId) {
        cliente(estudioId, clienteId).setUltimoContato(LocalDateTime.now());
    }

    // ================================================================ pesquisa de satisfação

    /** Cria a pesquisa do trabalho, se ainda não existir (chamada na entrega). */
    @Transactional
    public PesquisaSatisfacao garantirPesquisa(Trabalho t) {
        return pesquisas.findByTrabalhoId(t.getId()).orElseGet(() -> {
            PesquisaSatisfacao p = new PesquisaSatisfacao();
            p.setEstudioId(t.getEstudioId());
            p.setTrabalhoId(t.getId());
            p.setToken(TrabalhoServico.novoTokenGaleria());
            return pesquisas.save(p);
        });
    }

    @Transactional
    public PesquisaSatisfacao pesquisaDoTrabalho(Long estudioId, Long trabalhoId) {
        return garantirPesquisa(trabalho(estudioId, trabalhoId));
    }

    @Transactional(readOnly = true)
    public PesquisaSatisfacao pesquisaPorToken(String token) {
        return pesquisas.findByToken(token).orElseThrow(() -> new NaoEncontrado("Pesquisa não encontrada."));
    }

    @Transactional
    public PesquisaSatisfacao responder(String token, int nota, Integer atendimento, Integer fotos, Integer prazo,
                                        String comentario, Boolean autoriza) {
        PesquisaSatisfacao p = pesquisas.findByToken(token).orElseThrow(() -> new NaoEncontrado("Pesquisa não encontrada."));
        if (p.getRespondidaEm() != null) throw new RegraNegocio("Esta pesquisa já foi respondida. Obrigado!");
        if (nota < 0 || nota > 10) throw new RegraNegocio("Escolha uma nota de 0 a 10.");
        for (Integer e : new Integer[]{atendimento, fotos, prazo}) {
            if (e != null && (e < 1 || e > 5)) throw new RegraNegocio("Avaliação inválida.");
        }
        p.setNota(nota);
        p.setAtendimento(atendimento);
        p.setFotos(fotos);
        p.setPrazo(prazo);
        String c = comentario == null ? null : comentario.trim();
        p.setComentario(c == null || c.isEmpty() ? null : c.substring(0, Math.min(2000, c.length())));
        p.setAutorizaDepoimento(p.getComentario() != null ? Boolean.TRUE.equals(autoriza) : null);
        p.setRespondidaEm(LocalDateTime.now());
        return p;
    }

    public record ResumoPesquisas(long enviadas, long respondidas, Integer nps, long promotores, long neutros, long detratores,
                                  Double mediaAtendimento, Double mediaFotos, Double mediaPrazo) {}

    @Transactional(readOnly = true)
    public List<PesquisaSatisfacao> pesquisas(Long estudioId) {
        return pesquisas.findByEstudioIdOrderByCriadoEmDesc(estudioId);
    }

    /** NPS = % promotores (9–10) − % detratores (0–6). */
    public static ResumoPesquisas resumo(List<PesquisaSatisfacao> lista) {
        List<PesquisaSatisfacao> resp = lista.stream().filter(p -> p.getRespondidaEm() != null).toList();
        long prom = resp.stream().filter(p -> p.getNota() >= 9).count();
        long detr = resp.stream().filter(p -> p.getNota() <= 6).count();
        Integer nps = resp.isEmpty() ? null : (int) Math.round(100.0 * (prom - detr) / resp.size());
        return new ResumoPesquisas(lista.size(), resp.size(), nps, prom, resp.size() - prom - detr, detr,
                media(resp, PesquisaSatisfacao::getAtendimento), media(resp, PesquisaSatisfacao::getFotos), media(resp, PesquisaSatisfacao::getPrazo));
    }

    private static Double media(List<PesquisaSatisfacao> l, java.util.function.Function<PesquisaSatisfacao, Integer> f) {
        OptionalDouble m = l.stream().map(f).filter(Objects::nonNull).mapToInt(Integer::intValue).average();
        return m.isPresent() ? Math.round(m.getAsDouble() * 10) / 10.0 : null;
    }

    // ================================================================ freelancers

    @Transactional(readOnly = true)
    public List<Freelancer> freelancers(Long estudioId) {
        return freelancers.findByEstudioIdOrderByAtivoDescNomeAsc(estudioId);
    }

    @Transactional
    public Freelancer salvarFreelancer(Long estudioId, Long id, String nome, String telefone, String funcao, String pix,
                                       String obs, boolean ativo) {
        Freelancer f = id == null ? new Freelancer() : freelancer(estudioId, id);
        f.setEstudioId(estudioId);
        f.setNome(nome.trim());
        f.setTelefone(vazio(telefone) ? null : telefone.replaceAll("\\D", ""));
        f.setFuncao(vazio(funcao) ? null : funcao.trim());
        f.setChavePix(vazio(pix) ? null : pix.trim());
        f.setObservacoes(vazio(obs) ? null : obs.trim());
        f.setAtivo(ativo);
        return freelancers.save(f);
    }

    public Freelancer freelancer(Long estudioId, Long id) {
        return freelancers.findByIdAndEstudioId(id, estudioId).orElseThrow(() -> new NaoEncontrado("Freelancer não encontrado."));
    }

    @Transactional(readOnly = true)
    public List<TrabalhoFreelancer> freelancersDoTrabalho(Long estudioId, Long trabalhoId) {
        trabalho(estudioId, trabalhoId);
        return vinculos.findByTrabalhoIdOrderByIdAsc(trabalhoId);
    }

    @Transactional(readOnly = true)
    public List<TrabalhoFreelancer> extrato(Long estudioId, Long freelancerId) {
        freelancer(estudioId, freelancerId);
        return vinculos.findByEstudioIdOrderByCriadoEmDesc(estudioId).stream()
                .filter(v -> v.getFreelancer().getId().equals(freelancerId)).toList();
    }

    @Transactional(readOnly = true)
    public List<TrabalhoFreelancer> todosVinculos(Long estudioId) {
        return vinculos.findByEstudioIdOrderByCriadoEmDesc(estudioId);
    }

    /** Coloca um freelancer no trabalho, com valor fixo ou percentual sobre o valor do trabalho. */
    @Transactional
    public TrabalhoFreelancer vincular(Long estudioId, Long trabalhoId, Long freelancerId, String funcao, BigDecimal valor, BigDecimal percentual) {
        Trabalho t = trabalho(estudioId, trabalhoId);
        Freelancer f = freelancer(estudioId, freelancerId);
        if (percentual == null && valor == null) throw new RegraNegocio("Informe o valor ou o percentual da comissão.");
        if (percentual != null && (percentual.signum() <= 0 || percentual.compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new RegraNegocio("Percentual entre 0 e 100.");
        }
        if (valor != null && valor.signum() < 0) throw new RegraNegocio("Valor inválido.");
        TrabalhoFreelancer v = new TrabalhoFreelancer();
        v.setEstudioId(estudioId);
        v.setTrabalhoId(t.getId());
        v.setFreelancer(f);
        v.setFuncao(vazio(funcao) ? f.getFuncao() : funcao.trim());
        v.setPercentual(percentual);
        v.setValor(percentual != null
                ? t.getValorTotal().multiply(percentual).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : valor.setScale(2, RoundingMode.HALF_UP));
        return vinculos.save(v);
    }

    @Transactional
    public void desvincular(Long estudioId, Long vinculoId) {
        TrabalhoFreelancer v = vinculo(estudioId, vinculoId);
        if (v.getDespesaId() != null) throw new RegraNegocio("Já foi pago. Desfaça o pagamento antes de remover.");
        vinculos.delete(v);
    }

    /** Marca a comissão como paga: vira uma despesa paga (categoria Freelancers), ligada ao trabalho. */
    @Transactional
    public TrabalhoFreelancer pagar(Long estudioId, Long vinculoId, LocalDate data) {
        TrabalhoFreelancer v = vinculo(estudioId, vinculoId);
        if (v.getDespesaId() != null) throw new RegraNegocio("Esta comissão já foi paga.");
        Trabalho t = trabalho(estudioId, v.getTrabalhoId());
        Despesa d = new Despesa();
        d.setEstudioId(estudioId);
        d.setDescricao((v.getFuncao() != null ? v.getFuncao() + " · " : "") + v.getFreelancer().getNome() + " · " + t.getTitulo());
        d.setCategoria("Freelancers");
        d.setValor(v.getValor());
        LocalDate quando = data != null ? data : LocalDate.now();
        d.setVencimento(quando);
        d.setPagoEm(quando);
        d.setTrabalhoId(t.getId());
        v.setDespesaId(despesas.save(d).getId());
        return v;
    }

    @Transactional
    public TrabalhoFreelancer desfazerPagamento(Long estudioId, Long vinculoId) {
        TrabalhoFreelancer v = vinculo(estudioId, vinculoId);
        if (v.getDespesaId() != null) {
            despesas.findById(v.getDespesaId()).ifPresent(despesas::delete);
            v.setDespesaId(null);
        }
        return v;
    }

    public Optional<LocalDate> pagoEm(TrabalhoFreelancer v) {
        return v.getDespesaId() == null ? Optional.empty() : despesas.findById(v.getDespesaId()).map(Despesa::getPagoEm);
    }

    private TrabalhoFreelancer vinculo(Long estudioId, Long id) {
        return vinculos.findByIdAndEstudioId(id, estudioId).orElseThrow(() -> new NaoEncontrado("Comissão não encontrada."));
    }

    // ================================================================ tarefas

    @Transactional(readOnly = true)
    public List<Tarefa> tarefas(Long estudioId) {
        return tarefas.findByEstudioIdOrderByIdDesc(estudioId);
    }

    @Transactional(readOnly = true)
    public List<Tarefa> tarefasDoTrabalho(Long estudioId, Long trabalhoId) {
        trabalho(estudioId, trabalhoId);
        return tarefas.findByTrabalhoIdOrderByIdAsc(trabalhoId);
    }

    @Transactional
    public Tarefa salvarTarefa(Long estudioId, Long id, Long trabalhoId, String titulo, String descricao, Long usuarioId,
                               Long freelancerId, LocalDate prazo) {
        Tarefa t = id == null ? new Tarefa() : tarefa(estudioId, id);
        t.setEstudioId(estudioId);
        if (trabalhoId != null) trabalho(estudioId, trabalhoId);
        t.setTrabalhoId(trabalhoId);
        t.setTitulo(titulo.trim());
        t.setDescricao(vazio(descricao) ? null : descricao.trim());
        if (usuarioId != null && usuarios.findById(usuarioId).filter(u -> u.getEstudioId().equals(estudioId)).isEmpty()) {
            throw new RegraNegocio("Responsável não encontrado.");
        }
        if (freelancerId != null) freelancer(estudioId, freelancerId);
        if (usuarioId != null && freelancerId != null) throw new RegraNegocio("Escolha um responsável só.");
        t.setResponsavelUsuarioId(usuarioId);
        t.setResponsavelFreelancerId(freelancerId);
        t.setPrazo(prazo);
        return tarefas.save(t);
    }

    @Transactional
    public Tarefa mudarStatusTarefa(Long estudioId, Long id, StatusTarefa status) {
        Tarefa t = tarefa(estudioId, id);
        t.setStatus(status);
        t.setConcluidaEm(status == StatusTarefa.FEITA ? LocalDateTime.now() : null);
        return t;
    }

    @Transactional
    public void apagarTarefa(Long estudioId, Long id) {
        tarefas.delete(tarefa(estudioId, id));
    }

    /** Ao fechar a seleção, a edição entra na lista de tarefas com o prazo de entrega do trabalho. */
    @Transactional
    public void tarefaDeEdicao(Trabalho t, long fotos) {
        boolean jaTem = tarefas.findByTrabalhoIdOrderByIdAsc(t.getId()).stream()
                .anyMatch(x -> x.isAutomatica() && x.getStatus() != StatusTarefa.FEITA);
        if (jaTem) return;
        Tarefa x = new Tarefa();
        x.setEstudioId(t.getEstudioId());
        x.setTrabalhoId(t.getId());
        x.setTitulo("Editar " + fotos + (fotos == 1 ? " foto" : " fotos") + " · " + t.getCliente().getNome());
        x.setPrazo(t.getPrazoEntrega());
        x.setAutomatica(true);
        tarefas.save(x);
    }

    /** Na entrega, as tarefas abertas do trabalho são concluídas. */
    @Transactional
    public void concluirTarefasDoTrabalho(Long trabalhoId) {
        for (Tarefa x : tarefas.findByTrabalhoIdOrderByIdAsc(trabalhoId)) {
            if (x.getStatus() != StatusTarefa.FEITA) {
                x.setStatus(StatusTarefa.FEITA);
                x.setConcluidaEm(LocalDateTime.now());
            }
        }
    }

    private Tarefa tarefa(Long estudioId, Long id) {
        return tarefas.findByIdAndEstudioId(id, estudioId).orElseThrow(() -> new NaoEncontrado("Tarefa não encontrada."));
    }

    // ================================================================ apoio

    private Cliente cliente(Long estudioId, Long id) {
        return clientes.findByIdAndEstudioId(id, estudioId).orElseThrow(() -> new NaoEncontrado("Cliente não encontrado."));
    }

    private Trabalho trabalho(Long estudioId, Long id) {
        return trabalhos.findByIdAndEstudioId(id, estudioId).orElseThrow(() -> new NaoEncontrado("Trabalho não encontrado."));
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }
}
