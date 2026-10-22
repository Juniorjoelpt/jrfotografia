-- Assistente de conteúdo: qualquer serviço no formato OpenAI (Ollama na VPS, Groq, OpenRouter…), configurado em Ajustes › Assistente.
ALTER TABLE estudio ADD COLUMN ia_ativo BOOLEAN NOT NULL DEFAULT FALSE;
-- Endereço base da API, ex.: http://localhost:11434/v1 (Ollama) ou https://api.groq.com/openai/v1
ALTER TABLE estudio ADD COLUMN ia_url VARCHAR(300) NULL;
ALTER TABLE estudio ADD COLUMN ia_modelo VARCHAR(120) NULL;
-- Chave da API, cifrada (AES-GCM); nunca volta para a tela. O Ollama local não precisa.
ALTER TABLE estudio ADD COLUMN ia_chave VARCHAR(800) NULL;

-- Ideias geradas, para não se perderem (e não pagar duas vezes pela mesma).
CREATE TABLE ideia_conteudo (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    estudio_id  BIGINT       NOT NULL,
    tipo        VARCHAR(20)  NOT NULL,
    titulo      VARCHAR(200) NOT NULL,
    pedido      VARCHAR(600) NULL,
    modelo      VARCHAR(120) NULL,
    resposta    TEXT         NOT NULL,
    criado_em   DATETIME     NOT NULL
);
CREATE INDEX ix_ideia_estudio ON ideia_conteudo (estudio_id, criado_em);
