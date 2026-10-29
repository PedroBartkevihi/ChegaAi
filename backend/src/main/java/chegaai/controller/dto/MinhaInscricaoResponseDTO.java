package chegaai.controller.dto;
import java.util.UUID;
public record MinhaInscricaoResponseDTO(UUID eventoId, String status, boolean compareceu) {}
