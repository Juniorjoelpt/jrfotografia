package br.com.jrfotografia.servico;

import br.com.jrfotografia.config.AppProps;
import br.com.jrfotografia.dominio.*;
import br.com.jrfotografia.dominio.Enums.FormaPagamento;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * PDFs com a identidade JR: faixa carvão com o monograma, fio vermelho, Helvetica
 * (a mesma família do orçamento atual) e muito espaço em branco.
 */
@Service
public class PdfServico {

    static final Color CARVAO = new Color(0x17, 0x17, 0x16);
    static final Color TINTA_SUAVE = new Color(0x5E, 0x5C, 0x56);
    static final Color LINHA = new Color(0xDC, 0xD9, 0xD2);
    static final Color ACENTO = new Color(0xC6, 0x28, 0x28);
    static final Locale BR = Locale.forLanguageTag("pt-BR");
    static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");
    static final DateTimeFormatter DATA_LONGA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", BR);

    private static final Font TITULO = new Font(Font.HELVETICA, 20, Font.BOLD, CARVAO);
    private static final Font CHAMADA = new Font(Font.HELVETICA, 10, Font.BOLD, CARVAO);
    private static final Font TEXTO = new Font(Font.HELVETICA, 10, Font.NORMAL, CARVAO);
    private static final Font SUAVE = new Font(Font.HELVETICA, 8.5f, Font.NORMAL, TINTA_SUAVE);
    private static final Font TOTAL = new Font(Font.HELVETICA, 16, Font.BOLD, CARVAO);

    private final AppProps props;

    public PdfServico(AppProps props) {
        this.props = props;
    }

    // ------------------------------------------------------------------ orçamento
    public byte[] orcamento(Orcamento o, Estudio e) {
        return gerar(e, "ORÇAMENTO", "Nº " + o.getNumero(), doc -> {
            doc.add(blocoPartes(
                    new String[][]{{"CLIENTE", o.getNomeCliente()}, {"WHATSAPP", fone(o.getTelefone())}},
                    new String[][]{{"EMITIDO EM", DATA.format(o.getCriadoEm())}, {"VÁLIDO ATÉ", DATA.format(o.getValidade())}}));

            if (o.getTipo() != null || o.getDataPrevista() != null || o.getLocalPrevisto() != null) {
                doc.add(rotulo("ENSAIO"));
                doc.add(new Paragraph(junta(o.getTipo(), o.getDataPrevista() != null ? DATA_HORA.format(o.getDataPrevista()) : null,
                        o.getLocalPrevisto()), TEXTO));
            }
            if (o.getPacote() != null && !o.getPacote().listaItens().isEmpty()) {
                doc.add(rotulo("INCLUSO NO PACOTE"));
                for (String it : o.getPacote().listaItens()) doc.add(new Paragraph("·  " + it, TEXTO));
                doc.add(new Paragraph("Entrega em " + o.getPacote().getPrazoEntregaDias() + " dias úteis"
                        + (o.getPacote().getPrecoFotoExtra() != null ? " · foto extra " + moeda(o.getPacote().getPrecoFotoExtra()) : ""), SUAVE));
            }

            doc.add(rotulo("INVESTIMENTO"));
            PdfPTable t = new PdfPTable(new float[]{6f, 1.2f, 2f, 2f});
            t.setWidthPercentage(100);
            cabecalho(t, "Descrição", "Qtd.", "Valor", "Total");
            for (OrcamentoItem i : o.getItens()) {
                celula(t, i.getDescricao(), Element.ALIGN_LEFT);
                celula(t, String.valueOf(i.getQuantidade()), Element.ALIGN_CENTER);
                celula(t, moeda(i.getValorUnit()), Element.ALIGN_RIGHT);
                celula(t, moeda(i.total()), Element.ALIGN_RIGHT);
            }
            doc.add(t);
            PdfPTable totais = new PdfPTable(new float[]{7f, 3f});
            totais.setWidthPercentage(100);
            if (o.getDesconto() != null && o.getDesconto().signum() > 0) {
                linhaTotal(totais, "Subtotal", moeda(o.subtotal()), TEXTO);
                linhaTotal(totais, "Desconto", "- " + moeda(o.getDesconto()), TEXTO);
            }
            linhaTotal(totais, "TOTAL", moeda(o.getValorTotal()), TOTAL);
            doc.add(totais);

            doc.add(rotulo("FORMA DE PAGAMENTO"));
            doc.add(new Paragraph(condicoes(o.getFormaPagamento(), o.getValorTotal(), o.getValorSinal(), o.getNumParcelas()), TEXTO));
            if (o.getObservacoes() != null && !o.getObservacoes().isBlank()) {
                doc.add(rotulo("OBSERVAÇÕES"));
                doc.add(new Paragraph(o.getObservacoes(), TEXTO));
            }
            Paragraph aceite = new Paragraph("Para aceitar, use o link enviado pelo WhatsApp. A data só fica reservada após o aceite"
                    + (o.getFormaPagamento() == FormaPagamento.PARCELADO && o.getValorSinal() != null ? " e o pagamento do sinal." : "."), SUAVE);
            aceite.setSpacingBefore(18);
            doc.add(aceite);
        });
    }

    // ------------------------------------------------------------------ contrato
    public byte[] contrato(Trabalho t, Estudio e, List<Parcela> parcelas) {
        String modelo = e.getModeloContrato() != null && !e.getModeloContrato().isBlank() ? e.getModeloContrato() : ContratoPadrao.TEXTO;
        String texto = preencher(modelo, campos(t, e, parcelas));
        return gerar(e, "CONTRATO", "Prestação de serviços fotográficos", doc -> {
            for (String bloco : texto.split("\\n\\s*\\n")) {
                String b = bloco.strip();
                if (b.isEmpty()) continue;
                boolean titulo = b.matches("^\\d+\\.\\s+[A-ZÁÉÍÓÚÂÊÔÃÕÇ ,/]+$") || b.matches("^\\d+\\.\\s+[A-ZÁÉÍÓÚÂÊÔÃÕÇ ,/]+\\n[\\s\\S]*");
                if (titulo) {
                    // Título e texto da cláusula juntos, para o título nunca ficar sozinho no pé da página.
                    String[] partes = b.split("\\n", 2);
                    Paragraph h = new Paragraph();
                    h.setAlignment(Element.ALIGN_JUSTIFIED);
                    h.setLeading(14.5f);
                    h.setSpacingBefore(10);
                    h.setSpacingAfter(4);
                    h.setKeepTogether(true);
                    h.add(new Chunk(partes[0] + (partes.length > 1 ? "\n" : ""), CHAMADA));
                    if (partes.length > 1) h.add(new Chunk(partes[1].strip(), TEXTO));
                    doc.add(h);
                } else {
                    Paragraph p = justificado(b);
                    p.setSpacingBefore(8);
                    doc.add(p);
                }
            }
            PdfPTable ass = new PdfPTable(2);
            ass.setWidthPercentage(100);
            ass.setSpacingBefore(48);
            assinatura(ass, t.getCliente().getNome(), "CONTRATANTE");
            assinatura(ass, e.getResponsavel() != null ? e.getResponsavel() : e.getNome(), "CONTRATADO");
            doc.add(ass);
        });
    }

    Map<String, String> campos(Trabalho t, Estudio e, List<Parcela> parcelas) {
        Pacote p = t.getPacote();
        BigDecimal sinal = parcelas.stream().filter(x -> x.getTipo() == Enums.TipoParcela.SINAL).map(Parcela::getValor).findFirst().orElse(null);
        long n = parcelas.stream().filter(x -> x.getTipo() == Enums.TipoParcela.PARCELA).count();
        return Map.ofEntries(
                Map.entry("CONTRATANTE", t.getCliente().getNome()),
                Map.entry("CPF", vazio(t.getCliente().getCpf(), "________________")),
                Map.entry("TELEFONE", vazio(fone(t.getCliente().getTelefone()), "________________")),
                Map.entry("CONTRATADO", e.getNome()),
                Map.entry("RESPONSAVEL", vazio(e.getResponsavel(), "________________")),
                Map.entry("DOCUMENTO_CONTRATADO", e.getDocumento() != null && !e.getDocumento().isBlank() ? "documento " + e.getDocumento() : "documento ________________"),
                Map.entry("CIDADE", vazio(e.getCidade(), "________________")),
                Map.entry("SERVICO", "fotografia" + (t.getTipo() != null ? " (" + t.getTipo().toLowerCase(BR) + ")" : "") + (p != null ? ", pacote " + p.getNome() : "")),
                Map.entry("DATA_ENSAIO", t.getDataTrabalho() != null ? DATA_HORA.format(t.getDataTrabalho()) : "a combinar"),
                Map.entry("LOCAL", vazio(t.getLocalTrabalho(), "local a combinar")),
                Map.entry("MATERIAL", p != null && p.getMaterialEntregue() != null ? p.getMaterialEntregue().toLowerCase(BR) : "fotos digitais editadas"),
                Map.entry("PRAZO_ENTREGA", p != null ? String.valueOf(p.getPrazoEntregaDias()) : "15"),
                Map.entry("FOTO_EXTRA", p != null && p.getPrecoFotoExtra() != null ? moeda(p.getPrecoFotoExtra()) : "o valor combinado"),
                Map.entry("VALOR_TOTAL", moeda(t.getValorTotal())),
                Map.entry("VALOR_EXTENSO", Extenso.reais(t.getValorTotal())),
                Map.entry("CONDICOES_PAGAMENTO", condicoes(t.getFormaPagamento(), t.getValorTotal(), sinal, n > 0 ? (int) n : null)),
                Map.entry("VALIDADE_GALERIA", String.valueOf(props.galeria().validadeDias())),
                Map.entry("DATA_HOJE", DATA_LONGA.format(LocalDate.now())));
    }

    static String preencher(String modelo, Map<String, String> campos) {
        String s = modelo;
        for (var c : campos.entrySet()) s = s.replace("{" + c.getKey() + "}", c.getValue());
        return s;
    }

    // ------------------------------------------------------------------ recibo
    public byte[] recibo(Parcela p, Trabalho t, Estudio e) {
        return gerar(e, "RECIBO", moeda(p.getValor()), doc -> {
            Paragraph corpo = justificado("Recebi de " + t.getCliente().getNome() + " a importância de " + moeda(p.getValor())
                    + " (" + Extenso.reais(p.getValor()) + "), referente a " + p.getDescricao().toLowerCase(BR) + " do serviço de fotografia "
                    + (t.getPacote() != null ? "(pacote " + t.getPacote().getNome() + ")" : "")
                    + (t.getDataTrabalho() != null ? ", realizado em " + DATA.format(t.getDataTrabalho()) : "") + ".");
            corpo.setSpacingBefore(18);
            doc.add(corpo);
            doc.add(blocoPartes(
                    new String[][]{{"PAGO EM", p.getPagoEm() != null ? DATA.format(p.getPagoEm()) : "—"}, {"FORMA", p.getForma() != null ? nomeMeio(p.getForma()) : "—"}},
                    new String[][]{{"RECIBO Nº", String.format("%05d", p.getId())}, {"TRABALHO", t.getTitulo()}}));
            Paragraph local = new Paragraph(vazio(e.getCidade(), "") + (e.getCidade() != null ? ", " : "") + DATA_LONGA.format(p.getPagoEm() != null ? p.getPagoEm() : LocalDate.now()) + ".", TEXTO);
            local.setSpacingBefore(24);
            doc.add(local);
            PdfPTable ass = new PdfPTable(1);
            ass.setWidthPercentage(50);
            ass.setHorizontalAlignment(Element.ALIGN_LEFT);
            ass.setSpacingBefore(48);
            assinatura(ass, e.getResponsavel() != null ? e.getResponsavel() : e.getNome(), e.getNome().toUpperCase(BR));
            doc.add(ass);
        });
    }

    // ------------------------------------------------------------------ estrutura comum
    private interface Corpo { void escrever(Document doc) throws DocumentException; }

    private byte[] gerar(Estudio e, String titulo, String subtitulo, Corpo corpo) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 48, 48, 132, 64);
        PdfWriter w = PdfWriter.getInstance(doc, out);
        w.setPageEvent(new Moldura(e, titulo, subtitulo));
        doc.addTitle(titulo + " — " + e.getNome());
        doc.addAuthor(e.getNome());
        doc.open();
        try {
            corpo.escrever(doc);
        } catch (DocumentException ex) {
            throw new IllegalStateException("Falha ao gerar PDF", ex);
        }
        doc.close();
        return out.toByteArray();
    }

    /** Faixa carvão com monograma e título no topo; contatos e página no rodapé. */
    private static final class Moldura extends PdfPageEventHelper {
        private final Estudio e;
        private final String titulo;
        private final String subtitulo;

        Moldura(Estudio e, String titulo, String subtitulo) {
            this.e = e;
            this.titulo = titulo;
            this.subtitulo = subtitulo;
        }

        @Override
        public void onEndPage(PdfWriter w, Document d) {
            PdfContentByte cb = w.getDirectContent();
            float largura = d.getPageSize().getWidth(), altura = d.getPageSize().getHeight();
            float faixa = 96;
            cb.saveState();
            cb.setColorFill(CARVAO);
            cb.rectangle(0, altura - faixa, largura, faixa);
            cb.fill();
            cb.setColorFill(ACENTO);
            cb.rectangle(0, altura - faixa - 3, largura, 3);
            cb.fill();
            monograma(cb, 48, altura - faixa + 26, 52);
            texto(cb, "F O T O G R A F I A", BaseFont.HELVETICA, 6.5f, 48, altura - faixa + 14, Color.WHITE, Element.ALIGN_LEFT);
            texto(cb, titulo, BaseFont.HELVETICA_BOLD, 20, largura - 48, altura - 52, Color.WHITE, Element.ALIGN_RIGHT);
            texto(cb, subtitulo, BaseFont.HELVETICA, 10, largura - 48, altura - 70, new Color(0xA3, 0xA1, 0x9A), Element.ALIGN_RIGHT);

            String contato = String.join("   ·   ", java.util.stream.Stream.of(
                    e.getNome(), e.getCidade(), e.getWhatsapp() != null ? "WhatsApp " + fone(e.getWhatsapp()) : null,
                    e.getInstagram() != null ? "@" + e.getInstagram() : null, e.getDominio()).filter(x -> x != null && !x.isBlank()).toList());
            cb.setColorStroke(LINHA);
            cb.setLineWidth(.5f);
            cb.moveTo(48, 44);
            cb.lineTo(largura - 48, 44);
            cb.stroke();
            texto(cb, contato, BaseFont.HELVETICA, 7.5f, 48, 30, TINTA_SUAVE, Element.ALIGN_LEFT);
            texto(cb, String.valueOf(w.getPageNumber()), BaseFont.HELVETICA, 7.5f, largura - 48, 30, TINTA_SUAVE, Element.ALIGN_RIGHT);
            cb.restoreState();
        }

        /** O monograma JR, com a mesma geometria do SVG da marca, em branco. */
        private static void monograma(PdfContentByte cb, float x, float y, float alturaMarca) {
            float s = alturaMarca / 340f;
            cb.saveState();
            cb.setColorStroke(Color.WHITE);
            cb.setLineWidth(9 * s);
            // Converte coordenadas do SVG (y para baixo) para o PDF (y para cima), caixa 60,20 → 470,360.
            java.util.function.BiFunction<Float, Float, float[]> p = (px, py) -> new float[]{x + (px - 60) * s, y + (360 - py) * s};
            float[] a = p.apply(150f, 40f), b = p.apply(240f, 40f), c = p.apply(240f, 270f);
            cb.moveTo(a[0], a[1]);
            cb.lineTo(b[0], b[1]);
            cb.lineTo(c[0], c[1]);
            // Curva do J: arco de raio 87,5 com centro em (152,5; 270), de 0° a 126,87° (no sentido do SVG).
            float[] centro = p.apply(152.5f, 270f);
            arco(cb, centro[0], centro[1], 87.5f * s, 0, -126.87f);
            float[] r1 = p.apply(266f, 272f), r2 = p.apply(266f, 40f), r3 = p.apply(322f, 40f);
            cb.moveTo(r1[0], r1[1]);
            cb.lineTo(r2[0], r2[1]);
            cb.lineTo(r3[0], r3[1]);
            float[] cBojo = p.apply(322f, 84f);
            arco(cb, cBojo[0], cBojo[1], 44 * s, 90, -180);
            float[] r4 = p.apply(266f, 128f);
            cb.lineTo(r4[0], r4[1]);
            float[] l1 = p.apply(318f, 128f), l2 = p.apply(440f, 272f);
            cb.moveTo(l1[0], l1[1]);
            cb.lineTo(l2[0], l2[1]);
            cb.stroke();
            cb.restoreState();
        }

        /** Arco aproximado por segmentos curtos (ângulos em graus, sentido anti-horário positivo). */
        private static void arco(PdfContentByte cb, float cx, float cy, float r, float inicio, float extensao) {
            int passos = 24;
            for (int i = 0; i <= passos; i++) {
                double ang = Math.toRadians(inicio + extensao * i / passos);
                cb.lineTo((float) (cx + r * Math.cos(ang)), (float) (cy + r * Math.sin(ang)));
            }
        }

        private static void texto(PdfContentByte cb, String t, String fonte, float tamanho, float x, float y, Color cor, int alinha) {
            try {
                BaseFont bf = BaseFont.createFont(fonte, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
                cb.beginText();
                cb.setFontAndSize(bf, tamanho);
                cb.setColorFill(cor);
                cb.showTextAligned(alinha, t, x, y, 0);
                cb.endText();
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    private static PdfPTable blocoPartes(String[][] esquerda, String[][] direita) {
        PdfPTable t = new PdfPTable(4);
        t.setWidthPercentage(100);
        t.setSpacingAfter(8);
        int linhas = Math.max(esquerda.length, direita.length);
        for (int i = 0; i < linhas; i++) {
            for (String[] lado : new String[][]{i < esquerda.length ? esquerda[i] : new String[]{"", ""}, i < direita.length ? direita[i] : new String[]{"", ""}}) {
                PdfPCell r = new PdfPCell(new Phrase(lado[0], SUAVE));
                r.setBorder(Rectangle.NO_BORDER);
                r.setPaddingBottom(6);
                t.addCell(r);
                PdfPCell v = new PdfPCell(new Phrase(vazio(lado[1], "—"), TEXTO));
                v.setBorder(Rectangle.NO_BORDER);
                v.setPaddingBottom(6);
                t.addCell(v);
            }
        }
        return t;
    }

    private static Paragraph rotulo(String t) {
        Paragraph p = new Paragraph(t, CHAMADA);
        p.setSpacingBefore(14);
        p.setSpacingAfter(4);
        return p;
    }

    private static Paragraph justificado(String t) {
        Paragraph p = new Paragraph(t, TEXTO);
        p.setAlignment(Element.ALIGN_JUSTIFIED);
        p.setLeading(14.5f);
        p.setSpacingAfter(4);
        return p;
    }

    private static void cabecalho(PdfPTable t, String... textos) {
        for (int i = 0; i < textos.length; i++) {
            PdfPCell c = new PdfPCell(new Phrase(textos[i].toUpperCase(BR), SUAVE));
            c.setBorder(Rectangle.BOTTOM);
            c.setBorderColor(CARVAO);
            c.setPaddingBottom(6);
            c.setHorizontalAlignment(i == 0 ? Element.ALIGN_LEFT : i == 1 ? Element.ALIGN_CENTER : Element.ALIGN_RIGHT);
            t.addCell(c);
        }
    }

    private static void celula(PdfPTable t, String texto, int alinha) {
        PdfPCell c = new PdfPCell(new Phrase(texto, TEXTO));
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(LINHA);
        c.setPaddingTop(7);
        c.setPaddingBottom(7);
        c.setHorizontalAlignment(alinha);
        t.addCell(c);
    }

    private static void linhaTotal(PdfPTable t, String rotulo, String valor, Font fonte) {
        PdfPCell r = new PdfPCell(new Phrase(rotulo, fonte == TOTAL ? CHAMADA : TEXTO));
        r.setBorder(Rectangle.NO_BORDER);
        r.setHorizontalAlignment(Element.ALIGN_RIGHT);
        r.setVerticalAlignment(Element.ALIGN_MIDDLE);
        r.setPaddingTop(8);
        t.addCell(r);
        PdfPCell v = new PdfPCell(new Phrase(valor, fonte));
        v.setBorder(Rectangle.NO_BORDER);
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        v.setPaddingTop(8);
        t.addCell(v);
    }

    private static void assinatura(PdfPTable t, String nome, String papel) {
        Paragraph f = new Paragraph();
        f.setLeading(15f);
        f.add(new Chunk(nome, TEXTO));
        f.add(Chunk.NEWLINE);
        f.add(new Chunk(papel, SUAVE));
        PdfPCell c = new PdfPCell();
        c.addElement(f);
        c.setBorder(Rectangle.TOP);
        c.setBorderColor(CARVAO);
        c.setPaddingTop(6);
        c.setPaddingRight(24);
        t.addCell(c);
    }

    public static String condicoes(FormaPagamento forma, BigDecimal total, BigDecimal sinal, Integer parcelas) {
        if (forma != FormaPagamento.PARCELADO) return "à vista, " + moeda(total) + ", por Pix, transferência ou dinheiro";
        BigDecimal s = sinal != null ? sinal : BigDecimal.ZERO;
        int n = parcelas != null && parcelas > 0 ? parcelas : 1;
        BigDecimal resto = total.subtract(s);
        String txt = s.signum() > 0 ? "sinal de " + moeda(s) + " para reservar a data e " : "";
        return txt + n + (n == 1 ? " parcela" : " parcelas mensais") + " de " + moeda(resto.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP));
    }

    static String moeda(BigDecimal v) {
        return v == null ? "—" : NumberFormat.getCurrencyInstance(BR).format(v).replace(' ', ' ');
    }

    static String fone(String t) {
        if (t == null) return null;
        String d = t.replaceAll("\\D", "");
        if (d.length() == 11) return "(" + d.substring(0, 2) + ") " + d.substring(2, 7) + "-" + d.substring(7);
        if (d.length() == 10) return "(" + d.substring(0, 2) + ") " + d.substring(2, 6) + "-" + d.substring(6);
        return t;
    }

    private static String nomeMeio(Enums.MeioPagamento m) {
        return switch (m) { case PIX -> "Pix"; case DINHEIRO -> "Dinheiro"; case TRANSFERENCIA -> "Transferência"; case BOLETO -> "Boleto"; };
    }

    private static String junta(String... partes) {
        return String.join(" · ", java.util.Arrays.stream(partes).filter(x -> x != null && !x.isBlank()).toList());
    }

    private static String vazio(String v, String padrao) {
        return v == null || v.isBlank() ? padrao : v;
    }

    static LocalDateTime agora() { return LocalDateTime.now(); }
}
