import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../../services/api.service';
import { ToastService } from '../../../services/toast.service';

interface PointRule {
  id: number;
  ruleKey: string;
  label: string;
  description: string;
  value: number;
  updatedAt: string;
}

/**
 * B.8 — Pilotage admin du barème de points.
 *
 * L'ADMIN ajuste les valeurs de chaque règle (bonus pronostic, score exact,
 * bon signe, palier de niveau). Les modifs sont appliquées en temps réel
 * côté back : un cache en mémoire est rechargé après chaque PUT, et tous
 * les pronostics et crédits de points à venir utilisent immédiatement la
 * nouvelle valeur.
 *
 * Les valeurs historiques déjà créditées ne sont PAS réajustées (les
 * `point_transactions` restent immuables) — c'est seulement la *règle
 * métier future* qui change.
 */
@Component({
  selector: 'app-admin-points-rules',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-6">
      <div>
        <h2 class="text-2xl font-display font-bold uppercase tracking-wider text-white">Barème de points</h2>
        <p class="text-sm text-gray-400 mt-1">
          Ajustez les valeurs de chaque règle (pronostic, score exact, palier de niveau).
          Les modifications s'appliquent immédiatement aux prochains crédits de points.
        </p>
      </div>

      <div class="bg-blue-500/10 border border-blue-500/30 rounded-lg p-4 text-sm text-blue-200">
        <strong>À savoir :</strong> les transactions déjà créditées (historique des membres) ne sont
        pas rétroactivement modifiées. Vous modifiez uniquement la règle métier pour les crédits à venir.
      </div>

      <div class="bg-white/5 border border-white/10 rounded-lg overflow-hidden overflow-x-auto">
        <table class="w-full text-left border-collapse">
          <thead>
            <tr class="bg-black/50 border-b border-white/10 text-xs font-display font-bold text-gray-400 uppercase tracking-wider">
              <th class="px-6 py-3">Règle</th>
              <th class="px-6 py-3">Description</th>
              <th class="px-6 py-3">Valeur</th>
              <th class="px-6 py-3">Dernière MàJ</th>
              <th class="px-6 py-3 text-right">Action</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-white/5">
            <tr *ngFor="let r of rules" class="hover:bg-white/5 transition-colors">
              <td class="px-6 py-4">
                <span class="font-medium text-white">{{ r.label }}</span>
                <span class="block text-xs text-gray-500 font-mono mt-1">{{ r.ruleKey }}</span>
              </td>
              <td class="px-6 py-4 text-sm text-gray-300 max-w-md">{{ r.description }}</td>
              <td class="px-6 py-4">
                <div class="flex items-center gap-2">
                  <input type="number"
                         [(ngModel)]="r.value"
                         [disabled]="savingKey === r.ruleKey"
                         class="w-24 bg-black/40 border border-white/20 rounded px-2 py-1 text-white font-bold focus:outline-none focus:border-wydad-red disabled:opacity-50" />
                  <span class="text-xs text-gray-500">pts</span>
                </div>
              </td>
              <td class="px-6 py-4 text-xs text-gray-500">{{ formatDate(r.updatedAt) }}</td>
              <td class="px-6 py-4 text-right">
                <button (click)="save(r)"
                        [disabled]="savingKey === r.ruleKey"
                        class="bg-wydad-red hover:bg-red-700 disabled:bg-gray-600 text-white px-4 py-1.5 rounded text-xs font-bold uppercase tracking-wider transition-colors">
                  {{ savingKey === r.ruleKey ? '…' : 'Enregistrer' }}
                </button>
              </td>
            </tr>
            <tr *ngIf="rules.length === 0 && !loading">
              <td colspan="5" class="px-6 py-8 text-center text-gray-500">
                Aucune règle. Vérifiez que la table <code>point_rules</code> est bien seedée.
              </td>
            </tr>
            <tr *ngIf="loading">
              <td colspan="5" class="px-6 py-8 text-center text-gray-500">Chargement…</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  `
})
export class AdminPointsRulesComponent implements OnInit {
  private api = inject(ApiService);
  private toast = inject(ToastService);

  rules: PointRule[] = [];
  loading = true;
  savingKey: string | null = null;

  ngOnInit() {
    this.load();
  }

  load() {
    this.loading = true;
    this.api.getPointRules().subscribe({
      next: r => {
        this.rules = r || [];
        this.loading = false;
      },
      error: e => {
        this.toast.error('Erreur chargement barème : ' + (e?.error?.message || e?.message || 'inconnue'));
        this.loading = false;
      }
    });
  }

  save(r: PointRule) {
    this.savingKey = r.ruleKey;
    this.api.updatePointRule(r.ruleKey, r.value).subscribe({
      next: updated => {
        this.toast.success(`Règle « ${r.label} » mise à jour → ${updated.value} pts`);
        // Remplace la ligne par la version serveur (updatedAt frais)
        const idx = this.rules.findIndex(x => x.ruleKey === r.ruleKey);
        if (idx >= 0) this.rules[idx] = updated;
        this.savingKey = null;
      },
      error: e => {
        this.toast.error('Erreur : ' + (e?.error?.message || e?.message || 'inconnue'));
        this.savingKey = null;
      }
    });
  }

  formatDate(iso: string | null | undefined): string {
    if (!iso) return '—';
    return new Date(iso).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' });
  }
}
