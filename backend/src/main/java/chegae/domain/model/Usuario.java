package chegae.domain.model;
import chegae.domain.enums.PapelSistema;
import chegae.domain.enums.StatusUsuario;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
@Entity
@Table(
    name = "usuario",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_usuario_email", columnNames = "email"),
        @UniqueConstraint(name = "uk_usuario_cpf",   columnNames = "cpf")
    }
)
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "papel_sistema", discriminatorType = DiscriminatorType.STRING)
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString(exclude = {"eventosOrganizados", "inscricoes", "avaliacoesFeitas",
                     "avaliacoesRecebidas", "comentarios",
                     "denunciasRegistradas", "denunciasRecebidas", "penalidades"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public abstract class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_usuario", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;
    @NotBlank(message = "O nome é obrigatório.")
    @Size(min = 2, max = 120, message = "O nome deve ter entre 2 e 120 caracteres.")
    @Column(name = "nome", nullable = false, length = 120)
    private String nome;
    @NotBlank(message = "O e-mail é obrigatório.")
    @Email(message = "O e-mail informado não é válido.")
    @Size(max = 254, message = "O e-mail deve ter no máximo 254 caracteres.")
    @Column(name = "email", nullable = false, unique = true, length = 254)
    private String email;
    @NotBlank(message = "O CPF é obrigatório.")
    @Pattern(regexp = "^[0-9]{11}$", message = "O CPF deve conter exatamente 11 dígitos numéricos.")
    @Column(name = "cpf", nullable = false, unique = true, length = 11)
    private String cpf;
    @NotBlank(message = "A senha hash é obrigatória.")
    @Column(name = "senha_hash", nullable = false, columnDefinition = "text")
    private String senhaHash;
    @NotNull(message = "A data de nascimento é obrigatória.")
    @Past(message = "A data de nascimento deve ser uma data no passado.")
    @Column(name = "data_nascimento", nullable = false)
    private LocalDate dataNascimento;
    @NotNull
    @DecimalMin(value = "0.00", message = "A honra não pode ser negativa.")
    @DecimalMax(value = "5.00", message = "A honra não pode exceder 5.00.")
    @Column(name = "honra_media", nullable = false,
            columnDefinition = "numeric(3,2) DEFAULT 0")
    private BigDecimal honraMedia = BigDecimal.ZERO;
    @NotNull(message = "O status do usuário é obrigatório.")
    @Enumerated(EnumType.STRING)
    @Column(name = "status_usuario", nullable = false, length = 20)
    private StatusUsuario statusUsuario = StatusUsuario.ATIVO;
    @Min(value = 0, message = "A quantidade de denúncias não pode ser negativa.")
    @Column(name = "qtd_denuncias_validadas", nullable = false)
    private Integer qtdDenunciasValidadas = 0;
    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;
    @OneToMany(mappedBy = "organizador", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Evento> eventosOrganizados = new ArrayList<>();
    @OneToMany(mappedBy = "usuario", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Inscricao> inscricoes = new ArrayList<>();
    @OneToMany(mappedBy = "avaliador", fetch = FetchType.LAZY)
    private List<Avaliacao> avaliacoesFeitas = new ArrayList<>();
    @OneToMany(mappedBy = "avaliado", fetch = FetchType.LAZY)
    private List<Avaliacao> avaliacoesRecebidas = new ArrayList<>();
    @OneToMany(mappedBy = "autor", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Comentario> comentarios = new ArrayList<>();
    @OneToMany(mappedBy = "denunciante", fetch = FetchType.LAZY)
    private List<Denuncia> denunciasRegistradas = new ArrayList<>();
    @OneToMany(mappedBy = "denunciado", fetch = FetchType.LAZY)
    private List<Denuncia> denunciasRecebidas = new ArrayList<>();
    @OneToMany(mappedBy = "usuarioPenalizado", fetch = FetchType.LAZY)
    private List<Penalidade> penalidades = new ArrayList<>();
    public abstract PapelSistema getPapelSistema();
    public boolean podeCriarEvento(BigDecimal honraMinimaParaCriacao) {
        return statusUsuario == StatusUsuario.ATIVO
               && honraMedia.compareTo(honraMinimaParaCriacao) >= 0;
    }
    public boolean podeSeInscrever() {
        return statusUsuario == StatusUsuario.ATIVO;
    }
    public int registrarDenunciaValidada() {
        this.qtdDenunciasValidadas++;
        return this.qtdDenunciasValidadas;
    }
    public boolean qualificaEmbaixador(BigDecimal thresholdEmbaixador) {
        return honraMedia.compareTo(thresholdEmbaixador) >= 0;
    }
    public boolean deveSerBanidoAutomaticamente(BigDecimal honraLimite,
                                                int limiteDenunciasValidadas) {
        return honraMedia.compareTo(honraLimite) < 0
               || qtdDenunciasValidadas >= limiteDenunciasValidadas;
    }
}
