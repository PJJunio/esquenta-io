package com.pjjunio.esquentaio.controller;

import com.pjjunio.esquentaio.entity.Admin;
import com.pjjunio.esquentaio.entity.CartaoConteudo;
import com.pjjunio.esquentaio.enums.ModoRestrito;
import com.pjjunio.esquentaio.enums.TipoCartao;
import com.pjjunio.esquentaio.repository.AdminRepository;
import com.pjjunio.esquentaio.repository.CartaoConteudoRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final CartaoConteudoRepository cartaoRepo;
    private final AdminRepository adminRepo;

    public AdminController(CartaoConteudoRepository cartaoRepo, AdminRepository adminRepo) {
        this.cartaoRepo = cartaoRepo;
        this.adminRepo = adminRepo;
    }

    // redireciona /admin e /admin/ → /admin/cartoes
    @GetMapping({"", "/"})
    public String root() {
        return "redirect:/admin/cartoes";
    }

    // lista cartões com filtros
    @GetMapping("/cartoes")
    public String listarCartoes(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String modo,
            Model model) {

        List<CartaoConteudo> todos = cartaoRepo.findAll();

        List<CartaoConteudo> filtrados = todos.stream()
                .filter(c -> q == null || q.isBlank()
                        || c.getTexto().toLowerCase().contains(q.toLowerCase()))
                .filter(c -> tipo == null || tipo.isBlank() || tipo.equals("TODOS")
                        || c.getTipo().name().equals(tipo))
                .filter(c -> modo == null || modo.isBlank() || modo.equals("TODOS")
                        || c.getModoRestrito().name().equals(modo))
                .toList();

        model.addAttribute("cartoes", filtrados);
        model.addAttribute("totalCartoes", todos.size());
        model.addAttribute("totalPerguntas",
                todos.stream().filter(c -> c.getTipo() == TipoCartao.PERGUNTA || c.getTipo() == TipoCartao.PERGUNTA_SIM_NAO).count());
        model.addAttribute("totalMicos",
                todos.stream().filter(c -> c.getTipo() == TipoCartao.MICO).count());
        model.addAttribute("totalEsquenta",
                todos.stream().filter(c -> c.getModoRestrito() == ModoRestrito.ESQUENTA).count());
        model.addAttribute("q", q);
        model.addAttribute("filterTipo", tipo != null ? tipo : "TODOS");
        model.addAttribute("filterModo", modo != null ? modo : "TODOS");

        return "admin/dashboard";
    }

    // criar cartão
    @PostMapping("/cartoes/criar")
    public String criarCartao(
            @RequestParam String texto,
            @RequestParam TipoCartao tipo,
            @RequestParam ModoRestrito modoRestrito,
            @RequestParam(defaultValue = "0") int goles,
            @RequestParam(defaultValue = "10") int pontosRecompensa,
            @RequestParam(required = false) String respostaCorreta,
            @RequestParam(required = false) String opcoesErradas,
            Authentication auth) {

        if (tipo == TipoCartao.MICO || tipo == TipoCartao.PERGUNTA_SIM_NAO) {
            modoRestrito = ModoRestrito.ESQUENTA;
        }
        if (tipo == TipoCartao.PERGUNTA_SIM_NAO) {
            // Sim/Não: sem resposta correta nem alternativas — o grupo decide verbalmente
            respostaCorreta = null;
            opcoesErradas   = null;
        }

        Admin admin = adminRepo.findByUsername(auth.getName()).orElseThrow();
        CartaoConteudo cartao = new CartaoConteudo(admin, texto, tipo, modoRestrito, goles, pontosRecompensa);
        cartao.setRespostaCorreta(respostaCorreta);
        cartao.setOpcoesErradas(opcoesErradas);
        cartaoRepo.save(cartao);
        return "redirect:/admin/cartoes";
    }

    // editar cartão
    @PostMapping("/cartoes/{id}/editar")
    public String editarCartao(
            @PathVariable int id,
            @RequestParam String texto,
            @RequestParam TipoCartao tipo,
            @RequestParam ModoRestrito modoRestrito,
            @RequestParam(defaultValue = "0") int goles,
            @RequestParam(defaultValue = "10") int pontosRecompensa,
            @RequestParam(required = false) String respostaCorreta,
            @RequestParam(required = false) String opcoesErradas) {

        if (tipo == TipoCartao.MICO || tipo == TipoCartao.PERGUNTA_SIM_NAO) {
            modoRestrito = ModoRestrito.ESQUENTA;
        }
        if (tipo == TipoCartao.PERGUNTA_SIM_NAO) {
            respostaCorreta = null;
            opcoesErradas   = null;
        }

        CartaoConteudo cartao = cartaoRepo.findById(id).orElseThrow();
        cartao.setTexto(texto);
        cartao.setTipo(tipo);
        cartao.setModoRestrito(modoRestrito);
        cartao.setGoles(goles);
        cartao.setPontosRecompensa(pontosRecompensa);
        cartao.setRespostaCorreta(respostaCorreta);
        cartao.setOpcoesErradas(opcoesErradas);
        cartaoRepo.save(cartao);
        return "redirect:/admin/cartoes";
    }

    // deletar cartão
    @PostMapping("/cartoes/{id}/deletar")
    public String deletarCartao(@PathVariable int id) {
        cartaoRepo.deleteById(id);
        return "redirect:/admin/cartoes";
    }

    // ── seed de conteúdo ──────────────────────────────────────────────────────
    @PostMapping("/cartoes/seed")
    public String seedCartoes(Authentication auth) {
        Admin admin = adminRepo.findByUsername(auth.getName()).orElseThrow();
        for (CartaoConteudo c : seedCards(admin)) {
            cartaoRepo.save(c);
        }
        return "redirect:/admin/cartoes";
    }

    private List<CartaoConteudo> seedCards(Admin admin) {
        var list = new java.util.ArrayList<CartaoConteudo>();

        // ── PERGUNTAS DE BOA (PERGUNTA) — modo DE_BOA ─────────────────────────
        // formato: { pergunta, resposta_correta, errada1|errada2|errada3 }
        // Separador das opções erradas: § (o JS em partida.html faz split('§'))
        String[][] perguntasDeBoa = {
            {"Qual é a capital do Brasil?",                       "Brasília",         "São Paulo§Rio de Janeiro§Salvador"},
            {"Quantos estados tem o Brasil?",                     "26",               "25§27§24"},
            {"Qual é o maior planeta do sistema solar?",          "Júpiter",          "Saturno§Netuno§Urano"},
            {"Quem pintou a Mona Lisa?",                          "Leonardo da Vinci","Michelangelo§Rafael§Picasso"},
            {"Qual é o animal mais rápido do mundo?",             "Guepardo",         "Leão§Leopardo§Cavalo"},
            {"Em que ano o Brasil foi pentacampeão mundial?",     "2002",             "1998§1994§2006"},
            {"Qual é o planeta mais próximo do Sol?",             "Mercúrio",         "Vênus§Terra§Marte"},
            {"Quantos jogadores em campo num time de futebol?",   "11",               "10§12§9"},
            {"Qual é o elemento químico com símbolo 'O'?",        "Oxigênio",         "Ouro§Ósmio§Ozônio"},
            {"Quanto é a raiz quadrada de 144?",                  "12",               "10§14§16"},
            {"Qual continente tem mais países?",                   "África",           "Ásia§Europa§Américas"},
            {"Em que país fica a Torre Eiffel?",                  "França",           "Itália§Espanha§Bélgica"},
        };
        for (String[] row : perguntasDeBoa) {
            var c = new CartaoConteudo(admin, row[0], TipoCartao.PERGUNTA, ModoRestrito.DE_BOA, 0, 10);
            c.setRespostaCorreta(row[1]);
            c.setOpcoesErradas(row[2]);
            list.add(c);
        }

        // ── VERDADE (PERGUNTA) — modo ESQUENTA ────────────────────────────────
        String[][] verdades = {
            {"Qual foi a coisa mais embaraçosa que já fez bêbado(a)?"},
            {"Já ficou com alguém nessa sala? Pode contar quem."},
            {"Qual é o seu fetiche mais secreto que nunca contou pra ninguém?"},
            {"Já mandou mensagem comprometedora para a pessoa errada? O que dizia?"},
            {"Já ficou com mais de uma pessoa no mesmo dia? Como foi?"},
            {"Qual é a maior mentira que contou para um(a) parceiro(a)?"},
            {"Já teve pensamentos sobre alguém do grupo? Fala quem!"},
            {"O que você faria se soubesse que ninguém ficaria sabendo?"},
            {"Qual foi a situação mais constrangedora que já viveu na cama?"},
            {"Já terminou com alguém por WhatsApp? Por quê?"},
            {"Qual a coisa mais estranha que já fez para conquistar alguém?"},
            {"Já traiu alguém? Se não, já teve vontade de quê?"},
        };
        for (String[] t : verdades) {
            var c = new CartaoConteudo(admin, t[0], TipoCartao.PERGUNTA, ModoRestrito.ESQUENTA, 2, 10);
            list.add(c);
        }

        // ── SIM OU NÃO (PERGUNTA_SIM_NAO) — modo ESQUENTA ────────────────────
        String[][] simNao = {
            {"Você já ficou com alguém desta sala?"},
            {"Já mandou foto comprometedora para alguém aqui?"},
            {"Você toparia ficar com a pessoa à sua direita agora?"},
            {"Já mentiu para parceiro(a) sobre onde estava?"},
            {"Já teve envolvimento com colega de trabalho ou chefe?"},
            {"Já terminou um relacionamento por causa de alguém nesta festa?"},
        };
        for (String[] t : simNao) {
            var c = new CartaoConteudo(admin, t[0], TipoCartao.PERGUNTA_SIM_NAO, ModoRestrito.ESQUENTA, 2, 10);
            list.add(c);
        }

        // ── MICO — modo ESQUENTA ──────────────────────────────────────────────
        String[][] micos = {
            {"Chame alguém da sala pelo apelido mais carinhoso que conseguir inventar agora."},
            {"Dê um elogio sedutor para a pessoa à sua esquerda olhando nos olhos."},
            {"Faça uma proposta indecente (fictícia e engraçada) para alguém da sala."},
            {"Mostre a última foto salva no seu celular para todo mundo."},
            {"Sente no colo de quem você achar mais atraente por 30 segundos."},
            {"Imite seu ator/atriz pornô favorito(a) por 15 segundos."},
            {"Mande uma mensagem para seu ex dizendo 'tô com saudade agora.'"},
            {"Deixe alguém da sala ler suas últimas 5 conversas do WhatsApp."},
        };
        int[] golesMico = {2, 2, 2, 3, 2, 3, 3, 3};
        for (int i = 0; i < micos.length; i++) {
            var c = new CartaoConteudo(admin, micos[i][0], TipoCartao.MICO, ModoRestrito.ESQUENTA, golesMico[i], 15);
            list.add(c);
        }

        return list;
    }
}
