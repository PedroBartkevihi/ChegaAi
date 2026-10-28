-- O CPF passa a ser opcional no cadastro: na versão de demonstração, ninguém precisa informar dados
-- reais. Os CPFs informados continuam únicos e com 11 dígitos (o UNIQUE e o CHECK valem para os não nulos).
ALTER TABLE usuario ALTER COLUMN cpf DROP NOT NULL;
