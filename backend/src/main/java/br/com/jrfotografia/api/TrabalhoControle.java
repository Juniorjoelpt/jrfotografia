package br.com.jrfotografia.api;

import br.com.jrfotografia.dominio.Repositorios.PesquisaRepo;
import br.com.jrfotografia.servico.EmailAvisos;
import br.com.jrfotografia.api.Dtos.*;
import br.com.jrfotografia.config.Erros.NaoEncontrado;
import br.com.jrfotografia.config.Erros.RegraNegocio;
import br.com.jrfotografia.config.UsuarioLogado;
import br.com.jrfotografia.dominio.Enums.StatusTrabalho;
import br.com.jrfotografia.dominio.Foto;
import br.com.jrfotografia.dominio.Repositorios.FotoRepo;
import br.com.jrfotografia.dominio.Repositorios.ParcelaRepo;
import br.com.jrfotografia.dominio.Repositorios.TrabalhoRepo;
import br.com.jrfotografia.dominio.Trabalho;
import br.com.jrfotografia.dominio.Repositorios.EstudioRepo;
import br.com.jrfotografia.servico.AgendaServico;
import br.com.jrfotografia.servico.Armazenamento;
import br.com.jrfotografia.servico.PdfServico;
import br.com.jrfotografia.servico.RegraDownload;
import br.com.jrfotografia.servico.TrabalhoServico;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/trabalhos")
public class TrabalhoControle {

    private final PesquisaRepo pesquisas;
    private final br.com.jrfotografia.servico.BoasVindasServico boasVindas;
    private final br.com.jrfotografia.dominio.Repositorios.CobrancaPixRepo cobrancas;
    private final EmailAvisos avisos;
    private final TrabalhoServico servico;
    private final TrabalhoRepo trabalhos;
    private final FotoRepo fotos;
    private final ParcelaRepo parcelas;
    private final Armazenamento armazenamento;
    private final AgendaServico agenda;
    private final PdfServico pdf;
    private final EstudioRepo estudios;

    public TrabalhoControle(TrabalhoServico servico, TrabalhoRepo trabalhos, FotoRepo fotos,
                            ParcelaRepo parcelas, Armazenamento armazenamento, AgendaServico agenda,
                            PdfServico pdf, EstudioRepo estudios, EmailAvisos avisos, PesquisaRepo pesquisas,
                            br.com.jrfotografia.dominio.Repositorios.CobrancaPixRepo cobrancas,
                            br.com.jrfotografia.servico.BoasVindasServico boasVindas) {
        this.boasVindas = boasVindas;
        this.cobrancas = cobrancas;
        this.pesquisas = pesquisas;
        this.avisos = avisos;
        this.agenda = agenda;
        this.pdf = pdf;
        this.estudios = estudios;
        this.servico = servico;
        this.trabalhos = trabalhos;
        this.fotos = fotos;
        this.parcelas = parcelas;
        this.armazenamento = armazenamento;
    }

    @GetMapping
    public List<TrabalhoResumo> listar(@RequestParam(required = false) StatusTrabalho status) {
        Long e = UsuarioLogado.estudio();
        var lista = status == null ? trabalhos.findByEstudioIdOrderByDataTrabalhoDesc(e)
                : trabalhos.findByEstudioIdAndStatusOrderByDataTrabalhoDesc(e, status);
        return lista.stream().map(t -> TrabalhoResumo.de(t,
                fotos.findTop4ByTrabalhoIdOrderByOrdemAscIdAsc(t.getId()).stream().map(Foto::getId).toList(),
                fotos.countByTrabalhoId(t.getId()))).toList();
    }

    @GetMapping("/{id}")
    public TrabalhoDetalhe detalhe(@PathVariable Long id) {
        return detalheDe(servico.buscar(UsuarioLogado.estudio(), id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public TrabalhoDetalhe criar(@Valid @RequestBody TrabalhoForm f) {
        Trabalho t = servico.criar(UsuarioLogado.estudio(), new TrabalhoServico.NovoTrabalho(
                f.clienteId(), f.pacoteId(), f.titulo(), f.tipo(), f.dataTrabalho(), f.localTrabalho(),
                f.valorTotal(), f.formaPagamento(), f.valorSinal(), f.numParcelas(), f.primeiroVencimento(),
                f.observacoes()));
        t = servico.depoisDeCriar(UsuarioLogado.estudio(), t.getId(), f.duracaoMin(), f.pedidoId());
        // Como nas outras portas: contrato pronto, área da cliente e e-mail de boas-vindas.
        boasVindas.trabalhoCriado(t);
        return detalheDe(t);
    }

    @PutMapping("/{id}")
    @Transactional
    public TrabalhoDetalhe editar(@PathVariable Long id, @RequestBody TrabalhoEdicao f) {
        Trabalho t = servico.buscar(UsuarioLogado.estudio(), id);
        if (f.titulo() != null && !f.titulo().isBlank()) t.setTitulo(f.titulo().trim());
        t.setTipo(f.tipo());
        t.setDataTrabalho(f.dataTrabalho());
        t.setLocalTrabalho(f.localTrabalho());
        if (f.prazoEntrega() != null) t.setPrazoEntrega(f.prazoEntrega());
        t.setObservacoes(f.observacoes());
        if (f.duracaoMin() != null && f.duracaoMin() >= 30) t.setDuracaoMin(Math.min(f.duracaoMin(), 1440));
        return detalheDe(t);
    }

    /** Contrato do trabalho em PDF, montado a partir do modelo do estúdio. */
    @GetMapping("/{id}/contrato.pdf")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> contrato(@PathVariable Long id) {
        Trabalho t = servico.buscar(UsuarioLogado.estudio(), id);
        var e = estudios.findById(t.getEstudioId()).orElseThrow();
        byte[] corpo = pdf.contrato(t, e, parcelas.findByTrabalhoIdOrderByVencimentoAscIdAsc(t.getId()));
        return Pdfs.resposta(corpo, "contrato-" + Pdfs.nomeArquivo(t.getCliente().getNome()) + ".pdf");
    }

    @PatchMapping("/{id}/status")
    public TrabalhoDetalhe mudarStatus(@PathVariable Long id, @Valid @RequestBody StatusTrabalhoForm f) {
        br.com.jrfotografia.servico.AuditoriaServico.detalhe("Para " + f.status().name().toLowerCase().replace("selecao", "seleção").replace("edicao", "edição"));
        Trabalho t = servico.mudarStatus(UsuarioLogado.estudio(), id, f.status());
        if (f.status() == StatusTrabalho.ENTREGUE) {
            avisos.fotosEntregues(t, pesquisas.findByTrabalhoId(t.getId()).filter(p -> p.getRespondidaEm() == null).map(p -> p.getToken()).orElse(null),
                    br.com.jrfotografia.servico.RegraDownload.avaliar(t, parcelas.findByTrabalhoIdOrderByVencimentoAscIdAsc(t.getId())));
        }
        return detalheDe(t);
    }

    /** Reabre a seleção para o cliente escolher mais fotos. */
    @PostMapping("/{id}/reabrir-selecao")
    public TrabalhoDetalhe reabrirSelecao(@PathVariable Long id) {
        return detalheDe(servico.reabrirSelecao(UsuarioLogado.estudio(), id));
    }

    public record EmailGaleria(@jakarta.validation.constraints.NotBlank String senha) {}

    /** Manda o link e a senha da galeria por e-mail (a senha só existe na tela de quem acabou de gerar). */
    @PostMapping("/{id}/galeria/email")
    public java.util.Map<String, Boolean> galeriaPorEmail(@PathVariable Long id, @Valid @RequestBody EmailGaleria f) {
        Trabalho t = servico.buscar(UsuarioLogado.estudio(), id);
        if (t.getCliente().getEmail() == null || t.getCliente().getEmail().isBlank()) {
            throw new br.com.jrfotografia.config.Erros.RegraNegocio("A cliente não tem e-mail no cadastro.");
        }
        if (!estudios.findById(t.getEstudioId()).orElseThrow().isEmailAtivo()) {
            throw new br.com.jrfotografia.config.Erros.RegraNegocio("Os e-mails estão desligados. Ligue em Ajustes › E-mail.");
        }
        return java.util.Map.of("enviado", avisos.galeria(t, f.senha()));
    }

    /** Gera uma nova senha para a galeria; ela aparece só nesta resposta, para enviar ao cliente. */
    @PostMapping("/{id}/senha-galeria")
    public SenhaGaleria novaSenha(@PathVariable Long id) {
        String senha = servico.gerarSenhaGaleria(UsuarioLogado.estudio(), id);
        return new SenhaGaleria(senha, servico.buscar(UsuarioLogado.estudio(), id).getGaleriaToken());
    }

    // ---------- fotos ----------
    @GetMapping("/{id}/fotos")
    public List<FotoDto> listarFotos(@PathVariable Long id) {
        Trabalho t = servico.buscar(UsuarioLogado.estudio(), id);
        return fotos.findByTrabalhoIdOrderByOrdemAscIdAsc(t.getId()).stream().map(FotoDto::de).toList();
    }

    @PostMapping(value = "/{id}/fotos", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public List<FotoDto> enviarFotos(@PathVariable Long id, @RequestParam("arquivos") List<MultipartFile> arquivos) {
        if (arquivos == null || arquivos.isEmpty()) throw new RegraNegocio("Selecione pelo menos uma foto.");
        return servico.enviarFotos(UsuarioLogado.estudio(), id, arquivos).stream().map(FotoDto::de).toList();
    }

    @PostMapping(value = "/{id}/editadas", consumes = "multipart/form-data")
    public ResultadoEditadas enviarEditadas(@PathVariable Long id, @RequestParam("arquivos") List<MultipartFile> arquivos) {
        if (arquivos == null || arquivos.isEmpty()) throw new RegraNegocio("Selecione pelo menos uma foto editada.");
        var semPar = servico.enviarEditadas(UsuarioLogado.estudio(), id, arquivos);
        return new ResultadoEditadas(arquivos.size() - semPar.size(), semPar);
    }

    @DeleteMapping("/{id}/fotos/{fotoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void apagarFoto(@PathVariable Long id, @PathVariable Long fotoId) {
        servico.apagarFoto(UsuarioLogado.estudio(), id, fotoId);
    }

    @GetMapping("/{id}/fotos/{fotoId}/miniatura")
    public ResponseEntity<Resource> miniatura(@PathVariable Long id, @PathVariable Long fotoId) {
        return Respostas.imagem(armazenamento.caminho(foto(id, fotoId).getMiniatura()), false);
    }

    @GetMapping("/{id}/fotos/{fotoId}/previa")
    public ResponseEntity<Resource> previa(@PathVariable Long id, @PathVariable Long fotoId) {
        return Respostas.imagem(armazenamento.caminho(foto(id, fotoId).getPrevia()), false);
    }

    private Foto foto(Long trabalhoId, Long fotoId) {
        Trabalho t = servico.buscar(UsuarioLogado.estudio(), trabalhoId);
        return fotos.findByIdAndTrabalhoId(fotoId, t.getId()).orElseThrow(() -> new NaoEncontrado("Foto não encontrada"));
    }

    private TrabalhoDetalhe detalheDe(Trabalho t) {
        boolean admin = UsuarioLogado.atual().admin();
        var lista = parcelas.findByTrabalhoIdOrderByVencimentoAscIdAsc(t.getId());
        var download = RegraDownload.avaliar(t, lista);
        var todas = fotos.findByTrabalhoIdOrderByOrdemAscIdAsc(t.getId());
        return new TrabalhoDetalhe(
                TrabalhoResumo.de(t),
                ClienteDto.de(t.getCliente()),
                t.getPacote() != null ? PacoteDto.de(t.getPacote()) : null,
                t.getFormaPagamento(),
                t.getGaleriaToken(),
                t.getGaleriaSenhaHash() != null,
                t.getGaleriaExpiraEm(),
                t.getSelecaoFechadaEm(),
                t.getEntregueEm(),
                t.getObservacoes(),
                todas.size(),
                todas.stream().filter(Foto::isSelecionada).count(),
                todas.stream().filter(f -> f.getArquivoFinal() != null).count(),
                admin ? lista.stream().map(ParcelaDto::de).toList() : List.of(),
                download.liberado(),
                download.motivo(),
                t.getDuracaoMin(),
                t.getOrcamentoId(),
                t.getStatus() == StatusTrabalho.AGENDADO
                        ? agenda.conflitos(t.getEstudioId(), t.getDataTrabalho(), t.getDuracaoMin(), t.getId()) : List.of(),
                servico.garantirTokenCliente(t), t.getRemarcacoes(), t.getCanceladoEm(), t.getCanceladoPor(), t.getMotivoCancelamento(),
                admin ? pixAbertos(lista.stream().filter(p -> !p.paga()).map(br.com.jrfotografia.dominio.Parcela::getId).toList()) : List.of());
    }

    private List<Long> pixAbertos(List<Long> ids) {
        if (ids.isEmpty()) return List.of();
        return cobrancas.findByParcelaIdInAndStatusAndExpiraEmAfter(ids, br.com.jrfotografia.dominio.CobrancaPix.AGUARDANDO, java.time.LocalDateTime.now())
                .stream().map(br.com.jrfotografia.dominio.CobrancaPix::getParcelaId).distinct().toList();
    }
}
