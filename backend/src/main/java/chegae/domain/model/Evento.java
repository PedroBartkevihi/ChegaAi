package chegae.domain.model;

import chegae.domain.enums.StatusEvento;
import chegae.domain.enums.StatusInscricao;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tabela central do domínio ChegAê: representa as atividades presenciais
 * criadas pelos organizadores na plataforma.
 *
 * <h3>Relacionamentos</h3>
 * <ul>
 *   <li>{@code organizador}  → {@link Usuario}   (N:1, ON DELETE RESTRICT)</li>
 *   <li>{@code categoria}    → {@link Categoria} (N:1, ON DELETE RESTRICT)</li>
 *   <li>{@code endereco}     → {@link Endereco}  (N:1, ON DELETE RESTRICT)</li>
 *   <li>{@code inscricoes}   → {@link Inscricao} (1:N)</li>
 *   <li>{@code comentarios}  → {@link Comentario}(1:N)</li>
 *   <li>{@code avaliacoes}   → {@link Avaliacao} (1:N)</li>
 *   <li>{@code denuncias}    → {@link Denuncia}  (1:N, FK opcional)</li>
 * </ul>
 *
 * <h3>Campo {@code travado}</h3>
 * <p>Flag lógica que impede novas inscrições quando o moderador aciona RF009
 * (denúncia de local perigoso). Não existe no DDL original como coluna separada —
 * adicionado aqui como coluna extra para manter o status {@code ATIVO} no banco
 * enquanto a averiguação ocorre.</p>
 *
 * <h3>Regras de Negócio relacionadas</h3>
 * <ul>
 *   <li>RN001 – fila FIFO gerenciada pelas {@link Inscricao}s SOLICITADAS ordenadas por data</li>
 *   <li>RN003 – limite de vagas: 50 para Embaixador, 20 para Participante</li>
 *   <li>RN010 – avaliações válidas somente se ≥ 3 participantes confirmados além do organizador</li>
 * </ul>
 */
@Entity
@Table(name = "evento")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString(exclude = {"inscricoes", "comentarios", "avaliacoes", "denuncias"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Evento {

	// ------------------------------------------------------------------
	// Chave primária
	// ------------------------------------------------------------------

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id_evento", nullable = false, updatable = false)
	@EqualsAndHashCode.Include
	private UUID id;

	// ------------------------------------------------------------------
	// Chaves estrangeiras (relacionamentos Muitos-para-Um)
	// ------------------------------------------------------------------

	/**
	 * Organizador do evento.
	 * FK: {@code id_organizador → usuario(id_usuario)}
	 * ON UPDATE CASCADE / ON DELETE RESTRICT
	 */
	@NotNull(message = "O organizador é obrigatório.")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(
		name = "id_organizador",
		nullable = false,
		foreignKey = @ForeignKey(name = "fk_evento_organizador")
	)
	private Usuario organizador;

	/**
	 * Categoria temática do evento.
	 * FK: {@code id_categoria → categoria(id_categoria)}
	 * ON UPDATE CASCADE / ON DELETE RESTRICT
	 */
	@NotNull(message = "A categoria é obrigatória.")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(
		name = "id_categoria",
		nullable = false,
		foreignKey = @ForeignKey(name = "fk_evento_categoria")
	)
	private Categoria categoria;

	/**
	 * Endereço de realização do evento.
	 * FK: {@code id_endereco → endereco(id_endereco)}
	 * ON UPDATE CASCADE / ON DELETE RESTRICT
	 *
	 * <p>Sem constraint UNIQUE: múltiplos eventos podem referenciar
	 * o mesmo endereço (seção 7.3 do documento de requisitos).</p>
	 */
	@NotNull(message = "O endereço é obrigatório.")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(
		name = "id_endereco",
		nullable = false,
		foreignKey = @ForeignKey(name = "fk_evento_endereco")
	)
	private Endereco endereco;

	// ------------------------------------------------------------------
	// Atributos do evento
	// ------------------------------------------------------------------

	/**
	 * Título descritivo do evento.
	 * Constraint: {@code varchar(150) NOT NULL}
	 */
	@NotBlank(message = "O título é obrigatório.")
	@Size(min = 3, max = 150, message = "O título deve ter entre 3 e 150 caracteres.")
	@Column(name = "titulo", nullable = false, length = 150)
	private String titulo;

	/**
	 * Descrição completa do evento (ponto de encontro, regras, etc.).
	 * Constraint: {@code text NOT NULL}
	 */
	@NotBlank(message = "A descrição é obrigatória.")
	@Size(min = 10, max = 3000, message = "A descrição deve ter entre 10 e 3000 caracteres.")
	@Column(name = "descricao", nullable = false, columnDefinition = "text")
	private String descricao;

	/**
	 * Data e hora de realização do evento (com fuso horário).
	 * Constraint: {@code timestamptz NOT NULL}
	 */
	@NotNull(message = "A data e hora de realização são obrigatórias.")
	@Future(message = "A data de realização deve ser uma data futura.")
	@Column(name = "data_hora_realizacao", nullable = false,
			columnDefinition = "timestamptz")
	private OffsetDateTime dataHoraRealizacao;

	/**
	 * Limite máximo de participantes confirmados.
	 * Constraint: {@code integer NOT NULL, CHECK (limite_participantes > 0)}
	 *
	 * <p>Validação de negócio adicional — limites por papel — realizada
	 * na camada de serviço (RN003): máx 20 para Participante, máx 50 para Embaixador.</p>
	 */
	@NotNull(message = "O limite de participantes é obrigatório.")
	@Min(value = 3, message = "O evento deve ter no mínimo 3 participantes.")
	@Max(value = 50, message = "O limite máximo de participantes é 50 (Embaixadores).")
	@Column(name = "limite_participantes", nullable = false)
	private Integer limiteParticipantes;

	/**
	 * Status atual do evento no ciclo de vida.
	 * Constraint: {@code varchar(20) NOT NULL, CHECK (status_evento IN ('ATIVO','CANCELADO','CONCLUIDO'))}
	 *
	 * @see StatusEvento
	 */
	@NotNull(message = "O status do evento é obrigatório.")
	@Enumerated(EnumType.STRING)
	@Column(name = "status_evento", nullable = false, length = 20)
	private StatusEvento statusEvento = StatusEvento.ATIVO;

	/**
	 * Flag lógica de travamento pelo moderador (RF009).
	 *
	 * <p>Quando {@code true}, o evento está sob averiguação e não aceita
	 * novas inscrições, embora o status permaneça {@code ATIVO} no banco.
	 * Coluna adicional ao DDL original, adicionada para separar a gestão
	 * operacional da moderação.</p>
	 */
	@Column(name = "travado", nullable = false)
	private Boolean travado = Boolean.FALSE;

	/**
	 * Data e hora de criação do registro (auditoria).
	 */
	@CreationTimestamp
	@Column(name = "criado_em", nullable = false, updatable = false,
			columnDefinition = "timestamptz")
	private OffsetDateTime criadoEm;

	// ------------------------------------------------------------------
	// Relacionamentos (1:N)
	// ------------------------------------------------------------------

	/**
	 * Inscrições vinculadas a este evento (vagas confirmadas + fila de espera).
	 * A fila FIFO é gerenciada ordenando as inscrições SOLICITADAS por
	 * {@code dataHoraSolicitacao ASC} (RN001).
	 */
	@OneToMany(mappedBy = "evento", cascade = CascadeType.ALL,
			   orphanRemoval = true, fetch = FetchType.LAZY)
	@OrderBy("dataHoraSolicitacao ASC")
	private List<Inscricao> inscricoes = new ArrayList<>();

	/**
	 * Comentários publicados na página deste evento (UC07).
	 * Ordenados cronologicamente para exibição em formato de fórum.
	 */
	@OneToMany(mappedBy = "evento", cascade = CascadeType.ALL,
			   orphanRemoval = true, fetch = FetchType.LAZY)
	@OrderBy("dataHoraComentario ASC")
	private List<Comentario> comentarios = new ArrayList<>();

	/**
	 * Avaliações mútuas realizadas pelos participantes após a conclusão (UC06).
	 * Somente válidas se o evento atingiu o quórum mínimo (RN010).
	 */
	@OneToMany(mappedBy = "evento", cascade = CascadeType.ALL,
			   orphanRemoval = true, fetch = FetchType.LAZY)
	private List<Avaliacao> avaliacoes = new ArrayList<>();

	/**
	 * Denúncias associadas a este evento (contexto opcional — FK nullable).
	 */
	@OneToMany(mappedBy = "evento", fetch = FetchType.LAZY)
	private List<Denuncia> denuncias = new ArrayList<>();

	// ------------------------------------------------------------------
	// Métodos de domínio
	// ------------------------------------------------------------------

	/**
	 * Conta quantas inscrições com status {@code CONFIRMADA} existem no evento.
	 *
	 * @return número de vagas ocupadas
	 */
	public long contarConfirmados() {
		return inscricoes.stream()
				.filter(i -> i.getStatusInscricao() == StatusInscricao.CONFIRMADA)
				.count();
	}

	/**
	 * Verifica se ainda há vagas disponíveis para confirmação imediata.
	 *
	 * @return {@code true} se {@code contarConfirmados() < limiteParticipantes}
	 */
	public boolean possuiVaga() {
		return !travado
			   && statusEvento == StatusEvento.ATIVO
			   && contarConfirmados() < limiteParticipantes;
	}

	/**
	 * Verifica se o evento atingiu o quórum mínimo para validação das avaliações (RN010).
	 * O quórum é: pelo menos 3 participantes CONFIRMADOS além do organizador.
	 *
	 * @param quorumMinimo valor configurado em {@code chegae.regras.quorum-minimo-avaliacao}
	 * @return {@code true} se o quórum foi atingido
	 */
	public boolean atingiuQuorumMinimo(long quorumMinimo) {
		long confirmadosSemOrganizador = inscricoes.stream()
				.filter(i -> i.getStatusInscricao() == StatusInscricao.CONFIRMADA
							 && !i.getUsuario().getId().equals(organizador.getId()))
				.count();
		return confirmadosSemOrganizador >= quorumMinimo;
	}

	/**
	 * Marca o evento como concluído.
	 * A operação é válida somente para eventos com status {@code ATIVO}.
	 *
	 * @throws IllegalStateException se o evento não estiver ATIVO
	 */
	public void concluir() {
		if (statusEvento != StatusEvento.ATIVO) {
			throw new IllegalStateException(
				"Apenas eventos ATIVO podem ser concluídos. Status atual: " + statusEvento
			);
		}
		this.statusEvento = StatusEvento.CONCLUIDO;
	}

	/**
	 * Cancela o evento.
	 * Somente eventos com status {@code ATIVO} podem ser cancelados (UC03 — FA3).
	 *
	 * @throws IllegalStateException se o evento já estiver cancelado ou concluído
	 */
	public void cancelar() {
		if (statusEvento != StatusEvento.ATIVO) {
			throw new IllegalStateException(
				"Apenas eventos ATIVO podem ser cancelados. Status atual: " + statusEvento
			);
		}
		this.statusEvento = StatusEvento.CANCELADO;
	}

	/**
	 * Trava o evento para novas inscrições (RF009 — moderação).
	 * O status permanece ATIVO no banco; apenas a flag é alterada.
	 */
	public void travar() {
		this.travado = Boolean.TRUE;
	}

	/**
	 * Destrava o evento, permitindo novas inscrições após a averiguação.
	 */
	public void destravar() {
		this.travado = Boolean.FALSE;
	}
}
