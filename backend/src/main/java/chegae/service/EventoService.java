package chegae.service;

import chegae.controller.dto.EventoRequestDTO;
import chegae.controller.dto.EventoResponseDTO;
import chegae.domain.enums.StatusEvento;
import chegae.domain.enums.StatusInscricao;
import chegae.domain.model.*;
import chegae.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Serviço de gerenciamento de eventos.
 * Contém lógica de criação, listagem e conversão para DTO compatível com o frontend.
 */
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
    private final ViaCEPService viaCEPService;   // <-- NOVO: injetado via @RequiredArgsConstructor

    @Value("${chegae.regras.honra-minima-criacao:2.0}")
    private double honraMinimaCriacao;

    @Value("${chegae.regras.deducao-honra-ausencia:0.5}")
    private double deducaoHonraAusencia;

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
     *
     * <p>Integração com ViaCEP (UC03 — ator externo "API de Localização"):
     * O CEP informado pelo organizador é consultado na API ViaCEP para
     * preencher automaticamente logradouro, bairro, cidade e estado.
     * Caso a API esteja indisponível ou o CEP seja inválido (FE1), o sistema
     * usa o campo {@code place} informado pelo usuário como fallback.</p>
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

        // Buscar categoria
        Categoria categoria = categoriaRepository.findByNome(dto.category())
            .orElseThrow(() -> new NoSuchElementException("Categoria não encontrada: " + dto.category()));

        // ---------------------------------------------------------------
        // Montar entidade Endereco com integração ViaCEP
        // ---------------------------------------------------------------
        Endereco endereco = montarEndereco(dto);
        endereco = enderecoRepository.save(endereco);

        // Montar data/hora
        LocalDate data = LocalDate.parse(dto.date());
        LocalTime hora = LocalTime.parse(dto.time());
        OffsetDateTime dataHora = OffsetDateTime.of(data, hora, ZoneOffset.ofHours(-3));

        // RN003: limitar capacidade por papel (Embaixador: até 50, demais: até 20)
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

    // -----------------------------------------------------------------------
    // RN008 — Conclusão de evento e tratamento de ausências
    // -----------------------------------------------------------------------

    /**
     * Conclui um evento e aplica RN008 (dedução de honra por ausência).
     *
     * <p>Fluxo:</p>
     * <ol>
     *   <li>Verifica que o chamador é o organizador do evento</li>
     *   <li>Transiciona o evento para {@code CONCLUIDO}</li>
     *   <li>Para cada inscrição CONFIRMADA com {@code compareceu == false}:
     *       marca como AUSENTE e deduz {@code 0.5} da honraMedia (mínimo 0)</li>
     *   <li>Persiste todas as alterações</li>
     * </ol>
     *
     * @param eventoId  ID do evento a concluir
     * @param usuarioId ID do usuário que solicita a conclusão (deve ser o organizador)
     * @return DTO do evento atualizado
     */
    @Transactional
    public EventoResponseDTO concluirEvento(UUID eventoId, UUID usuarioId) {
        Evento evento = eventoRepository.findByIdWithRelations(eventoId)
            .orElseThrow(() -> new NoSuchElementException("Evento não encontrado: " + eventoId));

        // Verificar que o chamador é o organizador
        if (!evento.getOrganizador().getId().equals(usuarioId)) {
            throw new IllegalStateException(
                "Apenas o organizador pode concluir o evento.");
        }

        // Transição ATIVO → CONCLUIDO (validação de estado feita no domínio)
        evento.concluir();
        eventoRepository.save(evento);

        // RN008: tratar ausências não justificadas
        List<Inscricao> confirmadas = inscricaoRepository
            .findByEventoIdAndStatusInscricao(eventoId, StatusInscricao.CONFIRMADA);

        BigDecimal deducao = BigDecimal.valueOf(deducaoHonraAusencia);

        for (Inscricao inscricao : confirmadas) {
            if (!Boolean.TRUE.equals(inscricao.getCompareceu())) {
                // Marcar como AUSENTE (transição de domínio CONFIRMADA → AUSENTE)
                inscricao.marcarAusente();
                inscricaoRepository.save(inscricao);

                // Deduzir honra do usuário (mínimo 0)
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

    // -----------------------------------------------------------------------
    // RN002 — Recálculo de honra
    // -----------------------------------------------------------------------

    /**
     * Recalcula a honraMedia de um usuário com base na média das avaliações recebidas (RN002).
     *
     * <p>Deve ser chamado sempre que uma nova {@link Avaliacao} for salva.</p>
     *
     * @param usuarioId ID do usuário avaliado
     */
    public void recalcularHonra(UUID usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new NoSuchElementException("Usuário não encontrado: " + usuarioId));

        Optional<BigDecimal> media = avaliacaoRepository.calcularMediaNotas(usuarioId);

        if (media.isPresent() && media.get() != null) {
            // Escala para 2 casas decimais (compatível com numeric(3,2))
            BigDecimal novaHonra = media.get().setScale(2, java.math.RoundingMode.HALF_UP);
            usuario.setHonraMedia(novaHonra);
            usuarioRepository.save(usuario);
            log.info("RN002: Honra recalculada para usuário '{}': {}", usuario.getNome(), novaHonra);
        }
    }

    // -----------------------------------------------------------------------
    // Integração ViaCEP — método privado
    // -----------------------------------------------------------------------

    /**
     * Constrói a entidade {@link Endereco} consultando a API ViaCEP quando
     * o organizador informa um CEP.
     *
     * <h3>Estratégia de preenchimento</h3>
     * <ol>
     *   <li><b>CEP informado e ViaCEP disponível:</b> logradouro, bairro,
     *       cidade e estado são preenchidos automaticamente pela API.
     *       O campo {@code place} do formulário é usado como complemento
     *       ou referência, se fornecido.</li>
     *   <li><b>CEP inválido ou ViaCEP indisponível (FE1 — UC03):</b>
     *       o endereço é montado a partir do campo {@code place} do DTO
     *       (preenchimento manual pelo usuário).</li>
     * </ol>
     *
     * @param dto DTO com os dados do formulário de criação de evento
     * @return entidade Endereco pronta para persistência (sem id)
     */
    private Endereco montarEndereco(EventoRequestDTO dto) {
        Endereco endereco = new Endereco();

        String cepLimpo = (dto.cep() != null) ? dto.cep().replaceAll("[^0-9]", "") : "";
        boolean cepValido = cepLimpo.length() == 8;

        if (cepValido) {
            // Tenta consultar a ViaCEP
            Optional<ViaCEPService.ViaCEPResponse> viaCEP = viaCEPService.buscarEnderecoPorCep(cepLimpo);

            if (viaCEP.isPresent()) {
                ViaCEPService.ViaCEPResponse dados = viaCEP.get();

                // Preencher com dados reais da API
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

            // FE1: ViaCEP falhou — registra no log e usa fallback manual
            log.warn("ViaCEP indisponível ou CEP '{}' não encontrado. Usando preenchimento manual.", cepLimpo);
        }

        // ---------------------------------------------------------------
        // Fallback: preenchimento manual a partir do campo 'place'
        // ---------------------------------------------------------------
        endereco.setCep(cepValido ? cepLimpo : "00000000");
        endereco.setLogradouro(dto.place() != null ? dto.place() : "Local informado pelo organizador");
        endereco.setBairro(extrairPrimeiraParte(dto.place(), "Centro"));
        endereco.setCidade(extrairSegundaParte(dto.place(), "Cidade não informada"));
        endereco.setEstado("RJ");
        endereco.setNumero("S/N");

        return endereco;
    }

    /**
     * Retorna {@code valor} se não nulo e não vazio; caso contrário, tenta
     * {@code alternativo}; se este também falhar, retorna {@code padrao}.
     */
    private String valorOuFallback(String valor, String alternativo, String padrao) {
        if (valor != null && !valor.isBlank()) return valor;
        if (alternativo != null && !alternativo.isBlank()) return alternativo;
        return padrao;
    }

    /** Extrai a parte antes da primeira vírgula de uma String como "Bairro, Cidade". */
    private String extrairPrimeiraParte(String texto, String padrao) {
        if (texto == null || texto.isBlank()) return padrao;
        return texto.contains(",") ? texto.split(",")[0].trim() : texto.trim();
    }

    /** Extrai a parte após a primeira vírgula de uma String como "Bairro, Cidade". */
    private String extrairSegundaParte(String texto, String padrao) {
        if (texto == null || !texto.contains(",")) return padrao;
        return texto.split(",", 2)[1].trim();
    }

    // -----------------------------------------------------------------------
    // Conversão para DTO do frontend
    // -----------------------------------------------------------------------

    /**
     * Converte uma entidade Evento para o DTO compatível com o frontend React.
     */
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