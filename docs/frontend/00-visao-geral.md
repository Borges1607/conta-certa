# Conta Certa — Frontend: Visão Geral e Divisão em Partes

Status: aprovado para implementação
Versão: 1.0
Data: 2026-08-15
Documento-pai: [`../frontend-integration-spec.md`](../frontend-integration-spec.md)

## 1. Escopo deste conjunto de documentos

A spec de integração define **o que** o frontend precisa fazer e qual é o contrato com a API.
Este conjunto define **como** isso será construído em Angular + PrimeNG e **em que ordem**.

Regra de precedência: em qualquer divergência, `frontend-integration-spec.md` vence. Nenhuma
decisão daqui pode alterar contrato de API, regra de negócio, cálculo de XP/estrelas ou
política de autorização.

## 2. Stack

| Camada | Escolha |
|---|---|
| Framework | Angular 22 (standalone components, sem NgModules) |
| Linguagem | TypeScript em `strict`, `strictTemplates` ligado |
| Estado | Signals + `computed`; RxJS apenas na borda HTTP |
| UI | PrimeNG 21 com tema Aura, PrimeIcons (a 22 exige licença paga — ver README do frontend) |
| Layout | PrimeFlex-like via utilitários próprios + CSS Grid/Flex nativo |
| Gráficos | `primeng/chart` (Chart.js) |
| Markdown | `marked` + `DOMPurify` + `katex` |
| HTTP | `HttpClient` com interceptors funcionais |
| Rotas | Router standalone, lazy por feature, guards funcionais |
| Testes | Vitest/Karma conforme scaffold + `HttpTestingController` |

Sem NgRx, sem NgModules, sem `any` não justificado, sem `subscribe` em componente quando
`resource`/`async` resolve.

## 3. Princípios inegociáveis

Derivados da §11 da spec de integração. Toda parte deve respeitá-los.

1. **A API é a fonte de verdade.** O frontend nunca recalcula nota, XP, estrelas, nível,
   aprovação, desbloqueio ou ranking. Ele apresenta o que a API retornou.
2. **Nenhum gabarito antes do fim.** Nenhum modelo, store ou componente de tentativa em
   andamento pode conter `correctAnswer`, `explanation` ou `correct` por questão. O tipo do
   payload em andamento é diferente do tipo do resultado — isso é garantido pelo compilador.
3. **Tempo é do servidor.** Cronômetros derivam de `expiresAt` e do desvio medido entre o
   relógio local e o servidor. Nunca de `setTimeout` acumulado. Sobrevivem a recarga.
4. **Isolamento por sala.** XP, progresso, tentativas, ranking e conquistas são sempre
   indexados por `roomId`. Trocar de sala não pode vazar dados da anterior.
5. **Refresh único.** No máximo uma operação de refresh em andamento; requisições
   concorrentes que receberem `401` aguardam a mesma operação.
6. **`version` sempre.** Recursos editáveis reenviam `version` e tratam `409` recarregando,
   nunca sobrescrevendo.
7. **Markdown sanitizado.** KaTeX renderizado, HTML bruto e scripts jamais executados.
8. **Todo estado de tela é explícito.** Carregando, vazio, erro, sem permissão, não
   encontrado e conflito são estados distintos e visíveis.

## 4. Estrutura de pastas

```
frontend/
├── src/
│   ├── main.ts
│   ├── styles/
│   │   ├── styles.scss              # entrada global
│   │   ├── _theme.scss              # preset Aura customizado (tokens Conta Certa)
│   │   └── _utilities.scss          # utilitários de layout
│   ├── environments/
│   │   ├── environment.ts           # dev: apiBaseUrl, useMockApi
│   │   └── environment.prod.ts
│   └── app/
│       ├── app.config.ts            # providers raiz
│       ├── app.routes.ts            # rotas raiz + lazy por área
│       ├── app.ts                   # componente raiz
│       │
│       ├── core/                    # PARTE 1 — sem dependência de UI de feature
│       │   ├── api/                 # ApiClient, tipos de paginação, Problem Details
│       │   ├── auth/                # AuthStore, TokenStorage, sessão, guards
│       │   ├── interceptors/        # auth, refresh, erro, loading, mock
│       │   ├── models/              # tipos e enums do contrato da API
│       │   ├── notifications/       # wrapper de toast/confirm
│       │   └── util/                # datas, moeda, clock skew, idempotency key
│       │
│       ├── shared/                  # PARTE 2 — componentes reutilizáveis burros
│       │   ├── components/
│       │   ├── directives/
│       │   ├── pipes/
│       │   └── layout/              # shells por perfil
│       │
│       └── features/
│           ├── public/              # PARTE 3
│           ├── student/             # PARTE 4
│           ├── teacher/             # PARTE 5
│           └── admin/               # PARTE 6
├── proxy.conf.json                  # /api/v1 -> http://localhost:8080
├── angular.json
├── package.json
└── tsconfig.json
```

Cada feature segue o mesmo formato interno:

```
features/<area>/
├── <area>.routes.ts        # rotas lazy da área
├── data/                   # services HTTP + stores da área
├── models/                 # tipos exclusivos da área (o compartilhado vai em core/models)
└── pages/<pagina>/         # componente de página + template + estilo
```

Regras de dependência, verificadas em revisão:

- `core` não importa de `shared` nem de `features`.
- `shared` importa de `core`, nunca de `features`.
- `features/x` nunca importa de `features/y`. O que for comum sobe para `shared` ou `core`.

## 5. Mapa de rotas

| Rota | Guard | Área |
|---|---|---|
| `/login` | `guestGuard` | Público |
| `/cadastro` | `guestGuard` | Público |
| `/verificar-email` | — | Público |
| `/esqueci-senha`, `/redefinir-senha` | — | Público |
| `/convite-professor` | — | Público |
| `/aluno/salas` | `authGuard` + `roleGuard(STUDENT)` | Aluno |
| `/aluno/salas/:roomId` | idem | Aluno |
| `/aluno/salas/:roomId/trilha` | idem | Aluno |
| `/aluno/salas/:roomId/licoes/:lessonId` | idem | Aluno |
| `/aluno/tentativas/:attemptId` | idem | Aluno |
| `/aluno/tentativas/:attemptId/resultado` | idem | Aluno |
| `/aluno/salas/:roomId/{videos,materiais,ranking,conquistas}` | idem | Aluno |
| `/professor/...` | `authGuard` + `roleGuard(TEACHER)` | Professor |
| `/admin/...` | `authGuard` + `roleGuard(ADMIN)` | Admin |
| `/conta/senha`, `/conta/perfil` | `authGuard` | Comum |
| `/403`, `/404` | — | Comum |

Após login, o redirecionamento é por `user.role`: `ADMIN → /admin`, `TEACHER → /professor`,
`STUDENT → /aluno/salas`. Se `mustChangePassword` for `true`, o usuário é levado a
`/conta/senha` e nenhuma outra rota autenticada é acessível até a troca.

`roomId` faz parte da URL em toda tela do aluno ligada a sala. Isso é o que garante o
princípio 4 na navegação e no recarregamento.

## 6. As partes

Cada parte é entregável e testável isoladamente. As dependências são estritas: uma parte só
começa quando as que ela lista como pré-requisito estiverem concluídas.

| # | Parte | Documento | Depende de |
|---|---|---|---|
| 1 | Núcleo: HTTP, auth, erros | [`01-nucleo.md`](01-nucleo.md) | — |
| 2 | Design system e shells | [`02-design-system.md`](02-design-system.md) | 1 |
| 3 | Jornada pública | [`03-publico.md`](03-publico.md) | 1, 2 |
| 4 | Frontend do aluno | [`04-aluno.md`](04-aluno.md) | 1, 2 |
| 5 | Frontend do professor | [`05-professor.md`](05-professor.md) | 1, 2 |
| 6 | Frontend do administrador | [`06-admin.md`](06-admin.md) | 1, 2 |
| 7 | Qualidade, mock e entrega | [`07-qualidade-entrega.md`](07-qualidade-entrega.md) | todas |

As partes 4, 5 e 6 não se tocam e podem ser desenvolvidas em paralelo depois da 2.

## 7. Convenções de código

- Componentes standalone, `changeDetection: OnPush`, seletor com prefixo `cc-`.
- Um componente por arquivo; template inline só até 15 linhas.
- Nomes de arquivo em kebab-case; classes em PascalCase terminando pelo papel
  (`StudentRoomsPage`, `AttemptStore`, `TeacherRoomService`).
- Injeção por `inject()`, não por construtor.
- Entradas/saídas com `input()` / `output()`, não decorators.
- Fluxo de template com `@if` / `@for` / `@switch`; `*ngIf` e `*ngFor` não são usados.
- Textos de interface em pt-BR; identificadores, tipos e campos da API em inglês.
- Formatação de datas sempre pelo pipe próprio, fixado em `America/Sao_Paulo`.
- Nenhum literal de rota de API espalhado: todas ficam nos services de `data/`.

## 8. Configuração de ambiente

```ts
// environments/environment.ts
export const environment = {
  production: false,
  apiBaseUrl: '/api/v1',
  useMockApi: true,          // Parte 7: interceptor in-memory
  presentationTimeZone: 'America/Sao_Paulo',
};
```

`proxy.conf.json` encaminha `/api/v1` para `http://localhost:8080` no `ng serve`. Quando
`useMockApi` for `true`, o interceptor de mock responde antes de a requisição sair, e o proxy
é irrelevante. A troca entre mock e backend real é só essa flag: nenhum service muda.

## 9. Definição de pronto (aplicável a toda parte)

Uma parte só é considerada concluída quando:

1. `npm run build` passa sem erro e sem novo warning.
2. `npm run lint` passa.
3. Os testes da parte passam.
4. Todos os estados obrigatórios da §9 da spec de integração existem nas telas da parte.
5. Nenhuma regra da §3 deste documento foi violada.
6. Os critérios de aceite listados no documento da própria parte foram verificados.