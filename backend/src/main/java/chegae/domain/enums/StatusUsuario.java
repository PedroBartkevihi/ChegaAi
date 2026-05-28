package chegae.domain.enums;

/**
 * Status possíveis de uma conta de usuário na plataforma.
 *
 * <p>Mapeado pela constraint {@code ck_usuario_status} do DDL:
 * {@code CHECK (status_usuario IN ('ATIVO', 'SUSPENSO', 'INATIVO'))}</p>
 *
 * <ul>
 *   <li>{@code ATIVO}    – conta regular com acesso completo</li>
 *   <li>{@code SUSPENSO} – conta com acesso temporariamente bloqueado (RN005, RN009)</li>
 *   <li>{@code INATIVO}  – conta desativada ou banida definitivamente (RN005)</li>
 * </ul>
 */
public enum StatusUsuario {

	/** Conta regular com acesso completo à plataforma. */
	ATIVO,

	/**
	 * Conta suspensa temporariamente.
	 * Ocorre após banimento automático de 7 dias (RN005)
	 * ou cancelamentos excessivos em menos de 24h (RN009).
	 */
	SUSPENSO,

	/**
	 * Conta inativa ou banida definitivamente.
	 * Aplicada manualmente pelo moderador (UC05) ou automaticamente
	 * após acúmulo de penalidades graves.
	 */
	INATIVO
}
