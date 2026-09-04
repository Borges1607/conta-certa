# Dicas financeiras administrativas — plano de implementação

> **Para agentes:** use `superpowers:executing-plans` para executar este plano tarefa por tarefa, mantendo cada commit isolado e verificável.

**Goal:** Entregar o agregado e a API administrativa de dicas financeiras com arquivamento lógico, versionamento otimista e datas sem conversão de fuso.

**Architecture:** Uma entidade `FinancialTip` persistida em PostgreSQL será consultada por uma specification que sempre exclui registros arquivados. Um serviço administrativo concentra validação, transições e mapeamento; o controller limita o contrato HTTP e reutiliza a paginação administrativa existente.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring Data JPA, PostgreSQL, Flyway, Bean Validation, JUnit 5, Mockito, MockMvc e Testcontainers.

**Spec:** `docs/superpowers/specs/2026-09-03-backend-financial-tips-design.md`

## Global Constraints

- `publicationDate` é `LocalDate` e trafega como `YYYY-MM-DD`, sem conversão para UTC.
- `DELETE` é exclusão lógica: preenche `archivedAt`, define `active=false` e retorna `204`.
- Registros arquivados não aparecem em listas nem aceitam edição ou transições.
- Alterações concorrentes retornam `409 VERSION_CONFLICT`.
- Todas as rotas `/admin/**` exigem `ROLE_ADMIN`.
- Códigos de erro devem usar o envelope de erro existente (`ApiException`/`GlobalExceptionHandler`).
- Comandos shell usam o prefixo `rtk`; edições de arquivos usam `apply_patch`.

---

### Task 1: Criar migration e agregado `FinancialTip`

**Files:**
- Create: `backend/src/main/resources/db/migration/V13__create_financial_tips.sql`
- Create: `backend/src/main/java/com/ifsc/contacerta/entity/FinancialTip.java`
- Modify: `backend/src/test/java/com/ifsc/contacerta/support/PostgresIntegrationTest.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/entity/FinancialTipPersistenceTest.java`

**Interfaces:**
- Produces `FinancialTip(UUID, String, String, String, LocalDate, boolean)` and getters used by mapper/service.
- Produces `update(String, String, String, LocalDate)`, `activate()`, `deactivate()` and `archive(Instant)`.

- [ ] **Step 1: Write the failing persistence/domain tests**

  Teste com Testcontainers deve persistir a data como `LocalDate`, iniciar `archivedAt=null`, verificar `archive()` tornando a dica inativa e rejeitar nova alteração de uma dica arquivada.

- [ ] **Step 2: Run tests to verify they fail**

  Run: `./mvnw -Dtest=FinancialTipPersistenceTest test`

  Expected: falha de compilação porque a migration e a entidade ainda não existem.

- [ ] **Step 3: Implement migration and entity**

  Criar tabela com `id uuid`, `title varchar(160)`, `content text`, `source_url varchar(2048)`, `publication_date date`, `active boolean`, `archived_at timestamptz`, timestamps e `version bigint`; adicionar índices para `(publication_date, active)` e `archived_at`. A entidade deve usar `@Version`, callbacks de timestamp e lançar `IllegalStateException` em qualquer mutação de item arquivado. Atualizar a limpeza do `PostgresIntegrationTest` para truncar `financial_tips`.

- [ ] **Step 4: Run tests to verify they pass**

  Run: `./mvnw -Dtest=FinancialTipPersistenceTest test`

  Expected: PASS.

- [ ] **Step 5: Commit atomically**

  `git add backend/src/main/resources/db/migration/V13__create_financial_tips.sql backend/src/main/java/com/ifsc/contacerta/entity/FinancialTip.java backend/src/test/java/com/ifsc/contacerta/support/PostgresIntegrationTest.java backend/src/test/java/com/ifsc/contacerta/entity/FinancialTipPersistenceTest.java && git commit -m "feat: cria agregado de dicas financeiras"`

### Task 2: Adicionar repository e filtros administrativos

**Files:**
- Create: `backend/src/main/java/com/ifsc/contacerta/specification/FinancialTipSpecification.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/repository/FinancialTipRepository.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/repository/FinancialTipRepositoryTest.java`

**Interfaces:**
- Produces `Page<FinancialTip> findAll(Specification<FinancialTip>, Pageable)` through `JpaSpecificationExecutor`.
- Produces `FinancialTipSpecification.filtered(String search, Boolean active, LocalDate publicationDate)`.

- [ ] **Step 1: Write failing repository tests**

  Persistir duas dicas e uma arquivada; verificar busca case-insensitive por título, filtro de status/data e que o registro arquivado nunca aparece.

- [ ] **Step 2: Run red**

  Run: `./mvnw -Dtest=FinancialTipRepositoryTest test`

  Expected: falha de compilação por tipos ausentes.

- [ ] **Step 3: Implement repository/specification**

  A specification deve adicionar predicados `archivedAt is null`, título contendo busca quando informada, `active` quando não nulo e igualdade de `publicationDate` quando informada.

- [ ] **Step 4: Run green**

  Run: `./mvnw -Dtest=FinancialTipRepositoryTest test`

  Expected: PASS.

- [ ] **Step 5: Commit**

  `git add backend/src/main/java/com/ifsc/contacerta/specification/FinancialTipSpecification.java backend/src/main/java/com/ifsc/contacerta/repository/FinancialTipRepository.java backend/src/test/java/com/ifsc/contacerta/repository/FinancialTipRepositoryTest.java && git commit -m "feat: adiciona filtros de dicas financeiras"`

### Task 3: Definir DTOs e mapper

**Files:**
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/admin/AdminFinancialTipResponse.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/admin/CreateFinancialTipRequest.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/admin/PatchFinancialTipRequest.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/mapper/AdminFinancialTipMapper.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/mapper/AdminFinancialTipMapperTest.java`

**Interfaces:**
- `AdminFinancialTipResponse(UUID id, String title, String content, String sourceUrl, LocalDate publicationDate, boolean active, Instant createdAt, Instant updatedAt, long version, Instant archivedAt)`.
- `CreateFinancialTipRequest(String title, String content, String sourceUrl, LocalDate publicationDate, Boolean active)`; `active=null` significa `false`.
- `PatchFinancialTipRequest(String title, String content, String sourceUrl, LocalDate publicationDate, Long version)`.
- `AdminFinancialTipMapper.toResponse(FinancialTip)`.

- [ ] **Step 1: Write failing mapper/validation tests**

  Verificar serialização `publicationDate` sem hora, preservação de Markdown e que o response expõe `archivedAt` sem campos de sala, aluno ou professor.

- [ ] **Step 2: Run red**

  Run: `./mvnw -Dtest=AdminFinancialTipMapperTest test`

  Expected: falha de compilação por DTOs/mapper ausentes.

- [ ] **Step 3: Implement DTOs and mapper**

  Usar Bean Validation: título `@NotBlank @Size(max=160)`, conteúdo `@NotBlank`, URL opcional limitada a 2048, data `@NotNull`, e `version` obrigatório no patch. O mapper deve copiar os valores da entidade sem converter `LocalDate`.

- [ ] **Step 4: Run green**

  Run: `./mvnw -Dtest=AdminFinancialTipMapperTest test`

  Expected: PASS.

- [ ] **Step 5: Commit**

  `git add backend/src/main/java/com/ifsc/contacerta/dto/admin/AdminFinancialTipResponse.java backend/src/main/java/com/ifsc/contacerta/dto/admin/CreateFinancialTipRequest.java backend/src/main/java/com/ifsc/contacerta/dto/admin/PatchFinancialTipRequest.java backend/src/main/java/com/ifsc/contacerta/mapper/AdminFinancialTipMapper.java backend/src/test/java/com/ifsc/contacerta/mapper/AdminFinancialTipMapperTest.java && git commit -m "feat: define contrato de dicas financeiras"`

### Task 4: Implementar serviço administrativo

**Files:**
- Create: `backend/src/main/java/com/ifsc/contacerta/service/AdminFinancialTipService.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/service/AdminFinancialTipServiceTest.java`

**Interfaces:**
- `Page<AdminFinancialTipResponse> list(String search, Boolean active, LocalDate publicationDate, Pageable pageable)`.
- `AdminFinancialTipResponse get(UUID id)`.
- `AdminFinancialTipResponse create(CreateFinancialTipRequest request)`.
- `AdminFinancialTipResponse update(UUID id, PatchFinancialTipRequest request)`.
- `AdminFinancialTipResponse activate(UUID id)` and `deactivate(UUID id)`.
- `void archive(UUID id)`.

- [ ] **Step 1: Write failing service tests**

  Cobrir criação inativa por padrão, URL HTTP/HTTPS válida, rejeição de URL inválida com `422 INVALID_SOURCE_URL`, atualização com versão obsoleta (`409 VERSION_CONFLICT`), ativação/desativação idempotentes e arquivamento lógico.

- [ ] **Step 2: Run red**

  Run: `./mvnw -Dtest=AdminFinancialTipServiceTest test`

  Expected: falha de compilação porque o serviço ainda não existe.

- [ ] **Step 3: Implement service**

  Normalizar título/conteúdo com `trim` apenas nos limites, preservar o Markdown interno, validar URL com `URI` aceitando somente `http`/`https`, ignorar arquivados na busca por ID e mapear todos os erros para códigos estáveis. Comparar `version` antes de `update`; não carregar listas para paginação.

- [ ] **Step 4: Run green**

  Run: `./mvnw -Dtest=AdminFinancialTipServiceTest test`

  Expected: PASS.

- [ ] **Step 5: Commit**

  `git add backend/src/main/java/com/ifsc/contacerta/service/AdminFinancialTipService.java backend/src/test/java/com/ifsc/contacerta/service/AdminFinancialTipServiceTest.java && git commit -m "feat: implementa regras de dicas financeiras"`

### Task 5: Expor controller e contrato de segurança

**Files:**
- Create: `backend/src/main/java/com/ifsc/contacerta/controller/AdminFinancialTipController.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/controller/AdminFinancialTipControllerTest.java`
- Modify: `backend/src/test/java/com/ifsc/contacerta/config/SecurityConfigTest.java`

**Interfaces:**
- Rotas `GET/POST /admin/financial-tips`, `GET/PATCH/DELETE /admin/financial-tips/{tipId}`, `POST .../activate` e `POST .../deactivate`.
- Lista usa `AdminPageableFactory` com allow-list `title`, `publicationDate`, `createdAt`, `updatedAt` e retorna `PageResponse`.
- Criação retorna `201` com `Location`; exclusão retorna `204`; ações retornam o response atualizado.

- [ ] **Step 1: Write failing controller/security tests**

  MockMvc standalone deve verificar envelope, `Location`, `204` e parâmetros `publicationDate=2026-09-03`. O teste de segurança deve verificar `401` anônimo, `403` professor e `200` admin para a lista/dashboard de dicas.

- [ ] **Step 2: Run red**

  Run: `./mvnw -Dtest=AdminFinancialTipControllerTest,SecurityConfigTest test`

  Expected: falha de compilação por controller ausente ou falha de rota.

- [ ] **Step 3: Implement controller**

  Delegar toda regra ao serviço, converter query param diretamente para `LocalDate`, usar `@Valid` nos requests e não aceitar HTML/arquivo fora do Markdown textual.

- [ ] **Step 4: Run green**

  Run: `./mvnw -Dtest=AdminFinancialTipControllerTest,SecurityConfigTest test`

  Expected: PASS.

- [ ] **Step 5: Commit**

  `git add backend/src/main/java/com/ifsc/contacerta/controller/AdminFinancialTipController.java backend/src/test/java/com/ifsc/contacerta/controller/AdminFinancialTipControllerTest.java backend/src/test/java/com/ifsc/contacerta/config/SecurityConfigTest.java && git commit -m "feat: expõe API administrativa de dicas"`

### Task 6: Verificação final da fatia

**Files:**
- Test: suíte administrativa existente e nova suíte de dicas.

- [ ] **Step 1: Run focused verification**

  Run: `./mvnw -Dtest=FinancialTipPersistenceTest,FinancialTipRepositoryTest,AdminFinancialTipMapperTest,AdminFinancialTipServiceTest,AdminFinancialTipControllerTest,SecurityConfigTest test`

  Expected: todos os testes passam.

- [ ] **Step 2: Run full verification**

  Run: `./mvnw verify` and `git diff --check`

  Expected: build/testes sem falhas e nenhum erro de whitespace.

- [ ] **Step 3: Inspect status and commits**

  Run: `git status --short` and `git log --oneline --decorate -8`; confirmar branch isolada, commits atômicos e nenhuma alteração não intencional.
