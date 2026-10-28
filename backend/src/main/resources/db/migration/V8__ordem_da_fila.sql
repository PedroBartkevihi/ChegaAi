-- Preserva a ordem anterior, com desempate por id, e dá uma nova posição a cada reentrada.
CREATE SEQUENCE inscricao_ordem_entrada_seq;

ALTER TABLE inscricao ADD COLUMN ordem_entrada bigint;

WITH ordenadas AS (
    SELECT id_inscricao,
           row_number() OVER (ORDER BY data_hora_solicitacao, id_inscricao) AS ordem
    FROM inscricao
)
UPDATE inscricao i
SET ordem_entrada = o.ordem
FROM ordenadas o
WHERE i.id_inscricao = o.id_inscricao;

SELECT setval('inscricao_ordem_entrada_seq',
              COALESCE(MAX(ordem_entrada), 1), COUNT(*) > 0)
FROM inscricao;

ALTER TABLE inscricao
    ALTER COLUMN ordem_entrada SET DEFAULT nextval('inscricao_ordem_entrada_seq'),
    ALTER COLUMN ordem_entrada SET NOT NULL,
    ADD CONSTRAINT uk_inscricao_ordem_entrada UNIQUE (ordem_entrada);

ALTER SEQUENCE inscricao_ordem_entrada_seq OWNED BY inscricao.ordem_entrada;

CREATE INDEX ix_inscricao_fila ON inscricao (id_evento, status_inscricao, ordem_entrada);
