-- Fase 3, bloco 1: agendamento online com pré-reserva e sinal, remarcação e cancelamento pela cliente.

-- Regras da agenda online (editáveis em Ajustes › Agenda online)
ALTER TABLE estudio ADD COLUMN agenda_online BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE estudio ADD COLUMN antecedencia_horas INT NOT NULL DEFAULT 24;      -- não agenda em cima da hora
ALTER TABLE estudio ADD COLUMN horizonte_dias INT NOT NULL DEFAULT 60;          -- até quantos dias à frente
ALTER TABLE estudio ADD COLUMN intervalo_min INT NOT NULL DEFAULT 30;           -- folga entre ensaios
ALTER TABLE estudio ADD COLUMN passo_min INT NOT NULL DEFAULT 60;               -- horários de quanto em quanto tempo
ALTER TABLE estudio ADD COLUMN sinal_percentual INT NOT NULL DEFAULT 30;        -- sinal sobre o preço do pacote
ALTER TABLE estudio ADD COLUMN reserva_horas INT NOT NULL DEFAULT 24;           -- validade da pré-reserva sem comprovante
ALTER TABLE estudio ADD COLUMN remarcacao_horas INT NOT NULL DEFAULT 48;        -- aviso mínimo para remarcar
ALTER TABLE estudio ADD COLUMN remarcacoes_max INT NOT NULL DEFAULT 1;          -- remarcações sem custo
ALTER TABLE estudio ADD COLUMN reembolso_dias INT NOT NULL DEFAULT 7;           -- cancelando antes disso, o sinal volta

-- Janelas de atendimento por dia da semana (1 = segunda ... 7 = domingo)
CREATE TABLE horario_atendimento (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    estudio_id  BIGINT   NOT NULL,
    dia_semana  INT      NOT NULL,
    inicio      TIME     NOT NULL,
    fim         TIME     NOT NULL,
    CONSTRAINT fk_horario_estudio FOREIGN KEY (estudio_id) REFERENCES estudio (id)
);

-- Duração de cada pacote e se ele aparece na agenda online
ALTER TABLE pacote ADD COLUMN duracao_min INT NOT NULL DEFAULT 120;
ALTER TABLE pacote ADD COLUMN agenda_online BOOLEAN NOT NULL DEFAULT TRUE;
UPDATE pacote SET duracao_min = 240, agenda_online = FALSE WHERE qtd_fotos IS NULL;

-- Pré-reservas feitas pela cliente no site
CREATE TABLE reserva (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    estudio_id        BIGINT        NOT NULL,
    token             VARCHAR(40)   NOT NULL UNIQUE,
    pacote_id         BIGINT        NOT NULL,
    inicio            DATETIME      NOT NULL,
    fim               DATETIME      NOT NULL,
    nome              VARCHAR(120)  NOT NULL,
    telefone          VARCHAR(20)   NOT NULL,
    email             VARCHAR(120),
    tipo              VARCHAR(60),
    mensagem          VARCHAR(2000),
    origem            VARCHAR(20),
    valor_total       DECIMAL(10,2) NOT NULL,
    valor_sinal       DECIMAL(10,2) NOT NULL,
    status            VARCHAR(20)   NOT NULL,
    expira_em         DATETIME      NOT NULL,
    comprovante       VARCHAR(200),
    comprovante_tipo  VARCHAR(60),
    comprovante_em    DATETIME,
    motivo_recusa     VARCHAR(300),
    cliente_id        BIGINT,
    trabalho_id       BIGINT,
    criado_em         DATETIME      NOT NULL,
    CONSTRAINT fk_reserva_estudio FOREIGN KEY (estudio_id) REFERENCES estudio (id),
    CONSTRAINT fk_reserva_pacote FOREIGN KEY (pacote_id) REFERENCES pacote (id)
);
CREATE INDEX ix_reserva_inicio ON reserva (estudio_id, inicio);

-- Área da cliente (link para acompanhar, remarcar e cancelar)
ALTER TABLE trabalho ADD COLUMN token_cliente VARCHAR(40);
ALTER TABLE trabalho ADD COLUMN remarcacoes INT NOT NULL DEFAULT 0;
ALTER TABLE trabalho ADD COLUMN cancelado_em DATETIME;
ALTER TABLE trabalho ADD COLUMN cancelado_por VARCHAR(20);
ALTER TABLE trabalho ADD COLUMN motivo_cancelamento VARCHAR(300);
CREATE UNIQUE INDEX ux_trabalho_token_cliente ON trabalho (token_cliente);

-- Horários iniciais da JR: terça a sábado, 8h-12h e 14h-18h
INSERT INTO horario_atendimento (estudio_id, dia_semana, inicio, fim)
SELECT e.id, d.dia, h.ini, h.fim
FROM estudio e
CROSS JOIN (SELECT 2 AS dia UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6) d
CROSS JOIN (SELECT TIME '08:00:00' AS ini, TIME '12:00:00' AS fim UNION ALL SELECT TIME '14:00:00', TIME '18:00:00') h
WHERE e.slug = 'jrfotografia';
