/**
 * Polyfill minimal pour sockjs-client (chat d'équipe, Phase 4) :
 * la lib CommonJS référence `global` et `process`, inexistants dans le
 * navigateur. Doit être listé AVANT zone.js dans angular.json → polyfills.
 */
(globalThis as any).global = globalThis;
if (!(globalThis as any).process) {
  (globalThis as any).process = { env: {} };
}
