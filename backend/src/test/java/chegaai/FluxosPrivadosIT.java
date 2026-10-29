package chegaai;

import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FluxosPrivadosIT extends IntegracaoBase {
    @Test
    @DisplayName("rotas privadas exigem login e participantes so sao visiveis ao organizador")
    void autenticacaoEAutorizacaoDaListaPrivada() throws Exception {
        var org = cadastrar("PrivadoOrg");
        var outro = cadastrar("PrivadoOutro");
        UUID evento = criarEventoValido(org, 10);
        mockMvc.perform(get("/auth/me/inscricoes")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/auth/me/avaliacoes-pendentes")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/eventos/{id}/participantes", evento)).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/eventos/{id}/presencas/{pessoa}", evento, outro.id())
            .contentType(MediaType.APPLICATION_JSON).content("{\"presente\":true}"))
            .andExpect(status().isUnauthorized());
        participantes(outro, evento).andExpect(status().isForbidden());
        String moderador = login("moderador@chegaai.dev", "admin123");
        mockMvc.perform(get("/eventos/{id}/participantes", evento).header("Authorization", bearer(moderador)))
            .andExpect(status().isForbidden());
        participantes(org, evento).andExpect(status().isOk());
        mockMvc.perform(get("/eventos/{id}", evento)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("inscricoes pessoais usam UUID mesmo entre homonimos e nao revelam dados alheios")
    void inscricoesPessoaisNaoConfundemHomonimos() throws Exception {
        var org = cadastrar("IdOrg");
        var completo = cadastrar("IdCompleto");
        var alfa = cadastrar("IdAlfa");
        var beta = cadastrar("IdBeta");
        var cancelado = cadastrar("IdCancelado");
        jdbcTemplate.update("UPDATE usuario SET nome='Alex Silva' WHERE id_usuario IN (?,?)", alfa.id(), beta.id());
        UUID evento = criarEventoValido(org, 3);
        UUID outroEvento = criarEventoValido(org, 10);
        alternarInscricao(completo, evento).andExpect(status().isOk());
        alternarInscricao(alfa, evento).andExpect(jsonPath("$.status").value("CONFIRMADO"));
        alternarInscricao(beta, evento).andExpect(jsonPath("$.status").value("FILA"));
        alternarInscricao(cancelado, outroEvento).andExpect(status().isOk());
        alternarInscricao(cancelado, outroEvento).andExpect(status().isOk());
        minhas(alfa).andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].eventoId").value(evento.toString()))
            .andExpect(jsonPath("$[0].status").value("CONFIRMADO"))
            .andExpect(jsonPath("$[0].compareceu").value(false));
        JsonNode fila = json(minhas(beta).andExpect(status().isOk()));
        assertThat(fila.get(0).get("status").asText()).isEqualTo("FILA");
        assertThat(fila.get(0).size()).isEqualTo(3);
        mockMvc.perform(get("/auth/me/inscricoes").param("usuarioId", alfa.id().toString())
            .header("Authorization", bearer(beta.token())))
            .andExpect(jsonPath("$[0].status").value("FILA"));
        minhas(cancelado).andExpect(jsonPath("$.length()").value(0));
        moverParaOPassado(evento);
        concluir(org, evento).andExpect(status().isOk());
        minhas(alfa).andExpect(jsonPath("$[0].status").value("AUSENTE"))
            .andExpect(jsonPath("$[0].compareceu").value(false));
        minhas(beta).andExpect(jsonPath("$[0].status").value("FILA"));
    }

    @Test
    @DisplayName("pendencias distinguem IDs homonimos e a mesma lista bloqueia novas inscricoes")
    void pendenciasPorIdESemContagensDivergentes() throws Exception {
        var org = cadastrar("PendenciaOrg");
        var alfa = cadastrar("PendenciaAlfa");
        var beta = cadastrar("PendenciaBeta");
        var ausente = cadastrar("PendenciaAusente");
        var fila = cadastrar("PendenciaFila");
        jdbcTemplate.update("UPDATE usuario SET nome='Alex Silva' WHERE id_usuario IN (?,?)", alfa.id(), beta.id());
        UUID evento = criarEventoValido(org, 4);
        UUID proximo = criarEventoValido(cadastrar("PendenciaProximo"), 10);
        for (var pessoa : List.of(alfa, beta, ausente, fila)) alternarInscricao(pessoa, evento).andExpect(status().isOk());
        moverParaOPassado(evento);
        presenca(org, evento, alfa.id(), true).andExpect(status().isNoContent());
        presenca(org, evento, beta.id(), true).andExpect(status().isNoContent());
        concluir(org, evento).andExpect(status().isOk());
        // Historico legado pode conter uma avaliacao cujo alvo hoje e AUSENTE. Ela nao quita outro UUID.
        jdbcTemplate.update("INSERT INTO avaliacao (id_evento,id_avaliador,id_avaliado,nota) VALUES (?,?,?,4)", evento, org.id(), ausente.id());
        JsonNode pendente = json(pendencias(org).andExpect(status().isOk())
            .andExpect(jsonPath("$.podeAvaliar").value(true))
            .andExpect(jsonPath("$.eventos[0].eventoId").value(evento.toString()))
            .andExpect(jsonPath("$.eventos[0].data", matchesPattern("[0-9]{4}-[0-9]{2}-[0-9]{2}")))
            .andExpect(jsonPath("$.eventos[0].hora", matchesPattern("[0-9]{2}:[0-9]{2}"))));
        assertThat(ids(pendente.at("/eventos/0/participantes"))).containsExactly(alfa.id().toString(), beta.id().toString());
        assertThat(pendente.at("/eventos/0/participantes/0/nome").asText()).isEqualTo("Alex Silva");
        assertThat(pendente.at("/eventos/0/participantes/0").size()).isEqualTo(2);
        alternarInscricao(org, proximo).andExpect(status().isConflict());
        avaliar(org, evento, alfa, 5).andExpect(status().isCreated());
        avaliar(org, evento, alfa, 1).andExpect(status().isConflict());
        JsonNode umaRestante = json(pendencias(org).andExpect(status().isOk()));
        assertThat(ids(umaRestante.at("/eventos/0/participantes"))).containsExactly(beta.id().toString());
        alternarInscricao(org, proximo).andExpect(status().isConflict());
        jdbcTemplate.update("UPDATE usuario SET status_usuario='SUSPENSO' WHERE id_usuario=?", org.id());
        pendencias(org).andExpect(status().isOk()).andExpect(jsonPath("$.podeAvaliar").value(false))
            .andExpect(jsonPath("$.eventos[0].participantes[0].id").value(beta.id().toString()));
        avaliar(org, evento, beta, 5).andExpect(status().isForbidden());
        jdbcTemplate.update("UPDATE usuario SET status_usuario='ATIVO' WHERE id_usuario=?", org.id());
        avaliar(org, evento, beta, 5).andExpect(status().isCreated());
        pendencias(org).andExpect(jsonPath("$.eventos.length()").value(0));
        alternarInscricao(org, proximo).andExpect(jsonPath("$.status").value("CONFIRMADO"));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM avaliacao WHERE id_evento=? AND id_avaliador=?", Long.class, evento, org.id()))
            .isEqualTo(3);
    }

    @Test
    @DisplayName("preview minimo mostra apenas confirmados e ausencia historica; organizador aparece presente")
    void previewNaoExpoeFilaNemDadosPessoais() throws Exception {
        var org = cadastrar("PreviewOrg");
        var alfa = cadastrar("PreviewAlfa");
        var beta = cadastrar("PreviewBeta");
        var fila = cadastrar("PreviewFila");
        UUID evento = criarEventoValido(org, 3);
        alternarInscricao(alfa, evento).andExpect(status().isOk());
        alternarInscricao(beta, evento).andExpect(status().isOk());
        alternarInscricao(fila, evento).andExpect(status().isOk());
        JsonNode futuro = json(participantes(org, evento).andExpect(status().isOk())
            .andExpect(jsonPath("$.podeMarcarPresenca").value(false))
            .andExpect(jsonPath("$.podeConcluir").value(false))
            .andExpect(jsonPath("$.descontoPorAusencia").value(0.5)));
        OffsetDateTime.parse(futuro.get("inicio").asText());
        assertThat(futuro.size()).isEqualTo(7);
        assertThat(ids(futuro.get("participantes"))).containsExactly(org.id().toString(), alfa.id().toString(), beta.id().toString());
        for (var pessoa : futuro.get("participantes")) {
            assertThat(pessoa.size()).isEqualTo(5);
            assertThat(pessoa.get("status").asText()).isEqualTo("CONFIRMADA");
        }
        assertThat(futuro.at("/participantes/0/organizador").asBoolean()).isTrue();
        assertThat(futuro.at("/participantes/0/compareceu").asBoolean()).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT compareceu FROM inscricao WHERE id_evento=? AND id_usuario=?", Boolean.class, evento, org.id()))
            .isFalse();
        assertThat(futuro.toString()).doesNotContain("\"email\"", "\"cpf\"", "\"honor\"", "\"honra\"", "\"senha\"");
        moverParaOPassado(evento);
        presenca(org, evento, alfa.id(), true).andExpect(status().isNoContent());
        concluir(org, evento).andExpect(status().isOk());
        participantes(org, evento).andExpect(jsonPath("$.status").value("CONCLUIDO"))
            .andExpect(jsonPath("$.podeMarcarPresenca").value(false))
            .andExpect(jsonPath("$.podeConcluir").value(false))
            .andExpect(jsonPath("$.participantes[2].status").value("AUSENTE"));
    }

    @Test
    @DisplayName("presenca so muda apos inicio, e reversivel e fica congelada na conclusao")
    void presencaReversivelApenasNaJanelaPermitida() throws Exception {
        var org = cadastrar("JanelaOrg");
        var pessoa = cadastrar("JanelaPessoa");
        var fora = cadastrar("JanelaFora");
        UUID evento = criarEventoValido(org, 10);
        alternarInscricao(pessoa, evento).andExpect(status().isOk());
        presenca(org, evento, pessoa.id(), true).andExpect(status().isConflict());
        registrarPresenca(org, evento, pessoa).andExpect(status().isConflict());
        moverParaOPassado(evento);
        presenca(pessoa, evento, pessoa.id(), true).andExpect(status().isForbidden());
        presenca(org, evento, fora.id(), true).andExpect(status().isNotFound());
        mockMvc.perform(put("/eventos/{id}/presencas/{pessoa}", evento, pessoa.id())
            .header("Authorization", bearer(org.token())).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
        presenca(org, evento, org.id(), false).andExpect(status().isConflict());
        presenca(org, evento, pessoa.id(), true).andExpect(status().isNoContent());
        presenca(org, evento, pessoa.id(), true).andExpect(status().isNoContent());
        assertCompareceu(evento, pessoa.id(), true);
        presenca(org, evento, pessoa.id(), false).andExpect(status().isNoContent());
        assertCompareceu(evento, pessoa.id(), false);
        registrarPresenca(org, evento, pessoa).andExpect(status().isNoContent());
        assertCompareceu(evento, pessoa.id(), true);
        presenca(org, evento, pessoa.id(), false).andExpect(status().isNoContent());
        concluir(org, evento).andExpect(status().isOk());
        presenca(org, evento, pessoa.id(), true).andExpect(status().isConflict());
        presenca(org, evento, pessoa.id(), false).andExpect(status().isConflict());
        registrarPresenca(org, evento, pessoa).andExpect(status().isConflict());
        assertCompareceu(evento, pessoa.id(), false);
        assertThat(usuarioAtual(pessoa).get("honor").asDouble()).isEqualTo(2.5);
        assertThat(jdbcTemplate.queryForObject("SELECT status_inscricao FROM inscricao WHERE id_evento=? AND id_usuario=?", String.class, evento, pessoa.id()))
            .isEqualTo("AUSENTE");
    }

    @Test
    @DisplayName("organizador suspenso pode gerir presenca e concluir sem liberar avaliacao")
    void suspensoPodeGerirCompromissoProprio() throws Exception {
        var org = cadastrar("SuspensoFluxoOrg");
        var pessoa = cadastrar("SuspensoFluxoPessoa");
        UUID evento = criarEventoValido(org, 10);
        alternarInscricao(pessoa, evento).andExpect(status().isOk());
        moverParaOPassado(evento);
        jdbcTemplate.update("UPDATE usuario SET status_usuario='SUSPENSO' WHERE id_usuario=?", org.id());
        participantes(org, evento).andExpect(status().isOk()).andExpect(jsonPath("$.podeMarcarPresenca").value(true))
            .andExpect(jsonPath("$.podeConcluir").value(true));
        presenca(org, evento, pessoa.id(), true).andExpect(status().isNoContent());
        presenca(org, evento, pessoa.id(), false).andExpect(status().isNoContent());
        presenca(org, evento, pessoa.id(), true).andExpect(status().isNoContent());
        concluir(org, evento).andExpect(status().isOk());
        pendencias(org).andExpect(status().isOk()).andExpect(jsonPath("$.podeAvaliar").value(false));
        avaliar(org, evento, pessoa, 5).andExpect(status().isForbidden());
        minhas(org).andExpect(status().isOk()).andExpect(jsonPath("$[0].compareceu").value(true));
    }

    @Test
    @DisplayName("banimento invalida token anterior tambem nas novas rotas privadas")
    void banidoNaoLeNemGerePelasNovasRotas() throws Exception {
        var org = cadastrar("BanidoFluxoOrg");
        var pessoa = cadastrar("BanidoFluxoPessoa");
        UUID evento = criarEventoValido(org, 10);
        alternarInscricao(pessoa, evento).andExpect(status().isOk());
        moverParaOPassado(evento);
        jdbcTemplate.update("UPDATE usuario SET status_usuario='BANIDO' WHERE id_usuario=?", org.id());
        minhas(org).andExpect(status().isUnauthorized());
        pendencias(org).andExpect(status().isUnauthorized());
        participantes(org, evento).andExpect(status().isUnauthorized());
        presenca(org, evento, pessoa.id(), true).andExpect(status().isUnauthorized());
        registrarPresenca(org, evento, pessoa).andExpect(status().isUnauthorized());
        concluir(org, evento).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("organizador nao sai pelo toggle nem libera sua vaga para a fila")
    void organizadorPermaneceInscrito() throws Exception {
        var org = cadastrar("RetidoOrg");
        var a = cadastrar("RetidoA");
        var b = cadastrar("RetidoB");
        var fila = cadastrar("RetidoFila");
        UUID evento = criarEventoValido(org, 3);
        alternarInscricao(a, evento).andExpect(status().isOk());
        alternarInscricao(b, evento).andExpect(status().isOk());
        alternarInscricao(fila, evento).andExpect(status().isOk());
        alternarInscricao(org, evento).andExpect(status().isConflict())
            .andExpect(jsonPath("$.message", containsString("organizador")));
        assertThat(jdbcTemplate.queryForObject("SELECT status_inscricao FROM inscricao WHERE id_evento=? AND id_usuario=?", String.class, evento, org.id()))
            .isEqualTo("CONFIRMADA");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inscricao WHERE id_evento=? AND status_inscricao='CONFIRMADA'", Long.class, evento))
            .isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("SELECT status_inscricao FROM inscricao WHERE id_evento=? AND id_usuario=?", String.class, evento, fila.id()))
            .isEqualTo("SOLICITADA");
    }

    @Test
    @DisplayName("organizador legado cancelado nao e inventado no preview nem reativado na conclusao")
    void legadoCanceladoDoOrganizadorPermaneceIntacto() throws Exception {
        var org = cadastrar("LegadoFluxoOrg");
        var pessoa = cadastrar("LegadoFluxoPessoa");
        UUID evento = criarEventoValido(org, 10);
        alternarInscricao(pessoa, evento).andExpect(status().isOk());
        jdbcTemplate.update("UPDATE inscricao SET status_inscricao='CANCELADA' WHERE id_evento=? AND id_usuario=?", evento, org.id());
        JsonNode antes = json(participantes(org, evento).andExpect(status().isOk()));
        assertThat(ids(antes.get("participantes"))).containsExactly(pessoa.id().toString());
        alternarInscricao(org, evento).andExpect(status().isConflict());
        moverParaOPassado(evento);
        participantes(org, evento).andExpect(status().isOk()).andExpect(jsonPath("$.podeConcluir").value(true));
        concluir(org, evento).andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("SELECT status_inscricao FROM inscricao WHERE id_evento=? AND id_usuario=?", String.class, evento, org.id()))
            .isEqualTo("CANCELADA");
        assertThat(ids(json(participantes(org, evento).andExpect(status().isOk())).get("participantes")))
            .containsExactly(pessoa.id().toString());
    }

    @Test
    @DisplayName("desmarcar presenca e concluir em paralelo nao alteram historico depois da conclusao")
    void desmarcacaoEConclusaoConcorrentes() throws Exception {
        var org = cadastrar("PutConcOrg");
        var pessoa = cadastrar("PutConcPessoa");
        UUID evento = criarEventoValido(org, 10);
        alternarInscricao(pessoa, evento).andExpect(status().isOk());
        moverParaOPassado(evento);
        presenca(org, evento, pessoa.id(), true).andExpect(status().isNoContent());
        var executor = Executors.newFixedThreadPool(2);
        var largada = new CountDownLatch(1);
        try {
            var desmarcar = executor.submit(() -> { largada.await(); return presenca(org, evento, pessoa.id(), false).andReturn().getResponse().getStatus(); });
            var concluir = executor.submit(() -> { largada.await(); return concluir(org, evento).andReturn().getResponse().getStatus(); });
            largada.countDown();
            assertThat(concluir.get(30, TimeUnit.SECONDS)).isEqualTo(200);
            int resposta = desmarcar.get(30, TimeUnit.SECONDS);
            assertThat(resposta).isIn(204, 409);
            boolean compareceu = resposta == 409;
            assertCompareceu(evento, pessoa.id(), compareceu);
            assertThat(usuarioAtual(pessoa).get("honor").asDouble()).isEqualTo(compareceu ? 3.0 : 2.5);
            presenca(org, evento, pessoa.id(), !compareceu).andExpect(status().isConflict());
            assertCompareceu(evento, pessoa.id(), compareceu);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("cenario de demonstracao concluido de avaliacao pendente tem data passada")
    void demonstracaoPendenteJaAconteceu() {
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM evento WHERE titulo LIKE 'Encontro Passado%' " +
            "AND status_evento='CONCLUIDO' AND data_hora_realizacao < now()", Long.class)).isGreaterThanOrEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM evento WHERE titulo LIKE 'Encontro Passado%' " +
            "AND status_evento='CONCLUIDO' AND data_hora_realizacao >= now()", Long.class)).isZero();
    }

    @Test
    @DisplayName("fila e inscricao cancelada nao permitem marcar nem desmarcar presenca")
    void apenasConfirmadosPodemTerPresencaAlterada() throws Exception {
        var org = cadastrar("StatusPresencaOrg");
        var a = cadastrar("StatusPresencaA");
        var b = cadastrar("StatusPresencaB");
        var fila = cadastrar("StatusPresencaFila");
        var cancelado = cadastrar("StatusPresencaCancelado");
        UUID evento = criarEventoValido(org, 3);
        for (var pessoa : List.of(a,b,fila,cancelado)) alternarInscricao(pessoa, evento).andExpect(status().isOk());
        alternarInscricao(cancelado, evento).andExpect(status().isOk());
        moverParaOPassado(evento);
        for (var pessoa : List.of(fila,cancelado)) {
            presenca(org, evento, pessoa.id(), true).andExpect(status().isConflict());
            presenca(org, evento, pessoa.id(), false).andExpect(status().isConflict());
            assertCompareceu(evento,pessoa.id(),false);
        }
        assertThat(jdbcTemplate.queryForObject("SELECT status_inscricao FROM inscricao WHERE id_evento=? AND id_usuario=?", String.class,evento,fila.id()))
            .isEqualTo("SOLICITADA");
        assertThat(jdbcTemplate.queryForObject("SELECT status_inscricao FROM inscricao WHERE id_evento=? AND id_usuario=?", String.class,evento,cancelado.id()))
            .isEqualTo("CANCELADA");
    }

    @Test
    @DisplayName("evento cancelado continua consultavel pelo dono mas nao aceita presenca nem conclusao")
    void previewCanceladoNaoAutorizaMutacoes() throws Exception {
        var org = cadastrar("CanceladoPreviewOrg");
        var pessoa = cadastrar("CanceladoPreviewPessoa");
        UUID evento = criarEventoValido(org,10);
        alternarInscricao(pessoa,evento).andExpect(status().isOk());
        moverParaOPassado(evento);
        jdbcTemplate.update("UPDATE evento SET status_evento='CANCELADO' WHERE id_evento=?",evento);
        participantes(org,evento).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELADO"))
            .andExpect(jsonPath("$.podeMarcarPresenca").value(false))
            .andExpect(jsonPath("$.podeConcluir").value(false));
        presenca(org,evento,pessoa.id(),true).andExpect(status().isConflict());
        registrarPresenca(org,evento,pessoa).andExpect(status().isConflict());
        concluir(org,evento).andExpect(status().isConflict());
        assertCompareceu(evento,pessoa.id(),false);
    }

    private ResultActions minhas(UsuarioTeste pessoa) throws Exception {
        return mockMvc.perform(get("/auth/me/inscricoes").header("Authorization", bearer(pessoa.token())));
    }
    private ResultActions pendencias(UsuarioTeste pessoa) throws Exception {
        return mockMvc.perform(get("/auth/me/avaliacoes-pendentes").header("Authorization", bearer(pessoa.token())));
    }
    private ResultActions participantes(UsuarioTeste pessoa, UUID evento) throws Exception {
        return mockMvc.perform(get("/eventos/{id}/participantes", evento).header("Authorization", bearer(pessoa.token())));
    }
    private ResultActions presenca(UsuarioTeste org, UUID evento, UUID pessoa, boolean presente) throws Exception {
        return mockMvc.perform(put("/eventos/{id}/presencas/{pessoa}", evento, pessoa)
            .header("Authorization", bearer(org.token())).contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("presente", presente))));
    }
    private ResultActions avaliar(UsuarioTeste pessoa, UUID evento, UsuarioTeste alvo, int nota) throws Exception {
        return mockMvc.perform(post("/eventos/{id}/avaliacoes", evento).header("Authorization", bearer(pessoa.token()))
            .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("avaliadoId", alvo.id(), "nota", nota))));
    }
    private void assertCompareceu(UUID evento, UUID pessoa, boolean valor) {
        assertThat(jdbcTemplate.queryForObject("SELECT compareceu FROM inscricao WHERE id_evento=? AND id_usuario=?", Boolean.class, evento, pessoa))
            .isEqualTo(valor);
    }
    private static List<String> ids(JsonNode pessoas) {
        List<String> ids = new ArrayList<>();
        pessoas.forEach(pessoa -> ids.add(pessoa.get("id").asText()));
        return ids;
    }
}