# Parte 4 — Frontend do aluno

Depende de: Partes 1 e 2.
Referência normativa: §6 e §11 de [`../frontend-integration-spec.md`](../frontend-integration-spec.md).

## 1. Objetivo

Entregar a experiência do aluno: salas, trilha, lição, tentativa avaliada, mídias, ranking e
conquistas. É a parte com os requisitos mais rígidos da spec — sigilo do gabarito, tempo do
servidor e isolamento por sala.

## 2. Rotas

| Rota | Tela |
|---|---|
| `/aluno/salas` | Minhas salas + entrar por código |
| `/aluno/salas/:roomId` | Dashboard da sala |
| `/aluno/salas/:roomId/trilha` | Trilha de lições |
| `/aluno/salas/:roomId/licoes/:lessonId` | Lição: teoria, materiais, histórico |
| `/aluno/tentativas/:attemptId` | Tentativa em andamento |
| `/aluno/tentativas/:attemptId/resultado` | Resultado corrigido |
| `/aluno/salas/:roomId/videos` | Videoaulas |
| `/aluno/salas/:roomId/materiais` | Materiais |
| `/aluno/salas/:roomId/ranking` | Ranking da sala |
| `/aluno/salas/:roomId/conquistas` | Conquistas |

`roomId` está na URL de tudo que pertence a uma sala. Um `RoomContextStore` resolve a sala a
partir do parâmetro de rota e **descarta todo o estado ao mudar de `roomId`**. Nenhum cache é
compartilhado entre salas: XP, progresso, tentativas, ranking e conquistas são armazenados em
mapas indexados por `roomId` ou simplesmente recarregados.

As telas de tentativa ficam fora do prefixo de sala porque a API as endereça por
`attemptId`; a sala é obtida do próprio recurso.

## 3. Minhas salas

`GET /student/rooms` lista as salas ativas. Cada card mostra nome, professor, série, temas,
progresso e um selo quando arquivada.

Entrar por código: campo de 6 caracteres com máscara em maiúsculas e
`POST /student/rooms/join`. Tratamento por status:

| Status | Mensagem |
|---|---|
| `404` | "Código não encontrado. Confira com seu professor." |
| `403` | "Esta sala pertence a outra instituição." |
| `409` | "Você já participa desta sala." e navega para ela |
| `410` / sala arquivada | "Esta sala está arquivada e não aceita novos alunos." |

O aluno **não pode sair de uma sala**: nenhuma tela oferece essa ação. Reingresso após remoção
restaura o histórico automaticamente pela API, e a interface só reflete o resultado.

Vazio: estado com ação "Entrar em uma sala", que abre o formulário de código.

## 4. Dashboard da sala

`GET /student/rooms/{roomId}/dashboard` é a fonte única desta tela. Ela apresenta o que a API
retornou — progresso, nível, XP, estrelas, lições concluídas, conquistas recentes, dica do
dia e posição no ranking — **sem recalcular nada**.

Layout: faixa de saudação com nível e barra de XP; grade de cartões de métrica; "Continuar de
onde parou" apontando para a próxima lição disponível; dica financeira do dia renderizada por
`cc-markdown`; conquistas recentes; acesso ao ranking.

## 5. Trilha e lição

### 5.1 Trilha

`GET /student/rooms/{roomId}/lessons` devolve os itens ordenados com `assignmentId`, ordem,
disponibilidade, datas, melhor nota, estrelas, tentativas usadas e disponíveis e motivo de
bloqueio.

Cada item é um cartão com estado visual distinto e **rótulo textual**, nunca só cor:

| Estado | Sinal | Ação |
|---|---|---|
| Disponível | Botão primário | "Começar" |
| Em andamento | Selo e cronômetro | "Continuar" |
| Aprovada | Verde, estrelas e melhor nota | "Refazer" ou "Ver resultado" |
| Reprovada, com tentativas | Vermelho e melhor nota | "Tentar novamente" |
| Bloqueada | Cadeado e motivo | Nenhuma |

Motivos de bloqueio exibidos com texto próprio: pré-requisito não aprovado, ainda não
disponível (com a data de abertura), prazo encerrado (com a data), tentativas esgotadas e
conteúdo não publicado. O motivo vem da API; o frontend só traduz o código para português.

A trilha nunca decide sozinha que a próxima lição está liberada. Ela reflete a resposta.

### 5.2 Lição

`GET /student/rooms/{roomId}/lessons/{lessonId}` traz teoria, materiais e a situação do aluno.
A teoria é renderizada por `cc-markdown` com KaTeX. Materiais vinculados abrem pelos
componentes seguros da Parte 2.

`GET .../attempts` alimenta o histórico: cada tentativa com data, nota, estrelas e situação,
e link para o resultado. A melhor tentativa é destacada, porque é ela que define aprovação e
estrelas.

Botão de iniciar exibe as regras antes: tempo limite, número de questões, tentativas
restantes e nota mínima. Quando não há tempo limite ou não há limite de tentativas, o texto
diz "sem limite" — nunca um número inventado.

## 6. Tentativa — requisitos críticos

### 6.1 Sigilo do gabarito

Esta é a exigência mais forte da spec: *"nenhum gabarito aparece antes de a tentativa
terminar"*. Ela é garantida por tipagem, não por disciplina.

```ts
// Questão durante a tentativa — não existe campo de gabarito neste tipo
export interface AttemptQuestion {
  questionSnapshotId: string;
  type: QuestionType;
  prompt: string;
  order: number;
  options?: { id: string; text: string }[];   // sem 'correct'
  numeric?: { unit: NumericUnit; decimalPlaces: number };
}

// Questão no resultado — aí sim
export interface ResultAnswer {
  question: AttemptQuestion;
  studentAnswer: AnswerPayload | null;
  correctAnswer: AnswerPayload;
  correct: boolean;
  explanation: string;
}
```

`AttemptQuestion` e `ResultAnswer` vivem em arquivos separados. O componente da tentativa em
andamento importa apenas o primeiro. Nenhum tipo intermediário mistura os dois.

A resposta de `PUT .../answers/{questionSnapshotId}` traz `correct`, mas o registro dessa
resposta **não altera a interface da questão**: o valor é ignorado durante a tentativa e não
é armazenado no estado da tela. Só o resultado final exibe correção. Isso está explicitamente
coberto por teste.

### 6.2 Ciclo da tentativa

1. `POST /student/room-lessons/{assignmentId}/attempts` com `Idempotency-Key`. A chave é
   gerada uma vez ao abrir o diálogo de confirmação e reaproveitada em repetições da mesma
   intenção. A API inicia uma tentativa ou devolve a ativa.
2. Navega para `/aluno/tentativas/:attemptId`.
3. `GET /student/attempts/{attemptId}` hidrata a tela em qualquer entrada, inclusive recarga:
   `status`, `startedAt`, `expiresAt`, questões sorteadas e respostas já registradas.
4. Cada resposta vai por `PUT .../answers/{questionSnapshotId}` no momento em que o aluno
   confirma a questão.
5. `POST .../submit` finaliza.
6. `GET .../result` alimenta a tela de resultado.

### 6.3 Imutabilidade da resposta

A resposta é imutável dentro da tentativa. A interface reflete isso: ao confirmar uma
questão, os controles daquela questão ficam desabilitados e a navegação segue para a
seguinte. O diálogo de confirmação avisa antes: "Sua resposta não poderá ser alterada."

Questões respondidas aparecem marcadas no navegador de questões — como *respondida*, jamais
como certa ou errada.

### 6.4 Cronômetro

- Baseado exclusivamente em `expiresAt` e no `ServerClock` da Parte 1.
- Recalculado a cada segundo a partir do instante absoluto; nunca decrementado.
- Sobrevive a recarga, fechamento e reabertura da página, porque o estado autoritativo está
  no servidor e é rebuscado na hidratação.
- Avisos visuais aos 5 minutos e ao 1 minuto restante.
- Ao chegar a zero, a interface chama `submit` automaticamente e informa o aluno.
- **O servidor também expira e corrige a tentativa por conta própria.** Portanto, se o
  `submit` automático falhar, ou o aluno estiver offline, a tela não trava: ela consulta o
  resultado, e um `410` ou uma tentativa `EXPIRED` é tratada como encerramento normal, com a
  mensagem "Seu tempo terminou e a tentativa foi corrigida."
- Sem limite de tempo: nenhum cronômetro é exibido, e nada é inventado.

### 6.5 Interface da tentativa

Uma questão por vez. Topo fixo com cronômetro, progresso "questão X de Y" e navegador de
questões. Sem menu lateral e sem links que tirem o aluno da tentativa sem aviso — um guard de
saída (`CanDeactivate`) confirma o abandono explicando que o tempo continua correndo.

Controles por tipo:

| Tipo | Controle | Payload |
|---|---|---|
| `SINGLE_CHOICE` | Rádio | `{ selectedOptionIds: [id] }` |
| `MULTIPLE_CHOICE` | Caixas de seleção | `{ selectedOptionIds: [...] }` |
| `TRUE_FALSE` | Dois botões | `{ booleanValue: true }` |
| `NUMERIC` | Campo numérico com unidade e casas decimais | `{ numericValue: "100.50" }` |

`MULTIPLE_CHOICE` avisa que só a seleção exata pontua. `NUMERIC` respeita `decimalPlaces` e
`unit` na apresentação e envia **string decimal**, nunca `float`.

Offline durante a tentativa: banner de aviso, controles de resposta desabilitados, cronômetro
continua exibindo o cálculo do servidor e nenhuma resposta é enfileirada localmente para
envio posterior — o registro é sempre síncrono com a API.

### 6.6 Resultado

`GET /student/attempts/{attemptId}/result`. Exibe exatamente os valores retornados:
`correctAnswers`, `totalQuestions`, `scorePercent`, `passed`, `stars`, `xpEarnedThisAttempt`,
`roomXpTotal`, horários e a revisão questão a questão com resposta do aluno, resposta correta
e explicação.

Nada é recalculado. Em especial, o frontend **não** deriva estrelas de percentual nem XP de
acertos, ainda que a spec descreva as fórmulas — elas servem para explicar ao aluno, e a
explicação é textual:

- "Faixas de estrelas: abaixo de 50% nenhuma, 50–69% uma, 70–89% duas, 90–100% três."
- "Cada acerto vale 10 XP de potencial; só a melhoria do seu melhor resultado gera XP novo."

Aprovação é celebrada com animação contida. Reprovação mostra tentativas restantes e caminho
de volta à teoria.

## 7. Videoaulas e materiais

`GET /student/rooms/{roomId}/videos` e `.../materials` listam apenas conteúdo publicado,
agrupado por lição quando houver vínculo.

Ao abrir uma mídia, a interface chama
`POST /student/media/{mediaType}/{mediaId}/view`. O registro é idempotente por aluno e mídia;
a chamada não bloqueia a abertura e sua falha não impede o consumo do conteúdo.

Vídeos são links externos, abertos em `cc-video-embed`. Materiais podem ser link externo ou
arquivo; arquivos passam por `GET /files/{fileId}/download` através do `cc-secure-file-link`.
PDF abre em visualizador embutido; PPT/PPTX baixa.

Cada lista mostra o progresso de consumo — visto / total — a partir dos dados da API.

## 8. Ranking e conquistas

`GET /student/rooms/{roomId}/ranking`, paginado. A API já entrega os colegas com primeiro
nome e inicial do sobrenome; o frontend **não faz nenhuma anonimização própria** e nunca
exibe e-mail ou nome completo de colega. A linha do próprio aluno é destacada, e a tela
sempre mostra a posição dele mesmo quando fora da página visível.

`GET /student/rooms/{roomId}/achievements` lista as conquistas calculadas por sala: primeira
aprovação, primeira nota perfeita, 100 XP, 500 XP, 1.000 XP, cinco lições aprovadas e dez
lições aprovadas. Conquistas bloqueadas aparecem esmaecidas com o critério e o progresso
atual, quando a API o fornece.

Ranking e conquistas são sempre relativos à sala atual e recarregam ao trocar de `roomId`.

## 9. Entregáveis

```
features/student/
├── student.routes.ts
├── data/ student-room.service.ts, student-lesson.service.ts, attempt.service.ts,
│         student-media.service.ts, ranking.service.ts, achievement.service.ts,
│         room-context.store.ts, attempt.store.ts
├── models/ attempt-question.ts, attempt-result.ts, lesson-track.ts, dashboard.ts
├── components/ room-card/, lesson-track-card/, lock-reason/, attempt-timer-bar/,
│               question-navigator/, question-single-choice/, question-multiple-choice/,
│               question-true-false/, question-numeric/, result-summary/,
│               result-answer-review/, achievement-card/, ranking-row/, tip-of-day/
└── pages/ rooms/, room-dashboard/, lesson-track/, lesson-detail/,
          attempt/, attempt-result/, videos/, materials/, ranking/, achievements/
```

## 10. Testes desta parte

1. **Gabarito:** a resposta de `PUT .../answers/{id}` contendo `correct: true` não produz
   nenhuma marcação de acerto na tela nem entra no estado do componente.
2. **Gabarito:** o HTML renderizado durante a tentativa não contém explicação nem opção
   correta, para nenhum dos quatro tipos de questão.
3. **Cronômetro:** com `expiresAt` a 10 minutos e desvio de relógio de +3 minutos, o valor
   exibido é o corrigido.
4. **Cronômetro:** recriar o componente simulando recarga mostra o tempo restante correto.
5. **Cronômetro:** ao zerar, `submit` é chamado uma única vez.
6. **Expiração pelo servidor:** `submit` que responde `410` leva à tela de resultado com a
   mensagem de expiração, sem travar.
7. **Isolamento:** navegar da sala A para a sala B recarrega dashboard, ranking e conquistas,
   e nenhum valor da sala A permanece visível.
8. **Idempotência:** dois cliques em "Começar" enviam uma requisição com a mesma
   `Idempotency-Key`.
9. **Imutabilidade:** questão confirmada fica desabilitada e não pode ser reenviada.
10. **Ranking:** nenhum nome completo ou e-mail de colega é renderizado.
11. **Numérico:** o payload enviado é string e preserva as casas decimais.

## 11. Critérios de aceite

- Os cinco critérios da §11 da spec que tocam o aluno são verificáveis por teste automatizado
  desta parte.
- Nenhum cálculo de nota, XP, estrela, nível ou desbloqueio existe no código do aluno.
- Nenhuma tela do aluno oferece sair da sala.
- Todo estado de bloqueio da trilha tem texto explicativo próprio.