package chegaai.service;

import chegaai.domain.model.Usuario;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContaService {
    private final EntityManager entityManager;

    /** O evento, quando houver, deve estar travado antes da conta. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Usuario travarAtivo(UUID id) {
        Usuario usuario = travar(id);
        usuario.exigirContaAtiva(OffsetDateTime.now());
        return usuario;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Usuario travar(UUID id) {
        var ids = entityManager.createNativeQuery(
            "SELECT id_usuario FROM usuario WHERE id_usuario = :id FOR NO KEY UPDATE", UUID.class)
            .setParameter("id", id).getResultList();
        if (ids.isEmpty()) throw new NoSuchElementException("Usuário não encontrado: " + id);
        Usuario usuario = entityManager.find(Usuario.class, id);
        // A primeira leitura da conta ocorre depois da trava. Refresh em Usuario cascata para
        // eventosOrganizados e pode recolocar no contexto um evento que está sendo excluído.
        if (usuario.getStatusUsuario() == chegaai.domain.enums.StatusUsuario.BANIDO) {
            usuario.exigirContaAtiva(OffsetDateTime.now());
        }
        return usuario;
    }
}
