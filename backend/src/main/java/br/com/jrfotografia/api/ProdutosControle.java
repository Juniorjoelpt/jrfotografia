package br.com.jrfotografia.api;

import br.com.jrfotografia.config.UsuarioLogado;
import br.com.jrfotografia.dominio.Estudio;
import br.com.jrfotografia.dominio.Produtos.*;
import br.com.jrfotografia.dominio.Repositorios.*;
import br.com.jrfotografia.dominio.Trabalho;
import br.com.jrfotografia.dominio.Usuario;
import br.com.jrfotografia.servico.Armazenamento;
import br.com.jrfotografia.servico.ProdutosServico;
import br.com.jrfotografia.servico.ProdutosServico.NovoPedido;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Fase 3 · bloco 4: pedidos de produtos, aprovação de álbum pelo cliente e estoque. Painel só para administrador. */
@RestController
public class ProdutosControle {

    private final ProdutosServico servico;
    private final TrabalhoRepo trabalhos;
    private final ParcelaRepo parcelas;
    private final DespesaRepo despesas;
    private final EstudioRepo estudios;
    private final UsuarioRepo usuarios;
    private final Armazenamento armazenamento;

    public ProdutosControle(ProdutosServico servico, TrabalhoRepo trabalhos, ParcelaRepo parcelas, DespesaRepo despesas,
                            EstudioRepo estudios, UsuarioRepo usuarios, Armazenamento armazenamento) {
        this.servico = servico;
        this.trabalhos = trabalhos;
        this.parcelas = parcelas;
        this.despesas = despesas;
        this.estudios = estudios;
        this.usuarios = usuarios;
        this.armazenamento = armazenamento;
    }

    // ---------------------------------------------------------------- DTOs
    public record PedidoForm(@NotNull Long trabalhoId, @NotNull TipoProduto tipo, @NotBlank @Size(max = 200) String descricao,
                             @Min(1) @Max(1000) Integer quantidade, BigDecimal valorVenda, BigDecimal custo, @Size(max = 120) String laboratorio,
                             LocalDate prazo, @Size(max = 2000) String observacoes, Boolean cobrar) {}

    public record StatusForm(@NotNull StatusPedidoProduto status, LocalDate previsaoLab) {}

    public record LaminaDto(Long id, int versao, int ordem, String nome, Integer largura, Integer altura, String comentario) {}

    public record PedidoDto(Long id, Long trabalhoId, String trabalho, String cliente, String clienteTelefone, TipoProduto tipo,
                            String descricao, int quantidade, BigDecimal valorVenda, BigDecimal custo, String laboratorio,
                            StatusPedidoProduto status, LocalDate prazo, LocalDate previsaoLab, LocalDateTime enviadoLabEm,
                            LocalDateTime recebidoEm, LocalDateTime entregueEm, String observacoes, boolean cobrado, boolean recebido,
                            boolean custoLancado, boolean custoPago, String tokenAprovacao, int versaoLayout, Aprovacao aprovacao,
                            LocalDateTime aprovacaoEm, String comentarioCliente, boolean atrasado, List<LaminaDto> laminas,
                            List<LaminaDto> versoesAnteriores, LocalDateTime criadoEm) {}

    public record AlbumPublico(String cliente, String estudioNome, String estudioWhatsapp, String descricao, int versao,
                               Aprovacao aprovacao, LocalDateTime aprovacaoEm, String comentarioCliente, List<LaminaDto> laminas) {}

    public record RespostaAlbum(@NotNull Boolean aprova, @Size(max = 2000) String comentario, Map<Long, String> comentarios) {}

    public record MaterialForm(@NotBlank @Size(max = 120) String nome, @NotBlank @Size(max = 20) String unidade, BigDecimal minimo,
                               BigDecimal custoUnitario, Boolean ativo, BigDecimal quantidadeInicial) {}

    public record MaterialDto(Long id, String nome, String unidade, BigDecimal quantidade, BigDecimal minimo, BigDecimal custoUnitario,
                              boolean ativo, boolean baixo) {
        static MaterialDto de(Material m) {
            return new MaterialDto(m.getId(), m.getNome(), m.getUnidade(), m.getQuantidade(), m.getMinimo(), m.getCustoUnitario(), m.isAtivo(),
                    m.isAtivo() && m.getQuantidade().compareTo(m.getMinimo()) <= 0 && m.getMinimo().signum() > 0);
        }
    }

    public record MovimentoForm(@NotNull Long materialId, @NotNull TipoMovimento tipo, @NotNull BigDecimal quantidade,
                                @Size(max = 200) String motivo, Long pedidoId) {}

    public record MovimentoDto(Long id, Long materialId, String material, String unidade, TipoMovimento tipo, BigDecimal quantidade,
                               BigDecimal saldo, String motivo, Long pedidoId, String usuario, LocalDateTime criadoEm) {}

    // ================================================================ pedidos

    @GetMapping("/api/produtos/pedidos")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<PedidoDto> pedidos(@RequestParam(required = false) Long trabalho) {
        Long est = UsuarioLogado.estudio();
        var lista = trabalho != null ? servico.doTrabalho(est, trabalho) : servico.pedidos(est);
        return lista.stream().map(p -> dto(p, false)).toList();
    }

    @GetMapping("/api/produtos/pedidos/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public PedidoDto pedido(@PathVariable Long id) {
        return dto(servico.pedido(UsuarioLogado.estudio(), id), true);
    }

    @PostMapping("/api/produtos/pedidos")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public PedidoDto criar(@Valid @RequestBody PedidoForm f) {
        return dto(servico.criar(UsuarioLogado.estudio(), novo(f)), true);
    }

    @PutMapping("/api/produtos/pedidos/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public PedidoDto editar(@PathVariable Long id, @Valid @RequestBody PedidoForm f) {
        return dto(servico.editar(UsuarioLogado.estudio(), id, novo(f)), true);
    }

    @PatchMapping("/api/produtos/pedidos/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public PedidoDto status(@PathVariable Long id, @Valid @RequestBody StatusForm f) {
        return dto(servico.mudarStatus(UsuarioLogado.estudio(), id, f.status(), f.previsaoLab()), true);
    }

    @PostMapping(value = "/api/produtos/pedidos/{id}/laminas", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public PedidoDto laminas(@PathVariable Long id, @RequestParam("arquivos") List<MultipartFile> arquivos) {
        return dto(servico.enviarLaminas(UsuarioLogado.estudio(), id, arquivos), true);
    }

    @DeleteMapping("/api/produtos/pedidos/{id}/laminas/{laminaId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public PedidoDto apagarLamina(@PathVariable Long id, @PathVariable Long laminaId) {
        servico.apagarLamina(UsuarioLogado.estudio(), id, laminaId);
        return dto(servico.pedido(UsuarioLogado.estudio(), id), true);
    }

    @GetMapping("/api/produtos/pedidos/{id}/laminas/{laminaId}/{tamanho}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Resource> imagemPainel(@PathVariable Long id, @PathVariable Long laminaId, @PathVariable String tamanho) {
        PedidoProduto p = servico.pedido(UsuarioLogado.estudio(), id);
        return imagem(servico.lamina(p, laminaId), tamanho);
    }

    private NovoPedido novo(PedidoForm f) {
        return new NovoPedido(f.trabalhoId(), f.tipo(), f.descricao(), f.quantidade() == null ? 1 : f.quantidade(), f.valorVenda(), f.custo(),
                f.laboratorio(), f.prazo(), f.observacoes(), Boolean.TRUE.equals(f.cobrar()));
    }

    // ================================================================ aprovação do álbum (cliente)

    @GetMapping("/api/publico/albuns/{token}")
    @Transactional(readOnly = true)
    public AlbumPublico album(@PathVariable String token) {
        return publico(servico.porToken(token));
    }

    @PostMapping("/api/publico/albuns/{token}")
    @Transactional
    public AlbumPublico responder(@PathVariable String token, @Valid @RequestBody RespostaAlbum f) {
        return publico(servico.responderAprovacao(token, f.aprova(), f.comentario(), f.comentarios()));
    }

    @GetMapping("/api/publico/albuns/{token}/laminas/{laminaId}/{tamanho}")
    public ResponseEntity<Resource> imagemPublica(@PathVariable String token, @PathVariable Long laminaId, @PathVariable String tamanho) {
        PedidoProduto p = servico.porToken(token);
        AlbumLamina l = servico.lamina(p, laminaId);
        if (l.getVersao() != p.getVersaoLayout()) return ResponseEntity.notFound().build();
        return imagem(l, tamanho);
    }

    private AlbumPublico publico(PedidoProduto p) {
        Trabalho t = trabalhos.findById(p.getTrabalhoId()).orElseThrow();
        Estudio e = estudios.findById(p.getEstudioId()).orElseThrow();
        return new AlbumPublico(t.getCliente().getNome().trim().split("\\s+")[0], e.getNome(), e.getWhatsapp(), ProdutosServico.rotulo(p),
                p.getVersaoLayout(), p.getAprovacao(), p.getAprovacaoEm(), p.getComentarioCliente(),
                servico.laminasAtuais(p).stream().map(ProdutosControle::lamina).toList());
    }

    private ResponseEntity<Resource> imagem(AlbumLamina l, String tamanho) {
        String chave = "mini".equals(tamanho) ? l.getMiniatura() : l.getArquivo();
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePrivate())
                .contentType(MediaType.IMAGE_JPEG).body(new FileSystemResource(armazenamento.caminho(chave)));
    }

    private static LaminaDto lamina(AlbumLamina l) {
        return new LaminaDto(l.getId(), l.getVersao(), l.getOrdem(), l.getNome(), l.getLargura(), l.getAltura(), l.getComentario());
    }

    private PedidoDto dto(PedidoProduto p, boolean comLaminas) {
        Trabalho t = trabalhos.findById(p.getTrabalhoId()).orElseThrow();
        var parcela = p.getParcelaId() == null ? null : parcelas.findById(p.getParcelaId()).orElse(null);
        var despesa = p.getDespesaId() == null ? null : despesas.findById(p.getDespesaId()).orElse(null);
        boolean aberto = p.getStatus() != StatusPedidoProduto.ENTREGUE && p.getStatus() != StatusPedidoProduto.CANCELADO;
        List<LaminaDto> atuais = List.of(), anteriores = List.of();
        if (comLaminas && p.getTipo() == TipoProduto.ALBUM) {
            var todas = servico.todasLaminas(p);
            atuais = todas.stream().filter(l -> l.getVersao() == p.getVersaoLayout()).map(ProdutosControle::lamina).toList();
            anteriores = todas.stream().filter(l -> l.getVersao() < p.getVersaoLayout() && l.getComentario() != null).map(ProdutosControle::lamina).toList();
        }
        return new PedidoDto(p.getId(), t.getId(), t.getTitulo(), t.getCliente().getNome(), t.getCliente().getTelefone(), p.getTipo(),
                p.getDescricao(), p.getQuantidade(), p.getValorVenda(), p.getCusto(), p.getLaboratorio(), p.getStatus(), p.getPrazo(),
                p.getPrevisaoLab(), p.getEnviadoLabEm(), p.getRecebidoEm(), p.getEntregueEm(), p.getObservacoes(), parcela != null,
                parcela != null && parcela.paga(), despesa != null, despesa != null && despesa.getPagoEm() != null, p.getTokenAprovacao(),
                p.getVersaoLayout(), p.getAprovacao(), p.getAprovacaoEm(), p.getComentarioCliente(),
                aberto && p.getPrazo() != null && p.getPrazo().isBefore(LocalDate.now()), atuais, anteriores, p.getCriadoEm());
    }

    // ================================================================ estoque

    @GetMapping("/api/estoque/materiais")
    @PreAuthorize("hasRole('ADMIN')")
    public List<MaterialDto> materiais() {
        return servico.materiais(UsuarioLogado.estudio()).stream().map(MaterialDto::de).toList();
    }

    @PostMapping("/api/estoque/materiais")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public MaterialDto novoMaterial(@Valid @RequestBody MaterialForm f) {
        return MaterialDto.de(servico.salvarMaterial(UsuarioLogado.estudio(), null, f.nome(), f.unidade(), f.minimo(), f.custoUnitario(),
                f.ativo() == null || f.ativo(), f.quantidadeInicial(), nomeUsuario()));
    }

    @PutMapping("/api/estoque/materiais/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public MaterialDto editarMaterial(@PathVariable Long id, @Valid @RequestBody MaterialForm f) {
        return MaterialDto.de(servico.salvarMaterial(UsuarioLogado.estudio(), id, f.nome(), f.unidade(), f.minimo(), f.custoUnitario(),
                f.ativo() == null || f.ativo(), null, nomeUsuario()));
    }

    @PostMapping("/api/estoque/movimentos")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public MaterialDto movimentar(@Valid @RequestBody MovimentoForm f) {
        Long est = UsuarioLogado.estudio();
        servico.movimentar(est, f.materialId(), f.tipo(), f.quantidade(), f.motivo(), f.pedidoId(), nomeUsuario());
        return MaterialDto.de(servico.material(est, f.materialId()));
    }

    @GetMapping("/api/estoque/movimentos")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<MovimentoDto> movimentos(@RequestParam(required = false) Long material) {
        Long est = UsuarioLogado.estudio();
        Map<Long, Material> nomes = new HashMap<>();
        servico.materiais(est).forEach(m -> nomes.put(m.getId(), m));
        return servico.movimentos(est, material).stream().map(m -> {
            Material mat = nomes.get(m.getMaterialId());
            return new MovimentoDto(m.getId(), m.getMaterialId(), mat != null ? mat.getNome() : "?", mat != null ? mat.getUnidade() : "",
                    m.getTipo(), m.getQuantidade(), m.getSaldo(), m.getMotivo(), m.getPedidoId(), m.getUsuario(), m.getCriadoEm());
        }).toList();
    }

    private String nomeUsuario() {
        return usuarios.findById(UsuarioLogado.atual().usuarioId()).map(Usuario::getNome).orElse(null);
    }
}
