package br.com.jrfotografia.api;

import br.com.jrfotografia.config.Erros.RegraNegocio;
import br.com.jrfotografia.config.UsuarioLogado;
import br.com.jrfotografia.dominio.Estudio;
import br.com.jrfotografia.dominio.Repositorios.EstudioRepo;
import br.com.jrfotografia.servico.Cifra;
import br.com.jrfotografia.servico.ConteudoServico;
import br.com.jrfotografia.servico.ConteudoServico.Opcoes;
import br.com.jrfotografia.servico.ConteudoServico.Resultado;
import br.com.jrfotografia.servico.IaCliente;
import br.com.jrfotografia.servico.IaCliente.Conexao;
import br.com.jrfotografia.servico.IaCliente.FalhaIa;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static br.com.jrfotografia.servico.Textos.vazio;

/** Ajustes › Assistente de conteúdo e Marketing › Conteúdo (ideias de post para as redes). */
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class ConteudoControle {

    private final EstudioRepo estudios;
    private final Cifra cifra;
    private final IaCliente ia;
    private final ConteudoServico conteudo;

    public ConteudoControle(EstudioRepo estudios, Cifra cifra, IaCliente ia, ConteudoServico conteudo) {
        this.estudios = estudios;
        this.cifra = cifra;
        this.ia = ia;
        this.conteudo = conteudo;
    }

    /** A chave nunca volta: só diz se existe uma guardada. */
    public record ConfigIa(boolean ativo, String url, String modelo, boolean temChave) {}

    /** {@code chave} null ou vazia mantém a guardada; {@code apagarChave} remove (Ollama local não precisa). */
    public record ConfigIaForm(boolean ativo, @Size(max = 300) String url, @Size(max = 120) String modelo,
                               @Size(max = 600) String chave, boolean apagarChave) {}

    public record Teste(boolean ok, String mensagem) {}

    public record PedidoIdeias(@NotBlank String tipo, Long referencia, @Size(max = 600) String pedido) {}

    // ================================================================ configuração

    @GetMapping("/api/estudio/ia")
    public ConfigIa ler() {
        return dto(estudio());
    }

    @PutMapping("/api/estudio/ia")
    @Transactional
    public ConfigIa salvar(@Valid @RequestBody ConfigIaForm f) {
        Estudio e = estudio();
        String url = f.url() == null ? "" : f.url().trim();
        if (!url.isEmpty() && !url.matches("(?i)https?://\\S+")) throw new RegraNegocio("O endereço precisa começar com http:// ou https://");
        e.setIaUrl(url.isEmpty() ? null : url);
        e.setIaModelo(vazio(f.modelo()) ? null : f.modelo().trim());
        if (f.apagarChave()) e.setIaChave(null);
        else if (!vazio(f.chave())) e.setIaChave(cifra.cifrar(f.chave().trim()));
        if (f.ativo() && (e.getIaUrl() == null || e.getIaModelo() == null)) throw new RegraNegocio("Para ligar, informe o endereço e o modelo.");
        e.setIaAtivo(f.ativo());
        return dto(e);
    }

    @PostMapping("/api/estudio/ia/teste")
    public Teste testar() {
        Estudio e = estudio();
        if (vazio(e.getIaUrl()) || vazio(e.getIaModelo())) return new Teste(false, "Informe o endereço e o modelo e salve antes de testar.");
        try {
            String r = ia.testar(new Conexao(e.getIaUrl(), e.getIaChave() == null ? null : cifra.decifrar(e.getIaChave()), e.getIaModelo()));
            return new Teste(true, "Funcionando. O assistente respondeu: “" + (r.length() > 160 ? r.substring(0, 160) + "…" : r.trim()) + "”");
        } catch (FalhaIa ex) {
            return new Teste(false, ex.getMessage());
        }
    }

    // ================================================================ ideias

    @GetMapping("/api/conteudo/opcoes")
    public Opcoes opcoes() {
        return conteudo.opcoes(UsuarioLogado.estudio());
    }

    @PostMapping("/api/conteudo/ideias")
    @ResponseStatus(HttpStatus.CREATED)
    public Resultado gerar(@Valid @RequestBody PedidoIdeias p) {
        try {
            return conteudo.gerar(UsuarioLogado.estudio(), p.tipo(), p.referencia(), p.pedido());
        } catch (FalhaIa ex) {
            throw new RegraNegocio(ex.getMessage());
        }
    }

    @GetMapping("/api/conteudo/ideias")
    public List<Resultado> historico() {
        return conteudo.historico(UsuarioLogado.estudio());
    }

    @DeleteMapping("/api/conteudo/ideias/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void apagar(@PathVariable Long id) {
        conteudo.apagar(UsuarioLogado.estudio(), id);
    }

    private Estudio estudio() {
        return estudios.findById(UsuarioLogado.estudio()).orElseThrow();
    }

    private static ConfigIa dto(Estudio e) {
        return new ConfigIa(e.isIaAtivo(), e.getIaUrl(), e.getIaModelo(), e.getIaChave() != null);
    }
}
