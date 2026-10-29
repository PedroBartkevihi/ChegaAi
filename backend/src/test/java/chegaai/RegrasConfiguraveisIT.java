package chegaai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import java.util.UUID;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
    "chegaai.regras.honra-embaixador=4.2",
    "chegaai.regras.honra-minima-criacao=3.5",
    "chegaai.regras.limite-vagas-padrao=8",
    "chegaai.regras.limite-vagas-embaixador=60"
})
class RegrasConfiguraveisIT extends IntegracaoBase {
    @Test
    @DisplayName("perfil, selo e capacidade seguem valores configurados diferentes dos padrões")
    void perfilESeloSeguemAConfiguracao() throws Exception {
        UsuarioTeste usuario = cadastrar("Configurado");
        var inicial = usuarioAtual(usuario);
        assertThat(inicial.get("isAmbassador").asBoolean()).isFalse();
        assertThat(inicial.get("capacityLimit").asInt()).isEqualTo(8);
        assertThat(inicial.get("canCreateEvents").asBoolean()).isFalse();
        criarEvento(usuario.token(), "Sem honra para criar", 8).andExpect(status().isConflict());

        definirHonra(usuario, "4.0");
        assertThat(usuarioAtual(usuario).get("canCreateEvents").asBoolean()).isTrue();
        criarEvento(usuario.token(), "Capacidade comum configurada", 75)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.capacity").value(8))
            .andExpect(jsonPath("$.organizer.isAmbassador").value(false));

        definirHonra(usuario, "4.2");
        var embaixador = usuarioAtual(usuario);
        assertThat(embaixador.get("isAmbassador").asBoolean()).isTrue();
        assertThat(embaixador.get("capacityLimit").asInt()).isEqualTo(60);
        var evento = json(criarEvento(usuario.token(), "Capacidade de embaixador configurada", 75)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.capacity").value(60))
            .andExpect(jsonPath("$.organizer.isAmbassador").value(true)));
        UUID eventoId = UUID.fromString(evento.get("id").asText());
        assertThat(buscarEvento(eventoId).at("/organizer/isAmbassador").asBoolean()).isTrue();

        definirHonra(usuario, "4.19");
        assertThat(usuarioAtual(usuario).get("isAmbassador").asBoolean()).isFalse();
        assertThat(buscarEvento(eventoId).at("/organizer/isAmbassador").asBoolean()).isFalse();
    }

    private void definirHonra(UsuarioTeste usuario, String honra) {
        jdbcTemplate.update("UPDATE usuario SET honra_media = CAST(? AS numeric) WHERE id_usuario = ?", honra, usuario.id());
    }
}
