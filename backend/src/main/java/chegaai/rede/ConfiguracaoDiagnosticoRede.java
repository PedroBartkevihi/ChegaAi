package chegaai.rede;

import jakarta.servlet.DispatcherType;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import java.util.concurrent.atomic.AtomicInteger;

/** Instrumentação temporária opt-in; não configura a confiança dos proxies. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "chegaai.rede.diagnostico", havingValue = "true")
public class ConfiguracaoDiagnosticoRede {
    private static final AtomicInteger OBSERVACOES_JVM = new AtomicInteger();

    @Bean
    FilterRegistrationBean<FiltroDiagnosticoRede> diagnosticoRede(
            @Value("${chegaai.rede.diagnostico-max-observacoes:3}") int maximo,
            @Value("${server.forward-headers-strategy:none}") String estrategia) {
        var logger = LoggerFactory.getLogger("chegaai.rede.diagnostico");
        var filtro = new FiltroDiagnosticoRede(maximo, estrategia, OBSERVACOES_JVM,
            resumo -> logger.info("REDE_DIAGNOSTICO {}", resumo));
        var registro = new FilterRegistrationBean<>(filtro);
        registro.setName("diagnosticoRede");
        registro.addUrlPatterns("/actuator/health");
        registro.setDispatcherTypes(DispatcherType.REQUEST);
        // O ForwardedHeaderFilter do Boot usa HIGHEST_PRECEDENCE: observar depois do wrapper.
        registro.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registro;
    }
}
