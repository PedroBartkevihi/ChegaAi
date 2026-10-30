package chegaai;

import chegaai.controller.dto.EventoRequestDTO;
import chegaai.limite.RelogioLimites;
import chegaai.service.ComentarioService;
import chegaai.service.EventoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Janelas reais, capacidades reduzidas: o relógio avança somente no limitador, não no domínio.
@SpringBootTest(properties = {
    "chegaai.limites.criar-evento.capacidade=2", "chegaai.limites.criar-evento.periodo=PT1H",
    "chegaai.limites.criar-evento.longo-capacidade=3", "chegaai.limites.criar-evento.longo-periodo=PT24H",
    "chegaai.limites.comentar.capacidade=2", "chegaai.limites.comentar.periodo=PT1M",
    "chegaai.limites.comentar.longo-capacidade=3", "chegaai.limites.comentar.longo-periodo=PT1H"
})
@Import(LimitesEscritaIT.RelogioConfiguracao.class)
class LimitesEscritaIT extends IntegracaoBase {
    @Autowired RelogioTeste relogio;
    @Autowired EventoService eventoService;
    @Autowired ComentarioService comentarioService;

    @Test
    void criacaoLimitaPorContaSemGravarRecusadoNemSuspender() throws Exception {
        var org = cadastrar("CotaCriar");
        var outro = cadastrar("CotaCriarOutro");
        criarEvento(org.token(), "Primeiro da conta", 10).andExpect(status().isCreated());
        criarEvento(org.token(), "Segundo da conta", 10).andExpect(status().isCreated());
        long antes = eventosDe(org.id());
        limite(criarEvento(org.token(), "Terceiro recusado", 10));
        assertThat(eventosDe(org.id())).isEqualTo(antes).isEqualTo(2);
        criarEvento(outro.token(), "Primeiro de outra conta", 10).andExpect(status().isCreated());
        criarEvento(outro.token(), "Segundo de outra conta", 10).andExpect(status().isCreated());
        assertThat(eventosDe(outro.id())).isEqualTo(2);
        contaAtiva(org);
    }

    @Test
    void criacaoRespeitaJanelaLongaDepoisDeRecuperarACurta() throws Exception {
        var org = cadastrar("CotaDia");
        for (int i = 0; i < 2; i++) criarEvento(org.token(), "Evento inicial " + i, 10).andExpect(status().isCreated());
        limite(criarEvento(org.token(), "Sem cota da hora", 10));
        relogio.avancar(Duration.ofHours(1));
        criarEvento(org.token(), "Terceiro no dia", 10).andExpect(status().isCreated());
        limite(criarEvento(org.token(), "Sem cota do dia", 10));
        relogio.avancar(Duration.ofHours(1));
        limite(criarEvento(org.token(), "Dia ainda esgotado", 10));
        assertThat(eventosDe(org.id())).isEqualTo(3);
        relogio.avancar(Duration.ofHours(24));
        for (int i = 0; i < 2; i++) criarEvento(org.token(), "Novo período " + i, 10).andExpect(status().isCreated());
        assertThat(eventosDe(org.id())).isEqualTo(5);
    }

    @Test
    void criacaoComValidacaoConflitoOuContaSuspensaNaoConsome() throws Exception {
        var org = cadastrar("CotaFalhaCriar");
        var invalido = corpoDeEvento("", 10);
        for (int i = 0; i < 3; i++) criarEvento(org.token(), invalido).andExpect(status().isBadRequest());
        jdbcTemplate.update("UPDATE usuario SET honra_media=1 WHERE id_usuario=?", org.id());
        for (int i = 0; i < 3; i++) criarEvento(org.token(), "Honra insuficiente", 10).andExpect(status().isConflict());
        jdbcTemplate.update("UPDATE usuario SET honra_media=3,status_usuario='SUSPENSO',suspenso_ate=NULL WHERE id_usuario=?", org.id());
        for (int i = 0; i < 3; i++) criarEvento(org.token(), "Conta suspensa", 10).andExpect(status().isForbidden());
        assertThat(eventosDe(org.id())).isZero();
        jdbcTemplate.update("UPDATE usuario SET status_usuario='ATIVO' WHERE id_usuario=?", org.id());
        for (int i = 0; i < 2; i++) criarEvento(org.token(), "Criado após falhas " + i, 10).andExpect(status().isCreated());
        limite(criarEvento(org.token(), "Agora atingiu a cota", 10));
        assertThat(eventosDe(org.id())).isEqualTo(2);
    }

    @Test
    void comentarioAgregaEntreRolesEUsaUuidDoToken() throws Exception {
        var org = cadastrar("CotaComentarioOrg");
        var alfa = cadastrar("CotaComentarioAlfa");
        var beta = cadastrar("CotaComentarioBeta");
        UUID primeiro = criarEventoValido(org, 10), segundo = criarEventoValido(org, 10);
        for (var pessoa : List.of(alfa, beta)) {
            alternarInscricao(pessoa, primeiro).andExpect(status().isOk());
            alternarInscricao(pessoa, segundo).andExpect(status().isOk());
        }
        String prefixo = "agregado-" + UUID.randomUUID();
        comentar(alfa, primeiro, prefixo + "-alfa1").andExpect(status().isCreated());
        comentar(alfa, segundo, prefixo + "-alfa2").andExpect(status().isCreated());
        limite(mockMvc.perform(post("/eventos/{id}/comentarios", segundo)
            .header("Authorization", bearer(alfa.token())).header("X-User-Id", beta.id())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("text", prefixo + "-recusado")))));
        assertThat(comentariosDe(alfa.id(), prefixo)).isEqualTo(2);
        comentar(beta, primeiro, prefixo + "-beta1").andExpect(status().isCreated());
        comentar(beta, segundo, prefixo + "-beta2").andExpect(status().isCreated());
        assertThat(comentariosDe(beta.id(), prefixo)).isEqualTo(2);
        contaAtiva(alfa);
    }

    @Test
    void comentarioRespeitaJanelaLongaDepoisDeRecuperarACurta() throws Exception {
        var org = cadastrar("CotaComentarioHora");
        UUID evento = criarEventoValido(org, 10);
        String prefixo = "janela-" + UUID.randomUUID();
        for (int i = 0; i < 2; i++) comentar(org, evento, prefixo + "-inicial" + i).andExpect(status().isCreated());
        limite(comentar(org, evento, prefixo + "-minuto"));
        relogio.avancar(Duration.ofMinutes(1));
        comentar(org, evento, prefixo + "-terceiro").andExpect(status().isCreated());
        limite(comentar(org, evento, prefixo + "-hora"));
        relogio.avancar(Duration.ofMinutes(1));
        limite(comentar(org, evento, prefixo + "-hora-ainda"));
        assertThat(comentariosDe(org.id(), prefixo)).isEqualTo(3);
        relogio.avancar(Duration.ofHours(1));
        for (int i = 0; i < 2; i++) comentar(org, evento, prefixo + "-novo" + i).andExpect(status().isCreated());
        assertThat(comentariosDe(org.id(), prefixo)).isEqualTo(5);
    }

    @Test
    void comentarioComFalhasNaoConsomeCota() throws Exception {
        var org = cadastrar("CotaComentarioFalhaOrg");
        var pessoa = cadastrar("CotaComentarioFalhaPessoa");
        UUID evento = criarEventoValido(org, 10);
        String prefixo = "falha-" + UUID.randomUUID();
        for (int i = 0; i < 3; i++) comentar(pessoa, evento, prefixo + "-sem-inscricao").andExpect(status().isConflict());
        alternarInscricao(pessoa, evento).andExpect(status().isOk());
        jdbcTemplate.update("UPDATE usuario SET status_usuario='SUSPENSO',suspenso_ate=NULL WHERE id_usuario=?", pessoa.id());
        for (int i = 0; i < 3; i++) comentar(pessoa, evento, prefixo + "-suspenso").andExpect(status().isForbidden());
        jdbcTemplate.update("UPDATE usuario SET status_usuario='ATIVO' WHERE id_usuario=?", pessoa.id());
        for (int i = 0; i < 3; i++) comentar(pessoa, evento, "").andExpect(status().isBadRequest());
        assertThat(comentariosDe(pessoa.id(), prefixo)).isZero();
        for (int i = 0; i < 2; i++) comentar(pessoa, evento, prefixo + "-valido" + i).andExpect(status().isCreated());
        limite(comentar(pessoa, evento, prefixo + "-limite"));
        assertThat(comentariosDe(pessoa.id(), prefixo)).isEqualTo(2);
    }

    @Test
    void criacoesConcorrentesNaoUltrapassamACota() throws Exception {
        var org = cadastrar("CotaCriarConcorrente");
        List<Callable<Integer>> tarefas = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            String titulo = "Criação concorrente " + i;
            tarefas.add(() -> criarEvento(org.token(), titulo, 10).andReturn().getResponse().getStatus());
        }
        assertThat(executarJuntas(tarefas)).containsExactlyInAnyOrder(201, 201, 429, 429, 429, 429);
        assertThat(eventosDe(org.id())).isEqualTo(2);
        contaAtiva(org);
    }

    @Test
    void comentariosConcorrentesNaoUltrapassamACota() throws Exception {
        var org = cadastrar("CotaComentarioConcorrente");
        UUID evento = criarEventoValido(org, 10);
        String prefixo = "concorrente-" + UUID.randomUUID();
        List<Callable<Integer>> tarefas = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            String texto = prefixo + "-" + i;
            tarefas.add(() -> comentar(org, evento, texto).andReturn().getResponse().getStatus());
        }
        assertThat(executarJuntas(tarefas)).containsExactlyInAnyOrder(201, 201, 429, 429, 429, 429);
        assertThat(comentariosDe(org.id(), prefixo)).isEqualTo(2);
    }

    @Test
    void servicosInternosEComentariosAutomaticosNaoConsomemCotasHttp() throws Exception {
        var org = cadastrar("CotaInterna");
        UUID evento = null;
        for (int i = 0; i < 3; i++) {
            var dto = objectMapper.convertValue(corpoDeEvento("Criação interna " + i, 10), EventoRequestDTO.class);
            evento = eventoService.criarEvento(dto, org.id()).id();
        }
        for (int i = 0; i < 3; i++) comentarioService.adicionarComentario(evento, org.id(), "Comentário interno " + i);
        for (int i = 0; i < 2; i++) criarEvento(org.token(), "Criação HTTP " + i, 10).andExpect(status().isCreated());
        limite(criarEvento(org.token(), "Criação HTTP recusada", 10));
        String prefixo = "http-" + UUID.randomUUID();
        for (int i = 0; i < 2; i++) comentar(org, evento, prefixo + i).andExpect(status().isCreated());
        limite(comentar(org, evento, prefixo + "-recusado"));
        assertThat(eventosDe(org.id())).isEqualTo(5);
        assertThat(comentariosDe(org.id(), prefixo)).isEqualTo(2);
    }

    @Test
    void contasDemoRecebemAsMesmasCotas() throws Exception {
        String token = login("pedro@chegaai.dev", "admin123");
        UUID id = UUID.fromString(json(mockMvc.perform(get("/auth/me").header("Authorization", bearer(token)))
            .andExpect(status().isOk())).get("id").asText());
        long antes = eventosDe(id);
        for (int i = 0; i < 2; i++) criarEvento(token, "Cota de demonstração " + i, 10).andExpect(status().isCreated());
        limite(criarEvento(token, "Demo também tem limite", 10));
        assertThat(eventosDe(id)).isEqualTo(antes + 2);
    }

    @Test
    void cotasDeEscritaNaoBloqueiamOsDemaisFluxos() throws Exception {
        var org = cadastrar("CotaOutrosFluxosOrg");
        var outro = cadastrar("CotaOutrosFluxosOutro");
        UUID primeiro = criarEventoValido(org, 10);
        criarEventoValido(org, 10);
        String prefixo = "esgotar-" + UUID.randomUUID();
        for (int i = 0; i < 2; i++) comentar(org, primeiro, prefixo + i).andExpect(status().isCreated());
        limite(criarEvento(org.token(), "Criação esgotada", 10));
        limite(comentar(org, primeiro, prefixo + "-recusado"));
        mockMvc.perform(get("/eventos")).andExpect(status().isOk());
        usuarioAtual(org);
        mockMvc.perform(get("/auth/me/inscricoes").header("Authorization", bearer(org.token()))).andExpect(status().isOk());
        mockMvc.perform(get("/auth/me/avaliacoes-pendentes").header("Authorization", bearer(org.token()))).andExpect(status().isOk());
        UUID eventoAlheio = criarEventoValido(outro, 10);
        alternarInscricao(org, eventoAlheio).andExpect(status().isOk());
        alternarInscricao(org, eventoAlheio).andExpect(status().isOk());
        alternarInscricao(outro, primeiro).andExpect(status().isOk());
        moverParaOPassado(primeiro);
        registrarPresenca(org, primeiro, outro).andExpect(status().isNoContent());
        concluir(org, primeiro).andExpect(status().isOk());
        mockMvc.perform(post("/eventos/{id}/avaliacoes", primeiro).header("Authorization", bearer(org.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("avaliadoId", outro.id(), "nota", 5))))
            .andExpect(status().isCreated());
        contaAtiva(org);
    }

    private ResultActions comentar(UsuarioTeste pessoa, UUID evento, String texto) throws Exception {
        return mockMvc.perform(post("/eventos/{id}/comentarios", evento).header("Authorization", bearer(pessoa.token()))
            .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("text", texto))));
    }
    private void limite(ResultActions resposta) throws Exception {
        var response = resposta.andExpect(status().isTooManyRequests())
            .andExpect(header().string("Retry-After", matchesPattern("[1-9][0-9]*")))
            .andExpect(jsonPath("$.status").value(429)).andReturn().getResponse();
        assertThat(objectMapper.readTree(response.getContentAsString()).get("message").asText()).isNotBlank();
    }
    private long eventosDe(UUID pessoa) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM evento WHERE id_organizador=?", Long.class, pessoa);
    }
    private long comentariosDe(UUID pessoa, String prefixo) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM comentario WHERE id_usuario=? AND texto LIKE ?", Long.class, pessoa, prefixo + "%");
    }
    private void contaAtiva(UsuarioTeste pessoa) {
        assertThat(jdbcTemplate.queryForObject("SELECT status_usuario FROM usuario WHERE id_usuario=?", String.class, pessoa.id())).isEqualTo("ATIVO");
        assertThat(jdbcTemplate.queryForObject("SELECT suspenso_ate FROM usuario WHERE id_usuario=?", Object.class, pessoa.id())).isNull();
    }
    private List<Integer> executarJuntas(List<Callable<Integer>> tarefas) throws Exception {
        var executor = Executors.newFixedThreadPool(tarefas.size());
        var prontas = new CountDownLatch(tarefas.size());
        var largada = new CountDownLatch(1);
        try {
            var resultados = new ArrayList<java.util.concurrent.Future<Integer>>();
            for (var tarefa : tarefas) resultados.add(executor.submit(() -> {
                prontas.countDown();
                if (!largada.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Largada não liberada.");
                return tarefa.call();
            }));
            assertThat(prontas.await(10, TimeUnit.SECONDS)).isTrue();
            largada.countDown();
            var statuses = new ArrayList<Integer>();
            for (var resultado : resultados) statuses.add(resultado.get(30, TimeUnit.SECONDS));
            return statuses;
        } finally { executor.shutdownNow(); }
    }
    @TestConfiguration
    static class RelogioConfiguracao {
        @Bean @Primary RelogioTeste relogioLimitesTeste() { return new RelogioTeste(); }
    }
    static final class RelogioTeste implements RelogioLimites {
        private final AtomicLong nanos = new AtomicLong();
        @Override public long agoraNanos() { return nanos.get(); }
        void avancar(Duration duracao) { nanos.addAndGet(duracao.toNanos()); }
    }
}
