package chegae.repository;

import chegae.domain.model.Evento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
     * Busca eventos de forma paginada com JOIN FETCH nas relações diretas
     * (organizador, categoria, endereco) para eliminar queries N+1.
     *
     * <p>A countQuery separada é necessária porque o Hibernate não permite
     * JOIN FETCH em queries contadas automaticamente.</p>
     *
     * @param pageable configuração de página (page, size, sort)
     * @return página de eventos com relações pré-carregadas
     */
    @Query(value = "SELECT e FROM Evento e " +
                   "JOIN FETCH e.organizador " +
                   "JOIN FETCH e.categoria " +
                   "JOIN FETCH e.endereco " +
                   "ORDER BY e.criadoEm DESC",
           countQuery = "SELECT COUNT(e) FROM Evento e")
    Page<Evento> findAllWithRelations(Pageable pageable);

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
