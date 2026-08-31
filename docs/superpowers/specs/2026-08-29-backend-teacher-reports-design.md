# Relatórios do professor — design do backend

## Objetivo

Adicionar consultas JSON para que um professor acompanhe os resultados de uma sala, sem exportação CSV nesta entrega. Todos os dados devem ser limitados às salas do professor autenticado e calculados no PostgreSQL por consultas dedicadas.

## Escopo

Esta entrega inclui:

- visão geral da sala;
- progresso agregado dos alunos;
- histórico detalhado das tentativas de um aluno;
- ranking filtrável da sala;
- índices necessários para sustentar essas consultas.

Exportação CSV, relatórios materializados e alterações nas regras de pontuação ficam fora do escopo.

## Endpoints

Todos exigem autenticação com papel `TEACHER`:

- `GET /teacher/reports/overview`
- `GET /teacher/reports/students`
- `GET /teacher/reports/students/{studentId}/attempts`
- `GET /teacher/reports/ranking`

### Filtros comuns

- `roomId`: UUID obrigatório em todos os endpoints.
- `lessonId`: UUID opcional. A aula deve pertencer ao professor e estar atribuída à sala selecionada.
- `from` e `to`: instantes ISO 8601 UTC, obrigatoriamente informados em conjunto. O intervalo é inclusivo em `from` e exclusivo em `to`, isto é, `[from, to)`.
- `period=ALL`: remove o limite temporal.
- sem `period`, `from` ou `to`: usa os 30 dias anteriores ao instante atual.

`period=ALL` não pode ser combinado com datas. `from` deve ser anterior a `to`. O cálculo do período padrão usa um `Clock` injetável para permitir testes determinísticos.

## Arquitetura

O fluxo será:

`TeacherReportController` → `TeacherReportService` → repositórios de sala/aula e `TeacherReportQueryRepository` → PostgreSQL.

O controller converte e valida a forma dos parâmetros HTTP. O serviço valida o professor ativo, a propriedade da sala, o vínculo opcional da aula e o período, produzindo um `ReportFilter` imutável. O repositório de consultas executa SQL parametrizado e devolve projeções, sem carregar entidades ou agregar coleções em memória.

As dependências permanecem nas camadas técnicas existentes. Entidades JPA não serão expostas pela API. Campos de ordenação serão traduzidos por uma lista permitida; nomes recebidos pela API nunca serão interpolados diretamente no SQL.

## Visão geral

`GET /teacher/reports/overview` devolve:

- `activeStudentCount`: alunos ativos atualmente na sala;
- `participatingStudentCount`: alunos com ao menos uma tentativa finalizada no filtro;
- `averageRoomXp`: média do XP atual dos alunos ativos, independentemente do filtro temporal;
- `completionRatePercent`: pares aluno ativo/atividade publicada que possuem ao menos uma aprovação, divididos por todos os pares possíveis;
- `averageBestStars`: média do melhor número de estrelas por par aluno/atividade concluído;
- `attemptSeries`: quantidade de tentativas finalizadas por dia, agrupadas em UTC;
- `scoreDistribution`: tentativas nas faixas `0–49`, `50–69`, `70–89` e `90–100`;
- `lessonPerformance`: aula, participantes, tentativas, nota média e taxa de aprovação.

Os filtros temporal e de aula afetam participantes, série, distribuição e desempenho. O filtro de aula também restringe a base de atividades usada nos cálculos de conclusão e melhores estrelas. `averageRoomXp` sempre representa o estado atual da sala.

Quando não houver denominador ou observações, percentuais e médias serão `0`, evitando valores nulos nos contratos.

## Lista de alunos

`GET /teacher/reports/students` inclui todos os alunos ativos da sala, mesmo sem tentativas no filtro. Cada item contém:

- `studentId`, `fullName`, `registrationNumber` e `email`;
- estado atual: `totalXp`, `level`, `totalStars`, `completedAssignments`, `passedAssignments` e `lastActivityAt`;
- métricas filtradas: `attemptCount`, `averageScorePercent` e `bestScorePercent`.

Métricas filtradas sem tentativas valem `0`; `lastActivityAt` pode ser nulo.

A resposta é paginada com `page=0`, `size=20` e máximo de `100`. A ordenação padrão é `totalXp,desc`. São aceitos apenas `fullName`, `totalXp`, `totalStars`, `passedAssignments`, `lastActivityAt`, `attemptCount` e `averageScorePercent`. Empates recebem ordenação estável por UUID do aluno.

## Tentativas do aluno

`GET /teacher/reports/students/{studentId}/attempts` retorna apenas tentativas finalizadas do aluno na sala e no filtro. Cada item contém:

- `attemptId`, `lessonId`, `lessonTitle`, `assignmentId`, `sequence` e `status`;
- `startedAt`, `submittedAt` e `durationSeconds`;
- `totalQuestions`, `answeredQuestions`, `correctAnswers`, `scorePercent`, `passed`, `starsEarned` e `xpCredited`;
- respostas com enunciado congelado, tipo, resposta registrada, correção, chave de resposta e explicação do snapshot, além de `answeredAt`.

A resposta é paginada, com tamanho padrão `20`, máximo `100` e ordenação padrão `submittedAt,desc`. Para evitar N+1, o repositório primeiro busca a página de tentativas e depois carrega todas as respostas dos IDs dessa página em uma única consulta.

## Ranking

`GET /teacher/reports/ranking` inclui todos os alunos ativos da sala, inclusive os que tenham zero no filtro. Cada item contém posição, identidade do aluno, XP, estrelas e o instante de conclusão usado no desempate.

- XP: soma de `xpCredited` das tentativas finalizadas no filtro.
- Estrelas: soma do melhor número de estrelas obtido por aluno e atividade dentro do filtro.
- Ordem: XP decrescente, estrelas decrescentes, conclusão mais antiga e UUID crescente.

Alunos sem conclusão ficam depois de alunos com os mesmos XP e estrelas que possuam conclusão. A posição é sequencial na ordem final. O ranking não é paginado porque representa a classificação completa da sala.

## Segurança e erros

O serviço valida o escopo antes de executar consultas analíticas. Recursos fora do escopo são ocultados como inexistentes para impedir enumeração.

- `400 BAD_REQUEST`: UUID, data, enum, paginação ou ordenação malformada.
- `401 UNAUTHORIZED`: autenticação ausente ou inválida.
- `403 FORBIDDEN`: usuário sem papel `TEACHER`.
- `404 ROOM_NOT_FOUND`: sala inexistente ou de outro professor.
- `404 LESSON_NOT_FOUND`: aula inexistente, de outro professor ou não atribuída à sala.
- `404 STUDENT_NOT_FOUND`: aluno inexistente ou sem vínculo ativo com a sala.
- `422 VALIDATION_ERROR`: somente uma das datas, datas em ordem inválida ou `ALL` combinado com datas.

Os erros usam o `ProblemDetail` já adotado pelo backend.

## Persistência e desempenho

Uma migration Flyway adicionará somente índices comprovadamente úteis aos predicados e junções dos relatórios. Não haverá nova tabela de negócio. As consultas pesadas usarão projeções e agregações no banco, com filtros parametrizados.

As consultas de overview, alunos e ranking serão dedicadas. A consulta de tentativas será dividida entre resumos paginados e respostas da página. A implementação deve evitar carregamento integral de tentativas ou respostas e manter paginação estável.

## Testes

### Serviço

Testes unitários cobrirão período padrão, `ALL`, combinações inválidas, autorização, vínculo sala/aula/aluno e montagem do filtro imutável.

### Repositório

Testes de integração com PostgreSQL/Testcontainers cobrirão:

- agregações de todas as métricas;
- limite temporal `[from, to)` e agrupamento diário UTC;
- exclusão de tentativas não finalizadas;
- alunos sem tentativas;
- filtro de aula;
- paginação, ordenação permitida e desempates determinísticos;
- melhores estrelas por aluno/atividade;
- carregamento das respostas em lote.

### HTTP

Testes dos controllers cobrirão autenticação, papel, códigos `400`, `404` e `422`, filtros e estrutura das quatro respostas.

## Critérios de aceite

- Os quatro endpoints seguem os contratos e regras definidos neste documento.
- Um professor não consegue observar dados de outra conta.
- Alunos ativos sem tentativas aparecem com métricas zeradas e no ranking.
- Apenas tentativas finalizadas influenciam relatórios.
- A série temporal é agrupada em UTC e respeita `[from, to)`.
- As respostas detalhadas preservam os snapshots históricos.
- As consultas agregam no PostgreSQL e não introduzem N+1.
- A migration contém apenas índices relacionados às consultas.
- A especificação OpenAPI é atualizada se houver documentação explícita desses endpoints no projeto.
- A suíte completa do backend passa sem regressões.
