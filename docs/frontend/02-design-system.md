# Parte 2 — Design system, shells e componentes compartilhados

Depende de: Parte 1.
Referência normativa: §2.1 e §9 de [`../frontend-integration-spec.md`](../frontend-integration-spec.md).

## 1. Objetivo

Entregar a linguagem visual e os blocos reutilizáveis para que as Partes 3 a 6 escrevam
apenas lógica de tela. Ao final desta parte, os oito estados obrigatórios da §9 da spec são
componentes prontos, não algo que cada página reimplementa.

## 2. Identidade visual

O Conta Certa é uma plataforma educacional gamificada para ensino médio. A interface do aluno
é lúdica e colorida; a do professor e a do admin são densas e sóbrias. Mesmo tema, ênfases
diferentes.

Tema PrimeNG: preset **Aura** customizado via `definePreset`, em `styles/_theme.scss`.

| Token | Valor | Uso |
|---|---|---|
| `primary` | Azul `#2563eb` | Ações principais, marca |
| `secondary` | Cinza do Aura (`severity="secondary"`) | Ações de apoio, cancelar |
| `success` | Verde `#16a34a` | Aprovado, publicado, ativo |
| `warn` | Âmbar `#d97706` | Rascunho, prazo próximo, pendente |
| `danger` | Vermelho `#dc2626` | Reprovado, erro, destrutivo |
| `xp` | Ciano `#0891b2` | XP e nível |
| `star` | Amarelo `#eab308` | Estrelas |
| Raio | `12px` padrão, `20px` em cards do aluno | — |

Modo escuro segue o mecanismo do Aura (`.cc-dark` na raiz), com o seletor
`darkModeSelector` configurado. A preferência é persistida e respeita
`prefers-color-scheme` quando o usuário nunca escolheu.

Tipografia: fonte do sistema. Escala 12/14/16/20/24/32. Peso 600 para títulos.

Acessibilidade: contraste mínimo AA; foco visível em todo elemento interativo; nenhum estado
comunicado só por cor — estrelas têm número, status têm rótulo, gráficos têm legenda.

## 3. Shells de layout — `shared/layout/`

Três shells, um por perfil, sobre uma base comum.

### 3.1 Base comum

- Barra de progresso indeterminada no topo, ligada a `pendingRequests` da Parte 1.
- `p-toast` e `p-confirmdialog` montados uma única vez, no shell.
- Menu do usuário: nome, e-mail, perfil, alternar tema, "Minha conta", "Sair".
- Banner global de offline, exibido enquanto `navigator.onLine` for falso, com o texto
  "Você está sem conexão. Alguns dados podem estar desatualizados." Nenhum dado de tempo é
  inventado enquanto esse banner está visível.
- Área de conteúdo com largura máxima de 1280px, salvo telas de tentativa e relatório.

> **Decisão de implementação.** `TeacherShell` e `AdminShell` são a mesma
> estrutura com itens de menu diferentes, então viraram um único
> `cc-sidebar-shell` parametrizado por `items`. Já o shell do aluno depende do
> contexto de sala — dados que pertencem à Parte 4 — e `shared` não pode
> importar de `features` (visão geral, §4). Por isso ele mora em
> `features/student/layout/student-shell/` e é entregue na Parte 4, usando as
> peças genéricas desta parte.

### 3.2 `StudentShell` (implementado na Parte 4)

- Cabeçalho com logo, seletor da sala atual, XP e nível da sala selecionada, estrelas.
- O seletor de sala navega trocando `roomId` na URL; ele nunca troca dados em memória sem
  mudar a rota. É o que garante o isolamento por sala em recarga e histórico do navegador.
- Navegação da sala: Trilha, Videoaulas, Materiais, Ranking, Conquistas.
- Em telas estreitas vira menu inferior.

### 3.3 `TeacherShell` e `AdminShell`

- Menu lateral recolhível com ícones PrimeIcons; em telas estreitas, `p-drawer`.
- Professor: Dashboard, Salas, Lições, Questões, Videoaulas, Materiais, Relatórios.
- Admin: Dashboard, Instituições, Professores, Dicas financeiras.
- Trilha de navegação (`p-breadcrumb`) nas telas de detalhe.

## 4. Componentes compartilhados — `shared/components/`

Todos standalone, `OnPush`, sem dependência de feature.

### 4.1 Estados de tela

| Componente | Papel |
|---|---|
| `cc-loading-skeleton` | Esqueletos por formato: `list`, `card-grid`, `table`, `form`, `detail`, `stats` |
| `cc-empty-state` | Ícone, título, texto e **ação contextual obrigatória** |
| `cc-error-state` | Mensagem **e ação** por status |

> **Decisão de implementação.** A versão 1.0 desta spec previa um container
> `cc-page-state` que orquestrava os quatro estados. Ele foi descartado: para
> projetar o conteúdo, o container precisaria receber um `ng-template` com
> contexto tipado, porque `<ng-content>` é avaliado no contexto do pai e
> quebraria ao acessar `data` durante o carregamento. A alternativa — templates
> com guarda de contexto — troca clareza por cerimônia. Com o fluxo de controle
> do Angular, compor é mais direto e continua não deixando página nenhuma
> escrever o próprio esqueleto:
>
> ```html
> @if (rooms.isLoading()) {
>   <cc-loading-skeleton kind="card-grid" />
> } @else if (rooms.error(); as error) {
>   <cc-error-state [error]="error" (retry)="rooms.retry()" />
> } @else if (rooms.data(); as data) {
>   …
> }
> ```
>
> `createPageState` (§6) continua sendo o dono do ciclo de carga.

`cc-error-state` diferencia visualmente os quatro status que a spec exige separar:

- `401`: "Sessão expirada" com ação "Entrar novamente".
- `403`: "Sem permissão" com ação "Voltar".
- `404`: "Não encontrado" com ação "Voltar".
- `409`: "Dados desatualizados" com ação "Recarregar" — nunca oferece salvar por cima.

`cc-empty-state` recusa-se a existir sem ação: o texto do botão é entrada obrigatória. Isso
cumpre "vazio com ação contextual" por construção.

### 4.2 Formulários

| Componente | Papel |
|---|---|
| `cc-form-field` | Rótulo, controle projetado, texto de apoio e mensagem de erro |
| `cc-field-errors` | Casa `fieldErrors` do `ApiError` com o nome do controle |
| `cc-submit-button` | Botão ligado ao `submitGuard`; `loading` e `disabled` automáticos |

Fluxo de erro de validação, cumprindo "formulários exibem `fieldErrors` junto aos campos" e
"preservação de formulário quando houver erro":

1. A submissão falha com `422`.
2. A página chama `applyFieldErrors(form, error.fieldErrors)`, helper de `shared/`.
3. Cada `field` recebe `setErrors({ server: message })` no controle correspondente.
4. **O formulário não é resetado.** Os valores digitados permanecem.
5. O primeiro campo com erro recebe foco.
6. Qualquer alteração do usuário no campo limpa o erro de servidor daquele campo.

Erros com `field` sem controle correspondente vão para um resumo no topo do formulário, para
que nada seja silenciosamente perdido.

### 4.3 Conteúdo e mídia

| Componente | Papel |
|---|---|
| `cc-markdown` | Renderiza Markdown com KaTeX, sanitizado |
| `cc-file-upload` | Upload com progresso, validação e tratamento de `413`/`415` |
| `cc-secure-file-link` | Abre arquivo privado via endpoint autorizado |
| `cc-video-embed` | Player para link externo |

**`cc-markdown` é um componente de segurança.** Regras:

- Pipeline: `marked` → KaTeX nos delimitadores `$...$` e `$$...$$` → `DOMPurify.sanitize`.
- `DOMPurify` com lista de permissão restrita: formatação, listas, tabelas, código, links e
  imagens; e a lista de tags do KaTeX (MathML e spans com classes `katex-*`).
- Proibidos sempre: `script`, `style`, `iframe`, `object`, `embed`, `form`, atributos `on*`,
  `srcdoc` e URLs `javascript:` ou `data:` fora de imagem.
- Links externos recebem `target="_blank"` e `rel="noopener noreferrer nofollow"`.
- A saída é atribuída por `innerHTML` **somente após** a sanitização.
- Há teste dedicado com payloads maliciosos. Ver Parte 7.

`cc-file-upload`:

- Valida tipo e tamanho antes de enviar; PDF/PPT/PPTX até 10 MB, imagens PNG/JPEG/WebP até
  5 MB, conforme §7.4 da spec.
- Mostra percentual real a partir dos eventos de progresso do `HttpClient`.
- Trata `413` e `415` com mensagens específicas e mantém o arquivo selecionado para correção.
- Permite cancelar o envio em andamento.

`cc-secure-file-link` nunca coloca a URL da API num `href`. Busca o blob autorizado, abre a
URL de objeto e a revoga depois.

### 4.4 Domínio compartilhado

| Componente | Papel |
|---|---|
| `cc-star-rating` | 0–3 estrelas, somente leitura, com rótulo textual acessível |
| `cc-xp-badge` | XP e nível da sala |
| `cc-status-tag` | `p-tag` colorido a partir de qualquer enum de status |
| `cc-progress-bar` | Percentual 0–100 com rótulo |
| `cc-countdown` | Cronômetro a partir de `expiresAt` |
| `cc-page-header` | Título, subtítulo, breadcrumb e ações |
| `cc-data-table` | `p-table` com paginação servidor, ordenação e estados integrados |

`cc-countdown` usa exclusivamente `ServerClock.remainingMs(expiresAt)`, recalculado a cada
segundo, e emite `expired` uma única vez. Nunca decrementa contador local. Muda de cor abaixo
de 5 minutos e de 1 minuto. Ao voltar de aba oculta ou de recarga, apresenta o valor correto
sem ajuste especial, porque sempre recalcula a partir do instante do servidor.

`cc-data-table` encapsula o par `Page<T>` + `PageQuery`: recebe um carregador
`(query) => Promise<Page<T>>`, cuida de `lazy`, `rows`, `totalRecords` e `sortField`, e
delega vazio e erro aos componentes da §4.1. Respeita o limite de `size` 100.

## 5. Pipes e diretivas — `shared/pipes/`, `shared/directives/`

- `ccDateTime` / `ccDate`: fixos em `America/Sao_Paulo`.
- `ccMoney`: recebe `string`, formata em BRL, sem passar por `float`.
- `ccPercent`: número 0–100.
- `ccEnumLabel`: usa os mapas de `core/models/labels.ts`.
- `ccRelativeTime`: "há 2 dias", baseado no `ServerClock`.
- `ccAutofocus`, `ccTrapFocus` para diálogos e formulários.

## 6. Padrão de estado de página

Para uniformizar carregamento e atualização em segundo plano, toda página usa este formato:

```ts
type PageState<T> =
  | { kind: 'loading' }
  | { kind: 'ready'; data: T; refreshing: boolean }
  | { kind: 'error'; error: ApiError };
```

Distinção obrigatória: `loading` é a primeira carga e mostra esqueleto; `refreshing` é
atualização em segundo plano e mantém os dados na tela com um indicador discreto. Nunca
substituir conteúdo já visível por esqueleto numa atualização.

Um helper `createPageState(loader)` em `shared/` implementa esse ciclo, incluindo
`retry()` e `refresh()`.

## 7. Responsividade

Pontos de quebra: 640, 768, 1024, 1280. Regras:

- Tabelas do professor e do admin viram lista de cards abaixo de 768px.
- Grades de cards colapsam de 4 para 2 para 1 coluna.
- Menu lateral vira drawer abaixo de 1024px.
- A tela de tentativa é utilizável em celular: uma questão por vez, cronômetro fixo no topo.

## 8. Entregáveis

```
shared/
├── layout/ student-shell/, teacher-shell/, admin-shell/, app-topbar/, app-menu/
├── components/ page-state/, loading-skeleton/, empty-state/, error-state/,
│               form-field/, field-errors/, submit-button/, markdown/,
│               file-upload/, secure-file-link/, video-embed/, star-rating/,
│               xp-badge/, status-tag/, progress-bar/, countdown/,
│               page-header/, data-table/
├── pipes/ date-time, date, money, percent, enum-label, relative-time
├── directives/ autofocus, trap-focus
└── forms/ apply-field-errors.ts, page-state.ts
```

## 9. Testes desta parte

1. `cc-markdown` neutraliza `<script>`, `<img onerror>`, `javascript:` e `<iframe>`; e
   renderiza corretamente `$E=mc^2$`, tabela e bloco de código.
2. `cc-countdown` com `expiresAt` no passado emite `expired` imediatamente e uma vez só.
3. `cc-countdown` mostra o valor correto após simulação de recarga.
4. `applyFieldErrors` marca os controles certos e preserva os valores digitados.
5. `cc-file-upload` rejeita 11 MB e tipo não aceito antes de qualquer requisição.
6. `cc-empty-state` não compila sem ação.
7. `cc-data-table` emite a query correta ao paginar e ordenar.

## 10. Critérios de aceite

- Nenhuma página das Partes 3 a 6 escreve o próprio esqueleto, vazio ou erro.
- Nenhum `innerHTML` na aplicação fora do `cc-markdown`.
- Nenhuma URL de arquivo da API aparece em `href` ou `src` de template.
- Todo botão de mutação está ligado a um `submitGuard`.
- Modo claro e escuro legíveis em todas as telas do shell.