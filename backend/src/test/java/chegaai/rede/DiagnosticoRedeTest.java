package chegaai.rede;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.ForwardedHeaderFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class DiagnosticoRedeTest {
    private MockHttpServletRequest request() {
        var r = new MockHttpServletRequest("GET", "/api/actuator/health");
        r.setContextPath("/api"); r.setRemoteAddr("10.23.0.2");
        r.addHeader("X-Chegaai-Diagnostico", "proxy-v1");
        return r;
    }
    @Test void desligadoPorPadraoESomenteTrueRegistraFiltro() {
        var runner = new ApplicationContextRunner().withUserConfiguration(ConfiguracaoDiagnosticoRede.class);
        runner.run(c -> assertThat(c).doesNotHaveBean("diagnosticoRede"));
        runner.withPropertyValues("chegaai.rede.diagnostico=false").run(c -> assertThat(c).doesNotHaveBean("diagnosticoRede"));
        runner.withPropertyValues("chegaai.rede.diagnostico=true").run(c -> {
            assertThat(c).hasSingleBean(org.springframework.boot.web.servlet.FilterRegistrationBean.class);
            var registro = c.getBean(org.springframework.boot.web.servlet.FilterRegistrationBean.class);
            assertThat(registro.getOrder()).isEqualTo(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 20);
            assertThat(registro.getUrlPatterns()).containsExactly("/actuator/health");
        });
    }
    @Test void somenteGetExatoComMarcadorGastaCotaESegueResposta() throws Exception {
        var registros = new ArrayList<ResumoDiagnosticoRede>();
        var filtro = new FiltroDiagnosticoRede(3, "framework", new AtomicInteger(), registros::add);
        for (int i = 0; i < 5; i++) {
            var r = request();
            switch(i) {
                case 0 -> r.setMethod("POST");
                case 1 -> r.setRequestURI("/api/actuator/health/liveness");
                case 2 -> r.setRequestURI("/api/fora");
                case 3 -> r.removeHeader("X-Chegaai-Diagnostico");
                case 4 -> { r.removeHeader("X-Chegaai-Diagnostico"); r.addHeader("X-Chegaai-Diagnostico", "proxy-v2"); }
            }
            filtro.doFilter(r, new MockHttpServletResponse(), (a,b) -> {});
        }
        assertThat(registros).isEmpty();
        for (int i = 0; i < 8; i++) {
            var response = new MockHttpServletResponse();
            filtro.doFilter(request(), response, (a,b) -> { b.setContentType("application/json"); b.getWriter().write("UP"); });
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentAsString()).isEqualTo("UP");
        }
        assertThat(registros).hasSize(3);
    }
    @Test void quotaAtomicaPorContadorCompartilhadoNaoMudaRespostas() throws Exception {
        var registros = Collections.synchronizedList(new ArrayList<ResumoDiagnosticoRede>());
        var contador = new AtomicInteger();
        var a = new FiltroDiagnosticoRede(3, "framework", contador, registros::add);
        var b = new FiltroDiagnosticoRede(3, "framework", contador, registros::add);
        var pool = Executors.newFixedThreadPool(10);
        try {
            var futures = new ArrayList<java.util.concurrent.Future<Integer>>();
            for (int i = 0; i < 60; i++) {
                var filtro = i % 2 == 0 ? a : b;
                futures.add(pool.submit(() -> {
                    var resposta = new MockHttpServletResponse();
                    filtro.doFilter(request(), resposta, (r,s) -> s.getWriter().write("UP"));
                    return resposta.getStatus();
                }));
            }
            for (var future : futures) assertThat(future.get()).isEqualTo(200);
        } finally { pool.shutdownNow(); }
        assertThat(registros).hasSize(3);
        assertThat(contador.get()).isEqualTo(3);
    }
    @Test void unwrapRecuperaBaseDoFrameworkSemExporVisitanteOuCabecalho() throws Exception {
        var registros = new ArrayList<ResumoDiagnosticoRede>();
        var filtro = new FiltroDiagnosticoRede(3, "framework", new AtomicInteger(), registros::add);
        var r = request();
        r.addHeader("X-Forwarded-For", "198.51.100.44, 8.8.4.4, 10.44.0.2");
        r.addHeader("Forwarded", "for=192.0.2.42;proto=https;host=host-privado.example");
        r.addHeader("Authorization", "Bearer segredo-do-teste");
        r.addHeader("X-Chegaai-Cliente-Esperado", "8.8.4.4");
        new ForwardedHeaderFilter().doFilter(r, new MockHttpServletResponse(),
            (a,b) -> filtro.doFilter(a,b,(c,d) -> {}));
        var resumo = registros.get(0);
        assertThat(resumo.baseClasse()).isEqualTo(ResumoDiagnosticoRede.Classe.PRIVADO);
        assertThat(resumo.basePeerInterno()).isEqualTo("10.23.0.2");
        assertThat(resumo.esquema()).isEqualTo("https");
        assertThat(resumo.resolvidoMarcador()).isTrue();
        assertThat(resumo.esperadoValido()).isTrue();
        assertThat(resumo.esperadoResolvido()).isFalse();
        assertThat(resumo.xff().marcadores()).containsExactly(1);
        assertThat(resumo.xff().esperadoPosicoes()).containsExactly(2);
        assertThat(resumo.forwarded().marcadores()).containsExactly(1);
        assertThat(resumo.toString()).doesNotContain("8.8.4.4", "198.51.100.44", "192.0.2.42", "10.44.0.2",
            "host-privado.example", "segredo-do-teste", "Bearer", "for=");
    }
    @Test void publicoEsperadoSoGeraIgualdadeEPosicoes() {
        var r = request(); r.setRemoteAddr("8.8.4.4");
        r.addHeader("X-Chegaai-Cliente-Esperado", "8.8.4.4");
        r.addHeader("X-Forwarded-For", "8.8.4.4, 8.8.4.4");
        var resumo = ResumoDiagnosticoRede.observar(r,r,"native");
        assertThat(resumo.esperadoResolvido()).isTrue();
        assertThat(resumo.basePeerInterno()).isEqualTo("omitido");
        assertThat(resumo.xff().esperadoPosicoes()).containsExactly(1,2);
        assertThat(resumo.toString()).doesNotContain("8.8.4.4");
    }
    @Test void literaisIpv6EClassesPrivadasNaoResolvemHostnames() {
        var r = request(); r.setRemoteAddr("fd00:1::2");
        r.addHeader("X-Forwarded-For", "127.0.0.1, 169.254.1.2, 172.16.1.2, 192.168.1.2, 8.8.4.4, visitante@example.test, host.example, 2001:db8::1");
        r.addHeader("Forwarded", "for=\"[2001:db8::4]:443\";proto=https, for=unknown");
        r.addHeader("X-Chegaai-Cliente-Esperado", "host.example");
        var resumo = ResumoDiagnosticoRede.observar(r,r,"framework");
        assertThat(resumo.baseClasse()).isEqualTo(ResumoDiagnosticoRede.Classe.PRIVADO);
        assertThat(resumo.xff().classes()).containsExactly(ResumoDiagnosticoRede.Classe.LOOPBACK,
            ResumoDiagnosticoRede.Classe.LINKLOCAL, ResumoDiagnosticoRede.Classe.PRIVADO,
            ResumoDiagnosticoRede.Classe.PRIVADO, ResumoDiagnosticoRede.Classe.PUBLICO,
            ResumoDiagnosticoRede.Classe.INVALIDO, ResumoDiagnosticoRede.Classe.INVALIDO, ResumoDiagnosticoRede.Classe.PUBLICO);
        assertThat(resumo.forwarded().marcadores()).containsExactly(1);
        assertThat(resumo.esperadoValido()).isFalse();
        assertThat(resumo.toString()).doesNotContain("example", "2001:db8", "8.8.4.4");
    }
    @Test void parsingTemLimitesDeComponentesECaracteresSemFragmentosFalsos() {
        var r = request();
        r.addHeader("X-Forwarded-For", String.join(",", Collections.nCopies(1000,"192.0.2.9")));
        r.addHeader("Forwarded", "x".repeat(100_000) + ",for=192.0.2.42");
        var resumo = ResumoDiagnosticoRede.observar(r,r,"framework");
        assertThat(resumo.xff().componentes()).isEqualTo(16);
        assertThat(resumo.xff().truncado()).isTrue();
        assertThat(resumo.forwarded().componentes()).isZero();
        assertThat(resumo.forwarded().truncado()).isTrue();
        assertThat(resumo.toString()).hasSizeLessThan(1500);
    }
    @Test void configuracaoRejeitaLimitesForaDeUmATresEControlaEstrategia() {
        assertThatThrownBy(() -> new FiltroDiagnosticoRede(0,"framework",new AtomicInteger(), r -> {}))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FiltroDiagnosticoRede(4,"framework",new AtomicInteger(), r -> {}))
            .isInstanceOf(IllegalArgumentException.class);
        var registros = new ArrayList<ResumoDiagnosticoRede>();
        var filtro = new FiltroDiagnosticoRede(1,"segredo\nnao-controlado",new AtomicInteger(),registros::add);
        assertThatCode(() -> filtro.doFilter(request(),new MockHttpServletResponse(),(a,b) -> {})).doesNotThrowAnyException();
        assertThat(registros.get(0).estrategia()).isEqualTo("outra");
    }

    @Test void falhaDoRegistroNaoMudaStatusCorpoOuHeadersDoHealth() throws Exception {
        var tentativas = new AtomicInteger();
        var filtro = new FiltroDiagnosticoRede(3, "framework", new AtomicInteger(), resumo -> {
            tentativas.incrementAndGet();
            throw new IllegalStateException("8.8.4.4 visitante@example.test Bearer segredo-na-excecao");
        });
        for (int i = 0; i < 8; i++) {
            var resposta = new MockHttpServletResponse();
            filtro.doFilter(request(), resposta, (a,b) -> {
                var http = (jakarta.servlet.http.HttpServletResponse) b;
                http.setStatus(503);
                http.setHeader("X-Resposta-Original", "preservada");
                http.getWriter().write("{\"status\":\"OUT_OF_SERVICE\"}");
            });
            assertThat(resposta.getStatus()).isEqualTo(503);
            assertThat(resposta.getHeader("X-Resposta-Original")).isEqualTo("preservada");
            assertThat(resposta.getContentAsString()).isEqualTo("{\"status\":\"OUT_OF_SERVICE\"}");
            assertThat(resposta.getContentAsString()).doesNotContain("segredo", "example", "8.8.4.4");
        }
        assertThat(tentativas.get()).isEqualTo(3);
    }
    @Test void orcamentoDeCaracteresSomaHeadersERecusaIpCortado() {
        var r = request();
        r.addHeader("X-Forwarded-For", "x".repeat(2040));
        r.addHeader("X-Forwarded-For", "192.0.2.42");
        r.addHeader("X-Forwarded-For", "8.8.4.4");
        var resumo = ResumoDiagnosticoRede.observar(r,r,"framework");
        assertThat(resumo.xff().componentes()).isEqualTo(1);
        assertThat(resumo.xff().classes()).containsExactly(ResumoDiagnosticoRede.Classe.INVALIDO);
        assertThat(resumo.xff().marcadores()).isEmpty();
        assertThat(resumo.xff().truncado()).isTrue();
        assertThat(resumo.toString()).doesNotContain("192.0.2", "8.8.4.4", "xxxx");
    }
}
