-- Pix com confirmação automática pelo PagBank (PagSeguro), configurado em Ajustes › Pagamentos.
ALTER TABLE estudio ADD COLUMN pagbank_ativo BOOLEAN NOT NULL DEFAULT FALSE;
-- Token da conta PagBank, cifrado (AES-GCM); nunca volta para a tela.
ALTER TABLE estudio ADD COLUMN pagbank_token VARCHAR(800) NULL;
-- TRUE = ambiente de testes do PagBank (sandbox), sem dinheiro de verdade.
ALTER TABLE estudio ADD COLUMN pagbank_sandbox BOOLEAN NOT NULL DEFAULT FALSE;

-- Cada QR Code gerado: o sinal de uma pré-reserva ou uma parcela em aberto.
CREATE TABLE cobranca_pix (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    estudio_id    BIGINT        NOT NULL,
    reserva_id    BIGINT        NULL,
    parcela_id    BIGINT        NULL,
    descricao     VARCHAR(160)  NOT NULL,
    nome          VARCHAR(120)  NULL,
    valor         DECIMAL(10,2) NOT NULL,
    pedido_id     VARCHAR(80)   NOT NULL,
    texto         VARCHAR(800)  NOT NULL,
    sandbox       BOOLEAN       NOT NULL DEFAULT FALSE,
    status        VARCHAR(12)   NOT NULL,
    expira_em     DATETIME      NOT NULL,
    pago_em       DATETIME      NULL,
    valor_pago    DECIMAL(10,2) NULL,
    erro          VARCHAR(300)  NULL,
    verificado_em DATETIME      NULL,
    criado_em     DATETIME      NOT NULL
);
CREATE UNIQUE INDEX ux_cobranca_pedido ON cobranca_pix (pedido_id);
CREATE INDEX ix_cobranca_reserva ON cobranca_pix (reserva_id);
CREATE INDEX ix_cobranca_parcela ON cobranca_pix (parcela_id);
CREATE INDEX ix_cobranca_status ON cobranca_pix (status, criado_em);
