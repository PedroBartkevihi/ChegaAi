package chegaai;

import chegaai.limite.RelogioLimitesTeste;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "server.tomcat.remoteip.internal-proxies=127\\.0\\.0\\.1|::1|0:0:0:0:0:0:0:1",
    "chegaai.limites.login-ip.capacidade=3"
})
@Import(RelogioLimitesTeste.Config.class)
class ProxyConfiavelLimiteIT extends IntegracaoBase {
    @Value("${local.server.port}") int porta;
    @Autowired RelogioLimitesTeste relogio;
    final HttpClient cliente = HttpClient.newHttpClient();
    @BeforeEach void avancar() { relogio.avancar(Duration.ofDays(2)); }
    private int chamar(String xff, String forwarded) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + porta + "/api/auth/login"))
            .header("Content-Type", "application/json").header("X-Forwarded-For", xff);
        if (forwarded != null) builder.header("Forwarded", forwarded);
        return cliente.send(builder.POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString()).statusCode();
    }
    @Test
    void tomcatRealIgnoraPrefixoForjadoEForwardedMasUsaUltimoClienteNaoConfiavel() throws Exception {
        for (int i = 0; i < 3; i++) assertThat(chamar("198.51.100." + i + ", 203.0.113.44", "for=192.0.2." + i)).isEqualTo(400);
        assertThat(chamar("198.51.100.99, 203.0.113.44", "for=192.0.2.99")).isEqualTo(429);
        assertThat(chamar("198.51.100.99, 203.0.113.45", "for=192.0.2.99")).isEqualTo(400);
    }
    @Test
    void servidorRealAgrupaIpv6Por64() throws Exception {
        for (int i = 0; i < 3; i++) assertThat(chamar("2001:db8:1:2::" + (i + 1), null)).isEqualTo(400);
        assertThat(chamar("2001:db8:1:2::abcd", null)).isEqualTo(429);
        assertThat(chamar("2001:db8:1:3::1", null)).isEqualTo(400);
    }
    @Test
    void requisicoesHttpConcorrentesRespeitamOMesmoBalde() throws Exception {
        var pool = Executors.newFixedThreadPool(12);
        try {
            var futures = new ArrayList<Future<Integer>>();
            for (int i = 0; i < 30; i++) futures.add(pool.submit(() -> chamar("203.0.113.66", null)));
            var statuses = new ArrayList<Integer>();
            for (var future : futures) statuses.add(future.get());
            assertThat(statuses.stream().filter(s -> s == 400).count()).isEqualTo(3);
            assertThat(statuses.stream().filter(s -> s == 429).count()).isEqualTo(27);
        } finally { pool.shutdownNow(); }
    }
}
