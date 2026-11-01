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
    /** Quem a pessoa já avaliou em cada um dos rolés, numa consulta só, para as pendências não fazerem uma por rolé. */
    @Query("SELECT new chegaai.repository.AvaliadoNoEvento(a.evento.id, a.avaliado.id) FROM Avaliacao a " +
           "WHERE a.avaliador.id = :usuarioId AND a.evento.id IN :eventoIds")
    java.util.List<AvaliadoNoEvento> buscarAvaliados(@Param("usuarioId") UUID usuarioId,
                                                     @Param("eventoIds") java.util.Collection<UUID> eventoIds);
    boolean existsByAvaliador_IdAndAvaliado_IdAndEvento_Id(UUID avaliadorId, UUID avaliadoId, UUID eventoId);
}
