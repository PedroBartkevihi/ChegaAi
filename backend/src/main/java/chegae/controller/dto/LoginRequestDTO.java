package chegae.controller.dto;

/**
 * DTO para requisição de login simplificado (MVP).
 * Busca o usuário apenas pelo e-mail, sem validação de senha.
 */
public record LoginRequestDTO(
    String email
) {}
