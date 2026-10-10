package chegaai.service;
import chegaai.domain.model.Administrador;
import chegaai.domain.model.Participante;
import chegaai.domain.model.Usuario;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
class TokenServiceTest {
    private static final SecretKey CHAVE = chave("chave-de-teste-com-pelo-menos-32-bytes");
    private final TokenService tokenService = new TokenService(new NimbusJwtEncoder(new ImmutableSecret<>(CHAVE)), 120);
    @Test
    @DisplayName("gera um token assinado com id, nome, papel e validade do usuário")
    void geraTokenComClaims() {
        Usuario usuario = participante();
        TokenService.TokenGerado gerado = tokenService.gerar(usuario);
        Jwt jwt = decoder(CHAVE).decode(gerado.valor());
        assertThat(jwt.getSubject()).isEqualTo(usuario.getId().toString());
        assertThat(jwt.getClaimAsString("iss")).isEqualTo(TokenService.EMISSOR);
        assertThat(jwt.getClaimAsString("nome")).isEqualTo("Ana Souza");
        assertThat(jwt.getClaimAsString("papel")).isEqualTo("PARTICIPANTE");
        assertThat(jwt.getExpiresAt()).isEqualTo(gerado.expiraEm().truncatedTo(ChronoUnit.SECONDS));
        assertThat(Duration.between(Instant.now(), gerado.expiraEm())).isBetween(Duration.ofMinutes(119), Duration.ofMinutes(120));
    }
    @Test
    @DisplayName("registra o papel de administrador no token")
    void registraPapelAdministrador() {
        Administrador admin = new Administrador();
        admin.setId(UUID.randomUUID());
        admin.setNome("Moderação");
        Jwt jwt = decoder(CHAVE).decode(tokenService.gerar(admin).valor());
        assertThat(jwt.getClaimAsString("papel")).isEqualTo("ADMINISTRADOR");
    }
    @Test
    @DisplayName("um token assinado com outra chave é rejeitado")
    void rejeitaTokenDeOutraChave() {
        String token = tokenService.gerar(participante()).valor();
        NimbusJwtDecoder decoderDeOutraChave = decoder(chave("outra-chave-qualquer-com-mais-de-32-bytes"));
        assertThatThrownBy(() -> decoderDeOutraChave.decode(token)).isInstanceOf(JwtException.class);
    }
    @Test
    @DisplayName("um token vencido é rejeitado")
    void rejeitaTokenVencido() {
        Instant agora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(TokenService.EMISSOR)
            .subject(UUID.randomUUID().toString())
            .issuedAt(agora.minus(3, ChronoUnit.HOURS))
            .expiresAt(agora.minus(1, ChronoUnit.HOURS))
            .build();
        String token = new NimbusJwtEncoder(new ImmutableSecret<>(CHAVE))
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
        assertThatThrownBy(() -> decoder(CHAVE).decode(token)).isInstanceOf(JwtException.class);
    }
    private static Participante participante() {
        Participante participante = new Participante();
        participante.setId(UUID.randomUUID());
        participante.setNome("Ana Souza");
        return participante;
    }
    private static NimbusJwtDecoder decoder(SecretKey chave) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(chave).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(TokenService.EMISSOR));
        return decoder;
    }
    private static SecretKey chave(String segredo) {
        return new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
