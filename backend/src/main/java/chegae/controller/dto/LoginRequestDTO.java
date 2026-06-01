package chegae.controller.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO para requisição de login simplificado (MVP).
 * Busca o usuário apenas pelo e-mail, sem validação de senha.
 */
public record LoginRequestDTO(
    @NotBlank @Email
    String email
) {}
