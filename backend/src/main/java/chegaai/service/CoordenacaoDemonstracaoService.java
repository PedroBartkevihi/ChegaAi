package chegaai.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Mutações convivem entre si; somente a reconstrução dos cenários pede acesso exclusivo. */
@Service
@RequiredArgsConstructor
public class CoordenacaoDemonstracaoService {
    private final EntityManager entityManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public void aguardarRecriacao() {
        // Sempre antes das travas de evento e conta. O seed usa a versão exclusiva da mesma chave.
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock_shared(746283901)").getResultList();
    }
}
