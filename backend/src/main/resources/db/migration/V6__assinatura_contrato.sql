-- Assinatura eletrônica do contrato (Lei 14.063/2020, assinatura eletrônica simples).
-- O texto do contrato é congelado quando o link é gerado; o hash prova que nada mudou depois.
CREATE TABLE contrato_assinatura (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    estudio_id        BIGINT       NOT NULL,
    trabalho_id       BIGINT       NOT NULL,
    token             VARCHAR(64)  NOT NULL,
    codigo            VARCHAR(20)  NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    texto             TEXT         NOT NULL,
    hash_texto        VARCHAR(64)  NOT NULL,
    criado_em         DATETIME     NOT NULL,
    assinado_em       DATETIME     NULL,
    nome_assinante    VARCHAR(120) NULL,
    cpf_assinante     VARCHAR(14)  NULL,
    ip                VARCHAR(64)  NULL,
    user_agent        VARCHAR(400) NULL,
    imagem_assinatura VARCHAR(200) NULL,
    pdf               VARCHAR(200) NULL,
    hash_pdf          VARCHAR(64)  NULL,
    cancelado_em      DATETIME     NULL,
    autoriza_imagem   BOOLEAN      NULL,
    CONSTRAINT uk_assinatura_token UNIQUE (token),
    CONSTRAINT uk_assinatura_codigo UNIQUE (codigo),
    CONSTRAINT fk_assinatura_trabalho FOREIGN KEY (trabalho_id) REFERENCES trabalho (id)
);
CREATE INDEX ix_assinatura_trabalho ON contrato_assinatura (trabalho_id);

-- Assinatura desenhada do fotógrafo, aplicada no contrato assinado.
ALTER TABLE estudio ADD COLUMN assinatura_imagem VARCHAR(200) NULL;
