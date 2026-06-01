package chegae.service.external;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class GraphDBService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final WebClient.Builder webClientBuilder;

    @Value("${chegae.graphdb.url}")
    private String graphDbUrl;

    @Value("${chegae.graphdb.repositorio}")
    private String repository;

    @Value("${chegae.graphdb.ontologia-prefix}")
    private String ontologyPrefix;

    public String getRepository() {
        return repository;
    }

    public boolean isAvailable() {
        try {
            webClient()
                .get()
                .uri("/rest/repositories/{repository}", repository)
                .retrieve()
                .toBodilessEntity()
                .block(REQUEST_TIMEOUT);
            return true;
        } catch (RuntimeException ex) {
            log.debug("GraphDB indisponível em {} para o repositório '{}': {}",
                graphDbUrl, repository, ex.getMessage());
            return false;
        }
    }

    public String buscarEventosPorTexto(String termo) {
        String query = montarQueryEventos(termo);

        try {
            String result = webClient()
                .get()
                .uri(uriBuilder -> uriBuilder
                    .path("/repositories/{repository}")
                    .queryParam("query", query)
                    .build(repository))
                .accept(MediaType.valueOf("application/sparql-results+json"))
                .retrieve()
                .bodyToMono(String.class)
                .block(REQUEST_TIMEOUT);

            return result == null || result.isBlank() ? emptySparqlResult() : result;
        } catch (WebClientResponseException ex) {
            throw new IllegalStateException("GraphDB respondeu com erro " + ex.getStatusCode().value(), ex);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Não foi possível consultar o GraphDB.", ex);
        }
    }

    private WebClient webClient() {
        return webClientBuilder.baseUrl(graphDbUrl).build();
    }

    private String montarQueryEventos(String termo) {
        String filtro = termo == null ? "" : termo.trim().toLowerCase();
        String filtroSparql = filtro.isBlank()
            ? ""
            : "FILTER(CONTAINS(LCASE(STR(?label)), \"" + escaparLiteralSparql(filtro) + "\"))";

        return """
            PREFIX cg: <%s>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

            SELECT ?evento ?label
            WHERE {
              ?evento a cg:Evento .
              OPTIONAL { ?evento rdfs:label ?label . }
              %s
            }
            LIMIT 20
            """.formatted(ontologyPrefix, filtroSparql);
    }

    private String escaparLiteralSparql(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"");
    }

    private String emptySparqlResult() {
        return "{\"head\":{\"vars\":[\"evento\",\"label\"]},\"results\":{\"bindings\":[]}}";
    }
}
