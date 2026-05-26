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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class GameService {

    private static final SecureRandom secureRandom = new SecureRandom();

    private final SalaSessaoRepository    salaSessaoRepository;
    private final JogadorRepository       jogadorRepository;
    private final CartaoConteudoRepository cartaoConteudoRepository;
    private final CartaoUtilizadoRepository cartaoUtilizadoRepository;

    public GameService(SalaSessaoRepository salaSessaoRepository,
                       JogadorRepository jogadorRepository,
                       CartaoConteudoRepository cartaoConteudoRepository,
                       CartaoUtilizadoRepository cartaoUtilizadoRepository) {
        this.salaSessaoRepository     = salaSessaoRepository;
        this.jogadorRepository        = jogadorRepository;
        this.cartaoConteudoRepository = cartaoConteudoRepository;
        this.cartaoUtilizadoRepository = cartaoUtilizadoRepository;
    }

    public record SairSalaResult(String codigoAcesso, boolean salaClosed) {}

    public record TrocarCartaResult(SalaSessao sala, CartaoConteudo cartaoAnterior,
                                    int golespenalidade, int pontosDescontados) {}

    // Lobby

    public static String gerarPin() {
        int pin = 1000 + secureRandom.nextInt(9000);
        return String.valueOf(pin);
    }

    @Transactional
    public SalaSessao createDeBoa(String nickname) {
        return criarSala(nickname, ModoJogo.DE_BOA);
    }

    @Transactional
    public SalaSessao createEsquenta(String nickname) {
        return criarSala(nickname, ModoJogo.ESQUENTA);
    }

    @Transactional
    public Jogador entrarSala(String nickname, String codigo) {
        SalaSessao sala = salaSessaoRepository.findByCodigoAcesso(codigo)
                .orElseThrow(() -> new IllegalArgumentException("Sala não encontrada: " + codigo));

        if (sala.getStatus() != StatusSala.LOBBY) {
            throw new IllegalStateException("Partida já iniciada.");
        }

        String nick = nickname.trim();

        // Bloqueia nicknames duplicados dentro da mesma sala
        if (jogadorRepository.findBySalaSessaoIdAndNickname(sala.getId(), nick).isPresent()) {
            throw new IllegalArgumentException("Apelido já em uso nesta sala. Escolha outro.");
        }

        Jogador jogador = new Jogador();
        jogador.setNickname(nick);
        jogador.setHost(false);
        jogador.setPontuacao(0);
        jogador.setReady(false);
        jogador.setSalaSessao(sala);
        jogadorRepository.save(jogador);

        return jogador;
    }

    @Transactional
    public SairSalaResult sairSala(int jogadorId) {
        Jogador jogador = jogadorRepository.findById(jogadorId).orElse(null);
        if (jogador == null) return new SairSalaResult("", false);

        int salaId = jogador.getSalaSessao().getId();
        SalaSessao sala = salaSessaoRepository.findById(salaId).orElseThrow();
        String codigoAcesso = sala.getCodigoAcesso();
        boolean erHost = jogador.isHost();

        // Desvincular FK da sala (jogadorTurno) antes de deletar o jogador
        if (sala.getJogadorTurno() != null && sala.getJogadorTurno().getId() == jogadorId) {
            sala.setJogadorTurno(null);
            salaSessaoRepository.saveAndFlush(sala);
        }

        // Deletar jogador e garantir que o DELETE chegue ao banco antes do próximo save
        jogadorRepository.deleteById(jogadorId);
        jogadorRepository.flush();

        // Recarregar sala do banco — a lista de jogadores agora não contém o deletado
        sala = salaSessaoRepository.findById(salaId).orElseThrow();
        List<Jogador> restantes = new ArrayList<>(sala.getJogador());

        if (erHost || restantes.isEmpty()) {
            // Host saiu ou sala ficou vazia → encerra a sala
            sala.setStatus(StatusSala.FINALIZADA);
            sala.setJogadorTurno(null);
            sala.setCartaoAtual(null);
            salaSessaoRepository.save(sala);
            return new SairSalaResult(codigoAcesso, true);
        }

        // Jogador comum saiu, sala continua — sem necessidade de ação extra
        return new SairSalaResult(codigoAcesso, false);
    }

    @Transactional
    public SalaSessao toggleReady(int jogadorId) {
        Jogador jogador = jogadorRepository.findById(jogadorId)
                .orElseThrow(() -> new IllegalArgumentException("Jogador não encontrado: " + jogadorId));
        if (!jogador.isHost()) { // host já nasce pronto e não deve alternar
            jogador.setReady(!jogador.isReady());
            jogadorRepository.save(jogador);
        }
        return salaSessaoRepository.findById(jogador.getSalaSessao().getId()).orElseThrow();
    }

    // Validação de cartões mínimos para iniciar partida

    public String validarCartoesSuficientes(ModoJogo modo) {
        if (modo == ModoJogo.DE_BOA) {
            List<CartaoConteudo> cards = cartaoConteudoRepository.findByModoRestritoIn(
                    List.of(ModoRestrito.DE_BOA, ModoRestrito.AMBOS));
            long perguntas = cards.stream()
                    .filter(c -> c.getTipo() == TipoCartao.PERGUNTA)
                    .count();
            if (perguntas < 10) {
                return "O modo De Boa precisa de pelo menos 10 perguntas cadastradas. "
                        + "Atual: " + perguntas + "/10.";
            }
        } else { // ESQUENTA
            List<CartaoConteudo> cards = cartaoConteudoRepository.findByModoRestritoIn(
                    List.of(ModoRestrito.ESQUENTA, ModoRestrito.AMBOS));
            long perguntas = cards.stream()
                    .filter(c -> c.getTipo() == TipoCartao.PERGUNTA
                              || c.getTipo() == TipoCartao.PERGUNTA_SIM_NAO)
                    .count();
            long micos = cards.stream()
                    .filter(c -> c.getTipo() == TipoCartao.MICO)
                    .count();
            if (perguntas < 10 && micos < 10) {
                return "O modo Esquenta precisa de pelo menos 10 perguntas (atual: " + perguntas
                        + "/10) e 10 micos (atual: " + micos + "/10).";
            }
            if (perguntas < 10) {
                return "O modo Esquenta precisa de pelo menos 10 perguntas/verdades. Atual: " + perguntas + "/10.";
            }
            if (micos < 10) {
                return "O modo Esquenta precisa de pelo menos 10 micos/desafios. Atual: " + micos + "/10.";
            }
        }
        return null;
    }

    // Início da partida

    @Transactional
    public SalaSessao iniciarPartida(int salaId, Integer maxRodadas) {
        SalaSessao sala = salaSessaoRepository.findById(salaId)
                .orElseThrow(() -> new IllegalArgumentException("Sala não encontrada: " + salaId));

        if (sala.getStatus() != StatusSala.LOBBY) {
            throw new IllegalStateException("Sala não está em LOBBY.");
        }

        sala.setStatus(StatusSala.ATIVA);
        sala.setRodadaAtual(0);
        sala.setCartaoAtual(null);
        sala.setMaxRodadas(maxRodadas); // null = sem limite (usa todos os cartões)
        sala.setJogadorTurno(sortearProximoJogador(sala, null));

        return salaSessaoRepository.save(sala);
    }

    // Sorteio e exibição de cartão

    /**
     * Garante que a sala tenha um cartão sorteado para a rodada atual.
     * Se já tiver, retorna sem alterar. Se não tiver, sorteia e salva.
     */
    @Transactional
    public SalaSessao garantirCartaoAtual(int salaId) {
        // Lock exclusivo: evita race condition quando dois jogadores chegam em /partida simultâneo
        SalaSessao sala = salaSessaoRepository.findByIdForUpdate(salaId).orElseThrow();

        if (sala.getCartaoAtual() == null) {
            CartaoConteudo cartao = sortearCartaoDisponivel(sala);
            if (cartao == null) {
                // Sem cartões → encerra automaticamente
                sala.setStatus(StatusSala.FINALIZADA);
                sala.setJogadorTurno(null);
            } else {
                sala.setCartaoAtual(cartao);
            }
            sala = salaSessaoRepository.save(sala);
        }

        return sala;
    }

    @Transactional
    public TrocarCartaResult trocarCartaAtual(int salaId, int jogadorId) {
        SalaSessao sala = salaSessaoRepository.findById(salaId).orElseThrow();

        CartaoConteudo anterior = sala.getCartaoAtual();
        int golesPenalidade   = 0;
        int pontosDescontados = 0;

        if (anterior != null) {
            // Registra cartão como utilizado
            cartaoUtilizadoRepository.save(new CartaoUtilizado(sala, anterior));
            sala.setCartaoAtual(null);

            // Aplica penalidade ao jogador que trocou
            golesPenalidade   = anterior.getGoles();
            pontosDescontados = anterior.getPontosRecompensa();

            if (pontosDescontados > 0 || golesPenalidade > 0) {
                Jogador jogador = jogadorRepository.findById(jogadorId).orElse(null);
                if (jogador != null) {
                    jogador.setPontuacao(jogador.getPontuacao() - pontosDescontados);
                    jogadorRepository.save(jogador);
                }
            }
        }

        // Sorteia novo cartão preservando o tipo original no modo ESQUENTA
        // (evita que um jogador que escolheu VERDADE receba um MICO ao trocar)
        CartaoConteudo novo;
        if (anterior != null && "ESQUENTA".equals(sala.getModoJogo().name())) {
            novo = sortearCartaoDisponivelDoTipo(sala, anterior.getTipo());
            if (novo == null) {
                novo = sortearCartaoDisponivel(sala); // fallback: qualquer tipo
            }
        } else {
            novo = sortearCartaoDisponivel(sala);
        }
        if (novo == null) {
            sala.setStatus(StatusSala.FINALIZADA);
            sala.setJogadorTurno(null);
        } else {
            sala.setCartaoAtual(novo);
        }

        salaSessaoRepository.save(sala);
        return new TrocarCartaResult(sala, anterior, golesPenalidade, pontosDescontados);
    }

    // Resultado da rodada

    @Transactional
    public boolean processarResultado(int salaId, boolean sucesso) {
        SalaSessao sala = salaSessaoRepository.findById(salaId).orElseThrow();

        CartaoConteudo cartao  = sala.getCartaoAtual();
        Jogador        jogador = sala.getJogadorTurno();

        if (cartao == null || jogador == null) {
            // Resultado duplicado (ex: duplo clique); ignora
            return sala.getStatus() == StatusSala.FINALIZADA;
        }

        boolean isMico = cartao.getTipo() == TipoCartao.MICO;

        // Atualiza pontuação
        if (isMico) {
            // MICO: cumpriu = sem penalidade; recusou = perde pontos
            if (!sucesso) {
                jogador.setPontuacao(jogador.getPontuacao() - cartao.getPontosRecompensa());
            }
        } else {
            // PERGUNTA / PERGUNTA_SIM_NAO
            if (sucesso) {
                jogador.setPontuacao(jogador.getPontuacao() + cartao.getPontosRecompensa());
            } else {
                jogador.setPontuacao(jogador.getPontuacao() - cartao.getPontosRecompensa());
            }
        }
        jogadorRepository.save(jogador);

        // Registra cartão como utilizado
        cartaoUtilizadoRepository.save(new CartaoUtilizado(sala, cartao));

        // Avança rodada e limpa cartão atual
        sala.setRodadaAtual(sala.getRodadaAtual() + 1);
        sala.setCartaoAtual(null);

        // Verifica se há cartões restantes (ou se atingiu o limite de rodadas)
        Integer max = sala.getMaxRodadas();
        boolean acabou = (max != null && sala.getRodadaAtual() >= max)
                         || sortearCartaoDisponivel(sala) == null;
        if (acabou) {
            sala.setStatus(StatusSala.FINALIZADA);
            sala.setJogadorTurno(null);
        } else {
            // Avança para o próximo jogador (round-robin)
            sala.setJogadorTurno(sortearProximoJogador(sala, jogador));
        }

        salaSessaoRepository.save(sala);
        return acabou;
    }

    @Transactional
    public boolean processarRespostaQuiz(int salaId, int jogadorId, boolean sucesso) {
        SalaSessao sala = salaSessaoRepository.findById(salaId).orElseThrow();

        CartaoConteudo cartao = sala.getCartaoAtual();

        // Cartão já foi limpo: round já avançou (race condition), ignora silenciosamente
        if (cartao == null) {
            return false;
        }

        Jogador jogador = jogadorRepository.findById(jogadorId)
                .orElseThrow(() -> new IllegalArgumentException("Jogador não encontrado: " + jogadorId));

        // Atualiza pontuação individual
        if (sucesso) {
            jogador.setPontuacao(jogador.getPontuacao() + cartao.getPontosRecompensa());
        } else {
            // De Boa: perde pontos (pode ficar negativo)
            jogador.setPontuacao(jogador.getPontuacao() - cartao.getPontosRecompensa());
        }
        jogadorRepository.save(jogador);

        // Registra que este jogador respondeu na rodada atual
        java.util.Set<String> respondidos = new java.util.LinkedHashSet<>();
        String atual = sala.getRespostasRodadaAtual();
        if (atual != null && !atual.isBlank()) {
            java.util.Collections.addAll(respondidos, atual.split(","));
        }
        respondidos.add(String.valueOf(jogadorId));
        sala.setRespostasRodadaAtual(String.join(",", respondidos));

        // Avança rodada somente quando TODOS os jogadores responderam
        int totalJogadores = sala.getJogador().size();
        boolean todosResponderam = respondidos.size() >= totalJogadores;

        if (todosResponderam) {
            // Registra cartão como utilizado e avança rodada
            cartaoUtilizadoRepository.save(new CartaoUtilizado(sala, cartao));
            sala.setRodadaAtual(sala.getRodadaAtual() + 1);
            sala.setCartaoAtual(null);
            sala.setRespostasRodadaAtual(null); // reset para a próxima rodada

            Jogador turnoAtual = sala.getJogadorTurno();
            Integer max = sala.getMaxRodadas();
            boolean acabou = (max != null && sala.getRodadaAtual() >= max)
                             || sortearCartaoDisponivel(sala) == null;
            if (acabou) {
                sala.setStatus(StatusSala.FINALIZADA);
                sala.setJogadorTurno(null);
            } else {
                sala.setJogadorTurno(sortearProximoJogador(sala, turnoAtual));
            }
            salaSessaoRepository.save(sala);
            return true;
        }

        salaSessaoRepository.save(sala);
        return false;
    }

    // Jogar de novo (reset completo da sessão)

    @Transactional
    public SalaSessao jogarDeNovo(int salaId) {
        SalaSessao sala = salaSessaoRepository.findById(salaId).orElseThrow();

        // Reseta estado da sala
        sala.setStatus(StatusSala.LOBBY);
        sala.setJogadorTurno(null);
        sala.setCartaoAtual(null);
        sala.setRodadaAtual(0);
        salaSessaoRepository.save(sala);

        // Reseta jogadores
        for (Jogador j : sala.getJogador()) {
            j.setPontuacao(0);
            j.setReady(j.isHost()); // host auto-pronto, demais aguardam
            jogadorRepository.save(j);
        }

        // Limpa histórico de cartões
        cartaoUtilizadoRepository.deleteBySalaSessaoId(salaId);

        return sala;
    }

    @Transactional
    public SalaSessao garantirCartaoAtualDoTipo(int salaId, TipoCartao tipo) {
        SalaSessao sala = salaSessaoRepository.findById(salaId).orElseThrow();

        if (sala.getCartaoAtual() != null) {
            return sala; // outro jogador já escolheu — não troca
        }

        CartaoConteudo cartao = sortearCartaoDisponivelDoTipo(sala, tipo);
        if (cartao == null) {
            cartao = sortearCartaoDisponivel(sala); // fallback: qualquer tipo
        }
        if (cartao == null) {
            sala.setStatus(StatusSala.FINALIZADA);
            sala.setJogadorTurno(null);
        } else {
            sala.setCartaoAtual(cartao);
        }
        return salaSessaoRepository.save(sala);
    }

    // Helpers privados

    CartaoConteudo sortearCartaoDisponivelDoTipo(SalaSessao sala, TipoCartao tipo) {
        List<Integer> usados = cartaoUtilizadoRepository.findCartaoIdsBySalaSessaoId(sala.getId());

        List<ModoRestrito> modosCompativeis = List.of(
                ModoRestrito.AMBOS,
                ModoRestrito.valueOf(sala.getModoJogo().name())
        );

        List<CartaoConteudo> disponiveis = cartaoConteudoRepository
                .findByModoRestritoIn(modosCompativeis)
                .stream()
                .filter(c -> !usados.contains(c.getId()))
                .filter(c -> {
                    // VERDADE (PERGUNTA) engloba também PERGUNTA_SIM_NAO no modo ESQUENTA
                    if (tipo == TipoCartao.PERGUNTA) {
                        return c.getTipo() == TipoCartao.PERGUNTA
                                || c.getTipo() == TipoCartao.PERGUNTA_SIM_NAO;
                    }
                    return c.getTipo() == tipo;
                })
                .collect(java.util.stream.Collectors.toList());

        if (disponiveis.isEmpty()) return null;
        return disponiveis.get(secureRandom.nextInt(disponiveis.size()));
    }

    CartaoConteudo sortearCartaoDisponivel(SalaSessao sala) {
        List<Integer> usados = cartaoUtilizadoRepository.findCartaoIdsBySalaSessaoId(sala.getId());

        List<ModoRestrito> modosCompativeis = List.of(
                ModoRestrito.AMBOS,
                ModoRestrito.valueOf(sala.getModoJogo().name()) // DE_BOA ou ESQUENTA
        );

        List<CartaoConteudo> disponiveis = cartaoConteudoRepository
                .findByModoRestritoIn(modosCompativeis)
                .stream()
                .filter(c -> !usados.contains(c.getId()))
                .filter(c -> c.getTipo() != TipoCartao.MICO) // MICO só por escolha explícita
                .collect(java.util.stream.Collectors.toList());

        if (disponiveis.isEmpty()) return null;
        return disponiveis.get(secureRandom.nextInt(disponiveis.size()));
    }

    @Transactional
    public SalaSessao trocarParaMico(int salaId) {
        SalaSessao sala = salaSessaoRepository.findById(salaId).orElseThrow();

        CartaoConteudo anterior = sala.getCartaoAtual();
        if (anterior != null) {
            cartaoUtilizadoRepository.save(new CartaoUtilizado(sala, anterior));
            sala.setCartaoAtual(null);
        }

        // Sorteia um MICO disponível
        CartaoConteudo mico = sortearCartaoDisponivelDoTipo(sala, TipoCartao.MICO);
        if (mico == null) {
            // Sem MICO disponível: fallback para qualquer carta (não conta como encerramento)
            mico = sortearCartaoDisponivelDoTipo(sala, TipoCartao.PERGUNTA);
        }
        if (mico == null) {
            sala.setStatus(StatusSala.FINALIZADA);
            sala.setJogadorTurno(null);
        } else {
            sala.setCartaoAtual(mico);
        }
        return salaSessaoRepository.save(sala);
    }

    private Jogador sortearProximoJogador(SalaSessao sala, Jogador atual) {
        List<Jogador> lista = new ArrayList<>(sala.getJogador());
        if (lista.isEmpty()) throw new IllegalStateException("Sala sem jogadores.");

        if (atual == null) {
            return lista.get(secureRandom.nextInt(lista.size()));
        }

        for (int i = 0; i < lista.size(); i++) {
            if (lista.get(i).getId() == atual.getId()) {
                return lista.get((i + 1) % lista.size());
            }
        }
        return lista.get(0); // fallback
    }

    private SalaSessao criarSala(String nickname, ModoJogo modo) {
        SalaSessao sala = new SalaSessao();
        sala.setCodigoAcesso(gerarPin());
        sala.setModoJogo(modo);
        sala.setStatus(StatusSala.LOBBY);
        sala.setDataCriacao(Instant.now());
        salaSessaoRepository.save(sala);

        Jogador host = new Jogador();
        host.setSalaSessao(sala);
        host.setNickname(nickname.trim());
        host.setPontuacao(0);
        host.setHost(true);
        host.setReady(true);
        jogadorRepository.save(host);

        sala.getJogador().add(host);
        return sala;
    }

    public int totalCartoesDisponiveis(ModoJogo modo) {
        List<ModoRestrito> modos = List.of(
                ModoRestrito.AMBOS,
                ModoRestrito.valueOf(modo.name())
        );
        return cartaoConteudoRepository.findByModoRestritoIn(modos).size();
    }
}
