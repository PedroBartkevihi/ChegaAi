package chegaai.service;
import chegaai.controller.dto.AuthResponseDTO;
import chegaai.controller.dto.CadastroRequestDTO;
import chegaai.controller.dto.LoginRequestDTO;
import chegaai.controller.dto.UsuarioResponseDTO;
import chegaai.domain.Cpf;
import chegaai.domain.enums.StatusUsuario;
import chegaai.domain.model.Participante;
import chegaai.domain.model.Usuario;
import chegaai.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.UUID;
@Service
@RequiredArgsConstructor
public class AuthService {
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    @Value("${chegaai.regras.honra-inicial:3.0}")
    private double honraInicial;
    @Transactional(readOnly = true)
    public AuthResponseDTO login(LoginRequestDTO dto) {
        Usuario usuario = usuarioRepository.findByEmail(normalizarEmail(dto.email()))
            .filter(encontrado -> passwordEncoder.matches(dto.password(), encontrado.getSenhaHash()))
            .orElseThrow(() -> new BadCredentialsException("E-mail ou senha inválidos."));
        if (usuario.getStatusUsuario() == StatusUsuario.BANIDO) {
            throw new IllegalStateException("Esta conta foi banida pela moderação.");
        }
        return gerarResposta(usuario);
    }
    @Transactional
    public AuthResponseDTO cadastrar(CadastroRequestDTO dto) {
        String email = normalizarEmail(dto.email());
        if (!Cpf.valido(dto.cpf())) {
            throw new IllegalArgumentException("CPF inválido.");
        }
        if (usuarioRepository.existsByEmail(email)) {
            throw new IllegalStateException("Já existe uma conta com este e-mail.");
        }
        if (usuarioRepository.existsByCpf(dto.cpf())) {
            throw new IllegalStateException("Já existe uma conta com este CPF.");
        }
        Participante participante = new Participante(dto.name().trim(), email, dto.cpf(),
            passwordEncoder.encode(dto.password()), dto.birthDate());
        participante.setHonraMedia(BigDecimal.valueOf(honraInicial));
        return gerarResposta(usuarioRepository.save(participante));
    }
    @Transactional(readOnly = true)
    public UsuarioResponseDTO buscarUsuario(UUID usuarioId) {
        return usuarioRepository.findById(usuarioId)
            .map(UsuarioResponseDTO::de)
            .orElseThrow(() -> new NoSuchElementException("Usuário não encontrado: " + usuarioId));
    }
    private AuthResponseDTO gerarResposta(Usuario usuario) {
        TokenService.TokenGerado token = tokenService.gerar(usuario);
        return new AuthResponseDTO(token.valor(), "Bearer", token.expiraEm(), UsuarioResponseDTO.de(usuario));
    }
    private String normalizarEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
