package chegae.domain.model;

import chegae.domain.enums.StatusEvento;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Representa a avaliação mútua realizada entre participantes de um evento concluído.
 *
 * <h3>Dependência existencial</h3>
 * <p>Uma avaliação só existe no contexto de um evento concluído
 * (FK {@code id_evento} obrigatória — "não existe avaliação sem evento", seção 7.3).</p>
 *
 * <h3>Constraints de integridade</h3>
 * <ul>
 *   <li>{@code uk_avaliacao_evento_avaliador_avaliado} – garante que cada par
 *       (avaliador, avaliado) avalie-se no máximo uma vez por evento.</li>
 *   <li>{@code ck_avaliacao_usuarios_distintos} – impede auto-avaliação
 *       ({@code id_avaliador ≠ id_avaliado}).</li>
 *   <li>{@code ck_avaliacao_nota} – nota deve estar entre 1 e 5 estrelas.</li>
 * </ul>
 *
 * <h3>Regras de Negócio relacionadas</h3>
 * <ul>
 *   <li>RN002 – a nota impacta a {@code honraMedia} do avaliado</li>
 *   <li>RN007 – avaliações pendentes bloqueiam novas inscrições</li>
 *   <li>RN010 – avaliações só são contabilizadas se o evento atingiu quórum mínimo</li>
 * </ul>
 */
@Entity
@Table(
    name = "avaliacao",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_avaliacao_evento_avaliador_avaliado",
        columnNames = {"id_evento", "id_avaliador", "id_avaliado"}
    )
)
/*
 * Constraint de check (id_avaliador ≠ id_avaliado) é garantida no DDL.
 * No nível JPA, a validação é reforçada no método validar().
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString(exclude = {"evento", "avaliador", "avaliado"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Avaliacao {

    // ------------------------------------------------------------------
    // Chave primária
    // ------------------------------------------------------------------

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_avaliacao", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    // ------------------------------------------------------------------
    // Chaves estrangeiras
    // ------------------------------------------------------------------

    /**
     * Evento ao qual a avaliação está vinculada.
     * FK: {@code id_evento → evento(id_evento)}
     * ON UPDATE CASCADE / ON DELETE CASCADE
     */
    @NotNull(message = "O evento da avaliação é obrigatório.")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "id_evento",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_avaliacao_evento")
    )
    private Evento evento;

    /**
     * Usuário que está avaliando.
     * FK: {@code id_avaliador → usuario(id_usuario)}
     * ON UPDATE CASCADE / ON DELETE RESTRICT
     */
    @NotNull(message = "O avaliador é obrigatório.")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "id_avaliador",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_avaliacao_avaliador")
    )
    private Usuario avaliador;

    /**
     * Usuário que está sendo avaliado.
     * FK: {@code id_avaliado → usuario(id_usuario)}
     * ON UPDATE CASCADE / ON DELETE RESTRICT
     *
     * <p>Deve ser diferente do {@code avaliador} (constraint
     * {@code ck_avaliacao_usuarios_distintos} no DDL).</p>
     */
    @NotNull(message = "O avaliado é obrigatório.")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "id_avaliado",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_avaliacao_avaliado")
    )
    private Usuario avaliado;

    // ------------------------------------------------------------------
    // Atributos
    // ------------------------------------------------------------------

    /**
     * Nota atribuída na escala de 1 a 5 estrelas.
     * Constraint: {@code integer NOT NULL, CHECK (nota BETWEEN 1 AND 5)}
     */
    @NotNull(message = "A nota é obrigatória.")
    @Min(value = 1, message = "A nota mínima é 1 estrela.")
    @Max(value = 5, message = "A nota máxima é 5 estrelas.")
    @Column(name = "nota", nullable = false)
    private Integer nota;

    /**
     * Comentário opcional sobre o avaliado.
     * Constraint: {@code text} — opcional
     */
    @Size(max = 500, message = "O comentário não pode ultrapassar 500 caracteres.")
    @Column(name = "comentario_opcional", columnDefinition = "text")
    private String comentarioOpcional;

    /**
     * Data e hora em que a avaliação foi registrada.
     * Constraint: {@code timestamptz NOT NULL DEFAULT now()}
     */
    @CreationTimestamp
    @Column(name = "data_hora_avaliacao", nullable = false, updatable = false,
            columnDefinition = "timestamptz DEFAULT now()")
    private OffsetDateTime dataHoraAvaliacao;

    // ------------------------------------------------------------------
    // Métodos de domínio
    // ------------------------------------------------------------------

    /**
     * Valida as regras de negócio antes de persistir a avaliação.
     *
     * @throws IllegalArgumentException se avaliador == avaliado (auto-avaliação proibida)
     * @throws IllegalStateException    se o evento não estiver concluído
     */
    public void validar() {
        if (avaliador != null && avaliado != null
                && avaliador.getId().equals(avaliado.getId())) {
            throw new IllegalArgumentException(
                "Auto-avaliação não é permitida: avaliador e avaliado são o mesmo usuário."
            );
        }
        if (evento != null && evento.getStatusEvento() != StatusEvento.CONCLUIDO) {
            throw new IllegalStateException(
                "Somente eventos CONCLUIDO podem ser avaliados. Status atual: "
                + evento.getStatusEvento()
            );
        }
    }

    /**
     * Indica se esta avaliação configuraria uma auto-avaliação.
     *
     * @return {@code true} se avaliador e avaliado são o mesmo usuário
     */
    public boolean ehAutoAvaliacao() {
        return avaliador != null && avaliado != null
               && avaliador.getId().equals(avaliado.getId());
    }
}
