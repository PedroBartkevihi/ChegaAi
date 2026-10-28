package chegaai.service.external;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
class ViaCEPServiceTest {
    private static final String ENDERECO = """
        {"cep": "22250-040", "logradouro": "Praia do Flamengo", "complemento": "", "unidade": "",
         "bairro": "Flamengo", "localidade": "Rio de Janeiro", "uf": "RJ", "estado": "Rio de Janeiro",
         "regiao": "Sudeste", "ibge": "3304557", "gia": "", "ddd": "21", "siafi": "6001"}
        """;
    private HttpServer servidor;
    private ViaCEPService service;
    @BeforeEach
    void subirViaCepFalso() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/22250040/json/", troca -> responder(troca, ENDERECO));
        servidor.createContext("/99999999/json/", troca -> responder(troca, "{\"erro\": \"true\"}"));
        servidor.createContext("/50000000/json/", troca -> {
            troca.sendResponseHeaders(500, -1);
            troca.close();
        });
        servidor.createContext("/11111111/json/", troca -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            responder(troca, ENDERECO);
        });
        servidor.start();
        service = new ViaCEPService("http://127.0.0.1:" + servidor.getAddress().getPort() + "/",
            Duration.ofSeconds(1), Duration.ofMillis(300));
    }
    @AfterEach
    void derrubarViaCepFalso() {
        servidor.stop(0);
    }
    @Test
    @DisplayName("lê o endereço mesmo com campos que o serviço não usa")
    void encontraEndereco() {
        Optional<ViaCEPService.ViaCEPResponse> resposta = service.buscarEnderecoPorCep("22250-040");
        assertThat(resposta).isPresent();
        assertThat(resposta.get().localidade).isEqualTo("Rio de Janeiro");
        assertThat(resposta.get().bairro).isEqualTo("Flamengo");
    }
    @Test
    @DisplayName("CEP inexistente ({\"erro\": \"true\"}) não devolve endereço")
    void cepInexistente() {
        assertThat(service.buscarEnderecoPorCep("99999-999")).isEmpty();
    }
    @Test
    @DisplayName("erro HTTP do ViaCEP cai no preenchimento manual")
    void erroHttp() {
        assertThat(service.buscarEnderecoPorCep("50000-000")).isEmpty();
    }
    @Test
    @DisplayName("ViaCEP lento cai no preenchimento manual dentro do tempo limite")
    void viaCepLentoRespeitaTempoLimite() {
        long inicio = System.nanoTime();
        Optional<ViaCEPService.ViaCEPResponse> resposta = service.buscarEnderecoPorCep("11111-111");
        long milissegundos = Duration.ofNanos(System.nanoTime() - inicio).toMillis();
        assertThat(resposta).isEmpty();
        assertThat(milissegundos).isLessThan(1500);
    }
    private static void responder(com.sun.net.httpserver.HttpExchange troca, String json) throws IOException {
        byte[] corpo = json.getBytes(StandardCharsets.UTF_8);
        troca.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        troca.sendResponseHeaders(200, corpo.length);
        troca.getResponseBody().write(corpo);
        troca.close();
    }
}
