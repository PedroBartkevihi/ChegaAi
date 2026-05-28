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

/**
 * Registra formalmente um reporte de comportamento inadequado
 * feito por um usuário contra outro na plataforma.
 *
 * <h3>Auto-relacionamento</h3>
 * <p>A tabela possui duas FKs para a mesma tabela {@code usuario}:
 * {@code id_denunciante} e {@code id_denunciado} — o que configura um
 * auto-relacionamento (documentado na seção 7.2, item 8).</p>
 *
 * <h3>FK de evento opcional</h3>
 * <p>O campo {@code evento} é nullable: uma denúncia pode ou não estar
 * atrelada a um evento específico. Se o evento for deletado,
 * a FK é anulada via {@code ON DELETE SET NULL}.</p>
 *
 * <h3>Constraints</h3>
 * <ul>
 *   <li>{@code ck_denuncia_usuarios_distintos} — impede que um usuário
 *       denuncie a si mesmo ({@code id_denunciante ≠ id_denunciado}).</li>
 *   <li>FK {@code id_evento} é nullable (a denúncia pode não estar
 *       vinculada a um evento específico).</li>
 * </ul>
 *
 * <h3>Regras de Negócio relacionadas</h3>
 * <ul>
 *   <li>RN005 – 3 denúncias validadas → banimento automático de 7 dias</li>
 *   <li>UC08  – UC de reporte de usuário</li>
 *   <li>UC13  – Analisar denúncias no painel de moderação</li>
 * </ul>
 */
@Entity
@Table(name = "denuncia")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString(exclude = {"denunciante", "denunciado", "evento"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Denuncia {

    // ------------------------------------------------------------------
    // Chave primária
    // ------------------------------------------------------------------

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_denuncia", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    // ------------------------------------------------------------------
    // Auto-relacionamento: denunciante e denunciado
    // ------------------------------------------------------------------

    /**
     * Usuário que registrou a denúncia.
     * FK: {@code id_denunciante → usuario(id_usuario)}
     * ON UPDATE CASCADE / ON DELETE RESTRICT
     */
    @NotNull(message = "O denunciante é obrigatório.")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "id_denunciante",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_denuncia_denunciante")
    )
    private Usuario denunciante;

    /**
     * Usuário alvo da denúncia.
     * FK: {@code id_denunciado → usuario(id_usuario)}
     * ON UPDATE CASCADE / ON DELETE RESTRICT
     *
     * <p>Deve ser diferente do {@code denunciante}
     * (constraint {@code ck_denuncia_usuarios_distintos}).</p>
     */
    @NotNull(message = "O denunciado é obrigatório.")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "id_denunciado",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_denuncia_denunciado")
    )
    private Usuario denunciado;

    /**
     * Evento no qual ocorreu a infração — opcional.
     * FK: {@code id_evento → evento(id_evento)}
     * ON UPDATE CASCADE / ON DELETE SET NULL
     *
     * <p>Pode ser {@code null} quando a denúncia não está atrelada
     * a um evento específico.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "id_evento",
        nullable = true,
        foreignKey = @ForeignKey(name = "fk_denuncia_evento")
    )
    private Evento evento;

    // ------------------------------------------------------------------
    // Atributos
    // ------------------------------------------------------------------

    /**
     * Motivo resumido da denúncia.
     * Constraint: {@code varchar(120) NOT NULL}
     */
    @NotBlank(message = "O motivo da denúncia é obrigatório.")
    @Size(min = 5, max = 120, message = "O motivo deve ter entre 5 e 120 caracteres.")
    @Column(name = "motivo", nullable = false, length = 120)
    private String motivo;

    /**
     * Detalhes e evidências da denúncia (campo livre).
     * Constraint: {@code text} — opcional
     */
    @Size(max = 3000, message = "Os detalhes não podem ultrapassar 3000 caracteres.")
    @Column(name = "detalhes", columnDefinition = "text")
    private String detalhes;

    /**
     * Data e hora em que a denúncia foi registrada.
     * Constraint: {@code timestamptz NOT NULL DEFAULT now()}
     */
    @CreationTimestamp
    @Column(name = "data_hora_denuncia", nullable = false, updatable = false,
            columnDefinition = "timestamptz DEFAULT now()")
    private OffsetDateTime dataHoraDenuncia;

    /**
     * Status atual da denúncia no fluxo de moderação (UC13).
     * Constraint: {@code varchar(20) NOT NULL,
     * CHECK (status_denuncia IN ('ABERTA','EM_ANALISE','VALIDADA','REJEITADA','ENCERRADA'))}
     *
     * @see StatusDenuncia
     */
    @NotNull(message = "O status da denúncia é obrigatório.")
    @Enumerated(EnumType.STRING)
    @Column(name = "status_denuncia", nullable = false, length = 20)
    private StatusDenuncia statusDenuncia = StatusDenuncia.ABERTA;

    // ------------------------------------------------------------------
    // Métodos de domínio
    // ------------------------------------------------------------------

    /**
     * Abre a análise da denúncia pelo moderador.
     * Transição: ABERTA → EM_ANALISE.
     *
     * @throws IllegalStateException se a denúncia não estiver ABERTA
     */
    public void iniciarAnalise() {
        if (statusDenuncia != StatusDenuncia.ABERTA) {
            throw new IllegalStateException(
                "Somente denúncias ABERTA podem ser analisadas. Status: " + statusDenuncia
            );
        }
        this.statusDenuncia = StatusDenuncia.EM_ANALISE;
    }

    /**
     * Classifica a denúncia como procedente.
     * Transição: EM_ANALISE → VALIDADA.
     * Incrementa o contador de denúncias do denunciado na camada de serviço.
     *
     * @throws IllegalStateException se a denúncia não estiver EM_ANALISE
     */
    public void classificarComoProcedente() {
        if (statusDenuncia != StatusDenuncia.EM_ANALISE) {
            throw new IllegalStateException(
                "A denúncia deve estar EM_ANALISE para ser classificada. Status: "
                + statusDenuncia
            );
        }
        this.statusDenuncia = StatusDenuncia.VALIDADA;
    }

    /**
     * Classifica a denúncia como improcedente (spam/sem fundamento).
     * Transição: EM_ANALISE → REJEITADA.
     *
     * @throws IllegalStateException se a denúncia não estiver EM_ANALISE
     */
    public void classificarComoImprocedente() {
        if (statusDenuncia != StatusDenuncia.EM_ANALISE) {
            throw new IllegalStateException(
                "A denúncia deve estar EM_ANALISE para ser classificada. Status: "
                + statusDenuncia
            );
        }
        this.statusDenuncia = StatusDenuncia.REJEITADA;
    }

    /**
     * Encerra a denúncia após decisão final (penalidade aplicada ou arquivamento).
     * Transição: VALIDADA | REJEITADA → ENCERRADA.
     *
     * @throws IllegalStateException se a denúncia não estiver VALIDADA ou REJEITADA
     */
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

    /**
     * Indica se a denúncia ainda está aguardando ação do moderador.
     *
     * @return {@code true} se status == ABERTA ou EM_ANALISE
     */
    public boolean estaPendente() {
        return statusDenuncia == StatusDenuncia.ABERTA
               || statusDenuncia == StatusDenuncia.EM_ANALISE;
    }
}
