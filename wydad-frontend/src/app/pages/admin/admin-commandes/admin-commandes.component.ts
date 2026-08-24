import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../../services/api.service';
import { ToastService } from '../../../services/toast.service';

/**
 * B.12.c — Pilotage des commandes par l'ADMIN : liste de toutes les
 * commandes et avancement du workflow (préparation, expédition, livraison,
 * annulation, remboursement). Les transitions valides sont imposées par
 * le backend ; l'UI ne propose que celles autorisées depuis le statut
 * courant, et affiche l'erreur serveur sinon.
 */
@Component({
  selector: 'app-admin-commandes',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-commandes.component.html'
})
export class AdminCommandesComponent implements OnInit {
  orders: any[] = [];
  totalPages = 1;
  page = 0;
  loading = true;

  // Commande en cours de changement de statut (modal)
  selectedOrder: any = null;
  newStatus = '';
  statusComment = '';
  submitting = false;

  /**
   * Workflow serveur (OrderService.updateOrderStatus) — miroir pour
   * n'afficher QUE les transitions autorisées. Le backend reste la seule
   * autorité : toute transition refusée par lui renvoie une erreur affichée.
   */
  readonly TRANSITIONS: Record<string, string[]> = {
    PENDING: ['PAYMENT_RECEIVED', 'CANCELLED'],
    PAYMENT_RECEIVED: ['PREPARATION', 'CANCELLED', 'REFUNDED'],
    PREPARATION: ['SHIPPED', 'CANCELLED'],
    SHIPPED: ['DELIVERED', 'RETURN_REQUESTED'],
    DELIVERED: ['RETURN_REQUESTED'],
    RETURN_REQUESTED: ['RETURNED', 'REFUNDED'],
    RETURNED: ['REFUNDED'],
    CANCELLED: []
  };

  readonly STATUS_LABELS: Record<string, string> = {
    PENDING: 'En attente',
    PAYMENT_RECEIVED: 'Payée',
    PREPARATION: 'En préparation',
    SHIPPED: 'Expédiée',
    DELIVERED: 'Livrée',
    CANCELLED: 'Annulée',
    RETURN_REQUESTED: 'Retour demandé',
    RETURNED: 'Retournée',
    REFUNDED: 'Remboursée'
  };

  api = inject(ApiService);
  private toast = inject(ToastService);

  ngOnInit() {
    this.loadOrders();
  }

  loadOrders() {
    this.loading = true;
    this.api.getAllOrders().subscribe({
      next: (res) => {
        const page: any = res;
        this.orders = page?.content || [];
        this.totalPages = page?.totalPages || 1;
        this.loading = false;
      },
      error: (err) => {
        console.error('Erreur chargement commandes', err);
        this.loading = false;
        this.toast.error('Erreur lors du chargement des commandes.');
      }
    });
  }

  statusLabel(status: string): string {
    return this.STATUS_LABELS[status] || status;
  }

  /** Transitions possibles depuis le statut courant (miroir du workflow serveur). */
  allowedNext(status: string): string[] {
    return this.TRANSITIONS[status] || [];
  }

  openStatusModal(order: any) {
    this.selectedOrder = order;
    this.newStatus = this.allowedNext(order.status)[0] || '';
    this.statusComment = '';
  }

  closeStatusModal() {
    this.selectedOrder = null;
    this.newStatus = '';
    this.statusComment = '';
  }

  submitStatusChange() {
    if (!this.selectedOrder || !this.newStatus) return;
    this.submitting = true;
    this.api.updateOrderStatus(this.selectedOrder.orderNumber, this.newStatus, this.statusComment).subscribe({
      next: () => {
        this.closeStatusModal();
        this.loadOrders();
      },
      error: (err) => {
        console.error('Erreur changement statut', err);
        this.submitting = false;
        this.toast.error(err.error?.message || 'Transition refusée par le serveur.');
      }
    });
  }

  goToPage(p: number) {
    // Le backend renvoie tout via Pageable ; on pilote la pagination côté API.
    this.page = p;
    this.loadOrders();
  }
}
