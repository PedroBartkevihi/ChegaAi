package chegaai;
import chegaai.service.external.GraphDBIndisponivelException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
class RecomendacoesIT extends IntegracaoBase {
    @Test
    @DisplayName("sem GraphDB, como na versão online, as recomendações respondem 503 com mensagem clara, sem detalhe interno")
    void semGraphDbResponde503() throws Exception {
        mockMvc.perform(get("/recomendacoes").param("termo", "praia"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.status").value(503))
            .andExpect(jsonPath("$.message").value(GraphDBIndisponivelException.MENSAGEM));
        mockMvc.perform(get("/recomendacoes/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(false));
    }
}
