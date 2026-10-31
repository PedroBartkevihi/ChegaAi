package chegaai.service.external;

/**
 * O GraphDB não respondeu ou respondeu com erro (na versão online ele nem existe). Vira 503 com
 * {@link #MENSAGEM}; o detalhe da falha fica só no log.
 */
public class GraphDBIndisponivelException extends RuntimeException {
    public static final String MENSAGEM = "As recomendações dependem do GraphDB, que não está disponível agora.";
    public GraphDBIndisponivelException(String detalhe, Throwable causa) {
        super(detalhe, causa);
    }
}
