package chegaai.limite;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.HandlerInterceptor;

@RequiredArgsConstructor
public class InterceptorLimiteAutenticacao implements HandlerInterceptor {
    private final LimitadorRequisicoes limitador;
    private final IdentidadeCliente identidade;
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"POST".equals(request.getMethod())) return true;
        String caminho = request.getRequestURI().substring(request.getContextPath().length());
        if ("/auth/login".equals(caminho)) limitador.consumir(OperacaoLimite.LOGIN_IP, identidade.ip(request));
        if ("/auth/cadastro".equals(caminho)) limitador.consumir(OperacaoLimite.CADASTRO_IP, identidade.ip(request));
        return true;
    }
}
