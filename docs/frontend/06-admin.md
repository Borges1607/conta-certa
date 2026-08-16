# Parte 6 — Frontend do administrador

Depende de: Partes 1 e 2.
Referência normativa: §8 de [`../frontend-integration-spec.md`](../frontend-integration-spec.md).

## 1. Objetivo

Entregar a administração da plataforma: instituições, professores e dicas financeiras. É a
menor das três áreas e a mais uniforme — três CRUDs com paginação de servidor, `version` e
ações de ativação.

O admin **não** cria salas, lições, questões ou mídias, e **não** vê ranking. A matriz da §10
da spec é respeitada literalmente: o menu do admin não exibe nenhuma dessas entradas.

## 2. Rotas

| Rota | Tela |
|---|---|
| `/admin` | Dashboard |
| `/admin/instituicoes` | Lista de instituições |
| `/admin/instituicoes/:institutionId` | Detalhe da instituição |
| `/admin/professores` | Lista de professores |
| `/admin/professores/:teacherId` | Detalhe do professor |
| `/admin/dicas` | Dicas financeiras |

## 3. Dashboard

`GET /admin/dashboard`: quantidades de instituições e de professores por status. Cartões
clicáveis que navegam para a lista já filtrada pelo status correspondente — o filtro vai na
query string, então o link é compartilhável e sobrevive a recarga.

## 4. Instituições

`GET /admin/institutions` paginado, com busca por nome ou CNPJ e filtro por situação.

Formulário (`POST` e `PATCH`): `name`, `cnpj`, `contactEmail`, `contactPhone`.

- CNPJ com máscara de apresentação e **envio normalizado com 14 dígitos**, sem pontuação.
  A validação de dígito verificador é feita localmente como cortesia; o servidor decide.
  `409` de CNPJ duplicado vira erro no campo.
- Telefone no formato E.164 (`+5548999999999`), com máscara auxiliar.
- `PATCH` envia `version`; `409 VERSION_CONFLICT` oferece recarregar, nunca sobrescrever.

Ações:

| Ação | Endpoint | Regra de interface |
|---|---|---|
| Ativar | `POST .../activate` | Confirmação simples |
| Desativar | `POST .../deactivate` | Confirmação explicando que **novos vínculos** ficam bloqueados e que os existentes continuam funcionando |
| Excluir | `DELETE .../{institutionId}` | Só sem histórico; `409` explica que a instituição possui vínculos e sugere desativar |

O texto da desativação é importante e não pode ser genérico: desativar não derruba usuários
nem salas existentes, apenas impede novos.

O detalhe mostra os dados e um resumo de vínculos (professores e alunos), quando a API o
fornecer.

## 5. Professores

`GET /admin/teachers` paginado, com filtro por instituição e por `AccountStatus` e busca por
nome, e-mail ou matrícula.

Criar (`POST /admin/teachers`): nome completo, e-mail, matrícula e instituição. A instituição
vem de `GET /institutions/options?active=true`. O resultado é um professor `PENDING` com
convite enviado por e-mail — a tela deixa isso explícito: "Um convite será enviado para que o
professor defina a própria senha." O admin **nunca** define senha de professor.

Editar (`PATCH .../{teacherId}`): nome, matrícula e instituição. E-mail não é editável.

Ações:

| Ação | Endpoint | Confirmação |
|---|---|---|
| Reativar | `POST .../activate` | Simples |
| Desativar | `POST .../deactivate` | "As sessões ativas serão encerradas imediatamente." |
| Reenviar redefinição | `POST .../password-reset` | Simples; feedback neutro |

A desativação revoga todas as sessões do professor — a confirmação precisa dizer isso, porque
o efeito é imediato e visível para quem está usando o sistema.

O detalhe mostra situação da conta, e-mail verificado, instituição, matrícula e datas.

Estados de conta com cores e rótulos distintos: `PENDING` (âmbar, "Convite enviado"),
`ACTIVE` (verde), `INACTIVE` (cinza).

## 6. Dicas financeiras

`GET/POST /admin/financial-tips` e `GET/PATCH/DELETE /admin/financial-tips/{tipId}`, mais
`activate` e `deactivate`.

Campos: `title`, `content`, `sourceUrl` opcional, `publicationDate`, `active` e `version`.

- `content` é Markdown, editado com o mesmo editor com pré-visualização da Parte 5 e
  renderizado pelo `cc-markdown` sanitizado — a dica aparece no dashboard do aluno, então o
  conteúdo passa exatamente pelo mesmo caminho de segurança.
- `publicationDate` é `LocalDate` (`YYYY-MM-DD`), sem hora e sem conversão de fuso. Este é o
  único campo de data da aplicação que **não** é convertido para UTC; tratá-lo como `Instant`
  é um erro clássico e está coberto por teste.
- A lista mostra o agendamento e destaca a dica do dia corrente.
- A tela explica a regra: sem dica agendada para o dia, o backend escolhe uma dica ativa.
- Excluir arquiva logicamente quando a API assim responder; a interface reflete o retorno.

## 7. Entregáveis

```
features/admin/
├── admin.routes.ts
├── data/ admin-dashboard.service.ts, institution.service.ts,
│         admin-teacher.service.ts, financial-tip.service.ts
├── models/ institution-dto.ts, teacher-dto.ts, financial-tip-dto.ts
├── components/ institution-form-dialog/, teacher-form-dialog/,
│               account-status-tag/, tip-form-dialog/, cnpj-input/
└── pages/ dashboard/, institutions/, institution-detail/,
          teachers/, teacher-detail/, financial-tips/
```

`cnpj-input` e a máscara de telefone podem subir para `shared/` se a Parte 5 precisar deles.

## 8. Testes desta parte

1. CNPJ é enviado com 14 dígitos sem pontuação, e exibido com máscara.
2. `PATCH` de instituição envia `version`; `409` oferece recarregar e não salvar por cima.
3. `DELETE` de instituição com vínculos (`409`) mostra a explicação e sugere desativar.
4. Criar professor não expõe nenhum campo de senha.
5. Confirmação de desativação de professor menciona o encerramento das sessões.
6. `publicationDate` trafega como `YYYY-MM-DD`, sem componente de hora, independentemente do
   fuso do navegador.
7. Cartões do dashboard navegam para a lista com o filtro na query string.
8. O menu do admin não contém salas, lições, questões, mídias nem ranking.

## 9. Critérios de aceite

- Nenhuma tela do admin permite definir senha de outro usuário.
- Toda ação de ativação/desativação e exclusão passa por confirmação com texto específico do
  efeito real, não genérico.
- Todo recurso editável envia `version` e trata `409` sem sobrescrita.
- O conteúdo das dicas passa pela mesma sanitização do conteúdo do aluno.