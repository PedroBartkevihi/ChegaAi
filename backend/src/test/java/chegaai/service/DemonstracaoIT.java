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
                .containsExactlyElementsOf(estado.equals("travado") ? java.util.List.of(organizador.primeiroNome())
                    : java.util.List.of(organizador.primeiroNome(), "Pedro", "Caio"));
            assertThat(depois.get("waitlist")).extracting(JsonNode::asText).containsExactly(espera.primeiroNome());
        }
    }
    @Test
    void recriarPreservaNotasAusenciasEHonraEmRolesConcluidos() throws Exception {
        var org = cadastrar("HistoricoReal");
        var ausente = cadastrar("AusenteReal");
        UUID evento = criarEventoValido(org, 10);
        alternarInscricao(ausente, evento).andExpect(status().isOk());
        inscreverDemo("marcos@chegaai.dev", evento, "CONFIRMADO");
        UUID marcos = DataSeederService.idDeDemonstracao("marcos@chegaai.dev");
        moverParaOPassado(evento);
        mockMvc.perform(post("/eventos/{id}/presencas", evento).header("Authorization", bearer(org.token()))
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(Map.of("participanteId", marcos))))
            .andExpect(status().isNoContent());
        concluir(org, evento).andExpect(status().isOk());
        mockMvc.perform(post("/eventos/{id}/avaliacoes", evento).header("Authorization", bearer(org.token()))
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(Map.of("avaliadoId", marcos, "nota", 4))))
            .andExpect(status().isCreated());
        double honraAntes = usuarioAtual(ausente).get("honor").asDouble();
        recriarDemonstracao();
        recriarDemonstracao();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM avaliacao WHERE id_evento=?", Integer.class, evento)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM inscricao WHERE id_evento=? AND id_usuario=?", Integer.class, evento, marcos)).isEqualTo(1);
        assertThat(usuarioAtual(ausente).get("honor").asDouble()).isEqualTo(honraAntes).isEqualTo(2.5);
        assertThat(jdbcTemplate.queryForObject("SELECT desconto_honra_acumulado FROM usuario WHERE id_usuario=?", java.math.BigDecimal.class, ausente.id()))
            .isEqualByComparingTo("0.5");
        assertThat(jdbcTemplate.queryForObject("SELECT honra_media FROM usuario WHERE id_usuario=?", java.math.BigDecimal.class, marcos))
            .isEqualByComparingTo("5.0");
    }

    @Test
    void concluidoOrganizadoPorDemoComVisitanteNaoDesaparece() throws Exception {
        var visitante = cadastrar("VisitanteHistorico");
        String token = login("marcos@chegaai.dev", "admin123");
        UUID evento = UUID.fromString(json(criarEvento(token, "Historico demo com visitante", 10)
            .andExpect(status().isCreated())).get("id").asText());
        alternarInscricao(visitante, evento).andExpect(status().isOk());
        moverParaOPassado(evento);
        mockMvc.perform(post("/eventos/{id}/concluir", evento).header("Authorization", bearer(token)))
            .andExpect(status().isOk());
        recriarDemonstracao();
        recriarDemonstracao();
        assertThat(buscarEvento(evento).get("status").asText()).isEqualTo("CONCLUIDO");
        assertThat(jdbcTemplate.queryForObject("SELECT status_inscricao FROM inscricao WHERE id_evento=? AND id_usuario=?",
            String.class, evento, visitante.id())).isEqualTo("AUSENTE");
        assertThat(usuarioAtual(visitante).get("honor").asDouble()).isEqualTo(2.5);
    }

    @Test
    void duasRecriacoesSimultaneasNaoDuplicamCenarios() throws Exception {
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch largada = new java.util.concurrent.CountDownLatch(1);
        ReflectionTestUtils.setField(dataSeederService, "recriarAoIniciar", true);
        try {
            var primeira = executor.submit(() -> { largada.await(); dataSeederService.run(); return true; });
            var segunda = executor.submit(() -> { largada.await(); dataSeederService.run(); return true; });
            largada.countDown();
            assertThat(primeira.get(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(segunda.get(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        } finally {
            ReflectionTestUtils.setField(dataSeederService, "recriarAoIniciar", false);
            executor.shutdownNow();
        }
        assertThat(contar("SELECT COUNT(*) FROM evento WHERE titulo=?", "Futebol no Aterro")).isEqualTo(1);
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
