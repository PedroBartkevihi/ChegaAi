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
    boolean isModerator,
    boolean isAmbassador,
    int capacityLimit,
    boolean canCreateEvents
) {
    public static UsuarioResponseDTO de(Usuario usuario, boolean isAmbassador, int capacityLimit, boolean canCreateEvents) {
        return new UsuarioResponseDTO(
            usuario.getId(),
            usuario.getNome().split(" ")[0],
            usuario.getNome(),
            usuario.getEmail(),
            usuario.getHonraMedia().doubleValue(),
            usuario.getPapelSistema() == PapelSistema.ADMINISTRADOR,
            isAmbassador,
            capacityLimit,
            canCreateEvents
        );
    }
}
