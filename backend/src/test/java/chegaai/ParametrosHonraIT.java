package chegaai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "chegaai.regras.honra-inicial=4.0",
    "chegaai.regras.quorum-minimo-avaliacao=2",
    "chegaai.regras.deducao-honra-ausencia=0.25"
})
class ParametrosHonraIT extends IntegracaoBase {
    @Test
    @DisplayName("base, quórum e desconto obedecem às propriedades em vez de constantes")
    void usaParametrosConfigurados() throws Exception {
        UsuarioTeste organizador = cadastrar("OrgParametros");
        UsuarioTeste alvo = cadastrar("AlvoParametros");
        UsuarioTeste outro = cadastrar("OutroParametros");
        assertHonra(alvo, 4.0);
        UUID notas = criarEventoValido(organizador, 10);
        UUID falta = criarEventoValido(organizador, 10);
        alternarInscricao(alvo, notas).andExpect(status().isOk());
        alternarInscricao(outro, notas).andExpect(status().isOk());
        alternarInscricao(alvo, falta).andExpect(status().isOk());
        moverParaOPassado(notas);
        moverParaOPassado(falta);
        registrarPresenca(organizador, notas, alvo).andExpect(status().isNoContent());
        registrarPresenca(organizador, notas, outro).andExpect(status().isNoContent());
        concluir(organizador, notas).andExpect(status().isOk());
        concluir(organizador, falta).andExpect(status().isOk());
        assertHonra(alvo, 3.75);
        avaliar(organizador, notas, alvo, 5);
        assertHonra(alvo, 3.75);
        avaliar(outro, notas, alvo, 1);
        assertHonra(alvo, 2.75);
    }

    private void avaliar(UsuarioTeste avaliador, UUID evento, UsuarioTeste avaliado, int nota) throws Exception {
        mockMvc.perform(post("/eventos/{id}/avaliacoes", evento)
            .header("Authorization", bearer(avaliador.token())).contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("avaliadoId", avaliado.id(), "nota", nota))))
            .andExpect(status().isCreated());
    }

    private void assertHonra(UsuarioTeste usuario, double valor) throws Exception {
        assertThat(usuarioAtual(usuario).get("honor").asDouble()).isEqualTo(valor);
    }
}
