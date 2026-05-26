-- V10: Adiciona PERGUNTA_SIM_NAO ao constraint de tipo (era omitido em V7)
ALTER TABLE cartao_conteudo DROP CONSTRAINT chk_cartao_tipo;
ALTER TABLE cartao_conteudo
    ADD CONSTRAINT chk_cartao_tipo
        CHECK (tipo IN ('PERGUNTA', 'PERGUNTA_SIM_NAO', 'MICO'));
