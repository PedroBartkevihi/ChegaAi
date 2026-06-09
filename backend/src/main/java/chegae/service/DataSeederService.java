package chegae.service;

import chegae.domain.enums.StatusEvento;
import chegae.domain.enums.StatusInscricao;
import chegae.domain.model.*;
import chegae.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Carga de dados de demonstração para o MVP.
 *
 * <p>Insere dados compatíveis com o protótipo do frontend (categorias,
 * usuários, endereços, eventos, inscrições e comentários) caso o banco
 * esteja vazio.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeederService implements CommandLineRunner {

    private final CategoriaRepository categoriaRepository;
    private final EnderecoRepository enderecoRepository;
    private final UsuarioRepository usuarioRepository;
    private final EventoRepository eventoRepository;
    private final InscricaoRepository inscricaoRepository;
    private final ComentarioRepository comentarioRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (usuarioRepository.findByEmail("pedro@chegae.dev").isPresent()) {
            log.info("Dados de demonstração já existem no banco. Pulando seed.");
            return;
        }

        log.info("Iniciando carga de dados de demonstração...");

        // === CATEGORIAS ===
        Categoria catFutebol = salvarCategoria("Futebol", "Partidas e peladas de futebol.");
        Categoria catPets = salvarCategoria("Pets", "Passeios e encontros com animais de estimação.");
        Categoria catPatinacao = salvarCategoria("Patinação", "Treinos e passeios de patinação.");
        Categoria catPasseio = salvarCategoria("Passeio", "Passeios ao ar livre e caminhadas.");
        Categoria catEsporte = salvarCategoria("Esporte", "Atividades esportivas diversas.");
        Categoria catCultura = salvarCategoria("Cultura", "Eventos culturais e artísticos.");
        Categoria catLazer = salvarCategoria("Lazer", "Atividades de lazer e entretenimento.");

        // === ENDEREÇOS ===
        Endereco endAterro = salvarEndereco("22250040", "Aterro do Flamengo", "Flamengo",
            "Rio de Janeiro", "RJ", "S/N", "Próximo à quadra principal");
        Endereco endBotafogo = salvarEndereco("22290240", "Praça Nelson Mandela", "Botafogo",
            "Rio de Janeiro", "RJ", "S/N", null);
        Endereco endCopacabana = salvarEndereco("22010000", "Avenida Atlântica", "Copacabana",
            "Rio de Janeiro", "RJ", "S/N", "Orla de Copacabana");
        Endereco endTijuca = salvarEndereco("20550013", "Rua Conde de Bonfim", "Tijuca",
            "Rio de Janeiro", "RJ", "100", null);

        // === USUÁRIOS ===
        // Pedro - embaixador (usuário logável principal)
        Participante pedro = salvarParticipante("Pedro Farias", "pedro@chegae.dev",
            "12345678901", "2000-03-15", BigDecimal.valueOf(4.90));

        // Caio - embaixador organizador
        Participante caio = salvarParticipante("Caio Santos", "caio@chegae.dev",
            "23456789012", "1998-07-22", BigDecimal.valueOf(4.90));

        // Nina - organizadora
        Participante nina = salvarParticipante("Nina Oliveira", "nina@chegae.dev",
            "34567890123", "1999-12-05", BigDecimal.valueOf(4.70));

        // Marcos - embaixador organizador
        Participante marcos = salvarParticipante("Marcos Lima", "marcos@chegae.dev",
            "45678901234", "1997-05-10", BigDecimal.valueOf(5.0));

        // Participantes extras para inscrições nos eventos
        Participante lia = salvarParticipante("Lia Costa", "lia@chegae.dev",
            "67890123456", "2000-01-20", BigDecimal.valueOf(4.50));
        Participante rafael = salvarParticipante("Rafael Souza", "rafael@chegae.dev",
            "78901234567", "1999-06-11", BigDecimal.valueOf(4.30));
        Participante bia = salvarParticipante("Bia Martins", "bia@chegae.dev",
            "89012345678", "2001-04-25", BigDecimal.valueOf(4.60));
        Participante paula = salvarParticipante("Paula Mendes", "paula@chegae.dev",
            "90123456789", "1998-11-30", BigDecimal.valueOf(4.20));
        Participante bruno = salvarParticipante("Bruno Silva", "bruno@chegae.dev",
            "01234567890", "2000-08-14", BigDecimal.valueOf(4.10));

        // Usuário com honra baixa para testar RN004
        Participante userBaixa = salvarParticipante("Tester Baixa Honra", "baixa@chegae.dev",
            "11223344556", "2002-02-02", BigDecimal.valueOf(2.50));

        // Administrador/Moderador
        Administrador admin = salvarAdmin("Moderador ChegAê", "moderador@chegae.dev",
            "99988877766", "1990-01-01");

        // === EVENTOS ===
        // Evento 1: Futebol no Aterro (Caio organizador)
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

        // Evento 2: Passeio com cães na praça (Nina organizadora) - lotado
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
        // Fila de espera
        inscrever(marcos, evtPets, StatusInscricao.SOLICITADA);

        comentar(paula, evtPets, "Meu cachorro é pequeno e fica bem com outros cães.");

        // Evento 3: Patinação iniciante na orla (Marcos organizador)
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

        // Evento 4: Café e jogo de tabuleiro (Marcos organizador) - TRAVADO
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

        log.info("Carga de dados de demonstração concluída com sucesso!");
    }

    // --- Helpers ---

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
            "$2a$10$DEMO_HASH_MVP_NAO_USAR", LocalDate.parse(dataNascimento));
        p.setHonraMedia(honra);
        return usuarioRepository.save(p);
    }

    private Administrador salvarAdmin(String nome, String email, String cpf,
                                       String dataNascimento) {
        Administrador a = new Administrador();
        a.setNome(nome);
        a.setEmail(email);
        a.setCpf(cpf);
        a.setSenhaHash("$2a$10$ADMIN_HASH_MVP_NAO_USAR");
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
