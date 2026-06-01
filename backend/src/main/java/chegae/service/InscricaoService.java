package chegae.service;

import chegae.controller.dto.InscricaoResponseDTO;
import chegae.domain.enums.StatusEvento;
import chegae.domain.enums.StatusInscricao;
import chegae.domain.model.Evento;
import chegae.domain.model.Inscricao;
import chegae.domain.model.Usuario;
import chegae.repository.AvaliacaoRepository;
import chegae.repository.EventoRepository;
import chegae.repository.InscricaoRepository;
import chegae.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * Serviço de gerenciamento de inscrições em eventos.
 *
 * <p>Implementa as regras de negócio críticas:</p>
 * <ul>
 *   <li><b>RN001 (Fila FIFO):</b> Se a lotação máxima for atingida, o usuário
 *       entra como SOLICITADA (fila de espera). Ao cancelar, o primeiro
 *       da fila é promovido para CONFIRMADA.</li>
 *   <li><b>RN004 (Bloqueio por Honra):</b> Usuários com honra &lt; 3.0
 *       são impedidos de se inscrever.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class InscricaoService {

    private final InscricaoRepository inscricaoRepository;
    private final EventoRepository eventoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AvaliacaoRepository avaliacaoRepository;

    @Value("${chegae.regras.honra-minima-inscricao:3.0}")
    private double honraMinimaInscricao;

    /**
     * Toggle de inscrição: inscreve se não inscrito, desinscreve se já inscrito.
     *
     * <p>Fluxo de inscrição:</p>
     * <ol>
     *   <li>Verifica se o evento existe e está ATIVO/não-travado</li>
     *   <li>Verifica RN004: honra mínima para inscrição</li>
     *   <li>Se já inscrito (CONFIRMADA ou SOLICITADA) → cancela e promove fila</li>
     *   <li>Se não inscrito e há vaga → CONFIRMADA</li>
     *   <li>Se não inscrito e sem vaga → SOLICITADA (fila FIFO)</li>
     * </ol>
     */
    @Transactional
    public InscricaoResponseDTO toggleInscricao(UUID eventoId, UUID usuarioId) {
        Evento evento = eventoRepository.findById(eventoId)
            .orElseThrow(() -> new NoSuchElementException("Evento não encontrado: " + eventoId));

        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new NoSuchElementException("Usuário não encontrado: " + usuarioId));

        // Verificar se o evento está disponível para inscrições
        if (evento.getStatusEvento() != StatusEvento.ATIVO) {
            throw new IllegalStateException("Este evento não está aberto para inscrições.");
        }
        if (evento.getTravado() != null && evento.getTravado()) {
            throw new IllegalStateException("Este evento está travado pela moderação.");
        }

        // Verificar se já existe inscrição ativa
        Optional<Inscricao> inscricaoExistente = inscricaoRepository
            .findByUsuarioIdAndEventoId(usuarioId, eventoId);

        if (inscricaoExistente.isPresent()) {
            Inscricao inscricao = inscricaoExistente.get();

            // Se já está cancelada, tratar como nova inscrição
            if (inscricao.getStatusInscricao() == StatusInscricao.CANCELADA) {
                return realizarNovaInscricao(evento, usuario, inscricao);
            }

            // Cancelar inscrição existente (CONFIRMADA ou SOLICITADA)
            return cancelarInscricao(evento, inscricao);
        }

        // Nova inscrição
        return realizarNovaInscricao(evento, usuario, null);
    }

    /**
     * Realiza uma nova inscrição com lógica RN001 (fila FIFO) e RN004 (honra).
     */
    private InscricaoResponseDTO realizarNovaInscricao(Evento evento, Usuario usuario,
                                                        Inscricao inscricaoReaproveitada) {
        // RN007: verificar avaliações pendentes de eventos anteriores
        verificarAvaliacoesPendentes(usuario);

        // RN004: verificar honra mínima para inscrição
        if (usuario.getHonraMedia().compareTo(BigDecimal.valueOf(honraMinimaInscricao)) < 0) {
            throw new IllegalStateException(
                "Sua reputação de honra (" + usuario.getHonraMedia() +
                ") está abaixo do mínimo (" + honraMinimaInscricao +
                "). Você não pode se inscrever em novos eventos.");
        }

        // Contar confirmados atuais
        long confirmados = inscricaoRepository
            .countByEventoIdAndStatusInscricao(evento.getId(), StatusInscricao.CONFIRMADA);

        boolean temVaga = confirmados < evento.getLimiteParticipantes();

        Inscricao inscricao;
        if (inscricaoReaproveitada != null) {
            // Reativar inscrição cancelada
            inscricao = inscricaoReaproveitada;
        } else {
            // Criar nova inscrição
            inscricao = new Inscricao();
            inscricao.setUsuario(usuario);
            inscricao.setEvento(evento);
        }

        // RN001: definir status baseado na disponibilidade de vagas
        if (temVaga) {
            inscricao.setStatusInscricao(StatusInscricao.CONFIRMADA);
            inscricaoRepository.save(inscricao);
            return new InscricaoResponseDTO(
                evento.getId(), usuario.getId(),
                "CONFIRMADO",
                "Inscrição confirmada! Você está participando do evento."
            );
        } else {
            // Fila de espera FIFO
            inscricao.setStatusInscricao(StatusInscricao.SOLICITADA);
            inscricaoRepository.save(inscricao);
            return new InscricaoResponseDTO(
                evento.getId(), usuario.getId(),
                "FILA",
                "Evento lotado. Você foi adicionado à fila de espera."
            );
        }
    }

    /**
     * Cancela uma inscrição existente e promove o primeiro da fila (RN001 FIFO).
     */
    private InscricaoResponseDTO cancelarInscricao(Evento evento, Inscricao inscricao) {
        boolean eraConfirmado = inscricao.getStatusInscricao() == StatusInscricao.CONFIRMADA;

        inscricao.setStatusInscricao(StatusInscricao.CANCELADA);
        inscricaoRepository.save(inscricao);

        // RN001: Se era confirmado, promover o primeiro da fila
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

    // -----------------------------------------------------------------------
    // RN007 — Verificação de avaliações pendentes
    // -----------------------------------------------------------------------

    /**
     * Verifica se o usuário possui avaliações pendentes de eventos concluídos (RN007).
     *
     * <p>Um usuário tem avaliações pendentes se participou (compareceu) de um evento
     * já concluído e ainda não avaliou todos os outros participantes confirmados
     * desse evento.</p>
     *
     * @param usuario usuário a verificar
     * @throws IllegalStateException se houver avaliações pendentes
     */
    private void verificarAvaliacoesPendentes(Usuario usuario) {
        List<Inscricao> inscricoesComparecidas = inscricaoRepository
            .findCompareceuEmEventosConcluidos(usuario.getId());

        for (Inscricao inscricao : inscricoesComparecidas) {
            UUID eventoId = inscricao.getEvento().getId();

            // Contar outros participantes confirmados no evento (excluindo o próprio usuário)
            long outrosConfirmados = inscricaoRepository
                .findByEventoIdAndStatusInscricao(eventoId, StatusInscricao.CONFIRMADA)
                .stream()
                .filter(i -> !i.getUsuario().getId().equals(usuario.getId()))
                .count();

            // Contar avaliações já feitas pelo usuário nesse evento
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
