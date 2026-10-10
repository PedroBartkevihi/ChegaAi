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
    @Query("SELECT COUNT(a) FROM Avaliacao a WHERE a.avaliador.id = :avaliadorId AND a.evento.id = :eventoId")
    long contarAvaliacoesFeitas(@Param("avaliadorId") UUID avaliadorId, @Param("eventoId") UUID eventoId);
    boolean existsByAvaliador_IdAndAvaliado_IdAndEvento_Id(UUID avaliadorId, UUID avaliadoId, UUID eventoId);
}
