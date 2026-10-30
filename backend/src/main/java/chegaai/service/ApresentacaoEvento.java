package chegaai.service;
import chegaai.config.FusoHorario;
import chegaai.controller.dto.EventoResponseDTO;
import chegaai.domain.model.Endereco;
import chegaai.domain.model.Evento;
import chegaai.domain.model.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Campos de exibição comuns ao detalhe e ao resumo do rolé, para os dois não divergirem. */
@Component
@RequiredArgsConstructor
public class ApresentacaoEvento {
    private final FusoHorario fusoHorario;
    private final RegrasEventoService regrasEventoService;

    public String status(Evento evento) {
        if (evento.getTravado() != null && evento.getTravado()) {
            return "TRAVADO";
        }
        return switch (evento.getStatusEvento()) {
            case ATIVO -> "ABERTO";
            case CANCELADO -> "CANCELADO";
            case CONCLUIDO -> "CONCLUIDO";
        };
    }
    public String data(Evento evento) {
        return fusoHorario.data(evento.getDataHoraRealizacao()).toString();
    }
    public String hora(Evento evento) {
        return fusoHorario.hora(evento.getDataHoraRealizacao());
    }
    public String local(Evento evento) {
        Endereco end = evento.getEndereco();
        return end.getBairro() + ", " + end.getCidade();
    }
    public EventoResponseDTO.OrganizerDTO organizador(Evento evento) {
        Usuario org = evento.getOrganizador();
        return new EventoResponseDTO.OrganizerDTO(
            org.getId(),
            org.getNome().split(" ")[0],
            org.getHonraMedia().doubleValue(),
            regrasEventoService.ehEmbaixador(org)
        );
    }
}
