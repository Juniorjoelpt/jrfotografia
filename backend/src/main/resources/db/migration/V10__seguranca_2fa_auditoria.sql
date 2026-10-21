-- Fase 3 · bloco 6: verificação em duas etapas (app autenticador, TOTP) e registro de atividades.

ALTER TABLE usuario ADD COLUMN totp_ativo BOOLEAN NOT NULL DEFAULT FALSE;
-- Segredo TOTP cifrado (AES-GCM com chave derivada do segredo da aplicação).
ALTER TABLE usuario ADD COLUMN totp_segredo VARCHAR(200) NULL;
ALTER TABLE usuario ADD COLUMN totp_pendente VARCHAR(200) NULL;
-- Último intervalo de 30 s aceito: o mesmo código não serve duas vezes.
ALTER TABLE usuario ADD COLUMN totp_ultimo_passo BIGINT NULL;
-- Hashes SHA-256 dos códigos de recuperação ainda não usados, separados por vírgula.
ALTER TABLE usuario ADD COLUMN codigos_recuperacao TEXT NULL;

CREATE TABLE auditoria (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    estudio_id  BIGINT       NOT NULL,
    usuario_id  BIGINT       NULL,
    usuario     VARCHAR(120) NULL,
    acao        VARCHAR(120) NOT NULL,
    detalhe     VARCHAR(300) NULL,
    metodo      VARCHAR(10)  NULL,
    caminho     VARCHAR(200) NULL,
    entidade    VARCHAR(40)  NULL,
    entidade_id BIGINT       NULL,
    ip          VARCHAR(64)  NULL,
    criado_em   DATETIME     NOT NULL
);
CREATE INDEX ix_auditoria_estudio ON auditoria (estudio_id, criado_em);
