package com.pjjunio.esquentaio.repository;

import com.pjjunio.esquentaio.entity.Jogador;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JogadorRepository extends JpaRepository<Jogador, Integer> {

    Optional<Jogador> findBySalaSessaoIdAndNickname(int salaId, String nickname);

    List<Jogador> findBySalaSessaoId(int salaId);
}
