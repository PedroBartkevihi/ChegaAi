package chegaai.service;

import chegaai.domain.model.Usuario;
import chegaai.repository.AvaliacaoRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class HonraService {
    private final EntityManager entityManager;
    private final AvaliacaoRepository avaliacaoRepository;

    @Value("${chegaai.regras.quorum-minimo-avaliacao:3}")
    private long quorumMinimo;
    @Value("${chegaai.regras.deducao-honra-ausencia:0.5}")
    private BigDecimal deducaoAusencia;

    /** A transação chamadora trava primeiro o evento e depois todos os usuários em ordem estável. */
    public Map<UUID, Usuario> travarUsuarios(Collection<UUID> ids) {
        Map<UUID, Usuario> usuarios = new LinkedHashMap<>();
        ids.stream().distinct().sorted(Comparator.comparing(UUID::toString)).forEach(id -> {
            // NO KEY UPDATE serializa as mudanças, preservando os KEY SHARE das chaves estrangeiras.
            // O refresh também cobre usuários já carregados antes de outra transação ter concluído.
            if (entityManager.createNativeQuery(
                    "SELECT id_usuario FROM usuario WHERE id_usuario = :id FOR NO KEY UPDATE")
                    .setParameter("id", id).getResultList().isEmpty()) {
                throw new NoSuchElementException("Usuário não encontrado: " + id);
            }
            Usuario usuario = entityManager.find(Usuario.class, id);
            entityManager.refresh(usuario);
            usuarios.put(id, usuario);
        });
        return usuarios;
    }

    public void registrarAusencia(Usuario usuario) {
        usuario.setDescontoHonraAcumulado(usuario.getDescontoHonraAcumulado().add(deducaoAusencia));
        recalcular(usuario);
    }

    /** O usuário deve ter sido adquirido por travarUsuarios nesta mesma transação. */
    public void recalcular(Usuario usuario) {
        long notasRecebidas = avaliacaoRepository.countByAvaliado_Id(usuario.getId());
        BigDecimal base = usuario.getHonraInicial();
        if (notasRecebidas >= quorumMinimo) {
            base = BigDecimal.valueOf(avaliacaoRepository.calcularMediaNotas(usuario.getId()).orElseThrow());
        }
        usuario.setHonraMedia(base.subtract(usuario.getDescontoHonraAcumulado())
            .max(BigDecimal.ZERO).min(BigDecimal.valueOf(5)).setScale(2, RoundingMode.HALF_UP));
    }
}
