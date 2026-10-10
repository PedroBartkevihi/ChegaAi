package chegaai.controller.dto;
import chegaai.domain.enums.PapelSistema;
import chegaai.domain.model.Usuario;
import java.util.UUID;
public record UsuarioResponseDTO(
    UUID id,
    String name,
    String fullName,
    String email,
    double honor,
    boolean isModerator
) {
    public static UsuarioResponseDTO de(Usuario usuario) {
        return new UsuarioResponseDTO(
            usuario.getId(),
            usuario.getNome().split(" ")[0],
            usuario.getNome(),
            usuario.getEmail(),
            usuario.getHonraMedia().doubleValue(),
            usuario.getPapelSistema() == PapelSistema.ADMINISTRADOR
        );
    }
}
