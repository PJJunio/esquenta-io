package com.pjjunio.esquentaio.service;

import com.pjjunio.esquentaio.entity.CartaoConteudo;
import com.pjjunio.esquentaio.entity.CartaoUtilizado;
import com.pjjunio.esquentaio.entity.Jogador;
import com.pjjunio.esquentaio.entity.SalaSessao;
import com.pjjunio.esquentaio.enums.ModoJogo;
import com.pjjunio.esquentaio.enums.ModoRestrito;
import com.pjjunio.esquentaio.enums.StatusSala;
import com.pjjunio.esquentaio.enums.TipoCartao;
import com.pjjunio.esquentaio.repository.CartaoConteudoRepository;
import com.pjjunio.esquentaio.repository.CartaoUtilizadoRepository;
import com.pjjunio.esquentaio.repository.JogadorRepository;
import com.pjjunio.esquentaio.repository.SalaSessaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testes unitários do GameService — lógica de negócio do jogo.
 * Todos os repositórios são mockados (sem banco real).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GameService")
class GameServiceTest {

    @Mock SalaSessaoRepository     salaSessaoRepository;
    @Mock JogadorRepository        jogadorRepository;
    @Mock CartaoConteudoRepository cartaoConteudoRepository;
    @Mock CartaoUtilizadoRepository cartaoUtilizadoRepository;

    @InjectMocks GameService gameService;

    // ─── Helpers de construção ────────────────────────────────────────────────

    /** Instancia uma classe mesmo que o construtor no-arg seja protected (ex.: entidades JPA). */
    @SuppressWarnings("unchecked")
    private <T> T instantiate(Class<T> clazz) {
        try {
            Constructor<T> ctor = clazz.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Falha ao instanciar " + clazz.getSimpleName(), e);
        }
    }

    private SalaSessao buildSala(int id, ModoJogo modo, StatusSala status) {
        SalaSessao sala = new SalaSessao();
        ReflectionTestUtils.setField(sala, "id", id);
        sala.setModoJogo(modo);
        sala.setStatus(status);
        sala.setCodigoAcesso("1234");
        sala.setRodadaAtual(0);
        sala.setDataCriacao(Instant.now());
        return sala;
    }

    private Jogador buildJogador(int id, SalaSessao sala, boolean host) {
        Jogador j = new Jogador();
        ReflectionTestUtils.setField(j, "id", id);
        j.setSalaSessao(sala);
        j.setNickname("Jogador" + id);
        j.setPontuacao(0);
        j.setHost(host);
        j.setReady(host); // host nasce pronto
        return j;
    }

    private CartaoConteudo buildCartao(int id, ModoRestrito modo, int pontos) {
        CartaoConteudo c = instantiate(CartaoConteudo.class);
        ReflectionTestUtils.setField(c, "id", id);
        c.setTexto("Desafio " + id);
        c.setTipo(TipoCartao.PERGUNTA);
        c.setModoRestrito(modo);
        c.setPontosRecompensa(pontos);
        c.setGoles(1);
        return c;
    }

    // ─── sortearCartaoDisponivel ──────────────────────────────────────────────

    @Nested
    @DisplayName("sortearCartaoDisponivel")
    class SortearCartaoDisponivel {

        @Test
        @DisplayName("retorna cartão compatível com modo DE_BOA (AMBOS + DE_BOA)")
        void deveRetornarCartaoCompativel_DeBoa() {
            SalaSessao sala = buildSala(1, ModoJogo.DE_BOA, StatusSala.ATIVA);

            CartaoConteudo cartaoAmbos   = buildCartao(1, ModoRestrito.AMBOS,   5);
            CartaoConteudo cartaoDeBoa   = buildCartao(2, ModoRestrito.DE_BOA,  5);
            CartaoConteudo cartaoEsquenta = buildCartao(3, ModoRestrito.ESQUENTA, 5); // não deve aparecer

            when(cartaoUtilizadoRepository.findCartaoIdsBySalaSessaoId(1)).thenReturn(List.of());
            when(cartaoConteudoRepository.findByModoRestritoIn(
                    argThat(list -> list.contains(ModoRestrito.AMBOS) && list.contains(ModoRestrito.DE_BOA))))
                    .thenReturn(List.of(cartaoAmbos, cartaoDeBoa));

            CartaoConteudo resultado = gameService.sortearCartaoDisponivel(sala);

            assertThat(resultado).isNotNull();
            assertThat(resultado.getModoRestrito()).isIn(ModoRestrito.AMBOS, ModoRestrito.DE_BOA);
        }

        @Test
        @DisplayName("retorna cartão compatível com modo ESQUENTA (AMBOS + ESQUENTA)")
        void deveRetornarCartaoCompativel_Esquenta() {
            SalaSessao sala = buildSala(1, ModoJogo.ESQUENTA, StatusSala.ATIVA);

            CartaoConteudo cartaoEsquenta = buildCartao(10, ModoRestrito.ESQUENTA, 3);

            when(cartaoUtilizadoRepository.findCartaoIdsBySalaSessaoId(1)).thenReturn(List.of());
            when(cartaoConteudoRepository.findByModoRestritoIn(
                    argThat(list -> list.contains(ModoRestrito.AMBOS) && list.contains(ModoRestrito.ESQUENTA))))
                    .thenReturn(List.of(cartaoEsquenta));

            CartaoConteudo resultado = gameService.sortearCartaoDisponivel(sala);

            assertThat(resultado).isNotNull();
            assertThat(resultado.getId()).isEqualTo(10);
        }

        @Test
        @DisplayName("retorna null quando todos os cartões já foram utilizados")
        void deveRetornarNull_QuandoSemCartoes() {
            SalaSessao sala = buildSala(1, ModoJogo.DE_BOA, StatusSala.ATIVA);

            CartaoConteudo cartao = buildCartao(1, ModoRestrito.AMBOS, 5);

            when(cartaoUtilizadoRepository.findCartaoIdsBySalaSessaoId(1)).thenReturn(List.of(1)); // já usado
            when(cartaoConteudoRepository.findByModoRestritoIn(any())).thenReturn(List.of(cartao));

            CartaoConteudo resultado = gameService.sortearCartaoDisponivel(sala);

            assertThat(resultado).isNull();
        }

        @Test
        @DisplayName("retorna null quando não há cartões no banco")
        void deveRetornarNull_QuandoBancoVazio() {
            SalaSessao sala = buildSala(1, ModoJogo.DE_BOA, StatusSala.ATIVA);

            when(cartaoUtilizadoRepository.findCartaoIdsBySalaSessaoId(1)).thenReturn(List.of());
            when(cartaoConteudoRepository.findByModoRestritoIn(any())).thenReturn(List.of());

            assertThat(gameService.sortearCartaoDisponivel(sala)).isNull();
        }
    }

    // ─── garantirCartaoAtual ──────────────────────────────────────────────────

    @Nested
    @DisplayName("garantirCartaoAtual")
    class GarantirCartaoAtual {

        @Test
        @DisplayName("sorteia cartão quando cartaoAtual é null")
        void deveSortearCartao_QuandoNaoTemCartao() {
            SalaSessao sala = buildSala(1, ModoJogo.DE_BOA, StatusSala.ATIVA);
            // sem cartão atual
            CartaoConteudo cartao = buildCartao(5, ModoRestrito.AMBOS, 10);

            when(salaSessaoRepository.findById(1)).thenReturn(Optional.of(sala));
            when(cartaoUtilizadoRepository.findCartaoIdsBySalaSessaoId(1)).thenReturn(List.of());
            when(cartaoConteudoRepository.findByModoRestritoIn(any())).thenReturn(List.of(cartao));
            when(salaSessaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            SalaSessao resultado = gameService.garantirCartaoAtual(1);

            assertThat(resultado.getCartaoAtual()).isEqualTo(cartao);
            verify(salaSessaoRepository).save(sala);
        }

        @Test
        @DisplayName("não sorteia novo cartão quando cartaoAtual já existe (idempotente)")
        void deveManterCartaoExistente_QuandoJaTem() {
            SalaSessao sala = buildSala(1, ModoJogo.DE_BOA, StatusSala.ATIVA);
            CartaoConteudo cartaoExistente = buildCartao(99, ModoRestrito.AMBOS, 5);
            sala.setCartaoAtual(cartaoExistente);

            when(salaSessaoRepository.findById(1)).thenReturn(Optional.of(sala));

            SalaSessao resultado = gameService.garantirCartaoAtual(1);

            assertThat(resultado.getCartaoAtual()).isEqualTo(cartaoExistente);
            verify(salaSessaoRepository, never()).save(any()); // sem salvar = sem alteração
        }

        @Test
        @DisplayName("finaliza o jogo quando não há mais cartões disponíveis")
        void deveFinalizarJogo_QuandoSemCartoes() {
            SalaSessao sala = buildSala(1, ModoJogo.DE_BOA, StatusSala.ATIVA);

            when(salaSessaoRepository.findById(1)).thenReturn(Optional.of(sala));
            when(cartaoUtilizadoRepository.findCartaoIdsBySalaSessaoId(1)).thenReturn(List.of());
            when(cartaoConteudoRepository.findByModoRestritoIn(any())).thenReturn(List.of());
            when(salaSessaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            SalaSessao resultado = gameService.garantirCartaoAtual(1);

            assertThat(resultado.getStatus()).isEqualTo(StatusSala.FINALIZADA);
            assertThat(resultado.getJogadorTurno()).isNull();
            assertThat(resultado.getCartaoAtual()).isNull();
        }
    }

    // ─── processarResultado ───────────────────────────────────────────────────

    @Nested
    @DisplayName("processarResultado")
    class ProcessarResultado {

        private SalaSessao sala;
        private Jogador    jogador;
        private CartaoConteudo cartao;

        @BeforeEach
        void setup() {
            sala    = buildSala(1, ModoJogo.DE_BOA, StatusSala.ATIVA);
            jogador = buildJogador(10, sala, true);
            jogador.setPontuacao(20);
            cartao  = buildCartao(5, ModoRestrito.AMBOS, 10);

            sala.setJogadorTurno(jogador);
            sala.setCartaoAtual(cartao);
            sala.getJogador().add(jogador);

            when(salaSessaoRepository.findById(1)).thenReturn(Optional.of(sala));
            lenient().when(salaSessaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            lenient().when(cartaoUtilizadoRepository.findCartaoIdsBySalaSessaoId(1)).thenReturn(List.of(5));
            lenient().when(cartaoConteudoRepository.findByModoRestritoIn(any())).thenReturn(List.of());
        }

        @Test
        @DisplayName("sucesso: adiciona pontosRecompensa ao jogador")
        void sucesso_deveAdicionarPontos() {
            gameService.processarResultado(1, true);

            assertThat(jogador.getPontuacao()).isEqualTo(30); // 20 + 10
            verify(jogadorRepository).save(jogador);
        }

        @Test
        @DisplayName("falha em DE_BOA: subtrai pontos sem ir abaixo de zero")
        void falha_DeBoa_deveSubtrairPontos() {
            jogador.setPontuacao(5); // menos que a penalidade (10)
            gameService.processarResultado(1, false);

            assertThat(jogador.getPontuacao()).isEqualTo(0); // piso em 0
            verify(jogadorRepository).save(jogador);
        }

        @Test
        @DisplayName("falha em ESQUENTA: não altera pontuação")
        void falha_Esquenta_naoAlteraPontos() {
            ReflectionTestUtils.setField(sala, "modoJogo", ModoJogo.ESQUENTA);
            jogador.setPontuacao(15);

            gameService.processarResultado(1, false);

            assertThat(jogador.getPontuacao()).isEqualTo(15); // sem alteração
        }

        @Test
        @DisplayName("registra o cartão como utilizado após a rodada")
        void deveRegistrarCartaoUtilizado() {
            gameService.processarResultado(1, true);

            ArgumentCaptor<CartaoUtilizado> captor = ArgumentCaptor.forClass(CartaoUtilizado.class);
            verify(cartaoUtilizadoRepository).save(captor.capture());
            assertThat(captor.getValue().getCartao()).isEqualTo(cartao);
            assertThat(captor.getValue().getSalaSessao()).isEqualTo(sala);
        }

        @Test
        @DisplayName("finaliza o jogo e retorna true quando cartões se esgotam")
        void deveFinalizarJogo_QuandoSemCartoes() {
            boolean acabou = gameService.processarResultado(1, true);

            assertThat(acabou).isTrue();
            assertThat(sala.getStatus()).isEqualTo(StatusSala.FINALIZADA);
            assertThat(sala.getJogadorTurno()).isNull();
        }

        @Test
        @DisplayName("avança para próximo jogador e retorna false quando há cartões")
        void deveAvancarJogador_QuandoHaCartoes() {
            Jogador jogador2 = buildJogador(11, sala, false);
            sala.getJogador().add(jogador2);

            CartaoConteudo proximo = buildCartao(6, ModoRestrito.AMBOS, 5);
            // Novo estado: cartão 5 usado, cartão 6 disponível
            when(cartaoUtilizadoRepository.findCartaoIdsBySalaSessaoId(1)).thenReturn(List.of(5));
            when(cartaoConteudoRepository.findByModoRestritoIn(any())).thenReturn(List.of(proximo));

            boolean acabou = gameService.processarResultado(1, true);

            assertThat(acabou).isFalse();
            assertThat(sala.getStatus()).isEqualTo(StatusSala.ATIVA);
            assertThat(sala.getJogadorTurno()).isNotNull();
        }

        @Test
        @DisplayName("duplo clique (cartão null): ignora sem exceção")
        void deveIgnorar_QuandoCartaoNull() {
            sala.setCartaoAtual(null); // simulando duplo clique

            gameService.processarResultado(1, true);

            verify(jogadorRepository, never()).save(any());
            verify(cartaoUtilizadoRepository, never()).save(any());
        }
    }

    // ─── iniciarPartida ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("iniciarPartida")
    class IniciarPartida {

        @Test
        @DisplayName("muda status para ATIVA e define primeiro jogador")
        void deveIniciarPartida() {
            SalaSessao sala = buildSala(1, ModoJogo.DE_BOA, StatusSala.LOBBY);
            Jogador host = buildJogador(1, sala, true);
            sala.getJogador().add(host);

            when(salaSessaoRepository.findById(1)).thenReturn(Optional.of(sala));
            when(salaSessaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            SalaSessao resultado = gameService.iniciarPartida(1);

            assertThat(resultado.getStatus()).isEqualTo(StatusSala.ATIVA);
            assertThat(resultado.getRodadaAtual()).isEqualTo(0);
            assertThat(resultado.getJogadorTurno()).isNotNull();
        }

        @Test
        @DisplayName("lança exceção se sala não está em LOBBY")
        void deveLancarExcecao_QuandoNaoLobby() {
            SalaSessao sala = buildSala(1, ModoJogo.DE_BOA, StatusSala.ATIVA);
            when(salaSessaoRepository.findById(1)).thenReturn(Optional.of(sala));

            assertThatThrownBy(() -> gameService.iniciarPartida(1))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("LOBBY");
        }
    }

    // ─── jogarDeNovo ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("jogarDeNovo")
    class JogarDeNovo {

        @Test
        @DisplayName("reseta sala para LOBBY e limpa histórico de cartões")
        void deveResetarSala() {
            SalaSessao sala = buildSala(1, ModoJogo.DE_BOA, StatusSala.FINALIZADA);
            Jogador host  = buildJogador(1, sala, true);
            Jogador guest = buildJogador(2, sala, false);
            host.setPontuacao(100);
            guest.setPontuacao(50);
            sala.getJogador().addAll(List.of(host, guest));

            when(salaSessaoRepository.findById(1)).thenReturn(Optional.of(sala));
            when(salaSessaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            gameService.jogarDeNovo(1);

            assertThat(sala.getStatus()).isEqualTo(StatusSala.LOBBY);
            assertThat(sala.getRodadaAtual()).isEqualTo(0);
            assertThat(sala.getCartaoAtual()).isNull();
            assertThat(host.getPontuacao()).isEqualTo(0);
            assertThat(guest.getPontuacao()).isEqualTo(0);
            assertThat(host.isReady()).isTrue();   // host auto-pronto
            assertThat(guest.isReady()).isFalse(); // guest aguarda
            verify(cartaoUtilizadoRepository).deleteBySalaSessaoId(1);
        }
    }

    // ─── totalCartoesDisponiveis ──────────────────────────────────────────────

    @Nested
    @DisplayName("totalCartoesDisponiveis")
    class TotalCartoesDisponiveis {

        @Test
        @DisplayName("conta cartões AMBOS + DE_BOA para modo DE_BOA")
        void deveContarCartoes_DeBoa() {
            when(cartaoConteudoRepository.findByModoRestritoIn(
                    argThat(list -> list.contains(ModoRestrito.AMBOS) && list.contains(ModoRestrito.DE_BOA))))
                    .thenReturn(List.of(buildCartao(1, ModoRestrito.AMBOS, 5), buildCartao(2, ModoRestrito.DE_BOA, 3)));

            assertThat(gameService.totalCartoesDisponiveis(ModoJogo.DE_BOA)).isEqualTo(2);
        }

        @Test
        @DisplayName("conta cartões AMBOS + ESQUENTA para modo ESQUENTA")
        void deveContarCartoes_Esquenta() {
            when(cartaoConteudoRepository.findByModoRestritoIn(
                    argThat(list -> list.contains(ModoRestrito.AMBOS) && list.contains(ModoRestrito.ESQUENTA))))
                    .thenReturn(List.of(buildCartao(10, ModoRestrito.ESQUENTA, 5)));

            assertThat(gameService.totalCartoesDisponiveis(ModoJogo.ESQUENTA)).isEqualTo(1);
        }
    }

    // ─── toggleReady ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("toggleReady")
    class ToggleReady {

        @Test
        @DisplayName("guest alterna isReady de false para true")
        void deveAlternarReady_Guest() {
            SalaSessao sala = buildSala(1, ModoJogo.DE_BOA, StatusSala.LOBBY);
            Jogador guest = buildJogador(2, sala, false);
            guest.setReady(false);

            when(jogadorRepository.findById(2)).thenReturn(Optional.of(guest));
            when(salaSessaoRepository.findById(1)).thenReturn(Optional.of(sala));

            gameService.toggleReady(2);

            assertThat(guest.isReady()).isTrue();
            verify(jogadorRepository).save(guest);
        }

        @Test
        @DisplayName("host não alterna isReady (sempre permanece pronto)")
        void naoDeveAlterar_Host() {
            SalaSessao sala = buildSala(1, ModoJogo.DE_BOA, StatusSala.LOBBY);
            Jogador host = buildJogador(1, sala, true);
            host.setReady(true);

            when(jogadorRepository.findById(1)).thenReturn(Optional.of(host));
            when(salaSessaoRepository.findById(1)).thenReturn(Optional.of(sala));

            gameService.toggleReady(1);

            assertThat(host.isReady()).isTrue(); // não mudou
            verify(jogadorRepository, never()).save(any());
        }
    }

    // ─── entrarSala ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("entrarSala")
    class EntrarSala {

        @Test
        @DisplayName("cria jogador e retorna quando sala está em LOBBY")
        void deveCriarJogador_QuandoLobby() {
            SalaSessao sala = buildSala(1, ModoJogo.DE_BOA, StatusSala.LOBBY);
            when(salaSessaoRepository.findByCodigoAcesso("1234")).thenReturn(Optional.of(sala));
            when(jogadorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Jogador j = gameService.entrarSala("Paulo", "1234");

            assertThat(j.getNickname()).isEqualTo("Paulo");
            assertThat(j.isHost()).isFalse();
            assertThat(j.isReady()).isFalse();
        }

        @Test
        @DisplayName("lança IllegalStateException quando partida já iniciada")
        void deveLancarExcecao_QuandoAtiva() {
            SalaSessao sala = buildSala(1, ModoJogo.DE_BOA, StatusSala.ATIVA);
            when(salaSessaoRepository.findByCodigoAcesso("1234")).thenReturn(Optional.of(sala));

            assertThatThrownBy(() -> gameService.entrarSala("Paulo", "1234"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("iniciada");
        }

        @Test
        @DisplayName("lança IllegalArgumentException quando PIN não encontrado")
        void deveLancarExcecao_QuandoPinInvalido() {
            when(salaSessaoRepository.findByCodigoAcesso("9999")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> gameService.entrarSala("Paulo", "9999"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ─── processarRespostaQuiz ────────────────────────────────────────────────

    @Nested
    @DisplayName("processarRespostaQuiz")
    class ProcessarRespostaQuiz {

        @Test
        @DisplayName("retorna false e atualiza pontos quando não é o jogador do turno")
        void naoEhTurno_deveAtualizarPontosERetornarFalse() {
            SalaSessao sala = buildSala(1, ModoJogo.DE_BOA, StatusSala.ATIVA);
            CartaoConteudo cartao = buildCartao(10, ModoRestrito.DE_BOA, 5);
            Jogador turno   = buildJogador(1, sala, true);
            Jogador outro   = buildJogador(2, sala, false);
            sala.setCartaoAtual(cartao);
            sala.setJogadorTurno(turno);
            sala.getJogador().addAll(List.of(turno, outro));

            when(salaSessaoRepository.findById(1)).thenReturn(Optional.of(sala));
            when(jogadorRepository.findById(2)).thenReturn(Optional.of(outro));
            when(jogadorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            boolean avancou = gameService.processarRespostaQuiz(1, 2, true);

            assertThat(avancou).isFalse();
            assertThat(outro.getPontuacao()).isEqualTo(5);
            // Cartão continua presente (round não avançou)
            assertThat(sala.getCartaoAtual()).isNotNull();
        }

        @Test
        @DisplayName("retorna true e avança rodada quando é o jogador do turno")
        void ehTurno_deveAvancarRodadaERetornarTrue() {
            SalaSessao sala = buildSala(1, ModoJogo.DE_BOA, StatusSala.ATIVA);
            CartaoConteudo cartao  = buildCartao(10, ModoRestrito.DE_BOA, 5);
            CartaoConteudo proximo = buildCartao(11, ModoRestrito.DE_BOA, 5);
            Jogador turno  = buildJogador(1, sala, true);
            Jogador outro  = buildJogador(2, sala, false);
            sala.setCartaoAtual(cartao);
            sala.setJogadorTurno(turno);
            sala.getJogador().addAll(List.of(turno, outro));

            when(salaSessaoRepository.findById(1)).thenReturn(Optional.of(sala));
            when(jogadorRepository.findById(1)).thenReturn(Optional.of(turno));
            when(jogadorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(cartaoUtilizadoRepository.findCartaoIdsBySalaSessaoId(1)).thenReturn(List.of());
            when(cartaoConteudoRepository.findByModoRestritoIn(anyList())).thenReturn(List.of(proximo));
            when(salaSessaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            boolean avancou = gameService.processarRespostaQuiz(1, 1, true);

            assertThat(avancou).isTrue();
            assertThat(turno.getPontuacao()).isEqualTo(5);
            assertThat(sala.getCartaoAtual()).isNull();   // limpo
            assertThat(sala.getRodadaAtual()).isEqualTo(1);
        }

        @Test
        @DisplayName("penaliza pontos em erro (nao abaixo de 0)")
        void erro_devePenalizarPontos() {
            SalaSessao sala = buildSala(1, ModoJogo.DE_BOA, StatusSala.ATIVA);
            CartaoConteudo cartao = buildCartao(10, ModoRestrito.DE_BOA, 5);
            Jogador turno = buildJogador(1, sala, true);
            Jogador outro = buildJogador(2, sala, false);
            outro.setPontuacao(3);
            sala.setCartaoAtual(cartao);
            sala.setJogadorTurno(turno);
            sala.getJogador().addAll(List.of(turno, outro));

            when(salaSessaoRepository.findById(1)).thenReturn(Optional.of(sala));
            when(jogadorRepository.findById(2)).thenReturn(Optional.of(outro));
            when(jogadorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            gameService.processarRespostaQuiz(1, 2, false);

            assertThat(outro.getPontuacao()).isZero(); // 3 - 5 -> max(0, -2) = 0
        }

        @Test
        @DisplayName("retorna false silenciosamente quando cartaoAtual já foi limpo (race condition)")
        void cartaoNulo_deveRetornarFalseSemErro() {
            SalaSessao sala = buildSala(1, ModoJogo.DE_BOA, StatusSala.ATIVA);
            sala.setCartaoAtual(null); // round já avançou

            when(salaSessaoRepository.findById(1)).thenReturn(Optional.of(sala));

            boolean avancou = gameService.processarRespostaQuiz(1, 99, true);

            assertThat(avancou).isFalse();
            verifyNoInteractions(jogadorRepository); // não tocou em nenhum jogador
        }

        @Test
        @DisplayName("encerra jogo quando nao ha mais cartoes apos turno responder")
        void semCartoes_deveFinalizarJogo() {
            SalaSessao sala = buildSala(1, ModoJogo.DE_BOA, StatusSala.ATIVA);
            CartaoConteudo cartao = buildCartao(10, ModoRestrito.DE_BOA, 5);
            Jogador turno = buildJogador(1, sala, true);
            sala.setCartaoAtual(cartao);
            sala.setJogadorTurno(turno);
            sala.getJogador().add(turno);

            when(salaSessaoRepository.findById(1)).thenReturn(Optional.of(sala));
            when(jogadorRepository.findById(1)).thenReturn(Optional.of(turno));
            when(jogadorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(cartaoUtilizadoRepository.findCartaoIdsBySalaSessaoId(1)).thenReturn(List.of(10));
            when(cartaoConteudoRepository.findByModoRestritoIn(anyList())).thenReturn(List.of()); // esgotado
            when(salaSessaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            boolean avancou = gameService.processarRespostaQuiz(1, 1, true);

            assertThat(avancou).isTrue();
            assertThat(sala.getStatus()).isEqualTo(StatusSala.FINALIZADA);
            assertThat(sala.getJogadorTurno()).isNull();
        }
    }
}
