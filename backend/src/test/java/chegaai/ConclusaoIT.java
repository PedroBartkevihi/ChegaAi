package chegaai;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
class ConclusaoIT extends IntegracaoBase {
    @Test
    @DisplayName("evento cuja data já passou é concluído e só quem faltou perde honra (RN008)")
    void concluiEventoQueJaPassou() throws Exception {
        UsuarioTeste organizador = cadastrar("Org");
        UsuarioTeste presente = cadastrar("Presente");
        UsuarioTeste faltou = cadastrar("Faltou");
        UUID eventoId = criarEventoValido(organizador, 10);
        alternarInscricao(presente, eventoId).andExpect(status().isOk());
        alternarInscricao(faltou, eventoId).andExpect(status().isOk());
        moverParaOPassado(eventoId);
        registrarPresenca(organizador, eventoId, presente).andExpect(status().isNoContent());
        concluir(organizador, eventoId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CONCLUIDO"));
        assertThat(buscarEvento(eventoId).get("status").asText()).isEqualTo("CONCLUIDO");
        assertThat(usuarioAtual(faltou).get("honor").asDouble()).isEqualTo(2.5);
        assertThat(usuarioAtual(presente).get("honor").asDouble()).isEqualTo(3.0);
        assertThat(usuarioAtual(organizador).get("honor").asDouble()).isEqualTo(3.0);
    }
    @Test
    @DisplayName("evento que ainda não aconteceu não pode ser concluído nem penaliza ninguém")
    void naoConcluiAntesDoHorario() throws Exception {
        UsuarioTeste organizador = cadastrar("Org");
        UsuarioTeste inscrito = cadastrar("Inscrito");
        UUID eventoId = criarEventoValido(organizador, 10);
        alternarInscricao(inscrito, eventoId).andExpect(status().isOk());
        concluir(organizador, eventoId)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message", containsString("depois da data e hora de realização")));
        assertThat(buscarEvento(eventoId).get("status").asText()).isEqualTo("ABERTO");
        assertThat(usuarioAtual(inscrito).get("honor").asDouble()).isEqualTo(3.0);
    }
}
