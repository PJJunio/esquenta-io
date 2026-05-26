package com.pjjunio.esquentaio.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "cartao_utilizado")
public class CartaoUtilizado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sala_id", nullable = false)
    private SalaSessao salaSessao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cartao_id", nullable = false)
    private CartaoConteudo cartao;

    @Column(name = "usado_em", nullable = false)
    private Instant usadoEm;

    public CartaoUtilizado() {}

    public CartaoUtilizado(SalaSessao salaSessao, CartaoConteudo cartao) {
        this.salaSessao = salaSessao;
        this.cartao     = cartao;
        this.usadoEm    = Instant.now();
    }

    public int getId() { return id; }
    public SalaSessao getSalaSessao() { return salaSessao; }
    public CartaoConteudo getCartao() { return cartao; }
    public Instant getUsadoEm() { return usadoEm; }
}
