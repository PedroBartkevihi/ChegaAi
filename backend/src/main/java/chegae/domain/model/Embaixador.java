package chegae.domain.model;

import chegae.domain.enums.PapelSistema;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.NoArgsConstructor;

/**
 * Especialização de {@link Usuario} com privilégios ampliados (RN003).
 *
 * <p>Condição de promoção: {@code honraMedia >= 4.8} (configurável via
 * {@code chegae.regras.honra-embaixador}).</p>
 *
 * <p>Privilégios exclusivos:</p>
 * <ul>
 *   <li>Pode criar eventos com até 50 vagas (RN003; padrão: 20 para Participante)</li>
 *   <li>Seus eventos aparecem em destaque (posição prioritária) no feed (RN003, UC09 — FA3)</li>
 * </ul>
 *
 * <p>Herda todos os comportamentos de {@link Participante}.</p>
 */
@Entity
@DiscriminatorValue("EMBAIXADOR")
@NoArgsConstructor
public class Embaixador extends Usuario {

	/**
	 * Retorna {@link PapelSistema#EMBAIXADOR}.
	 *
	 * @return papel EMBAIXADOR
	 */
	@Override
	public PapelSistema getPapelSistema() {
		return PapelSistema.EMBAIXADOR;
	}
}
