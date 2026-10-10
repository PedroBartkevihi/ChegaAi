package chegaai.service;
import chegaai.domain.enums.StatusEvento;
import chegaai.domain.enums.StatusInscricao;
import chegaai.domain.model.*;
import chegaai.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeederService implements CommandLineRunner {
    /** Senha de todas as contas de demonstração. */
    public static final String SENHA_DEMO = "admin123";
    private final CategoriaRepository categoriaRepository;
    private final EnderecoRepository enderecoRepository;
    private final UsuarioRepository usuarioRepository;
    private final EventoRepository eventoRepository;
    private final InscricaoRepository inscricaoRepository;
    private final ComentarioRepository comentarioRepository;
    private final PasswordEncoder passwordEncoder;
    private String senhaDemoHash;
    @Override
    @Transactional
    public void run(String... args) {
        if (usuarioRepository.findByEmail("pedro@chegaai.dev").isPresent()) {
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
            "11223344556", "2002-02-02", BigDecimal.valueOf(2.50));
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
            LocalDate.now().plusDays(1).toString(), "10:00", 10);
        evtPassadoSemAvaliacao.setStatusEvento(StatusEvento.CONCLUIDO);
        eventoRepository.save(evtPassadoSemAvaliacao);
        
        Inscricao inscPendente = new Inscricao();
        inscPendente.setUsuario(testerPendente);
        inscPendente.setEvento(evtPassadoSemAvaliacao);
        inscPendente.setStatusInscricao(StatusInscricao.CONFIRMADA);
        inscPendente.setCompareceu(true);
        inscricaoRepository.save(inscPendente);
        // Nenhuma avaliação é adicionada na tabela 'avaliacao' para este usuário neste evento.

        // --- FIM: CENÁRIOS E2E ---

        log.info("Carga de dados de demonstração concluída com sucesso!");
    }
    private Categoria salvarCategoria(String nome, String descricao) {
        Categoria cat = new Categoria();
        cat.setNome(nome);
        cat.setDescricao(descricao);
        cat.setAtiva(true);
        return categoriaRepository.save(cat);
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
    private Participante salvarParticipante(String nome, String email, String cpf,
                                             String dataNascimento, BigDecimal honra) {
        Participante p = new Participante(nome, email, cpf,
            senhaDemoHash, LocalDate.parse(dataNascimento));
        p.setHonraMedia(honra);
        return usuarioRepository.save(p);
    }
    private Administrador salvarAdmin(String nome, String email, String cpf,
                                       String dataNascimento) {
        Administrador a = new Administrador();
        a.setNome(nome);
        a.setEmail(email);
        a.setCpf(cpf);
        a.setSenhaHash(senhaDemoHash);
        a.setDataNascimento(LocalDate.parse(dataNascimento));
        a.setHonraMedia(BigDecimal.valueOf(5.0));
        return usuarioRepository.save(a);
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
