-- Os exemplos inseridos pela V2 têm data fixa (maio de 2026) e ficaram no feed como
-- um evento antigo ainda "aberto". A demonstração passou a ser criada pelo seed da
-- aplicação, com datas atuais, então esses registros saem.
DELETE FROM avaliacao
    WHERE id_avaliador IN (SELECT id_usuario FROM usuario WHERE email IN ('admin@chegaai.dev', 'embaixador@chegaai.dev', 'participante@chegaai.dev'))
       OR id_avaliado IN (SELECT id_usuario FROM usuario WHERE email IN ('admin@chegaai.dev', 'embaixador@chegaai.dev', 'participante@chegaai.dev'));

DELETE FROM denuncia
    WHERE id_denunciante IN (SELECT id_usuario FROM usuario WHERE email IN ('admin@chegaai.dev', 'embaixador@chegaai.dev', 'participante@chegaai.dev'))
       OR id_denunciado IN (SELECT id_usuario FROM usuario WHERE email IN ('admin@chegaai.dev', 'embaixador@chegaai.dev', 'participante@chegaai.dev'));

DELETE FROM penalidade
    WHERE id_usuario IN (SELECT id_usuario FROM usuario WHERE email IN ('admin@chegaai.dev', 'embaixador@chegaai.dev', 'participante@chegaai.dev'))
       OR id_admin_aplicador IN (SELECT id_usuario FROM usuario WHERE email IN ('admin@chegaai.dev', 'embaixador@chegaai.dev', 'participante@chegaai.dev'));

DELETE FROM evento
    WHERE id_evento = '77777777-7777-7777-7777-777777777777'
       OR id_organizador IN (SELECT id_usuario FROM usuario WHERE email IN ('admin@chegaai.dev', 'embaixador@chegaai.dev', 'participante@chegaai.dev'));

DELETE FROM usuario
    WHERE email IN ('admin@chegaai.dev', 'embaixador@chegaai.dev', 'participante@chegaai.dev');

DELETE FROM endereco e
    WHERE e.id_endereco = '33333333-3333-3333-3333-333333333333'
      AND NOT EXISTS (SELECT 1 FROM evento ev WHERE ev.id_endereco = e.id_endereco);

DELETE FROM categoria c
    WHERE c.id_categoria IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222')
      AND NOT EXISTS (SELECT 1 FROM evento ev WHERE ev.id_categoria = c.id_categoria);
