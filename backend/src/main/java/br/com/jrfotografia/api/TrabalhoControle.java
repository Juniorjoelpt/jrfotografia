package br.com.jrfotografia.api;

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
import br.com.jrfotografia.servico.Armazenamento;
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

    private final TrabalhoServico servico;
    private final TrabalhoRepo trabalhos;
    private final FotoRepo fotos;
    private final ParcelaRepo parcelas;
    private final Armazenamento armazenamento;

    public TrabalhoControle(TrabalhoServico servico, TrabalhoRepo trabalhos, FotoRepo fotos,
                            ParcelaRepo parcelas, Armazenamento armazenamento) {
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
        return lista.stream().map(TrabalhoResumo::de).toList();
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
        return detalheDe(t);
    }

    @PatchMapping("/{id}/status")
    public TrabalhoDetalhe mudarStatus(@PathVariable Long id, @Valid @RequestBody StatusTrabalhoForm f) {
        return detalheDe(servico.mudarStatus(UsuarioLogado.estudio(), id, f.status()));
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
                download.motivo());
    }
}
