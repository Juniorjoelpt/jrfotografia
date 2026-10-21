-- Fase 3 · bloco 5: campanhas e mini-sessões com página própria, cupons de desconto e programa de indicação.

CREATE TABLE campanha (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    estudio_id         BIGINT         NOT NULL,
    pacote_id          BIGINT         NOT NULL,
    slug               VARCHAR(80)    NOT NULL,
    titulo             VARCHAR(120)   NOT NULL,
    descricao          TEXT           NULL,
    preco              DECIMAL(10, 2) NOT NULL,
    sinal_percentual   INT            NOT NULL DEFAULT 50,
    duracao_min        INT            NOT NULL DEFAULT 20,
    intervalo_min      INT            NOT NULL DEFAULT 10,
    qtd_fotos          INT            NULL,
    preco_foto_extra   DECIMAL(10, 2) NULL,
    prazo_entrega_dias INT            NOT NULL DEFAULT 10,
    capa               VARCHAR(200)   NULL,
    ativa              BOOLEAN        NOT NULL DEFAULT TRUE,
    criado_em          DATETIME       NOT NULL,
    CONSTRAINT uk_campanha_slug UNIQUE (slug),
    CONSTRAINT fk_campanha_pacote FOREIGN KEY (pacote_id) REFERENCES pacote (id)
);

CREATE TABLE campanha_dia (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    campanha_id BIGINT       NOT NULL,
    data        DATE         NOT NULL,
    inicio      TIME         NOT NULL,
    fim         TIME         NOT NULL,
    local       VARCHAR(160) NULL,
    CONSTRAINT fk_campanha_dia FOREIGN KEY (campanha_id) REFERENCES campanha (id) ON DELETE CASCADE
);

CREATE TABLE cupom (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    estudio_id  BIGINT         NOT NULL,
    codigo      VARCHAR(30)    NOT NULL,
    descricao   VARCHAR(120)   NULL,
    tipo        VARCHAR(12)    NOT NULL,
    valor       DECIMAL(10, 2) NOT NULL,
    validade    DATE           NULL,
    usos_max    INT            NULL,
    usos        INT            NOT NULL DEFAULT 0,
    campanha_id BIGINT         NULL,
    ativo       BOOLEAN        NOT NULL DEFAULT TRUE,
    criado_em   DATETIME       NOT NULL,
    CONSTRAINT uk_cupom_codigo UNIQUE (estudio_id, codigo)
);

CREATE TABLE indicacao (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    estudio_id           BIGINT         NOT NULL,
    indicador_cliente_id BIGINT         NOT NULL,
    reserva_id           BIGINT         NULL,
    trabalho_id          BIGINT         NULL,
    indicado_nome        VARCHAR(120)   NOT NULL,
    recompensa           DECIMAL(10, 2) NOT NULL,
    status               VARCHAR(20)    NOT NULL,
    criado_em            DATETIME       NOT NULL,
    recompensada_em      DATETIME       NULL,
    CONSTRAINT fk_indicacao_cliente FOREIGN KEY (indicador_cliente_id) REFERENCES cliente (id)
);

ALTER TABLE reserva ADD COLUMN campanha_id BIGINT NULL;
ALTER TABLE reserva ADD COLUMN cupom_codigo VARCHAR(30) NULL;
ALTER TABLE reserva ADD COLUMN desconto DECIMAL(10, 2) NULL;
ALTER TABLE reserva ADD COLUMN indicador_cliente_id BIGINT NULL;

ALTER TABLE cliente ADD COLUMN codigo_indicacao VARCHAR(20) NULL;
CREATE UNIQUE INDEX uk_cliente_codigo_indicacao ON cliente (codigo_indicacao);

ALTER TABLE estudio ADD COLUMN indicacao_ativa BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE estudio ADD COLUMN indicacao_desconto INT NOT NULL DEFAULT 10;
ALTER TABLE estudio ADD COLUMN indicacao_recompensa DECIMAL(10, 2) NOT NULL DEFAULT 50.00;
