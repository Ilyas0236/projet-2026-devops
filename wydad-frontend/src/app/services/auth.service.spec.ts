import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { AuthService } from './auth.service';
import { environment } from '../../environments/environment';

/**
 * Tests unitaires du AuthService (partie client).
 * Technique ISTQB : partition d'équivalence sur les payloads JWT
 * (valide / expiré / illisible), frontières temporelles sur exp,
 * vérification des interactions HTTP (URL, verbe, corps).
 */
describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;
  const baseUrl = `${environment.apiBaseUrl}/auth`;

  /** Fabrique un JWT factice (signature non vérifiée côté client). */
  function makeToken(payload: object): string {
    const b64 = (obj: object) =>
      btoa(JSON.stringify(obj)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
    return `${b64({ alg: 'HS256', typ: 'JWT' })}.${b64(payload)}.sig`;
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule]
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
    localStorage.clear();
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  // ---------- login / logout ----------

  it('login stocke le token et les infos utilisateur dans localStorage', () => {
    service.login('a@wac.ma', 'secret123').subscribe();

    const req = httpMock.expectOne(`${baseUrl}/login`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'a@wac.ma', password: 'secret123' });
    req.flush({
      accessToken: makeToken({ sub: 'a@wac.ma', role: 'ADHERENT' }),
      id: 7, email: 'a@wac.ma', firstName: 'Ali', lastName: 'T', role: 'ADHERENT'
    });

    expect(localStorage.getItem('wydad_token')).toBeTruthy();
    expect(localStorage.getItem('wydad_email')).toBe('a@wac.ma');
    expect(service.currentUserValue).toBe('a@wac.ma');
  });

  it('logout efface toutes les clés et réinitialise l’utilisateur courant', () => {
    localStorage.setItem('wydad_token', 'x');
    localStorage.setItem('wydad_email', 'a@wac.ma');

    service.logout();

    expect(localStorage.getItem('wydad_token')).toBeNull();
    expect(localStorage.getItem('wydad_email')).toBeNull();
    expect(service.currentUserValue).toBeNull();
  });

  // ---------- decodeToken : partitions ----------

  it('decodeToken décode un payload JWT standard', () => {
    localStorage.setItem('wydad_token', makeToken({ role: 'ADMIN', exp: 9999999999 }));
    expect(service.decodeToken()?.role).toBe('ADMIN');
  });

  it('decodeToken retourne null sans token', () => {
    expect(service.decodeToken()).toBeNull();
  });

  it('decodeToken retourne null pour un token illisible', () => {
    localStorage.setItem('wydad_token', 'pas-un-jwt');
    expect(service.decodeToken()).toBeNull();
  });

  // ---------- isTokenValid : frontières temporelles ----------

  it('isTokenValid est faux sans token ni exp', () => {
    expect(service.isTokenValid()).toBeFalse();
    localStorage.setItem('wydad_token', makeToken({ role: 'X' }));
    expect(service.isTokenValid()).toBeFalse();
  });

  it('isTokenValid est vrai avant expiration, faux après (frontière)', () => {
    const now = Date.now();
    // +1 h : valide
    localStorage.setItem('wydad_token', makeToken({ exp: Math.floor((now + 3600_000) / 1000) }));
    expect(service.isTokenValid()).toBeTrue();
    // -1 s : expiré
    localStorage.setItem('wydad_token', makeToken({ exp: Math.floor(now / 1000) - 1 }));
    expect(service.isTokenValid()).toBeFalse();
  });

  // ---------- getTokenRole : source de vérité = JWT ----------

  it('getTokenRole lit le rôle depuis le JWT en priorité', () => {
    localStorage.setItem('wydad_token', makeToken({ role: 'ENTRAINEUR' }));
    localStorage.setItem('wydad_role', 'ADHERENT');
    expect(service.getTokenRole()).toBe('ENTRAINEUR');
  });

  it('getTokenRole retombe sur localStorage si le JWT est absent', () => {
    localStorage.setItem('wydad_role', 'JOURNALISTE');
    expect(service.getTokenRole()).toBe('JOURNALISTE');
  });
});
