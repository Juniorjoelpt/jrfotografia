package br.com.jrfotografia.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Quem está usando o painel: vem do token e define o estúdio de todas as consultas. */
public record UsuarioLogado(Long usuarioId, Long estudioId, String perfil) {

    public boolean admin() {
        return "ADMIN".equals(perfil);
    }

    public static UsuarioLogado atual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioLogado u) return u;
        throw new IllegalStateException("Nenhum usuário autenticado");
    }

    public static Long estudio() {
        return atual().estudioId();
    }
}
