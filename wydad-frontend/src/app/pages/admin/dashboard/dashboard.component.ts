import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { Router } from '@angular/router';
import { ApiService } from '../../../services/api.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './dashboard.component.html'
})
export class DashboardComponent implements OnInit {
  loading = true;
  loadError = false;

  kpis: { label: string; value: string; change?: string; isPositive?: boolean; icon: string }[] = [];

  upcomingMatches: any[] = [];
  recentOrders: any[] = [];
  totalUsers = 0;
  activeUsers = 0;

  constructor(
    private api: ApiService,
    private router: Router
  ) {}

  /** Export CSV des comptes utilisateurs (données affichées sur le dashboard). */
  exportCsv() {
    const rows = [
      ['Total utilisateurs', String(this.totalUsers)],
      ['Comptes actifs', String(this.activeUsers)],
      ['Matchs a venir', String(this.upcomingMatches.length)],
      ['Commandes recentes', String(this.recentOrders.length)]
    ];
    const csv = 'Indicateur,Valeur\n' + rows.map(r => r.join(',')).join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'wydad-dashboard.csv';
    a.click();
    URL.revokeObjectURL(url);
  }

  goToNewMatch() {
    this.router.navigate(['/admin/matchs']);
  }

  ngOnInit() {
    this.loadData();
  }

  loadData() {
    this.loading = true;
    this.loadError = false;

    // Les 3 sources de données sont indépendantes : on charge tout en parallèle
    this.api.getAllUsers().subscribe({
      next: (users: any[]) => {
        this.totalUsers = users?.length || 0;
        this.activeUsers = users?.filter(u => u.active).length || 0;
        this.buildKpis();
      },
      error: () => {
        this.loadError = true;
        this.loading = false;
      }
    });

    this.api.getMatchesByStatut('PROGRAMME').subscribe({
      next: (matches: any[]) => {
        this.upcomingMatches = (matches || []).slice(0, 5);
        this.buildKpis();
      },
      error: () => {
        this.upcomingMatches = [];
        this.buildKpis();
      }
    });

    this.api.getAllOrders().subscribe({
      next: (res: any) => {
        // Réponse paginée Spring : { content: [...], totalElements: ... }
        const list = res?.content || res || [];
        this.recentOrders = Array.isArray(list) ? list.slice(0, 5) : [];
        this.buildKpis();
      },
      error: () => {
        this.recentOrders = [];
        this.buildKpis();
      }
    });
  }

  private buildKpis() {
    if (!this.loading) return;

    // KPIs dérivés des données réelles uniquement
    this.kpis = [
      { label: 'Utilisateurs', value: String(this.totalUsers), icon: 'users' },
      { label: 'Comptes actifs', value: String(this.activeUsers), icon: 'user-check' },
      { label: 'Matchs à venir', value: String(this.upcomingMatches.length), icon: 'calendar' },
      { label: 'Commandes récentes', value: String(this.recentOrders.length), icon: 'shopping-bag' }
    ];
  }

  formatOrderAmount(order: any): string {
    return order?.totalAmount != null ? `${order.totalAmount} MAD` : '—';
  }

  formatMatchDate(match: any): string {
    if (!match?.matchDate) return '';
    const d = new Date(match.matchDate);
    return isNaN(d.getTime()) ? '' : `${d.toLocaleDateString('fr-FR')} ${d.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })}`;
  }
}
