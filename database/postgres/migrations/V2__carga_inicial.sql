INSERT INTO categoria (id_categoria, nome, descricao, ativa) VALUES
    ('11111111-1111-1111-1111-111111111111', 'Música', 'Shows, festivais e apresentações ao vivo.', true),
    ('22222222-2222-2222-2222-222222222222', 'Tecnologia', 'Meetups, palestras e workshops.', true);

INSERT INTO endereco (id_endereco, cep, logradouro, bairro, cidade, estado, numero, complemento) VALUES
    ('33333333-3333-3333-3333-333333333333', '01001000', 'Praça da Sé', 'Sé', 'São Paulo', 'SP', '100', 'Próximo ao metrô');

INSERT INTO usuario (
    id_usuario,
    nome,
    email,
    cpf,
    senha_hash,
    data_nascimento,
    honra_media,
    status_usuario,
    papel_sistema,
    qtd_denuncias_validadas
) VALUES
    (
        '44444444-4444-4444-4444-444444444444',
        'Admin ChegAê',
        'admin@chegae.dev',
        '00000000000',
        '$2a$10$ADMIN_HASH_DEMO_NAO_USAR_EM_PRODUCAO',
        '1990-01-01',
        0,
        'ATIVO',
        'ADMINISTRADOR',
        0
    ),
    (
        '55555555-5555-5555-5555-555555555555',
        'Embaixador ChegAê',
        'embaixador@chegae.dev',
        '11111111111',
        '$2a$10$EMBAIXADOR_HASH_DEMO_NAO_USAR_EM_PRODUCAO',
        '1995-05-20',
        0,
        'ATIVO',
        'EMBAIXADOR',
        0
    ),
    (
        '66666666-6666-6666-6666-666666666666',
        'Participante ChegAê',
        'participante@chegae.dev',
        '22222222222',
        '$2a$10$PARTICIPANTE_HASH_DEMO_NAO_USAR_EM_PRODUCAO',
        '2001-10-10',
        0,
        'ATIVO',
        'PARTICIPANTE',
        0
    );

INSERT INTO evento (
    id_evento,
    id_organizador,
    id_categoria,
    id_endereco,
    titulo,
    descricao,
    data_hora_realizacao,
    limite_participantes,
    status_evento
) VALUES (
    '77777777-7777-7777-7777-777777777777',
    '55555555-5555-5555-5555-555555555555',
    '22222222-2222-2222-2222-222222222222',
    '33333333-3333-3333-3333-333333333333',
    'Meetup ChegAê - Backend & Dados',
    'Encontro para discutir arquitetura, escalabilidade e boas práticas.',
    '2026-05-10 19:00:00-03',
    50,
    'ATIVO'
);

INSERT INTO inscricao (
    id_inscricao,
    id_usuario,
    id_evento,
    data_hora_solicitacao,
    status_inscricao,
    compareceu
) VALUES (
    '88888888-8888-8888-8888-888888888888',
    '66666666-6666-6666-6666-666666666666',
    '77777777-7777-7777-7777-777777777777',
    '2026-04-30 12:00:00-03',
    'SOLICITADA',
    false
);

INSERT INTO comentario (
    id_comentario,
    id_usuario,
    id_evento,
    texto,
    data_hora_comentario
) VALUES (
    '99999999-9999-9999-9999-999999999999',
    '66666666-6666-6666-6666-666666666666',
    '77777777-7777-7777-7777-777777777777',
    'Inscrição realizada! Ansioso para o evento.',
    '2026-04-30 12:05:00-03'
);

UPDATE evento
SET status_evento = 'CONCLUIDO'
WHERE id_evento = '77777777-7777-7777-7777-777777777777';

UPDATE inscricao
SET status_inscricao = 'CONFIRMADA',
    compareceu = true
WHERE id_inscricao = '88888888-8888-8888-8888-888888888888';

INSERT INTO avaliacao (
    id_avaliacao,
    id_evento,
    id_avaliador,
    id_avaliado,
    nota,
    comentario_opcional,
    data_hora_avaliacao
) VALUES (
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    '77777777-7777-7777-7777-777777777777',
    '66666666-6666-6666-6666-666666666666',
    '55555555-5555-5555-5555-555555555555',
    5,
    'Evento excelente, conteúdo muito bem organizado.',
    '2026-05-10 22:30:00-03'
);
