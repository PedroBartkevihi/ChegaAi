package chegae.service;

import chegae.controller.dto.EventoRequestDTO;
import chegae.controller.dto.EventoResponseDTO;
import chegae.domain.enums.StatusEvento;
import chegae.domain.enums.StatusInscricao;
import chegae.domain.model.*;
import chegae.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Serviço de gerenciamento de eventos.
 * Contém lógica de criação, listagem e conversão para DTO compatível com o frontend.
 */
@Service
@RequiredArgsConstructor
public class EventoService {

    private final EventoRepository eventoRepository;
    private final UsuarioRepository usuarioRepository;
    private final CategoriaRepository categoriaRepository;
    private final EnderecoRepository enderecoRepository;
    private final InscricaoRepository inscricaoRepository;
    private final ComentarioRepository comentarioRepository;

    @Value("${chegae.regras.honra-minima-criacao:2.0}")
    private double honraMinimaCriacao;

    /**
     * Lista todos os eventos convertidos para o formato do frontend.
     */
    @Transactional(readOnly = true)
    public List<EventoResponseDTO> listarTodos() {
        List<Evento> eventos = eventoRepository.findAllWithRelations();
        return eventos.stream()
            .map(this::toResponseDTO)
            .collect(Collectors.toList());
    }

    /**
     * Busca um evento por ID e retorna no formato do frontend.
     */
    @Transactional(readOnly = true)
    public EventoResponseDTO buscarPorId(UUID id) {
        Evento evento = eventoRepository.findByIdWithRelations(id)
            .orElseThrow(() -> new NoSuchElementException("Evento não encontrado: " + id));
        return toResponseDTO(evento);
    }

    /**
     * Cria um novo evento aplicando as regras de negócio:
     * - RN004: honra mínima para criar eventos
     * - RN003: limite de vagas baseado no papel
     */
    @Transactional
    public EventoResponseDTO criarEvento(EventoRequestDTO dto, UUID usuarioId) {
        Usuario organizador = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new NoSuchElementException("Usuário não encontrado: " + usuarioId));

        // RN004: verificar honra mínima para criação
        if (organizador.getHonraMedia().compareTo(BigDecimal.valueOf(honraMinimaCriacao)) < 0) {
            throw new IllegalStateException(
                "Sua honra está abaixo de " + honraMinimaCriacao +
                ". Pelo RN004, você não pode criar novos rolés.");
        }

        // Buscar ou criar categoria
        Categoria categoria = categoriaRepository.findByNome(dto.category())
            .orElseThrow(() -> new NoSuchElementException("Categoria não encontrada: " + dto.category()));

        // Criar endereço simplificado
        Endereco endereco = new Endereco();
        String cepLimpo = dto.cep() != null ? dto.cep().replaceAll("[^0-9]", "") : "00000000";
        endereco.setCep(cepLimpo.length() == 8 ? cepLimpo : "00000000");
        endereco.setLogradouro(dto.place() != null ? dto.place() : "Local informado pelo organizador");
        endereco.setBairro(dto.place() != null ? dto.place().split(",")[0].trim() : "Centro");
        endereco.setCidade(dto.place() != null && dto.place().contains(",")
            ? dto.place().split(",")[1].trim() : "Rio de Janeiro");
        endereco.setEstado("RJ");
        endereco.setNumero("S/N");
        endereco = enderecoRepository.save(endereco);

        // Montar data/hora
        LocalDate data = LocalDate.parse(dto.date());
        LocalTime hora = LocalTime.parse(dto.time());
        OffsetDateTime dataHora = OffsetDateTime.of(data, hora, ZoneOffset.ofHours(-3));

        // RN003: limitar capacidade por papel
        int limiteMax = organizador.getHonraMedia().compareTo(BigDecimal.valueOf(4.8)) >= 0 ? 50 : 20;
        int capacidade = Math.min(dto.capacity(), limiteMax);

        // Criar evento
        Evento evento = new Evento();
        evento.setOrganizador(organizador);
        evento.setCategoria(categoria);
        evento.setEndereco(endereco);
        evento.setTitulo(dto.title());
        evento.setDescricao(dto.description());
        evento.setDataHoraRealizacao(dataHora);
        evento.setLimiteParticipantes(capacidade);
        evento.setStatusEvento(StatusEvento.ATIVO);
        evento.setTravado(false);

        evento = eventoRepository.save(evento);

        // Auto-inscrever o organizador como CONFIRMADO
        Inscricao inscricaoOrganizador = new Inscricao();
        inscricaoOrganizador.setUsuario(organizador);
        inscricaoOrganizador.setEvento(evento);
        inscricaoOrganizador.setStatusInscricao(StatusInscricao.CONFIRMADA);
        inscricaoRepository.save(inscricaoOrganizador);

        // Adicionar comentário inicial
        Comentario comentarioInicial = new Comentario();
        comentarioInicial.setAutor(organizador);
        comentarioInicial.setEvento(evento);
        comentarioInicial.setTexto("Rolé publicado. Confirmem presença por aqui.");
        comentarioRepository.save(comentarioInicial);

        return toResponseDTO(evento);
    }

    /**
     * Converte uma entidade Evento para o DTO compatível com o frontend React.
     */
    public EventoResponseDTO toResponseDTO(Evento evento) {
        UUID eventoId = evento.getId();

        // Buscar inscrições confirmadas e na fila
        List<Inscricao> confirmadas = inscricaoRepository
            .findByEventoIdAndStatusInscricao(eventoId, StatusInscricao.CONFIRMADA);
        List<Inscricao> fila = inscricaoRepository
            .findByEventoIdAndStatusInscricao(eventoId, StatusInscricao.SOLICITADA);

        // Buscar comentários
        List<Comentario> comentarios = comentarioRepository.findByEventoIdWithAutor(eventoId);

        // Nomes dos confirmados e da fila
        List<String> confirmedNames = confirmadas.stream()
            .map(i -> i.getUsuario().getNome().split(" ")[0]) // Primeiro nome
            .collect(Collectors.toList());
        List<String> waitlistNames = fila.stream()
            .map(i -> i.getUsuario().getNome().split(" ")[0])
            .collect(Collectors.toList());

        // Comentários formatados
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");
        List<EventoResponseDTO.CommentDTO> commentDTOs = comentarios.stream()
            .map(c -> new EventoResponseDTO.CommentDTO(
                c.getAutor().getNome().split(" ")[0],
                c.getTexto(),
                c.getDataHoraComentario() != null
                    ? c.getDataHoraComentario().format(timeFmt)
                    : "00:00"
            ))
            .collect(Collectors.toList());

        // Determinar status para o frontend
        String statusFront;
        if (evento.getTravado() != null && evento.getTravado()) {
            statusFront = "TRAVADO";
        } else {
            statusFront = switch (evento.getStatusEvento()) {
                case ATIVO -> "ABERTO";
                case CANCELADO -> "CANCELADO";
                case CONCLUIDO -> "CONCLUIDO";
            };
        }

        // Formatar data e hora
        String dateStr = evento.getDataHoraRealizacao().toLocalDate().toString();
        String timeStr = evento.getDataHoraRealizacao().toLocalTime().format(timeFmt);

        // Endereço formatado
        Endereco end = evento.getEndereco();
        String placeStr = end.getBairro() + ", " + end.getCidade();

        // Organizador
        Usuario org = evento.getOrganizador();
        EventoResponseDTO.OrganizerDTO organizerDTO = new EventoResponseDTO.OrganizerDTO(
            org.getId(),
            org.getNome().split(" ")[0],
            org.getHonraMedia().doubleValue()
        );

        return new EventoResponseDTO(
            evento.getId(),
            evento.getTitulo(),
            evento.getCategoria().getNome(),
            dateStr,
            timeStr,
            end.getCep(),
            placeStr,
            0, // distance (simplificado para MVP)
            evento.getLimiteParticipantes(),
            statusFront,
            organizerDTO,
            confirmedNames,
            waitlistNames,
            commentDTOs,
            0, // reports count (simplificado para MVP)
            evento.getDescricao()
        );
    }
}
