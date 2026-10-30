package chegaai.limite;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

@RequiredArgsConstructor
public class InterceptorLimiteAutenticacao implements HandlerInterceptor {
    private final LimitadorRequisicoes limitador;
    private final IdentidadeCliente identidade;
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"POST".equals(request.getMethod())) return true;
        // Usa o mapeamento já resolvido pelo MVC: a URI bruta pode conter letras percent-encoded.
        Object mapeamento = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String caminho = mapeamento == null ? "" : mapeamento.toString();
        if ("/auth/login".equals(caminho)) limitador.consumir(OperacaoLimite.LOGIN_IP, identidade.ip(request));
        if ("/auth/cadastro".equals(caminho)) limitador.consumir(OperacaoLimite.CADASTRO_IP, identidade.ip(request));
        return true;
    }
}
