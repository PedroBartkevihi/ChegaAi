package chegae.domain.model;

import chegae.domain.enums.TipoPenalidade;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Registra as sanções disciplinares aplicadas a usuários da plataforma.
 *
 * <p>Serve como histórico rastreável das ações de moderação (UC05, UC13),
 * permitindo verificar advertências anteriores, suspensões ativas e
 * banimentos definitivos.</p>
 *
 * <h3>Dois FKs para a tabela Usuario</h3>
 * <ul>
 *   <li>{@code usuarioPenalizado}  – conta que recebeu a sanção</li>
 *   <li>{@code adminAplicador}     – moderador/administrador que aplicou a penalidade
 *       (ou {@code null} se foi um banimento automático gerado pelo sistema — RN005)</li>
 * </ul>
 *
 * <h3>Constraint de integridade</h3>
 * <ul>
 *   <li>{@code ck_penalidade_usuarios_distintos} – admin não pode penalizar a si mesmo.</li>
 *   <li>{@code ck_penalidade_periodo} – dataFim, se presente, deve ser ≥ dataInicio.</li>
 *   <li>{@code dataFim = null} indica banimento permanente.</li>
 * </ul>
 *
 * <h3>Regras de Negócio relacionadas</h3>
 * <ul>
 *   <li>RN005 – banimento automático de 7 dias quando honra &lt; 1.0 ou ≥ 3 denúncias</li>
 *   <li>RN009 – suspensão de 7 dias por cancelamentos excessivos</li>
 * </ul>
 */
@Entity
@Table(name = "penalidade")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString(exclude = {"usuarioPenalizado", "adminAplicador"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Penalidade {

    // ------------------------------------------------------------------
    // Chave primária
    // ------------------------------------------------------------------

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_penalidade", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    // ------------------------------------------------------------------
    // Chaves estrangeiras (dois FKs para a tabela usuario)
    // ------------------------------------------------------------------

    /**
     * Usuário que recebeu a penalidade.
     * FK: {@code id_usuario → usuario(id_usuario)}
     * ON UPDATE CASCADE / ON DELETE RESTRICT
     */
    @NotNull(message = "O usuário penalizado é obrigatório.")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "id_usuario",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_penalidade_usuario")
    )
    private Usuario usuarioPenalizado;

    /**
     * Administrador ou moderador que aplicou a penalidade.
     * FK: {@code id_admin_aplicador → usuario(id_usuario)}
     * ON UPDATE CASCADE / ON DELETE RESTRICT
     *
     * <p>Para banimentos automáticos gerados pelo sistema (RN005),
     * este campo referencia a conta de sistema/admin padrão.
     * O serviço {@code BanimentoAutomaticoJob} é responsável por
     * definir essa referência.</p>
     */
    @NotNull(message = "O administrador aplicador é obrigatório.")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "id_admin_aplicador",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_penalidade_admin")
    )
    private Usuario adminAplicador;

    // ------------------------------------------------------------------
    // Atributos
    // ------------------------------------------------------------------

    /**
     * Tipo da penalidade aplicada.
     * Constraint: {@code varchar(20) NOT NULL,
     * CHECK (tipo_penalidade IN ('ADVERTENCIA','SUSPENSAO','BANIMENTO'))}
     *
     * @see TipoPenalidade
     */
    @NotNull(message = "O tipo de penalidade é obrigatório.")
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_penalidade", nullable = false, length = 20)
    private TipoPenalidade tipoPenalidade;

    /**
     * Justificativa textual da penalidade.
     * Constraint: {@code varchar(200) NOT NULL}
     */
    @NotBlank(message = "O motivo da penalidade é obrigatório.")
    @Size(min = 5, max = 200, message = "O motivo deve ter entre 5 e 200 caracteres.")
    @Column(name = "motivo", nullable = false, length = 200)
    private String motivo;

    /**
     * Data de início de vigência da penalidade.
     * Constraint: {@code date NOT NULL}
     */
    @NotNull(message = "A data de início é obrigatória.")
    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    /**
     * Data de término da penalidade.
     * {@code null} indica penalidade permanente (banimento definitivo).
     *
     * Constraint: {@code date — opcional,
     * CHECK (data_fim IS NULL OR data_fim >= data_inicio)}
     */
    @Column(name = "data_fim")
    private LocalDate dataFim;

    /**
     * Indica se a penalidade ainda está em vigor.
     * Atualizado pelo job agendado {@code PenalidadeExpiracaoJob}.
     * Constraint: {@code boolean NOT NULL DEFAULT true}
     */
    @Column(name = "ativa", nullable = false)
    private Boolean ativa = Boolean.TRUE;

    // ------------------------------------------------------------------
    // Métodos de domínio
    // ------------------------------------------------------------------

    /**
     * Encerra a penalidade, marcando-a como inativa.
     * Chamado pelo job de expiração ou manualmente pelo moderador.
     */
    public void encerrar() {
        this.ativa = Boolean.FALSE;
    }

    /**
     * Verifica se a penalidade está atualmente em vigor
     * comparando com a data de hoje.
     *
     * @return {@code true} se ativa e dentro do período de vigência
     */
    public boolean estaAtiva() {
        if (!ativa) {
            return false;
        }
        LocalDate hoje = LocalDate.now();
        // Banimento permanente: dataFim == null → sempre ativa enquanto ativa=true
        if (dataFim == null) {
            return true;
        }
        return !hoje.isAfter(dataFim);
    }

    /**
     * Verifica se a penalidade é um banimento permanente.
     *
     * @return {@code true} se tipo == BANIMENTO e dataFim == null
     */
    public boolean ehBanimentoPermanente() {
        return TipoPenalidade.BANIMENTO == tipoPenalidade && dataFim == null;
    }
}
