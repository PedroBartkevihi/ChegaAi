package chegaai.repository;
import chegaai.domain.model.Evento;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** Consultas do feed paginado; a implementação monta o JPQL só com os filtros pedidos. */
public interface EventoFeedRepository {
    /** Página de rolés com organizador, categoria e endereço já carregados. */
    List<Evento> buscarPagina(FiltroFeed filtro, OffsetDateTime agora, BigDecimal honraEmbaixador);
    long contar(FiltroFeed filtro, OffsetDateTime agora, BigDecimal honraEmbaixador);
    /** Os números do topo do feed numa consulta só. */
    NumerosDoFeed numeros(BigDecimal honraEmbaixador);

    record NumerosDoFeed(long total, long abertos, long naFila, long deEmbaixadores) {}
}
