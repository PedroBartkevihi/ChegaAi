package chegaai.config;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
class SecurityConfigTest {
    private final SecurityConfig config = new SecurityConfig();
    @Test
    @DisplayName("sem segredo configurado, gera uma chave aleatória diferente a cada inicialização")
    void semSegredoGeraChaveAleatoria() {
        SecretKey primeira = config.jwtSecretKey("");
        SecretKey segunda = config.jwtSecretKey("  ");
        assertThat(primeira.getEncoded()).hasSize(32);
        assertThat(primeira.getEncoded()).isNotEqualTo(segunda.getEncoded());
    }
    @Test
    @DisplayName("usa o segredo configurado quando ele tem pelo menos 32 bytes")
    void usaSegredoConfigurado() {
        String segredo = "um-segredo-de-producao-com-mais-de-32-bytes";
        assertThat(config.jwtSecretKey(segredo).getEncoded()).isEqualTo(segredo.getBytes(StandardCharsets.UTF_8));
    }
    @Test
    @DisplayName("recusa subir com a antiga chave padrão, que é pública no repositório")
    void recusaChavePadraoAntiga() {
        assertThatThrownBy(() -> config.jwtSecretKey("chave-de-desenvolvimento-local-nao-usar-em-producao"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("pública");
    }
    @Test
    @DisplayName("recusa segredo com menos de 32 bytes")
    void recusaSegredoCurto() {
        assertThatThrownBy(() -> config.jwtSecretKey("curto-demais"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("32 bytes");
    }
}
