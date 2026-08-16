# Parte 7 — Qualidade, mock e entrega

Depende de: todas as anteriores. Parte do trabalho (o mock) precede as Partes 3 a 6 na
prática, porque é o que permite rodá-las sem backend.

## 1. Mock plugável da API

O backend ainda não expõe endpoints. O frontend é escrito **contra a API real** e ganha um
interceptor de mock que responde no lugar da rede quando `environment.useMockApi` é `true`.

Regras que tornam o mock seguro:

1. Nenhum service, componente ou store sabe que o mock existe. A troca é só a flag.
2. O mock vive em `src/mocks/`, fora de `app/`, e é importado apenas em `app.config.ts`.
3. O mock responde no **formato exato do contrato**: `Page<T>` com os cinco campos,
   `application/problem+json` nos erros, instantes em ISO 8601 UTC, dinheiro e resposta
   numérica como string, percentuais de 0 a 100.
4. O mock aplica **as regras de negócio que o frontend não pode aplicar**: ele é quem calcula
   nota, estrelas, XP, aprovação e desbloqueio, exatamente como o servidor faria. Isso
   mantém honesto o princípio de que o frontend só apresenta.
5. Latência simulada de 150–400 ms, para que estados de carregamento sejam reais.
6. Um painel de desenvolvimento permite forçar cenários: erro `500`, `409` de versão, `403`,
   offline, tentativa expirada e lentidão.

Estrutura:

```
src/mocks/
├── mock-api.interceptor.ts
├── db.ts                 # estado in-memory, semeado e persistido em sessionStorage
├── seed.ts               # dados de exemplo
├── handlers/ auth.ts, student.ts, teacher.ts, admin.ts, files.ts
└── scenarios.ts          # forçar erros e latência
```

Semente mínima: uma instituição, um admin, dois professores, quatro alunos, duas salas com
trilhas diferentes, cinco lições publicadas com questões dos quatro tipos, um rascunho, uma
lição arquivada, vídeos, materiais (um link e um arquivo), tentativas concluídas com notas
variadas, uma tentativa em andamento com `expiresAt` próximo, ranking com empate, conquistas
parcialmente desbloqueadas e dicas financeiras agendadas.

Credenciais de demonstração ficam documentadas no `README` do frontend.

## 2. Estratégia de testes

Pirâmide enxuta e focada nos riscos reais desta aplicação.

### 2.1 Obrigatórios — os testes que protegem os critérios da spec

Estes não são opcionais; cada um corresponde a um item da §11 da spec de integração.

| # | Critério da spec | Onde |
|---|---|---|
| 1 | Nenhum gabarito antes do fim | Parte 4, testes 1 e 2 |
| 2 | Cronômetro do servidor, sobrevive a recarga | Parte 4, testes 3 a 6 |
| 3 | Troca de sala não mistura dados | Parte 4, teste 7 |
| 4 | Refresh concorrente único | Parte 1, testes 1 a 4 |
| 5 | `fieldErrors` junto aos campos | Parte 2, teste 4 |
| 6 | `version` e conflito sem sobrescrita | Partes 5 e 6 |
| 7 | Arquivos privados só por endpoint autorizado | Parte 2 e varredura da §3 abaixo |
| 8 | Markdown sanitizado com KaTeX | Parte 2, teste 1 |
| 9 | Estados distinguíveis | Partes 4 e 5 |
| 10 | Frontend não recalcula resultado | §3 abaixo |

### 2.2 Testes de unidade

Serviços de dados, stores, pipes de formatação, `ServerClock`, `applyFieldErrors` e o
pipeline de sanitização. `HttpTestingController` para verificar URL, query, headers e corpo
de cada chamada — inclusive `Idempotency-Key` e `version`.

### 2.3 Testes de componente

Renderização dos estados de cada página: carregando, vazio, erro, `403`, `404`, `409` e
conteúdo. Os quatro tipos de questão. O cronômetro. O editor de questão.

### 2.4 Verificação manual roteirizada

Um roteiro em `docs/frontend/roteiro-verificacao.md` com os fluxos ponta a ponta sobre o
mock: cadastro → confirmação → login → entrar em sala → trilha → tentativa → resultado;
professor criando lição, publicando e atribuindo; admin criando instituição e convidando
professor.

## 3. Varreduras automáticas

Regras verificáveis por busca no código, rodadas junto do lint. Cada uma protege um critério
que testes unitários cobrem mal:

| Busca | Regra |
|---|---|
| `innerHTML` | Só permitido em `cc-markdown` |
| `bypassSecurityTrust` | Proibido em todo o projeto |
| `Date.now()` em contexto de tentativa | Proibido; usar `ServerClock` |
| `Math.round`/`Math.ceil` perto de `stars`, `xp`, `score`, `level` | Proibido; a API decide |
| `apiBaseUrl` em template | Proibido; arquivo privado só por `cc-secure-file-link` |
| `localStorage` fora de `core/auth` e `src/mocks` | Proibido |
| `HttpClient` fora de `core/api` e `src/mocks` | Proibido |
| `parseFloat`/`Number(` sobre campo monetário | Proibido no caminho de envio |

Implementadas como regras de ESLint (`no-restricted-syntax`, `no-restricted-imports`) quando
possível, e como script `npm run check:rules` quando não.

## 4. Scripts

```json
{
  "start": "ng serve --proxy-config proxy.conf.json",
  "build": "ng build",
  "test": "ng test",
  "lint": "ng lint",
  "check:rules": "node scripts/check-rules.mjs",
  "verify": "npm run lint && npm run check:rules && npm run test -- --watch=false && npm run build"
}
```

`npm run verify` é o portão de cada parte.

## 5. Desempenho e acessibilidade

- Toda área é lazy-loaded; o pacote inicial carrega apenas o núcleo, o shell público e o
  login.
- `marked`, `DOMPurify`, `katex` e `chart.js` entram por import dinâmico, só quando a tela que
  os usa é aberta.
- Orçamento de build: 500 kB de aviso e 1 MB de erro para o pacote inicial.
- Navegação por teclado completa; foco visível; `aria-live` para toasts e para o cronômetro
  nos avisos de 5 e 1 minuto; `prefers-reduced-motion` desliga as animações de celebração.
- Idioma do documento `pt-BR`; `LOCALE_ID` configurado como `pt-BR`.

## 6. Entrega

`frontend/README.md` documenta: pré-requisitos, instalação, execução com mock, execução
contra o backend real, credenciais de demonstração, mapa das partes e como rodar `verify`.

Build de produção em `frontend/dist/`. O backend Spring pode servi-lo estaticamente ou o
artefato pode ir para um host de arquivos; em ambos os casos, é necessário o *fallback* de
SPA para `index.html`, sem o qual a navegação direta a uma rota profunda quebra.

## 7. Definição de pronto do projeto

- `npm run verify` passa.
- Os dez critérios da §11 da spec de integração têm teste ou varredura correspondente.
- As três áreas rodam ponta a ponta sobre o mock.
- Trocar `useMockApi` para `false` não exige nenhuma alteração de código.
- O `README` permite a outra pessoa subir o projeto sem perguntar nada.