import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.scss']
})
export class HomeComponent implements OnInit {
  nextMatch: any = null;
  articles: any[] = [];
  api = inject(ApiService);

  // Membership tiers for the premium cards section
  membershipTiers = [
    {
      name: 'Rouge',
      price: '200',
      color: 'wydad-red',
      gradient: 'from-red-950 to-surface-3',
      borderColor: 'border-wydad-red/30',
      glowClass: 'hover:shadow-glow-red',
      features: ['Carte membre digitale', 'Accès billetterie prioritaire', 'Newsletter exclusive', '5% réduction boutique']
    },
    {
      name: 'Or',
      price: '500',
      color: 'wydad-gold',
      gradient: 'from-yellow-950 to-surface-3',
      borderColor: 'border-wydad-gold/30',
      glowClass: 'hover:shadow-glow-gold',
      popular: true,
      features: ['Tout le niveau Rouge', 'Place garantie tous les matchs', '15% réduction boutique', 'Accès zone VIP', 'Maillot personnalisé']
    },
    {
      name: 'Diamant',
      price: '1500',
      color: 'tier-diamant',
      gradient: 'from-cyan-950 to-surface-3',
      borderColor: 'border-tier-diamant/30',
      glowClass: 'hover:shadow-[0_0_30px_rgba(185,242,255,0.2)]',
      features: ['Tout le niveau Or', 'Rencontre joueurs', 'Accès vestiaires', 'Conciergerie dédiée', 'Invitations événements privés']
    },
    {
      name: 'Légende',
      price: '5000',
      color: 'tier-legende',
      gradient: 'from-purple-950 to-surface-3',
      borderColor: 'border-tier-legende/30',
      glowClass: 'hover:shadow-[0_0_30px_rgba(156,39,176,0.2)]',
      features: ['Tout le niveau Diamant', 'Loge privée', 'Accès terrain avant-match', 'Nom au mur des légendes', 'Conseiller personnel']
    }
  ];

  ngOnInit() {
    // Fetch upcoming match
    this.api.getEvents().subscribe({
      next: (events) => {
        const upcoming = events.filter(e => e.status === 'UPCOMING');
        if (upcoming.length > 0) {
          upcoming.sort((a, b) => new Date(a.eventDate).getTime() - new Date(b.eventDate).getTime());
          this.nextMatch = upcoming[0];
        }
      },
      error: () => {
        this.nextMatch = null;
      }
    });

    // Fetch news
    this.api.getArticles().subscribe({
      next: (data) => {
        this.articles = data.slice(0, 3);
      },
      error: () => {
        this.articles = [];
      }
    });
  }
}
