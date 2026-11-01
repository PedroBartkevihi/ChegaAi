package chegaai.service;

import chegaai.domain.enums.StatusInscricao;
import chegaai.domain.enums.StatusUsuario;
import chegaai.domain.model.Evento;
import chegaai.domain.model.Usuario;
import chegaai.repository.EventoRepository;
import chegaai.repository.InscricaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Exclusão da própria conta: saem os dados pessoais e os compromissos futuros, fica o histórico.
 * Os rolés futuros que a pessoa organiza são excluídos, e as inscrições dela em rolés futuros são
 * canceladas sem penalidade, com a fila promovida. Presenças, avaliações e comentários continuam,
 * com o nome "Conta excluída".
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExclusaoContaService {
    public static final String NOME = "Conta excluída";
    public static final String SENHA_INCORRETA = "Senha incorreta.";
    public static final String CONTA_DE_DEMONSTRACAO = "As contas de demonstração não podem ser excluídas.";
    public static final String ROLE_EM_ANDAMENTO =
        "Você organiza um rolé que já começou. Conclua o rolé antes de excluir a conta.";
    public static final String ROLES_MUDARAM = "Seus rolés mudaram durante a exclusão. Tente de novo.";
    /** A coluna é obrigatória; a data verdadeira não fica guardada. */
    static final LocalDate NASCIMENTO = LocalDate.of(1900, 1, 1);
    private final JdbcTemplate jdbc;
    private final EventoRepository eventoRepository;
    private final InscricaoRepository inscricaoRepository;
    private final ContaService contaService;
    private final InscricaoService inscricaoService;
    private final CoordenacaoDemonstracaoService coordenacaoDemonstracao;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void excluir(UUID usuarioId, String senha) {
        coordenacaoDemonstracao.aguardarRecriacao();
        // Senha e e-mail lidos sem carregar a conta: a entidade só é lida depois da trava (ContaService.travar).
        Map<String, Object> conta = jdbc.queryForList(
                "SELECT email, senha_hash FROM usuario WHERE id_usuario = ?", usuarioId).stream().findFirst()
            .orElseThrow(() -> new NoSuchElementException("Usuário não encontrado: " + usuarioId));
        if (!passwordEncoder.matches(senha, (String) conta.get("senha_hash"))) {
            throw new IllegalArgumentException(SENHA_INCORRETA);
        }
        // A senha delas está publicada na tela de entrada: qualquer visitante as apagaria.
        if (DataSeederService.ehContaDeDemonstracao((String) conta.get("email"))) {
            throw new AccessDeniedException(CONTA_DE_DEMONSTRACAO);
        }
        OffsetDateTime agora = OffsetDateTime.now();
        if (eventoRepository.organizaRoleEmAndamento(usuarioId, agora)) {
            throw new IllegalStateException(ROLE_EM_ANDAMENTO);
        }
        List<UUID> organizados = eventoRepository.idsDosFuturosOrganizados(usuarioId, agora);
        List<UUID> inscritos = inscricaoRepository.idsDosRolesFuturosInscritos(usuarioId, agora);
        // Como nas inscrições, os rolés são travados antes da conta, e entre si em ordem estável.
        Map<UUID, Evento> travados = new LinkedHashMap<>();
        Stream.concat(organizados.stream(), inscritos.stream()).distinct()
            .sorted(Comparator.comparing(UUID::toString))
            .forEach(id -> eventoRepository.findByIdForUpdate(id).ifPresent(evento -> travados.put(id, evento)));
        Usuario usuario = contaService.travar(usuarioId);
        // Com a conta travada, a pessoa não cria rolés nem se inscreve. Se fez isso entre a leitura e a
        // trava, o rolé novo não está travado: recusar e repetir é mais simples que travá-lo fora de ordem.
        if (!travados.keySet().containsAll(eventoRepository.idsDosFuturosOrganizados(usuarioId, agora))
                || !travados.keySet().containsAll(inscricaoRepository.idsDosRolesFuturosInscritos(usuarioId, agora))) {
            throw new IllegalStateException(ROLES_MUDARAM);
        }
        for (UUID eventoId : organizados) {
            Evento evento = travados.get(eventoId);
            if (evento != null) {
                eventoRepository.delete(evento);
            }
        }
        for (UUID eventoId : inscritos) {
            inscricaoRepository.findByUsuarioIdAndEventoId(usuarioId, eventoId)
                .filter(inscricao -> inscricao.getStatusInscricao() == StatusInscricao.CONFIRMADA
                    || inscricao.getStatusInscricao() == StatusInscricao.SOLICITADA)
                .ifPresent(inscricao -> {
                    boolean eraConfirmada = inscricao.getStatusInscricao() == StatusInscricao.CONFIRMADA;
                    // Sem registrar cancelamento tardio: a conta sai e não há mais a quem penalizar.
                    inscricao.setStatusInscricao(StatusInscricao.CANCELADA);
                    inscricaoRepository.save(inscricao);
                    if (eraConfirmada) {
                        inscricaoService.preencherVagasDaFila(eventoId);
                    }
                });
        }
        anonimizar(usuario);
        log.info("Conta {} excluída pela própria pessoa. Rolés futuros excluídos: {}; inscrições canceladas: {}.",
            usuarioId, organizados.size(), inscritos.size());
    }

    private void anonimizar(Usuario usuario) {
        usuario.setNome(NOME);
        // Único e inválido de propósito: libera o e-mail verdadeiro para um cadastro novo.
        usuario.setEmail("excluida+" + usuario.getId() + "@chegaai.invalid");
        usuario.setCpf(null);
        // Hash de um valor aleatório que ninguém conhece: nenhuma senha volta a entrar.
        usuario.setSenhaHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        usuario.setDataNascimento(NASCIMENTO);
        usuario.setStatusUsuario(StatusUsuario.EXCLUIDO);
        usuario.setSuspensoAte(null);
    }
}
