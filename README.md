# LGABOT - CookieBot Core API

API Core do **CookieBot**, desenvolvida com foco em altíssima performance, eficiência de recursos e observabilidade em produção. A aplicação utiliza o ecossistema moderno do **Spring Boot 4.1.0** e **Java 26**, encapsulada em uma infraestrutura conteinerizada com compilação nativa Ahead-Of-Time (AOT).

---

## 🛠️ Stack Tecnológica & Arquitetura

A arquitetura do projeto foi estruturada para garantir escalabilidade horizontal, resiliência de dados e proteção de rede:

*   **Linguagem & Runtime:** Java 26 & GraalVM Native Build Tools (imagens nativas focadas em baixo consumo de RAM e inicialização em milissegundos).
*   **Framework Base:** Spring Boot 4.1.0 (aproveitando otimizações avançadas de análise estática para AOT).
*   **Persistência & Cache:** Spring Data JPA (PostgreSQL) & Spring Data Redis.
*   **Agendamento:** Quartz Scheduler para tarefas assíncronas distribuídas.
*   **Segurança:** Spring Security + Gateway Nginx com terminação SSL (HTTPS).
*   **Observabilidade:** Spring Boot Actuator & Micrometer Prometheus Registry.

---

## 📋 Plano Geral

O objetivo é consolidar a API Core como uma infraestrutura de alta performance, segura e totalmente monitorada. A estratégia central consiste em utilizar o Java 26 e o Spring Boot 4.1.0 para gerar um binário nativo via GraalVM, eliminando o peso da JVM tradicional em produção. A arquitetura completa conta com persistência em PostgreSQL, cache em Redis, agendamentos com Quartz e uma camada externa de segurança e proxy reverso controlada pelo Nginx com SSL, onde todas as métricas internas são expostas de forma restrita via Actuator para coleta do Prometheus.

## ⚡ Status Atual

O ambiente de desenvolvimento está validado. O projeto já compila localmente com sucesso usando o Java 26, com o bytecode do Hibernate otimizado e as dependências core resolvidas. Além disso, as regras de roteamento, terminação SSL e o bloqueio de segurança das rotas do Actuator já foram totalmente estruturados no arquivo de configuração do Nginx. O próximo passo imediato é a criação do Dockerfile multi-stage focado na compilação nativa.

---

## ⚙️ Como Executar o Projeto Localmente (Modo Desenvolvimento)

Por conta da pesada análise estática da compilação nativa, o desenvolvimento diário deve ser feito utilizando a JVM tradicional.

### Pré-requisitos
*   JDK 26 configurado na máquina (`JAVA_HOME` apontando para a versão 26).
*   Maven 3.9+ integrado ao Path.

### Comandos de Build Inicial
Para limpar o workspace, otimizar o bytecode do Hibernate e gerar o artefato JAR executável pulando temporariamente os testes de banco de dados (que subirão apenas no Docker):

```bash
mvn clean package -DskipTests