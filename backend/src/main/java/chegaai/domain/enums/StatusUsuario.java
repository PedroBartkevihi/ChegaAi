package chegaai.domain.enums;
public enum StatusUsuario {
	ATIVO,
	SUSPENSO,
	BANIDO,
	/** A própria pessoa excluiu a conta: os dados pessoais foram apagados e ela não volta a ser usada. */
	EXCLUIDO
}
