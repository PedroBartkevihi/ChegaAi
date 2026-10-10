package chegaai.config;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
/**
 * Injeta o id do usuário autenticado, lido do "sub" do token JWT.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@AuthenticationPrincipal(expression = "T(java.util.UUID).fromString(subject)")
public @interface UsuarioAutenticado {
}
