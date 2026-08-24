import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../services/api.service';

interface MembershipTier {
  level: string;
  name: string;
  subtitle?: string;
  price: number | null;
  popular?: boolean;
  features: string[];
}

// Styles techniques (couleurs/gradients Tailwind) par niveau de membership — thème clair.
// Les donnees metier (noms, prix, avantages) viennent exclusivement de l'API.
const TIER_STYLES: Record<string, { color: string; gradient: string; borderColor: string; glowClass: string }> = {
  JUNIOR: {
    color: 'text-ink-secondary',
    gradient: 'from-paper-2 to-paper-1',
    borderColor: 'border-paper-3',
    glowClass: ''
  },
  ROUGE: {
    color: 'text-wydad-red',
    gradient: 'from-red-50 to-paper-1',
    borderColor: 'border-wydad-red/40',
    glowClass: 'hover:shadow-glow-red'
  },
  OR: {
    color: 'text-amber-600',
    gradient: 'from-amber-50 to-paper-1',
    borderColor: 'border-amber-400/50',
    glowClass: 'hover:shadow-[0_8px_30px_rgba(217,119,6,0.15)]'
  },
  DIAMANT: {
    color: 'text-cyan-700',
    gradient: 'from-cyan-50 to-paper-1',
    borderColor: 'border-cyan-400/50',
    glowClass: 'hover:shadow-[0_8px_30px_rgba(8,145,178,0.15)]'
  },
  LEGENDE: {
    color: 'text-purple-700',
    gradient: 'from-purple-50 to-paper-1',
    borderColor: 'border-purple-400/50',
    glowClass: 'hover:shadow-[0_8px_30px_rgba(147,51,234,0.15)]'
  }
};

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
  membershipTiers: MembershipTier[] = [];
  /** Saison en cours depuis la configuration club (source de verite ADMIN). */
  saison = '';
  api = inject(ApiService);

  ngOnInit() {
    this.api.getClubSetting('club_info').subscribe({
      next: (info) => {
        this.saison = info?.saison || '';
      },
      error: () => {
        this.saison = '';
      }
    });

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

    // Paliers d'adhesion depuis la configuration club geree par l'ADMIN
    this.api.getClubSetting('membership_tiers').subscribe({
      next: (tiers) => {
        this.membershipTiers = (Array.isArray(tiers) ? tiers : []).filter(t => t.level !== 'LEGENDE');
      },
      error: () => {
        this.membershipTiers = [];
      }
    });
  }

  /** Styles UI associes au niveau (les donnees metier restent en base). */
  tierStyle(level: string) {
    return TIER_STYLES[level] ?? TIER_STYLES['JUNIOR'];
  }
}
