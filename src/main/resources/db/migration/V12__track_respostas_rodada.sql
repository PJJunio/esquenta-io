-- V12: rastreia quais jogadores já responderam na rodada atual (DE_BOA)
-- Reset a NULL quando a rodada avança; armazena IDs separados por vírgula.
ALTER TABLE sala_sessao
    ADD COLUMN respostas_rodada_atual TEXT DEFAULT NULL;
