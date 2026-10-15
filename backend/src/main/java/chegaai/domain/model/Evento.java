package chegaai.domain.model;
import chegaai.domain.enums.StatusEvento;
import chegaai.domain.enums.StatusInscricao;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
@Entity
@Table(name = "evento")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString(exclude = {"inscricoes", "comentarios", "avaliacoes", "denuncias"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Evento {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id_evento", nullable = false, updatable = false)
	@EqualsAndHashCode.Include
	private UUID id;
	@NotNull(message = "O organizador é obrigatório.")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(
		name = "id_organizador",
		nullable = false,
		foreignKey = @ForeignKey(name = "fk_evento_organizador")
	)
	private Usuario organizador;
	@NotNull(message = "A categoria é obrigatória.")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(
		name = "id_categoria",
		nullable = false,
		foreignKey = @ForeignKey(name = "fk_evento_categoria")
	)
	private Categoria categoria;
	@NotNull(message = "O endereço é obrigatório.")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(
		name = "id_endereco",
		nullable = false,
		foreignKey = @ForeignKey(name = "fk_evento_endereco")
	)
	private Endereco endereco;
	@NotBlank(message = "O título é obrigatório.")
	@Size(min = 3, max = 150, message = "O título deve ter entre 3 e 150 caracteres.")
	@Column(name = "titulo", nullable = false, length = 150)
	private String titulo;
	@NotBlank(message = "A descrição é obrigatória.")
	@Size(min = 10, max = 3000, message = "A descrição deve ter entre 10 e 3000 caracteres.")
	@Column(name = "descricao", nullable = false, columnDefinition = "text")
	private String descricao;
	// Sem @Future: o Hibernate revalida a entidade a cada UPDATE, e um evento que já aconteceu
	// precisa continuar podendo ser concluído. A data futura é exigida só na criação (EventoService).
	@NotNull(message = "A data e hora de realização são obrigatórias.")
	@Column(name = "data_hora_realizacao", nullable = false,
			columnDefinition = "timestamptz")
	private OffsetDateTime dataHoraRealizacao;
	@NotNull(message = "O limite de participantes é obrigatório.")
	@Min(value = 3, message = "O evento deve ter no mínimo 3 participantes.")
	@Max(value = 50, message = "O limite máximo de participantes é 50 (Embaixadores).")
	@Column(name = "limite_participantes", nullable = false)
	private Integer limiteParticipantes;
	@NotNull(message = "O status do evento é obrigatório.")
	@Enumerated(EnumType.STRING)
	@Column(name = "status_evento", nullable = false, length = 20)
	private StatusEvento statusEvento = StatusEvento.ATIVO;
	@Column(name = "travado", nullable = false)
	private Boolean travado = Boolean.FALSE;
	@CreationTimestamp
	@Column(name = "criado_em", nullable = false, updatable = false,
			columnDefinition = "timestamptz")
	private OffsetDateTime criadoEm;
	@OneToMany(mappedBy = "evento", cascade = CascadeType.ALL,
			   orphanRemoval = true, fetch = FetchType.LAZY)
	@OrderBy("dataHoraSolicitacao ASC")
	private List<Inscricao> inscricoes = new ArrayList<>();
	@OneToMany(mappedBy = "evento", cascade = CascadeType.ALL,
			   orphanRemoval = true, fetch = FetchType.LAZY)
	@OrderBy("dataHoraComentario ASC")
	private List<Comentario> comentarios = new ArrayList<>();
	@OneToMany(mappedBy = "evento", cascade = CascadeType.ALL,
			   orphanRemoval = true, fetch = FetchType.LAZY)
	private List<Avaliacao> avaliacoes = new ArrayList<>();
	@OneToMany(mappedBy = "evento", fetch = FetchType.LAZY)
	private List<Denuncia> denuncias = new ArrayList<>();
	public long contarConfirmados() {
		return inscricoes.stream()
				.filter(i -> i.getStatusInscricao() == StatusInscricao.CONFIRMADA)
				.count();
	}
	public boolean possuiVaga() {
		return !travado
			   && statusEvento == StatusEvento.ATIVO
			   && contarConfirmados() < limiteParticipantes;
	}
	public boolean atingiuQuorumMinimo(long quorumMinimo) {
		long confirmadosSemOrganizador = inscricoes.stream()
				.filter(i -> i.getStatusInscricao() == StatusInscricao.CONFIRMADA
							 && !i.getUsuario().getId().equals(organizador.getId()))
				.count();
		return confirmadosSemOrganizador >= quorumMinimo;
	}
	public void concluir(OffsetDateTime agora) {
		if (statusEvento != StatusEvento.ATIVO) {
			throw new IllegalStateException(
				"Apenas eventos ATIVO podem ser concluídos. Status atual: " + statusEvento
			);
		}
		if (agora.isBefore(dataHoraRealizacao)) {
			throw new IllegalStateException(
				"O evento só pode ser concluído depois da data e hora de realização."
			);
		}
		this.statusEvento = StatusEvento.CONCLUIDO;
	}
	public void cancelar() {
		if (statusEvento != StatusEvento.ATIVO) {
			throw new IllegalStateException(
				"Apenas eventos ATIVO podem ser cancelados. Status atual: " + statusEvento
			);
		}
		this.statusEvento = StatusEvento.CANCELADO;
	}
	public void travar() {
		this.travado = Boolean.TRUE;
	}
	public void destravar() {
		this.travado = Boolean.FALSE;
	}
}
