package chegaai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HonraPersistenteIT extends IntegracaoBase {
    @Test
    @DisplayName("quórum é de notas recebidas por pessoa entre rolés; uma ou duas não substituem a base")
    void quorumEntreEventos() throws Exception {
        UsuarioTeste alvo = cadastrar("Alvo");
        List<UsuarioTeste> avaliadores = List.of(cadastrar("NotaUm"), cadastrar("NotaDois"), cadastrar("NotaTres"));
        List<UUID> eventos = new ArrayList<>();
        for (UsuarioTeste avaliador : avaliadores) {
            eventos.add(eventoComPresenca(avaliador, alvo));
        }
        for (UUID evento : eventos) concluirComoOrganizador(evento);
        avaliar(avaliadores.get(0), eventos.get(0), alvo, 1).andExpect(status().isCreated());
        assertHonra(alvo, "3.00", "0.00");
        avaliar(avaliadores.get(1), eventos.get(1), alvo, 2).andExpect(status().isCreated());
        assertHonra(alvo, "3.00", "0.00");
        avaliar(avaliadores.get(2), eventos.get(2), alvo, 5).andExpect(status().isCreated());
        assertHonra(alvo, "2.67", "0.00");
    }

    @Test
    @DisplayName("ausência antes ou depois das notas tem o mesmo desconto acumulado")
    void ordemNaoApagaPenalidade() throws Exception {
        UsuarioTeste antes = cadastrar("Antes");
        UsuarioTeste depois = cadastrar("Depois");
        UsuarioTeste avaliador = cadastrar("Avaliador");
        UsuarioTeste segundo = cadastrar("Segundo");
        UsuarioTeste terceiro = cadastrar("Terceiro");
        UUID avaliacoesAntes = eventoComPresenca(avaliador, antes, segundo, terceiro);
        UUID avaliacoesDepois = eventoComPresenca(avaliador, depois, segundo, terceiro);
        UUID ausenciaAntes = eventoComAusenciaPendente(cadastrar("OrgAntes"), antes);
        UUID ausenciaDepois = eventoComAusenciaPendente(cadastrar("OrgDepois"), depois);
        concluirComoOrganizador(avaliacoesAntes);
        concluirComoOrganizador(avaliacoesDepois);
        concluirComoOrganizador(ausenciaAntes);
        assertHonra(antes, "2.50", "0.50");
        for (UsuarioTeste autor : List.of(avaliador, segundo, terceiro)) {
            avaliar(autor, avaliacoesAntes, antes, 5).andExpect(status().isCreated());
            avaliar(autor, avaliacoesDepois, depois, 5).andExpect(status().isCreated());
        }
        concluirComoOrganizador(ausenciaDepois);
        assertHonra(antes, "4.50", "0.50");
        assertHonra(depois, "4.50", "0.50");
    }

    @Test
    @DisplayName("descontos continuam acumulando com piso zero; concluir de novo não duplica")
    void acumulaSemNegativarNemDuplicar() throws Exception {
        UsuarioTeste alvo = cadastrar("Faltou");
        List<UUID> eventos = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            eventos.add(eventoComAusenciaPendente(cadastrar("OrgFalta"), alvo));
        }
        for (UUID evento : eventos) {
            concluirComoOrganizador(evento);
        }
        assertHonra(alvo, "0.00", "3.50");
        UUID ultimo = eventos.get(6);
        concluirComoOrganizador(ultimo, 409);
        assertHonra(alvo, "0.00", "3.50");
    }

    @Test
    @DisplayName("avaliações simultâneas em rolés diferentes enxergam o quórum e todas as notas")
    void avaliacoesSimultaneas() throws Exception {
        UsuarioTeste alvo = cadastrar("AlvoConc");
        UsuarioTeste um = cadastrar("UmConc");
        UsuarioTeste dois = cadastrar("DoisConc");
        UsuarioTeste tres = cadastrar("TresConc");
        UUID primeiro = eventoComPresenca(um, alvo);
        UUID segundo = eventoComPresenca(dois, alvo);
        UUID terceiro = eventoComPresenca(tres, alvo);
        concluirComoOrganizador(primeiro);
        concluirComoOrganizador(segundo);
        concluirComoOrganizador(terceiro);
        avaliar(um, primeiro, alvo, 5).andExpect(status().isCreated());
        paralelo(
            () -> avaliar(dois, segundo, alvo, 5).andReturn().getResponse(),
            () -> avaliar(tres, terceiro, alvo, 2).andReturn().getResponse()
        ).forEach(resposta -> assertThat(resposta.getStatus()).isEqualTo(201));
        assertHonra(alvo, "4.00", "0.00");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM avaliacao WHERE id_avaliado = ?",
            Long.class, alvo.id())).isEqualTo(3);
    }

    @Test
    @DisplayName("conclusões simultâneas em rolés diferentes acumulam ambos os descontos")
    void conclusoesSimultaneas() throws Exception {
        UsuarioTeste alvo = cadastrar("FaltaConc");
        UsuarioTeste orgUm = cadastrar("OrgUmConc");
        UsuarioTeste orgDois = cadastrar("OrgDoisConc");
        UUID um = eventoComAusenciaPendente(orgUm, alvo);
        UUID dois = eventoComAusenciaPendente(orgDois, alvo);
        paralelo(
            () -> concluir(orgUm, um).andReturn().getResponse(),
            () -> concluir(orgDois, dois).andReturn().getResponse()
        ).forEach(resposta -> assertThat(resposta.getStatus()).isEqualTo(200));
        assertHonra(alvo, "2.00", "1.00");
    }

    @Test
    @DisplayName("avaliação simultânea à conclusão preserva média e penalidade")
    void conclusaoEAvaliacaoSimultaneas() throws Exception {
        UsuarioTeste alvo = cadastrar("AlvoMisto");
        UsuarioTeste orgFalta = cadastrar("OrgMisto");
        UsuarioTeste um = cadastrar("UmMisto");
        UsuarioTeste dois = cadastrar("DoisMisto");
        UsuarioTeste tres = cadastrar("TresMisto");
        UUID primeiro = eventoComPresenca(um, alvo);
        UUID segundo = eventoComPresenca(dois, alvo);
        UUID terceiro = eventoComPresenca(tres, alvo);
        UUID falta = eventoComAusenciaPendente(orgFalta, alvo);
        concluirComoOrganizador(primeiro);
        concluirComoOrganizador(segundo);
        concluirComoOrganizador(terceiro);
        avaliar(um, primeiro, alvo, 5).andExpect(status().isCreated());
        avaliar(dois, segundo, alvo, 5).andExpect(status().isCreated());
        List<MockHttpServletResponse> respostas = paralelo(
            () -> concluir(orgFalta, falta).andReturn().getResponse(),
            () -> avaliar(tres, terceiro, alvo, 2).andReturn().getResponse()
        );
        assertThat(respostas.get(0).getStatus()).isEqualTo(200);
        assertThat(respostas.get(1).getStatus()).isEqualTo(201);
        assertHonra(alvo, "3.50", "0.50");
    }

    @Test
    @DisplayName("avaliações cruzadas em eventos diferentes usam a mesma ordem de usuários sem deadlock")
    void avaliacoesCruzadas() throws Exception {
        UsuarioTeste alfa = cadastrar("AlfaCruzada");
        UsuarioTeste bravo = cadastrar("BravoCruzada");
        UUID um = eventoComPresenca(alfa, bravo);
        UUID dois = eventoComPresenca(bravo, alfa);
        concluirComoOrganizador(um);
        concluirComoOrganizador(dois);
        paralelo(
            () -> avaliar(alfa, um, bravo, 5).andReturn().getResponse(),
            () -> avaliar(bravo, dois, alfa, 4).andReturn().getResponse()
        ).forEach(resposta -> assertThat(resposta.getStatus()).isEqualTo(201));
        assertHonra(alfa, "3.00", "0.00");
        assertHonra(bravo, "3.00", "0.00");
    }

    private UUID eventoComPresenca(UsuarioTeste organizador, UsuarioTeste... participantes) throws Exception {
        UUID evento = criarEventoValido(organizador, 10);
        for (UsuarioTeste participante : participantes) {
            alternarInscricao(participante, evento).andExpect(status().isOk());
            registrarPresenca(organizador, evento, participante).andExpect(status().isNoContent());
        }
        return evento;
    }

    private UUID eventoComAusenciaPendente(UsuarioTeste organizador, UsuarioTeste participante) throws Exception {
        UUID evento = criarEventoValido(organizador, 10);
        alternarInscricao(participante, evento).andExpect(status().isOk());
        moverParaOPassado(evento);
        return evento;
    }

    private void concluirComoOrganizador(UUID evento) throws Exception {
        concluirComoOrganizador(evento, 200);
    }

    private void concluirComoOrganizador(UUID evento, int statusEsperado) throws Exception {
        if (statusEsperado == 200) moverParaOPassado(evento);
        String email = jdbcTemplate.queryForObject("SELECT u.email FROM usuario u JOIN evento e " +
            "ON e.id_organizador = u.id_usuario WHERE e.id_evento = ?", String.class, evento);
        String token = login(email, SENHA);
        mockMvc.perform(post("/eventos/{id}/concluir", evento).header("Authorization", bearer(token)))
            .andExpect(status().is(statusEsperado));
    }

    private ResultActions avaliar(UsuarioTeste avaliador, UUID evento, UsuarioTeste avaliado, int nota) throws Exception {
        return mockMvc.perform(post("/eventos/{id}/avaliacoes", evento)
            .header("Authorization", bearer(avaliador.token())).contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("avaliadoId", avaliado.id(), "nota", nota))));
    }

    private void assertHonra(UsuarioTeste usuario, String honra, String desconto) throws Exception {
        assertThat(usuarioAtual(usuario).get("honor").decimalValue()).isEqualByComparingTo(honra);
        assertThat(jdbcTemplate.queryForObject("SELECT desconto_honra_acumulado FROM usuario WHERE id_usuario = ?",
            BigDecimal.class, usuario.id())).isEqualByComparingTo(desconto);
    }

    @SafeVarargs
    private final List<MockHttpServletResponse> paralelo(Callable<MockHttpServletResponse>... chamadas) throws Exception {
        var executor = Executors.newFixedThreadPool(chamadas.length);
        CountDownLatch prontos = new CountDownLatch(chamadas.length);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<MockHttpServletResponse>> futuros = new ArrayList<>();
            for (var chamada : chamadas) {
                futuros.add(executor.submit(() -> {
                    prontos.countDown();
                    if (!largada.await(10, TimeUnit.SECONDS)) throw new AssertionError("Sem largada");
                    return chamada.call();
                }));
            }
            assertThat(prontos.await(10, TimeUnit.SECONDS)).isTrue();
            largada.countDown();
            List<MockHttpServletResponse> respostas = new ArrayList<>();
            for (var futuro : futuros) respostas.add(futuro.get(30, TimeUnit.SECONDS));
            return respostas;
        } finally {
            executor.shutdownNow();
        }
    }
}
