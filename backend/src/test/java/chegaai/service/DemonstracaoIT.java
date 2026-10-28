package chegaai.service;
import chegaai.IntegracaoBase;
import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
class DemonstracaoIT extends IntegracaoBase {
    @Autowired
    private DataSeederService dataSeederService;
    @Autowired
    private NamedParameterJdbcTemplate namedJdbc;
    @Test
    @DisplayName("recriar a demonstração não duplica dados e preserva contas e eventos reais")
    void recriarDemonstracaoPreservaDadosReais() throws Exception {
        UsuarioTeste organizadorReal = cadastrar("Real");
        UUID eventoReal = criarEventoValido(organizadorReal, 10);
        String tokenPedro = login("pedro@chegaai.dev", "admin123");
        mockMvc.perform(post("/eventos/{id}/inscricao", eventoReal).header("Authorization", bearer(tokenPedro)))
            .andExpect(status().isOk());
        ReflectionTestUtils.setField(dataSeederService, "recriarAoIniciar", true);
        try {
            dataSeederService.run();
            dataSeederService.run();
        } finally {
            ReflectionTestUtils.setField(dataSeederService, "recriarAoIniciar", false);
        }
        assertThat(namedJdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE email IN (:emails)",
            Map.of("emails", DataSeederService.EMAILS_DEMO), Long.class))
            .isEqualTo(DataSeederService.EMAILS_DEMO.size());
        assertThat(contar("SELECT COUNT(*) FROM categoria WHERE nome = ?", "Futebol")).isEqualTo(1);
        assertThat(contar("SELECT COUNT(*) FROM evento WHERE titulo = ?", "Futebol no Aterro")).isEqualTo(1);
        JsonNode evento = buscarEvento(eventoReal);
        assertThat(evento.get("confirmed")).extracting(JsonNode::asText)
            .containsExactly(organizadorReal.primeiroNome());
        assertThat(usuarioAtual(organizadorReal).get("email").asText()).isEqualTo(organizadorReal.email());
        login("pedro@chegaai.dev", "admin123");
    }
    private long contar(String sql, Object... args) {
        Long total = jdbcTemplate.queryForObject(sql, Long.class, args);
        return total == null ? 0 : total;
    }
}
