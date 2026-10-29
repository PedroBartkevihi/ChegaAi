package chegaai.limite;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.util.regex.Pattern;

@Configuration
@EnableConfigurationProperties(ConfiguracaoLimites.class)
public class ConfiguracaoLimitacao implements WebMvcConfigurer {
    private final LimitadorRequisicoes limitador;
    private final IdentidadeCliente identidade;
    public ConfiguracaoLimitacao(LimitadorRequisicoes limitador, IdentidadeCliente identidade) {
        this.limitador = limitador; this.identidade = identidade;
    }
    @Bean
    public static RelogioLimites relogioLimites() { return System::nanoTime; }
    @Bean
    public InitializingBean validarProxyCloudRun(Environment env) {
        return () -> validarProxy(env.getProperty("K_SERVICE"), env.getProperty("server.forward-headers-strategy"),
            env.getProperty("server.tomcat.remoteip.internal-proxies"));
    }
    static void validarProxy(String cloudRun, String estrategia, String proxies) {
        if (!"native".equalsIgnoreCase(estrategia) || proxies == null || proxies.isBlank()) {
            throw new IllegalStateException("O limitador exige estratégia native e uma expressão explícita de proxies; o padrão (?!) não confia em nenhum.");
        }
        Pattern pattern = Pattern.compile(proxies);
        if (pattern.matcher("nao-e-um-endereco").matches()) {
            throw new IllegalStateException("A configuração de proxies confiáveis não pode aceitar qualquer valor.");
        }
        if (cloudRun != null && !cloudRun.isBlank() && "(?!)".equals(proxies)) {
            throw new IllegalStateException("Cloud Run exige proxies confiáveis explícitos e verificados antes de ativar a aplicação. Configure CHEGAAI_PROXIES_CONFIAVEIS; veja DEPLOY.md.");
        }
    }
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new InterceptorLimiteAutenticacao(limitador, identidade))
            .addPathPatterns("/auth/login", "/auth/cadastro");
    }
}
