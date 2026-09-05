# Download autorizado Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Entregar GET /files/{fileId}/download conforme a proposta aprovada.
**Architecture:** Controller → serviço → FileStorage → consultas autorizadas no PostgreSQL. Mapper cria valor interno com bytes desconectados da entidade. Componente HTTP normaliza nomes e cria headers.
**Tech Stack:** Java 21, Spring Boot, JPA, Maven, JUnit 5, PostgreSQL/Testcontainers.
**Spec:** docs/superpowers/specs/2026-09-04-backend-file-download-design.md

## Global Constraints
- Nenhuma migration é prevista.
- Não manter uma transação aberta durante o envio da resposta ao cliente.
- Download é somente leitura: não cria nem incrementa MediaView, não concede XP e não modifica conteúdo ou timestamps.
- UUID inválido usa ApiException BAD_REQUEST apenas nesta rota.
- Sem mudanças no upload, na autenticação ou na publicação existente.

## Task 1: Persistência autorizada
Files: backend/src/main/java/com/ifsc/contacerta/{repository/StoredFileRepository.java,storage/FileStorage.java,storage/PostgresFileStorage.java}; teste backend/src/test/java/com/ifsc/contacerta/storage/FileDownloadStorageTest.java.
Interfaces: findDownloadableByTeacherId(UUID fileId, UUID teacherId) e findDownloadableByStudentId(UUID fileId, UUID studentId), retornam Optional<StoredFile> em FileStorage e no repositório.
- [x] Escrever testes PostgreSQL de propriedade, matrícula, publicação, associação consistente, várias salas e revogação.
```java
assertThat(storage.findDownloadableByStudentId(file.getId(), student.getId())).isEmpty();
entityManager.persist(new RoomMembership(room, student));
entityManager.flush();
assertThat(storage.findDownloadableByStudentId(file.getId(), student.getId())).isPresent();
```
- [x] Executar ./mvnw -Dtest=FileDownloadStorageTest test e registrar RED.
- [x] Implementar professor com proprietário OU EXISTS material do professor; aluno com EXISTS material FILE/PUBLISHED, associação MATERIAL, professor da sala igual ao do material e matrícula ACTIVE do aluno na sala. Usar parâmetros UUID e enum literais JPQL tipados.
- [x] Executar teste e registrar GREEN; revisar consultas contra matriz da spec.

## Task 2: Serviço e transporte
Files: backend/src/main/java/com/ifsc/contacerta/{model/FileDownload.java,mapper/FileDownloadMapper.java,service/FileDownloadService.java,controller/FileDownloadController.java,mapper/FileDownloadResponseMapper.java}; testes em service/FileDownloadServiceTest.java e controller/FileDownloadControllerTest.java.
Interfaces: FileDownloadService.get(UUID userId, UUID fileId) → FileDownload(String fileName, String contentType, long sizeBytes, byte[] content); FileDownloadMapper.toDownload(StoredFile) → FileDownload; FileDownloadResponseMapper.toResponse(FileDownload) → ResponseEntity<byte[]>.
- [x] Escrever testes de conta inexistente/inativa/papel inválido antes do storage, rotas por papel, erro uniforme e isolamento dos bytes.
```java
assertThatThrownBy(() -> service.get(userId, fileId)).isInstanceOfSatisfying(ApiException.class,
    ex -> assertThat(ex.getCode()).isEqualTo("FILE_NOT_FOUND"));
```
- [x] Registrar RED; implementar serviço readOnly com USER_NOT_FOUND, ACCOUNT_INACTIVE, FILE_ACCESS_FORBIDDEN, FILE_NOT_FOUND nesta ordem.
- [x] Escrever testes HTTP para três MIMEs, nomes Unicode/caminhos/controles/vazios, UUID malformado, Range completo e erros.
```java
mockMvc.perform(get("/files/{fileId}/download", fileId).header("Range", "bytes=0-1"))
    .andExpect(status().isOk()).andExpect(content().bytes(bytes))
    .andExpect(header().string("Cache-Control", "private, no-store"));
```
- [x] Registrar RED; implementar headers Content-Disposition via ContentDisposition UTF-8, Content-Type, Content-Length, Cache-Control e nosniff; normalizar separadores e remover controles; fallback arquivo.ext.
- [x] Executar os testes para GREEN.

## Task 3: Integração HTTP e regressão
Files: backend/src/test/java/com/ifsc/contacerta/controller/FileDownloadIntegrationTest.java.
- [x] Testar upload → download byte a byte para PDF/PPT/PPTX e PDF válido de 10 MiB, identidade JWT de professor/aluno, sem token/inválido/inativo/admin e ausência de efeito em MediaView.
```java
mockMvc.perform(get("/files/{fileId}/download", fileId))
    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
```
- [x] Executar testes focados e ./mvnw verify com Docker real.
- [x] Revisar diff, matriz da spec, git diff --check; registrar resultado e commit.

## Resultado

Implementação e revisão concluídas. `./mvnw verify`: 401 testes, zero falhas/erros/skips, BUILD SUCCESS. Revisão final sem achados.
