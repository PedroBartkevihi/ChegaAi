package chegae.repository;

import chegae.domain.model.Evento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositório JPA para a entidade {@link Evento}.
 */
@Repository
public interface EventoRepository extends JpaRepository<Evento, UUID> {

    /**
     * Busca todos os eventos com fetch join no organizador, categoria e endereço
     * para evitar N+1 queries.
     */
    @Query("SELECT e FROM Evento e " +
           "JOIN FETCH e.organizador " +
           "JOIN FETCH e.categoria " +
           "JOIN FETCH e.endereco " +
           "ORDER BY e.criadoEm DESC")
    List<Evento> findAllWithRelations();

    /**
     * Busca um evento por ID com fetch join nas relações necessárias.
     */
    @Query("SELECT e FROM Evento e " +
           "JOIN FETCH e.organizador " +
           "JOIN FETCH e.categoria " +
           "JOIN FETCH e.endereco " +
           "WHERE e.id = :id")
    Optional<Evento> findByIdWithRelations(UUID id);
}
