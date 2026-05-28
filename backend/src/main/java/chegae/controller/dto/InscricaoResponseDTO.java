package chegae.controller.dto;

import java.util.UUID;

/**
 * DTO de resposta para operações de inscrição.
 * Retorna o status resultante para o frontend atualizar a UI.
 */
public record InscricaoResponseDTO(
    UUID eventoId,
    UUID usuarioId,
    String status,
    String message
) {}
