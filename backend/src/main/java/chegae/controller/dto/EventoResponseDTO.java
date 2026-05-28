package chegae.controller.dto;

import java.util.List;
import java.util.UUID;

/**
 * DTO de resposta de evento, formatado para compatibilidade direta com o frontend React.
 * Os nomes dos campos são em inglês para que o JSON retornado seja consumido
 * diretamente pelo App.jsx sem necessidade de mapeamento.
 */
public record EventoResponseDTO(
    UUID id,
    String title,
    String category,
    String date,
    String time,
    String cep,
    String place,
    int distance,
    int capacity,
    String status,
    OrganizerDTO organizer,
    List<String> confirmed,
    List<String> waitlist,
    List<CommentDTO> comments,
    int reports,
    String description
) {
    public record OrganizerDTO(
        UUID id,
        String name,
        double honor
    ) {}

    public record CommentDTO(
        String author,
        String text,
        String time
    ) {}
}
