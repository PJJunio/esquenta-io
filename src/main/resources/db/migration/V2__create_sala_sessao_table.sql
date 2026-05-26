CREATE TYPE modo_jogo_enum   AS ENUM ('DE_BOA', 'ESQUENTA');
CREATE TYPE status_sala_enum AS ENUM ('LOBBY', 'ATIVA', 'FINALIZADA');

CREATE TABLE sala_sessao (
    id                BIGSERIAL        PRIMARY KEY,
    codigo_acesso     VARCHAR(4)       NOT NULL UNIQUE,
    modo_jogo         modo_jogo_enum   NOT NULL,
    status            status_sala_enum NOT NULL DEFAULT 'LOBBY',
    jogador_turno_id  BIGINT,
    cartao_atual_id   BIGINT,
    rodada_atual      INTEGER          NOT NULL DEFAULT 0,
    data_criacao      TIMESTAMPTZ      NOT NULL DEFAULT NOW()
);
