package chegaai.domain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
class CpfTest {
    @ParameterizedTest
    @ValueSource(strings = {"52998224725", "11144477735", "39053344705"})
    @DisplayName("aceita CPFs com dígitos verificadores corretos")
    void aceitaCpfValido(String cpf) {
        assertThat(Cpf.valido(cpf)).isTrue();
    }
    @ParameterizedTest
    @ValueSource(strings = {"52998224726", "12345678900", "11111111111", "1234567890", "529.982.247-25", "abcdefghijk"})
    @DisplayName("recusa dígito errado, sequência repetida e formato diferente de 11 números")
    void recusaCpfInvalido(String cpf) {
        assertThat(Cpf.valido(cpf)).isFalse();
    }
    @ParameterizedTest
    @NullSource
    @DisplayName("recusa CPF nulo")
    void recusaNulo(String cpf) {
        assertThat(Cpf.valido(cpf)).isFalse();
    }
    @Test
    @DisplayName("CPFs fictícios dos dados de demonstração não passariam no cadastro")
    void cpfsDeDemonstracaoSaoInvalidos() {
        assertThat(Cpf.valido("12345678901")).isFalse();
    }
}
