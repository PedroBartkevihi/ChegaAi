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
import java.nio.charset.StandardCharsets;
import chegaai.limite.IdentidadeCliente;
import java.util.NoSuchElementException;
import java.util.UUID;
@Service
@RequiredArgsConstructor
public class AuthService {
    public static final String EMAIL_EM_USO = "Já existe uma conta com este e-mail.";
    public static final String CADASTRO_RECUSADO =
        "Não foi possível concluir o cadastro com esses dados. Se você já tem conta, entre com seu e-mail.";
    public static final String SENHA_LONGA =
        "A senha pode ter no máximo 72 bytes; letras com acento contam como dois.";
    /** Limite do BCrypt, em bytes: o DTO conta caracteres, e uma letra com acento ocupa dois. */
    private static final int MAX_BYTES_SENHA = 72;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final RegrasEventoService regrasEventoService;
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
        // Sem isso, o BCrypt recusaria com uma mensagem técnica em inglês.
        if (dto.password().getBytes(StandardCharsets.UTF_8).length > MAX_BYTES_SENHA) {
            throw new IllegalArgumentException(SENHA_LONGA);
        }
        // CPF opcional (o DTO já transforma o vazio em nulo); se vier, é validado e precisa ser único.
        String cpf = dto.cpf();
        if (cpf != null && !Cpf.valido(cpf)) {
            throw new IllegalArgumentException("CPF inválido.");
        }
        if (usuarioRepository.existsByEmail(email)) {
            throw new IllegalStateException(EMAIL_EM_USO);
        }
        // Mensagem genérica: não confirma para qualquer pessoa que aquele CPF tem conta.
        if (cpf != null && usuarioRepository.existsByCpf(cpf)) {
            throw new IllegalStateException(CADASTRO_RECUSADO);
        }
        Participante participante = new Participante(dto.name().trim(), email, cpf,
            passwordEncoder.encode(dto.password()), dto.birthDate());
        participante.setHonraInicial(BigDecimal.valueOf(honraInicial));
        participante.setHonraMedia(participante.getHonraInicial());
        return gerarResposta(usuarioRepository.save(participante));
    }
    @Transactional(readOnly = true)
    public UsuarioResponseDTO buscarUsuario(UUID usuarioId) {
        return usuarioRepository.findById(usuarioId)
            .map(regrasEventoService::respostaDoUsuario)
            .orElseThrow(() -> new NoSuchElementException("Usuário não encontrado: " + usuarioId));
    }
    private AuthResponseDTO gerarResposta(Usuario usuario) {
        TokenService.TokenGerado token = tokenService.gerar(usuario);
        return new AuthResponseDTO(token.valor(), "Bearer", token.expiraEm(), regrasEventoService.respostaDoUsuario(usuario));
    }
    private String normalizarEmail(String email) {
        return IdentidadeCliente.email(email);
    }
}
