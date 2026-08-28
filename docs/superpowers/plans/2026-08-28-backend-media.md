# Backend Media Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implementar vídeos, materiais, arquivos privados no PostgreSQL, publicação em salas, consumo pelo aluno e visualizações conforme os contratos existentes do frontend.

**Architecture:** Vídeos e materiais são agregados JPA separados. Arquivos passam pela interface `FileStorage`, implementada por `PostgresFileStorage`; publicação e visualização usam tabelas polimórficas com constraints PostgreSQL. Controllers cuidam apenas do transporte e services aplicam autorização contextual, transações e mapeamento para DTOs.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring MVC multipart, Spring Data JPA, PostgreSQL `bytea`, Flyway, Bean Validation, JUnit 5, Mockito, MockMvc e Testcontainers.

**Spec:** `docs/superpowers/specs/2026-08-28-backend-media-design.md`

## Global Constraints

- Base URL `/api/v1`; JSON em inglês e `camelCase`; IDs UUID; datas `Instant` UTC.
- Arquivos residem somente no PostgreSQL; não usar S3 nem filesystem.
- Aceitar somente PDF, PPT e PPTX até `10 * 1024 * 1024` bytes.
- URLs externas aceitam somente HTTPS, sem credenciais embutidas.
- Conta deve estar ativa; autoria, instituição, sala e matrícula são verificadas no service.
- Recurso não visível retorna `404`; perfil incompatível retorna `403`.
- Vídeos, materiais e vínculos usam optimistic locking e `409 VERSION_CONFLICT`.
- Visualização é única por aluno, sala, tipo e mídia e preserva `firstViewedAt`.
- Tamanho de página: `page >= 0` e `1 <= size <= 100`; valores inválidos retornam `422 VALIDATION_ERROR`.
- Preservar as alterações locais não relacionadas em `ContacertaApplication.java`, `.angular/` e planos antigos não rastreados.
- Todo comando shell deve usar o prefixo `rtk`.

---

### Task 1: Schema e modelo persistente de mídias

**Files:**
- Create: `backend/src/main/resources/db/migration/V8__create_media_tables.sql`
- Create: `backend/src/main/java/com/ifsc/contacerta/model/MaterialKind.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/model/MediaViewType.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/entity/Video.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/entity/StoredFile.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/entity/Material.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/entity/MediaAssignment.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/entity/MediaView.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/repository/VideoRepository.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/repository/StoredFileRepository.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/repository/MaterialRepository.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/repository/MediaAssignmentRepository.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/repository/MediaViewRepository.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/entity/MediaPersistenceTest.java`

**Interfaces:**
- Produces: `MaterialKind.FILE|EXTERNAL_LINK`, `MediaViewType.VIDEO|MATERIAL`.
- Produces: repositories JPA usados por todos os tasks seguintes.
- Produces: `MediaView.recordView(Instant)` que preserva a primeira data, atualiza a última e incrementa `viewCount`.

- [ ] **Step 1: Escrever teste PostgreSQL inicialmente vermelho**

Criar `MediaPersistenceTest extends PostgresIntegrationTest` com fixtures de instituição, professor, aluno, sala e lição. Persistir vídeo, arquivo, material e vínculo; confirmar relações e constraints:

```java
@Test
void devePersistirArquivoMaterialVinculoEVisualizacao() {
	StoredFile file = storedFileRepository.save(new StoredFile(
			teacher, "aula.pdf", "application/pdf", 4, "sha256", new byte[]{1, 2, 3, 4}, now
	));
	Material material = materialRepository.save(Material.file(
			teacher, "Apostila", null, "Porcentagem", file, now
	));
	MediaAssignment assignment = mediaAssignmentRepository.save(
			MediaAssignment.material(room, material, lessonAssignment, 1, now)
	);
	MediaView view = mediaViewRepository.save(MediaView.material(student, room, material, now));

	assertThat(file.getContent()).containsExactly(1, 2, 3, 4);
	assertThat(assignment.getMaterial().getId()).isEqualTo(material.getId());
	assertThat(view.getViewCount()).isEqualTo(1);
}
```

Adicionar testes que tentem persistir dois vínculos da mesma mídia na mesma sala e dois registros de visualização com a mesma chave, esperando `DataIntegrityViolationException` no `flush()`.

- [ ] **Step 2: Executar o teste e confirmar falha de compilação/migration ausente**

Run: `cd backend && rtk ./mvnw -Dtest=MediaPersistenceTest test`  
Expected: FAIL porque entidades, repositories e migration ainda não existem.

- [ ] **Step 3: Criar migration com constraints completas**

O SQL deve criar as cinco tabelas. Usar checks polimórficos e índices parciais:

```sql
constraint ck_media_assignments_target check (
    (media_type = 'VIDEO' and video_id is not null and material_id is null)
    or (media_type = 'MATERIAL' and material_id is not null and video_id is null)
);
create unique index uk_media_assignments_room_video
    on media_assignments(room_id, video_id) where video_id is not null;
create unique index uk_media_assignments_room_material
    on media_assignments(room_id, material_id) where material_id is not null;

constraint ck_media_views_target check (
    (media_type = 'VIDEO' and video_id is not null and material_id is null)
    or (media_type = 'MATERIAL' and material_id is not null and video_id is null)
);
create unique index uk_media_views_student_room_video
    on media_views(student_id, room_id, video_id) where video_id is not null;
create unique index uk_media_views_student_room_material
    on media_views(student_id, room_id, material_id) where material_id is not null;
```

Adicionar checks de status, kind, posição positiva, tamanho não negativo, `view_count > 0` e coerência `external_url/file_id`. Indexar `videos(teacher_id,status,title)`, `materials(teacher_id,status,title)`, `media_assignments(room_id,position)`, `media_assignments(lesson_assignment_id)` e `media_views(room_id,student_id)`.

- [ ] **Step 4: Implementar entidades e repositories mínimos**

Cada entidade usa UUID gerado no construtor, timestamps explícitos e `@Version` onde definido na spec. `StoredFile.content` usa:

```java
@Column(nullable = false, columnDefinition = "bytea")
private byte[] content;
```

`MediaAssignment` e `MediaView` oferecem factories distintas para vídeo/material, garantindo o alvo correto antes do banco. Repositories incluem buscas escopadas:

```java
Page<Video> findByTeacherIdAndStatusNot(UUID teacherId, ContentStatus status, Pageable pageable);
Optional<Video> findByIdAndTeacherId(UUID id, UUID teacherId);
List<MediaAssignment> findByRoomIdOrderByPositionAsc(UUID roomId);
@Query("select assignment from MediaAssignment assignment "
		+ "join RoomMembership membership on membership.room.id = assignment.room.id "
		+ "where assignment.material.id = :materialId and membership.student.id = :studentId "
		+ "and membership.status = :status")
List<MediaAssignment> findAccessibleMaterialAssignments(
		UUID materialId, UUID studentId, MembershipStatus status
);
```

Usar `@Query` explícita para travessias por matrícula e alvos polimórficos; não depender de nomes derivados que atravessem associações inexistentes em `Room`.

- [ ] **Step 5: Executar teste focado**

Run: `cd backend && rtk ./mvnw -Dtest=MediaPersistenceTest test`  
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
rtk git add backend/src/main/resources/db/migration/V8__create_media_tables.sql backend/src/main/java/com/ifsc/contacerta/model/MaterialKind.java backend/src/main/java/com/ifsc/contacerta/model/MediaViewType.java backend/src/main/java/com/ifsc/contacerta/entity/Video.java backend/src/main/java/com/ifsc/contacerta/entity/StoredFile.java backend/src/main/java/com/ifsc/contacerta/entity/Material.java backend/src/main/java/com/ifsc/contacerta/entity/MediaAssignment.java backend/src/main/java/com/ifsc/contacerta/entity/MediaView.java backend/src/main/java/com/ifsc/contacerta/repository/VideoRepository.java backend/src/main/java/com/ifsc/contacerta/repository/StoredFileRepository.java backend/src/main/java/com/ifsc/contacerta/repository/MaterialRepository.java backend/src/main/java/com/ifsc/contacerta/repository/MediaAssignmentRepository.java backend/src/main/java/com/ifsc/contacerta/repository/MediaViewRepository.java backend/src/test/java/com/ifsc/contacerta/entity/MediaPersistenceTest.java
rtk git commit -m "feat(backend): add media persistence model"
```

Antes de commitar, confirmar que o staged não contém entidades/repositories não relacionados já existentes.

---

### Task 2: Armazenamento PostgreSQL e validação de upload

**Files:**
- Create: `backend/src/main/java/com/ifsc/contacerta/storage/FileStorage.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/storage/PostgresFileStorage.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/service/MaterialFileValidator.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/material/MaterialFileResponse.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/controller/TeacherMaterialFileController.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/service/MaterialFileValidatorTest.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/controller/TeacherMaterialFileControllerTest.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/storage/PostgresFileStorageTest.java`

**Interfaces:**
- Produces: `StoredFile store(User owner, MaterialFileValidator.ValidatedMaterialFile file, Instant createdAt)`.
- Produces: `Optional<StoredFile> findAuthorized(UUID fileId, CurrentUser user)` será completado no Task 7; neste task, leitura interna por ID/proprietário.
- Produces: `MaterialFileResponse(UUID id, String fileName, String contentType, long sizeBytes)`.

- [ ] **Step 1: Escrever testes vermelhos do validador**

Cobrir assinatura `%PDF`, OLE (`D0 CF 11 E0 A1 B1 1A E1`) e PPTX ZIP contendo `[Content_Types].xml` e `ppt/presentation.xml`. Cobrir 10 MiB exatos, 10 MiB + 1, extensão falsa e MIME incompatível:

```java
assertThatThrownBy(() -> validator.validate(file("a.pdf", "application/pdf", oversized)))
		.isInstanceOfSatisfying(ApiException.class, error -> {
			assertThat(error.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
			assertThat(error.getCode()).isEqualTo("FILE_TOO_LARGE");
		});
```

- [ ] **Step 2: Executar teste e confirmar vermelho**

Run: `cd backend && rtk ./mvnw -Dtest=MaterialFileValidatorTest test`  
Expected: FAIL porque o validador não existe.

- [ ] **Step 3: Implementar validação e storage**

`MaterialFileValidator` define `MAX_BYTES = 10L * 1024 * 1024`, lê os bytes uma vez, normaliza extensão em minúsculas e retorna o nested record `ValidatedMaterialFile(String fileName, String contentType, byte[] content)`. Calcular SHA-256 no adapter. Não confiar apenas em `MultipartFile.getContentType()`.

```java
public interface FileStorage {
	StoredFile store(User owner, MaterialFileValidator.ValidatedMaterialFile file, Instant createdAt);
	Optional<StoredFile> findById(UUID fileId);
}
```

- [ ] **Step 4: Criar teste de controller vermelho**

Com MockMvc standalone, enviar multipart `file`, verificar `201`, `Location: /files/{id}` e corpo `MaterialFileResponse`; validar `413` e `415` via `GlobalExceptionHandler`.

- [ ] **Step 5: Implementar endpoint multipart**

`POST /teacher/materials/files` requer professor ativo, chama o validator/storage em transação e devolve `201`. Arquivo ausente retorna `422 INVALID_MEDIA`.

- [ ] **Step 6: Testar storage com PostgreSQL real**

Persistir bytes contendo `0x00` e `0xFF`, limpar `EntityManager` e confirmar igualdade byte a byte.

Run: `cd backend && rtk ./mvnw -Dtest=MaterialFileValidatorTest,TeacherMaterialFileControllerTest,PostgresFileStorageTest test`  
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
rtk git add backend/src/main/java/com/ifsc/contacerta/storage backend/src/main/java/com/ifsc/contacerta/service/MaterialFileValidator.java backend/src/main/java/com/ifsc/contacerta/dto/material/MaterialFileResponse.java backend/src/main/java/com/ifsc/contacerta/controller/TeacherMaterialFileController.java backend/src/test/java/com/ifsc/contacerta/service/MaterialFileValidatorTest.java backend/src/test/java/com/ifsc/contacerta/controller/TeacherMaterialFileControllerTest.java backend/src/test/java/com/ifsc/contacerta/storage/PostgresFileStorageTest.java
rtk git commit -m "feat(backend): store private material files in postgres"
```

---

### Task 3: Acervo de vídeos do professor

**Files:**
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/video/CreateVideoRequest.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/video/PatchVideoRequest.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/video/TeacherVideoResponse.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/service/ExternalUrlValidator.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/service/VideoService.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/controller/TeacherVideoController.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/service/VideoServiceTest.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/controller/TeacherVideoControllerTest.java`

**Interfaces:**
- Produces: CRUD `/teacher/videos` compatível com `TeacherVideo` do frontend.
- Produces: `ExternalUrlValidator.requireHttps(String, String field)` reutilizado por materiais.

- [ ] **Step 1: Escrever testes vermelhos do service**

Cobrir professor ativo, criação `PUBLISHED`, URL HTTPS, rejeição de HTTP/credenciais, autoria escopada, pesquisa/filtro e archive lógico:

```java
TeacherVideoResponse created = service.create(teacherId,
		new CreateVideoRequest("Juros", null, "Finanças", "https://example.com/video"));
assertThat(created.status()).isEqualTo(ContentStatus.PUBLISHED);
```

No update, uma versão divergente deve lançar `409 VERSION_CONFLICT` antes de mutar a entidade.

- [ ] **Step 2: Executar vermelho**

Run: `cd backend && rtk ./mvnw -Dtest=VideoServiceTest test`  
Expected: FAIL porque DTOs/service não existem.

- [ ] **Step 3: Implementar DTOs, validator e service**

Requests usam Bean Validation para título e comprimentos. `PatchVideoRequest` carrega `long version`; strings não nulas usam `null` como “não alterar”, enquanto `description` e `category` usam `JsonNode`, repetindo o padrão de nullable patch de `UpdateRoomRequest`.

O validator usa `URI`, exige `https`, host não vazio e `getUserInfo() == null`; nunca faz requisição de rede.

- [ ] **Step 4: Escrever e executar contrato HTTP vermelho**

Verificar listagem paginada com `search`, `category`, `page`, `size`, `sort`; criação `201` com `Location`; get/patch/delete; paginação inválida `422`.

- [ ] **Step 5: Implementar controller**

Permitir sort somente em `title`, `createdAt`, `updatedAt`; direções `asc|desc`. DELETE retorna `204`.

- [ ] **Step 6: Rodar testes focados**

Run: `cd backend && rtk ./mvnw -Dtest=VideoServiceTest,TeacherVideoControllerTest test`  
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
rtk git add backend/src/main/java/com/ifsc/contacerta/dto/video backend/src/main/java/com/ifsc/contacerta/service/ExternalUrlValidator.java backend/src/main/java/com/ifsc/contacerta/service/VideoService.java backend/src/main/java/com/ifsc/contacerta/controller/TeacherVideoController.java backend/src/test/java/com/ifsc/contacerta/service/VideoServiceTest.java backend/src/test/java/com/ifsc/contacerta/controller/TeacherVideoControllerTest.java
rtk git commit -m "feat(backend): add teacher video library"
```

---

### Task 4: Acervo de materiais do professor

**Files:**
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/material/CreateMaterialRequest.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/material/PatchMaterialRequest.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/material/TeacherMaterialResponse.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/service/MaterialService.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/controller/TeacherMaterialController.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/service/MaterialServiceTest.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/controller/TeacherMaterialControllerTest.java`

**Interfaces:**
- Consumes: `FileStorage`, `ExternalUrlValidator`.
- Produces: CRUD `/teacher/materials` compatível com `TeacherMaterial`.

- [ ] **Step 1: Escrever testes vermelhos do service**

Cobrir as duas variantes válidas e combinações inválidas:

```java
assertThatThrownBy(() -> service.create(teacherId,
		new CreateMaterialRequest("Apostila", null, null, FILE, null, null)))
		.isInstanceOfSatisfying(ApiException.class,
				error -> assertThat(error.getCode()).isEqualTo("INVALID_MEDIA"));
```

Cobrir arquivo de outro professor como `404 FILE_NOT_FOUND`, update com version, troca de kind coerente, archive e filtro `kind`.

- [ ] **Step 2: Executar vermelho**

Run: `cd backend && rtk ./mvnw -Dtest=MaterialServiceTest test`  
Expected: FAIL porque service/DTOs não existem.

- [ ] **Step 3: Implementar DTOs e service**

`CreateMaterialRequest` reproduz os campos TypeScript: `title`, `description`, `category`, `kind`, `url`, `fileId`. `TeacherMaterialResponse` usa `url` e `MaterialFileResponse file`. Criação é `PUBLISHED`; DELETE arquiva.

- [ ] **Step 4: Escrever contrato HTTP vermelho e implementar controller**

Listagem recebe `search`, `kind`, paginação e sort. POST devolve `201` e `Location`; get/patch/delete seguem o contrato do frontend.

- [ ] **Step 5: Rodar testes focados**

Run: `cd backend && rtk ./mvnw -Dtest=MaterialServiceTest,TeacherMaterialControllerTest test`  
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
rtk git add backend/src/main/java/com/ifsc/contacerta/dto/material backend/src/main/java/com/ifsc/contacerta/service/MaterialService.java backend/src/main/java/com/ifsc/contacerta/controller/TeacherMaterialController.java backend/src/test/java/com/ifsc/contacerta/service/MaterialServiceTest.java backend/src/test/java/com/ifsc/contacerta/controller/TeacherMaterialControllerTest.java
rtk git commit -m "feat(backend): add teacher material library"
```

---

### Task 5: Publicação de mídias em salas

**Files:**
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/media/CreateMediaAssignmentRequest.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/media/PatchMediaAssignmentRequest.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/media/MediaAssignmentResponse.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/service/MediaAssignmentService.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/controller/TeacherMediaAssignmentController.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/service/MediaAssignmentServiceTest.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/controller/TeacherMediaAssignmentControllerTest.java`

**Interfaces:**
- Produces: GET/POST/PATCH/DELETE `/teacher/rooms/{roomId}/media-assignments`.
- Produces: `MediaAssignmentResponse` exatamente igual ao modelo TypeScript.

- [ ] **Step 1: Escrever testes vermelhos de autorização e regras**

Cobrir mídia/sala de professores diferentes, mídia arquivada, `lessonAssignmentId` de outra sala, duplicidade e posição ao fim. PATCH troca lição e reordena posição, exigindo version.

- [ ] **Step 2: Executar vermelho**

Run: `cd backend && rtk ./mvnw -Dtest=MediaAssignmentServiceTest test`  
Expected: FAIL porque service não existe.

- [ ] **Step 3: Implementar service transacional**

Resolver alvo por `MediaViewType`, sempre escopado ao professor. Ao criar:

```java
int position = mediaAssignmentRepository.findMaxPositionByRoomId(roomId) + 1;
MediaAssignment assignment = request.mediaType() == VIDEO
		? MediaAssignment.video(room, video, lessonAssignment, position, now)
		: MediaAssignment.material(room, material, lessonAssignment, position, now);
```

Converter violação da constraint única em `409 MEDIA_ALREADY_ASSIGNED`. Na remoção e reordenação, normalizar posições para `1..n`.

- [ ] **Step 4: Escrever contrato HTTP e implementar controller**

GET retorna array ordenado; POST retorna `201`; PATCH retorna o vínculo; DELETE retorna `204`.

- [ ] **Step 5: Rodar testes focados**

Run: `cd backend && rtk ./mvnw -Dtest=MediaAssignmentServiceTest,TeacherMediaAssignmentControllerTest test`  
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
rtk git add backend/src/main/java/com/ifsc/contacerta/dto/media backend/src/main/java/com/ifsc/contacerta/service/MediaAssignmentService.java backend/src/main/java/com/ifsc/contacerta/controller/TeacherMediaAssignmentController.java backend/src/test/java/com/ifsc/contacerta/service/MediaAssignmentServiceTest.java backend/src/test/java/com/ifsc/contacerta/controller/TeacherMediaAssignmentControllerTest.java
rtk git commit -m "feat(backend): publish media in rooms"
```

---

### Task 6: Coleções do aluno e visualizações

**Files:**
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/media/MediaLessonLinkResponse.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/media/StudentVideoResponse.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/media/StudentMaterialResponse.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/media/MediaCollectionResponse.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/service/StudentMediaService.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/controller/StudentMediaController.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/repository/MediaViewRepository.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/service/StudentMediaServiceTest.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/controller/StudentMediaControllerTest.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/repository/MediaViewRepositoryTest.java`

**Interfaces:**
- Produces: `MediaCollectionResponse<StudentVideoResponse> videos(UUID studentId, UUID roomId)`.
- Produces: `MediaCollectionResponse<StudentMaterialResponse> materials(UUID studentId, UUID roomId)`.
- Produces: `void registerView(UUID studentId, MediaViewType type, UUID mediaId)`.

- [ ] **Step 1: Escrever testes vermelhos das coleções**

Cobrir matrícula ativa, apenas `PUBLISHED`, ordem do vínculo, lesson link, contadores e estado de visualização específico por sala. DTOs devem corresponder exatamente aos modelos TypeScript.

- [ ] **Step 2: Escrever teste PostgreSQL vermelho do upsert**

Adicionar query nativa atômica no repository:

```sql
insert into media_views (..., first_viewed_at, last_viewed_at, view_count)
values (..., :viewedAt, :viewedAt, 1)
on conflict (student_id, room_id, video_id) where video_id is not null do update
set last_viewed_at = excluded.last_viewed_at,
    view_count = media_views.view_count + 1
```

Como existem índices parciais separados, criar métodos nativos específicos para vídeo e material. O teste chama duas vezes e confirma `firstViewedAt` original, `lastViewedAt` novo e `viewCount = 2`.

- [ ] **Step 3: Executar vermelho**

Run: `cd backend && rtk ./mvnw -Dtest=StudentMediaServiceTest,MediaViewRepositoryTest test`  
Expected: FAIL porque DTOs/service/upsert não existem.

- [ ] **Step 4: Implementar service e upsert**

`registerView` resolve todos os vínculos `PUBLISHED` acessíveis ao aluno e executa um upsert por sala. Sem vínculo acessível, retorna `404 MEDIA_NOT_FOUND`. `videos(roomId)` e `materials(roomId)` usam a view da sala consultada para `viewed` e `firstViewedAt`.

- [ ] **Step 5: Escrever contrato HTTP e implementar controller**

GETs retornam `MediaCollectionResponse`; POST view aceita corpo vazio e retorna `204`. `mediaType` inválido retorna `422 INVALID_MEDIA` por conversão controlada, não `500`.

- [ ] **Step 6: Rodar testes focados**

Run: `cd backend && rtk ./mvnw -Dtest=StudentMediaServiceTest,StudentMediaControllerTest,MediaViewRepositoryTest test`  
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
rtk git add backend/src/main/java/com/ifsc/contacerta/dto/media backend/src/main/java/com/ifsc/contacerta/service/StudentMediaService.java backend/src/main/java/com/ifsc/contacerta/controller/StudentMediaController.java backend/src/main/java/com/ifsc/contacerta/repository/MediaViewRepository.java backend/src/test/java/com/ifsc/contacerta/service/StudentMediaServiceTest.java backend/src/test/java/com/ifsc/contacerta/controller/StudentMediaControllerTest.java backend/src/test/java/com/ifsc/contacerta/repository/MediaViewRepositoryTest.java
rtk git commit -m "feat(backend): expose student media and views"
```

---

### Task 7: Download privado, relatório e materiais no detalhe da lição

**Files:**
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/media/MediaViewResponse.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/media/MediaViewsPageResponse.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/service/MediaViewReportService.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/controller/TeacherMediaViewController.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/service/FileDownloadService.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/controller/FileController.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/service/StudentLessonService.java`
- Remove: `backend/src/main/java/com/ifsc/contacerta/dto/studentlesson/StudentMaterialResponse.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/dto/studentlesson/StudentLessonDetailResponse.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/controller/FileControllerTest.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/service/FileDownloadServiceTest.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/service/MediaViewReportServiceTest.java`
- Modify: `backend/src/test/java/com/ifsc/contacerta/service/StudentLessonServiceTest.java`

**Interfaces:**
- Produces: `MediaViewsPageResponse views(UUID teacherId, MediaViewType type, UUID mediaId, Pageable pageable)`.
- Produces: `FileDownloadService.AuthorizedFile download(CurrentUser user, UUID fileId)`, nested record com `fileName`, `contentType`, `byte[] content` e `boolean inline`.
- Consumes: `StudentMaterialResponse` real do Task 6 no detalhe da lição.

- [ ] **Step 1: Escrever testes vermelhos do download**

Cobrir aluno com matrícula ativa, aluno de outra sala, professor proprietário, outro professor, PDF inline, PPT/PPTX attachment e bytes idênticos:

```java
mockMvc.perform(get("/files/{fileId}/download", fileId))
		.andExpect(status().isOk())
		.andExpect(header().string("Content-Disposition", startsWith("inline;")))
		.andExpect(content().bytes(originalBytes));
```

- [ ] **Step 2: Implementar autorização e controller de download**

`FileDownloadService` carrega metadados/bytes e verifica propriedade do professor ou material publicado em sala com matrícula ativa. Retorna `404 FILE_NOT_FOUND` fora do escopo. `FileController` usa `ResponseEntity<byte[]>`, `Content-Length`, MIME canônico e `ContentDisposition` do Spring para escapar o nome.

- [ ] **Step 3: Escrever testes vermelhos do relatório**

Cobrir autoria, paginação, aluno duplicado em duas salas aparecendo uma vez, menor `firstViewedAt`, maior `lastViewedAt` e `totalViewers` distinto.

- [ ] **Step 4: Implementar projeção/repository e endpoint de relatório**

Usar query de projeção agrupada por aluno, limitada às salas do professor. O DTO contém exatamente `studentId`, `fullName`, `registrationNumber`, `firstViewedAt`, `lastViewedAt`; `MediaViewsPageResponse` repete `content`, `page`, `size`, `totalElements`, `totalPages` e acrescenta `totalViewers`.

- [ ] **Step 5: Escrever teste vermelho do detalhe da lição**

No `StudentLessonServiceTest`, criar material publicado vinculado à atribuição e confirmar:

```java
assertThat(service.detail(studentId, roomId, lessonId).materials())
		.singleElement()
		.extracting(StudentMaterialResponse::id)
		.isEqualTo(materialId);
```

- [ ] **Step 6: Integrar materiais reais ao detalhe**

Substituir o DTO provisório de `studentlesson` pelo DTO canônico de `dto.media`. Consultar somente vínculos `MATERIAL` da mesma `lesson_assignment`, mídia `PUBLISHED` e matrícula ativa já validada.

- [ ] **Step 7: Rodar testes focados**

Run: `cd backend && rtk ./mvnw -Dtest=FileControllerTest,FileDownloadServiceTest,MediaViewReportServiceTest,StudentLessonServiceTest test`  
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
rtk git add backend/src/main/java/com/ifsc/contacerta/dto/media backend/src/main/java/com/ifsc/contacerta/service/MediaViewReportService.java backend/src/main/java/com/ifsc/contacerta/service/FileDownloadService.java backend/src/main/java/com/ifsc/contacerta/service/StudentLessonService.java backend/src/main/java/com/ifsc/contacerta/controller/TeacherMediaViewController.java backend/src/main/java/com/ifsc/contacerta/controller/FileController.java backend/src/main/java/com/ifsc/contacerta/dto/studentlesson backend/src/test/java/com/ifsc/contacerta/controller/FileControllerTest.java backend/src/test/java/com/ifsc/contacerta/service/FileDownloadServiceTest.java backend/src/test/java/com/ifsc/contacerta/service/MediaViewReportServiceTest.java backend/src/test/java/com/ifsc/contacerta/service/StudentLessonServiceTest.java
rtk git commit -m "feat(backend): secure media downloads and reporting"
```

---

### Task 8: Verificação integrada e hardening do módulo

**Files:**
- Create: `backend/src/test/java/com/ifsc/contacerta/controller/MediaFlowIntegrationTest.java`
- Modify: `docs/superpowers/specs/2026-08-28-backend-media-design.md` somente se a implementação revelar uma correção normativa necessária.

**Interfaces:**
- Verifies: todos os endpoints da spec de mídias como uma única fatia vertical.

- [ ] **Step 1: Escrever fluxo HTTP integrado**

Com SpringBootTest, MockMvc e PostgreSQL Testcontainers, executar: professor envia PDF → cria material → publica na sala/lição → aluno lista material → aluno registra view duas vezes → aluno baixa bytes → professor consulta visualizações → material aparece no detalhe da lição.

Adicionar matriz negativa com outro professor, aluno sem matrícula, mídia arquivada, version conflitante, vínculo duplicado, arquivo >10 MiB e assinatura falsa.

- [ ] **Step 2: Executar fluxo integrado**

Run: `cd backend && rtk ./mvnw -Dtest=MediaFlowIntegrationTest test`  
Expected: PASS. Se falhar, corrigir a menor unidade responsável e rodar também seu teste focado.

- [ ] **Step 3: Executar inspeções estáticas do diff**

Run: `rtk git diff --check`  
Expected: sem saída.

Run: `rtk rg -n "TODO|TBD|System\.out|printStackTrace" backend/src/main backend/src/test`  
Expected: nenhuma ocorrência nova no módulo.

- [ ] **Step 4: Executar suíte completa**

Run: `cd backend && rtk ./mvnw verify`  
Expected: `BUILD SUCCESS`, zero failures e zero errors. O comando precisa de Docker para Testcontainers.

- [ ] **Step 5: Conferir isolamento do commit**

Run: `rtk git status --short`  
Expected: somente arquivos do módulo de mídia, além das alterações preexistentes explicitamente preservadas.

- [ ] **Step 6: Commit final de integração**

```bash
rtk git add backend/src/test/java/com/ifsc/contacerta/controller/MediaFlowIntegrationTest.java
rtk git commit -m "test(backend): verify complete media flow"
```

Se alguma correção de produção for necessária neste task, incluí-la em commit `fix(backend): harden media flow` separado do teste.
