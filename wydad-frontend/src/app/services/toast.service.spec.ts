import { TestBed } from '@angular/core/testing';
import { ToastService } from './toast.service';

/**
 * Tests unitaires du ToastService.
 * Technique ISTQB : partition en classes d'équivalence sur le type de toast
 * (success/error/info), frontière sur la durée d'affichage (auto-dismiss),
 * test d'état (dismiss d'un id inexistant ne casse pas la liste).
 */
describe('ToastService', () => {
  let service: ToastService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ToastService);
  });

  it('est créé avec aucune notification', () => {
    expect(service.toasts()).toEqual([]);
  });

  it('show() ajoute un toast avec un id unique croissant', () => {
    service.show('info', 'message 1');
    service.show('error', 'message 2');

    const list = service.toasts();
    expect(list.length).toBe(2);
    expect(list[0].id).not.toBe(list[1].id);
    expect(list[1].type).toBe('error');
    expect(list[1].message).toBe('message 2');
  });

  it('les helpers success/error/info créent le bon type', () => {
    service.success('ok');
    service.error('ko');
    service.info('ns');

    const types = service.toasts().map(t => t.type);
    expect(types).toEqual(['success', 'error', 'info']);
  });

  it('dismiss() retire uniquement le toast visé', () => {
    service.success('a');
    service.error('b');
    const idB = service.toasts()[1].id;

    service.dismiss(idB);

    const list = service.toasts();
    expect(list.length).toBe(1);
    expect(list[0].message).toBe('a');
  });

  it('dismiss() avec un id inconnu laisse la liste inchangée', () => {
    service.success('a');
    service.dismiss(9999);
    expect(service.toasts().length).toBe(1);
  });

  it('un toast disparaît seul après sa durée (durée réduite pour le test)', done => {
    jasmine.clock().install();
    service.show('info', 'temporaire', 1000);
    expect(service.toasts().length).toBe(1);

    jasmine.clock().tick(1001);
    expect(service.toasts().length).toBe(0);
    jasmine.clock().uninstall();
    done();
  });
});
