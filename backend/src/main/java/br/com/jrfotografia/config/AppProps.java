package br.com.jrfotografia.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProps(
        String estudioPadrao,
        Jwt jwt,
        Admin admin,
        Arquivos arquivos,
        Galeria galeria,
        Cors cors,
        Email email
) {
    /**
     * modo: "smtp" (padrão) envia de verdade; "memoria" guarda na memória (testes).
     * urlPublica: endereço do site usado nos links dos e-mails (ex.: https://jrfotografiagnf.com).
     */
    public record Email(String modo, String urlPublica) {}

    public record Jwt(String segredo, int horasPainel, int horasGaleria) {}

    public record Admin(String nome, String email, String senha) {}

    public record Arquivos(String pasta) {}

    public record Galeria(int validadeDias) {}

    public record Cors(List<String> origens) {}
}
