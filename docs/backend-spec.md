# Conta Certa — Especificação do Backend

Status: aprovado para implementação
Versão: 1.0
Data: 2026-08-15

## 1. Objetivo e arquitetura

O backend é a fonte de verdade do Conta Certa. Ele implementa autenticação, autorização, conteúdo, salas, avaliações, gamificação, relatórios, e-mail e arquivos para três perfis: `ADMIN`, `TEACHER` e `STUDENT`.

A solução existente usa Java 21, Spring Boot 4.1, Spring MVC, JPA, PostgreSQL e Flyway. A arquitetura será um monólito modular em camadas:

```text
controller -> service -> repository -> PostgreSQL
                 |----> mail/storage adapters
```

- Controllers validam transporte e convertem DTOs; nunca acessam repositories.
- Services concentram casos de uso, autorização contextual e transações.
- Repositories encapsulam persistência.
- Entidades JPA nunca são expostas pela API.
- Módulos sugeridos: `auth`, `user`, `institution`, `room`, `content`, `assessment`, `gamification`, `report`, `media`, `notification` e `shared`.
- A implementação deve adicionar Spring Security, suporte JWT, envio de e-mail, documentação OpenAPI e Testcontainers; versões concretas ficam sob gerenciamento compatível com o Spring Boot usado pelo projeto.

## 2. Convenções normativas da API

- Base URL `/api/v1`; JSON e recursos em inglês e `camelCase`.
- UUID para IDs.
- `Instant` em ISO 8601 UTC; timezone de apresentação é responsabilidade do cliente.
- `LocalDate` no formato `YYYY-MM-DD`.
- Percentuais são inteiros de 0 a 100.
- Valores decimais usam `BigDecimal` e trafegam como string quando representam dinheiro ou resposta numérica.
- Paginação: `page=0`, `size=20`, máximo `100`, e `sort=field,asc|desc` em campos permitidos.
- Listagens retornam `{ content, page, size, totalElements, totalPages }`.
- Criações retornam `201 Created` e header `Location`; operações assíncronas de e-mail podem retornar `202 Accepted`; exclusões sem corpo retornam `204 No Content`.
- Erros usam RFC 9457/Problem Details com `code`, `timestamp`, `traceId` e `fieldErrors`.

Enums:

```text
Role = ADMIN | TEACHER | STUDENT
AccountStatus = PENDING | ACTIVE | INACTIVE
Grade = HIGH_SCHOOL_1 | HIGH_SCHOOL_2 | HIGH_SCHOOL_3
ContentStatus = DRAFT | PUBLISHED | ARCHIVED
QuestionType = SINGLE_CHOICE | MULTIPLE_CHOICE | TRUE_FALSE | NUMERIC
AttemptStatus = IN_PROGRESS | SUBMITTED | EXPIRED
MaterialKind = FILE | EXTERNAL_LINK
MediaViewType = VIDEO | MATERIAL
```

Recursos mutáveis importantes usam optimistic locking (`@Version`) e expõem `version`. Versão divergente retorna `409 VERSION_CONFLICT`.

## 3. Modelo de domínio e persistência

### 3.1 Identidade

`users`

- `id`, `role`, `status`, `full_name`, `email` único case-insensitive, `password_hash`.
- `registration_number` obrigatório para professor e aluno.
- `institution_id` obrigatório para professor e aluno, nulo para admin global.
- `email_verified_at`, `must_change_password`, `created_at`, `updated_at`, `version`.
- Desativar usuário revoga todas as sessões imediatamente.

`institutions`

- `id`, `name`, `cnpj` único normalizado com 14 dígitos, `contact_email`, `contact_phone`, `active`, timestamps e `version`.
- Instituição vinculada a qualquer histórico não é apagada; apenas desativada.
- Instituição inativa não aceita novos usuários ou salas, mas vínculos existentes continuam acessíveis.

`auth_sessions`

- Uma linha por dispositivo/sessão: hash do refresh token, usuário, expiração, revogação, criação, último uso e metadados mínimos de auditoria.
- Refresh token é armazenado apenas como hash e rotacionado atomicamente.

`action_tokens`

- Tokens de confirmação, convite e recuperação armazenados como hash, com tipo, usuário/e-mail, expiração e uso único.

### 3.2 Salas e matrículas

`rooms`

- `id`, `teacher_id`, `institution_id`, `name`, `description`, `grade`, `passing_score_percent` padrão 50, `join_code_hash`, `join_code_display`, `archived_at`, timestamps e `version`.
- `room_topics(room_id, topic)` preserva a lista ordenada de temas.
- Cada sala tem exatamente um professor.
- Professor, aluno e sala devem pertencer à mesma instituição.

`room_memberships`

- `room_id`, `student_id`, `status` (`ACTIVE` ou `REMOVED`), `joined_at`, `removed_at`, `removed_by`.
- Par `(room_id, student_id)` é único. Reingresso reativa a matrícula e restaura todo o histórico.
- Aluno não pode encerrar a própria matrícula.

O código da sala é único, permanente e regenerável. A regeneração invalida o código anterior sem afetar matrículas existentes.

### 3.3 Conteúdo

`lessons`

- `id`, `teacher_id`, `title`, `summary`, `theory_markdown`, `status`, timestamps e `version`.
- Não há lições padrão nem biblioteca compartilhada.
- Professor só acessa o próprio acervo; duplicação cria novo agregado independente.

`questions`

- `id`, `lesson_id`, `type`, `prompt`, `explanation`, `position`, `active`, configuração específica por tipo, timestamps e `version`.
- Opções ficam em `question_options(id, question_id, text, correct, position)`.
- `SINGLE_CHOICE`: exatamente uma opção correta.
- `MULTIPLE_CHOICE`: pelo menos duas corretas; exige conjunto exato, sem crédito parcial.
- `TRUE_FALSE`: resposta booleana.
- `NUMERIC`: `correct_value`, `absolute_tolerance >= 0`, `unit` (`BRL`, `PERCENT`, `NONE`) e `decimal_places`.
- Questão com snapshot histórico é desativada logicamente, nunca removida fisicamente.

`lesson_assignments`

- Liga uma lição reutilizável a uma sala.
- Campos: `id`, `room_id`, `lesson_id`, `position`, `status`, `available_from`, `due_at`, `time_limit_minutes`, `max_attempts`, `question_count`, `shuffle_questions`, `shuffle_options`, timestamps e `version`.
- Padrões: 30 minutos e 3 tentativas; `null` representa sem limite.
- `question_count = null` seleciona todas as questões ativas.
- Publicação exige ao menos uma questão ativa e quantidade disponível suficiente.
- A ordem define pré-requisito sequencial: a atribuição anterior precisa estar aprovada.

`videos` e `materials`

- Pertencem a um professor e possuem `title`, `description`, `status`, timestamps e `version`.
- Vídeo guarda URL externa validada.
- Material tem `kind`; link externo ou referência a arquivo privado.
- `media_assignments` liga vídeo/material a uma sala e, opcionalmente, a uma lição.
- `media_views` é único por aluno, sala, tipo e mídia; guarda `first_viewed_at`, `last_viewed_at` e contagem de aberturas.

### 3.4 Avaliações e progresso

`attempts`

- `id`, `assignment_id`, `student_id`, sequência, status, `started_at`, `expires_at`, `submitted_at`, totais, percentual, aprovação, estrelas e XP creditado.
- Só pode existir uma tentativa `IN_PROGRESS` por aluno e atribuição.
- Todas as tentativas são preservadas.

`attempt_question_snapshots`

- Congela tipo, enunciado, opções, gabarito, explicação, tolerância/unidade e ordem no início da tentativa.
- Endpoints do aluno omitem gabarito e explicação até a tentativa terminar.

`attempt_answers`

- Uma resposta imutável por snapshot. Guarda payload normalizado, correção e horário.
- Resposta numérica normaliza vírgula/ponto e usa `abs(answer - expected) <= tolerance`.
- Questões não respondidas são incorretas.

`extra_attempt_grants`

- Quantidade concedida pelo professor para um aluno e atribuição, com auditoria. Não apaga tentativas.

`room_student_progress`

- Projeção/materialização por aluno e sala: XP total, nível, estrelas e atividade. Pode ser recalculada a partir das tentativas.
- Melhor nota por atribuição define aprovação e estrelas.
- XP potencial da tentativa é `correct_answers * 10`.
- Crédito de XP é apenas a diferença positiva para o melhor XP anterior daquela atribuição.
- Estrelas: `<50 = 0`, `50–69 = 1`, `70–89 = 2`, `90–100 = 3`.
- Nível segue `floor(roomXpTotal / 100) + 1`.

### 3.5 Gamificação e dicas

- Conquistas são definições fixas no código, calculadas no backend e retornadas com data de desbloqueio.
- O catálogo inicial contém: primeira aprovação, primeira nota perfeita, 100 XP, 500 XP, 1.000 XP, cinco lições aprovadas e dez lições aprovadas. O cálculo e desbloqueio são separados por sala e idempotentes.
- O ranking é por XP dentro da sala; desempate por maior número de estrelas, depois conclusão mais antiga.
- Para alunos, o ranking retorna primeiro nome e inicial do sobrenome; professor recebe identidade completa.
- `financial_tips` guarda título, conteúdo, fonte opcional, data de publicação, status, timestamps e versão.
- A dica do dia é a ativa agendada para a data local de São Paulo. Sem uma agendada, escolher deterministicamente uma dica ativa para que todos recebam a mesma durante o dia.

## 4. Segurança e autenticação

### 4.1 JWT

- Access token: 15 minutos; contém `sub`, `role`, `sessionId`, `iat`, `exp` e `jti`.
- Refresh token: 7 dias, opaco ou JWT com entropia equivalente, armazenado somente como hash.
- `POST /auth/refresh` rotaciona access e refresh tokens em uma transação e invalida o anterior.
- Reuso de refresh token já rotacionado revoga a cadeia daquela sessão e retorna `401 REFRESH_TOKEN_REUSED`.
- Logout revoga apenas a sessão atual. Desativação, alteração de senha e reset administrativo revogam todas as sessões do usuário.
- Senhas usam Argon2id ou BCrypt com custo configurado. Nunca são registradas em log.

### 4.2 Fluxos de conta

- Aluno se cadastra como `PENDING`; confirmação de e-mail torna a conta `ACTIVE`.
- Admin cria professor `PENDING`; e-mail de convite de uso único permite definir senha e ativar.
- Recuperação de senha existe para professores e alunos, não revela se o e-mail existe e usa token de uso único.
- Tokens de ação têm expiração configurável; padrão: confirmação 24 h, convite 72 h e recuperação 30 min.
- Primeiro administrador global é criado de forma idempotente a partir de variáveis de ambiente e deve trocar a senha no primeiro acesso.
- Senhas aceitam de 8 a 72 caracteres e exigem pelo menos uma letra e um número. A validação ocorre antes do hash.

### 4.3 Autorização

Toda autorização é aplicada no service e validada novamente na consulta ao banco:

- Admin é global e administra instituições, professores e dicas.
- Professor acessa apenas salas, conteúdos, alunos e relatórios próprios.
- Aluno acessa apenas matrículas ativas e recursos publicados das próprias salas.
- Vínculo entre aluno, professor e sala exige a mesma instituição.
- Responder e consultar tentativa exige que ela pertença ao aluno autenticado.
- Download valida autoria ou matrícula na sala associada.
- Recurso não visível retorna `404`, evitando enumeração; perfil incompatível conhecido retorna `403`.

### 4.4 Proteções adicionais

- Rate limit em login, refresh, reenvio de e-mail, recuperação, ingresso por código e criação de tentativa.
- CORS por lista de origens configurada por ambiente.
- Validação de URL externa com esquemas `https` permitidos; bloquear SSRF e esquemas executáveis.
- Sanitizar Markdown no momento de renderização e, preferencialmente, também no ingresso.
- Validar MIME real, extensão e tamanho de upload.
- Logs estruturados com `traceId`, sem tokens, senhas, gabaritos ou conteúdo sensível.

## 5. Catálogo de endpoints

### 5.1 Autenticação e perfil

```text
POST   /auth/login
POST   /auth/refresh
POST   /auth/logout
POST   /auth/student-registration
POST   /auth/verify-email
POST   /auth/resend-verification
POST   /auth/forgot-password
POST   /auth/reset-password
POST   /auth/accept-teacher-invite
GET    /me
PATCH  /me
POST   /me/change-password
GET    /institutions/options
```

Login retorna access/refresh tokens, expirações em segundos e `user`. Refresh recebe o token no corpo e retorna os dois novos tokens. Cadastro de aluno recebe nome, e-mail, senha, matrícula e instituição e retorna `202`.

### 5.2 Aluno

```text
GET    /student/rooms
POST   /student/rooms/join
GET    /student/rooms/{roomId}/dashboard
GET    /student/rooms/{roomId}/lessons
GET    /student/rooms/{roomId}/lessons/{lessonId}
GET    /student/rooms/{roomId}/lessons/{lessonId}/attempts
POST   /student/room-lessons/{assignmentId}/attempts
GET    /student/attempts/{attemptId}
PUT    /student/attempts/{attemptId}/answers/{questionSnapshotId}
POST   /student/attempts/{attemptId}/submit
GET    /student/attempts/{attemptId}/result
GET    /student/rooms/{roomId}/videos
GET    /student/rooms/{roomId}/materials
POST   /student/media/{mediaType}/{mediaId}/view
GET    /student/rooms/{roomId}/ranking
GET    /student/rooms/{roomId}/achievements
GET    /files/{fileId}/download
```

### 5.3 Professor

```text
GET    /teacher/dashboard
GET    /teacher/rooms
POST   /teacher/rooms
GET    /teacher/rooms/{roomId}
PATCH  /teacher/rooms/{roomId}
POST   /teacher/rooms/{roomId}/archive
DELETE /teacher/rooms/{roomId}
POST   /teacher/rooms/{roomId}/duplicate
POST   /teacher/rooms/{roomId}/regenerate-code
GET    /teacher/rooms/{roomId}/students
DELETE /teacher/rooms/{roomId}/students/{studentId}

GET    /teacher/lessons
POST   /teacher/lessons
GET    /teacher/lessons/{lessonId}
PATCH  /teacher/lessons/{lessonId}
POST   /teacher/lessons/{lessonId}/publish
POST   /teacher/lessons/{lessonId}/archive
POST   /teacher/lessons/{lessonId}/duplicate
POST   /teacher/lessons/{lessonId}/images
GET    /teacher/lessons/{lessonId}/questions
POST   /teacher/lessons/{lessonId}/questions
PATCH  /teacher/questions/{questionId}
POST   /teacher/questions/{questionId}/duplicate
DELETE /teacher/questions/{questionId}
PUT    /teacher/lessons/{lessonId}/questions/order

GET    /teacher/rooms/{roomId}/lesson-assignments
POST   /teacher/rooms/{roomId}/lesson-assignments
PATCH  /teacher/rooms/{roomId}/lesson-assignments/{assignmentId}
DELETE /teacher/rooms/{roomId}/lesson-assignments/{assignmentId}
PUT    /teacher/rooms/{roomId}/lesson-assignments/order
POST   /teacher/room-lessons/{assignmentId}/students/{studentId}/extra-attempts

GET    /teacher/videos
POST   /teacher/videos
GET    /teacher/videos/{videoId}
PATCH  /teacher/videos/{videoId}
DELETE /teacher/videos/{videoId}
GET    /teacher/materials
POST   /teacher/materials
GET    /teacher/materials/{materialId}
PATCH  /teacher/materials/{materialId}
DELETE /teacher/materials/{materialId}
POST   /teacher/materials/files
GET    /teacher/rooms/{roomId}/media-assignments
POST   /teacher/rooms/{roomId}/media-assignments
PATCH  /teacher/rooms/{roomId}/media-assignments/{assignmentId}
DELETE /teacher/rooms/{roomId}/media-assignments/{assignmentId}
GET    /teacher/media/{mediaType}/{mediaId}/views

GET    /teacher/reports/overview
GET    /teacher/reports/students
GET    /teacher/reports/students/{studentId}/attempts
GET    /teacher/reports/ranking
GET    /teacher/reports/export.csv
```

### 5.4 Administrador

```text
GET    /admin/dashboard
GET    /admin/teachers
POST   /admin/teachers
GET    /admin/teachers/{teacherId}
PATCH  /admin/teachers/{teacherId}
POST   /admin/teachers/{teacherId}/activate
POST   /admin/teachers/{teacherId}/deactivate
POST   /admin/teachers/{teacherId}/password-reset

GET    /admin/institutions
POST   /admin/institutions
GET    /admin/institutions/{institutionId}
PATCH  /admin/institutions/{institutionId}
POST   /admin/institutions/{institutionId}/activate
POST   /admin/institutions/{institutionId}/deactivate
DELETE /admin/institutions/{institutionId}

GET    /admin/financial-tips
POST   /admin/financial-tips
GET    /admin/financial-tips/{tipId}
PATCH  /admin/financial-tips/{tipId}
DELETE /admin/financial-tips/{tipId}
POST   /admin/financial-tips/{tipId}/activate
POST   /admin/financial-tips/{tipId}/deactivate
```

## 6. Contratos críticos

### 6.1 Criar sala

```json
{
  "name": "2º ano A",
  "description": "Matemática financeira aplicada",
  "grade": "HIGH_SCHOOL_2",
  "contentTopics": ["Porcentagem", "Juros compostos"],
  "passingScorePercent": 50
}
```

O backend deriva professor e instituição do token, gera código único e devolve `version`.

### 6.2 Configurar atribuição

```json
{
  "lessonId": "uuid",
  "position": 1,
  "status": "PUBLISHED",
  "availableFrom": "2026-08-20T10:00:00Z",
  "dueAt": "2026-09-01T02:59:59Z",
  "timeLimitMinutes": 30,
  "maxAttempts": 3,
  "questionCount": 10,
  "shuffleQuestions": true,
  "shuffleOptions": true
}
```

Validar propriedade da lição, sala não arquivada, intervalo de datas, valores positivos e questões suficientes. Valores nulos de tempo/tentativas significam ilimitado.

### 6.3 Iniciar tentativa

`POST /student/room-lessons/{assignmentId}/attempts` aceita `Idempotency-Key`.

Em uma transação:

1. bloquear logicamente o agregado aluno/atribuição;
2. validar matrícula, publicação, abertura, prazo, pré-requisito e limite de tentativas;
3. retornar tentativa ativa existente quando aplicável;
4. selecionar questões e embaralhar com aleatoriedade segura;
5. criar snapshots e `expiresAt` baseado no relógio do servidor;
6. retornar DTO sem gabaritos.

### 6.4 Registrar resposta

```json
{ "selectedOptionIds": ["uuid"] }
{ "booleanValue": true }
{ "numericValue": "100.50" }
```

O payload aceito depende do tipo. A inserção é única; repetição idêntica pode devolver o resultado anterior, enquanto alteração retorna `409 ANSWER_ALREADY_RECORDED`. A resposta pública contém somente `correct` e `answeredAt`.

### 6.5 Finalizar tentativa

Finalização é idempotente. O backend:

1. expira a tentativa quando necessário;
2. considera omissões incorretas;
3. calcula percentual, aprovação, estrelas e melhoria de XP;
4. atualiza projeções, desbloqueios e conquistas na mesma transação;
5. retorna o resultado com gabarito e explicações.

Um job periódico encerra tentativas vencidas mesmo que o cliente não chame `submit`.

## 7. Arquivos e e-mail

### 7.1 Armazenamento

Definir uma porta `FileStorage` com implementações:

- filesystem local no desenvolvimento;
- armazenamento de objetos compatível com S3 em produção.

Metadados persistidos: dono, nome original, nome seguro/chave, MIME detectado, tamanho, hash, data e status. Objetos são privados. Download faz streaming autorizado ou devolve redirecionamento temporário.

Limites:

- PDF, PPT e PPTX: 10 MB.
- PNG, JPEG e WebP: 5 MB.

Falha entre banco e storage deve ser compensada; objetos órfãos são removidos por rotina de manutenção.

### 7.2 E-mail

Definir uma porta `MailSender` com SMTP configurável. Desenvolvimento pode usar Mailpit ou adaptador que registra somente o link seguro. Templates necessários:

- confirmação de e-mail do aluno;
- convite do professor;
- recuperação de senha.

Envio deve ocorrer após commit, preferencialmente via outbox persistente com retentativas, para não perder mensagens nem manter transações abertas durante I/O.

## 8. Relatórios

- Todas as consultas são limitadas às salas do professor autenticado.
- Filtros: `roomId`, `lessonId`, `from`, `to`; padrão últimos 30 dias; `period=ALL` para todo o histórico.
- Overview: alunos ativos, XP médio, conclusão, estrelas médias, desempenho por lição e série temporal.
- Alunos: progresso agregado e última atividade.
- Detalhe: cada tentativa, respostas, duração, nota, estrelas e XP.
- Ranking: XP, estrelas e posição.
- CSV é gerado em streaming pelo backend. PDF não faz parte do backend.

Consultas pesadas devem usar projeções/DTOs no banco, índices adequados e paginação; não carregar grafos JPA completos.

## 9. Concorrência, idempotência e auditoria

- Optimistic locking em instituições, salas, lições, questões, atribuições, vídeos, materiais e dicas.
- Constraint para uma tentativa ativa por aluno/atribuição.
- Constraint para uma resposta por snapshot.
- `Idempotency-Key` em criação de tentativa; chave associada ao usuário, rota, payload e resposta por período configurável.
- Finalização, registro de visualização, refresh e reingresso são idempotentes nos termos descritos.
- Auditar criação/edição/ativação de professor, instituições, regeneração de código, remoção de aluno, concessão de tentativa extra, publicação e arquivamento.

## 10. Migrations e índices mínimos

- Toda alteração de schema usa Flyway; `ddl-auto=validate` permanece.
- Índices únicos case-insensitive para e-mail, CNPJ normalizado e código ativo de sala.
- Índices em chaves estrangeiras e consultas frequentes: professor/sala, aluno/matrícula, atribuição/posição, tentativa/aluno/status, datas de relatório, mídia/visualização e dica/data/status.
- Constraints de domínio devem existir também no banco quando possível.
- Seeds são idempotentes e limitados ao admin inicial/configurações fixas; não há seed de lições.

## 11. Estratégia de testes

### 11.1 Unitários

- Cálculo de estrelas, XP incremental, nível e ranking.
- Correção dos quatro tipos de questão, inclusive limites de tolerância.
- disponibilidade, prazo, cronômetro, pré-requisito e tentativas extras;
- seleção/embaralhamento e criação de snapshots;
- autorização por perfil, autoria e instituição.

### 11.2 Integração

- Repositories com PostgreSQL real via Testcontainers.
- Migrations Flyway em banco vazio e evolução de schema.
- Rotação/reuso/revogação de refresh token.
- Concorrência ao iniciar/finalizar tentativa e ao registrar resposta.
- Upload/download privado com adaptador de teste.
- Outbox e retentativas de e-mail.

### 11.3 API

- Contratos de sucesso e Problem Details.
- Ausência de gabarito antes do resultado.
- Matriz `401`/`403`/`404` por perfil e propriedade.
- Paginação, filtros, ordenação e CSV.
- `409` de versão e idempotência.

## 12. Ordem recomendada de implementação

1. Base compartilhada, Problem Details, segurança, usuários e instituições.
2. Convite, confirmação, recuperação, sessões e admin inicial.
3. Salas, códigos e matrículas.
4. Lições, questões e atribuições.
5. Tentativas, snapshots, correção e progresso.
6. Gamificação, ranking e conquistas.
7. Uploads, vídeos, materiais e visualizações.
8. Dicas, dashboards, relatórios e CSV.
9. Hardening de concorrência, rate limit, auditoria e observabilidade.

## 13. Critérios de aceite do backend

- Nenhum endpoint do aluno revela gabarito ou explicação antes da finalização.
- Progresso, XP, estrelas e ranking nunca atravessam salas.
- O servidor, e não o cliente, determina tempo, nota, aprovação e recompensas.
- Toda tentativa mantém snapshot imutável e histórico completo.
- Repetição da mesma nota não gera XP adicional.
- Usuários e conteúdos não atravessam instituições ou proprietários indevidamente.
- Refresh tokens são rotacionados, armazenados como hash e revogáveis por sessão.
- Arquivos são privados e autorizados antes da entrega.
- Edição concorrente nunca sobrescreve silenciosamente.
- Migrations recriam o banco do zero e todos os fluxos críticos têm testes automatizados.
