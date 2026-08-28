import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../../services/api.service';
import { ToastService } from '../../../services/toast.service';
import { ConfirmService } from '../../../services/confirm.service';

/**
 * Page admin CRUD des plans d'abonnement saisonnier.
 *
 * Alimente la table `subscription_plans` du back, qui pilote :
 *  - la home (section "Mes Abonnements")
 *  - la page /abonnement (catalogue)
 *  - la résolution prix lors d'un achat
 *
 * L'ancien enum `SubscriptionZoneCode` (côté back) reste en lecture
 * pour la rétro-compat PDF/audit, mais l'admin ne le touche plus :
 * tout passe par cette page.
 */
@Component({
  selector: 'app-admin-subscription-plans',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-subscription-plans.component.html'
})
export class AdminSubscriptionPlansComponent implements OnInit {
  plans: any[] = [];
  loading = true;
  showModal = false;
  isEdit = false;
  editingId: number | null = null;
  /** Filtre actif : null = tous, true = actifs, false = inactifs. */
  filterActive: boolean | null = null;

  emptyPlan() {
    return {
      code: '',
      name: '',
      regularPrice: 0,
      adherentPrice: 0,
      benefits: '',
      isActive: true,
      displayOrder: 0,
      exceptionalPriority: false,
      season: ''
    };
  }

  newPlan: any = this.emptyPlan();

  api = inject(ApiService);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);

  ngOnInit() {
    this.loadPlans();
  }

  loadPlans() {
    this.loading = true;
    this.api.adminListSubscriptionPlans(0, 100).subscribe({
      next: (data) => {
        // Le back renvoie { content: [...], ... } (Page Spring) ou un array brut.
        const list = Array.isArray(data) ? data : (data?.content ?? []);
        this.plans = list.sort((a: any, b: any) =>
          (a.displayOrder ?? 0) - (b.displayOrder ?? 0));
        this.loading = false;
      },
      error: (err) => {
        console.error('Erreur chargement plans', err);
        this.toast.error('Impossible de charger les plans d\'abonnement');
        this.loading = false;
      }
    });
  }

  get filteredPlans(): any[] {
    if (this.filterActive === null) return this.plans;
    return this.plans.filter(p => p.isActive === this.filterActive);
  }

  get countActive(): number {
    return this.plans.filter(p => p.isActive === true).length;
  }

  get countInactive(): number {
    return this.plans.filter(p => p.isActive === false).length;
  }

  openAddModal() {
    this.isEdit = false;
    this.editingId = null;
    this.newPlan = this.emptyPlan();
    this.showModal = true;
  }

  openEditModal(plan: any) {
    this.isEdit = true;
    this.editingId = plan.id;
    // Snapshot pour ne pas muter l'original si l'admin annule.
    this.newPlan = {
      code: plan.code,
      name: plan.name,
      regularPrice: plan.regularPrice,
      adherentPrice: plan.adherentPrice,
      benefits: plan.benefits ?? '',
      isActive: plan.isActive,
      displayOrder: plan.displayOrder ?? 0,
      exceptionalPriority: plan.exceptionalPriority ?? false,
      season: plan.season ?? ''
    };
    this.showModal = true;
  }

  closeModal() {
    this.showModal = false;
    this.editingId = null;
    this.newPlan = this.emptyPlan();
  }

  save() {
    // Validation côté client (le back revérifie de toute façon)
    if (!this.newPlan.code || !this.newPlan.name) {
      this.toast.error('Le code et le nom sont obligatoires.');
      return;
    }
    if (!/^[A-Z0-9_-]{1,32}$/.test(this.newPlan.code)) {
      this.toast.error('Le code doit être en MAJUSCULES/chiffres/_/- (max 32).');
      return;
    }
    const regular = Number(this.newPlan.regularPrice);
    const adherent = Number(this.newPlan.adherentPrice);
    if (isNaN(regular) || regular < 0 || isNaN(adherent) || adherent < 0) {
      this.toast.error('Les prix doivent être des nombres ≥ 0.');
      return;
    }
    const payload = {
      ...this.newPlan,
      regularPrice: regular,
      adherentPrice: adherent,
      displayOrder: Number(this.newPlan.displayOrder) || 0
    };

    if (this.isEdit && this.editingId) {
      this.api.adminUpdateSubscriptionPlan(this.editingId, payload).subscribe({
        next: () => {
          this.toast.success(`Plan ${payload.code} mis à jour.`);
          this.closeModal();
          this.loadPlans();
        },
        error: (err) => this.handleSaveError(err, 'mise à jour')
      });
    } else {
      this.api.adminCreateSubscriptionPlan(payload).subscribe({
        next: () => {
          this.toast.success(`Plan ${payload.code} créé.`);
          this.closeModal();
          this.loadPlans();
        },
        error: (err) => this.handleSaveError(err, 'création')
      });
    }
  }

  private handleSaveError(err: any, action: string) {
    const code = err.error?.code;
    if (code === 'DUPLICATE_PLAN_CODE') {
      this.toast.error(`Un plan avec le code ${this.newPlan.code} existe déjà.`);
    } else if (code === 'BAD_REQUEST') {
      this.toast.error(err.error?.message || 'Données invalides.');
    } else {
      this.toast.error(`Échec de la ${action} : ${err.error?.message || err.message}`);
    }
  }

  async toggleActive(plan: any) {
    const updated = { ...plan, isActive: !plan.isActive };
    this.api.adminUpdateSubscriptionPlan(plan.id, updated).subscribe({
      next: () => {
        this.toast.success(`Plan ${plan.code} ${updated.isActive ? 'activé' : 'désactivé'}.`);
        this.loadPlans();
      },
      error: (err) => this.toast.error('Impossible de modifier le plan : ' + (err.error?.message || err.message))
    });
  }

  async deletePlan(plan: any) {
    const ok = await this.confirm.confirm({
      title: 'Supprimer le plan',
      message: `Supprimer définitivement le plan ${plan.code} (${plan.name}) ? ` +
        `Cette action est irréversible. Si des abonnements existants référencent ` +
        `ce plan, la suppression sera refusée.`,
      confirmLabel: 'Supprimer',
      danger: true
    });
    if (!ok) return;
    this.api.adminDeleteSubscriptionPlan(plan.id).subscribe({
      next: () => {
        this.toast.success(`Plan ${plan.code} supprimé.`);
        this.loadPlans();
      },
      error: (err) => {
        const code = err.error?.code;
        if (code === 'PLAN_CONFLICT' || code === 'PLAN_IN_USE') {
          this.toast.error(
            `Impossible de supprimer : ce plan est référencé par des abonnements existants. ` +
            `Désactivez-le plutôt (isActive=false).`
          );
        } else {
          this.toast.error('Suppression échouée : ' + (err.error?.message || err.message));
        }
      }
    });
  }
}
