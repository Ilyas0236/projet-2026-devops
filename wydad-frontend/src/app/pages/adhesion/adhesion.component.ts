import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { ApiService } from '../../services/api.service';

interface MembershipTier {
  level: string;
  name: string;
  subtitle?: string;
  price: number | null;
  popular?: boolean;
  features: any[];
}

// Styles techniques (couleurs/gradients Tailwind) par niveau de membership.
// Les donnees metier (noms, prix, avantages) viennent exclusivement de l'API.
const TIER_STYLES: Record<string, { color: string; bgGradient: string; borderColor: string; btnClass: string; glowClass?: string }> = {
  JUNIOR: {
    color: 'text-text-secondary',
    bgGradient: 'from-surface-4 to-surface-3',
    borderColor: 'border-white/[0.06]',
    btnClass: 'bg-white/10 text-white hover:bg-white/20'
  },
  ROUGE: {
    color: 'text-wydad-red',
    bgGradient: 'from-[#2a0a0a] to-surface-3',
    borderColor: 'border-wydad-red/25',
    btnClass: 'bg-wydad-red text-white hover:bg-red-700',
    glowClass: 'hover:shadow-[0_0_40px_rgba(215,30,40,0.15)]'
  },
  OR: {
    color: 'text-wydad-gold',
    bgGradient: 'from-[#2a220a] to-surface-3',
    borderColor: 'border-wydad-gold/25',
    btnClass: 'bg-wydad-gold text-black hover:bg-wydad-gold-light',
    glowClass: 'hover:shadow-glow-gold'
  },
  DIAMANT: {
    color: 'text-blue-300',
    bgGradient: 'from-[#0a1526] to-surface-3',
    borderColor: 'border-blue-400/20',
    btnClass: 'bg-blue-300 text-black hover:bg-blue-200'
  },
  LEGENDE: {
    color: 'text-white',
    bgGradient: 'from-[#150a26] to-surface-3',
    borderColor: 'border-white/15',
    btnClass: 'bg-white text-black hover:bg-gray-200'
  }
};

@Component({
  selector: 'app-adhesion',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './adhesion.component.html',
  styles: []
})
export class AdhesionComponent implements OnInit {
  tiers: MembershipTier[] = [];
  loading = true;

  constructor(private router: Router, private api: ApiService) {}

  ngOnInit() {
    // Paliers depuis la configuration club geree par l'ADMIN (source de verite)
    this.api.getClubSetting('membership_tiers').subscribe({
      next: (tiers) => {
        this.tiers = Array.isArray(tiers) ? tiers : [];
        this.loading = false;
      },
      error: () => {
        this.tiers = [];
        this.loading = false;
      }
    });
  }

  /** Normalise les features (string ou objet {text, enabled}). */
  featureText(feature: MembershipTier['features'][number]): string {
    return typeof feature === 'string' ? feature : feature.text;
  }

  featureEnabled(feature: MembershipTier['features'][number]): boolean {
    return typeof feature === 'string' ? true : (feature.enabled !== false);
  }

  tierStyle(level: string) {
    return TIER_STYLES[level] ?? TIER_STYLES['JUNIOR'];
  }

  selectLevel(level: string) {
    // LEGENDE est attribue par le club : pas d'auto-inscription
    if (level === 'LEGENDE') return;
    this.router.navigate(['/register'], { queryParams: { level } });
  }
}
