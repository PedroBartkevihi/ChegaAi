UPDATE usuario
    SET senha_hash = crypt('admin123', gen_salt('bf', 10))
    WHERE email LIKE '%@chegaai.dev'
      AND length(senha_hash) <> 60;
