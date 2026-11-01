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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
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
    private final TransactionTemplate transacao;
    private final ContaService contaService;
    private final CoordenacaoDemonstracaoService coordenacaoDemonstracao;
    private final HonraService honraService;
    private final RegrasEventoService regrasEventoService;
    private final ApresentacaoEvento apresentacao;
    @Transactional(readOnly = true)
    public EventoResponseDTO buscarPorId(UUID id) {
        Evento evento = eventoRepository.findByIdWithRelations(id)
            .orElseThrow(() -> new NoSuchElementException("Evento não encontrado: " + id));
        return toResponseDTO(evento);
    }
    // Sem @Transactional: o ViaCEP é consultado antes, fora da transação, para uma demora dele não
    // prender uma conexão do banco (são só 5 na versão online). A parte do banco roda numa transação só.
    public EventoResponseDTO criarEvento(EventoRequestDTO dto, UUID usuarioId) {
        OffsetDateTime dataHora = fusoHorario.instante(lerData(dto.date()), lerHorario(dto.time()));
        if (!dataHora.isAfter(OffsetDateTime.now())) {
            throw new IllegalArgumentException("A data de realização deve ser uma data futura.");
        }
        // Recusa antes do ViaCEP o que já se sabe que vai falhar; a transação confere de novo.
        exigirCapacidadePermitida(organizadorApto(usuarioId), dto.capacity());
        categoriaPorNome(dto.category());
        Endereco endereco = montarEndereco(dto);
        return transacao.execute(status -> criarEventoNoBanco(dto, usuarioId, dataHora, endereco));
    }
    private EventoResponseDTO criarEventoNoBanco(EventoRequestDTO dto, UUID usuarioId,
                                                 OffsetDateTime dataHora, Endereco endereco) {
        coordenacaoDemonstracao.aguardarRecriacao();
        contaService.travarAtivo(usuarioId);
        Usuario organizador = organizadorApto(usuarioId);
        exigirCapacidadePermitida(organizador, dto.capacity());
        Categoria categoria = categoriaPorNome(dto.category());
        Endereco enderecoSalvo = enderecoRepository.save(endereco);
        Evento evento = new Evento();
        evento.setOrganizador(organizador);
        evento.setCategoria(categoria);
        evento.setEndereco(enderecoSalvo);
        evento.setTitulo(dto.title());
        evento.setDescricao(dto.description());
        evento.setDataHoraRealizacao(dataHora);
        evento.setLimiteParticipantes(dto.capacity());
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
        coordenacaoDemonstracao.aguardarRecriacao();
        Evento evento = eventoRepository.findByIdForUpdate(eventoId)
            .orElseThrow(() -> new NoSuchElementException("Evento não encontrado: " + eventoId));
        Usuario usuario = contaService.travarAtivo(usuarioId);
        boolean organizador = evento.getOrganizador().getId().equals(usuarioId);
        boolean moderador = usuario.getPapelSistema() == PapelSistema.ADMINISTRADOR;
        if (!organizador && !moderador) {
            throw new AccessDeniedException("Apenas o organizador ou um moderador pode excluir este rolé.");
        }
        // O moderador de demonstração tem a senha publicada na tela de entrada: ele só modera os
        // rolés da demonstração, para nenhum visitante apagar o que outras pessoas criaram.
        if (!organizador && DataSeederService.ehContaDeDemonstracao(usuario.getEmail())
                && !DataSeederService.ehContaDeDemonstracao(evento.getOrganizador().getEmail())) {
            throw new AccessDeniedException("O moderador de demonstração só exclui rolés das contas de demonstração.");
        }
        if (evento.getStatusEvento() == StatusEvento.CONCLUIDO) {
            throw new IllegalStateException("Rolés concluídos não podem ser excluídos: o histórico de presença e avaliações é preservado.");
        }
        eventoRepository.delete(evento);
    }
    @Transactional
    public EventoResponseDTO concluirEvento(UUID eventoId, UUID usuarioId) {
        coordenacaoDemonstracao.aguardarRecriacao();
        Evento evento = eventoRepository.findByIdForUpdate(eventoId)
            .orElseThrow(() -> new NoSuchElementException("Evento não encontrado: " + eventoId));
        if (!evento.getOrganizador().getId().equals(usuarioId)) {
            throw new AccessDeniedException("Apenas o organizador pode concluir o evento.");
        }
        List<Inscricao> confirmadas = inscricaoRepository
            .findByEventoIdAndStatusInscricao(eventoId, StatusInscricao.CONFIRMADA);
        var usuarios = honraService.travarUsuarios(java.util.stream.Stream.concat(
            confirmadas.stream().map(i -> i.getUsuario().getId()), java.util.stream.Stream.of(usuarioId)).toList());
        if (usuarios.get(usuarioId).contaEncerrada()) {
            throw new AccessDeniedException(usuarios.get(usuarioId).motivoDoBloqueio());
        }
        evento.concluir(OffsetDateTime.now());
        eventoRepository.save(evento);
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
                Usuario usuario = usuarios.get(inscricao.getUsuario().getId());
                honraService.registrarAusencia(inscricao);
                log.info("RN008: Ausência registrada para usuário {}. Honra: {}",
                    usuario.getId(), usuario.getHonraMedia());
            }
        }
        return toResponseDTO(evento);
    }
    @Transactional
    public void recalcularHonra(UUID usuarioId) {
        Usuario usuario = honraService.travarUsuarios(List.of(usuarioId)).get(usuarioId);
        honraService.recalcular(usuario);
    }
    @Transactional
    public AvaliacaoResponseDTO avaliarParticipante(UUID eventoId, UUID avaliadorId,
                                                     AvaliacaoRequestDTO dto) {
        coordenacaoDemonstracao.aguardarRecriacao();
        Evento evento = eventoRepository.findByIdForUpdate(eventoId)
            .orElseThrow(() -> new NoSuchElementException("Evento não encontrado: " + eventoId));

        if (evento.getStatusEvento() != StatusEvento.CONCLUIDO) {
            throw new IllegalStateException(
                "Avaliações só podem ser registradas após a conclusão do evento.");
        }

        var usuarios = honraService.travarUsuarios(List.of(avaliadorId, dto.avaliadoId()));
        Usuario avaliador = usuarios.get(avaliadorId);
        Usuario avaliado = usuarios.get(dto.avaliadoId());

        avaliador.exigirContaAtiva(OffsetDateTime.now());
        if (avaliadorId.equals(dto.avaliadoId())) {
            throw new IllegalArgumentException("Um usuário não pode avaliar a si mesmo.");
        }

        if (!participouDoEvento(avaliadorId, eventoId)) {
            throw new IllegalStateException(
                "Apenas quem esteve presente no rolé pode avaliar.");
        }

        if (!participouDoEvento(dto.avaliadoId(), eventoId)) {
            throw new IllegalStateException(
                "Só é possível avaliar quem esteve presente no rolé.");
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

        honraService.recalcular(avaliado);

        // Só ids: nome e título são dados de quem usa o app e não precisam ir para o log.
        log.info("RF004: Avaliação registrada — avaliador={} avaliado={} nota={} evento={}",
            avaliadorId, dto.avaliadoId(), dto.nota(), eventoId);

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

    private Usuario organizadorApto(UUID usuarioId) {
        Usuario organizador = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new NoSuchElementException("Usuário não encontrado: " + usuarioId));
        organizador.exigirContaAtiva(OffsetDateTime.now());
        BigDecimal honraMinimaCriacao = regrasEventoService.honraMinimaCriacao();
        if (organizador.getHonraMedia().compareTo(honraMinimaCriacao) < 0) {
            throw new IllegalStateException(
                "Sua honra está abaixo de " + honraMinimaCriacao +
                ". Pelo RN004, você não pode criar novos rolés.");
        }
        return organizador;
    }
    // Acima do limite da honra, o pedido é recusado: cortar em silêncio criaria um rolé diferente do pedido.
    private void exigirCapacidadePermitida(Usuario organizador, int capacidade) {
        int limite = regrasEventoService.limiteVagas(organizador);
        if (capacidade > limite) {
            throw new IllegalArgumentException(
                "Com a sua reputação atual, o rolé pode ter até " + limite + " participantes.");
        }
    }
    private Categoria categoriaPorNome(String nome) {
        return categoriaRepository.findByNome(nome)
            .orElseThrow(() -> new NoSuchElementException("Categoria não encontrada: " + nome));
    }
    // O mesmo critério das avaliações pendentes (findCompareceuEmEventosConcluidos): confirmado e presente.
    private boolean participouDoEvento(UUID usuarioId, UUID eventoId) {
        return inscricaoRepository.findByUsuarioIdAndEventoId(usuarioId, eventoId)
            .map(i -> i.getStatusInscricao() == StatusInscricao.CONFIRMADA && Boolean.TRUE.equals(i.getCompareceu()))
            .orElse(false);
    }

    // O DTO já garante o formato (AAAA-MM-DD e HH:MM); aqui se recusa o que não existe, como 30/02 ou 25:99.
    private static LocalDate lerData(String data) {
        try {
            return LocalDate.parse(data);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                "Data inválida: " + data + ". Informe uma data que exista no calendário, no formato AAAA-MM-DD.");
        }
    }
    private static LocalTime lerHorario(String horario) {
        try {
            return LocalTime.parse(horario);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                "Horário inválido: " + horario + ". Informe um horário entre 00:00 e 23:59.");
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
        endereco.setCep(cepValido ? cepLimpo : Endereco.CEP_NAO_INFORMADO);
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
        // Confirmados e fila numa consulta só, já com o usuário: os nomes não custam uma consulta por pessoa.
        List<Inscricao> inscricoes = inscricaoRepository.listarParticipantes(eventoId,
            List.of(StatusInscricao.CONFIRMADA, StatusInscricao.SOLICITADA));
        List<Inscricao> confirmadas = inscricoes.stream()
            .filter(i -> i.getStatusInscricao() == StatusInscricao.CONFIRMADA).toList();
        List<Inscricao> fila = inscricoes.stream()
            .filter(i -> i.getStatusInscricao() == StatusInscricao.SOLICITADA).toList();
        List<Comentario> comentarios = comentarioRepository.findByEventoIdWithAutor(eventoId);
        List<String> confirmedNames = confirmadas.stream()
            .map(i -> i.getUsuario().primeiroNome())
            .collect(Collectors.toList());
        List<String> waitlistNames = fila.stream()
            .map(i -> i.getUsuario().primeiroNome())
            .collect(Collectors.toList());
        List<EventoResponseDTO.CommentDTO> commentDTOs = comentarios.stream()
            .map(c -> new EventoResponseDTO.CommentDTO(
                c.getAutor().primeiroNome(),
                c.getTexto(),
                c.getDataHoraComentario() != null
                    ? fusoHorario.hora(c.getDataHoraComentario())
                    : "00:00"
            ))
            .collect(Collectors.toList());
        return new EventoResponseDTO(
            evento.getId(),
            evento.getTitulo(),
            evento.getCategoria().getNome(),
            apresentacao.data(evento),
            apresentacao.hora(evento),
            apresentacao.cep(evento),
            apresentacao.local(evento),
            evento.getLimiteParticipantes(),
            apresentacao.status(evento),
            apresentacao.organizador(evento),
            confirmedNames,
            waitlistNames,
            commentDTOs,
            evento.getDescricao()
        );
    }
}
