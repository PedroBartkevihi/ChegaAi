package chegae.repository;

import chegae.domain.model.Avaliacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositório JPA para a entidade {@link Avaliacao}.
 *
 * <p>Fornece queries para o recálculo de honra (RN002)
 * e verificação de avaliações pendentes (RN007).</p>
 */
@Repository
public interface AvaliacaoRepository extends JpaRepository<Avaliacao, UUID> {

    /**
     * Calcula a média aritmética das notas recebidas por um usuário (RN002).
     *
     * @param usuarioId ID do usuário avaliado
     * @return média das notas, ou {@code empty} se não houver avaliações
     */
    @Query("SELECT AVG(a.nota) FROM Avaliacao a WHERE a.avaliado.id = :usuarioId")
    Optional<BigDecimal> calcularMediaNotas(@Param("usuarioId") UUID usuarioId);

    /**
     * Conta quantas avaliações um usuário fez em um determinado evento.
     * Usado para verificação de avaliações pendentes (RN007).
     *
     * @param avaliadorId ID do avaliador
     * @param eventoId    ID do evento
     * @return número de avaliações feitas
     */
    @Query("SELECT COUNT(a) FROM Avaliacao a WHERE a.avaliador.id = :avaliadorId AND a.evento.id = :eventoId")
    long contarAvaliacoesFeitas(@Param("avaliadorId") UUID avaliadorId, @Param("eventoId") UUID eventoId);
}
