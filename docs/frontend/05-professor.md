# Parte 5 — Frontend do professor

Depende de: Partes 1 e 2.
Referência normativa: §7 de [`../frontend-integration-spec.md`](../frontend-integration-spec.md).

## 1. Objetivo

Entregar a área de autoria e acompanhamento: salas, alunos, acervo de lições e questões,
montagem da trilha, mídias e relatórios. É a maior parte em superfície, e a que mais usa
`version`, confirmação destrutiva e paginação de servidor.

## 2. Rotas

| Rota | Tela |
|---|---|
| `/professor` | Dashboard |
| `/professor/salas` | Lista de salas |
| `/professor/salas/:roomId` | Detalhe da sala (abas) |
| `/professor/licoes` | Acervo de lições |
| `/professor/licoes/:lessonId` | Editor de lição |
| `/professor/licoes/:lessonId/questoes` | Questões da lição |
| `/professor/videos` | Videoaulas |
| `/professor/materiais` | Materiais |
| `/professor/relatorios` | Relatórios |

O detalhe da sala usa abas com a aba no fragmento da URL, para que recarregar ou compartilhar
o link preserve o contexto: Visão geral, Alunos, Trilha, Mídias.

## 3. Dashboard

`GET /teacher/dashboard`. Cartões-resumo e atalhos para criar sala, criar lição e abrir
relatórios. Vazio de professor novo tem ação contextual "Criar minha primeira sala".

## 4. Salas

`GET/POST /teacher/rooms`, `GET/PATCH /teacher/rooms/{roomId}`.

Criação e edição em diálogo: nome, descrição, série (`Grade`), temas e nota mínima
(`passingScorePercent`, padrão 50). A instituição **não é campo do formulário** — é derivada
do professor autenticado pela API.

Temas são uma lista ordenada, editada com `p-chips` ou equivalente.

Ações da sala, cada uma com confirmação destrutiva quando aplicável:

| Ação | Endpoint | Confirmação |
|---|---|---|
| Arquivar | `POST .../archive` | "A sala ficará somente leitura. Alunos não poderão entrar nem fazer novas tentativas." |
| Excluir | `DELETE .../{roomId}` | Só oferecida quando a sala nunca foi usada; `409` explica o motivo |
| Duplicar | `POST .../duplicate` | Aviso de que alunos e progresso **não** são copiados |
| Regenerar código | `POST .../regenerate-code` | "O código atual deixará de funcionar. Alunos já matriculados continuam na sala." |

Sala arquivada é somente leitura em toda a interface: todos os controles de mutação ficam
desabilitados, com um selo "Arquivada" e explicação. Isso vale também para trilha, mídias e
alunos daquela sala.

O código de ingresso aparece em destaque com botão de copiar, usando `navigator.clipboard`
com fallback de seleção de texto.

Edição envia `version`. Em `409 VERSION_CONFLICT`, o diálogo mostra "Esta sala foi alterada em
outro lugar" e oferece **Recarregar**; salvar por cima não é oferecido em nenhuma
circunstância.

## 5. Alunos da sala

`GET /teacher/rooms/{roomId}/students` paginado, com busca. Colunas: nome, matrícula, e-mail,
XP na sala, lições concluídas, estrelas, última atividade e situação da matrícula.

Remover aluno (`DELETE .../students/{studentId}`) exige confirmação com texto explícito de que
**o histórico é preservado** e que o aluno pode reingressar com o código, recuperando tudo.

Conceder tentativa extra: `POST /teacher/room-lessons/{assignmentId}/students/{studentId}/extra-attempts`
com `{ "quantity": 1 }`. Acessível a partir do aluno ou da lição, com seletor da outra ponta.
Feedback informa a nova quantidade disponível retornada pela API.

## 6. Lições e questões

### 6.1 Acervo

`GET /teacher/lessons` lista o acervo próprio, filtrável por `ContentStatus` e busca. O acervo
é reutilizável entre salas: uma lição pertence ao professor, não a uma sala.

Ciclo de vida com estados visualmente distintos: `DRAFT`, `PUBLISHED`, `ARCHIVED`.

| Ação | Endpoint | Nota |
|---|---|---|
| Criar rascunho | `POST /teacher/lessons` | Abre o editor |
| Editar | `PATCH .../{lessonId}` | Envia `version` |
| Publicar | `POST .../publish` | `422` quando faltam questões; o erro é exibido com o motivo |
| Arquivar | `POST .../archive` | Confirmação |
| Duplicar | `POST .../duplicate` | Copia lição e questões |

### 6.2 Editor de lição

Campos: `title`, `summary` e `theoryMarkdown`.

O editor de teoria é dividido: fonte Markdown à esquerda, pré-visualização ao vivo à direita
pelo mesmo `cc-markdown` que o aluno usa — a pré-visualização passa pela mesma sanitização,
para que o professor veja exatamente o que o aluno verá, inclusive KaTeX.

Barra de ferramentas com atalhos de título, negrito, lista, tabela, código, fórmula e
imagem. "Imagem" abre o upload `POST /teacher/lessons/{lessonId}/images` (PNG/JPEG/WebP até
5 MB) e insere a referência retornada no texto.

Salvamento é explícito, com aviso de alterações não salvas ao sair. `version` sempre enviado.

### 6.3 Questões

`GET/POST /teacher/lessons/{lessonId}/questions`, `PATCH /teacher/questions/{questionId}`,
`DELETE`, `POST .../duplicate` e `PUT .../questions/order`.

Campos comuns: `prompt`, `type`, `explanation`, `order`. O editor muda conforme `type` e
valida **antes de enviar**, além do servidor:

| Tipo | Regra local |
|---|---|
| `SINGLE_CHOICE` | Duas ou mais opções e exatamente uma marcada como correta |
| `MULTIPLE_CHOICE` | Duas ou mais corretas; aviso de que só a seleção exata pontua |
| `TRUE_FALSE` | `correctBoolean` obrigatório |
| `NUMERIC` | `correctNumericValue` como string, `absoluteTolerance`, `unit` e `decimalPlaces` |

`prompt` e `explanation` aceitam Markdown com KaTeX e têm pré-visualização.

Reordenação por arrastar com `PUT .../questions/order`; a ordem otimista é revertida se a
requisição falhar. Duplicar questão pede a lição de destino.

Exclusão avisa que questões já usadas em tentativas podem ser arquivadas logicamente pela
API em vez de removidas, e a interface reflete o que a resposta indicar.

## 7. Trilha da sala — atribuições de lição

`GET/POST /teacher/rooms/{roomId}/lesson-assignments`, `PATCH`, `DELETE` e
`PUT .../lesson-assignments/order`.

Esta é a tela que liga o acervo à sala. Ela é uma lista ordenável de atribuições; adicionar
abre um seletor do acervo publicado do professor.

Formulário da atribuição:

| Campo | Regra de interface |
|---|---|
| `position` | Definido pela ordenação |
| `status` | `DRAFT` / `PUBLISHED` / `ARCHIVED` |
| `availableFrom`, `dueAt` | `p-datepicker` com hora, exibido em `America/Sao_Paulo` e **enviado em UTC ISO 8601**; `dueAt` posterior a `availableFrom` |
| `timeLimitMinutes` | Numérico ou alternador "sem limite" que envia `null`; padrão 30 na criação |
| `maxAttempts` | Numérico ou "sem limite" que envia `null`; padrão 3 na criação |
| `questionCount` | Numérico ou vazio, que envia `null` e significa todas as questões ativas |
| `shuffleQuestions`, `shuffleOptions` | Alternadores |

A conversão de fuso é responsabilidade explícita desta tela: o professor pensa em horário de
Brasília, a API fala UTC. Todo campo de data e hora exibe o fuso ao lado do valor.

Publicar uma atribuição sem questões suficientes retorna `422`; a mensagem do servidor é
exibida junto ao campo `questionCount`, e o formulário é preservado.

Remover atribuição só é oferecido quando ela é futura; caso contrário a API responde `409` e a
interface explica que a trilha já em uso não pode ser desmontada, sugerindo arquivar.

Cada linha mostra um resumo do que o aluno verá: janela de disponibilidade, tempo, tentativas
e número de questões.

## 8. Vídeos e materiais

Acervo próprio, independente de sala, no mesmo formato das lições.

- `GET/POST /teacher/videos`, `GET/PATCH/DELETE /teacher/videos/{videoId}`: título, descrição,
  categoria e URL externa. A URL é validada e pré-visualizada.
- `GET/POST /teacher/materials`, `GET/PATCH/DELETE /teacher/materials/{materialId}`:
  `MaterialKind` decide entre `EXTERNAL_LINK` (URL) e `FILE`.
- `POST /teacher/materials/files`: upload multipart de PDF/PPT/PPTX até 10 MB, com progresso
  real, cancelamento e tratamento específico de `413` e `415`.

Publicação em sala: `GET/POST /teacher/rooms/{roomId}/media-assignments`, com `PATCH` e
`DELETE`. Uma mídia pode ser vinculada a uma lição da sala ou ficar solta na sala. A tela de
mídias da sala mostra as duas colunas — vinculadas e gerais.

`GET /teacher/media/{mediaType}/{mediaId}/views` abre a lista de visualizações por aluno com
`firstViewedAt` e `lastViewedAt`, paginada, mais o total de alunos que abriram.

## 9. Relatórios

`GET /teacher/reports/overview`, `/students`, `/students/{studentId}/attempts`, `/ranking` e
`/export.csv`.

Filtros no topo, aplicados a todas as abas: sala (obrigatória quando a API exigir), lição
opcional, período com `from`/`to`, e o atalho `period=ALL`. **O padrão é os últimos 30 dias** e
isso é exibido, não implícito.

| Aba | Conteúdo |
|---|---|
| Visão geral | Cartões de métrica e séries em `p-chart`: evolução de tentativas, distribuição de notas, conclusão por lição |
| Alunos | Tabela paginada por aluno, com detalhamento das tentativas ao expandir |
| Ranking | Ranking completo — o professor vê **nomes completos** das próprias salas |

Exportações:

- CSV: baixado de `GET /teacher/reports/export.csv` com os mesmos filtros. O arquivo vem do
  backend; o frontend não monta CSV.
- PDF/impressão: gerada no cliente **a partir dos dados já retornados**, com folha de estilo
  de impressão dedicada. Nenhum número é recalculado para o relatório impresso.

Gráficos seguem as regras de acessibilidade da Parte 2: legenda, rótulos e tabela equivalente
disponível.

## 10. Entregáveis

```
features/teacher/
├── teacher.routes.ts
├── data/ teacher-dashboard.service.ts, teacher-room.service.ts,
│         teacher-student.service.ts, lesson.service.ts, question.service.ts,
│         lesson-assignment.service.ts, video.service.ts, material.service.ts,
│         media-assignment.service.ts, report.service.ts
├── models/ (dtos de lição, questão, atribuição, mídia, relatório)
├── components/ room-form-dialog/, join-code-panel/, archived-banner/,
│               lesson-status-tag/, markdown-editor/, question-editor/,
│               question-type-fields/, assignment-form/, assignment-row/,
│               media-form-dialog/, media-views-dialog/, report-filters/,
│               report-charts/, extra-attempts-dialog/
└── pages/ dashboard/, rooms/, room-detail/, lessons/, lesson-editor/,
          lesson-questions/, videos/, materials/, reports/
```

## 11. Testes desta parte

1. `PATCH` de sala envia `version`; `409` mostra o diálogo de conflito e **não** oferece
   sobrescrever.
2. Sala arquivada desabilita todo controle de mutação nas quatro abas.
3. Datas escolhidas em Brasília são enviadas em UTC correto, inclusive na virada do dia.
4. "Sem limite" envia `null` em `timeLimitMinutes`, `maxAttempts` e `questionCount`.
5. Editor de `SINGLE_CHOICE` bloqueia salvar com zero ou duas corretas.
6. `NUMERIC` envia `correctNumericValue` como string.
7. Upload de 11 MB é barrado antes da requisição; `413` do servidor mantém o arquivo
   selecionado.
8. Reordenação que falha reverte a ordem otimista.
9. Filtro padrão dos relatórios cobre 30 dias e `period=ALL` remove `from`/`to`.
10. Pré-visualização do Markdown sanitiza igual à do aluno.

## 12. Critérios de aceite

- Nenhuma tela de professor permite escolher instituição.
- Toda mutação destrutiva passa por `confirm.destructive`.
- Todo recurso editável envia `version` e trata conflito sem sobrescrita silenciosa.
- Todo campo de data e hora exibe o fuso de apresentação.
- Relatório impresso usa apenas números vindos da API.