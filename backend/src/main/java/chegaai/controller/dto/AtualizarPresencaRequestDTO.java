package chegaai.controller.dto;
import jakarta.validation.constraints.NotNull;
public record AtualizarPresencaRequestDTO(@NotNull Boolean presente) {}
