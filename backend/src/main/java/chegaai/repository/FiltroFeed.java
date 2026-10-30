package chegaai.repository;
import java.math.BigDecimal;
import java.util.Locale;

/**
 * Filtros e página do feed, já validados. A busca segue a da tela (sem diferenciar maiúsculas,
 * sobre título, categoria, local, descrição e primeiro nome do organizador) e vira um padrão LIKE
 * com os curingas escapados, para "%" e "_" digitados não casarem com qualquer texto.
 */
public record FiltroFeed(Secao secao, String categoria, BigDecimal honraMinima, boolean embaixador,
                         String padraoBusca, int pagina, int tamanho) {
    public enum Secao { PROXIMOS, ANTERIORES }
    public static final int TAMANHO_MAXIMO = 50;
    static final int PAGINA_MAXIMA = 10_000;
    static final int BUSCA_MAXIMA = 100;

    public static FiltroFeed de(String secao, String categoria, BigDecimal honraMinima, boolean embaixador,
                                String busca, int pagina, int tamanho) {
        Secao secaoValida = switch (secao == null ? "" : secao.trim().toLowerCase(Locale.ROOT)) {
            case "", "proximos" -> Secao.PROXIMOS;
            case "anteriores" -> Secao.ANTERIORES;
            default -> throw new IllegalArgumentException("O parâmetro 'secao' aceita 'proximos' ou 'anteriores'.");
        };
        if (pagina < 0 || pagina > PAGINA_MAXIMA) {
            throw new IllegalArgumentException("O parâmetro 'pagina' deve estar entre 0 e " + PAGINA_MAXIMA + ".");
        }
        if (tamanho < 1 || tamanho > TAMANHO_MAXIMO) {
            throw new IllegalArgumentException("O parâmetro 'tamanho' deve estar entre 1 e " + TAMANHO_MAXIMO + ".");
        }
        if (honraMinima != null && (honraMinima.signum() < 0 || honraMinima.compareTo(BigDecimal.valueOf(5)) > 0)) {
            throw new IllegalArgumentException("O parâmetro 'honraMinima' deve estar entre 0 e 5.");
        }
        String texto = busca == null ? "" : busca.trim();
        if (texto.length() > BUSCA_MAXIMA) {
            throw new IllegalArgumentException("A busca aceita até " + BUSCA_MAXIMA + " caracteres.");
        }
        String categoriaValida = categoria == null || categoria.isBlank() || categoria.trim().equalsIgnoreCase("todos")
            ? null : categoria.trim();
        BigDecimal honraValida = honraMinima == null || honraMinima.signum() == 0 ? null : honraMinima;
        String padrao = texto.isEmpty() ? null : "%" + escaparLike(texto.toLowerCase(Locale.ROOT)) + "%";
        return new FiltroFeed(secaoValida, categoriaValida, honraValida, embaixador, padrao, pagina, tamanho);
    }

    static String escaparLike(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    public int deslocamento() {
        return pagina * tamanho;
    }
}
