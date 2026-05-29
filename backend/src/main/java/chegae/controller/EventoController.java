package chegae.controller;

import chegae.controller.dto.*;
import chegae.service.ComentarioService;
import chegae.service.EventoService;
import chegae.service.InscricaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller REST para gerenciamento de eventos.
 *
 * <p>Expõe os endpoints consumidos pelo frontend React:</p>
 * <ul>
 *   <li>{@code GET  /api/eventos}               — listar todos os eventos</li>
 *   <li>{@code POST /api/eventos}               — criar novo evento</li>
 *   <li>{@code GET  /api/eventos/{id}}          — buscar evento por ID</li>
 *   <li>{@code POST /api/eventos/{id}/inscricao}    — toggle inscrição</li>
 *   <li>{@code POST /api/eventos/{id}/comentarios}  — adicionar comentário</li>
 * </ul>
 *
 * <p>A identificação do usuário é feita via header {@code X-User-Id} (UUID),
 * enviado pelo frontend após o login simplificado.</p>
 */
@RestController
@RequestMapping("/eventos")
@RequiredArgsConstructor
public class EventoController {

    private final EventoService eventoService;
    private final InscricaoService inscricaoService;
    private final ComentarioService comentarioService;

    /**
     * GET /api/eventos
     * Lista todos os eventos no formato consumido pelo frontend.
     */
    @GetMapping
    public ResponseEntity<List<EventoResponseDTO>> listarTodos() {
        return ResponseEntity.ok(eventoService.listarTodos());
    }

    /**
     * GET /api/eventos/{id}
     * Busca um evento específico por ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<EventoResponseDTO> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(eventoService.buscarPorId(id));
    }

    /**
     * POST /api/eventos
     * Cria um novo evento. Requer header X-User-Id.
     */
    @PostMapping
    public ResponseEntity<EventoResponseDTO> criarEvento(
            @RequestBody EventoRequestDTO dto,
            @RequestHeader("X-User-Id") UUID usuarioId) {
        EventoResponseDTO criado = eventoService.criarEvento(dto, usuarioId);
        return ResponseEntity.status(HttpStatus.CREATED).body(criado);
    }

    /**
     * POST /api/eventos/{id}/inscricao
     * Toggle de inscrição: inscreve se não inscrito, cancela se já inscrito.
     * Aplica regras RN001 (fila FIFO) e RN004 (bloqueio por honra).
     */
    @PostMapping("/{id}/inscricao")
    public ResponseEntity<InscricaoResponseDTO> toggleInscricao(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID usuarioId) {
        InscricaoResponseDTO resultado = inscricaoService.toggleInscricao(id, usuarioId);
        return ResponseEntity.ok(resultado);
    }

    /**
     * POST /api/eventos/{id}/concluir
     * Conclui o evento e aplica RN008 (dedução de honra por ausência).
     * Somente o organizador pode concluir o evento.
     */
    @PostMapping("/{id}/concluir")
    public ResponseEntity<EventoResponseDTO> concluirEvento(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID usuarioId) {
        EventoResponseDTO concluido = eventoService.concluirEvento(id, usuarioId);
        return ResponseEntity.ok(concluido);
    }

    /**
     * POST /api/eventos/{id}/comentarios
     * Adiciona um comentário ao evento. Requer header X-User-Id.
     */
    @PostMapping("/{id}/comentarios")
    public ResponseEntity<EventoResponseDTO> adicionarComentario(
            @PathVariable UUID id,
            @RequestBody ComentarioRequestDTO dto,
            @RequestHeader("X-User-Id") UUID usuarioId) {
        comentarioService.adicionarComentario(id, usuarioId, dto.text());
        // Retorna o evento atualizado para o frontend
        EventoResponseDTO eventoAtualizado = eventoService.buscarPorId(id);
        return ResponseEntity.ok(eventoAtualizado);
    }
}
