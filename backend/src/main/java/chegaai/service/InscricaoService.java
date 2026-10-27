package chegaai.service;
import chegaai.controller.dto.InscricaoResponseDTO;
import chegaai.domain.enums.StatusEvento;
import chegaai.domain.enums.StatusInscricao;
import chegaai.domain.model.Evento;
import chegaai.domain.model.Inscricao;
import chegaai.domain.model.Usuario;
import chegaai.repository.AvaliacaoRepository;
import chegaai.repository.EventoRepository;
import chegaai.repository.InscricaoRepository;
import chegaai.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
@Service
@RequiredArgsConstructor
public class InscricaoService {
    private final InscricaoRepository inscricaoRepository;
    private final EventoRepository eventoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AvaliacaoRepository avaliacaoRepository;
    @Value("${chegaai.regras.honra-minima-inscricao:3.0}")
    private double honraMinimaInscricao;
    @Transactional
    public InscricaoResponseDTO toggleInscricao(UUID eventoId, UUID usuarioId) {
        // Trava o evento até o commit: pedidos simultâneos no mesmo evento passam um por vez, então a
        // contagem de vagas (realizarNovaInscricao) e a promoção da fila (cancelarInscricao) sempre
        // enxergam o que o pedido anterior gravou.
        Evento evento = eventoRepository.findByIdForUpdate(eventoId)
            .orElseThrow(() -> new NoSuchElementException("Evento não encontrado: " + eventoId));
        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new NoSuchElementException("Usuário não encontrado: " + usuarioId));
        if (evento.getStatusEvento() != StatusEvento.ATIVO) {
            throw new IllegalStateException("Este evento não está aberto para inscrições.");
        }
        if (evento.getTravado() != null && evento.getTravado()) {
            throw new IllegalStateException("Este evento está travado pela moderação.");
        }
        Optional<Inscricao> inscricaoExistente = inscricaoRepository
            .findByUsuarioIdAndEventoId(usuarioId, eventoId);
        if (inscricaoExistente.isPresent()) {
            Inscricao inscricao = inscricaoExistente.get();
            if (inscricao.getStatusInscricao() == StatusInscricao.CANCELADA) {
                return realizarNovaInscricao(evento, usuario, inscricao);
            }
            return cancelarInscricao(evento, inscricao);
        }
        return realizarNovaInscricao(evento, usuario, null);
    }
    private InscricaoResponseDTO realizarNovaInscricao(Evento evento, Usuario usuario,
                                                        Inscricao inscricaoReaproveitada) {
        verificarAvaliacoesPendentes(usuario);
        if (usuario.getHonraMedia().compareTo(BigDecimal.valueOf(honraMinimaInscricao)) < 0) {
            throw new IllegalStateException(
                "Sua reputação de honra (" + usuario.getHonraMedia() +
                ") está abaixo do mínimo (" + honraMinimaInscricao +
                "). Você não pode se inscrever em novos eventos.");
        }
        long confirmados = inscricaoRepository
            .countByEventoIdAndStatusInscricao(evento.getId(), StatusInscricao.CONFIRMADA);
        boolean temVaga = confirmados < evento.getLimiteParticipantes();
        Inscricao inscricao;
        if (inscricaoReaproveitada != null) {
            inscricao = inscricaoReaproveitada;
        } else {
            inscricao = new Inscricao();
            inscricao.setUsuario(usuario);
            inscricao.setEvento(evento);
        }
        if (temVaga) {
            inscricao.setStatusInscricao(StatusInscricao.CONFIRMADA);
            inscricaoRepository.save(inscricao);
            return new InscricaoResponseDTO(
                evento.getId(), usuario.getId(),
                "CONFIRMADO",
                "Inscrição confirmada! Você está participando do evento."
            );
        } else {
            inscricao.setStatusInscricao(StatusInscricao.SOLICITADA);
            inscricaoRepository.save(inscricao);
            return new InscricaoResponseDTO(
                evento.getId(), usuario.getId(),
                "FILA",
                "Evento lotado. Você foi adicionado à fila de espera."
            );
        }
    }
    private InscricaoResponseDTO cancelarInscricao(Evento evento, Inscricao inscricao) {
        boolean eraConfirmado = inscricao.getStatusInscricao() == StatusInscricao.CONFIRMADA;
        inscricao.setStatusInscricao(StatusInscricao.CANCELADA);
        inscricaoRepository.save(inscricao);
        if (eraConfirmado) {
            Optional<Inscricao> primeiroFila = inscricaoRepository
                .findFirstByEvento_IdAndStatusInscricaoOrderByDataHoraSolicitacaoAsc(
                    evento.getId(),
                    StatusInscricao.SOLICITADA
                );
            if (primeiroFila.isPresent()) {
                Inscricao promovido = primeiroFila.get();
                promovido.setStatusInscricao(StatusInscricao.CONFIRMADA);
                inscricaoRepository.save(promovido);
            }
        }
        return new InscricaoResponseDTO(
            evento.getId(), inscricao.getUsuario().getId(),
            "CANCELADO",
            "Inscrição cancelada com sucesso."
        );
    }
    @Transactional
    public void registrarPresenca(UUID eventoId, UUID organizadorId, UUID participanteId) {
        Evento evento = eventoRepository.findById(eventoId)
            .orElseThrow(() -> new NoSuchElementException("Evento não encontrado: " + eventoId));
        if (!evento.getOrganizador().getId().equals(organizadorId)) {
            throw new IllegalStateException("Apenas o organizador pode registrar presenças.");
        }
        if (evento.getStatusEvento() != StatusEvento.ATIVO) {
            throw new IllegalStateException("Presenças só podem ser registradas antes de concluir o evento.");
        }
        Inscricao inscricao = inscricaoRepository.findByUsuarioIdAndEventoId(participanteId, eventoId)
            .orElseThrow(() -> new NoSuchElementException("Este usuário não está inscrito no evento."));
        inscricao.registrarPresenca();
        inscricaoRepository.save(inscricao);
    }
    private void verificarAvaliacoesPendentes(Usuario usuario) {
        List<Inscricao> inscricoesComparecidas = inscricaoRepository
            .findCompareceuEmEventosConcluidos(usuario.getId());
        for (Inscricao inscricao : inscricoesComparecidas) {
            UUID eventoId = inscricao.getEvento().getId();
            long outrosConfirmados = inscricaoRepository
                .findByEventoIdAndStatusInscricao(eventoId, StatusInscricao.CONFIRMADA)
                .stream()
                .filter(i -> !i.getUsuario().getId().equals(usuario.getId()))
                .count();
            long avaliacoesFeitas = avaliacaoRepository
                .contarAvaliacoesFeitas(usuario.getId(), eventoId);
            if (avaliacoesFeitas < outrosConfirmados) {
                throw new IllegalStateException(
                    "Você possui avaliações pendentes de eventos anteriores. " +
                    "Avalie os participantes para se inscrever em novos eventos.");
            }
        }
    }
}
