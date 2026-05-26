package com.pjjunio.esquentaio.entity;

import com.pjjunio.esquentaio.enums.ModoJogo;
import com.pjjunio.esquentaio.enums.StatusSala;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sala_sessao")
public class SalaSessao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "codigo_acesso", length = 4, unique = true, nullable = false)
    private String codigoAcesso;

    @Enumerated(EnumType.STRING)
    @Column(name = "modo_jogo", length = 25, nullable = false)
    private ModoJogo modoJogo;

    @Enumerated(EnumType.STRING)
    @Column(length = 25, nullable = false)
    private StatusSala status;

    /** Jogador cuja vez é agora (null = sala em LOBBY ou sem jogadores) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jogador_turno_id")
    private Jogador jogadorTurno;

    /** Cartão sorteado para a rodada atual (null = aguardando sorteio) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cartao_atual_id")
    private CartaoConteudo cartaoAtual;

    /** Contador de rodadas já resolvidas nesta sessão */
    @Column(name = "rodada_atual", nullable = false)
    private int rodadaAtual = 0;

    /**
     * IDs dos jogadores que já responderam na rodada atual (DE_BOA).
     * Armazenados como CSV separado por vírgula. Resetado para null a cada nova rodada.
     */
    @Column(name = "respostas_rodada_atual", columnDefinition = "TEXT")
    private String respostasRodadaAtual;

    @Column(name = "data_criacao", nullable = false)
    private Instant dataCriacao;

    /** Lista de todos os jogadores nesta sala */
    @OneToMany(mappedBy = "salaSessao", cascade = CascadeType.ALL)
    private List<Jogador> jogador = new ArrayList<>();

    public SalaSessao() {}

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getCodigoAcesso() { return codigoAcesso; }
    public void setCodigoAcesso(String codigoAcesso) { this.codigoAcesso = codigoAcesso; }

    public ModoJogo getModoJogo() { return modoJogo; }
    public void setModoJogo(ModoJogo modoJogo) { this.modoJogo = modoJogo; }

    public StatusSala getStatus() { return status; }
    public void setStatus(StatusSala status) { this.status = status; }

    public Jogador getJogadorTurno() { return jogadorTurno; }
    public void setJogadorTurno(Jogador jogadorTurno) { this.jogadorTurno = jogadorTurno; }

    public CartaoConteudo getCartaoAtual() { return cartaoAtual; }
    public void setCartaoAtual(CartaoConteudo cartaoAtual) { this.cartaoAtual = cartaoAtual; }

    public int getRodadaAtual() { return rodadaAtual; }
    public void setRodadaAtual(int rodadaAtual) { this.rodadaAtual = rodadaAtual; }

    public Instant getDataCriacao() { return dataCriacao; }
    public void setDataCriacao(Instant dataCriacao) { this.dataCriacao = dataCriacao; }

    public List<Jogador> getJogador() { return jogador; }
    public void setJogador(List<Jogador> jogador) { this.jogador = jogador; }

    public String getRespostasRodadaAtual() { return respostasRodadaAtual; }
    public void setRespostasRodadaAtual(String respostasRodadaAtual) { this.respostasRodadaAtual = respostasRodadaAtual; }

    /** Número máximo de rodadas que o host configurou (null = sem limite) */
    @Column(name = "max_rodadas")
    private Integer maxRodadas;

    public Integer getMaxRodadas() { return maxRodadas; }
    public void setMaxRodadas(Integer maxRodadas) { this.maxRodadas = maxRodadas; }
}
