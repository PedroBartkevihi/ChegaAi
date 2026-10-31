package chegaai;

import tools.jackson.databind.JsonNode;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O banco é compartilhado entre as classes de teste: cada teste usa uma marca própria no título
 * e filtra o feed por ela, e o resumo global é conferido pela diferença antes e depois.
 */
class FeedIT extends IntegracaoBase {
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("páginas seguem a data e não repetem nem pulam rolés")
    void paginasSemRepetirNemPular() throws Exception {
        String marca = marca();
        var org = cadastrar("FeedOrdem");
        List<String> esperados = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            esperados.add(criar(org, "Rolé " + marca + " " + i, "Futebol", LocalDate.now().plusDays(i), 10).toString());
        }
        JsonNode primeira = feed("busca", marca, "tamanho", "3");
        JsonNode segunda = feed("busca", marca, "tamanho", "3", "pagina", "1");
        JsonNode terceira = feed("busca", marca, "tamanho", "3", "pagina", "2");
        assertThat(primeira.get("total").asLong()).isEqualTo(7);
        assertThat(primeira.get("hasNext").asBoolean()).isTrue();
        assertThat(segunda.get("page").asInt()).isEqualTo(1);
        assertThat(terceira.get("hasNext").asBoolean()).isFalse();
        List<String> todos = new ArrayList<>(ids(primeira));
        todos.addAll(ids(segunda));
        todos.addAll(ids(terceira));
        assertThat(todos).containsExactlyElementsOf(esperados);
    }

    @Test
    @DisplayName("na mesma data, o organizador com mais honra vem antes, como na tela")
    void desempatePelaHonra() throws Exception {
        String marca = marca();
        var comum = cadastrar("FeedComum");
        var experiente = cadastrar("FeedExperiente");
        jdbcTemplate.update("UPDATE usuario SET honra_media = 4.5 WHERE id_usuario = ?", experiente.id());
        LocalDate data = LocalDate.now().plusDays(3);
        UUID doComum = criar(comum, "Encontro " + marca + " A", "Futebol", data, 10);
        UUID doExperiente = criar(experiente, "Encontro " + marca + " B", "Futebol", data, 10);
        assertThat(ids(feed("busca", marca))).containsExactly(doExperiente.toString(), doComum.toString());
        assertThat(ids(feed("busca", marca, "secao", "anteriores"))).isEmpty();
    }

    @Test
    @DisplayName("próximos são só os abertos que ainda vão começar; o resto vai para anteriores")
    void secoes() throws Exception {
        String marca = marca();
        var org = cadastrar("FeedSecao");
        UUID aberto = criar(org, "Aberto " + marca, "Futebol", LocalDate.now().plusDays(2), 10);
        UUID travado = criar(org, "Travado " + marca, "Futebol", LocalDate.now().plusDays(3), 10);
        UUID cancelado = criar(org, "Cancelado " + marca, "Futebol", LocalDate.now().plusDays(4), 10);
        UUID comecou = criar(org, "Começou " + marca, "Futebol", LocalDate.now().plusDays(5), 10);
        jdbcTemplate.update("UPDATE evento SET travado = true WHERE id_evento = ?", travado);
        jdbcTemplate.update("UPDATE evento SET status_evento = 'CANCELADO' WHERE id_evento = ?", cancelado);
        moverParaOPassado(comecou);
        assertThat(ids(feed("busca", marca))).containsExactly(aberto.toString());
        JsonNode anteriores = feed("busca", marca, "secao", "anteriores");
        // Data decrescente: os indisponíveis com data futura primeiro, o que já começou por último.
        assertThat(ids(anteriores)).containsExactly(cancelado.toString(), travado.toString(), comecou.toString());
        assertThat(anteriores.at("/items/0/status").asText()).isEqualTo("CANCELADO");
        assertThat(anteriores.at("/items/1/status").asText()).isEqualTo("TRAVADO");
        assertThat(anteriores.at("/items/2/status").asText()).isEqualTo("ABERTO");
    }

    @Test
    @DisplayName("filtra por categoria, honra mínima e Embaixador")
    void filtros() throws Exception {
        String marca = marca();
        var comum = cadastrar("FeedFiltro");
        var embaixadora = cadastrar("FeedEmbaixadora");
        jdbcTemplate.update("UPDATE usuario SET honra_media = 4.9 WHERE id_usuario = ?", embaixadora.id());
        criar(comum, "Futebol " + marca, "Futebol", LocalDate.now().plusDays(2), 10);
        UUID pets = criar(comum, "Pets " + marca, "Pets", LocalDate.now().plusDays(3), 10);
        UUID passeio = criar(embaixadora, "Passeio " + marca, "Passeio", LocalDate.now().plusDays(4), 10);
        assertThat(ids(feed("busca", marca, "categoria", "Pets"))).containsExactly(pets.toString());
        assertThat(ids(feed("busca", marca, "categoria", "todos"))).hasSize(3);
        assertThat(ids(feed("busca", marca, "honraMinima", "4.5"))).containsExactly(passeio.toString());
        JsonNode deEmbaixadores = feed("busca", marca, "embaixador", "true");
        assertThat(ids(deEmbaixadores)).containsExactly(passeio.toString());
        assertThat(deEmbaixadores.at("/items/0/organizer/isAmbassador").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("a busca ignora maiúsculas, atravessa campos, usa o primeiro nome e trata curingas como texto")
    void busca() throws Exception {
        String marca = marca();
        var org = cadastrar("Feedbusca");
        UUID evento = criar(org, "Caminhada " + marca, "Futebol", LocalDate.now().plusDays(2), 10);
        // O texto da tela juntava título e categoria: "caminhada <marca> futebol ...".
        assertThat(ids(feed("busca", marca.toUpperCase() + " futebol"))).containsExactly(evento.toString());
        assertThat(ids(feed("busca", org.primeiroNome().toLowerCase()))).containsExactly(evento.toString());
        assertThat(feed("busca", marca + "%").get("total").asLong()).isZero();
        assertThat(feed("busca", marca.substring(0, 6) + "_" + marca.substring(7)).get("total").asLong()).isZero();
    }

    @Test
    @DisplayName("as contagens do feed batem com o detalhe, que continua trazendo nomes e comentários")
    void contagensBatemComODetalhe() throws Exception {
        String marca = marca();
        var org = cadastrar("FeedConta");
        UUID evento = criar(org, "Lotado " + marca, "Futebol", LocalDate.now().plusDays(2), 3);
        for (int i = 0; i < 3; i++) {
            alternarInscricao(cadastrar("FeedVaga"), evento).andExpect(status().isOk());
        }
        JsonNode item = feed("busca", marca).at("/items/0");
        JsonNode detalhe = buscarEvento(evento);
        assertThat(item.get("confirmedCount").asLong()).isEqualTo(3).isEqualTo(detalhe.get("confirmed").size());
        assertThat(item.get("waitlistCount").asLong()).isEqualTo(1).isEqualTo(detalhe.get("waitlist").size());
        assertThat(item.has("confirmed")).isFalse();
        assertThat(item.has("comments")).isFalse();
        assertThat(item.has("distance")).isFalse();
        assertThat(detalhe.has("distance")).isFalse();
        assertThat(detalhe.get("comments").size()).isPositive();
    }

    @Test
    @DisplayName("uma página faz no máximo três consultas, qualquer que seja o número de rolés")
    void consultasPorPagina() throws Exception {
        String marca = marca();
        var org = cadastrar("FeedConsultas");
        for (int i = 1; i <= 14; i++) {
            criar(org, "Consulta " + marca + " " + i, "Futebol", LocalDate.now().plusDays(i), 10);
        }
        Statistics estatisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean ligadas = estatisticas.isStatisticsEnabled();
        estatisticas.setStatisticsEnabled(true);
        try {
            estatisticas.clear();
            JsonNode pagina = feed("busca", marca, "tamanho", "12");
            assertThat(pagina.get("items").size()).isEqualTo(12);
            assertThat(pagina.get("hasNext").asBoolean()).isTrue();
            // A página, as contagens agrupadas e o total; antes eram 3 por rolé.
            assertThat(estatisticas.getPrepareStatementCount()).isPositive().isLessThanOrEqualTo(3);
        } finally {
            estatisticas.setStatisticsEnabled(ligadas);
        }
    }

    @Test
    @DisplayName("o resumo conta abertos, fila e rolés de Embaixadores como a tela calculava")
    void resumo() throws Exception {
        JsonNode antes = json(mockMvc.perform(get("/eventos/resumo")).andExpect(status().isOk()));
        var embaixadora = cadastrar("FeedResumo");
        jdbcTemplate.update("UPDATE usuario SET honra_media = 4.9 WHERE id_usuario = ?", embaixadora.id());
        UUID evento = criar(embaixadora, "Resumo " + marca(), "Futebol", LocalDate.now().plusDays(2), 3);
        for (int i = 0; i < 3; i++) {
            alternarInscricao(cadastrar("FeedResumoVaga"), evento).andExpect(status().isOk());
        }
        JsonNode depois = json(mockMvc.perform(get("/eventos/resumo")).andExpect(status().isOk()));
        assertThat(depois.get("total").asLong() - antes.get("total").asLong()).isEqualTo(1);
        assertThat(depois.get("active").asLong() - antes.get("active").asLong()).isEqualTo(1);
        assertThat(depois.get("waitlist").asLong() - antes.get("waitlist").asLong()).isEqualTo(1);
        assertThat(depois.get("ambassadors").asLong() - antes.get("ambassadors").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("as inscrições do perfil trazem o resumo do rolé")
    void perfilTrazResumo() throws Exception {
        String marca = marca();
        var org = cadastrar("FeedPerfilOrg");
        var pessoa = cadastrar("FeedPerfil");
        UUID evento = criar(org, "Perfil " + marca, "Futebol", LocalDate.now().plusDays(2), 10);
        alternarInscricao(pessoa, evento).andExpect(status().isOk());
        JsonNode inscricoes = json(mockMvc.perform(get("/auth/me/inscricoes")
            .header("Authorization", bearer(pessoa.token()))).andExpect(status().isOk()));
        assertThat(inscricoes.size()).isEqualTo(1);
        assertThat(inscricoes.at("/0/eventoId").asText()).isEqualTo(evento.toString());
        assertThat(inscricoes.at("/0/status").asText()).isEqualTo("CONFIRMADO");
        assertThat(inscricoes.at("/0/evento/title").asText()).isEqualTo("Perfil " + marca);
        assertThat(inscricoes.at("/0/evento/confirmedCount").asLong()).isEqualTo(2);
    }

    @Test
    @DisplayName("parâmetros fora dos limites respondem 400")
    void parametrosInvalidos() throws Exception {
        mockMvc.perform(pedido("tamanho", "0")).andExpect(status().isBadRequest());
        mockMvc.perform(pedido("tamanho", "51")).andExpect(status().isBadRequest());
        mockMvc.perform(pedido("pagina", "-1")).andExpect(status().isBadRequest());
        mockMvc.perform(pedido("pagina", "abc")).andExpect(status().isBadRequest());
        mockMvc.perform(pedido("secao", "todos")).andExpect(status().isBadRequest());
        mockMvc.perform(pedido("honraMinima", "5.5")).andExpect(status().isBadRequest());
        mockMvc.perform(pedido("busca", "x".repeat(101))).andExpect(status().isBadRequest());
    }

    private static String marca() {
        return "feedit" + UUID.randomUUID().toString().substring(0, 8);
    }

    private UUID criar(UsuarioTeste organizador, String titulo, String categoria, LocalDate data, int capacidade)
            throws Exception {
        Map<String, Object> corpo = corpoDeEvento(titulo, capacidade);
        corpo.put("category", categoria);
        corpo.put("date", data.toString());
        return UUID.fromString(json(criarEvento(organizador.token(), corpo).andExpect(status().isCreated()))
            .get("id").asText());
    }

    private JsonNode feed(String... parametros) throws Exception {
        return json(mockMvc.perform(pedido(parametros)).andExpect(status().isOk()));
    }

    private static MockHttpServletRequestBuilder pedido(String... parametros) {
        MockHttpServletRequestBuilder pedido = get("/eventos/feed");
        for (int i = 0; i < parametros.length; i += 2) {
            pedido.param(parametros[i], parametros[i + 1]);
        }
        return pedido;
    }

    private static List<String> ids(JsonNode pagina) {
        List<String> ids = new ArrayList<>();
        pagina.get("items").forEach(item -> ids.add(item.get("id").asText()));
        return ids;
    }
}
