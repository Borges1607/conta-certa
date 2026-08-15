# Conta Certa — Especificação de Integração do Frontend

Status: aprovado para implementação
Versão: 1.0
Data: 2026-08-15

## 1. Objetivo

Este documento define o comportamento esperado do frontend e seu contrato com a API do Conta Certa. Ele não determina framework, biblioteca visual ou estratégia interna de estado. A API é a fonte de verdade para autenticação, autorização, tempo, correção, progresso, XP, estrelas e disponibilidade de conteúdo.

O frontend atende três perfis:

- `ADMIN`: administra instituições, professores e dicas financeiras.
- `TEACHER`: administra salas, conteúdos, alunos e relatórios próprios.
- `STUDENT`: participa de várias salas e realiza atividades com progresso separado por sala.

## 2. Convenções compartilhadas

### 2.1 HTTP e dados

- Base URL: `/api/v1`.
- JSON em `camelCase`; recursos e campos em inglês.
- IDs são UUID em texto.
- Instantes usam ISO 8601 em UTC, por exemplo `2026-08-15T19:30:00Z`.
- O frontend apresenta horários em `America/Sao_Paulo`.
- Datas sem horário usam `YYYY-MM-DD`.
- Valores percentuais são números de `0` a `100`.
- Valores monetários são strings decimais, nunca `float`, por exemplo `"1250.50"`.
- Conteúdo Markdown aceita KaTeX, tabelas e blocos de código. HTML bruto não deve ser renderizado.

### 2.2 Autorização

Endpoints protegidos recebem:

```http
Authorization: Bearer <accessToken>
```

O access token dura 15 minutos. O refresh token dura 7 dias, é rotacionado a cada uso e representa uma sessão independente. O frontend pode manter sessões em mais de um dispositivo. O logout encerra apenas a sessão atual.

Ao receber `401`, o cliente pode executar uma única tentativa de refresh e repetir a requisição original. Se o refresh falhar, deve limpar a sessão local e voltar ao login. `403` não deve provocar refresh.

### 2.3 Paginação

Listagens aceitam `page` (padrão `0`), `size` (padrão `20`, máximo `100`) e, quando documentado, `sort=field,asc|desc`.

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

### 2.4 Erros

Erros usam `application/problem+json`:

```json
{
  "type": "https://api.contacerta/errors/validation",
  "title": "Validation failed",
  "status": 422,
  "code": "VALIDATION_ERROR",
  "detail": "One or more fields are invalid.",
  "instance": "/api/v1/rooms",
  "timestamp": "2026-08-15T19:30:00Z",
  "traceId": "01K...",
  "fieldErrors": [{ "field": "name", "message": "must not be blank" }]
}
```

O frontend deve tratar especialmente:

| Status | Uso |
|---|---|
| `400` | Requisição malformada |
| `401` | Token ausente, expirado ou inválido |
| `403` | Perfil, instituição, autoria ou matrícula sem permissão |
| `404` | Recurso inexistente ou não visível ao usuário |
| `409` | Duplicidade, estado inválido ou conflito de versão |
| `410` | Convite/token expirado ou tentativa definitivamente encerrada |
| `413` | Arquivo acima do limite |
| `415` | Tipo de arquivo não aceito |
| `422` | Validação de campos ou regra de negócio |
| `429` | Limite de requisições excedido |

### 2.5 Enums normativos

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

## 3. Modelos consumidos pelo frontend

Os tipos abaixo são conceituais e independem da linguagem escolhida.

```ts
type UserSummary = {
  id: string;
  role: "ADMIN" | "TEACHER" | "STUDENT";
  status: "PENDING" | "ACTIVE" | "INACTIVE";
  fullName: string;
  email: string;
  registrationNumber: string | null;
  institution: InstitutionSummary | null;
  emailVerified: boolean;
  mustChangePassword: boolean;
};

type InstitutionSummary = {
  id: string;
  name: string;
  cnpj: string;
  contactEmail: string;
  contactPhone: string;
  active: boolean;
};

type RoomSummary = {
  id: string;
  name: string;
  description: string | null;
  grade: "HIGH_SCHOOL_1" | "HIGH_SCHOOL_2" | "HIGH_SCHOOL_3";
  contentTopics: string[];
  teacher: { id: string; fullName: string };
  institution: InstitutionSummary;
  membershipStatus: "ACTIVE" | "REMOVED";
  archived: boolean;
  progressPercent?: number;
};
```

Recursos editáveis retornam `version`, que deve ser reenviado em alterações. Um `409 VERSION_CONFLICT` exige recarregar os dados e informar o usuário; o frontend não deve sobrescrever silenciosamente.

## 4. Autenticação e conta

### 4.1 Endpoints

| Método e rota | Acesso | Finalidade |
|---|---|---|
| `POST /auth/login` | Público | Autenticar por e-mail e senha |
| `POST /auth/refresh` | Público com refresh token | Rotacionar tokens |
| `POST /auth/logout` | Autenticado | Revogar a sessão atual |
| `POST /auth/student-registration` | Público | Cadastrar aluno |
| `POST /auth/verify-email` | Público | Confirmar e-mail |
| `POST /auth/resend-verification` | Público | Reenviar confirmação |
| `POST /auth/forgot-password` | Público | Solicitar recuperação |
| `POST /auth/reset-password` | Público com token | Definir nova senha |
| `POST /auth/accept-teacher-invite` | Público com token | Professor define senha do convite |
| `GET /me` | Autenticado | Obter usuário atual |
| `PATCH /me` | Autenticado | Alterar o próprio nome |
| `POST /me/change-password` | Autenticado | Trocar senha |

Login:

```json
// POST /auth/login
{ "email": "ana@example.com", "password": "senha-segura" }

// 200
{
  "accessToken": "...",
  "refreshToken": "...",
  "tokenType": "Bearer",
  "accessExpiresIn": 900,
  "refreshExpiresIn": 604800,
  "user": { "id": "uuid", "role": "STUDENT", "status": "ACTIVE", "fullName": "Ana Lima", "email": "ana@example.com", "registrationNumber": "2026001", "institution": { "id": "uuid", "name": "IFSC", "cnpj": "00000000000000", "contactEmail": "contato@example.com", "contactPhone": "+5548999999999", "active": true }, "emailVerified": true, "mustChangePassword": false }
}
```

Refresh:

```json
// POST /auth/refresh
{ "refreshToken": "..." }

// 200
{ "accessToken": "...", "refreshToken": "...", "tokenType": "Bearer", "accessExpiresIn": 900, "refreshExpiresIn": 604800 }
```

Cadastro de aluno:

```json
{
  "fullName": "Ana Lima",
  "email": "ana@example.com",
  "password": "senha-segura",
  "registrationNumber": "2026001",
  "institutionId": "uuid"
}
```

O cadastro retorna `202 Accepted`. O aluno só pode autenticar após confirmar o e-mail. Alterações de e-mail, matrícula ou instituição não são feitas em `/me`; exigem suporte administrativo fora do escopo desta versão.

Senhas devem ter de 8 a 72 caracteres e conter ao menos uma letra e um número. O frontend pode exibir essa regra, mas o backend sempre a valida.

## 5. Jornada pública

### 5.1 Telas

- Login único por e-mail e senha, com redirecionamento conforme `user.role`.
- Cadastro exclusivo para aluno.
- Confirmação de e-mail, reenvio de confirmação e estados de token válido, expirado e já utilizado.
- Esqueci/redefinir senha para alunos e professores.
- Aceite de convite do professor e definição inicial de senha.

### 5.2 Instituições públicas

`GET /institutions/options?active=true` retorna instituições ativas não paginadas para selects:

```json
[{ "id": "uuid", "name": "IFSC", "cnpj": "00000000000000" }]
```

## 6. Frontend do aluno

### 6.1 Minhas salas

| Método e rota | Finalidade |
|---|---|
| `GET /student/rooms` | Listar salas ativas do aluno |
| `POST /student/rooms/join` | Entrar por código |
| `GET /student/rooms/{roomId}/dashboard` | Carregar visão agregada da sala |

```json
// POST /student/rooms/join
{ "code": "A7K9Q2" }
```

O aluno pode participar de várias salas, não pode sair sozinho e só ingressa em sala da própria instituição. Um código é permanente até ser regenerado. Reingresso após remoção restaura o histórico.

O dashboard agregado retorna sala, progresso, nível, XP, estrelas, lições concluídas, conquistas recentes, dica do dia e posição no ranking.

### 6.2 Trilha e lição

| Método e rota | Finalidade |
|---|---|
| `GET /student/rooms/{roomId}/lessons` | Trilha ordenada e estado de bloqueio |
| `GET /student/rooms/{roomId}/lessons/{lessonId}` | Conteúdo teórico, materiais e situação do aluno |
| `GET /student/rooms/{roomId}/lessons/{lessonId}/attempts` | Histórico do aluno |

Cada item da trilha informa `assignmentId`, ordem, status de disponibilidade, datas, melhor nota, estrelas, tentativas usadas/disponíveis e motivo de bloqueio. Motivos possíveis incluem pré-requisito, data de abertura, prazo encerrado, tentativas esgotadas e conteúdo não publicado.

### 6.3 Tentativas

| Método e rota | Finalidade |
|---|---|
| `POST /student/room-lessons/{assignmentId}/attempts` | Iniciar ou recuperar tentativa ativa |
| `GET /student/attempts/{attemptId}` | Retomar tentativa |
| `PUT /student/attempts/{attemptId}/answers/{questionSnapshotId}` | Registrar uma resposta |
| `POST /student/attempts/{attemptId}/submit` | Finalizar explicitamente |
| `GET /student/attempts/{attemptId}/result` | Consultar resultado final |

O início aceita `Idempotency-Key` no header. A resposta contém `attemptId`, `status`, `startedAt`, `expiresAt`, questões sorteadas e respostas já registradas. Antes da finalização, nunca contém resposta correta ou explicação.

Resposta por tipo:

```json
{ "selectedOptionIds": ["uuid"] }
{ "selectedOptionIds": ["uuid", "uuid"] }
{ "booleanValue": true }
{ "numericValue": "100.50" }
```

Após registrar, a API retorna apenas:

```json
{ "questionSnapshotId": "uuid", "answeredAt": "2026-08-15T19:30:00Z", "correct": false }
```

A resposta é imutável dentro da tentativa. Quando `expiresAt` for atingido, a interface envia `submit`; o servidor também expira e corrige a tentativa independentemente do cliente. Questões sem resposta contam como incorretas.

Resultado:

```json
{
  "attemptId": "uuid",
  "status": "SUBMITTED",
  "correctAnswers": 7,
  "totalQuestions": 10,
  "scorePercent": 70,
  "passed": true,
  "stars": 2,
  "xpEarnedThisAttempt": 20,
  "roomXpTotal": 150,
  "startedAt": "2026-08-15T19:00:00Z",
  "submittedAt": "2026-08-15T19:20:00Z",
  "answers": [{ "question": {}, "studentAnswer": {}, "correctAnswer": {}, "correct": true, "explanation": "..." }]
}
```

Regras de apresentação:

- Nota mínima é única por sala, padrão 50%.
- Melhor nota define aprovação e estrelas; todas as tentativas permanecem no histórico.
- XP potencial é `acertos × 10`. Só a melhoria do melhor resultado gera a diferença de XP.
- Faixas: `<50 = 0`, `50–69 = 1`, `70–89 = 2`, `90–100 = 3` estrelas.
- A próxima lição só libera após aprovação da anterior, respeitando ordem e datas.

### 6.4 Mídias, ranking e conquistas

| Método e rota | Finalidade |
|---|---|
| `GET /student/rooms/{roomId}/videos` | Listar videoaulas publicadas |
| `GET /student/rooms/{roomId}/materials` | Listar materiais publicados |
| `POST /student/media/{mediaType}/{mediaId}/view` | Registrar visualização ao abrir |
| `GET /files/{fileId}/download` | Baixar/abrir arquivo autorizado |
| `GET /student/rooms/{roomId}/ranking` | Ranking paginado da sala |
| `GET /student/rooms/{roomId}/achievements` | Conquistas calculadas |

O ranking expõe aos alunos somente primeiro nome e inicial do sobrenome dos colegas. XP e posição são sempre relativos à sala.

As conquistas fixas da primeira versão são: primeira aprovação, primeira nota perfeita, 100 XP, 500 XP, 1.000 XP, cinco lições aprovadas e dez lições aprovadas. Cada conquista é calculada separadamente por sala.

## 7. Frontend do professor

### 7.1 Dashboard, salas e alunos

| Método e rota | Finalidade |
|---|---|
| `GET /teacher/dashboard` | Cards-resumo do professor |
| `GET /teacher/rooms` | Listar salas próprias |
| `POST /teacher/rooms` | Criar sala |
| `GET /teacher/rooms/{roomId}` | Detalhar sala |
| `PATCH /teacher/rooms/{roomId}` | Editar sala e nota mínima |
| `POST /teacher/rooms/{roomId}/archive` | Arquivar sala |
| `DELETE /teacher/rooms/{roomId}` | Excluir sala nunca utilizada |
| `POST /teacher/rooms/{roomId}/duplicate` | Copiar configuração, sem alunos/progresso |
| `POST /teacher/rooms/{roomId}/regenerate-code` | Trocar código de ingresso |
| `GET /teacher/rooms/{roomId}/students` | Listar alunos |
| `DELETE /teacher/rooms/{roomId}/students/{studentId}` | Remover aluno, preservando histórico |

Criação de sala:

```json
{
  "name": "2º ano A",
  "description": "Matemática financeira aplicada",
  "grade": "HIGH_SCHOOL_2",
  "contentTopics": ["Porcentagem", "Juros compostos"],
  "passingScorePercent": 50
}
```

A instituição é derivada do professor autenticado. Salas arquivadas são somente leitura; não aceitam ingresso nem novas tentativas.

### 7.2 Lições e questões

| Método e rota | Finalidade |
|---|---|
| `GET /teacher/lessons` | Listar acervo próprio |
| `POST /teacher/lessons` | Criar rascunho |
| `GET /teacher/lessons/{lessonId}` | Detalhar |
| `PATCH /teacher/lessons/{lessonId}` | Editar com `version` |
| `POST /teacher/lessons/{lessonId}/publish` | Publicar versão válida |
| `POST /teacher/lessons/{lessonId}/archive` | Arquivar |
| `POST /teacher/lessons/{lessonId}/duplicate` | Duplicar lição e questões |
| `POST /teacher/lessons/{lessonId}/images` | Upload de imagem do Markdown |
| `GET /teacher/lessons/{lessonId}/questions` | Listar questões ordenadas |
| `POST /teacher/lessons/{lessonId}/questions` | Criar questão |
| `PATCH /teacher/questions/{questionId}` | Editar questão |
| `POST /teacher/questions/{questionId}/duplicate` | Duplicar para uma lição destino |
| `DELETE /teacher/questions/{questionId}` | Excluir ou arquivar logicamente |
| `PUT /teacher/lessons/{lessonId}/questions/order` | Reordenar |

Lição:

```json
{
  "title": "Juros compostos",
  "summary": "Conceitos e aplicações",
  "theoryMarkdown": "## Fórmula\n$M=C(1+i)^t$"
}
```

Questões compartilham `prompt`, `type`, `explanation` e `order`. Campos específicos:

- `SINGLE_CHOICE`: opções e exatamente um `correct: true`.
- `MULTIPLE_CHOICE`: opções e duas ou mais corretas; só a seleção exata pontua.
- `TRUE_FALSE`: `correctBoolean`.
- `NUMERIC`: `correctNumericValue`, `absoluteTolerance`, `unit` (`BRL`, `PERCENT` ou `NONE`) e `decimalPlaces`.

### 7.3 Atribuição de lições à sala

| Método e rota | Finalidade |
|---|---|
| `GET /teacher/rooms/{roomId}/lesson-assignments` | Listar trilha configurada |
| `POST /teacher/rooms/{roomId}/lesson-assignments` | Reutilizar lição na sala |
| `PATCH /teacher/rooms/{roomId}/lesson-assignments/{assignmentId}` | Configurar atribuição |
| `DELETE /teacher/rooms/{roomId}/lesson-assignments/{assignmentId}` | Retirar atribuição futura |
| `PUT /teacher/rooms/{roomId}/lesson-assignments/order` | Reordenar trilha |

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

`timeLimitMinutes` e `maxAttempts` aceitam `null` para sem limite. Se omitidos na criação, usam 30 minutos e 3 tentativas. `questionCount` nulo usa todas as questões ativas. A publicação é rejeitada quando não há questões suficientes.

`POST /teacher/room-lessons/{assignmentId}/students/{studentId}/extra-attempts` recebe `{ "quantity": 1 }` e preserva o histórico.

### 7.4 Vídeos e materiais

| Método e rota | Finalidade |
|---|---|
| `GET/POST /teacher/videos` | Listar/criar vídeos próprios |
| `GET/PATCH/DELETE /teacher/videos/{videoId}` | Detalhar/editar/arquivar |
| `GET/POST /teacher/materials` | Listar/criar materiais próprios |
| `GET/PATCH/DELETE /teacher/materials/{materialId}` | Detalhar/editar/arquivar |
| `POST /teacher/materials/files` | Upload multipart |
| `GET/POST /teacher/rooms/{roomId}/media-assignments` | Listar/publicar vídeo ou material, com lição opcional |
| `PATCH/DELETE /teacher/rooms/{roomId}/media-assignments/{assignmentId}` | Alterar vínculo ou retirá-lo da sala |
| `GET /teacher/media/{mediaType}/{mediaId}/views` | Visualizações por aluno |

Vídeos aceitam links externos. Materiais aceitam link ou arquivo PDF/PPT/PPTX de até 10 MB. Imagens aceitam PNG/JPEG/WebP de até 5 MB. Abertura pelo aluno registra uma visualização idempotente por aluno e mídia, preservando também `firstViewedAt` e `lastViewedAt`.

### 7.5 Relatórios

| Método e rota | Finalidade |
|---|---|
| `GET /teacher/reports/overview` | Métricas e séries agregadas |
| `GET /teacher/reports/students` | Resultado agregado por aluno |
| `GET /teacher/reports/students/{studentId}/attempts` | Tentativas detalhadas |
| `GET /teacher/reports/ranking` | Ranking completo |
| `GET /teacher/reports/export.csv` | CSV gerado no backend |

Filtros: `roomId` obrigatório quando necessário, `lessonId` opcional, `from`, `to`, paginação e ordenação. O padrão é últimos 30 dias; `period=ALL` seleciona todo o histórico. O frontend gera a versão de impressão/PDF a partir dos dados retornados.

## 8. Frontend do administrador

### 8.1 Dashboard e professores

| Método e rota | Finalidade |
|---|---|
| `GET /admin/dashboard` | Quantidades de instituições e professores por status |
| `GET /admin/teachers` | Listar e filtrar professores |
| `POST /admin/teachers` | Criar professor pendente e enviar convite |
| `GET /admin/teachers/{teacherId}` | Detalhar professor |
| `PATCH /admin/teachers/{teacherId}` | Editar nome, matrícula e instituição |
| `POST /admin/teachers/{teacherId}/activate` | Reativar |
| `POST /admin/teachers/{teacherId}/deactivate` | Desativar e revogar sessões |
| `POST /admin/teachers/{teacherId}/password-reset` | Enviar link de redefinição |

### 8.2 Instituições

| Método e rota | Finalidade |
|---|---|
| `GET /admin/institutions` | Listar e filtrar |
| `POST /admin/institutions` | Criar |
| `GET /admin/institutions/{institutionId}` | Detalhar |
| `PATCH /admin/institutions/{institutionId}` | Editar com `version` |
| `POST /admin/institutions/{institutionId}/activate` | Ativar |
| `POST /admin/institutions/{institutionId}/deactivate` | Desativar para novos vínculos |
| `DELETE /admin/institutions/{institutionId}` | Excluir somente sem histórico |

```json
{ "name": "Instituto Exemplo", "cnpj": "12345678000190", "contactEmail": "contato@example.com", "contactPhone": "+5548999999999" }
```

### 8.3 Dicas financeiras

| Método e rota | Finalidade |
|---|---|
| `GET/POST /admin/financial-tips` | Listar/criar |
| `GET/PATCH/DELETE /admin/financial-tips/{tipId}` | Detalhar/editar/arquivar |
| `POST /admin/financial-tips/{tipId}/activate` | Ativar |
| `POST /admin/financial-tips/{tipId}/deactivate` | Desativar |

Campos: `title`, `content`, `sourceUrl` opcional, `publicationDate`, `active` e `version`. O endpoint agregado do aluno retorna a dica agendada do dia; sem agendamento, o backend escolhe uma dica ativa.

## 9. Estados obrigatórios de interface

Toda tela que consulta dados deve prever:

- carregamento inicial e atualização em segundo plano;
- vazio com ação contextual;
- erro recuperável com nova tentativa;
- `401`, `403`, `404` e `409` com mensagens distintas;
- confirmação para arquivar, remover aluno, regenerar código e desativar conta;
- bloqueio contra duplo envio em mutações;
- progresso de upload e tratamento de `413`/`415`;
- preservação de formulário quando houver erro de validação;
- aviso de tentativa offline ou conexão perdida, sem inventar tempo local.

## 10. Matriz de permissões

| Capacidade | Admin | Professor | Aluno |
|---|:---:|:---:|:---:|
| Gerenciar instituições | Sim | Não | Não |
| Gerenciar professores | Sim | Não | Não |
| Gerenciar dicas | Sim | Não | Não |
| Criar salas e conteúdo | Não | Próprios | Não |
| Gerenciar alunos da sala | Não | Próprios | Não |
| Ver relatórios detalhados | Não | Próprias salas | Próprio histórico |
| Entrar em sala | Não | Não | Mesma instituição |
| Realizar tentativa | Não | Não | Matrícula ativa e disponibilidade válida |
| Ver ranking | Não | Completo das próprias salas | Nomes parcialmente anonimizados da sala |

## 11. Critérios de aceite do frontend

- Nenhum gabarito aparece antes de a tentativa terminar.
- Cronômetros usam `expiresAt` do servidor e sobrevivem a recarga/fechamento da página.
- A troca de sala nunca mistura XP, progresso, tentativas ou ranking.
- O cliente trata refresh concorrente com uma única operação em andamento.
- Formulários exibem `fieldErrors` junto aos campos correspondentes.
- Recursos editáveis enviam `version` e tratam conflito sem sobrescrita silenciosa.
- Arquivos privados são acessados exclusivamente pelos endpoints autorizados.
- Conteúdo Markdown é sanitizado, renderiza KaTeX e não executa HTML ou scripts.
- Estados de rascunho, bloqueio, prazo, tentativas esgotadas e arquivamento são distinguíveis.
- O frontend não recalcula resultados oficiais; apenas apresenta os valores retornados pela API.
