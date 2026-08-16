import type {
  Database,
  DbAssignment,
  DbLesson,
  DbQuestion,
  DbRoom,
  DbUser,
} from './db';
import { registerSeed } from './db';

/**
 * Dados de exemplo do mock — Parte 7, §1.
 *
 * Cobre deliberadamente os casos difíceis: sala arquivada, lição em rascunho,
 * tentativa em andamento prestes a expirar, aluno removido, empate no ranking
 * e conquistas parcialmente desbloqueadas. Um mock só de caminho feliz não
 * exercita os estados que a §9 da spec exige.
 *
 * Credenciais estão no README do frontend.
 */

/** Instante fixo de referência, para a semente ser determinística. */
const NOW = Date.now();
const iso = (offsetMs: number) => new Date(NOW + offsetMs).toISOString();
const days = (n: number) => n * 86_400_000;
const minutes = (n: number) => n * 60_000;

const INSTITUTION_ID = 'inst-ifsc';
const INSTITUTION_2_ID = 'inst-outra';

const ADMIN_ID = 'user-admin';
const TEACHER_ANA_ID = 'user-ana';
const TEACHER_BRUNO_ID = 'user-bruno';
const STUDENT_CARLA_ID = 'user-carla';
const STUDENT_DIEGO_ID = 'user-diego';
const STUDENT_ELISA_ID = 'user-elisa';
const STUDENT_FABIO_ID = 'user-fabio';

const ROOM_2A_ID = 'room-2a';
const ROOM_3B_ID = 'room-3b';
const ROOM_ARCHIVED_ID = 'room-arquivada';

function users(): DbUser[] {
  const base = {
    password: 'senha123',
    emailVerified: true,
    mustChangePassword: false,
    version: 1,
    createdAt: iso(-days(120)),
  };

  return [
    {
      ...base,
      id: ADMIN_ID,
      role: 'ADMIN',
      status: 'ACTIVE',
      fullName: 'Administração Conta Certa',
      email: 'admin@contacerta.dev',
      registrationNumber: null,
      institutionId: null,
    },
    {
      ...base,
      id: TEACHER_ANA_ID,
      role: 'TEACHER',
      status: 'ACTIVE',
      fullName: 'Ana Lima',
      email: 'ana@contacerta.dev',
      registrationNumber: 'P2026001',
      institutionId: INSTITUTION_ID,
    },
    {
      ...base,
      id: TEACHER_BRUNO_ID,
      role: 'TEACHER',
      status: 'PENDING',
      fullName: 'Bruno Costa',
      email: 'bruno@contacerta.dev',
      registrationNumber: 'P2026002',
      institutionId: INSTITUTION_ID,
      emailVerified: false,
    },
    {
      ...base,
      id: STUDENT_CARLA_ID,
      role: 'STUDENT',
      status: 'ACTIVE',
      fullName: 'Carla Souza',
      email: 'carla@contacerta.dev',
      registrationNumber: '2026001',
      institutionId: INSTITUTION_ID,
    },
    {
      ...base,
      id: STUDENT_DIEGO_ID,
      role: 'STUDENT',
      status: 'ACTIVE',
      fullName: 'Diego Martins',
      email: 'diego@contacerta.dev',
      registrationNumber: '2026002',
      institutionId: INSTITUTION_ID,
    },
    {
      ...base,
      id: STUDENT_ELISA_ID,
      role: 'STUDENT',
      status: 'ACTIVE',
      fullName: 'Elisa Prado',
      email: 'elisa@contacerta.dev',
      registrationNumber: '2026003',
      institutionId: INSTITUTION_ID,
    },
    {
      ...base,
      id: STUDENT_FABIO_ID,
      role: 'STUDENT',
      status: 'ACTIVE',
      fullName: 'Fábio Nunes',
      email: 'fabio@contacerta.dev',
      registrationNumber: '2026004',
      institutionId: INSTITUTION_ID,
    },
  ];
}

function rooms(): DbRoom[] {
  return [
    {
      id: ROOM_2A_ID,
      teacherId: TEACHER_ANA_ID,
      institutionId: INSTITUTION_ID,
      name: '2º ano A',
      description: 'Matemática financeira aplicada ao dia a dia.',
      grade: 'HIGH_SCHOOL_2',
      contentTopics: ['Porcentagem', 'Juros simples', 'Juros compostos'],
      passingScorePercent: 50,
      joinCode: 'A7K9Q2',
      archivedAt: null,
      everUsed: true,
      version: 1,
      createdAt: iso(-days(90)),
    },
    {
      id: ROOM_3B_ID,
      teacherId: TEACHER_ANA_ID,
      institutionId: INSTITUTION_ID,
      name: '3º ano B',
      description: 'Preparação para o mercado e para a vida financeira.',
      grade: 'HIGH_SCHOOL_3',
      contentTopics: ['Descontos sucessivos', 'Aplicações práticas'],
      passingScorePercent: 60,
      joinCode: 'B3M8X5',
      archivedAt: null,
      everUsed: true,
      version: 1,
      createdAt: iso(-days(60)),
    },
    {
      id: ROOM_ARCHIVED_ID,
      teacherId: TEACHER_ANA_ID,
      institutionId: INSTITUTION_ID,
      name: '1º ano C (2025)',
      description: 'Turma encerrada.',
      grade: 'HIGH_SCHOOL_1',
      contentTopics: ['Porcentagem'],
      passingScorePercent: 50,
      joinCode: 'C1Z4W7',
      archivedAt: iso(-days(30)),
      everUsed: true,
      version: 1,
      createdAt: iso(-days(400)),
    },
  ];
}

interface LessonSeed {
  id: string;
  title: string;
  summary: string;
  theory: string;
  status: DbLesson['status'];
}

const LESSON_SEEDS: LessonSeed[] = [
  {
    id: 'lesson-porcentagem',
    title: 'Introdução à porcentagem',
    summary: 'O que é porcentagem e como calcular na prática.',
    status: 'PUBLISHED',
    theory: `## O que é porcentagem

Porcentagem é uma razão de denominador 100. Escrever $25\\%$ é o mesmo que escrever
$\\frac{25}{100} = 0{,}25$.

### Calculando

Para achar $p\\%$ de um valor $V$:

$$P = V \\times \\frac{p}{100}$$

| Situação | Conta | Resultado |
|---|---|---|
| 10% de 250 | $250 \\times 0{,}10$ | 25 |
| 15% de 80 | $80 \\times 0{,}15$ | 12 |

> Atenção: aumentar 10% e depois reduzir 10% **não** volta ao valor original.`,
  },
  {
    id: 'lesson-juros-simples',
    title: 'Juros simples',
    summary: 'Quando o juro incide sempre sobre o capital inicial.',
    status: 'PUBLISHED',
    theory: `## Juros simples

O juro é calculado sempre sobre o **capital inicial**:

$$J = C \\times i \\times t$$

onde $C$ é o capital, $i$ a taxa por período e $t$ o número de períodos.

O montante é $M = C + J$.`,
  },
  {
    id: 'lesson-juros-compostos',
    title: 'Juros compostos',
    summary: 'Quando o juro passa a render juro.',
    status: 'PUBLISHED',
    theory: `## Juros compostos

Aqui o juro de cada período entra no capital do período seguinte:

$$M = C(1+i)^t$$

É a fórmula que explica tanto o crescimento de um investimento quanto o
tamanho de uma dívida de cartão de crédito.

\`\`\`text
Capital 1000, taxa 2% ao mês, 12 meses
M = 1000 * (1,02)^12 = 1268,24
\`\`\``,
  },
  {
    id: 'lesson-descontos',
    title: 'Descontos sucessivos',
    summary: 'Por que 20% + 10% não é 30%.',
    status: 'PUBLISHED',
    theory: `## Descontos sucessivos

Dois descontos seguidos **não** se somam. Aplicando $d_1$ e depois $d_2$:

$$V_{final} = V \\times (1-d_1) \\times (1-d_2)$$

Um desconto de $20\\%$ seguido de $10\\%$ equivale a $28\\%$, não a $30\\%$.`,
  },
  {
    id: 'lesson-aplicacoes',
    title: 'Aplicações práticas',
    summary: 'Financiamento, parcelamento e o custo do crédito.',
    status: 'PUBLISHED',
    theory: `## Comprar à vista ou parcelado?

"Sem juros" quase nunca é sem juros: compare o preço à vista com a soma das
parcelas. Se houver diferença, existe juro embutido.`,
  },
  {
    id: 'lesson-rascunho',
    title: 'Inflação e poder de compra',
    summary: 'Em construção.',
    status: 'DRAFT',
    theory: '## Rascunho\n\nAinda escrevendo esta lição.',
  },
  {
    id: 'lesson-arquivada',
    title: 'Regra de três (arquivada)',
    summary: 'Substituída pela lição de porcentagem.',
    status: 'ARCHIVED',
    theory: '## Regra de três\n\nConteúdo mantido apenas por histórico.',
  },
];

function lessons(): DbLesson[] {
  return LESSON_SEEDS.map((seed, index) => ({
    id: seed.id,
    teacherId: TEACHER_ANA_ID,
    title: seed.title,
    summary: seed.summary,
    theoryMarkdown: seed.theory,
    status: seed.status,
    version: 1,
    createdAt: iso(-days(80 - index * 5)),
  }));
}

let questionCounter = 0;

function choice(
  lessonId: string,
  prompt: string,
  options: [string, boolean][],
  explanation: string,
  multiple = false,
): DbQuestion {
  questionCounter++;
  return {
    id: `q-${questionCounter}`,
    lessonId,
    type: multiple ? 'MULTIPLE_CHOICE' : 'SINGLE_CHOICE',
    prompt,
    explanation,
    order: questionCounter,
    active: true,
    options: options.map(([text, correct], i) => ({
      id: `q-${questionCounter}-o${i}`,
      text,
      correct,
    })),
    correctBoolean: null,
    correctNumericValue: null,
    absoluteTolerance: null,
    unit: 'NONE',
    decimalPlaces: 0,
    version: 1,
  };
}

function trueFalse(
  lessonId: string,
  prompt: string,
  correct: boolean,
  explanation: string,
): DbQuestion {
  questionCounter++;
  return {
    id: `q-${questionCounter}`,
    lessonId,
    type: 'TRUE_FALSE',
    prompt,
    explanation,
    order: questionCounter,
    active: true,
    options: [],
    correctBoolean: correct,
    correctNumericValue: null,
    absoluteTolerance: null,
    unit: 'NONE',
    decimalPlaces: 0,
    version: 1,
  };
}

function numeric(
  lessonId: string,
  prompt: string,
  value: string,
  unit: DbQuestion['unit'],
  decimalPlaces: number,
  tolerance: string,
  explanation: string,
): DbQuestion {
  questionCounter++;
  return {
    id: `q-${questionCounter}`,
    lessonId,
    type: 'NUMERIC',
    prompt,
    explanation,
    order: questionCounter,
    active: true,
    options: [],
    correctBoolean: null,
    correctNumericValue: value,
    absoluteTolerance: tolerance,
    unit,
    decimalPlaces,
    version: 1,
  };
}

function questions(): DbQuestion[] {
  return [
    // Porcentagem — cobre os quatro tipos.
    choice(
      'lesson-porcentagem',
      'Quanto é $20\\%$ de R$ 350,00?',
      [
        ['R$ 70,00', true],
        ['R$ 35,00', false],
        ['R$ 7,00', false],
        ['R$ 700,00', false],
      ],
      'Multiplique 350 por 0,20: o resultado é 70.',
    ),
    choice(
      'lesson-porcentagem',
      'Quais afirmações sobre porcentagem estão corretas?',
      [
        ['$50\\%$ equivale a $\\frac{1}{2}$', true],
        ['$25\\%$ equivale a 0,25', true],
        ['$100\\%$ equivale a 10', false],
        ['$1\\%$ equivale a 0,1', false],
      ],
      'Metade é 50% e 25% é 0,25. Já 100% equivale a 1, e 1% a 0,01.',
      true,
    ),
    trueFalse(
      'lesson-porcentagem',
      'Aumentar um valor em $10\\%$ e depois reduzi-lo em $10\\%$ devolve o valor original.',
      false,
      'A redução incide sobre um valor maior. 100 vira 110 e depois 99.',
    ),
    numeric(
      'lesson-porcentagem',
      'Uma blusa de R$ 80,00 teve desconto de $15\\%$. Qual o preço final?',
      '68.00',
      'BRL',
      2,
      '0.01',
      'O desconto é de R$ 12,00, então o preço final é R$ 68,00.',
    ),

    // Juros simples
    numeric(
      'lesson-juros-simples',
      'Capital de R$ 1.000,00 a $2\\%$ ao mês, por 6 meses, em juros simples. Qual o juro total?',
      '120.00',
      'BRL',
      2,
      '0.01',
      '$J = 1000 \\times 0{,}02 \\times 6 = 120$.',
    ),
    choice(
      'lesson-juros-simples',
      'No regime de juros simples, o juro incide sobre:',
      [
        ['o capital inicial, sempre', true],
        ['o montante do período anterior', false],
        ['a média entre capital e montante', false],
        ['a taxa acumulada', false],
      ],
      'É exatamente isso que diferencia juros simples de compostos.',
    ),
    trueFalse(
      'lesson-juros-simples',
      'Em juros simples, o montante cresce de forma linear ao longo do tempo.',
      true,
      'O juro por período é constante, então o crescimento é linear.',
    ),

    // Juros compostos
    numeric(
      'lesson-juros-compostos',
      'R$ 1.000,00 a $2\\%$ ao mês por 12 meses. Qual o montante? (2 casas)',
      '1268.24',
      'BRL',
      2,
      '0.50',
      '$M = 1000 \\times (1{,}02)^{12} \\approx 1268{,}24$.',
    ),
    choice(
      'lesson-juros-compostos',
      'A fórmula do montante em juros compostos é:',
      [
        ['$M = C(1+i)^t$', true],
        ['$M = C(1+it)$', false],
        ['$M = C \\times i \\times t$', false],
        ['$M = C + i + t$', false],
      ],
      'A segunda é o montante em juros simples; a terceira é apenas o juro simples.',
    ),
    trueFalse(
      'lesson-juros-compostos',
      'A prazos longos, juros compostos rendem mais que juros simples à mesma taxa.',
      true,
      'O juro passa a render juro, e a diferença cresce com o tempo.',
    ),

    // Descontos
    numeric(
      'lesson-descontos',
      'Descontos sucessivos de $20\\%$ e $10\\%$ equivalem a um desconto único de quanto? (em %)',
      '28',
      'PERCENT',
      0,
      '0.5',
      '$(1-0{,}20)(1-0{,}10) = 0{,}72$, ou seja, 28% de desconto.',
    ),
    choice(
      'lesson-descontos',
      'Um produto de R$ 200,00 com descontos de $10\\%$ e $10\\%$ sai por:',
      [
        ['R$ 162,00', true],
        ['R$ 160,00', false],
        ['R$ 180,00', false],
        ['R$ 158,00', false],
      ],
      '$200 \\times 0{,}9 \\times 0{,}9 = 162$.',
    ),
    trueFalse(
      'lesson-descontos',
      'A ordem em que dois descontos sucessivos são aplicados altera o preço final.',
      false,
      'A multiplicação é comutativa: a ordem não muda o resultado.',
    ),

    // Aplicações
    choice(
      'lesson-aplicacoes',
      'Um produto custa R$ 900,00 à vista ou 10x de R$ 100,00. O parcelamento:',
      [
        ['embute juros, pois o total é R$ 1.000,00', true],
        ['é realmente sem juros', false],
        ['é mais barato que à vista', false],
        ['não dá para comparar', false],
      ],
      'A diferença de R$ 100,00 é o custo do crédito.',
    ),
    numeric(
      'lesson-aplicacoes',
      'Preço à vista R$ 900,00; parcelado 10x R$ 100,00. Qual o acréscimo, em reais?',
      '100.00',
      'BRL',
      2,
      '0.01',
      'R$ 1.000,00 menos R$ 900,00.',
    ),
    trueFalse(
      'lesson-aplicacoes',
      '"Parcelamento sem juros" sempre significa ausência de custo financeiro.',
      false,
      'Muitas vezes o juro está embutido no preço à vista inflado.',
    ),
  ];
}

function assignments(): DbAssignment[] {
  const base = {
    status: 'PUBLISHED' as const,
    shuffleQuestions: true,
    shuffleOptions: true,
    version: 1,
  };

  return [
    {
      ...base,
      id: 'assign-2a-1',
      roomId: ROOM_2A_ID,
      lessonId: 'lesson-porcentagem',
      position: 1,
      availableFrom: iso(-days(30)),
      dueAt: iso(days(30)),
      timeLimitMinutes: 30,
      maxAttempts: 3,
      questionCount: 4,
    },
    {
      ...base,
      id: 'assign-2a-2',
      roomId: ROOM_2A_ID,
      lessonId: 'lesson-juros-simples',
      position: 2,
      availableFrom: iso(-days(20)),
      dueAt: iso(days(30)),
      timeLimitMinutes: 30,
      maxAttempts: 3,
      questionCount: 3,
    },
    {
      ...base,
      id: 'assign-2a-3',
      roomId: ROOM_2A_ID,
      lessonId: 'lesson-juros-compostos',
      position: 3,
      availableFrom: iso(-days(10)),
      dueAt: iso(days(30)),
      timeLimitMinutes: 45,
      maxAttempts: 3,
      questionCount: 3,
    },
    {
      // Ainda não abriu: exercita o bloqueio por data de abertura.
      ...base,
      id: 'assign-2a-4',
      roomId: ROOM_2A_ID,
      lessonId: 'lesson-descontos',
      position: 4,
      availableFrom: iso(days(7)),
      dueAt: iso(days(40)),
      timeLimitMinutes: null,
      maxAttempts: null,
      questionCount: 3,
    },
    {
      // Prazo encerrado: exercita o bloqueio por prazo.
      ...base,
      id: 'assign-3b-1',
      roomId: ROOM_3B_ID,
      lessonId: 'lesson-descontos',
      position: 1,
      availableFrom: iso(-days(40)),
      dueAt: iso(-days(2)),
      timeLimitMinutes: 30,
      maxAttempts: 2,
      questionCount: 3,
    },
    {
      ...base,
      id: 'assign-3b-2',
      roomId: ROOM_3B_ID,
      lessonId: 'lesson-aplicacoes',
      position: 2,
      availableFrom: iso(-days(15)),
      dueAt: iso(days(20)),
      timeLimitMinutes: 40,
      maxAttempts: 3,
      questionCount: 3,
    },
  ];
}

function buildDatabase(): Database {
  questionCounter = 0;

  return {
    institutions: [
      {
        id: INSTITUTION_ID,
        name: 'IFSC — Campus Florianópolis',
        cnpj: '11222333000181',
        contactEmail: 'contato@ifsc.example',
        contactPhone: '+5548999990000',
        active: true,
        version: 1,
        createdAt: iso(-days(365)),
      },
      {
        id: INSTITUTION_2_ID,
        name: 'Colégio Horizonte',
        cnpj: '11444777000161',
        contactEmail: 'secretaria@horizonte.example',
        contactPhone: '+5548988880000',
        active: true,
        version: 1,
        createdAt: iso(-days(200)),
      },
    ],
    users: users(),
    sessions: [],
    actionTokens: [],
    rooms: rooms(),
    memberships: [
      { roomId: ROOM_2A_ID, studentId: STUDENT_CARLA_ID, status: 'ACTIVE', joinedAt: iso(-days(28)), removedAt: null },
      { roomId: ROOM_2A_ID, studentId: STUDENT_DIEGO_ID, status: 'ACTIVE', joinedAt: iso(-days(27)), removedAt: null },
      { roomId: ROOM_2A_ID, studentId: STUDENT_ELISA_ID, status: 'ACTIVE', joinedAt: iso(-days(26)), removedAt: null },
      // Removido, com histórico preservado: reingresso restaura tudo.
      { roomId: ROOM_2A_ID, studentId: STUDENT_FABIO_ID, status: 'REMOVED', joinedAt: iso(-days(25)), removedAt: iso(-days(5)) },
      { roomId: ROOM_3B_ID, studentId: STUDENT_CARLA_ID, status: 'ACTIVE', joinedAt: iso(-days(14)), removedAt: null },
      { roomId: ROOM_3B_ID, studentId: STUDENT_DIEGO_ID, status: 'ACTIVE', joinedAt: iso(-days(13)), removedAt: null },
    ],
    lessons: lessons(),
    questions: questions(),
    assignments: assignments(),
    attempts: [],
    extraAttempts: [],
    videos: [
      {
        id: 'video-1',
        teacherId: TEACHER_ANA_ID,
        title: 'Porcentagem em 8 minutos',
        description: 'Revisão rápida com exemplos do supermercado.',
        url: 'https://www.youtube.com/watch?v=dQw4w9WgXcQ',
        category: 'Porcentagem',
        status: 'PUBLISHED',
        version: 1,
        createdAt: iso(-days(25)),
      },
      {
        id: 'video-2',
        teacherId: TEACHER_ANA_ID,
        title: 'Juros compostos na prática',
        description: 'Simulações de investimento e de dívida.',
        url: 'https://www.youtube.com/watch?v=9bZkp7q19f0',
        category: 'Juros compostos',
        status: 'PUBLISHED',
        version: 1,
        createdAt: iso(-days(12)),
      },
    ],
    materials: [
      {
        id: 'material-1',
        teacherId: TEACHER_ANA_ID,
        title: 'Slides — Porcentagem',
        description: 'Material da aula 1.',
        kind: 'FILE',
        url: null,
        fileId: 'file-1',
        category: 'Porcentagem',
        status: 'PUBLISHED',
        version: 1,
        createdAt: iso(-days(24)),
      },
      {
        id: 'material-2',
        teacherId: TEACHER_ANA_ID,
        title: 'Simulador de juros (planilha online)',
        description: 'Link externo para praticar.',
        kind: 'EXTERNAL_LINK',
        url: 'https://exemplo.com/simulador-juros',
        fileId: null,
        category: 'Juros compostos',
        status: 'PUBLISHED',
        version: 1,
        createdAt: iso(-days(11)),
      },
    ],
    files: [
      {
        id: 'file-1',
        name: 'porcentagem.pdf',
        mimeType: 'application/pdf',
        size: 842_000,
        content: 'Conteúdo simbólico do PDF de exemplo do Conta Certa.',
      },
    ],
    mediaAssignments: [
      { id: 'ma-1', roomId: ROOM_2A_ID, mediaType: 'VIDEO', mediaId: 'video-1', lessonId: 'lesson-porcentagem', publishedAt: iso(-days(25)) },
      { id: 'ma-2', roomId: ROOM_2A_ID, mediaType: 'VIDEO', mediaId: 'video-2', lessonId: 'lesson-juros-compostos', publishedAt: iso(-days(12)) },
      { id: 'ma-3', roomId: ROOM_2A_ID, mediaType: 'MATERIAL', mediaId: 'material-1', lessonId: 'lesson-porcentagem', publishedAt: iso(-days(24)) },
      { id: 'ma-4', roomId: ROOM_2A_ID, mediaType: 'MATERIAL', mediaId: 'material-2', lessonId: null, publishedAt: iso(-days(11)) },
      { id: 'ma-5', roomId: ROOM_3B_ID, mediaType: 'VIDEO', mediaId: 'video-2', lessonId: null, publishedAt: iso(-days(10)) },
    ],
    mediaViews: [
      { mediaType: 'VIDEO', mediaId: 'video-1', studentId: STUDENT_CARLA_ID, firstViewedAt: iso(-days(20)), lastViewedAt: iso(-days(3)) },
      { mediaType: 'VIDEO', mediaId: 'video-1', studentId: STUDENT_DIEGO_ID, firstViewedAt: iso(-days(19)), lastViewedAt: iso(-days(19)) },
      { mediaType: 'MATERIAL', mediaId: 'material-1', studentId: STUDENT_CARLA_ID, firstViewedAt: iso(-days(18)), lastViewedAt: iso(-days(18)) },
    ],
    financialTips: [
      {
        id: 'tip-1',
        title: 'Anote tudo por 30 dias',
        content:
          'Registrar cada gasto por um mês costuma revelar de **10% a 20%** de despesas que você nem lembrava. Não é sobre cortar: é sobre enxergar.',
        sourceUrl: null,
        publicationDate: localDate(0),
        active: true,
        version: 1,
      },
      {
        id: 'tip-2',
        title: 'Compare o preço à vista, sempre',
        content:
          'Antes de aceitar "10x sem juros", pergunte o preço à vista. Se houver desconto, o parcelamento tinha juro embutido.',
        sourceUrl: null,
        publicationDate: localDate(1),
        active: true,
        version: 1,
      },
      {
        id: 'tip-3',
        title: 'A regra dos 72',
        content:
          'Dividindo 72 pela taxa de juros anual, você estima em quantos anos um valor dobra. A $6\\%$ ao ano, cerca de 12 anos.',
        sourceUrl: 'https://exemplo.com/regra-dos-72',
        publicationDate: localDate(-1),
        active: true,
        version: 1,
      },
      {
        id: 'tip-4',
        title: 'Reserva antes de investir',
        content: 'Uma reserva de emergência evita que um imprevisto vire dívida cara.',
        sourceUrl: null,
        publicationDate: localDate(-10),
        active: false,
        version: 1,
      },
    ],
  };
}

/** `publicationDate` é LocalDate: nada de fuso aqui (Parte 6, §6). */
function localDate(offsetDays: number): string {
  const date = new Date(NOW + days(offsetDays));
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

registerSeed(buildDatabase);

export const SEED_IDS = {
  INSTITUTION_ID,
  INSTITUTION_2_ID,
  ADMIN_ID,
  TEACHER_ANA_ID,
  TEACHER_BRUNO_ID,
  STUDENT_CARLA_ID,
  STUDENT_DIEGO_ID,
  STUDENT_ELISA_ID,
  STUDENT_FABIO_ID,
  ROOM_2A_ID,
  ROOM_3B_ID,
  ROOM_ARCHIVED_ID,
} as const;

export { buildDatabase };

/** Minutos usados pela semente de tentativas em andamento. */
export const SEED_ATTEMPT_WINDOW_MS = minutes(12);
