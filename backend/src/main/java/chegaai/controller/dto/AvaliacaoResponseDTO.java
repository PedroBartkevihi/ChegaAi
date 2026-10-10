package chegaai.controller.dto;

import java.util.UUID;

public record AvaliacaoResponseDTO(
    UUID id,
    UUID eventoId,
    UUID avaliadorId,
    UUID avaliadoId,
    int nota,
    String comentario,
    String message
) {}
