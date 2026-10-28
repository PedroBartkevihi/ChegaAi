package chegaai.config;
import chegaai.repository.UsuarioRepository;
import chegaai.service.TokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import static java.time.temporal.ChronoUnit.HOURS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
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
    @Test
    @DisplayName("token vencido ou de outro emissor é recusado sem consultar o banco")
    void tokenVencidoOuDeOutroEmissorNaoConsultaOBanco() {
        UsuarioRepository repositorio = mock(UsuarioRepository.class);
        OAuth2TokenValidator<Jwt> validador = SecurityConfig.validadorDeToken(repositorio);
        String id = UUID.randomUUID().toString();
        Instant agora = Instant.now();
        assertThat(validador.validate(jwt(TokenService.EMISSOR, id, agora.minus(3, HOURS), agora.minus(2, HOURS)))
            .hasErrors()).isTrue();
        assertThat(validador.validate(jwt("outro-emissor", id, agora, agora.plus(1, HOURS))).hasErrors()).isTrue();
        verifyNoInteractions(repositorio);
    }
    @Test
    @DisplayName("token com sub que não é UUID, ou de conta que não existe mais, é recusado")
    void tokenComSubInvalidoOuDeContaInexistente() {
        UsuarioRepository repositorio = mock(UsuarioRepository.class);
        UUID existente = UUID.randomUUID();
        when(repositorio.existsById(existente)).thenReturn(true);
        OAuth2TokenValidator<Jwt> validador = SecurityConfig.validadorDeToken(repositorio);
        Instant agora = Instant.now();
        assertThat(validador.validate(jwt(TokenService.EMISSOR, existente.toString(), agora, agora.plus(1, HOURS)))
            .hasErrors()).isFalse();
        assertThat(validador.validate(jwt(TokenService.EMISSOR, UUID.randomUUID().toString(), agora, agora.plus(1, HOURS)))
            .hasErrors()).isTrue();
        assertThat(validador.validate(jwt(TokenService.EMISSOR, "nao-e-uuid", agora, agora.plus(1, HOURS)))
            .hasErrors()).isTrue();
    }
    @Test
    @DisplayName("falha do banco ao conferir a conta não vira token inválido: a exceção segue adiante")
    void falhaDoBancoNaoViraTokenInvalido() {
        UsuarioRepository repositorio = mock(UsuarioRepository.class);
        when(repositorio.existsById(any())).thenThrow(new DataAccessResourceFailureException("banco fora do ar"));
        OAuth2TokenValidator<Jwt> validador = SecurityConfig.validadorDeToken(repositorio);
        Instant agora = Instant.now();
        assertThatThrownBy(() -> validador.validate(
                jwt(TokenService.EMISSOR, UUID.randomUUID().toString(), agora, agora.plus(1, HOURS))))
            .isInstanceOf(DataAccessResourceFailureException.class);
    }
    private static Jwt jwt(String emissor, String subject, Instant emitidoEm, Instant expiraEm) {
        return Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .issuer(emissor)
            .subject(subject)
            .issuedAt(emitidoEm)
            .expiresAt(expiraEm)
            .build();
    }
}
