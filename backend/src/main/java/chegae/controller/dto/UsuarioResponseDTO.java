package chegae.controller.dto;
import java.util.UUID;
public record UsuarioResponseDTO(
    UUID id,
    String name,
    String fullName,
    String email,
    double honor,
    boolean isModerator
) {}
