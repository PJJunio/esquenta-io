-- V9: Garante integridade dos tipos de cartão
-- MICO só pode ter modo_restrito = 'ESQUENTA' (exclusivo do modo 18+)
ALTER TABLE cartao_conteudo
    ADD CONSTRAINT chk_mico_modo_esquenta
        CHECK (tipo != 'MICO' OR modo_restrito = 'ESQUENTA');
