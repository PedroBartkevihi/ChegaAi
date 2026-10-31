package chegaai.controller.dto;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
/** Os limites de tamanho são os mesmos do formulário de criação do site (CreateView.jsx). */
public record EventoRequestDTO(
    @NotBlank @Size(min = 3, max = 80, message = "o título deve ter entre 3 e 80 caracteres")
    String title,
    @NotBlank
    String category,
    @NotBlank @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "a data deve estar no formato AAAA-MM-DD")
    String date,
    @NotBlank @Pattern(regexp = "^\\d{2}:\\d{2}$", message = "o horário deve estar no formato HH:MM")
    String time,
    @Size(max = 9)
    String cep,
    @NotBlank @Size(max = 90, message = "o local deve ter até 90 caracteres")
    String place,
    @Min(3)
    int capacity,
    @NotBlank @Size(min = 10, max = 260, message = "a descrição deve ter entre 10 e 260 caracteres")
    String description
) {}
