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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
        // as contas de demonstração voltam com o mesmo id: o token de antes da recriação continua valendo
        mockMvc.perform(get("/auth/me").header("Authorization", bearer(tokenPedro)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(DataSeederService.idDeDemonstracao("pedro@chegaai.dev").toString()));
    }
    @Test
    @DisplayName("recriar demonstração promove todas as vagas liberadas em rolé real, pela ordem da fila")
    void recriarDemonstracaoPromoveFilaReal() throws Exception {
        UsuarioTeste organizador = cadastrar("Real");
        UsuarioTeste primeiro = cadastrar("Primeiro");
        UsuarioTeste segundo = cadastrar("Segundo");
        UsuarioTeste terceiro = cadastrar("Terceiro");
        UUID evento = criarEventoValido(organizador, 3);
        inscreverDemo("pedro@chegaai.dev", evento, "CONFIRMADO");
        inscreverDemo("caio@chegaai.dev", evento, "CONFIRMADO");
        alternarInscricao(primeiro, evento).andExpect(jsonPath("$.status").value("FILA"));
        alternarInscricao(segundo, evento).andExpect(jsonPath("$.status").value("FILA"));
        alternarInscricao(terceiro, evento).andExpect(jsonPath("$.status").value("FILA"));
        recriarDemonstracao();
        recriarDemonstracao();
        JsonNode depois = buscarEvento(evento);
        assertThat(depois.get("confirmed")).extracting(JsonNode::asText)
            .containsExactly(organizador.primeiroNome(), primeiro.primeiroNome(), segundo.primeiroNome());
        assertThat(depois.get("waitlist")).extracting(JsonNode::asText).containsExactly(terceiro.primeiroNome());
    }
    @Test
    @DisplayName("remover demo da fila não libera vaga nem promove além da capacidade")
    void removerDemoDaFilaNaoPromove() throws Exception {
        UsuarioTeste organizador = cadastrar("Real");
        UsuarioTeste alfa = cadastrar("Alfa");
        UsuarioTeste bravo = cadastrar("Bravo");
        UsuarioTeste espera = cadastrar("Espera");
        UUID evento = criarEventoValido(organizador, 3);
        alternarInscricao(alfa, evento).andExpect(jsonPath("$.status").value("CONFIRMADO"));
        alternarInscricao(bravo, evento).andExpect(jsonPath("$.status").value("CONFIRMADO"));
        inscreverDemo("pedro@chegaai.dev", evento, "FILA");
        alternarInscricao(espera, evento).andExpect(jsonPath("$.status").value("FILA"));
        recriarDemonstracao();
        JsonNode depois = buscarEvento(evento);
        assertThat(depois.get("confirmed")).hasSize(3);
        assertThat(depois.get("waitlist")).extracting(JsonNode::asText).containsExactly(espera.primeiroNome());
    }
    @Test
    @DisplayName("limpeza não promove participantes em rolé passado, concluído ou travado")
    void limpezaNaoPromoveEventosFechados() throws Exception {
        for (String estado : new String[] {"passado", "concluido", "travado"}) {
            UsuarioTeste organizador = cadastrar("Real");
            UsuarioTeste espera = cadastrar("Espera");
            UUID evento = criarEventoValido(organizador, 3);
            inscreverDemo("pedro@chegaai.dev", evento, "CONFIRMADO");
            inscreverDemo("caio@chegaai.dev", evento, "CONFIRMADO");
            alternarInscricao(espera, evento).andExpect(jsonPath("$.status").value("FILA"));
            switch (estado) {
                case "passado" -> moverParaOPassado(evento);
                case "concluido" -> jdbcTemplate.update(
                    "UPDATE evento SET status_evento = 'CONCLUIDO' WHERE id_evento = ?", evento);
                case "travado" -> jdbcTemplate.update("UPDATE evento SET travado = true WHERE id_evento = ?", evento);
                default -> throw new AssertionError(estado);
            }
            recriarDemonstracao();
            JsonNode depois = buscarEvento(evento);
            assertThat(depois.get("confirmed")).extracting(JsonNode::asText)
                .containsExactly(organizador.primeiroNome());
            assertThat(depois.get("waitlist")).extracting(JsonNode::asText).containsExactly(espera.primeiroNome());
        }
    }
    private void inscreverDemo(String email, UUID evento, String esperado) throws Exception {
        mockMvc.perform(post("/eventos/{id}/inscricao", evento)
            .header("Authorization", bearer(login(email, "admin123"))))
            .andExpect(jsonPath("$.status").value(esperado));
    }
    private void recriarDemonstracao() {
        ReflectionTestUtils.setField(dataSeederService, "recriarAoIniciar", true);
        try {
            dataSeederService.run();
        } finally {
            ReflectionTestUtils.setField(dataSeederService, "recriarAoIniciar", false);
        }
    }
    private long contar(String sql, Object... args) {
        Long total = jdbcTemplate.queryForObject(sql, Long.class, args);
        return total == null ? 0 : total;
    }
}
