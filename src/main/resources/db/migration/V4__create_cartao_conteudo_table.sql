CREATE TYPE tipo_cartao_enum   AS ENUM ('PERGUNTA', 'MICO');
CREATE TYPE modo_restrito_enum AS ENUM ('DE_BOA', 'ESQUENTA', 'AMBOS');

CREATE TABLE cartao_conteudo (
    id                BIGSERIAL          PRIMARY KEY,
    admin_id          BIGINT             NOT NULL REFERENCES admin(id),
    texto             TEXT               NOT NULL,
    tipo              tipo_cartao_enum   NOT NULL,
    modo_restrito     modo_restrito_enum NOT NULL,
    goles             INTEGER            NOT NULL DEFAULT 0,
    pontos_recompensa INTEGER            NOT NULL DEFAULT 10
);

ALTER TABLE sala_sessao
    ADD CONSTRAINT fk_sala_cartao_atual
        FOREIGN KEY (cartao_atual_id) REFERENCES cartao_conteudo(id);
