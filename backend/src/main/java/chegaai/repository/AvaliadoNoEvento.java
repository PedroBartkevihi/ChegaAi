package chegaai.repository;
import java.util.UUID;
/** Quem uma pessoa já avaliou em um rolé, vindo de uma consulta sobre vários rolés de uma vez. */
public record AvaliadoNoEvento(UUID eventoId, UUID avaliadoId) {}
