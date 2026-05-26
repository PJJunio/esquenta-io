# Esquenta.io 🍻

Esquenta.io é um jogo web multiplayer estilo *party game* voltado para animar as suas reuniões com amigos (o famoso "esquenta"). A aplicação permite a criação de salas em tempo real, onde os jogadores entram através de seus dispositivos e competem (ou sofrem punições) respondendo perguntas e cumprindo desafios.

## 🎯 Modos de Jogo

O jogo é projetado para agradar desde grupos mais tranquilos até os mais insanos:

*   **DE_BOA**: Modo familiar, com perguntas descontraídas. Sem conteúdo adulto e sem penalidades que envolvam beber.
*   **ESQUENTA (+18)**: Modo adulto intenso, onde errar perguntas gera penalidades de goles, e inclui desafios polêmicos ("Micos"). Recusar os desafios resulta em penalidades mais severas.

### 🃏 Tipos de Cartas (Desafios)
*   **PERGUNTA**: Cartões com perguntas abertas ou de múltipla escolha. Acertos rendem pontos, erros geram penalidades no modo Esquenta.
*   **SIM/NÃO**: Perguntas diretas que devem ser respondidas com Sim ou Não.
*   **MICO**: Desafios e prendas exclusivas do modo Esquenta. Cumprir garante a pontuação, recusar significa perda de pontos e doses a mais.

## 🚀 Tecnologias Utilizadas

Este projeto foi construído utilizando tecnologias modernas do ecossistema Java e Web:

**Backend:**
*   [Java 21](https://jdk.java.net/21/)
*   [Spring Boot 3.5.1](https://spring.io/projects/spring-boot) (Web, Security, Data JPA, Validation)
*   [Spring WebSockets](https://docs.spring.io/spring-framework/reference/web/websocket.html) (Comunicação em tempo real para a dinâmica multiplayer)
*   [Flyway](https://flywaydb.org/) (Migrações de Banco de Dados)
*   [Spring Dotenv](https://github.com/paulschwarz/spring-dotenv) (Gerenciamento de variáveis de ambiente via `.env`)

**Frontend:**
*   [Thymeleaf](https://www.thymeleaf.org/) (Server-Side Rendering integrado ao Spring)
*   HTML5, CSS3 e JavaScript puro (Vanilla JS)
*   WebSockets no cliente para reatividade em tempo real das interações no jogo.

**Infraestrutura & Banco de Dados:**
*   [PostgreSQL](https://www.postgresql.org/) (Banco de dados relacional primário)
*   [Docker & Docker Compose](https://www.docker.com/) (Conteinerização do banco de dados)

## 🏗️ Estrutura do Projeto

A arquitetura do projeto segue o padrão MVC (Model-View-Controller) tradicional do Spring Boot:
*   `controller/`: Controladores Spring MVC e endpoints WebSocket (`GameController`, `SalaController`, `AdminController`).
*   `entity/`: Entidades de persistência JPA (`Jogador`, `SalaSessao`, `CartaoConteudo`, etc).
*   `enums/`: Definições de domínio e regras de negócio (`ModoJogo`, `TipoCartao`, `StatusSala`).
*   `repository/`: Interfaces do Spring Data JPA.
*   `service/`: Lógica de negócios.
*   `resources/templates/`: Views do Thymeleaf separadas por áreas (`game/`, `admin/`, `login`).
*   `resources/static/`: Assets estáticos (CSS, JS, Imagens).
*   `resources/db/migration/`: Scripts SQL do Flyway para versionamento de banco.

## ⚙️ Como Executar o Projeto Localmente

### Pré-requisitos
*   JDK 21 ou superior instalado
*   Docker e Docker Compose instalados
*   Maven (Opcional, pois o projeto possui o `mvnw` wrapper incluso)

### Passos para a instalação e execução:

1. **Configurar as Variáveis de Ambiente:**
   Crie uma cópia do arquivo de exemplo `.env.example` e renomeie para `.env`. Configure as senhas e credenciais conforme a sua preferência.
   ```bash
   cp .env.example .env
   ```

2. **Subir o Banco de Dados:**
   Utilize o Docker Compose para subir uma instância do PostgreSQL configurada para o projeto.
   ```bash
   docker-compose up -d
   ```

3. **Executar a Aplicação:**
   Execute o projeto utilizando o Maven Wrapper na raiz do repositório. O Flyway aplicará automaticamente as migrações no banco de dados na inicialização.
   ```bash
   ./mvnw spring-boot:run
   ```

4. **Acessar a Aplicação:**
   *   **Jogo:** Acesse `http://localhost:8080` para entrar no lobby e criar as salas.
   *   **Painel Admin:** Acesse `http://localhost:8080/admin` para gerenciar perguntas (utilizando as credenciais definidas no seu `.env` ou o padrão do `application.properties`).

## 🧹 Manutenção e Limpeza
O sistema possui configurações ativas no `application.properties` para limpar salas inativas automaticamente (Salas ativas abandonadas, Lobbies não iniciados ou partidas finalizadas), garantindo a integridade e otimizando o armazenamento no banco.
