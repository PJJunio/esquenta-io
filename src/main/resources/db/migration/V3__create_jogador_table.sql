CREATE TABLE jogador (
    id        BIGSERIAL    PRIMARY KEY,
    sala_id   BIGINT       NOT NULL REFERENCES sala_sessao(id),
    nickname  VARCHAR(25)  NOT NULL,
    pontuacao INTEGER      NOT NULL DEFAULT 0,
    is_host   BOOLEAN      NOT NULL DEFAULT FALSE,
    is_ready  BOOLEAN      NOT NULL DEFAULT FALSE,
    UNIQUE(sala_id, nickname)
);

ALTER TABLE sala_sessao
    ADD CONSTRAINT fk_sala_jogador_turno
        FOREIGN KEY (jogador_turno_id) REFERENCES jogador(id);
