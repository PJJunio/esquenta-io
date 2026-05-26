-- ============================================================
-- V7: Converte tipos enum nativos do PostgreSQL para VARCHAR
-- ============================================================
-- Motivo: Hibernate 6 envia parâmetros de enum como 'character varying',
-- mas o PostgreSQL não possui operador '=' entre um tipo enum nativo e
-- varchar sem cast explícito. A V6 tentava resolver via IMPLICIT CAST,
-- mas criar CASTs exige privilégio de superusuário. A solução definitiva
-- é usar VARCHAR com CHECK constraint, que é totalmente compatível com
-- @Enumerated(EnumType.STRING) do Hibernate.
-- ============================================================

-- ────────────────────────────────────────────────────────────
-- 1. cartao_conteudo.tipo  (tipo_cartao_enum → VARCHAR)
-- ────────────────────────────────────────────────────────────
ALTER TABLE cartao_conteudo
    ALTER COLUMN tipo TYPE VARCHAR(20) USING tipo::text;

ALTER TABLE cartao_conteudo
    ADD CONSTRAINT chk_cartao_tipo
        CHECK (tipo IN ('PERGUNTA', 'MICO'));

-- ────────────────────────────────────────────────────────────
-- 2. cartao_conteudo.modo_restrito  (modo_restrito_enum → VARCHAR)
-- ────────────────────────────────────────────────────────────
ALTER TABLE cartao_conteudo
    ALTER COLUMN modo_restrito TYPE VARCHAR(20) USING modo_restrito::text;

ALTER TABLE cartao_conteudo
    ADD CONSTRAINT chk_cartao_modo_restrito
        CHECK (modo_restrito IN ('DE_BOA', 'ESQUENTA', 'AMBOS'));

-- ────────────────────────────────────────────────────────────
-- 3. sala_sessao.modo_jogo  (modo_jogo_enum → VARCHAR)
-- ────────────────────────────────────────────────────────────
ALTER TABLE sala_sessao
    ALTER COLUMN modo_jogo TYPE VARCHAR(20) USING modo_jogo::text;

ALTER TABLE sala_sessao
    ADD CONSTRAINT chk_sala_modo_jogo
        CHECK (modo_jogo IN ('DE_BOA', 'ESQUENTA'));

-- ────────────────────────────────────────────────────────────
-- 4. sala_sessao.status  (status_sala_enum → VARCHAR)
-- ────────────────────────────────────────────────────────────
ALTER TABLE sala_sessao
    ALTER COLUMN status TYPE VARCHAR(20) USING status::text;

ALTER TABLE sala_sessao
    ADD CONSTRAINT chk_sala_status
        CHECK (status IN ('LOBBY', 'ATIVA', 'FINALIZADA'));

-- ────────────────────────────────────────────────────────────
-- 5. Remove os tipos enum nativos e seus casts dependentes
--    (CASCADE remove automaticamente os IMPLICITs casts criados na V6)
-- ────────────────────────────────────────────────────────────
DROP TYPE IF EXISTS tipo_cartao_enum CASCADE;
DROP TYPE IF EXISTS modo_restrito_enum CASCADE;
DROP TYPE IF EXISTS modo_jogo_enum CASCADE;
DROP TYPE IF EXISTS status_sala_enum CASCADE;
