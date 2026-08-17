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

  tiers = [
    {
      name: 'Fan',
      subtitle: 'Gratuit',
      price: '0',
      color: 'text-text-secondary',
      bgGradient: 'from-surface-4 to-surface-3',
      borderColor: 'border-white/[0.06]',
      btnClass: 'bg-white/10 text-white hover:bg-white/20',
      level: 'GRATUIT',
      features: [
        { text: 'Accès aux actualités', enabled: true },
        { text: 'Accès aux classements et stats', enabled: true },
        { text: 'Carte membre digitale basique', enabled: true },
        { text: 'Priorité billetterie', enabled: false },
        { text: 'E-cash WydadPay', enabled: false },
        { text: 'Réductions boutique', enabled: false },
      ]
    },
    {
      name: 'Bronze',
      price: '300',
      color: 'text-[#cd7f32]',
      bgGradient: 'from-[#2a1a0a] to-surface-3',
      borderColor: 'border-[#cd7f32]/20',
      btnClass: 'bg-[#cd7f32] text-white hover:bg-[#b06a28]',
      level: 'BRONZE',
      features: [
        { text: 'Tout du niveau Fan', enabled: true },
        { text: 'Activation E-cash WydadPay', enabled: true },
        { text: '5% de réduction boutique', enabled: true },
        { text: 'Priorité billetterie (Niveau 3)', enabled: true },
        { text: 'Accès événements VIP', enabled: false },
      ]
    },
    {
      name: 'Argent',
      price: '600',
      color: 'text-gray-300',
      bgGradient: 'from-[#1a1a20] to-surface-3',
      borderColor: 'border-gray-400/20',
      btnClass: 'bg-gray-400 text-black hover:bg-gray-300',
      level: 'ARGENT',
      features: [
        { text: 'Tout du niveau Bronze', enabled: true },
        { text: '10% de réduction boutique', enabled: true },
        { text: 'Priorité billetterie (Niveau 2)', enabled: true },
        { text: 'Invitation à l\'AG (Observateur)', enabled: true },
        { text: 'Droit de vote à l\'AG', enabled: false },
      ]
    },
    {
      name: 'Or',
      price: '1500',
      color: 'text-wydad-gold',
      bgGradient: 'from-[#2a220a] to-surface-3',
      borderColor: 'border-wydad-gold/25',
      btnClass: 'bg-wydad-gold text-black hover:bg-wydad-gold-light',
      glowClass: 'hover:shadow-glow-gold',
      popular: true,
      level: 'OR',
      features: [
        { text: 'Tout du niveau Argent', enabled: true },
        { text: '15% de réduction boutique', enabled: true },
        { text: 'Priorité absolue billetterie', enabled: true },
        { text: 'Accès Tribune VIP (2 matchs/an)', enabled: true },
        { text: 'Droit de vote à l\'AG', enabled: true },
      ]
    },
    {
      name: 'Platine',
      price: '5000',
      color: 'text-white',
      bgGradient: 'from-[#0a0a15] to-surface-3',
      borderColor: 'border-white/15',
      btnClass: 'bg-white text-black hover:bg-gray-200',
      glowClass: 'hover:shadow-[0_0_40px_rgba(255,255,255,0.1)]',
      level: 'PLATINE',
      features: [
        { text: 'Tout du niveau Or', enabled: true },
        { text: '25% de réduction boutique', enabled: true },
        { text: 'Abonnement Annuel VIP inclus', enabled: true },
        { text: 'Rencontres exclusives joueurs', enabled: true },
        { text: 'Maillot officiel dédicacé offert', enabled: true },
      ]
    }
  ];

  selectLevel(level: string) {
    this.router.navigate(['/register'], { queryParams: { level } });
  }
}
