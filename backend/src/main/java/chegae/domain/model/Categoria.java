package chegae.domain.model;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
@Entity
@Table(
    name = "categoria",
    uniqueConstraints = @UniqueConstraint(name = "uk_categoria_nome", columnNames = "nome")
)
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString(exclude = "eventos")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Categoria {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_categoria", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;
    @NotBlank(message = "O nome da categoria é obrigatório.")
    @Size(min = 2, max = 80, message = "O nome deve ter entre 2 e 80 caracteres.")
    @Column(name = "nome", nullable = false, unique = true, length = 80)
    private String nome;
    @Size(max = 2000, message = "A descrição não pode ultrapassar 2000 caracteres.")
    @Column(name = "descricao", columnDefinition = "text")
    private String descricao;
    @Column(name = "ativa", nullable = false)
    private Boolean ativa = Boolean.TRUE;
    @OneToMany(mappedBy = "categoria", fetch = FetchType.LAZY)
    private List<Evento> eventos = new ArrayList<>();
    public void inativar() {
        this.ativa = Boolean.FALSE;
    }
    public void ativar() {
        this.ativa = Boolean.TRUE;
    }
}
