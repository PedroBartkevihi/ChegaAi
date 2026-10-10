package chegaai.domain.model;
import chegaai.domain.enums.TipoPenalidade;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
@Entity
@Table(name = "penalidade")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString(exclude = {"usuarioPenalizado", "adminAplicador"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Penalidade {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_penalidade", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;
    @NotNull(message = "O usuário penalizado é obrigatório.")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "id_usuario",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_penalidade_usuario")
    )
    private Usuario usuarioPenalizado;
    @NotNull(message = "O administrador aplicador é obrigatório.")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "id_admin_aplicador",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_penalidade_admin")
    )
    private Usuario adminAplicador;
    @NotNull(message = "O tipo de penalidade é obrigatório.")
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_penalidade", nullable = false, length = 20)
    private TipoPenalidade tipoPenalidade;
    @NotBlank(message = "O motivo da penalidade é obrigatório.")
    @Size(min = 5, max = 200, message = "O motivo deve ter entre 5 e 200 caracteres.")
    @Column(name = "motivo", nullable = false, length = 200)
    private String motivo;
    @NotNull(message = "A data de início é obrigatória.")
    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;
    @Column(name = "data_fim")
    private LocalDate dataFim;
    @Column(name = "ativa", nullable = false)
    private Boolean ativa = Boolean.TRUE;
    public void encerrar() {
        this.ativa = Boolean.FALSE;
    }
    public boolean estaAtiva() {
        if (!ativa) {
            return false;
        }
        LocalDate hoje = LocalDate.now();
        if (dataFim == null) {
            return true;
        }
        return !hoje.isAfter(dataFim);
    }
    public boolean ehBanimentoPermanente() {
        return TipoPenalidade.BANIMENTO == tipoPenalidade && dataFim == null;
    }
}
