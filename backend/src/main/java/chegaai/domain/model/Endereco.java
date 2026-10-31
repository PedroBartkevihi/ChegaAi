package chegaai.domain.model;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
@Entity
@Table(name = "endereco")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString(exclude = "eventos")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Endereco {
    /** Gravado quando o organizador não informa um CEP válido, porque a coluna é obrigatória; a API nunca o mostra. */
    public static final String CEP_NAO_INFORMADO = "00000000";
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_endereco", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;
    @NotBlank(message = "O CEP é obrigatório.")
    @Pattern(regexp = "^[0-9]{8}$",
             message = "O CEP deve conter exatamente 8 dígitos numéricos, sem hífen.")
    @Column(name = "cep", nullable = false, length = 8)
    private String cep;
    @NotBlank(message = "O logradouro é obrigatório.")
    @Size(max = 150, message = "O logradouro deve ter no máximo 150 caracteres.")
    @Column(name = "logradouro", nullable = false, length = 150)
    private String logradouro;
    @NotBlank(message = "O bairro é obrigatório.")
    @Size(max = 100, message = "O bairro deve ter no máximo 100 caracteres.")
    @Column(name = "bairro", nullable = false, length = 100)
    private String bairro;
    @NotBlank(message = "A cidade é obrigatória.")
    @Size(max = 100, message = "A cidade deve ter no máximo 100 caracteres.")
    @Column(name = "cidade", nullable = false, length = 100)
    private String cidade;
    @NotBlank(message = "O estado (UF) é obrigatório.")
    @Pattern(regexp = "^[A-Z]{2}$",
             message = "O estado deve ser uma sigla válida de 2 letras maiúsculas (ex: RJ, SP).")
    @Column(name = "estado", nullable = false, length = 2)
    private String estado;
    @NotBlank(message = "O número é obrigatório (use S/N se não houver número).")
    @Size(max = 20, message = "O número deve ter no máximo 20 caracteres.")
    @Column(name = "numero", nullable = false, length = 20)
    private String numero;
    @Size(max = 150, message = "O complemento deve ter no máximo 150 caracteres.")
    @Column(name = "complemento", length = 150)
    private String complemento;
    @OneToMany(mappedBy = "endereco", fetch = FetchType.LAZY)
    private List<Evento> eventos = new ArrayList<>();
    public String formatarEnderecoCompleto() {
        String base = logradouro + ", " + numero + " – " + bairro + ", " + cidade + "/" + estado;
        if (complemento != null && !complemento.isBlank()) {
            base += " (" + complemento + ")";
        }
        return base + " – CEP " + cep;
    }
}
