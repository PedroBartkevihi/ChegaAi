UPDATE usuario
    SET nome = replace(nome, 'ChegAê', 'ChegaAí'),
        email = replace(email, '@chegae.dev', '@chegaai.dev');

UPDATE evento
    SET titulo = replace(titulo, 'ChegAê', 'ChegaAí');
