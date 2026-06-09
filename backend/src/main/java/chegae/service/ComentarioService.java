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

/**
 * Serviço para gerenciamento de comentários em eventos.
 */
@Service
@RequiredArgsConstructor
public class ComentarioService {

    private final ComentarioRepository comentarioRepository;
    private final EventoRepository eventoRepository;
    private final InscricaoRepository inscricaoRepository;
    private final UsuarioRepository usuarioRepository;

    /**
     * Adiciona um comentário a um evento.
     *
     * <p><b>Otimização V2:</b> Usa {@code getReferenceById()} em vez de
     * {@code findById()} para o Evento. O Hibernate retorna um proxy JPA
     * sem executar SELECT — o INSERT usa diretamente o UUID como FK.
     * Isso elimina uma query pesada (Evento + proxies de coleções LAZY)
     * e libera a conexão HikariCP mais rápido sob concorrência.</p>
     *
     * @param eventoId  ID do evento
     * @param usuarioId ID do autor do comentário
     * @param texto     Texto do comentário
     * @return o comentário salvo
     */
    @Transactional
    public Comentario adicionarComentario(UUID eventoId, UUID usuarioId, String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            throw new IllegalArgumentException("O texto do comentário não pode ser vazio.");
        }

        // Validação de existência leve: SELECT 1 (sem carregar a entidade inteira)
        if (!eventoRepository.existsById(eventoId)) {
            throw new NoSuchElementException("Evento não encontrado: " + eventoId);
        }

        // Proxy JPA: não executa SELECT, apenas referencia o ID para o INSERT
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
