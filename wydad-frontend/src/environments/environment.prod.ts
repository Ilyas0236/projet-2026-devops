export const environment = {
  production: true,
  // En production, le frontend est servi depuis le même domaine que la gateway
  // (reverse proxy) : on utilise des chemins relatifs.
  apiBaseUrl: '/api',
  mediaBaseUrl: ''
};
