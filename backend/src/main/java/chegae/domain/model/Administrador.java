package chegae.domain.model;

import chegae.domain.enums.PapelSistema;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.NoArgsConstructor;

/**
 * Especialização de {@link Usuario} que representa a equipe de suporte
 * e moderação da plataforma ChegAê.
 *
 * <p>Responsabilidades exclusivas:</p>
 * <ul>
 *   <li>Acesso ao painel de moderação (RF006)</li>
 *   <li>Analisar e classificar denúncias (UC13)</li>
 *   <li>Banir ou suspender contas de usuários (UC05, RF005)</li>
 *   <li>Travar eventos com denúncias de local perigoso (RF009)</li>
 *   <li>Gerenciar categorias de eventos (RF008, UC12)</li>
 *   <li>Aplicar penalidades manuais (Advertência, Suspensão, Banimento)</li>
 * </ul>
 *
 * <p>O Administrador é identificado como {@code id_admin_aplicador} na
 * tabela {@code penalidade} e como moderador responsável em denúncias.</p>
 */
@Entity
@DiscriminatorValue("ADMINISTRADOR")
@NoArgsConstructor
public class Administrador extends Usuario {

	/**
	 * Retorna {@link PapelSistema#ADMINISTRADOR}.
	 *
	 * @return papel ADMINISTRADOR
	 */
	@Override
	public PapelSistema getPapelSistema() {
		return PapelSistema.ADMINISTRADOR;
	}
}
