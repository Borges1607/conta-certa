# Backend Media Design

**Status:** aprovado para planejamento  
**Data:** 2026-08-28  
**Referências normativas:** `docs/backend-spec.md` §§ 2.5, 3.3, 4.4, 5.2, 5.3, 7.4, 9–11; `docs/frontend-integration-spec.md` §§ 6.4 e 7.4.

## Objetivo e escopo

Implementar a fatia vertical completa de vídeos e materiais: acervo do professor, upload privado no PostgreSQL, publicação em salas, vínculo opcional com lições, consumo pelo aluno, download autorizado e acompanhamento de visualizações.

O incremento inclui exatamente estes contratos já consumidos pelo frontend:

```text
GET    /teacher/videos
POST   /teacher/videos
GET    /teacher/videos/{videoId}
PATCH  /teacher/videos/{videoId}
DELETE /teacher/videos/{videoId}

GET    /teacher/materials
POST   /teacher/materials
GET    /teacher/materials/{materialId}
PATCH  /teacher/materials/{materialId}
DELETE /teacher/materials/{materialId}
POST   /teacher/materials/files

GET    /teacher/rooms/{roomId}/media-assignments
POST   /teacher/rooms/{roomId}/media-assignments
PATCH  /teacher/rooms/{roomId}/media-assignments/{assignmentId}
DELETE /teacher/rooms/{roomId}/media-assignments/{assignmentId}
GET    /teacher/media/{mediaType}/{mediaId}/views

GET    /student/rooms/{roomId}/videos
GET    /student/rooms/{roomId}/materials
POST   /student/media/{mediaType}/{mediaId}/view
GET    /files/{fileId}/download
```

O detalhe da lição do aluno também passa a preencher `materials` com materiais publicados vinculados àquela lição.

## Arquitetura

O módulo segue as camadas existentes: controllers tratam transporte, services concentram autorização e transações, repositories encapsulam consultas e PostgreSQL/Flyway armazenam o estado. Entidades JPA nunca são expostas.

Vídeo e material são agregados separados porque possuem ciclos e campos diferentes. Publicação e visualização usam referências polimórficas explícitas (`media_type` + `video_id`/`material_id`) validadas por constraints e pelo serviço. Não haverá uma tabela genérica `media`.

Arquivos são acessados por uma interface interna `FileStorage` e uma implementação `PostgresFileStorage`. O conteúdo permanece no PostgreSQL em `bytea`; a interface evita acoplar controllers e casos de uso ao mecanismo físico.

## Persistência

### `videos`

- `id`, `teacher_id`, `title`, `description`, `category`, `url`, `status`;
- `created_at`, `updated_at`, `version`;
- `url` deve usar HTTPS;
- `status`: `DRAFT`, `PUBLISHED` ou `ARCHIVED`.

### `stored_files`

- `id`, `owner_teacher_id`, `file_name`, `content_type`, `size_bytes`, `sha256`, `content` (`bytea`), `created_at`;
- tamanho máximo de 10 MiB para PDF/PPT/PPTX;
- imagens PNG/JPEG/WebP, quando aceitas por fluxos futuros, têm máximo de 5 MiB;
- este incremento de materiais aceita somente PDF, PPT e PPTX;
- arquivo enviado e ainda não associado pertence exclusivamente ao professor que o enviou;
- um arquivo associado não pode ser reutilizado por outro professor;
- arquivos órfãos podem ser removidos posteriormente por rotina de manutenção; essa rotina não faz parte deste incremento.

### `materials`

- `id`, `teacher_id`, `title`, `description`, `category`, `kind`, `external_url`, `file_id`, `status`;
- `created_at`, `updated_at`, `version`;
- `kind = EXTERNAL_LINK` exige `external_url` HTTPS e `file_id` nulo;
- `kind = FILE` exige `file_id` e `external_url` nulo;
- `file_id` é único, impedindo que o mesmo upload componha materiais diferentes.

### `media_assignments`

- `id`, `room_id`, `media_type`, `video_id`, `material_id`, `lesson_assignment_id`, `position`;
- `created_at`, `version`;
- exatamente uma entre `video_id` e `material_id` deve estar preenchida e deve corresponder a `media_type`;
- a mídia e a sala devem pertencer ao mesmo professor;
- `lesson_assignment_id`, quando presente, deve pertencer à mesma sala;
- uma mesma mídia só pode ser publicada uma vez por sala;
- novas posições são atribuídas ao fim da lista; alteração de posição reordena os demais vínculos de forma contígua;
- remover o vínculo retira a mídia da sala, preservando mídia e histórico de visualizações.

### `media_views`

- `id`, `student_id`, `room_id`, `media_type`, `video_id`, `material_id`;
- `first_viewed_at`, `last_viewed_at`, `view_count`;
- unicidade por aluno, sala, tipo e mídia;
- cada abertura atualiza `last_viewed_at` e incrementa `view_count`, preservando `first_viewed_at`;
- exatamente uma FK de mídia deve estar preenchida e corresponder ao tipo.

Índices cobrem professor/status/título, sala/posição, lição, mídia atribuída, aluno/sala e relatórios de visualização.

## Regras de autorização

Todos os casos de uso exigem conta ativa e perfil compatível.

- Professor só gerencia mídias próprias e salas próprias de sua instituição.
- Professor só associa mídia própria a sala própria e a uma atribuição daquela sala.
- Professor só consulta visualizações de mídia própria; o relatório agrega apenas salas próprias.
- Aluno só lista mídia vinculada à sala em que possui matrícula `ACTIVE`.
- Aluno só registra visualização de mídia publicada e atualmente vinculada a uma sala com matrícula ativa. Como a rota não contém `roomId`, o corpo vazio permanece e o backend resolve os vínculos acessíveis; se houver mais de um, registra a abertura em cada sala ativa na qual a mídia está publicada. Isso preserva a chave normativa por sala e o contrato atual do frontend.
- Download por aluno exige que o arquivo pertença a material publicado em pelo menos uma sala com matrícula ativa.
- Download por professor exige propriedade do arquivo ou do material correspondente.
- Recursos fora do escopo, inclusive mídia pertencente a outro professor, retornam `404`, evitando enumeração. Perfil autenticado incompatível retorna `403`.

## Contratos do professor

Listagens são paginadas, com `page=0`, `size=20`, máximo 100 e ordenação permitida por `title`, `createdAt` ou `updatedAt`. `search` pesquisa título sem diferenciar maiúsculas e minúsculas. Vídeos aceitam filtro `category`; materiais aceitam `kind`.

Os DTOs de vídeo seguem `TeacherVideo`: `id`, `title`, `description`, `category`, `url`, `status`, timestamps e `version`.

Os DTOs de material seguem `TeacherMaterial`: metadados comuns, `kind`, `url` para link, `file` para arquivo, `status`, timestamps e `version`. `MaterialFile` contém `id`, `fileName`, `contentType` e `sizeBytes`.

Criação produz conteúdo `PUBLISHED`, como exigem os contratos atuais do frontend e a ausência de um endpoint separado de publicação. `DELETE` sempre arquiva logicamente. Mídia `ARCHIVED` não aceita novos vínculos, deixa de aparecer nas listagens normais do professor e do aluno, mas seus vínculos e histórico permanecem preservados no banco.

`PATCH` exige `version`; divergência retorna `409 VERSION_CONFLICT`. A troca entre `FILE` e `EXTERNAL_LINK` é permitida enquanto o material não estiver `ARCHIVED`, desde que o novo par `url`/`fileId` seja válido. Vínculos existentes passam a expor a versão atualizada do material.

O upload multipart usa o campo `file` e retorna `201` com `MaterialFile`. O upload apenas armazena o arquivo; `POST /teacher/materials` cria o material referenciando `fileId`.

`MediaAssignment` segue o frontend: `id`, `roomId`, `mediaType`, `mediaId`, `title`, `lessonAssignmentId`, `lessonTitle`, `position`, `createdAt`, `version`.

O relatório de visualizações é paginado e retorna `content`, metadados padrão de página e `totalViewers`. Cada item contém `studentId`, `fullName`, `registrationNumber`, `firstViewedAt` e `lastViewedAt`. `totalViewers` conta alunos distintos em todas as salas próprias onde a mídia foi publicada.

## Contratos do aluno

As listas retornam `MediaCollection<T>` com `items`, `viewedCount` e `totalCount`, ordenadas pela posição do vínculo. Apenas mídia `PUBLISHED` é exposta.

`StudentVideo` contém `id`, `title`, `description`, `url`, `durationMinutes` nulo nesta versão, vínculo opcional de lição, `viewed` e `firstViewedAt`.

`StudentMaterial` contém `id`, `title`, `description`, `kind`, `externalUrl` ou `fileId`, `fileName`, `fileSizeBytes` e `contentType`, vínculo opcional de lição, `viewed` e `firstViewedAt`. O DTO do professor usa o campo `url`; o DTO do aluno usa `externalUrl`, exatamente como os modelos TypeScript.

O vínculo de lição usa `lessonId` e `lessonTitle`, derivados da `lesson_assignment`. Mídia sem vínculo de lição retorna `lesson: null`.

O detalhe de lição retorna somente materiais vinculados à atribuição correspondente; vídeos continuam na coleção própria e não são incorporados ao detalhe.

`POST /student/media/{mediaType}/{mediaId}/view` retorna `204 No Content`. Repetições são seguras: não criam linhas duplicadas e apenas atualizam último acesso e contador.

`GET /files/{fileId}/download` devolve bytes idênticos ao upload, `Content-Type` validado, `Content-Length` e `Content-Disposition`. PDF usa `inline`; PPT/PPTX usa `attachment`. O nome enviado é normalizado para impedir injeção em headers.

## Validação e segurança

- URLs externas aceitam apenas HTTPS e são validadas sintaticamente; esquemas executáveis e credenciais embutidas são rejeitados.
- O backend não busca URLs externas, evitando SSRF.
- Upload valida tamanho, extensão, MIME declarado e assinatura real do arquivo.
- Tipos aceitos: `application/pdf`, PPT legado OLE e PPTX ZIP/OOXML, com extensões correspondentes.
- PDF/PPT/PPTX acima de 10 MiB retorna `413 FILE_TOO_LARGE`.
- Tipo, extensão ou assinatura incompatível retorna `415 UNSUPPORTED_MEDIA_TYPE`.
- Campos inválidos retornam `422 INVALID_MEDIA`.
- Duplicidade de vínculo retorna `409 MEDIA_ALREADY_ASSIGNED`.
- Mídia e arquivo invisíveis retornam `404 MEDIA_NOT_FOUND` ou `404 FILE_NOT_FOUND`.
- Paginação inválida retorna `422 VALIDATION_ERROR`.
- Conteúdo binário, URLs privadas e dados pessoais não entram em logs.

## Concorrência e transações

- Vídeos, materiais e vínculos usam `@Version`.
- A constraint única de vínculo garante publicação única por sala mesmo sob concorrência.
- O registro de visualização usa upsert atômico PostgreSQL para preservar a primeira data e incrementar uma vez por requisição.
- Reordenação de vínculos bloqueia a sala ou os vínculos da sala antes de recalcular posições.
- Upload e criação do material são transações separadas; falha de criação não perde o arquivo enviado.
- Download é somente leitura e não materializa entidades relacionadas fora do necessário para autorização.

## Testes e critérios de aceite

Testes unitários cobrem validação de URL, arquivos, transições de status, autorização e mapeamento dos contratos.

Testes PostgreSQL/Testcontainers cobrem migrations, constraints polimórficas, unicidade de vínculo, upsert concorrente de visualização e conteúdo `bytea`.

Testes HTTP cobrem:

- contratos completos do professor e aluno;
- paginação, pesquisa, filtros e ordenação;
- optimistic locking;
- isolamento entre professores, instituições, salas e alunos;
- upload válido, 10 MiB, excesso de tamanho e MIME/assinatura incompatíveis;
- download byte a byte, headers inline/attachment e acesso negado por `404`;
- visualização repetida com primeira data estável, última data atualizada e contador incrementado;
- mídia vinculada a mais de uma sala ativa registra a abertura em cada sala acessível, dado que a rota normativa de visualização não recebe `roomId`;
- materiais publicados no detalhe da lição;
- mídia rascunho ou arquivada ausente para o aluno.

O módulo está aceito quando todos os endpoints acima correspondem aos modelos TypeScript existentes, as migrations validam em PostgreSQL vazio e `./mvnw verify` passa integralmente.

## Fora do escopo

- S3, filesystem externo ou CDN;
- upload de vídeo;
- transcodificação, thumbnails ou inspeção de URLs remotas;
- imagens como material neste incremento;
- limpeza automática de uploads órfãos;
- range requests e cache distribuído;
- antivírus externo.
