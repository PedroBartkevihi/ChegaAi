package chegaai.limite;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import static org.assertj.core.api.Assertions.*;

class LimitadorRequisicoesTest {
    @Test
    void concorrenciaNuncaAdmiteMaisQueOsTokensDisponiveis() throws Exception {
        var clock = new RelogioLimitesTeste();
        var config = new ConfiguracaoLimites();
        config.setLoginIp(new ConfiguracaoLimites.Politica(10, Duration.ofMinutes(1)));
        var limiter = new LimitadorRequisicoes(config, clock);
        var pool = Executors.newFixedThreadPool(12);
        var inicio = new CountDownLatch(1);
        try {
            var resultados = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 100; i++) resultados.add(pool.submit(() -> {
                inicio.await();
                try { limiter.consumir(OperacaoLimite.LOGIN_IP, "origem"); return true; }
                catch (LimiteExcedidoException ex) { return false; }
            }));
            inicio.countDown();
            int aceitos = 0;
            for (var resultado : resultados) if (resultado.get()) aceitos++;
            assertThat(aceitos).isEqualTo(10);
        } finally { pool.shutdownNow(); }
    }

    @Test
    void retryAfterArredondaParaCimaERecargaUsaTempoControlado() {
        var clock = new RelogioLimitesTeste();
        var config = new ConfiguracaoLimites();
        config.setLoginIp(new ConfiguracaoLimites.Politica(2, Duration.ofSeconds(3)));
        var limiter = new LimitadorRequisicoes(config, clock);
        limiter.consumir(OperacaoLimite.LOGIN_IP, "a"); limiter.consumir(OperacaoLimite.LOGIN_IP, "a");
        assertThatThrownBy(() -> limiter.consumir(OperacaoLimite.LOGIN_IP, "a"))
            .isInstanceOfSatisfying(LimiteExcedidoException.class, ex -> assertThat(ex.getTentarAposSegundos()).isEqualTo(2));
        clock.avancar(Duration.ofMillis(1500));
        limiter.consumir(OperacaoLimite.LOGIN_IP, "a");
        assertThat(new LimiteExcedidoException(1).getTentarAposSegundos()).isEqualTo(1);
    }

    @Test
    void saturacaoPreservaChavesAtivasEExpiracaoLiberaEspacoSemLru() {
        var clock = new RelogioLimitesTeste(); var config = new ConfiguracaoLimites();
        config.setMaxChaves(2);
        var limiter = new LimitadorRequisicoes(config, clock);
        limiter.consumir(OperacaoLimite.LOGIN_IP, "a"); limiter.consumir(OperacaoLimite.LOGIN_IP, "b");
        assertThatThrownBy(() -> limiter.consumir(OperacaoLimite.LOGIN_IP, "c")).isInstanceOf(LimiteExcedidoException.class);
        limiter.consumir(OperacaoLimite.LOGIN_IP, "a");
        for (int i = 0; i < 28; i++) limiter.consumir(OperacaoLimite.LOGIN_IP, "a");
        assertThatThrownBy(() -> limiter.consumir(OperacaoLimite.LOGIN_IP, "a")).isInstanceOf(LimiteExcedidoException.class);
        clock.avancar(Duration.ofMinutes(1));
        limiter.consumir(OperacaoLimite.LOGIN_IP, "c");
        assertThat(entradas(limiter)).hasSizeLessThanOrEqualTo(2);
    }

    @Test
    void reservaEmVooNaoExpiraMesmoDepoisDoPeriodo() {
        var clock = new RelogioLimitesTeste(); var config = new ConfiguracaoLimites(); config.setMaxChaves(1);
        var limiter = new LimitadorRequisicoes(config, clock);
        var reserva = limiter.reservar(OperacaoLimite.CRIAR_EVENTO, "a");
        clock.avancar(Duration.ofDays(2));
        assertThatThrownBy(() -> limiter.reservar(OperacaoLimite.CRIAR_EVENTO, "b")).isInstanceOf(LimiteExcedidoException.class);
        reserva.close(); reserva.close();
        clock.avancar(Duration.ofDays(2));
        try (var outra = limiter.reservar(OperacaoLimite.CRIAR_EVENTO, "b")) { outra.confirmar(); }
        assertThat(entradas(limiter)).hasSize(1);
    }

    @Test
    void falhaDevolveAmbasJanelasESucessoConsomeSemDuplicarClose() {
        var clock = new RelogioLimitesTeste(); var config = new ConfiguracaoLimites();
        config.setCriarEvento(new ConfiguracaoLimites.Politica(1, Duration.ofSeconds(1), 2L, Duration.ofHours(1)));
        var limiter = new LimitadorRequisicoes(config, clock);
        var falha = limiter.reservar(OperacaoLimite.CRIAR_EVENTO, "pessoa");
        assertThatThrownBy(() -> limiter.reservar(OperacaoLimite.CRIAR_EVENTO, "pessoa")).isInstanceOf(LimiteExcedidoException.class);
        falha.close(); falha.close();
        assertThatThrownBy(falha::confirmar).isInstanceOf(IllegalStateException.class);
        var sucesso = limiter.reservar(OperacaoLimite.CRIAR_EVENTO, "pessoa");
        sucesso.confirmar(); sucesso.confirmar(); sucesso.close(); sucesso.close();
        clock.avancar(Duration.ofSeconds(1));
        try (var segunda = limiter.reservar(OperacaoLimite.CRIAR_EVENTO, "pessoa")) { segunda.confirmar(); }
        clock.avancar(Duration.ofSeconds(1));
        assertThatThrownBy(() -> limiter.reservar(OperacaoLimite.CRIAR_EVENTO, "pessoa")).isInstanceOf(LimiteExcedidoException.class);
        clock.avancar(Duration.ofHours(1));
        try (var nova = limiter.reservar(OperacaoLimite.CRIAR_EVENTO, "pessoa")) { nova.confirmar(); }
    }

    @Test
    void chavesNaoRetemIdentidadesNemColidemPorConcatenacao() {
        var limiter = new LimitadorRequisicoes(new ConfiguracaoLimites(), new RelogioLimitesTeste());
        limiter.consumir(OperacaoLimite.LOGIN_PAR, "a", "bc");
        limiter.consumir(OperacaoLimite.LOGIN_PAR, "ab", "c");
        limiter.consumir(OperacaoLimite.LOGIN_IP, "a", "bc");
        assertThat(entradas(limiter)).hasSize(3);
        assertThat(entradas(limiter).keySet()).allSatisfy(key -> assertThat(key).matches("[0-9a-f]{64}"));
    }

    @Test
    void configuracaoNaoPermiteDesligarLimitesComZeroOuRegistroSemLimite() {
        var config = new ConfiguracaoLimites(); config.setMaxChaves(10001);
        assertThatThrownBy(() -> new LimitadorRequisicoes(config, new RelogioLimitesTeste())).isInstanceOf(IllegalArgumentException.class);
        config.setMaxChaves(10); config.getLoginIp().setCapacidade(0);
        assertThatThrownBy(() -> new LimitadorRequisicoes(config, new RelogioLimitesTeste())).isInstanceOf(IllegalArgumentException.class);
    }
    @SuppressWarnings("unchecked")
    private static Map<String, ?> entradas(LimitadorRequisicoes limiter) {
        return (Map<String, ?>) ReflectionTestUtils.getField(limiter, "entradas");
    }
}
