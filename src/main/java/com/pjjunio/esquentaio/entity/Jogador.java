package com.pjjunio.esquentaio.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "jogador", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"sala_id", "nickname"})
})
public class Jogador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sala_id", nullable = false)
    private SalaSessao salaSessao;

    @Column(length = 25, nullable = false)
    private String nickname;

    @Column(nullable = false)
    private int pontuacao;

    @Column(name = "is_host", nullable = false)
    private boolean isHost;

    @Column(name = "is_ready", nullable = false)
    private boolean isReady;

    public Jogador() {}

    public Jogador(SalaSessao salaSessao, String nickname, int pontuacao, boolean isHost, boolean isReady) {
        this.salaSessao = salaSessao;
        this.nickname = nickname;
        this.pontuacao = pontuacao;
        this.isHost = isHost;
        this.isReady = isReady;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public SalaSessao getSalaSessao() {
        return salaSessao;
    }

    public void setSalaSessao(SalaSessao salaSessao) {
        this.salaSessao = salaSessao;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public int getPontuacao() {
        return pontuacao;
    }

    public void setPontuacao(int pontuacao) {
        this.pontuacao = pontuacao;
    }

    public boolean isHost() {
        return isHost;
    }

    public void setHost(boolean host) {
        isHost = host;
    }

    public boolean isReady() {
        return isReady;
    }

    public void setReady(boolean ready) {
        isReady = ready;
    }
}
