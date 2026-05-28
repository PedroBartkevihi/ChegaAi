package chegae.controller.dto;

import java.util.UUID;

/**
 * DTO de resposta com dados do usuário logado.
 * Formato compatível com o frontend React (currentUser).
 */
public record UsuarioResponseDTO(
    UUID id,
    String name,
    String fullName,
    String email,
    double honor,
    boolean isModerator
) {}
