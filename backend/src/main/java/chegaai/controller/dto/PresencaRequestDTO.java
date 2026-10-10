package chegaai.controller.dto;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
public record PresencaRequestDTO(
    @NotNull
    UUID participanteId
) {}
