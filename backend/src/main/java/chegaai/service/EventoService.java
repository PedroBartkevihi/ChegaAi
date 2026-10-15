package chegaai.service;
import chegaai.config.FusoHorario;
import chegaai.controller.dto.EventoRequestDTO;
import chegaai.controller.dto.EventoResponseDTO;
import chegaai.controller.dto.AvaliacaoRequestDTO;
import chegaai.controller.dto.AvaliacaoResponseDTO;
import chegaai.domain.enums.PapelSistema;
import chegaai.domain.enums.StatusEvento;
import chegaai.domain.enums.StatusInscricao;
import chegaai.domain.model.*;
import chegaai.repository.*;
import chegaai.service.external.ViaCEPService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
@Service
@RequiredArgsConstructor
@Slf4j
public class EventoService {
    private final EventoRepository eventoRepository;
    private final UsuarioRepository usuarioRepository;
    private final CategoriaRepository categoriaRepository;
    private final EnderecoRepository enderecoRepository;
    private final InscricaoRepository inscricaoRepository;
    private final ComentarioRepository comentarioRepository;
    private final AvaliacaoRepository avaliacaoRepository;
    private final ViaCEPService viaCEPService;   
    private final FusoHorario fusoHorario;
    @Value("${chegaai.regras.honra-minima-criacao:2.0}")
    private double honraMinimaCriacao;
    @Value("${chegaai.regras.deducao-honra-ausencia:0.5}")
    private double deducaoHonraAusencia;
    @Transactional(readOnly = true)
    public List<EventoResponseDTO> listarTodos() {
        List<Evento> eventos = eventoRepository.findAllWithRelations();
        return eventos.stream()
            .map(this::toResponseDTO)
            .collect(Collectors.toList());
    }
    @Transactional(readOnly = true)
    public Page<EventoResponseDTO> listarTodosPaginado(Pageable pageable) {
        Page<Evento> pagina = eventoRepository.findAllWithRelations(pageable);
        return pagina.map(this::toResponseDTO);
    }
    @Transactional(readOnly = true)
    public EventoResponseDTO buscarPorId(UUID id) {
        Evento evento = eventoRepository.findByIdWithRelations(id)
            .orElseThrow(() -> new NoSuchElementException("Evento não encontrado: " + id));
        return toResponseDTO(evento);
    }
    @Transactional
    public EventoResponseDTO criarEvento(EventoRequestDTO dto, UUID usuarioId) {
        LocalDate data = LocalDate.parse(dto.date());
        LocalTime hora = LocalTime.parse(dto.time());
        OffsetDateTime dataHora = fusoHorario.instante(data, hora);
        if (!dataHora.isAfter(OffsetDateTime.now())) {
            throw new IllegalArgumentException("A data de realização deve ser uma data futura.");
        }
        Usuario organizador = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new NoSuchElementException("Usuário não encontrado: " + usuarioId));
        if (organizador.getHonraMedia().compareTo(BigDecimal.valueOf(honraMinimaCriacao)) < 0) {
            throw new IllegalStateException(
                "Sua honra está abaixo de " + honraMinimaCriacao +
                ". Pelo RN004, você não pode criar novos rolés.");
        }
        Categoria categoria = categoriaRepository.findByNome(dto.category())
            .orElseThrow(() -> new NoSuchElementException("Categoria não encontrada: " + dto.category()));
        Endereco endereco = montarEndereco(dto);
        endereco = enderecoRepository.save(endereco);
        int limiteMax = organizador.getHonraMedia().compareTo(BigDecimal.valueOf(4.8)) >= 0 ? 50 : 20;
        int capacidade = Math.min(dto.capacity(), limiteMax);
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
        Inscricao inscricaoOrganizador = new Inscricao();
        inscricaoOrganizador.setUsuario(organizador);
        inscricaoOrganizador.setEvento(evento);
        inscricaoOrganizador.setStatusInscricao(StatusInscricao.CONFIRMADA);
        inscricaoRepository.save(inscricaoOrganizador);
        Comentario comentarioInicial = new Comentario();
        comentarioInicial.setAutor(organizador);
        comentarioInicial.setEvento(evento);
        comentarioInicial.setTexto("Rolé publicado. Confirmem presença por aqui.");
        comentarioRepository.save(comentarioInicial);
        return toResponseDTO(evento);
    }
    @Transactional
    public void excluirEvento(UUID eventoId, UUID usuarioId) {
        Evento evento = eventoRepository.findByIdWithRelations(eventoId)
            .orElseThrow(() -> new NoSuchElementException("Evento não encontrado: " + eventoId));
        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new NoSuchElementException("Usuário não encontrado: " + usuarioId));
        boolean organizador = evento.getOrganizador().getId().equals(usuarioId);
        boolean moderador = usuario.getPapelSistema() == PapelSistema.ADMINISTRADOR;
        if (!organizador && !moderador) {
            throw new IllegalStateException("Apenas o organizador ou um moderador pode excluir este rolé.");
        }
        eventoRepository.delete(evento);
    }
    @Transactional
    public EventoResponseDTO concluirEvento(UUID eventoId, UUID usuarioId) {
        Evento evento = eventoRepository.findByIdWithRelations(eventoId)
            .orElseThrow(() -> new NoSuchElementException("Evento não encontrado: " + eventoId));
        if (!evento.getOrganizador().getId().equals(usuarioId)) {
            throw new IllegalStateException(
                "Apenas o organizador pode concluir o evento.");
        }
        evento.concluir(OffsetDateTime.now());
        eventoRepository.save(evento);
        List<Inscricao> confirmadas = inscricaoRepository
            .findByEventoIdAndStatusInscricao(eventoId, StatusInscricao.CONFIRMADA);
        BigDecimal deducao = BigDecimal.valueOf(deducaoHonraAusencia);
        for (Inscricao inscricao : confirmadas) {
            if (inscricao.getUsuario().getId().equals(usuarioId)) {
                // o organizador conduziu o rolé: conta como presente e não é penalizado
                inscricao.registrarPresenca();
                inscricaoRepository.save(inscricao);
                continue;
            }
            if (!Boolean.TRUE.equals(inscricao.getCompareceu())) {
                inscricao.marcarAusente();
                inscricaoRepository.save(inscricao);
                Usuario usuario = inscricao.getUsuario();
                BigDecimal novaHonra = usuario.getHonraMedia().subtract(deducao);
                if (novaHonra.compareTo(BigDecimal.ZERO) < 0) {
                    novaHonra = BigDecimal.ZERO;
                }
                usuario.setHonraMedia(novaHonra);
                usuarioRepository.save(usuario);
                log.info("RN008: Ausência registrada para usuário '{}'. Honra: {} → {}",
                    usuario.getNome(), usuario.getHonraMedia().add(deducao), novaHonra);
            }
        }
        return toResponseDTO(evento);
    }
    public void recalcularHonra(UUID usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new NoSuchElementException("Usuário não encontrado: " + usuarioId));
        Optional<Double> media = avaliacaoRepository.calcularMediaNotas(usuarioId);
        if (media.isPresent() && media.get() != null) {
            BigDecimal novaHonra = BigDecimal.valueOf(media.get())
                .setScale(2, java.math.RoundingMode.HALF_UP);
            usuario.setHonraMedia(novaHonra);
            usuarioRepository.save(usuario);
            log.info("RN002: Honra recalculada para usuário '{}': {}", usuario.getNome(), novaHonra);
        }
    }
    @Transactional
    public AvaliacaoResponseDTO avaliarParticipante(UUID eventoId, UUID avaliadorId,
                                                     AvaliacaoRequestDTO dto) {
        Evento evento = eventoRepository.findByIdWithRelations(eventoId)
            .orElseThrow(() -> new NoSuchElementException("Evento não encontrado: " + eventoId));

        if (evento.getStatusEvento() != StatusEvento.CONCLUIDO) {
            throw new IllegalStateException(
                "Avaliações só podem ser registradas após a conclusão do evento.");
        }

        Usuario avaliador = usuarioRepository.findById(avaliadorId)
            .orElseThrow(() -> new NoSuchElementException(
                "Avaliador não encontrado: " + avaliadorId));

        Usuario avaliado = usuarioRepository.findById(dto.avaliadoId())
            .orElseThrow(() -> new NoSuchElementException(
                "Usuário avaliado não encontrado: " + dto.avaliadoId()));

        if (avaliadorId.equals(dto.avaliadoId())) {
            throw new IllegalArgumentException("Um usuário não pode avaliar a si mesmo.");
        }

        if (!participouDoEvento(avaliadorId, eventoId)) {
            throw new IllegalStateException(
                "Apenas participantes confirmados podem avaliar.");
        }

        if (!participouDoEvento(dto.avaliadoId(), eventoId)) {
            throw new IllegalStateException(
                "Só é possível avaliar quem participou do evento.");
        }

        if (avaliacaoRepository.existsByAvaliador_IdAndAvaliado_IdAndEvento_Id(
                avaliadorId, dto.avaliadoId(), eventoId)) {
            throw new IllegalStateException(
                "Você já avaliou este participante neste evento.");
        }

        Avaliacao avaliacao = new Avaliacao();
        avaliacao.setEvento(evento);
        avaliacao.setAvaliador(avaliador);
        avaliacao.setAvaliado(avaliado);
        avaliacao.setNota(dto.nota());
        avaliacao.setComentarioOpcional(dto.comentario());

        avaliacao = avaliacaoRepository.save(avaliacao);

        recalcularHonra(dto.avaliadoId());

        log.info("RF004: Avaliação registrada — avaliador={} avaliado={} nota={} evento={}",
            avaliador.getNome(), avaliado.getNome(), dto.nota(), evento.getTitulo());

        return new AvaliacaoResponseDTO(
            avaliacao.getId(),
            eventoId,
            avaliadorId,
            dto.avaliadoId(),
            dto.nota(),
            dto.comentario(),
            "Avaliação registrada com sucesso."
        );
    }

    private boolean participouDoEvento(UUID usuarioId, UUID eventoId) {
        return inscricaoRepository.findByUsuarioIdAndEventoId(usuarioId, eventoId)
            .map(i -> i.getStatusInscricao() == StatusInscricao.CONFIRMADA)
            .orElse(false);
    }

    private Endereco montarEndereco(EventoRequestDTO dto) {
        Endereco endereco = new Endereco();
        String cepLimpo = (dto.cep() != null) ? dto.cep().replaceAll("[^0-9]", "") : "";
        boolean cepValido = cepLimpo.length() == 8;
        if (cepValido) {
            Optional<ViaCEPService.ViaCEPResponse> viaCEP = viaCEPService.buscarEnderecoPorCep(cepLimpo);
            if (viaCEP.isPresent()) {
                ViaCEPService.ViaCEPResponse dados = viaCEP.get();
                endereco.setCep(cepLimpo);
                endereco.setLogradouro(valorOuFallback(dados.logradouro, dto.place(), "Local informado pelo organizador"));
                endereco.setBairro(valorOuFallback(dados.bairro, null, "Centro"));
                endereco.setCidade(valorOuFallback(dados.localidade, null, "Cidade não informada"));
                endereco.setEstado(valorOuFallback(dados.uf, null, "RJ"));
                endereco.setNumero("S/N");
                endereco.setComplemento(
                    (dados.complemento != null && !dados.complemento.isBlank())
                        ? dados.complemento
                        : (dto.place() != null && !dto.place().isBlank() ? dto.place() : null)
                );
                log.info("Endereço preenchido via ViaCEP: {}, {}/{}", dados.logradouro, dados.localidade, dados.uf);
                return endereco;
            }
            log.warn("ViaCEP indisponível ou CEP '{}' não encontrado. Usando preenchimento manual.", cepLimpo);
        }
        endereco.setCep(cepValido ? cepLimpo : "00000000");
        endereco.setLogradouro(dto.place() != null ? dto.place() : "Local informado pelo organizador");
        endereco.setBairro(extrairPrimeiraParte(dto.place(), "Centro"));
        endereco.setCidade(extrairSegundaParte(dto.place(), "Cidade não informada"));
        endereco.setEstado("RJ");
        endereco.setNumero("S/N");
        return endereco;
    }
    private String valorOuFallback(String valor, String alternativo, String padrao) {
        if (valor != null && !valor.isBlank()) return valor;
        if (alternativo != null && !alternativo.isBlank()) return alternativo;
        return padrao;
    }
    private String extrairPrimeiraParte(String texto, String padrao) {
        if (texto == null || texto.isBlank()) return padrao;
        return texto.contains(",") ? texto.split(",")[0].trim() : texto.trim();
    }
    private String extrairSegundaParte(String texto, String padrao) {
        if (texto == null || !texto.contains(",")) return padrao;
        return texto.split(",", 2)[1].trim();
    }
    public EventoResponseDTO toResponseDTO(Evento evento) {
        UUID eventoId = evento.getId();
        List<Inscricao> confirmadas = inscricaoRepository
            .findByEventoIdAndStatusInscricao(eventoId, StatusInscricao.CONFIRMADA);
        List<Inscricao> fila = inscricaoRepository
            .findByEventoIdAndStatusInscricao(eventoId, StatusInscricao.SOLICITADA);
        List<Comentario> comentarios = comentarioRepository.findByEventoIdWithAutor(eventoId);
        List<String> confirmedNames = confirmadas.stream()
            .map(i -> i.getUsuario().getNome().split(" ")[0])
            .collect(Collectors.toList());
        List<String> waitlistNames = fila.stream()
            .map(i -> i.getUsuario().getNome().split(" ")[0])
            .collect(Collectors.toList());
        List<EventoResponseDTO.CommentDTO> commentDTOs = comentarios.stream()
            .map(c -> new EventoResponseDTO.CommentDTO(
                c.getAutor().getNome().split(" ")[0],
                c.getTexto(),
                c.getDataHoraComentario() != null
                    ? fusoHorario.hora(c.getDataHoraComentario())
                    : "00:00"
            ))
            .collect(Collectors.toList());
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
        String dateStr = fusoHorario.data(evento.getDataHoraRealizacao()).toString();
        String timeStr = fusoHorario.hora(evento.getDataHoraRealizacao());
        Endereco end = evento.getEndereco();
        String placeStr = end.getBairro() + ", " + end.getCidade();
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
            0,
            evento.getLimiteParticipantes(),
            statusFront,
            organizerDTO,
            confirmedNames,
            waitlistNames,
            commentDTOs,
            0,
            evento.getDescricao()
        );
    }
}
