package chegaai;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MigracaoHonraIT extends IntegracaoBase {
    @Test
    @DisplayName("V10 reconstrói somente ausências existentes e recalcula a honra pelo quórum")
    void migraHistoricoExistente() {
        String database = "legado_" + UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.execute("CREATE DATABASE " + database);
        String url = "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + database;
        JdbcTemplate banco = new JdbcTemplate(new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword()));
        try {
            Flyway.configure().dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
                .target(MigrationVersion.fromVersion("7")).load().migrate();
            UUID alvo = usuario(banco, "Alvo", "1.00");
            UUID poucasNotas = usuario(banco, "Poucas", "5.00");
            UUID semHistorico = usuario(banco, "SemHistorico", "1.23");
            UUID demo = usuario(banco, "Demo", "1.00");
            banco.update("UPDATE usuario SET email = 'pedro@chegaai.dev' WHERE id_usuario = ?", demo);
            UUID um = usuario(banco, "Um", "3.00");
            UUID dois = usuario(banco, "Dois", "3.00");
            UUID tres = usuario(banco, "Tres", "3.00");
            UUID categoria = UUID.randomUUID();
            banco.update("INSERT INTO categoria (id_categoria, nome) VALUES (?, 'Migração')", categoria);
            UUID endereco = UUID.randomUUID();
            banco.update("INSERT INTO endereco (id_endereco, cep, logradouro, bairro, cidade, estado, numero) " +
                "VALUES (?, '20000000', 'Rua de teste', 'Centro', 'Rio', 'RJ', '1')", endereco);
            UUID notas = evento(banco, um, categoria, endereco);
            UUID faltaUm = evento(banco, um, categoria, endereco);
            UUID faltaDois = evento(banco, um, categoria, endereco);
            ausencia(banco, alvo, faltaUm);
            ausencia(banco, alvo, faltaDois);
            ausencia(banco, poucasNotas, faltaUm);
            ausencia(banco, demo, faltaUm);
            nota(banco, notas, um, alvo, 5);
            nota(banco, notas, dois, alvo, 4);
            nota(banco, notas, tres, alvo, 3);
            nota(banco, notas, um, poucasNotas, 5);

            Flyway.configure().dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
                .load().migrate();

            assertValor(banco, alvo, "desconto_honra_acumulado", "1.00");
            assertValor(banco, alvo, "honra_media", "3.00");
            assertValor(banco, poucasNotas, "desconto_honra_acumulado", "0.50");
            assertValor(banco, poucasNotas, "honra_media", "2.50");
            assertValor(banco, semHistorico, "desconto_honra_acumulado", "0.00");
            assertValor(banco, semHistorico, "honra_media", "3.00");
            assertValor(banco, semHistorico, "honra_inicial", "3.00");
            assertValor(banco, demo, "honra_inicial", "4.90");
            assertValor(banco, demo, "desconto_honra_acumulado", "0.50");
            assertValor(banco, demo, "honra_media", "4.40");
        } finally {
            jdbcTemplate.execute("DROP DATABASE " + database);
        }
    }

    private static UUID usuario(JdbcTemplate banco, String nome, String honra) {
        UUID id = UUID.randomUUID();
        banco.update("INSERT INTO usuario (id_usuario, nome, email, senha_hash, data_nascimento, honra_media, " +
            "status_usuario, papel_sistema) VALUES (?, ?, ?, 'hash-teste', '2000-01-01', ?, 'ATIVO', 'PARTICIPANTE')",
            id, nome, id + "@teste.dev", new BigDecimal(honra));
        return id;
    }

    private static UUID evento(JdbcTemplate banco, UUID organizador, UUID categoria, UUID endereco) {
        UUID id = UUID.randomUUID();
        banco.update("INSERT INTO evento (id_evento, id_organizador, id_categoria, id_endereco, titulo, descricao, " +
            "data_hora_realizacao, limite_participantes, status_evento) " +
            "VALUES (?, ?, ?, ?, 'Histórico', 'Evento do histórico', now() - interval '1 day', 10, 'CONCLUIDO')",
            id, organizador, categoria, endereco);
        return id;
    }

    private static void ausencia(JdbcTemplate banco, UUID usuario, UUID evento) {
        banco.update("INSERT INTO inscricao (id_usuario, id_evento, status_inscricao, compareceu) " +
            "VALUES (?, ?, 'AUSENTE', false)", usuario, evento);
    }

    private static void nota(JdbcTemplate banco, UUID evento, UUID avaliador, UUID avaliado, int nota) {
        banco.update("INSERT INTO avaliacao (id_evento, id_avaliador, id_avaliado, nota) VALUES (?, ?, ?, ?)",
            evento, avaliador, avaliado, nota);
    }

    private static void assertValor(JdbcTemplate banco, UUID usuario, String coluna, String esperado) {
        assertThat(banco.queryForObject("SELECT " + coluna + " FROM usuario WHERE id_usuario = ?", BigDecimal.class, usuario))
            .isEqualByComparingTo(esperado);
    }
}
