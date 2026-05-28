package chegae.domain.enums;

/**
 * Status do ciclo de vida de um evento.
 *
 * <p>Mapeado pela constraint {@code ck_evento_status} do DDL:
 * {@code CHECK (status_evento IN ('ATIVO', 'CANCELADO', 'CONCLUIDO'))}</p>
 *
 * <p>O status {@code TRAVADO} é um estado lógico gerenciado pela aplicação
 * e pelo moderador (RF009): impede novas inscrições enquanto uma denúncia
 * de local perigoso está sendo averiguada. No banco, um evento travado
 * permanece com status {@code ATIVO}, mas o campo {@code travado} (flag
 * na entidade) sinaliza a restrição operacional.</p>
 */
public enum StatusEvento {

	/**
	 * Evento aberto: aceita inscrições ou fila de espera.
	 * Um evento ATIVO pode estar operacionalmente "travado"
	 * pelo moderador (flag separada na entidade).
	 */
	ATIVO,

	/**
	 * Evento cancelado pelo organizador (UC03 – FA3) ou pelo moderador.
	 * Inscritos são notificados e nenhuma avaliação é possível.
	 */
	CANCELADO,

	/**
	 * Evento concluído após a data de realização.
	 * Habilita o processo de avaliação mútua (UC06) desde que
	 * atinja o quórum mínimo de 3 participantes confirmados além
	 * do organizador (RN010).
	 */
	CONCLUIDO
}
