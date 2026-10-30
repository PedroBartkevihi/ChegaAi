package chegaai.repository;
import chegaai.domain.enums.StatusInscricao;
import chegaai.domain.model.Inscricao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
@Repository
public interface InscricaoRepository extends JpaRepository<Inscricao, UUID> {
    Optional<Inscricao> findByUsuarioIdAndEventoId(UUID usuarioId, UUID eventoId);
    @Query("SELECT i FROM Inscricao i WHERE i.evento.id = :eventoId " +
           "AND i.statusInscricao = :status ORDER BY i.ordemEntrada ASC")
    List<Inscricao> findByEventoIdAndStatusInscricao(@Param("eventoId") UUID eventoId,
                                                  @Param("status") StatusInscricao status);
    long countByEventoIdAndStatusInscricao(UUID eventoId, StatusInscricao status);
    /** Contagens de vários rolés numa consulta só, para o feed não fazer uma por rolé. */
    @Query("SELECT new chegaai.repository.ContagemInscricoes(i.evento.id, i.statusInscricao, COUNT(i)) " +
           "FROM Inscricao i WHERE i.evento.id IN :eventoIds AND i.statusInscricao IN :status " +
           "GROUP BY i.evento.id, i.statusInscricao")
    List<ContagemInscricoes> contarPorEventoEStatus(@Param("eventoIds") Collection<UUID> eventoIds,
                                                    @Param("status") Collection<StatusInscricao> status);
    @Query("SELECT i FROM Inscricao i JOIN FETCH i.usuario WHERE i.evento.id = :eventoId " +
           "AND i.statusInscricao IN :status ORDER BY i.ordemEntrada ASC")
    List<Inscricao> listarParticipantes(@Param("eventoId") UUID eventoId, @Param("status") List<StatusInscricao> status);
    @Query(value = "SELECT nextval('inscricao_ordem_entrada_seq')", nativeQuery = true)
    long proximaOrdemEntrada();
    @Query("SELECT i FROM Inscricao i " +
           "JOIN FETCH i.evento e " +
           "JOIN FETCH e.organizador " +
           "JOIN FETCH e.categoria " +
           "JOIN FETCH e.endereco " +
           "WHERE i.usuario.id = :usuarioId " +
           "AND i.statusInscricao <> 'CANCELADA'")
    List<Inscricao> findActiveByUsuarioId(@Param("usuarioId") UUID usuarioId);
    @Query("SELECT i FROM Inscricao i " +
           "JOIN FETCH i.evento e " +
           "JOIN FETCH e.organizador " +
           "WHERE i.usuario.id = :usuarioId " +
           "AND i.statusInscricao = 'CONFIRMADA' " +
           "AND i.compareceu = true " +
           "AND e.statusEvento = 'CONCLUIDO'")
    List<Inscricao> findCompareceuEmEventosConcluidos(@Param("usuarioId") UUID usuarioId);
}
