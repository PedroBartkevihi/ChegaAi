package chegaai.controller.dto;
/**
 * Números do topo do feed, sobre todos os rolés, sem filtros (como a tela calculava): abertos,
 * pessoas na fila de espera e rolés de Embaixadores, além do total.
 */
public record FeedResumoDTO(long total, long active, long waitlist, long ambassadors) {}
