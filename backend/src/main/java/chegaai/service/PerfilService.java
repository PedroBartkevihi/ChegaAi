package chegaai.service;
import chegaai.controller.dto.AvaliacoesPendentesResponseDTO;
import chegaai.controller.dto.EventoResumoDTO;
import chegaai.controller.dto.MinhaInscricaoResponseDTO;
import chegaai.domain.enums.StatusUsuario;
import chegaai.domain.model.Inscricao;
import chegaai.domain.model.Usuario;
import chegaai.repository.InscricaoRepository;
import chegaai.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PerfilService {
    private final UsuarioRepository usuarioRepository;
    private final InscricaoRepository inscricaoRepository;
    private final PendenciasAvaliacaoService pendenciasAvaliacaoService;
    private final FeedService feedService;

    public List<MinhaInscricaoResponseDTO> minhasInscricoes(UUID usuarioId) {
        buscarConta(usuarioId);
        List<Inscricao> inscricoes = inscricaoRepository.findActiveByUsuarioId(usuarioId);
        // A consulta já traz os rolés com organizador, categoria e endereço; os resumos só contam as inscrições.
        Map<UUID, EventoResumoDTO> eventos = feedService.resumos(inscricoes.stream().map(Inscricao::getEvento).toList())
            .stream().collect(Collectors.toMap(EventoResumoDTO::id, Function.identity()));
        return inscricoes.stream().map(i ->
            new MinhaInscricaoResponseDTO(i.getEvento().getId(), switch(i.getStatusInscricao()) {
                case CONFIRMADA -> "CONFIRMADO";
                case SOLICITADA -> "FILA";
                case AUSENTE -> "AUSENTE";
                case CANCELADA -> throw new IllegalStateException("Inscrição cancelada não pertence ao perfil ativo.");
            }, Boolean.TRUE.equals(i.getCompareceu()), eventos.get(i.getEvento().getId()))).toList();
    }
    public AvaliacoesPendentesResponseDTO avaliacoesPendentes(UUID usuarioId) {
        Usuario usuario = buscarConta(usuarioId);
        return new AvaliacoesPendentesResponseDTO(usuario.estaAtivo(OffsetDateTime.now()),
            pendenciasAvaliacaoService.buscarEventos(usuarioId));
    }
    private Usuario buscarConta(UUID id) {
        Usuario usuario = usuarioRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Usuário não encontrado: " + id));
        if (usuario.getStatusUsuario() == StatusUsuario.BANIDO) {
            throw new AccessDeniedException("Esta conta foi banida pela moderação.");
        }
        return usuario;
    }
}
