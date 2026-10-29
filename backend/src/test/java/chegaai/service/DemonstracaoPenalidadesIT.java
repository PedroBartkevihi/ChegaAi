package chegaai.service;

import chegaai.IntegracaoBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DemonstracaoPenalidadesIT extends IntegracaoBase {
    @Autowired
    private DataSeederService dataSeederService;
    @Autowired
    private HonraService honraService;
    private final List<UUID> eventosDoTeste = new ArrayList<>();

    @BeforeEach
    void prepararDemonstracao() {
        recriar();
    }

    @AfterEach
    void limparCenariosDoTeste() {
        // Apenas fixtures deste banco descartavel; o reset de producao preserva os concluidos reais.
        for (UUID evento : eventosDoTeste) {
            jdbcTemplate.update("DELETE FROM evento WHERE id_evento=?", evento);
        }
        recriar();
    }

    @Test
    @DisplayName("reset restaura demo suspenso, remove tardios e descarta desconto de cenario somente ficticio")
    void resetLimpaPunicoesDeCenarioDemo() throws Exception {
        UUID pedro = DataSeederService.idDeDemonstracao("pedro@chegaai.dev");
        String tokenMarcos = login("marcos@chegaai.dev", DataSeederService.SENHA_DEMO);
        UUID evento = criarEventoDemo(tokenMarcos, "Cenario ficticio com ausencia");
        inscreverPedro(evento);
        moverParaOPassado(evento);
        concluirDemo(tokenMarcos, evento);
        assertValorUsuario(pedro, "honra_media", "4.40");
        assertDescontoInscricao(evento, pedro, "0.50");
        suspender(pedro);
        inserirTardios(pedro, evento);

        recriar();
        recriar();

        assertThat(contar("SELECT COUNT(*) FROM evento WHERE id_evento=?", evento)).isZero();
        assertThat(contar("SELECT COUNT(*) FROM cancelamento_tardio WHERE id_usuario=?", pedro)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT status_usuario FROM usuario WHERE id_usuario=?", String.class, pedro))
            .isEqualTo("ATIVO");
        assertThat(jdbcTemplate.queryForObject("SELECT suspenso_ate FROM usuario WHERE id_usuario=?", OffsetDateTime.class, pedro))
            .isNull();
        assertValorUsuario(pedro, "desconto_honra_acumulado", "0.00");
        assertValorUsuario(pedro, "honra_media", "4.90");
    }

    @Test
    @DisplayName("reset preserva desconto original de concluido com visitante e nao reinicia punicoes reais")
    void resetPreservaValorOriginalEContaReal() throws Exception {
        UsuarioTeste visitante = cadastrar("VisitantePunido");
        UUID pedro = DataSeederService.idDeDemonstracao("pedro@chegaai.dev");
        String tokenMarcos = login("marcos@chegaai.dev", DataSeederService.SENHA_DEMO);
        UUID evento = criarEventoDemo(tokenMarcos, "Historico com desconto original");
        inscreverPedro(evento);
        alternarInscricao(visitante, evento).andExpect(status().isOk());
        BigDecimal descontoAnterior = (BigDecimal) ReflectionTestUtils.getField(honraService, "deducaoAusencia");
        try {
            ReflectionTestUtils.setField(honraService, "deducaoAusencia", new BigDecimal("0.70"));
            moverParaOPassado(evento);
            concluirDemo(tokenMarcos, evento);
        } finally {
            ReflectionTestUtils.setField(honraService, "deducaoAusencia", descontoAnterior);
        }
        assertDescontoInscricao(evento, pedro, "0.70");
        assertDescontoInscricao(evento, visitante.id(), "0.70");
        OffsetDateTime suspensoAte = suspender(visitante.id());
        inserirTardios(visitante.id(), evento);

        recriar();
        recriar();

        assertThat(buscarEvento(evento).get("status").asText()).isEqualTo("CONCLUIDO");
        assertDescontoInscricao(evento, pedro, "0.70");
        assertValorUsuario(pedro, "desconto_honra_acumulado", "0.70");
        assertValorUsuario(pedro, "honra_media", "4.20");
        assertDescontoInscricao(evento, visitante.id(), "0.70");
        assertValorUsuario(visitante.id(), "desconto_honra_acumulado", "0.70");
        assertValorUsuario(visitante.id(), "honra_media", "2.30");
        assertThat(contar("SELECT COUNT(*) FROM cancelamento_tardio WHERE id_usuario=?", visitante.id())).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("SELECT status_usuario FROM usuario WHERE id_usuario=?", String.class, visitante.id()))
            .isEqualTo("SUSPENSO");
        assertThat(jdbcTemplate.queryForObject("SELECT suspenso_ate FROM usuario WHERE id_usuario=?", OffsetDateTime.class, visitante.id()))
            .isEqualTo(suspensoAte);
    }

    @Test
    @DisplayName("upsert preserva a base individual existente da conta demo")
    void resetPreservaBaseIndividual() {
        UUID pedro = DataSeederService.idDeDemonstracao("pedro@chegaai.dev");
        BigDecimal baseAnterior = jdbcTemplate.queryForObject("SELECT honra_inicial FROM usuario WHERE id_usuario=?", BigDecimal.class, pedro);
        try {
            jdbcTemplate.update("UPDATE usuario SET honra_inicial=4.60 WHERE id_usuario=?", pedro);
            recriar();
            recriar();
            assertValorUsuario(pedro, "honra_inicial", "4.60");
            assertValorUsuario(pedro, "honra_media", "4.60");
        } finally {
            jdbcTemplate.update("UPDATE usuario SET honra_inicial=? WHERE id_usuario=?", baseAnterior, pedro);
        }
    }

    private UUID criarEventoDemo(String token, String titulo) throws Exception {
        UUID evento = UUID.fromString(json(criarEvento(token, titulo, 10)
            .andExpect(status().isCreated())).get("id").asText());
        eventosDoTeste.add(evento);
        return evento;
    }

    private void inscreverPedro(UUID evento) throws Exception {
        String token = login("pedro@chegaai.dev", DataSeederService.SENHA_DEMO);
        mockMvc.perform(post("/eventos/{id}/inscricao", evento).header("Authorization", bearer(token)))
            .andExpect(status().isOk());
    }

    private void concluirDemo(String token, UUID evento) throws Exception {
        mockMvc.perform(post("/eventos/{id}/concluir", evento).header("Authorization", bearer(token)))
            .andExpect(status().isOk());
    }

    private OffsetDateTime suspender(UUID usuario) {
        OffsetDateTime ate = OffsetDateTime.now().plusDays(7).withNano(0);
        jdbcTemplate.update("UPDATE usuario SET status_usuario='SUSPENSO', suspenso_ate=? WHERE id_usuario=?", ate, usuario);
        return ate;
    }

    private void inserirTardios(UUID usuario, UUID evento) {
        for (int i = 0; i < 3; i++) {
            jdbcTemplate.update("INSERT INTO cancelamento_tardio (id_cancelamento, id_usuario, id_evento, cancelado_em) " +
                "VALUES (?, ?, ?, now())", UUID.randomUUID(), usuario, evento);
        }
    }

    private void recriar() {
        ReflectionTestUtils.setField(dataSeederService, "recriarAoIniciar", true);
        try {
            dataSeederService.run();
        } finally {
            ReflectionTestUtils.setField(dataSeederService, "recriarAoIniciar", false);
        }
    }

    private void assertDescontoInscricao(UUID evento, UUID usuario, String valor) {
        assertThat(jdbcTemplate.queryForObject("SELECT desconto_honra_aplicado FROM inscricao " +
            "WHERE id_evento=? AND id_usuario=? AND status_inscricao='AUSENTE'", BigDecimal.class, evento, usuario))
            .isEqualByComparingTo(valor);
    }

    private void assertValorUsuario(UUID usuario, String coluna, String valor) {
        assertThat(jdbcTemplate.queryForObject("SELECT " + coluna + " FROM usuario WHERE id_usuario=?", BigDecimal.class, usuario))
            .isEqualByComparingTo(valor);
    }

    private long contar(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }
}