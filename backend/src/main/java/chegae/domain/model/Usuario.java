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

/**
 * Entidade central que representa todos os utilizadores da plataforma ChegAê.
 *
 * <h3>Estratégia de herança – SINGLE TABLE</h3>
 * <p>Conforme decisão de projeto documentada na seção 7.3 do documento de
 * requisitos, optou-se pela estratégia {@code SINGLE_TABLE} com coluna
 * discriminadora {@code papel_sistema}. Essa abordagem evita JOINs
 * excessivos e simplifica as consultas, uma vez que Organizador,
 * Embaixador e Participante compartilham os mesmos atributos.</p>
 *
 * <p>Os papéis {@code EMBAIXADOR} e {@code PARTICIPANTE} são subclasses
 * mapeadas separadamente para possibilitar futuras especializações sem
 * alterar esta classe base.</p>
 *
 * <h3>Regras de Negócio relacionadas</h3>
 * <ul>
 *   <li>RN002 – honraMedia: média aritmética das avaliações recebidas (1–5)</li>
 *   <li>RN003 – honraMedia ≥ 4.8 → papel promovido para EMBAIXADOR</li>
 *   <li>RN004 – honraMedia &lt; 2.0 → não pode criar eventos</li>
 *   <li>RN005 – honraMedia &lt; 1.0 ou qtdDenunciasValidadas ≥ 3 → banimento automático</li>
 * </ul>
 *
 * @see chegae.domain.enums.PapelSistema
 * @see chegae.domain.enums.StatusUsuario
 */
@Entity
@Table(
    name = "usuario",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_usuario_email", columnNames = "email"),
        @UniqueConstraint(name = "uk_usuario_cpf",   columnNames = "cpf")
    }
)
/*
 * Herança lógica: uma única tabela com coluna discriminadora.
 * Subclasses (Embaixador, Participante, Administrador) mapeiam
 * o mesmo conjunto de colunas — apenas a coluna papel_sistema difere.
 */
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

    // ------------------------------------------------------------------
    // Chave primária
    // ------------------------------------------------------------------

    /**
     * Identificador único universal.
     * Gerado pelo PostgreSQL via {@code gen_random_uuid()} (extensão pgcrypto).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_usuario", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    // ------------------------------------------------------------------
    // Dados pessoais e de autenticação
    // ------------------------------------------------------------------

    /**
     * Nome completo do usuário.
     * Constraint: {@code varchar(120) NOT NULL}
     */
    @NotBlank(message = "O nome é obrigatório.")
    @Size(min = 2, max = 120, message = "O nome deve ter entre 2 e 120 caracteres.")
    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    /**
     * Endereço de e-mail — único na plataforma.
     * Constraint: {@code varchar(254) NOT NULL, UNIQUE}
     */
    @NotBlank(message = "O e-mail é obrigatório.")
    @Email(message = "O e-mail informado não é válido.")
    @Size(max = 254, message = "O e-mail deve ter no máximo 254 caracteres.")
    @Column(name = "email", nullable = false, unique = true, length = 254)
    private String email;

    /**
     * CPF do usuário — exatamente 11 dígitos numéricos, único na plataforma.
     * Constraint: {@code char(11) NOT NULL, UNIQUE, CHECK (cpf ~ '^[0-9]{11}$')}
     *
     * <p><b>Atenção:</b> armazenar apenas os 11 dígitos sem pontuação.</p>
     */
    @NotBlank(message = "O CPF é obrigatório.")
    @Pattern(regexp = "^[0-9]{11}$", message = "O CPF deve conter exatamente 11 dígitos numéricos.")
    @Column(name = "cpf", nullable = false, unique = true, columnDefinition = "char(11)")
    private String cpf;

    /**
     * Hash da senha armazenado com BCrypt (RNF002).
     * Nunca deve ser exposto em DTOs de saída.
     */
    @NotBlank(message = "A senha hash é obrigatória.")
    @Column(name = "senha_hash", nullable = false, columnDefinition = "text")
    private String senhaHash;

    /**
     * Data de nascimento do usuário.
     * Constraint: {@code date NOT NULL}
     */
    @NotNull(message = "A data de nascimento é obrigatória.")
    @Past(message = "A data de nascimento deve ser uma data no passado.")
    @Column(name = "data_nascimento", nullable = false)
    private LocalDate dataNascimento;

    // ------------------------------------------------------------------
    // Sistema de honra (RN002, RN003, RN004, RN005)
    // ------------------------------------------------------------------

    /**
     * Média aritmética das avaliações recebidas (escala 1.00–5.00).
     *
     * <p>Atualizada pelo serviço {@code AvaliacaoService} após cada nova
     * avaliação registrada. O valor 0.00 representa usuários sem avaliações.</p>
     *
     * <p>Constraint: {@code numeric(3,2) NOT NULL DEFAULT 0,
     * CHECK (honra_media BETWEEN 0 AND 5)}</p>
     */
    @NotNull
    @DecimalMin(value = "0.00", message = "A honra não pode ser negativa.")
    @DecimalMax(value = "5.00", message = "A honra não pode exceder 5.00.")
    @Column(name = "honra_media", nullable = false,
            columnDefinition = "numeric(3,2) DEFAULT 0")
    private BigDecimal honraMedia = BigDecimal.ZERO;

    // ------------------------------------------------------------------
    // Status da conta
    // ------------------------------------------------------------------

    /**
     * Status atual da conta do usuário.
     * Persiste como String para compatibilidade com o DDL PostgreSQL.
     *
     * @see StatusUsuario
     */
    @NotNull(message = "O status do usuário é obrigatório.")
    @Enumerated(EnumType.STRING)
    @Column(name = "status_usuario", nullable = false, length = 20)
    private StatusUsuario statusUsuario = StatusUsuario.ATIVO;

    // ------------------------------------------------------------------
    // Controle de denúncias validadas (RN005)
    // ------------------------------------------------------------------

    /**
     * Contador de denúncias validadas pelo moderador.
     * Ao atingir 3, aciona o banimento automático (RN005).
     *
     * Constraint: {@code integer NOT NULL DEFAULT 0, CHECK (qtd_denuncias_validadas >= 0)}
     */
    @Min(value = 0, message = "A quantidade de denúncias não pode ser negativa.")
    @Column(name = "qtd_denuncias_validadas", nullable = false)
    private Integer qtdDenunciasValidadas = 0;

    // ------------------------------------------------------------------
    // Timestamp de criação (auditoria)
    // ------------------------------------------------------------------

    /**
     * Data e hora de criação do registro (gerado automaticamente).
     * Não mapeado no DDL original, adicionado como boa prática de auditoria.
     */
    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    // ------------------------------------------------------------------
    // Relacionamentos (lazy por padrão para performance)
    // ------------------------------------------------------------------

    /**
     * Eventos criados por este usuário como organizador.
     * Um usuário pode criar vários eventos; cada evento pertence a um único organizador.
     */
    @OneToMany(mappedBy = "organizador", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Evento> eventosOrganizados = new ArrayList<>();

    /**
     * Inscrições do usuário em eventos de outros organizadores.
     * Relacionamento N:M resolvido pela entidade associativa {@link Inscricao}.
     */
    @OneToMany(mappedBy = "usuario", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Inscricao> inscricoes = new ArrayList<>();

    /**
     * Avaliações atribuídas por este usuário a outros participantes.
     */
    @OneToMany(mappedBy = "avaliador", fetch = FetchType.LAZY)
    private List<Avaliacao> avaliacoesFeitas = new ArrayList<>();

    /**
     * Avaliações recebidas por este usuário de outros participantes.
     * A honraMedia é calculada a partir destas avaliações (RN002).
     */
    @OneToMany(mappedBy = "avaliado", fetch = FetchType.LAZY)
    private List<Avaliacao> avaliacoesRecebidas = new ArrayList<>();

    /**
     * Comentários publicados pelo usuário em páginas de eventos.
     */
    @OneToMany(mappedBy = "autor", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Comentario> comentarios = new ArrayList<>();

    /**
     * Denúncias registradas por este usuário contra outros.
     */
    @OneToMany(mappedBy = "denunciante", fetch = FetchType.LAZY)
    private List<Denuncia> denunciasRegistradas = new ArrayList<>();

    /**
     * Denúncias recebidas (este usuário é o denunciado).
     */
    @OneToMany(mappedBy = "denunciado", fetch = FetchType.LAZY)
    private List<Denuncia> denunciasRecebidas = new ArrayList<>();

    /**
     * Penalidades aplicadas a este usuário pelo moderador/administrador
     * ou pelo sistema automaticamente (RN005, RN009).
     */
    @OneToMany(mappedBy = "usuarioPenalizado", fetch = FetchType.LAZY)
    private List<Penalidade> penalidades = new ArrayList<>();

    // ------------------------------------------------------------------
    // Métodos de domínio
    // ------------------------------------------------------------------

    /**
     * Retorna o papel do sistema inferido da coluna discriminadora.
     * Subclasses concretas devem retornar o valor correto do enum.
     *
     * @return papel atual deste usuário
     */
    public abstract PapelSistema getPapelSistema();

    /**
     * Verifica se o usuário pode criar novos eventos (RN004).
     *
     * @param honraMinimaParaCriacao valor configurado em {@code chegae.regras.honra-minima-criacao}
     * @return {@code true} se o usuário pode criar eventos
     */
    public boolean podeCriarEvento(BigDecimal honraMinimaParaCriacao) {
        return statusUsuario == StatusUsuario.ATIVO
               && honraMedia.compareTo(honraMinimaParaCriacao) >= 0;
    }

    /**
     * Verifica se o usuário pode se inscrever em eventos.
     * Conta suspensa ou inativa não pode se inscrever.
     *
     * @return {@code true} se o usuário pode se inscrever
     */
    public boolean podeSeInscrever() {
        return statusUsuario == StatusUsuario.ATIVO;
    }

    /**
     * Incrementa o contador de denúncias validadas e retorna o novo valor.
     * Chamado pelo moderador ao validar uma denúncia (UC13).
     *
     * @return novo valor de qtdDenunciasValidadas
     */
    public int registrarDenunciaValidada() {
        this.qtdDenunciasValidadas++;
        return this.qtdDenunciasValidadas;
    }

    /**
     * Verifica se este usuário qualifica para o status de Embaixador (RN003).
     *
     * @param thresholdEmbaixador valor configurado em {@code chegae.regras.honra-embaixador}
     * @return {@code true} se honraMedia ≥ threshold
     */
    public boolean qualificaEmbaixador(BigDecimal thresholdEmbaixador) {
        return honraMedia.compareTo(thresholdEmbaixador) >= 0;
    }

    /**
     * Verifica se o banimento automático deve ser acionado (RN005).
     *
     * @param honraLimite              valor mínimo de honra antes do banimento
     * @param limiteDenunciasValidadas número máximo de denúncias antes do banimento
     * @return {@code true} se o banimento automático deve ser aplicado
     */
    public boolean deveSerBanidoAutomaticamente(BigDecimal honraLimite,
                                                int limiteDenunciasValidadas) {
        return honraMedia.compareTo(honraLimite) < 0
               || qtdDenunciasValidadas >= limiteDenunciasValidadas;
    }
}