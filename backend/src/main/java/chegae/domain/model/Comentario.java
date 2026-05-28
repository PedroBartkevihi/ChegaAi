package chegae.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Representa uma mensagem publicada na seção de comentários de um evento.
 *
 * <h3>Controle de acesso (RN006)</h3>
 * <p>Somente usuários com inscrição {@code CONFIRMADA} ou {@code SOLICITADA}
 * (fila de espera) podem publicar comentários. A verificação é realizada
 * na camada de serviço ({@code ComentarioService}).</p>
 *
 * <h3>Exclusão</h3>
 * <p>O organizador pode excluir comentários do seu próprio evento (RN006, UC07 — FA1).
 * O moderador/administrador pode excluir qualquer comentário (RF005).</p>
 *
 * <h3>Formato de exibição</h3>
 * <p>Os comentários são exibidos em ordem cronológica crescente,
 * no estilo de fórum (UC07 — FA2). O índice composto
 * {@code idx_comentario_evento_data} no DDL otimiza essa consulta.</p>
 */
@Entity
@Table(name = "comentario")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString(exclude = {"autor", "evento"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Comentario {

    // ------------------------------------------------------------------
    // Chave primária
    // ------------------------------------------------------------------

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_comentario", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    // ------------------------------------------------------------------
    // Chaves estrangeiras
    // ------------------------------------------------------------------

    /**
     * Usuário autor do comentário.
     * FK: {@code id_usuario → usuario(id_usuario)}
     * ON UPDATE CASCADE / ON DELETE CASCADE
     */
    @NotNull(message = "O autor do comentário é obrigatório.")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "id_usuario",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_comentario_usuario")
    )
    private Usuario autor;

    /**
     * Evento ao qual o comentário pertence.
     * FK: {@code id_evento → evento(id_evento)}
     * ON UPDATE CASCADE / ON DELETE CASCADE
     */
    @NotNull(message = "O evento do comentário é obrigatório.")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "id_evento",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_comentario_evento")
    )
    private Evento evento;

    // ------------------------------------------------------------------
    // Atributos
    // ------------------------------------------------------------------

    /**
     * Conteúdo textual do comentário.
     * Constraint: {@code text NOT NULL}
     */
    @NotBlank(message = "O texto do comentário não pode ser vazio.")
    @Size(min = 1, max = 2000, message = "O comentário deve ter entre 1 e 2000 caracteres.")
    @Column(name = "texto", nullable = false, columnDefinition = "text")
    private String texto;

    /**
     * Data e hora em que o comentário foi publicado.
     * Gerado automaticamente na persistência.
     * Constraint: {@code timestamptz NOT NULL DEFAULT now()}
     */
    @CreationTimestamp
    @Column(name = "data_hora_comentario", nullable = false, updatable = false,
            columnDefinition = "timestamptz DEFAULT now()")
    private OffsetDateTime dataHoraComentario;

    // ------------------------------------------------------------------
    // Métodos de domínio
    // ------------------------------------------------------------------

    /**
     * Verifica se o usuário informado é o autor deste comentário.
     * Usado na camada de serviço para autorizar exclusão (RN006).
     *
     * @param idUsuario UUID do usuário a verificar
     * @return {@code true} se o usuário é o autor
     */
    public boolean pertenceAo(UUID idUsuario) {
        return autor != null && autor.getId().equals(idUsuario);
    }
}
