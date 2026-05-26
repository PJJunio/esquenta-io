package com.pjjunio.esquentaio.controller;

import com.pjjunio.esquentaio.entity.Jogador;
import com.pjjunio.esquentaio.entity.SalaSessao;
import com.pjjunio.esquentaio.enums.ModoJogo;
import com.pjjunio.esquentaio.enums.StatusSala;
import com.pjjunio.esquentaio.repository.SalaSessaoRepository;
import com.pjjunio.esquentaio.service.AdminUserDetailsService;
import com.pjjunio.esquentaio.service.GameService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testes dos endpoints de GameController usando @WebMvcTest (sem banco real).
 *
 * <p>Cobre: criar sala (DE_BOA e ESQUENTA), entrar via PIN, lobby e ready.
 */
@WebMvcTest(GameController.class)
@WithMockUser
@DisplayName("GameController — endpoints /game/...")
class GameControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean GameService             gameService;
    @MockitoBean SalaSessaoRepository    salaSessaoRepository;
    @MockitoBean SimpMessagingTemplate   messagingTemplate;
    @MockitoBean AdminUserDetailsService adminUserDetailsService;

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private SalaSessao buildSala(int id, StatusSala status, ModoJogo modo) {
        SalaSessao sala = new SalaSessao();
        ReflectionTestUtils.setField(sala, "id", id);
        sala.setStatus(status);
        sala.setModoJogo(modo);
        sala.setCodigoAcesso("5678");
        sala.setDataCriacao(Instant.now());
        return sala;
    }

    private Jogador buildJogador(int id, SalaSessao sala, boolean host) {
        Jogador j = new Jogador();
        ReflectionTestUtils.setField(j, "id", id);
        j.setSalaSessao(sala);
        j.setNickname("Player" + id);
        j.setPontuacao(0);
        j.setHost(host);
        j.setReady(host);
        return j;
    }

    // ─── GET /game/criar-sala ─────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /game/criar-sala")
    class CriarSalaPage {

        @Test
        @DisplayName("exibe página de escolha de modo")
        void deveExibirPaginaEscolha() throws Exception {
            mockMvc.perform(get("/game/criar-sala"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("game/criar-sala"));
        }
    }

    // ─── GET /game/criar-sala/esquenta-confirm ────────────────────────────────

    @Test
    @DisplayName("GET /game/criar-sala/esquenta-confirm → exibe confirmação +18")
    void deveExibirConfirmacao18() throws Exception {
        mockMvc.perform(get("/game/criar-sala/esquenta-confirm"))
                .andExpect(status().isOk())
                .andExpect(view().name("game/confirm-18"));
    }

    // ─── GET + POST /game/criar-sala/de-boa ──────────────────────────────────

    @Nested
    @DisplayName("Criar Sala De Boa")
    class CriarSalaDeBoa {

        @Test
        @DisplayName("GET → exibe formulário com modo DE_BOA")
        void get_deveExibirFormulario() throws Exception {
            mockMvc.perform(get("/game/criar-sala/de-boa"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("game/setup-nome"))
                    .andExpect(model().attribute("modo", "DE_BOA"));
        }

        @Test
        @DisplayName("POST com nickname válido → cria sala e redireciona para lobby")
        void post_nicknameValido_deveCriarSalaERedirecionarLobby() throws Exception {
            SalaSessao sala = buildSala(1, StatusSala.LOBBY, ModoJogo.DE_BOA);
            Jogador host = buildJogador(1, sala, true);
            sala.getJogador().add(host);

            when(gameService.createDeBoa("Paulo")).thenReturn(sala);

            mockMvc.perform(post("/game/criar-sala/de-boa")
                            .with(csrf())
                            .param("nickname", "Paulo"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/game/lobby/5678"))
                    .andExpect(request().sessionAttribute("jogadorId", 1));
        }

        @Test
        @DisplayName("POST com nickname vazio → volta ao formulário com erro")
        void post_nicknameVazio_deveExibirErro() throws Exception {
            mockMvc.perform(post("/game/criar-sala/de-boa")
                            .with(csrf())
                            .param("nickname", ""))
                    .andExpect(status().isOk())
                    .andExpect(view().name("game/setup-nome"))
                    .andExpect(model().attributeExists("errorNick"));

            verify(gameService, never()).createDeBoa(any());
        }

        @Test
        @DisplayName("POST com nickname muito curto (1 char) → volta ao formulário com erro")
        void post_nicknameMuitoCurto_deveExibirErro() throws Exception {
            mockMvc.perform(post("/game/criar-sala/de-boa")
                            .with(csrf())
                            .param("nickname", "A"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("game/setup-nome"))
                    .andExpect(model().attributeExists("errorNick"));
        }

        @Test
        @DisplayName("POST com nickname muito longo (>16 chars) → volta ao formulário com erro")
        void post_nicknameMuitoLongo_deveExibirErro() throws Exception {
            mockMvc.perform(post("/game/criar-sala/de-boa")
                            .with(csrf())
                            .param("nickname", "NomeExcessivamenteL"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("game/setup-nome"))
                    .andExpect(model().attributeExists("errorNick"));
        }
    }

    // ─── GET + POST /game/criar-sala/esquenta ────────────────────────────────

    @Nested
    @DisplayName("Criar Sala Esquenta")
    class CriarSalaEsquenta {

        @Test
        @DisplayName("GET → exibe formulário com modo ESQUENTA")
        void get_deveExibirFormulario() throws Exception {
            mockMvc.perform(get("/game/criar-sala/esquenta"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("game/setup-nome"))
                    .andExpect(model().attribute("modo", "ESQUENTA"));
        }

        @Test
        @DisplayName("POST com nickname válido → cria sala Esquenta e redireciona para lobby")
        void post_nicknameValido_deveCriarSalaEsquenta() throws Exception {
            SalaSessao sala = buildSala(2, StatusSala.LOBBY, ModoJogo.ESQUENTA);
            Jogador host = buildJogador(1, sala, true);
            sala.getJogador().add(host);

            when(gameService.createEsquenta("Maria")).thenReturn(sala);

            mockMvc.perform(post("/game/criar-sala/esquenta")
                            .with(csrf())
                            .param("nickname", "Maria"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/game/lobby/5678"));

            verify(gameService).createEsquenta("Maria");
        }
    }

    // ─── GET /game/entrar ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Entrar na Sala via PIN")
    class EntrarSala {

        @Test
        @DisplayName("GET → exibe formulário de entrada")
        void get_deveExibirFormulario() throws Exception {
            mockMvc.perform(get("/game/entrar"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("game/entrar"));
        }

        @Test
        @DisplayName("POST com PIN e nickname válidos → redireciona para lobby")
        void post_dadosValidos_deveRedirecionarParaLobby() throws Exception {
            SalaSessao sala = buildSala(1, StatusSala.LOBBY, ModoJogo.DE_BOA);
            Jogador guest = buildJogador(2, sala, false);

            when(gameService.entrarSala("Pedro", "5678")).thenReturn(guest);

            mockMvc.perform(post("/game/entrar")
                            .with(csrf())
                            .param("nickname", "Pedro")
                            .param("pin1", "5")
                            .param("pin2", "6")
                            .param("pin3", "7")
                            .param("pin4", "8"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/game/lobby/5678"))
                    .andExpect(request().sessionAttribute("jogadorId", 2));

            verify(messagingTemplate).convertAndSend(eq("/topic/lobby/5678"), eq("update"));
        }

        @Test
        @DisplayName("POST com PIN inválido (sala não encontrada) → exibe erro de PIN")
        void post_pinInvalido_deveExibirErroPIN() throws Exception {
            when(gameService.entrarSala(anyString(), anyString()))
                    .thenThrow(new IllegalArgumentException("Sala não encontrada"));

            mockMvc.perform(post("/game/entrar")
                            .with(csrf())
                            .param("nickname", "Pedro")
                            .param("pin1", "9")
                            .param("pin2", "9")
                            .param("pin3", "9")
                            .param("pin4", "9"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("game/entrar"))
                    .andExpect(model().attribute("errorPin", true));
        }

        @Test
        @DisplayName("POST com partida já iniciada → exibe mensagem de erro")
        void post_partidaJaIniciada_deveExibirErroMsg() throws Exception {
            when(gameService.entrarSala(anyString(), anyString()))
                    .thenThrow(new IllegalStateException("Partida já iniciada."));

            mockMvc.perform(post("/game/entrar")
                            .with(csrf())
                            .param("nickname", "Pedro")
                            .param("pin1", "5")
                            .param("pin2", "6")
                            .param("pin3", "7")
                            .param("pin4", "8"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("game/entrar"))
                    .andExpect(model().attributeExists("errorMsg"));
        }

        @Test
        @DisplayName("POST com nickname inválido (espaço em branco) → exibe erro de nick")
        void post_nicknameInvalido_deveExibirErroNick() throws Exception {
            mockMvc.perform(post("/game/entrar")
                            .with(csrf())
                            .param("nickname", "  ")
                            .param("pin1", "5").param("pin2", "6")
                            .param("pin3", "7").param("pin4", "8"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("game/entrar"))
                    .andExpect(model().attributeExists("errorNick"));

            verify(gameService, never()).entrarSala(any(), any());
        }
    }

    // ─── GET /game/lobby/{codigo} ─────────────────────────────────────────────

    @Nested
    @DisplayName("GET /game/lobby/{codigo}")
    class Lobby {

        @Test
        @DisplayName("exibe lobby com jogadores, contagem de prontos e flag de host")
        void deveExibirLobby() throws Exception {
            SalaSessao sala = buildSala(1, StatusSala.LOBBY, ModoJogo.DE_BOA);
            Jogador host  = buildJogador(1, sala, true);
            Jogador guest = buildJogador(2, sala, false);
            guest.setReady(true);
            sala.getJogador().addAll(List.of(host, guest));

            when(salaSessaoRepository.findByCodigoAcesso("5678")).thenReturn(Optional.of(sala));

            mockMvc.perform(get("/game/lobby/5678")
                            .sessionAttr("jogadorId", 1)) // sessão do host
                    .andExpect(status().isOk())
                    .andExpect(view().name("game/lobby"))
                    .andExpect(model().attribute("isHost", true))
                    .andExpect(model().attribute("jogadoresProntos", 2L)) // host + guest prontos
                    .andExpect(model().attributeExists("sala", "jogadores"));
        }

        @Test
        @DisplayName("guest vê lobby com isHost=false")
        void guest_deveVerLobbyComoNaoHost() throws Exception {
            SalaSessao sala = buildSala(1, StatusSala.LOBBY, ModoJogo.DE_BOA);
            Jogador host  = buildJogador(1, sala, true);
            Jogador guest = buildJogador(2, sala, false);
            sala.getJogador().addAll(List.of(host, guest));

            when(salaSessaoRepository.findByCodigoAcesso("5678")).thenReturn(Optional.of(sala));

            mockMvc.perform(get("/game/lobby/5678")
                            .sessionAttr("jogadorId", 2)) // sessão do guest
                    .andExpect(model().attribute("isHost", false));
        }

        @Test
        @DisplayName("código inexistente → lança IllegalArgumentException não tratada")
        void codigoInexistente_deveLancarExcecao() {
            when(salaSessaoRepository.findByCodigoAcesso("9999")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> mockMvc.perform(get("/game/lobby/9999")))
                    .hasCauseInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("9999");
        }
    }

    // ─── POST /game/lobby/{codigo}/ready ─────────────────────────────────────

    @Nested
    @DisplayName("POST /game/lobby/{codigo}/ready")
    class ToggleReady {

        @Test
        @DisplayName("jogador com sessão → alterna ready e redireciona para lobby")
        void comSessao_deveAlternarERedirecionarLobby() throws Exception {
            mockMvc.perform(post("/game/lobby/5678/ready")
                            .with(csrf())
                            .sessionAttr("jogadorId", 2))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/game/lobby/5678"));

            verify(gameService).toggleReady(2);
            verify(messagingTemplate).convertAndSend(eq("/topic/lobby/5678"), eq("update"));
        }

        @Test
        @DisplayName("sem jogadorId na sessão → apenas redireciona sem chamar toggleReady")
        void semSessao_deveRedirecionarSemToggle() throws Exception {
            mockMvc.perform(post("/game/lobby/5678/ready").with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/game/lobby/5678"));

            verify(gameService, never()).toggleReady(anyInt());
        }
    }
}
