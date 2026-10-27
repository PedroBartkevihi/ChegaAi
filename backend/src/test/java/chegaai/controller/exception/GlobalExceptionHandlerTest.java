package chegaai.controller.exception;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {
    private static final String DETALHE_INTERNO = "FATAL: password authentication failed for user \"chegaai\"";
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new RotaQueFalha())
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();
    @Test
    @DisplayName("erro inesperado devolve 500 com mensagem genérica e deixa o detalhe só no log")
    void erroInesperadoNaoExpoeMensagemInterna(CapturedOutput log) throws Exception {
        mockMvc.perform(get("/falha"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.message").value(GlobalExceptionHandler.ERRO_INTERNO))
            .andExpect(content().string(not(containsString("password"))));
        assertThat(log.getOut()).contains("Erro inesperado ao processar requisição").contains(DETALHE_INTERNO);
    }
    @RestController
    static class RotaQueFalha {
        @GetMapping("/falha")
        String falhar() {
            throw new RuntimeException(DETALHE_INTERNO);
        }
    }
}
