-- Permite que o Hibernate passe strings VARCHAR para colunas ENUM sem cast explícito
CREATE CAST (VARCHAR AS modo_jogo_enum)   WITH INOUT AS IMPLICIT;
CREATE CAST (VARCHAR AS status_sala_enum) WITH INOUT AS IMPLICIT;
CREATE CAST (VARCHAR AS tipo_cartao_enum)   WITH INOUT AS IMPLICIT;
CREATE CAST (VARCHAR AS modo_restrito_enum) WITH INOUT AS IMPLICIT;
