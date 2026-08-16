# Parte 1 — Núcleo: HTTP, autenticação e erros

Depende de: nada. É a base de todas as outras partes.
Referência normativa: §2, §3, §4 e §11 de [`../frontend-integration-spec.md`](../frontend-integration-spec.md).

## 1. Objetivo

Entregar a camada que todas as features consomem: tipos do contrato, cliente HTTP,
sessão autenticada, ciclo de refresh, tradução de erros e guards de rota. Nenhuma tela é
entregue nesta parte. O critério de sucesso é que uma feature possa ser escrita sem tocar em
`HttpClient`, `localStorage`, cabeçalho `Authorization` ou tratamento de status HTTP.

## 2. Modelos do contrato — `core/models/`

Traduzir para TypeScript os tipos da §2.5 e §3 da spec de integração. Enums como union types
de string, não `enum` do TypeScript.

```ts
// core/models/enums.ts
export type Role = 'ADMIN' | 'TEACHER' | 'STUDENT';
export type AccountStatus = 'PENDING' | 'ACTIVE' | 'INACTIVE';
export type Grade = 'HIGH_SCHOOL_1' | 'HIGH_SCHOOL_2' | 'HIGH_SCHOOL_3';
export type ContentStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
export type QuestionType = 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'TRUE_FALSE' | 'NUMERIC';
export type AttemptStatus = 'IN_PROGRESS' | 'SUBMITTED' | 'EXPIRED';
export type MaterialKind = 'FILE' | 'EXTERNAL_LINK';
export type MediaViewType = 'VIDEO' | 'MATERIAL';
export type NumericUnit = 'BRL' | 'PERCENT' | 'NONE';
```

Rótulos em português para cada enum ficam em `core/models/labels.ts`, num mapa
`Record<Enum, string>`, e são a única fonte de tradução. Nenhum `switch` de rótulo espalhado
por template.

Tipos compartilhados obrigatórios: `UserSummary`, `InstitutionSummary`, `InstitutionOption`,
`RoomSummary`, `Page<T>`, `PageQuery`, `ProblemDetails`, `FieldError`, `AuthTokens`,
`LoginResponse`.

```ts
// core/models/page.ts
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface PageQuery {
  page?: number;   // padrão 0
  size?: number;   // padrão 20, máximo 100
  sort?: string;   // 'field,asc' | 'field,desc'
}
```

Valores monetários e respostas numéricas são `string` no tipo, sempre. Converter para
`number` só no momento da apresentação, e nunca para reenviar à API.

Recursos editáveis carregam `version: number`. Todo payload `PATCH` correspondente inclui
`version` como campo obrigatório no tipo — não opcional. Isso torna o esquecimento um erro
de compilação.

## 3. Cliente HTTP — `core/api/api-client.ts`

Serviço fino sobre `HttpClient` que concentra a montagem de URL e de parâmetros.

Responsabilidades:

- Prefixar `environment.apiBaseUrl` em caminhos relativos.
- Serializar `PageQuery` em `page`, `size`, `sort`, omitindo indefinidos.
- Serializar filtros descartando `null`, `undefined` e string vazia.
- Expor `get<T>`, `getPage<T>`, `post<T>`, `patch<T>`, `put<T>`, `delete<T>`,
  `postMultipart<T>` (com relatório de progresso) e `download` (blob autorizado).
- Aceitar `Idempotency-Key` como opção tipada em `post`.

O `ApiClient` **não** trata erro nem autenticação. Isso é responsabilidade dos interceptors.

`download(fileId)` usa `GET /files/{fileId}/download` com `responseType: 'blob'` e passa pelo
interceptor de auth. Arquivos privados nunca são referenciados por `<img src>` ou `<a href>`
direto para a API — sempre por blob URL obtido por essa via, revogado ao destruir o
componente.

## 4. Sessão e tokens — `core/auth/`

### 4.1 `TokenStorage`

Persiste em `localStorage` sob as chaves `cc.accessToken`, `cc.refreshToken`,
`cc.accessExpiresAt` (instante absoluto calculado no recebimento) e `cc.user`.

Motivo de `localStorage` e não memória: o critério "cronômetros sobrevivem a recarga e
fechamento da página" implica sessão sobrevivente à recarga. O acesso a arquivos privados
segue exclusivamente pelos endpoints autorizados, conforme §11 da spec.

O storage escuta o evento `storage` do navegador: logout em outra aba derruba a sessão nesta
aba também.

### 4.2 `AuthStore`

Estado exposto por signals:

```ts
readonly user = signal<UserSummary | null>(null);
readonly isAuthenticated = computed(() => this.user() !== null);
readonly role = computed(() => this.user()?.role ?? null);
readonly mustChangePassword = computed(() => this.user()?.mustChangePassword ?? false);
```

Operações: `login`, `logout`, `refresh`, `loadCurrentUser` (`GET /me`), `patchProfile`,
`changePassword`, `hydrateFromStorage` (executada no bootstrap).

`login` guarda tokens e usuário e devolve a rota inicial conforme a §5 do documento de visão
geral. `logout` chama `POST /auth/logout` para revogar somente a sessão atual, limpa o
storage e navega para `/login`; a limpeza local acontece mesmo se a chamada falhar.

### 4.3 Ciclo de refresh — requisito crítico

Regra da §2.2 e do critério "o cliente trata refresh concorrente com uma única operação em
andamento".

Implementação:

- Um único campo privado `refreshInFlight: Observable<AuthTokens> | null` no `AuthStore`.
- `refresh()` retorna o observable existente se houver um; caso contrário cria um com
  `shareReplay({ bufferSize: 1, refCount: false })` e o limpa no `finalize`.
- Ao concluir, grava o par rotacionado de tokens.
- Ao falhar, limpa a sessão e redireciona para `/login` preservando `returnUrl`.

O interceptor de refresh:

1. Só age em `401`.
2. Nunca age em requisições para `/auth/login`, `/auth/refresh`, `/auth/student-registration`
   e demais rotas públicas.
3. Marca a requisição como já repetida; uma requisição é retentada **no máximo uma vez**.
4. `403` nunca dispara refresh.
5. Se não há refresh token, não tenta: limpa e vai para o login.

## 5. Interceptors — `core/interceptors/`

Registrados nesta ordem em `app.config.ts`:

| Ordem | Interceptor | Papel |
|---|---|---|
| 1 | `authInterceptor` | Injeta `Authorization: Bearer` em rota não pública |
| 2 | `errorInterceptor` | Converte `HttpErrorResponse` em `ApiError` tipado |
| 3 | `refreshInterceptor` | Trata `401` com refresh único e repetição |
| 4 | `serverClockInterceptor` | Mede o desvio de relógio pelo header `Date` |
| 5 | `loadingInterceptor` | Contador global de requisições em andamento |
| 6 | `mockApiInterceptor` | Ativo só se `environment.useMockApi`; responde como se fosse o backend (Parte 7) |

A ordem importa e não é arbitrária. **A requisição percorre a lista de 1 a 6; a resposta
volta de 6 a 1.** Duas consequências governam o desenho:

- `refreshInterceptor` está **depois** de `errorInterceptor` na lista, portanto vê o erro
  **antes** dele na volta. É isso que permite tratar um `HttpErrorResponse` cru: se a
  repetição der certo, nenhum erro chega ao `errorInterceptor` e a aplicação nem fica
  sabendo que houve um `401`. Só o que o refresh não resolve vira `ApiError`.
- `mockApiInterceptor` é o **último**, no lugar do backend. Assim ele recebe a requisição já
  com o header `Authorization` e pode validar tokens, expirar sessão e devolver `401` de
  verdade — exercitando o ciclo de refresh em desenvolvimento.

### 5.1 `errorInterceptor` e `ApiError`

Toda falha vira uma instância única:

```ts
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;              // 'NETWORK_ERROR' quando status === 0
  readonly detail: string;
  readonly fieldErrors: FieldError[]; // vazio quando não há
  readonly traceId?: string;
  readonly problem?: ProblemDetails;

  get isOffline(): boolean;           // status === 0
  get isValidation(): boolean;        // 422 ou fieldErrors não vazio
  get isVersionConflict(): boolean;   // 409 com code VERSION_CONFLICT
}
```

Mensagens padrão por status, usadas quando o `detail` do servidor não for apresentável:

| Status | Mensagem padrão |
|---|---|
| `0` | "Sem conexão. Verifique sua internet e tente novamente." |
| `400` | "Requisição inválida." |
| `401` | "Sua sessão expirou. Entre novamente." |
| `403` | "Você não tem permissão para esta ação." |
| `404` | "Não encontramos o que você procura." |
| `409` | "Os dados mudaram desde que você abriu esta tela." |
| `410` | "Este link ou tentativa não está mais disponível." |
| `413` | "Arquivo acima do tamanho permitido." |
| `415` | "Tipo de arquivo não aceito." |
| `422` | "Verifique os campos destacados." |
| `429` | "Muitas tentativas. Aguarde alguns instantes." |
| `5xx` | "Erro no servidor. Tente novamente em instantes." |

`401`, `403`, `404` e `409` produzem mensagens distintas — critério explícito da §9.

O interceptor **não** exibe toast. Quem decide entre toast, estado de página e erro de campo
é a feature, porque `422` costuma ir para o formulário e `500` para o toast.

### 5.2 `loadingInterceptor`

Mantém `pendingRequests` em signal. Requisições marcadas com o contexto `SILENT` não contam
(polling, refresh, atualização em segundo plano). Alimenta a barra de progresso global do
shell na Parte 2.

## 6. Guards — `core/auth/guards.ts`

Guards funcionais:

- `authGuard`: exige sessão; sem sessão, redireciona para `/login?returnUrl=<url>`.
- `roleGuard(...roles)`: exige `user.role` entre as permitidas; caso contrário `/403`.
- `guestGuard`: usuário autenticado em `/login` ou `/cadastro` é levado à home do seu perfil.
- `passwordChangeGuard`: se `mustChangePassword`, força `/conta/senha`.

Os guards leem apenas o `AuthStore`. Eles são conveniência de navegação, não segurança: a
autorização real é do servidor, e um `403` de API é sempre tratado ainda que o guard tenha
deixado passar.

## 7. Tempo e relógio — `core/util/server-clock.ts`

Suporta o critério "cronômetros usam `expiresAt` do servidor e sobrevivem a recarga" e o
"sem inventar tempo local".

- Um interceptor de resposta lê o header `Date` quando presente e registra
  `skewMs = serverTime - localTime`, suavizado pela última medição.
- `ServerClock.now(): number` devolve `Date.now() + skewMs`.
- `ServerClock.remainingMs(expiresAt: string): number` devolve a diferença já corrigida.
- Todo cronômetro recalcula a partir de `expiresAt` a cada tique; nada de decrementar um
  contador. Ao voltar de aba suspensa ou de recarga, o valor está correto por construção.
- Quando o dispositivo está offline, a interface avisa e não confia no relógio local para
  decidir expiração — quem expira é o servidor.

Utilitários irmãos: `formatDateTime` (fixo em `America/Sao_Paulo`), `formatDate`,
`formatDuration`, `formatMoney` (recebe `string`, nunca `number`), `formatPercent`.

## 8. Idempotência — `core/util/idempotency.ts`

`newIdempotencyKey(): string` gera um UUID v4 via `crypto.randomUUID()`. Usado no início de
tentativa (§6.3 da spec). A chave é gerada uma vez por intenção do usuário e reaproveitada
em repetições da mesma intenção, não regenerada a cada clique.

## 9. Notificações — `core/notifications/`

Fachada fina sobre `MessageService` e `ConfirmationService` do PrimeNG, para que as features
não dependam da API do PrimeNG diretamente:

```ts
notify.success(summary, detail?)
notify.error(error: ApiError | string)
notify.warn(...) / notify.info(...)
confirm.destructive({ header, message, acceptLabel }): Promise<boolean>
```

`confirm.destructive` é obrigatório para arquivar, remover aluno, regenerar código, excluir e
desativar conta — §9 da spec.

## 10. Proteção contra duplo envio — `core/util/submitting.ts`

Helper `createSubmitGuard()` devolve `{ submitting: Signal<boolean>, run: (fn) => Promise }`
que ignora chamadas enquanto uma está em andamento. Toda mutação da aplicação passa por ele,
e o botão correspondente liga `[loading]` e `[disabled]` a esse signal. É assim que o
requisito "bloqueio contra duplo envio em mutações" é cumprido de forma uniforme.

## 11. Entregáveis

```
core/
├── api/api-client.ts, page.ts, problem-details.ts, http-context.ts
├── auth/auth.store.ts, token-storage.ts, guards.ts, auth.service.ts
├── interceptors/auth.interceptor.ts, refresh.interceptor.ts,
│                error.interceptor.ts, loading.interceptor.ts
├── models/enums.ts, labels.ts, user.ts, institution.ts, room.ts, page.ts
├── notifications/notification.service.ts
└── util/server-clock.ts, format.ts, idempotency.ts, submitting.ts
```

## 12. Testes desta parte

1. `refresh` concorrente: três requisições recebem `401` simultaneamente → exatamente **uma**
   chamada a `/auth/refresh` e as três são repetidas com o novo token.
2. Refresh que falha limpa a sessão e navega para `/login`.
3. `403` não dispara refresh.
4. Uma requisição já repetida que recebe `401` de novo não entra em laço.
5. `ProblemDetails` com `fieldErrors` vira `ApiError.fieldErrors` preservando `field` e
   `message`.
6. Erro de rede (status `0`) vira `ApiError.isOffline`.
7. `ServerClock.remainingMs` aplica o desvio medido e é imune a mudança do relógio local.
8. `roleGuard` bloqueia perfil errado e libera o correto.
9. `ApiClient` omite parâmetros nulos e monta `sort` corretamente.

## 13. Critérios de aceite

- Nenhum componente de feature precisa importar `HttpClient`, `localStorage` ou
  `HttpErrorResponse`.
- Existe exatamente um ponto no código que escreve o header `Authorization`.
- Existe exatamente um ponto que chama `/auth/refresh`.
- Todo tipo `Patch*` do contrato exige `version` obrigatoriamente.
- Nenhum tipo de tentativa em andamento possui campo de gabarito.