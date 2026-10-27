package chegaai;
import chegaai.service.TokenService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
    @DisplayName("token assinado com a antiga chave padrão do repositório devolve 401")
    void tokenForjadoComChavePadraoAntigaDevolve401() throws Exception {
        UsuarioTeste ana = cadastrar("Ana");
        // A chave era o padrão do application.properties: com ela, qualquer pessoa assinava um token
        // para o id de um organizador, que aparece em GET /eventos.
        String forjado = assinar("chave-de-desenvolvimento-local-nao-usar-em-producao", ana.id());
        mockMvc.perform(get("/auth/me").header("Authorization", bearer(forjado)))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/auth/me").header("Authorization", bearer(ana.token())))
            .andExpect(status().isOk());
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
    @Test
    @DisplayName("CORS libera só as origens configuradas")
    void corsLiberaSoOrigensConfiguradas() throws Exception {
        mockMvc.perform(options("/eventos")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "POST"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
        mockMvc.perform(options("/eventos")
                .header("Origin", "https://site-qualquer.example")
                .header("Access-Control-Request-Method", "POST"))
            .andExpect(status().isForbidden());
    }
    @Test
    @DisplayName("id de evento que não é UUID devolve 400 sem detalhes internos")
    void idQueNaoEhUuidDevolve400() throws Exception {
        mockMvc.perform(get("/eventos/nao-e-uuid"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Valor inválido para o parâmetro 'id'."));
    }
    @Test
    @DisplayName("rota inexistente devolve 404")
    void rotaInexistenteDevolve404() throws Exception {
        UsuarioTeste ana = cadastrar("Ana");
        mockMvc.perform(get("/rota-que-nao-existe").header("Authorization", bearer(ana.token())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Rota não encontrada."));
        // GET em /eventos/** é público: sem token, a requisição também chega ao Spring MVC
        mockMvc.perform(get("/eventos/{id}/rota-que-nao-existe", UUID.randomUUID()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Rota não encontrada."));
    }
    @Test
    @DisplayName("método não suportado na rota devolve 405 com o cabeçalho Allow")
    void metodoNaoSuportadoDevolve405() throws Exception {
        UsuarioTeste ana = cadastrar("Ana");
        mockMvc.perform(put("/eventos").header("Authorization", bearer(ana.token())))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(header().string("Allow", allOf(containsString("GET"), containsString("POST"))))
            .andExpect(jsonPath("$.message").value("Método PUT não é aceito nesta rota."));
    }
    private static String assinar(String segredo, UUID usuarioId) {
        Instant agora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(TokenService.EMISSOR)
            .subject(usuarioId.toString())
            .issuedAt(agora)
            .expiresAt(agora.plus(1, ChronoUnit.HOURS))
            .build();
        SecretKeySpec chave = new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return new NimbusJwtEncoder(new ImmutableSecret<>(chave))
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    }
}
