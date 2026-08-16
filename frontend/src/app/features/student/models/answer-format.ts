import { formatMoney } from '../../../core/util/format';
import type { AnswerPayload, AttemptQuestion } from './attempt-question';

/**
 * Apresentação e normalização de respostas — Parte 4, §6.5.
 *
 * Funções puras, sem estado e sem gabarito: elas só sabem transformar um
 * `AnswerPayload` em texto e um texto digitado em `AnswerPayload`. São usadas
 * tanto pela tentativa em andamento (para reexibir a resposta do próprio aluno)
 * quanto pelo resultado (para descrever resposta do aluno e resposta correta).
 */

/**
 * Texto digitado → string decimal do contrato.
 *
 * Feito **inteiramente com operações de string**: o valor nunca passa por
 * `Number`, `parseFloat` ou `toFixed`. É assim que `"100,5"` com duas casas
 * vira `"100.50"` — e não `100.5` — cumprindo "envia string decimal, nunca
 * float" (§2.1 e §6.3 da spec de integração).
 *
 * Devolve `null` quando o texto ainda não é um número válido; a tela usa isso
 * para manter o botão de confirmar desabilitado.
 */
export function normalizeDecimal(raw: string, decimalPlaces: number): string | null {
  const cleaned = raw.trim().replace(',', '.');
  if (!/^-?\d+(\.\d+)?$/.test(cleaned)) {
    return null;
  }

  const [integerPart, fractionPart = ''] = cleaned.split('.');
  if (decimalPlaces <= 0) {
    return integerPart;
  }
  return `${integerPart}.${fractionPart.padEnd(decimalPlaces, '0').slice(0, decimalPlaces)}`;
}

/** String decimal do contrato → texto com unidade, para exibir. */
export function formatNumericAnswer(value: string, question: AttemptQuestion): string {
  const unit = question.numeric?.unit ?? 'NONE';
  if (unit === 'BRL') {
    return formatMoney(value);
  }
  const shown = value.replace('.', ',');
  return unit === 'PERCENT' ? `${shown}%` : shown;
}

/**
 * Descreve uma resposta em português.
 *
 * Para escolhas, resolve os ids nas alternativas da própria questão. Se um id
 * não for encontrado, mostra o id — melhor que esconder a informação.
 */
export function describeAnswer(
  question: AttemptQuestion,
  answer: AnswerPayload | null | undefined,
): string {
  if (!answer) {
    return 'Sem resposta';
  }

  if (answer.selectedOptionIds && answer.selectedOptionIds.length > 0) {
    const options = question.options ?? [];
    return answer.selectedOptionIds
      .map((id) => options.find((option) => option.id === id)?.text ?? id)
      .join(', ');
  }

  if (answer.booleanValue !== undefined) {
    return answer.booleanValue ? 'Verdadeiro' : 'Falso';
  }

  if (answer.numericValue !== undefined && answer.numericValue !== '') {
    return formatNumericAnswer(answer.numericValue, question);
  }

  return 'Sem resposta';
}

/** Sufixo/rótulo curto da unidade, usado ao lado do campo numérico. */
export function unitSuffix(question: AttemptQuestion): string {
  switch (question.numeric?.unit) {
    case 'BRL':
      return 'R$';
    case 'PERCENT':
      return '%';
    default:
      return '';
  }
}
