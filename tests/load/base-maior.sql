-- Base maior para a medição: cada rolé da demonstração ganha 28 cópias, com as inscrições e os
-- comentários dele, em dias seguidos (7 rolés viram 203). É a mesma base da medição do PR #23.
BEGIN;
CREATE TEMP TABLE mapa AS
SELECT e.id_evento AS original, gen_random_uuid() AS copia, n
FROM evento e CROSS JOIN generate_series(1, 28) AS n;
INSERT INTO evento (id_evento, id_organizador, id_categoria, id_endereco, titulo, descricao,
                    data_hora_realizacao, limite_participantes, status_evento, travado, criado_em)
SELECT m.copia, e.id_organizador, e.id_categoria, e.id_endereco, left(e.titulo || ' #' || m.n, 150), e.descricao,
       e.data_hora_realizacao + make_interval(days => m.n), e.limite_participantes, e.status_evento, e.travado, e.criado_em
FROM mapa m JOIN evento e ON e.id_evento = m.original;
INSERT INTO inscricao (id_inscricao, id_usuario, id_evento, data_hora_solicitacao, status_inscricao, compareceu)
SELECT gen_random_uuid(), i.id_usuario, m.copia, i.data_hora_solicitacao, i.status_inscricao, i.compareceu
FROM mapa m JOIN inscricao i ON i.id_evento = m.original;
INSERT INTO comentario (id_comentario, id_usuario, id_evento, texto, data_hora_comentario)
SELECT gen_random_uuid(), c.id_usuario, m.copia, c.texto, c.data_hora_comentario
FROM mapa m JOIN comentario c ON c.id_evento = m.original;
COMMIT;
