import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';

@Component({
  selector: 'app-adhesion',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './adhesion.component.html',
  styles: []
})
export class AdhesionComponent {
  constructor(private router: Router) {}

  // Paliers alignes sur l'enum backend MembershipLevel :
  // JUNIOR(200), ROUGE(500), OR(1200), DIAMANT(3000), LEGENDE (sur invitation)
  tiers = [
    {
      name: 'Junior',
      subtitle: 'Moins de 16 ans',
      price: '200',
      color: 'text-text-secondary',
      bgGradient: 'from-surface-4 to-surface-3',
      borderColor: 'border-white/[0.06]',
      btnClass: 'bg-white/10 text-white hover:bg-white/20',
      level: 'JUNIOR',
      features: [
        { text: 'Accès aux actualités', enabled: true },
        { text: 'Carte membre digitale Junior', enabled: true },
        { text: 'Activités académie WAC', enabled: true },
        { text: 'E-cash WydadPay', enabled: false },
        { text: 'Réductions boutique', enabled: false },
      ]
    },
    {
      name: 'Rouge',
      price: '500',
      color: 'text-wydad-red',
      bgGradient: 'from-[#2a0a0a] to-surface-3',
      borderColor: 'border-wydad-red/25',
      btnClass: 'bg-wydad-red text-white hover:bg-red-700',
      glowClass: 'hover:shadow-[0_0_40px_rgba(215,30,40,0.15)]',
      popular: true,
      level: 'ROUGE',
      features: [
        { text: 'Tout du niveau Junior', enabled: true },
        { text: 'Activation E-cash WydadPay', enabled: true },
        { text: '5% de réduction boutique', enabled: true },
        { text: 'Priorité billetterie', enabled: true },
        { text: 'Accès événements VIP', enabled: false },
      ]
    },
    {
      name: 'Or',
      price: '1200',
      color: 'text-wydad-gold',
      bgGradient: 'from-[#2a220a] to-surface-3',
      borderColor: 'border-wydad-gold/25',
      btnClass: 'bg-wydad-gold text-black hover:bg-wydad-gold-light',
      glowClass: 'hover:shadow-glow-gold',
      level: 'OR',
      features: [
        { text: 'Tout du niveau Rouge', enabled: true },
        { text: '10% de réduction boutique', enabled: true },
        { text: 'Priorité billetterie renforcée', enabled: true },
        { text: 'Accès Tribune VIP (2 matchs/an)', enabled: true },
        { text: "Droit de vote à l'AG", enabled: false },
      ]
    },
    {
      name: 'Diamant',
      price: '3000',
      color: 'text-blue-300',
      bgGradient: 'from-[#0a1526] to-surface-3',
      borderColor: 'border-blue-400/20',
      btnClass: 'bg-blue-300 text-black hover:bg-blue-200',
      level: 'DIAMANT',
      features: [
        { text: 'Tout du niveau Or', enabled: true },
        { text: '15% de réduction boutique', enabled: true },
        { text: 'Priorité absolue billetterie', enabled: true },
        { text: "Droit de vote à l'AG", enabled: true },
        { text: 'Rencontres exclusives joueurs', enabled: true },
      ]
    },
    {
      name: 'Légende',
      subtitle: 'Sur invitation',
      price: '—',
      color: 'text-white',
      bgGradient: 'from-[#150a26] to-surface-3',
      borderColor: 'border-white/15',
      btnClass: 'bg-white text-black hover:bg-gray-200',
      level: 'LEGENDE',
      features: [
        { text: 'Tous les avantages Diamant', enabled: true },
        { text: 'Abonnement Annuel VIP inclus', enabled: true },
        { text: 'Maillot officiel dédicacé offert', enabled: true },
        { text: 'Statut honorifique à vie', enabled: true },
        { text: 'Attribué par le club', enabled: true },
      ]
    }
  ];

  selectLevel(level: string) {
    // LEGENDE est attribue par le club : pas d'auto-inscription
    if (level === 'LEGENDE') return;
    this.router.navigate(['/register'], { queryParams: { level } });
  }
}
