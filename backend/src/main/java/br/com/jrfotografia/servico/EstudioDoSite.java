package br.com.jrfotografia.servico;

import br.com.jrfotografia.config.AppProps;
import br.com.jrfotografia.config.Erros.NaoEncontrado;
import br.com.jrfotografia.dominio.Estudio;
import br.com.jrfotografia.dominio.Repositorios.EstudioRepo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/** Descobre o estúdio pelo domínio do site (jrfotografiagnf.com); sem domínio cadastrado, vale o estúdio padrão. */
@Component
public class EstudioDoSite {

    private final EstudioRepo estudios;
    private final AppProps props;

    public EstudioDoSite(EstudioRepo estudios, AppProps props) {
        this.estudios = estudios;
        this.props = props;
    }

    public Estudio resolver(HttpServletRequest req) {
        String host = req.getServerName();
        if (host != null) {
            String limpo = host.toLowerCase().replaceFirst("^www\\.", "");
            var porDominio = estudios.findByDominio(limpo);
            if (porDominio.isPresent()) return porDominio.get();
        }
        return estudios.findBySlug(props.estudioPadrao()).orElseThrow(() -> new NaoEncontrado("Estúdio não encontrado"));
    }
}
