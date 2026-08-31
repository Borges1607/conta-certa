# Backend Teacher Reports Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implementar quatro endpoints JSON para relatórios de professor, com isolamento por sala, filtros temporais/aula, agregações PostgreSQL e histórico detalhado de tentativas.

**Architecture:** `TeacherReportController` delega a `TeacherReportService`; `TeacherReportFilterFactory` valida usuário, escopo e período e cria um `ReportFilter` imutável. `TeacherReportQueryRepository` usa `JdbcClient` e SQL parametrizado para devolver DTOs/projeções já agregados; tentativas e respostas são carregadas em duas consultas para evitar N+1.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring MVC/Security, Spring JDBC `JdbcClient`, Spring Data JPA, PostgreSQL 18, Flyway, JUnit 5, Mockito e Testcontainers.

**Spec:** `docs/superpowers/specs/2026-08-29-backend-teacher-reports-design.md`

## Global Constraints

- Todos os comandos devem ser executados com prefixo `rtk`; no backend, usar sempre `./mvnw`.
- Trabalhar em worktree isolado criado com `superpowers:using-git-worktrees`; não alterar nem incluir os arquivos sujos do checkout atual.
- Seguir TDD: teste vermelho, implementação mínima, teste verde e commit atômico por tarefa.
- Usar Java 21, imports explícitos, sem imports curinga e sem tipos totalmente qualificados inline.
- Controllers não acessam repositórios; entidades JPA não são contratos da API.
- Usar Lombok conforme `backend/AGENTS.md`, sem `@Data`, `@Setter` ou `@AllArgsConstructor` em entidades.
- `roomId` é obrigatório; `lessonId` é opcional e deve estar atribuído à sala do professor.
- Períodos usam `[from,to)` em UTC; padrão de 30 dias via `Clock`; `period=ALL` não aceita datas.
- Somente tentativas `SUBMITTED` e `EXPIRED` entram nos relatórios.
- Ordenação dinâmica usa whitelist; nunca interpolar valores arbitrários recebidos pelo endpoint.
- Cada tarefa termina em commit próprio, contendo somente os arquivos listados nela.

## File Structure

- `dto/report/ReportPeriod.java`: enum público aceito pela API (`ALL`).
- `dto/report/*Response.java`: records públicos dos quatro contratos e seus itens internos.
- `model/ReportFilter.java`: filtro interno imutável com sala, aula e limites opcionais.
- `model/ReportStudentSort.java`: whitelist que converte o campo público em fragmento SQL constante.
- `repository/TeacherReportQueryRepository.java`: contrato das consultas analíticas.
- `repository/JdbcTeacherReportQueryRepository.java`: SQL, mapeamento de linhas, paginação e montagem em lote.
- `service/TeacherReportFilterFactory.java`: valida período, professor, sala, aula e aluno e cria o filtro interno.
- `service/TeacherReportService.java`: orquestra as consultas e converte páginas para os contratos públicos.
- `controller/TeacherReportController.java`: parâmetros HTTP e respostas.
- `db/migration/V10__add_teacher_report_indexes.sql`: índices das consultas; V9 pertence à gamificação que antecede esta branch.
- `service/TeacherReportFilterFactoryTest.java`: testes unitários de filtros e escopo.
- `service/TeacherReportServiceTest.java`: testes unitários de orquestração, paginação e ordenação.
- `repository/TeacherReportQueryRepositoryTest.java`: integração PostgreSQL das métricas.
- `controller/TeacherReportControllerTest.java`: autenticação e contratos HTTP.

---

### Task 1: Filtro temporal e validação de escopo

**Files:**
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/report/ReportPeriod.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/model/ReportFilter.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/service/TeacherReportFilterFactory.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/repository/RoomMembershipRepository.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/service/TeacherReportFilterFactoryTest.java`

**Interfaces:**
- Consumes: `UserRepository.findById`, `RoomRepository.findByIdAndTeacherId`, `LessonAssignmentRepository.existsByRoomIdAndLessonId` e membership ativa.
- Produces: `ReportFilter(UUID roomId, UUID lessonId, Instant from, Instant to)`; limites nulos representam `ALL`; `TeacherReportFilterFactory.create(UUID teacherId, UUID roomId, UUID lessonId, ReportPeriod period, Instant from, Instant to)` e `requireActiveStudent(UUID roomId, UUID studentId)`.

- [ ] **Step 1: Escrever testes vermelhos do período**

Criar mocks dos repositórios, `Clock.fixed(Instant.parse("2026-08-29T12:00:00Z"), ZoneOffset.UTC)` e testar o resultado de `create`:

```java
@Test
void deveUsarUltimosTrintaDiasQuandoPeriodoNaoForInformado() {
	ReportFilter filter = factory.create(teacherId, roomId, null, null, null, null);
	assertThat(filter).isEqualTo(new ReportFilter(
		roomId, null,
		Instant.parse("2026-07-30T12:00:00Z"),
		Instant.parse("2026-08-29T12:00:00Z")
	));
}

@Test
void deveAceitarAllSemLimites() {
	ReportFilter filter = factory.create(teacherId, roomId, null, ReportPeriod.ALL, null, null);
	assertThat(filter).isEqualTo(new ReportFilter(roomId, null, null, null));
}
```

Adicionar casos para apenas uma data, `from >= to` e `ALL` com datas, esperando `ApiException` com status 422 e código `VALIDATION_ERROR`.

- [ ] **Step 2: Executar o teste e confirmar falha**

Run: `cd backend && rtk ./mvnw -Dtest=TeacherReportFilterFactoryTest test`

Expected: FAIL de compilação porque os tipos de relatório ainda não existem.

- [ ] **Step 3: Implementar filtro e validações mínimas**

```java
public record ReportFilter(UUID roomId, UUID lessonId, Instant from, Instant to) {
	public boolean allTime() { return from == null; }
}
```

Na factory, implementar o período com `clock.instant().minus(30, ChronoUnit.DAYS)`, validar as combinações e lançar:

```java
throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_ERROR", detail);
```

Adicionar `existsByRoomIdAndStudentIdAndStatus(...)` ao repositório de memberships. Centralizar helpers `requireActiveTeacher`, `requireOwnedRoom`, `requireAssignedLesson` e `requireActiveStudent`; recursos fora do escopo retornam os códigos 404 da spec.

- [ ] **Step 4: Executar testes unitários**

Run: `cd backend && rtk ./mvnw -Dtest=TeacherReportFilterFactoryTest test`

Expected: PASS para período, papel/status e propriedade de sala/aula/aluno.

- [ ] **Step 5: Commit atômico**

```bash
rtk git add backend/src/main/java/com/ifsc/contacerta/dto/report/ReportPeriod.java backend/src/main/java/com/ifsc/contacerta/model/ReportFilter.java backend/src/main/java/com/ifsc/contacerta/service/TeacherReportFilterFactory.java backend/src/main/java/com/ifsc/contacerta/repository/RoomMembershipRepository.java backend/src/test/java/com/ifsc/contacerta/service/TeacherReportFilterFactoryTest.java
rtk git commit -m "feat: validate teacher report filters"
```

### Task 2: Visão geral agregada

**Files:**
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/report/TeacherReportOverviewResponse.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/report/ReportAttemptSeriesItemResponse.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/report/ReportScoreDistributionResponse.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/report/ReportLessonPerformanceResponse.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/repository/TeacherReportQueryRepository.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/repository/JdbcTeacherReportQueryRepository.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/service/TeacherReportService.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/repository/TeacherReportQueryRepositoryTest.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/service/TeacherReportServiceTest.java`

**Interfaces:**
- Consumes: `TeacherReportFilterFactory` e `ReportFilter` da Task 1.
- Produces: `TeacherReportOverviewResponse overview(ReportFilter filter)` e `TeacherReportService.overview(...)`.

- [ ] **Step 1: Escrever fixture PostgreSQL e teste vermelho da overview**

Criar professor, sala, dois alunos ativos, duas atividades publicadas, uma draft e tentativas em dentro/fora do período. Verificar explicitamente:

```java
TeacherReportOverviewResponse result = repository.overview(filter);
assertThat(result.activeStudentCount()).isEqualTo(2);
assertThat(result.participatingStudentCount()).isEqualTo(1);
assertThat(result.averageRoomXp()).isEqualByComparingTo("75.00");
assertThat(result.completionRatePercent()).isEqualByComparingTo("25.00");
assertThat(result.averageBestStars()).isEqualByComparingTo("2.00");
assertThat(result.scoreDistribution()).isEqualTo(new ReportScoreDistributionResponse(1, 1, 1, 1));
```

Também validar `lessonId`, buckets diários UTC, média/taxa por aula e zeros quando não houver dados.

- [ ] **Step 2: Executar o teste e confirmar falha**

Run: `cd backend && rtk ./mvnw -Dtest=TeacherReportQueryRepositoryTest test`

Expected: FAIL de compilação pelos DTOs/repositório ausentes.

- [ ] **Step 3: Implementar DTOs e consultas da overview**

Usar `JdbcClient` com parâmetros `roomId`, `lessonId`, `from` e `to`. Padronizar o predicado temporal:

```sql
and (:from_at is null or a.submitted_at >= :from_at)
and (:to_at is null or a.submitted_at < :to_at)
and a.status in ('SUBMITTED', 'EXPIRED')
```

Calcular conclusão somente sobre memberships `ACTIVE` × assignments `PUBLISHED`; melhor estrela com `max(a.stars)` por aluno/atividade; série com `(a.submitted_at at time zone 'UTC')::date`; distribuição com `count(*) filter (where ...)`; desempenho agrupado por lesson. Executar consultas focadas e montar o record final sem carregar entidades.

- [ ] **Step 4: Executar testes da overview e do serviço**

Run: `cd backend && rtk ./mvnw -Dtest=TeacherReportQueryRepositoryTest,TeacherReportServiceTest,TeacherReportFilterFactoryTest test`

Expected: PASS, incluindo limites `[from,to)` e tentativas expiradas finalizadas.

- [ ] **Step 5: Commit atômico**

```bash
rtk git add backend/src/main/java/com/ifsc/contacerta/dto/report backend/src/main/java/com/ifsc/contacerta/repository/TeacherReportQueryRepository.java backend/src/main/java/com/ifsc/contacerta/repository/JdbcTeacherReportQueryRepository.java backend/src/main/java/com/ifsc/contacerta/service/TeacherReportService.java backend/src/test/java/com/ifsc/contacerta/repository/TeacherReportQueryRepositoryTest.java backend/src/test/java/com/ifsc/contacerta/service/TeacherReportServiceTest.java
rtk git commit -m "feat: query teacher report overview"
```

### Task 3: Progresso paginado dos alunos

**Files:**
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/report/TeacherReportStudentResponse.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/model/ReportStudentSort.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/repository/TeacherReportQueryRepository.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/repository/JdbcTeacherReportQueryRepository.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/service/TeacherReportService.java`
- Modify: `backend/src/test/java/com/ifsc/contacerta/repository/TeacherReportQueryRepositoryTest.java`
- Modify: `backend/src/test/java/com/ifsc/contacerta/service/TeacherReportServiceTest.java`

**Interfaces:**
- Consumes: `ReportFilter`, `Pageable` e whitelist `ReportStudentSort`.
- Produces: `Page<TeacherReportStudentResponse> students(ReportFilter, Pageable)`.

- [ ] **Step 1: Escrever testes vermelhos de conteúdo, paginação e ordenação**

Testar aluno ativo sem tentativas com zeros, estado atual vindo de `room_student_progress`, métricas apenas no filtro, `size`/`totalElements`, cada campo permitido e desempate por UUID. No serviço, testar rejeição de campo e direção desconhecidos com `400 BAD_REQUEST`.

- [ ] **Step 2: Executar testes focados e confirmar falha**

Run: `cd backend && rtk ./mvnw -Dtest=TeacherReportQueryRepositoryTest,TeacherReportServiceTest test`

Expected: FAIL porque a operação `students` ainda não existe.

- [ ] **Step 3: Implementar query paginada e whitelist**

Criar enum com fragmentos constantes, por exemplo:

```java
TOTAL_XP("totalXp", "coalesce(rsp.total_xp, 0)"),
AVERAGE_SCORE_PERCENT("averageScorePercent", "coalesce(metrics.average_score, 0)");
```

O SQL parte de `room_memberships` ativos, faz `left join` no progresso atual e em subconsulta agregada das tentativas filtradas. Executar uma query de conteúdo com `limit/offset` e uma de `count(*)`. Converter para `PageImpl`; adicionar UUID como último critério.

- [ ] **Step 4: Executar os testes focados**

Run: `cd backend && rtk ./mvnw -Dtest=TeacherReportQueryRepositoryTest,TeacherReportServiceTest test`

Expected: PASS para zeros, filtro, paginação e todos os sorts permitidos.

- [ ] **Step 5: Commit atômico**

```bash
rtk git add backend/src/main/java/com/ifsc/contacerta/dto/report/TeacherReportStudentResponse.java backend/src/main/java/com/ifsc/contacerta/model/ReportStudentSort.java backend/src/main/java/com/ifsc/contacerta/repository/TeacherReportQueryRepository.java backend/src/main/java/com/ifsc/contacerta/repository/JdbcTeacherReportQueryRepository.java backend/src/main/java/com/ifsc/contacerta/service/TeacherReportService.java backend/src/test/java/com/ifsc/contacerta/repository/TeacherReportQueryRepositoryTest.java backend/src/test/java/com/ifsc/contacerta/service/TeacherReportServiceTest.java
rtk git commit -m "feat: query teacher student progress report"
```

### Task 4: Histórico detalhado das tentativas

**Files:**
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/report/TeacherReportAttemptResponse.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/report/TeacherReportAttemptAnswerResponse.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/repository/TeacherReportQueryRepository.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/repository/JdbcTeacherReportQueryRepository.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/service/TeacherReportService.java`
- Modify: `backend/src/test/java/com/ifsc/contacerta/repository/TeacherReportQueryRepositoryTest.java`
- Modify: `backend/src/test/java/com/ifsc/contacerta/service/TeacherReportServiceTest.java`

**Interfaces:**
- Consumes: `ReportFilter`, `studentId` já validado e `Pageable` limitado a `submittedAt`.
- Produces: `Page<TeacherReportAttemptResponse> attempts(ReportFilter, UUID studentId, Pageable)`.

- [ ] **Step 1: Escrever teste vermelho do snapshot histórico**

Persistir uma tentativa finalizada com respostas choice, boolean e numeric; alterar depois a questão original e validar que a resposta usa `attempt_question_snapshots`. Verificar duração `submittedAt - startedAt`, contadores, XP/estrelas e exclusão de `IN_PROGRESS`.

```java
assertThat(result.getContent().getFirst().answers())
	.extracting(TeacherReportAttemptAnswerResponse::prompt)
	.containsExactly("Enunciado congelado");
```

Adicionar teste com duas tentativas na mesma página e confirmar que todas as respostas são associadas pelo `attemptId`, sem consulta por item.

- [ ] **Step 2: Executar teste e confirmar falha**

Run: `cd backend && rtk ./mvnw -Dtest=TeacherReportQueryRepositoryTest test`

Expected: FAIL pela ausência dos DTOs/operação.

- [ ] **Step 3: Implementar as duas consultas**

Primeiro buscar a página de resumos, ordenada por `submitted_at desc, id asc`. Depois, se houver IDs, executar uma única query de snapshots/respostas/opções para todos eles. Representar o valor registrado e a chave correta como `JsonNode`, produzidos no SQL com `jsonb_build_object/jsonb_agg`, preservando tipo, prompt, explicação e `answeredAt`. Agrupar em `Map<UUID, List<...>>` e montar records imutáveis.

- [ ] **Step 4: Executar testes de repositório e serviço**

Run: `cd backend && rtk ./mvnw -Dtest=TeacherReportQueryRepositoryTest,TeacherReportServiceTest test`

Expected: PASS para snapshot, paginação, ordenação e filtro.

- [ ] **Step 5: Commit atômico**

```bash
rtk git add backend/src/main/java/com/ifsc/contacerta/dto/report/TeacherReportAttemptResponse.java backend/src/main/java/com/ifsc/contacerta/dto/report/TeacherReportAttemptAnswerResponse.java backend/src/main/java/com/ifsc/contacerta/repository/TeacherReportQueryRepository.java backend/src/main/java/com/ifsc/contacerta/repository/JdbcTeacherReportQueryRepository.java backend/src/main/java/com/ifsc/contacerta/service/TeacherReportService.java backend/src/test/java/com/ifsc/contacerta/repository/TeacherReportQueryRepositoryTest.java backend/src/test/java/com/ifsc/contacerta/service/TeacherReportServiceTest.java
rtk git commit -m "feat: query teacher student attempt history"
```

### Task 5: Ranking filtrado da sala

**Files:**
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/report/TeacherReportRankingResponse.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/repository/TeacherReportQueryRepository.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/repository/JdbcTeacherReportQueryRepository.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/service/TeacherReportService.java`
- Modify: `backend/src/test/java/com/ifsc/contacerta/repository/TeacherReportQueryRepositoryTest.java`

**Interfaces:**
- Consumes: `ReportFilter`.
- Produces: `List<TeacherReportRankingResponse> ranking(ReportFilter filter)`.

- [ ] **Step 1: Escrever teste vermelho do ranking**

Criar alunos empatados e testar XP, melhor estrela por assignment, aluno zero, filtro temporal/aula e desempates nesta ordem: XP desc, estrelas desc, primeira conclusão asc, UUID asc. Verificar posições `1..N`.

- [ ] **Step 2: Executar teste e confirmar falha**

Run: `cd backend && rtk ./mvnw -Dtest=TeacherReportQueryRepositoryTest test`

Expected: FAIL pela operação ausente.

- [ ] **Step 3: Implementar agregação e posicionamento**

Usar CTE para tentativas filtradas e outra para melhor estrela por aluno/assignment. Partir de memberships ativos e fazer `left join`; ordenar com `first_completion_at asc nulls last, student_id asc`. A posição pode ser produzida com `row_number() over (...)` e convertida para inteiro no mapper.

- [ ] **Step 4: Executar teste de integração**

Run: `cd backend && rtk ./mvnw -Dtest=TeacherReportQueryRepositoryTest test`

Expected: PASS para filtros, zeros e todos os desempates.

- [ ] **Step 5: Commit atômico**

```bash
rtk git add backend/src/main/java/com/ifsc/contacerta/dto/report/TeacherReportRankingResponse.java backend/src/main/java/com/ifsc/contacerta/repository/TeacherReportQueryRepository.java backend/src/main/java/com/ifsc/contacerta/repository/JdbcTeacherReportQueryRepository.java backend/src/main/java/com/ifsc/contacerta/service/TeacherReportService.java backend/src/test/java/com/ifsc/contacerta/repository/TeacherReportQueryRepositoryTest.java
rtk git commit -m "feat: query filtered teacher room ranking"
```

### Task 6: Controller e segurança dos quatro endpoints

**Files:**
- Create: `backend/src/main/java/com/ifsc/contacerta/controller/TeacherReportController.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/controller/TeacherReportControllerTest.java`

**Interfaces:**
- Consumes: quatro métodos públicos de `TeacherReportService` e `CurrentUser.userId()`.
- Produces: os quatro `GET /teacher/reports/**` definidos na spec.

- [ ] **Step 1: Escrever testes HTTP vermelhos**

Cobrir bearer ausente (401), aluno autenticado (403 `TEACHER_REQUIRED`), professor com sala alheia (404), aula não atribuída (404), aluno removido (404), período inválido (422), parâmetro malformado (400), tamanho acima de 100 (400) e payload feliz de cada endpoint.

```java
mockMvc.perform(get("/teacher/reports/overview")
		.header("Authorization", bearer(login))
		.param("roomId", room.getId().toString())
		.param("period", "ALL"))
	.andExpect(status().isOk())
	.andExpect(jsonPath("$.activeStudentCount").value(2));
```

- [ ] **Step 2: Executar o teste e confirmar 404/compilação**

Run: `cd backend && rtk ./mvnw -Dtest=TeacherReportControllerTest test`

Expected: FAIL porque o controller não existe.

- [ ] **Step 3: Implementar controller fino**

Usar `@RequestMapping("/teacher/reports")`, `@AuthenticationPrincipal CurrentUser`, `@RequestParam UUID roomId`, instantes opcionais e `ReportPeriod`. Seguir o padrão atual: toda rota autenticada chega ao serviço, e `TeacherReportFilterFactory.requireActiveTeacher` produz 403 `TEACHER_REQUIRED` para aluno. Validar `page >= 0` e `1 <= size <= 100` no serviço e devolver `PageResponse.from(page)` sem alterar `PageResponse`.

- [ ] **Step 4: Executar testes HTTP e de segurança**

Run: `cd backend && rtk ./mvnw -Dtest=TeacherReportControllerTest,SecurityConfigTest test`

Expected: PASS nos quatro contratos e erros.

- [ ] **Step 5: Commit atômico**

```bash
rtk git add backend/src/main/java/com/ifsc/contacerta/controller/TeacherReportController.java backend/src/test/java/com/ifsc/contacerta/controller/TeacherReportControllerTest.java
rtk git commit -m "feat: expose teacher report endpoints"
```

### Task 7: Índices e verificação completa

**Files:**
- Create: `backend/src/main/resources/db/migration/V10__add_teacher_report_indexes.sql`
- Test: `backend/src/test/java/com/ifsc/contacerta/repository/TeacherReportQueryRepositoryTest.java`

**Interfaces:**
- Consumes: predicados reais observados nas queries das Tasks 2–5.
- Produces: índices de attempts para sala/aluno/período via assignment e leitura de respostas por tentativa.

- [ ] **Step 1: Registrar teste vermelho da migration/índices**

No teste PostgreSQL, consultar `pg_indexes` e exigir os nomes escolhidos:

```java
assertThat(indexNames("attempts"))
	.contains("idx_attempts_assignment_submitted_finalized", "idx_attempts_student_submitted_finalized");
```

- [ ] **Step 2: Executar teste e confirmar falha**

Run: `cd backend && rtk ./mvnw -Dtest=TeacherReportQueryRepositoryTest test`

Expected: FAIL porque os índices ainda não existem.

- [ ] **Step 3: Adicionar somente índices alinhados às queries**

Criar migration inicial:

```sql
create index idx_attempts_assignment_submitted_finalized
    on attempts (assignment_id, submitted_at, student_id)
    where status in ('SUBMITTED', 'EXPIRED');

create index idx_attempts_student_submitted_finalized
    on attempts (student_id, submitted_at desc, assignment_id)
    where status in ('SUBMITTED', 'EXPIRED');
```

Não duplicar índices já existentes em assignments, memberships ou snapshots.

- [ ] **Step 4: Executar testes de relatório e suíte completa**

Run: `cd backend && rtk ./mvnw -Dtest=TeacherReportQueryRepositoryTest,TeacherReportServiceTest,TeacherReportControllerTest test`

Expected: PASS.

Run: `cd backend && rtk ./mvnw verify`

Expected: BUILD SUCCESS, zero failures e zero errors.

- [ ] **Step 5: Revisar diff e commit atômico final**

Run: `rtk git diff --check`

Run: `rtk git status --short`

Confirmar que nenhuma alteração anterior do checkout principal entrou na worktree.

```bash
rtk git add backend/src/main/resources/db/migration/V10__add_teacher_report_indexes.sql backend/src/test/java/com/ifsc/contacerta/repository/TeacherReportQueryRepositoryTest.java
rtk git commit -m "perf: index teacher report queries"
```

### Task 8: Revisão final da entrega

**Files:**
- Review only: all files committed in Tasks 1–7.

**Interfaces:**
- Consumes: spec e implementação completa.
- Produces: evidência de conformidade e branch pronta para PR.

- [ ] **Step 1: Conferir cobertura da spec**

Validar manualmente os quatro endpoints, todos os campos, filtros, erros, alunos zero, UTC, snapshots e ausência de CSV.

- [ ] **Step 2: Conferir higiene Java e escopo dos commits**

Run: `rtk rg -n "import .*\\*;|java\\.(util|time|math)\\." backend/src/main/java/com/ifsc/contacerta/{controller,dto,model,repository,service}`

Expected: nenhum novo import curinga ou tipo totalmente qualificado inline nos arquivos da feature.

Run: `rtk git log --oneline --decorate -8`

Expected: commits separados por filtro, overview, alunos, tentativas, ranking, endpoints e índices.

- [ ] **Step 3: Executar verificação limpa final**

Run: `cd backend && rtk ./mvnw clean verify`

Expected: BUILD SUCCESS.

- [ ] **Step 4: Solicitar code review antes da integração**

Usar `superpowers:requesting-code-review`, corrigir apenas achados comprovados em novos commits atômicos e repetir `rtk ./mvnw clean verify`.

- [ ] **Step 5: Preparar handoff da branch**

Usar `superpowers:finishing-a-development-branch` para oferecer merge, push/PR ou preservação da worktree. Não criar commit vazio para esta tarefa.
