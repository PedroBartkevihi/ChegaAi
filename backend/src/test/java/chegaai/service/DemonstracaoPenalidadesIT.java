package chegaai.service;

import chegaai.IntegracaoBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DemonstracaoPenalidadesIT extends IntegracaoBase {
    // Bases gravadas pelo seed; a tela de login anuncia as de Pedro, Caio, Marcos e Baixa.
    private static final Map<String, String> BASES_DO_SEED = Map.ofEntries(
        Map.entry("pedro@chegaai.dev", "4.90"), Map.entry("caio@chegaai.dev", "4.90"),
        Map.entry("nina@chegaai.dev", "4.70"), Map.entry("marcos@chegaai.dev", "5.00"),
        Map.entry("lia@chegaai.dev", "4.50"), Map.entry("rafael@chegaai.dev", "4.30"),
        Map.entry("bia@chegaai.dev", "4.60"), Map.entry("paula@chegaai.dev", "4.20"),
        Map.entry("bruno@chegaai.dev", "4.10"), Map.entry("baixa@chegaai.dev", "1.50"),
        Map.entry("moderador@chegaai.dev", "5.00"), Map.entry("tester.pendente@chegaai.dev", "5.00"));
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
    @DisplayName("reset devolve a base do seed as contas demo recriadas sem ela, como em producao")
    void resetRestauraBaseDoSeed() throws Exception {
        assertThat(BASES_DO_SEED).containsOnlyKeys(DataSeederService.EMAILS_DEMO);
        Map<String, BigDecimal> honraAntes = honraDasContasDemo();
        long embaixadoresAntes = embaixadoresNoResumo();
        // Estado da producao em 30/10/2026: uma revisao anterior a V10 recriou as contas sem honra_inicial
        // (padrao da coluna, 3,00), e o recalculo seguinte levou a honra de todas para 3,00.
        for (String email : DataSeederService.EMAILS_DEMO) {
            jdbcTemplate.update("UPDATE usuario SET honra_inicial=DEFAULT, honra_media=3.00 WHERE email=?", email);
        }
        assertThat(embaixadoresNoResumo()).isLessThan(embaixadoresAntes);

        recriar();

        BASES_DO_SEED.forEach((email, base) ->
            assertValorUsuario(DataSeederService.idDeDemonstracao(email), "honra_inicial", base));
        assertThat(honraDasContasDemo()).isEqualTo(honraAntes);
        UUID futebol = jdbcTemplate.queryForObject("SELECT id_evento FROM evento WHERE titulo=?", UUID.class,
            "Futebol no Aterro");
        JsonNode caio = buscarEvento(futebol).get("organizer");
        assertThat(caio.get("honor").asDouble()).isEqualTo(4.9);
        assertThat(caio.get("isAmbassador").asBoolean()).isTrue();
        assertThat(embaixadoresNoResumo()).isEqualTo(embaixadoresAntes);
    }

    private Map<String, BigDecimal> honraDasContasDemo() {
        Map<String, BigDecimal> honras = new HashMap<>();
        for (String email : DataSeederService.EMAILS_DEMO) {
            honras.put(email, jdbcTemplate.queryForObject("SELECT honra_media FROM usuario WHERE email=?", BigDecimal.class, email));
        }
        return honras;
    }

    private long embaixadoresNoResumo() throws Exception {
        return json(mockMvc.perform(get("/eventos/resumo")).andExpect(status().isOk())).get("ambassadors").asLong();
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