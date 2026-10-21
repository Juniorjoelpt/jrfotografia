package br.com.jrfotografia.servico;

import br.com.jrfotografia.config.Erros.NaoEncontrado;
import br.com.jrfotografia.config.Erros.RegraNegocio;
import br.com.jrfotografia.dominio.ContratoAssinatura;
import br.com.jrfotografia.dominio.ContratoAssinatura.Status;
import br.com.jrfotografia.dominio.Enums.StatusTrabalho;
import br.com.jrfotografia.dominio.Estudio;
import br.com.jrfotografia.dominio.Repositorios.*;
import br.com.jrfotografia.dominio.Trabalho;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/**
 * Assinatura eletrônica do contrato pelo cliente, por link (Lei 14.063/2020, assinatura eletrônica simples).
 * O texto é congelado quando o link é gerado; na assinatura guardamos nome, CPF, data, IP, navegador e o desenho,
 * e geramos o PDF final com uma página de registro e um código de verificação.
 */
@Service
public class AssinaturaServico {

    private static final SecureRandom ALEATORIO = new SecureRandom();
    /** Sem letras e números que se confundem (0/O, 1/I/L). */
    private static final char[] ALFABETO = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int MAX_IMAGEM = 400 * 1024;
    /** Trecho do modelo em que o cliente escolhe se autoriza o uso das imagens no portfólio. */
    private static final java.util.regex.Pattern USO_IMAGEM =
            java.util.regex.Pattern.compile("\\(\\s*\\)\\s*autoriza\\s+\\(\\s*\\)\\s*não autoriza");

    public static boolean perguntaUsoImagem(String texto) {
        return texto != null && USO_IMAGEM.matcher(texto).find();
    }

    /** Texto com a escolha do cliente marcada, para o PDF assinado. */
    static String marcarUsoImagem(String texto, Boolean autoriza) {
        if (autoriza == null) return texto;
        return USO_IMAGEM.matcher(texto).replaceFirst(autoriza ? "( X ) autoriza  (   ) não autoriza" : "(   ) autoriza  ( X ) não autoriza");
    }

    private final AssinaturaRepo assinaturas;
    private final TrabalhoRepo trabalhos;
    private final EstudioRepo estudios;
    private final ParcelaRepo parcelas;
    private final ClienteRepo clientes;
    private final PdfServico pdf;
    private final Armazenamento armazenamento;

    public AssinaturaServico(AssinaturaRepo assinaturas, TrabalhoRepo trabalhos, EstudioRepo estudios, ParcelaRepo parcelas,
                             ClienteRepo clientes, PdfServico pdf, Armazenamento armazenamento) {
        this.assinaturas = assinaturas;
        this.trabalhos = trabalhos;
        this.estudios = estudios;
        this.parcelas = parcelas;
        this.clientes = clientes;
        this.pdf = pdf;
        this.armazenamento = armazenamento;
    }

    // ================================================================ painel

    /** Pedido de assinatura mais recente que não foi cancelado (ou null). */
    @Transactional(readOnly = true)
    public ContratoAssinatura atual(Long trabalhoId) {
        return assinaturas.findByTrabalhoIdOrderByCriadoEmDesc(trabalhoId).stream()
                .filter(a -> a.getStatus() != Status.CANCELADO).findFirst().orElse(null);
    }

    /** O texto atual do contrato é diferente do que foi enviado para assinar? */
    @Transactional(readOnly = true)
    public boolean desatualizado(ContratoAssinatura a) {
        if (a == null || a.getStatus() != Status.PENDENTE) return false;
        Trabalho t = a.getTrabalho();
        return !textoAtual(t).equals(a.getTexto());
    }

    /**
     * Gera (ou reaproveita) o link de assinatura. Se já existe um pendente com o mesmo texto, devolve ele;
     * se o contrato mudou desde então, o link antigo é cancelado e um novo é criado.
     */
    @Transactional
    public ContratoAssinatura gerar(Long estudioId, Long trabalhoId) {
        Trabalho t = trabalhos.findByIdAndEstudioId(trabalhoId, estudioId).orElseThrow(() -> new NaoEncontrado("Trabalho não encontrado"));
        if (t.getStatus() == StatusTrabalho.CANCELADO) throw new RegraNegocio("Este trabalho foi cancelado.");
        ContratoAssinatura atual = atual(t.getId());
        if (atual != null && atual.getStatus() == Status.ASSINADO) {
            throw new RegraNegocio("O contrato já foi assinado em " + PdfServico.DATA_HORA.format(atual.getAssinadoEm()) + ".");
        }
        String texto = textoAtual(t);
        if (atual != null) {
            if (atual.getTexto().equals(texto)) return atual;
            atual.setStatus(Status.CANCELADO);
            atual.setCanceladoEm(LocalDateTime.now());
        }
        ContratoAssinatura a = new ContratoAssinatura();
        a.setEstudioId(estudioId);
        a.setTrabalho(t);
        a.setToken(TrabalhoServico.novoTokenGaleria() + TrabalhoServico.novoTokenGaleria().substring(0, 8));
        a.setCodigo(novoCodigo());
        a.setTexto(texto);
        a.setHashTexto(sha256(texto.getBytes(StandardCharsets.UTF_8)));
        a.setCriadoEm(LocalDateTime.now());
        return assinaturas.save(a);
    }

    /** Cria o pedido de assinatura sem deixar a operação principal falhar (ex.: ao confirmar uma reserva). */
    @Transactional
    public void gerarSePossivel(Long estudioId, Long trabalhoId) {
        try {
            if (atual(trabalhoId) == null) gerar(estudioId, trabalhoId);
        } catch (RuntimeException ignorado) {
            // sem contrato configurado ou trabalho em estado que não permite: o fotógrafo gera depois pelo painel
        }
    }

    @Transactional
    public void cancelar(Long estudioId, Long trabalhoId) {
        trabalhos.findByIdAndEstudioId(trabalhoId, estudioId).orElseThrow(() -> new NaoEncontrado("Trabalho não encontrado"));
        ContratoAssinatura a = atual(trabalhoId);
        if (a == null) return;
        if (a.getStatus() == Status.ASSINADO) throw new RegraNegocio("Contrato assinado não pode ser cancelado por aqui.");
        a.setStatus(Status.CANCELADO);
        a.setCanceladoEm(LocalDateTime.now());
    }

    // ================================================================ cliente

    @Transactional(readOnly = true)
    public ContratoAssinatura porToken(String token) {
        ContratoAssinatura a = assinaturas.findByToken(token).orElseThrow(() -> new NaoEncontrado("Link de assinatura não encontrado"));
        if (a.getStatus() == Status.CANCELADO) throw new NaoEncontrado("Este link de assinatura foi substituído. Peça o link novo ao estúdio.");
        return a;
    }

    @Transactional
    public ContratoAssinatura assinar(String token, String nome, String cpf, String desenhoDataUrl, boolean aceite,
                                      Boolean autorizaImagem, String ip, String navegador, String urlBase) {
        ContratoAssinatura a = porToken(token);
        if (a.getStatus() == Status.ASSINADO) throw new RegraNegocio("Este contrato já foi assinado.");
        Trabalho t = a.getTrabalho();
        if (t.getStatus() == StatusTrabalho.CANCELADO) throw new RegraNegocio("Este trabalho foi cancelado.");
        if (!aceite) throw new RegraNegocio("Para assinar, marque que leu e concorda com o contrato.");
        String nomeLimpo = nome == null ? "" : nome.trim().replaceAll("\\s+", " ");
        if (nomeLimpo.length() < 5 || !nomeLimpo.contains(" ")) throw new RegraNegocio("Informe seu nome completo.");
        String digitos = cpf == null ? "" : cpf.replaceAll("\\D", "");
        if (!cpfValido(digitos)) throw new RegraNegocio("CPF inválido. Confira os números.");
        boolean pergunta = perguntaUsoImagem(a.getTexto());
        if (pergunta && autorizaImagem == null) throw new RegraNegocio("Escolha se autoriza ou não o uso das imagens no portfólio.");
        byte[] desenho = desenho(desenhoDataUrl);

        Estudio e = estudios.findById(a.getEstudioId()).orElseThrow();
        LocalDateTime agora = LocalDateTime.now();
        String cpfFmt = digitos.substring(0, 3) + "." + digitos.substring(3, 6) + "." + digitos.substring(6, 9) + "-" + digitos.substring(9);
        a.setAutorizaImagem(pergunta ? autorizaImagem : null);
        a.setNomeAssinante(nomeLimpo);
        a.setCpfAssinante(cpfFmt);
        a.setIp(limitar(ip, 64));
        a.setUserAgent(limitar(navegador, 400));
        a.setAssinadoEm(agora.withNano(0));
        a.setImagemAssinatura(a.getEstudioId() + "/contratos/" + a.getToken() + "_assinatura.png");
        armazenamento.gravar(a.getImagemAssinatura(), desenho);

        byte[] assinaturaEstudio = e.getAssinaturaImagem() != null ? ler(e.getAssinaturaImagem()) : null;
        // Domínio cadastrado do estúdio tem preferência sobre o endereço informado pelo navegador.
        String base = e.getDominio() != null && !e.getDominio().isBlank() ? "https://" + e.getDominio().replaceFirst("^https?://", "").replaceAll("/+$", "")
                : urlBase != null ? urlBase : "";
        String url = base + "/verificar/" + formatarCodigo(a.getCodigo());
        var auditoria = new PdfServico.Auditoria(formatarCodigo(a.getCodigo()), nomeLimpo, cpfFmt, a.getAssinadoEm(), a.getIp(), a.getUserAgent(),
                a.getHashTexto(), a.getCriadoEm(), url, a.getAutorizaImagem());
        byte[] documento = pdf.contrato(e, marcarUsoImagem(a.getTexto(), a.getAutorizaImagem()), t.getCliente().getNome(), desenho, assinaturaEstudio, auditoria, t.getCliente().getNome());
        a.setPdf(a.getEstudioId() + "/contratos/" + a.getToken() + ".pdf");
        armazenamento.gravar(a.getPdf(), documento);
        a.setHashPdf(sha256(documento));
        a.setStatus(Status.ASSINADO);

        // CPF informado completa o cadastro do cliente, se estava vazio.
        var cliente = t.getCliente();
        if (cliente.getCpf() == null || cliente.getCpf().isBlank()) {
            cliente.setCpf(cpfFmt);
            clientes.save(cliente);
        }
        return a;
    }

    public byte[] pdfAssinado(ContratoAssinatura a) {
        if (a.getStatus() != Status.ASSINADO || a.getPdf() == null) throw new NaoEncontrado("O contrato ainda não foi assinado.");
        return ler(a.getPdf());
    }

    // ================================================================ verificação pública

    @Transactional(readOnly = true)
    public ContratoAssinatura porCodigo(String codigo) {
        String c = codigo == null ? "" : codigo.trim().toUpperCase().replaceAll("[^A-Z0-9]", "");
        return assinaturas.findByCodigo(c).filter(a -> a.getStatus() == Status.ASSINADO).orElse(null);
    }

    @Transactional(readOnly = true)
    public ContratoAssinatura porArquivo(byte[] arquivo) {
        return assinaturas.findByHashPdf(sha256(arquivo)).filter(a -> a.getStatus() == Status.ASSINADO).orElse(null);
    }

    // ================================================================ assinatura do estúdio

    @Transactional
    public void salvarAssinaturaEstudio(Long estudioId, String dataUrl) {
        Estudio e = estudios.findById(estudioId).orElseThrow();
        byte[] png = desenho(dataUrl);
        armazenamento.apagar(e.getAssinaturaImagem());
        e.setAssinaturaImagem(estudioId + "/estudio/assinatura_" + System.currentTimeMillis() + ".png");
        armazenamento.gravar(e.getAssinaturaImagem(), png);
    }

    @Transactional
    public void apagarAssinaturaEstudio(Long estudioId) {
        Estudio e = estudios.findById(estudioId).orElseThrow();
        armazenamento.apagar(e.getAssinaturaImagem());
        e.setAssinaturaImagem(null);
    }

    public byte[] ler(String chave) {
        try {
            return java.nio.file.Files.readAllBytes(armazenamento.caminho(chave));
        } catch (IOException ex) {
            throw new NaoEncontrado("Arquivo não encontrado.");
        }
    }

    // ================================================================ apoio

    private String textoAtual(Trabalho t) {
        Estudio e = estudios.findById(t.getEstudioId()).orElseThrow();
        return pdf.textoContrato(t, e, parcelas.findByTrabalhoIdOrderByVencimentoAscIdAsc(t.getId()));
    }

    /** PNG da assinatura desenhada: confere formato, tamanho e se tem traço de verdade (não está em branco). */
    static byte[] desenho(String dataUrl) {
        if (dataUrl == null || !dataUrl.startsWith("data:image/png;base64,")) throw new RegraNegocio("Desenhe sua assinatura no quadro.");
        byte[] png;
        try {
            png = Base64.getDecoder().decode(dataUrl.substring("data:image/png;base64,".length()));
        } catch (IllegalArgumentException ex) {
            throw new RegraNegocio("Assinatura inválida. Desenhe de novo.");
        }
        if (png.length > MAX_IMAGEM) throw new RegraNegocio("Assinatura grande demais. Limpe e desenhe de novo.");
        if (png.length < 8 || (png[0] & 0xFF) != 0x89 || png[1] != 'P' || png[2] != 'N' || png[3] != 'G') {
            throw new RegraNegocio("Assinatura inválida. Desenhe de novo.");
        }
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
            if (img == null || img.getWidth() > 2000 || img.getHeight() > 1000) throw new RegraNegocio("Assinatura inválida. Desenhe de novo.");
            int tinta = 0;
            for (int y = 0; y < img.getHeight(); y += 2) {
                for (int x = 0; x < img.getWidth(); x += 2) {
                    int argb = img.getRGB(x, y);
                    int alfa = argb >>> 24, lum = ((argb >> 16 & 0xFF) + (argb >> 8 & 0xFF) + (argb & 0xFF)) / 3;
                    if (alfa > 100 && lum < 160) tinta++;
                }
            }
            if (tinta < 40) throw new RegraNegocio("A assinatura ficou muito pequena. Desenhe de novo, ocupando o quadro.");
        } catch (IOException ex) {
            throw new RegraNegocio("Assinatura inválida. Desenhe de novo.");
        }
        return png;
    }

    static boolean cpfValido(String d) {
        if (d == null || !d.matches("\\d{11}") || d.chars().distinct().count() == 1) return false;
        for (int n = 9; n <= 10; n++) {
            int soma = 0;
            for (int i = 0; i < n; i++) soma += (d.charAt(i) - '0') * (n + 1 - i);
            int dv = (soma * 10) % 11 % 10;
            if (dv != d.charAt(n) - '0') return false;
        }
        return true;
    }

    private String novoCodigo() {
        for (int tentativa = 0; tentativa < 10; tentativa++) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 10; i++) sb.append(ALFABETO[ALEATORIO.nextInt(ALFABETO.length)]);
            if (assinaturas.findByCodigo(sb.toString()).isEmpty()) return sb.toString();
        }
        throw new IllegalStateException("Não foi possível gerar código de verificação");
    }

    /** Código para exibir: ABCD-EFGH-JK. */
    public static String formatarCodigo(String c) {
        return c.length() == 10 ? c.substring(0, 4) + "-" + c.substring(4, 8) + "-" + c.substring(8) : c;
    }

    static String sha256(byte[] b) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String limitar(String s, int max) {
        return s == null ? null : s.length() > max ? s.substring(0, max) : s;
    }

    public List<ContratoAssinatura> historico(Long trabalhoId) {
        return assinaturas.findByTrabalhoIdOrderByCriadoEmDesc(trabalhoId);
    }
}
