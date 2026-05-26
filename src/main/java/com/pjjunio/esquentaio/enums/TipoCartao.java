package com.pjjunio.esquentaio.enums;

public enum TipoCartao {

    /** Cartão com uma pergunta aberta ou múltipla escolha. Acerto gera pontos; erro gera penalidade. */
    PERGUNTA,

    /** Cartão com pergunta de Sim ou Não. Exibe botões SIM/NÃO. Mesma lógica de pontos de PERGUNTA. */
    PERGUNTA_SIM_NAO,

    /** Cartão de mico/desafio — exclusivo do modo Esquenta. Cumprir = sem penalidade; recusar/trocar = perde pontos + bebe. */
    MICO
}
