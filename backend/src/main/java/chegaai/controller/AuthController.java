package chegaai.controller;
import chegaai.controller.dto.LoginRequestDTO;
import chegaai.controller.dto.UsuarioResponseDTO;
import chegaai.domain.enums.PapelSistema;
import chegaai.domain.model.Usuario;
import chegaai.repository.UsuarioRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.NoSuchElementException;
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UsuarioRepository usuarioRepository;
    @PostMapping("/login")
    public ResponseEntity<UsuarioResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        Usuario usuario = usuarioRepository.findByEmail(request.email())
            .orElseThrow(() -> new NoSuchElementException(
                "Usuário não encontrado com o e-mail: " + request.email()));
        UsuarioResponseDTO dto = new UsuarioResponseDTO(
            usuario.getId(),
            usuario.getNome().split(" ")[0],  
            usuario.getNome(),                 
            usuario.getEmail(),
            usuario.getHonraMedia().doubleValue(),
            usuario.getPapelSistema() == PapelSistema.ADMINISTRADOR
        );
        return ResponseEntity.ok(dto);
    }
}
