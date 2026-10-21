package br.com.jrfotografia.api;

import br.com.jrfotografia.dominio.*;
import br.com.jrfotografia.dominio.Enums.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Formatos de entrada e saída da API. */
public final class Dtos {

    private Dtos() {}

    // ---------- autenticação ----------
    public record Login(@NotBlank @Email String email, @NotBlank String senha) {}

    /** Com a verificação em duas etapas ligada, o login devolve só o {@code desafio}; a sessão vem depois do código. */
    public record Sessao(String token, String nome, String perfil, String estudio, boolean senhaPadrao, boolean precisa2fa,
                         String desafio, boolean doisFatores) {}

    public record SegundoPasso(@NotBlank String desafio, @NotBlank @Size(max = 20) String codigo) {}

    // ---------- estúdio ----------
    public record EstudioDto(String nome, String slug, String cidade, String whatsapp, String instagram,
                             String email, String chavePix, String apresentacao, String dominio) {
        static EstudioDto de(Estudio e, boolean comPix) {
            return new EstudioDto(e.getNome(), e.getSlug(), e.getCidade(), e.getWhatsapp(), e.getInstagram(),
                    e.getEmail(), comPix ? e.getChavePix() : null, e.getApresentacao(), e.getDominio());
        }
    }

    public record EstudioForm(@NotBlank String nome, String cidade, String whatsapp, String instagram,
                              String email, String chavePix, String apresentacao) {}

    // ---------- clientes ----------
    public record ClienteForm(@NotBlank @Size(max = 120) String nome, @Size(max = 20) String telefone,
                              @Email String email, @Size(max = 14) String cpf, LocalDate dataNascimento,
                              Origem origem, String observacoes) {}

    public record ClienteDto(Long id, String nome, String telefone, String email, String cpf,
                             LocalDate dataNascimento, Origem origem, String observacoes, LocalDateTime criadoEm) {
        static ClienteDto de(Cliente c) {
            return new ClienteDto(c.getId(), c.getNome(), c.getTelefone(), c.getEmail(), c.getCpf(),
                    c.getDataNascimento(), c.getOrigem(), c.getObservacoes(), c.getCriadoEm());
        }
    }

    // ---------- pacotes ----------
    public record PacoteForm(@NotBlank String nome, @Positive Integer qtdFotos, @NotNull @PositiveOrZero BigDecimal preco,
                             @PositiveOrZero BigDecimal precoFotoExtra, @NotNull @PositiveOrZero Integer prazoEntregaDias,
                             List<String> itens, String materialEntregue, Boolean publico, Boolean ativo, Integer ordem,
                             @Min(30) @Max(1440) Integer duracaoMin, Boolean agendaOnline) {}

    public record PacoteDto(Long id, String nome, Integer qtdFotos, BigDecimal preco, BigDecimal precoFotoExtra,
                            int prazoEntregaDias, List<String> itens, String materialEntregue,
                            boolean publico, boolean ativo, int ordem, int duracaoMin, boolean agendaOnline) {
        static PacoteDto de(Pacote p) {
            return new PacoteDto(p.getId(), p.getNome(), p.getQtdFotos(), p.getPreco(), p.getPrecoFotoExtra(),
                    p.getPrazoEntregaDias(), p.listaItens(), p.getMaterialEntregue(), p.isPublico(), p.isAtivo(), p.getOrdem(),
                    p.getDuracaoMin(), p.isAgendaOnline());
        }
    }

    // ---------- pedidos de orçamento ----------
    public record PedidoForm(@NotBlank @Size(max = 120) String nome, @NotBlank @Size(max = 20) String telefone,
                             @Email String email, @Size(max = 60) String tipoTrabalho, Long pacoteId,
                             LocalDate dataDesejada, @Size(max = 2000) String mensagem, Origem origem,
                             @AssertTrue(message = "é preciso aceitar o uso dos dados para contato") Boolean consentimento) {}

    public record PedidoDto(Long id, String nome, String telefone, String email, String tipoTrabalho, Long pacoteId,
                            LocalDate dataDesejada, String mensagem, Origem origem, StatusPedido status,
                            Long clienteId, LocalDateTime criadoEm) {
        static PedidoDto de(PedidoOrcamento p) {
            return new PedidoDto(p.getId(), p.getNome(), p.getTelefone(), p.getEmail(), p.getTipoTrabalho(),
                    p.getPacoteId(), p.getDataDesejada(), p.getMensagem(), p.getOrigem(), p.getStatus(),
                    p.getClienteId(), p.getCriadoEm());
        }
    }

    public record StatusPedidoForm(@NotNull StatusPedido status) {}

    // ---------- trabalhos ----------
    public record TrabalhoForm(@NotNull Long clienteId, Long pacoteId, String titulo, String tipo,
                               LocalDateTime dataTrabalho, String localTrabalho, @PositiveOrZero BigDecimal valorTotal,
                               FormaPagamento formaPagamento, @PositiveOrZero BigDecimal valorSinal,
                               @Min(1) @Max(12) Integer numParcelas, LocalDate primeiroVencimento, String observacoes,
                               @Min(30) @Max(1440) Integer duracaoMin) {}

    public record TrabalhoEdicao(String titulo, String tipo, LocalDateTime dataTrabalho, String localTrabalho,
                                 LocalDate prazoEntrega, String observacoes, Integer duracaoMin) {}

    public record StatusTrabalhoForm(@NotNull StatusTrabalho status) {}

    public record TrabalhoResumo(Long id, String titulo, String clienteNome, String clienteTelefone, String pacoteNome,
                                 String tipo, LocalDateTime dataTrabalho, String localTrabalho, StatusTrabalho status,
                                 LocalDate prazoEntrega, BigDecimal valorTotal, boolean atrasado,
                                 List<Long> capas, Long totalFotos) {
        /** Com capas: as 4 primeiras fotos, para o cartão em mosaico. */
        static TrabalhoResumo de(Trabalho t, List<Long> capas, long totalFotos) {
            TrabalhoResumo r = de(t);
            return new TrabalhoResumo(r.id(), r.titulo(), r.clienteNome(), r.clienteTelefone(), r.pacoteNome(), r.tipo(),
                    r.dataTrabalho(), r.localTrabalho(), r.status(), r.prazoEntrega(), r.valorTotal(), r.atrasado(),
                    capas, totalFotos);
        }

        static TrabalhoResumo de(Trabalho t) {
            boolean atrasado = t.getPrazoEntrega() != null && t.getPrazoEntrega().isBefore(LocalDate.now())
                    && t.getStatus() != StatusTrabalho.ENTREGUE && t.getStatus() != StatusTrabalho.CANCELADO;
            return new TrabalhoResumo(t.getId(), t.getTitulo(), t.getCliente().getNome(), t.getCliente().getTelefone(),
                    t.getPacote() != null ? t.getPacote().getNome() : null, t.getTipo(), t.getDataTrabalho(),
                    t.getLocalTrabalho(), t.getStatus(), t.getPrazoEntrega(), t.getValorTotal(), atrasado, null, null);
        }
    }

    public record TrabalhoDetalhe(TrabalhoResumo resumo, ClienteDto cliente, PacoteDto pacote,
                                  Enums.FormaPagamento formaPagamento, String galeriaToken, boolean galeriaComSenha,
                                  LocalDateTime galeriaExpiraEm, LocalDateTime selecaoFechadaEm, LocalDateTime entregueEm,
                                  String observacoes, long totalFotos, long selecionadas, long editadas,
                                  List<ParcelaDto> parcelas, boolean downloadLiberado, String motivoDownload,
                                  int duracaoMin, Long orcamentoId, List<String> conflitos, String areaCliente, int remarcacoes,
                                  LocalDateTime canceladoEm, String canceladoPor, String motivoCancelamento) {}

    public record FotoDto(Long id, String nomeOriginal, Integer largura, Integer altura, int ordem,
                          boolean selecionada, boolean editada, String comentario) {
        static FotoDto de(Foto f) {
            return new FotoDto(f.getId(), f.getNomeOriginal(), f.getLargura(), f.getAltura(), f.getOrdem(),
                    f.isSelecionada(), f.getArquivoFinal() != null, f.getComentario());
        }
    }

    public record SenhaGaleria(String senha, String token) {}

    public record ResultadoEditadas(int recebidas, List<String> semCorrespondencia) {}

    // ---------- financeiro ----------
    public record ParcelaDto(Long id, Long trabalhoId, TipoParcela tipo, String descricao, BigDecimal valor,
                             LocalDate vencimento, LocalDate pagoEm, MeioPagamento forma) {
        static ParcelaDto de(Parcela p) {
            return new ParcelaDto(p.getId(), p.getTrabalhoId(), p.getTipo(), p.getDescricao(), p.getValor(),
                    p.getVencimento(), p.getPagoEm(), p.getForma());
        }
    }

    public record PagamentoForm(LocalDate data, MeioPagamento forma) {}

    public record ContaReceber(ParcelaDto parcela, String trabalhoTitulo, String clienteNome, String clienteTelefone,
                               boolean vencida) {}

    // ---------- portfólio ----------
    public record PortfolioDto(Long id, String categoria, String titulo, Integer largura, Integer altura,
                               int ordem, boolean destaque) {
        static PortfolioDto de(PortfolioFoto p) {
            return new PortfolioDto(p.getId(), p.getCategoria(), p.getTitulo(), p.getLargura(), p.getAltura(),
                    p.getOrdem(), p.isDestaque());
        }
    }

    public record PortfolioEdicao(String categoria, String titulo, Integer ordem, Boolean destaque) {}

    // ---------- painel ----------
    public record Painel(List<TrabalhoResumo> proximos, List<TrabalhoResumo> entregasProximas,
                         List<TrabalhoResumo> atrasados, long aguardandoSelecao, long emEdicao,
                         long pedidosNovos, BigDecimal aReceber, BigDecimal vencido, BigDecimal recebidoNoMes) {}

    // ---------- galeria do cliente ----------
    public record AcessoGaleria(@NotBlank String senha) {}

    public record GaleriaDto(String titulo, String clienteNome, LocalDateTime dataTrabalho, String estudioNome, String estudioWhatsapp,
                             StatusTrabalho status, boolean exigeSelecao, Integer fotosNoPacote,
                             BigDecimal precoFotoExtra, boolean selecaoAberta, long selecionadas,
                             boolean downloadLiberado, String motivoDownload, LocalDateTime expiraEm,
                             List<FotoGaleria> fotos, String pesquisaToken) {}

    public record FotoGaleria(Long id, Integer largura, Integer altura, boolean selecionada, String comentario, boolean editada) {}

    public record MarcarForm(boolean selecionada) {}

    public record ComentarioForm(@Size(max = 500) String comentario) {}

    // ---------- conta e usuários ----------
    public record TrocaSenha(@NotBlank String senhaAtual, @NotBlank @Size(min = 8, max = 72, message = "use de 8 a 72 caracteres") String novaSenha) {}

    public record UsuarioForm(@NotBlank @Size(max = 120) String nome, @NotBlank @Email String email,
                              @Size(min = 8, max = 72, message = "use de 8 a 72 caracteres") String senha,
                              Enums.Perfil perfil, Boolean ativo) {}

    public record UsuarioDto(Long id, String nome, String email, Enums.Perfil perfil, boolean ativo, boolean voce, boolean doisFatores) {}
}
