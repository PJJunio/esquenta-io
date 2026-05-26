package com.pjjunio.esquentaio.repository;

import com.pjjunio.esquentaio.entity.SalaSessao;
import com.pjjunio.esquentaio.enums.StatusSala;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SalaSessaoRepository extends JpaRepository<SalaSessao, Integer> {

    Optional<SalaSessao> findByCodigoAcesso(String codigoAcesso);

    /** Lock exclusivo: impede race condition ao sortear cartão (dois jogadores batendo /partida ao mesmo tempo) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SalaSessao s WHERE s.id = :id")
    Optional<SalaSessao> findByIdForUpdate(@Param("id") int id);

    /** Retorna salas com um dos status informados cuja data de criação é anterior ao limite. */
    List<SalaSessao> findByStatusInAndDataCriacaoBefore(List<StatusSala> status, Instant limite);
}
