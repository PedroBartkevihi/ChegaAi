package chegaai.controller;
import chegaai.config.UsuarioAutenticado;
import chegaai.controller.dto.AuthResponseDTO;
import chegaai.controller.dto.CadastroRequestDTO;
import chegaai.controller.dto.LoginRequestDTO;
import chegaai.controller.dto.UsuarioResponseDTO;
import chegaai.service.AuthService;
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
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
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
}
