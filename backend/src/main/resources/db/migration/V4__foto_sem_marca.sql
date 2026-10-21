-- Prévia e miniatura SEM marca d'água da foto editada.
-- Só são mostradas ao cliente depois que o download é liberado; antes disso a galeria usa as versões com marca.
ALTER TABLE foto ADD COLUMN previa_limpa VARCHAR(200) NULL;
ALTER TABLE foto ADD COLUMN miniatura_limpa VARCHAR(200) NULL;
