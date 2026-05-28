package chegae.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Isola os dados de localização física dos eventos.
 *
 * <p>A separação em tabela própria (Normalização 3FN) evita redundância
 * quando múltiplos eventos ocorrem no mesmo local e facilita a integração
 * com a API de localização ViaCEP (RF003 — UC03, ator externo).</p>
 *
 * <p>A FK {@code id_endereco} na tabela {@code evento} <b>não</b> possui
 * constraint UNIQUE, o que é intencional: múltiplos eventos podem referenciar
 * o mesmo endereço (seção 7.3 do documento de requisitos).</p>
 */
@Entity
@Table(name = "endereco")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString(exclude = "eventos")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Endereco {

    // ------------------------------------------------------------------
    // Chave primária
    // ------------------------------------------------------------------

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_endereco", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    // ------------------------------------------------------------------
    // Atributos de localização
    // ------------------------------------------------------------------

    /**
     * CEP — exatamente 8 dígitos numéricos (sem hífen).
     * Constraint: {@code char(8) NOT NULL, CHECK (cep ~ '^[0-9]{8}$')}
     */
    @NotBlank(message = "O CEP é obrigatório.")
    @Pattern(regexp = "^[0-9]{8}$",
             message = "O CEP deve conter exatamente 8 dígitos numéricos, sem hífen.")
    @Column(name = "cep", nullable = false, columnDefinition = "char(8)")
    private String cep;

    /**
     * Logradouro (rua, avenida, etc.).
     * Constraint: {@code varchar(150) NOT NULL}
     */
    @NotBlank(message = "O logradouro é obrigatório.")
    @Size(max = 150, message = "O logradouro deve ter no máximo 150 caracteres.")
    @Column(name = "logradouro", nullable = false, length = 150)
    private String logradouro;

    /**
     * Bairro do endereço.
     * Constraint: {@code varchar(100) NOT NULL}
     */
    @NotBlank(message = "O bairro é obrigatório.")
    @Size(max = 100, message = "O bairro deve ter no máximo 100 caracteres.")
    @Column(name = "bairro", nullable = false, length = 100)
    private String bairro;

    /**
     * Cidade do endereço.
     * Constraint: {@code varchar(100) NOT NULL}
     */
    @NotBlank(message = "A cidade é obrigatória.")
    @Size(max = 100, message = "A cidade deve ter no máximo 100 caracteres.")
    @Column(name = "cidade", nullable = false, length = 100)
    private String cidade;

    /**
     * Sigla do estado (UF) — exatamente 2 letras maiúsculas.
     * Constraint: {@code char(2) NOT NULL, CHECK (estado ~ '^[A-Z]{2}$')}
     */
    @NotBlank(message = "O estado (UF) é obrigatório.")
    @Pattern(regexp = "^[A-Z]{2}$",
             message = "O estado deve ser uma sigla válida de 2 letras maiúsculas (ex: RJ, SP).")
    @Column(name = "estado", nullable = false, columnDefinition = "char(2)")
    private String estado;

    /**
     * Número do imóvel ou "S/N".
     * Constraint: {@code varchar(20) NOT NULL}
     */
    @NotBlank(message = "O número é obrigatório (use S/N se não houver número).")
    @Size(max = 20, message = "O número deve ter no máximo 20 caracteres.")
    @Column(name = "numero", nullable = false, length = 20)
    private String numero;

    /**
     * Complemento do endereço (ponto de referência, bloco, etc.).
     * Constraint: {@code varchar(150)} — opcional
     */
    @Size(max = 150, message = "O complemento deve ter no máximo 150 caracteres.")
    @Column(name = "complemento", length = 150)
    private String complemento;

    // ------------------------------------------------------------------
    // Relacionamentos
    // ------------------------------------------------------------------

    /**
     * Eventos realizados neste endereço.
     * Mapeamento inverso de {@link Evento#endereco}.
     */
    @OneToMany(mappedBy = "endereco", fetch = FetchType.LAZY)
    private List<Evento> eventos = new ArrayList<>();

    // ------------------------------------------------------------------
    // Métodos de domínio
    // ------------------------------------------------------------------

    /**
     * Retorna o endereço completo formatado em uma única String.
     *
     * @return endereço no formato "logradouro, número – bairro, cidade/estado – CEP"
     */
    public String formatarEnderecoCompleto() {
        String base = logradouro + ", " + numero + " – " + bairro + ", " + cidade + "/" + estado;
        if (complemento != null && !complemento.isBlank()) {
            base += " (" + complemento + ")";
        }
        return base + " – CEP " + cep;
    }
}
