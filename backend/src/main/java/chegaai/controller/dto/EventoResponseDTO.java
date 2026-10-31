package chegaai.controller.dto;
import java.util.List;
import java.util.UUID;
public record EventoResponseDTO(
    UUID id,
    String title,
    String category,
    String date,
    String time,
    String cep,
    String place,
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
        double honor,
        boolean isAmbassador
    ) {}
    public record CommentDTO(
        String author,
        String text,
        String time
    ) {}
}
