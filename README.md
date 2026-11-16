Academic RAG Assistant

Assistente acadêmico inteligente baseado em Retrieval-Augmented Generation (RAG), desenvolvido para responder dúvidas acadêmicas da UniFBV utilizando documentos oficiais em PDF como fonte de conhecimento.

O sistema busca informações nos documentos disponibilizados, gera respostas contextualizadas com Google Gemini e apresenta referências ao documento e à página de origem.

Objetivo

Facilitar o acesso às informações acadêmicas institucionais por meio de uma interface conversacional, garantindo respostas fundamentadas em fontes documentais.

O projeto também tem como objetivo demonstrar a aplicação prática de arquiteturas RAG, bancos vetoriais, modelos de linguagem e engenharia de software.

Funcionalidades planejadas
Upload de documentos acadêmicos em PDF.
Extração de conteúdo textual.
Divisão de documentos em chunks.
Preservação de metadados e números de página.
Geração de embeddings com Google GenAI.
Armazenamento vetorial com PostgreSQL e PGvector.
Busca semântica nos documentos.
Respostas contextualizadas com Google Gemini.
Citação do documento e da página utilizada.
Recusa de respostas sem evidências suficientes.
Interface conversacional.
Avaliação da qualidade das respostas.
Arquitetura

O sistema utiliza uma arquitetura RAG (Retrieval-Augmented Generation).

Fluxo planejado:

text
              DOCUMENTOS PDF
                    |
                    v
             EXTRAÇÃO DE TEXTO
                    |
                    v
              CHUNKING
                    |
                    v
               EMBEDDINGS
                    |
                    v
          POSTGRESQL + PGVECTOR
                    |
                    |
USUÁRIO ---> PERGUNTA
                    |
                    v
             BUSCA SEMÂNTICA
                    |
                    v
           CONTEXTO RECUPERADO
                    |
                    v
               GOOGLE GEMINI
                    |
                    v
          RESPOSTA FUNDAMENTADA
                    |
                    v
          DOCUMENTO + PÁGINA

Tecnologias utilizadas
Backend
Java 21
Spring Boot 4.1.2
Spring AI 2.0.1
Maven
Inteligência Artificial
Google Gemini
Google GenAI Embeddings
Retrieval-Augmented Generation (RAG)
Banco de dados
PostgreSQL 16
PGvector
Frontend
React
Vite
TypeScript
Infraestrutura e testes
Docker
Docker Compose
JUnit
Testcontainers
Estrutura do projeto
text
academic-rag-assistant/
│
├── backend/              # API e processamento RAG
├── frontend/             # Interface React
├── docs/                 # Documentação acadêmica
├── eval/
│   ├── results/          # Resultados das avaliações
│   └── golden-set.json   # Conjunto de avaliação
├── scripts/              # Scripts auxiliares
│
├── .gitignore
├── CLAUDE.md
└── README.md

Pré-requisitos

Para executar o projeto, serão necessários:

Java 21
Maven
Node.js e npm
Docker Desktop
Chave de API do Google Gemini
Configuração

As configurações sensíveis devem ser fornecidas por variáveis de ambiente.

Exemplo de variáveis necessárias:

env
GEMINI_API_KEY=sua_chave_aqui


Nunca versione arquivos .env ou credenciais reais.

Executando o projeto
Backend

Entre na pasta:

bash
cd backend


Execute os testes:

bash
./mvnw verify


No Windows:

powershell
.\mvnw.cmd verify


Execute a aplicação:

powershell
.\mvnw.cmd spring-boot:run

Frontend

Entre na pasta:

bash
cd frontend


Instale as dependências:

bash
npm install


Execute em desenvolvimento:

bash
npm run dev

Docker

Quando o Docker Compose estiver configurado:

bash
docker compose up

Testes e avaliação

O projeto utiliza testes automatizados para validar o comportamento da aplicação.

JUnit para testes unitários.
Spring Boot Test para testes de integração.
Testcontainers para PostgreSQL e PGvector.
Golden Set para avaliação das respostas do assistente.

Para executar os testes do backend:

bash
cd backend
./mvnw verify


Para executar a avaliação:

bash
bash scripts/run-eval.sh

Segurança e privacidade
Documentos acadêmicos não devem ser versionados.
Chaves de API devem permanecer em variáveis de ambiente.
Arquivos .env não devem ser enviados ao GitHub.
As respostas devem ser fundamentadas nos documentos disponíveis.
O sistema deve evitar apresentar informações sem evidências documentais.
Status do desenvolvimento

<details>
<summary>Infraestrutura inicial</summary>

Repositório GitHub criado.
Estrutura inicial de diretórios.
Projeto Spring Boot gerado.
Configuração inicial do Spring AI.
Integração inicial com PGvector.
Testcontainers configurado.
Primeiro teste de inicialização aprovado.

</details>

As funcionalidades de ingestão, recuperação, chat e frontend ainda serão implementadas.

Licença

A definir.