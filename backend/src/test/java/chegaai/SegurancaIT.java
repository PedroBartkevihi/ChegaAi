package chegaai;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import java.util.Map;
import java.util.UUID;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
class SegurancaIT extends IntegracaoBase {
    @Test
    @DisplayName("o feed de eventos é público")
    void feedEhPublico() throws Exception {
        mockMvc.perform(get("/eventos")).andExpect(status().isOk());
    }
    @Test
    @DisplayName("criar evento sem token devolve 401")
    void escritaSemTokenDevolve401() throws Exception {
        mockMvc.perform(post("/eventos").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized());
    }
    @Test
    @DisplayName("o antigo cabeçalho X-User-Id não autentica ninguém")
    void cabecalhoXUserIdNaoAutentica() throws Exception {
        UsuarioTeste ana = cadastrar("Ana");
        mockMvc.perform(post("/eventos")
                .header("X-User-Id", ana.id().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }
    @Test
    @DisplayName("token adulterado devolve 401")
    void tokenAdulteradoDevolve401() throws Exception {
        UsuarioTeste ana = cadastrar("Ana");
        String adulterado = ana.token().substring(0, ana.token().length() - 2) + "xx";
        mockMvc.perform(get("/auth/me").header("Authorization", bearer(adulterado)))
            .andExpect(status().isUnauthorized());
    }
    @Test
    @DisplayName("contas de demonstração entram com a senha documentada e recusam senha errada")
    void loginDasContasDeDemonstracao() throws Exception {
        String token = login("pedro@chegaai.dev", "admin123");
        mockMvc.perform(get("/auth/me").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fullName").value("Pedro Farias"));
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", "pedro@chegaai.dev", "password", "errada"))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("E-mail ou senha inválidos."));
    }
    @Test
    @DisplayName("quem se cadastra consegue entrar depois com a mesma senha")
    void cadastroSeguidoDeLogin() throws Exception {
        UsuarioTeste bia = cadastrar("Bia");
        String token = login(bia.email(), SENHA);
        mockMvc.perform(get("/auth/me").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(bia.id().toString()))
            .andExpect(jsonPath("$.honor").value(3.0));
    }
    @Test
    @DisplayName("cadastro com e-mail repetido devolve 409")
    void cadastroComEmailRepetido() throws Exception {
        UsuarioTeste caio = cadastrar("Caio");
        mockMvc.perform(post("/auth/cadastro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "name", "Outro Caio", "email", caio.email(), "cpf", cpfValido(),
                    "birthDate", "1999-09-09", "password", SENHA))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message", containsString("e-mail")));
    }
    @Test
    @DisplayName("só o organizador ou a moderação excluem um evento")
    void exclusaoDeEventoRespeitaPermissoes() throws Exception {
        UsuarioTeste organizador = cadastrar("Dani");
        UsuarioTeste outro = cadastrar("Edu");
        UUID eventoId = criarEventoValido(organizador, 10);
        mockMvc.perform(delete("/eventos/{id}", eventoId).header("Authorization", bearer(outro.token())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message", containsString("organizador ou um moderador")));
        String moderador = login("moderador@chegaai.dev", "admin123");
        mockMvc.perform(delete("/eventos/{id}", eventoId).header("Authorization", bearer(moderador)))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/eventos/{id}", eventoId)).andExpect(status().isNotFound());
    }
}
