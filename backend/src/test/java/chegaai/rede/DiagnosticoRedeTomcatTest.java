package chegaai.rede;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.valves.RemoteIpValve;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.filter.ForwardedHeaderFilter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;

/** Servidor real sem aplicação/DB; servlet auxiliar existe somente em src/test. */
class DiagnosticoRedeTomcatTest {
    @TempDir Path temporario;

    @Test void frameworkMantemPeerNaBaseMasResolveMarcadorForjado() throws Exception {
        var resumo = observar("framework",false);
        assertThat(resumo.baseClasse()).isEqualTo(ResumoDiagnosticoRede.Classe.LOOPBACK);
        assertThat(resumo.basePeerInterno()).isEqualTo("127.0.0.1");
        assertThat(resumo.resolvidoMarcador()).isTrue();
        assertThat(resumo.esperadoResolvido()).isFalse();
        assertThat(resumo.xff().esperadoPosicoes()).containsExactly(2);
        assertThat(resumo.esquema()).isEqualTo("https");
    }
    @Test void nativeJaAlteraBaseEHeadersAntesDoFiltro() throws Exception {
        var resumo = observar("native",true);
        assertThat(resumo.baseClasse()).isEqualTo(ResumoDiagnosticoRede.Classe.PUBLICO);
        assertThat(resumo.basePeerInterno()).isEqualTo("omitido");
        assertThat(resumo.resolvidoMarcador()).isFalse();
        assertThat(resumo.esperadoResolvido()).isTrue();
        assertThat(resumo.xff().componentes()).isEqualTo(1);
        assertThat(resumo.xff().marcadores()).containsExactly(1);
        assertThat(resumo.xff().esperadoPosicoes()).isEmpty();
        assertThat(resumo.esquema()).isEqualTo("https");
    }
    @Test void nativeSemProxyConfiavelIgnoraTentativaDeForjarResolucao() throws Exception {
        var resumo = observar("native",false);
        assertThat(resumo.baseClasse()).isEqualTo(ResumoDiagnosticoRede.Classe.LOOPBACK);
        assertThat(resumo.esperadoResolvido()).isFalse();
        assertThat(resumo.xff().esperadoPosicoes()).containsExactly(2);
        assertThat(resumo.esquema()).isEqualTo("http");
    }

    @Test void quarentaGetsConcorrentesPreservamHealthERegistramSoTres() throws Exception {
        assertThat(observar("framework",false,40)).hasSize(3);
    }

    private ResumoDiagnosticoRede observar(String estrategia, boolean confiar) throws Exception {
        return observar(estrategia,confiar,1).get(0);
    }
    private java.util.List<ResumoDiagnosticoRede> observar(String estrategia, boolean confiar, int quantidade) throws Exception {
        var tomcat = new Tomcat();
        tomcat.setBaseDir(Files.createDirectory(temporario.resolve("tomcat")).toString());
        tomcat.setPort(0); tomcat.getConnector().setProperty("address", "127.0.0.1");
        var context = tomcat.addContext("/api", temporario.toString());
        context.setParentClassLoader(getClass().getClassLoader());
        Tomcat.addServlet(context,"healthTeste",new HttpServlet() {
            @Override protected void doGet(HttpServletRequest r, HttpServletResponse s) throws IOException {
                s.setContentType("application/json"); s.getWriter().write("{\"status\":\"UP\"}");
            }
        });
        context.addServletMappingDecoded("/actuator/health","healthTeste");
        if (estrategia.equals("framework")) adicionar(context,"forwarded",new ForwardedHeaderFilter());
        else {
            var valve = new RemoteIpValve();
            valve.setInternalProxies(confiar ? "127\\.0\\.0\\.1|10\\.23\\.0\\.2" : "(?!)");
            valve.setTrustedProxies("(?!)");
            valve.setRemoteIpHeader("X-Forwarded-For");
            valve.setProtocolHeader("X-Forwarded-Proto");
            context.getPipeline().addValve(valve);
        }
        var registros = java.util.Collections.synchronizedList(new ArrayList<ResumoDiagnosticoRede>());
        adicionar(context,"diagnostico",new FiltroDiagnosticoRede(3,estrategia,new AtomicInteger(),registros::add));
        try {
            tomcat.start();
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+tomcat.getConnector().getLocalPort()+"/api/actuator/health"))
                .header("X-Chegaai-Diagnostico","proxy-v1").header("X-Chegaai-Cliente-Esperado","8.8.4.4")
                .header("X-Forwarded-For","198.51.100.7, 8.8.4.4, 10.23.0.2")
                .header("Forwarded","for=192.0.2.61;proto=https")
                .header("X-Forwarded-Proto","https").GET().build();
            var client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build();
            var pool = java.util.concurrent.Executors.newFixedThreadPool(Math.min(10,quantidade));
            try {
                var respostas = new ArrayList<java.util.concurrent.Future<HttpResponse<String>>>();
                for (int i = 0; i < quantidade; i++)
                    respostas.add(pool.submit(() -> client.send(request,HttpResponse.BodyHandlers.ofString())));
                for (var resposta : respostas) {
                    var response = resposta.get(10,java.util.concurrent.TimeUnit.SECONDS);
                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(response.body()).isEqualTo("{\"status\":\"UP\"}");
                }
            } finally { pool.shutdownNow(); }
            assertThat(registros).hasSize(Math.min(quantidade,3));
            for (var resumo : registros)
                assertThat(resumo.toString()).doesNotContain("8.8.4.4","198.51.100.7","192.0.2.61","10.23.0.2");
            return java.util.List.copyOf(registros);
        } finally { tomcat.stop(); tomcat.destroy(); }
    }
    private void adicionar(org.apache.catalina.Context context, String nome, jakarta.servlet.Filter filtro) {
        var def = new FilterDef(); def.setFilterName(nome); def.setFilter(filtro); context.addFilterDef(def);
        var map = new FilterMap(); map.setFilterName(nome); map.addURLPattern("/*"); map.setDispatcher("REQUEST"); context.addFilterMap(map);
    }
}
