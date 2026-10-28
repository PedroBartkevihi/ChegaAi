ALTER TABLE usuario ADD COLUMN suspenso_ate timestamptz;

-- O histórico sobrevive à exclusão do rolé e à reentrada na mesma inscrição.
CREATE TABLE cancelamento_tardio (
    id_cancelamento uuid PRIMARY KEY,
    id_usuario uuid NOT NULL REFERENCES usuario(id_usuario) ON DELETE CASCADE,
    id_evento uuid REFERENCES evento(id_evento) ON DELETE SET NULL,
    cancelado_em timestamptz NOT NULL
);
CREATE INDEX idx_cancelamento_tardio_usuario_data ON cancelamento_tardio(id_usuario, cancelado_em);
