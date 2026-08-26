import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../../services/api.service';
import { ToastService } from '../../../services/toast.service';

/**
 * §9 — File de publication des feuilles de match : l'Admin voit les feuilles
 * SOUMISES par les entraîneurs, les consulte (titulaires/remplaçants) puis
 * PUBLIE sur le site public — ou REFUSE avec motif.
 */
@Component({
  selector: 'app-admin-convocations',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-6">
      <div>
        <h2 class="text-2xl font-display font-bold uppercase tracking-wider text-white">Feuilles de Match</h2>
        <p class="text-sm text-gray-400 mt-1">
          Feuilles de convocation soumises par les entraîneurs — publiez-les sur le site public ou refusez-les.
        </p>
      </div>

      <div *ngIf="loading" class="flex justify-center py-20">
        <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-wydad-red"></div>
      </div>

      <div *ngIf="!loading && feuilles.length === 0"
           class="bg-white/5 border border-white/10 rounded-lg p-12 text-center text-gray-400 text-sm uppercase tracking-widest">
        Aucune feuille en attente de validation.
      </div>

      <!-- Une carte par match soumis -->
      <div *ngFor="let f of feuilles" class="bg-white/5 border border-white/10 rounded-lg overflow-hidden">
        <div class="flex flex-wrap justify-between items-center gap-3 px-5 py-4 bg-black/30 border-b border-white/10">
          <div>
            <p class="text-white font-bold uppercase tracking-wider text-sm">
              WAC vs {{ f.adversaire }}
              <span class="ml-2 px-2 py-0.5 bg-wydad-red/20 text-wydad-red rounded text-[10px] uppercase">{{ f.sportType }} {{ f.category }}</span>
            </p>
            <p class="text-gray-400 text-xs mt-1">
              {{ f.convocations.length }} joueur(s) · soumis le {{ (f.convocations[0]?.submittedAt | date:'dd/MM/yyyy HH:mm') || '—' }}
            </p>
          </div>
          <div class="flex gap-2">
            <button (click)="publish(f.matchId)" [disabled]="isProcessing === f.matchId"
                    class="admin-btn-primary !py-2 !px-4 disabled:opacity-50">
              {{ isProcessing === f.matchId ? '…' : 'Publier' }}
            </button>
            <button (click)="openReject(f)" class="admin-btn-ghost !py-2 !px-4">Refuser</button>
          </div>
        </div>

        <!-- Détail titulaires / remplaçants -->
        <div class="grid grid-cols-1 md:grid-cols-2 gap-6 p-5">
          <div>
            <h4 class="text-[11px] font-bold uppercase tracking-widest text-green-400 mb-3">Titulaires</h4>
            <ul class="space-y-2">
              <li *ngFor="let c of titulaires(f)" class="flex items-center gap-3 text-sm text-gray-200 bg-white/5 rounded-lg px-3 py-2">
                <span class="font-display font-black text-wydad-red w-8 text-center">{{ c.jerseyNumber ?? '-' }}</span>
                {{ c.joueurName }}
              </li>
              <li *ngIf="titulaires(f).length === 0" class="text-xs text-gray-500 italic">Aucun</li>
            </ul>
          </div>
          <div>
            <h4 class="text-[11px] font-bold uppercase tracking-widest text-yellow-400 mb-3">Remplaçants</h4>
            <ul class="space-y-2">
              <li *ngFor="let c of remplacants(f)" class="flex items-center gap-3 text-sm text-gray-200 bg-white/5 rounded-lg px-3 py-2">
                <span class="font-display font-black text-gray-400 w-8 text-center">{{ c.jerseyNumber ?? '-' }}</span>
                {{ c.joueurName }}
              </li>
              <li *ngIf="remplacants(f).length === 0" class="text-xs text-gray-500 italic">Aucun</li>
            </ul>
          </div>
        </div>

        <!-- Motif de refus affiché si présent -->
        <p *ngIf="f.refusMotif" class="px-5 pb-4 text-xs text-red-400">{{ f.refusMotif }}</p>
      </div>

      <!-- Modal refus avec motif -->
      <div *ngIf="showRejectModal" class="admin-overlay">
        <div class="admin-modal max-w-md">
          <div class="admin-modal-header"><h3>Refuser la feuille — WAC vs {{ rejectTarget?.adversaire }}</h3></div>
          <div class="admin-modal-body">
            <div class="admin-field">
              <label class="admin-label">Motif du refus<span class="req">*</span></label>
              <textarea [(ngModel)]="rejectReason" rows="3" maxlength="500"
                        placeholder="Ex : feuille incomplète, joueur inapte convoqué…" class="admin-input resize-none"></textarea>
            </div>
          </div>
          <div class="admin-modal-footer">
            <button (click)="showRejectModal = false" class="admin-btn-ghost">Annuler</button>
            <button (click)="confirmReject()" [disabled]="!rejectReason.trim()" class="admin-btn-primary disabled:opacity-50">Refuser</button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AdminConvocationsComponent implements OnInit {
  feuilles: any[] = [];   // groupées par matchId côté composant
  loading = true;
  isProcessing: number | null = null;

  showRejectModal = false;
  rejectTarget: any = null;
  rejectReason = '';

  constructor(private api: ApiService, private toast: ToastService) {}

  ngOnInit() {
    this.load();
  }

  load() {
    this.loading = true;
    this.api.getSubmittedSheets().subscribe({
      next: (rows) => {
        // Regroupe les lignes SOUMISES par match pour une carte = un match.
        const byMatch = new Map<number, any>();
        for (const r of rows) {
          if (!byMatch.has(r.matchId)) {
            byMatch.set(r.matchId, {
              matchId: r.matchId,
              adversaire: r.adversaire || `Match #${r.matchId}`,
              sportType: r.sportType,
              category: r.category,
              convocations: []
            });
          }
          byMatch.get(r.matchId).convocations.push(r);
        }
        this.feuilles = [...byMatch.values()];
        this.loading = false;
      },
      error: () => {
        this.toast.error('Chargement des feuilles soumises impossible');
        this.loading = false;
      }
    });
  }

  titulaires(f: any) {
    return f.convocations.filter((c: any) => c.playerRole === 'TITULAIRE');
  }

  remplacants(f: any) {
    return f.convocations.filter((c: any) => c.playerRole === 'REMPLACANT');
  }

  publish(matchId: number) {
    this.isProcessing = matchId;
    this.api.publishMatchSheet(matchId).subscribe({
      next: () => {
        this.toast.success('Feuille publiée sur le site public');
        this.isProcessing = null;
        this.load();
      },
      error: (err) => {
        this.toast.error(err?.error?.message || 'Publication impossible');
        this.isProcessing = null;
      }
    });
  }

  openReject(f: any) {
    this.rejectTarget = f;
    this.rejectReason = '';
    this.showRejectModal = true;
  }

  confirmReject() {
    if (!this.rejectTarget || !this.rejectReason.trim()) { return; }
    this.isProcessing = this.rejectTarget.matchId;
    this.api.rejectMatchSheet(this.rejectTarget.matchId, this.rejectReason.trim()).subscribe({
      next: () => {
        this.toast.success('Feuille renvoyée à l\'entraîneur avec le motif');
        this.showRejectModal = false;
        this.isProcessing = null;
        this.load();
      },
      error: (err) => {
        this.toast.error(err?.error?.message || 'Refus impossible');
        this.isProcessing = null;
      }
    });
  }
}
