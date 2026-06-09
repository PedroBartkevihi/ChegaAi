package chegae.domain.model;
import chegae.domain.enums.StatusDenuncia;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.OffsetDateTime;
import java.util.UUID;
@Entity
@Table(name = "denuncia")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString(exclude = {"denunciante", "denunciado", "evento"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Denuncia {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_denuncia", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;
    @NotNull(message = "O denunciante é obrigatório.")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "id_denunciante",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_denuncia_denunciante")
    )
    private Usuario denunciante;
    @NotNull(message = "O denunciado é obrigatório.")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "id_denunciado",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_denuncia_denunciado")
    )
    private Usuario denunciado;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "id_evento",
        nullable = true,
        foreignKey = @ForeignKey(name = "fk_denuncia_evento")
    )
    private Evento evento;
    @NotBlank(message = "O motivo da denúncia é obrigatório.")
    @Size(min = 5, max = 120, message = "O motivo deve ter entre 5 e 120 caracteres.")
    @Column(name = "motivo", nullable = false, length = 120)
    private String motivo;
    @Size(max = 3000, message = "Os detalhes não podem ultrapassar 3000 caracteres.")
    @Column(name = "detalhes", columnDefinition = "text")
    private String detalhes;
    @CreationTimestamp
    @Column(name = "data_hora_denuncia", nullable = false, updatable = false,
            columnDefinition = "timestamptz DEFAULT now()")
    private OffsetDateTime dataHoraDenuncia;
    @NotNull(message = "O status da denúncia é obrigatório.")
    @Enumerated(EnumType.STRING)
    @Column(name = "status_denuncia", nullable = false, length = 20)
    private StatusDenuncia statusDenuncia = StatusDenuncia.ABERTA;
    public void iniciarAnalise() {
        if (statusDenuncia != StatusDenuncia.ABERTA) {
            throw new IllegalStateException(
                "Somente denúncias ABERTA podem ser analisadas. Status: " + statusDenuncia
            );
        }
        this.statusDenuncia = StatusDenuncia.EM_ANALISE;
    }
    public void classificarComoProcedente() {
        if (statusDenuncia != StatusDenuncia.EM_ANALISE) {
            throw new IllegalStateException(
                "A denúncia deve estar EM_ANALISE para ser classificada. Status: "
                + statusDenuncia
            );
        }
        this.statusDenuncia = StatusDenuncia.VALIDADA;
    }
    public void classificarComoImprocedente() {
        if (statusDenuncia != StatusDenuncia.EM_ANALISE) {
            throw new IllegalStateException(
                "A denúncia deve estar EM_ANALISE para ser classificada. Status: "
                + statusDenuncia
            );
        }
        this.statusDenuncia = StatusDenuncia.REJEITADA;
    }
    public void encerrar() {
        if (statusDenuncia != StatusDenuncia.VALIDADA
                && statusDenuncia != StatusDenuncia.REJEITADA) {
            throw new IllegalStateException(
                "Somente denúncias VALIDADA ou REJEITADA podem ser encerradas. Status: "
                + statusDenuncia
            );
        }
        this.statusDenuncia = StatusDenuncia.ENCERRADA;
    }
    public boolean estaPendente() {
        return statusDenuncia == StatusDenuncia.ABERTA
               || statusDenuncia == StatusDenuncia.EM_ANALISE;
    }
}
