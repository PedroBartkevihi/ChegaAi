package chegae.repository;

import chegae.domain.model.Comentario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repositório JPA para a entidade {@link Comentario}.
 */
@Repository
public interface ComentarioRepository extends JpaRepository<Comentario, UUID> {

    /**
     * Lista comentários de um evento em ordem cronológica com fetch join no autor.
     */
    @Query("SELECT c FROM Comentario c " +
           "JOIN FETCH c.autor " +
           "WHERE c.evento.id = :eventoId " +
           "ORDER BY c.dataHoraComentario ASC")
    List<Comentario> findByEventoIdWithAutor(@Param("eventoId") UUID eventoId);
}
