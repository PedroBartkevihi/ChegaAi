package chegaai;
import chegaai.domain.model.Usuario;
import chegaai.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.math.BigDecimal;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
class RegrasDeHonraIT extends IntegracaoBase {
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Test
    @DisplayName("honra abaixo de 2.0 impede criar eventos (RN004)")
    void honraBaixaNaoCriaEvento() throws Exception {
        UsuarioTeste usuario = cadastrar("Baixa");
        definirHonra(usuario, "1.50");
        criarEvento(usuario.token(), "Rolé bloqueado", 10)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message", containsString("honra está abaixo de 2.0")));
    }
    @Test
    @DisplayName("honra abaixo de 3.0 impede se inscrever em eventos")
    void honraBaixaNaoSeInscreve() throws Exception {
        UsuarioTeste organizador = cadastrar("Org");
        UsuarioTeste usuario = cadastrar("Media");
        UUID eventoId = criarEventoValido(organizador, 10);
        definirHonra(usuario, "2.50");
        alternarInscricao(usuario, eventoId)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message", containsString("abaixo do mínimo")));
    }
    @Test
    @DisplayName("quem fica com honra alta tem limite ampliado de vagas (Embaixador)")
    void embaixadorTemLimiteAmpliado() throws Exception {
        UsuarioTeste comum = cadastrar("Comum");
        UsuarioTeste embaixador = cadastrar("Embaixador");
        definirHonra(embaixador, "4.90");
        criarEvento(comum.token(), "Pelada comum", 50).andExpect(jsonPath("$.capacity").value(20));
        criarEvento(embaixador.token(), "Pelada grande", 50).andExpect(jsonPath("$.capacity").value(50));
    }
    @Test
    @DisplayName("confirmado que não comparece perde 0.5 de honra quando o evento é concluído (RN008)")
    void ausenciaReduzHonra() throws Exception {
        UsuarioTeste organizador = cadastrar("Org");
        UsuarioTeste faltou = cadastrar("Faltou");
        UUID eventoId = criarEventoValido(organizador, 10);
        alternarInscricao(faltou, eventoId).andExpect(status().isOk());
        moverParaOPassado(eventoId);
        concluir(organizador, eventoId).andExpect(status().isOk());
        assertThat(usuarioAtual(faltou).get("honor").asDouble()).isEqualTo(2.5);
    }
    @Test
    @DisplayName("só o organizador conclui o evento")
    void soOrganizadorConclui() throws Exception {
        UsuarioTeste organizador = cadastrar("Org");
        UsuarioTeste outro = cadastrar("Outro");
        UUID eventoId = criarEventoValido(organizador, 10);
        moverParaOPassado(eventoId);
        concluir(outro, eventoId)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message", containsString("Apenas o organizador")));
    }
    private void definirHonra(UsuarioTeste usuarioTeste, String honra) {
        Usuario usuario = usuarioRepository.findById(usuarioTeste.id()).orElseThrow();
        usuario.setHonraMedia(new BigDecimal(honra));
        usuarioRepository.save(usuario);
    }
}
