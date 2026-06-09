package chegae.controller.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO para criação de um novo evento.
 * Campos nomeados em inglês para compatibilidade com o frontend.
 */
public record EventoRequestDTO(
    @NotBlank @Size(max = 150)
    String title,
    @NotBlank
    String category,
    @NotBlank @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$")
    String date,
    @NotBlank @Pattern(regexp = "^\\d{2}:\\d{2}$")
    String time,
    @Size(max = 9)
    String cep,
    @NotBlank @Size(max = 150)
    String place,
    @Min(3) @Max(50)
    int capacity,
    @Min(0) @Max(80)
    int distance,
    @NotBlank @Size(max = 3000)
    String description
) {}
