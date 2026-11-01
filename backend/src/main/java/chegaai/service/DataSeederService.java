package chegaai.service;
import chegaai.domain.enums.StatusEvento;
import chegaai.domain.enums.StatusInscricao;
import chegaai.domain.model.*;
import chegaai.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeederService implements CommandLineRunner {
    /** Senha de todas as contas de demonstração. */
    public static final String SENHA_DEMO = "admin123";
    /** Contas fictícias: o reset preserva as identidades e o histórico que envolve visitantes. */
    static final List<String> EMAILS_DEMO = List.of(
        "pedro@chegaai.dev", "caio@chegaai.dev", "nina@chegaai.dev", "marcos@chegaai.dev",
        "lia@chegaai.dev", "rafael@chegaai.dev", "bia@chegaai.dev", "paula@chegaai.dev",
        "bruno@chegaai.dev", "baixa@chegaai.dev", "moderador@chegaai.dev", "tester.pendente@chegaai.dev");
    public static boolean ehContaDeDemonstracao(String email) {
        return email != null && EMAILS_DEMO.contains(email);
    }
    private final CategoriaRepository categoriaRepository;
    private final EnderecoRepository enderecoRepository;
    private final UsuarioRepository usuarioRepository;
    private final EventoRepository eventoRepository;
    private final InscricaoRepository inscricaoRepository;
    private final ComentarioRepository comentarioRepository;
    private final InscricaoService inscricaoService;
    private final HonraService honraService;
    private final jakarta.persistence.EntityManager entityManager;
    private final PasswordEncoder passwordEncoder;
    private final NamedParameterJdbcTemplate jdbc;
    private String senhaDemoHash;
    @Value("${chegaai.demo.recriar-ao-iniciar:false}")
    private boolean recriarAoIniciar;
    @Override
    @Transactional
    public void run(String... args) {
        // Revisões simultâneas não podem reconstruir o mesmo conjunto de dados ao mesmo tempo.
        jdbc.query("SELECT pg_advisory_xact_lock(746283901)", Map.of(), (rs) -> { });
        if (recriarAoIniciar) {
            removerDemonstracao();
        } else if (usuarioRepository.findByEmail("pedro@chegaai.dev").isPresent()) {
            log.info("Dados de demonstração já existem no banco. Pulando seed.");
            return;
        }
        log.info("Iniciando carga de dados de demonstração...");
        senhaDemoHash = passwordEncoder.encode(SENHA_DEMO);
        Categoria catFutebol = salvarCategoria("Futebol", "Partidas e peladas de futebol.");
        Categoria catPets = salvarCategoria("Pets", "Passeios e encontros com animais de estimação.");
        Categoria catPatinacao = salvarCategoria("Patinação", "Treinos e passeios de patinação.");
        Categoria catPasseio = salvarCategoria("Passeio", "Passeios ao ar livre e caminhadas.");
        Categoria catEsporte = salvarCategoria("Esporte", "Atividades esportivas diversas.");
        Categoria catCultura = salvarCategoria("Cultura", "Eventos culturais e artísticos.");
        Categoria catLazer = salvarCategoria("Lazer", "Atividades de lazer e entretenimento.");
        Endereco endAterro = salvarEndereco("22250040", "Aterro do Flamengo", "Flamengo",
            "Rio de Janeiro", "RJ", "S/N", "Próximo à quadra principal");
        Endereco endBotafogo = salvarEndereco("22290240", "Praça Nelson Mandela", "Botafogo",
            "Rio de Janeiro", "RJ", "S/N", null);
        Endereco endCopacabana = salvarEndereco("22010000", "Avenida Atlântica", "Copacabana",
            "Rio de Janeiro", "RJ", "S/N", "Orla de Copacabana");
        Endereco endTijuca = salvarEndereco("20550013", "Rua Conde de Bonfim", "Tijuca",
            "Rio de Janeiro", "RJ", "100", null);
        Participante pedro = salvarParticipante("Pedro Farias", "pedro@chegaai.dev",
            "12345678901", "2000-03-15", BigDecimal.valueOf(4.90));
        Participante caio = salvarParticipante("Caio Santos", "caio@chegaai.dev",
            "23456789012", "1998-07-22", BigDecimal.valueOf(4.90));
        Participante nina = salvarParticipante("Nina Oliveira", "nina@chegaai.dev",
            "34567890123", "1999-12-05", BigDecimal.valueOf(4.70));
        Participante marcos = salvarParticipante("Marcos Lima", "marcos@chegaai.dev",
            "45678901234", "1997-05-10", BigDecimal.valueOf(5.0));
        Participante lia = salvarParticipante("Lia Costa", "lia@chegaai.dev",
            "67890123456", "2000-01-20", BigDecimal.valueOf(4.50));
        Participante rafael = salvarParticipante("Rafael Souza", "rafael@chegaai.dev",
            "78901234567", "1999-06-11", BigDecimal.valueOf(4.30));
        Participante bia = salvarParticipante("Bia Martins", "bia@chegaai.dev",
            "89012345678", "2001-04-25", BigDecimal.valueOf(4.60));
        Participante paula = salvarParticipante("Paula Mendes", "paula@chegaai.dev",
            "90123456789", "1998-11-30", BigDecimal.valueOf(4.20));
        Participante bruno = salvarParticipante("Bruno Silva", "bruno@chegaai.dev",
            "01234567890", "2000-08-14", BigDecimal.valueOf(4.10));
        Participante userBaixa = salvarParticipante("Tester Baixa Honra", "baixa@chegaai.dev",
            "11223344556", "2002-02-02", BigDecimal.valueOf(1.50));
        Administrador admin = salvarAdmin("Moderador ChegaAí", "moderador@chegaai.dev",
            "99988877766", "1990-01-01");
        Evento evtFutebol = salvarEvento(caio, catFutebol, endAterro,
            "Futebol no Aterro",
            "Pelada leve com times mistos. Chegar 15 minutos antes para dividir equipes e combinar regras.",
            LocalDate.now().plusDays(3).toString(), "09:00", 20);
        inscrever(caio, evtFutebol, StatusInscricao.CONFIRMADA);
        inscrever(marcos, evtFutebol, StatusInscricao.CONFIRMADA);
        inscrever(lia, evtFutebol, StatusInscricao.CONFIRMADA);
        inscrever(nina, evtFutebol, StatusInscricao.CONFIRMADA);
        inscrever(rafael, evtFutebol, StatusInscricao.CONFIRMADA);
        inscrever(bia, evtFutebol, StatusInscricao.CONFIRMADA);
        inscrever(pedro, evtFutebol, StatusInscricao.CONFIRMADA);
        comentar(marcos, evtFutebol, "Vou levar duas bolas e coletes.");
        comentar(lia, evtFutebol, "O ponto de encontro é perto da quadra principal?");
        Evento evtPets = salvarEvento(nina, catPets, endBotafogo,
            "Passeio com cães na praça",
            "Rolé tranquilo para socializar pets. Guia obrigatória e água para os cães.",
            LocalDate.now().plusDays(4).toString(), "16:30", 8);
        inscrever(nina, evtPets, StatusInscricao.CONFIRMADA);
        inscrever(paula, evtPets, StatusInscricao.CONFIRMADA);
        inscrever(bruno, evtPets, StatusInscricao.CONFIRMADA);
        inscrever(lia, evtPets, StatusInscricao.CONFIRMADA);
        inscrever(bia, evtPets, StatusInscricao.CONFIRMADA);
        inscrever(rafael, evtPets, StatusInscricao.CONFIRMADA);
        inscrever(pedro, evtPets, StatusInscricao.CONFIRMADA);
        inscrever(caio, evtPets, StatusInscricao.CONFIRMADA);
        inscrever(marcos, evtPets, StatusInscricao.SOLICITADA);
        comentar(paula, evtPets, "Meu cachorro é pequeno e fica bem com outros cães.");
        Evento evtPatinacao = salvarEvento(marcos, catPatinacao, endCopacabana,
            "Patinação iniciante na orla",
            "Treino básico para quem quer ganhar confiança. Embaixador organizando, capacete recomendado.",
            LocalDate.now().plusDays(5).toString(), "18:00", 50);
        inscrever(marcos, evtPatinacao, StatusInscricao.CONFIRMADA);
        inscrever(bruno, evtPatinacao, StatusInscricao.CONFIRMADA);
        inscrever(lia, evtPatinacao, StatusInscricao.CONFIRMADA);
        inscrever(pedro, evtPatinacao, StatusInscricao.CONFIRMADA);
        inscrever(nina, evtPatinacao, StatusInscricao.CONFIRMADA);
        comentar(lia, evtPatinacao, "Posso ir mesmo sabendo frear só um pouco?");
        Evento evtLazer = salvarEvento(marcos, catLazer, endTijuca,
            "Café e jogo de tabuleiro",
            "Encontro em local público. Evento temporariamente travado por análise de moderação.",
            LocalDate.now().plusDays(6).toString(), "15:00", 6);
        evtLazer.setTravado(true);
        eventoRepository.save(evtLazer);
        inscrever(marcos, evtLazer, StatusInscricao.CONFIRMADA);
        inscrever(bia, evtLazer, StatusInscricao.CONFIRMADA);
        inscrever(rafael, evtLazer, StatusInscricao.CONFIRMADA);
        inscrever(paula, evtLazer, StatusInscricao.CONFIRMADA);
        // --- INÍCIO: CENÁRIOS ESPECÍFICOS PARA TESTES E2E (QA) ---
        
        // 1. Evento Lotado para Marcos testar FIFO (Capacidade 3 - mínimo permitido)
        Evento evtLotadoFifo = salvarEvento(caio, catFutebol, endAterro,
            "Futebol de Domingo (Lotado)",
            "Evento criado especificamente para testar limite de vagas e transição para fila de espera.",
            LocalDate.now().plusDays(7).toString(), "10:00", 3);
        inscrever(caio, evtLotadoFifo, StatusInscricao.CONFIRMADA); 
        inscrever(lia, evtLotadoFifo, StatusInscricao.CONFIRMADA);
        inscrever(bruno, evtLotadoFifo, StatusInscricao.CONFIRMADA);
        // Evento agora tem 3/3 vagas ocupadas. Marcos (marcos@chegaai.dev) não é inscrito inicialmente.

        // 2. Evento Ativo onde Caio NÃO está inscrito (Testar bloqueio de comentários RN006)
        Evento evtComentarioBloqueado = salvarEvento(nina, catPasseio, endBotafogo,
            "Passeio Fechado (Sem Caio)",
            "Apenas confirmados e fila de espera podem comentar. Caio tentará comentar e falhará.",
            LocalDate.now().plusDays(2).toString(), "15:00", 10);
        inscrever(nina, evtComentarioBloqueado, StatusInscricao.CONFIRMADA);
        // Caio (caio@chegaai.dev) NÃO está inscrito neste evento.

        // 3. Usuário tester.pendente com participação concluída e compareceu=true, mas sem avaliação (RN007)
        Participante testerPendente = salvarParticipante("Tester Pendente", "tester.pendente@chegaai.dev",
            "12312312312", "1995-05-05", BigDecimal.valueOf(5.0));
        
        Evento evtPassadoSemAvaliacao = salvarEvento(pedro, catLazer, endTijuca,
            "Encontro Passado (Avaliação Pendente)",
            "Evento concluído no passado que requer avaliação mútua dos participantes.",
            LocalDate.now().minusDays(1).toString(), "10:00", 10);
        evtPassadoSemAvaliacao.setStatusEvento(StatusEvento.CONCLUIDO);
        eventoRepository.save(evtPassadoSemAvaliacao);
        
        Inscricao inscPendente = new Inscricao();
        inscPendente.setUsuario(testerPendente);
        inscPendente.setEvento(evtPassadoSemAvaliacao);
        inscPendente.setStatusInscricao(StatusInscricao.CONFIRMADA);
        inscPendente.setCompareceu(true);
        inscricaoRepository.save(inscPendente);
        // Rafael também esteve presente, então o tester.pendente tem alguém para avaliar. Avaliar e ter
        // avaliação pendente exigem presença; sem ela, o Rafael avaliaria sem ver pendência nenhuma.
        Inscricao inscRafael = new Inscricao();
        inscRafael.setUsuario(rafael);
        inscRafael.setEvento(evtPassadoSemAvaliacao);
        inscRafael.setStatusInscricao(StatusInscricao.CONFIRMADA);
        inscRafael.setCompareceu(true);
        inscricaoRepository.save(inscRafael);
        // Nenhuma avaliação é adicionada na tabela 'avaliacao' para este usuário neste evento.

        // --- FIM: CENÁRIOS E2E ---

        // Primeiro grava os novos cenários; só então relê as contas e aplica a fórmula única.
        entityManager.flush();
        entityManager.clear();
        List<UUID> contasDemo = jdbc.queryForList(
            "SELECT id_usuario FROM usuario WHERE email IN (:emails)", Map.of("emails", EMAILS_DEMO), UUID.class);
        honraService.travarUsuarios(contasDemo).values().forEach(honraService::recalcular);
        log.info("Carga de dados de demonstração concluída com sucesso!");
    }
    /** Reinicia os cenários fictícios sem apagar a identidade nem o histórico de visitantes. */
    private void removerDemonstracao() {
        List<UUID> ids = jdbc.queryForList(
            "SELECT id_usuario FROM usuario WHERE email IN (:emails)", Map.of("emails", EMAILS_DEMO), UUID.class);
        if (ids.isEmpty()) return;
        Map<String, Object> params = Map.of("ids", ids);
        // Eventos primeiro, em ordem estável; contas depois. Nenhum DELETE de conta provoca cascatas.
        List<UUID> eventosAfetados = jdbc.queryForList(
            "SELECT e.id_evento FROM evento e WHERE e.id_organizador IN (:ids) "
                + "OR EXISTS (SELECT 1 FROM inscricao i WHERE i.id_evento=e.id_evento AND i.id_usuario IN (:ids)) "
                + "ORDER BY e.id_evento FOR UPDATE OF e", params, UUID.class);
        jdbc.queryForList("SELECT id_usuario FROM usuario WHERE id_usuario IN (:ids) ORDER BY id_usuario FOR NO KEY UPDATE",
            params, UUID.class);
        // Em rolés de visitantes, a participação que já começou fica congelada, assim como na API.
        jdbc.update("DELETE FROM inscricao i USING evento e WHERE i.id_evento=e.id_evento "
            + "AND i.id_usuario IN (:ids) AND e.id_organizador NOT IN (:ids) "
            + "AND e.status_evento='ATIVO' AND e.data_hora_realizacao>clock_timestamp()", params);
        // Preserva concluídos com participação, avaliações ou comentários de visitantes.
        jdbc.update("DELETE FROM evento e WHERE e.id_organizador IN (:ids) AND NOT (e.status_evento='CONCLUIDO' AND ("
            + "EXISTS (SELECT 1 FROM inscricao i WHERE i.id_evento=e.id_evento AND i.id_usuario NOT IN (:ids)) "
            + "OR EXISTS (SELECT 1 FROM avaliacao a WHERE a.id_evento=e.id_evento AND (a.id_avaliador NOT IN (:ids) OR a.id_avaliado NOT IN (:ids))) "
            + "OR EXISTS (SELECT 1 FROM comentario c WHERE c.id_evento=e.id_evento AND c.id_usuario NOT IN (:ids))))", params);
        // So contas ficticias sao reiniciadas; as ausencias preservadas mantem o valor original.
        jdbc.update("DELETE FROM cancelamento_tardio WHERE id_usuario IN (:ids)", params);
        jdbc.update("UPDATE usuario u SET status_usuario='ATIVO', suspenso_ate=NULL, "
            + "desconto_honra_acumulado=COALESCE((SELECT SUM(i.desconto_honra_aplicado) FROM inscricao i "
            + "WHERE i.id_usuario=u.id_usuario AND i.status_inscricao='AUSENTE'), 0) "
            + "WHERE u.id_usuario IN (:ids)", params);
        entityManager.clear();
        for (UUID eventoId : eventosAfetados) {
            if (eventoRepository.existsById(eventoId)) inscricaoService.preencherVagasDaFila(eventoId);
        }
        entityManager.flush();
        entityManager.clear();
        jdbc.update("DELETE FROM endereco e WHERE NOT EXISTS "
            + "(SELECT 1 FROM evento ev WHERE ev.id_endereco=e.id_endereco)", Map.of());
        log.info("Cenários fictícios reiniciados; identidades e histórico dos visitantes preservados.");
    }
    private Categoria salvarCategoria(String nome, String descricao) {
        return categoriaRepository.findByNome(nome).orElseGet(() -> {
            Categoria cat = new Categoria();
            cat.setNome(nome);
            cat.setDescricao(descricao);
            cat.setAtiva(true);
            return categoriaRepository.save(cat);
        });
    }
    private Endereco salvarEndereco(String cep, String logradouro, String bairro,
                                     String cidade, String estado, String numero,
                                     String complemento) {
        Endereco end = new Endereco();
        end.setCep(cep);
        end.setLogradouro(logradouro);
        end.setBairro(bairro);
        end.setCidade(cidade);
        end.setEstado(estado);
        end.setNumero(numero);
        end.setComplemento(complemento);
        return enderecoRepository.save(end);
    }
    /**
     * Id fixo de cada conta de demonstração, derivado do e-mail. Recriar a demonstração (a cada
     * inicialização, na versão online) mantém os ids, e quem já tinha entrado continua com um token válido.
     */
    static UUID idDeDemonstracao(String email) {
        return UUID.nameUUIDFromBytes(("chegaai-demo:" + email).getBytes(StandardCharsets.UTF_8));
    }
    private Participante salvarParticipante(String nome, String email, String cpf,
                                             String dataNascimento, BigDecimal honra) {
        return (Participante) salvarConta("PARTICIPANTE", nome, email, cpf, dataNascimento, honra);
    }
    private Administrador salvarAdmin(String nome, String email, String cpf,
                                       String dataNascimento) {
        return (Administrador) salvarConta("ADMINISTRADOR", nome, email, cpf, dataNascimento, BigDecimal.valueOf(5.0));
    }
    // Por SQL porque o JPA gera o id sozinho (@GeneratedValue) e não aceita o id fixo.
    // A base da honra é sempre a daqui: uma revisão anterior à V10 já recriou as contas com o padrão 3,00.
    private Usuario salvarConta(String papel, String nome, String email, String cpf,
                                String dataNascimento, BigDecimal honra) {
        UUID id = idDeDemonstracao(email);
        jdbc.update("INSERT INTO usuario (id_usuario, nome, email, cpf, senha_hash, data_nascimento, honra_media, "
                + "status_usuario, papel_sistema, honra_inicial) "
                + "VALUES (:id, :nome, :email, :cpf, :senha, :nascimento, :honra, 'ATIVO', :papel, :honra) "
                + "ON CONFLICT (id_usuario) DO UPDATE SET nome=EXCLUDED.nome, senha_hash=EXCLUDED.senha_hash, "
                + "honra_inicial=EXCLUDED.honra_inicial",
            new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("nome", nome)
                .addValue("email", email)
                .addValue("cpf", cpf)
                .addValue("senha", senhaDemoHash)
                .addValue("nascimento", LocalDate.parse(dataNascimento))
                .addValue("honra", honra)
                .addValue("papel", papel));
        return usuarioRepository.findById(id).orElseThrow();
    }
    private Evento salvarEvento(Usuario organizador, Categoria categoria,
                                 Endereco endereco, String titulo, String descricao,
                                 String data, String hora, int limite) {
        Evento evt = new Evento();
        evt.setOrganizador(organizador);
        evt.setCategoria(categoria);
        evt.setEndereco(endereco);
        evt.setTitulo(titulo);
        evt.setDescricao(descricao);
        evt.setDataHoraRealizacao(OffsetDateTime.of(
            LocalDate.parse(data), LocalTime.parse(hora), ZoneOffset.ofHours(-3)));
        evt.setLimiteParticipantes(limite);
        evt.setStatusEvento(StatusEvento.ATIVO);
        evt.setTravado(false);
        return eventoRepository.save(evt);
    }
    private void inscrever(Usuario usuario, Evento evento, StatusInscricao status) {
        Inscricao insc = new Inscricao();
        insc.setUsuario(usuario);
        insc.setEvento(evento);
        insc.setStatusInscricao(status);
        inscricaoRepository.save(insc);
    }
    private void comentar(Usuario autor, Evento evento, String texto) {
        Comentario com = new Comentario();
        com.setAutor(autor);
        com.setEvento(evento);
        com.setTexto(texto);
        comentarioRepository.save(com);
    }
}
