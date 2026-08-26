import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ApiService } from '../../../services/api.service';

/**
 * Vue d'ensemble ADMIN — §25 : TOUTES les statistiques sont calculées
 * depuis les données réelles des microservices (aucune valeur figée) :
 * comptes (auth), demandes en attente (file de validation), matchs
 * programmés (content), commandes + CA boutique (shop), actualités,
 * billetterie. Chaque source est indépendante : une panne dégrade sa
 * tuile sans casser le tableau de bord.
 */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './dashboard.component.html'
})
export class DashboardComponent implements OnInit {
  loading = true;
  loadError = false;

  /** KPIs reconstruits après chargement — chaque valeur vient d'une API réelle. */
  kpis: { label: string; value: string; icon: string; source: string }[] = [];

  // ── Sources réelles ──
  users: any[] = [];
  upcomingMatches: any[] = [];
  recentOrders: any[] = [];
  pendingAccounts: any[] = [];

  // ── Indicateurs dérivés ──
  totalUsers = 0;
  activeUsers = 0;
  totalRevenue = 0;
  totalArticles = 0;
  totalProducts = 0;

  /** Sources échouées (affichées comme « indisponible », jamais simulées). */
  failedSources = new Set<string>();

  /** Répartition des rôles calculée sur les comptes réels (top rôles). */
  roleDistribution: { label: string; count: number; pct: number }[] = [];

  constructor(
    private api: ApiService,
    private router: Router
  ) {}

  /** Export CSV des indicateurs réellement affichés. */
  exportCsv() {
    const rows = [
      ['Total utilisateurs', String(this.totalUsers)],
      ['Comptes actifs', String(this.activeUsers)],
      ['Demandes en attente', String(this.pendingAccounts.length)],
      ['Matchs a venir', String(this.upcomingMatches.length)],
      ['Commandes recentes', String(this.recentOrders.length)],
      ['CA boutique affiche (MAD)', this.totalRevenue.toFixed(2)]
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
    this.failedSources.clear();

    // Sources indépendantes chargées en parallèle.
    let remaining = 5;
    const finish = () => {
      if (--remaining === 0) {
        this.computeDerived();
        this.loading = false;
      }
    };

    this.api.getAllUsers().subscribe({
      next: (users: any[]) => {
        this.users = users || [];
        finish();
      },
      error: () => {
        this.failedSources.add('users');
        finish();
      }
    });

    this.api.getPendingDemandes().subscribe({
      next: (list: any[]) => {
        this.pendingAccounts = list || [];
        finish();
      },
      error: () => {
        this.failedSources.add('demandes');
        finish();
      }
    });

    this.api.getMatchesByStatut('PROGRAMME').subscribe({
      next: (matches: any[]) => {
        // Tri réel par date : les plus proches d'abord.
        this.upcomingMatches = (matches || [])
          .slice()
          .sort((a, b) => String(a.date).localeCompare(String(b.date)))
          .slice(0, 5);
        finish();
      },
      error: () => {
        this.failedSources.add('matchs');
        this.upcomingMatches = [];
        finish();
      }
    });

    this.api.getAllOrders().subscribe({
      next: (res: any) => {
        // Réponse paginée Spring : { content: [...], totalElements: ... }
        const list = res?.content || res || [];
        this.recentOrders = Array.isArray(list) ? list.slice(0, 6) : [];
        // CA réel = somme des commandes non annulées.
        this.totalRevenue = (Array.isArray(list) ? list : [])
          .filter(o => o?.status !== 'CANCELLED')
          .reduce((sum, o) => sum + (Number(o?.totalAmount) || 0), 0);
        finish();
      },
      error: () => {
        this.failedSources.add('commandes');
        this.recentOrders = [];
        finish();
      }
    });

    forkJoin({
      articles: this.api.getArticles().pipe(catchError(() => [[]])),
      products: this.api.getProducts().pipe(catchError(() => [[]]))
    }).subscribe({
      next: ({ articles, products }) => {
        this.totalArticles = articles?.length || 0;
        this.totalProducts = products?.length || 0;
        finish();
      },
      error: () => {
        this.totalArticles = 0;
        this.totalProducts = 0;
        finish();
      }
    });
  }

  private computeDerived() {
    this.totalUsers = this.users.length;
    this.activeUsers = this.users.filter(u => u.active).length;

    // Toutes les valeurs affichées sont dérivées des réponses API réelles.
    const failed = (s: string) => this.failedSources.has(s);
    this.kpis = [
      { label: 'Comptes', value: failed('users') ? '—' : String(this.totalUsers), icon: 'users', source: 'users' },
      { label: 'Actifs', value: failed('users') ? '—' : String(this.activeUsers), icon: 'user-check', source: 'users' },
      { label: 'Demandes en attente', value: failed('demandes') ? '—' : String(this.pendingAccounts.length), icon: 'clock', source: 'demandes' },
      { label: 'Matchs à venir', value: failed('matchs') ? '—' : String(this.upcomingMatches.length), icon: 'calendar', source: 'matchs' },
      { label: 'Commandes', value: failed('commandes') ? '—' : String(this.recentOrders.length), icon: 'shopping-bag', source: 'commandes' },
      { label: 'CA boutique (MAD)', value: failed('commandes') ? '—' : this.totalRevenue.toLocaleString('fr-FR'), icon: 'revenue', source: 'commandes' }
    ];

    // Répartition par rôle : pourcentage réel sur l'ensemble des comptes.
    const counts = new Map<string, number>();
    for (const u of this.users) {
      const role = (u.role || 'INDEFINI').toString();
      counts.set(role, (counts.get(role) || 0) + 1);
    }
    const order = ['ADHERENT', 'JOUEUR', 'STAFF', 'ENTRAINEUR', 'JOURNALISTE', 'PRESIDENT', 'ADMIN'];
    const entries = [...counts.entries()]
      .sort((a, b) => {
        const ia = order.indexOf(a[0]), ib = order.indexOf(b[0]);
        return (ia < 0 ? 99 : ia) - (ib < 0 ? 99 : ib) || b[1] - a[1];
      })
      .slice(0, 6);
    this.roleDistribution = entries.map(([role, count]) => ({
      label: role,
      count,
      pct: this.totalUsers ? Math.round(count / this.totalUsers * 1000) / 10 : 0
    }));
  }

  formatOrderAmount(order: any): string {
    return order?.totalAmount != null ? `${order.totalAmount} MAD` : '—';
  }

  /** Le content-service renvoie date (LocalDate) + heure (LocalTime). */
  formatMatchDate(match: any): string {
    if (!match?.date) return '';
    const d = new Date(`${match.date}T${match.heure || '00:00'}`);
    return isNaN(d.getTime())
      ? `${match.date}`
      : `${d.toLocaleDateString('fr-FR')} ${d.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })}`;
  }

  isFailed(source: string): boolean {
    return this.failedSources.has(source);
  }
}
