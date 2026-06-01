ALTER TABLE usuario
    ALTER COLUMN cpf TYPE varchar(11) USING cpf::varchar;

ALTER TABLE endereco
    ALTER COLUMN cep TYPE varchar(8) USING cep::varchar,
    ALTER COLUMN estado TYPE varchar(2) USING estado::varchar;
