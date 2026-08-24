import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ApiService } from '../../../services/api.service';
import { AuthService } from '../../../services/auth.service';

@Component({
  selector: 'app-dashboard-parent',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './dashboard-parent.component.html'
})
export class DashboardParentComponent implements OnInit {
  api = inject(ApiService);
  auth = inject(AuthService);

  children: any[] = [];
  loading = true;

  // Planning déplié par enfant (id -> séances du sport/catégorie de l'enfant)
  planningOpen: Record<number, boolean> = {};
  sessions: Record<number, any[]> = {};
  sessionsLoading: Record<number, boolean> = {};

  // État téléchargement des pièces justificatives
  docsLoading: Record<number, boolean> = {};
  message = '';
  messageKind: 'success' | 'error' = 'success';

  ngOnInit() {
    const parentId = this.auth.getCurrentUserId();
    if (parentId) {
      this.api.getAcademyChildrenByParent(parentId).subscribe({
        next: (data) => {
          this.children = data;
          this.loading = false;
        },
        error: (err) => {
          console.error(err);
          this.loading = false;
        }
      });
    } else {
      this.loading = false;
    }
  }

  /** Affiche/masque le planning des entraînements de la catégorie de l'enfant. */
  togglePlanning(child: any) {
    const id = child.id;
    this.planningOpen[id] = !this.planningOpen[id];
    if (!this.sessions[id]) this.loadSessions(child, id);
  }

  private loadSessions(child: any, id: number) {
    this.sessionsLoading[id] = true;
    this.api.getSessionsByCategory(child.sportType || 'FOOTBALL', child.category || child.level || '')
      .subscribe({
        next: (data) => {
          this.sessions[id] = data || [];
          this.sessionsLoading[id] = false;
        },
        error: () => {
          this.sessions[id] = [];
          this.sessionsLoading[id] = false;
        }
      });
  }

  /** Télécharge les pièces justificatives du dossier (acte de naissance + certificat médical).
   * Chaque pièce est indépendante : une pièce manquante n'empêche pas l'autre. */
  downloadDocuments(child: any) {
    const id = child.id;
    this.docsLoading[id] = true;
    forkJoin({
      birth: this.api.getAcademyDocumentBlob(id, 'BIRTH_CERTIFICATE').pipe(catchError(() => of(null))),
      medical: this.api.getAcademyDocumentBlob(id, 'MEDICAL_CERTIFICATE').pipe(catchError(() => of(null)))
    }).subscribe({
      next: ({ birth, medical }) => {
        let ok = false;
        if (birth) { this.saveBlob(birth as Blob, `acte-naissance-${this.slug(child.childFullName)}.pdf`); ok = true; }
        if (medical) { this.saveBlob(medical as Blob, `certificat-medical-${this.slug(child.childFullName)}.pdf`); ok = true; }
        this.docsLoading[id] = false;
        this.showMessage(ok ? 'Pièces justificatives téléchargées.' : 'Aucune pièce disponible pour ce dossier.', ok ? 'success' : 'error');
      },
      error: () => {
        this.docsLoading[id] = false;
        this.showMessage('Impossible de télécharger les pièces du dossier.', 'error');
      }
    });
  }

  private saveBlob(blob: Blob, filename: string) {
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    window.URL.revokeObjectURL(url);
  }

  private slug(name: string): string {
    return (name || 'enfant').toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/\s+/g, '-');
  }

  private showMessage(text: string, kind: 'success' | 'error') {
    this.message = text;
    this.messageKind = kind;
    setTimeout(() => { this.message = ''; }, 4000);
  }

  formatSessionDate(s: any): string {
    if (!s?.sessionDate) return '';
    const d = new Date(s.sessionDate);
    return isNaN(d.getTime()) ? '' : d.toLocaleDateString('fr-FR', { weekday: 'short', day: '2-digit', month: '2-digit' });
  }

  formatSessionTime(s: any): string {
    return s?.startTime ? String(s.startTime).slice(0, 5) : '';
  }
}
