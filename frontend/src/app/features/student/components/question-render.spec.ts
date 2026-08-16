import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import type { AnswerPayload, AttemptQuestion } from '../models/attempt-question';
import { QuestionMultipleChoiceComponent } from './question-multiple-choice/question-multiple-choice';
import { QuestionNavigatorComponent } from './question-navigator/question-navigator';
import { QuestionNumericComponent } from './question-numeric/question-numeric';
import { QuestionSingleChoiceComponent } from './question-single-choice/question-single-choice';
import { QuestionTrueFalseComponent } from './question-true-false/question-true-false';

/**
 * Protege o critério "nenhum gabarito aparece antes de a tentativa terminar"
 * (§11 da spec de integração) **no HTML renderizado**, para os quatro tipos de
 * questão.
 *
 * O teste do `AttemptService` prova que o dado não chega; este prova que a tela
 * não o inventa nem o expõe por outra via — classe CSS, atributo ou texto.
 */
describe('Questões em andamento — sigilo do gabarito', () => {
  const html = (fixture: ComponentFixture<unknown>) =>
    (fixture.nativeElement as HTMLElement).innerHTML;

  /**
   * `cc-markdown` carrega marked, DOMPurify e KaTeX por import dinâmico, então
   * o texto das alternativas só aparece depois de o chunk chegar —
   * `whenStable()` sozinho não cobre isso.
   */
  const waitForMarkdown = async (fixture: ComponentFixture<unknown>): Promise<void> => {
    for (let i = 0; i < 100; i++) {
      await fixture.whenStable();
      const rendered = (fixture.nativeElement as HTMLElement).querySelector('.cc-markdown');
      if (rendered && rendered.innerHTML.trim() !== '') {
        return;
      }
      await new Promise((resolve) => setTimeout(resolve, 20));
    }
    throw new Error('O conteúdo do cc-markdown não foi renderizado a tempo.');
  };

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection()] });
  });

  /** Palavras que denunciariam correção vazando para a tela. */
  const forbidden = ['correct', 'correta', 'incorreta', 'gabarito', 'explanation', 'explicação'];

  /**
   * Varre **a área das alternativas**, não a tela inteira.
   *
   * O recorte é proposital: o aviso da múltipla escolha diz "marque todas as
   * alternativas corretas", que é instrução ao aluno e não vazamento. Varrer o
   * componente todo transformaria uma frase legítima em falso positivo — e a
   * saída seria reescrever a interface para agradar o teste, o que é a ordem
   * errada das prioridades.
   */
  const expectNoAnswerKey = (fixture: ComponentFixture<unknown>) => {
    const element = fixture.nativeElement as HTMLElement;
    const scope = element.querySelector('.options, .tf, .numeric, .nav') ?? element;
    const lower = scope.innerHTML.toLowerCase();

    for (const word of forbidden) {
      expect(lower).not.toContain(word);
    }
  };

  it('escolha única não revela a alternativa correta', async () => {
    const question: AttemptQuestion = {
      questionSnapshotId: 's1',
      type: 'SINGLE_CHOICE',
      prompt: 'Quanto é 20% de R$ 350,00?',
      order: 1,
      options: [
        { id: 'o1', text: 'R$ 70,00' },
        { id: 'o2', text: 'R$ 35,00' },
      ],
    };

    const fixture = TestBed.createComponent(QuestionSingleChoiceComponent);
    fixture.componentRef.setInput('question', question);
    await waitForMarkdown(fixture);

    expect(html(fixture)).toContain('R$ 70,00');
    expectNoAnswerKey(fixture);
  });

  it('múltipla escolha não revela quais alternativas pontuam', async () => {
    const question: AttemptQuestion = {
      questionSnapshotId: 's2',
      type: 'MULTIPLE_CHOICE',
      prompt: 'Quais afirmações estão certas?',
      order: 2,
      options: [
        { id: 'o1', text: 'Metade é 50%' },
        { id: 'o2', text: '100% equivale a 10' },
      ],
    };

    const fixture = TestBed.createComponent(QuestionMultipleChoiceComponent);
    fixture.componentRef.setInput('question', question);
    await waitForMarkdown(fixture);

    expect(html(fixture)).toContain('Metade é 50%');
    // O aviso da tela fala em "seleção exata", nunca em resposta certa.
    expect(html(fixture)).toContain('seleção exata');
    expectNoAnswerKey(fixture);
  });

  it('verdadeiro ou falso não pré-seleciona nem marca o certo', async () => {
    const question: AttemptQuestion = {
      questionSnapshotId: 's3',
      type: 'TRUE_FALSE',
      prompt: 'Aumentar 10% e reduzir 10% volta ao valor original.',
      order: 3,
    };

    const fixture = TestBed.createComponent(QuestionTrueFalseComponent);
    fixture.componentRef.setInput('question', question);
    await fixture.whenStable();

    const element = fixture.nativeElement as HTMLElement;
    const checked = element.querySelectorAll('input:checked');
    // Nada nasce selecionado: o aluno precisa escolher.
    expect(checked.length).toBe(0);
    expect(element.querySelectorAll('.is-selected').length).toBe(0);
  });

  it('numérica não revela o valor esperado', async () => {
    const question: AttemptQuestion = {
      questionSnapshotId: 's4',
      type: 'NUMERIC',
      prompt: 'Qual o preço final?',
      order: 4,
      numeric: { unit: 'BRL', decimalPlaces: 2 },
    };

    const fixture = TestBed.createComponent(QuestionNumericComponent);
    fixture.componentRef.setInput('question', question);
    await fixture.whenStable();

    const input = (fixture.nativeElement as HTMLElement).querySelector('input');
    expect(input?.value).toBe('');
    expectNoAnswerKey(fixture);
  });

  it('o navegador marca respondida, nunca certa ou errada', async () => {
    const questions: AttemptQuestion[] = [
      { questionSnapshotId: 's1', type: 'TRUE_FALSE', prompt: 'A', order: 1 },
      { questionSnapshotId: 's2', type: 'TRUE_FALSE', prompt: 'B', order: 2 },
    ];

    const fixture = TestBed.createComponent(QuestionNavigatorComponent);
    fixture.componentRef.setInput('questions', questions);
    fixture.componentRef.setInput('currentIndex', 0);
    fixture.componentRef.setInput('answeredIds', new Set(['s1']));
    await fixture.whenStable();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelectorAll('.is-answered').length).toBe(1);
    expect(element.querySelector('[aria-label*="respondida"]')).not.toBeNull();
    expectNoAnswerKey(fixture);
  });

  it('reexibe a resposta do próprio aluno sem julgá-la', async () => {
    const question: AttemptQuestion = {
      questionSnapshotId: 's1',
      type: 'SINGLE_CHOICE',
      prompt: 'Pergunta',
      order: 1,
      options: [
        { id: 'o1', text: 'Primeira' },
        { id: 'o2', text: 'Segunda' },
      ],
    };
    const answer: AnswerPayload = { selectedOptionIds: ['o2'] };

    const fixture = TestBed.createComponent(QuestionSingleChoiceComponent);
    fixture.componentRef.setInput('question', question);
    fixture.componentRef.setInput('initial', answer);
    fixture.componentRef.setInput('disabled', true);
    await fixture.whenStable();

    const element = fixture.nativeElement as HTMLElement;
    // A alternativa escolhida aparece selecionada — e nada indica se acertou.
    expect(element.querySelectorAll('.is-selected').length).toBe(1);
    expect(element.querySelectorAll('.is-locked').length).toBe(2);
    expectNoAnswerKey(fixture);
  });
});
