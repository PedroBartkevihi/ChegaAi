package chegae.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Representa as categorias temáticas dos eventos (ex.: Esportes, Pets, Lazer).
 *
 * <p>Permite organizar o feed, habilitar a filtragem por tipo de atividade
 * (RF007, UC09) e é gerenciada exclusivamente pelo Administrador (RF008, UC12).</p>
 *
 * <h3>Regras relevantes</h3>
 * <ul>
 *   <li>O nome de categoria deve ser único na plataforma
 *       (constraint {@code uk_categoria_nome}).</li>
 *   <li>Inativação em vez de exclusão física: categorias inativas deixam de
 *       aparecer para novos eventos, mas eventos existentes mantêm a referência
 *       (UC12 — FA1).</li>
 * </ul>
 */
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

    // ------------------------------------------------------------------
    // Chave primária
    // ------------------------------------------------------------------

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_categoria", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    // ------------------------------------------------------------------
    // Atributos
    // ------------------------------------------------------------------

    /**
     * Nome da categoria — único na plataforma.
     * Constraint: {@code varchar(80) NOT NULL, UNIQUE}
     */
    @NotBlank(message = "O nome da categoria é obrigatório.")
    @Size(min = 2, max = 80, message = "O nome deve ter entre 2 e 80 caracteres.")
    @Column(name = "nome", nullable = false, unique = true, length = 80)
    private String nome;

    /**
     * Descrição textual da categoria.
     * Constraint: {@code text} (opcional)
     */
    @Size(max = 2000, message = "A descrição não pode ultrapassar 2000 caracteres.")
    @Column(name = "descricao", columnDefinition = "text")
    private String descricao;

    /**
     * Indica se a categoria está ativa e disponível para novos eventos.
     * Constraint: {@code boolean NOT NULL DEFAULT true}
     */
    @Column(name = "ativa", nullable = false)
    private Boolean ativa = Boolean.TRUE;

    // ------------------------------------------------------------------
    // Relacionamentos
    // ------------------------------------------------------------------

    /**
     * Eventos classificados nesta categoria.
     * Mapeamento inverso de {@link Evento#categoria}.
     */
    @OneToMany(mappedBy = "categoria", fetch = FetchType.LAZY)
    private List<Evento> eventos = new ArrayList<>();

    // ------------------------------------------------------------------
    // Métodos de domínio
    // ------------------------------------------------------------------

    /**
     * Inativa a categoria, impedindo que novos eventos a utilizem.
     * Eventos já vinculados mantêm a referência (UC12 — FA1).
     */
    public void inativar() {
        this.ativa = Boolean.FALSE;
    }

    /**
     * Reativa uma categoria previamente inativada (UC12 — FA2).
     */
    public void ativar() {
        this.ativa = Boolean.TRUE;
    }
}
