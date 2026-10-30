package chegaai.repository;
import chegaai.domain.enums.StatusEvento;
import chegaai.domain.model.Evento;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * O JPQL leva só os filtros pedidos, em vez de parâmetros opcionais testados contra nulo.
 * A ordem repete a da tela: próximos por data e honra do organizador (decrescente); anteriores
 * na ordem inversa. O id fecha o desempate, para as páginas não repetirem nem pularem rolés.
 */
class EventoFeedRepositoryImpl implements EventoFeedRepository {
    // "Próximos" é a regra da tela: aberto, não travado e com o início ainda por vir.
    private static final String PROXIMO =
        "(e.statusEvento = :ativo and e.travado = false and e.dataHoraRealizacao >= :agora)";
    private static final String PRIMEIRO_NOME =
        "case when locate(' ', o.nome) > 0 then substring(o.nome, 1, locate(' ', o.nome) - 1) else o.nome end";
    private static final String TEXTO_DA_BUSCA =
        "lower(concat(e.titulo, ' ', c.nome, ' ', en.bairro, ', ', en.cidade, ' ', e.descricao, ' ', "
            + PRIMEIRO_NOME + "))";

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<Evento> buscarPagina(FiltroFeed filtro, OffsetDateTime agora, BigDecimal honraEmbaixador) {
        String ordem = filtro.secao() == FiltroFeed.Secao.PROXIMOS
            ? " order by e.dataHoraRealizacao asc, o.honraMedia desc, e.criadoEm desc, e.id asc"
            : " order by e.dataHoraRealizacao desc, o.honraMedia asc, e.criadoEm asc, e.id desc";
        var consulta = entityManager.createQuery("select e from Evento e join fetch e.organizador o "
            + "join fetch e.categoria c join fetch e.endereco en" + onde(filtro) + ordem, Evento.class);
        parametros(consulta, filtro, agora, honraEmbaixador);
        return consulta.setFirstResult(filtro.deslocamento()).setMaxResults(filtro.tamanho()).getResultList();
    }

    @Override
    public long contar(FiltroFeed filtro, OffsetDateTime agora, BigDecimal honraEmbaixador) {
        var consulta = entityManager.createQuery("select count(e) from Evento e join e.organizador o "
            + "join e.categoria c join e.endereco en" + onde(filtro), Long.class);
        parametros(consulta, filtro, agora, honraEmbaixador);
        return consulta.getSingleResult();
    }

    @Override
    public NumerosDoFeed numeros(BigDecimal honraEmbaixador) {
        Object[] linha = (Object[]) entityManager.createNativeQuery("""
                SELECT (SELECT count(*) FROM evento),
                       (SELECT count(*) FROM evento WHERE status_evento = 'ATIVO' AND travado = false),
                       (SELECT count(*) FROM inscricao WHERE status_inscricao = 'SOLICITADA'),
                       (SELECT count(*) FROM evento e JOIN usuario u ON u.id_usuario = e.id_organizador
                        WHERE u.honra_media >= :honraEmbaixador)
                """)
            .setParameter("honraEmbaixador", honraEmbaixador)
            .getSingleResult();
        return new NumerosDoFeed(numero(linha[0]), numero(linha[1]), numero(linha[2]), numero(linha[3]));
    }

    private static String onde(FiltroFeed filtro) {
        StringBuilder onde = new StringBuilder(filtro.secao() == FiltroFeed.Secao.PROXIMOS
            ? " where " + PROXIMO : " where not " + PROXIMO);
        if (filtro.categoria() != null) onde.append(" and c.nome = :categoria");
        if (filtro.honraMinima() != null) onde.append(" and o.honraMedia >= :honraMinima");
        if (filtro.embaixador()) onde.append(" and o.honraMedia >= :honraEmbaixador");
        if (filtro.padraoBusca() != null) onde.append(" and " + TEXTO_DA_BUSCA + " like :busca escape '\\'");
        return onde.toString();
    }

    private static void parametros(Query consulta, FiltroFeed filtro, OffsetDateTime agora,
                                   BigDecimal honraEmbaixador) {
        consulta.setParameter("ativo", StatusEvento.ATIVO);
        consulta.setParameter("agora", agora);
        if (filtro.categoria() != null) consulta.setParameter("categoria", filtro.categoria());
        if (filtro.honraMinima() != null) consulta.setParameter("honraMinima", filtro.honraMinima());
        if (filtro.embaixador()) consulta.setParameter("honraEmbaixador", honraEmbaixador);
        if (filtro.padraoBusca() != null) consulta.setParameter("busca", filtro.padraoBusca());
    }

    private static long numero(Object valor) {
        return ((Number) valor).longValue();
    }
}
