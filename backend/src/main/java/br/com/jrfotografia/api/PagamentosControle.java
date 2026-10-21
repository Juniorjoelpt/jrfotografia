package br.com.jrfotografia.api;

import br.com.jrfotografia.config.Erros.NaoEncontrado;
import br.com.jrfotografia.config.Erros.RegraNegocio;
import br.com.jrfotografia.config.UsuarioLogado;
import br.com.jrfotografia.dominio.CobrancaPix;
import br.com.jrfotografia.dominio.Estudio;
import br.com.jrfotografia.dominio.Repositorios.CobrancaPixRepo;
import br.com.jrfotografia.dominio.Repositorios.EstudioRepo;
import br.com.jrfotografia.servico.Cifra;
import br.com.jrfotografia.servico.PagBankCliente;
import br.com.jrfotografia.servico.PagBankCliente.FalhaPagBank;
import br.com.jrfotografia.servico.PixServico;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/** Ajustes › Pagamentos (PagBank) e a notificação que o PagBank manda quando um Pix é pago. */
@RestController
public class PagamentosControle {

    private final EstudioRepo estudios;
    private final CobrancaPixRepo cobrancas;
    private final Cifra cifra;
    private final PagBankCliente pagbank;
    private final PixServico pix;

    public PagamentosControle(EstudioRepo estudios, CobrancaPixRepo cobrancas, Cifra cifra, PagBankCliente pagbank, PixServico pix) {
        this.estudios = estudios;
        this.cobrancas = cobrancas;
        this.cifra = cifra;
        this.pagbank = pagbank;
        this.pix = pix;
    }

    /** O token nunca volta: só diz se já existe um guardado. */
    public record ConfigPagBank(boolean ativo, boolean temToken, boolean sandbox, String urlNotificacao, boolean notificacaoPublica) {}

    /** {@code token} vazio mantém o guardado. */
    public record ConfigForm(boolean ativo, @Size(max = 400) String token, boolean sandbox) {}

    public record Teste(boolean ok, String mensagem) {}

    public record CobrancaDto(Long id, String descricao, String nome, BigDecimal valor, String status, boolean sandbox, String pedidoId,
                              LocalDateTime criadoEm, LocalDateTime expiraEm, LocalDateTime pagoEm, String erro, Long reservaId, Long parcelaId) {
        static CobrancaDto de(CobrancaPix c) {
            return new CobrancaDto(c.getId(), c.getDescricao(), c.getNome(), c.getValor(),
                    c.getStatus().equals(CobrancaPix.AGUARDANDO) && c.getExpiraEm().isBefore(LocalDateTime.now()) ? CobrancaPix.EXPIRADA : c.getStatus(),
                    c.isSandbox(), c.getPedidoId(), c.getCriadoEm(), c.getExpiraEm(), c.getPagoEm(), c.getErro(), c.getReservaId(), c.getParcelaId());
        }
    }

    // ================================================================ painel

    @GetMapping("/api/estudio/pagbank")
    @PreAuthorize("hasRole('ADMIN')")
    public ConfigPagBank ler() {
        return dto(estudio());
    }

    @PutMapping("/api/estudio/pagbank")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ConfigPagBank salvar(@Valid @RequestBody ConfigForm f) {
        Estudio e = estudio();
        String novo = f.token() == null ? "" : f.token().trim();
        if (!novo.isEmpty()) {
            // Confere o token antes de guardar: evita descobrir o erro só quando a cliente for pagar.
            try {
                pagbank.testarToken(novo, f.sandbox());
            } catch (FalhaPagBank ex) {
                throw new RegraNegocio(ex.getMessage());
            }
            e.setPagbankToken(cifra.cifrar(novo));
        }
        e.setPagbankSandbox(f.sandbox());
        if (f.ativo() && e.getPagbankToken() == null) throw new RegraNegocio("Para ligar, cole o token do PagBank.");
        e.setPagbankAtivo(f.ativo());
        return dto(e);
    }

    @PostMapping("/api/estudio/pagbank/teste")
    @PreAuthorize("hasRole('ADMIN')")
    public Teste testar() {
        Estudio e = estudio();
        if (e.getPagbankToken() == null) return new Teste(false, "Nenhum token guardado ainda.");
        try {
            pagbank.testarToken(cifra.decifrar(e.getPagbankToken()), e.isPagbankSandbox());
            return new Teste(true, "Conexão com o PagBank funcionando" + (e.isPagbankSandbox() ? " (ambiente de testes)." : "."));
        } catch (FalhaPagBank ex) {
            return new Teste(false, ex.getMessage());
        }
    }

    @GetMapping("/api/estudio/pagbank/cobrancas")
    @PreAuthorize("hasRole('ADMIN')")
    public List<CobrancaDto> cobrancas() {
        return cobrancas.findTop100ByEstudioIdOrderByIdDesc(UsuarioLogado.estudio()).stream().map(CobrancaDto::de).toList();
    }

    /** Consulta agora no PagBank (útil se a notificação não chegou). */
    @PostMapping("/api/estudio/pagbank/cobrancas/{id}/verificacao")
    @PreAuthorize("hasRole('ADMIN')")
    public CobrancaDto verificar(@PathVariable Long id) {
        CobrancaPix c = cobrancas.findByIdAndEstudioId(id, UsuarioLogado.estudio()).orElseThrow(() -> new NaoEncontrado("Cobrança não encontrada."));
        try {
            return CobrancaDto.de(pix.verificar(c, Duration.ZERO));
        } catch (FalhaPagBank ex) {
            throw new RegraNegocio(ex.getMessage());
        }
    }

    // ================================================================ notificação do PagBank (pública)

    /**
     * O PagBank faz POST aqui quando o pedido muda (ex.: Pix pago). O corpo é lido cru, byte a byte,
     * porque a assinatura (x-authenticity-token) é calculada sobre ele exatamente como chegou.
     */
    @PostMapping("/api/publico/pagbank/notificacao")
    public ResponseEntity<Void> notificacao(HttpServletRequest req) throws IOException {
        byte[] corpo = req.getInputStream().readNBytes(256 * 1024);
        boolean ok = pix.notificacao(corpo, req.getHeader("x-authenticity-token"));
        return ResponseEntity.status(ok ? HttpStatus.OK : HttpStatus.UNAUTHORIZED).build();
    }

    // ================================================================ apoio

    private Estudio estudio() {
        return estudios.findById(UsuarioLogado.estudio()).orElseThrow();
    }

    private ConfigPagBank dto(Estudio e) {
        String url = pix.urlNotificacao(e);
        return new ConfigPagBank(e.isPagbankAtivo(), e.getPagbankToken() != null, e.isPagbankSandbox(), url, url.startsWith("https://"));
    }
}
