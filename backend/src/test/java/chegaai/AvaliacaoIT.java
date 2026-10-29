package chegaai;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
class AvaliacaoIT extends IntegracaoBase {
    @Test
    @DisplayName("fluxo completo: presença, conclusão, bloqueio por avaliação pendente e liberação após avaliar")
    void fluxoCompletoDeAvaliacao() throws Exception {
        UsuarioTeste organizador = cadastrar("Org");
        UsuarioTeste alfa = cadastrar("Alfa");
        UsuarioTeste bravo = cadastrar("Bravo");
        UUID eventoId = criarEventoValido(organizador, 10);
        alternarInscricao(alfa, eventoId).andExpect(status().isOk());
        alternarInscricao(bravo, eventoId).andExpect(status().isOk());
        moverParaOPassado(eventoId);
        registrarPresenca(organizador, eventoId, alfa).andExpect(status().isNoContent());
        registrarPresenca(organizador, eventoId, bravo).andExpect(status().isNoContent());
        concluir(organizador, eventoId).andExpect(jsonPath("$.status").value("CONCLUIDO"));
        UUID proximoEvento = criarEventoValido(cadastrar("OutroOrg"), 10);
        alternarInscricao(alfa, proximoEvento)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message", containsString("avaliações pendentes")));
        avaliar(alfa, eventoId, bravo, 5).andExpect(status().isCreated());
        avaliar(alfa, eventoId, organizador, 4).andExpect(status().isCreated());
        alternarInscricao(alfa, proximoEvento)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CONFIRMADO"));
        assertThat(usuarioAtual(bravo).get("honor").asDouble()).isEqualTo(3.0);
        assertThat(usuarioAtual(organizador).get("honor").asDouble()).isEqualTo(3.0);
    }
    @Test
    @DisplayName("avaliação exige evento concluído, participação dos dois lados e é única por pessoa")
    void regrasDaAvaliacao() throws Exception {
        UsuarioTeste organizador = cadastrar("Org");
        UsuarioTeste alfa = cadastrar("Alfa");
        UsuarioTeste bravo = cadastrar("Bravo");
        UsuarioTeste faltou = cadastrar("Faltou");
        UsuarioTeste naoInscrito = cadastrar("Fora");
        UUID eventoId = criarEventoValido(organizador, 10);
        alternarInscricao(alfa, eventoId).andExpect(status().isOk());
        alternarInscricao(bravo, eventoId).andExpect(status().isOk());
        alternarInscricao(faltou, eventoId).andExpect(status().isOk());
        moverParaOPassado(eventoId);
        registrarPresenca(organizador, eventoId, alfa).andExpect(status().isNoContent());
        registrarPresenca(organizador, eventoId, bravo).andExpect(status().isNoContent());
        avaliar(alfa, eventoId, bravo, 5)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message", containsString("após a conclusão")));
        concluir(organizador, eventoId).andExpect(status().isOk());
        avaliar(alfa, eventoId, naoInscrito, 3)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message", containsString("quem participou")));
        avaliar(alfa, eventoId, alfa, 5).andExpect(status().isBadRequest());
        avaliar(alfa, eventoId, bravo, 5).andExpect(status().isCreated());
        avaliar(alfa, eventoId, bravo, 1)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message", containsString("já avaliou")));
        avaliar(faltou, eventoId, alfa, 1)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message", containsString("Apenas participantes confirmados")));
        assertThat(usuarioAtual(faltou).get("honor").asDouble()).isEqualTo(2.5);
    }
    @Test
    @DisplayName("só o organizador registra presença, apenas de inscritos e antes de concluir; ele mesmo não é penalizado")
    void regrasDaPresenca() throws Exception {
        UsuarioTeste organizador = cadastrar("Org");
        UsuarioTeste alfa = cadastrar("Alfa");
        UsuarioTeste naoInscrito = cadastrar("Fora");
        UUID eventoId = criarEventoValido(organizador, 10);
        alternarInscricao(alfa, eventoId).andExpect(status().isOk());
        moverParaOPassado(eventoId);
        registrarPresenca(alfa, eventoId, alfa)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message", containsString("Apenas o organizador")));
        registrarPresenca(organizador, eventoId, naoInscrito).andExpect(status().isNotFound());
        concluir(organizador, eventoId).andExpect(status().isOk());
        registrarPresenca(organizador, eventoId, alfa)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message", containsString("antes de concluir")));
        assertThat(usuarioAtual(organizador).get("honor").asDouble()).isEqualTo(3.0);
    }
    private ResultActions avaliar(UsuarioTeste avaliador, UUID eventoId, UsuarioTeste avaliado, int nota)
            throws Exception {
        return mockMvc.perform(post("/eventos/{id}/avaliacoes", eventoId)
            .header("Authorization", bearer(avaliador.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("avaliadoId", avaliado.id(), "nota", nota))));
    }
}
