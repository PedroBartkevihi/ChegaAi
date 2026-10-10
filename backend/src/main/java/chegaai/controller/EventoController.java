package chegaai.controller;
import chegaai.config.FusoHorario;
import chegaai.config.UsuarioAutenticado;
import chegaai.controller.dto.*;
import chegaai.controller.dto.AvaliacaoRequestDTO;
import chegaai.controller.dto.AvaliacaoResponseDTO;
import chegaai.domain.model.Comentario;
import chegaai.service.ComentarioService;
import chegaai.service.EventoService;
import chegaai.service.InscricaoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;
@RestController
@RequestMapping("/eventos")
@RequiredArgsConstructor
public class EventoController {
    private final EventoService eventoService;
    private final InscricaoService inscricaoService;
    private final ComentarioService comentarioService;
    private final FusoHorario fusoHorario;
    @GetMapping
    public ResponseEntity<List<EventoResponseDTO>> listarTodos() {
        return ResponseEntity.ok(eventoService.listarTodos());
    }
    @GetMapping("/paginado")
    public ResponseEntity<Page<EventoResponseDTO>> listarPaginado(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(eventoService.listarTodosPaginado(pageable));
    }
    @GetMapping("/{id}")
    public ResponseEntity<EventoResponseDTO> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(eventoService.buscarPorId(id));
    }
    @PostMapping
    public ResponseEntity<EventoResponseDTO> criarEvento(
            @Valid @RequestBody EventoRequestDTO dto,
            @UsuarioAutenticado UUID usuarioId) {
        EventoResponseDTO criado = eventoService.criarEvento(dto, usuarioId);
        return ResponseEntity.status(HttpStatus.CREATED).body(criado);
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluirEvento(
            @PathVariable UUID id,
            @UsuarioAutenticado UUID usuarioId) {
        eventoService.excluirEvento(id, usuarioId);
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/{id}/inscricao")
    public ResponseEntity<InscricaoResponseDTO> toggleInscricao(
            @PathVariable UUID id,
            @UsuarioAutenticado UUID usuarioId) {
        InscricaoResponseDTO resultado = inscricaoService.toggleInscricao(id, usuarioId);
        return ResponseEntity.ok(resultado);
    }
    @PostMapping("/{id}/concluir")
    public ResponseEntity<EventoResponseDTO> concluirEvento(
            @PathVariable UUID id,
            @UsuarioAutenticado UUID usuarioId) {
        EventoResponseDTO concluido = eventoService.concluirEvento(id, usuarioId);
        return ResponseEntity.ok(concluido);
    }
    @PostMapping("/{id}/avaliacoes")
    public ResponseEntity<AvaliacaoResponseDTO> avaliarParticipante(
            @PathVariable UUID id,
            @Valid @RequestBody AvaliacaoRequestDTO dto,
            @UsuarioAutenticado UUID usuarioId) {
        AvaliacaoResponseDTO resultado = eventoService.avaliarParticipante(id, usuarioId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(resultado);
    }

    @PostMapping("/{id}/comentarios")
    public ResponseEntity<ComentarioResponseDTO> adicionarComentario(
            @PathVariable UUID id,
            @Valid @RequestBody ComentarioRequestDTO dto,
            @UsuarioAutenticado UUID usuarioId) {
        Comentario comentario = comentarioService.adicionarComentario(id, usuarioId, dto.text());
        ComentarioResponseDTO response = new ComentarioResponseDTO(
            comentario.getId(),
            comentario.getAutor().getNome().split(" ")[0],
            comentario.getTexto(),
            comentario.getDataHoraComentario() != null
                ? fusoHorario.hora(comentario.getDataHoraComentario())
                : "00:00"
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
