package chegaai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class OrdemDaFilaMigrationIT extends IntegracaoBase {
    @Test
    @DisplayName("V8 preserva a ordem histórica por data/id e continua a sequência depois do backfill")
    void migraFilaExistente() throws Exception {
        verificarMigracao(true);
    }

    @Test
    @DisplayName("V8 começa a sequência em 1 quando ainda não há inscrições")
    void migraFilaVazia() throws Exception {
        verificarMigracao(false);
    }

    private void verificarMigracao(boolean comHistorico) throws Exception {
        String schema = "teste_fila_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = jdbcTemplate.getDataSource().getConnection();
             Statement sql = connection.createStatement()) {
            String schemaAnterior = connection.getSchema();
            sql.execute("CREATE SCHEMA " + schema);
            try {
                connection.setSchema(schema);
                sql.execute("CREATE TABLE inscricao (id_inscricao uuid PRIMARY KEY, id_evento uuid, "
                    + "status_inscricao varchar(20), data_hora_solicitacao timestamptz NOT NULL DEFAULT now())");
                if (comHistorico) {
                    sql.execute("INSERT INTO inscricao (id_inscricao, data_hora_solicitacao) VALUES "
                        + "('00000000-0000-0000-0000-000000000002', '2026-10-02T00:00:00Z'), "
                        + "('00000000-0000-0000-0000-000000000003', '2026-10-01T00:00:00Z'), "
                        + "('00000000-0000-0000-0000-000000000001', '2026-10-02T00:00:00Z')");
                }
                ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V8__ordem_da_fila.sql"));
                if (comHistorico) {
                    List<String> ids = new ArrayList<>();
                    try (ResultSet resultado = sql.executeQuery("SELECT id_inscricao FROM inscricao ORDER BY ordem_entrada")) {
                        while (resultado.next()) {
                            ids.add(resultado.getString(1));
                        }
                    }
                    assertThat(ids).containsExactly(
                        "00000000-0000-0000-0000-000000000003",
                        "00000000-0000-0000-0000-000000000001",
                        "00000000-0000-0000-0000-000000000002");
                }
                try (ResultSet resultado = sql.executeQuery("INSERT INTO inscricao (id_inscricao) VALUES "
                        + "('00000000-0000-0000-0000-000000000004') RETURNING ordem_entrada")) {
                    assertThat(resultado.next()).isTrue();
                    assertThat(resultado.getLong(1)).isEqualTo(comHistorico ? 4L : 1L);
                }
            } finally {
                connection.setSchema(schemaAnterior);
                sql.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }
}
