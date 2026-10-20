package br.com.jrfotografia.api;

import br.com.jrfotografia.api.Dtos.*;
import br.com.jrfotografia.config.AppProps;
import br.com.jrfotografia.config.Erros.NaoEncontrado;
import br.com.jrfotografia.config.Erros.RegraNegocio;
import br.com.jrfotografia.dominio.Enums.Origem;
import br.com.jrfotografia.dominio.Estudio;
import br.com.jrfotografia.dominio.PedidoOrcamento;
import br.com.jrfotografia.dominio.PortfolioFoto;
import br.com.jrfotografia.dominio.Repositorios.EstudioRepo;
import br.com.jrfotografia.dominio.Repositorios.PacoteRepo;
import br.com.jrfotografia.dominio.Repositorios.PedidoRepo;
import br.com.jrfotografia.dominio.Repositorios.PortfolioRepo;
import br.com.jrfotografia.servico.Armazenamento;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * O que o site público usa. O estúdio é identificado pelo domínio do site
 * (jrfotografiagnf.com); sem domínio cadastrado, vale o estúdio padrão.
 */
@RestController
@RequestMapping("/api/publico")
public class PublicoControle {

    private final EstudioRepo estudios;
    private final PacoteRepo pacotes;
    private final PortfolioRepo portfolio;
    private final PedidoRepo pedidos;
    private final Armazenamento armazenamento;
    private final AppProps props;

    public PublicoControle(EstudioRepo estudios, PacoteRepo pacotes, PortfolioRepo portfolio, PedidoRepo pedidos,
                           Armazenamento armazenamento, AppProps props) {
        this.estudios = estudios;
        this.pacotes = pacotes;
        this.portfolio = portfolio;
        this.pedidos = pedidos;
        this.armazenamento = armazenamento;
        this.props = props;
    }

    @GetMapping("/estudio")
    public EstudioDto estudio(HttpServletRequest req) {
        return EstudioDto.de(resolver(req), false);
    }

    @GetMapping("/pacotes")
    public List<PacoteDto> pacotes(HttpServletRequest req) {
        return pacotes.findByEstudioIdAndPublicoTrueAndAtivoTrueOrderByOrdem(resolver(req).getId())
                .stream().map(PacoteDto::de).toList();
    }

    @GetMapping("/portfolio")
    public List<PortfolioDto> portfolio(HttpServletRequest req, @RequestParam(required = false) String categoria) {
        Long e = resolver(req).getId();
        var lista = categoria == null || categoria.isBlank()
                ? portfolio.findByEstudioIdOrderByOrdemAscIdDesc(e)
                : portfolio.findByEstudioIdAndCategoriaOrderByOrdemAscIdDesc(e, categoria.toLowerCase());
        return lista.stream().map(PortfolioDto::de).toList();
    }

    @GetMapping("/portfolio/{id}/imagem")
    public ResponseEntity<Resource> imagem(HttpServletRequest req, @PathVariable Long id) {
        return Respostas.imagem(armazenamento.caminho(foto(req, id).getArquivo()), true);
    }

    @GetMapping("/portfolio/{id}/miniatura")
    public ResponseEntity<Resource> miniatura(HttpServletRequest req, @PathVariable Long id) {
        return Respostas.imagem(armazenamento.caminho(foto(req, id).getMiniatura()), true);
    }

    @PostMapping("/orcamento")
    @ResponseStatus(HttpStatus.CREATED)
    public void pedirOrcamento(HttpServletRequest req, @Valid @RequestBody PedidoForm f) {
        Estudio estudio = resolver(req);
        String telefone = CadastrosControle.soDigitos(f.telefone());
        if (telefone == null || telefone.length() < 10) throw new RegraNegocio("Informe um WhatsApp com DDD.");
        if (f.pacoteId() != null && pacotes.findByIdAndEstudioId(f.pacoteId(), estudio.getId()).isEmpty()) {
            throw new NaoEncontrado("Pacote não encontrado");
        }
        PedidoOrcamento p = new PedidoOrcamento();
        p.setEstudioId(estudio.getId());
        p.setNome(f.nome().trim());
        p.setTelefone(telefone);
        p.setEmail(f.email());
        p.setTipoTrabalho(f.tipoTrabalho());
        p.setPacoteId(f.pacoteId());
        p.setDataDesejada(f.dataDesejada());
        p.setMensagem(f.mensagem());
        p.setOrigem(f.origem() != null ? f.origem() : Origem.SITE);
        pedidos.save(p);
    }

    private PortfolioFoto foto(HttpServletRequest req, Long id) {
        return portfolio.findByIdAndEstudioId(id, resolver(req).getId())
                .orElseThrow(() -> new NaoEncontrado("Foto não encontrada"));
    }

    private Estudio resolver(HttpServletRequest req) {
        String host = req.getServerName();
        if (host != null) {
            String limpo = host.toLowerCase().replaceFirst("^www\\.", "");
            var porDominio = estudios.findByDominio(limpo);
            if (porDominio.isPresent()) return porDominio.get();
        }
        return estudios.findBySlug(props.estudioPadrao()).orElseThrow(() -> new NaoEncontrado("Estúdio não encontrado"));
    }
}
