package chegaai.rede;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

final class FiltroDiagnosticoRede extends OncePerRequestFilter {
    private final int maximo;
    private final String estrategia;
    private final AtomicInteger observacoes;
    private final Consumer<ResumoDiagnosticoRede> registrar;

    FiltroDiagnosticoRede(int maximo, String estrategia, AtomicInteger observacoes,
                         Consumer<ResumoDiagnosticoRede> registrar) {
        if (maximo < 1 || maximo > 3) throw new IllegalArgumentException("Limite de observações deve ser de 1 a 3.");
        this.maximo = maximo;
        this.estrategia = switch (estrategia.toLowerCase(java.util.Locale.ROOT)) {
            case "framework", "native", "none" -> estrategia.toLowerCase(java.util.Locale.ROOT);
            default -> "outra";
        };
        this.observacoes = observacoes;
        this.registrar = registrar;
    }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"GET".equals(request.getMethod())
            || !(request.getContextPath() + "/actuator/health").equals(request.getRequestURI())
            || !"proxy-v1".equals(request.getHeader("X-Chegaai-Diagnostico"));
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                              FilterChain chain) throws ServletException, IOException {
        if (adquirir()) {
            try {
                HttpServletRequest base = base(request);
                registrar.accept(ResumoDiagnosticoRede.observar(base, request, estrategia));
            } catch (RuntimeException ignorada) {
                // Diagnóstico não altera o health; nem a exceção (que pode conter dados) é registrada.
            }
        }
        chain.doFilter(request, response);
    }

    private boolean adquirir() {
        int atual = observacoes.get();
        while (atual < maximo) {
            if (observacoes.compareAndSet(atual, atual + 1)) return true;
            atual = observacoes.get();
        }
        return false;
    }

    static HttpServletRequest base(HttpServletRequest request) {
        ServletRequest atual = request;
        // Tomcat nativo já aplicou a Valve; unwrap não recupera o peer anterior à Valve.
        for (int i = 0; i < 32 && atual instanceof ServletRequestWrapper wrapper; i++) {
            ServletRequest proximo = wrapper.getRequest();
            if (proximo == atual || !(proximo instanceof HttpServletRequest)) break;
            atual = proximo;
        }
        return (HttpServletRequest) atual;
    }
}
