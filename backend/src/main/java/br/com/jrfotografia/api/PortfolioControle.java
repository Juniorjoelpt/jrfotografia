package br.com.jrfotografia.api;

import br.com.jrfotografia.api.Dtos.PortfolioDto;
import br.com.jrfotografia.api.Dtos.PortfolioEdicao;
import br.com.jrfotografia.config.Erros.NaoEncontrado;
import br.com.jrfotografia.config.Erros.RegraNegocio;
import br.com.jrfotografia.config.UsuarioLogado;
import br.com.jrfotografia.dominio.PortfolioFoto;
import br.com.jrfotografia.dominio.Repositorios.PortfolioRepo;
import br.com.jrfotografia.servico.Armazenamento;
import br.com.jrfotografia.servico.Imagens;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Fotos do portfólio do site. Sem marca d'água: são as melhores fotos, escolhidas por você. */
@RestController
@RequestMapping("/api/portfolio")
@PreAuthorize("hasRole('ADMIN')")
public class PortfolioControle {

    private final PortfolioRepo portfolio;
    private final Imagens imagens;
    private final Armazenamento armazenamento;

    public PortfolioControle(PortfolioRepo portfolio, Imagens imagens, Armazenamento armazenamento) {
        this.portfolio = portfolio;
        this.imagens = imagens;
        this.armazenamento = armazenamento;
    }

    @GetMapping
    public List<PortfolioDto> listar() {
        return portfolio.findByEstudioIdOrderByOrdemAscIdDesc(UsuarioLogado.estudio()).stream().map(PortfolioDto::de).toList();
    }

    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public List<PortfolioDto> enviar(@RequestParam("arquivos") List<MultipartFile> arquivos,
                                     @RequestParam(defaultValue = "feminino") String categoria) {
        if (arquivos == null || arquivos.isEmpty()) throw new RegraNegocio("Selecione pelo menos uma foto.");
        Long e = UsuarioLogado.estudio();
        String cat = categoria.trim().toLowerCase();
        List<PortfolioDto> criadas = new ArrayList<>();
        for (MultipartFile arq : arquivos) {
            byte[] bytes;
            try {
                bytes = arq.getBytes();
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
            var web = imagens.processarParaWeb(bytes, 2000);
            var mini = imagens.processarParaWeb(bytes, Imagens.LADO_MINIATURA);
            String base = e + "/portfolio/" + UUID.randomUUID();
            PortfolioFoto p = new PortfolioFoto();
            p.setEstudioId(e);
            p.setCategoria(cat);
            p.setArquivo(base + ".jpg");
            p.setMiniatura(base + "_mini.jpg");
            p.setLargura(web.largura());
            p.setAltura(web.altura());
            armazenamento.gravar(p.getArquivo(), web.imagem());
            armazenamento.gravar(p.getMiniatura(), mini.imagem());
            criadas.add(PortfolioDto.de(portfolio.save(p)));
        }
        return criadas;
    }

    @PutMapping("/{id}")
    @Transactional
    public PortfolioDto editar(@PathVariable Long id, @RequestBody PortfolioEdicao f) {
        PortfolioFoto p = buscar(id);
        if (f.categoria() != null && !f.categoria().isBlank()) p.setCategoria(f.categoria().trim().toLowerCase());
        if (f.titulo() != null) p.setTitulo(f.titulo().isBlank() ? null : f.titulo().trim());
        if (f.ordem() != null) p.setOrdem(f.ordem());
        if (f.destaque() != null) p.setDestaque(f.destaque());
        return PortfolioDto.de(p);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void apagar(@PathVariable Long id) {
        PortfolioFoto p = buscar(id);
        armazenamento.apagar(p.getArquivo());
        armazenamento.apagar(p.getMiniatura());
        portfolio.delete(p);
    }

    private PortfolioFoto buscar(Long id) {
        return portfolio.findByIdAndEstudioId(id, UsuarioLogado.estudio())
                .orElseThrow(() -> new NaoEncontrado("Foto do portfólio não encontrada"));
    }
}
