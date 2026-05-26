package com.pjjunio.esquentaio.repository;

import com.pjjunio.esquentaio.entity.CartaoUtilizado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CartaoUtilizadoRepository extends JpaRepository<CartaoUtilizado, Integer> {

    /** IDs dos cartões já utilizados nesta sala (para excluir do sorteio) */
    @Query("SELECT cu.cartao.id FROM CartaoUtilizado cu WHERE cu.salaSessao.id = :salaId")
    List<Integer> findCartaoIdsBySalaSessaoId(@Param("salaId") int salaId);

    /** Total de rodadas já jogadas nesta sala */
    long countBySalaSessaoId(int salaId);

    /** Limpa o histórico de cartões ao reiniciar a partida */
    void deleteBySalaSessaoId(int salaId);
}
