-- Valores históricos do código anterior: base 3,0, quórum 3 e desconto de 0,5 por ausência.
-- Reconstrói somente AUSENTE ainda existentes; não presume histórico já apagado.
ALTER TABLE usuario
    ADD COLUMN honra_inicial numeric(3,2) NOT NULL DEFAULT 3.00,
    ADD COLUMN desconto_honra_acumulado numeric(12,2) NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_usuario_honra_inicial CHECK (honra_inicial BETWEEN 0 AND 5),
    ADD CONSTRAINT ck_usuario_desconto_honra CHECK (desconto_honra_acumulado >= 0);

-- Contas fictícias têm bases próprias para demonstrar cenários; valores do DataSeederService.
-- Usuários reais recebem a base histórica 3,0, sem inferir dados de uma honra já inconsistente.
UPDATE usuario
SET honra_inicial = CASE email
    WHEN 'pedro@chegaai.dev' THEN 4.90
    WHEN 'caio@chegaai.dev' THEN 4.90
    WHEN 'nina@chegaai.dev' THEN 4.70
    WHEN 'marcos@chegaai.dev' THEN 5.00
    WHEN 'lia@chegaai.dev' THEN 4.50
    WHEN 'rafael@chegaai.dev' THEN 4.30
    WHEN 'bia@chegaai.dev' THEN 4.60
    WHEN 'paula@chegaai.dev' THEN 4.20
    WHEN 'bruno@chegaai.dev' THEN 4.10
    WHEN 'baixa@chegaai.dev' THEN 1.50
    WHEN 'moderador@chegaai.dev' THEN 5.00
    WHEN 'tester.pendente@chegaai.dev' THEN 5.00
    ELSE 3.00
END;

ALTER TABLE inscricao
    ADD COLUMN desconto_honra_aplicado numeric(12,2) NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_inscricao_desconto_honra CHECK (desconto_honra_aplicado >= 0);

-- A ausencia guarda o valor original: mudar a configuracao nao altera o passado.
UPDATE inscricao SET desconto_honra_aplicado = 0.50 WHERE status_inscricao = 'AUSENTE';

UPDATE usuario u
SET desconto_honra_acumulado = COALESCE((
    SELECT SUM(i.desconto_honra_aplicado) FROM inscricao i
    WHERE i.id_usuario = u.id_usuario AND i.status_inscricao = 'AUSENTE'
), 0);

UPDATE usuario u
SET honra_media = GREATEST(0.00, LEAST(5.00,
    CASE WHEN (SELECT COUNT(*) FROM avaliacao a WHERE a.id_avaliado = u.id_usuario) >= 3
        THEN (SELECT AVG(a.nota) FROM avaliacao a WHERE a.id_avaliado = u.id_usuario)
        ELSE u.honra_inicial
    END - u.desconto_honra_acumulado
));
