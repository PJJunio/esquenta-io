package com.pjjunio.esquentaio.entity;

import com.pjjunio.esquentaio.enums.ModoRestrito;
import com.pjjunio.esquentaio.enums.TipoCartao;
import jakarta.persistence.*;

@Entity
@Table(name = "cartao_conteudo")
public class CartaoConteudo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id", nullable = false)
    private Admin admin;

    @Column(nullable = false)
    private String texto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoCartao tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "modo_restrito", nullable = false)
    private ModoRestrito modoRestrito;

    @Column(nullable = false)
    private int goles;

    @Column(name = "pontos_recompensa", nullable = false)
    private int pontosRecompensa;

    /** Resposta correta (somente para PERGUNTA no modo De Boa / quiz). Nullable. */
    @Column(name = "resposta_correta", length = 500)
    private String respostaCorreta;

    /**
     * Alternativas erradas separadas pelo caractere §
     * Ex: "Paris§Madrid§Roma"
     * Somente para PERGUNTA no modo De Boa / quiz. Nullable.
     */
    @Column(name = "opcoes_erradas", length = 1000)
    private String opcoesErradas;

    protected CartaoConteudo() {}

    public CartaoConteudo(Admin admin, String texto, TipoCartao tipo, ModoRestrito modoRestrito, int goles, int pontosRecompensa) {
        this.admin = admin;
        this.texto = texto;
        this.tipo = tipo;
        this.modoRestrito = modoRestrito;
        this.goles = goles;
        this.pontosRecompensa = pontosRecompensa;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Admin getAdmin() {
        return admin;
    }

    public void setAdmin(Admin admin) {
        this.admin = admin;
    }

    public String getTexto() {
        return texto;
    }

    public void setTexto(String texto) {
        this.texto = texto;
    }

    public TipoCartao getTipo() {
        return tipo;
    }

    public void setTipo(TipoCartao tipo) {
        this.tipo = tipo;
    }

    public ModoRestrito getModoRestrito() {
        return modoRestrito;
    }

    public void setModoRestrito(ModoRestrito modoRestrito) {
        this.modoRestrito = modoRestrito;
    }

    public int getGoles() {
        return goles;
    }

    public void setGoles(int goles) {
        this.goles = goles;
    }

    public int getPontosRecompensa() {
        return pontosRecompensa;
    }

    public void setPontosRecompensa(int pontosRecompensa) {
        this.pontosRecompensa = pontosRecompensa;
    }

    public String getRespostaCorreta() {
        return respostaCorreta;
    }

    public void setRespostaCorreta(String respostaCorreta) {
        this.respostaCorreta = (respostaCorreta != null && respostaCorreta.isBlank()) ? null : respostaCorreta;
    }

    public String getOpcoesErradas() {
        return opcoesErradas;
    }

    public void setOpcoesErradas(String opcoesErradas) {
        this.opcoesErradas = (opcoesErradas != null && opcoesErradas.isBlank()) ? null : opcoesErradas;
    }

    /** Retorna true se este cartão tem opções de múltipla escolha configuradas. */
    public boolean isQuizCompleto() {
        return respostaCorreta != null && !respostaCorreta.isBlank()
                && opcoesErradas != null && !opcoesErradas.isBlank();
    }

    /** Retorna true se tem apenas a resposta correta (sem opções erradas). */
    public boolean hasRespostaCorreta() {
        return respostaCorreta != null && !respostaCorreta.isBlank();
    }
}
