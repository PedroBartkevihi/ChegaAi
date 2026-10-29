package chegaai.repository;
import chegaai.domain.model.Avaliacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;
@Repository
public interface AvaliacaoRepository extends JpaRepository<Avaliacao, UUID> {
    @Query("SELECT AVG(a.nota) FROM Avaliacao a WHERE a.avaliado.id = :usuarioId")
    Optional<Double> calcularMediaNotas(@Param("usuarioId") UUID usuarioId);
    long countByAvaliado_Id(UUID usuarioId);
    @Query("SELECT a.avaliado.id FROM Avaliacao a WHERE a.avaliador.id = :usuarioId AND a.evento.id = :eventoId")
    java.util.List<UUID> buscarIdsAvaliados(@Param("usuarioId") UUID usuarioId, @Param("eventoId") UUID eventoId);
    boolean existsByAvaliador_IdAndAvaliado_IdAndEvento_Id(UUID avaliadorId, UUID avaliadoId, UUID eventoId);
}
