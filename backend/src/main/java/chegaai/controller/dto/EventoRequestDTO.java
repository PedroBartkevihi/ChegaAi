package chegaai.controller.dto;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
public record EventoRequestDTO(
    @NotBlank @Size(min = 3, max = 150, message = "o título deve ter entre 3 e 150 caracteres")
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
    @NotBlank @Size(min = 10, max = 3000, message = "a descrição deve ter entre 10 e 3000 caracteres")
    String description
) {}
