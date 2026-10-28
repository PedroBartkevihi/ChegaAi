package chegaai;
import chegaai.controller.exception.GlobalExceptionHandler;
import chegaai.domain.enums.StatusInscricao;
import chegaai.domain.model.Inscricao;
import chegaai.repository.EventoRepository;
import chegaai.repository.InscricaoRepository;
import chegaai.repository.UsuarioRepository;
import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;
import java.time.LocalDate;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
class InscricaoIT extends IntegracaoBase {
    @Autowired
    private InscricaoRepository inscricaoRepository;
    @Autowired
    private EventoRepository eventoRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private GlobalExceptionHandler exceptionHandler;
    @Test
    @DisplayName("evento lotado manda novos inscritos para a fila e promove o primeiro da fila quando abre vaga")
    void filaDeEsperaSegueOrdemDeChegada() throws Exception {
        UsuarioTeste organizador = cadastrar("Org");
        UsuarioTeste alfa = cadastrar("Alfa");
        UsuarioTeste bravo = cadastrar("Bravo");
        UsuarioTeste charlie = cadastrar("Charlie");
        UsuarioTeste delta = cadastrar("Delta");
        UUID eventoId = criarEventoValido(organizador, 3);
        alternarInscricao(alfa, eventoId).andExpect(jsonPath("$.status").value("CONFIRMADO"));
        alternarInscricao(bravo, eventoId).andExpect(jsonPath("$.status").value("CONFIRMADO"));
        alternarInscricao(charlie, eventoId).andExpect(jsonPath("$.status").value("FILA"));
        alternarInscricao(delta, eventoId).andExpect(jsonPath("$.status").value("FILA"));
        JsonNode lotado = buscarEvento(eventoId);
        assertThat(nomes(lotado, "confirmed")).containsExactlyInAnyOrder(
            organizador.primeiroNome(), alfa.primeiroNome(), bravo.primeiroNome());
        assertThat(nomes(lotado, "waitlist")).containsExactly(charlie.primeiroNome(), delta.primeiroNome());
        alternarInscricao(bravo, eventoId).andExpect(jsonPath("$.status").value("CANCELADO"));
        JsonNode depois = buscarEvento(eventoId);
        assertThat(nomes(depois, "confirmed")).containsExactlyInAnyOrder(
            organizador.primeiroNome(), alfa.primeiroNome(), charlie.primeiroNome());
        assertThat(nomes(depois, "waitlist")).containsExactly(delta.primeiroNome());
    }
    @Test
    @DisplayName("inscrições simultâneas nunca passam do limite: quem não couber vai para a fila")
    void inscricoesSimultaneasRespeitamOLimite() throws Exception {
        int capacidade = 3;
        int vagasLivres = capacidade - 1;
        UsuarioTeste organizador = cadastrar("Org");
        UUID eventoId = criarEventoValido(organizador, capacidade);
        List<UsuarioTeste> participantes = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            participantes.add(cadastrar("Corrida"));
        }
        List<MockHttpServletResponse> respostas = dispararAoMesmoTempo(eventoId, participantes);
        List<String> confirmados = new ArrayList<>(List.of(organizador.primeiroNome()));
        List<String> naFila = new ArrayList<>();
        for (int i = 0; i < respostas.size(); i++) {
            MockHttpServletResponse resposta = respostas.get(i);
            assertThat(resposta.getStatus()).as(resposta.getContentAsString()).isEqualTo(200);
            String situacao = objectMapper.readTree(resposta.getContentAsString(StandardCharsets.UTF_8))
                .get("status").asText();
            String nome = participantes.get(i).primeiroNome();
            switch (situacao) {
                case "CONFIRMADO" -> confirmados.add(nome);
                case "FILA" -> naFila.add(nome);
                default -> throw new AssertionError("Situação inesperada para " + nome + ": " + situacao);
            }
        }
        assertThat(confirmados).hasSize(capacidade);
        assertThat(naFila).hasSize(participantes.size() - vagasLivres);
        JsonNode evento = buscarEvento(eventoId);
        assertThat(nomes(evento, "confirmed")).containsExactlyInAnyOrderElementsOf(confirmados);
        assertThat(nomes(evento, "waitlist")).containsExactlyInAnyOrderElementsOf(naFila);
    }
    @Test
    @DisplayName("clique duplo simultâneo vale como dois cliques seguidos, sem erro 500 nem inscrição duplicada")
    void cliqueDuploSimultaneoNaoDuplicaInscricao() throws Exception {
        UsuarioTeste organizador = cadastrar("Org");
        UsuarioTeste apressado = cadastrar("Apressado");
        UUID eventoId = criarEventoValido(organizador, 10);
        List<MockHttpServletResponse> respostas = dispararAoMesmoTempo(eventoId, List.of(apressado, apressado));
        List<String> situacoes = new ArrayList<>();
        for (MockHttpServletResponse resposta : respostas) {
            assertThat(resposta.getStatus()).as(resposta.getContentAsString()).isEqualTo(200);
            situacoes.add(objectMapper.readTree(resposta.getContentAsString(StandardCharsets.UTF_8))
                .get("status").asText());
        }
        assertThat(situacoes).containsExactlyInAnyOrder("CONFIRMADO", "CANCELADO");
        JsonNode evento = buscarEvento(eventoId);
        assertThat(nomes(evento, "confirmed")).containsExactly(organizador.primeiroNome());
        assertThat(nomes(evento, "waitlist")).isEmpty();
    }
    @Test
    @DisplayName("inscrição duplicada barrada pelo banco vira 409 com mensagem amigável, sem SQL")
    void inscricaoDuplicadaNoBancoViraConflito() throws Exception {
        UsuarioTeste organizador = cadastrar("Org");
        UUID eventoId = criarEventoValido(organizador, 10);
        // o organizador já entra inscrito ao criar o evento: uma segunda linha viola uk_inscricao_usuario_evento
        Inscricao duplicada = new Inscricao();
        duplicada.setUsuario(usuarioRepository.findById(organizador.id()).orElseThrow());
        duplicada.setEvento(eventoRepository.findById(eventoId).orElseThrow());
        duplicada.setStatusInscricao(StatusInscricao.CONFIRMADA);
        Throwable erro = catchThrowable(() ->
            transactionTemplate.executeWithoutResult(transacao -> inscricaoRepository.save(duplicada)));
        assertThat(erro).isInstanceOf(DataIntegrityViolationException.class);
        ResponseEntity<Map<String, Object>> resposta =
            exceptionHandler.handleDataIntegrity((DataIntegrityViolationException) erro);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(String.valueOf(resposta.getBody().get("message")))
            .contains("Você já tem uma inscrição neste evento")
            .doesNotContain("uk_inscricao_usuario_evento", "duplicate key", "insert into");
    }
    @Test
    @DisplayName("só confirmados ou quem está na fila podem comentar")
    void comentarioExigeInscricao() throws Exception {
        UsuarioTeste organizador = cadastrar("Org");
        UsuarioTeste visitante = cadastrar("Visitante");
        UUID eventoId = criarEventoValido(organizador, 10);
        comentar(visitante, eventoId, "Posso ir também?")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message", containsString("Apenas participantes")));
        alternarInscricao(visitante, eventoId).andExpect(status().isOk());
        comentar(visitante, eventoId, "Confirmado, estarei lá!")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.author").value(visitante.primeiroNome()));
        assertThat(buscarEvento(eventoId).get("comments").toString()).contains("Confirmado, estarei lá!");
    }
    @Test
    @DisplayName("evento com dados inválidos devolve 400 com a mensagem da regra")
    void eventoInvalidoDevolve400() throws Exception {
        UsuarioTeste organizador = cadastrar("Org");
        criarEvento(organizador.token(), "Pe", 10)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("título")));
    }
    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource({
        "date, 2026-02-30, Data inválida",
        "date, 2026-13-01, Data inválida",
        "time, 25:99, Horário inválido",
        "time, 18:60, Horário inválido"
    })
    @DisplayName("data ou horário que não existem devolvem 400 com mensagem clara, não 500")
    void dataOuHorarioInexistenteDevolve400(String campo, String valor, String mensagem) throws Exception {
        UsuarioTeste organizador = cadastrar("Org");
        Map<String, Object> corpo = corpoDeEvento("Pelada fora do calendário", 10);
        corpo.put(campo, valor);
        criarEvento(organizador.token(), corpo)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString(mensagem)))
            .andExpect(jsonPath("$.message", containsString(valor)));
    }
    @Test
    @DisplayName("evento com data que já passou devolve 400 e não é criado")
    void eventoNoPassadoDevolve400() throws Exception {
        UsuarioTeste organizador = cadastrar("Org");
        String titulo = "Pelada de ontem " + organizador.primeiroNome();
        criarEvento(organizador.token(), titulo, 10, LocalDate.now().minusDays(1))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("A data de realização deve ser uma data futura."));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM evento WHERE titulo = ?", Long.class, titulo))
            .isZero();
    }
    private ResultActions comentar(UsuarioTeste autor, UUID eventoId, String texto)
            throws Exception {
        return mockMvc.perform(post("/eventos/{id}/comentarios", eventoId)
            .header("Authorization", bearer(autor.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("text", texto))));
    }
    /**
     * Dispara um POST de inscrição por usuário, todos liberados no mesmo instante.
     * As respostas voltam na mesma ordem da lista de usuários.
     */
    private List<MockHttpServletResponse> dispararAoMesmoTempo(UUID eventoId, List<UsuarioTeste> usuarios)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(usuarios.size());
        CountDownLatch prontos = new CountDownLatch(usuarios.size());
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<MockHttpServletResponse>> pendentes = new ArrayList<>();
            for (UsuarioTeste usuario : usuarios) {
                pendentes.add(executor.submit(() -> {
                    prontos.countDown();
                    largada.await();
                    return alternarInscricao(usuario, eventoId).andReturn().getResponse();
                }));
            }
            assertThat(prontos.await(10, TimeUnit.SECONDS)).isTrue();
            largada.countDown();
            List<MockHttpServletResponse> respostas = new ArrayList<>();
            for (Future<MockHttpServletResponse> pendente : pendentes) {
                respostas.add(pendente.get(30, TimeUnit.SECONDS));
            }
            return respostas;
        } finally {
            executor.shutdownNow();
        }
    }
    private static List<String> nomes(JsonNode evento, String campo) {
        List<String> nomes = new ArrayList<>();
        evento.get(campo).forEach(nome -> nomes.add(nome.asText()));
        return nomes;
    }
}
