package chegaai.limite;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties("chegaai.limites")
public class ConfiguracaoLimites {
    private int maxChaves = 10_000;
    private Politica loginIp = new Politica(30, Duration.ofMinutes(1));
    private Politica loginPar = new Politica(5, Duration.ofMinutes(1));
    private Politica cadastroIp = new Politica(20, Duration.ofHours(1));
    private Politica criarEvento = new Politica(3, Duration.ofHours(1), 10L, Duration.ofDays(1));
    private Politica comentar = new Politica(5, Duration.ofMinutes(1), 50L, Duration.ofHours(1));

    public Politica politica(OperacaoLimite operacao) {
        return switch (operacao) {
            case LOGIN_IP -> loginIp;
            case LOGIN_PAR -> loginPar;
            case CADASTRO_IP -> cadastroIp;
            case CRIAR_EVENTO -> criarEvento;
            case COMENTAR -> comentar;
        };
    }

    @Getter
    @Setter
    public static class Politica {
        private long capacidade;
        private Duration periodo;
        private Long longoCapacidade;
        private Duration longoPeriodo;
        public Politica() {}
        public Politica(long capacidade, Duration periodo) {
            this(capacidade, periodo, null, null);
        }
        public Politica(long capacidade, Duration periodo, Long longoCapacidade, Duration longoPeriodo) {
            this.capacidade = capacidade;
            this.periodo = periodo;
            this.longoCapacidade = longoCapacidade;
            this.longoPeriodo = longoPeriodo;
        }
    }
}
