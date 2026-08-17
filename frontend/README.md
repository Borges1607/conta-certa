# Conta Certa — Frontend

Angular 22 + PrimeNG 21. Implementa a [spec de integração](../docs/frontend-integration-spec.md)
segundo o plano em [`docs/frontend/`](../docs/frontend/).

## Pré-requisitos

- Node.js ^22.22.3, ^24.15.0 ou ≥ 26 (testado em 26.1)
- npm ≥ 10

## Executar

```bash
npm install
npm start          # http://localhost:4200
```

Por padrão o app sobe com o **mock da API ligado** e não precisa do backend.

### Contra o backend real

Em `src/environments/environment.ts`, troque:

```ts
useMockApi: false,
```

Essa é a **única** mudança necessária. O `ng serve` já encaminha `/api/v1` para
`http://localhost:8080` via `proxy.conf.json`.

## Contas de demonstração (mock)

Senha de todas: `senha123`

| E-mail | Perfil | Serve para ver |
|---|---|---|
| `admin@contacerta.dev` | Administrador | Painel do admin |
| `ana@contacerta.dev` | Professor | Acervo, salas e trilhas |
| `carla@contacerta.dev` | Aluno | Duas salas, com progresso |
| `diego@contacerta.dev` | Aluno | Progresso menor, para o ranking |
| `bruno@contacerta.dev` | Professor **pendente** | O bloqueio de e-mail não confirmado no login |

Os links de confirmação de e-mail, recuperação de senha e convite não são
enviados: o mock **imprime a URL no console do navegador**. Procure por `[mock]`.

O estado do mock vive em `sessionStorage` e sobrevive a recargas. Para começar
do zero, limpe o storage da aba ou abra uma aba anônima.

## Comandos

| Comando | O que faz |
|---|---|
| `npm start` | Servidor de desenvolvimento |
| `npm run build` | Build de produção em `dist/` |
| `npm test` | Testes em modo watch |
| `npm run test:ci` | Testes uma vez |
| `npm run lint` | ESLint (TS + templates) |
| `npm run check:rules` | Varreduras arquiteturais (ver abaixo) |
| `npm run verify` | **Portão completo**: lint + regras + testes + build |
| `npm run format` | Prettier |

## Por que o `.npmrc` existe

Duas coisas não óbvias vivem lá, e as duas são necessárias:

**`node-options=--no-experimental-webstorage`.** A partir do Node 26 existe um
`localStorage` nativo em `globalThis`, e o ambiente jsdom do Vitest só instala
globais que ainda não existem — sem a flag, os 55 testes que tocam em storage
quebram com `Cannot read properties of undefined`. Rode os testes pelos scripts
do npm, não chamando o `ng` direto, ou a flag não é aplicada.

**`legacy-peer-deps=true`.** O PrimeNG está deliberadamente na 21 sobre o
Angular 22. A partir da 22 o PrimeNG exige chave de licença: sem ela, o
`providePrimeNG` injeta um banner vermelho "Invalid PrimeUI License" em toda
página — e o caminho não tem guard de dev, então vale para produção também. O
PrimeNG 21 declara peer de `@angular/*` ^21, daí o flag. A combinação foi
validada em build, nos 153 testes e navegando o app.

Se um dia houver licença do PrimeNG, dá para subir para a 22: passe a chave em
`providePrimeNG({ license: ... })` e mova o `borderRadius` do `ccPreset` de
`semantic` para `primitive` (mudou no `@primeuix/themes` v3).

## Como o projeto está organizado

```
src/app/
├── core/       # HTTP, sessão, erros, modelos do contrato — sem UI
├── shared/     # Componentes, pipes e shells reutilizáveis
└── features/   # public, account, student, teacher, admin, errors
src/mocks/      # "Backend" in-memory, carregado sob demanda
```

Regras de dependência, verificadas por ESLint:

- `core` não importa de `shared` nem de `features`
- `shared` não importa de `features`
- `features/x` não importa de `features/y`

## As varreduras arquiteturais

`npm run check:rules` reprova código que viola critérios da §11 da spec de
integração e que testes cobrem mal — por exemplo, aritmética sobre nota, XP ou
estrelas (o frontend **não** recalcula resultados), `Date.now()` em contexto de
tentativa, `[innerHTML]` em template e URL da API em `href`.

Violação legítima se marca com `cc-allow: <id-da-regra>` na linha, como um
`eslint-disable`. O ESLint cobre o resto: `innerHTML` e `bypassSecurityTrust*`
só existem em `cc-markdown` e `cc-video-embed`, cada um com a justificativa no
próprio arquivo.

## Estado da implementação

| Parte | Documento | Situação |
|---|---|---|
| 1 — Núcleo (HTTP, auth, erros) | [`01-nucleo.md`](../docs/frontend/01-nucleo.md) | ✅ completa, com testes |
| 2 — Design system e shells | [`02-design-system.md`](../docs/frontend/02-design-system.md) | ✅ completa, com testes |
| 3 — Jornada pública e conta | [`03-publico.md`](../docs/frontend/03-publico.md) | ✅ completa |
| 4 — Aluno | [`04-aluno.md`](../docs/frontend/04-aluno.md) | ✅ completa, com testes de integração |
| 5 — Professor | [`05-professor.md`](../docs/frontend/05-professor.md) | ✅ completa, com testes de integração |
| 6 — Administrador | [`06-admin.md`](../docs/frontend/06-admin.md) | ✅ completa, com testes de integração |
| 7 — Qualidade e mock | [`07-qualidade-entrega.md`](../docs/frontend/07-qualidade-entrega.md) | ✅ varreduras, mock completo dos três perfis e 153 testes |

Tudo roda de ponta a ponta sobre o mock:

1. cadastro → confirmação de e-mail → login → troca de senha → perfil;
2. **aluno**: entrar em sala por código → trilha → lição → tentativa →
   resultado → ranking, conquistas, videoaulas e materiais;
3. **professor**: painel → salas → trilha e alunos → acervo de lições e
   questões → videoaulas e materiais → relatórios com gráficos, CSV e impressão;
4. **admin**: painel → instituições → professores (convite, ativação, reset) →
   dicas financeiras.

O código da sala "2º ano A" é `A7K9Q2`. A aluna Carla já tem progresso semeado,
para ranking, conquistas e histórico terem o que mostrar.
