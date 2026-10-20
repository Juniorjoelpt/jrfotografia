package br.com.jrfotografia.dominio;

import br.com.jrfotografia.dominio.Enums.StatusPedido;
import br.com.jrfotografia.dominio.Enums.StatusTrabalho;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
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
        List<Trabalho> findByEstudioIdOrderByDataTrabalhoDesc(Long estudioId);
        List<Trabalho> findByEstudioIdAndStatusOrderByDataTrabalhoDesc(Long estudioId, StatusTrabalho status);
        List<Trabalho> findByEstudioIdAndClienteIdOrderByDataTrabalhoDesc(Long estudioId, Long clienteId);
        List<Trabalho> findByEstudioIdAndStatusIn(Long estudioId, Collection<StatusTrabalho> status);
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
        List<Parcela> findByEstudioIdAndPagoEmIsNullOrderByVencimento(Long estudioId);
        List<Parcela> findByEstudioIdAndPagoEmBetween(Long estudioId, LocalDate inicio, LocalDate fim);
    }

    public interface PortfolioRepo extends JpaRepository<PortfolioFoto, Long> {
        Optional<PortfolioFoto> findByIdAndEstudioId(Long id, Long estudioId);
        List<PortfolioFoto> findByEstudioIdOrderByOrdemAscIdDesc(Long estudioId);
        List<PortfolioFoto> findByEstudioIdAndCategoriaOrderByOrdemAscIdDesc(Long estudioId, String categoria);
    }
}
