package chegaai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PrazosEContasIT extends IntegracaoBase {
    @Test
    void naoPermiteEntrarNemSairAposOInicio() throws Exception {
        var org = cadastrar("PrazoOrg");
        var inscrito = cadastrar("PrazoInscrito");
        var visitante = cadastrar("PrazoVisitante");
        UUID evento = criarEventoValido(org, 10);
        alternarInscricao(inscrito, evento).andExpect(status().isOk());
        moverParaOPassado(evento);
        alternarInscricao(inscrito, evento).andExpect(status().isConflict());
        alternarInscricao(visitante, evento).andExpect(status().isConflict());
        concluir(org, evento).andExpect(status().isOk());
        assertThat(usuarioAtual(inscrito).get("honor").asDouble()).isEqualTo(2.5);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM inscricao WHERE id_evento=? AND id_usuario=?",
            Integer.class, evento, visitante.id())).isZero();
    }

    @Test
    void terceiroCancelamentoTardioSuspendeMesmoComTokenAntigoEExpira() throws Exception {
        var org = cadastrar("TardioOrg");
        var pessoa = cadastrar("TardioPessoa");
        for (int i = 0; i < 3; i++) {
            UUID evento = criarEventoValido(org, 10);
            alternarInscricao(pessoa, evento).andExpect(status().isOk());
            aproximarInicio(evento);
            alternarInscricao(pessoa, evento).andExpect(status().isOk());
        }
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM cancelamento_tardio WHERE id_usuario=?",
            Integer.class, pessoa.id())).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("SELECT status_usuario FROM usuario WHERE id_usuario=?",
            String.class, pessoa.id())).isEqualTo("SUSPENSO");
        assertThat(jdbcTemplate.queryForObject("SELECT suspenso_ate BETWEEN now()+interval '6 days 23 hours' AND now()+interval '7 days 1 hour' FROM usuario WHERE id_usuario=?",
            Boolean.class, pessoa.id())).isTrue();
        mockMvc.perform(get("/auth/me").header("Authorization", bearer(pessoa.token())))
            .andExpect(status().isOk());
        criarEvento(pessoa.token(), "Suspenso não cria", 10).andExpect(status().isForbidden());
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("email", pessoa.email(), "password", SENHA))))
            .andExpect(status().isOk());
        jdbcTemplate.update("UPDATE usuario SET suspenso_ate=now()-interval '1 second' WHERE id_usuario=?", pessoa.id());
        assertThat(usuarioAtual(pessoa).get("id").asText()).isEqualTo(pessoa.id().toString());
        login(pessoa.email(), SENHA);
    }

    @ParameterizedTest
    @ValueSource(strings = {"indefinida", "prazoMaior"})
    void cancelamentoNaoEncurtaSuspensaoExistente(String tipo) throws Exception {
        var org = cadastrar("SuspensaoOrg");
        var pessoa = cadastrar("SuspensaoPessoa");
        UUID evento = criarEventoValido(org, 10);
        alternarInscricao(pessoa, evento).andExpect(status().isOk());
        aproximarInicio(evento);
        for (int i = 0; i < 2; i++) {
            jdbcTemplate.update("INSERT INTO cancelamento_tardio VALUES (?,?,null,now()-interval '1 day')",
                UUID.randomUUID(), pessoa.id());
        }
        jdbcTemplate.update("UPDATE usuario SET status_usuario='SUSPENSO', suspenso_ate="
            + (tipo.equals("indefinida") ? "NULL" : "now()+interval '30 days'") + " WHERE id_usuario=?", pessoa.id());
        OffsetDateTime prazoAnterior = jdbcTemplate.queryForObject(
            "SELECT suspenso_ate FROM usuario WHERE id_usuario=?", OffsetDateTime.class, pessoa.id());

        alternarInscricao(pessoa, evento).andExpect(jsonPath("$.status").value("CANCELADO"));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM cancelamento_tardio WHERE id_usuario=?",
            Integer.class, pessoa.id())).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("SELECT status_usuario FROM usuario WHERE id_usuario=?",
            String.class, pessoa.id())).isEqualTo("SUSPENSO");
        assertThat(jdbcTemplate.queryForObject("SELECT suspenso_ate FROM usuario WHERE id_usuario=?",
            OffsetDateTime.class, pessoa.id())).isEqualTo(prazoAnterior);
        criarEvento(pessoa.token(), "Conta continua suspensa", 10).andExpect(status().isForbidden());
    }

    @Test
    void cancelamentoNaFilaEComMaisDe24HorasNaoConta() throws Exception {
        var org = cadastrar("FilaPrazoOrg");
        var a = cadastrar("FilaPrazoA");
        var b = cadastrar("FilaPrazoB");
        var fila = cadastrar("FilaPrazoEspera");
        UUID evento = criarEventoValido(org, 3);
        alternarInscricao(a, evento).andExpect(status().isOk());
        alternarInscricao(b, evento).andExpect(status().isOk());
        aproximarInicio(evento);
        for (int i = 0; i < 3; i++) {
            alternarInscricao(fila, evento).andExpect(jsonPath("$.status").value("FILA"));
            alternarInscricao(fila, evento).andExpect(jsonPath("$.status").value("CANCELADO"));
        }
        UUID distante = criarEventoValido(org, 10);
        alternarInscricao(fila, distante).andExpect(status().isOk());
        alternarInscricao(fila, distante).andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM cancelamento_tardio WHERE id_usuario=?",
            Integer.class, fila.id())).isZero();
        usuarioAtual(fila);
    }

    @Test
    void historicoSobreviveAExclusaoMasCancelamentosAntigosNaoContam() throws Exception {
        var org = cadastrar("HistoricoOrg");
        var pessoa = cadastrar("HistoricoPessoa");
        jdbcTemplate.update("INSERT INTO cancelamento_tardio VALUES (?,?,null,now()-interval '31 days')", UUID.randomUUID(), pessoa.id());
        jdbcTemplate.update("INSERT INTO cancelamento_tardio VALUES (?,?,null,now()-interval '31 days')", UUID.randomUUID(), pessoa.id());
        UUID evento = criarEventoValido(org, 10);
        alternarInscricao(pessoa, evento).andExpect(status().isOk());
        aproximarInicio(evento);
        alternarInscricao(pessoa, evento).andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM cancelamento_tardio WHERE id_usuario=?", Integer.class, pessoa.id())).as("antes de excluir").isEqualTo(3);
        mockMvc.perform(delete("/eventos/{id}", evento).header("Authorization", bearer(org.token())))
            .andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM cancelamento_tardio WHERE id_usuario=? AND id_evento IS NULL",
            Integer.class, pessoa.id())).isEqualTo(3);
        usuarioAtual(pessoa);
    }

    @Test
    void cancelamentosConcorrentesEmRolesDiferentesNaoPerdemAContagem() throws Exception {
        var org = cadastrar("ConcPrazoOrg");
        var pessoa = cadastrar("ConcPrazoPessoa");
        List<UUID> eventos = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            UUID evento = criarEventoValido(org, 10);
            alternarInscricao(pessoa, evento).andExpect(status().isOk());
            aproximarInicio(evento);
            eventos.add(evento);
        }
        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch prontos = new CountDownLatch(3), largada = new CountDownLatch(1);
        try {
            List<Future<Integer>> respostas = new ArrayList<>();
            for (UUID evento : eventos) respostas.add(executor.submit(() -> {
                prontos.countDown();
                largada.await();
                return alternarInscricao(pessoa, evento).andReturn().getResponse().getStatus();
            }));
            assertThat(prontos.await(10, TimeUnit.SECONDS)).isTrue();
            largada.countDown();
            for (Future<Integer> resposta : respostas) assertThat(resposta.get(30, TimeUnit.SECONDS)).isEqualTo(200);
        } finally { executor.shutdownNow(); }
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM cancelamento_tardio WHERE id_usuario=?", Integer.class, pessoa.id())).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("SELECT status_usuario FROM usuario WHERE id_usuario=?", String.class, pessoa.id())).isEqualTo("SUSPENSO");
    }

    @Test
    void excluirConcluidoPreservaAvaliacoes() throws Exception {
        var org = cadastrar("ConcluidoOrg");
        var pessoa = cadastrar("ConcluidoPessoa");
        UUID evento = criarEventoValido(org, 10);
        alternarInscricao(pessoa, evento).andExpect(status().isOk());
        moverParaOPassado(evento);
        registrarPresenca(org, evento, pessoa).andExpect(status().isNoContent());
        concluir(org, evento).andExpect(status().isOk());
        mockMvc.perform(post("/eventos/{id}/avaliacoes", evento).header("Authorization", bearer(pessoa.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("avaliadoId", org.id(), "nota", 4))))
            .andExpect(status().isCreated());
        mockMvc.perform(delete("/eventos/{id}", evento).header("Authorization", bearer(org.token())))
            .andExpect(status().isConflict());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM avaliacao WHERE id_evento=?", Integer.class, evento)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings={"SUSPENSO", "BANIDO"})
    void statusAtualValeParaTokenAnterior(String situacao) throws Exception {
        var org = cadastrar("ContaOrg");
        var pessoa = cadastrar("ContaPessoa");
        UUID evento = criarEventoValido(org, 10);
        UUID outro = criarEventoValido(org, 10);
        alternarInscricao(pessoa, evento).andExpect(status().isOk());
        jdbcTemplate.update("UPDATE usuario SET status_usuario=? WHERE id_usuario=?", situacao, pessoa.id());
        String token = bearer(pessoa.token());
        boolean banido = situacao.equals("BANIDO");
        mockMvc.perform(post("/eventos/{id}/inscricao", outro).header("Authorization", token))
            .andExpect(status().is(banido ? 401 : 403));
        mockMvc.perform(post("/eventos/{id}/comentarios", evento).header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Não pode comentar\"}"))
            .andExpect(status().is(banido ? 401 : 403));
        criarEvento(pessoa.token(), "Conta impedida de criar", 10).andExpect(status().is(banido ? 401 : 403));
        alternarInscricao(pessoa, evento).andExpect(status().is(banido ? 401 : 200));
    }

    @Test
    void organizadorSuspensoPodeRegistrarPresencaEConcluir() throws Exception {
        var org = cadastrar("SuspensoOrg");
        var pessoa = cadastrar("SuspensoParticipante");
        UUID evento = criarEventoValido(org, 10);
        alternarInscricao(pessoa, evento).andExpect(status().isOk());
        jdbcTemplate.update("UPDATE usuario SET status_usuario='SUSPENSO' WHERE id_usuario=?", org.id());
        moverParaOPassado(evento);
        registrarPresenca(org, evento, pessoa).andExpect(status().isNoContent());
        concluir(org, evento).andExpect(status().isOk());
        mockMvc.perform(post("/eventos/{id}/avaliacoes", evento).header("Authorization", bearer(org.token()))
            .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("avaliadoId", pessoa.id(), "nota", 5))))
            .andExpect(status().isForbidden());
    }

    @Test
    void presencaEConclusaoConcorrentesMantemAusenciaCoerente() throws Exception {
        var org = cadastrar("PresencaConcOrg");
        var pessoa = cadastrar("PresencaConcPessoa");
        UUID evento = criarEventoValido(org, 10);
        alternarInscricao(pessoa, evento).andExpect(status().isOk());
        moverParaOPassado(evento);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            Future<Integer> presenca = executor.submit(() -> { largada.await(); return registrarPresenca(org, evento, pessoa).andReturn().getResponse().getStatus(); });
            Future<Integer> conclusao = executor.submit(() -> { largada.await(); return concluir(org, evento).andReturn().getResponse().getStatus(); });
            largada.countDown();
            assertThat(conclusao.get(30, TimeUnit.SECONDS)).isEqualTo(200);
            int resposta = presenca.get(30, TimeUnit.SECONDS);
            assertThat(resposta).isIn(204, 409);
            boolean compareceu = jdbcTemplate.queryForObject("SELECT compareceu FROM inscricao WHERE id_usuario=? AND id_evento=?", Boolean.class, pessoa.id(), evento);
            assertThat(compareceu).isEqualTo(resposta == 204);
            assertThat(usuarioAtual(pessoa).get("honor").asDouble()).isEqualTo(compareceu ? 3.0 : 2.5);
        } finally { executor.shutdownNow(); }
    }

    @Test
    void organizadoresQueParticipamUmDoOutroConcluemSemInverterTravas() throws Exception {
        var alfa = cadastrar("CruzadoAlfa");
        var beta = cadastrar("CruzadoBeta");
        UUID primeiro = criarEventoValido(alfa, 10);
        UUID segundo = criarEventoValido(beta, 10);
        alternarInscricao(beta, primeiro).andExpect(status().isOk());
        alternarInscricao(alfa, segundo).andExpect(status().isOk());
        moverParaOPassado(primeiro);
        moverParaOPassado(segundo);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            Future<Integer> um = executor.submit(() -> { largada.await(); return concluir(alfa, primeiro).andReturn().getResponse().getStatus(); });
            Future<Integer> dois = executor.submit(() -> { largada.await(); return concluir(beta, segundo).andReturn().getResponse().getStatus(); });
            largada.countDown();
            assertThat(um.get(30, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(dois.get(30, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(usuarioAtual(alfa).get("honor").asDouble()).isEqualTo(2.5);
            assertThat(usuarioAtual(beta).get("honor").asDouble()).isEqualTo(2.5);
        } finally { executor.shutdownNow(); }
    }

    private void aproximarInicio(UUID evento) {
        jdbcTemplate.update("UPDATE evento SET data_hora_realizacao=now()+interval '12 hours' WHERE id_evento=?", evento);
    }
}
