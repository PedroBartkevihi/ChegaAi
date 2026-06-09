package chegae.controller;

import chegae.controller.dto.*;
import chegae.domain.model.Comentario;
import chegae.service.ComentarioService;
import chegae.service.EventoService;
import chegae.service.InscricaoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Controller REST para gerenciamento de eventos.
 *
 * <p>Expõe os endpoints consumidos pelo frontend React:</p>
 * <ul>
 *   <li>{@code GET  /api/eventos}               — listar todos os eventos</li>
 *   <li>{@code GET  /api/eventos/paginado}       — listar eventos com paginação</li>
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
     * Mantido para compatibilidade com o frontend React que espera um array.
     */
    @GetMapping
    public ResponseEntity<List<EventoResponseDTO>> listarTodos() {
        return ResponseEntity.ok(eventoService.listarTodos());
    }

    /**
     * GET /api/eventos/paginado?page=0&size=20
     * Listagem paginada de eventos — otimização para reduzir carga no banco
     * e na serialização JSON sob alta concorrência.
     *
     * <p>Parâmetros de query:</p>
     * <ul>
     *   <li>{@code page} — número da página (0-indexed, default: 0)</li>
     *   <li>{@code size} — tamanho da página (default: 20)</li>
     * </ul>
     */
    @GetMapping("/paginado")
    public ResponseEntity<Page<EventoResponseDTO>> listarPaginado(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(eventoService.listarTodosPaginado(pageable));
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
            @Valid @RequestBody EventoRequestDTO dto,
            @RequestHeader("X-User-Id") UUID usuarioId) {
        EventoResponseDTO criado = eventoService.criarEvento(dto, usuarioId);
        return ResponseEntity.status(HttpStatus.CREATED).body(criado);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluirEvento(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID usuarioId) {
        eventoService.excluirEvento(id, usuarioId);
        return ResponseEntity.noContent().build();
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
     *
     * <p><b>Otimização (Medição 2):</b> Retorna apenas o DTO do comentário
     * recém-criado (HTTP 201) em vez de reconstruir o Evento completo.
     * Isso elimina a query extra de buscarPorId() que gerava overhead
     * de serialização e retenção de conexão HikariCP sob concorrência.</p>
     */
    @PostMapping("/{id}/comentarios")
    public ResponseEntity<ComentarioResponseDTO> adicionarComentario(
            @PathVariable UUID id,
            @Valid @RequestBody ComentarioRequestDTO dto,
            @RequestHeader("X-User-Id") UUID usuarioId) {
        Comentario comentario = comentarioService.adicionarComentario(id, usuarioId, dto.text());

        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");
        ComentarioResponseDTO response = new ComentarioResponseDTO(
            comentario.getId(),
            comentario.getAutor().getNome().split(" ")[0],
            comentario.getTexto(),
            comentario.getDataHoraComentario() != null
                ? comentario.getDataHoraComentario().format(timeFmt)
                : "00:00"
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

