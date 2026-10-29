package chegaai.limite;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import org.springframework.stereotype.Service;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
public class LimitadorRequisicoes {
    private final int maxChaves;
    private final RelogioLimites relogio;
    private final TimeMeter timeMeter;
    private final Map<OperacaoLimite, Regra> regras = new EnumMap<>(OperacaoLimite.class);
    private final Map<String, Entrada> entradas = new HashMap<>();
    private final SecretKeySpec segredo;
    private boolean varreduraAgendada;
    private long proximaVarredura;

    public LimitadorRequisicoes(ConfiguracaoLimites config, RelogioLimites relogio) {
        if (config.getMaxChaves() < 1 || config.getMaxChaves() > 10_000) {
            throw new IllegalArgumentException("O registro de limites deve ter entre 1 e 10000 chaves.");
        }
        this.maxChaves = config.getMaxChaves();
        this.relogio = relogio;
        this.timeMeter = new TimeMeter() {
            public long currentTimeNanos() { return relogio.agoraNanos(); }
            public boolean isWallClockBased() { return false; }
        };
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        this.segredo = new SecretKeySpec(bytes, "HmacSHA256");
        for (OperacaoLimite operacao : OperacaoLimite.values()) {
            var politica = config.politica(operacao);
            List<Bandwidth> faixas = new ArrayList<>();
            long expiracao = faixa(faixas, politica.getCapacidade(), politica.getPeriodo());
            if (politica.getLongoCapacidade() != null || politica.getLongoPeriodo() != null) {
                if (politica.getLongoCapacidade() == null || politica.getLongoPeriodo() == null) {
                    throw new IllegalArgumentException("A segunda janela precisa de capacidade e período.");
                }
                expiracao = Math.max(expiracao, faixa(faixas, politica.getLongoCapacidade(), politica.getLongoPeriodo()));
            }
            regras.put(operacao, new Regra(faixas, expiracao));
        }
    }

    private static long faixa(List<Bandwidth> faixas, long capacidade, java.time.Duration periodo) {
        if (capacidade < 1 || periodo == null || periodo.isZero() || periodo.isNegative()) {
            throw new IllegalArgumentException("As cotas precisam de capacidade e período positivos.");
        }
        long nanos = periodo.toNanos();
        faixas.add(Bandwidth.builder().capacity(capacidade).refillGreedy(capacidade, periodo).build());
        return nanos;
    }

    /** Tentativa de autenticação: o consumo não é devolvido por falhas de credenciais/validação. */
    public void consumir(OperacaoLimite operacao, String... componentes) {
        obter(operacao, false, componentes);
    }

    public ReservaLimite reservar(OperacaoLimite operacao, String... componentes) {
        Entrada entrada = obter(operacao, true, componentes);
        return new ReservaLimite(() -> encerrar(entrada, true), () -> encerrar(entrada, false));
    }

    private Entrada obter(OperacaoLimite operacao, boolean reserva, String... componentes) {
        String chave = chave(operacao, componentes);
        // A mesma seção protege o ciclo de vida e o consumo: nenhuma reserva fica em bucket removido.
        synchronized (entradas) {
            long agora = relogio.agoraNanos();
            Entrada entrada = entradas.get(chave);
            if (entrada != null && expirada(entrada, agora)) {
                entradas.remove(chave);
                entrada = null;
            }
            if (entrada == null) {
                if (entradas.size() >= maxChaves) {
                    if (!varreduraAgendada || agora - proximaVarredura >= 0) {
                        entradas.values().removeIf(e -> expirada(e, agora));
                        long espera = entradas.values().stream().filter(e -> e.reservas == 0)
                            .mapToLong(e -> Math.max(1, e.expiracaoNanos - (agora - e.ultimoAcesso)))
                            .min().orElse(1_000_000_000L);
                        proximaVarredura = agora + espera;
                        varreduraAgendada = true;
                    }
                    if (entradas.size() >= maxChaves) {
                        throw new LimiteExcedidoException(Math.max(1, proximaVarredura - agora));
                    }
                }
                Regra regra = regras.get(operacao);
                var builder = Bucket.builder().withCustomTimePrecision(timeMeter);
                regra.faixas.forEach(builder::addLimit);
                entrada = new Entrada(builder.build(), regra.expiracaoNanos, agora);
                entradas.put(chave, entrada);
                varreduraAgendada = false;
            }
            entrada.ultimoAcesso = agora;
            ConsumptionProbe probe = entrada.bucket.tryConsumeAndReturnRemaining(1);
            if (!probe.isConsumed()) throw new LimiteExcedidoException(probe.getNanosToWaitForRefill());
            if (reserva) entrada.reservas++;
            return entrada;
        }
    }

    private static boolean expirada(Entrada entrada, long agora) {
        return entrada.reservas == 0 && agora - entrada.ultimoAcesso >= entrada.expiracaoNanos;
    }

    private void encerrar(Entrada entrada, boolean devolver) {
        synchronized (entradas) {
            if (devolver) entrada.bucket.addTokens(1);
            entrada.reservas--;
            varreduraAgendada = false;
            entrada.ultimoAcesso = relogio.agoraNanos();
        }
    }

    private String chave(OperacaoLimite operacao, String... componentes) {
        if (operacao == null || componentes == null || componentes.length == 0) {
            throw new IllegalArgumentException("Operação e componentes de identidade são obrigatórios.");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(segredo);
            adicionar(mac, operacao.name());
            mac.update(ByteBuffer.allocate(4).putInt(componentes.length).array());
            for (String componente : componentes) {
                if (componente == null) throw new IllegalArgumentException("Componente de identidade ausente.");
                adicionar(mac, componente);
            }
            return HexFormat.of().formatHex(mac.doFinal());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC indisponível para o limitador.", ex);
        }
    }
    private static void adicionar(Mac mac, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        mac.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
        mac.update(bytes);
    }
    private record Regra(List<Bandwidth> faixas, long expiracaoNanos) {}
    private static final class Entrada {
        final Bucket bucket;
        final long expiracaoNanos;
        long ultimoAcesso;
        int reservas;
        Entrada(Bucket bucket, long expiracaoNanos, long agora) {
            this.bucket = bucket; this.expiracaoNanos = expiracaoNanos; this.ultimoAcesso = agora;
        }
    }
}
