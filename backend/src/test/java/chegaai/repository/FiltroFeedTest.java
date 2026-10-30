package chegaai.repository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
class FiltroFeedTest {
    @Test
    @DisplayName("sem parâmetros, mostra os próximos, sem filtros, na primeira página de 12")
    void padroes() {
        FiltroFeed filtro = FiltroFeed.de(null, "todos", BigDecimal.ZERO, false, "  ", 0, 12);
        assertThat(filtro.secao()).isEqualTo(FiltroFeed.Secao.PROXIMOS);
        assertThat(filtro.categoria()).isNull();
        assertThat(filtro.honraMinima()).isNull();
        assertThat(filtro.padraoBusca()).isNull();
        assertThat(filtro.deslocamento()).isZero();
    }
    @Test
    @DisplayName("a busca ignora maiúsculas e trata %, _ e \\ como texto")
    void escapaCuringasDaBusca() {
        assertThat(FiltroFeed.de("anteriores", "Pets", null, false, " FUTEBOL ", 2, 10).padraoBusca())
            .isEqualTo("%futebol%");
        assertThat(FiltroFeed.de("proximos", null, null, false, "50%_a\\b", 0, 12).padraoBusca())
            .isEqualTo("%50\\%\\_a\\\\b%");
        assertThat(FiltroFeed.de("anteriores", null, null, false, null, 2, 10).deslocamento()).isEqualTo(20);
    }
    @Test
    @DisplayName("recusa seção, página, tamanho, honra e busca fora dos limites")
    void recusaValoresInvalidos() {
        assertThatThrownBy(() -> FiltroFeed.de("todos", null, null, false, null, 0, 12))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("secao");
        assertThatThrownBy(() -> FiltroFeed.de(null, null, null, false, null, -1, 12))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("pagina");
        assertThatThrownBy(() -> FiltroFeed.de(null, null, null, false, null, FiltroFeed.PAGINA_MAXIMA + 1, 12))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("pagina");
        assertThatThrownBy(() -> FiltroFeed.de(null, null, null, false, null, 0, 0))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("tamanho");
        assertThatThrownBy(() -> FiltroFeed.de(null, null, null, false, null, 0, FiltroFeed.TAMANHO_MAXIMO + 1))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("tamanho");
        assertThatThrownBy(() -> FiltroFeed.de(null, null, new BigDecimal("5.1"), false, null, 0, 12))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("honraMinima");
        assertThatThrownBy(() -> FiltroFeed.de(null, null, null, false, "x".repeat(FiltroFeed.BUSCA_MAXIMA + 1), 0, 12))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("busca");
    }
}
