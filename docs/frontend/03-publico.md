# Parte 3 — Jornada pública e conta

Depende de: Partes 1 e 2.
Referência normativa: §4 e §5 de [`../frontend-integration-spec.md`](../frontend-integration-spec.md).

## 1. Objetivo

Entregar tudo que acontece antes de haver sessão, mais as telas de conta do usuário
autenticado. É a primeira parte com telas e a primeira prova real da Parte 1.

## 2. Telas

| Rota | Tela | Endpoint |
|---|---|---|
| `/login` | Login | `POST /auth/login` |
| `/cadastro` | Cadastro de aluno | `POST /auth/student-registration`, `GET /institutions/options` |
| `/verificar-email` | Confirmação de e-mail | `POST /auth/verify-email`, `POST /auth/resend-verification` |
| `/esqueci-senha` | Solicitar recuperação | `POST /auth/forgot-password` |
| `/redefinir-senha` | Definir nova senha | `POST /auth/reset-password` |
| `/convite-professor` | Aceite de convite | `POST /auth/accept-teacher-invite` |
| `/conta/perfil` | Perfil (autenticado) | `GET /me`, `PATCH /me` |
| `/conta/senha` | Trocar senha (autenticado) | `POST /me/change-password` |

Layout público próprio: coluna centralizada, logo, card do formulário, sem shell de perfil.

## 3. Login

Campos: e-mail e senha. Ambos obrigatórios; e-mail validado por formato.

Comportamento:

- Sucesso: guarda tokens e usuário, e redireciona por `user.role` conforme §5 da visão geral.
- Se houver `returnUrl` na query e ele for compatível com o perfil, ele vence o padrão.
- Se `mustChangePassword` for `true`, vai para `/conta/senha` com aviso, ignorando
  `returnUrl`.
- `401`: "E-mail ou senha inválidos." — mensagem única, sem revelar qual campo falhou.
- `403` com conta `PENDING` ou e-mail não verificado: mostra bloco de ação com botão
  "Reenviar confirmação" que chama `POST /auth/resend-verification`.
- `403` com conta `INACTIVE`: "Sua conta está desativada. Procure a coordenação."
- `429`: mensagem de limite e desabilita o botão por alguns segundos.
- Nenhuma resposta de erro apaga o e-mail digitado.

Links para `/cadastro` e `/esqueci-senha`.

## 4. Cadastro de aluno

Exclusivo para aluno — a spec não prevê autocadastro de professor ou admin. A tela deixa isso
explícito: "Professores recebem convite da coordenação."

Campos: nome completo, e-mail, senha, confirmação de senha, matrícula e instituição.

A instituição vem de `GET /institutions/options?active=true`, que **não é paginado**, em um
`p-select` com filtro. Se a lista falhar, o formulário mostra erro recuperável e não permite
envio às cegas.

Senha: 8 a 72 caracteres, ao menos uma letra e um número. A regra é exibida como lista de
requisitos que marca cada item conforme o usuário digita. A validação local é conveniência; o
servidor decide e seus `fieldErrors` são exibidos do mesmo jeito.

Sucesso é `202 Accepted`: a tela troca por uma confirmação — "Enviamos um link de confirmação
para <e-mail>" — com botão de reenviar e link para o login. **Não** há sessão e **não** há
redirecionamento automático.

`409` de e-mail ou matrícula já em uso vira erro no campo correspondente, com o restante do
formulário preservado.

## 5. Confirmação de e-mail

Recebe o token pela query string. Estados, todos distintos e exigidos pela §5.1 da spec:

| Estado | Origem | Interface |
|---|---|---|
| Verificando | carga inicial | Esqueleto |
| Confirmado | `200` | Sucesso e botão "Entrar" |
| Expirado | `410` | Aviso e formulário de e-mail para reenviar |
| Já utilizado | `409` | Aviso neutro e botão "Entrar" |
| Inválido | `404`/`422` | Erro e link para reenviar |
| Sem token na URL | — | Formulário de reenvio direto |

O reenvio responde sempre igual, mesmo para e-mail inexistente, para não revelar cadastro.

## 6. Recuperação de senha

`/esqueci-senha`: campo de e-mail. A resposta é sempre a mesma mensagem neutra — "Se este
e-mail estiver cadastrado, enviaremos as instruções" — independentemente do status, exceto
`429`.

`/redefinir-senha`: token pela query, nova senha e confirmação, mesma régua de senha do
cadastro. Token expirado ou usado (`410`) mostra estado próprio com link para solicitar de
novo. Sucesso leva ao login com aviso, sem autenticar automaticamente.

## 7. Aceite de convite do professor

`/convite-professor` com token na query. Quando o token é válido, a tela mostra o nome e o
e-mail do professor convidado em modo leitura e pede a senha inicial e a confirmação.

Sucesso: mensagem e redirecionamento para o login. Token expirado (`410`) instrui a pedir um
novo convite à administração — o professor não consegue se convidar.

## 8. Telas de conta

`/conta/perfil`: mostra nome, e-mail, matrícula, instituição, perfil e situação do e-mail.
**Somente o nome é editável** (`PATCH /me`). E-mail, matrícula e instituição aparecem
desabilitados com a nota "Alterações exigem suporte administrativo", conforme §4.1 da spec.

`/conta/senha`: senha atual, nova senha e confirmação. Quando chegou por
`mustChangePassword`, a tela exibe um aviso explicando a obrigatoriedade e o menu de
navegação fica indisponível até a conclusão. Sucesso limpa `mustChangePassword` no
`AuthStore` e leva à home do perfil.

## 9. Entregáveis

```
features/public/
├── public.routes.ts
├── data/ public-auth.service.ts, institution-options.service.ts
├── layout/ public-shell/
├── components/ password-requirements/, auth-card/
└── pages/ login/, student-registration/, verify-email/, forgot-password/,
          reset-password/, accept-teacher-invite/
features/account/
├── account.routes.ts
└── pages/ profile/, change-password/
```

## 10. Testes desta parte

1. Login de cada perfil leva à rota inicial correta.
2. Login com `mustChangePassword` leva a `/conta/senha` e bloqueia outras rotas.
3. `returnUrl` é respeitado quando compatível e ignorado quando não é.
4. `422` no cadastro preenche os erros por campo e preserva todos os valores digitados.
5. Cada estado de token da confirmação de e-mail é renderizado com o status correspondente.
6. `guestGuard` tira o usuário autenticado de `/login`.
7. A régua de senha aceita `abc12345` e recusa `abcdefgh` e `1234567`.

## 11. Critérios de aceite

- Nenhuma mensagem revela se um e-mail existe no sistema.
- Nenhuma tela pública dispara refresh de token.
- Cadastro de aluno nunca autentica automaticamente.
- Todo formulário sobrevive a erro de validação com os dados intactos.
- Nenhum botão de submissão aceita clique duplo.