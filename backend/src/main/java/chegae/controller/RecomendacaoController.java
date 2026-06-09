package chegae.controller;
import chegae.service.external.GraphDBService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
@RestController
@RequestMapping("/recomendacoes")
@RequiredArgsConstructor
public class RecomendacaoController {
    private final GraphDBService graphDBService;
    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
            "repository", graphDBService.getRepository(),
            "available", graphDBService.isAvailable()
        );
    }
    @GetMapping(produces = "application/sparql-results+json")
    public ResponseEntity<String> listar(@RequestParam(name = "termo", required = false) String termo) {
        return ResponseEntity.ok()
            .contentType(MediaType.valueOf("application/sparql-results+json"))
            .body(graphDBService.buscarEventosPorTexto(termo));
    }
}
