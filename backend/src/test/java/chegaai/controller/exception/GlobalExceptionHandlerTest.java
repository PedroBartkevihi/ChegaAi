package chegaai.controller.exception;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    @Test
    @DisplayName("erro inesperado devolve 500 com mensagem genérica, sem o detalhe da exceção")
    void erroInesperadoNaoExpoeDetalheDaExcecao() {
        ResponseEntity<Map<String, Object>> resposta =
            handler.handleGeneral(new RuntimeException("SELECT secret FROM x"));
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resposta.getBody()).containsEntry("message", GlobalExceptionHandler.ERRO_INTERNO);
        assertThat(resposta.getBody().toString()).doesNotContain("SELECT", "secret");
    }
}
