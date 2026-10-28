package chegaai;
import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
class HorarioIT extends IntegracaoBase {
    @Test
    @DisplayName("o evento volta com a mesma data e hora em que foi criado, no horário de Brasília")
    void eventoMantemDataEHora() throws Exception {
        UsuarioTeste organizador = cadastrar("Org");
        UUID eventoId = criarEventoValido(organizador, 10);
        JsonNode evento = buscarEvento(eventoId);
        assertThat(evento.get("date").asText()).isEqualTo(LocalDate.now().plusDays(5).toString());
        assertThat(evento.get("time").asText()).isEqualTo("18:00");
    }
    @Test
    @DisplayName("o horário do comentário é exibido no fuso de Brasília")
    void comentarioUsaHorarioDeBrasilia() throws Exception {
        UsuarioTeste organizador = cadastrar("Org");
        UUID eventoId = criarEventoValido(organizador, 10);
        mockMvc.perform(post("/eventos/{id}/comentarios", eventoId)
                .header("Authorization", bearer(organizador.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("text", "Chego às 18h."))))
            .andExpect(status().isCreated());
        // lido de volta do banco, onde o timestamptz chega em UTC
        JsonNode comentarios = buscarEvento(eventoId).get("comments");
        LocalTime exibido = LocalTime.parse(comentarios.get(comentarios.size() - 1).get("time").asText());
        LocalTime agoraEmBrasilia = LocalTime.now(ZoneId.of("America/Sao_Paulo"));
        long diferencaEmMinutos = Math.abs(Duration.between(exibido, agoraEmBrasilia).toMinutes());
        assertThat(Math.min(diferencaEmMinutos, 24 * 60 - diferencaEmMinutos)).isLessThanOrEqualTo(1);
    }
}
