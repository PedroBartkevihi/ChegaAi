package chegaai.service;
import chegaai.domain.model.Usuario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
@Service
public class TokenService {
    public static final String EMISSOR = "chegaai";
    private final JwtEncoder jwtEncoder;
    private final long expiracaoMinutos;
    public TokenService(JwtEncoder jwtEncoder,
                        @Value("${chegaai.jwt.expiracao-minutos:120}") long expiracaoMinutos) {
        this.jwtEncoder = jwtEncoder;
        this.expiracaoMinutos = expiracaoMinutos;
    }
    public TokenGerado gerar(Usuario usuario) {
        Instant agora = Instant.now();
        Instant expiraEm = agora.plus(expiracaoMinutos, ChronoUnit.MINUTES);
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(EMISSOR)
            .issuedAt(agora)
            .expiresAt(expiraEm)
            .subject(usuario.getId().toString())
            .claim("nome", usuario.getNome())
            .claim("papel", usuario.getPapelSistema().name())
            .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String valor = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenGerado(valor, expiraEm);
    }
    public record TokenGerado(String valor, Instant expiraEm) {}
}
