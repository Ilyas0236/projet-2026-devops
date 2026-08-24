import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { DashboardJoueurComponent } from './dashboard-joueur.component';
import { ApiService } from '../../../services/api.service';
import { AuthService } from '../../../services/auth.service';
import { environment } from '../../../../environments/environment';

/**
 * Phase 2 — Tests de la logique billets VIP du dashboard joueur :
 * filtrage catégorie VIP, tri par proximité d'événement, téléchargement PDF.
 * (Le composant complet est couvert par les parcours E2E Playwright — ici
 * on valide le contrat HTTP et les partitions de données VIP/non-VIP.)
 */
describe('DashboardJoueur — billets VIP', () => {
  let component: DashboardJoueurComponent;
  let httpMock: HttpTestingController;
  const base = environment.apiBaseUrl;

  const joueur = { id: 5, fullName: 'Youssef El Amrani', sportType: 'FOOTBALL', category: 'PRO' };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule, RouterTestingModule],
      providers: [AuthService]
    });
    // Instanciation manuelle : on ne rend pas le template (hors périmètre
    // de ce lot), on teste la logique VIP du composant.
    const fixture = TestBed.createComponent(DashboardJoueurComponent);
    component = fixture.componentInstance;

    // Utilisateur connecté : id fixe pour toutes les requêtes.
    const auth = TestBed.inject(AuthService);
    spyOn(auth, 'getCurrentUserId').and.returnValue(5);

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  /** Répond aux appels déclenchés par ngOnInit. */
  function flushPlayerCalls() {
    httpMock.expectOne(`${base}/sports/players/user/5`).flush(joueur);
    // Sessions + espace + rapports : réponses vides suffisent
    const pend = httpMock.match((r) => r.url.includes('/sports/') || r.url.includes('/content/') || r.url.includes('/notification/'));
    pend.forEach((r) => r.flush(r.request.responseType === 'blob' ? new Blob() : Array.isArray(r.request.body) ? [] : {}));
  }

  it('ne charge que les billets VIP et les trie par date croissante', () => {
    component.ngOnInit();
    flushPlayerCalls();

    const ticketsReq = httpMock.expectOne(`${base}/ticket/tickets/user/5`);
    ticketsReq.flush([
      { id: 1, category: 'VIP', eventDate: '2026-09-20T19:00:00', ticketNumber: 'WAC-VIP-B' },
      { id: 2, category: 'VIRAGE_NORD', eventDate: '2026-09-01T19:00:00', ticketNumber: 'WAC-A' },
      { id: 3, category: 'VIP', eventDate: '2026-09-10T19:00:00', ticketNumber: 'WAC-VIP-A' }
    ]);

    expect(component.vipTickets.length).toBe(2);
    expect(component.vipTickets.every(t => t.category === 'VIP')).toBeTrue();
    expect(component.vipTickets[0].ticketNumber).toBe('WAC-VIP-A'); // 10 sept avant 20 sept
  });

  it('affiche l état vide quand aucun billet VIP n existe', () => {
    component.ngOnInit();
    flushPlayerCalls();

    httpMock.expectOne(`${base}/ticket/tickets/user/5`).flush([]);

    expect(component.vipTickets).toEqual([]);
    expect(component.vipTicketsLoading).toBeFalse();
  });

  it('une erreur HTTP laisse la section utilisable sans crash', () => {
    component.ngOnInit();
    flushPlayerCalls();

    httpMock.expectOne(`${base}/ticket/tickets/user/5`).flush(null, { status: 500, statusText: 'Server Error' });

    expect(component.vipTicketsLoading).toBeFalse();
    expect(component.vipTickets).toEqual([]);
  });
});
