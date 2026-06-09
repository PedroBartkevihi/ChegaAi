package chegae.service;
import chegae.domain.enums.StatusInscricao;
import chegae.domain.model.Comentario;
import chegae.domain.model.Evento;
import chegae.domain.model.Inscricao;
import chegae.domain.model.Usuario;
import chegae.repository.ComentarioRepository;
import chegae.repository.EventoRepository;
import chegae.repository.InscricaoRepository;
import chegae.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.NoSuchElementException;
import java.util.UUID;
@Service
@RequiredArgsConstructor
public class ComentarioService {
    private final ComentarioRepository comentarioRepository;
    private final EventoRepository eventoRepository;
    private final InscricaoRepository inscricaoRepository;
    private final UsuarioRepository usuarioRepository;
    @Transactional
    public Comentario adicionarComentario(UUID eventoId, UUID usuarioId, String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            throw new IllegalArgumentException("O texto do comentário não pode ser vazio.");
        }
        if (!eventoRepository.existsById(eventoId)) {
            throw new NoSuchElementException("Evento não encontrado: " + eventoId);
        }
        Evento evento = eventoRepository.getReferenceById(eventoId);
        Usuario autor = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new NoSuchElementException("Usuário não encontrado: " + usuarioId));
        Inscricao inscricao = inscricaoRepository.findByUsuarioIdAndEventoId(usuarioId, eventoId)
            .orElseThrow(() -> new IllegalStateException(
                "Apenas participantes confirmados ou na lista de espera podem comentar."));
        if (inscricao.getStatusInscricao() != StatusInscricao.CONFIRMADA
                && inscricao.getStatusInscricao() != StatusInscricao.SOLICITADA) {
            throw new IllegalStateException(
                "Apenas participantes confirmados ou na lista de espera podem comentar.");
        }
        Comentario comentario = new Comentario();
        comentario.setAutor(autor);
        comentario.setEvento(evento);
        comentario.setTexto(texto.trim());
        return comentarioRepository.save(comentario);
    }
}
