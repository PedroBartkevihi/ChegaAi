package chegaai;

import chegaai.service.ExclusaoContaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExclusaoContaIT extends IntegracaoBase {
    @Test
    @DisplayName("excluir a conta apaga os dados pessoais, derruba o token e libera o e-mail")
    void excluiOsDadosPessoais() throws Exception {
        UsuarioTeste ana = cadastrar("Saida");
        excluir(ana, SENHA).andExpect(status().isNoContent());
        Map<String, Object> conta = jdbcTemplate.queryForMap(
            "SELECT nome, email, cpf, data_nascimento, status_usuario FROM usuario WHERE id_usuario = ?", ana.id());
        assertThat(conta.get("nome")).isEqualTo(ExclusaoContaService.NOME);
        assertThat(conta.get("email")).isEqualTo("excluida+" + ana.id() + "@chegaai.invalid");
        assertThat(conta.get("cpf")).isNull();
        assertThat(conta.get("data_nascimento").toString()).isEqualTo("1900-01-01");
        assertThat(conta.get("status_usuario")).isEqualTo("EXCLUIDO");
        mockMvc.perform(get("/auth/me").header("Authorization", bearer(ana.token())))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", ana.email(), "password", SENHA))))
            .andExpect(status().isUnauthorized());
        // O e-mail verdadeiro fica livre para um cadastro novo, que começa do zero.
        mockMvc.perform(post("/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Ana de Novo", "email", ana.email(),
                    "birthDate", "2000-01-15", "password", SENHA))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.user.id").value(org.hamcrest.Matchers.not(ana.id().toString())));
    }

    @Test
    @DisplayName("os rolés futuros que a pessoa organiza saem; nos dos outros, a vaga vai para a fila, sem penalidade")
    void rolesFuturosEFila() throws Exception {
        UsuarioTeste ana = cadastrar("Saida");
        UsuarioTeste bruno = cadastrar("Org");
        UsuarioTeste carla = cadastrar("Fila");
        UsuarioTeste davi = cadastrar("Convidado");
        UUID doAna = criarEventoValido(ana, 5);
        alternarInscricao(davi, doAna).andExpect(jsonPath("$.status").value("CONFIRMADO"));
        // Três vagas no rolé do Bruno: ele, o Davi e a Ana; a Carla espera na fila.
        UUID doBruno = criarEventoValido(bruno, 3);
        alternarInscricao(davi, doBruno).andExpect(jsonPath("$.status").value("CONFIRMADO"));
        alternarInscricao(ana, doBruno).andExpect(jsonPath("$.status").value("CONFIRMADO"));
        alternarInscricao(carla, doBruno).andExpect(jsonPath("$.status").value("FILA"));
        // A menos de 24 h do rolé: sair por conta própria contaria como cancelamento tardio.
        jdbcTemplate.update("UPDATE evento SET data_hora_realizacao = now() + interval '3 hours' WHERE id_evento = ?", doBruno);
        excluir(ana, SENHA).andExpect(status().isNoContent());
        mockMvc.perform(get("/eventos/{id}", doAna)).andExpect(status().isNotFound());
        assertThat(statusDaInscricao(doBruno, ana.id())).isEqualTo("CANCELADA");
        assertThat(statusDaInscricao(doBruno, carla.id())).isEqualTo("CONFIRMADA");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM cancelamento_tardio WHERE id_usuario = ?",
            Integer.class, ana.id())).isZero();
    }

    @Test
    @DisplayName("presenças, avaliações e comentários continuam, e ninguém precisa avaliar a conta excluída")
    void historicoContinua() throws Exception {
        UsuarioTeste ana = cadastrar("Saida");
        UsuarioTeste bruno = cadastrar("Org");
        UUID role = criarEventoValido(bruno, 5);
        alternarInscricao(ana, role).andExpect(jsonPath("$.status").value("CONFIRMADO"));
        mockMvc.perform(post("/eventos/{id}/comentarios", role).header("Authorization", bearer(ana.token()))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("text", "Levo a bola."))))
            .andExpect(status().isCreated());
        moverParaOPassado(role);
        registrarPresenca(bruno, role, ana).andExpect(status().is2xxSuccessful());
        concluir(bruno, role).andExpect(jsonPath("$.status").value("CONCLUIDO"));
        mockMvc.perform(post("/eventos/{id}/avaliacoes", role).header("Authorization", bearer(ana.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("avaliadoId", bruno.id(), "nota", 5))))
            .andExpect(status().is2xxSuccessful());
        assertThat(pendencias(bruno).get("eventos")).hasSize(1);
        excluir(ana, SENHA).andExpect(status().isNoContent());
        assertThat(statusDaInscricao(role, ana.id())).isEqualTo("CONFIRMADA");
        assertThat(jdbcTemplate.queryForObject("SELECT compareceu FROM inscricao WHERE id_evento = ? AND id_usuario = ?",
            Boolean.class, role, ana.id())).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM avaliacao WHERE id_avaliador = ?",
            Integer.class, ana.id())).isEqualTo(1);
        JsonNode comentario = buscarEvento(role).get("comments").get(1);
        assertThat(comentario.get("author").asText()).isEqualTo(ExclusaoContaService.NOME);
        assertThat(comentario.get("text").asText()).isEqualTo("Levo a bola.");
        assertThat(pendencias(bruno).get("eventos")).isEmpty();
    }

    @Test
    @DisplayName("senha errada, conta de demonstração e rolé em andamento impedem a exclusão")
    void recusas() throws Exception {
        UsuarioTeste ana = cadastrar("Saida");
        excluir(ana, "senhaErrada123").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(ExclusaoContaService.SENHA_INCORRETA));
        excluir(ana, "").andExpect(status().isBadRequest());
        // Quem organiza um rolé que já começou precisa concluí-lo antes: presenças e avaliações dependem disso.
        UUID role = criarEventoValido(ana, 5);
        moverParaOPassado(role);
        excluir(ana, SENHA).andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value(ExclusaoContaService.ROLE_EM_ANDAMENTO));
        assertThat(usuarioAtual(ana).get("email").asText()).isEqualTo(ana.email());
        UsuarioTeste demo = new UsuarioTeste(null, "Pedro", "pedro@chegaai.dev", login("pedro@chegaai.dev", "admin123"));
        excluir(demo, "admin123").andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value(ExclusaoContaService.CONTA_DE_DEMONSTRACAO));
        assertThat(usuarioAtual(demo).get("email").asText()).isEqualTo("pedro@chegaai.dev");
    }

    private ResultActions excluir(UsuarioTeste usuario, String senha) throws Exception {
        return mockMvc.perform(post("/auth/me/exclusao").header("Authorization", bearer(usuario.token()))
            .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("password", senha))));
    }
    private JsonNode pendencias(UsuarioTeste usuario) throws Exception {
        return json(mockMvc.perform(get("/auth/me/avaliacoes-pendentes").header("Authorization", bearer(usuario.token())))
            .andExpect(status().isOk()));
    }
    private String statusDaInscricao(UUID eventoId, UUID usuarioId) {
        return jdbcTemplate.queryForObject("SELECT status_inscricao FROM inscricao WHERE id_evento = ? AND id_usuario = ?",
            String.class, eventoId, usuarioId);
    }
}
