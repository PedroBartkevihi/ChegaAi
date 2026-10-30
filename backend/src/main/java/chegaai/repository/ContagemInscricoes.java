package chegaai.repository;
import chegaai.domain.enums.StatusInscricao;
import java.util.UUID;
/** Quantas inscrições um rolé tem em um status, vindas de uma consulta agrupada. */
public record ContagemInscricoes(UUID eventoId, StatusInscricao status, Long total) {}
