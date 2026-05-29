package chegae.domain.enums;

/**
 * Status da inscrição de um usuário em um evento.
 *
 * <p>Mapeado pela constraint {@code ck_inscricao_status} do DDL:
 * {@code CHECK (status_inscricao IN ('SOLICITADA', 'CONFIRMADA', 'CANCELADA', 'AUSENTE'))}</p>
 *
 * <p>Transições de estado:</p>
 * <pre>
 *  SOLICITADA ──(vaga disponível)──► CONFIRMADA
 *  SOLICITADA ──(sem vaga)──────────► FILA DE ESPERA (flag interna; status permanece SOLICITADA)
 *  CONFIRMADA ──(cancelamento)──────► CANCELADA  (verifica RN009)
 *  SOLICITADA ──(cancelamento)──────► CANCELADA
 *  CONFIRMADA ──(não compareceu)────► AUSENTE    (RN008 — dedução de honra)
 * </pre>
 *
 * <p><b>Nota FILA DE ESPERA:</b> a fila FIFO é representada pelo conjunto de
 * inscrições com status {@code SOLICITADA} ordenadas por
 * {@code dataHoraSolicitacao ASC} (RN001). Não existe um status "FILA_ESPERA"
 * separado: a posição na fila é inferida pela data de solicitação quando
 * o evento já atingiu o {@code limiteParticipantes} de inscrições
 * {@code CONFIRMADA}.</p>
 */
public enum StatusInscricao {

    /**
     * Inscrição registrada, aguardando confirmação de vaga.
     * Se o evento já estiver cheio, este registro representa
     * a posição na fila de espera FIFO (RN001).
     */
    SOLICITADA,

    /**
     * Vaga confirmada: o usuário está oficialmente inscrito
     * e pode comentar no evento (RN006) e avaliá-lo após a conclusão.
     */
    CONFIRMADA,

    /**
     * Inscrição cancelada pelo usuário ou pelo organizador.
     * Se cancelada com menos de 24h de antecedência, aciona
     * a contagem de cancelamentos tardios (RN009).
     * Se o usuário era CONFIRMADO e não compareceu sem cancelar,
     * será tratado como ausência não justificada (RN008).
     */
    CANCELADA,

    /**
     * Ausência não justificada após conclusão do evento (RN008).
     * Transição: CONFIRMADA → AUSENTE (quando compareceu == false
     * após o organizador concluir o evento).
     * Implica dedução de 0.5 na honraMedia do usuário.
     */
    AUSENTE
}
