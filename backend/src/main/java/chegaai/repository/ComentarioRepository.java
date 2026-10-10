package chegaai.repository;
import chegaai.domain.model.Comentario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;
@Repository
public interface ComentarioRepository extends JpaRepository<Comentario, UUID> {
    @Query("SELECT c FROM Comentario c " +
           "JOIN FETCH c.autor " +
           "WHERE c.evento.id = :eventoId " +
           "ORDER BY c.dataHoraComentario ASC")
    List<Comentario> findByEventoIdWithAutor(@Param("eventoId") UUID eventoId);
}
