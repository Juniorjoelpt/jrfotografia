package br.com.jrfotografia.servico;

import br.com.jrfotografia.dominio.Auditoria;
import br.com.jrfotografia.dominio.Repositorios.AuditoriaRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/** Grava o registro de atividades. Falhar aqui nunca derruba a operação principal. */
@Service
public class AuditoriaServico {

    /** Atributo da requisição com um detalhe que o controlador quer no registro (ex.: "para Entregue"). */
    public static final String DETALHE = "auditoria.detalhe";

    private final AuditoriaRepo repo;

    public AuditoriaServico(AuditoriaRepo repo) {
        this.repo = repo;
    }

    public record Registro(Long estudioId, Long usuarioId, String usuario, String acao, String detalhe, String metodo, String caminho,
                           String entidade, Long entidadeId, String ip) {}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(Registro r) {
        try {
            Auditoria a = new Auditoria();
            a.setEstudioId(r.estudioId());
            a.setUsuarioId(r.usuarioId());
            a.setUsuario(corta(r.usuario(), 120));
            a.setAcao(corta(r.acao(), 120));
            a.setDetalhe(corta(r.detalhe(), 300));
            a.setMetodo(r.metodo());
            a.setCaminho(corta(r.caminho(), 200));
            a.setEntidade(r.entidade());
            a.setEntidadeId(r.entidadeId());
            a.setIp(corta(r.ip(), 64));
            repo.save(a);
        } catch (RuntimeException ignorado) {
            // registro é complementar: não atrapalha quem está trabalhando
        }
    }

    /** Controladores chamam para acrescentar um detalhe legível ao registro desta requisição. */
    public static void detalhe(String texto) {
        RequestAttributes atr = RequestContextHolder.getRequestAttributes();
        if (atr != null) atr.setAttribute(DETALHE, texto, RequestAttributes.SCOPE_REQUEST);
    }

    private static String corta(String s, int max) {
        return s == null ? null : s.length() > max ? s.substring(0, max) : s;
    }
}
