-- V11: PERGUNTA_SIM_NAO só pode ter modo_restrito = 'ESQUENTA'
--      (mesma regra do MICO — é um tipo exclusivo do modo 18+)
ALTER TABLE cartao_conteudo
    ADD CONSTRAINT chk_pergunta_sim_nao_esquenta
        CHECK (tipo != 'PERGUNTA_SIM_NAO' OR modo_restrito = 'ESQUENTA');
