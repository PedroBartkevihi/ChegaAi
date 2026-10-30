package chegaai.limite;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

public final class RelogioLimitesTeste implements RelogioLimites {
    private final AtomicLong agora = new AtomicLong();
    public long agoraNanos() { return agora.get(); }
    public void avancar(Duration duracao) {
        if (duracao.isNegative()) throw new IllegalArgumentException("Relógio monotônico não retrocede.");
        agora.addAndGet(duracao.toNanos());
    }
    @TestConfiguration
    public static class Config {
        @Bean @Primary
        RelogioLimitesTeste relogioControlado() { return new RelogioLimitesTeste(); }
    }
}
