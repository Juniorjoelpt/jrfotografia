package br.com.jrfotografia.servico;

import br.com.jrfotografia.config.Erros.RegraNegocio;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Gera prévias e miniaturas em JPEG, com o monograma JR translúcido no canto
 * inferior direito, cortado pela borda, como no orçamento e no manual da marca.
 */
@Service
public class Imagens {

    public static final int LADO_PREVIA = 1600;
    public static final int LADO_MINIATURA = 600;

    /** Opacidade do token marca-dagua (branco a 28%). */
    private static final float OPACIDADE_MARCA = 0.28f;

    /** Monograma JR desenhado no mesmo sistema de coordenadas do SVG da marca (caixa 60,20 → 470,360). */
    private static final Shape MONOGRAMA = criarMonograma();
    private static final double MARCA_X = 60, MARCA_Y = 20, MARCA_L = 410, MARCA_A = 340, TRACO = 9;

    public record Gerado(byte[] previa, byte[] miniatura, int largura, int altura) {}

    public record Simples(byte[] imagem, int largura, int altura) {}

    /** Para a foto editada já paga: prévia e miniatura sem marca d'água. */
    public Gerado processarSemMarca(byte[] editada) {
        BufferedImage img = ler(editada);
        return new Gerado(jpeg(reduzir(img, LADO_PREVIA), 0.85f), jpeg(reduzir(img, LADO_MINIATURA), 0.8f),
                img.getWidth(), img.getHeight());
    }

    /** Para fotos de clientes: prévia e miniatura com marca d'água. */
    public Gerado processarComMarca(byte[] original) {
        BufferedImage img = ler(original);
        return new Gerado(
                jpeg(comMarca(reduzir(img, LADO_PREVIA)), 0.82f),
                jpeg(comMarca(reduzir(img, LADO_MINIATURA)), 0.78f),
                img.getWidth(), img.getHeight());
    }

    /** Para o portfólio: imagem para a web e miniatura, sem marca d'água. */
    public Simples processarParaWeb(byte[] original, int lado) {
        BufferedImage img = ler(original);
        return new Simples(jpeg(reduzir(img, lado), 0.85f), img.getWidth(), img.getHeight());
    }

    BufferedImage ler(byte[] bytes) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img == null) throw new RegraNegocio("Formato de imagem não suportado. Envie JPEG ou PNG.");
            return img;
        } catch (IOException e) {
            throw new RegraNegocio("Não foi possível ler a imagem enviada.");
        }
    }

    /** Reduz em etapas de metade para manter a nitidez, até caber no lado máximo. */
    BufferedImage reduzir(BufferedImage img, int ladoMax) {
        int w = img.getWidth(), h = img.getHeight();
        double escala = Math.min(1.0, (double) ladoMax / Math.max(w, h));
        int alvoW = Math.max(1, (int) Math.round(w * escala));
        int alvoH = Math.max(1, (int) Math.round(h * escala));
        BufferedImage atual = paraRgb(img);
        while (atual.getWidth() / 2 >= alvoW && atual.getHeight() / 2 >= alvoH) {
            atual = redimensionar(atual, atual.getWidth() / 2, atual.getHeight() / 2);
        }
        if (atual.getWidth() != alvoW || atual.getHeight() != alvoH) {
            atual = redimensionar(atual, alvoW, alvoH);
        }
        return atual;
    }

    BufferedImage comMarca(BufferedImage img) {
        BufferedImage saida = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = saida.createGraphics();
        g.drawImage(img, 0, 0, null);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        // Monograma com cerca de metade da altura da foto, encostado e cortado no canto inferior direito.
        double altura = img.getHeight() * 0.5;
        double escala = altura / MARCA_A;
        double largura = MARCA_L * escala;
        double x = img.getWidth() - largura * 0.92;
        double y = img.getHeight() - altura * 0.8;

        AffineTransform t = new AffineTransform();
        t.translate(x, y);
        t.scale(escala, escala);
        t.translate(-MARCA_X, -MARCA_Y);
        g.setTransform(t);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, OPACIDADE_MARCA));
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke((float) TRACO, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        g.draw(MONOGRAMA);
        g.dispose();
        return saida;
    }

    private static Shape criarMonograma() {
        Path2D p = new Path2D.Double();
        // J: barra superior, haste e curva tangente à haste, aberta para a esquerda (raio 87,5).
        p.moveTo(150, 40);
        p.lineTo(240, 40);
        p.lineTo(240, 270);
        p.append(new Arc2D.Double(152.5 - 87.5, 270 - 87.5, 175, 175, 0, -126.87, Arc2D.OPEN), true);
        // R: haste, bojo de raio 44 e perna diagonal.
        p.moveTo(266, 272);
        p.lineTo(266, 40);
        p.lineTo(322, 40);
        p.append(new Arc2D.Double(322 - 44, 84 - 44, 88, 88, 90, -180, Arc2D.OPEN), true);
        p.lineTo(266, 128);
        p.moveTo(318, 128);
        p.lineTo(440, 272);
        return p;
    }

    private BufferedImage paraRgb(BufferedImage img) {
        if (img.getType() == BufferedImage.TYPE_INT_RGB) return img;
        BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return rgb;
    }

    private BufferedImage redimensionar(BufferedImage img, int w, int h) {
        BufferedImage saida = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = saida.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(img, 0, 0, w, h, null);
        g.dispose();
        return saida;
    }

    private byte[] jpeg(BufferedImage img, float qualidade) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(qualidade);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(img, null, null), param);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}
