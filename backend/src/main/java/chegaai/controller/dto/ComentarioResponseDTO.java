package chegaai.controller.dto;
import java.util.UUID;
public record ComentarioResponseDTO(
    UUID id,
    String author,
    String text,
    String time
) {}
