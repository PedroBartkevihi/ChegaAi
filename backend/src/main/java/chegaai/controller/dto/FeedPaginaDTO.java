package chegaai.controller.dto;
import java.util.List;
/** Uma página do feed; {@code total} conta os rolés da seção com os mesmos filtros. */
public record FeedPaginaDTO(List<EventoResumoDTO> items, int page, int size, long total, boolean hasNext) {}
