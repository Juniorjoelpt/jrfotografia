-- Estrutura inicial da JR Fotografia.
-- Toda tabela de negócio carrega estudio_id: o sistema já nasce pronto para atender vários estúdios.

CREATE TABLE estudio (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    slug            VARCHAR(60)  NOT NULL UNIQUE,
    nome            VARCHAR(120) NOT NULL,
    dominio         VARCHAR(120),
    cidade          VARCHAR(120),
    whatsapp        VARCHAR(20),
    instagram       VARCHAR(60),
    email           VARCHAR(120),
    chave_pix       VARCHAR(120),
    apresentacao    TEXT,
    criado_em       DATETIME     NOT NULL
);

CREATE TABLE usuario (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    estudio_id      BIGINT       NOT NULL,
    nome            VARCHAR(120) NOT NULL,
    email           VARCHAR(120) NOT NULL UNIQUE,
    senha_hash      VARCHAR(100) NOT NULL,
    perfil          VARCHAR(20)  NOT NULL,
    ativo           BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em       DATETIME     NOT NULL,
    CONSTRAINT fk_usuario_estudio FOREIGN KEY (estudio_id) REFERENCES estudio (id)
);

CREATE TABLE cliente (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    estudio_id      BIGINT       NOT NULL,
    nome            VARCHAR(120) NOT NULL,
    telefone        VARCHAR(20),
    email           VARCHAR(120),
    cpf             VARCHAR(14),
    data_nascimento DATE,
    origem          VARCHAR(20),
    observacoes     TEXT,
    criado_em       DATETIME     NOT NULL,
    CONSTRAINT fk_cliente_estudio FOREIGN KEY (estudio_id) REFERENCES estudio (id)
);
CREATE INDEX ix_cliente_estudio_nome ON cliente (estudio_id, nome);

CREATE TABLE pacote (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    estudio_id          BIGINT        NOT NULL,
    nome                VARCHAR(80)   NOT NULL,
    qtd_fotos           INT,
    preco               DECIMAL(10,2) NOT NULL,
    preco_foto_extra    DECIMAL(10,2),
    prazo_entrega_dias  INT           NOT NULL,
    itens               TEXT,
    material_entregue   VARCHAR(255),
    publico             BOOLEAN       NOT NULL DEFAULT TRUE,
    ativo               BOOLEAN       NOT NULL DEFAULT TRUE,
    ordem               INT           NOT NULL DEFAULT 0,
    CONSTRAINT fk_pacote_estudio FOREIGN KEY (estudio_id) REFERENCES estudio (id)
);

CREATE TABLE pedido_orcamento (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    estudio_id      BIGINT       NOT NULL,
    nome            VARCHAR(120) NOT NULL,
    telefone        VARCHAR(20)  NOT NULL,
    email           VARCHAR(120),
    tipo_trabalho   VARCHAR(60),
    pacote_id       BIGINT,
    data_desejada   DATE,
    mensagem        TEXT,
    origem          VARCHAR(20),
    status          VARCHAR(20)  NOT NULL,
    cliente_id      BIGINT,
    criado_em       DATETIME     NOT NULL,
    CONSTRAINT fk_pedido_estudio FOREIGN KEY (estudio_id) REFERENCES estudio (id),
    CONSTRAINT fk_pedido_pacote FOREIGN KEY (pacote_id) REFERENCES pacote (id),
    CONSTRAINT fk_pedido_cliente FOREIGN KEY (cliente_id) REFERENCES cliente (id)
);

CREATE TABLE trabalho (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    estudio_id          BIGINT        NOT NULL,
    cliente_id          BIGINT        NOT NULL,
    pacote_id           BIGINT,
    titulo              VARCHAR(120)  NOT NULL,
    tipo                VARCHAR(60),
    data_trabalho       DATETIME,
    local_trabalho      VARCHAR(200),
    valor_total         DECIMAL(10,2) NOT NULL,
    forma_pagamento     VARCHAR(20)   NOT NULL,
    status              VARCHAR(20)   NOT NULL,
    prazo_entrega       DATE,
    galeria_token       VARCHAR(40)   NOT NULL UNIQUE,
    galeria_senha_hash  VARCHAR(100),
    galeria_expira_em   DATETIME,
    selecao_fechada_em  DATETIME,
    entregue_em         DATETIME,
    observacoes         TEXT,
    criado_em           DATETIME      NOT NULL,
    CONSTRAINT fk_trabalho_estudio FOREIGN KEY (estudio_id) REFERENCES estudio (id),
    CONSTRAINT fk_trabalho_cliente FOREIGN KEY (cliente_id) REFERENCES cliente (id),
    CONSTRAINT fk_trabalho_pacote FOREIGN KEY (pacote_id) REFERENCES pacote (id)
);
CREATE INDEX ix_trabalho_estudio_status ON trabalho (estudio_id, status);

CREATE TABLE foto (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    estudio_id      BIGINT       NOT NULL,
    trabalho_id     BIGINT       NOT NULL,
    nome_original   VARCHAR(200) NOT NULL,
    arquivo         VARCHAR(200) NOT NULL,
    arquivo_final   VARCHAR(200),
    previa          VARCHAR(200) NOT NULL,
    miniatura       VARCHAR(200) NOT NULL,
    largura         INT,
    altura          INT,
    ordem           INT          NOT NULL DEFAULT 0,
    selecionada     BOOLEAN      NOT NULL DEFAULT FALSE,
    comentario      VARCHAR(500),
    criado_em       DATETIME     NOT NULL,
    CONSTRAINT fk_foto_estudio FOREIGN KEY (estudio_id) REFERENCES estudio (id),
    CONSTRAINT fk_foto_trabalho FOREIGN KEY (trabalho_id) REFERENCES trabalho (id)
);
CREATE INDEX ix_foto_trabalho ON foto (trabalho_id, ordem);

CREATE TABLE parcela (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    estudio_id      BIGINT        NOT NULL,
    trabalho_id     BIGINT        NOT NULL,
    tipo            VARCHAR(20)   NOT NULL,
    descricao       VARCHAR(120)  NOT NULL,
    valor           DECIMAL(10,2) NOT NULL,
    vencimento      DATE,
    pago_em         DATE,
    forma           VARCHAR(20),
    CONSTRAINT fk_parcela_estudio FOREIGN KEY (estudio_id) REFERENCES estudio (id),
    CONSTRAINT fk_parcela_trabalho FOREIGN KEY (trabalho_id) REFERENCES trabalho (id)
);
CREATE INDEX ix_parcela_trabalho ON parcela (trabalho_id);

CREATE TABLE portfolio_foto (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    estudio_id      BIGINT       NOT NULL,
    categoria       VARCHAR(40)  NOT NULL,
    titulo          VARCHAR(120),
    arquivo         VARCHAR(200) NOT NULL,
    miniatura       VARCHAR(200) NOT NULL,
    largura         INT,
    altura          INT,
    ordem           INT          NOT NULL DEFAULT 0,
    destaque        BOOLEAN      NOT NULL DEFAULT FALSE,
    criado_em       DATETIME     NOT NULL,
    CONSTRAINT fk_portfolio_estudio FOREIGN KEY (estudio_id) REFERENCES estudio (id)
);
