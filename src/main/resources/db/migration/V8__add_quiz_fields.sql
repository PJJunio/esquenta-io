-- ============================================================
-- V8: Adiciona campos de quiz ao cartão (modo De Boa)
-- resposta_correta : resposta certa para perguntas de quiz
-- opcoes_erradas   : alternativas erradas separadas por §
--                    (ex: "Paris§Madrid§Roma")
-- ============================================================

ALTER TABLE cartao_conteudo
    ADD COLUMN IF NOT EXISTS resposta_correta VARCHAR(500);

ALTER TABLE cartao_conteudo
    ADD COLUMN IF NOT EXISTS opcoes_erradas VARCHAR(1000);
