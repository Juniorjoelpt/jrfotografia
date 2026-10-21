# JR Fotografia

Sistema de gestão do estúdio JR Fotografia (Governador Nunes Freire, MA): site com portfólio e pacotes, galeria privada para o cliente escolher e baixar as fotos, e painel do estúdio para clientes, trabalhos e recebimentos.

Já nasce preparado para atender outros estúdios no futuro: toda tabela de negócio carrega `estudio_id`.

## O que a versão 1 faz

| Parte | Funções |
| --- | --- |
| **Site** (`/`) | Abertura no estilo da proposta, portfólio de ensaio feminino, sobre, pacotes com preço e prazo, pedido de orçamento com consentimento (LGPD) |
| **Galeria do cliente** (`/g/<código>`) | Acesso com senha de 6 dígitos, prévias com marca d'água JR, seleção por coração com cálculo de extras, entrega com download individual ou ZIP, validade de 90 dias |
| **Painel** (`/painel`) | Início com agenda da semana e prazos, trabalhos por etapa, envio de fotos em lote, envio das editadas por nome de arquivo, parcelas e cobrança pelo WhatsApp, pedidos do site, portfólio, pacotes e dados do estúdio |
| **Agenda** (`/painel/agenda`) | Visão por mês e por semana, bloqueio de datas (dia inteiro ou horário), aviso de choque de horário ao agendar, remarcar ou orçar |
| **Vendas** (`/painel/vendas`) | Funil (pedidos novos, em conversa, aguardando resposta, aceitos), orçamento com pacote + itens avulsos + desconto + forma de pagamento, envio pelo WhatsApp, PDF com a marca |
| **Orçamento do cliente** (`/o/<código>`) | O cliente vê o orçamento e aceita ou recusa. No aceite, o cadastro, o trabalho e as parcelas são criados sozinhos e o contrato fica disponível para baixar |
| **Financeiro** (`/painel/financeiro`) | A receber, contas a pagar (despesas por categoria), fluxo de caixa mês a mês, recibo em PDF de cada parcela paga, contrato em PDF de cada trabalho |
| **Relatórios** (`/painel/relatorios`) | Faturamento por mês, ticket médio, por tipo de ensaio, origem dos clientes, conversão de orçamentos e de pedidos, despesas por categoria |

### Regras de negócio

- **Etapas:** Agendado → Fotografado → Seleção → Edição → Entregue. Pacotes sem limite de fotos (Eventos) pulam a seleção.
- **Fotos extras:** ao concluir a seleção, cada foto além do pacote vira uma cobrança de R$ 35,00 (configurável por pacote).
- **Download:** à vista, só depois de quitar tudo (inclusive extras); parcelado, depois do sinal e da 1ª parcela.
- **Marca d'água e originais:** o cliente nunca baixa a original, só a versão editada. Na seleção e até o download ser liberado, tudo na galeria tem a marca d'água JR; liberado o download, a galeria passa a mostrar as editadas sem marca. Só dá para entregar quando todas as fotos escolhidas têm a editada. No pacote sem seleção (Eventos), as fotos enviadas já contam como editadas.
- **Prazo de entrega:** calculado em dias úteis a partir da data do ensaio (5, 10 ou 15, conforme o pacote).
- **Arquivos:** guarda só os JPEGs exportados e as versões editadas, nunca os brutos.
- **Acesso:** depois de 5 senhas erradas seguidas (no login ou na galeria), aquele aparelho espera 15 minutos; a galeria também trava depois de 30 erros em 1 hora, somando todos os aparelhos. Enquanto a senha for a de fábrica, o painel pede para trocar (Minha conta).
- **Perfis:** administrador vê tudo e cadastra colaboradores em Minha conta; colaborador não vê valores (financeiro, orçamentos, relatórios) nem cria trabalhos.
- **Orçamentos:** valem 7 dias por padrão; vencidos aparecem como "Vencido" e não podem mais ser aceitos. Aceito, não pode ser editado nem apagado.
- **Contrato:** sai do modelo em Ajustes › Contrato, com campos como `{CONTRATANTE}` e `{VALOR_TOTAL}` preenchidos pelo trabalho. O modelo padrão é um ponto de partida; revise com um advogado.

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
# Usa o MySQL do computador: usuário root, senha araca123 (troque em application.yml, perfil local, ou defina DB_SENHA). O banco "jrfotografia" é criado sozinho.
# Outro usuário/senha: defina DB_USUARIO e DB_SENHA. Sem MySQL: rode com o perfil "dev" (banco H2).
# Login do painel: admin@jrfotografia.local / trocar123 (troque no primeiro acesso, em Minha conta)

# Terminal 2 — site e painel em http://localhost:5173
cd frontend
npm install
npm run dev
```

Para abrir no celular, com o celular e o computador no mesmo Wi-Fi: rode `npm run celular` em vez de `npm run dev` e acesse no celular o endereço **Network** que aparece no terminal (ex.: `http://192.168.0.15:5173`). Na primeira vez, permita o Node.js no aviso do Firewall do Windows (redes privadas).

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
5. Em **Ajustes › E-mail**, configure o envio de e-mails (veja abaixo) e ligue a verificação em duas etapas em **Minha conta**.

### E-mails automáticos

O painel envia e-mails por SMTP para clientes que informaram e-mail (pré-reserva, confirmação, contrato para assinar e assinado com o PDF,
fotos prontas, lembrete do ensaio na véspera, lembrete de pagamento) e avisos para o fotógrafo (nova pré-reserva, comprovante,
contrato assinado, seleção concluída, pedido de orçamento, álbum aprovado). Os lembretes saem todo dia às 8h.

Em **Ajustes › E-mail** escolha o provedor, preencha usuário e senha, salve, clique em **Enviar e-mail de teste** e ligue o envio:

- **Gmail**: `smtp.gmail.com`, porta 587, STARTTLS. A senha é uma **senha de app** (Conta Google › Segurança › Verificação em duas etapas › Senhas de app).
- **Outlook/Hotmail**: `smtp-mail.outlook.com`, porta 587, STARTTLS.
- **Brevo**: `smtp-relay.brevo.com`, porta 587, com o login e a chave SMTP do Brevo.

A senha fica cifrada no banco e não volta para a tela. Os links dos e-mails usam o domínio do estúdio; para outro endereço, defina `URL_PUBLICA`
(ex.: `URL_PUBLICA=http://localhost:5173` para testar no computador). O histórico dos envios, com o motivo de qualquer falha, fica na mesma tela.

### Backup

Os dados ficam em dois volumes do Docker: `banco` (MySQL) e `arquivos` (fotos). Faça cópia diária dos dois para fora da VPS.

```bash
docker compose exec banco sh -c 'mysqldump -u root -p"$MYSQL_ROOT_PASSWORD" jrfotografia' > backup-$(date +%F).sql
```

## Próximas fases

Fases 1, 2 e 3 concluídas, com e-mails automáticos. Falta só o Pix com baixa automática, que depende de uma conta com API de pagamento (Mercado Pago, Asaas, PagSeguro ou API Pix do banco). O detalhamento está no documento de requisitos.
