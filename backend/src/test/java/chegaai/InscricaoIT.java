package chegaai;
import chegaai.controller.exception.GlobalExceptionHandler;
import chegaai.domain.enums.StatusInscricao;
import chegaai.domain.model.Endereco;
import chegaai.domain.model.Inscricao;
import chegaai.repository.EventoRepository;
import chegaai.repository.InscricaoRepository;
import chegaai.repository.UsuarioRepository;
import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
    @DisplayName("quem cancela e volta recebe nova posição, mesmo com datas de solicitação empatadas")
    void reentradaVaiParaOFimDaFila() throws Exception {
        UsuarioTeste organizador = cadastrar("Org");
        UsuarioTeste confirmado = cadastrar("Confirmado");
        UsuarioTeste primeiro = cadastrar("Primeiro");
        UsuarioTeste segundo = cadastrar("Segundo");
        UsuarioTeste terceiro = cadastrar("Terceiro");
        UUID eventoId = criarEventoValido(organizador, 3);
        alternarInscricao(confirmado, eventoId).andExpect(jsonPath("$.status").value("CONFIRMADO"));
        alternarInscricao(primeiro, eventoId).andExpect(jsonPath("$.status").value("CONFIRMADO"));
        alternarInscricao(primeiro, eventoId).andExpect(jsonPath("$.status").value("CANCELADO"));
        alternarInscricao(segundo, eventoId).andExpect(jsonPath("$.status").value("CONFIRMADO"));
        alternarInscricao(terceiro, eventoId).andExpect(jsonPath("$.status").value("FILA"));
        alternarInscricao(primeiro, eventoId).andExpect(jsonPath("$.status").value("FILA"));
        // Datas iguais não alteram a ordem numérica de entrada.
        jdbcTemplate.update("UPDATE inscricao SET data_hora_solicitacao = '2026-01-01T00:00:00Z' "
            + "WHERE id_evento = ?", eventoId);
        assertThat(nomes(buscarEvento(eventoId), "waitlist"))
            .containsExactly(terceiro.primeiroNome(), primeiro.primeiroNome());
        alternarInscricao(confirmado, eventoId).andExpect(jsonPath("$.status").value("CANCELADO"));
        assertThat(nomes(buscarEvento(eventoId), "confirmed"))
            .containsExactlyInAnyOrder(organizador.primeiroNome(), segundo.primeiroNome(), terceiro.primeiroNome());
        assertThat(nomes(buscarEvento(eventoId), "waitlist")).containsExactly(primeiro.primeiroNome());
        // Sair já estando na fila e voltar também perde a posição.
        alternarInscricao(confirmado, eventoId).andExpect(jsonPath("$.status").value("FILA"));
        alternarInscricao(primeiro, eventoId).andExpect(jsonPath("$.status").value("CANCELADO"));
        alternarInscricao(primeiro, eventoId).andExpect(jsonPath("$.status").value("FILA"));
        assertThat(nomes(buscarEvento(eventoId), "waitlist"))
            .containsExactly(confirmado.primeiroNome(), primeiro.primeiroNome());
    }
    @ParameterizedTest
    @ValueSource(strings = {"SUSPENSO", "BANIDO"})
    @DisplayName("promoção pula contas impedidas sem retirar sua posição e aproveita a vaga com a próxima ativa")
    void promocaoPulaContasImpedidasSemPerderPosicao(String statusConta) throws Exception {
        UsuarioTeste organizador = cadastrar("Org");
        UsuarioTeste confirmadoA = cadastrar("ConfirmadoA");
        UsuarioTeste confirmadoB = cadastrar("ConfirmadoB");
        UsuarioTeste impedido = cadastrar("Impedido");
        UsuarioTeste ativo = cadastrar("Ativo");
        UsuarioTeste novo = cadastrar("Novo");
        UUID evento = criarEventoValido(organizador, 3);
        alternarInscricao(confirmadoA, evento).andExpect(jsonPath("$.status").value("CONFIRMADO"));
        alternarInscricao(confirmadoB, evento).andExpect(jsonPath("$.status").value("CONFIRMADO"));
        alternarInscricao(impedido, evento).andExpect(jsonPath("$.status").value("FILA"));
        alternarInscricao(ativo, evento).andExpect(jsonPath("$.status").value("FILA"));
        Long ordemAnterior = ordemDaInscricao(impedido, evento);
        jdbcTemplate.update("UPDATE usuario SET status_usuario=?, suspenso_ate=NULL WHERE id_usuario=?",
            statusConta, impedido.id());

        alternarInscricao(confirmadoA, evento).andExpect(jsonPath("$.status").value("CANCELADO"));
        assertThat(nomes(buscarEvento(evento), "confirmed"))
            .containsExactlyInAnyOrder(organizador.primeiroNome(), confirmadoB.primeiroNome(), ativo.primeiroNome());
        assertThat(nomes(buscarEvento(evento), "waitlist")).containsExactly(impedido.primeiroNome());

        alternarInscricao(confirmadoB, evento).andExpect(jsonPath("$.status").value("CANCELADO"));
        alternarInscricao(novo, evento).andExpect(jsonPath("$.status").value("CONFIRMADO"));
        assertThat(nomes(buscarEvento(evento), "confirmed"))
            .containsExactlyInAnyOrder(organizador.primeiroNome(), ativo.primeiroNome(), novo.primeiroNome());
        assertThat(nomes(buscarEvento(evento), "waitlist")).containsExactly(impedido.primeiroNome());
        assertThat(ordemDaInscricao(impedido, evento)).isEqualTo(ordemAnterior);
    }
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("suspensão vencida recupera prioridade antes de uma nova entrada ou reentrada")
    void suspensaoVencidaTemPrioridadeSobreNovaEntrada(boolean reentrada) throws Exception {
        UsuarioTeste organizador = cadastrar("Org");
        UsuarioTeste confirmadoA = cadastrar("ConfirmadoA");
        UsuarioTeste confirmadoB = cadastrar("ConfirmadoB");
        UsuarioTeste esperando = cadastrar("Esperando");
        UsuarioTeste novo = cadastrar("Novo");
        UUID evento = criarEventoValido(organizador, 3);
        if (reentrada) {
            alternarInscricao(novo, evento).andExpect(jsonPath("$.status").value("CONFIRMADO"));
            alternarInscricao(novo, evento).andExpect(jsonPath("$.status").value("CANCELADO"));
        }
        alternarInscricao(confirmadoA, evento).andExpect(jsonPath("$.status").value("CONFIRMADO"));
        alternarInscricao(confirmadoB, evento).andExpect(jsonPath("$.status").value("CONFIRMADO"));
        alternarInscricao(esperando, evento).andExpect(jsonPath("$.status").value("FILA"));
        Long ordemAnterior = ordemDaInscricao(esperando, evento);
        jdbcTemplate.update("UPDATE usuario SET status_usuario='SUSPENSO', suspenso_ate=now()+interval '1 day' "
            + "WHERE id_usuario=?", esperando.id());
        alternarInscricao(confirmadoA, evento).andExpect(jsonPath("$.status").value("CANCELADO"));
        assertThat(nomes(buscarEvento(evento), "confirmed"))
            .containsExactlyInAnyOrder(organizador.primeiroNome(), confirmadoB.primeiroNome());
        jdbcTemplate.update("UPDATE usuario SET suspenso_ate=now()-interval '1 second' WHERE id_usuario=?", esperando.id());

        alternarInscricao(novo, evento).andExpect(jsonPath("$.status").value("FILA"));

        assertThat(nomes(buscarEvento(evento), "confirmed"))
            .containsExactlyInAnyOrder(organizador.primeiroNome(), confirmadoB.primeiroNome(), esperando.primeiroNome());
        assertThat(nomes(buscarEvento(evento), "waitlist")).containsExactly(novo.primeiroNome());
        assertThat(ordemDaInscricao(esperando, evento)).isEqualTo(ordemAnterior);
    }
    private Long ordemDaInscricao(UsuarioTeste pessoa, UUID evento) {
        return jdbcTemplate.queryForObject("SELECT ordem_entrada FROM inscricao WHERE id_usuario=? AND id_evento=?",
            Long.class, pessoa.id(), evento);
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
    @ParameterizedTest(name = "{0}: até {1} caracteres")
    @CsvSource({
        "title, 80, título",
        "place, 90, local",
        "description, 260, descrição"
    })
    @DisplayName("título, local e descrição seguem os limites do formulário do site")
    void camposSeguemOsLimitesDoSite(String campo, int limite, String mensagem) throws Exception {
        UsuarioTeste organizador = cadastrar("Org");
        Map<String, Object> corpo = corpoDeEvento("Pelada no limite", 10);
        corpo.put(campo, "x".repeat(limite + 1));
        criarEvento(organizador.token(), corpo)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString(mensagem)));
        corpo.put(campo, "x".repeat(limite));
        criarEvento(organizador.token(), corpo).andExpect(status().isCreated());
    }
    @Test
    @DisplayName("comentário segue o limite do site: 180 caracteres")
    void comentarioSegueOLimiteDoSite() throws Exception {
        UsuarioTeste organizador = cadastrar("Org");
        UUID eventoId = criarEventoValido(organizador, 10);
        comentar(organizador, eventoId, "x".repeat(181))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("até 180 caracteres")));
        comentar(organizador, eventoId, "x".repeat(180)).andExpect(status().isCreated());
    }
    @ParameterizedTest(name = "CEP \"{0}\"")
    @ValueSource(strings = {"", "123"})
    @DisplayName("sem CEP válido, a API devolve o CEP vazio, e o banco guarda o 00000000 de sempre, sem migração")
    void semCepValidoDevolveCepVazio(String cep) throws Exception {
        UsuarioTeste organizador = cadastrar("Org");
        Map<String, Object> corpo = corpoDeEvento("Pelada sem CEP", 10);
        corpo.put("cep", cep);
        JsonNode criado = json(criarEvento(organizador.token(), corpo).andExpect(status().isCreated()));
        UUID eventoId = UUID.fromString(criado.get("id").asText());
        assertThat(criado.get("cep").asText()).isEmpty();
        assertThat(buscarEvento(eventoId).get("cep").asText()).isEmpty();
        assertThat(jdbcTemplate.queryForObject("SELECT en.cep FROM evento ev JOIN endereco en "
            + "ON en.id_endereco = ev.id_endereco WHERE ev.id_evento = ?", String.class, eventoId))
            .isEqualTo(Endereco.CEP_NAO_INFORMADO);
    }
    @Test
    @DisplayName("a distância saiu da API, e o site publicado antes dessa mudança, que ainda a envia, continua criando rolés")
    void distanciaSaiuEOSiteAntigoContinuaCriando() throws Exception {
        UsuarioTeste organizador = cadastrar("Org");
        Map<String, Object> corpo = corpoDeEvento("Pelada com distância", 10);
        corpo.put("distance", 3);
        JsonNode criado = json(criarEvento(organizador.token(), corpo).andExpect(status().isCreated()));
        assertThat(criado.has("distance")).isFalse();
        assertThat(buscarEvento(UUID.fromString(criado.get("id").asText())).has("distance")).isFalse();
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
