# Conta Certa — Especificação Técnica e Funcional

> **Conta Certa** — Plataforma web de **educação financeira gamificada** para o ensino de
> Matemática Financeira. Alunos aprendem porcentagem, juros e descontos por meio de lições
> interativas com exercícios, ganhando XP, níveis e estrelas; professores criam salas,
> lições, questões, videoaulas e slides, e acompanham o progresso das turmas.

Este documento descreve a **organização** e o **funcionamento** da aplicação com base no
design/protótipo exportado do Figma (`Site de Matemática Financeira.zip`).

---

## 1. Visão Geral

| Item | Descrição |
|------|-----------|
| Nome | Conta Certa — "Educação Financeira Gamificada" |
| Tipo | Single Page Application (SPA) web, sem backend |
| Público | Alunos e professores do ensino fundamental/médio |
| Domínio | Matemática Financeira (porcentagem, juros simples/compostos, descontos) |
| Idioma | Português (pt-BR) |
| Persistência | 100% no `localStorage` do navegador (protótipo — ver §9) |

A aplicação tem **dois perfis de usuário** (aluno e professor), cada um com sua própria
interface e conjunto de funcionalidades. Não há servidor: todo o estado (usuários,
salas, progresso, conteúdos) é gravado no navegador.

---

## 2. Stack Tecnológica

| Camada | Tecnologia |
|--------|-----------|
| Framework | **React 18.3** + **TypeScript** |
| Build/Dev | **Vite 6** (`npm run dev`, `npm run build`) |
| Estilização | **Tailwind CSS v4** (`@tailwindcss/vite`) + `tw-animate-css` |
| Componentes UI | **shadcn/ui** sobre **Radix UI** (`src/app/components/ui/`) |
| Ícones | **lucide-react** |
| Notificações | **sonner** (toasts) |
| Gráficos | **recharts** (relatórios do professor) |
| Formulários | `react-hook-form` (disponível na base de componentes) |
| Entrada | `src/main.tsx` → renderiza `<App />` em `#root` |

Ponto de entrada: `src/main.tsx` → `src/app/App.tsx`.

---

## 3. Estrutura de Pastas

```
src/
├── main.tsx                      # bootstrap React
├── app/
│   ├── App.tsx                   # raiz: roteamento por estado, app do aluno
│   └── components/
│       ├── Login.tsx             # login (abas Aluno / Professor)
│       ├── Register.tsx          # cadastro (abas Aluno / Professor)
│       ├── Header.tsx            # cabeçalho do aluno (nível + XP)
│       ├── Logo.tsx              # logo "Conta Certa" (SVG)
│       ├── LessonCard.tsx        # card de lição (estado: bloqueada/completa/perfeita)
│       ├── ExerciseModal.tsx     # modal de exercícios (quiz da lição)
│       │
│       ├── TeacherDashboard.tsx  # painel do professor (7 abas)
│       ├── RoomManager.tsx       # CRUD de salas + código de acesso
│       ├── LessonManager.tsx     # CRUD de lições personalizadas
│       ├── QuestionManager.tsx   # CRUD/filtro de questões (padrão + custom)
│       ├── VideoManager.tsx      # CRUD de videoaulas + visualizações
│       ├── SlidesManager.tsx     # CRUD de slides (arquivo/link) + visualizações
│       ├── StudentReports.tsx    # relatórios, gráficos e exportação
│       │
│       ├── StudentVideos.tsx     # aba de videoaulas do aluno
│       ├── StudentSlides.tsx     # aba de slides do aluno
│       │
│       ├── figma/
│       │   └── ImageWithFallback.tsx
│       └── ui/                   # biblioteca shadcn/ui (button, card, dialog, tabs…)
└── styles/                       # globals.css, index.css, default_theme.css
```

---

## 4. Perfis de Usuário e Fluxo Principal

O `App.tsx` controla a navegação por estado (`currentView`: `"login" | "register" | "app"`)
e pelo tipo do usuário logado — **não há rotas de URL**.

```
                    ┌──────────────┐
                    │  Login/Cadastro │
                    └──────┬───────┘
              login aluno  │  login professor
        ┌──────────────────┴──────────────────┐
        ▼                                      ▼
┌───────────────────┐              ┌──────────────────────┐
│  App do Aluno       │              │ Dashboard do Professor │
│  (3 abas)           │              │ (7 abas)              │
│  • Lições           │              │ • Alunos              │
│  • Videoaulas       │              │ • Salas               │
│  • Slides           │              │ • Lições              │
└───────────────────┘              │ • Questões            │
                                    │ • Videoaulas          │
                                    │ • Slides              │
                                    │ • Relatórios          │
                                    └──────────────────────┘
```

### 4.1 Cadastro (`Register.tsx`)
- Abas separadas para **Aluno** e **Professor**.
- Campos: nome, email, senha (mín. 6 caracteres).
- Valida email duplicado; grava novo usuário em `localStorage["users"]`.
- Aluno **não** informa sala no cadastro — apenas no login.

### 4.2 Login (`Login.tsx`)
- **Aluno**: email + senha + **código da sala** (6 caracteres). O código é validado contra
  as salas existentes; ao logar, o `roomCode` é gravado no usuário. Isso vincula o aluno à
  turma/professor e determina quais conteúdos ele verá.
- **Professor**: email + senha (sem código de sala).
- Feedback via toasts (sonner).

---

## 5. Funcionalidades do Aluno

App do aluno (`App.tsx`, exibido quando `currentUser.type === "student"`), organizado em
**3 abas**:

### 5.1 Aba "Lições"
- **Cabeçalho de estatísticas**: saudação, Nível, XP Total, Lições concluídas, Estrelas.
- **Barra de progresso** para o próximo nível.
- **Grid de cards de estatísticas rápidas** (concluídas, estrelas, taxa de conclusão, nível).
- **Trilha de lições** (`LessonCard`): cards em grade com estados:
  - **Bloqueada** (`locked`): cadeado, botão desabilitado.
  - **Disponível**: botão "Começar Agora".
  - **Completa**: botão "Revisar Lição".
  - **Perfeita** (3/3 estrelas): selo "PERFEITO!" + animação.
- **Cards de "Dica Financeira do Dia"** e **"Conquistas Recentes"** (conquistas
  desbloqueadas conforme o progresso).

**Progressão / gamificação:**
- A **primeira lição** começa desbloqueada; as demais desbloqueiam em sequência ao concluir
  a anterior.
- Cada lição vale um **XP** fixo (10 a 30 pontos).
- **Nível** = `floor(totalXP / 100) + 1` (100 XP por nível).
- **Estrelas** por lição = `ceil((acertos / total de questões) × 3)` (0 a 3).

### 5.2 Exercícios (`ExerciseModal.tsx`)
- Quiz de múltipla escolha (4 opções) apresentado questão a questão.
- Barra de progresso, contador de acertos.
- Ao "Verificar": feedback imediato (correto/incorreto) + **explicação** da resposta.
- Ao finalizar: tela de parabéns com estrelas conquistadas e chamada de `onComplete(stars)`,
  que atualiza XP, marca a lição como concluída e desbloqueia a próxima.

### 5.3 Aba "Videoaulas" (`StudentVideos.tsx`)
- Lista videoaulas do professor da sala, **agrupadas por categoria**.
- Player em modal via `<iframe>` (converte URLs de YouTube/Vimeo para formato *embed*).
- Ao abrir um vídeo, ele é **marcado como assistido** (registro em `videoViews`).
- Mostra progresso: total, assistidas, percentual.

### 5.4 Aba "Slides" (`StudentSlides.tsx`)
- Lista slides do professor da sala, agrupados por categoria.
- Suporta **arquivo** (PDF exibido em iframe; PPT/PPTX via download) e **link** (Google
  Slides convertido para embed).
- Ao abrir, marca como visualizado (`slidesViews`) e exibe progresso.

---

## 6. Funcionalidades do Professor

Painel (`TeacherDashboard.tsx`) com **7 abas**:

### 6.1 Aba "Alunos"
- Cards resumo: **Total de Alunos**, **XP Médio**, **Alunos Ativos** (atividade ≤ 7 dias).
- Tabela com progresso de cada aluno da(s) sala(s) do professor: sala, email, XP, lições,
  estrelas, última atividade.

### 6.2 Aba "Salas" (`RoomManager.tsx`)
- **CRUD de salas**. Cada sala tem nome, **código único de 6 caracteres** (gerado
  aleatoriamente) e a lista de **lições liberadas** (`lessonIds`).
- Código copiável (Clipboard API com fallback) para distribuir aos alunos.
- Exibe contagem de alunos por sala.
- A seleção de lições define o que os alunos daquela sala verão (padrão + personalizadas).

### 6.3 Aba "Lições" (`LessonManager.tsx`)
- Lista as **6 lições padrão do sistema** (somente leitura) + **lições personalizadas** do
  professor (CRUD).
- Ao criar uma lição personalizada, abre um fluxo para **adicionar questões** em sequência.
- Excluir lição personalizada remove também suas questões associadas.
- Clicar numa linha navega para a aba "Questões" já filtrada por aquela lição.
- Cores selecionáveis (gradientes) para identidade visual da lição.

### 6.4 Aba "Questões" (`QuestionManager.tsx`)
- Combina **questões padrão** (embutidas no código, 4 por lição padrão) com **questões
  personalizadas** do professor.
- **CRUD** de questões personalizadas (enunciado, 4 opções, resposta correta, explicação).
- Questões padrão **não são editadas/excluídas diretamente**: podem ser **desabilitadas**
  (não aparecem mais para os alunos) ou usadas como base para criar uma cópia personalizada.
- **Filtros**: por lição, por tipo (padrão/personalizada), por status (ativa/desabilitada) e
  busca textual. Estatísticas de contagem por lição.

### 6.5 Aba "Videoaulas" (`VideoManager.tsx`)
- **CRUD** de videoaulas (título, categoria, URL de YouTube/Vimeo/etc., descrição).
- Agrupadas por categoria.
- Botão "Visualizações": lista quais alunos assistiram cada vídeo e quando.

### 6.6 Aba "Slides" (`SlidesManager.tsx`)
- **CRUD** de slides. Duas formas de origem:
  - **Upload de arquivo**: PDF/PPT/PPTX (máx. 10 MB), convertido para **base64** e salvo no
    `localStorage`.
  - **Link online**: Google Slides, SlideShare, etc.
- Agrupados por categoria; botão de visualizações por aluno.

### 6.7 Aba "Relatórios" (`StudentReports.tsx`)
- Filtro por sala (ou todas).
- Cards resumo: total de alunos, XP médio, taxa de conclusão, estrelas médias.
- **Gráficos (recharts)**: taxa de conclusão por lição (barras), média de estrelas por lição
  (linha), (distribuição de XP disponível no código).
- **Top 5 alunos** por XP.
- **Exportação**: CSV, relatório detalhado em TXT e **PDF via janela de impressão** do
  navegador.

---

## 7. Categorias e Conteúdo Padrão

**6 lições padrão** (ids `"1"`–`"6"`), cada uma com 4 exercícios embutidos:

| ID | Lição | XP | Cor |
|----|-------|----|-----|
| 1 | Introdução à Porcentagem | 10 | Roxo |
| 2 | Juros Simples | 15 | Azul |
| 3 | Juros Compostos | 20 | Verde |
| 4 | Descontos Sucessivos | 15 | Rosa |
| 5 | Aplicações Práticas | 25 | Laranja |
| 6 | Desafio Final | 30 | Amarelo |

**Categorias de videoaulas/slides**: Porcentagem, Juros Simples, Juros Compostos, Descontos
Sucessivos, Aplicações Práticas, Revisão Geral, Dicas e Truques.

---

## 8. Modelo de Dados (localStorage)

Toda a persistência usa chaves em `localStorage` contendo JSON. Entidades:

### `users` — array de usuários
```ts
{
  id: string;            // Date.now().toString()
  name: string;
  email: string;
  password: string;      // texto puro (protótipo)
  type: "student" | "teacher";
  roomCode?: string;     // sala atual do aluno (definida no login)
  createdAt: string;     // ISO
}
```

### `rooms` — array de salas
```ts
{
  id: string;
  name: string;
  code: string;          // 6 caracteres, único, gerado aleatoriamente
  teacherId: string;     // dono da sala
  lessonIds: string[];   // lições liberadas (padrão + personalizadas)
  createdAt: string;
}
```

### `customLessons` — lições personalizadas do professor
```ts
{
  id: string;            // "lesson_<timestamp>"
  title: string;
  description: string;
  color: string;         // classe de gradiente Tailwind
  createdBy: string;     // teacherId
  createdAt: string;
  isCustom?: boolean;
}
```

### `customQuestions` — questões personalizadas
```ts
{
  id: string;            // "custom_<timestamp>"
  lessonId: string;
  lessonTitle: string;
  question: string;
  options: string[];     // 4 alternativas
  correctAnswer: number; // índice 0–3
  explanation: string;
  createdBy: string;     // teacherId
  createdAt: string;
}
```

### `disabledDefaultQuestions` — questões padrão desabilitadas
```ts
{ questionId: string; teacherId: string; }
```

### `videos` / `slides` — conteúdos de mídia
```ts
// videos
{ id, title, description, videoUrl, category, createdBy, createdAt }
// slides (+ campos de arquivo)
{ id, title, description, slidesUrl, category, createdBy, createdAt,
  isFile?, fileName?, fileType? }   // slidesUrl = base64 quando isFile
```

### `videoViews` / `slidesViews` — registros de visualização
```ts
{ videoId|slidesId: string; studentId: string; studentName: string;
  viewedAt: string; completed: boolean; }
```

### `progress_<userId>` — progresso individual do aluno
```ts
{
  xp: number;
  lessons: Array<{ id: string; stars: number; completed: boolean; locked: boolean }>;
  lastActivity: string;  // ISO
}
```

---

## 9. Regras e Comportamentos Importantes

1. **Vínculo aluno ↔ professor** é feito pelo **código da sala**: o aluno vê apenas as
   lições liberadas na sala e os vídeos/slides criados pelo professor dono da sala.
2. **Montagem das lições do aluno** (`loadRoomConfig` em `App.tsx`): parte das 6 lições
   padrão filtradas por `room.lessonIds`, mescla questões personalizadas, adiciona lições
   personalizadas da sala e recalcula bloqueios (só a 1ª desbloqueada).
3. **Persistência de progresso**: salvo automaticamente em `progress_<userId>` sempre que XP
   ou lições mudam (`useEffect`).
4. **Sincronização entre componentes** do professor: evento customizado
   `window.dispatchEvent(new Event('lessonsUpdated'))` notifica gerenciadores quando lições
   mudam.
5. **Cálculos de gamificação**:
   - Nível: `floor(totalXP / 100) + 1`.
   - Estrelas: `ceil((acertos / totalQuestões) × 3)`, mantendo o melhor resultado.
   - "Aluno ativo": última atividade nos últimos 7 dias.

---

## 10. Design System / UI

- **Estética**: cores vivas, gradientes (roxo/azul/rosa/verde), cantos bem arredondados
  (`rounded-2xl`/`rounded-3xl`), emojis e microanimações — visual lúdico voltado a estudantes.
- **Componentes** baseados em shadcn/ui + Radix (Tabs, Dialog, Card, Table, Select, Badge,
  Progress, Checkbox, etc.), garantindo acessibilidade e consistência.
- **Layout responsivo** (mobile-first) com Tailwind: grids que colapsam em telas menores.
- **Feedback** sempre via toasts (sonner).
- **Logo** "Conta Certa": SVG com símbolo "$" e estrelas, em três tamanhos.

---

## 11. Limitações do Protótipo & Evolução Sugerida

Este é um **protótipo front-end** (exportação do Figma Make). Pontos a evoluir para produção:

| Área | Situação atual | Evolução recomendada |
|------|----------------|----------------------|
| Persistência | `localStorage` (por navegador) | Backend + banco de dados |
| Autenticação | Senha em texto puro no navegador | Auth server + hash + tokens (JWT) |
| Multiusuário | Não há sincronização entre dispositivos | API central |
| Roteamento | Por estado (sem URL) | React Router (rotas reais) |
| Upload de slides | base64 no localStorage (limite ~10 MB) | Armazenamento de arquivos (S3/afim) |
| Autorização | Baseada em `teacherId`/`type` no cliente | Validação no servidor |

---

## 12. Como Executar

```bash
npm i        # instala dependências
npm run dev  # inicia o servidor de desenvolvimento (Vite)
npm run build
```

Projeto original no Figma:
<https://www.figma.com/design/zjZaVeLgTVmPjUGmgWMMpl/Site-de-Matem%C3%A1tica-Financeira>