package chegae.controller.dto;

/**
 * DTO para criação de um novo evento.
 * Campos nomeados em inglês para compatibilidade com o frontend.
 */
public record EventoRequestDTO(
    String title,
    String category,
    String date,
    String time,
    String cep,
    String place,
    int capacity,
    int distance,
    String description
) {}
