package chegae.repository;

import chegae.domain.model.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositório JPA para a entidade {@link Categoria}.
 */
@Repository
public interface CategoriaRepository extends JpaRepository<Categoria, UUID> {

    /**
     * Busca uma categoria pelo nome (case-sensitive).
     */
    Optional<Categoria> findByNome(String nome);
}
