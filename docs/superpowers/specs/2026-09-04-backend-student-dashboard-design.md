# Dashboard do aluno — desenho do backend

Status: aprovado para planejamento
Data: 2026-09-04

## 1. Objetivo e escopo

Implementar `GET /student/rooms/{roomId}/dashboard` como fonte única da tela inicial de uma sala. A resposta agrega dados já existentes de sala, progresso, trilha, gamificação, ranking e dicas financeiras, sem criar nova persistência.

Este incremento não altera regras de tentativas, pontuação, desbloqueio de lições ou conquistas. Também não inclui o dashboard do professor, download de arquivos ou consulta administrativa de visualizações de mídia.

## 2. Contrato HTTP

O endpoint recebe o aluno autenticado e o `roomId` na rota. Retorna `200 OK` com:

```json
{
  "room": {
    "id": "uuid",
    "name": "2º ano A",
    "description": "Matemática financeira aplicada",
    "grade": "HIGH_SCHOOL_2",
    "contentTopics": ["Porcentagem"],
    "teacher": { "id": "uuid", "fullName": "Ana Souza" },
    "institution": {
      "id": "uuid",
      "name": "IFSC",
      "cnpj": "00000000000000",
      "contactEmail": "contato@example.com",
      "contactPhone": "+5548999999999",
      "active": true
    },
    "membershipStatus": "ACTIVE",
    "archived": false,
    "progressPercent": 50
  },
  "progress": {
    "totalXp": 150,
    "level": 2,
    "levelProgressPercent": 50,
    "totalStars": 5,
    "completedLessons": 3,
    "passedLessons": 2,
    "totalLessons": 4
  },
  "nextLesson": {
    "assignmentId": "uuid",
    "lessonId": "uuid",
    "title": "Juros compostos",
    "summary": "Conceitos fundamentais",
    "order": 3,
    "availability": "AVAILABLE",
    "lockReason": null,
    "availableFrom": null,
    "dueAt": null,
    "rules": {
      "timeLimitMinutes": 30,
      "maxAttempts": 3,
      "attemptsUsed": 0,
      "attemptsRemaining": 3,
      "questionCount": 10,
      "passingScorePercent": 50
    },
    "bestScorePercent": null,
    "stars": null,
    "activeAttemptId": null,
    "activeAttemptExpiresAt": null,
    "bestAttemptId": null
  },
  "recentAchievements": [],
  "financialTip": {
    "id": "uuid",
    "title": "Monte uma reserva",
    "content": "**Comece pequeno.**",
    "sourceUrl": "https://example.org/reserva",
    "publicationDate": "2026-09-04"
  },
  "ranking": {
    "position": 3,
    "studentId": "uuid",
    "displayName": "Luiz S.",
    "totalXp": 150,
    "totalStars": 5,
    "level": 2,
    "currentStudent": true
  }
}
```

`nextLesson` e `financialTip` são anuláveis. `recentAchievements` nunca é nulo.

O objeto `room` mantém o contrato de `StudentRoomResponse`, mas `progressPercent` passa a refletir a sala consultada no dashboard. O objeto `nextLesson` reutiliza o contrato completo de `StudentLessonPathResponse`.

## 3. Autorização e visibilidade

O serviço valida, antes da agregação:

- o usuário existe e possui papel `STUDENT`;
- a conta está `ACTIVE`;
- existe matrícula `ACTIVE` para o par aluno/sala;
- aluno e sala pertencem à mesma instituição.

Papel incompatível retorna `403 STUDENT_REQUIRED`; conta inativa retorna `403 ACCOUNT_INACTIVE`. Sala sem matrícula ativa, matrícula removida e divergência de instituição retornam `404 ROOM_NOT_FOUND`, evitando enumeração entre salas ou instituições.

Uma sala arquivada continua legível para um aluno com matrícula ativa, preservando o acesso ao histórico. As restrições de ingresso e novas tentativas continuam nos casos de uso existentes.

## 4. Composição do dashboard

`StudentRoomDashboardService` é o único orquestrador do caso de uso e executa em transação somente leitura. O controller apenas recebe `CurrentUser`, converte o `roomId` e devolve o DTO.

Após autorizar o acesso, o serviço:

1. mapeia a sala e a matrícula;
2. carrega `RoomStudentProgress`, usando zero para XP, estrelas e contagens e nível 1 quando a projeção ainda não existe;
3. obtém a trilha pelo `StudentLessonService`, preservando suas regras de disponibilidade;
4. obtém conquistas e a posição do próprio aluno pelo `StudentGamificationService`;
5. seleciona a dica pelo `StudentFinancialTipService`;
6. monta `StudentRoomDashboardResponse`.

O total de lições considera atribuições publicadas e acessíveis devolvidas pela trilha. `progressPercent` é `passedLessons / totalLessons * 100`, arredondado para baixo; quando não há lições, é zero. `levelProgressPercent` é `totalXp % 100`, coerente com a regra de 100 XP por nível.

## 5. Próxima lição

A seleção é determinística sobre a trilha ordenada:

1. primeira lição `IN_PROGRESS`;
2. primeira lição `AVAILABLE`;
3. primeira lição `FAILED` que ainda tenha tentativas disponíveis ou limite ilimitado;
4. `null` quando nenhuma pode ser continuada.

O dashboard não recalcula disponibilidade, pré-requisitos, prazos ou tentativas. Esses valores permanecem responsabilidade de `StudentLessonService`.

## 6. Conquistas recentes e ranking

O dashboard retorna no máximo três conquistas desbloqueadas. Elas são ordenadas por `unlockedAt` decrescente e depois por código, garantindo estabilidade quando os horários forem iguais. Conquistas ainda bloqueadas não aparecem nesse recorte; o catálogo completo continua em `/student/rooms/{roomId}/achievements`.

`ranking` corresponde à entrada `self` já produzida pelo serviço de gamificação. O nome permanece anonimizado de acordo com a regra atual do ranking do aluno.

## 7. Dica financeira do dia

`StudentFinancialTipService` usa o `Clock` da aplicação e `ZoneId.of("America/Sao_Paulo")` para obter a data local.

- Havendo uma ou mais dicas ativas, não arquivadas e agendadas exatamente para a data local, escolhe a de menor UUID para manter o resultado determinístico.
- Sem dica agendada, carrega as dicas ativas e não arquivadas ordenadas por UUID e seleciona `floorMod(data.toEpochDay(), quantidade)`. Assim, todos recebem a mesma dica naquele dia e o fallback pode variar entre dias.
- Sem dicas ativas, retorna `null`.

A resposta estudantil expõe `id`, `title`, Markdown bruto em `content`, `sourceUrl` e `publicationDate`. Não expõe `active`, `archivedAt`, timestamps administrativos ou `version`. A sanitização do Markdown permanece responsabilidade do `cc-markdown` no frontend.

## 8. Componentes e alterações

- `dto/studentdashboard/StudentRoomDashboardResponse`: envelope agregado.
- `dto/studentdashboard/StudentDashboardProgressResponse`: métricas da sala.
- `dto/studentdashboard/StudentFinancialTipResponse`: contrato público mínimo da dica.
- `service/StudentRoomDashboardService`: autorização, composição e seleção de próxima lição.
- `service/StudentFinancialTipService`: data local e escolha determinística da dica.
- `repository/FinancialTipRepository`: consultas de dicas ativas por data e ordenação estável.
- `controller/StudentRoomController`: novo `GET /{roomId}/dashboard`.

Nenhuma migration é necessária.

## 9. Erros e consistência

Erros seguem RFC 9457 pelo handler global existente. Consultas inexistentes ou invisíveis retornam `404 ROOM_NOT_FOUND`; o endpoint não devolve dados parciais em caso de erro.

A resposta é uma fotografia lógica montada em uma única transação somente leitura. Ela não promete isolamento serializável entre atualizações concorrentes, mas cada fonte usa os dados confirmados disponíveis no momento da consulta.

## 10. Testes e critérios de aceite

- teste unitário do seletor de dica cobre data de São Paulo, prioridade da dica agendada, fallback determinístico e ausência de dicas;
- teste unitário do dashboard cobre valores padrão sem progresso, cálculo dos percentuais, próxima lição, limite/ordenação das conquistas e montagem do ranking;
- teste do controller valida rota e passagem do usuário autenticado;
- teste de integração valida aluno autorizado, matrícula removida, acesso cruzado entre salas/instituições e sala arquivada legível;
- testes existentes de sala, trilha, gamificação e dicas continuam verdes;
- nenhum DTO expõe entidade JPA ou campos administrativos da dica.
