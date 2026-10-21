package br.com.jrfotografia.servico;

import org.springframework.web.util.HtmlUtils;

import java.util.List;

/**
 * Layout dos e-mails: faixa carvão com o nome do estúdio, fio vermelho, texto escuro sobre branco e um botão.
 * HTML simples com estilos em linha (é o que Gmail, Outlook e celulares mostram direito).
 */
public final class EmailModelos {

    private EmailModelos() {}

    public record Botao(String texto, String url) {}

    /** Linha "Rótulo: valor" num quadro de resumo. */
    public record Linha(String rotulo, String valor) {}

    public static String esc(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s, "UTF-8");
    }

    /** Monta o e-mail. {@code paragrafos} já vêm escapados ou com HTML simples (negrito). */
    public static String layout(String estudio, String titulo, List<String> paragrafos, List<Linha> resumo, Botao botao, String rodape) {
        StringBuilder corpo = new StringBuilder();
        for (String p : paragrafos) {
            if (p == null || p.isBlank()) continue;
            corpo.append("<p style=\"margin:0 0 14px;font-size:15px;line-height:23px;color:#2b2a27\">").append(p).append("</p>");
        }
        if (resumo != null && !resumo.isEmpty()) {
            corpo.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:6px 0 18px;border-collapse:collapse;background:#f6f5f1;border-radius:6px\">");
            for (Linha l : resumo) {
                corpo.append("<tr><td style=\"padding:9px 14px;font-size:12px;letter-spacing:.06em;text-transform:uppercase;color:#6b6962;border-bottom:1px solid #e6e3dc\">")
                        .append(esc(l.rotulo())).append("</td><td style=\"padding:9px 14px;font-size:15px;color:#171716;text-align:right;border-bottom:1px solid #e6e3dc\"><b>")
                        .append(esc(l.valor())).append("</b></td></tr>");
            }
            corpo.append("</table>");
        }
        if (botao != null) {
            corpo.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:8px 0 18px\"><tr><td style=\"background:#c62828;border-radius:6px\">")
                    .append("<a href=\"").append(esc(botao.url())).append("\" style=\"display:inline-block;padding:13px 24px;font-size:15px;font-weight:bold;color:#ffffff;text-decoration:none\">")
                    .append(esc(botao.texto())).append("</a></td></tr></table>")
                    .append("<p style=\"margin:0 0 14px;font-size:12px;line-height:18px;color:#8a877f\">Se o botão não abrir, copie o endereço: <br><span style=\"color:#5e5c56;word-break:break-all\">")
                    .append(esc(botao.url())).append("</span></p>");
        }
        return """
                <!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>%s</title></head>
                <body style="margin:0;padding:0;background:#efede8;font-family:Helvetica,Arial,sans-serif">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#efede8;padding:24px 12px"><tr><td align="center">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:560px;background:#ffffff;border-radius:8px;overflow:hidden">
                <tr><td style="background:#171716;padding:22px 28px;border-bottom:3px solid #c62828">
                  <div style="font-size:11px;letter-spacing:.32em;color:#a3a19a;text-transform:uppercase">Fotografia</div>
                  <div style="font-size:20px;font-weight:bold;color:#ffffff;margin-top:2px">%s</div>
                </td></tr>
                <tr><td style="padding:28px 28px 10px">
                  <h1 style="margin:0 0 16px;font-size:21px;line-height:28px;color:#171716">%s</h1>
                  %s
                </td></tr>
                <tr><td style="padding:14px 28px 24px;border-top:1px solid #eeebe4;font-size:12px;line-height:18px;color:#8a877f">%s</td></tr>
                </table></td></tr></table></body></html>
                """.formatted(esc(titulo), esc(estudio), esc(titulo), corpo, rodape == null ? esc(estudio) : rodape);
    }

    /** Versão só texto (para quem não abre HTML e para os filtros de spam). */
    public static String textoSimples(String html) {
        String t = html.replaceAll("(?is)<head.*?</head>", "")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(p|tr|h1|div)>", "\n")
                .replaceAll("(?i)<a [^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", "$2: $1")
                .replaceAll("<[^>]+>", " ");
        t = HtmlUtils.htmlUnescape(t).replaceAll("[ \\t]+", " ").replaceAll(" *\n *", "\n").replaceAll("\n{3,}", "\n\n");
        return t.trim();
    }
}
