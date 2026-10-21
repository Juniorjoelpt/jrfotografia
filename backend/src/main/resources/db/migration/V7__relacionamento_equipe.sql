-- Fase 3 · bloco 3: relacionamento com clientes (datas especiais, inativos, pesquisa) e equipe (freelancers, tarefas).

CREATE TABLE data_especial (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    estudio_id BIGINT      NOT NULL,
    cliente_id BIGINT      NOT NULL,
    descricao  VARCHAR(80) NOT NULL,
    dia        INT         NOT NULL,
    mes        INT         NOT NULL,
    ano        INT         NULL,
    criado_em  DATETIME    NOT NULL,
    CONSTRAINT fk_data_especial_cliente FOREIGN KEY (cliente_id) REFERENCES cliente (id) ON DELETE CASCADE
);
CREATE INDEX ix_data_especial_estudio ON data_especial (estudio_id, mes, dia);

-- Quando o fotógrafo chamou a cliente pela última vez (lista de inativas).
ALTER TABLE cliente ADD COLUMN ultimo_contato DATETIME NULL;

CREATE TABLE pesquisa_satisfacao (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    estudio_id          BIGINT      NOT NULL,
    trabalho_id         BIGINT      NOT NULL,
    token               VARCHAR(64) NOT NULL,
    nota                INT         NULL,
    atendimento         INT         NULL,
    fotos               INT         NULL,
    prazo               INT         NULL,
    comentario          TEXT        NULL,
    autoriza_depoimento BOOLEAN     NULL,
    criado_em           DATETIME    NOT NULL,
    respondida_em       DATETIME    NULL,
    CONSTRAINT uk_pesquisa_token UNIQUE (token),
    CONSTRAINT uk_pesquisa_trabalho UNIQUE (trabalho_id),
    CONSTRAINT fk_pesquisa_trabalho FOREIGN KEY (trabalho_id) REFERENCES trabalho (id)
);

CREATE TABLE freelancer (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    estudio_id  BIGINT       NOT NULL,
    nome        VARCHAR(120) NOT NULL,
    telefone    VARCHAR(20)  NULL,
    funcao      VARCHAR(60)  NULL,
    chave_pix   VARCHAR(120) NULL,
    observacoes TEXT         NULL,
    ativo       BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em   DATETIME     NOT NULL
);

CREATE TABLE trabalho_freelancer (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    estudio_id    BIGINT        NOT NULL,
    trabalho_id   BIGINT        NOT NULL,
    freelancer_id BIGINT        NOT NULL,
    funcao        VARCHAR(60)   NULL,
    percentual    DECIMAL(5, 2) NULL,
    valor         DECIMAL(10, 2) NOT NULL,
    despesa_id    BIGINT        NULL,
    criado_em     DATETIME      NOT NULL,
    CONSTRAINT fk_tf_trabalho FOREIGN KEY (trabalho_id) REFERENCES trabalho (id),
    CONSTRAINT fk_tf_freelancer FOREIGN KEY (freelancer_id) REFERENCES freelancer (id)
);

CREATE TABLE tarefa (
    id                        BIGINT AUTO_INCREMENT PRIMARY KEY,
    estudio_id                BIGINT       NOT NULL,
    trabalho_id               BIGINT       NULL,
    titulo                    VARCHAR(160) NOT NULL,
    descricao                 TEXT         NULL,
    responsavel_usuario_id    BIGINT       NULL,
    responsavel_freelancer_id BIGINT       NULL,
    prazo                     DATE         NULL,
    status                    VARCHAR(20)  NOT NULL,
    automatica                BOOLEAN      NOT NULL DEFAULT FALSE,
    criado_em                 DATETIME     NOT NULL,
    concluida_em              DATETIME     NULL,
    CONSTRAINT fk_tarefa_trabalho FOREIGN KEY (trabalho_id) REFERENCES trabalho (id)
);
CREATE INDEX ix_tarefa_estudio ON tarefa (estudio_id, status);
