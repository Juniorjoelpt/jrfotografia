package br.com.jrfotografia.api;

import br.com.jrfotografia.servico.EmailAvisos;
import br.com.jrfotografia.config.Erros.NaoEncontrado;
import br.com.jrfotografia.config.Erros.RegraNegocio;
import br.com.jrfotografia.config.Tentativas;
import br.com.jrfotografia.config.UsuarioLogado;
import br.com.jrfotografia.dominio.ContratoAssinatura;
import br.com.jrfotografia.dominio.ContratoAssinatura.Status;
import br.com.jrfotografia.dominio.Estudio;
import br.com.jrfotografia.dominio.Repositorios.EstudioRepo;
import br.com.jrfotografia.servico.AssinaturaServico;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;

/** Assinatura eletrônica do contrato: link para o cliente, assinatura, PDF assinado e verificação pública. */
@RestController
public class AssinaturaControle {

    private final EmailAvisos avisos;
    private final AssinaturaServico servico;
    private final EstudioRepo estudios;
    private final Tentativas tentativas;

    public AssinaturaControle(AssinaturaServico servico, EstudioRepo estudios, Tentativas tentativas, EmailAvisos avisos) {
        this.avisos = avisos;
        this.servico = servico;
        this.estudios = estudios;
        this.tentativas = tentativas;
    }

    public record AssinaturaDto(Long id, String token, String codigo, Status status, LocalDateTime criadoEm, LocalDateTime assinadoEm,
                                String nomeAssinante, String cpfAssinante, String ip, boolean desatualizado) {}

    public record AssinaturaPublica(Status status, String titulo, String texto, String contratante, String estudioNome,
                                    String estudioWhatsapp, String responsavel, LocalDateTime enviadoEm, LocalDateTime assinadoEm,
                                    String nomeAssinante, String codigo, String hashTexto, boolean perguntaUsoImagem,
                                    Boolean autorizaImagem) {}

    public record AssinarForm(@NotBlank @Size(max = 120) String nome, @NotBlank @Size(max = 20) String cpf,
                              @NotBlank @Size(max = 600_000) String assinatura, Boolean aceite, Boolean autorizaImagem) {}

    public record Verificacao(boolean valido, String codigo, String estudio, String contratante, String cpf, LocalDateTime assinadoEm,
                              LocalDateTime enviadoEm, String hashTexto, String hashPdf, Boolean arquivoConfere) {}

    public record DesenhoForm(@NotBlank @Size(max = 600_000) String assinatura) {}

    public record AssinaturaEstudio(boolean tem) {}

    // ================================================================ painel

    @GetMapping("/api/trabalhos/{id}/assinatura")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<AssinaturaDto> atual(@PathVariable Long id) {
        ContratoAssinatura a = doEstudio(id);
        return a == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(dto(a));
    }

    @PostMapping("/api/trabalhos/{id}/assinatura")
    @PreAuthorize("hasRole('ADMIN')")
    public AssinaturaDto gerar(@PathVariable Long id) {
        return dto(servico.gerar(UsuarioLogado.estudio(), id));
    }

    /** Manda o link de assinatura por e-mail para a cliente (além do WhatsApp). */
    @PostMapping("/api/trabalhos/{id}/assinatura/email")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public java.util.Map<String, Boolean> enviarPorEmail(@PathVariable Long id) {
        ContratoAssinatura a = doEstudio(id);
        if (a == null || a.getStatus() != Status.PENDENTE) throw new RegraNegocio("Gere o link de assinatura primeiro.");
        exigirEmail(a.getTrabalho().getCliente().getEmail(), a.getEstudioId());
        return java.util.Map.of("enviado", avisos.contratoParaAssinar(a));
    }

    private void exigirEmail(String para, Long estudioId) {
        if (para == null || para.isBlank()) throw new RegraNegocio("A cliente não tem e-mail no cadastro.");
        Estudio e = estudios.findById(estudioId).orElseThrow();
        if (!e.isEmailAtivo()) throw new RegraNegocio("Os e-mails estão desligados. Ligue em Ajustes › E-mail.");
    }

    @DeleteMapping("/api/trabalhos/{id}/assinatura")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> cancelar(@PathVariable Long id) {
        servico.cancelar(UsuarioLogado.estudio(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/trabalhos/{id}/assinatura/contrato.pdf")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> pdfPainel(@PathVariable Long id) {
        ContratoAssinatura a = doEstudio(id);
        if (a == null) throw new NaoEncontrado("O contrato ainda não foi assinado.");
        return Pdfs.resposta(servico.pdfAssinado(a), "contrato-assinado-" + Pdfs.nomeArquivo(a.getTrabalho().getCliente().getNome()) + ".pdf");
    }

    @GetMapping("/api/estudio/assinatura")
    @PreAuthorize("hasRole('ADMIN')")
    public AssinaturaEstudio temAssinatura() {
        return new AssinaturaEstudio(estudio().getAssinaturaImagem() != null);
    }

    @GetMapping(value = "/api/estudio/assinatura.png", produces = MediaType.IMAGE_PNG_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> imagemAssinatura() {
        Estudio e = estudio();
        if (e.getAssinaturaImagem() == null) throw new NaoEncontrado("Sem assinatura cadastrada.");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG).body(servico.ler(e.getAssinaturaImagem()));
    }

    @PutMapping("/api/estudio/assinatura")
    @PreAuthorize("hasRole('ADMIN')")
    public AssinaturaEstudio salvarAssinatura(@Valid @RequestBody DesenhoForm f) {
        servico.salvarAssinaturaEstudio(UsuarioLogado.estudio(), f.assinatura());
        return new AssinaturaEstudio(true);
    }

    @DeleteMapping("/api/estudio/assinatura")
    @PreAuthorize("hasRole('ADMIN')")
    public AssinaturaEstudio apagarAssinatura() {
        servico.apagarAssinaturaEstudio(UsuarioLogado.estudio());
        return new AssinaturaEstudio(false);
    }

    // ================================================================ cliente

    @GetMapping("/api/publico/assinaturas/{token}")
    @Transactional(readOnly = true)
    public AssinaturaPublica ver(@PathVariable String token) {
        return publica(servico.porToken(token));
    }

    @PostMapping("/api/publico/assinaturas/{token}")
    public AssinaturaPublica assinar(HttpServletRequest req, @PathVariable String token, @Valid @RequestBody AssinarForm f) {
        String aparelho = Tentativas.aparelho(req);
        tentativas.verificar(Tentativas.ASSINATURA_APARELHO, aparelho, "Muitas tentativas de assinatura.");
        try {
            ContratoAssinatura a = servico.assinar(token, f.nome(), f.cpf(), f.assinatura(), Boolean.TRUE.equals(f.aceite()),
                    f.autorizaImagem(), aparelho, req.getHeader("User-Agent"), origem(req));
            avisos.contratoAssinado(a, servico.pdfAssinado(a));
            return publica(a);
        } catch (RegraNegocio ex) {
            tentativas.erro(Tentativas.ASSINATURA_APARELHO, aparelho);
            throw ex;
        }
    }

    @GetMapping("/api/publico/assinaturas/{token}/contrato.pdf")
    public ResponseEntity<byte[]> pdfCliente(@PathVariable String token) {
        ContratoAssinatura a = servico.porToken(token);
        return Pdfs.resposta(servico.pdfAssinado(a), "contrato-assinado.pdf");
    }

    // ================================================================ verificação

    @GetMapping("/api/publico/verificar/{codigo}")
    @Transactional(readOnly = true)
    public Verificacao verificar(HttpServletRequest req, @PathVariable String codigo) {
        String aparelho = Tentativas.aparelho(req);
        tentativas.verificar(Tentativas.VERIFICACAO_APARELHO, aparelho, "Muitas consultas.");
        ContratoAssinatura a = servico.porCodigo(codigo);
        if (a == null) tentativas.erro(Tentativas.VERIFICACAO_APARELHO, aparelho);
        return verificacao(a, codigo, null);
    }

    @PostMapping(value = "/api/publico/verificar", consumes = "multipart/form-data")
    @Transactional(readOnly = true)
    public Verificacao verificarArquivo(HttpServletRequest req, @RequestParam("arquivo") MultipartFile arquivo,
                                        @RequestParam(value = "codigo", required = false) String codigo) {
        String aparelho = Tentativas.aparelho(req);
        tentativas.verificar(Tentativas.VERIFICACAO_APARELHO, aparelho, "Muitas consultas.");
        if (arquivo.getSize() > 20L * 1024 * 1024) throw new RegraNegocio("Arquivo grande demais (máximo 20 MB).");
        byte[] bytes;
        try { bytes = arquivo.getBytes(); } catch (IOException ex) { throw new RegraNegocio("Não foi possível ler o arquivo."); }
        ContratoAssinatura peloArquivo = servico.porArquivo(bytes);
        if (codigo != null && !codigo.isBlank()) {
            ContratoAssinatura peloCodigo = servico.porCodigo(codigo);
            if (peloCodigo == null) tentativas.erro(Tentativas.VERIFICACAO_APARELHO, aparelho);
            boolean confere = peloCodigo != null && peloArquivo != null && peloCodigo.getId().equals(peloArquivo.getId());
            return verificacao(peloCodigo, codigo, confere);
        }
        if (peloArquivo == null) tentativas.erro(Tentativas.VERIFICACAO_APARELHO, aparelho);
        return verificacao(peloArquivo, null, peloArquivo != null);
    }

    // ================================================================ apoio

    private ContratoAssinatura doEstudio(Long trabalhoId) {
        ContratoAssinatura a = servico.atual(trabalhoId);
        if (a != null && !a.getEstudioId().equals(UsuarioLogado.estudio())) throw new NaoEncontrado("Trabalho não encontrado");
        return a;
    }

    private Estudio estudio() {
        return estudios.findById(UsuarioLogado.estudio()).orElseThrow();
    }

    private AssinaturaDto dto(ContratoAssinatura a) {
        return new AssinaturaDto(a.getId(), a.getToken(), AssinaturaServico.formatarCodigo(a.getCodigo()), a.getStatus(), a.getCriadoEm(),
                a.getAssinadoEm(), a.getNomeAssinante(), a.getCpfAssinante(), a.getIp(), servico.desatualizado(a));
    }

    private AssinaturaPublica publica(ContratoAssinatura a) {
        Estudio e = estudios.findById(a.getEstudioId()).orElseThrow();
        boolean assinado = a.getStatus() == Status.ASSINADO;
        return new AssinaturaPublica(a.getStatus(), a.getTrabalho().getTitulo(), a.getTexto(), a.getTrabalho().getCliente().getNome(),
                e.getNome(), e.getWhatsapp(), e.getResponsavel(), a.getCriadoEm(), a.getAssinadoEm(), a.getNomeAssinante(),
                assinado ? AssinaturaServico.formatarCodigo(a.getCodigo()) : null, a.getHashTexto(),
                AssinaturaServico.perguntaUsoImagem(a.getTexto()), a.getAutorizaImagem());
    }

    private Verificacao verificacao(ContratoAssinatura a, String codigo, Boolean confere) {
        if (a == null) return new Verificacao(false, codigo, null, null, null, null, null, null, null, confere);
        Estudio e = estudios.findById(a.getEstudioId()).orElseThrow();
        String cpf = a.getCpfAssinante();
        String cpfOculto = cpf != null && cpf.length() == 14 ? "***." + cpf.substring(4, 11) + "-**" : null;
        return new Verificacao(true, AssinaturaServico.formatarCodigo(a.getCodigo()), e.getNome(), a.getNomeAssinante(), cpfOculto,
                a.getAssinadoEm(), a.getCriadoEm(), a.getHashTexto(), a.getHashPdf(), confere);
    }

    /** Endereço do site como o cliente acessou (para o link de verificação no PDF). */
    private String origem(HttpServletRequest req) {
        for (String h : List.of("Origin", "Referer")) {
            String v = req.getHeader(h);
            if (v == null || v.isBlank() || v.equals("null")) continue;
            try {
                URI u = URI.create(v);
                if (u.getScheme() != null && u.getHost() != null) {
                    return u.getScheme() + "://" + u.getHost() + (u.getPort() > 0 ? ":" + u.getPort() : "");
                }
            } catch (IllegalArgumentException ignorado) { /* cabeçalho estranho */ }
        }
        return null;
    }
}
