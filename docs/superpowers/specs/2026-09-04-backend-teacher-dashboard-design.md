# Dashboard do professor — desenho do backend

Status: aprovado para planejamento
Data: 2026-09-04

## 1. Objetivo e escopo

Implementar `GET /teacher/dashboard` como fonte dos cards-resumo da área do professor. A resposta agrega somente contagens de salas, alunos, lições e atribuições pertencentes ao professor autenticado. O incremento não inclui relatórios detalhados, alteração de salas, métricas de tentativas por aluno ou dados de outros professores.

## 2. Contrato HTTP

O endpoint recebe o professor autenticado e não possui parâmetros. Retorna `200 OK` com o seguinte formato:

```json
{
  "rooms": {
    "total": 4,
    "active": 3,
    "archived": 1
  },
  "students": {
    "total": 86,
    "activeMemberships": 80
  },
  "lessons": {
    "total": 12,
    "published": 9,
    "draft": 3
  },
  "assignments": {
    "total": 24,
    "published": 20
  }
}
```

Os campos são contagens inteiras não negativas. `students.total` conta alunos distintos com qualquer matrícula em salas do professor; `students.activeMemberships` conta matrículas com status `ACTIVE`. Uma pessoa matriculada em duas salas conta uma vez em `total` e duas vezes em `activeMemberships` quando ambas estão ativas.

`rooms.active` conta salas não arquivadas e `rooms.archived` conta salas arquivadas; `rooms.total` é a soma das duas. `lessons.total` conta lições pertencentes ao professor, incluindo todos os estados persistidos; `lessons.published` e `lessons.draft` contam os estados correspondentes. `assignments.total` conta atribuições de lições nas salas do professor, incluindo estados de conteúdo; `assignments.published` conta somente atribuições com status `PUBLISHED`.

## 3. Autorização e isolamento

O serviço valida que o usuário existe, possui papel `TEACHER` e está com conta `ACTIVE`. Papel incompatível retorna `403 TEACHER_REQUIRED`; conta inativa retorna `403 ACCOUNT_INACTIVE`. Usuário inexistente retorna `404 TEACHER_NOT_FOUND`, seguindo o padrão de serviços de professor.

Cada consulta recebe o `teacherId` autenticado ou deriva o escopo por `room.teacher.id`. Nenhuma contagem usa apenas a instituição, o que evita que um professor veja dados de outro professor da mesma instituição. Salas arquivadas permanecem nas contagens históricas, mas não recebem tratamento especial além da separação entre `active` e `archived`.

## 4. Composição e persistência

`TeacherDashboardService` será o único orquestrador do caso de uso e executará em transação somente leitura. O controller apenas recebe `CurrentUser` e devolve o DTO.

As contagens serão obtidas por métodos explícitos de repositório, sem carregar entidades completas e sem `Specification`:

- `RoomRepository`: total por professor e contagem por professor com `archivedAt is null`/`is not null`;
- `RoomMembershipRepository`: alunos distintos por professor e matrículas por professor com status;
- `LessonRepository`: total por professor e contagem por `ContentStatus`;
- `LessonAssignmentRepository`: total por professor e contagem por `ContentStatus`.

As consultas devem usar agregações SQL/JPQL e retornar `long`. Se o professor não possuir dados, todos os contadores retornam zero e o envelope continua válido.

## 5. DTO público

Criar `TeacherDashboardResponse` com records aninhados para manter o contrato explícito:

```java
public record TeacherDashboardResponse(
        RoomCounts rooms,
        StudentCounts students,
        LessonCounts lessons,
        AssignmentCounts assignments
) {
    public record RoomCounts(long total, long active, long archived) {}
    public record StudentCounts(long total, long activeMemberships) {}
    public record LessonCounts(long total, long published, long draft) {}
    public record AssignmentCounts(long total, long published) {}
}
```

Nenhuma entidade JPA, e-mail, nome de aluno ou campo administrativo será exposto.

## 6. Erros e consistência

Erros seguem o handler global e o formato RFC 9457 já existente. A resposta é uma fotografia lógica montada em uma única transação `readOnly`; ela não promete isolamento serializável entre atualizações concorrentes, mas todas as fontes são consultadas com o mesmo escopo do professor.

## 7. Testes e critérios de aceite

- teste unitário do serviço cobre professor sem dados, contagens separadas por estado e soma de salas;
- teste unitário verifica `TEACHER_REQUIRED`, `ACCOUNT_INACTIVE` e `TEACHER_NOT_FOUND`;
- teste do controller valida `GET /teacher/dashboard`, passagem do `userId` e serialização do envelope;
- teste de integração cria dois professores na mesma instituição e comprova que cada um recebe somente suas próprias salas, alunos, lições e atribuições;
- teste de integração cobre salas arquivadas e uma mesma pessoa matriculada em duas salas;
- testes existentes de salas, lições, atribuições e relatórios continuam verdes;
- nenhuma migration é necessária.

## 8. Fora de escopo

Este incremento não altera `GET /teacher/reports/*`, não cria gráficos, não calcula taxa de aprovação, não lista atividades recentes e não implementa os endpoints de download ou visualizações de mídia.
