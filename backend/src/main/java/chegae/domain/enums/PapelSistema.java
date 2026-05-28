package chegae.domain.enums;

/**
 * Papéis (roles) de um usuário no sistema ChegAê.
 *
 * <p>Mapeado pela constraint {@code ck_usuario_papel} do DDL:
 * {@code CHECK (papel_sistema IN ('ADMINISTRADOR', 'EMBAIXADOR', 'PARTICIPANTE'))}</p>
 *
 * <p>Implementa a herança lógica (Single Table) entre os atores do diagrama de classes:
 * Participante → Embaixador → Organizador (papel contextual), Administrador/Moderador.</p>
 */
public enum PapelSistema {

	/**
	 * Usuário padrão da plataforma.
	 * Pode participar de eventos, avaliar outros usuários,
	 * criar eventos (desde que RN004 não esteja ativa) e
	 * funcionar como organizador no contexto de seus próprios eventos.
	 */
	PARTICIPANTE,

	/**
	 * Usuário com honra ≥ 4.8 (RN003).
	 * Herda todos os privilégios de PARTICIPANTE e adicionalmente:
	 * pode criar eventos com limite de até 50 vagas e tem seus
	 * eventos destacados no feed (posição prioritária).
	 */
	EMBAIXADOR,

	/**
	 * Equipe de suporte e moderação da plataforma.
	 * Possui acesso ao painel exclusivo de moderação (RF006),
	 * pode analisar denúncias (UC13), banir/suspender contas (UC05),
	 * travar eventos (RF009) e gerenciar categorias (RF008).
	 */
	ADMINISTRADOR
}
