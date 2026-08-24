import { TestBed } from '@angular/core/testing';
import { ConfirmService } from './confirm.service';

/**
 * Tests unitaires du ConfirmService (dialogue modale promise-based).
 * Technique ISTQB : transitions d'état (null -> pending -> résolu),
 * cas limite : deux confirm() successifs — le premier est résolu à false.
 */
describe('ConfirmService', () => {
  let service: ConfirmService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ConfirmService);
  });

  it('démarre sans dialogue en attente', () => {
    expect(service.pending()).toBeNull();
  });

  it('accept() résout la promesse à true et referme le dialogue', async () => {
    const promise = service.confirm({ title: 'T', message: 'M' });
    expect(service.pending()).not.toBeNull();

    service.accept();
    await expectAsync(promise).toBeResolvedTo(true);
    expect(service.pending()).toBeNull();
  });

  it('reject() résout la promesse à false et referme le dialogue', async () => {
    const promise = service.confirm({ title: 'T', message: 'M' });

    service.reject();
    await expectAsync(promise).toBeResolvedTo(false);
    expect(service.pending()).toBeNull();
  });

  it('un second confirm() résout le premier à false (un seul dialogue à la fois)', async () => {
    const first = service.confirm({ title: 'Premier', message: 'M' });
    const second = service.confirm({ title: 'Second', message: 'M' });

    // Le pending actif est désormais le second.
    expect(service.pending()?.title).toBe('Second');

    await expectAsync(first).toBeResolvedTo(false);

    service.reject();
    await expectAsync(second).toBeResolvedTo(false);
  });

  it('les options danger/labels sont transmises au dialogue', () => {
    void service.confirm({
      title: 'Supprimer',
      message: 'Sûr ?',
      confirmLabel: 'Oui, supprimer',
      danger: true
    });
    const p = service.pending();
    expect(p?.danger).toBeTrue();
    expect(p?.confirmLabel).toBe('Oui, supprimer');
  });
});
