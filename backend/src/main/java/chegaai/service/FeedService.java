package chegaai.service;
import chegaai.controller.dto.EventoResumoDTO;
import chegaai.controller.dto.FeedPaginaDTO;
import chegaai.controller.dto.FeedResumoDTO;
import chegaai.domain.enums.StatusInscricao;
import chegaai.domain.model.Evento;
import chegaai.repository.ContagemInscricoes;
import chegaai.repository.EventoFeedRepository;
import chegaai.repository.EventoRepository;
import chegaai.repository.FiltroFeed;
import chegaai.repository.InscricaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Feed paginado: no máximo três consultas por página, qualquer que seja o número de rolés
 * (a página, as contagens agrupadas e o total), em vez de três consultas por rolé.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedService {
    private static final List<StatusInscricao> CONTADOS = List.of(StatusInscricao.CONFIRMADA, StatusInscricao.SOLICITADA);
    private final EventoRepository eventoRepository;
    private final InscricaoRepository inscricaoRepository;
    private final RegrasEventoService regrasEventoService;
    private final ApresentacaoEvento apresentacao;

    public FeedPaginaDTO pagina(FiltroFeed filtro) {
        OffsetDateTime agora = OffsetDateTime.now();
        BigDecimal honraEmbaixador = regrasEventoService.honraEmbaixador();
        List<Evento> eventos = eventoRepository.buscarPagina(filtro, agora, honraEmbaixador);
        // Uma primeira página incompleta já é o total: dispensa a consulta de contagem.
        long total = filtro.pagina() == 0 && eventos.size() < filtro.tamanho()
            ? eventos.size()
            : eventoRepository.contar(filtro, agora, honraEmbaixador);
        return new FeedPaginaDTO(resumos(eventos), filtro.pagina(), filtro.tamanho(), total,
            (long) filtro.deslocamento() + eventos.size() < total);
    }

    public FeedResumoDTO resumo() {
        EventoFeedRepository.NumerosDoFeed numeros = eventoRepository.numeros(regrasEventoService.honraEmbaixador());
        return new FeedResumoDTO(numeros.total(), numeros.abertos(), numeros.naFila(), numeros.deEmbaixadores());
    }

    /**
     * Resumos na ordem recebida. Os rolés precisam chegar com organizador, categoria e endereço
     * carregados; as contagens de todos vêm numa consulta só.
     */
    public List<EventoResumoDTO> resumos(List<Evento> eventos) {
        if (eventos.isEmpty()) {
            return List.of();
        }
        Map<UUID, Map<StatusInscricao, Long>> contagens = new HashMap<>();
        for (ContagemInscricoes contagem : inscricaoRepository.contarPorEventoEStatus(
                eventos.stream().map(Evento::getId).toList(), CONTADOS)) {
            contagens.computeIfAbsent(contagem.eventoId(), id -> new EnumMap<>(StatusInscricao.class))
                .put(contagem.status(), contagem.total());
        }
        return eventos.stream().map(evento -> {
            Map<StatusInscricao, Long> doEvento = contagens.getOrDefault(evento.getId(), Map.of());
            return new EventoResumoDTO(
                evento.getId(),
                evento.getTitulo(),
                evento.getCategoria().getNome(),
                apresentacao.data(evento),
                apresentacao.hora(evento),
                apresentacao.local(evento),
                0,
                evento.getLimiteParticipantes(),
                apresentacao.status(evento),
                apresentacao.organizador(evento),
                doEvento.getOrDefault(StatusInscricao.CONFIRMADA, 0L),
                doEvento.getOrDefault(StatusInscricao.SOLICITADA, 0L),
                evento.getDescricao()
            );
        }).toList();
    }
}
