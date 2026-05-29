package chegae.domain.enums;

/**
 * Tipos de penalidade aplicáveis a usuários da plataforma.
 *
 * <p>Mapeado pela constraint {@code ck_penalidade_tipo} do DDL:
 * {@code CHECK (tipo_penalidade IN ('ADVERTENCIA', 'SUSPENSAO', 'BANIMENTO'))}</p>
 *
 * <p>Escalonamento previsto pelo sistema de moderação (UC05, UC13):</p>
 * <ul>
 *   <li>{@code ADVERTENCIA} – infração leve; registrada no histórico sem
 *       bloquear a conta.</li>
 *   <li>{@code SUSPENSAO}   – infração média; bloqueia o acesso por período
 *       definido em {@code dataFim} (status do usuário: {@code SUSPENSO}).</li>
 *   <li>{@code BANIMENTO}   – infração grave ou reincidência; pode ser
 *       permanente (dataFim = null) ou temporária de 7 dias (RN005 automático).
 *       Status do usuário: {@code BANIDO}.</li>
 * </ul>
 */
public enum TipoPenalidade {

    /** Aviso formal sem bloqueio de conta. Infração leve. */
    ADVERTENCIA,

    /**
     * Bloqueio temporário de acesso.
     * Duração definida pelo moderador ou por regra automática (RN009: 7 dias).
     * Define status do usuário como {@code SUSPENSO}.
     */
    SUSPENSAO,

    /**
     * Banimento da plataforma.
     * Pode ser permanente (dataFim = null) ou de 7 dias (RN005 automático).
     * Define status do usuário como {@code BANIDO}.
     */
    BANIMENTO
}
