package chegaai.service;
import chegaai.config.FusoHorario;
import chegaai.controller.dto.AvaliacoesPendentesResponseDTO.EventoPendenteDTO;
import chegaai.controller.dto.AvaliacoesPendentesResponseDTO.ParticipantePendenteDTO;
import chegaai.domain.enums.StatusInscricao;
import chegaai.repository.AvaliacaoRepository;
import chegaai.repository.InscricaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PendenciasAvaliacaoService {
    private final InscricaoRepository inscricaoRepository;
    private final AvaliacaoRepository avaliacaoRepository;
    private final FusoHorario fusoHorario;

    // Esta lista por UUID e a fonte unica para a tela e para o bloqueio de novas inscricoes.
    public List<EventoPendenteDTO> buscarEventos(UUID usuarioId) {
        List<EventoPendenteDTO> eventos = new ArrayList<>();
        for (var inscricao : inscricaoRepository.findCompareceuEmEventosConcluidos(usuarioId)) {
            var evento = inscricao.getEvento();
            var avaliados = new HashSet<>(avaliacaoRepository.buscarIdsAvaliados(usuarioId, evento.getId()));
            var participantes = inscricaoRepository.listarParticipantes(evento.getId(), List.of(StatusInscricao.CONFIRMADA))
                .stream().map(i -> i.getUsuario())
                .filter(u -> !u.getId().equals(usuarioId) && !avaliados.contains(u.getId()))
                .map(u -> new ParticipantePendenteDTO(u.getId(), u.getNome())).toList();
            if (!participantes.isEmpty()) {
                eventos.add(new EventoPendenteDTO(evento.getId(), evento.getTitulo(),
                    fusoHorario.data(evento.getDataHoraRealizacao()).toString(),
                    fusoHorario.hora(evento.getDataHoraRealizacao()), participantes));
            }
        }
        return eventos.stream().sorted(Comparator.comparing(EventoPendenteDTO::data)
            .thenComparing(EventoPendenteDTO::hora).thenComparing(e -> e.eventoId().toString())).toList();
    }
    public boolean temPendencias(UUID usuarioId) {
        return !buscarEventos(usuarioId).isEmpty();
    }
}
