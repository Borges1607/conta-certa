# Dicas financeiras administrativas — desenho

## Objetivo

Implementar o agregado `FinancialTip` e o contrato administrativo usado pela tela de dicas financeiras. O administrador poderá criar, consultar, editar, ativar, desativar e arquivar dicas sem expor operações a professores ou alunos.

## Contrato HTTP

As rotas serão protegidas por `ROLE_ADMIN` e seguirão o envelope de paginação existente:

- `GET /admin/financial-tips?search=&active=&publicationDate=&page=&size=&sort=` lista dicas não arquivadas;
- `POST /admin/financial-tips` cria uma dica inativa por padrão; o payload pode informar `active=true` quando a publicação for imediata;
- `GET /admin/financial-tips/{tipId}` retorna o detalhe;
- `PATCH /admin/financial-tips/{tipId}` altera título, conteúdo Markdown, URL opcional e data, exigindo `version`; o status muda somente pelas ações explícitas;
- `POST /admin/financial-tips/{tipId}/activate` ativa;
- `POST /admin/financial-tips/{tipId}/deactivate` desativa;
- `DELETE /admin/financial-tips/{tipId}` arquiva logicamente e retorna `204`.

Respostas incluem `id`, `title`, `content`, `sourceUrl`, `publicationDate`, `active`, `createdAt`, `updatedAt`, `version` e `archivedAt` quando aplicável. Conflitos de versão retornam `409 VERSION_CONFLICT`; item inexistente ou arquivado retorna `404 FINANCIAL_TIP_NOT_FOUND`.

## Persistência

Uma migration Flyway criará `financial_tips` com UUID, título (`varchar(160)`), conteúdo Markdown (`text`), URL opcional (`varchar(2048)`), `publication_date` (`date`), `active`, `archived_at` (`timestamptz`), timestamps e `version`. Índices cobrirão publicação/status e busca administrativa.

O `DELETE` não remove a linha: preenche `archived_at`, define `active=false` e mantém o conteúdo para auditoria. Listagens administrativas e qualquer seleção futura para aluno ignoram registros arquivados.

## Regras de domínio

- título e conteúdo são obrigatórios e têm limites explícitos;
- `publicationDate` é `LocalDate`, trafega como `YYYY-MM-DD` e nunca é convertido para `Instant`;
- `sourceUrl` é opcional, mas quando presente deve ser uma URL HTTP/HTTPS válida;
- dicas arquivadas não podem ser editadas, ativadas ou desativadas;
- ativação/desativação são idempotentes;
- o conteúdo é armazenado como Markdown, sem execução de HTML pelo backend; o consumidor usa o mesmo renderer/sanitização `cc-markdown` definido para o frontend;
- alterações concorrentes exigem a versão atual e nunca sobrescrevem dados silenciosamente.

## Componentes

- `FinancialTip` concentra estado e transições;
- `FinancialTipRepository` usa `JpaSpecificationExecutor` para filtros e paginação;
- `FinancialTipSpecification` compõe busca, status, data e exclusão lógica;
- `AdminFinancialTipService` valida regras, normaliza entrada e mapeia respostas;
- `AdminFinancialTipController` expõe somente o contrato administrativo;
- DTOs e mapper mantêm o modelo HTTP separado da entidade.

## Testes

Serão cobertos: migration e persistência com PostgreSQL, filtro que exclui arquivados, data sem fuso, URL inválida, conflito de versão, transições idempotentes, exclusão lógica, envelope HTTP e matriz `401/403/200`.
