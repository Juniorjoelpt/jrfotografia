-- Fase 3 · bloco 4: pedidos de produtos (álbum, quadro, impressões) com laboratório,
-- aprovação da diagramação do álbum pelo cliente e estoque de materiais.

CREATE TABLE pedido_produto (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    estudio_id         BIGINT         NOT NULL,
    trabalho_id        BIGINT         NOT NULL,
    tipo               VARCHAR(20)    NOT NULL,
    descricao          VARCHAR(200)   NOT NULL,
    quantidade         INT            NOT NULL DEFAULT 1,
    valor_venda        DECIMAL(10, 2) NOT NULL DEFAULT 0,
    custo              DECIMAL(10, 2) NULL,
    laboratorio        VARCHAR(120)   NULL,
    status             VARCHAR(20)    NOT NULL,
    prazo              DATE           NULL,
    previsao_lab       DATE           NULL,
    enviado_lab_em     DATETIME       NULL,
    recebido_em        DATETIME       NULL,
    entregue_em        DATETIME       NULL,
    observacoes        TEXT           NULL,
    parcela_id         BIGINT         NULL,
    despesa_id         BIGINT         NULL,
    token_aprovacao    VARCHAR(64)    NULL,
    versao_layout      INT            NOT NULL DEFAULT 0,
    aprovacao          VARCHAR(20)    NULL,
    aprovacao_em       DATETIME       NULL,
    comentario_cliente TEXT           NULL,
    criado_em          DATETIME       NOT NULL,
    CONSTRAINT uk_pedido_token UNIQUE (token_aprovacao),
    CONSTRAINT fk_pedido_trabalho FOREIGN KEY (trabalho_id) REFERENCES trabalho (id)
);
CREATE INDEX ix_pedido_produto_estudio ON pedido_produto (estudio_id, status);

CREATE TABLE album_lamina (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    pedido_id  BIGINT       NOT NULL,
    versao     INT          NOT NULL,
    ordem      INT          NOT NULL,
    nome       VARCHAR(200) NULL,
    arquivo    VARCHAR(200) NOT NULL,
    miniatura  VARCHAR(200) NOT NULL,
    largura    INT          NULL,
    altura     INT          NULL,
    comentario TEXT         NULL,
    CONSTRAINT fk_lamina_pedido FOREIGN KEY (pedido_id) REFERENCES pedido_produto (id) ON DELETE CASCADE
);
CREATE INDEX ix_lamina_pedido ON album_lamina (pedido_id, versao, ordem);

CREATE TABLE material (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    estudio_id     BIGINT         NOT NULL,
    nome           VARCHAR(120)   NOT NULL,
    unidade        VARCHAR(20)    NOT NULL,
    quantidade     DECIMAL(10, 2) NOT NULL DEFAULT 0,
    minimo         DECIMAL(10, 2) NOT NULL DEFAULT 0,
    custo_unitario DECIMAL(10, 2) NULL,
    ativo          BOOLEAN        NOT NULL DEFAULT TRUE,
    criado_em      DATETIME       NOT NULL
);

CREATE TABLE movimento_material (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    estudio_id  BIGINT         NOT NULL,
    material_id BIGINT         NOT NULL,
    tipo        VARCHAR(10)    NOT NULL,
    quantidade  DECIMAL(10, 2) NOT NULL,
    saldo       DECIMAL(10, 2) NOT NULL,
    motivo      VARCHAR(200)   NULL,
    pedido_id   BIGINT         NULL,
    usuario     VARCHAR(120)   NULL,
    criado_em   DATETIME       NOT NULL,
    CONSTRAINT fk_mov_material FOREIGN KEY (material_id) REFERENCES material (id)
);
CREATE INDEX ix_mov_material ON movimento_material (material_id, criado_em);
