# ESPECIFICAÇÃO TÉCNICA E PLANO DE ENGENHARIA DEFINITIVO: COOKIEBOT SELF-HOSTED (VERSÃO 2.0.0-PROD)

---

## 1. Visão Geral e Filosofia Arquitetural

O **CookieBot Self-Hosted** é uma plataforma auto-hospedada de moderação, entretenimento e gestão de comunidades para o Telegram, projetada para um horizonte de manutenção contínua de 10 anos. A arquitetura abandona o acúmulo de abstrações genéricas e adota um pragmatismo radical: **extrair o throughput máximo do hardware com o menor footprint de RAM possível, mantendo soberania total dos dados sem qualquer dependência de serviços SaaS comerciais**.

### Pilares Arquiteturais Inegociáveis

* **Compilação Nativa AOT (GraalVM First):** O sistema é compilado para um binário estático auto-contido otimizado para compilação Ahead-Of-Time. O tempo de inicialização (cold-start) ocorre em menos de 50 milissegundos e o consumo de memória Heap é estritamente regulado.
* **Concorrência Não-Bloqueante (Project Loom):** Uso integral de **Virtual Threads do Java 26** para 100% das operações de I/O (rede, banco de dados, sistema de arquivos e chamadas HTTP). A aplicação escala para milhares de requisições simultâneas sem esgotar as threads físicas do sistema operacional.
* **Isolamento de Processamento Pesado:** Operações intensivas de CPU e memória (edição de vídeo, manipulação de áudio, OCR, reconhecimento musical e geração de memes) são excluídas da memória Heap da JVM e delegadas a ferramentas nativas consolidadas do sistema operacional (**FFmpeg** e **ImageMagick**) executadas via subprocessos controlados.
* **Zero Dependências SaaS Pagas:** Substituição integral de APIs comerciais (OpenAI, Google Cloud Storage, YouTube v3, Shazam comercial e LibreTranslate pago) por contêineres locais e gratuitos na mesma infraestrutura isolada.

---

## 2. Orçamento de Memória e Metas Quantitativas (SLAs)

A alocação de recursos da máquina hospedeira é fatiada entre os contêineres Docker via políticas estritas de contenção (`deploy.resources.limits`). O consumo é planejado separadamente para a aplicação Java e para os serviços auxiliares.

### Tabela 1: Orçamento Máximo de Memória por Componente (Stack Completa)

| Componente | Orçamento Máximo (Hard Limit) | Estratégia Técnica de Contenção de Recursos |
| --- | --- | --- |
| **CookieBot Core (Java 26 AOT)** | **`-Xmx96m` / RSS `< 120 MB**` | Binário estático sem JVM tradicional. Exclusão de processamento multimídia no Heap. |
| **PostgreSQL 18** | **`512 MB`** | Buffer de memória partilhada travado em `shared_buffers = 128MB`. Limite de conexões regulado. |
| **Redis 7.4** | **`256 MB`** | Política de despejo `allkeys-lru` ativa. TTL obrigatório para todas as chaves temporárias e sessões. |
| **MinIO Storage** | **`256 MB`** | Operação em modo enxuto para objeto único. I/O de disco sem cache de leitura excessivo em RAM. |
| **RAM Disk (`tmpfs`)** | **`250 MB`** | Volume Docker montado na RAM. Atingir o limite bloqueia novas edições para proteger o Kernel. |
| **SearXNG (Motor de Busca)** | **`128 MB`** | Configuração de engines reduzida ao núcleo essencial de retorno JSON (DuckDuckGo, Wikipedia). |
| **Argos Translate (Tradução)** | **`250 MB`** | Carregamento exclusivo de modelos quantizados locais de tradução direta em memória. |
| **Whisper.cpp (STT Worker)** | **`512 MB` (Quando Ativo)** | **Arquitetura Scale-to-Zero:** Consome 0 MB em repouso e sobe na memória apenas ao processar áudio. |
| **Teto Máximo da Stack** | **`~2.2 GB` (Pico Absoluto)** | Em operação nominal (sem transcrição em andamento), o consumo consolidado é de **1.2 GB a 1.5 GB**. |

### Tabela 2: Metas Quantitativas de Performance (SLIs / SLOs)

| Indicador de Sistema / Operação | Meta Quantitativa (Alvo) | Metodologia e Ferramenta de Aferição |
| --- | --- | --- |
| **Latência de Ingestão Webhook (P95)** | **`< 5 ms`** | Teste de carga via K6 / Gatling aplicando 3.000 requisições simultâneas por segundo. |
| **Leitura em Cache L1 (Caffeine)** | **`< 0.5 ms`** | Teste de microbenchmarking via JMH sobre a memória nativa do processo em isolamento. |
| **Consulta em Hot-Path DB (`JdbcClient`)** | **`< 3 ms`** | Latência P99 aferida via Micrometer na rede interna do contêiner Docker. |
| **Tempo de Inicialização (Boot AOT)** | **`< 50 ms`** | Tempo decorrido entre a execução do binário e a abertura da porta HTTP de atendimento. |
| **Pausa Máxima de Garbage Collector** | **`< 5 ms`** | Monitoramento contínuo das métricas do coletor de lixo nativo (Serial GC / G1 em Native Image). |
| **Capacidade de Concorrência de I/O** | **`> 2.500 req/sec`** | Manutenção do throughput sustentado sem rejeição por esgotamento de pool ou descritores. |

---

## 3. Especificação dos 5 Pilares Funcionais Integrados

A arquitetura engloba 100% das funcionalidades de moderação, inteligência artificial, entretenimento, automação de publicações e gestão web, divididas em cinco pilares operacionais:

```
+-----------------------------------------------------------------------------------+
|                           COOKIEBOT CORE (JAVA 26 AOT)                            |
|       [Virtual Threads Engine] ---> (Moderação Ativa & Roteamento Padrão)         |
+-----------------------------------------------------------------------------------+
    |                   |                   |                   |                   |
    v                   v                   v                   v                   v
+-------+           +-------+           +-------+           +-------+           +-------+
|PILAR 1|           |PILAR 2|           |PILAR 3|           |PILAR 4|           |PILAR 5|
|Modera-|           |IA, Voz|           |Engaja-|           |Publica|           |Painel |
| ção e |           |Áudio e|           |mento, |           |dor,   |           |WebHub |
|Recep- |           |Mídia  |           |Eventos|           |Tradu- |           |e BFF  |
| ção   |           |Destroy|           |Sorteio|           |ção/i18|           |Secure |
+-------+           +-------+           +-------+           +-------+           +-------+

```

### Pilar 1: Moderação, Recepção (Welcome) e Proteção Ativa

* **Sistema Anti-Raid Inteligente:** Algoritmo de janela deslizante (Sliding Window Log) implementado via script Lua atômico no Redis (`ZADD` e `ZREMRANGEBYSCORE`). Bloqueia ataques de robôs e spammers em massa em milissegundos sem causar contenção transacional no banco de dados.
* **CAPTCHA Vetorial Headless:** Geração dinâmica de desafios visuais em memória através de primitivas geométricas em formato vetorial SVG, convertidas diretamente para array de bytes (`byte[]`). Opera sem inicializar subsistemas gráficos pesados (`java.awt` ou Java2D).
* **Recepção e Boas-Vindas Customizáveis (Welcome & Rules Matrix):** Saudação automatizada de novos membros com envio de painéis interativos de regras contendo botões inline de concordância obrigatória. Inclui rotina agendada para exclusão automática de mensagens de entrada ("Fulano entrou no grupo") e das próprias saudações de boas-vindas após a aceitação, mantendo o histórico do chat limpo.
* **Sticker Anti-Spam e Blacklists:** Controle temporal de flood para figurinhas, GIFs e encaminhamentos em massa através de contadores no Redis com TTL curto (10 segundos). Verificação instantânea de usuários em listas de banimento locais e globais (como o CAS: *Community Anti-Spam*) através de consultas SQL indexadas no hot-path.

### Pilar 2: Inteligência Artificial, Áudio e Mídia Distrutiva

* **IA Conversacional e Respostas Inteligentes:** Interação em linguagem natural com membros do grupo para sanar dúvidas e responder menções. Utiliza modelo LLM local auto-hospedado conectado via API REST rápida (`RestClient`), com retenção transacional de contexto conversacional armazenada em cache Redis com TTL estrito de 5 minutos (evitando alocação excessiva de tokens e consumo infinita de RAM).
* **Transcrição de Voz via Worker Scale-to-Zero (STT):** Download assíncrono de notas de voz (`.ogg`) e envio para o contêiner local do **Whisper.cpp** (modelo `tiny` ou `small`). O contêiner permanece com consumo zero de RAM em repouso, sendo ativado sob demanda ao receber tarefas de áudio.
* **Identificação Acústica de Músicas (ShazamIO Native):** Reconhecimento de assinaturas acústicas (fingerprints) de trilhas sonoras em vídeos ou arquivos de áudio postados no grupo. A execução do utilitário de checagem opera isolada via subprocesso controlado por semáforo na JVM.
* **Distorção e Edição de Mídia (*Destroy*):** Pipeline de processamento multimídia destrutivo e cômico. Aplica filtros digitais em arquivos de áudio (alteração de pitch, vibrato, overdrive e distorção severa) e redimensionamento agressivo com degradação de qualidade em vídeos e imagens. O processamento é executado por binários nativos (**FFmpeg** e **ImageMagick**) diretamente em volume RAM Disk (`tmpfs`), com destruição compulsória de arquivos residuais após o envio ao Telegram.
* **Reescrita Automática de Links Sociais:** Interceptor de mensagens baseado em expressões regulares pré-compiladas estaticamente. Mapeia e reescreve instantaneamente URLs de redes sociais (X/Twitter para `fxtwitter`, TikTok para `vxtiktok`, Instagram para `ddinstagram`), garantindo a pré-visualização de vídeos no cliente do Telegram sem consumo inútil do Garbage Collector.

### Pilar 3: Engajamento, Quadro de Eventos, Sorteios e Utilitários

* **Aniversários e Colagens Diárias:** Rastreamento persistido das datas de nascimento dos membros. À meia-noite, uma rotina agendada aplica a API de concorrência estruturada (`StructuredTaskScope`) do Java 26 para efetuar o download simultâneo dos avatares dos aniversariantes a partir do MinIO Storage, delegando a composição geométrica do grid comemorativo ao ImageMagick CLI.
* **Sorteios de Alta Concorrência (*Giveaways*):** Módulo de criação de sorteios com botões de participação inline. O controle de milhares de cliques concorrentes é resolvido através do mecanismo de bloqueio otimista da JPA (`@Version`), eliminando travas de tabela pessimistas e garantindo consistência transacional sob picos severos de tráfego.
* **Quadro e Gestão de Eventos da Comunidade:** Módulo transacional para cadastro, listagem, divulgação e gerenciamento de eventos, meetups e datas importantes da comunidade. Inclui agendador nativo para envio automático de lembretes no chat e integração CRUD completa com o painel WebHub.
* **Comandos Customizados e Motores Estatísticos:** Criação de atalhos personalizados e respostas rápidas gerenciadas por administradores em cache L1/L2. Inclui módulos recreativos e estatísticos na memória nativa (rolagem de dados, roletas, simulações e biscoito da sorte com carregamento estático no boot via classpath), operando com latência zero via `ThreadLocalRandom.current()`.
* **Comunicação em Massa e Alertas (`/broadcast`, `/everyone`, `/admincall`):** Ferramentas de comunicação crítica. O comando `/admincall` notifica moderadores em emergências; o comando `/everyone` menciona membros ativos sob proteção de chaves de Rate Limit no Redis (tempo de respiro de 30 minutos); o comando `/broadcast` envia anúncios encadeados do proprietário para todos os grupos administrados, paginando consultas SQL e regulando a vazão via Virtual Threads em lotes controlados de 30 mensagens por segundo (evitando bloqueios por Error 429).

### Pilar 4: Engine de Publicação (Publisher), Tradução e Internacionalização

* **Agendamento Robusto de Postagens (State Machine):** Substituição de filas complexas de mensageria por uma tabela transacional de controle de estado (`publisher_queue`) no PostgreSQL 18. O disparo de posts agendados utiliza a anotação nativa `@Scheduled` acoplada à consulta SQL `FOR UPDATE SKIP LOCKED`, garantindo que múltiplas instâncias em cluster nunca publiquem a mesma mensagem simultaneamente.
* **Fila de Aprovação Interativa via Callbacks:** Propostas de postagem submetidas via painel WebHub são roteadas para canais de moderação no Telegram. O fluxo de aprovação ou rejeição é executado de forma assíncrona através de botões inline (*CallbackQuery*), atualizando o status transacional diretamente via `JdbcClient`.
* **Tradução Automática com Argos Translate:** Integração local com contêiner leve do **Argos Translate** para tradução automática de comunicados, notícias e legendas entre idiomas suportados, apoiada por cache SQL relacional (`translation_cache`) para evitar processamento de textos repetidos. Inclui conversor embutido de valores monetários.
* **Internacionalização Nativa Multi-idioma (i18n):** Suporte integral para Português (`pt_BR`), Inglês (`en`) e Espanhol (`es`). A interface do bot, retornos de comandos e painéis de regras utilizam o `ResourceBundleMessageSource` da JDK 26 com resolução de fallback encadeado (`LocaleMessageResolver`). Inclui rotina de inicialização no boot que aciona a API `setMyCommands` do Telegram, sincronizando os menus nativos no aplicativo dos utilizadores com base no idioma detectado.

### Pilar 5: Painel Administrativo Web (WebHub BFF) e Segurança

* **Interface Web e Padrão BFF (Backend-for-Frontend):** Integração com o painel web (desenvolvido em Next.js 14 / TSX) através de controladores REST dedicados sob o prefixo `/api/admin/*`. O acesso é protegido por autenticação stateless via tokens JWT RS256 gerados a partir da validação criptográfica do widget oficial de login do Telegram.
* **Controle Centralizado em Tempo Real:** O painel permite aos administradores editar painéis de regras, customizar matrizes de botões de boas-vindas, gerenciar o quadro de eventos, modular a sensibilidade do anti-raid e monitorar a fila do Publisher visualmente, com sincronização em tempo real via canal de invalidação Redis Pub/Sub (`cookiebot:cache:evict`).

---

## 4. Stack Tecnológica e Padrões de Acesso a Dados

| Camada da Arquitetura | Tecnologia Adoptada | Papel e Justificativa no Sistema |
| --- | --- | --- |
| **Linguagem & Runtime** | **Java 26 + GraalVM Native** | Compilação AOT gerando binários estáticos auto-contidos sem overhead de JVM, garantindo tempo de inicialização < 50ms e teto de memória RSS < 120 MB. |
| **Framework Core** | **Spring Boot 4.1.0 (Enxuto)** | Gestão de DI, webserver não-bloqueante e motor AOT. Mantém estritamente os starters fundamentais: `web`, `data-jpa` e `data-redis`. |
| **Concorrência & I/O** | **Virtual Threads (Project Loom)** | Orquestração de 100% das tarefas de rede, banco e HTTP. Escala para milhares de requisições concorrentes sem alocar threads de SO. |
| **Banco de Dados** | **PostgreSQL 18** | Persistência transacional relacional (ACID). Controle de schema via **Flyway** com suporte nativo a consultas transacionadas sobre colunas `JSONB`. |
| **Acesso a Dados Críticos** | **Spring `JdbcClient**` | SQL parametrizado de baixo overhead focado em caminhos quentes (hot-paths): moderação, blacklists, rate limit, filas e regras de grupo. |
| **Acesso a Dados Domínio** | **JPA / Hibernate** | Mapeamento objeto-relacional focado em gestão de usuários, papéis, permissões, eventos e cadastros administrativos do WebHub. |
| **Cache L1 / L2** | **Caffeine (L1) + Redis 7.4 (L2)** | Cache em memória local ultrarrápido (L1, TTL 5 min) apoiado por cache distribuído (L2). Sincronização via canal Redis Pub/Sub. |
| **Pool de Conexões DB** | **HikariCP Minimal / Native** | Pool JDBC enxuto calibrado para Virtual Threads (`maximum-pool-size=10`, `minimum-idle=2`), evitando esgotamento de conexões no PostgreSQL. |
| **Armazenamento Mídia** | **MinIO Storage** | Object Storage local compatível com S3. Geração de *Presigned URLs* temporárias (TTL 15 min) para transferência direta de arquivos para o Telegram. |
| **Edição Visual / Áudio** | **ImageMagick & FFmpeg (CLI)** | Processamento destrutivo de mídia executado via `ProcessBuilder` travado por semáforo (`Semaphore`), operando exclusivamente em RAM Disk (`tmpfs`). |
| **Tradução Automática** | **Argos Translate** | Motor de tradução neuronal rodando localmente com modelos quantizados, com suporte a cache relacional no banco de dados via `JdbcClient`. |
| **IA & Reconhecimento** | **Whisper.cpp & ShazamIO** | Transcrição de áudio em contêiner com ciclo de vida Scale-to-Zero e identificação musical acústica via CLI orquestrado por semáforo na JVM. |

### Contrato Híbrido de Acesso a Dados (JPA vs. JdbcClient)

A escolha do mecanismo de persistência obedece estritamente ao perfil de carga transacional:

1. **Hot-Paths (Caminhos Quentes -> OBRIGATÓRIO `JdbcClient`):** Todas as rotinas invocadas a cada mensagem de chat ou com concorrência severa utilizam SQL nativo parametrizado. Isso zera o custo computacional com *dirty checking*, interceptação de proxies e criação de objetos temporários no Heap. Aplica-se a: Blacklist Global/Local, Anti-Raid, checagem de Rate Limit, verificação de regras e leitura/escrita na fila `publisher_queue`.
2. **Domínio Administrativo (Baixo Volume -> OBRIGATÓRIO JPA):** Rotinas de cadastro estruturado, configuração de grupos (`ChatConfig`), gestão do quadro de eventos (`EventEntity`), autenticação do WebHub e controle de permissões utilizam Hibernate/JPA para manter a produtividade de modelagem objeto-relacional sem impactar a latência de moderação em tempo real.

---

## 5. Plano de Implementação Passo a Passo (15 Fases Contratuais)

A execução da engenharia deve seguir estritamente a ordem cronológica das 15 fases abaixo, sem pular etapas ou antecipar desenvolvimentos futuros.

### 📌 Fase 1: Fundação AOT, Flyway e Configuração de Pool

1. **Bootstrap do Projeto:** Configurar `pom.xml` com Spring Boot 4.1.0 e Java 26 no perfil GraalVM Native Image. Importar estritamente: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-data-redis`, driver `postgresql` e `flyway-core`.
2. **Concorrência Loom:** Ativar no `application.properties`: `spring.threads.virtual.enabled=true` e `spring.jpa.hibernate.ddl-auto=validate`.
3. **Controle de Schema Flyway:** Criar script de migração inicial `V1__init_core_schema.sql` modelando as tabelas estruturais de usuários, grupos, papéis e permissões administrativas.
4. **Calibração do Pool JDBC:** Configurar pool HikariCP enxuto: `spring.datasource.hikari.maximum-pool-size=10` e `spring.datasource.hikari.minimum-idle=2`.
5. **Orquestração Docker:** Estruturar `docker-compose.yml` declarando limites de memória rígidos (`deploy.resources.limits`) para PostgreSQL 18 (512MB), Redis 7.4 (256MB), MinIO (256MB) e Nginx (128MB).

### 📌 Fase 2: Gateway Webhook, Token de Segurança e Roteamento AOT

1. **Validação de Carga:** Implementar `TelegramTokenValidator` para validar o cabeçalho `X-Telegram-Bot-Api-Secret-Token` via comparação em tempo constante (`MessageDigest.isEqual`), bloqueando payloads ilegítimas na borda.
2. **Ingressão Não-Bloqueante (< 5 ms):** Criar o controller `POST /webhook/telegram`. A requisição delega a payload para uma Virtual Thread e retorna imediatamente `HTTP 200 OK` para o Telegram.
3. **Roteamento com Pattern Matching:** Desenvolver o `MessageRouter` aplicando Pattern Matching do Java 26 (`switch` sobre `record`), sem o uso de reflexão dinâmica:
```java
switch (update) {
    case Update(var id, Message(var msgId, var chat, var text, ...)) when text.startsWith("/") -> 
        commandRegistry.execute(text, chat, update);
    case Update(var id, CallbackQuery(var cbId, var from, var data, ...)) -> 
        callbackRegistry.process(data, from);
    default -> defaultHandler.ignore();
}

```


4. **BFF WebHub Minimalista:** Implementar endpoints REST protegidos sob o prefixo `/api/admin/*`. Criar filtro de segurança stateless via JWT RS256 gerado após validação criptográfica do login oficial do Telegram.

### 📌 Fase 3: Moderação, CAPTCHA Vetorial, Anti-Raid e Welcome Matrix

1. **CAPTCHA SVG Headless:** Implementar `SvgCaptchaGenerator` gerando primitivas vetoriais na memória em formato SVG puras, convertidas diretamente para `byte[]`, sem acoplar classes `java.awt` ou Java2D.
2. **Anti-Raid via Lua Script:** Desenvolver `RaidDetector` escrevendo um script Lua nativo executado de forma atômica no Redis via comando `EVAL`. O algoritmo de janela deslizante (`ZADD`, `ZREMRANGEBYSCORE`, `EXPIRE`) é processado diretamente no barramento de cache.
3. **Sticker Anti-Spam e Blacklists:** Criar `StickerAntiSpam` usando contadores transacionais no Redis com TTL de 10 segundos e verificação em cache L1. Implementar checagem em listas de banimento locais/globais (CAS) utilizando **exclusivamente `JdbcClient**` no hot-path.
4. **Recepção e Boas-Vindas (Welcome Matrix):** Implementar o módulo de saudações interativas. Quando um novo membro entra, o bot envia o painel de regras customizado com botões inline de concordância via *CallbackQuery*. Incluir rotina programada em Virtual Thread para exclusão automática de notificações do sistema ("Fulano entrou") e das saudações aceitas.

### 📌 Fase 4: Duplo Cache L1/L2, Pub/Sub e Governança JSONB

1. **Arquitetura Dupla (L1 + L2):** Configurar cache local em JVM (`Caffeine`, L1) focado em entidades `ChatConfig` e regras de moderação (capacidade máxima de 5.000 entradas, TTL de 5 minutos), com repasse de falhas de leitura para o Redis 7.4 (L2).
2. **Invalidação Distribuída:** Implementar listener assíncrono conectado ao canal Redis Pub/Sub `cookiebot:cache:evict`. Ao receber o ID de um chat alterado pelo WebHub ou comando no Telegram, o listener executa `caffeineCache.invalidate(chatId)` em todos os nós da aplicação.
3. **Modelagem Relacional e JSONB:** Mapear tabelas de logs e auditoria com relacionamentos normais em JPA. Na entidade `ChatConfig`, aplicar a coluna SQL `jsonb` estritamente na propriedade `customRulesMatrix` através da anotação `@Type(JsonBinaryType.class)`, armazenando matrizes dinâmicas de botões e regras customizáveis sem desnormalizar o esquema.

### 📌 Fase 5: IA Conversacional, Worker STT e Reconhecimento Acústico

1. **IA Conversacional On-Demand:** Integrar cliente HTTP conectado ao modelo LLM local auto-hospedado via `RestClient`. Implementar limitador transacional no Redis que armazena o histórico do bate-papo no canal `cookiebot:chat:context:{chatId}:{userId}` com expiração automática (TTL de 5 minutos).
2. **Worker STT Whisper.cpp:** Desenvolver `SpeechToTextService`. Ao receber uma nota de voz (`.ogg`), o serviço baixa o arquivo e envia o buffer via HTTP para o contêiner `whisper.cpp` (modelo `tiny` ou `small`). Configurar script monitor no host Docker para pausar o contêiner (`docker pause`) após 10 minutos de inatividade, aplicando a arquitetura Scale-to-Zero.
3. **Identificação Musical ShazamIO:** Implementar `MusicDetector` invocando o script CLI do ShazamIO via `ProcessBuilder`. **OBRIGATÓRIO:** A chamada de processo externo deve ser encapsulada por um semáforo estrito na JVM (`new Semaphore(5)`) para prevenir esgotamento de descritores de arquivos no SO.

### 📌 Fase 6: Engine do Publisher via State Machine SQL

1. **Modelagem da Fila Transacional:** Criar migração Flyway modelando a tabela `publisher_queue` (`id`, `chat_id`, `admin_id`, `media_url`, `caption`, `status`, `scheduled_time`, `version`). É proibida a adoção de Redis Streams ou RabbitMQ.
2. **Agendamento com Trava Relacional:** Implementar `PublisherScheduler` usando a anotação `@Scheduled(fixedRate = 30000)`. A consulta de postagens pendentes é blindada transacionalmente no PostgreSQL 18:
```sql
SELECT id, chat_id, media_url, caption FROM publisher_queue 
WHERE status = 'APPROVED' AND scheduled_time <= CURRENT_TIMESTAMP 
ORDER BY scheduled_time ASC FOR UPDATE SKIP LOCKED LIMIT 20;

```


3. **Moderação Interativa via Callbacks:** Implementar o fluxo de aprovação de posts submetidos pelo WebHub via botões inline. A concordância de um moderador aciona um evento *CallbackQuery* que executa uma atualização atômica via `JdbcClient` aplicando controle otimista na cláusula `WHERE version = :currentVersion`.

### 📌 Fase 7: Metabusca Diet, Tradução Dedicada e Interceptores Regex

1. **SearXNG em Modo Diet:** Configurar manifesto Docker do SearXNG desativando scrapers visuais e motores HTML complexos. Ativar exclusivamente as fontes JSON rápidas: `duckduckgo`, `qwant`, `wikipedia` e `bing` (orçamento travado em 128MB RAM).
2. **Tradução via Argos Translate:** Implementar cliente local de tradução conectado ao contêiner do Argos Translate. Estruturar cache relacional na tabela `translation_cache` no PostgreSQL via `JdbcClient` para verificar hashes de textos recorrentes antes de acionar a tradução neural.
3. **MemeGenerator via `ProcessBuilder` e Draining:** Criar `MemeGeneratorService` delegando a manipulação geométrica de texto sobre imagem ao binário estático do **ImageMagick (`convert`)**. A invocação do processo aciona imediatamente 2 Virtual Threads assíncronas para consumir de forma contínua os canais `stdout` e `stderr`, evitando deadlocks no buffer IPC do Linux.
4. **Interceptor Social de Alta Velocidade:** Desenvolver `SocialMediaEmbedInterceptor`. Compilar expressões regulares estaticamente na inicialização da classe (`private static final Pattern`). A substituição de links sociais (`fxtwitter`, `vxtiktok`, `ddinstagram`) é executada em microsegundos via `Matcher.replaceAll()` sobre o texto da mensagem sem alocação dinâmica no Garbage Collector.

### 📌 Fase 8: Aniversários, Sorteios e Concorrência Estruturada

1. **Concorrência Estruturada no BirthdayService:** No módulo de aniversários (rotina agendada à 00:00), aplicar **exclusivamente a API `StructuredTaskScope` do Java 26** para disparar o download paralelo dos avatares fotográficos a partir do MinIO Storage:
```java
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    List<StructuredTaskScope.Subtask<File>> tasks = birthdayUsers.stream()
        .map(user -> scope.fork(() -> minioService.downloadAvatar(user.getAvatarUrl())))
        .toList();
    scope.join();
    scope.throwIfFailed();
    generateCollageImage(tasks.stream().map(StructuredTaskScope.Subtask::get).toList());
}

```


2. **Sorteios com Bloqueio Otimista (`@Version`):** Modelar entidades `Giveaway` e `GiveawayParticipant` no JPA com anotação `@Version`. Cliques simultâneos de milhares de participantes no botão inline são resolvidos transacionalmente no banco relacional sem travas de tabela pessimistas.
3. **Emissão de Presigned URLs no MinIO:** Implementar `MinioStorageService` gerando links de acesso temporário via `getPresignedObjectUrl` (TTL 15 minutos). Mídias de publicações, colagens e sorteios são baixadas diretamente pelo Telegram do storage S3 sem consumir a largura de banda de saída do webserver do bot.

### 📌 Fase 9: Quadro de Eventos, Motores Estatísticos e Utilitários

1. **Quadro e Gestão de Eventos (Events):** Criar migração Flyway para a tabela `community_events` (`id`, `chat_id`, `title`, `description`, `event_date`, `created_by`). Implementar `EventsController` no BFF para o WebHub gerenciar os eventos. Desenvolver rotina agendada que dispara lembretes automatizados no chat em períodos pré-definidos (ex.: 24h e 1h antes do evento).
2. **Estatística Zero-Contention:** Implementar módulos recreativos (`DiceService`, roletas e sorteios rápidos) utilizando estritamente `java.util.concurrent.ThreadLocalRandom.current()`, eliminando disputas de concorrência em ambiente multithread.
3. **Utilitários Estáticos no Startup:** Estruturar `FortuneCookieService` e `DeathService` para ler arquivos estáticos de texto do classpath durante o boot do Spring Boot, carregando o conteúdo para matrizes imutáveis `String[]` na memória nativa (I/O zero de leitura em runtime). Mapear comandos personalizados na entidade `CustomCommand`, com leituras em cache L1/L2 e gravações via `JdbcClient`.

### 📌 Fase 10: Processador Destrutivo de Mídia via RAM Disk (`tmpfs`)

1. **Isolamento no Volume RAM Disk:** Configurar no Docker a montagem de um volume de sistema de arquivos na memória RAM sob a diretiva: `--tmpfs /app/tmp_media:rw,size=250m,mode=1777`. Toda gravação de áudio e vídeo em processamento destrutivo é realizada exclusivamente neste diretório.
2. **FFmpeg CLI com Draining e Timeouts:** Implementar `MediaDistortionService` orquestrando comandos do **FFmpeg via CLI** via `ProcessBuilder`. As tarefas recebem timeout rígido de CPU via temporizador nativo da JDK, executando `process.destroyForcibly()` caso a renderização ultrapasse 15 segundos.
3. **Limpeza Compulsória de Disco:** Envolver todo o ciclo de processamento multimídia em blocos de garantia `try-with-resources` ou retornos assíncronos `CompletableFuture.whenComplete()`. O código executa compulsoriamente `Files.deleteIfExists(Path.of(tmpPath))` no término, garantindo zero vazamento de arquivos no volume RAM Disk.

### 📌 Fase 11: Comunicação em Massa, Alertas e Inspeção JSON

1. **Controle de Taxa em Alertas:** Proteger comandos administrativos de menção em massa (`/everyone`, `/admincall`) implementando verificação transacional em chave Redis via script Lua, impondo intervalo de respiro mínimo de 30 minutos por grupo para evitar abusos.
2. **Engine de Broadcasting Escalável:** Desenvolver rotina de transmissão em massa `/broadcast`. O algoritmo pagina consultas relacionais e despacha envios via Virtual Threads reguladas por um rate limiter interno, fixando a vazão máxima em 30 mensagens por segundo para respeitar os limites da API do Telegram (evitando Error 429).
3. **Inspeção Bruta JSON (`/analysis`):** Criar comando de diagnóstico avançado para moderadores. Ao responder a uma mensagem com `/analysis`, o bot extrai todos os cabeçalhos, IDs de encaminhamento e metadados ocultos, serializa o objeto bruto em uma string JSON limpa gerada e enviada diretamente como bloco de código estrito (````json`) para o chat.

### 📌 Fase 12: Internacionalização (i18n) Nativa no Runtime AOT

1. **i18n via `ResourceBundleMessageSource`:** Configurar módulo de localização do Spring Boot aplicando a implementação nativa da JDK 26. Criar no classpath os arquivos pré-compilados: `messages_en.properties` (idioma global padrão), `messages_pt_BR.properties` e `messages_es.properties`.
2. **Resolução com Fallback Encadeado:** Implementar `LocaleMessageResolver` que interroga a localidade no objeto de usuário do Telegram e resolve a chave de texto aplicando degradação graciosamente encadeada em memória (`es-AR` -> `es` -> `en`), sem efetuar lookups no banco de dados.
3. **Sincronização Dinâmica do BotFather:** Programar um `ApplicationRunner` acionado no startup que executa chamadas HTTP à API oficial do Telegram através do método `setMyCommands`, enviando o vetor de comandos e descrições traduzidas para cada localidade suportada, atualizando o menu nativo no aplicativo dos utilizadores de forma sincronizada.

### 📌 Fase 13: Observabilidade Minimalista, Retries e Tuning de DB

1. **Retries em Código (Fim do Resilience4j):** Eliminar bibliotecas externas de resiliência. Construir interceptor HTTP no `RestClient` nativo que aplica retries automáticos com exponencial backoff em código puro para até 2 tentativas falhas, configurando timeouts inegociáveis de rede (`ConnectTimeout = 2000 ms`, `ReadTimeout = 5000 ms`).
2. **Otimização de Índices via Flyway:** Criar migração Flyway `V2__optimize_indexes.sql` aplicando índices compostos e seletivos transacionados no PostgreSQL 18:
```sql
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_mod_logs_chat_user 
ON moderation_logs (chat_id, user_id, created_at DESC);

```


3. **Observabilidade Leve sem Actuator:** Remover o pacote `spring-boot-starter-actuator`. Implementar controller REST simplificado na rota `/internal/metrics` que formata variáveis nativas da JVM nativa gerando saída compatível com **Prometheus** (uptime, RSS real, virtual threads ativas, taxa de hits no Caffeine, conexões JDBC).
4. **Silenciamento de I/O de Log:** Configurar `logback-spring.xml` com appender em JSON assíncrono para `stdout`. Silenciar logs de debug SQL e traços de Hibernate no perfil de produção, zerando o tráfego de escritas em disco pelo daemon do Docker.

### 📌 Fase 14: Testes de Estresse, Certificação AOT e Homologação

1. **Testes com Testcontainers:** Escrever suíte de testes de integração automatizados via **JUnit 5 e Testcontainers**, subindo instâncias efêmeras reais de PostgreSQL 18 e Redis 7.4 para certificar a correta execução das queries SQL no `JdbcClient` e a reversibilidade das migrações Flyway.
2. **Certificação de Compilação AOT:** Configurar perfil de compilação nativa no Maven executando o builder com verificação estrita:
```bash
mvn clean package -Pnative -DskipTests -Dspring.aot.enabled=true

```


Auditar manualmente o arquivo gerado `META-INF/native-image/reachability-metadata.json`, certificando a ausência de chamadas de reflexão ou proxies dinâmicos não mapeados no build.
3. **Homologação e Estresse (K6):** Submeter o contêiner AOT gerado a testes de estresse intensivo utilizando o **K6**, injetando 3.000 requisições simultâneas por segundo contra o endpoint do webhook. Certificar em relatório técnico o cumprimento dos SLAs: consumo RSS constante **abaixo de 120 MB**, latência P95 < 5 ms e zero vazamento de memória no Heap após simulação de 72 horas contínuas.

### 📌 Fase 15: Esteira Multi-Stage Distroless e Rotinas de Operação

1. **Dockerfile Multi-Stage Ultraleve:**
* **Stage 1 (Builder):** Imagem oficial `ghcr.io/graalvm/native-image-community:26` executando compilação AOT limpa.
* **Stage 2 (Runtime):** Imagem base minimalista **Docker Distroless (`gcr.io/distroless/static-debian12`)**. Copiar estritamente o binário nativo compilado, os binários estáticos CLI do FFmpeg/ImageMagick e o pacote raiz de certificados SSL/TLS do SO. O tamanho final da imagem implantada deve selar entre **40 MB e 50 MB**.


2. **Protocolo Graceful Shutdown:** Configurar a propriedade `server.shutdown=graceful` no Spring Boot com tempo limite de respiro de `30s`. Ao interceptar o sinal `SIGTERM` do Docker, o webserver cessa a aceitação de novas conexões, aguarda a finalização das tarefas nas Virtual Threads ativas e devolve todas as conexões ao pool JDBC do PostgreSQL de forma segura.
3. **Rotina de Backup Transacional (Disaster Recovery):** Estruturar no host físico fora dos contêineres um script Shell automatizado via Cron agendado à 03:00 UTC. O script executa `pg_dump` no contêiner do PostgreSQL 18 para extrair o dump transacional compactado, realiza sincronização delta dos buckets de mídia do MinIO para uma unidade de armazenamento externa a frio e expira backups com mais de 30 dias, garantindo RTO inferior a 5 minutos durante os 10 anos do projeto.

---

## 6. Instruções e Proibições para Agentes de IA (AI Directives)

Seção contratual de governança. Agentes de IA (Claude Code, Codex, Cursor Agent, OpenHands, Gemini CLI) e desenvolvedores **DEVEM** obedecer inegociavelmente às regras abaixo durante a leitura, refatoração ou geração de código deste projeto.

### 1. Ordem de Prioridade de Execução

Em qualquer trade-off de implementação, aplique a seguinte ordem de prioridade decrescente:

1. **Correção (Correctness):** O código deve cumprir a regra do domínio sem exceções silenciosas ou vazamento de recursos.
2. **Simplicidade:** Menos partes móveis, menos classes e menos indireções vencem. Código explícito é superior a código "inteligente".
3. **Footprint de Memória (Memory First):** A menor alocação no Heap da JVM e a garantia de compilação GraalVM AOT vencem.
4. **Performance e Throughput:** Otimização para I/O não-bloqueante via Virtual Threads no hot-path.
5. **Legibilidade:** Código limpo e autodocumentado conforme padrões da JDK 26.
6. **Conveniência / Produtividade:** A facilidade imediata de escrever ou o uso de frameworks mágicos é a **ÚLTIMA** prioridade. Escreva 150 linhas de código nativo da JDK em vez de importar uma biblioteca genérica externa se isso economizar RAM e proteger o binário nativo.

### 2. Proibições Arquiteturais Estritas (The "DO NOT" List)

| Tecnologia / Padrão Proibido | Justificativa do Banimento | Alternativa Obrigatória na Stack |
| --- | --- | --- |
| **Lombok** (`@Data`, `@Builder`, etc.) | Quebra o processamento nativo e oculta alocações de memória com geração de bytecode imprevisível. | **Java Records** nativos, construtores explícitos e getters padrão da JDK. |
| **MapStruct / ModelMapper** | Adiciona complexidade de reflexão/geração de código desnecessária para mapeamento de DTOs. | Construtores de cópia ou métodos estáticos explicítos de mapeamento (`DTO.fromEntity()`). |
| **Apache Commons / Guava** | Biblioteca incha o binário nativo e polui o classpath com utilitários redundantes. | **APIs nativas da JDK 26** (`java.util`, `java.time`, `java.net.http`, `Files`, `Path`). |
| **Gson / Jackson XML / Fastjson** | Motores de serialização baseados em reflexão pesada que exigem extensos arquivos de configuração AOT. | **Jackson JSON Core** otimizado com Spring AOT Engine (geração estática no build). |
| **Reflexão Manual (`java.lang.reflect`)** | Destrói a análise de reachability do GraalVM Native Image e exige fallback em runtime. | **Pattern Matching estático** (`switch` sobre `record`), polimorfismo e interfaces. |
| **Geração de Bytecode em Runtime** | CGLIB, Javassist ou Byte Buddy são absolutamente incompatíveis com binários nativos AOT. | Proxies estáticos gerados no tempo de compilação via Spring AOT. |
| **Thread Pools Tradicionais** | `ThreadPoolExecutor` ou `Executors.newFixedThreadPool()` desperdiçam threads físicas do SO. | **Virtual Threads** (`Thread.ofVirtual()`, `Executors.newVirtualThreadPerTaskExecutor()`). |
| **CompletableFuture / ForkJoinPool** | Encadeamento assíncrono complexo e uso de `ForkJoinPool.commonPool()` causam contenção de CPU. | **Structured Concurrency** (`StructuredTaskScope`) nativa do Java 26 ou Virtual Threads síncronas. |
| **MongoDB / Elasticsearch / Kafka** | Infraestrutura pesada que viola o teto de memória da stack auto-hospedada (< 1.5 GB). | **PostgreSQL 18** (relacional + `JSONB`) para persistência e filas transacionadas. |
| **RabbitMQ / Redis Streams** | Complexidade de mensageria desnecessária para o volume e padrão de eventos do Telegram. | Tabela `publisher_queue` no PostgreSQL + **Redis Pub/Sub** apenas para invalidação de cache. |
| **Quartz Scheduler** | Scheduler pesado com tabelas próprias e serialização excessiva no banco. | Anotação nativa **`@Scheduled`** do Spring + trava transacional SQL (`FOR UPDATE SKIP LOCKED`). |
| **Bucket4j / Resilience4j** | Bibliotecas externas de Rate Limit e Circuit Breaker adicionam classes e consumo de heap. | **Scripts Lua nativos no Redis** (via `EVAL`) e retries/timeouts implementados via `RestClient`. |

### 3. Padrões de Código, Limites e Proibições

* **Admissão de Bibliotecas:** Nenhuma dependência entra no `pom.xml` sem ter manutenção ativa (últimos 6 meses), ser 100% compatível com GraalVM AOT sem reflexão manual e resolver um problema algorítmico complexo. Caso contrário, implemente nativamente em código JDK.
* **Limites Quantitativos de Tamanho:**
* **Classe:** Máximo de **400 linhas** de código.
* **Método:** Máximo de **40 linhas** por método. Fatie métodos longos em submétodos privados sem estado.
* **Construtor:** Máximo de **10 parâmetros / dependências** injetadas. Mais do que isso viola o Princípio de Responsabilidade Única (SRP).


* **Proibição Absoluta de Classes "Util" ou "Helper":** É proibido criar classes chamadas `Utils.java`, `CommonUtils.java`, `Helpers.java`, `AppUtils.java` ou `GenericService.java`. Todo código pertence a um conceito claro do domínio (ex.: em vez de `DateUtils`, crie `TimeWindowCalculator` no pacote de moderação).
* **Herança vs. Composição:** É proibido criar hierarquias de herança profundas (ex.: `AbstractBotService` -> `AbstractTelegramService` -> `AbstractCommandService` -> `BirthdayService`). Limite a herança a no máximo 1 nível e apenas para relações polimórficas reais. Favoreça sistematicamente **Composição sobre Herança**.
* **Injeção de Dependência:** Use **exclusivamente Constructor Injection**. É estritamente proibido usar `@Autowired` em campos (*Field Injection*) ou setters, bem como instanciar serviços com o operador `new` dentro de outros componentes Spring.
* **Otimização de Memória (Memory First):**
* Priorize primitivos (`int`, `long`, `boolean`, `double`) sobre classes wrapper (`Integer`, `Long`, `Boolean`) para evitar autoboxing e alocação no Heap.
* Proibido usar `Optional` como campo de classe, parâmetro de método ou dentro de loops quentes.
* Proibido o uso de Java Streams (`list.stream().filter(...).collect(...)`) em Hot-Paths executados milhares de vezes por segundo (ex.: roteamento e anti-spam). Use loops tradicionais (`for-each` ou `for` indexado).
* Todas as expressões regulares devem ser obrigatoriamente compiladas na inicialização como constantes estáticas imutáveis (`private static final Pattern`). Proibido compilar Regex dinamicamente dentro de métodos em execução de requisição.


* **Logging e Privacidade:** Proibido logar em nível `INFO` ou `DEBUG` o JSON integral de payloads recebidas do Telegram em ambiente de produção. Utilize `INFO` exclusivamente para startup, shutdown, alterações administrativas no WebHub e conclusão de rotinas agendadas.
* **Regra Transacional SQL:** Nunca execute consultas SQL com `SELECT *`. Declare explicitamente os nomes de todas as colunas necessárias. Evite consultas N+1 utilizando `JOIN` ou projeções. Em JPA, todos os relacionamentos (`@OneToMany`, `@ManyToOne`) devem ser obrigatoriamente configurados com `fetch = FetchType.LAZY`.

### 4. A Constituição Suprema do Projeto

> ***"Toda decisão arquitetural, escrita de código e refatoração futura no projeto CookieBot Self-Hosted DEVE preservar estritamente a simplicidade estrutural, o footprint de memória minimizado (RSS < 120 MB) e a compatibilidade nativa com a compilação estática Ahead-Of-Time (GraalVM Native Image). Nenhuma biblioteca, framework ou padrão de projeto pode ser introduzido na base de código apenas por conveniência, estética ou abstração sintática. Toda dependência admitida deve justificar de forma quantificável e inegociável seu impacto no consumo de memória, tempo de inicialização, complexidade de build e custo de manutenção para um horizonte contínuo de 10 anos. Em qualquer caso de conflito entre produtividade de desenvolvimento e desempenho de execução, deve-se priorizar o desempenho apenas quando houver microbenchmark comprovando ganho mensurável em hot-path; caso contrário, a simplicidade arquitetural vence. A linguagem Java 26, suas APIs padronizadas e o motor transacional do PostgreSQL 18 devem ser considerados a primeira e principal opção para solucionar qualquer desafio algorítmico antes da introdução de qualquer código de terceiros."***

**Fim da Especificação de Engenharia.**