package chegae.repository;

import chegae.domain.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositório JPA para a entidade {@link Usuario}.
 */
@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    /**
     * Busca um usuário pelo e-mail (login simplificado para MVP).
     *
     * @param email e-mail do usuário
     * @return optional com o usuário encontrado
     */
    Optional<Usuario> findByEmail(String email);
}
