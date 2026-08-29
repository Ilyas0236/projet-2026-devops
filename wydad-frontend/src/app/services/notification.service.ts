import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { environment } from '../../environments/environment';

/**
 * Cloche de notification — service front.
 *
 * Le composant {@link NotificationBellComponent} s'abonne à :
 * <ul>
 *   <li>{@link unreadCount$}, signal rafraîchi toutes les 60s, pour le badge ;</li>
 *   <li>{@link loadRecent()}, pour le popover (10 dernières notifs).</li>
 * </ul>
 *
 * Les endpoints utilisés existent déjà côté back :
 * <ul>
 *   <li>GET /api/notification/user/{id}/unread/count → nombre non lues</li>
 *   <li>GET /api/notification/user/{id} → liste triée desc (limitée à 10 côté front)</li>
 *   <li>PATCH /api/notification/{id}/read → marquer comme lu</li>
 * </ul>
 */
@Injectable({ providedIn: 'root' })
export class NotificationService {

  private http = inject(HttpClient);
  private base = environment.apiBaseUrl + '/notification';

  /** Compte des non lues. Retourne 0 si l'utilisateur n'est pas loggué. */
  getUnreadCount(userId: number | null): Observable<number> {
    if (!userId) return of(0);
    return this.http
      .get<number>(`${this.base}/user/${userId}/unread/count`)
      .pipe(catchError(() => of(0)));
  }

  /**
   * Dix dernières notifications (toutes statuts). Le popover garde la liste
   * complète reçue ; le badge non-lues est calculé séparément.
   */
  getRecent(userId: number | null, limit = 10): Observable<NotificationItem[]> {
    if (!userId) return of([]);
    return this.http
      .get<NotificationItem[]>(`${this.base}/user/${userId}`)
      .pipe(
        map(list => (list || []).slice(0, limit)),
        catchError(() => of([]))
      );
  }

  markAsRead(id: number): Observable<void> {
    return this.http
      .patch<void>(`${this.base}/${id}/read`, {})
      .pipe(catchError(() => of(void 0)));
  }
}

export interface NotificationItem {
  id: number;
  userId: number;
  type: 'IN_APP' | 'EMAIL' | 'SMS' | string;
  title: string;
  message: string;
  targetUrl: string | null;
  status: 'UNREAD' | 'READ' | 'SENT' | string;
  createdAt: string;
}
