package chegae.controller.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record ComentarioRequestDTO(
    @NotBlank @Size(max = 2000)
    String text
) {}
