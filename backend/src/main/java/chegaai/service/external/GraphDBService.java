package chegaai.service.external;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import java.time.Duration;
@Service
@Slf4j
public class GraphDBService {
    private static final Duration TEMPO_CONEXAO = Duration.ofSeconds(2);
    private static final Duration TEMPO_RESPOSTA = Duration.ofSeconds(5);
    @Value("${chegaai.graphdb.url}")
    private String graphDbUrl;
    @Value("${chegaai.graphdb.repositorio}")
    private String repository;
    @Value("${chegaai.graphdb.ontologia-prefix}")
    private String ontologyPrefix;
    public String getRepository() {
        return repository;
    }
    public boolean isAvailable() {
        try {
            restClient()
                .get()
                .uri("/rest/repositories/{repository}", repository)
                .retrieve()
                .toBodilessEntity();
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
            // A consulta vai como variável da URI: assim ela é codificada inteira, e as chaves
            // do SPARQL não são confundidas com variáveis do modelo da URI.
            String result = restClient()
                .get()
                .uri("/repositories/{repository}?query={query}", repository, query)
                .accept(MediaType.valueOf("application/sparql-results+json"))
                .retrieve()
                .body(String.class);
            return result == null || result.isBlank() ? emptySparqlResult() : result;
        } catch (RestClientResponseException ex) {
            throw new IllegalStateException("GraphDB respondeu com erro " + ex.getStatusCode().value(), ex);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Não foi possível consultar o GraphDB.", ex);
        }
    }
    private RestClient restClient() {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(TEMPO_CONEXAO);
        fabrica.setReadTimeout(TEMPO_RESPOSTA);
        return RestClient.builder().baseUrl(graphDbUrl).requestFactory(fabrica).build();
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
