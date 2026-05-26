package com.pjjunio.esquentaio.repository;

import com.pjjunio.esquentaio.entity.CartaoConteudo;
import com.pjjunio.esquentaio.enums.ModoRestrito;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CartaoConteudoRepository extends JpaRepository<CartaoConteudo, Integer> {

    /** Retorna cartões compatíveis com os modos informados (ex: DE_BOA + AMBOS) */
    List<CartaoConteudo> findByModoRestritoIn(List<ModoRestrito> modos);
}
