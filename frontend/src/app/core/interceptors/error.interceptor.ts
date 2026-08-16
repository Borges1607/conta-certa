import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';

import { ApiError } from '../api/problem-details';

/**
 * Converte toda falha em `ApiError` — Parte 1, §5.1.
 *
 * Fica **antes** do `refreshInterceptor` na lista, portanto vê o erro **depois**
 * dele na volta: um `401` resolvido por refresh nunca chega aqui, e a aplicação
 * nem fica sabendo que houve.
 *
 * Deliberadamente não exibe toast. Quem decide entre toast, estado de página e
 * erro de campo é a feature — um `422` vai para o formulário, um `500` vai para
 * o toast.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) =>
  next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof ApiError) {
        return throwError(() => error);
      }
      if (error instanceof HttpErrorResponse) {
        return throwError(() => ApiError.fromHttp(error));
      }
      return throwError(() => error);
    }),
  );
