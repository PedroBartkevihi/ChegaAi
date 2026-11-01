package chegaai.controller.dto;
import jakarta.validation.constraints.NotBlank;
/** A senha confirma que é a própria pessoa quem pede a exclusão da conta. */
public record ExclusaoContaRequestDTO(
    @NotBlank(message = "informe a sua senha")
    String password
) {}
