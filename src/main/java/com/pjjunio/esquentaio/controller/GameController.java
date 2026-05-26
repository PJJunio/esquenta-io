package com.pjjunio.esquentaio.controller;

import com.pjjunio.esquentaio.entity.Jogador;
import com.pjjunio.esquentaio.entity.SalaSessao;
import com.pjjunio.esquentaio.enums.StatusSala;
import com.pjjunio.esquentaio.repository.JogadorRepository;
import com.pjjunio.esquentaio.repository.SalaSessaoRepository;
import com.pjjunio.esquentaio.service.GameService;
import jakarta.servlet.http.HttpSession;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/game")
public class GameController {

    private final GameService             gameService;
    private final SalaSessaoRepository    salaSessaoRepository;
    private final JogadorRepository       jogadorRepository;
    private final SimpMessagingTemplate   messagingTemplate;

    public GameController(GameService gameService,
                          SalaSessaoRepository salaSessaoRepository,
                          JogadorRepository jogadorRepository,
                          SimpMessagingTemplate messagingTemplate) {
        this.gameService          = gameService;
        this.salaSessaoRepository = salaSessaoRepository;
        this.jogadorRepository    = jogadorRepository;
        this.messagingTemplate    = messagingTemplate;
    }

    // Escolha do modo

    @GetMapping("/criar-sala")
    public String escolherModo() {
        return "game/criar-sala";
    }

    // Confirmação +18 (só Esquenta)

    @GetMapping("/criar-sala/esquenta-confirm")
    public String confirmar18() {
        return "game/confirm-18";
    }

    // Criar sala De Boa

    @GetMapping("/criar-sala/de-boa")
    public String setupNomeDeBoa(Model model) {
        model.addAttribute("modo", "DE_BOA");
        return "game/setup-nome";
    }

    @PostMapping("/criar-sala/de-boa")
    public String createDeBoa(@RequestParam String nickname, Model model, HttpSession session) {
        String erro = validarNick(nickname);
        if (erro != null) {
            model.addAttribute("modo", "DE_BOA");
            model.addAttribute("nickname", nickname);
            model.addAttribute("errorNick", erro);
            return "game/setup-nome";
        }
        SalaSessao sala = gameService.createDeBoa(nickname);
        session.setAttribute("jogadorId", sala.getJogador().get(0).getId());
        return "redirect:/game/lobby/" + sala.getCodigoAcesso();
    }

    // Criar sala Esquenta

    @GetMapping("/criar-sala/esquenta")
    public String setupNomeEsquenta(Model model) {
        model.addAttribute("modo", "ESQUENTA");
        return "game/setup-nome";
    }

    @PostMapping("/criar-sala/esquenta")
    public String createEsquenta(@RequestParam String nickname, Model model, HttpSession session) {
        String erro = validarNick(nickname);
        if (erro != null) {
            model.addAttribute("modo", "ESQUENTA");
            model.addAttribute("nickname", nickname);
            model.addAttribute("errorNick", erro);
            return "game/setup-nome";
        }
        SalaSessao sala = gameService.createEsquenta(nickname);
        session.setAttribute("jogadorId", sala.getJogador().get(0).getId());
        return "redirect:/game/lobby/" + sala.getCodigoAcesso();
    }

    // Entrar na sala via PIN

    @GetMapping("/entrar")
    public String entrarForm() {
        return "game/entrar";
    }

    @PostMapping("/entrar")
    public String entrarSala(
            @RequestParam String nickname,
            @RequestParam String pin1,
            @RequestParam String pin2,
            @RequestParam String pin3,
            @RequestParam String pin4,
            Model model,
            HttpSession session) {

        String codigo = pin1 + pin2 + pin3 + pin4;

        String erro = validarNick(nickname);
        if (erro != null) {
            model.addAttribute("nickname", nickname);
            model.addAttribute("errorNick", erro);
            return "game/entrar";
        }

        // Idempotência: se o jogador já está nesta sala (sessão ainda válida),
        // redireciona direto sem criar duplicata
        Integer jogadorIdExistente = (Integer) session.getAttribute("jogadorId");
        if (jogadorIdExistente != null) {
            Jogador existente = jogadorRepository.findById(jogadorIdExistente).orElse(null);
            if (existente != null
                    && existente.getSalaSessao().getCodigoAcesso().equals(codigo)) {
                return "redirect:/game/lobby/" + codigo;
            }
        }

        try {
            Jogador jogador = gameService.entrarSala(nickname, codigo);
            session.setAttribute("jogadorId", jogador.getId());

            // Notifica os demais jogadores do lobby via WebSocket
            messagingTemplate.convertAndSend("/topic/lobby/" + codigo, "update");

            return "redirect:/game/lobby/" + codigo;
        } catch (IllegalArgumentException e) {
            model.addAttribute("nickname", nickname);
            model.addAttribute("errorPin", true);
            return "game/entrar";
        } catch (IllegalStateException e) {
            model.addAttribute("nickname", nickname);
            model.addAttribute("errorMsg", e.getMessage());
            return "game/entrar";
        }
    }

    // Lobby

    // Sair da sala (remove o jogador do banco, fecha se era o host)

    @PostMapping("/lobby/{codigo}/sair")
    public String sairSala(@PathVariable String codigo, HttpSession session) {
        Integer jogadorId = (Integer) session.getAttribute("jogadorId");
        session.removeAttribute("jogadorId");

        if (jogadorId != null) {
            GameService.SairSalaResult resultado = gameService.sairSala(jogadorId);
            if (!resultado.codigoAcesso().isBlank()) {
                String msg = resultado.salaClosed()
                        ? "{\"type\":\"sala-encerrada\"}"
                        : "{\"type\":\"update\"}";
                messagingTemplate.convertAndSend("/topic/lobby/" + resultado.codigoAcesso(), msg);
            }
        }

        return "redirect:/";
    }

    @GetMapping("/lobby/{codigo}")
    public String lobby(@PathVariable String codigo, Model model, HttpSession session) {
        SalaSessao sala = salaSessaoRepository.findByCodigoAcesso(codigo)
                .orElseThrow(() -> new IllegalArgumentException("Sala não encontrada: " + codigo));

        // Sala encerrada (host saiu): manda pra home
        if (sala.getStatus() == StatusSala.FINALIZADA) {
            session.removeAttribute("jogadorId");
            return "redirect:/";
        }

        Integer jogadorId = (Integer) session.getAttribute("jogadorId");

        // Verifica se o jogador da sessão ainda pertence a esta sala
        if (jogadorId != null) {
            boolean aindaNaSala = sala.getJogador().stream()
                    .anyMatch(j -> j.getId() == (int) jogadorId);
            if (!aindaNaSala) {
                session.removeAttribute("jogadorId");
                return "redirect:/";
            }
        }

        boolean isHost = jogadorId != null && sala.getJogador().stream()
                .anyMatch(j -> j.getId() == (int) jogadorId && j.isHost());

        long jogadoresProntos = sala.getJogador().stream()
                .filter(Jogador::isReady)
                .count();

        model.addAttribute("sala", sala);
        model.addAttribute("jogadores", sala.getJogador());
        model.addAttribute("jogadoresProntos", jogadoresProntos);
        model.addAttribute("isHost", isHost);
        return "game/lobby";
    }

    // Remover jogador da sala (host only) — responde 204 para chamada AJAX

    @PostMapping("/lobby/{codigo}/remover/{jogadorId}")
    @ResponseBody
    public org.springframework.http.ResponseEntity<Void> removerJogador(
            @PathVariable String codigo,
            @PathVariable int jogadorId,
            HttpSession session) {

        Integer hostId = (Integer) session.getAttribute("jogadorId");
        if (hostId == null) return org.springframework.http.ResponseEntity.status(403).build();

        // Verifica se quem solicita é o host
        SalaSessao sala = salaSessaoRepository.findByCodigoAcesso(codigo).orElse(null);
        if (sala == null) return org.springframework.http.ResponseEntity.notFound().build();

        boolean isHost = sala.getJogador().stream()
                .anyMatch(j -> j.getId() == (int) hostId && j.isHost());
        if (!isHost) return org.springframework.http.ResponseEntity.status(403).build();

        // Não permite o host remover a si mesmo
        if (jogadorId == (int) hostId) return org.springframework.http.ResponseEntity.badRequest().build();

        gameService.sairSala(jogadorId);

        // Notifica: jogador removido vai para home, os demais recarregam a lista
        messagingTemplate.convertAndSend("/topic/lobby/" + codigo,
                "{\"type\":\"kicked\",\"jogadorId\":" + jogadorId + "}");

        return org.springframework.http.ResponseEntity.noContent().build();
    }

    // Marcar pronto

    @PostMapping("/lobby/{codigo}/ready")
    public String toggleReady(@PathVariable String codigo, HttpSession session) {
        Integer jogadorId = (Integer) session.getAttribute("jogadorId");
        if (jogadorId != null) {
            gameService.toggleReady(jogadorId);
            // Notifica o lobby em tempo real
            messagingTemplate.convertAndSend("/topic/lobby/" + codigo, "update");
        }
        return "redirect:/game/lobby/" + codigo;
    }

    // Validações

    private String validarNick(String nickname) {
        if (nickname == null || nickname.isBlank()) return "Nickname não pode ser vazio.";
        if (nickname.trim().length() < 2)           return "Mínimo 2 caracteres.";
        if (nickname.trim().length() > 16)          return "Máximo 16 caracteres.";
        return null;
    }
}
