import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { BehaviorSubject, of } from 'rxjs';
import { TeamChatComponent } from './team-chat.component';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { ToastService } from '../../services/toast.service';
import { TeamChatService } from '../../services/team-chat.service';

describe('TeamChatComponent', () => {
  let component: TeamChatComponent;
  let fixture: ComponentFixture<TeamChatComponent>;
  let apiSpy: jasmine.SpyObj<ApiService>;
  let authSpy: jasmine.SpyObj<AuthService>;
  let chatSpy: jasmine.SpyObj<TeamChatService>;

  const MSG_COACH = {
    id: 1, sportType: 'FOOTBALL', category: 'U19',
    senderUserId: 702, senderName: 'Coach', senderRole: 'ENTRAINEUR',
    content: 'Concentration demain 10h', createdAt: new Date().toISOString()
  };
  const MSG_ME = {
    id: 2, sportType: 'FOOTBALL', category: 'U19',
    senderUserId: 601, senderName: 'Moi', senderRole: 'JOUEUR',
    content: 'Présent !', createdAt: new Date().toISOString()
  };

  beforeEach(async () => {
    apiSpy = jasmine.createSpyObj('ApiService',
      ['getTeamChatHistory', 'getTeamChatMembers', 'sendTeamChatMessage']);
    apiSpy.getTeamChatHistory.and.returnValue(of([MSG_COACH]));
    apiSpy.getTeamChatMembers.and.returnValue(of([]));
    apiSpy.sendTeamChatMessage.and.returnValue(of({}));

    authSpy = jasmine.createSpyObj('AuthService', ['getCurrentUserId']);
    authSpy.getCurrentUserId.and.returnValue(601);

    chatSpy = jasmine.createSpyObj('TeamChatService',
      ['connect', 'send', 'presence', 'disconnect']);
    // connectionState$ est une propriété, pas une méthode : on fournit un BehaviorSubject.
    (chatSpy as any).connectionState$ = new BehaviorSubject<'OPEN' | 'CONNECTING' | 'CLOSED'>('OPEN');
    chatSpy.connect.and.returnValue(of());

    await TestBed.configureTestingModule({
      imports: [TeamChatComponent]
    }).overrideProvider(ApiService, { useValue: apiSpy })
      .overrideProvider(AuthService, { useValue: authSpy })
      .overrideProvider(TeamChatService, { useValue: chatSpy })
      .overrideProvider(ToastService, { useValue: jasmine.createSpyObj('ToastService', ['success', 'error']) })
      .compileComponents();

    fixture = TestBed.createComponent(TeamChatComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('sportType', 'FOOTBALL');
    fixture.componentRef.setInput('category', 'U19');
    fixture.detectChanges();
  });

  it('charge l historique et les membres au demarrage', () => {
    expect(apiSpy.getTeamChatHistory).toHaveBeenCalledWith('FOOTBALL', 'U19');
    expect(component.messages.length).toBe(1);
    expect(component.loadingHistory).toBeFalse();
  });

  it('identifie ses propres messages', () => {
    expect(component.isMine(MSG_ME)).toBeTrue();
    expect(component.isMine(MSG_COACH)).toBeFalse();
  });

  it('refuse d envoyer vide ou trop long', () => {
    component.draft = '   ';
    expect(component.canSend).toBeFalse();
    component.draft = 'x'.repeat(501);
    expect(component.canSend).toBeFalse();
    expect(chatSpy.send).not.toHaveBeenCalled();
  });

  it('envoie via STOMP quand en ligne', fakeAsync(() => {
    component.draft = 'Allez Wydad';
    expect(component.canSend).toBeTrue();
    component.send();
    // Le brouillon est vidé après le délai de vérification d'écho (1,5 s).
    tick(1600);
    expect(chatSpy.send).toHaveBeenCalledWith('FOOTBALL', 'U19', 'Allez Wydad');
    expect(component.draft).toBe('');
    expect(component.sending).toBeFalse();
  }));

  it('filtre les messages des autres groupes', () => {
    const other = { ...MSG_COACH, category: 'PRO' };
    component.onIncomingMessage(other);
    expect(component.messages.length).toBe(1); // inchangé
    component.onIncomingMessage({ ...MSG_ME, id: 3 });
    expect(component.messages.length).toBe(2);
  });
});
