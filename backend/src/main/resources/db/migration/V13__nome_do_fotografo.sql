-- Nome do fotógrafo como aparece no site ("Sobre") e nas mensagens ("Aqui é o Joel…"), em vez de fixo no código.
ALTER TABLE estudio ADD COLUMN fotografo VARCHAR(120) NULL;
UPDATE estudio SET fotografo = 'Joel Júnior' WHERE slug = 'jrfotografia';
