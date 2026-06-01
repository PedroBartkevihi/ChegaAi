package chegae.repository;

import chegae.domain.enums.StatusInscricao;
import chegae.domain.model.Inscricao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositório JPA para a entidade {@link Inscricao}.
 */
@Repository
public interface InscricaoRepository extends JpaRepository<Inscricao, UUID> {

    /**
     * Busca inscrição de um usuário em um evento específico.
     */
    Optional<Inscricao> findByUsuarioIdAndEventoId(UUID usuarioId, UUID eventoId);

    /**
     * Lista inscrições de um evento com um determinado status.
     */
    List<Inscricao> findByEventoIdAndStatusInscricao(UUID eventoId, StatusInscricao status);

    /**
     * Conta quantas inscrições com determinado status existem em um evento.
     */
    long countByEventoIdAndStatusInscricao(UUID eventoId, StatusInscricao status);

    /**
     * Busca a primeira inscrição SOLICITADA (fila FIFO) para um evento,
     * ordenada pela data de solicitação ascendente.
     * Usada na promoção automática quando alguém cancela.
     */
    Optional<Inscricao> findFirstByEvento_IdAndStatusInscricaoOrderByDataHoraSolicitacaoAsc(
        UUID eventoId,
        StatusInscricao status
    );

    /**
     * Lista todas as inscrições de um usuário (para o perfil).
     */
    @Query("SELECT i FROM Inscricao i " +
           "JOIN FETCH i.evento e " +
           "JOIN FETCH e.organizador " +
           "JOIN FETCH e.categoria " +
           "WHERE i.usuario.id = :usuarioId " +
           "AND i.statusInscricao <> 'CANCELADA'")
    List<Inscricao> findActiveByUsuarioId(@Param("usuarioId") UUID usuarioId);

    /**
     * Busca inscrições CONFIRMADA com compareceu=true em eventos CONCLUIDO
     * para um dado usuário. Usada para verificação de avaliações pendentes (RN007).
     */
    @Query("SELECT i FROM Inscricao i " +
           "JOIN FETCH i.evento e " +
           "JOIN FETCH e.organizador " +
           "WHERE i.usuario.id = :usuarioId " +
           "AND i.statusInscricao = 'CONFIRMADA' " +
           "AND i.compareceu = true " +
           "AND e.statusEvento = 'CONCLUIDO'")
    List<Inscricao> findCompareceuEmEventosConcluidos(@Param("usuarioId") UUID usuarioId);
}
