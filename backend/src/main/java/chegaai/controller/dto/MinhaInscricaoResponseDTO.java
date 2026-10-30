package chegaai.controller.dto;
import java.util.UUID;
/** O resumo do rolé vem junto para o perfil não depender do feed carregado na tela. */
public record MinhaInscricaoResponseDTO(UUID eventoId, String status, boolean compareceu, EventoResumoDTO evento) {}
