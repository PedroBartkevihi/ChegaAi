package chegae.domain.model;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.OffsetDateTime;
import java.util.UUID;
@Entity
@Table(name = "comentario")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString(exclude = {"autor", "evento"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Comentario {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_comentario", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;
    @NotNull(message = "O autor do comentário é obrigatório.")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "id_usuario",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_comentario_usuario")
    )
    private Usuario autor;
    @NotNull(message = "O evento do comentário é obrigatório.")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "id_evento",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_comentario_evento")
    )
    private Evento evento;
    @NotBlank(message = "O texto do comentário não pode ser vazio.")
    @Size(min = 1, max = 2000, message = "O comentário deve ter entre 1 e 2000 caracteres.")
    @Column(name = "texto", nullable = false, columnDefinition = "text")
    private String texto;
    @CreationTimestamp
    @Column(name = "data_hora_comentario", nullable = false, updatable = false,
            columnDefinition = "timestamptz DEFAULT now()")
    private OffsetDateTime dataHoraComentario;
    public boolean pertenceAo(UUID idUsuario) {
        return autor != null && autor.getId().equals(idUsuario);
    }
}
