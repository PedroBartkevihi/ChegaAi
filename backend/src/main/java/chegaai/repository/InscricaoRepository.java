package chegaai.repository;
import chegaai.domain.enums.StatusInscricao;
import chegaai.domain.model.Inscricao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
@Repository
public interface InscricaoRepository extends JpaRepository<Inscricao, UUID> {
    Optional<Inscricao> findByUsuarioIdAndEventoId(UUID usuarioId, UUID eventoId);
    List<Inscricao> findByEventoIdAndStatusInscricao(UUID eventoId, StatusInscricao status);
    long countByEventoIdAndStatusInscricao(UUID eventoId, StatusInscricao status);
    Optional<Inscricao> findFirstByEvento_IdAndStatusInscricaoOrderByDataHoraSolicitacaoAsc(
        UUID eventoId,
        StatusInscricao status
    );
    @Query("SELECT i FROM Inscricao i " +
           "JOIN FETCH i.evento e " +
           "JOIN FETCH e.organizador " +
           "JOIN FETCH e.categoria " +
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
