package chegaai;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
@ExtendWith(OutputCaptureExtension.class)
class AvaliacaoIT extends IntegracaoBase {
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Test
    @DisplayName("quem não teve presença registrada não avalia, não é avaliado e não fica com pendência")
    void avaliacaoExigePresenca() throws Exception {
        UsuarioTeste organizador = cadastrar("Org");
        UsuarioTeste presente = cadastrar("Presente");
        UsuarioTeste semPresenca = cadastrar("SemPresenca");
        UUID eventoId = criarEventoValido(organizador, 10);
        alternarInscricao(presente, eventoId).andExpect(status().isOk());
        alternarInscricao(semPresenca, eventoId).andExpect(status().isOk());
        moverParaOPassado(eventoId);
        registrarPresenca(organizador, eventoId, presente).andExpect(status().isNoContent());
        // Como no seed: o rolé fica concluído sem passar pela conclusão, que marcaria a ausência.
        jdbcTemplate.update("UPDATE evento SET status_evento = 'CONCLUIDO' WHERE id_evento = ?", eventoId);
        avaliar(semPresenca, eventoId, presente, 5)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message", containsString("Apenas quem esteve presente")));
        avaliar(presente, eventoId, semPresenca, 5)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message", containsString("Só é possível avaliar quem esteve presente")));
        assertThat(pendencias(semPresenca).get("eventos").size()).isZero();
        assertThat(pendencias(presente).get("eventos").size()).isZero();
    }
    @Test
    @DisplayName("as avaliações pendentes custam as mesmas consultas com um ou três rolés no histórico")
    void pendenciasNaoCrescemComOHistorico() throws Exception {
        UsuarioTeste organizador = cadastrar("Org");
        UsuarioTeste umRole = cadastrar("UmRole");
        UsuarioTeste tresRoles = cadastrar("TresRoles");
        concluirComPresenca(organizador, umRole, 1);
        concluirComPresenca(organizador, tresRoles, 3);
        Statistics estatisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean ligadas = estatisticas.isStatisticsEnabled();
        estatisticas.setStatisticsEnabled(true);
        try {
            estatisticas.clear();
            assertThat(pendencias(umRole).get("eventos").size()).isEqualTo(1);
            long comUm = estatisticas.getPrepareStatementCount();
            estatisticas.clear();
            assertThat(pendencias(tresRoles).get("eventos").size()).isEqualTo(3);
            long comTres = estatisticas.getPrepareStatementCount();
            // Antes eram duas consultas a mais por rolé do histórico.
            assertThat(comTres).isPositive().isEqualTo(comUm);
        } finally {
            estatisticas.setStatisticsEnabled(ligadas);
        }
    }
    @Test
    @DisplayName("o log da avaliação traz só os ids, sem o nome de quem avaliou nem de quem foi avaliado")
    void logDaAvaliacaoSemNomes(CapturedOutput saida) throws Exception {
        UsuarioTeste organizador = cadastrar("Org");
        UsuarioTeste alfa = cadastrar("Alfa");
        UUID eventoId = criarEventoValido(organizador, 10);
        alternarInscricao(alfa, eventoId).andExpect(status().isOk());
        moverParaOPassado(eventoId);
        registrarPresenca(organizador, eventoId, alfa).andExpect(status().isNoContent());
        concluir(organizador, eventoId).andExpect(status().isOk());
        avaliar(alfa, eventoId, organizador, 5).andExpect(status().isCreated());
        String linha = saida.getOut().lines().filter(l -> l.contains("RF004") && l.contains(alfa.id().toString()))
            .findFirst().orElseThrow();
        assertThat(linha).contains(organizador.id().toString())
            .doesNotContain(alfa.primeiroNome()).doesNotContain(organizador.primeiroNome());
    }
    /** Cria rolés do organizador, com a pessoa presente, e os conclui: em cada um, ela fica com o organizador para avaliar. */
    private void concluirComPresenca(UsuarioTeste organizador, UsuarioTeste participante, int quantos) throws Exception {
        List<UUID> eventos = new ArrayList<>();
        for (int i = 0; i < quantos; i++) {
            UUID eventoId = criarEventoValido(organizador, 10);
            // Todas as inscrições antes das conclusões: com avaliação pendente, ninguém entra em outro rolé.
            alternarInscricao(participante, eventoId).andExpect(status().isOk());
            eventos.add(eventoId);
        }
        for (UUID eventoId : eventos) {
            moverParaOPassado(eventoId);
            registrarPresenca(organizador, eventoId, participante).andExpect(status().isNoContent());
            concluir(organizador, eventoId).andExpect(status().isOk());
        }
    }
    private tools.jackson.databind.JsonNode pendencias(UsuarioTeste usuario) throws Exception {
        return json(mockMvc.perform(get("/auth/me/avaliacoes-pendentes").header("Authorization", bearer(usuario.token())))
            .andExpect(status().isOk()));
    }
    @Test
    @DisplayName("fluxo completo: presença, conclusão, bloqueio por avaliação pendente e liberação após avaliar")
    void fluxoCompletoDeAvaliacao() throws Exception {
        UsuarioTeste organizador = cadastrar("Org");
        UsuarioTeste alfa = cadastrar("Alfa");
        UsuarioTeste bravo = cadastrar("Bravo");
        UUID eventoId = criarEventoValido(organizador, 10);
        alternarInscricao(alfa, eventoId).andExpect(status().isOk());
        alternarInscricao(bravo, eventoId).andExpect(status().isOk());
        moverParaOPassado(eventoId);
        registrarPresenca(organizador, eventoId, alfa).andExpect(status().isNoContent());
        registrarPresenca(organizador, eventoId, bravo).andExpect(status().isNoContent());
        concluir(organizador, eventoId).andExpect(jsonPath("$.status").value("CONCLUIDO"));
        UUID proximoEvento = criarEventoValido(cadastrar("OutroOrg"), 10);
        alternarInscricao(alfa, proximoEvento)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message", containsString("avaliações pendentes")));
        avaliar(alfa, eventoId, bravo, 5).andExpect(status().isCreated());
        avaliar(alfa, eventoId, organizador, 4).andExpect(status().isCreated());
        alternarInscricao(alfa, proximoEvento)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CONFIRMADO"));
        assertThat(usuarioAtual(bravo).get("honor").asDouble()).isEqualTo(3.0);
        assertThat(usuarioAtual(organizador).get("honor").asDouble()).isEqualTo(3.0);
    }
    @Test
    @DisplayName("avaliação exige evento concluído, participação dos dois lados e é única por pessoa")
    void regrasDaAvaliacao() throws Exception {
        UsuarioTeste organizador = cadastrar("Org");
        UsuarioTeste alfa = cadastrar("Alfa");
        UsuarioTeste bravo = cadastrar("Bravo");
        UsuarioTeste faltou = cadastrar("Faltou");
        UsuarioTeste naoInscrito = cadastrar("Fora");
        UUID eventoId = criarEventoValido(organizador, 10);
        alternarInscricao(alfa, eventoId).andExpect(status().isOk());
        alternarInscricao(bravo, eventoId).andExpect(status().isOk());
        alternarInscricao(faltou, eventoId).andExpect(status().isOk());
        moverParaOPassado(eventoId);
        registrarPresenca(organizador, eventoId, alfa).andExpect(status().isNoContent());
        registrarPresenca(organizador, eventoId, bravo).andExpect(status().isNoContent());
        avaliar(alfa, eventoId, bravo, 5)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message", containsString("após a conclusão")));
        concluir(organizador, eventoId).andExpect(status().isOk());
        avaliar(alfa, eventoId, naoInscrito, 3)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message", containsString("quem esteve presente")));
        avaliar(alfa, eventoId, alfa, 5).andExpect(status().isBadRequest());
        avaliar(alfa, eventoId, bravo, 5).andExpect(status().isCreated());
        avaliar(alfa, eventoId, bravo, 1)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message", containsString("já avaliou")));
        avaliar(faltou, eventoId, alfa, 1)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message", containsString("Apenas quem esteve presente")));
        assertThat(usuarioAtual(faltou).get("honor").asDouble()).isEqualTo(2.5);
    }
    @Test
    @DisplayName("só o organizador registra presença, apenas de inscritos e antes de concluir; ele mesmo não é penalizado")
    void regrasDaPresenca() throws Exception {
        UsuarioTeste organizador = cadastrar("Org");
        UsuarioTeste alfa = cadastrar("Alfa");
        UsuarioTeste naoInscrito = cadastrar("Fora");
        UUID eventoId = criarEventoValido(organizador, 10);
        alternarInscricao(alfa, eventoId).andExpect(status().isOk());
        moverParaOPassado(eventoId);
        registrarPresenca(alfa, eventoId, alfa)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message", containsString("Apenas o organizador")));
        registrarPresenca(organizador, eventoId, naoInscrito).andExpect(status().isNotFound());
        concluir(organizador, eventoId).andExpect(status().isOk());
        registrarPresenca(organizador, eventoId, alfa)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message", containsString("antes de concluir")));
        assertThat(usuarioAtual(organizador).get("honor").asDouble()).isEqualTo(3.0);
    }
    @Test
    @DisplayName("o comentário da avaliação aceita até 180 caracteres, como o comentário do rolé")
    void comentarioDaAvaliacaoTemLimite() throws Exception {
        UsuarioTeste organizador = cadastrar("Org");
        UsuarioTeste alfa = cadastrar("Alfa");
        UUID eventoId = criarEventoValido(organizador, 10);
        alternarInscricao(alfa, eventoId).andExpect(status().isOk());
        moverParaOPassado(eventoId);
        registrarPresenca(organizador, eventoId, alfa).andExpect(status().isNoContent());
        concluir(organizador, eventoId).andExpect(status().isOk());
        avaliar(alfa, eventoId, Map.of("avaliadoId", organizador.id(), "nota", 5, "comentario", "x".repeat(181)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("até 180 caracteres")));
        avaliar(alfa, eventoId, Map.of("avaliadoId", organizador.id(), "nota", 5, "comentario", "x".repeat(180)))
            .andExpect(status().isCreated());
    }
    private ResultActions avaliar(UsuarioTeste avaliador, UUID eventoId, UsuarioTeste avaliado, int nota)
            throws Exception {
        return avaliar(avaliador, eventoId, Map.of("avaliadoId", avaliado.id(), "nota", nota));
    }
    private ResultActions avaliar(UsuarioTeste avaliador, UUID eventoId, Map<String, Object> corpo) throws Exception {
        return mockMvc.perform(post("/eventos/{id}/avaliacoes", eventoId)
            .header("Authorization", bearer(avaliador.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(corpo)));
    }
}
