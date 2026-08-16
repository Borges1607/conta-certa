import type { AttemptQuestion } from './attempt-question';
import { describeAnswer, formatNumericAnswer, normalizeDecimal } from './answer-format';

/**
 * Protege "valores monetários e respostas numéricas trafegam como string,
 * nunca float" (§2.1 e §6.3 da spec de integração).
 */
describe('normalizeDecimal', () => {
  it('completa as casas decimais sem passar por float', () => {
    expect(normalizeDecimal('100,5', 2)).toBe('100.50');
    expect(normalizeDecimal('100.5', 2)).toBe('100.50');
    expect(normalizeDecimal('7', 2)).toBe('7.00');
  });

  it('trunca casas excedentes em vez de arredondar', () => {
    // Arredondar seria uma decisão sobre a resposta do aluno; truncar é
    // apenas respeitar o formato pedido.
    expect(normalizeDecimal('1.239', 2)).toBe('1.23');
  });

  it('preserva precisão que um float perderia', () => {
    // 0.1 + 0.2 em float dá 0.30000000000000004. Aqui é só string.
    expect(normalizeDecimal('0,30', 2)).toBe('0.30');
    expect(normalizeDecimal('1268,24', 2)).toBe('1268.24');
  });

  it('respeita zero casas decimais', () => {
    expect(normalizeDecimal('28', 0)).toBe('28');
    expect(normalizeDecimal('28,7', 0)).toBe('28');
  });

  it('aceita negativos', () => {
    expect(normalizeDecimal('-5,5', 2)).toBe('-5.50');
  });

  it('devolve null para texto que ainda não é número', () => {
    expect(normalizeDecimal('', 2)).toBeNull();
    expect(normalizeDecimal('abc', 2)).toBeNull();
    expect(normalizeDecimal('1,2,3', 2)).toBeNull();
    expect(normalizeDecimal('-', 2)).toBeNull();
  });
});

describe('describeAnswer', () => {
  const choiceQuestion: AttemptQuestion = {
    questionSnapshotId: 's1',
    type: 'SINGLE_CHOICE',
    prompt: 'Quanto é 20% de 350?',
    order: 1,
    options: [
      { id: 'o1', text: 'R$ 70,00' },
      { id: 'o2', text: 'R$ 35,00' },
    ],
  };

  const numericQuestion: AttemptQuestion = {
    questionSnapshotId: 's2',
    type: 'NUMERIC',
    prompt: 'Qual o preço final?',
    order: 2,
    numeric: { unit: 'BRL', decimalPlaces: 2 },
  };

  it('resolve ids de alternativa em texto', () => {
    expect(describeAnswer(choiceQuestion, { selectedOptionIds: ['o1'] })).toBe('R$ 70,00');
  });

  it('junta múltiplas alternativas', () => {
    expect(describeAnswer(choiceQuestion, { selectedOptionIds: ['o1', 'o2'] })).toBe(
      'R$ 70,00, R$ 35,00',
    );
  });

  it('mostra o id quando a alternativa não é encontrada', () => {
    // Melhor mostrar algo do que esconder a informação.
    expect(describeAnswer(choiceQuestion, { selectedOptionIds: ['sumiu'] })).toBe('sumiu');
  });

  it('descreve verdadeiro e falso', () => {
    const tf: AttemptQuestion = { ...choiceQuestion, type: 'TRUE_FALSE', options: undefined };
    expect(describeAnswer(tf, { booleanValue: true })).toBe('Verdadeiro');
    expect(describeAnswer(tf, { booleanValue: false })).toBe('Falso');
  });

  it('formata valor monetário a partir da string', () => {
    expect(describeAnswer(numericQuestion, { numericValue: '68.00' })).toContain('68,00');
  });

  it('trata ausência de resposta', () => {
    expect(describeAnswer(choiceQuestion, null)).toBe('Sem resposta');
    expect(describeAnswer(choiceQuestion, {})).toBe('Sem resposta');
  });

  it('formata porcentagem com vírgula', () => {
    const percent: AttemptQuestion = {
      ...numericQuestion,
      numeric: { unit: 'PERCENT', decimalPlaces: 1 },
    };
    expect(formatNumericAnswer('28.5', percent)).toBe('28,5%');
  });
});
