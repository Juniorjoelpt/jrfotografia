package br.com.jrfotografia.api;

import br.com.jrfotografia.api.Dtos.*;
import br.com.jrfotografia.config.Erros.Proibido;
import br.com.jrfotografia.dominio.Enums.StatusTrabalho;
import br.com.jrfotografia.dominio.Foto;
import br.com.jrfotografia.dominio.Repositorios.EstudioRepo;
import br.com.jrfotografia.dominio.Trabalho;
import br.com.jrfotografia.servico.Armazenamento;
import br.com.jrfotografia.servico.GaleriaServico;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Galeria do cliente, aberta pelo link /g/{token}. O acesso vem do cabeçalho
 * Authorization ou do parâmetro "t" (para imagens e downloads abertos pelo navegador).
 */
@RestController
@RequestMapping("/api/galeria/{token}")
public class GaleriaControle {

    private final GaleriaServico galeria;
    private final Armazenamento armazenamento;
    private final EstudioRepo estudios;

    public GaleriaControle(GaleriaServico galeria, Armazenamento armazenamento, EstudioRepo estudios) {
        this.galeria = galeria;
        this.armazenamento = armazenamento;
        this.estudios = estudios;
    }

    @PostMapping("/acesso")
    public Map<String, String> entrar(@PathVariable String token, @Valid @RequestBody AcessoGaleria f) {
        return Map.of("acesso", galeria.entrar(token, f.senha()));
    }

    @GetMapping
    public GaleriaDto ver(@PathVariable String token,
                          @RequestHeader(value = "Authorization", required = false) String auth,
                          @RequestParam(value = "t", required = false) String t) {
        return dto(galeria.autorizar(token, acesso(auth, t)));
    }

    @PutMapping("/fotos/{fotoId}/selecao")
    public GaleriaDto marcar(@PathVariable String token, @PathVariable Long fotoId,
                             @RequestHeader("Authorization") String auth, @RequestBody MarcarForm f) {
        Trabalho t = galeria.autorizar(token, acesso(auth, null));
        galeria.marcar(t, fotoId, f.selecionada());
        return dto(t);
    }

    @PutMapping("/fotos/{fotoId}/comentario")
    public FotoGaleria comentar(@PathVariable String token, @PathVariable Long fotoId,
                                @RequestHeader("Authorization") String auth, @Valid @RequestBody ComentarioForm f) {
        Trabalho t = galeria.autorizar(token, acesso(auth, null));
        Foto foto = galeria.comentar(t, fotoId, f.comentario());
        return new FotoGaleria(foto.getId(), foto.getLargura(), foto.getAltura(), foto.isSelecionada(), foto.getComentario());
    }

    @PostMapping("/fechar-selecao")
    public GaleriaServico.ResultadoSelecao fechar(@PathVariable String token, @RequestHeader("Authorization") String auth) {
        return galeria.fecharSelecao(galeria.autorizar(token, acesso(auth, null)));
    }

    @GetMapping("/fotos/{fotoId}/miniatura")
    public ResponseEntity<Resource> miniatura(@PathVariable String token, @PathVariable Long fotoId,
                                              @RequestParam("t") String t) {
        Trabalho trabalho = galeria.autorizar(token, t);
        return Respostas.imagem(armazenamento.caminho(galeria.foto(trabalho, fotoId).getMiniatura()), false);
    }

    @GetMapping("/fotos/{fotoId}/previa")
    public ResponseEntity<Resource> previa(@PathVariable String token, @PathVariable Long fotoId,
                                           @RequestParam("t") String t) {
        Trabalho trabalho = galeria.autorizar(token, t);
        return Respostas.imagem(armazenamento.caminho(galeria.foto(trabalho, fotoId).getPrevia()), false);
    }

    @GetMapping("/fotos/{fotoId}/download")
    public ResponseEntity<Resource> baixar(@PathVariable String token, @PathVariable Long fotoId,
                                           @RequestParam("t") String t) {
        Trabalho trabalho = galeria.autorizar(token, t);
        exigirDownload(trabalho);
        Foto f = galeria.foto(trabalho, fotoId);
        return Respostas.download(armazenamento.caminho(f.arquivoParaEntrega()), f.getNomeOriginal());
    }

    @GetMapping("/download.zip")
    public ResponseEntity<StreamingResponseBody> baixarTudo(@PathVariable String token, @RequestParam("t") String t) {
        Trabalho trabalho = galeria.autorizar(token, t);
        exigirDownload(trabalho);
        List<Foto> fotos = galeria.fotosVisiveis(trabalho);
        StreamingResponseBody corpo = out -> {
            try (ZipOutputStream zip = new ZipOutputStream(out)) {
                zip.setLevel(Deflater.NO_COMPRESSION);
                Set<String> nomes = new HashSet<>();
                for (Foto f : fotos) {
                    String nome = f.getNomeOriginal();
                    for (int i = 2; !nomes.add(nome); i++) nome = i + "_" + f.getNomeOriginal();
                    zip.putNextEntry(new ZipEntry(nome));
                    Files.copy(armazenamento.caminho(f.arquivoParaEntrega()), zip);
                    zip.closeEntry();
                }
            }
        };
        String arquivo = "JR Fotografia - " + trabalho.getCliente().getNome() + ".zip";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(arquivo, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(corpo);
    }

    private void exigirDownload(Trabalho t) {
        var s = galeria.download(t);
        if (!s.liberado()) throw new Proibido(s.motivo());
    }

    private static String acesso(String header, String param) {
        if (header != null && header.startsWith("Bearer ")) return header.substring(7);
        return param;
    }

    private GaleriaDto dto(Trabalho t) {
        var estudio = estudios.findById(t.getEstudioId()).orElseThrow();
        var fotos = galeria.fotosVisiveis(t);
        var download = galeria.download(t);
        boolean selecaoAberta = t.exigeSelecao() && t.getStatus() == StatusTrabalho.SELECAO && t.getSelecaoFechadaEm() == null;
        return new GaleriaDto(
                t.getTitulo(),
                t.getCliente().getNome(),
                t.getDataTrabalho(),
                estudio.getNome(),
                estudio.getWhatsapp(),
                t.getStatus(),
                t.exigeSelecao(),
                t.getPacote() != null ? t.getPacote().getQtdFotos() : null,
                t.getPacote() != null ? t.getPacote().getPrecoFotoExtra() : null,
                selecaoAberta,
                fotos.stream().filter(Foto::isSelecionada).count(),
                download.liberado(),
                download.motivo(),
                t.getGaleriaExpiraEm(),
                fotos.stream().map(f -> new FotoGaleria(f.getId(), f.getLargura(), f.getAltura(), f.isSelecionada(), f.getComentario())).toList());
    }
}
