-- Fase 2: orçamentos com aceite por link, agenda com bloqueios, contas a pagar e modelo de contrato.

-- Duração do ensaio na agenda (meio período = 4 h por padrão)
ALTER TABLE trabalho ADD COLUMN duracao_min INT NOT NULL DEFAULT 240;
ALTER TABLE trabalho ADD COLUMN orcamento_id BIGINT;

-- Catálogo de itens avulsos (foto extra, álbum, deslocamento...)
CREATE TABLE item_avulso (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    estudio_id  BIGINT        NOT NULL,
    nome        VARCHAR(80)   NOT NULL,
    preco       DECIMAL(10,2) NOT NULL,
    ativo       BOOLEAN       NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_item_estudio FOREIGN KEY (estudio_id) REFERENCES estudio (id)
);

CREATE TABLE orcamento (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    estudio_id        BIGINT        NOT NULL,
    numero            INT           NOT NULL,
    token             VARCHAR(40)   NOT NULL UNIQUE,
    cliente_id        BIGINT,
    pedido_id         BIGINT,
    nome_cliente      VARCHAR(120)  NOT NULL,
    telefone          VARCHAR(20),
    email             VARCHAR(120),
    pacote_id         BIGINT,
    tipo              VARCHAR(60),
    data_prevista     DATETIME,
    local_previsto    VARCHAR(200),
    desconto          DECIMAL(10,2) NOT NULL DEFAULT 0,
    valor_total       DECIMAL(10,2) NOT NULL,
    forma_pagamento   VARCHAR(20)   NOT NULL,
    valor_sinal       DECIMAL(10,2),
    num_parcelas      INT,
    validade          DATE          NOT NULL,
    observacoes       TEXT,
    status            VARCHAR(20)   NOT NULL,
    enviado_em        DATETIME,
    respondido_em     DATETIME,
    trabalho_id       BIGINT,
    criado_em         DATETIME      NOT NULL,
    CONSTRAINT fk_orc_estudio FOREIGN KEY (estudio_id) REFERENCES estudio (id),
    CONSTRAINT fk_orc_cliente FOREIGN KEY (cliente_id) REFERENCES cliente (id),
    CONSTRAINT fk_orc_pedido FOREIGN KEY (pedido_id) REFERENCES pedido_orcamento (id),
    CONSTRAINT fk_orc_pacote FOREIGN KEY (pacote_id) REFERENCES pacote (id)
);
CREATE INDEX ix_orc_estudio_status ON orcamento (estudio_id, status);

CREATE TABLE orcamento_item (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    orcamento_id  BIGINT        NOT NULL,
    descricao     VARCHAR(160)  NOT NULL,
    quantidade    INT           NOT NULL,
    valor_unit    DECIMAL(10,2) NOT NULL,
    ordem         INT           NOT NULL DEFAULT 0,
    CONSTRAINT fk_item_orc FOREIGN KEY (orcamento_id) REFERENCES orcamento (id)
);

CREATE TABLE bloqueio_agenda (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    estudio_id  BIGINT       NOT NULL,
    inicio      DATETIME     NOT NULL,
    fim         DATETIME     NOT NULL,
    motivo      VARCHAR(120) NOT NULL,
    CONSTRAINT fk_bloq_estudio FOREIGN KEY (estudio_id) REFERENCES estudio (id)
);
CREATE INDEX ix_bloq_periodo ON bloqueio_agenda (estudio_id, inicio);

CREATE TABLE despesa (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    estudio_id   BIGINT        NOT NULL,
    descricao    VARCHAR(120)  NOT NULL,
    categoria    VARCHAR(40)   NOT NULL,
    valor        DECIMAL(10,2) NOT NULL,
    vencimento   DATE          NOT NULL,
    pago_em      DATE,
    trabalho_id  BIGINT,
    criado_em    DATETIME      NOT NULL,
    CONSTRAINT fk_desp_estudio FOREIGN KEY (estudio_id) REFERENCES estudio (id)
);
CREATE INDEX ix_desp_venc ON despesa (estudio_id, vencimento);

ALTER TABLE estudio ADD COLUMN modelo_contrato TEXT;
ALTER TABLE estudio ADD COLUMN responsavel VARCHAR(120);
ALTER TABLE estudio ADD COLUMN documento VARCHAR(20);

INSERT INTO item_avulso (estudio_id, nome, preco) VALUES (1, 'Foto extra', 35.00);

