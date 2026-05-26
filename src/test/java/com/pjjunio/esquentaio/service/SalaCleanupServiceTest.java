package com.pjjunio.esquentaio.service;

import com.pjjunio.esquentaio.entity.Jogador;
import com.pjjunio.esquentaio.entity.SalaSessao;
import com.pjjunio.esquentaio.enums.ModoJogo;
import com.pjjunio.esquentaio.enums.StatusSala;
import com.pjjunio.esquentaio.repository.CartaoUtilizadoRepository;
import com.pjjunio.esquentaio.repository.SalaSessaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testes unitários do SalaCleanupService — limpeza automática de salas inativas.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SalaCleanupService")
class SalaCleanupServiceTest {

    @Mock SalaSessaoRepository      salaSessaoRepository;
    @Mock CartaoUtilizadoRepository cartaoUtilizadoRepository;

    @InjectMocks SalaCleanupService cleanupService;

    @BeforeEach
    void injectTtlValues() {
        // Injeta os valores de @Value manualmente (sem Spring context)
        ReflectionTestUtils.setField(cleanupService, "horasFinalizadas", 2L);
        ReflectionTestUtils.setField(cleanupService, "horasLobby",       1L);
        ReflectionTestUtils.setField(cleanupService, "horasAtiva",       8L);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private SalaSessao buildSala(int id, StatusSala status, Instant criacao) {
        SalaSessao sala = new SalaSessao();
        ReflectionTestUtils.setField(sala, "id", id);
        sala.setStatus(status);
        sala.setModoJogo(ModoJogo.DE_BOA);
        sala.setCodigoAcesso(String.format("%04d", id));
        sala.setDataCriacao(criacao);
        return sala;
    }

    // ─── Nenhuma sala para limpar ─────────────────────────────────────────────

    @Test
    @DisplayName("não deleta nada quando não há salas expiradas")
    void deveNaoDeletar_QuandoSemSalasExpiradas() {
        when(salaSessaoRepository.findByStatusInAndDataCriacaoBefore(any(), any()))
                .thenReturn(List.of());

        cleanupService.limparSalasInativas();

        verify(salaSessaoRepository, never()).delete(any());
        verify(salaSessaoRepository, never()).save(any());
        verify(cartaoUtilizadoRepository, never()).deleteBySalaSessaoId(anyInt());
    }

    // ─── Sala FINALIZADA expirada ────────────────────────────────────────────

    @Test
    @DisplayName("deleta sala FINALIZADA mais velha que 2 horas")
    void deveDeletar_SalaFinalizadaExpirada() {
        Instant muitoAntiga = Instant.now().minus(3, ChronoUnit.HOURS);
        SalaSessao sala = buildSala(1, StatusSala.FINALIZADA, muitoAntiga);

        // Apenas para FINALIZADA, devolvemos a sala
        when(salaSessaoRepository.findByStatusInAndDataCriacaoBefore(
                eq(List.of(StatusSala.FINALIZADA)), any()))
                .thenReturn(List.of(sala));

        // LOBBY e ATIVA retornam vazio
        when(salaSessaoRepository.findByStatusInAndDataCriacaoBefore(
                eq(List.of(StatusSala.LOBBY)), any()))
                .thenReturn(List.of());
        when(salaSessaoRepository.findByStatusInAndDataCriacaoBefore(
                eq(List.of(StatusSala.ATIVA)), any()))
                .thenReturn(List.of());

        when(salaSessaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        cleanupService.limparSalasInativas();

        // 1. cartão utilizado deletado
        verify(cartaoUtilizadoRepository).deleteBySalaSessaoId(1);
        // 2. FK circular quebrada (save com turno/cartao null)
        verify(salaSessaoRepository).save(sala);
        assertThat(sala.getJogadorTurno()).isNull();
        assertThat(sala.getCartaoAtual()).isNull();
        // 3. sala deletada
        verify(salaSessaoRepository).delete(sala);
    }

    // ─── Sala LOBBY expirada ─────────────────────────────────────────────────

    @Test
    @DisplayName("deleta sala LOBBY mais velha que 1 hora")
    void deveDeletar_SalaLobbyExpirada() {
        Instant antiga = Instant.now().minus(90, ChronoUnit.MINUTES);
        SalaSessao sala = buildSala(2, StatusSala.LOBBY, antiga);

        when(salaSessaoRepository.findByStatusInAndDataCriacaoBefore(
                eq(List.of(StatusSala.FINALIZADA)), any())).thenReturn(List.of());
        when(salaSessaoRepository.findByStatusInAndDataCriacaoBefore(
                eq(List.of(StatusSala.LOBBY)), any())).thenReturn(List.of(sala));
        when(salaSessaoRepository.findByStatusInAndDataCriacaoBefore(
                eq(List.of(StatusSala.ATIVA)), any())).thenReturn(List.of());
        when(salaSessaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        cleanupService.limparSalasInativas();

        verify(cartaoUtilizadoRepository).deleteBySalaSessaoId(2);
        verify(salaSessaoRepository).delete(sala);
    }

    // ─── Sala ATIVA expirada ─────────────────────────────────────────────────

    @Test
    @DisplayName("deleta sala ATIVA mais velha que 8 horas")
    void deveDeletar_SalaAtivaExpirada() {
        Instant muitoAntiga = Instant.now().minus(9, ChronoUnit.HOURS);
        SalaSessao sala = buildSala(3, StatusSala.ATIVA, muitoAntiga);

        when(salaSessaoRepository.findByStatusInAndDataCriacaoBefore(
                eq(List.of(StatusSala.FINALIZADA)), any())).thenReturn(List.of());
        when(salaSessaoRepository.findByStatusInAndDataCriacaoBefore(
                eq(List.of(StatusSala.LOBBY)), any())).thenReturn(List.of());
        when(salaSessaoRepository.findByStatusInAndDataCriacaoBefore(
                eq(List.of(StatusSala.ATIVA)), any())).thenReturn(List.of(sala));
        when(salaSessaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        cleanupService.limparSalasInativas();

        verify(salaSessaoRepository).delete(sala);
    }

    // ─── Sala recente: não deleta ─────────────────────────────────────────────

    @Test
    @DisplayName("não deleta sala FINALIZADA criada há menos de 2 horas")
    void naoDeveDeletar_SalaFinalizadaRecente() {
        // Sala recente — mas o mock retorna lista vazia para simular que não passou do TTL
        when(salaSessaoRepository.findByStatusInAndDataCriacaoBefore(any(), any()))
                .thenReturn(List.of());

        cleanupService.limparSalasInativas();

        verify(salaSessaoRepository, never()).delete(any());
    }

    // ─── Múltiplas salas ─────────────────────────────────────────────────────

    @Test
    @DisplayName("deleta múltiplas salas expiradas de diferentes status em uma execução")
    void deveDeletar_MultiplasSalas() {
        SalaSessao salaFinalizada = buildSala(1, StatusSala.FINALIZADA,
                Instant.now().minus(3, ChronoUnit.HOURS));
        SalaSessao salaLobby = buildSala(2, StatusSala.LOBBY,
                Instant.now().minus(2, ChronoUnit.HOURS));

        when(salaSessaoRepository.findByStatusInAndDataCriacaoBefore(
                eq(List.of(StatusSala.FINALIZADA)), any())).thenReturn(List.of(salaFinalizada));
        when(salaSessaoRepository.findByStatusInAndDataCriacaoBefore(
                eq(List.of(StatusSala.LOBBY)), any())).thenReturn(List.of(salaLobby));
        when(salaSessaoRepository.findByStatusInAndDataCriacaoBefore(
                eq(List.of(StatusSala.ATIVA)), any())).thenReturn(List.of());
        when(salaSessaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        cleanupService.limparSalasInativas();

        verify(cartaoUtilizadoRepository).deleteBySalaSessaoId(1);
        verify(cartaoUtilizadoRepository).deleteBySalaSessaoId(2);
        verify(salaSessaoRepository).delete(salaFinalizada);
        verify(salaSessaoRepository).delete(salaLobby);
    }

    // ─── Erro em uma sala não bloqueia as demais ──────────────────────────────

    @Test
    @DisplayName("erro ao deletar uma sala não impede a deleção das seguintes")
    void deveContinar_QuandoErroParcial() {
        SalaSessao salaOk   = buildSala(1, StatusSala.FINALIZADA,
                Instant.now().minus(3, ChronoUnit.HOURS));
        SalaSessao salaRuim = buildSala(2, StatusSala.FINALIZADA,
                Instant.now().minus(4, ChronoUnit.HOURS));

        when(salaSessaoRepository.findByStatusInAndDataCriacaoBefore(
                eq(List.of(StatusSala.FINALIZADA)), any())).thenReturn(List.of(salaRuim, salaOk));
        when(salaSessaoRepository.findByStatusInAndDataCriacaoBefore(
                eq(List.of(StatusSala.LOBBY)), any())).thenReturn(List.of());
        when(salaSessaoRepository.findByStatusInAndDataCriacaoBefore(
                eq(List.of(StatusSala.ATIVA)), any())).thenReturn(List.of());

        // Sala 2 lança exceção ao salvar; sala 1 deve ser processada mesmo assim
        when(salaSessaoRepository.save(eq(salaRuim)))
                .thenThrow(new RuntimeException("DB error simulado"));
        when(salaSessaoRepository.save(eq(salaOk)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Não deve lançar exceção (erro é capturado internamente)
        cleanupService.limparSalasInativas();

        // Sala 1 foi deletada mesmo com o erro na sala 2
        verify(salaSessaoRepository).delete(salaOk);
        verify(salaSessaoRepository, never()).delete(salaRuim); // falhou antes do delete
    }
}
