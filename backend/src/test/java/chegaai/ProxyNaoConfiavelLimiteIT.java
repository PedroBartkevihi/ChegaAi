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
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "server.tomcat.remoteip.internal-proxies=(?!)", "chegaai.limites.login-ip.capacidade=3"
})
@Import(RelogioLimitesTeste.Config.class)
class ProxyNaoConfiavelLimiteIT extends IntegracaoBase {
    @Value("${local.server.port}") int porta;
    @Autowired RelogioLimitesTeste relogio;
    @BeforeEach void avancar() { relogio.avancar(Duration.ofDays(2)); }
    @Test
    void tomcatRealNaoConfiaEmHeadersDePeerForaDaLista() throws Exception {
        var client = HttpClient.newHttpClient();
        for (int i = 0; i < 4; i++) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + porta + "/api/auth/login"))
                .header("Content-Type", "application/json").header("X-Forwarded-For", "203.0.113." + i)
                .header("Forwarded", "for=192.0.2." + i).POST(HttpRequest.BodyPublishers.ofString("{}")).build();
            assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(i < 3 ? 400 : 429);
        }
    }
}
