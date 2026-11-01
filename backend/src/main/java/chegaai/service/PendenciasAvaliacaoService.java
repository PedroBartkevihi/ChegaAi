package chegaai.service;
import chegaai.config.FusoHorario;
import chegaai.controller.dto.AvaliacoesPendentesResponseDTO.EventoPendenteDTO;
import chegaai.controller.dto.AvaliacoesPendentesResponseDTO.ParticipantePendenteDTO;
import chegaai.domain.model.Usuario;
import chegaai.repository.AvaliacaoRepository;
import chegaai.repository.InscricaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PendenciasAvaliacaoService {
    private final InscricaoRepository inscricaoRepository;
    private final AvaliacaoRepository avaliacaoRepository;
    private final FusoHorario fusoHorario;

    // Esta lista por UUID e a fonte unica para a tela e para o bloqueio de novas inscricoes.
    // Três consultas, qualquer que seja o histórico: os rolés concluídos de que a pessoa participou,
    // quem ela já avaliou neles e quem esteve presente em cada um.
    public List<EventoPendenteDTO> buscarEventos(UUID usuarioId) {
        var participacoes = inscricaoRepository.findCompareceuEmEventosConcluidos(usuarioId);
        if (participacoes.isEmpty()) {
            return List.of();
        }
        var eventoIds = participacoes.stream().map(i -> i.getEvento().getId()).toList();
        Map<UUID, Set<UUID>> avaliados = new HashMap<>();
        for (var avaliado : avaliacaoRepository.buscarAvaliados(usuarioId, eventoIds)) {
            avaliados.computeIfAbsent(avaliado.eventoId(), id -> new HashSet<>()).add(avaliado.avaliadoId());
        }
        Map<UUID, List<Usuario>> presentes = new HashMap<>();
        for (var inscricao : inscricaoRepository.listarPresentes(eventoIds)) {
            presentes.computeIfAbsent(inscricao.getEvento().getId(), id -> new ArrayList<>()).add(inscricao.getUsuario());
        }
        List<EventoPendenteDTO> eventos = new ArrayList<>();
        for (var inscricao : participacoes) {
            var evento = inscricao.getEvento();
            var jaAvaliados = avaliados.getOrDefault(evento.getId(), Set.of());
            var participantes = presentes.getOrDefault(evento.getId(), List.of()).stream()
                .filter(u -> !u.getId().equals(usuarioId) && !jaAvaliados.contains(u.getId()))
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
