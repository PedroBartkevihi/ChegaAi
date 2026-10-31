package chegaai.controller.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record ComentarioRequestDTO(
    @NotBlank @Size(max = 180, message = "o comentário deve ter até 180 caracteres")
    String text
) {}
