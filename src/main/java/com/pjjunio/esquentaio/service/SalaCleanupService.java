package com.pjjunio.esquentaio.service;

import com.pjjunio.esquentaio.entity.SalaSessao;
import com.pjjunio.esquentaio.enums.StatusSala;
import com.pjjunio.esquentaio.repository.CartaoUtilizadoRepository;
import com.pjjunio.esquentaio.repository.SalaSessaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class SalaCleanupService {

    private static final Logger log = LoggerFactory.getLogger(SalaCleanupService.class);

    private final SalaSessaoRepository salaSessaoRepository;
    private final CartaoUtilizadoRepository cartaoUtilizadoRepository;

    @Value("${cleanup.sala.finalizada.horas:2}")
    private long horasFinalizadas;

    @Value("${cleanup.sala.lobby.horas:1}")
    private long horasLobby;

    @Value("${cleanup.sala.ativa.horas:8}")
    private long horasAtiva;

    public SalaCleanupService(SalaSessaoRepository salaSessaoRepository,
                              CartaoUtilizadoRepository cartaoUtilizadoRepository) {
        this.salaSessaoRepository = salaSessaoRepository;
        this.cartaoUtilizadoRepository = cartaoUtilizadoRepository;
    }

    @Scheduled(fixedDelay = 600_000) // a cada 10 minutos
    @Transactional
    public void limparSalasInativas() {
        Instant agora = Instant.now();

        List<SalaSessao> paraRemover = new ArrayList<>();

        // Salas finalizadas (TTL configurável, padrão 2h)
        paraRemover.addAll(salaSessaoRepository.findByStatusInAndDataCriacaoBefore(
                List.of(StatusSala.FINALIZADA),
                agora.minus(horasFinalizadas, ChronoUnit.HOURS)));

        // Salas no lobby (TTL configurável, padrão 1h)
        paraRemover.addAll(salaSessaoRepository.findByStatusInAndDataCriacaoBefore(
                List.of(StatusSala.LOBBY),
                agora.minus(horasLobby, ChronoUnit.HOURS)));

        // Salas ativas sem atividade (TTL configurável, padrão 8h)
        paraRemover.addAll(salaSessaoRepository.findByStatusInAndDataCriacaoBefore(
                List.of(StatusSala.ATIVA),
                agora.minus(horasAtiva, ChronoUnit.HOURS)));

        if (paraRemover.isEmpty()) {
            log.debug("Cleanup: nenhuma sala inativa encontrada.");
            return;
        }

        log.info("Cleanup: removendo {} sala(s) inativa(s).", paraRemover.size());

        for (SalaSessao sala : paraRemover) {
            try {
                deletarSala(sala);
                log.info("Cleanup: sala id={} codigo={} status={} removida.",
                        sala.getId(), sala.getCodigoAcesso(), sala.getStatus());
            } catch (Exception e) {
                log.error("Cleanup: erro ao remover sala id={}: {}", sala.getId(), e.getMessage(), e);
            }
        }
    }

    private void deletarSala(SalaSessao sala) {
        int salaId = sala.getId();

        // 1. Remove histórico de cartões (não tem cascade automático)
        cartaoUtilizadoRepository.deleteBySalaSessaoId(salaId);

        // 2. Quebra o ciclo FK: sala_sessao.jogador_turno_id → jogador.id
        //    (jogador.sala_id → sala_sessao.id impede deleção direta sem isso)
        sala.setJogadorTurno(null);
        sala.setCartaoAtual(null);
        salaSessaoRepository.save(sala);

        // 3. Deleta a sala; CascadeType.ALL em SalaSessao.jogadores
        //    deleta automaticamente todos os Jogador relacionados
        salaSessaoRepository.delete(sala);
    }
}
