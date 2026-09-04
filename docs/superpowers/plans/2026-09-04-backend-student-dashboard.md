# Plano de implementação: dashboard do aluno

> **Para execução:** usar `superpowers:executing-plans` para implementar este plano tarefa por tarefa, com revisão e verificação entre os commits.

**Objetivo:** disponibilizar `GET /student/rooms/{roomId}/dashboard`, agregando dados da sala, progresso, próxima lição, conquistas recentes, ranking do próprio aluno e dica financeira diária.

**Arquitetura:** um `StudentRoomDashboardService` autoriza explicitamente o aluno e orquestra serviços já existentes de trilha e gamificação. Um `StudentFinancialTipService` encapsula a escolha determinística da dica usando o `Clock` da aplicação. DTOs específicos evitam expor campos administrativos de `FinancialTip`; não haverá migration.

**Stack técnico:** Java 21, Spring Boot, Spring Data JPA, Maven, JUnit 5, Mockito, Testcontainers/PostgreSQL.

---

## Tarefa 1: selecionar e expor a dica financeira diária

**Arquivos:**

- Criar: `backend/src/main/java/com/ifsc/contacerta/dto/studentdashboard/StudentFinancialTipResponse.java`
- Criar: `backend/src/main/java/com/ifsc/contacerta/service/StudentFinancialTipService.java`
- Modificar: `backend/src/main/java/com/ifsc/contacerta/repository/FinancialTipRepository.java`
- Criar: `backend/src/test/java/com/ifsc/contacerta/service/StudentFinancialTipServiceTest.java`

**Passo 1: escrever o teste que falha.**

Criar `StudentFinancialTipServiceTest` com `FinancialTipRepository` mockado e `Clock.fixed(...)`. Cobrir estes cenários públicos de `currentTip()`:

1. para `2026-09-04` no fuso `America/Sao_Paulo`, uma dica ativa e não arquivada com `publicationDate` igual à data local vence o fallback;
2. havendo duas dicas agendadas, a consulta ordenada por UUID retorna a primeira e ela é usada;
3. sem dica agendada, o serviço usa `Math.floorMod(localDate.toEpochDay(), tips.size())` sobre a lista de ativas, não arquivadas e ordenadas por UUID;
4. sem dicas, retorna `null`.

Verificar também que o DTO contém apenas `id`, `title`, `content`, `sourceUrl` e `publicationDate`.

**Passo 2: executar o teste e confirmar o estado vermelho.**

Executar no diretório `backend`:

```bash
rtk ./mvnw -Dtest=StudentFinancialTipServiceTest test
```

O build deve falhar porque serviço, DTO e consultas ainda não existem.

**Passo 3: implementar o mínimo para o teste passar.**

Adicionar ao repositório as consultas, já ordenadas para tornar a seleção estável:

```java
List<FinancialTip> findByActiveTrueAndArchivedAtIsNullAndPublicationDateOrderByIdAsc(LocalDate publicationDate);

List<FinancialTip> findByActiveTrueAndArchivedAtIsNullOrderByIdAsc();
```

Implementar o record:

```java
public record StudentFinancialTipResponse(
	UUID id,
	String title,
	String content,
	String sourceUrl,
	LocalDate publicationDate
) {
}
```

Implementar `StudentFinancialTipService.currentTip()` com `Clock` injetado. Obter a data com `LocalDate.now(clock.withZone(ZoneId.of("America/Sao_Paulo")))`; retornar a primeira dica agendada quando existir; caso contrário, aplicar o índice `Math.floorMod(date.toEpochDay(), tips.size())`; mapear somente os cinco campos públicos.

**Passo 4: executar o teste e confirmar o estado verde.**

```bash
rtk ./mvnw -Dtest=StudentFinancialTipServiceTest test
```

**Passo 5: verificar e criar o commit atômico.**

```bash
rtk git diff --check
rtk git add backend/src/main/java/com/ifsc/contacerta/dto/studentdashboard/StudentFinancialTipResponse.java backend/src/main/java/com/ifsc/contacerta/service/StudentFinancialTipService.java backend/src/main/java/com/ifsc/contacerta/repository/FinancialTipRepository.java backend/src/test/java/com/ifsc/contacerta/service/StudentFinancialTipServiceTest.java
rtk git commit -m "feat: seleciona dica financeira do aluno"
```

## Tarefa 2: compor o dashboard e suas regras de domínio

**Arquivos:**

- Criar: `backend/src/main/java/com/ifsc/contacerta/dto/studentdashboard/StudentDashboardProgressResponse.java`
- Criar: `backend/src/main/java/com/ifsc/contacerta/dto/studentdashboard/StudentRoomDashboardResponse.java`
- Criar: `backend/src/main/java/com/ifsc/contacerta/service/StudentRoomDashboardService.java`
- Modificar: `backend/src/main/java/com/ifsc/contacerta/mapper/RoomMapper.java`
- Criar: `backend/src/test/java/com/ifsc/contacerta/service/StudentRoomDashboardServiceTest.java`

**Passo 1: escrever os testes que falham.**

Criar um teste Mockito para `StudentRoomDashboardService.dashboard(UUID studentId, UUID roomId)`. Simular usuário estudante ativo, matrícula ativa e sala da mesma instituição. Cobrir:

1. ausência de `RoomStudentProgress` produz XP, estrelas, aulas concluídas e aprovadas iguais a zero, nível 1 e percentuais zero;
2. com `passedAssignmentCount=2`, quatro itens da trilha e `totalXp=150`, retornar `progressPercent=50` e `levelProgressPercent=50`;
3. selecionar a primeira lição `IN_PROGRESS`, depois a primeira `AVAILABLE`, depois a primeira `FAILED` com `attemptsRemaining` nulo ou positivo; retornar `null` se nenhuma dessas condições for satisfeita;
4. filtrar somente conquistas desbloqueadas, ordenar por `unlockedAt` decrescente e `code` crescente, e limitar a três;
5. usar exatamente `RankingResponse.self()` e a dica retornada pelo seletor;
6. conta inativa gera `403 ACCOUNT_INACTIVE`, usuário não estudante gera `403 STUDENT_REQUIRED`, matrícula ausente/inativa e instituição divergente geram `404 ROOM_NOT_FOUND`.

**Passo 2: executar o teste e confirmar o estado vermelho.**

```bash
rtk ./mvnw -Dtest=StudentRoomDashboardServiceTest test
```

**Passo 3: implementar o mínimo para o teste passar.**

Criar os records:

```java
public record StudentDashboardProgressResponse(
	int totalXp,
	int level,
	int levelProgressPercent,
	int totalStars,
	int completedLessons,
	int passedLessons,
	int totalLessons
) {
}
```

```java
public record StudentRoomDashboardResponse(
	StudentRoomResponse room,
	StudentDashboardProgressResponse progress,
	StudentLessonPathResponse nextLesson,
	List<AchievementResponse> recentAchievements,
	StudentFinancialTipResponse financialTip,
	RankingEntryResponse ranking
) {
}
```

Implementar `StudentRoomDashboardService` como `@Service`, `@RequiredArgsConstructor` e `@Transactional(readOnly = true)`. Carregar o usuário, validar `Role.STUDENT` e `AccountStatus.ACTIVE`, buscar a matrícula pelo par sala/aluno, exigir `MembershipStatus.ACTIVE` e comparar a instituição da sala com a do aluno. Para os casos não visíveis, lançar `ApiException` com `HttpStatus.NOT_FOUND`, código `ROOM_NOT_FOUND`; para papel e conta, usar os códigos definidos na especificação.

Reutilizar `StudentLessonService.path`, `StudentGamificationService.achievements` e `StudentGamificationService.ranking(studentId, roomId, 0, 1)`. Não duplicar regras de disponibilidade. Usar `RoomStudentProgressRepository.findByRoomIdAndStudentId`; quando vazio, aplicar os valores padrão definidos pelo contrato. Calcular os percentuais com divisão inteira e tratar zero aulas.

Adicionar uma sobrecarga em `RoomMapper` que aceite `Room`, `MembershipStatus` e `progressPercent`, deixando o mapeamento atual da lista de salas com progresso zero. Usar a sobrecarga no dashboard, sem construir `StudentRoomResponse` manualmente.

**Passo 4: executar o teste e confirmar o estado verde.**

```bash
rtk ./mvnw -Dtest=StudentRoomDashboardServiceTest test
```

**Passo 5: verificar e criar o commit atômico.**

```bash
rtk git diff --check
rtk git add backend/src/main/java/com/ifsc/contacerta/dto/studentdashboard/StudentDashboardProgressResponse.java backend/src/main/java/com/ifsc/contacerta/dto/studentdashboard/StudentRoomDashboardResponse.java backend/src/main/java/com/ifsc/contacerta/service/StudentRoomDashboardService.java backend/src/main/java/com/ifsc/contacerta/mapper/RoomMapper.java backend/src/test/java/com/ifsc/contacerta/service/StudentRoomDashboardServiceTest.java
rtk git commit -m "feat: agrega dashboard da sala do aluno"
```

## Tarefa 3: publicar a rota e validar a integração de acesso

**Arquivos:**

- Modificar: `backend/src/main/java/com/ifsc/contacerta/controller/StudentRoomController.java`
- Criar: `backend/src/test/java/com/ifsc/contacerta/controller/StudentRoomDashboardControllerTest.java`
- Criar: `backend/src/test/java/com/ifsc/contacerta/service/StudentRoomDashboardServiceIntegrationTest.java`

**Passo 1: escrever os testes que falham.**

Criar `StudentRoomDashboardControllerTest` com MockMvc e `StudentRoomDashboardService` mockado para verificar que `GET /student/rooms/{roomId}/dashboard` recebe `CurrentUser`, delega o `userId` e o UUID da rota ao serviço e serializa os campos principais do DTO retornado.

Criar `StudentRoomDashboardServiceIntegrationTest` estendendo `PostgresIntegrationTest`. Persistir instituição, professor, estudante, sala e matrícula pelo conjunto de builders/constructores já usado nos testes de sala. Exercitar o serviço real para comprovar:

1. estudante ativo com matrícula `ACTIVE` recebe o dashboard;
2. matrícula removida ou status diferente de `ACTIVE` recebe `ROOM_NOT_FOUND`;
3. aluno e sala de instituições diferentes recebem `ROOM_NOT_FOUND`;
4. uma sala marcada como arquivada continua acessível ao aluno com matrícula ativa.

Manter a massa de dados sem atribuições quando não forem necessárias: a trilha vazia, ranking e conquistas existentes devem continuar compor uma resposta válida.

**Passo 2: executar os testes e confirmar o estado vermelho.**

```bash
rtk ./mvnw -Dtest=StudentRoomDashboardControllerTest,StudentRoomDashboardServiceIntegrationTest test
```

**Passo 3: implementar o mínimo para os testes passarem.**

Injetar `StudentRoomDashboardService` em `StudentRoomController` e adicionar:

```java
@GetMapping("/{roomId}/dashboard")
public StudentRoomDashboardResponse dashboard(
		@AuthenticationPrincipal CurrentUser currentUser,
		@PathVariable UUID roomId
) {
	return dashboardService.dashboard(currentUser.userId(), roomId);
}
```

Adicionar somente os imports necessários (`StudentRoomDashboardResponse`, `StudentRoomDashboardService`, `PathVariable` e `UUID`) e preservar as rotas existentes de lista e ingresso.

**Passo 4: executar os testes e confirmar o estado verde.**

```bash
rtk ./mvnw -Dtest=StudentRoomDashboardControllerTest,StudentRoomDashboardServiceIntegrationTest test
```

**Passo 5: verificar e criar o commit atômico.**

```bash
rtk git diff --check
rtk git add backend/src/main/java/com/ifsc/contacerta/controller/StudentRoomController.java backend/src/test/java/com/ifsc/contacerta/controller/StudentRoomDashboardControllerTest.java backend/src/test/java/com/ifsc/contacerta/service/StudentRoomDashboardServiceIntegrationTest.java
rtk git commit -m "feat: expoe dashboard da sala do aluno"
```

## Tarefa 4: verificação de regressão e preparação para revisão

**Arquivos:**

- Modificar somente se uma correção descoberta pela verificação for necessária; incluí-la em um commit de correção separado e coberto por teste.

**Passo 1: executar a suíte focalizada.**

```bash
rtk ./mvnw -Dtest=StudentFinancialTipServiceTest,StudentRoomDashboardServiceTest,StudentRoomDashboardControllerTest,StudentRoomDashboardServiceIntegrationTest,StudentRoomControllerTest,StudentLessonControllerTest,StudentGamificationControllerTest,FinancialTipRepositoryTest test
```

**Passo 2: verificar compilação empacotada.**

```bash
rtk ./mvnw -DskipTests package
```

**Passo 3: verificar integridade do diff e histórico.**

```bash
rtk git diff --check origin/main...HEAD
rtk git status --short
rtk git log --oneline origin/main..HEAD
```

**Passo 4: tentar a suíte completa e registrar qualquer limitação ambiental.**

```bash
rtk ./mvnw test
```

Se a execução integral falhar pela interrupção compartilhada do container Testcontainers/PostgreSQL já observada neste projeto, registrar o erro e os resultados verdes da suíte focalizada e do `package`; não alterar regras de produção para contornar a instabilidade do ambiente de teste.

