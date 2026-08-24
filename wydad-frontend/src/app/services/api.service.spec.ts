import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ApiService } from './api.service';
import { environment } from '../../environments/environment';

/**
 * Tests unitaires du ApiService — contrat HTTP (URL, verbe, corps, params).
 * Technique ISTQB : vérification des interactions ; chaque endpoint est une
 * classe d'équivalence « appel correctement formé ». Un échantillon
 * représentatif de chaque famille d'endpoints est couvert.
 */
describe('ApiService', () => {
  let service: ApiService;
  let httpMock: HttpTestingController;
  const base = environment.apiBaseUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(ApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('getArticles appelle GET /content/articles', () => {
    service.getArticles().subscribe();
    const req = httpMock.expectOne(`${base}/content/articles`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('updateMatchResult envoie les scores en query params', () => {
    service.updateMatchResult(42, 3, 1).subscribe();
    const req = httpMock.expectOne(`${base}/content/matches/42/result?scoreWydad=3&scoreAdversaire=1`);
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  it('deleteArticle appelle DELETE /content/articles/:id', () => {
    service.deleteArticle(9).subscribe();
    const req = httpMock.expectOne(`${base}/content/articles/9`);
    expect(req.request.method).toBe('DELETE');
    req.flush({});
  });

  it('createReclamation poste subject/title/description', () => {
    const payload = { subject: 'SHOP', title: 'Colis perdu', description: 'RIEN REÇU' };
    let captured: any;
    service.createReclamation(payload).subscribe(r => (captured = r));
    const req = httpMock.expectOne(`${base}/content/reclamations`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush({ id: 1 });
    expect(captured.id).toBe(1);
  });

  it('getMyPreferences / updateMyPreferences pointent sur le même endpoint', () => {
    service.getMyPreferences().subscribe();
    httpMock.expectOne(`${base}/notification/preferences`).flush({ emailEnabled: true });

    service.updateMyPreferences({ emailEnabled: false, pushEnabled: true, inAppEnabled: true }).subscribe();
    const req = httpMock.expectOne(`${base}/notification/preferences`);
    expect(req.request.method).toBe('PUT');
    req.flush({ emailEnabled: false });
  });
});
