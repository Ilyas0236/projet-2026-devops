import {
  Component, ElementRef, HostListener, OnDestroy, OnInit, inject, signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { Subject, interval, of } from 'rxjs';
import { catchError, switchMap, takeUntil } from 'rxjs/operators';
import { NotificationService, NotificationItem } from '../../services/notification.service';

/**
 * Cloche de notification universelle.
 *
 * - Affiche un badge avec le nombre de notifications non lues.
 * - Au clic, ouvre un popover listant les 10 dernières notifications.
 * - Au clic sur une ligne : marque comme lu, puis navigue vers {@code targetUrl}
 *   si défini (sinon ferme simplement le popover).
 * - Poll 60s pour le compteur (interval choisi pour limiter la charge VM 1 Go).
 * - Thème auto-détecté via la classe `data-theme` posée sur <html> ou
 *   `<body>`. Défaut : thème sombre (compatible ADMIN).
 *
 * Le composant est **silencieux en cas d'erreur réseau** (catchError → 0 notif).
 * Aucun crash UI si l'API est momentanément indisponible.
 */
@Component({
  selector: 'app-notification-bell',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="bell-wrap">
      <button type="button"
              class="bell-btn"
              [class.theme-light]="isLightTheme()"
              [class.theme-dark]="!isLightTheme()"
              [attr.aria-label]="'Notifications' + (unreadCount() > 0 ? ' (' + unreadCount() + ' non lues)' : '')"
              [class.has-unread]="unreadCount() > 0"
              (click)="toggle()">
        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24"
             fill="none" stroke="currentColor" stroke-width="2"
             stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"></path>
          <path d="M13.73 21a2 2 0 0 1-3.46 0"></path>
        </svg>
        @if (unreadCount() > 0) {
          <span class="bell-badge" aria-hidden="true">{{ unreadCount() > 99 ? '99+' : unreadCount() }}</span>
        }
      </button>

      @if (open()) {
        <div class="bell-popover"
             [class.theme-light]="isLightTheme()"
             [class.theme-dark]="!isLightTheme()"
             role="dialog" aria-label="Notifications">
          <div class="bell-popover-header">
            <span class="bell-popover-title">Notifications</span>
            @if (unreadCount() > 0) {
              <button type="button" class="bell-popover-action" (click)="refresh()">Actualiser</button>
            }
          </div>
          <div class="bell-popover-body">
            @if (loading() && recent().length === 0) {
              <div class="bell-empty">Chargement…</div>
            } @else if (recent().length === 0) {
              <div class="bell-empty">
                <svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 24 24"
                     fill="none" stroke="currentColor" stroke-width="1.5"
                     stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                  <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"></path>
                  <path d="M13.73 21a2 2 0 0 1-3.46 0"></path>
                </svg>
                <p>Aucune notification</p>
              </div>
            } @else {
              @for (n of recent(); track n.id) {
                <button type="button"
                        class="bell-item"
                        [class.unread]="n.status === 'UNREAD'"
                        (click)="onItemClick(n)">
                  <div class="bell-item-icon">
                    <span class="dot" [class.unread-dot]="n.status === 'UNREAD'"></span>
                  </div>
                  <div class="bell-item-content">
                    <div class="bell-item-title">{{ n.title }}</div>
                    <div class="bell-item-msg">{{ n.message }}</div>
                    <div class="bell-item-time">{{ formatTime(n.createdAt) }}</div>
                  </div>
                </button>
              }
            }
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .bell-wrap { position: relative; display: inline-flex; }

    .bell-btn {
      position: relative;
      width: 40px; height: 40px;
      display: inline-flex; align-items: center; justify-content: center;
      border-radius: 10px;
      border: 1px solid transparent;
      background: transparent;
      cursor: pointer;
      transition: all 0.2s;
    }
    .bell-btn.theme-light { color: #1a1a1a; }
    .bell-btn.theme-light:hover { background: rgba(0,0,0,0.06); border-color: rgba(0,0,0,0.08); }
    .bell-btn.theme-dark  { color: #d1d5db; }
    .bell-btn.theme-dark:hover  { background: rgba(255,255,255,0.06); border-color: rgba(255,255,255,0.1); color: #fff; }
    .bell-btn.has-unread { animation: bellShake 0.6s ease-out 0s 1; }

    .bell-badge {
      position: absolute;
      top: 4px; right: 4px;
      min-width: 18px; height: 18px;
      padding: 0 5px;
      border-radius: 9px;
      background: linear-gradient(135deg, #dc143c, #b0102e);
      color: #fff;
      font-size: 10px;
      font-weight: 700;
      line-height: 18px;
      text-align: center;
      box-shadow: 0 0 0 2px currentColor, 0 0 8px rgba(220,20,60,0.5);
    }
    .bell-btn.theme-light .bell-badge { box-shadow: 0 0 0 2px #fff, 0 0 8px rgba(220,20,60,0.5); }
    .bell-btn.theme-dark  .bell-badge { box-shadow: 0 0 0 2px #1f2937, 0 0 8px rgba(220,20,60,0.5); }

    @keyframes bellShake {
      0%,100% { transform: rotate(0); }
      20% { transform: rotate(-8deg); }
      40% { transform: rotate(6deg); }
      60% { transform: rotate(-4deg); }
      80% { transform: rotate(2deg); }
    }

    .bell-popover {
      position: absolute;
      top: calc(100% + 8px);
      right: 0;
      width: 360px;
      max-width: calc(100vw - 16px);
      max-height: 480px;
      border-radius: 14px;
      box-shadow: 0 20px 50px -10px rgba(0,0,0,0.4), 0 0 0 1px rgba(0,0,0,0.06);
      overflow: hidden;
      display: flex; flex-direction: column;
      z-index: 60;
      animation: popIn 0.15s ease-out;
    }
    @keyframes popIn {
      from { opacity: 0; transform: translateY(-4px) scale(0.97); }
      to   { opacity: 1; transform: translateY(0) scale(1); }
    }
    .bell-popover.theme-light { background: #ffffff; color: #1a1a1a; }
    .bell-popover.theme-dark  { background: #1f2937; color: #f3f4f6; }

    .bell-popover-header {
      display: flex; align-items: center; justify-content: space-between;
      padding: 14px 16px;
      border-bottom: 1px solid;
      font-family: 'Cinzel', serif;
    }
    .bell-popover.theme-light .bell-popover-header { border-color: rgba(0,0,0,0.08); }
    .bell-popover.theme-dark  .bell-popover-header { border-color: rgba(255,255,255,0.08); }
    .bell-popover-title { font-weight: 700; font-size: 14px; text-transform: uppercase; letter-spacing: 0.05em; }
    .bell-popover-action {
      background: none; border: none; cursor: pointer;
      font-size: 11px; text-transform: uppercase; letter-spacing: 0.05em;
      color: #dc143c; font-weight: 600;
    }
    .bell-popover-action:hover { text-decoration: underline; }

    .bell-popover-body {
      overflow-y: auto;
      max-height: 400px;
    }

    .bell-empty {
      display: flex; flex-direction: column; align-items: center; justify-content: center;
      gap: 8px;
      padding: 40px 16px;
      opacity: 0.6;
      font-size: 13px;
    }

    .bell-item {
      display: flex; gap: 12px;
      width: 100%;
      padding: 12px 16px;
      background: none;
      border: none;
      border-bottom: 1px solid;
      text-align: left;
      cursor: pointer;
      transition: background 0.15s;
      font: inherit;
      color: inherit;
    }
    .bell-popover.theme-light .bell-item { border-color: rgba(0,0,0,0.05); }
    .bell-popover.theme-dark  .bell-item { border-color: rgba(255,255,255,0.05); }
    .bell-popover.theme-light .bell-item:hover { background: rgba(0,0,0,0.04); }
    .bell-popover.theme-dark  .bell-item:hover { background: rgba(255,255,255,0.05); }
    .bell-item.unread { background: rgba(220,20,60,0.04); }

    .bell-item-icon { padding-top: 6px; }
    .bell-item-icon .dot {
      display: block; width: 8px; height: 8px; border-radius: 50%;
      background: rgba(0,0,0,0.15);
    }
    .bell-item-icon .dot.unread-dot { background: #dc143c; box-shadow: 0 0 6px rgba(220,20,60,0.6); }

    .bell-item-content { flex: 1; min-width: 0; }
    .bell-item-title { font-weight: 600; font-size: 13px; margin-bottom: 2px; }
    .bell-item-msg {
      font-size: 12px;
      opacity: 0.75;
      line-height: 1.4;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }
    .bell-item-time { font-size: 10px; opacity: 0.55; margin-top: 4px; }
  `]
})
export class NotificationBellComponent implements OnInit, OnDestroy {

  private notifications = inject(NotificationService);
  private router = inject(Router);
  private host = inject(ElementRef<HTMLElement>);

  /** ID utilisateur — null si pas connecté. */
  private userId: number | null = null;
  private destroy$ = new Subject<void>();
  private pollPeriodMs = 60_000;

  unreadCount = signal(0);
  recent = signal<NotificationItem[]>([]);
  loading = signal(false);
  open = signal(false);

  ngOnInit(): void {
    const idStr = localStorage.getItem('wydad_user_id');
    this.userId = idStr ? Number(idStr) : null;
    if (!this.userId) return;

    this.refresh();

    // Polling 60s. Le compteur est rafraîchi en silence (silencieux sur erreur).
    interval(this.pollPeriodMs)
      .pipe(
        takeUntil(this.destroy$),
        switchMap(() => this.notifications.getUnreadCount(this.userId))
      )
      .subscribe(count => this.unreadCount.set(count));
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  /** Ferme le popover au clic extérieur. */
  @HostListener('document:click', ['$event'])
  onDocClick(ev: MouseEvent): void {
    if (!this.open()) return;
    const target = ev.target as Node;
    if (!this.host.nativeElement.contains(target)) {
      this.open.set(false);
    }
  }

  /** Ferme à Échap. */
  @HostListener('document:keydown.escape')
  onEsc(): void {
    if (this.open()) this.open.set(false);
  }

  toggle(): void {
    const willOpen = !this.open();
    this.open.set(willOpen);
    if (willOpen) this.refresh();
  }

  refresh(): void {
    if (!this.userId) return;
    this.loading.set(true);
    this.notifications.getUnreadCount(this.userId)
      .pipe(catchError(() => of(0)))
      .subscribe(count => this.unreadCount.set(count));
    this.notifications.getRecent(this.userId, 10)
      .pipe(catchError(() => of([] as NotificationItem[])))
      .subscribe(list => {
        this.recent.set(list);
        this.loading.set(false);
      });
  }

  onItemClick(n: NotificationItem): void {
    this.open.set(false);
    if (n.status === 'UNREAD') {
      this.notifications.markAsRead(n.id).subscribe(() => {
        this.unreadCount.update(c => Math.max(0, c - 1));
        // Met à jour la ligne en local (statut → READ) sans refetch.
        this.recent.update(list => list.map(x => x.id === n.id ? { ...x, status: 'READ' } : x));
      });
    }
    if (n.targetUrl) {
      // Sanitize léger : refuse les targets externes (protocoles autres que /).
      const url = n.targetUrl.trim();
      if (url.startsWith('/') && !url.startsWith('//')) {
        this.router.navigateByUrl(url);
      }
    }
  }

  /** Format français court : "il y a 5 min", "12:34", "23/08". */
  formatTime(iso: string): string {
    if (!iso) return '';
    const d = new Date(iso);
    const now = new Date();
    const diff = (now.getTime() - d.getTime()) / 1000;
    if (diff < 60) return "à l'instant";
    if (diff < 3600) return `il y a ${Math.floor(diff / 60)} min`;
    if (diff < 86_400) return d.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
    return d.toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit' });
  }

  /**
   * Détecte le thème via les classes posés sur <html> ou <body>.
   * Le site public pose souvent `bg-paper-0` (Tailwind), on s'appuie
   * plutôt sur une convention : la classe `theme-light` ou `theme-dark`
   * est posée par les layouts, ou à défaut on regarde le `data-theme`.
   */
  isLightTheme(): boolean {
    if (typeof document === 'undefined') return false;
    const theme = document.documentElement.getAttribute('data-theme')
      || document.body.getAttribute('data-theme');
    if (theme === 'light') return true;
    if (theme === 'dark') return false;
    // Heuristique : si le body a un fond sombre (ADMIN), retour false.
    const bg = getComputedStyle(document.body).backgroundColor;
    if (!bg || bg === 'rgba(0, 0, 0, 0)' || bg === 'transparent') return true;
    const m = bg.match(/\d+/g);
    if (!m || m.length < 3) return true;
    const lum = (0.299 * +m[0] + 0.587 * +m[1] + 0.114 * +m[2]) / 255;
    return lum > 0.5;
  }
}
