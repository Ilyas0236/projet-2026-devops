import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { MyCallsComponent } from './my-calls.component';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { ToastService } from '../../services/toast.service';

describe('MyCallsComponent', () => {
  let component: MyCallsComponent;
  let fixture: ComponentFixture<MyCallsComponent>;
  let apiSpy: jasmine.SpyObj<ApiService>;
  let authSpy: jasmine.SpyObj<AuthService>;
  let toastSpy: jasmine.SpyObj<ToastService>;

  const CALL_ORGANIZER = {
    id: 1, title: 'Briefing avant match', roomName: 'wac-call-abcd1234',
    organizerUserId: 9, organizerName: 'Moi', status: 'PROGRAMME',
    scheduledAt: '2026-08-26T10:00:00', durationMinutes: 30,
    participantUserIds: [8, 9],
  };
  const CALL_PARTICIPANT = {
    id: 2, title: 'Réunion premium', roomName: 'wac-call-efgh5678',
    organizerUserId: 11, organizerName: 'Président', status: 'PROGRAMME',
    scheduledAt: null, durationMinutes: 45,
    participantUserIds: [11, 9],
  };

  beforeEach(async () => {
    apiSpy = jasmine.createSpyObj('ApiService',
      ['getMyCalls', 'getCallToken', 'cancelCall', 'getMediaStatus']);
    authSpy = jasmine.createSpyObj('AuthService', ['decodeToken']);
    authSpy.decodeToken.and.returnValue({ sub: '9', role: 'JOUEUR' });
    toastSpy = jasmine.createSpyObj('ToastService', ['success', 'error']);

    await TestBed.configureTestingModule({
      imports: [MyCallsComponent]
    }).overrideProvider(ApiService, { useValue: apiSpy })
      .overrideProvider(AuthService, { useValue: authSpy })
      .overrideProvider(ToastService, { useValue: toastSpy })
      .compileComponents();

    fixture = TestBed.createComponent(MyCallsComponent);
    component = fixture.componentInstance;
  });

  it('charge mon agenda et le statut média au demarrage', () => {
    apiSpy.getMyCalls.and.returnValue(of([CALL_ORGANIZER, CALL_PARTICIPANT]));
    apiSpy.getMediaStatus.and.returnValue(of({ configured: true }));
    component.ngOnInit();
    expect(component.calls().length).toBe(2);
    expect(component.mediaConfigured).toBeTrue();
    expect(component.me).toBe(9);
  });

  it('affiche un avertissement quand le media n est pas configure', () => {
    apiSpy.getMyCalls.and.returnValue(of([]));
    apiSpy.getMediaStatus.and.returnValue(of({ configured: false }));
    component.ngOnInit();
    fixture.detectChanges();
    const warn = fixture.nativeElement.querySelector('.bg-amber-50');
    expect(warn).withContext('bandeau indisponible affiché').toBeTruthy();
  });

  it('identifie le badge organisateur sur mes propres appels seulement', () => {
    apiSpy.getMyCalls.and.returnValue(of([CALL_ORGANIZER, CALL_PARTICIPANT]));
    apiSpy.getMediaStatus.and.returnValue(of({ configured: true }));
    component.ngOnInit();
    fixture.detectChanges();
    const badges = fixture.nativeElement.querySelectorAll('.bg-wydad-red.rounded-full');
    // Un seul badge « Organisateur » (call id 1) — l'autre est un simple statut.
    expect(badges.length).toBe(1);
    expect(badges[0].textContent).toContain('Organisateur');
  });

  it('annule un appel dont je suis organisateur et recharge l agenda', () => {
    apiSpy.getMyCalls.and.returnValues(of([CALL_ORGANIZER]), of([CALL_ORGANIZER]));
    apiSpy.getMediaStatus.and.returnValue(of({ configured: true }));
    apiSpy.cancelCall.and.returnValue(of({}));
    component.ngOnInit();

    component.cancel(CALL_ORGANIZER);
    expect(apiSpy.cancelCall).toHaveBeenCalledWith(1);
    expect(toastSpy.success).toHaveBeenCalled();
    expect(apiSpy.getMyCalls).toHaveBeenCalledTimes(2); // initial + après annulation
  });

  it('signale une erreur si le chargement echoue', () => {
    apiSpy.getMyCalls.and.returnValue(throwError(() => new Error('boom')));
    apiSpy.getMediaStatus.and.returnValue(of({ configured: true }));
    component.ngOnInit();
    expect(toastSpy.error).toHaveBeenCalled();
    expect(component.loading).toBeFalse();
  });
});
