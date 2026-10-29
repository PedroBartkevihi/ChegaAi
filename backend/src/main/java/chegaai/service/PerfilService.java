package chegaai.service;
import chegaai.controller.dto.AvaliacoesPendentesResponseDTO;
import chegaai.controller.dto.MinhaInscricaoResponseDTO;
import chegaai.domain.enums.StatusUsuario;
import chegaai.domain.model.Usuario;
import chegaai.repository.InscricaoRepository;
import chegaai.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PerfilService {
    private final UsuarioRepository usuarioRepository;
    private final InscricaoRepository inscricaoRepository;
    private final PendenciasAvaliacaoService pendenciasAvaliacaoService;

    public List<MinhaInscricaoResponseDTO> minhasInscricoes(UUID usuarioId) {
        buscarConta(usuarioId);
        return inscricaoRepository.findActiveByUsuarioId(usuarioId).stream().map(i ->
            new MinhaInscricaoResponseDTO(i.getEvento().getId(), switch(i.getStatusInscricao()) {
                case CONFIRMADA -> "CONFIRMADO";
                case SOLICITADA -> "FILA";
                case AUSENTE -> "AUSENTE";
                case CANCELADA -> throw new IllegalStateException("Inscrição cancelada não pertence ao perfil ativo.");
            }, Boolean.TRUE.equals(i.getCompareceu()))).toList();
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
