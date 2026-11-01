package chegaai.repository;
import chegaai.domain.model.Evento;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
@Repository
public interface EventoRepository extends JpaRepository<Evento, UUID>, EventoFeedRepository {
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
    /** Rolés ativos que a pessoa organiza e que ainda não começaram: saem junto com a conta. */
    @Query("SELECT e.id FROM Evento e WHERE e.organizador.id = :usuarioId " +
           "AND e.statusEvento = 'ATIVO' AND e.dataHoraRealizacao > :agora")
    List<UUID> idsDosFuturosOrganizados(@Param("usuarioId") UUID usuarioId, @Param("agora") OffsetDateTime agora);
    /** Um rolé que já começou e não foi concluído ainda depende de quem organiza (presenças e conclusão). */
    @Query("SELECT COUNT(e) > 0 FROM Evento e WHERE e.organizador.id = :usuarioId " +
           "AND e.statusEvento = 'ATIVO' AND e.dataHoraRealizacao <= :agora")
    boolean organizaRoleEmAndamento(@Param("usuarioId") UUID usuarioId, @Param("agora") OffsetDateTime agora);
}
