import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { ToastService } from '../../services/toast.service';

@Component({
  selector: 'app-espace-fan',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './espace-fan.component.html'
})
export class EspaceFanComponent implements OnInit {
  api = inject(ApiService);
  auth = inject(AuthService);
  toast = inject(ToastService);

  loading = true;
  userId: number | null = null;
  pointsData: any = null;
  leaderboard: any[] = [];
  matches: any[] = [];
  predictions: any[] = [];
  /** B.8 : catalogue des badges actifs + badges possédés par l'utilisateur */
  badgeCatalog: any[] = [];
  myBadges: any[] = [];
  /** Historique des gains de points (50 dernières) — Commit 1 gamification. */
  pointHistory: any[] = [];
  /** B.8.c : défis temporels actifs avec ma progression. */
  myChallenges: any[] = [];
  /** B.8.f : barème de points public — affiché dans l'encart pédagogique
   *  « Qu'est-ce qui me rapporte des points ? ». Valeurs chargées depuis
   *  /api/gamification/point-rules/public (modifiable par l'admin via
   *  /admin/bareme-points) — AUCUNE valeur en dur dans le HTML. */
  pointRules: { [k: string]: number } = { predictionBonus: 10, predictionExact: 25, predictionSign: 10 };

  // Pour le formulaire de pronostic
  predictionForm: { [matchId: number]: { homeScore: number, awayScore: number } } = {};
  submittingMatchId: number | null = null;

  // Tâche #53 — onglets de la colonne principale
  activeTab: 'overview' | 'predictions' | 'challenges' | 'gains' = 'overview';

  ngOnInit() {
    // Le barème est PUBLIC — doit être chargé même pour un visiteur
    // non connecté qui ouvre l'espace-fan (encart pédagogique toujours
    // visible côté front).
    this.api.getPublicPointRules().subscribe({
      next: (data) => this.pointRules = data || this.pointRules,
      error: (err) => console.warn('Barème public indisponible, valeurs par défaut conservées', err)
    });

    this.userId = this.auth.getCurrentUserId();
    if (this.userId) {
      this.loadGamificationData();
    } else {
      this.loading = false; // User non connecté géré dans le HTML
    }
  }

  loadGamificationData() {
    // Charger les points de l'utilisateur
    this.api.getUserPoints(this.userId as number).subscribe({
      next: (data: any) => {
        this.pointsData = data;
        this.checkLoading();
      },
      error: (err: any) => console.error('Erreur points', err)
    });

    // Charger le leaderboard
    this.api.getLeaderboard().subscribe({
      next: (data: any) => {
        this.leaderboard = data;
        this.checkLoading();
      },
      error: (err: any) => console.error('Erreur leaderboard', err)
    });

    // Charger les matchs pour les pronostics (les prochains matchs)
    this.api.getMatches().subscribe({
      next: (data: any) => {
        // MatchResponse : date + heure séparés, adversaire = équipe extérieure
        const toDateTime = (m: any) => new Date(`${m.date}T${m.heure || '00:00'}`);
        // Filtrer les matchs à venir
        this.matches = data.filter((m: any) => toDateTime(m) > new Date())
                           .sort((a: any, b: any) => toDateTime(a).getTime() - toDateTime(b).getTime())
                           .slice(0, 5); // Garder les 5 prochains matchs

        // Initialiser le formulaire
        this.matches.forEach(m => {
          this.predictionForm[m.id] = { homeScore: 0, awayScore: 0 };
        });

        this.checkLoading();
      },
      error: (err: any) => console.error('Erreur matchs', err)
    });

    // Charger l'historique des pronostics
    this.api.getUserPredictions(this.userId as number).subscribe({
      next: (data: any) => {
        this.predictions = data;
        this.checkLoading();
      },
      error: (err: any) => console.error('Erreur pronostics', err)
    });

    // B.8 : catalogue des badges + badges de l'utilisateur
    this.api.getBadgesCatalog().subscribe({
      next: (data: any) => (this.badgeCatalog = data || []),
      error: (err: any) => console.error('Erreur badges', err)
    });
    this.api.getUserBadges(this.userId as number).subscribe({
      next: (data: any) => (this.myBadges = data || []),
      error: (err: any) => console.error('Erreur badges utilisateur', err)
    });

    // Historique des gains de points (50 dernières transactions)
    this.api.getUserPointHistory(this.userId as number, 50).subscribe({
      next: (data: any) => (this.pointHistory = data || []),
      error: (err: any) => console.error('Erreur historique points', err)
    });

    // B.8.c : défis en cours avec ma progression
    this.api.getMyChallenges().subscribe({
      next: (data: any) => (this.myChallenges = data || []),
      error: (err: any) => console.error('Erreur défis', err)
    });
  }

  /** Libellé humain d'un type de défi. */
  challengeTypeLabel(type: string): string {
    switch (type) {
      case 'SUBMIT_PREDICTIONS': return 'Soumettre des pronostics';
      case 'WIN_EXACT_SCORES': return 'Scores exacts validés';
      case 'REACH_POINTS': return 'Atteindre un solde de points';
      default: return type;
    }
  }

  /** % de progression du défi (0-100). */
  challengePercent(c: any): number {
    if (!c?.progress || !c?.challenge?.target) return 0;
    return Math.min(100, Math.round((c.progress.progress / c.challenge.target) * 100));
  }

  /** Libellé humain d'une source de points (pour l'affichage historique). */
  sourceLabel(source: string): string {
    switch (source) {
      case 'PREDICTION': return 'Pronostic soumis';
      case 'EXACT_SCORE': return 'Score exact';
      case 'PREDICTION_WIN': return 'Bon signe (1X2)';
      case 'BUY':
      case 'BUY_SHOP':
      case 'BUY_TICKET': return 'Achat';
      case 'DON': return 'Don';
      case 'ADMIN_BONUS': return 'Ajustement admin';
      default: return source;
    }
  }

  /** Couleur Tailwind associée au type de transaction. */
  sourceColorClass(source: string, amount: number): string {
    if (amount < 0) return 'text-red-400';
    if (source === 'EXACT_SCORE') return 'text-emerald-600 font-bold';
    if (source === 'PREDICTION_WIN') return 'text-emerald-500';
    if (source?.startsWith('BUY')) return 'text-blue-500';
    return 'text-wydad-red';
  }

  /** B.8 : le badge est-il possédé par l'utilisateur ? */
  ownsBadge(badgeId: number): boolean {
    return this.myBadges.some((b: any) => b.badge?.id === badgeId);
  }

  checkLoading() {
    // Simple vérification de chargement
    if (this.pointsData && this.leaderboard && this.matches) {
      this.loading = false;
    }
  }

  hasPredicted(matchId: number): boolean {
    return this.predictions.some(p => p.matchId === matchId);
  }

  getPrediction(matchId: number): any {
    return this.predictions.find(p => p.matchId === matchId);
  }

  submitPrediction(matchId: number) {
    if (!this.userId) return;

    const scores = this.predictionForm[matchId];
    if (scores.homeScore === null || scores.awayScore === null) return;

    this.submittingMatchId = matchId;

    const payload = {
      userId: this.userId,
      matchId: matchId,
      predictedHomeScore: scores.homeScore,
      predictedAwayScore: scores.awayScore
    };

    this.api.submitPrediction(payload).subscribe({
      next: (res: any) => {
        this.predictions.unshift(res); // Ajouter à l'historique
        this.submittingMatchId = null;

        // Mettre à jour les points (bonus de participation)
        this.api.getUserPoints(this.userId as number).subscribe((data: any) => this.pointsData = data);
      },
      error: (err: any) => {
        console.error('Erreur pronostic', err);
        this.toast.error('Erreur lors de la soumission de votre pronostic.');
        this.submittingMatchId = null;
      }
    });
  }

  // ─────────────────────────────────────────────────────────────────────
  // Tâche #53 — getters pour le hero "Mon classement" + podium
  // ─────────────────────────────────────────────────────────────────────

  /** Vrai si l'utilisateur connecté a le rôle ADMIN (affiche les liens d'admin). */
  isAdmin(): boolean {
    return this.auth.getRole() === 'ADMIN';
  }

  /** Rang de l'utilisateur dans le leaderboard (1-based), 0 si non classé. */
  myRank(): number {
    if (!this.userId || !this.leaderboard?.length) return 0;
    const idx = this.leaderboard.findIndex((u: any) => u.userId === this.userId);
    return idx >= 0 ? idx + 1 : 0;
  }

  /** Percentile "Top X%" — approximation sur le top 50 du leaderboard. */
  myPercentile(): number {
    const rank = this.myRank();
    const total = this.leaderboard?.length || 0;
    if (rank === 0 || total === 0) return 0;
    return Math.max(1, Math.round(100 - ((rank - 1) / total) * 100));
  }

  /** Points d'écart avec le supporter juste en dessous de moi (rang N+1) — pour "devant". */
  pointsAheadOfBelow(): number {
    const rank = this.myRank();
    if (rank === 0 || rank >= this.leaderboard.length) return 0;
    const me = this.pointsData?.totalPoints || 0;
    const below = this.leaderboard[rank]?.totalPoints || 0; // rank 1-based → index rank = juste après moi
    return Math.max(0, me - below);
  }

  /** Points d'écart avec le supporter juste au-dessus de moi (rang N-1) — pour "derrière". */
  pointsBehindNext(): number {
    const rank = this.myRank();
    if (rank <= 1) return 0;
    const me = this.pointsData?.totalPoints || 0;
    const above = this.leaderboard[rank - 2]?.totalPoints || 0; // rank 1-based → index rank-2
    return Math.max(0, above - me);
  }

  /** Top 3 du leaderboard pour le podium. */
  podiumTop3(): any[] {
    return (this.leaderboard || []).slice(0, 3);
  }

  /** Reste du leaderboard à partir du rang 4. */
  restOfLeaderboard(): any[] {
    return (this.leaderboard || []).slice(3);
  }

  /** Initiales déterministes A-Z à partir d'un userId (le leaderboard n'a pas firstName). */
  userInitials(userId: number): string {
    const code = (userId % 26) + 65; // 65 = 'A'
    return String.fromCharCode(code);
  }

  /** % de progression vers le prochain niveau (utilise les données déjà calculées par le back). */
  progressPercent(): number {
    const level = this.pointsData?.level || 1;
    const toNext = this.pointsData?.pointsToNextLevel || 0;
    // Le back fournit pointsToNextLevel = level * pointsPerLevel - totalPoints
    // donc 100% = au début du palier, 0% = juste promu
    const pointsPerLevel = 500; // cohérent avec la valeur seed par défaut
    const cap = level * pointsPerLevel;
    if (cap <= 0) return 0;
    return Math.max(0, Math.min(100, Math.round(((cap - toNext) / cap) * 100)));
  }

  /** Nombre de badges débloqués. */
  unlockedBadgesCount(): number {
    return this.myBadges?.length || 0;
  }

  /** Nombre de défis en cours (non terminés). */
  activeChallengesCount(): number {
    if (!this.myChallenges?.length) return 0;
    return this.myChallenges.filter((c: any) => c.progress?.status !== 'COMPLETED').length;
  }

  /** Nombre de pronostics validés. */
  predictionsCount(): number {
    return this.predictions?.length || 0;
  }

  /** Points gagnés ce mois (transactions positives du mois courant). */
  pointsThisMonth(): number {
    if (!this.pointHistory?.length) return 0;
    const now = new Date();
    const month = now.getMonth();
    const year = now.getFullYear();
    return this.pointHistory
      .filter((tx: any) => {
        const d = new Date(tx.createdAt);
        return tx.amount > 0 && d.getMonth() === month && d.getFullYear() === year;
      })
      .reduce((sum: number, tx: any) => sum + (tx.amount || 0), 0);
  }

  /** Setter d'onglet (template doit appeler une méthode, pas une assignation). */
  setTab(tab: 'overview' | 'predictions' | 'challenges' | 'gains'): void {
    this.activeTab = tab;
  }
}
