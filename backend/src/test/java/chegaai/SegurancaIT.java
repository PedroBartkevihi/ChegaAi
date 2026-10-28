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
    @DisplayName("token de uma conta que não existe mais devolve 401, inclusive em rota pública")
    void tokenDeContaApagadaDevolve401() throws Exception {
        UsuarioTeste helo = cadastrar("Helo");
        jdbcTemplate.update("DELETE FROM usuario WHERE id_usuario = ?", helo.id());
        mockMvc.perform(get("/auth/me").header("Authorization", bearer(helo.token())))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/eventos").header("Authorization", bearer(helo.token())))
            .andExpect(status().isUnauthorized());
    }
    @Test
    @DisplayName("o actuator só expõe health e info, sem detalhes, mesmo para quem está logado")
    void actuatorSemMetricasNemDetalhes() throws Exception {
        UsuarioTeste ivo = cadastrar("Ivo");
        mockMvc.perform(get("/actuator/metrics").header("Authorization", bearer(ivo.token())))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/health").header("Authorization", bearer(ivo.token())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.components").doesNotExist());
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
    @DisplayName("cadastro sem CPF funciona, e CPF repetido recebe mensagem genérica")
    void cadastroComCpfOpcional() throws Exception {
        mockMvc.perform(post("/auth/cadastro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "name", "Jade Sem Cpf", "email", "jade@teste.chegaai.dev", "cpf", "",
                    "birthDate", "1998-08-08", "password", SENHA))))
            .andExpect(status().isCreated());
        login("jade@teste.chegaai.dev", SENHA);
        String cpf = cpfValido();
        Map<String, Object> primeiro = Map.of("name", "Kai Teste", "email", "kai@teste.chegaai.dev", "cpf", cpf,
            "birthDate", "1997-07-07", "password", SENHA);
        mockMvc.perform(post("/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(primeiro)))
            .andExpect(status().isCreated());
        Map<String, Object> segundo = Map.of("name", "Lu Teste", "email", "lu@teste.chegaai.dev", "cpf", cpf,
            "birthDate", "1996-06-06", "password", SENHA);
        mockMvc.perform(post("/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(segundo)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message", containsString("Não foi possível concluir o cadastro")));
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
        UsuarioTeste moderadora = cadastrar("Fabi");
        jdbcTemplate.update("UPDATE usuario SET papel_sistema = 'ADMINISTRADOR' WHERE id_usuario = ?", moderadora.id());
        mockMvc.perform(delete("/eventos/{id}", eventoId).header("Authorization", bearer(moderadora.token())))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/eventos/{id}", eventoId)).andExpect(status().isNotFound());
    }
    @Test
    @DisplayName("o moderador de demonstração, de senha pública, só exclui rolés da demonstração")
    void moderadorDeDemonstracaoSoExcluiRolesDaDemonstracao() throws Exception {
        UUID eventoDeVisitante = criarEventoValido(cadastrar("Gabi"), 10);
        String caio = login("caio@chegaai.dev", "admin123");
        UUID eventoDaDemonstracao = UUID.fromString(json(criarEvento(caio, "Rolé da demonstração", 10)
            .andExpect(status().isCreated())).get("id").asText());
        String moderador = login("moderador@chegaai.dev", "admin123");
        mockMvc.perform(delete("/eventos/{id}", eventoDeVisitante).header("Authorization", bearer(moderador)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message", containsString("contas de demonstração")));
        mockMvc.perform(get("/eventos/{id}", eventoDeVisitante)).andExpect(status().isOk());
        mockMvc.perform(delete("/eventos/{id}", eventoDaDemonstracao).header("Authorization", bearer(moderador)))
            .andExpect(status().isNoContent());
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
