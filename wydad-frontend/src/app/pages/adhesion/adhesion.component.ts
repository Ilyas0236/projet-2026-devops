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

  selectLevel(level: string) {
    // LEGENDE est attribue par le club : pas d'auto-inscription
    if (level === 'LEGENDE') return;
    this.router.navigate(['/register'], { queryParams: { level } });
  }
}
