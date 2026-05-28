package chegae;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Ponto de entrada da aplicação ChegAê.
 *
 * <ul>
 *   <li>{@code @EnableAsync}      – habilita execução assíncrona (notificações de fila)</li>
 *   <li>{@code @EnableScheduling} – habilita jobs agendados (banimento automático — RN005,
 *                                   verificação de ausência — RN008)</li>
 * </ul>
 *
 * <p>Redis e Cache foram desabilitados para o MVP de demonstração local.</p>
 */
@SpringBootApplication(exclude = {
    RedisAutoConfiguration.class,
    RedisRepositoriesAutoConfiguration.class
})
@EnableAsync
@EnableScheduling
public class ChegaeApplication {

	public static void main(String[] args) {
		SpringApplication.run(ChegaeApplication.class, args);
	}
}
