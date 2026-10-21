package br.com.jrfotografia.dominio;

import br.com.jrfotografia.dominio.Enums.StatusPedido;
import br.com.jrfotografia.dominio.Enums.StatusTrabalho;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repositórios. Toda consulta de negócio filtra por estudioId:
 * um estúdio nunca enxerga dados de outro.
 */
public final class Repositorios {

    private Repositorios() {}

    public interface EstudioRepo extends JpaRepository<Estudio, Long> {
        Optional<Estudio> findBySlug(String slug);
        Optional<Estudio> findByDominio(String dominio);
    }

    public interface UsuarioRepo extends JpaRepository<Usuario, Long> {
        Optional<Usuario> findByEmailIgnoreCase(String email);
        List<Usuario> findByEstudioIdOrderByNome(Long estudioId);
    }

    public interface ClienteRepo extends JpaRepository<Cliente, Long> {
        Optional<Cliente> findByIdAndEstudioId(Long id, Long estudioId);
        List<Cliente> findByEstudioIdOrderByNome(Long estudioId);
        List<Cliente> findByEstudioIdAndTelefone(Long estudioId, String telefone);
        Optional<Cliente> findByEstudioIdAndCodigoIndicacao(Long estudioId, String codigo);

        @Query("select c from Cliente c where c.estudioId = ?1 and (lower(c.nome) like lower(concat('%', ?2, '%')) or c.telefone like concat('%', ?2, '%')) order by c.nome")
        List<Cliente> buscar(Long estudioId, String termo);
    }

    public interface PacoteRepo extends JpaRepository<Pacote, Long> {
        Optional<Pacote> findByIdAndEstudioId(Long id, Long estudioId);
        List<Pacote> findByEstudioIdOrderByOrdem(Long estudioId);
        List<Pacote> findByEstudioIdAndPublicoTrueAndAtivoTrueOrderByOrdem(Long estudioId);
    }

    public interface PedidoRepo extends JpaRepository<PedidoOrcamento, Long> {
        Optional<PedidoOrcamento> findByIdAndEstudioId(Long id, Long estudioId);
        List<PedidoOrcamento> findByEstudioIdOrderByCriadoEmDesc(Long estudioId);
        long countByEstudioIdAndStatus(Long estudioId, StatusPedido status);
    }

    public interface TrabalhoRepo extends JpaRepository<Trabalho, Long> {
        Optional<Trabalho> findByIdAndEstudioId(Long id, Long estudioId);
        Optional<Trabalho> findByGaleriaToken(String galeriaToken);
        Optional<Trabalho> findByTokenCliente(String tokenCliente);
        List<Trabalho> findByEstudioIdOrderByDataTrabalhoDesc(Long estudioId);
        List<Trabalho> findByEstudioIdAndStatusOrderByDataTrabalhoDesc(Long estudioId, StatusTrabalho status);
        List<Trabalho> findByEstudioIdAndClienteIdOrderByDataTrabalhoDesc(Long estudioId, Long clienteId);
        List<Trabalho> findByEstudioIdAndStatusIn(Long estudioId, Collection<StatusTrabalho> status);
        List<Trabalho> findByEstudioIdAndDataTrabalhoBetweenOrderByDataTrabalho(Long estudioId, LocalDateTime de, LocalDateTime ate);
    }

    public interface FotoRepo extends JpaRepository<Foto, Long> {
        List<Foto> findByTrabalhoIdOrderByOrdemAscIdAsc(Long trabalhoId);
        List<Foto> findTop4ByTrabalhoIdOrderByOrdemAscIdAsc(Long trabalhoId);
        Optional<Foto> findByIdAndTrabalhoId(Long id, Long trabalhoId);
        long countByTrabalhoId(Long trabalhoId);
        long countByTrabalhoIdAndSelecionadaTrue(Long trabalhoId);
    }

    public interface ParcelaRepo extends JpaRepository<Parcela, Long> {
        Optional<Parcela> findByIdAndEstudioId(Long id, Long estudioId);
        List<Parcela> findByTrabalhoIdOrderByVencimentoAscIdAsc(Long trabalhoId);
        List<Parcela> findByEstudioId(Long estudioId);
        List<Parcela> findByEstudioIdAndPagoEmIsNullOrderByVencimento(Long estudioId);
        List<Parcela> findByEstudioIdAndPagoEmBetween(Long estudioId, LocalDate inicio, LocalDate fim);
    }

    public interface OrcamentoRepo extends JpaRepository<Orcamento, Long> {
        Optional<Orcamento> findByIdAndEstudioId(Long id, Long estudioId);
        Optional<Orcamento> findByToken(String token);
        List<Orcamento> findByEstudioIdOrderByCriadoEmDesc(Long estudioId);

        @Query("select coalesce(max(o.numero), 0) from Orcamento o where o.estudioId = ?1")
        int ultimoNumero(Long estudioId);
    }

    public interface ItemAvulsoRepo extends JpaRepository<Fase2.ItemAvulso, Long> {
        Optional<Fase2.ItemAvulso> findByIdAndEstudioId(Long id, Long estudioId);
        List<Fase2.ItemAvulso> findByEstudioIdOrderByNome(Long estudioId);
    }

    public interface BloqueioRepo extends JpaRepository<Fase2.BloqueioAgenda, Long> {
        Optional<Fase2.BloqueioAgenda> findByIdAndEstudioId(Long id, Long estudioId);

        @Query("select b from BloqueioAgenda b where b.estudioId = ?1 and b.inicio < ?3 and b.fim > ?2 order by b.inicio")
        List<Fase2.BloqueioAgenda> noPeriodo(Long estudioId, LocalDateTime de, LocalDateTime ate);
    }

    public interface DespesaRepo extends JpaRepository<Fase2.Despesa, Long> {
        Optional<Fase2.Despesa> findByIdAndEstudioId(Long id, Long estudioId);
        List<Fase2.Despesa> findByEstudioIdOrderByVencimentoDesc(Long estudioId);
        List<Fase2.Despesa> findByEstudioIdAndVencimentoBetweenOrderByVencimento(Long estudioId, LocalDate de, LocalDate ate);
        List<Fase2.Despesa> findByEstudioIdAndPagoEmBetween(Long estudioId, LocalDate de, LocalDate ate);
    }

    public interface PortfolioRepo extends JpaRepository<PortfolioFoto, Long> {
        Optional<PortfolioFoto> findByIdAndEstudioId(Long id, Long estudioId);
        List<PortfolioFoto> findByEstudioIdOrderByOrdemAscIdDesc(Long estudioId);
        List<PortfolioFoto> findByEstudioIdAndCategoriaOrderByOrdemAscIdDesc(Long estudioId, String categoria);
    }

    public interface HorarioRepo extends JpaRepository<Agendamento.HorarioAtendimento, Long> {
        List<Agendamento.HorarioAtendimento> findByEstudioIdOrderByDiaSemanaAscInicioAsc(Long estudioId);
        void deleteByEstudioId(Long estudioId);
    }

    public interface AssinaturaRepo extends JpaRepository<ContratoAssinatura, Long> {
        Optional<ContratoAssinatura> findByToken(String token);
        Optional<ContratoAssinatura> findByCodigo(String codigo);
        Optional<ContratoAssinatura> findByHashPdf(String hashPdf);
        List<ContratoAssinatura> findByTrabalhoIdOrderByCriadoEmDesc(Long trabalhoId);
    }

    public interface DataEspecialRepo extends JpaRepository<Equipe.DataEspecial, Long> {
        List<Equipe.DataEspecial> findByEstudioId(Long estudioId);
        List<Equipe.DataEspecial> findByClienteIdOrderByMesAscDiaAsc(Long clienteId);
        Optional<Equipe.DataEspecial> findByIdAndClienteId(Long id, Long clienteId);
    }

    public interface PesquisaRepo extends JpaRepository<Equipe.PesquisaSatisfacao, Long> {
        Optional<Equipe.PesquisaSatisfacao> findByToken(String token);
        Optional<Equipe.PesquisaSatisfacao> findByTrabalhoId(Long trabalhoId);
        List<Equipe.PesquisaSatisfacao> findByEstudioIdOrderByCriadoEmDesc(Long estudioId);
    }

    public interface FreelancerRepo extends JpaRepository<Equipe.Freelancer, Long> {
        List<Equipe.Freelancer> findByEstudioIdOrderByAtivoDescNomeAsc(Long estudioId);
        Optional<Equipe.Freelancer> findByIdAndEstudioId(Long id, Long estudioId);
    }

    public interface TrabalhoFreelancerRepo extends JpaRepository<Equipe.TrabalhoFreelancer, Long> {
        List<Equipe.TrabalhoFreelancer> findByTrabalhoIdOrderByIdAsc(Long trabalhoId);
        List<Equipe.TrabalhoFreelancer> findByEstudioIdOrderByCriadoEmDesc(Long estudioId);
        Optional<Equipe.TrabalhoFreelancer> findByIdAndEstudioId(Long id, Long estudioId);
    }

    public interface TarefaRepo extends JpaRepository<Equipe.Tarefa, Long> {
        List<Equipe.Tarefa> findByEstudioIdOrderByIdDesc(Long estudioId);
        List<Equipe.Tarefa> findByTrabalhoIdOrderByIdAsc(Long trabalhoId);
        Optional<Equipe.Tarefa> findByIdAndEstudioId(Long id, Long estudioId);
    }

    public interface PedidoProdutoRepo extends JpaRepository<Produtos.PedidoProduto, Long> {
        List<Produtos.PedidoProduto> findByEstudioIdOrderByIdDesc(Long estudioId);
        List<Produtos.PedidoProduto> findByTrabalhoIdOrderByIdAsc(Long trabalhoId);
        Optional<Produtos.PedidoProduto> findByIdAndEstudioId(Long id, Long estudioId);
        Optional<Produtos.PedidoProduto> findByTokenAprovacao(String token);
    }

    public interface AlbumLaminaRepo extends JpaRepository<Produtos.AlbumLamina, Long> {
        List<Produtos.AlbumLamina> findByPedidoIdAndVersaoOrderByOrdemAscIdAsc(Long pedidoId, int versao);
        List<Produtos.AlbumLamina> findByPedidoIdOrderByVersaoAscOrdemAsc(Long pedidoId);
        Optional<Produtos.AlbumLamina> findByIdAndPedidoId(Long id, Long pedidoId);
    }

    public interface MaterialRepo extends JpaRepository<Produtos.Material, Long> {
        List<Produtos.Material> findByEstudioIdOrderByAtivoDescNomeAsc(Long estudioId);
        Optional<Produtos.Material> findByIdAndEstudioId(Long id, Long estudioId);
    }

    public interface MovimentoMaterialRepo extends JpaRepository<Produtos.MovimentoMaterial, Long> {
        List<Produtos.MovimentoMaterial> findTop50ByEstudioIdOrderByIdDesc(Long estudioId);
        List<Produtos.MovimentoMaterial> findByMaterialIdOrderByIdDesc(Long materialId);
        List<Produtos.MovimentoMaterial> findByPedidoIdOrderByIdAsc(Long pedidoId);
    }

    public interface CampanhaRepo extends JpaRepository<Marketing.Campanha, Long> {
        List<Marketing.Campanha> findByEstudioIdOrderByIdDesc(Long estudioId);
        Optional<Marketing.Campanha> findByIdAndEstudioId(Long id, Long estudioId);
        Optional<Marketing.Campanha> findBySlug(String slug);
    }

    public interface CampanhaDiaRepo extends JpaRepository<Marketing.CampanhaDia, Long> {
        List<Marketing.CampanhaDia> findByCampanhaIdOrderByDataAscInicioAsc(Long campanhaId);
        void deleteByCampanhaId(Long campanhaId);
    }

    public interface CupomRepo extends JpaRepository<Marketing.Cupom, Long> {
        List<Marketing.Cupom> findByEstudioIdOrderByIdDesc(Long estudioId);
        Optional<Marketing.Cupom> findByIdAndEstudioId(Long id, Long estudioId);
        Optional<Marketing.Cupom> findByEstudioIdAndCodigo(Long estudioId, String codigo);
    }

    public interface IndicacaoRepo extends JpaRepository<Marketing.Indicacao, Long> {
        List<Marketing.Indicacao> findByEstudioIdOrderByIdDesc(Long estudioId);
        List<Marketing.Indicacao> findByIndicadorClienteIdOrderByIdDesc(Long clienteId);
        Optional<Marketing.Indicacao> findByIdAndEstudioId(Long id, Long estudioId);
    }

    public interface ReservaRepo extends JpaRepository<Agendamento.Reserva, Long> {
        Optional<Agendamento.Reserva> findByToken(String token);
        Optional<Agendamento.Reserva> findByIdAndEstudioId(Long id, Long estudioId);
        List<Agendamento.Reserva> findByEstudioIdOrderByCriadoEmDesc(Long estudioId);

        @Query("select r from Reserva r where r.estudioId = ?1 and r.status = br.com.jrfotografia.dominio.Enums.StatusReserva.PENDENTE and r.inicio < ?3 and r.fim > ?2")
        List<Agendamento.Reserva> pendentesNoPeriodo(Long estudioId, LocalDateTime de, LocalDateTime ate);
    }
}
