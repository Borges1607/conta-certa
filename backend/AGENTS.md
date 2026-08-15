# Repository Guidelines

## Estrutura do projeto e organização dos módulos

Este backend usa Spring Boot, Java 21 e Maven. Organize `src/main/java/com/ifsc/contacerta/` em camadas técnicas:

- `controller/`: endpoints HTTP, validação de entrada e conversão de DTOs.
- `service/`: casos de uso, regras de negócio e transações.
- `repository/`: interfaces Spring Data e acesso à persistência.
- `model/` ou `entity/`: entidades JPA e objetos do domínio; escolha um nome e mantenha-o.
- `dto/`: contratos de entrada e saída da API.
- `config/`: configurações transversais do Spring.

As dependências devem fluir de `controller` para `service` e de `service` para `repository`; controladores não acessam repositórios diretamente. Não exponha entidades JPA como contratos da API.

Configurações ficam em `src/main/resources/`; migrations Flyway, em `src/main/resources/db/migration/`, com nomes como `V1__cria_tabela_conta.sql`. Os testes espelham as camadas em `src/test/java/com/ifsc/contacerta/`. `target/` contém artefatos gerados e não deve ser versionado.

## Comandos de desenvolvimento, teste e build

Use o Maven Wrapper incluído no projeto para evitar diferenças de versão:

- `./mvnw spring-boot:run`: inicia a API localmente na porta configurada.
- `./mvnw test`: executa os testes JUnit 5 e os testes de contexto Spring.
- `./mvnw verify`: executa todas as verificações do ciclo Maven.
- `./mvnw clean package`: limpa artefatos anteriores e gera o JAR em `target/`.

Configure um PostgreSQL acessível antes de iniciar a aplicação.

## Estilo de código e nomenclatura

Siga o estilo existente: tabulação em Java e XML, uma classe pública por arquivo e imports explícitos. Use `PascalCase` para classes, `camelCase` para métodos e variáveis e `UPPER_SNAKE_CASE` para constantes. Não há formatador ou linter configurado; preserve o padrão dos arquivos vizinhos.

Não use nomes de tipos totalmente qualificados diretamente em campos, assinaturas ou implementações (por exemplo, `java.util.UUID`). Declare imports explícitos no topo do arquivo; imports com curinga também não são permitidos.

Use Lombok para eliminar boilerplate: `@RequiredArgsConstructor` na injeção por construtor, `@Getter` em entidades e exceções e `@NoArgsConstructor(access = AccessLevel.PROTECTED)` em entidades JPA. Não use `@Data`, `@Setter` ou `@AllArgsConstructor` em entidades. Mantenha construtores manuais somente quando eles executarem inicialização ou invariantes de domínio.

## Diretrizes de testes

O projeto usa JUnit 5 e Spring Boot Test. Nomeie testes como `ClasseTest` ou `ClasseTests` e métodos pelo comportamento esperado, por exemplo `deveCriarContaValida`. Cubra regras de serviço com testes unitários e use `@SpringBootTest` somente quando precisar do contexto completo. Não existe meta formal de cobertura; toda correção deve incluir um teste de regressão quando aplicável.

## Commits e pull requests

O histórico é curto e mistura mensagens simples com Conventional Commits. Prefira mensagens no imperativo, como `feat: adiciona cadastro de contas` ou `fix: valida saldo inicial`. Pull requests devem explicar motivação e impacto, listar testes, vincular a issue e incluir exemplos de requisição/resposta ao alterar endpoints.

## Segurança e configuração

Forneça `DB_URL`, `DB_USERNAME` e `DB_PASSWORD` pelo ambiente. Valores padrão locais servem apenas para desenvolvimento; nunca versione credenciais reais, arquivos `.env` ou dados pessoais.
