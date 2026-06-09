package chegae.controller.dto;
import java.util.UUID;
public record InscricaoResponseDTO(
    UUID eventoId,
    UUID usuarioId,
    String status,
    String message
) {}
