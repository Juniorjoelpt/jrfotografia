-- Estúdio JR Fotografia e os pacotes da proposta comercial.
-- O usuário administrador é criado na primeira inicialização a partir de ADMIN_EMAIL e ADMIN_SENHA.

INSERT INTO estudio (id, slug, nome, dominio, cidade, apresentacao, criado_em)
VALUES (1, 'jrfotografia', 'JR Fotografia', 'jrfotografiagnf.com', 'Governador Nunes Freire - MA',
        'Meu nome é Joel Júnior, sou fotógrafo especializado em gestante, família, temático, debutante, profissional, infantil, casal, lifestyle, boudoir e newborn. Trabalho com fotografia há quatro anos e busco sempre entregar o melhor. Meu foco sempre foi a qualidade e a satisfação.',
        CURRENT_TIMESTAMP);

INSERT INTO pacote (estudio_id, nome, qtd_fotos, preco, preco_foto_extra, prazo_entrega_dias, itens, material_entregue, ordem)
VALUES
 (1, 'Sessão 10', 10, 200.00, 35.00, 5,  'Sessão fotográfica|01 sessão sem limite de tempo|Edição fotográfica completa', '10 fotografias em formato digital para redes', 1),
 (1, 'Sessão 20', 20, 350.00, 35.00, 10, 'Sessão fotográfica|01 sessão sem limite de tempo|Edição fotográfica completa', '20 fotografias em formato digital para redes', 2),
 (1, 'Sessão 30', 30, 600.00, 35.00, 15, 'Sessão fotográfica|01 sessão sem limite de tempo|Edição fotográfica completa', '30 fotografias em formato digital para redes + brinde', 3),
 (1, 'Eventos',   NULL, 800.00, NULL, 15, 'Cobertura fotográfica|01 sessão sem limite de tempo|Edição básica', 'Todas as fotos do evento em formato digital + pendrive', 4);
