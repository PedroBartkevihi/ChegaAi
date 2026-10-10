package chegaai.domain.model;
import chegaai.domain.enums.StatusInscricao;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.OffsetDateTime;
import java.util.UUID;
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
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_inscricao", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;
    @NotNull(message = "O usuário da inscrição é obrigatório.")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "id_usuario",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_inscricao_usuario")
    )
    private Usuario usuario;
    @NotNull(message = "O evento da inscrição é obrigatório.")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "id_evento",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_inscricao_evento")
    )
    private Evento evento;
    @CreationTimestamp
    @Column(name = "data_hora_solicitacao", nullable = false, updatable = false,
            columnDefinition = "timestamptz DEFAULT now()")
    private OffsetDateTime dataHoraSolicitacao;
    @NotNull(message = "O status da inscrição é obrigatório.")
    @Enumerated(EnumType.STRING)
    @Column(name = "status_inscricao", nullable = false, length = 20)
    private StatusInscricao statusInscricao;
    @Column(name = "compareceu", nullable = false)
    private Boolean compareceu = Boolean.FALSE;
    public void confirmar() {
        if (statusInscricao != StatusInscricao.SOLICITADA) {
            throw new IllegalStateException(
                "Somente inscrições SOLICITADA podem ser confirmadas. Status atual: "
                + statusInscricao
            );
        }
        this.statusInscricao = StatusInscricao.CONFIRMADA;
    }
    public void cancelar() {
        if (statusInscricao == StatusInscricao.CANCELADA) {
            throw new IllegalStateException("A inscrição já está cancelada.");
        }
        this.statusInscricao = StatusInscricao.CANCELADA;
    }
    public void registrarPresenca() {
        if (statusInscricao != StatusInscricao.CONFIRMADA) {
            throw new IllegalStateException(
                "Somente inscrições CONFIRMADA podem registrar presença."
            );
        }
        this.compareceu = Boolean.TRUE;
    }
    public void registrarAusencia() {
        this.compareceu = Boolean.FALSE;
    }
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
    public boolean estaConfirmada() {
        return StatusInscricao.CONFIRMADA == statusInscricao;
    }
    public boolean estaEmFilaDeEspera() {
        return StatusInscricao.SOLICITADA == statusInscricao;
    }
}
