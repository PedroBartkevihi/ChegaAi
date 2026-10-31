package chegaai.service.external;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
class GraphDBServiceTest {
    private static final String RESPOSTA = "{\"head\":{\"vars\":[\"evento\",\"label\"]},\"results\":{\"bindings\":[]}}";
    private HttpServer servidor;
    private final AtomicReference<String> caminhoRecebido = new AtomicReference<>();
    private final AtomicReference<String> consultaRecebida = new AtomicReference<>();
    private final AtomicInteger statusResposta = new AtomicInteger(200);
    private GraphDBService service;
    @BeforeEach
    void subirGraphDbFalso() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", troca -> {
            caminhoRecebido.set(troca.getRequestURI().getPath());
            String query = troca.getRequestURI().getRawQuery();
            consultaRecebida.set(URLDecoder.decode(query.substring("query=".length()), StandardCharsets.UTF_8));
            byte[] corpo = RESPOSTA.getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/sparql-results+json");
            troca.sendResponseHeaders(statusResposta.get(), corpo.length);
            troca.getResponseBody().write(corpo);
            troca.close();
        });
        servidor.start();
        service = new GraphDBService();
        ReflectionTestUtils.setField(service, "graphDbUrl", "http://127.0.0.1:" + servidor.getAddress().getPort());
        ReflectionTestUtils.setField(service, "repository", "chegaai");
        ReflectionTestUtils.setField(service, "ontologyPrefix", "http://chegaai.dev/ontology#");
    }
    @AfterEach
    void derrubarGraphDbFalso() {
        servidor.stop(0);
    }
    @Test
    @DisplayName("a consulta SPARQL chega inteira ao GraphDB, com chaves e caracteres reservados")
    void consultaChegaInteira() {
        String resultado = service.buscarEventosPorTexto("Futebol & Praia");
        assertThat(resultado).isEqualTo(RESPOSTA);
        assertThat(caminhoRecebido.get()).isEqualTo("/repositories/chegaai");
        assertThat(consultaRecebida.get())
            .contains("WHERE {")
            .contains("OPTIONAL { ?evento rdfs:label ?label . }")
            .contains("FILTER(CONTAINS(LCASE(STR(?label)), \"futebol & praia\"))");
    }
    @Test
    @DisplayName("GraphDB com erro ou fora do ar vira GraphDBIndisponivelException, que a API responde com 503")
    void falhaDoGraphDbViraIndisponivel() {
        statusResposta.set(500);
        assertThatThrownBy(() -> service.buscarEventosPorTexto("praia"))
            .isInstanceOf(GraphDBIndisponivelException.class)
            .hasMessageContaining("500");
        // Porta 1: nada escuta nela, como na versão online, onde o GraphDB não existe.
        ReflectionTestUtils.setField(service, "graphDbUrl", "http://127.0.0.1:1");
        assertThatThrownBy(() -> service.buscarEventosPorTexto("praia"))
            .isInstanceOf(GraphDBIndisponivelException.class)
            .hasMessage("Não foi possível consultar o GraphDB.");
    }
}
