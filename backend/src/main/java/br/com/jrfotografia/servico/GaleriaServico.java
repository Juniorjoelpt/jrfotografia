package br.com.jrfotografia.servico;

import br.com.jrfotografia.config.Erros.NaoAutorizado;
import br.com.jrfotografia.config.Erros.NaoEncontrado;
import br.com.jrfotografia.config.Erros.Proibido;
import br.com.jrfotografia.config.Erros.RegraNegocio;
import br.com.jrfotografia.config.JwtService;
import br.com.jrfotografia.dominio.Enums.StatusTrabalho;
import br.com.jrfotografia.dominio.Enums.TipoParcela;
import br.com.jrfotografia.dominio.Foto;
import br.com.jrfotografia.dominio.Parcela;
import br.com.jrfotografia.dominio.Repositorios.FotoRepo;
import br.com.jrfotografia.dominio.Repositorios.ParcelaRepo;
import br.com.jrfotografia.dominio.Repositorios.TrabalhoRepo;
import br.com.jrfotografia.dominio.Trabalho;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A galeria do cliente: acesso por link + senha, seleção das favoritas,
 * cobrança automática de fotos extras e liberação do download.
 */
@Service
public class GaleriaServico {

    private final TrabalhoRepo trabalhos;
    private final FotoRepo fotos;
    private final ParcelaRepo parcelas;
    private final PasswordEncoder senhas;
    private final JwtService jwt;

    public GaleriaServico(TrabalhoRepo trabalhos, FotoRepo fotos, ParcelaRepo parcelas,
                          PasswordEncoder senhas, JwtService jwt) {
        this.trabalhos = trabalhos;
        this.fotos = fotos;
        this.parcelas = parcelas;
        this.senhas = senhas;
        this.jwt = jwt;
    }

    public String entrar(String token, String senha) {
        Trabalho t = trabalhos.findByGaleriaToken(token).orElseThrow(() -> new NaoEncontrado("Galeria não encontrada."));
        verificarDisponivel(t);
        if (t.getGaleriaSenhaHash() == null || senha == null || !senhas.matches(senha.trim(), t.getGaleriaSenhaHash())) {
            throw new NaoAutorizado("Senha incorreta. Confira a senha enviada pelo fotógrafo.");
        }
        return jwt.tokenGaleria(t.getId(), t.getGaleriaToken());
    }

    /** Confere o token de acesso da galeria e devolve o trabalho correspondente. */
    public Trabalho autorizar(String token, String acesso) {
        if (acesso == null) throw new NaoAutorizado("Informe a senha da galeria.");
        var claims = jwt.validar(acesso)
                .filter(c -> JwtService.TIPO_GALERIA.equals(c.get("tipo")))
                .filter(c -> token.equals(c.get("galeria")))
                .orElseThrow(() -> new NaoAutorizado("Sua sessão expirou. Entre de novo com a senha."));
        Trabalho t = trabalhos.findById(Long.valueOf(claims.getSubject()))
                .orElseThrow(() -> new NaoEncontrado("Galeria não encontrada."));
        verificarDisponivel(t);
        return t;
    }

    private void verificarDisponivel(Trabalho t) {
        if (t.getStatus() == StatusTrabalho.CANCELADO || t.getStatus() == StatusTrabalho.AGENDADO) {
            throw new NaoEncontrado("Esta galeria ainda não está disponível.");
        }
        if (t.getGaleriaExpiraEm() != null && LocalDateTime.now().isAfter(t.getGaleriaExpiraEm())) {
            throw new Proibido("Esta galeria expirou. Fale com o fotógrafo para reabrir.");
        }
    }

    public List<Foto> fotosVisiveis(Trabalho t) {
        List<Foto> todas = fotos.findByTrabalhoIdOrderByOrdemAscIdAsc(t.getId());
        // Depois que a seleção fecha, o cliente vê só as escolhidas.
        if (t.exigeSelecao() && t.getSelecaoFechadaEm() != null) {
            return todas.stream().filter(Foto::isSelecionada).toList();
        }
        return todas;
    }

    @Transactional
    public Foto marcar(Trabalho t, Long fotoId, boolean selecionada) {
        if (!t.exigeSelecao()) throw new RegraNegocio("Este pacote entrega todas as fotos, sem seleção.");
        if (t.getStatus() != StatusTrabalho.SELECAO || t.getSelecaoFechadaEm() != null) {
            throw new RegraNegocio("A seleção não está aberta.");
        }
        Foto f = fotos.findByIdAndTrabalhoId(fotoId, t.getId()).orElseThrow(() -> new NaoEncontrado("Foto não encontrada"));
        f.setSelecionada(selecionada);
        return f;
    }

    @Transactional
    public Foto comentar(Trabalho t, Long fotoId, String comentario) {
        Foto f = fotos.findByIdAndTrabalhoId(fotoId, t.getId()).orElseThrow(() -> new NaoEncontrado("Foto não encontrada"));
        String c = comentario == null ? null : comentario.trim();
        f.setComentario(c == null || c.isEmpty() ? null : c.substring(0, Math.min(500, c.length())));
        return f;
    }

    public record ResultadoSelecao(long escolhidas, long extras, BigDecimal valorExtras) {}

    /** Fecha a seleção. Fotos além do pacote viram uma parcela EXTRA (R$ 35 por foto na JR). */
    @Transactional
    public ResultadoSelecao fecharSelecao(Trabalho recebido) {
        // Recarrega dentro da transação para que status e valor sejam gravados.
        Trabalho t = trabalhos.findById(recebido.getId()).orElseThrow(() -> new NaoEncontrado("Galeria não encontrada."));
        if (!t.exigeSelecao()) throw new RegraNegocio("Este pacote entrega todas as fotos, sem seleção.");
        if (t.getStatus() != StatusTrabalho.SELECAO || t.getSelecaoFechadaEm() != null) {
            throw new RegraNegocio("A seleção não está aberta.");
        }
        long escolhidas = fotos.countByTrabalhoIdAndSelecionadaTrue(t.getId());
        if (escolhidas == 0) throw new RegraNegocio("Escolha pelo menos uma foto.");

        long extras = Math.max(0, escolhidas - t.getPacote().getQtdFotos());
        BigDecimal valor = BigDecimal.ZERO;
        BigDecimal preco = t.getPacote().getPrecoFotoExtra();
        if (extras > 0 && preco != null && preco.signum() > 0) {
            valor = preco.multiply(BigDecimal.valueOf(extras));
            Parcela p = new Parcela();
            p.setEstudioId(t.getEstudioId());
            p.setTrabalhoId(t.getId());
            p.setTipo(TipoParcela.EXTRA);
            p.setDescricao(extras + (extras == 1 ? " foto extra" : " fotos extras"));
            p.setValor(valor);
            p.setVencimento(LocalDate.now());
            parcelas.save(p);
            t.setValorTotal(t.getValorTotal().add(valor));
        }
        t.setSelecaoFechadaEm(LocalDateTime.now());
        t.setStatus(StatusTrabalho.EDICAO);
        return new ResultadoSelecao(escolhidas, extras, valor);
    }

    public RegraDownload.Situacao download(Trabalho t) {
        return RegraDownload.avaliar(t, parcelas.findByTrabalhoIdOrderByVencimentoAscIdAsc(t.getId()));
    }

    public Foto foto(Trabalho t, Long fotoId) {
        return fotosVisiveis(t).stream().filter(f -> f.getId().equals(fotoId)).findFirst()
                .orElseThrow(() -> new NaoEncontrado("Foto não encontrada"));
    }
}
