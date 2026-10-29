package chegaai.controller.exception;
import chegaai.service.AuthService;
import chegaai.limite.LimiteExcedidoException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
/**
 * Converte exceções em respostas JSON no formato {timestamp, status, error, message}.
 * Os erros do próprio Spring MVC (rota inexistente, método não suportado, parâmetro inválido,
 * corpo ilegível...) recebem o status do {@link ResponseEntityExceptionHandler}; aqui só se troca
 * o corpo. Respostas 5xx nunca levam a mensagem da exceção: o detalhe fica só no log.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    static final String ERRO_INTERNO = "Erro interno do servidor. Tente novamente em instantes.";
    @ExceptionHandler(LimiteExcedidoException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimit(LimiteExcedidoException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, Long.toString(ex.getTentarAposSegundos()))
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(buildBody(HttpStatus.TOO_MANY_REQUESTS, LimiteExcedidoException.MENSAGEM));
    }
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NoSuchElementException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorized(AuthenticationException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(AccessDeniedException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage());
    }
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(IllegalStateException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
            .findFirst()
            .map(ConstraintViolation::getMessage)
            .orElse("Requisição inválida.");
        return buildResponse(HttpStatus.BAD_REQUEST, message);
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
        if (violouConstraint(ex, "uk_inscricao_usuario_evento")) {
            log.warn("Inscrição duplicada barrada pelo banco: {}", ex.getMostSpecificCause().getMessage());
            return buildResponse(HttpStatus.CONFLICT,
                "Você já tem uma inscrição neste evento. Atualize a página para ver a situação atual.");
        }
        // Cadastros simultâneos passam juntos pela checagem do serviço e só o banco barra o segundo.
        if (violouConstraint(ex, "uk_usuario_email")) {
            return buildResponse(HttpStatus.CONFLICT, AuthService.EMAIL_EM_USO);
        }
        if (violouConstraint(ex, "uk_usuario_cpf")) {
            return buildResponse(HttpStatus.CONFLICT, AuthService.CADASTRO_RECUSADO);
        }
        return handleGeneral(ex);
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        log.error("Erro inesperado ao processar requisição", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, ERRO_INTERNO);
    }
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        if (status.is5xxServerError()) {
            log.error("Erro inesperado ao processar requisição", ex);
        }
        return super.handleExceptionInternal(ex, buildBody(status, mensagemDoSpringMvc(ex, status)),
            headers, statusCode, request);
    }
    private static String mensagemDoSpringMvc(Exception ex, HttpStatus status) {
        if (status.is5xxServerError()) {
            return ERRO_INTERNO;
        }
        if (ex instanceof MethodArgumentNotValidException invalido) {
            return invalido.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> "Campo '" + error.getField() + "': " + error.getDefaultMessage())
                .orElse("Requisição inválida.");
        }
        if (ex instanceof HttpMessageNotReadableException) {
            return "O corpo da requisição não é um JSON válido.";
        }
        if (ex instanceof MethodArgumentTypeMismatchException tipoErrado) {
            return "Valor inválido para o parâmetro '" + tipoErrado.getName() + "'.";
        }
        if (ex instanceof MissingServletRequestParameterException ausente) {
            return "Parâmetro obrigatório ausente: '" + ausente.getParameterName() + "'.";
        }
        if (ex instanceof NoResourceFoundException) {
            return "Rota não encontrada.";
        }
        if (ex instanceof HttpRequestMethodNotSupportedException metodo) {
            return "Método " + metodo.getMethod() + " não é aceito nesta rota.";
        }
        if (ex instanceof HttpMediaTypeNotSupportedException) {
            return "Envie o corpo da requisição como application/json.";
        }
        return "Requisição inválida.";
    }
    private static boolean violouConstraint(Throwable ex, String constraint) {
        for (Throwable causa = ex; causa != null; causa = causa.getCause()) {
            if (causa instanceof org.hibernate.exception.ConstraintViolationException violacao) {
                return constraint.equalsIgnoreCase(violacao.getConstraintName());
            }
        }
        return false;
    }
    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(buildBody(status, message));
    }
    private static Map<String, Object> buildBody(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", OffsetDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return body;
    }
}
