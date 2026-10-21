package br.com.jrfotografia.config;

import br.com.jrfotografia.dominio.Repositorios.UsuarioRepo;
import br.com.jrfotografia.dominio.Usuario;
import br.com.jrfotografia.servico.AuditoriaServico;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Registra no histórico toda alteração feita no painel (POST, PUT, PATCH, DELETE que deu certo),
 * com uma descrição legível de acordo com o endereço.
 */
@Configuration
public class AuditoriaInterceptor implements HandlerInterceptor, WebMvcConfigurer {

    private record Regra(String metodo, Pattern caminho, String acao, String entidade) {}

    private static Regra r(String metodo, String caminho, String acao, String entidade) {
        return new Regra(metodo, Pattern.compile("^/api" + caminho.replace("{id}", "(\\d+)").replace("{x}", "[^/]+") + "$"), acao, entidade);
    }

    /** Da mais específica para a mais geral. O primeiro (\d+) é o id da entidade. */
    private static final List<Regra> REGRAS = List.of(
            r("POST", "/trabalhos", "Criou trabalho", "TRABALHO"),
            r("PUT", "/trabalhos/{id}", "Editou trabalho (data, local, duração)", "TRABALHO"),
            r("PATCH", "/trabalhos/{id}/status", "Mudou a etapa do trabalho", "TRABALHO"),
            r("POST", "/trabalhos/{id}/fotos", "Enviou fotos", "TRABALHO"),
            r("POST", "/trabalhos/{id}/editadas", "Enviou fotos editadas", "TRABALHO"),
            r("DELETE", "/trabalhos/{id}/fotos/{x}", "Apagou foto", "TRABALHO"),
            r("POST", "/trabalhos/{id}/senha-galeria", "Gerou nova senha da galeria", "TRABALHO"),
            r("POST", "/trabalhos/{id}/reabrir-selecao", "Reabriu a seleção", "TRABALHO"),
            r("POST", "/trabalhos/{id}/assinatura", "Gerou link de assinatura do contrato", "TRABALHO"),
            r("DELETE", "/trabalhos/{id}/assinatura", "Cancelou link de assinatura", "TRABALHO"),
            r("POST", "/trabalhos/{id}/freelancers", "Lançou comissão de freelancer", "TRABALHO"),
            r("POST", "/trabalhos/{id}/pesquisa", "Abriu pesquisa de satisfação", "TRABALHO"),
            r("POST", "/parcelas/{id}/pagamento", "Registrou pagamento", "PARCELA"),
            r("DELETE", "/parcelas/{id}/pagamento", "Desfez pagamento", "PARCELA"),
            r("POST", "/clientes", "Cadastrou cliente", "CLIENTE"),
            r("PUT", "/clientes/{id}", "Editou cliente", "CLIENTE"),
            r("POST", "/clientes/{id}/datas", "Adicionou data especial", "CLIENTE"),
            r("DELETE", "/clientes/{id}/datas/{x}", "Apagou data especial", "CLIENTE"),
            r("POST", "/clientes/{id}/contato", "Registrou convite a cliente", "CLIENTE"),
            r("POST", "/orcamentos", "Criou orçamento", "ORCAMENTO"),
            r("PUT", "/orcamentos/{id}", "Editou orçamento", "ORCAMENTO"),
            r("POST", "/orcamentos/{id}/{x}", "Atualizou orçamento", "ORCAMENTO"),
            r("PATCH", "/pedidos/{id}", "Atualizou pedido de orçamento", "PEDIDO"),
            r("POST", "/reservas/{id}/confirmacao", "Confirmou pré-reserva do site", "RESERVA"),
            r("POST", "/reservas/{id}/recusa", "Recusou pré-reserva do site", "RESERVA"),
            r("POST", "/agenda/bloqueios", "Bloqueou data na agenda", "AGENDA"),
            r("DELETE", "/agenda/bloqueios/{id}", "Liberou data bloqueada", "AGENDA"),
            r("POST", "/despesas", "Lançou despesa", "DESPESA"),
            r("PUT", "/despesas/{id}", "Editou despesa", "DESPESA"),
            r("DELETE", "/despesas/{id}", "Apagou despesa", "DESPESA"),
            r("POST", "/despesas/{id}/{x}", "Atualizou despesa", "DESPESA"),
            r("POST", "/comissoes/{id}/pagamento", "Pagou comissão de freelancer", "COMISSAO"),
            r("DELETE", "/comissoes/{id}/pagamento", "Desfez pagamento de comissão", "COMISSAO"),
            r("DELETE", "/comissoes/{id}", "Removeu comissão de freelancer", "COMISSAO"),
            r("POST", "/freelancers", "Cadastrou freelancer", "FREELANCER"),
            r("PUT", "/freelancers/{id}", "Editou freelancer", "FREELANCER"),
            r("POST", "/tarefas", "Criou tarefa", "TAREFA"),
            r("PUT", "/tarefas/{id}", "Editou tarefa", "TAREFA"),
            r("PATCH", "/tarefas/{id}/status", "Mudou status da tarefa", "TAREFA"),
            r("DELETE", "/tarefas/{id}", "Apagou tarefa", "TAREFA"),
            r("POST", "/produtos/pedidos", "Criou pedido de produto", "PEDIDO_PRODUTO"),
            r("PUT", "/produtos/pedidos/{id}", "Editou pedido de produto", "PEDIDO_PRODUTO"),
            r("PATCH", "/produtos/pedidos/{id}/status", "Mudou etapa do pedido de produto", "PEDIDO_PRODUTO"),
            r("POST", "/produtos/pedidos/{id}/laminas", "Enviou lâminas do álbum", "PEDIDO_PRODUTO"),
            r("DELETE", "/produtos/pedidos/{id}/laminas/{x}", "Tirou lâmina do álbum", "PEDIDO_PRODUTO"),
            r("POST", "/estoque/materiais", "Cadastrou material", "MATERIAL"),
            r("PUT", "/estoque/materiais/{id}", "Editou material", "MATERIAL"),
            r("POST", "/estoque/movimentos", "Movimentou estoque", "MATERIAL"),
            r("POST", "/campanhas", "Criou mini-sessão", "CAMPANHA"),
            r("PUT", "/campanhas/{id}", "Editou mini-sessão", "CAMPANHA"),
            r("POST", "/campanhas/{id}/capa", "Trocou capa da mini-sessão", "CAMPANHA"),
            r("POST", "/cupons", "Criou cupom", "CUPOM"),
            r("PUT", "/cupons/{id}", "Editou cupom", "CUPOM"),
            r("PATCH", "/indicacoes/{id}", "Atualizou indicação", "INDICACAO"),
            r("POST", "/usuarios", "Cadastrou usuário do painel", "USUARIO"),
            r("PUT", "/usuarios/{id}", "Editou usuário do painel", "USUARIO"),
            r("POST", "/usuarios/{id}/2fa/remover", "Removeu verificação em duas etapas de outro usuário", "USUARIO"),
            r("PUT", "/conta/senha", "Trocou a própria senha", "USUARIO"),
            r("POST", "/conta/2fa/ativar", "Ligou a verificação em duas etapas", "USUARIO"),
            r("POST", "/conta/2fa/desativar", "Desligou a verificação em duas etapas", "USUARIO"),
            r("POST", "/conta/2fa/codigos", "Gerou novos códigos de recuperação", "USUARIO"),
            r("PUT", "/estudio/agenda-online", "Alterou a agenda online", "ESTUDIO"),
            r("PUT", "/estudio/contrato", "Alterou o modelo de contrato", "ESTUDIO"),
            r("PUT", "/estudio/assinatura", "Alterou a assinatura do estúdio", "ESTUDIO"),
            r("PUT", "/estudio/indicacao", "Alterou o programa de indicação", "ESTUDIO"),
            r("PUT", "/estudio", "Alterou os dados do estúdio", "ESTUDIO"),
            r("POST", "/pacotes", "Criou pacote", "PACOTE"),
            r("PUT", "/pacotes/{id}", "Editou pacote", "PACOTE"),
            r("POST", "/portfolio", "Enviou foto do portfólio", "PORTFOLIO"),
            r("DELETE", "/portfolio/{id}", "Apagou foto do portfólio", "PORTFOLIO")
    );

    /** Endereços que não interessam no histórico (ex.: iniciar a configuração, sem efeito ainda). */
    private static final List<Pattern> IGNORAR = List.of(Pattern.compile("^/api/conta/2fa/iniciar$"), Pattern.compile("^/api/auth/.*"));

    private final AuditoriaServico auditoria;
    private final UsuarioRepo usuarios;

    public AuditoriaInterceptor(@Lazy AuditoriaServico auditoria, @Lazy UsuarioRepo usuarios) {
        this.auditoria = auditoria;
        this.usuarios = usuarios;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/**");
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse res, Object handler, Exception ex) {
        String m = req.getMethod();
        if ("GET".equals(m) || "HEAD".equals(m) || "OPTIONS".equals(m) || ex != null || res.getStatus() >= 400) return;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UsuarioLogado u)) return;
        String caminho = req.getRequestURI();
        if (IGNORAR.stream().anyMatch(p -> p.matcher(caminho).matches())) return;
        String acao = null, entidade = null;
        Long entidadeId = null;
        for (Regra regra : REGRAS) {
            if (!regra.metodo().equals(m)) continue;
            Matcher mt = regra.caminho().matcher(caminho);
            if (mt.matches()) {
                acao = regra.acao();
                entidade = regra.entidade();
                if (mt.groupCount() >= 1) entidadeId = Long.valueOf(mt.group(1));
                break;
            }
        }
        if (acao == null) acao = "Alteração em " + caminho.replaceFirst("^/api/", "").split("/")[0];
        Object detalhe = req.getAttribute(AuditoriaServico.DETALHE);
        String nome = usuarios.findById(u.usuarioId()).map(Usuario::getNome).orElse(null);
        auditoria.registrar(new AuditoriaServico.Registro(u.estudioId(), u.usuarioId(), nome, acao, detalhe != null ? detalhe.toString() : null,
                m, caminho, entidade, entidadeId, Tentativas.aparelho(req)));
    }
}
