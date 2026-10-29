package chegaai.controller.dto;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
public record ParticipantesResponseDTO(UUID eventoId, String inicio, String status,
    boolean podeMarcarPresenca, boolean podeConcluir, BigDecimal descontoPorAusencia,
    List<ParticipanteDTO> participantes) {
    public record ParticipanteDTO(UUID id, String nome, String status, boolean compareceu,
                                  boolean organizador) {}
}
