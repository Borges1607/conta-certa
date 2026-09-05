# Download autorizado de arquivos — desenho do backend

Status: proposta para revisão
Data: 2026-09-04

## 1. Objetivo e escopo

Implementar `GET /files/{fileId}/download` para recuperar arquivos PDF, PPT e PPTX já enviados pelo fluxo `POST /teacher/materials/files`. A entrega é exclusivamente de backend.

Referências: `docs/superpowers/specs/2026-08-28-backend-media-design.md`, especialmente armazenamento, autorização e download; `docs/backend-spec.md`, seções 4, 5 e 7.

Esta spec detalha o download que permanece pendente no módulo de mídia. Preserva a decisão específica desse módulo de armazenar conteúdo privado em PostgreSQL (`stored_files.content`, tipo `bytea`), apesar da alternativa de filesystem/S3 descrita na spec geral. Não altera upload, publicação de materiais ou contratos dos dashboards.

## 2. Abordagem escolhida

O backend entrega o corpo binário completo a partir do armazenamento atual. Cada arquivo aceito pelo upload tem no máximo 10 MiB. A implementação pode materializar um arquivo autorizado em memória; não deve carregar coleções de materiais, salas ou matrículas para decidir acesso.

Alternativas consideradas:

- **Resposta binária completa com PostgreSQL — escolhida:** reaproveita `FileStorage`, `PostgresFileStorage` e `StoredFile`, mantendo pequeno o incremento. O consumo de memória cresce com o tamanho dos arquivos e a quantidade de downloads concorrentes.
- **Streaming direto do PostgreSQL:** exige mudar a leitura atual, que expõe `byte[]`, e coordenar conexão/transação durante a transferência. Fica para um incremento motivado por volume medido.
- **URL temporária em armazenamento de objetos:** exige outro adaptador e migração do armazenamento. Fica fora desta entrega.

Não chamar de streaming do banco uma resposta que primeiro materializa todo o `bytea`. Não manter uma transação aberta durante o envio da resposta ao cliente.

## 3. Contrato HTTP

```text
GET /files/{fileId}/download
Authorization: Bearer <accessToken>
```

`fileId` é um UUID. A rota não recebe corpo, `roomId`, `teacherId`, `studentId`, nome de arquivo ou URL de destino. A identidade vem exclusivamente de `CurrentUser`.

Sucesso retorna `200 OK` com os bytes originais, sem envelope JSON ou codificação Base64.

| Tipo | Content-Type | Content-Disposition |
| --- | --- | --- |
| PDF | `application/pdf` | `inline` com nome seguro |
| PPT | `application/vnd.ms-powerpoint` | `attachment` com nome seguro |
| PPTX | `application/vnd.openxmlformats-officedocument.presentationml.presentation` | `attachment` com nome seguro |

Headers adicionais:

- `Content-Length`: quantidade exata de bytes enviados, correspondente a `sizeBytes` do upload válido.
- `Cache-Control: private, no-store`: o resultado exige autorização em cada nova requisição.
- `X-Content-Type-Options: nosniff`.

O MIME vem dos metadados validados e persistidos no upload. Não inferir o tipo a partir de um parâmetro fornecido na requisição de download.

O nome deve preservar caracteres Unicode válidos, inclusive acentos, e ser serializado com o construtor de `ContentDisposition` do Spring e codificação UTF-8. Não concatenar nomes diretamente no header. Antes da serialização, remover componentes de caminho e caracteres de controle, incluindo CR, LF, NUL e DEL; aparar espaços externos. Se o resultado ficar vazio, usar `arquivo` com a extensão correspondente ao MIME. Essa normalização de saída protege também registros antigos sem modificar o arquivo armazenado.

Range requests não são implementadas: se `Range` estiver presente, retornar a representação completa com `200`, sem `Content-Range` e sem anunciar `Accept-Ranges: bytes`. Não implementar ETag, resposta `304` ou negociação de download parcial neste incremento.

## 4. Autorização

O serviço carrega o usuário pelo ID autenticado, exige conta `ACTIVE` e aceita somente `TEACHER` ou `STUDENT`. A instituição, isoladamente, nunca concede acesso. O perfil `ADMIN` não recebe acesso universal a arquivos nesta entrega.

### Professor

Permitir quando o professor for o proprietário do arquivo (`ownerTeacher.id`) ou do material que atualmente referencia esse arquivo (`material.teacher.id`). O fluxo existente de associação já exige que esses proprietários coincidam; a consulta mantém explícita a regra de propriedade da spec de mídia.

O professor pode recuperar seu upload ainda sem material associado e arquivos de materiais próprios em qualquer estado (`DRAFT`, `PUBLISHED`, `ARCHIVED`). Associação em sala ou existência de alunos não é requisito para o proprietário.

Outro professor, mesmo na mesma instituição, recebe `404 FILE_NOT_FOUND`.

### Aluno

Permitir somente se existir ao menos um caminho atual que satisfaça todos os requisitos:

1. Material do tipo `FILE` referencia exatamente o `fileId` solicitado.
2. Material está em `PUBLISHED`.
3. Existe `MediaAssignment` de tipo `MATERIAL` ligando esse material a uma sala.
4. Existe `RoomMembership` do aluno autenticado nessa mesma sala com status `ACTIVE`.

O vínculo de mídia deve respeitar a propriedade da sala pelo professor do material, conforme a invariável já exigida na publicação. Uma associação inconsistente não deve conceder acesso.

Vínculos opcionais com lições não acrescentam regras de prazo, tentativas, estado de lição ou estado de atribuição de lição ao download. O acesso segue a publicação do material e a matrícula, como nas regras de consumo de mídia.

Salas arquivadas continuam elegíveis enquanto a matrícula estiver `ACTIVE`, coerente com a regra atual de acesso a mídia, que não filtra `archivedAt`. Arquivar um material impede o download pelo aluno; arquivar apenas a sala não revoga essa permissão.

Quando houver várias salas, basta uma satisfazer todos os requisitos. Matrícula ativa em uma sala diferente daquela que contém o material não concede acesso. Matrícula removida, ausência de vínculo, material rascunho/arquivado e upload órfão retornam `404 FILE_NOT_FOUND`.

Trocar o arquivo de um material, transformá-lo em link externo ou remover sua última associação acessível revoga o acesso do aluno ao arquivo anterior em novas requisições. O proprietário continua podendo recuperar seu upload.

## 5. Erros

Erros da aplicação usam `ApiException`, o handler global e o envelope `application/problem+json` existente, com `code`, `timestamp` e `traceId`.

| Situação | HTTP | Código |
| --- | --- | --- |
| Token ausente, inválido, expirado ou sessão inválida | 401 | `INVALID_ACCESS_TOKEN` |
| UUID malformado, após autenticação válida | 400 | `BAD_REQUEST` |
| Usuário não encontrado pelo serviço | 404 | `USER_NOT_FOUND` |
| Usuário encontrado com conta não ativa | 403 | `ACCOUNT_INACTIVE` |
| Conta ativa com papel incompatível, incluindo ADMIN | 403 | `FILE_ACCESS_FORBIDDEN` |
| Arquivo inexistente ou fora do escopo | 404 | `FILE_NOT_FOUND` |

Ordem do serviço: localizar usuário, validar conta ativa, validar papel, consultar arquivo no escopo autorizado. Não consultar conteúdo para usuário rejeitado nessas validações.

A autenticação já rejeita sessões de usuários inativos com `401 INVALID_ACCESS_TOKEN`. Preservar esse comportamento HTTP; `403 ACCOUNT_INACTIVE` é a proteção do serviço quando ele recebe um usuário inativo, inclusive em chamadas internas. Não alterar autenticação global para forçar outro status.

Para UUID malformado, produzir o envelope de erro da rota sem alterar o comportamento de validação dos demais controllers. Arquivo inexistente e acesso negado devem ter o mesmo código e mensagem (`File was not found.`), sem nome, MIME, tamanho ou indicação de existência do arquivo.

## 6. Componentes e persistência

Fluxo: `FileDownloadController` → `FileDownloadService` → `FileStorage` → `PostgresFileStorage` → `StoredFileRepository`. O serviço também usa `UserRepository` para validar o usuário.

- **FileDownloadController:** resolve `CurrentUser` e `fileId`, delega ao serviço e devolve a resposta binária com headers. Não acessa repositórios.
- **FileDownloadService:** orquestra validação e leitura autorizada dentro de `@Transactional(readOnly = true)`; retorna um valor interno com nome, MIME, tamanho e conteúdo materializado, nunca a entidade JPA como contrato HTTP.
- **FileDownloadMapper:** converte `StoredFile` no valor interno de download e mantém essa responsabilidade fora do controller. O nome seguro e a construção dos headers ficam em um componente de transporte dedicado caso precisem de lógica além da composição direta da resposta.
- **FileStorage:** recebe operações explícitas de leitura autorizada por professor e por aluno; preserva os métodos usados no upload e na associação de materiais.
- **StoredFileRepository:** implementa consultas dedicadas e parametrizadas por `fileId` e ID do usuário. Não usar `Specification` para esta busca direta.

Interfaces previstas:

```text
FileDownloadService.get(UUID userId, UUID fileId) -> FileDownload
FileStorage.findDownloadableByTeacherId(UUID fileId, UUID teacherId) -> Optional<StoredFile>
FileStorage.findDownloadableByStudentId(UUID fileId, UUID studentId) -> Optional<StoredFile>
```

`FileDownload` é um valor interno com `fileName`, `contentType`, `sizeBytes` e `byte[] content`. Não serializá-lo como JSON. O array não deve expor estado mutável compartilhado da entidade; evitar cópias redundantes durante a composição da resposta.

A consulta do aluno deve selecionar o arquivo por ID usando uma condição de existência sobre material, associação e matrícula. A consulta do professor deve aplicar a propriedade na própria busca. Não buscar o binário por ID sem escopo para depois verificar permissão em Java; não reaproveitar consultas que retornam listas completas de associações para fazer essa validação em memória.

Usar `exists` em subconsulta para que um material em várias salas não duplique o arquivo retornado. A condição `material.status = PUBLISHED` precisa estar explícita: as consultas atuais de associações usadas pelo registro de visualização não garantem esse filtro e não bastam para autorizar download.

Nenhuma migration é prevista. Reaproveitar as tabelas, FKs e índices atuais do módulo de mídia. Não incluir alteração de armazenamento físico ou índice especulativo nesta entrega.

## 7. Consistência e efeitos colaterais

Cada requisição decide acesso a partir do estado visível na consulta autorizada. Não há promessa de revogar bytes já carregados caso uma matrícula ou publicação mude durante a transferência. Requisições posteriores à revogação concluída devem refletir o novo estado.

Download é somente leitura: não cria nem incrementa `MediaView`, não concede XP e não modifica conteúdo ou timestamps. O registro de visualização continua sendo responsabilidade de `POST /student/media/{mediaType}/{mediaId}/view`.

Não registrar conteúdo binário, nomes de arquivos ou dados pessoais em logs de aplicação. Falhas de banco não devem ser convertidas em sucesso ou em arquivo vazio; seguem o tratamento de falhas de infraestrutura existente.

## 8. Testes e critérios de aceite

### Serviço e transporte

- Usuário ausente, conta `PENDING`/`INACTIVE` e papel `ADMIN` são rejeitados antes da leitura do arquivo.
- Professor e aluno usam seus próprios IDs autenticados nas leituras autorizadas.
- Ausência de resultado retorna `404 FILE_NOT_FOUND`.
- Controller retorna bytes idênticos e todos os headers normativos para PDF, PPT e PPTX.
- Nomes com acentos, aspas, caminhos e caracteres de controle produzem um único header válido, sem injeção ou perda de Unicode válido.
- UUID malformado retorna `400 BAD_REQUEST` no envelope da API.
- Requisição autenticada com `Range` retorna corpo completo e `200`.

### PostgreSQL e segurança HTTP

- Professor baixa upload próprio não associado e arquivo de material próprio arquivado.
- Outro professor na mesma instituição e professor de outra instituição recebem `404`, assim como para UUID inexistente.
- Aluno com matrícula ativa recebe arquivo de material publicado associado à sua sala.
- Matrícula ativa em sala sem esse material não autoriza o acesso.
- Aluno sem matrícula ou com matrícula `REMOVED` recebe `404`.
- Material `DRAFT`/`ARCHIVED`, arquivo órfão ou ausência de associação recebem `404` para o aluno.
- Material em duas salas: uma matrícula válida basta; várias matrículas válidas não causam resultado duplicado.
- Sala arquivada com matrícula ativa preserva acesso; material arquivado o revoga.
- Troca de arquivo, conversão para link externo e remoção da última associação acessível revogam acesso ao arquivo anterior.
- Vínculo opcional com lição não muda as condições de acesso ao material.
- Token ausente/inválido retorna `401`; token válido de ADMIN retorna `403 FILE_ACCESS_FORBIDDEN`; token de conta inativa preserva `401 INVALID_ACCESS_TOKEN`.
- Fluxo upload → download preserva conteúdo byte a byte e tamanho, incluindo um arquivo válido de 10 MiB.
- Downloads repetidos não modificam visualizações nem conteúdo persistido.

Executar testes novos, regressão do módulo de mídia e autenticação e `./mvnw verify`. A entrega estará aceita quando o contrato binário, a matriz de acesso e o isolamento passarem com PostgreSQL/Testcontainers real.

## 9. Fora do escopo

- Alterações de frontend, telas ou contratos de dashboard.
- Consulta de visualizações do professor e exportação CSV de relatórios.
- Upload de novos tipos, imagens de lições ou vídeos.
- S3, filesystem, CDN, links públicos ou URLs assinadas.
- Streaming direto do banco, suporte a ranges, cache condicional e limpeza de órfãos.
- Registro automático de visualização e alterações gerais nas regras de publicação ou autenticação.
