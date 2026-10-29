package chegaai.controller.dto;
import java.util.List;
import java.util.UUID;
public record AvaliacoesPendentesResponseDTO(boolean podeAvaliar, List<EventoPendenteDTO> eventos) {
    public record EventoPendenteDTO(UUID eventoId, String titulo, String data, String hora,
                                   List<ParticipantePendenteDTO> participantes) {}
    public record ParticipantePendenteDTO(UUID id, String nome) {}
}
