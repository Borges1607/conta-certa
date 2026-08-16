/**
 * Chave de idempotência — §6.3 da spec de integração.
 *
 * Gerada **uma vez por intenção do usuário** e reaproveitada em repetições da
 * mesma intenção. Regenerar a cada clique anularia a proteção: o servidor
 * criaria uma tentativa nova a cada vez.
 */
export function newIdempotencyKey(): string {
  const webCrypto: Crypto | undefined = globalThis.crypto;

  if (typeof webCrypto?.randomUUID === 'function') {
    return webCrypto.randomUUID();
  }

  // `randomUUID` só existe em contexto seguro. Servir o frontend por HTTP em
  // um IP de rede local — comum em teste com celular — cai aqui.
  const bytes = new Uint8Array(16);
  webCrypto.getRandomValues(bytes);
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}
