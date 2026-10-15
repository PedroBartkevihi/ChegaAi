package chegaai.repository;
import chegaai.domain.model.Evento;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
@Repository
public interface EventoRepository extends JpaRepository<Evento, UUID> {
    @Query("SELECT e FROM Evento e " +
           "JOIN FETCH e.organizador " +
           "JOIN FETCH e.categoria " +
           "JOIN FETCH e.endereco " +
           "ORDER BY e.criadoEm DESC")
    List<Evento> findAllWithRelations();
    @Query(value = "SELECT e FROM Evento e " +
                   "JOIN FETCH e.organizador " +
                   "JOIN FETCH e.categoria " +
                   "JOIN FETCH e.endereco " +
                   "ORDER BY e.criadoEm DESC",
           countQuery = "SELECT COUNT(e) FROM Evento e")
    Page<Evento> findAllWithRelations(Pageable pageable);
    @Query("SELECT e FROM Evento e " +
           "JOIN FETCH e.organizador " +
           "JOIN FETCH e.categoria " +
           "JOIN FETCH e.endereco " +
           "WHERE e.id = :id")
    Optional<Evento> findByIdWithRelations(UUID id);
    /**
     * SELECT ... FOR UPDATE: a linha do evento fica travada até o fim da transação,
     * e qualquer outra chamada para o mesmo evento espera a vez.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Evento e WHERE e.id = :id")
    Optional<Evento> findByIdForUpdate(UUID id);
}
