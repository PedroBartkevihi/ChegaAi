-- Exclusão da própria conta: os dados pessoais são apagados e a conta fica com o status EXCLUIDO,
-- para o histórico (presenças, avaliações e comentários) continuar apontando para ela.
ALTER TABLE usuario
    DROP CONSTRAINT ck_usuario_status,
    ADD CONSTRAINT ck_usuario_status CHECK (status_usuario IN ('ATIVO', 'SUSPENSO', 'BANIDO', 'EXCLUIDO'));
