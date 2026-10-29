package chegaai.service;
import chegaai.controller.dto.AuthResponseDTO;
import chegaai.controller.dto.CadastroRequestDTO;
import chegaai.controller.dto.LoginRequestDTO;
import chegaai.domain.enums.StatusUsuario;
import chegaai.domain.model.Participante;
import chegaai.domain.model.Usuario;
import chegaai.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder(4);
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private TokenService tokenService;
    private AuthService authService;
    @BeforeEach
    void setUp() {
        authService = new AuthService(usuarioRepository, ENCODER, tokenService,
            new RegrasEventoService(new BigDecimal("4.8"), new BigDecimal("2.0"), 20, 50));
        ReflectionTestUtils.setField(authService, "honraInicial", 3.0);
    }
    @Test
    @DisplayName("login com e-mail e senha corretos devolve o token e os dados do usuário")
    void loginValido() {
        Participante pedro = participante("pedro@chegaai.dev", "admin123");
        when(usuarioRepository.findByEmail("pedro@chegaai.dev")).thenReturn(Optional.of(pedro));
        when(tokenService.gerar(pedro)).thenReturn(new TokenService.TokenGerado("token-jwt", Instant.now()));
        AuthResponseDTO resposta = authService.login(new LoginRequestDTO(" Pedro@ChegaAi.dev ", "admin123"));
        assertThat(resposta.token()).isEqualTo("token-jwt");
        assertThat(resposta.tokenType()).isEqualTo("Bearer");
        assertThat(resposta.user().email()).isEqualTo("pedro@chegaai.dev");
        assertThat(resposta.user().isAmbassador()).isTrue();
        assertThat(resposta.user().capacityLimit()).isEqualTo(50);
        assertThat(resposta.user().canCreateEvents()).isTrue();
    }
    @Test
    @DisplayName("login com senha errada é recusado com a mesma mensagem de e-mail inexistente")
    void loginComSenhaErrada() {
        when(usuarioRepository.findByEmail("pedro@chegaai.dev"))
            .thenReturn(Optional.of(participante("pedro@chegaai.dev", "admin123")));
        assertThatThrownBy(() -> authService.login(new LoginRequestDTO("pedro@chegaai.dev", "errada")))
            .isInstanceOf(BadCredentialsException.class)
            .hasMessage("E-mail ou senha inválidos.");
        verify(tokenService, never()).gerar(any());
    }
    @Test
    @DisplayName("login com e-mail inexistente é recusado")
    void loginComEmailInexistente() {
        when(usuarioRepository.findByEmail("ninguem@chegaai.dev")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> authService.login(new LoginRequestDTO("ninguem@chegaai.dev", "admin123")))
            .isInstanceOf(BadCredentialsException.class)
            .hasMessage("E-mail ou senha inválidos.");
    }
    @Test
    @DisplayName("conta banida não consegue entrar")
    void loginDeContaBanida() {
        Participante banido = participante("banido@chegaai.dev", "admin123");
        banido.setStatusUsuario(StatusUsuario.BANIDO);
        when(usuarioRepository.findByEmail("banido@chegaai.dev")).thenReturn(Optional.of(banido));
        assertThatThrownBy(() -> authService.login(new LoginRequestDTO("banido@chegaai.dev", "admin123")))
            .isInstanceOf(IllegalStateException.class);
        verify(tokenService, never()).gerar(any());
    }
    @Test
    @DisplayName("cadastro guarda a senha com BCrypt, normaliza o e-mail e começa com a honra inicial")
    void cadastroValido() {
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocacao -> {
            Usuario salvo = invocacao.getArgument(0);
            salvo.setId(UUID.randomUUID());
            return salvo;
        });
        when(tokenService.gerar(any())).thenReturn(new TokenService.TokenGerado("token-jwt", Instant.now()));
        AuthResponseDTO resposta = authService.cadastrar(cadastro(" Ana@Exemplo.com", "52998224725"));
        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        Usuario salvo = captor.getValue();
        assertThat(salvo).isInstanceOf(Participante.class);
        assertThat(salvo.getEmail()).isEqualTo("ana@exemplo.com");
        assertThat(salvo.getSenhaHash()).isNotEqualTo("senhaSegura123");
        assertThat(ENCODER.matches("senhaSegura123", salvo.getSenhaHash())).isTrue();
        assertThat(salvo.getHonraInicial()).isEqualByComparingTo(BigDecimal.valueOf(3.0));
        assertThat(salvo.getHonraMedia()).isEqualByComparingTo(BigDecimal.valueOf(3.0));
        assertThat(resposta.user().honor()).isEqualTo(3.0);
        assertThat(resposta.user().isAmbassador()).isFalse();
        assertThat(resposta.user().capacityLimit()).isEqualTo(20);
    }
    @Test
    @DisplayName("cadastro com e-mail já usado é recusado")
    void cadastroComEmailRepetido() {
        when(usuarioRepository.existsByEmail("ana@exemplo.com")).thenReturn(true);
        assertThatThrownBy(() -> authService.cadastrar(cadastro("ana@exemplo.com", "52998224725")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("e-mail");
        verify(usuarioRepository, never()).save(any());
    }
    @Test
    @DisplayName("cadastro com CPF já usado é recusado com mensagem que não confirma o CPF")
    void cadastroComCpfRepetido() {
        when(usuarioRepository.existsByCpf("52998224725")).thenReturn(true);
        assertThatThrownBy(() -> authService.cadastrar(cadastro("ana@exemplo.com", "52998224725")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(AuthService.CADASTRO_RECUSADO)
            .message().doesNotContain("CPF");
        verify(usuarioRepository, never()).save(any());
    }
    @Test
    @DisplayName("cadastro sem CPF (vazio, só espaços ou ausente) é aceito e não consulta CPF")
    void cadastroSemCpf() {
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocacao -> {
            Usuario salvo = invocacao.getArgument(0);
            salvo.setId(UUID.randomUUID());
            return salvo;
        });
        when(tokenService.gerar(any())).thenReturn(new TokenService.TokenGerado("token-jwt", Instant.now()));
        authService.cadastrar(cadastro("ana@exemplo.com", ""));
        authService.cadastrar(cadastro("bia@exemplo.com", "   "));
        authService.cadastrar(cadastro("cris@exemplo.com", null));
        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository, org.mockito.Mockito.times(3)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(Usuario::getCpf).containsOnlyNulls();
        verify(usuarioRepository, never()).existsByCpf(any());
    }
    @Test
    @DisplayName("cadastro com CPF de dígito verificador errado é recusado")
    void cadastroComCpfInvalido() {
        assertThatThrownBy(() -> authService.cadastrar(cadastro("ana@exemplo.com", "12345678900")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("CPF inválido.");
        verify(usuarioRepository, never()).save(any());
    }
    private static Participante participante(String email, String senha) {
        Participante participante = new Participante("Pedro Farias", email, "52998224725",
            ENCODER.encode(senha), LocalDate.of(2000, 3, 15));
        participante.setId(UUID.randomUUID());
        participante.setHonraMedia(BigDecimal.valueOf(4.9));
        return participante;
    }
    private static CadastroRequestDTO cadastro(String email, String cpf) {
        return new CadastroRequestDTO("Ana Souza", email, cpf, LocalDate.of(2001, 5, 10), "senhaSegura123");
    }
}
