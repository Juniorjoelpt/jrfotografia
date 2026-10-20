package br.com.jrfotografia.config;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** Lê o token do painel no cabeçalho Authorization e autentica a requisição. */
@Component
public class JwtFiltro extends OncePerRequestFilter {

    private final JwtService jwt;

    public JwtFiltro(JwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        String token = header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
        // Imagens do painel são abertas por <img>, que não envia cabeçalho: aceita ?t= só em GET.
        if (token == null && "GET".equals(req.getMethod())) token = req.getParameter("t");
        if (token != null) {
            jwt.validar(token)
                    .filter(c -> JwtService.TIPO_PAINEL.equals(c.get("tipo")))
                    .ifPresent(this::autenticar);
        }
        chain.doFilter(req, res);
    }

    private void autenticar(Claims c) {
        String perfil = c.get("perfil", String.class);
        UsuarioLogado usuario = new UsuarioLogado(
                Long.valueOf(c.getSubject()),
                ((Number) c.get("estudio")).longValue(),
                perfil);
        var auth = new UsernamePasswordAuthenticationToken(usuario, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + perfil)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
