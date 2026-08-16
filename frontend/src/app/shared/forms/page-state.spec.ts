import { ApiError } from '../../core/api/problem-details';
import { createPageState } from './page-state';

/**
 * Protege a distinção obrigatória entre carga inicial e atualização em segundo
 * plano (§9 da spec de integração): nunca substituir conteúdo já visível por
 * esqueleto.
 */
describe('createPageState', () => {
  const notFound = () =>
    new ApiError({ status: 404, code: 'NOT_FOUND', detail: 'Não encontramos.' });

  it('começa carregando e termina pronto', async () => {
    const state = createPageState(async () => ['a', 'b']);

    expect(state.isLoading()).toBe(true);
    expect(state.data()).toBeNull();

    await state.load();

    expect(state.isLoading()).toBe(false);
    expect(state.data()).toEqual(['a', 'b']);
    expect(state.error()).toBeNull();
  });

  it('expõe o erro da carga inicial', async () => {
    const state = createPageState(async () => {
      throw notFound();
    });

    await state.load();

    expect(state.error()?.status).toBe(404);
    expect(state.data()).toBeNull();
  });

  it('refresh mantém os dados na tela e sinaliza atualização', async () => {
    let value = 'primeiro';
    let resolveLoad: (() => void) | null = null;

    const state = createPageState(async () => {
      if (resolveLoad) {
        await new Promise<void>((resolve) => {
          resolveLoad = resolve;
          resolve();
        });
      }
      return value;
    });

    await state.load();
    expect(state.data()).toBe('primeiro');

    value = 'segundo';
    const pending = state.refresh();

    // Durante a atualização o conteúdo antigo continua visível.
    expect(state.isLoading()).toBe(false);
    expect(state.data()).toBe('primeiro');
    expect(state.isRefreshing()).toBe(true);

    await pending;

    expect(state.data()).toBe('segundo');
    expect(state.isRefreshing()).toBe(false);
  });

  it('falha em refresh não apaga o que já está na tela', async () => {
    let shouldFail = false;
    const state = createPageState(async () => {
      if (shouldFail) {
        throw notFound();
      }
      return 'conteúdo';
    });

    await state.load();
    shouldFail = true;

    await expect(state.refresh()).rejects.toBeInstanceOf(ApiError);

    // O usuário continua vendo o que tinha; o erro vira toast, não tela vazia.
    expect(state.data()).toBe('conteúdo');
    expect(state.error()).toBeNull();
    expect(state.isRefreshing()).toBe(false);
  });

  it('retry volta ao esqueleto e recupera', async () => {
    let shouldFail = true;
    const state = createPageState(async () => {
      if (shouldFail) {
        throw notFound();
      }
      return 'ok';
    });

    await state.load();
    expect(state.error()).not.toBeNull();

    shouldFail = false;
    await state.retry();

    expect(state.error()).toBeNull();
    expect(state.data()).toBe('ok');
  });

  it('patch altera os dados sem ir à API', async () => {
    const state = createPageState(async () => ({ count: 1 }));
    await state.load();

    state.patch((current) => ({ count: current.count + 1 }));

    expect(state.data()).toEqual({ count: 2 });
  });

  it('patch é inerte antes de os dados chegarem', () => {
    const state = createPageState(async () => ({ count: 1 }));
    state.patch((current) => ({ count: current.count + 1 }));
    expect(state.data()).toBeNull();
  });

  it('converte erro inesperado em ApiError', async () => {
    const state = createPageState(async () => {
      throw new Error('boom');
    });

    await state.load();

    expect(state.error()).toBeInstanceOf(ApiError);
    expect(state.error()?.detail).toBe('boom');
  });
});
