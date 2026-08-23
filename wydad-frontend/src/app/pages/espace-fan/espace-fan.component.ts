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
  
  // Pour le formulaire de pronostic
  predictionForm: { [matchId: number]: { homeScore: number, awayScore: number } } = {};
  submittingMatchId: number | null = null;

  ngOnInit() {
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
}
