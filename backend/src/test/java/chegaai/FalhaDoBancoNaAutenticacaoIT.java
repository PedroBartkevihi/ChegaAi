package chegaai;
import chegaai.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessResourceFailureException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Com servidor HTTP de verdade: o MockMvc não faz o despacho de erro (/error) do contêiner, que é
 * justamente o caminho de uma exceção lançada durante a autenticação.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FalhaDoBancoNaAutenticacaoIT.RepositorioQueFalha.class)
class FalhaDoBancoNaAutenticacaoIT extends IntegracaoBase {
    static final AtomicBoolean BANCO_FORA = new AtomicBoolean();
    @Value("${local.server.port}")
    private int porta;
    @Test
    @DisplayName("se o banco falha ao conferir o token, a API responde 500, e não 401 (que deslogaria o site)")
    void falhaDoBancoNaoViraSessaoExpirada() throws Exception {
        String token = login("pedro@chegaai.dev", "admin123");
        HttpClient cliente = HttpClient.newHttpClient();
        HttpRequest pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + "/api/auth/me"))
            .header("Authorization", bearer(token))
            .build();
        assertThat(cliente.send(pedido, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
        BANCO_FORA.set(true);
        try {
            HttpResponse<String> resposta = cliente.send(pedido, HttpResponse.BodyHandlers.ofString());
            assertThat(resposta.statusCode()).isEqualTo(500);
            assertThat(resposta.body()).doesNotContain("banco fora do ar");
        } finally {
            BANCO_FORA.set(false);
        }
    }
    /** O repositório de verdade, só que a checagem de existência falha enquanto BANCO_FORA estiver ligado. */
    @TestConfiguration
    static class RepositorioQueFalha {
        @Bean
        @Primary
        UsuarioRepository usuarioRepositoryQueFalha(@Qualifier("usuarioRepository") UsuarioRepository real) {
            return (UsuarioRepository) Proxy.newProxyInstance(UsuarioRepository.class.getClassLoader(),
                new Class<?>[] {UsuarioRepository.class}, (proxy, metodo, argumentos) -> {
                    if (BANCO_FORA.get() && metodo.getName().equals("existsById")) {
                        throw new DataAccessResourceFailureException("banco fora do ar");
                    }
                    try {
                        return metodo.invoke(real, argumentos);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
        }
    }
}
