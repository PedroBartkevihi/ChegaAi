package chegae.domain.model;

import chegae.domain.enums.StatusInscricao;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entidade associativa que resolve o relacionamento N:M entre {@link Usuario}
 * e {@link Evento}, registrando as inscrições e a participação efetiva.
 *
 * <h3>Fila FIFO (RN001)</h3>
 * <p>A fila de espera é representada pelo conjunto de inscrições com status
 * {@code SOLICITADA} cujo evento já atingiu o {@code limiteParticipantes}
 * de inscrições {@code CONFIRMADA}. A ordem da fila é determinada pelo
 * campo {@code dataHoraSolicitacao ASC} — o "First In" da política FIFO.</p>
 *
 * <h3>Constraint de unicidade</h3>
 * <p>A combinação {@code (id_usuario, id_evento)} deve ser única,
 * impedindo que um mesmo usuário ocupe múltiplas posições no mesmo evento
 * (constraint {@code uk_inscricao_usuario_evento}).</p>
 *
 * <h3>Tratamento de concorrência (RNF003)</h3>
 * <p>A promoção de SOLICITADA → CONFIRMADA ocorre no serviço
 * {@code InscricaoService} com uso de {@code SELECT FOR UPDATE SKIP LOCKED}
 * via JPQL ou query nativa para garantir que dois usuários não ocupem
 * simultaneamente a mesma e última vaga.</p>
 */
@Entity
@Table(
    name = "inscricao",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_inscricao_usuario_evento",
        columnNames = {"id_usuario", "id_evento"}
    )
)
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString(exclude = {"usuario", "evento"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Inscricao {

    // ------------------------------------------------------------------
    // Chave primária
    // ------------------------------------------------------------------

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_inscricao", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    // ------------------------------------------------------------------
    // Chaves estrangeiras
    // ------------------------------------------------------------------

    /**
     * Usuário inscrito (participante ou organizador como participante).
     * FK: {@code id_usuario → usuario(id_usuario)}
     * ON UPDATE CASCADE / ON DELETE CASCADE
     */
    @NotNull(message = "O usuário da inscrição é obrigatório.")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "id_usuario",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_inscricao_usuario")
    )
    private Usuario usuario;

    /**
     * Evento ao qual o usuário se inscreveu.
     * FK: {@code id_evento → evento(id_evento)}
     * ON UPDATE CASCADE / ON DELETE CASCADE
     */
    @NotNull(message = "O evento da inscrição é obrigatório.")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "id_evento",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_inscricao_evento")
    )
    private Evento evento;

    // ------------------------------------------------------------------
    // Atributos
    // ------------------------------------------------------------------

    /**
     * Data e hora em que a inscrição foi solicitada.
     * Gerado automaticamente no momento da persistência.
     *
     * <p><b>Campo FIFO:</b> este timestamp é a chave de ordenação da fila
     * de espera. Não deve ser alterado após a criação (RN001).</p>
     *
     * Constraint: {@code timestamptz NOT NULL DEFAULT now()}
     */
    @CreationTimestamp
    @Column(name = "data_hora_solicitacao", nullable = false, updatable = false,
            columnDefinition = "timestamptz DEFAULT now()")
    private OffsetDateTime dataHoraSolicitacao;

    /**
     * Status atual da inscrição.
     * Constraint: {@code varchar(20) NOT NULL,
     * CHECK (status_inscricao IN ('SOLICITADA','CONFIRMADA','CANCELADA','AUSENTE'))}
     *
     * @see StatusInscricao
     */
    @NotNull(message = "O status da inscrição é obrigatório.")
    @Enumerated(EnumType.STRING)
    @Column(name = "status_inscricao", nullable = false, length = 20)
    private StatusInscricao statusInscricao;

    /**
     * Indica se o usuário efetivamente compareceu ao evento.
     * Atualizado pelo organizador ou pelo sistema após a realização.
     *
     * <p>Se {@code statusInscricao == CONFIRMADA} e {@code compareceu == false}
     * após a data do evento, configura ausência não justificada → dedução de 0.5
     * na honra (RN008), desde que o usuário não tenha cancelado com ≥ 24h
     * de antecedência.</p>
     *
     * Constraint: {@code boolean NOT NULL DEFAULT false}
     */
    @Column(name = "compareceu", nullable = false)
    private Boolean compareceu = Boolean.FALSE;

    // ------------------------------------------------------------------
    // Métodos de domínio
    // ------------------------------------------------------------------

    /**
     * Confirma a inscrição do usuário (promoção da fila para vaga).
     * Transição: SOLICITADA → CONFIRMADA.
     *
     * @throws IllegalStateException se a inscrição não estiver SOLICITADA
     */
    public void confirmar() {
        if (statusInscricao != StatusInscricao.SOLICITADA) {
            throw new IllegalStateException(
                "Somente inscrições SOLICITADA podem ser confirmadas. Status atual: "
                + statusInscricao
            );
        }
        this.statusInscricao = StatusInscricao.CONFIRMADA;
    }

    /**
     * Cancela a inscrição.
     * Transição: SOLICITADA | CONFIRMADA → CANCELADA.
     *
     * <p>A verificação de cancelamento tardio (RN009) e dedução de honra
     * por ausência (RN008) são tratadas na camada de serviço.</p>
     *
     * @throws IllegalStateException se a inscrição já estiver cancelada
     */
    public void cancelar() {
        if (statusInscricao == StatusInscricao.CANCELADA) {
            throw new IllegalStateException("A inscrição já está cancelada.");
        }
        this.statusInscricao = StatusInscricao.CANCELADA;
    }

    /**
     * Registra que o usuário compareceu ao evento.
     *
     * @throws IllegalStateException se a inscrição não estiver CONFIRMADA
     */
    public void registrarPresenca() {
        if (statusInscricao != StatusInscricao.CONFIRMADA) {
            throw new IllegalStateException(
                "Somente inscrições CONFIRMADA podem registrar presença."
            );
        }
        this.compareceu = Boolean.TRUE;
    }

    /**
     * Registra ausência não justificada.
     * A dedução de 0.5 na honra (RN008) é processada pelo serviço de avaliação.
     */
    public void registrarAusencia() {
        this.compareceu = Boolean.FALSE;
    }

    /**
     * Marca a inscrição como AUSENTE (RN008).
     * Transição: CONFIRMADA → AUSENTE.
     *
     * <p>Chamado pelo serviço ao concluir um evento quando o participante
     * confirmado não compareceu ({@code compareceu == false}).</p>
     *
     * @throws IllegalStateException se a inscrição não estiver CONFIRMADA
     */
    public void marcarAusente() {
        if (statusInscricao != StatusInscricao.CONFIRMADA) {
            throw new IllegalStateException(
                "Somente inscrições CONFIRMADA podem ser marcadas como AUSENTE. Status atual: "
                + statusInscricao
            );
        }
        this.statusInscricao = StatusInscricao.AUSENTE;
        this.compareceu = Boolean.FALSE;
    }

    /**
     * Verifica se a inscrição está confirmada.
     *
     * @return {@code true} se status == CONFIRMADA
     */
    public boolean estaConfirmada() {
        return StatusInscricao.CONFIRMADA == statusInscricao;
    }

    /**
     * Verifica se a inscrição está na fila de espera.
     * Na modelagem adotada, a fila é o conjunto de inscrições SOLICITADAS
     * em eventos que já atingiram o limite de vagas confirmadas.
     *
     * @return {@code true} se status == SOLICITADA
     */
    public boolean estaEmFilaDeEspera() {
        return StatusInscricao.SOLICITADA == statusInscricao;
    }
}
