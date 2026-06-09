package chegae.controller.dto;

import java.util.UUID;

/**
 * DTO leve de resposta para o endpoint POST /api/eventos/{id}/comentarios.
 *
 * <p><b>Otimização de performance (Medição 2):</b> Substitui a devolução
 * do {@link EventoResponseDTO} inteiro (que forçava uma query extra de
 * reconstrução do Evento com todas as inscrições e comentários). Retorna
 * apenas os dados do comentário recém-criado, reduzindo o payload HTTP
 * e liberando a conexão HikariCP imediatamente após o INSERT.</p>
 */
public record ComentarioResponseDTO(
    UUID id,
    String author,
    String text,
    String time
) {}
