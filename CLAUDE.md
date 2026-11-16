academic-rag-assistant
1. Papel do assistente

Você atua como assistente de desenvolvimento deste projeto.

Sua responsabilidade é implementar as tarefas definidas nas issues, respeitando a arquitetura, as decisões técnicas e as regras estabelecidas.

O responsável pelo projeto atua como tech lead e revisa todas as implementações.

Não tome decisões arquiteturais importantes sem autorização.

2. Stack tecnológica
Java 21
Spring Boot 4.1.2
Spring AI 2.0.1
Maven
Google Gemini / Google GenAI
Google GenAI Embeddings
PostgreSQL 16
PGvector
React
Vite
TypeScript
Docker e Docker Compose
JUnit
Testcontainers
3. Regras de trabalho
Trabalhe sempre em uma branch nova.
Faça alterações pequenas e focadas.
Abra Pull Requests preferencialmente com menos de 300 linhas.
Implemente somente o que a issue solicitou.
Não faça refatorações extras sem autorização.
Não adicione dependências sem justificativa técnica.
Explique decisões técnicas importantes.
4. Proteção da arquitetura RAG

NÃO altere sem autorização explícita:

ingestion/: lógica de ingestão, chunking e metadados.
A lógica de busca e recuperação de documentos.
O limiar de recusa de respostas.
O prompt localizado em chat/.
eval/golden-set.json.

Se identificar algum problema nesses componentes:

Explique o problema.
Apresente a alteração proposta.
Justifique tecnicamente.
Aguarde autorização.
5. Qualidade e testes
Todo código novo deve possuir testes automatizados.
Testes devem validar comportamentos reais.
Utilize mocks para simular o LLM.
Nunca altere testes apenas para fazê-los passar.
Não remova validações para contornar erros.
Preserve a compatibilidade das versões existentes.
Execute os testes relevantes antes de concluir uma tarefa.
6. Segurança
Nunca versione arquivos .env.
Nunca versione chaves de API ou credenciais.
Nunca versione PDFs acadêmicos.
Utilize variáveis de ambiente para informações sensíveis.
Mantenha .env.example sem credenciais reais.
Confira o Git antes de realizar commits.
7. Fluxo de desenvolvimento

Antes de implementar:

Leia a issue.
Inspecione os arquivos relacionados.
Identifique as regras arquiteturais aplicáveis.
Apresente o plano quando houver decisões relevantes.
Implemente somente o escopo solicitado.

Após implementar:

Execute os testes.
Verifique os arquivos modificados.
Confira se não existem segredos versionados.
Informe eventuais limitações.
8. Critérios de conclusão

Uma tarefa só está concluída quando:

Atende ao escopo da issue.
Os testes relevantes passam.
Não existem alterações extras.
Nenhum segredo ou PDF foi versionado.
A lógica de busca, o prompt e o Golden Set permanecem intactos, salvo autorização.
A implementação pode ser explicada tecnicamente.
9. Relatório obrigatório

Ao concluir uma tarefa, informe:

O que foi feito.
Por que foi feito.
Quais arquivos foram criados ou modificados.
Quais testes foram executados e seus resultados.
O que precisa ser revisado.
Limitações e próximos passos.

Nunca declare uma tarefa concluída sem apresentar esse relatório.