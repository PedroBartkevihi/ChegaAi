CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE usuario (
    id_usuario uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    nome varchar(120) NOT NULL,
    email varchar(254) NOT NULL,
    cpf char(11) NOT NULL,
    senha_hash text NOT NULL,
    data_nascimento date NOT NULL,
    honra_media numeric(3,2) NOT NULL DEFAULT 0,
    status_usuario varchar(20) NOT NULL,
    papel_sistema varchar(20) NOT NULL,
    qtd_denuncias_validadas integer NOT NULL DEFAULT 0,
    criado_em timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT uk_usuario_email UNIQUE (email),
    CONSTRAINT uk_usuario_cpf UNIQUE (cpf),

    CONSTRAINT ck_usuario_cpf_formato CHECK (cpf ~ '^[0-9]{11}$'),
    CONSTRAINT ck_usuario_honra_media CHECK (honra_media BETWEEN 0 AND 5),
    CONSTRAINT ck_usuario_qtd_denuncias CHECK (qtd_denuncias_validadas >= 0),

    CONSTRAINT ck_usuario_status CHECK (status_usuario IN ('ATIVO', 'SUSPENSO', 'BANIDO')),
    CONSTRAINT ck_usuario_papel CHECK (papel_sistema IN ('ADMINISTRADOR', 'EMBAIXADOR', 'PARTICIPANTE'))
);

CREATE TABLE categoria (
    id_categoria uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    nome varchar(80) NOT NULL,
    descricao text,
    ativa boolean NOT NULL DEFAULT true,

    CONSTRAINT uk_categoria_nome UNIQUE (nome)
);

CREATE TABLE endereco (
    id_endereco uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    cep char(8) NOT NULL,
    logradouro varchar(150) NOT NULL,
    bairro varchar(100) NOT NULL,
    cidade varchar(100) NOT NULL,
    estado char(2) NOT NULL,
    numero varchar(20) NOT NULL,
    complemento varchar(150),

    CONSTRAINT ck_endereco_cep_formato CHECK (cep ~ '^[0-9]{8}$'),
    CONSTRAINT ck_endereco_estado_formato CHECK (estado ~ '^[A-Z]{2}$')
);

CREATE TABLE evento (
    id_evento uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_organizador uuid NOT NULL,
    id_categoria uuid NOT NULL,
    id_endereco uuid NOT NULL,
    titulo varchar(150) NOT NULL,
    descricao text NOT NULL,
    data_hora_realizacao timestamptz NOT NULL,
    limite_participantes integer NOT NULL,
    status_evento varchar(20) NOT NULL,
    travado boolean NOT NULL DEFAULT false,
    criado_em timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT fk_evento_organizador FOREIGN KEY (id_organizador)
        REFERENCES usuario (id_usuario)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_evento_categoria FOREIGN KEY (id_categoria)
        REFERENCES categoria (id_categoria)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_evento_endereco FOREIGN KEY (id_endereco)
        REFERENCES endereco (id_endereco)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT ck_evento_limite_participantes CHECK (limite_participantes > 0),
    CONSTRAINT ck_evento_status CHECK (status_evento IN ('ATIVO', 'CANCELADO', 'CONCLUIDO'))
);

CREATE TABLE inscricao (
    id_inscricao uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_usuario uuid NOT NULL,
    id_evento uuid NOT NULL,
    data_hora_solicitacao timestamptz NOT NULL DEFAULT now(),
    status_inscricao varchar(20) NOT NULL,
    compareceu boolean NOT NULL DEFAULT false,

    CONSTRAINT fk_inscricao_usuario FOREIGN KEY (id_usuario)
        REFERENCES usuario (id_usuario)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT fk_inscricao_evento FOREIGN KEY (id_evento)
        REFERENCES evento (id_evento)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT uk_inscricao_usuario_evento UNIQUE (id_usuario, id_evento),
    CONSTRAINT ck_inscricao_status CHECK (status_inscricao IN ('SOLICITADA', 'CONFIRMADA', 'CANCELADA', 'AUSENTE'))
);

CREATE TABLE avaliacao (
    id_avaliacao uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_evento uuid NOT NULL,
    id_avaliador uuid NOT NULL,
    id_avaliado uuid NOT NULL,
    nota integer NOT NULL,
    comentario_opcional text,
    data_hora_avaliacao timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT fk_avaliacao_evento FOREIGN KEY (id_evento)
        REFERENCES evento (id_evento)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT fk_avaliacao_avaliador FOREIGN KEY (id_avaliador)
        REFERENCES usuario (id_usuario)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_avaliacao_avaliado FOREIGN KEY (id_avaliado)
        REFERENCES usuario (id_usuario)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT uk_avaliacao_evento_avaliador_avaliado UNIQUE (id_evento, id_avaliador, id_avaliado),
    CONSTRAINT ck_avaliacao_nota CHECK (nota BETWEEN 1 AND 5),
    CONSTRAINT ck_avaliacao_usuarios_distintos CHECK (id_avaliador <> id_avaliado)
);

CREATE TABLE comentario (
    id_comentario uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_usuario uuid NOT NULL,
    id_evento uuid NOT NULL,
    texto text NOT NULL,
    data_hora_comentario timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT fk_comentario_usuario FOREIGN KEY (id_usuario)
        REFERENCES usuario (id_usuario)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT fk_comentario_evento FOREIGN KEY (id_evento)
        REFERENCES evento (id_evento)
        ON UPDATE CASCADE
        ON DELETE CASCADE
);

CREATE TABLE denuncia (
    id_denuncia uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_denunciante uuid NOT NULL,
    id_denunciado uuid NOT NULL,
    id_evento uuid,
    motivo varchar(120) NOT NULL,
    detalhes text,
    data_hora_denuncia timestamptz NOT NULL DEFAULT now(),
    status_denuncia varchar(20) NOT NULL,

    CONSTRAINT fk_denuncia_denunciante FOREIGN KEY (id_denunciante)
        REFERENCES usuario (id_usuario)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_denuncia_denunciado FOREIGN KEY (id_denunciado)
        REFERENCES usuario (id_usuario)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_denuncia_evento FOREIGN KEY (id_evento)
        REFERENCES evento (id_evento)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT ck_denuncia_status CHECK (status_denuncia IN ('ABERTA', 'EM_ANALISE', 'VALIDADA', 'REJEITADA', 'ENCERRADA')),
    CONSTRAINT ck_denuncia_usuarios_distintos CHECK (id_denunciante <> id_denunciado)
);

CREATE TABLE penalidade (
    id_penalidade uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_usuario uuid NOT NULL,
    id_admin_aplicador uuid NOT NULL,
    tipo_penalidade varchar(20) NOT NULL,
    motivo varchar(200) NOT NULL,
    data_inicio date NOT NULL,
    data_fim date,
    ativa boolean NOT NULL DEFAULT true,

    CONSTRAINT fk_penalidade_usuario FOREIGN KEY (id_usuario)
        REFERENCES usuario (id_usuario)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_penalidade_admin FOREIGN KEY (id_admin_aplicador)
        REFERENCES usuario (id_usuario)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT ck_penalidade_tipo CHECK (tipo_penalidade IN ('ADVERTENCIA', 'SUSPENSAO', 'BANIMENTO')),
    CONSTRAINT ck_penalidade_periodo CHECK (data_fim IS NULL OR data_fim >= data_inicio),
    CONSTRAINT ck_penalidade_usuarios_distintos CHECK (id_admin_aplicador <> id_usuario)
);
CREATE INDEX idx_evento_organizador ON evento (id_organizador);
CREATE INDEX idx_evento_categoria ON evento (id_categoria);
CREATE INDEX idx_evento_endereco ON evento (id_endereco);
CREATE INDEX idx_evento_status_data ON evento (status_evento, data_hora_realizacao);

CREATE INDEX idx_inscricao_evento ON inscricao (id_evento);
CREATE INDEX idx_inscricao_usuario ON inscricao (id_usuario);

CREATE INDEX idx_avaliacao_avaliador ON avaliacao (id_avaliador);
CREATE INDEX idx_avaliacao_avaliado ON avaliacao (id_avaliado);

CREATE INDEX idx_comentario_evento_data ON comentario (id_evento, data_hora_comentario);

CREATE INDEX idx_denuncia_denunciante ON denuncia (id_denunciante);
CREATE INDEX idx_denuncia_denunciado ON denuncia (id_denunciado);
CREATE INDEX idx_denuncia_evento ON denuncia (id_evento);

CREATE INDEX idx_penalidade_usuario ON penalidade (id_usuario);
CREATE INDEX idx_penalidade_admin ON penalidade (id_admin_aplicador);
