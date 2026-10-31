package chegaai;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
/**
 * Base dos testes de integração: sobe a aplicação inteira contra um PostgreSQL real
 * (Testcontainers), com as mesmas migrations do Flyway usadas em produção.
 * O contêiner é compartilhado entre as classes de teste; cada teste cria os próprios
 * usuários e eventos para não depender da ordem de execução. O GraphDB aponta para uma porta
 * fechada, como na versão online, onde ele não existe: um GraphDB local não muda os testes.
 */
@SpringBootTest(properties = {
    "chegaai.limites.login-ip.capacidade=1000000", "chegaai.limites.login-par.capacidade=1000000",
    "chegaai.limites.cadastro-ip.capacidade=1000000", "chegaai.limites.criar-evento.capacidade=1000000",
    "chegaai.limites.criar-evento.longo-capacidade=1000000", "chegaai.limites.comentar.capacidade=1000000",
    "chegaai.limites.comentar.longo-capacidade=1000000", "chegaai.graphdb.url=http://127.0.0.1:1"
})
@AutoConfigureMockMvc
public abstract class IntegracaoBase {
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:15-alpine");
    static {
        POSTGRES.start();
    }
    private static final AtomicInteger SEQUENCIA = new AtomicInteger();
    protected static final String SENHA = "senhaSegura123";
    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected JdbcTemplate jdbcTemplate;
    protected record UsuarioTeste(UUID id, String primeiroNome, String email, String token) {}
    protected UsuarioTeste cadastrar(String prefixoNome) throws Exception {
        int n = SEQUENCIA.incrementAndGet();
        String primeiroNome = prefixoNome + n;
        String email = primeiroNome.toLowerCase() + "@teste.chegaai.dev";
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("name", primeiroNome + " Teste");
        corpo.put("email", email);
        corpo.put("cpf", cpfValido());
        corpo.put("birthDate", "2000-01-15");
        corpo.put("password", SENHA);
        JsonNode resposta = json(mockMvc.perform(post("/auth/cadastro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(corpo)))
            .andExpect(status().isCreated()));
        return new UsuarioTeste(UUID.fromString(resposta.at("/user/id").asText()), primeiroNome, email,
            resposta.get("token").asText());
    }
    protected String login(String email, String senha) throws Exception {
        return json(mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", senha))))
            .andExpect(status().isOk()))
            .get("token").asText();
    }
    protected ResultActions criarEvento(String token, String titulo, int capacidade) throws Exception {
        return criarEvento(token, corpoDeEvento(titulo, capacidade));
    }
    protected ResultActions criarEvento(String token, String titulo, int capacidade, LocalDate data)
            throws Exception {
        Map<String, Object> corpo = corpoDeEvento(titulo, capacidade);
        corpo.put("date", data.toString());
        return criarEvento(token, corpo);
    }
    protected ResultActions criarEvento(String token, Map<String, Object> corpo) throws Exception {
        return mockMvc.perform(post("/eventos")
            .header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(corpo)));
    }
    /** Corpo válido de criação de evento; o teste pode trocar qualquer campo antes de enviar. */
    protected static Map<String, Object> corpoDeEvento(String titulo, int capacidade) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("title", titulo);
        corpo.put("category", "Futebol");
        corpo.put("date", LocalDate.now().plusDays(5).toString());
        corpo.put("time", "18:00");
        corpo.put("cep", "");
        corpo.put("place", "Quadra do Aterro, Rio de Janeiro");
        corpo.put("capacity", capacidade);
        corpo.put("description", "Evento criado pelo teste de integração.");
        return corpo;
    }
    protected UUID criarEventoValido(UsuarioTeste organizador, int capacidade) throws Exception {
        JsonNode evento = json(criarEvento(organizador.token(), "Pelada de " + organizador.primeiroNome(), capacidade)
            .andExpect(status().isCreated()));
        return UUID.fromString(evento.get("id").asText());
    }
    protected ResultActions alternarInscricao(UsuarioTeste usuario, UUID eventoId) throws Exception {
        return mockMvc.perform(post("/eventos/{id}/inscricao", eventoId)
            .header("Authorization", bearer(usuario.token())));
    }
    /**
     * A API só aceita criar eventos futuros e só deixa concluir depois do horário do rolé:
     * os testes criam o evento normalmente e depois o levam para duas horas atrás.
     */
    protected void moverParaOPassado(UUID eventoId) {
        jdbcTemplate.update(
            "UPDATE evento SET data_hora_realizacao = now() - interval '2 hours' WHERE id_evento = ?", eventoId);
    }
    protected ResultActions registrarPresenca(UsuarioTeste organizador, UUID eventoId, UsuarioTeste participante)
            throws Exception {
        return mockMvc.perform(post("/eventos/{id}/presencas", eventoId)
            .header("Authorization", bearer(organizador.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("participanteId", participante.id()))));
    }
    protected ResultActions concluir(UsuarioTeste organizador, UUID eventoId) throws Exception {
        return mockMvc.perform(post("/eventos/{id}/concluir", eventoId)
            .header("Authorization", bearer(organizador.token())));
    }
    protected JsonNode buscarEvento(UUID eventoId) throws Exception {
        return json(mockMvc.perform(get("/eventos/{id}", eventoId)).andExpect(status().isOk()));
    }
    protected JsonNode usuarioAtual(UsuarioTeste usuario) throws Exception {
        return json(mockMvc.perform(get("/auth/me").header("Authorization", bearer(usuario.token())))
            .andExpect(status().isOk()));
    }
    protected JsonNode json(ResultActions resultado) throws Exception {
        return objectMapper.readTree(resultado.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
    protected static String bearer(String token) {
        return "Bearer " + token;
    }
    /** Gera um CPF aleatório com dígitos verificadores corretos. */
    protected static String cpfValido() {
        int[] digitos = new int[11];
        for (int i = 0; i < 9; i++) {
            digitos[i] = ThreadLocalRandom.current().nextInt(10);
        }
        digitos[9] = digitoVerificador(digitos, 9);
        digitos[10] = digitoVerificador(digitos, 10);
        StringBuilder cpf = new StringBuilder();
        for (int digito : digitos) {
            cpf.append(digito);
        }
        return cpf.toString();
    }
    private static int digitoVerificador(int[] digitos, int quantidade) {
        int soma = 0;
        for (int i = 0; i < quantidade; i++) {
            soma += digitos[i] * (quantidade + 1 - i);
        }
        int resto = (soma * 10) % 11;
        return resto == 10 ? 0 : resto;
    }
}
