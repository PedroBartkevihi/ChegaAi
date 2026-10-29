package chegaai.limite;

public class LimiteExcedidoException extends RuntimeException {
    public static final String MENSAGEM = "Muitas solicitações. Aguarde um pouco e tente novamente.";
    private final long tentarAposSegundos;
    public LimiteExcedidoException(long esperaNanos) {
        super(MENSAGEM);
        this.tentarAposSegundos = Math.max(1, esperaNanos / 1_000_000_000L
            + (esperaNanos % 1_000_000_000L == 0 ? 0 : 1));
    }
    public long getTentarAposSegundos() { return tentarAposSegundos; }
}
