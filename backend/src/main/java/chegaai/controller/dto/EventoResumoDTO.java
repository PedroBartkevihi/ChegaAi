package chegaai.controller.dto;
import java.util.UUID;
/** Rolé no feed: os campos do card, com as contagens no lugar dos nomes e sem os comentários. */
public record EventoResumoDTO(
    UUID id,
    String title,
    String category,
    String date,
    String time,
    String place,
    int capacity,
    String status,
    EventoResponseDTO.OrganizerDTO organizer,
    long confirmedCount,
    long waitlistCount,
    String description
) {}
