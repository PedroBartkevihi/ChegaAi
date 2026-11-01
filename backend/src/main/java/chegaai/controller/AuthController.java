package chegaai.controller;
import chegaai.config.UsuarioAutenticado;
import chegaai.limite.IdentidadeCliente;
import chegaai.limite.LimitadorRequisicoes;
import chegaai.limite.OperacaoLimite;
import jakarta.servlet.http.HttpServletRequest;
import chegaai.controller.dto.AuthResponseDTO;
import chegaai.controller.dto.CadastroRequestDTO;
import chegaai.controller.dto.ExclusaoContaRequestDTO;
import chegaai.controller.dto.LoginRequestDTO;
import chegaai.controller.dto.UsuarioResponseDTO;
import chegaai.service.AuthService;
import chegaai.service.ExclusaoContaService;
import chegaai.service.PerfilService;
import chegaai.controller.dto.MinhaInscricaoResponseDTO;
import chegaai.controller.dto.AvaliacoesPendentesResponseDTO;
import java.util.List;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final PerfilService perfilService;
    private final ExclusaoContaService exclusaoContaService;
    private final LimitadorRequisicoes limitador;
    private final IdentidadeCliente identidade;
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody LoginRequestDTO request, HttpServletRequest http) {
        limitador.consumir(OperacaoLimite.LOGIN_PAR, identidade.ip(http), IdentidadeCliente.email(request.email()));
        return ResponseEntity.ok(authService.login(request));
    }
    @PostMapping("/cadastro")
    public ResponseEntity<AuthResponseDTO> cadastrar(@Valid @RequestBody CadastroRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.cadastrar(request));
    }
    @GetMapping("/me")
    public ResponseEntity<UsuarioResponseDTO> usuarioAtual(@UsuarioAutenticado UUID usuarioId) {
        return ResponseEntity.ok(authService.buscarUsuario(usuarioId));
    }
    @GetMapping("/me/inscricoes")
    public ResponseEntity<List<MinhaInscricaoResponseDTO>> minhasInscricoes(@UsuarioAutenticado UUID usuarioId) {
        return ResponseEntity.ok(perfilService.minhasInscricoes(usuarioId));
    }
    @GetMapping("/me/avaliacoes-pendentes")
    public ResponseEntity<AvaliacoesPendentesResponseDTO> avaliacoesPendentes(@UsuarioAutenticado UUID usuarioId) {
        return ResponseEntity.ok(perfilService.avaliacoesPendentes(usuarioId));
    }
    /** Exclui a conta de quem está logado, com a senha como confirmação; o token deixa de valer em seguida. */
    @PostMapping("/me/exclusao")
    public ResponseEntity<Void> excluirConta(@UsuarioAutenticado UUID usuarioId,
                                             @Valid @RequestBody ExclusaoContaRequestDTO request, HttpServletRequest http) {
        // O mesmo limite do par IP + conta do login: um token roubado não vira um jeito de testar senhas.
        limitador.consumir(OperacaoLimite.LOGIN_PAR, identidade.ip(http), usuarioId.toString());
        exclusaoContaService.excluir(usuarioId, request.password());
        return ResponseEntity.noContent().build();
    }
}
