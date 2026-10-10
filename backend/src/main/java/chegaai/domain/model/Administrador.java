package chegaai.domain.model;
import chegaai.domain.enums.PapelSistema;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.NoArgsConstructor;
@Entity
@DiscriminatorValue("ADMINISTRADOR")
@NoArgsConstructor
public class Administrador extends Usuario {
	@Override
	public PapelSistema getPapelSistema() {
		return PapelSistema.ADMINISTRADOR;
	}
}
