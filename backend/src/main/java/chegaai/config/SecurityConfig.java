package chegaai.config;
import chegaai.repository.UsuarioRepository;
import chegaai.service.TokenService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
@Configuration
@EnableWebSecurity
@Slf4j
public class SecurityConfig {
    private static final int TAMANHO_MINIMO_CHAVE = 32;
    /** Foi o padrão do application.properties: está no histórico do Git, então não protege nada. */
    private static final String SEGREDO_PUBLICO_ANTIGO = "chave-de-desenvolvimento-local-nao-usar-em-producao";
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, CorsConfigurationSource corsConfigurationSource)
            throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/auth/login", "/auth/cadastro").permitAll()
                .requestMatchers(HttpMethod.GET, "/eventos", "/eventos/**", "/recomendacoes/**").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));
        return http.build();
    }
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    /**
     * Chave HMAC dos tokens. Sem CHEGAAI_JWT_SECRET, gera uma chave aleatória a cada inicialização:
     * nada no repositório assina tokens válidos, mas todos são deslogados quando a API reinicia.
     */
    @Bean
    public SecretKey jwtSecretKey(@Value("${chegaai.jwt.secret:}") String secret) {
        if (secret.isBlank()) {
            log.warn("CHEGAAI_JWT_SECRET não definido: usando uma chave aleatória gerada nesta inicialização. "
                + "Os tokens emitidos deixam de valer quando a API reinicia.");
            byte[] aleatoria = new byte[TAMANHO_MINIMO_CHAVE];
            new SecureRandom().nextBytes(aleatoria);
            return new SecretKeySpec(aleatoria, "HmacSHA256");
        }
        if (secret.equals(SEGREDO_PUBLICO_ANTIGO)) {
            throw new IllegalStateException("CHEGAAI_JWT_SECRET usa a chave que já foi o padrão do repositório "
                + "e é pública. Gere outra (veja o DEPLOY.md).");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < TAMANHO_MINIMO_CHAVE) {
            throw new IllegalStateException("chegaai.jwt.secret precisa ter pelo menos 32 bytes para o HS256.");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }
    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey, UsuarioRepository usuarioRepository) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
        decoder.setJwtValidator(validadorDeToken(usuarioRepository));
        return decoder;
    }
    /**
     * Além do emissor e da validade, exige que a conta do token ainda exista. Uma conta apagada deixaria
     * tokens assinados apontando para ninguém; assim a API responde 401, e o site encerra a sessão.
     */
    static OAuth2TokenValidator<Jwt> validadorDeToken(UsuarioRepository usuarioRepository) {
        OAuth2TokenValidator<Jwt> padrao = JwtValidators.createDefaultWithIssuer(TokenService.EMISSOR);
        OAuth2Error contaInexistente = new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN,
            "A conta deste token não existe mais.", null);
        return jwt -> {
            OAuth2TokenValidatorResult resultado = padrao.validate(jwt);
            if (resultado.hasErrors()) {
                return resultado;
            }
            return contaExiste(usuarioRepository, jwt.getSubject())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(contaInexistente);
        };
    }
    private static boolean contaExiste(UsuarioRepository usuarioRepository, String subject) {
        try {
            return subject != null && usuarioRepository.existsById(UUID.fromString(subject));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${chegaai.cors.origens}") List<String> origens) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origens.stream().map(String::trim).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
