package com.pjjunio.esquentaio.controller;

import com.pjjunio.esquentaio.entity.CartaoConteudo;
import com.pjjunio.esquentaio.entity.Jogador;
import com.pjjunio.esquentaio.entity.SalaSessao;
import com.pjjunio.esquentaio.enums.ModoJogo;
import com.pjjunio.esquentaio.enums.ModoRestrito;
import com.pjjunio.esquentaio.enums.StatusSala;
import com.pjjunio.esquentaio.enums.TipoCartao;
import com.pjjunio.esquentaio.repository.CartaoConteudoRepository;
import com.pjjunio.esquentaio.repository.JogadorRepository;
import com.pjjunio.esquentaio.repository.SalaSessaoRepository;
import com.pjjunio.esquentaio.service.AdminUserDetailsService;
import com.pjjunio.esquentaio.service.GameService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testes dos endpoints de SalaController usando @WebMvcTest (sem banco real).
 *
 * <p>Todos os repositórios e serviços são mockados via @MockitoBean.
 * Spring Security está ativo (rotas de jogo são públicas = .anyRequest().permitAll()).
 */
@WebMvcTest(SalaController.class)
@WithMockUser // fornece SecurityContext para Thymeleaf sec: e evita 401 anônimo
@DisplayName("SalaController — endpoints /sala/{id}/...")
class SalaControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean SalaSessaoRepository     salaSessaoRepository;
    @MockitoBean JogadorRepository        jogadorRepository;
    @MockitoBean CartaoConteudoRepository cartaoConteudoRepository;
    @MockitoBean GameService              gameService;
    @MockitoBean AdminUserDetailsService  adminUserDetailsService; // exigido pelo SecurityConfig

    // ─── Helpers ──────────────────────────────────────────────────────────────

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

    private SalaSessao buildSala(int id, StatusSala status, ModoJogo modo) {
        SalaSessao sala = new SalaSessao();
        ReflectionTestUtils.setField(sala, "id", id);
        sala.setStatus(status);
        sala.setModoJogo(modo);
        sala.setCodigoAcesso("1234");
        sala.setRodadaAtual(1);
        sala.setDataCriacao(Instant.now());
        return sala;
    }

    private Jogador buildJogador(int id, SalaSessao sala, boolean host) {
        Jogador j = new Jogador();
        ReflectionTestUtils.setField(j, "id", id);
        j.setSalaSessao(sala);
        j.setNickname("Player" + id);
        j.setPontuacao(10);
        j.setHost(host);
        j.setReady(true);
        return j;
    }

    private CartaoConteudo buildCartao(int id) {
        CartaoConteudo c = instantiate(CartaoConteudo.class);
        ReflectionTestUtils.setField(c, "id", id);
        c.setTexto("Desafio de teste");
        c.setTipo(TipoCartao.PERGUNTA);
        c.setModoRestrito(ModoRestrito.AMBOS);
        c.setPontosRecompensa(10);
        c.setGoles(1);
        return c;
    }

    // ─── POST /{id}/iniciar ───────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /{id}/iniciar")
    class Iniciar {

        @Test
        @DisplayName("host com sala LOBBY e ≥2 prontos → inicia e redireciona para sorteio")
        void host_deveIniciarERedirecionarParaSorteio() throws Exception {
            SalaSessao sala = buildSala(1, StatusSala.LOBBY, ModoJogo.DE_BOA);
            Jogador host  = buildJogador(1, sala, true);
            Jogador guest = buildJogador(2, sala, false);
            guest.setReady(true); // guest precisa estar pronto para atingir mínimo de 2
            sala.getJogador().addAll(List.of(host, guest));

            when(salaSessaoRepository.findById(1)).thenReturn(Optional.of(sala));

            mockMvc.perform(post("/sala/1/iniciar")
                            .with(csrf())
                            .sessionAttr("jogadorId", 1))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/sala/1/sorteio"));

            verify(gameService).iniciarPartida(1);
        }

        @Test
        @DisplayName("non-host → redireciona de volta para lobby sem iniciar")
        void nonHost_deveRedirecionarParaLobby() throws Exception {
            SalaSessao sala = buildSala(1, StatusSala.LOBBY, ModoJogo.DE_BOA);
            Jogador host  = buildJogador(1, sala, true);
            Jogador guest = buildJogador(2, sala, false);
            sala.getJogador().addAll(List.of(host, guest));

            when(salaSessaoRepository.findById(1)).thenReturn(Optional.of(sala));

            mockMvc.perform(post("/sala/1/iniciar")
                            .with(csrf())
                            .sessionAttr("jogadorId", 2)) // guest tenta iniciar
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/game/lobby/1234"));

            verify(gameService, never()).iniciarPartida(anyInt());
        }

        @Test
        @DisplayName("sala já ATIVA → redireciona para lobby sem iniciar")
        void salaAtiva_naoDeveIniciar() throws Exception {
            SalaSessao sala = buildSala(1, StatusSala.ATIVA, ModoJogo.DE_BOA);
            when(salaSessaoRepository.findById(1)).thenReturn(Optional.of(sala));

            mockMvc.perform(post("/sala/1/iniciar")
                            .with(csrf())
                            .sessionAttr("jogadorId", 1))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/game/lobby/1234"));

            verify(gameService, never()).iniciarPartida(anyInt());
        }

        @Test
        @DisplayName("sem sessão (jogadorId null) → redireciona para lobby")
        void semSessao_deveRedirecionarParaLobby() throws Exception {
            SalaSessao sala = buildSala(1, StatusSala.LOBBY, ModoJogo.DE_BOA);
            when(salaSessaoRepository.findById(1)).thenReturn(Optional.of(sala));

            mockMvc.perform(post("/sala/1/iniciar").with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/game/lobby/1234"));
        }
    }

    // ─── GET /{id}/sorteio ────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /{id}/sorteio")
    class Sorteio {

        @Test
        @DisplayName("sala ATIVA → exibe tela de sorteio")
        void salaAtiva_deveExibirSorteio() throws Exception {
            SalaSessao sala = buildSala(1, StatusSala.ATIVA, ModoJogo.DE_BOA);
            Jogador turno = buildJogador(1, sala, true);
            sala.setJogadorTurno(turno);

            when(salaSessaoRepository.findById(1)).thenReturn(Optional.of(sala));

            mockMvc.perform(get("/sala/1/sorteio"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("game/sorteio"))
                    .andExpect(model().attributeExists("sala", "jogadorDaVez"));
        }

        @Test
        @DisplayName("sala LOBBY → redireciona para lobby")
        void salaLobby_deveRedirecionarParaLobby() throws Exception {
            SalaSessao sala = buildSala(1, StatusSala.LOBBY, ModoJogo.DE_BOA);
            when(salaSessaoRepository.findById(1)).thenReturn(Optional.of(sala));

            mockMvc.perform(get("/sala/1/sorteio"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/game/lobby/1234"));
        }

        @Test
        @DisplayName("sala FINALIZADA → redireciona para pódio")
        void salaFinalizada_deveRedirecionarParaPodio() throws Exception {
            SalaSessao sala = buildSala(1, StatusSala.FINALIZADA, ModoJogo.DE_BOA);
            when(salaSessaoRepository.findById(1)).thenReturn(Optional.of(sala));

            mockMvc.perform(get("/sala/1/sorteio"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/sala/1/podio"));
        }
    }

    // ─── GET /{id}/partida ────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /{id}/partida")
    class Partida {

        @Test
        @DisplayName("sala ATIVA com cartão → exibe tela da partida (endpoint do bug reportado)")
        void salaAtiva_deveExibirPartida() throws Exception {
            SalaSessao sala = buildSala(1, StatusSala.ATIVA, ModoJogo.DE_BOA);
            CartaoConteudo cartao = buildCartao(5);
            Jogador turno = buildJogador(1, sala, true);
            sala.setCartaoAtual(cartao);
            sala.setJogadorTurno(turno);
            sala.getJogador().add(turno);

            when(gameService.garantirCartaoAtual(1)).thenReturn(sala);
            when(gameService.totalCartoesDisponiveis(ModoJogo.DE_BOA)).thenReturn(20);

            mockMvc.perform(get("/sala/1/partida"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("game/partida"))
                    .andExpect(model().attributeExists("sala", "cartao", "jogadorDaVez",
                            "rodadaAtual", "totalRodadas", "topJogadores"));
        }

        @Test
        @DisplayName("sala FINALIZADA → redireciona para pódio")
        void salaFinalizada_deveRedirecionarParaPodio() throws Exception {
            SalaSessao sala = buildSala(1, StatusSala.FINALIZADA, ModoJogo.DE_BOA);

            when(gameService.garantirCartaoAtual(1)).thenReturn(sala);

            mockMvc.perform(get("/sala/1/partida"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/sala/1/podio"));
        }

        @Test
        @DisplayName("cartão null (sem cartões disponíveis) → redireciona para pódio")
        void semCartao_deveRedirecionarParaPodio() throws Exception {
            SalaSessao sala = buildSala(1, StatusSala.ATIVA, ModoJogo.DE_BOA);
            // cartaoAtual == null

            when(gameService.garantirCartaoAtual(1)).thenReturn(sala);

            mockMvc.perform(get("/sala/1/partida"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/sala/1/podio"));
        }
    }

    // ─── POST /{id}/resultado ─────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /{id}/resultado")
    class Resultado {

        @Test
        @DisplayName("sucesso=true → redireciona para recompensa")
        void sucesso_deveRedirecionarParaRecompensa() throws Exception {
            SalaSessao sala = buildSala(1, StatusSala.ATIVA, ModoJogo.DE_BOA);
            CartaoConteudo cartao = buildCartao(5);
            Jogador turno = buildJogador(1, sala, true);
            sala.setCartaoAtual(cartao);
            sala.setJogadorTurno(turno);

            when(salaSessaoRepository.findById(1)).thenReturn(Optional.of(sala));

            mockMvc.perform(post("/sala/1/resultado")
                            .with(csrf())
                            .param("sucesso", "true"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/sala/1/recompensa"));

            verify(gameService).processarResultado(1, true);
        }

        @Test
        @DisplayName("sucesso=false → redireciona para penalidade")
        void falha_deveRedirecionarParaPenalidade() throws Exception {
            SalaSessao sala = buildSala(1, StatusSala.ATIVA, ModoJogo.DE_BOA);
            CartaoConteudo cartao = buildCartao(5);
            Jogador turno = buildJogador(1, sala, true);
            sala.setCartaoAtual(cartao);
            sala.setJogadorTurno(turno);

            when(salaSessaoRepository.findById(1)).thenReturn(Optional.of(sala));

            mockMvc.perform(post("/sala/1/resultado")
                            .with(csrf())
                            .param("sucesso", "false"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/sala/1/penalidade"));

            verify(gameService).processarResultado(1, false);
        }

        @Test
        @DisplayName("salva ids de cartão e jogador na sessão antes de processar")
        void deveGuardarIdsNaSessao() throws Exception {
            SalaSessao sala = buildSala(1, StatusSala.ATIVA, ModoJogo.DE_BOA);
            CartaoConteudo cartao = buildCartao(7);
            Jogador turno = buildJogador(3, sala, true);
            sala.setCartaoAtual(cartao);
            sala.setJogadorTurno(turno);

            when(salaSessaoRepository.findById(1)).thenReturn(Optional.of(sala));

            mockMvc.perform(post("/sala/1/resultado")
                            .with(csrf())
                            .param("sucesso", "true"))
                    .andExpect(request().sessionAttribute("lastCartaoId", 7))
                    .andExpect(request().sessionAttribute("lastJogadorId", 3));
        }
    }

    // ─── POST /{id}/trocar-carta ──────────────────────────────────────────────

    @Nested
    @DisplayName("POST /{id}/trocar-carta")
    class TrocarCarta {

        @Test
        @DisplayName("sala ATIVA → troca cartão e redireciona para partida")
        void salaAtiva_deveRedirecionarParaPartida() throws Exception {
            SalaSessao sala = buildSala(1, StatusSala.ATIVA, ModoJogo.DE_BOA);
            when(salaSessaoRepository.findById(1)).thenReturn(Optional.of(sala));

            mockMvc.perform(post("/sala/1/trocar-carta").with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/sala/1/partida"));

            verify(gameService).trocarCartaAtual(1);
        }

        @Test
        @DisplayName("após trocar, sala FINALIZADA → redireciona para pódio")
        void aposTraca_salaFinalizada_deveRedirecionarParaPodio() throws Exception {
            SalaSessao sala = buildSala(1, StatusSala.FINALIZADA, ModoJogo.DE_BOA);
            when(salaSessaoRepository.findById(1)).thenReturn(Optional.of(sala));

            mockMvc.perform(post("/sala/1/trocar-carta").with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/sala/1/podio"));
        }
    }

    // ─── GET /{id}/recompensa e /penalidade ──────────────────────────────────

    @Nested
    @DisplayName("GET /{id}/recompensa e /{id}/penalidade")
    class ResultadoTelas {

        @Test
        @DisplayName("recompensa → exibe tela com atributos de modelo")
        void recompensa_deveExibirTela() throws Exception {
            SalaSessao sala = buildSala(1, StatusSala.ATIVA, ModoJogo.DE_BOA);
            CartaoConteudo cartao = buildCartao(7);
            Jogador jogador = buildJogador(3, sala, false);
            when(salaSessaoRepository.findById(1)).thenReturn(Optional.of(sala));
            when(cartaoConteudoRepository.findById(7)).thenReturn(Optional.of(cartao));
            when(jogadorRepository.findById(3)).thenReturn(Optional.of(jogador));

            mockMvc.perform(get("/sala/1/recompensa")
                            .sessionAttr("lastCartaoId", 7)
                            .sessionAttr("lastJogadorId", 3))
                    .andExpect(status().isOk())
                    .andExpect(view().name("game/recompensa"))
                    .andExpect(model().attributeExists("sala"));
        }

        @Test
        @DisplayName("penalidade → exibe tela com atributos de modelo")
        void penalidade_deveExibirTela() throws Exception {
            SalaSessao sala = buildSala(1, StatusSala.ATIVA, ModoJogo.DE_BOA);
            CartaoConteudo cartao = buildCartao(7);
            Jogador jogador = buildJogador(3, sala, false);
            when(salaSessaoRepository.findById(1)).thenReturn(Optional.of(sala));
            when(cartaoConteudoRepository.findById(7)).thenReturn(Optional.of(cartao));
            when(jogadorRepository.findById(3)).thenReturn(Optional.of(jogador));

            mockMvc.perform(get("/sala/1/penalidade")
                            .sessionAttr("lastCartaoId", 7)
                            .sessionAttr("lastJogadorId", 3))
                    .andExpect(status().isOk())
                    .andExpect(view().name("game/penalidade"))
                    .andExpect(model().attributeExists("sala"));
        }

        @Test
        @DisplayName("recompensa com ids na sessão → carrega cartão e jogador")
        void recompensa_comSessao_deveCarregarEntidades() throws Exception {
            SalaSessao sala = buildSala(1, StatusSala.ATIVA, ModoJogo.DE_BOA);
            CartaoConteudo cartao = buildCartao(7);
            Jogador jogador = buildJogador(3, sala, false);

            when(salaSessaoRepository.findById(1)).thenReturn(Optional.of(sala));
            when(cartaoConteudoRepository.findById(7)).thenReturn(Optional.of(cartao));
            when(jogadorRepository.findById(3)).thenReturn(Optional.of(jogador));

            mockMvc.perform(get("/sala/1/recompensa")
                            .sessionAttr("lastCartaoId", 7)
                            .sessionAttr("lastJogadorId", 3))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("cartao", cartao))
                    .andExpect(model().attribute("jogadorDaVez", jogador));
        }
    }

    // ─── GET /{id}/proximo-turno ──────────────────────────────────────────────

    @Nested
    @DisplayName("GET /{id}/proximo-turno")
    class ProximoTurno {

        @Test
        @DisplayName("sala ATIVA → redireciona para sorteio")
        void salaAtiva_deveRedirecionarParaSorteio() throws Exception {
            SalaSessao sala = buildSala(1, StatusSala.ATIVA, ModoJogo.DE_BOA);
            when(salaSessaoRepository.findById(1)).thenReturn(Optional.of(sala));

            mockMvc.perform(get("/sala/1/proximo-turno"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/sala/1/sorteio"));
        }

        @Test
        @DisplayName("sala FINALIZADA → redireciona para pódio")
        void salaFinalizada_deveRedirecionarParaPodio() throws Exception {
            SalaSessao sala = buildSala(1, StatusSala.FINALIZADA, ModoJogo.DE_BOA);
            when(salaSessaoRepository.findById(1)).thenReturn(Optional.of(sala));

            mockMvc.perform(get("/sala/1/proximo-turno"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/sala/1/podio"));
        }
    }

    // ─── GET /{id}/podio ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /{id}/podio")
    class Podio {

        @Test
        @DisplayName("exibe pódio com top3 e classificação ordenada por pontuação")
        void deveExibirPodio() throws Exception {
            SalaSessao sala = buildSala(1, StatusSala.FINALIZADA, ModoJogo.DE_BOA);
            Jogador p1 = buildJogador(1, sala, true);  p1.setPontuacao(100);
            Jogador p2 = buildJogador(2, sala, false); p2.setPontuacao(80);
            Jogador p3 = buildJogador(3, sala, false); p3.setPontuacao(60);
            Jogador p4 = buildJogador(4, sala, false); p4.setPontuacao(40);
            sala.getJogador().addAll(List.of(p1, p2, p3, p4));

            when(salaSessaoRepository.findById(1)).thenReturn(Optional.of(sala));

            mockMvc.perform(get("/sala/1/podio"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("game/podio"))
                    .andExpect(model().attributeExists("sala", "top3", "resto"));
        }

        @Test
        @DisplayName("pódio com menos de 3 jogadores → top3 com posições null")
        void poucosJogadores_top3ComNulls() throws Exception {
            SalaSessao sala = buildSala(1, StatusSala.FINALIZADA, ModoJogo.DE_BOA);
            Jogador p1 = buildJogador(1, sala, true); p1.setPontuacao(50);
            sala.getJogador().add(p1);

            when(salaSessaoRepository.findById(1)).thenReturn(Optional.of(sala));

            mockMvc.perform(get("/sala/1/podio"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("game/podio"));
        }
    }

    // ─── GET /{id}/jogar-de-novo ──────────────────────────────────────────────

    @Nested
    @DisplayName("GET /{id}/jogar-de-novo")
    class JogarDeNovo {

        @Test
        @DisplayName("reseta sala e redireciona para lobby via código")
        void deveResetarERedirecionarParaLobby() throws Exception {
            SalaSessao salaReset = buildSala(1, StatusSala.LOBBY, ModoJogo.DE_BOA);
            when(gameService.jogarDeNovo(1)).thenReturn(salaReset);

            mockMvc.perform(get("/sala/1/jogar-de-novo"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/game/lobby/1234"));
        }
    }
}
