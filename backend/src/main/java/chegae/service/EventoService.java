package chegae.service;
import chegae.controller.dto.EventoRequestDTO;
import chegae.controller.dto.EventoResponseDTO;
import chegae.domain.enums.PapelSistema;
import chegae.domain.enums.StatusEvento;
import chegae.domain.enums.StatusInscricao;
import chegae.domain.model.*;
import chegae.repository.*;
import chegae.service.external.ViaCEPService;
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
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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
    @Value("${chegae.regras.honra-minima-criacao:2.0}")
    private double honraMinimaCriacao;
    @Value("${chegae.regras.deducao-honra-ausencia:0.5}")
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
        LocalDate data = LocalDate.parse(dto.date());
        LocalTime hora = LocalTime.parse(dto.time());
        OffsetDateTime dataHora = OffsetDateTime.of(data, hora, ZoneOffset.ofHours(-3));
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
        evento.concluir();
        eventoRepository.save(evento);
        List<Inscricao> confirmadas = inscricaoRepository
            .findByEventoIdAndStatusInscricao(eventoId, StatusInscricao.CONFIRMADA);
        BigDecimal deducao = BigDecimal.valueOf(deducaoHonraAusencia);
        for (Inscricao inscricao : confirmadas) {
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
        String dateStr = evento.getDataHoraRealizacao().toLocalDate().toString();
        String timeStr = evento.getDataHoraRealizacao().toLocalTime().format(timeFmt);
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
