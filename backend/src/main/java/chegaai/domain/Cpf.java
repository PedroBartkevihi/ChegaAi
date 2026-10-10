package chegaai.domain;
/**
 * Validação dos dígitos verificadores do CPF.
 */
public final class Cpf {
    private Cpf() {
    }
    public static boolean valido(String cpf) {
        if (cpf == null || !cpf.matches("^[0-9]{11}$") || cpf.chars().distinct().count() == 1) {
            return false;
        }
        return digitoVerificador(cpf, 9) == cpf.charAt(9) - '0'
            && digitoVerificador(cpf, 10) == cpf.charAt(10) - '0';
    }
    private static int digitoVerificador(String cpf, int quantidade) {
        int soma = 0;
        for (int i = 0; i < quantidade; i++) {
            soma += (cpf.charAt(i) - '0') * (quantidade + 1 - i);
        }
        int resto = (soma * 10) % 11;
        return resto == 10 ? 0 : resto;
    }
}
