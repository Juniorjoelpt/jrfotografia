-- E-mails automáticos por SMTP (Gmail com senha de app, Brevo, Outlook…), configurados em Ajustes › E-mail.
ALTER TABLE estudio ADD COLUMN email_ativo BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE estudio ADD COLUMN smtp_host VARCHAR(120) NULL;
ALTER TABLE estudio ADD COLUMN smtp_porta INT NULL;
ALTER TABLE estudio ADD COLUMN smtp_usuario VARCHAR(160) NULL;
-- Senha cifrada (AES-GCM); nunca volta para a tela.
ALTER TABLE estudio ADD COLUMN smtp_senha VARCHAR(400) NULL;
ALTER TABLE estudio ADD COLUMN smtp_seguranca VARCHAR(10) NULL;
ALTER TABLE estudio ADD COLUMN email_remetente VARCHAR(160) NULL;
-- O fotógrafo recebe avisos (nova pré-reserva, comprovante, contrato assinado…)?
ALTER TABLE estudio ADD COLUMN email_avisos BOOLEAN NOT NULL DEFAULT TRUE;
-- Lembretes automáticos para a cliente (ensaio amanhã, parcela vencendo)?
ALTER TABLE estudio ADD COLUMN email_lembretes BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE email_enviado (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    estudio_id BIGINT       NOT NULL,
    para       VARCHAR(200) NOT NULL,
    assunto    VARCHAR(200) NOT NULL,
    tipo       VARCHAR(40)  NOT NULL,
    referencia VARCHAR(80)  NULL,
    status     VARCHAR(12)  NOT NULL,
    erro       VARCHAR(400) NULL,
    criado_em  DATETIME     NOT NULL,
    enviado_em DATETIME     NULL
);
CREATE INDEX ix_email_estudio ON email_enviado (estudio_id, criado_em);
CREATE INDEX ix_email_referencia ON email_enviado (tipo, referencia);
