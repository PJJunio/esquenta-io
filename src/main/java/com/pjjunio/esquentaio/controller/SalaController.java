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
import com.pjjunio.esquentaio.service.GameService;
import jakarta.servlet.http.HttpSession;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gerencia todas as rotas de uma partida em andamento (/sala/{id}/...).
 * O identificador usado é o ID numérico da sala (não o PIN de 4 dígitos,
 * que fica apenas para entrada no lobby).
 */
@Controller
@RequestMapping("/sala")
public class SalaController {

    private final SalaSessaoRepository     salaSessaoRepository;
    private final JogadorRepository        jogadorRepository;
    private final CartaoConteudoRepository cartaoConteudoRepository;
    private final GameService              gameService;
    private final SimpMessagingTemplate    messagingTemplate;

    public SalaController(SalaSessaoRepository salaSessaoRepository,
                          JogadorRepository jogadorRepository,
                          CartaoConteudoRepository cartaoConteudoRepository,
                          GameService gameService,
                          SimpMessagingTemplate messagingTemplate) {
        this.salaSessaoRepository     = salaSessaoRepository;
        this.jogadorRepository        = jogadorRepository;
        this.cartaoConteudoRepository = cartaoConteudoRepository;
        this.gameService              = gameService;
        this.messagingTemplate        = messagingTemplate;
    }

    // Iniciar partida (host)

    @PostMapping("/{id}/iniciar")
    public String iniciar(@PathVariable int id, HttpSession session,
                          @RequestParam(required = false) Integer maxRodadas,
                          RedirectAttributes redirectAttributes) {
        Integer jogadorId = (Integer) session.getAttribute("jogadorId");
        SalaSessao sala   = salaSessaoRepository.findById(id).orElseThrow();

        // Só o host pode iniciar e a sala deve estar em LOBBY
        if (jogadorId == null || sala.getStatus() != StatusSala.LOBBY) {
            return "redirect:/game/lobby/" + sala.getCodigoAcesso();
        }

        boolean isHost = sala.getJogador().stream()
                .anyMatch(j -> j.getId() == (int) jogadorId && j.isHost());
        if (!isHost) {
            return "redirect:/game/lobby/" + sala.getCodigoAcesso();
        }

        long prontos = sala.getJogador().stream().filter(Jogador::isReady).count();
        if (prontos < 2) {
            return "redirect:/game/lobby/" + sala.getCodigoAcesso();
        }

        // Verifica se há cartões suficientes para o modo da sala
        String erroCartoes = gameService.validarCartoesSuficientes(sala.getModoJogo());
        if (erroCartoes != null) {
            redirectAttributes.addFlashAttribute("erroCartoes", erroCartoes);
            return "redirect:/game/lobby/" + sala.getCodigoAcesso();
        }

        gameService.iniciarPartida(id, maxRodadas);

        // Notifica todos os jogadores do lobby que o jogo iniciou
        messagingTemplate.convertAndSend(
                "/topic/lobby/" + sala.getCodigoAcesso(),
                "{\"type\":\"game-started\",\"salaId\":" + id + "}");

        return "redirect:/sala/" + id + "/sorteio";
    }

    // Tela "Verdade ou Desafio?" (só modo ESQUENTA, antes de revelar o cartão)

    @GetMapping("/{id}/escolha")
    public String escolha(@PathVariable int id, Model model, HttpSession session) {
        SalaSessao sala = salaSessaoRepository.findById(id).orElseThrow();

        if (sala.getStatus() == StatusSala.LOBBY) {
            return "redirect:/game/lobby/" + sala.getCodigoAcesso();
        }
        if (sala.getStatus() == StatusSala.FINALIZADA) {
            return "redirect:/sala/" + id + "/podio";
        }
        // Se um jogador já fez a escolha, vai direto para a partida
        if (sala.getCartaoAtual() != null) {
            return "redirect:/sala/" + id + "/partida";
        }

        Integer jogadorId = (Integer) session.getAttribute("jogadorId");
        Jogador turno     = sala.getJogadorTurno();
        boolean isMinhaVez = jogadorId != null && turno != null && turno.getId() == (int) jogadorId;

        model.addAttribute("sala", sala);
        model.addAttribute("jogadorDaVez", turno);
        model.addAttribute("isMinhaVez", isMinhaVez);
        return "game/escolha";
    }

    @PostMapping("/{id}/escolha")
    public String fazerEscolha(@PathVariable int id, @RequestParam TipoCartao tipo) {
        SalaSessao sala = gameService.garantirCartaoAtualDoTipo(id, tipo);

        if (sala.getStatus() == StatusSala.FINALIZADA) {
            return "redirect:/sala/" + id + "/podio";
        }

        // Avisa os outros jogadores que o cartão foi definido
        messagingTemplate.convertAndSend(
                "/topic/partida/" + id,
                "{\"type\":\"card-ready\"}");

        return "redirect:/sala/" + id + "/partida";
    }

    // Tela de sorteio do jogador da vez

    @GetMapping("/{id}/sorteio")
    public String sorteio(@PathVariable int id, Model model) {
        SalaSessao sala = salaSessaoRepository.findById(id).orElseThrow();

        if (sala.getStatus() == StatusSala.LOBBY) {
            return "redirect:/game/lobby/" + sala.getCodigoAcesso();
        }
        if (sala.getStatus() == StatusSala.FINALIZADA) {
            return "redirect:/sala/" + id + "/podio";
        }

        // DE_BOA: sem vez individual — todos respondem juntos. Pula direto pra partida.
        if (sala.getModoJogo() == ModoJogo.DE_BOA) {
            return "redirect:/sala/" + id + "/partida";
        }

        model.addAttribute("sala", sala);
        model.addAttribute("jogadorDaVez", sala.getJogadorTurno());
        model.addAttribute("partidaUrl", "/sala/" + id + "/partida");
        return "game/sorteio";
    }

    // Tela da partida (exibe cartão)

    @GetMapping("/{id}/partida")
    public String partida(@PathVariable int id, Model model, HttpSession session) {
        // Garante que existe um cartão sorteado (também encerra se esgotado)
        SalaSessao sala = gameService.garantirCartaoAtual(id);

        if (sala.getStatus() == StatusSala.FINALIZADA) {
            return "redirect:/sala/" + id + "/podio";
        }
        if (sala.getCartaoAtual() == null) {
            return "redirect:/sala/" + id + "/podio";
        }

        // Total de rodadas: usa o limite definido pelo host, ou total de cartões disponíveis
        int totalRodadas = sala.getMaxRodadas() != null
                ? sala.getMaxRodadas()
                : gameService.totalCartoesDisponiveis(sala.getModoJogo());

        List<Jogador> topJogadores = sala.getJogador().stream()
                .sorted(Comparator.comparingInt(Jogador::getPontuacao).reversed())
                .toList();

        Integer jogadorId  = (Integer) session.getAttribute("jogadorId");
        Jogador turno      = sala.getJogadorTurno();
        boolean isMinhaVez = jogadorId != null && turno != null && turno.getId() == (int) jogadorId;

        model.addAttribute("sala", sala);
        model.addAttribute("cartao", sala.getCartaoAtual());
        model.addAttribute("jogadorDaVez", turno);
        model.addAttribute("rodadaAtual", sala.getRodadaAtual() + 1); // 1-indexado
        model.addAttribute("totalRodadas", totalRodadas);
        model.addAttribute("topJogadores", topJogadores);
        model.addAttribute("isMinhaVez", isMinhaVez);
        return "game/partida";
    }

    @PostMapping("/{id}/resposta-quiz")
    public String respostaQuiz(@PathVariable int id,
                               @RequestParam boolean sucesso,
                               HttpSession session) {

        SalaSessao sala = salaSessaoRepository.findById(id).orElseThrow();

        // Se o round já avançou (race condition), manda pro sorteio
        if (sala.getCartaoAtual() == null) {
            return "redirect:/sala/" + id + "/sorteio";
        }

        // Guarda contexto na sessão para exibir na tela de resultado
        session.setAttribute("lastCartaoId", sala.getCartaoAtual().getId());
        // Salva rodadaAtual ANTES de processar — result screen usa para detectar se round avançou
        session.setAttribute("lastRodada", sala.getRodadaAtual());

        Integer jogadorId = (Integer) session.getAttribute("jogadorId");
        if (jogadorId == null) {
            return "redirect:/sala/" + id + "/sorteio";
        }
        session.setAttribute("lastJogadorId", jogadorId);

        boolean roundAvancou = gameService.processarRespostaQuiz(id, jogadorId, sucesso);

        if (roundAvancou) {
            // Constrói placar com pontuações atualizadas de todos os jogadores
            List<Jogador> placarJogadores = jogadorRepository.findBySalaSessaoId(id);
            StringBuilder placarJson = new StringBuilder("[");
            for (int i = 0; i < placarJogadores.size(); i++) {
                Jogador j = placarJogadores.get(i);
                if (i > 0) placarJson.append(",");
                String nick = j.getNickname().replace("\\", "").replace("\"", "'");
                placarJson.append("{\"n\":\"").append(nick)
                          .append("\",\"p\":").append(j.getPontuacao()).append("}");
            }
            placarJson.append("]");

            SalaSessao salaFinal = salaSessaoRepository.findById(id).orElseThrow();
            if (salaFinal.getStatus() == StatusSala.FINALIZADA) {
                messagingTemplate.convertAndSend("/topic/partida/" + id, "{\"type\":\"game-over\"}");
                // Quem disparou o game-over vai direto ao pódio
                return "redirect:/sala/" + id + "/podio";
            } else {
                // Notifica todos: rodada encerrou, inclui placar
                messagingTemplate.convertAndSend(
                        "/topic/partida/" + id,
                        "{\"type\":\"round-over\",\"placar\":" + placarJson + "}");
            }
        }

        return sucesso
                ? "redirect:/sala/" + id + "/recompensa"
                : "redirect:/sala/" + id + "/penalidade";
    }

    // Resultado da rodada (POST do jogador da vez)

    @PostMapping("/{id}/resultado")
    public String resultado(@PathVariable int id,
                            @RequestParam boolean sucesso,
                            HttpSession session) {

        SalaSessao sala = salaSessaoRepository.findById(id).orElseThrow();

        // Guarda cartão e jogador atual ANTES de processar (processarResultado limpa cartaoAtual)
        Integer lastCartaoId  = sala.getCartaoAtual()  != null ? sala.getCartaoAtual().getId()  : null;
        Integer lastJogadorId = sala.getJogadorTurno() != null ? sala.getJogadorTurno().getId() : null;
        if (lastCartaoId  != null) session.setAttribute("lastCartaoId",  lastCartaoId);
        if (lastJogadorId != null) session.setAttribute("lastJogadorId", lastJogadorId);

        boolean acabou = gameService.processarResultado(id, sucesso);

        if (acabou) {
            messagingTemplate.convertAndSend("/topic/partida/" + id, "{\"type\":\"game-over\"}");
            return "redirect:/sala/" + id + "/podio";
        }

        // Broadcast para TODOS navegarem para a tela de recompensa ou penalidade
        String eventType = sucesso ? "recompensa" : "penalidade";
        String cartaoParam = lastCartaoId  != null ? "&cartaoId="  + lastCartaoId  : "";
        String jogadorParam = lastJogadorId != null ? "&jogadorId=" + lastJogadorId : "";
        messagingTemplate.convertAndSend("/topic/partida/" + id,
                "{\"type\":\"" + eventType + "\""
                + (lastCartaoId  != null ? ",\"cartaoId\":"  + lastCartaoId  : "")
                + (lastJogadorId != null ? ",\"jogadorId\":" + lastJogadorId : "")
                + "}");

        String urlBase = "/sala/" + id + "/" + eventType + "?_=1" + cartaoParam + jogadorParam;
        return "redirect:" + urlBase;
    }

    // Trocar cartão (sem contar rodada) — aplica penalidade de pontos/goles ao jogador

    @PostMapping("/{id}/trocar-carta")
    public String trocarCarta(@PathVariable int id, HttpSession session) {
        Integer jogadorId = (Integer) session.getAttribute("jogadorId");
        if (jogadorId == null) return "redirect:/sala/" + id + "/partida";

        GameService.TrocarCartaResult resultado = gameService.trocarCartaAtual(id, jogadorId);
        SalaSessao sala = resultado.sala();

        if (sala.getStatus() == StatusSala.FINALIZADA) {
            messagingTemplate.convertAndSend("/topic/partida/" + id, "{\"type\":\"game-over\"}");
            return "redirect:/sala/" + id + "/podio";
        }

        // Se havia penalidade, vai para tela de penalidade (todos veem)
        boolean temPenalidade = resultado.golespenalidade() > 0 || resultado.pontosDescontados() > 0;
        if (temPenalidade && resultado.cartaoAnterior() != null) {
            int lastCartaoId = resultado.cartaoAnterior().getId();
            session.setAttribute("lastCartaoId",  lastCartaoId);
            session.setAttribute("lastJogadorId", jogadorId);
            // Broadcast para todos verem a tela de penalidade
            messagingTemplate.convertAndSend("/topic/partida/" + id,
                    "{\"type\":\"penalidade\",\"cartaoId\":" + lastCartaoId
                    + ",\"jogadorId\":" + jogadorId + "}");
            return "redirect:/sala/" + id + "/penalidade?_=1&cartaoId=" + lastCartaoId + "&jogadorId=" + jogadorId;
        }

        // Sem penalidade: novo cartão disponível, todos voltam pra partida
        messagingTemplate.convertAndSend("/topic/partida/" + id, "{\"type\":\"card-ready\"}");
        return "redirect:/sala/" + id + "/partida";
    }

    /**
     * Estado atual do quiz para catch-up: cliente chama após WS conectar para não
     * perder eventos (round-over / game-over) que chegaram enquanto a página carregava.
     */
    @GetMapping("/{id}/quiz-status")
    @ResponseBody
    public Map<String, Object> quizStatus(@PathVariable int id, HttpSession session) {
        SalaSessao sala = salaSessaoRepository.findById(id).orElseThrow();
        Map<String, Object> result = new HashMap<>();

        if (sala.getStatus() == StatusSala.FINALIZADA) {
            result.put("type", "game-over");
            return result;
        }

        Integer lastRodada = (Integer) session.getAttribute("lastRodada");
        if (lastRodada != null && sala.getRodadaAtual() > lastRodada) {
            result.put("type", "round-over");
            List<Jogador> jogadores = jogadorRepository.findBySalaSessaoId(id);
            List<Map<String, Object>> placar = new java.util.ArrayList<>();
            for (Jogador j : jogadores) {
                Map<String, Object> item = new HashMap<>();
                item.put("n", j.getNickname().replace("\"", "'"));
                item.put("p", j.getPontuacao());
                placar.add(item);
            }
            result.put("placar", placar);
            return result;
        }

        result.put("type", "waiting");
        return result;
    }

    // Tela de recompensa (acerto)

    @GetMapping("/{id}/recompensa")
    public String recompensa(@PathVariable int id, Model model, HttpSession session,
                             @RequestParam(required = false) Integer cartaoId,
                             @RequestParam(required = false) Integer jogadorId) {
        SalaSessao sala = salaSessaoRepository.findById(id).orElseThrow();

        // Jogo encerrado: vai direto ao pódio (evita travar na tela de resultado)
        if (sala.getStatus() == StatusSala.FINALIZADA
                && sala.getModoJogo() == ModoJogo.DE_BOA) {
            return "redirect:/sala/" + id + "/podio";
        }

        CartaoConteudo cartao = cartaoId != null
                ? cartaoConteudoRepository.findById(cartaoId).orElse(null)
                : carregaUltimoCartao(session);
        Jogador jogador = jogadorId != null
                ? jogadorRepository.findById(jogadorId).orElse(null)
                : carregaUltimoJogador(session);

        model.addAttribute("sala", sala);
        model.addAttribute("cartao",       cartao);
        model.addAttribute("jogadorDaVez", jogador);
        model.addAttribute("placarJson",   buildPlacarSeRoundAvancou(id, sala, session));
        return "game/recompensa";
    }

    // Tela de penalidade (erro / recusa / trocar)

    @GetMapping("/{id}/penalidade")
    public String penalidade(@PathVariable int id, Model model, HttpSession session,
                             @RequestParam(required = false) Integer cartaoId,
                             @RequestParam(required = false) Integer jogadorId) {
        SalaSessao sala = salaSessaoRepository.findById(id).orElseThrow();

        // Jogo encerrado: vai direto ao pódio (evita travar na tela de penalidade)
        if (sala.getStatus() == StatusSala.FINALIZADA
                && sala.getModoJogo() == ModoJogo.DE_BOA) {
            return "redirect:/sala/" + id + "/podio";
        }

        CartaoConteudo cartao = cartaoId != null
                ? cartaoConteudoRepository.findById(cartaoId).orElse(null)
                : carregaUltimoCartao(session);
        Jogador jogador = jogadorId != null
                ? jogadorRepository.findById(jogadorId).orElse(null)
                : carregaUltimoJogador(session);

        model.addAttribute("sala", sala);
        model.addAttribute("cartao",       cartao);
        model.addAttribute("jogadorDaVez", jogador);
        model.addAttribute("placarJson",   buildPlacarSeRoundAvancou(id, sala, session));
        return "game/penalidade";
    }

    /**
     * Para DE_BOA: detecta se o round já avançou (último a responder chega ao result screen
     * depois que o round-over WS foi emitido) e devolve o placar em JSON.
     * Retorna null se não é DE_BOA ou o round ainda não avançou.
     */
    private String buildPlacarSeRoundAvancou(int salaId, SalaSessao sala, HttpSession session) {
        if (sala.getModoJogo() != ModoJogo.DE_BOA) return null;
        Integer lastRodada = (Integer) session.getAttribute("lastRodada");
        if (lastRodada == null || sala.getRodadaAtual() <= lastRodada) return null;

        List<Jogador> jogadores = jogadorRepository.findBySalaSessaoId(salaId);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < jogadores.size(); i++) {
            if (i > 0) sb.append(",");
            String nick = jogadores.get(i).getNickname().replace("\"", "'");
            sb.append("{\"n\":\"").append(nick)
              .append("\",\"p\":").append(jogadores.get(i).getPontuacao()).append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    // Escolher MICO: troca pergunta atual por um MICO sem penalidade

    @PostMapping("/{id}/escolher-mico")
    public String escolherMico(@PathVariable int id) {
        SalaSessao sala = gameService.trocarParaMico(id);
        if (sala.getStatus() == StatusSala.FINALIZADA) {
            messagingTemplate.convertAndSend("/topic/partida/" + id, "{\"type\":\"game-over\"}");
            return "redirect:/sala/" + id + "/podio";
        }
        messagingTemplate.convertAndSend("/topic/partida/" + id, "{\"type\":\"card-ready\"}");
        return "redirect:/sala/" + id + "/partida";
    }

    // Próximo turno — avança para sorteio e notifica todos via WS

    @GetMapping("/{id}/proximo-turno")
    public String proximoTurno(@PathVariable int id) {
        SalaSessao sala = salaSessaoRepository.findById(id).orElseThrow();
        if (sala.getStatus() == StatusSala.FINALIZADA) {
            messagingTemplate.convertAndSend("/topic/partida/" + id, "{\"type\":\"game-over\"}");
            return "redirect:/sala/" + id + "/podio";
        }
        // Notifica outros jogadores ainda em recompensa/penalidade/partida para ir ao sorteio
        messagingTemplate.convertAndSend("/topic/partida/" + id, "{\"type\":\"next-turn\"}");
        return "redirect:/sala/" + id + "/sorteio";
    }

    // Pódio (fim de jogo)

    @GetMapping("/{id}/podio")
    public String podio(@PathVariable int id, Model model) {
        SalaSessao sala = salaSessaoRepository.findById(id).orElseThrow();

        List<Jogador> classificacao = sala.getJogador().stream()
                .sorted(Comparator.comparingInt(Jogador::getPontuacao).reversed())
                .toList();

        // top3 como array de tamanho fixo (pode ter nulls se < 3 jogadores)
        Jogador[] top3 = new Jogador[3];
        for (int i = 0; i < Math.min(3, classificacao.size()); i++) {
            top3[i] = classificacao.get(i);
        }

        List<Jogador> resto = classificacao.size() > 3
                ? classificacao.subList(3, classificacao.size())
                : List.of();

        model.addAttribute("sala",  sala);
        model.addAttribute("top3",  top3);
        model.addAttribute("resto", resto);
        return "game/podio";
    }

    // Jogar de novo (reseta a sessão para LOBBY)

    @GetMapping("/{id}/jogar-de-novo")
    public String jogarDeNovo(@PathVariable int id, HttpSession session) {
        SalaSessao sala = gameService.jogarDeNovo(id);

        // Garante que o jogadorId da sessão é de um jogador que ainda existe na sala.
        // Se o jogador atual não pertence mais à sala (ex: foi removido), limpa a sessão.
        Integer jogadorId = (Integer) session.getAttribute("jogadorId");
        if (jogadorId != null) {
            boolean aindaNaSala = sala.getJogador().stream()
                    .anyMatch(j -> j.getId() == (int) jogadorId);
            if (!aindaNaSala) {
                session.removeAttribute("jogadorId");
            }
        }

        return "redirect:/game/lobby/" + sala.getCodigoAcesso();
    }

    // Helpers de sessão

    private CartaoConteudo carregaUltimoCartao(HttpSession session) {
        Integer id = (Integer) session.getAttribute("lastCartaoId");
        return id != null ? cartaoConteudoRepository.findById(id).orElse(null) : null;
    }

    private Jogador carregaUltimoJogador(HttpSession session) {
        Integer id = (Integer) session.getAttribute("lastJogadorId");
        return id != null ? jogadorRepository.findById(id).orElse(null) : null;
    }
}
