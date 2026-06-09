package chegae.domain.model;
import chegae.domain.enums.StatusEvento;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.OffsetDateTime;
import java.util.UUID;
@Entity
@Table(
    name = "avaliacao",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_avaliacao_evento_avaliador_avaliado",
        columnNames = {"id_evento", "id_avaliador", "id_avaliado"}
    )
)
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString(exclude = {"evento", "avaliador", "avaliado"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Avaliacao {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_avaliacao", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;
    @NotNull(message = "O evento da avaliação é obrigatório.")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "id_evento",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_avaliacao_evento")
    )
    private Evento evento;
    @NotNull(message = "O avaliador é obrigatório.")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "id_avaliador",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_avaliacao_avaliador")
    )
    private Usuario avaliador;
    @NotNull(message = "O avaliado é obrigatório.")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "id_avaliado",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_avaliacao_avaliado")
    )
    private Usuario avaliado;
    @NotNull(message = "A nota é obrigatória.")
    @Min(value = 1, message = "A nota mínima é 1 estrela.")
    @Max(value = 5, message = "A nota máxima é 5 estrelas.")
    @Column(name = "nota", nullable = false)
    private Integer nota;
    @Size(max = 500, message = "O comentário não pode ultrapassar 500 caracteres.")
    @Column(name = "comentario_opcional", columnDefinition = "text")
    private String comentarioOpcional;
    @CreationTimestamp
    @Column(name = "data_hora_avaliacao", nullable = false, updatable = false,
            columnDefinition = "timestamptz DEFAULT now()")
    private OffsetDateTime dataHoraAvaliacao;
    public void validar() {
        if (avaliador != null && avaliado != null
                && avaliador.getId().equals(avaliado.getId())) {
            throw new IllegalArgumentException(
                "Auto-avaliação não é permitida: avaliador e avaliado são o mesmo usuário."
            );
        }
        if (evento != null && evento.getStatusEvento() != StatusEvento.CONCLUIDO) {
            throw new IllegalStateException(
                "Somente eventos CONCLUIDO podem ser avaliados. Status atual: "
                + evento.getStatusEvento()
            );
        }
    }
    public boolean ehAutoAvaliacao() {
        return avaliador != null && avaliado != null
               && avaliador.getId().equals(avaliado.getId());
    }
}
