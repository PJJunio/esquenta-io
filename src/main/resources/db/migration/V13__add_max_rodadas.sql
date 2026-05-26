-- V13: host pode escolher número máximo de rodadas na sala
-- NULL = sem limite (joga todos os cartões disponíveis)
ALTER TABLE sala_sessao ADD COLUMN max_rodadas INT DEFAULT NULL;
