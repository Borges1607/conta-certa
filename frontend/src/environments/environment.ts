export const environment = {
  production: false,
  /** Base URL da API. Ver §2.1 da spec de integração. */
  apiBaseUrl: '/api/v1',
  /**
   * Quando true, o interceptor de mock responde no lugar da rede (Parte 7).
   * Trocar para false é a única mudança necessária para falar com o backend real.
   */
  useMockApi: true,
  /** Fuso de apresentação. A API sempre trafega UTC. */
  presentationTimeZone: 'America/Sao_Paulo',
} as const;