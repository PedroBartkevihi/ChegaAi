package chegaai.controller;
import chegaai.config.FusoHorario;
import chegaai.config.UsuarioAutenticado;
import chegaai.controller.dto.*;
import chegaai.controller.dto.AvaliacaoRequestDTO;
import chegaai.controller.dto.AvaliacaoResponseDTO;
import chegaai.domain.model.Comentario;
import chegaai.limite.LimitadorRequisicoes;
import chegaai.limite.OperacaoLimite;
import chegaai.limite.ReservaLimite;
import chegaai.repository.FiltroFeed;
import chegaai.service.ComentarioService;
import chegaai.service.EventoService;
import chegaai.service.FeedService;
import chegaai.service.InscricaoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
@RestController
@RequestMapping("/eventos")
@RequiredArgsConstructor
public class EventoController {
    private final EventoService eventoService;
    private final FeedService feedService;
    private final InscricaoService inscricaoService;
    private final ComentarioService comentarioService;
    private final FusoHorario fusoHorario;
    private final LimitadorRequisicoes limitadorRequisicoes;
    @GetMapping
    public ResponseEntity<List<EventoResponseDTO>> listarTodos() {
        return ResponseEntity.ok(eventoService.listarTodos());
    }
    /**
     * Feed paginado: resumos dos rolés, com filtros e ordem no servidor. {@code secao} separa os
     * próximos (abertos, com início por vir) dos anteriores e indisponíveis.
     */
    @GetMapping("/feed")
    public ResponseEntity<FeedPaginaDTO> feed(
            @RequestParam(defaultValue = "proximos") String secao,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) BigDecimal honraMinima,
            @RequestParam(defaultValue = "false") boolean embaixador,
            @RequestParam(required = false) String busca,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "12") int tamanho) {
        return ResponseEntity.ok(feedService.pagina(
            FiltroFeed.de(secao, categoria, honraMinima, embaixador, busca, pagina, tamanho)));
    }
    @GetMapping("/resumo")
    public ResponseEntity<FeedResumoDTO> resumo() {
        return ResponseEntity.ok(feedService.resumo());
    }
    @GetMapping("/{id}")
    public ResponseEntity<EventoResponseDTO> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(eventoService.buscarPorId(id));
    }
    @PostMapping
    public ResponseEntity<EventoResponseDTO> criarEvento(
            @Valid @RequestBody EventoRequestDTO dto,
            @UsuarioAutenticado UUID usuarioId) {
        try (ReservaLimite reserva = limitadorRequisicoes.reservar(OperacaoLimite.CRIAR_EVENTO, usuarioId.toString())) {
            EventoResponseDTO criado = eventoService.criarEvento(dto, usuarioId);
            reserva.confirmar();
            return ResponseEntity.status(HttpStatus.CREATED).body(criado);
        }
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
    @GetMapping("/{id}/participantes")
    public ResponseEntity<ParticipantesResponseDTO> participantes(@PathVariable UUID id,
            @UsuarioAutenticado UUID usuarioId) {
        return ResponseEntity.ok(inscricaoService.participantes(id, usuarioId));
    }
    @PutMapping("/{id}/presencas/{participanteId}")
    public ResponseEntity<Void> atualizarPresenca(@PathVariable UUID id, @PathVariable UUID participanteId,
            @Valid @RequestBody AtualizarPresencaRequestDTO dto, @UsuarioAutenticado UUID usuarioId) {
        inscricaoService.registrarPresenca(id, usuarioId, participanteId, dto.presente());
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/{id}/presencas")
    public ResponseEntity<Void> registrarPresenca(
            @PathVariable UUID id,
            @Valid @RequestBody PresencaRequestDTO dto,
            @UsuarioAutenticado UUID usuarioId) {
        inscricaoService.registrarPresenca(id, usuarioId, dto.participanteId());
        return ResponseEntity.noContent().build();
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
        try (ReservaLimite reserva = limitadorRequisicoes.reservar(OperacaoLimite.COMENTAR, usuarioId.toString())) {
            Comentario comentario = comentarioService.adicionarComentario(id, usuarioId, dto.text());
            ComentarioResponseDTO response = new ComentarioResponseDTO(
                comentario.getId(),
                comentario.getAutor().getNome().split(" ")[0],
                comentario.getTexto(),
                comentario.getDataHoraComentario() != null
                    ? fusoHorario.hora(comentario.getDataHoraComentario())
                    : "00:00"
            );
            reserva.confirmar();
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }
    }
}
