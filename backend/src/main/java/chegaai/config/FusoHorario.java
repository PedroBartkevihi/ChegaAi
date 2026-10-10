package chegaai.config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
/**
 * Converte os instantes gravados no banco (timestamptz, lidos em UTC) para o fuso
 * dos eventos, e vice-versa. Sem isso, o servidor em UTC exibiria horários 3h adiantados.
 */
@Component
public class FusoHorario {
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private final ZoneId zona;
    public FusoHorario(@Value("${chegaai.fuso-horario:America/Sao_Paulo}") String zona) {
        this.zona = ZoneId.of(zona);
    }
    public OffsetDateTime instante(LocalDate data, LocalTime hora) {
        return data.atTime(hora).atZone(zona).toOffsetDateTime();
    }
    public LocalDate data(OffsetDateTime instante) {
        return instante.atZoneSameInstant(zona).toLocalDate();
    }
    public String hora(OffsetDateTime instante) {
        return instante.atZoneSameInstant(zona).format(HORA);
    }
}
