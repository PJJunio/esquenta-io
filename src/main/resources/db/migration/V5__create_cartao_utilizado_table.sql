CREATE TABLE cartao_utilizado (
    id        BIGSERIAL   PRIMARY KEY,
    sala_id   BIGINT      NOT NULL REFERENCES sala_sessao(id),
    cartao_id BIGINT      NOT NULL REFERENCES cartao_conteudo(id),
    usado_em  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
