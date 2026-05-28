package chegae.controller;

import chegae.controller.dto.LoginRequestDTO;
import chegae.controller.dto.UsuarioResponseDTO;
import chegae.domain.enums.PapelSistema;
import chegae.domain.model.Usuario;
import chegae.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;

/**
 * Controller de autenticação simplificada para o MVP.
 * Busca o usuário pelo e-mail e retorna seus dados — sem validação de senha.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UsuarioRepository usuarioRepository;

    /**
     * POST /api/auth/login
     * Simula autenticação buscando o usuário pelo e-mail.
     */
    @PostMapping("/login")
    public ResponseEntity<UsuarioResponseDTO> login(@RequestBody LoginRequestDTO request) {
        Usuario usuario = usuarioRepository.findByEmail(request.email())
            .orElseThrow(() -> new NoSuchElementException(
                "Usuário não encontrado com o e-mail: " + request.email()));

        UsuarioResponseDTO dto = new UsuarioResponseDTO(
            usuario.getId(),
            usuario.getNome().split(" ")[0],  // Primeiro nome
            usuario.getNome(),                 // Nome completo
            usuario.getEmail(),
            usuario.getHonraMedia().doubleValue(),
            usuario.getPapelSistema() == PapelSistema.ADMINISTRADOR
        );

        return ResponseEntity.ok(dto);
    }
}
