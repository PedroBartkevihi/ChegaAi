package chegaai;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
class InscricaoIT extends IntegracaoBase {
    @Test
    @DisplayName("evento lotado manda novos inscritos para a fila e promove o primeiro da fila quando abre vaga")
    void filaDeEsperaSegueOrdemDeChegada() throws Exception {
        UsuarioTeste organizador = cadastrar("Org");
        UsuarioTeste alfa = cadastrar("Alfa");
        UsuarioTeste bravo = cadastrar("Bravo");
        UsuarioTeste charlie = cadastrar("Charlie");
        UsuarioTeste delta = cadastrar("Delta");
        UUID eventoId = criarEventoValido(organizador, 3);
        alternarInscricao(alfa, eventoId).andExpect(jsonPath("$.status").value("CONFIRMADO"));
        alternarInscricao(bravo, eventoId).andExpect(jsonPath("$.status").value("CONFIRMADO"));
        alternarInscricao(charlie, eventoId).andExpect(jsonPath("$.status").value("FILA"));
        alternarInscricao(delta, eventoId).andExpect(jsonPath("$.status").value("FILA"));
        JsonNode lotado = buscarEvento(eventoId);
        assertThat(nomes(lotado, "confirmed")).containsExactlyInAnyOrder(
            organizador.primeiroNome(), alfa.primeiroNome(), bravo.primeiroNome());
        assertThat(nomes(lotado, "waitlist")).containsExactly(charlie.primeiroNome(), delta.primeiroNome());
        alternarInscricao(bravo, eventoId).andExpect(jsonPath("$.status").value("CANCELADO"));
        JsonNode depois = buscarEvento(eventoId);
        assertThat(nomes(depois, "confirmed")).containsExactlyInAnyOrder(
            organizador.primeiroNome(), alfa.primeiroNome(), charlie.primeiroNome());
        assertThat(nomes(depois, "waitlist")).containsExactly(delta.primeiroNome());
    }
    @Test
    @DisplayName("só confirmados ou quem está na fila podem comentar")
    void comentarioExigeInscricao() throws Exception {
        UsuarioTeste organizador = cadastrar("Org");
        UsuarioTeste visitante = cadastrar("Visitante");
        UUID eventoId = criarEventoValido(organizador, 10);
        comentar(visitante, eventoId, "Posso ir também?")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message", containsString("Apenas participantes")));
        alternarInscricao(visitante, eventoId).andExpect(status().isOk());
        comentar(visitante, eventoId, "Confirmado, estarei lá!")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.author").value(visitante.primeiroNome()));
        assertThat(buscarEvento(eventoId).get("comments").toString()).contains("Confirmado, estarei lá!");
    }
    @Test
    @DisplayName("evento com dados inválidos devolve 400 com a mensagem da regra")
    void eventoInvalidoDevolve400() throws Exception {
        UsuarioTeste organizador = cadastrar("Org");
        criarEvento(organizador.token(), "Pe", 10)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("título")));
    }
    @Test
    @DisplayName("evento com data que já passou devolve 400 e não é criado")
    void eventoNoPassadoDevolve400() throws Exception {
        UsuarioTeste organizador = cadastrar("Org");
        String titulo = "Pelada de ontem " + organizador.primeiroNome();
        criarEvento(organizador.token(), titulo, 10, LocalDate.now().minusDays(1))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("A data de realização deve ser uma data futura."));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM evento WHERE titulo = ?", Long.class, titulo))
            .isZero();
    }
    private ResultActions comentar(UsuarioTeste autor, UUID eventoId, String texto)
            throws Exception {
        return mockMvc.perform(post("/eventos/{id}/comentarios", eventoId)
            .header("Authorization", bearer(autor.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("text", texto))));
    }
    private static List<String> nomes(JsonNode evento, String campo) {
        List<String> nomes = new ArrayList<>();
        evento.get(campo).forEach(nome -> nomes.add(nome.asText()));
        return nomes;
    }
}
