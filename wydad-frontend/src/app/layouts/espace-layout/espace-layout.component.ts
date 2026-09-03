import { Component, inject } from '@angular/core';
import { Router, RouterOutlet } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { NotificationBellComponent } from '../../components/notification-bell/notification-bell.component';
import { ConfirmDialogComponent } from '../../components/confirm-dialog/confirm-dialog.component';

/**
 * Layout des espaces connectés (joueur, staff/entraîneur, président,
 * journaliste, parent) — remplace le header public par un bandeau fin :
 * logo club + nom de l'espace + cloche de notification + déconnexion (seule
 * sortie possible vers le site public).
 */
@Component({
  selector: 'app-espace-layout',
  standalone: true,
  imports: [RouterOutlet, NotificationBellComponent, ConfirmDialogComponent],
  templateUrl: './espace-layout.component.html',
  styleUrls: ['./espace-layout.component.scss']
})
export class EspaceLayoutComponent {
  private auth = inject(AuthService);
  private router = inject(Router);

  /** Libellé de l'espace courant selon le rôle du compte. */
  get espaceLabel(): string {
    switch (this.auth.getTokenRole()) {
      case 'JOUEUR': return 'Espace Joueur';
      case 'ENTRAINEUR': return 'Espace Entraîneur';
      case 'STAFF': return 'Espace Staff';
      case 'PRESIDENT': return 'Espace Président';
      case 'JOURNALISTE': return 'Espace Journaliste';
      case 'PARENT': return 'Espace Parent';
      default: return 'Mon espace';
    }
  }

  get firstName(): string | null {
    const n = localStorage.getItem('wydad_first_name');
    return n && n !== 'null' ? n : null;
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
