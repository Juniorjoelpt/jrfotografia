# JR Fotografia

Sistema de gestão do estúdio JR Fotografia (Governador Nunes Freire, MA): site com portfólio e pacotes, galeria privada para o cliente escolher e baixar as fotos, e painel do estúdio para clientes, trabalhos e recebimentos.

Já nasce preparado para atender outros estúdios no futuro: toda tabela de negócio carrega `estudio_id`.

## O que a versão 1 faz

| Parte | Funções |
| --- | --- |
| **Site** (`/`) | Abertura no estilo da proposta, portfólio de ensaio feminino, sobre, pacotes com preço e prazo, pedido de orçamento com consentimento (LGPD) |
| **Galeria do cliente** (`/g/<código>`) | Acesso com senha de 6 dígitos, prévias com marca d'água JR, seleção por coração com cálculo de extras, entrega com download individual ou ZIP, validade de 90 dias |
| **Painel** (`/painel`) | Início com agenda da semana e prazos, trabalhos por etapa, envio de fotos em lote, envio das editadas por nome de arquivo, parcelas e cobrança pelo WhatsApp, pedidos do site, portfólio, pacotes e dados do estúdio |

### Regras de negócio

- **Etapas:** Agendado → Fotografado → Seleção → Edição → Entregue. Pacotes sem limite de fotos (Eventos) pulam a seleção.
- **Fotos extras:** ao concluir a seleção, cada foto além do pacote vira uma cobrança de R$ 35,00 (configurável por pacote).
- **Download:** à vista, só depois de quitar tudo (inclusive extras); parcelado, depois do sinal e da 1ª parcela.
- **Prazo de entrega:** calculado em dias úteis a partir da data do ensaio (5, 10 ou 15, conforme o pacote).
- **Arquivos:** guarda só os JPEGs exportados e as versões editadas, nunca os brutos.
- **Perfis:** administrador vê tudo; colaborador não vê recebimentos nem cria trabalhos.

## Tecnologia

- **backend/**: Java 21, Spring Boot 3.3, Spring Security com JWT, JPA, Flyway, MySQL 8.
- **frontend/**: React 18, TypeScript, Vite, React Router. Sem biblioteca de componentes: o visual segue o manual da marca JR.
- Marca d'água e prévias geradas no servidor (Java 2D), com o monograma JR em vetor.

## Rodar no computador (desenvolvimento)

Precisa de Java 21, Maven e Node 20.

```bash
# Terminal 1 — API (ou Run no NetBeans)
cd backend
mvn spring-boot:run
# Usa o MySQL do computador: usuário root, senha vazia. O banco "jrfotografia" é criado sozinho.
# Outro usuário/senha: defina DB_USUARIO e DB_SENHA. Sem MySQL: rode com o perfil "dev" (banco H2).
# Login do painel: admin@jrfotografia.local / trocar123

# Terminal 2 — site e painel em http://localhost:5173
cd frontend
npm install
npm run dev
```

Testes da API: `cd backend && mvn test` (inclui o fluxo completo, da criação do trabalho ao download).

## Colocar no ar

Numa VPS com Docker (2 vCPU e 4 GB bastam no começo):

```bash
git clone https://github.com/Juniorjoelpt/jrfotografia.git
cd jrfotografia
cp .env.exemplo .env      # preencha senhas, JWT_SEGREDO, ADMIN_EMAIL e ADMIN_SENHA
docker compose up -d --build
```

Depois:

1. Registre **jrfotografiagnf.com** e aponte o domínio para o IP da VPS.
2. Coloque HTTPS na frente (Caddy ou Nginx com Let's Encrypt).
3. Entre em `/painel`, abra **Ajustes** e preencha WhatsApp, Instagram e uma chave Pix aleatória ou de e-mail.
4. Em **Ajustes › Portfólio do site**, envie as fotos de ensaio feminino e marque uma como destaque.

### Backup

Os dados ficam em dois volumes do Docker: `banco` (MySQL) e `arquivos` (fotos). Faça cópia diária dos dois para fora da VPS.

```bash
docker compose exec banco sh -c 'mysqldump -u root -p"$MYSQL_ROOT_PASSWORD" jrfotografia' > backup-$(date +%F).sql
```

## Próximas fases

Fase 2 (vendas): funil de orçamentos, orçamento e contrato em PDF, agenda, Pix com baixa automática, e-mails, relatórios. Fase 3: agendamento online, assinatura digital, álbuns e laboratório, campanhas. O detalhamento está no documento de requisitos.
