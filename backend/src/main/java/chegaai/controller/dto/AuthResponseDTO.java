package chegaai.controller.dto;
import java.time.Instant;
public record AuthResponseDTO(
    String token,
    String tokenType,
    Instant expiresAt,
    UsuarioResponseDTO user
) {}
