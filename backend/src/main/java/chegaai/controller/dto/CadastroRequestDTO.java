package chegaai.controller.dto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
public record CadastroRequestDTO(
    @NotBlank @Size(min = 2, max = 120, message = "o nome deve ter entre 2 e 120 caracteres")
    String name,
    @NotBlank @Email @Size(max = 254)
    String email,
    @Pattern(regexp = "^[0-9]{11}$", message = "informe os 11 dígitos do CPF ou deixe o campo vazio")
    String cpf,
    @NotNull @Past(message = "a data de nascimento deve estar no passado")
    LocalDate birthDate,
    @NotBlank @Size(min = 8, max = 72, message = "a senha deve ter entre 8 e 72 caracteres")
    String password
) {
    // CPF opcional: vazio ou só espaços vira nulo antes da validação.
    public CadastroRequestDTO {
        cpf = cpf == null || cpf.isBlank() ? null : cpf.trim();
    }
}
