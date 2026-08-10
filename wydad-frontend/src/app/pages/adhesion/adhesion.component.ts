import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';

@Component({
  selector: 'app-adhesion',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <div class="adhesion-page pb-20">
      <div class="header">
        <h1>🎟️ Carte d'Adhésion WAC</h1>
        <p>Rejoignez la famille Wydadie, profitez d'avantages exclusifs et soutenez votre club.</p>
      </div>

      <div class="container mx-auto px-4 mt-12 max-w-6xl">
        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
          
          <!-- GRATUIT -->
          <div class="pricing-card">
            <div class="card-header bg-gray-100 text-gray-800">
              <h3 class="text-xl font-bold uppercase">Fan (Gratuit)</h3>
              <div class="price">0 MAD <span>/ an</span></div>
            </div>
            <div class="card-body">
              <ul>
                <li><i class="check-icon">✓</i> Accès aux actualités</li>
                <li><i class="check-icon">✓</i> Accès aux classements et stats</li>
                <li><i class="check-icon">✓</i> Carte membre digitale basique</li>
                <li class="disabled"><i class="cross-icon">✗</i> Priorité billetterie</li>
                <li class="disabled"><i class="cross-icon">✗</i> E-cash WydadPay</li>
                <li class="disabled"><i class="cross-icon">✗</i> Réductions boutique</li>
              </ul>
              <button (click)="selectLevel('GRATUIT')" class="btn-select bg-gray-800 text-white hover:bg-black">Devenir Fan</button>
            </div>
          </div>

          <!-- BRONZE -->
          <div class="pricing-card shadow-lg transform md:-translate-y-4">
            <div class="card-header bg-[#cd7f32] text-white">
              <div class="absolute top-0 right-0 bg-black text-white text-xs font-bold px-3 py-1 uppercase rounded-bl-lg">Populaire</div>
              <h3 class="text-xl font-bold uppercase">Bronze</h3>
              <div class="price">300 MAD <span>/ an</span></div>
            </div>
            <div class="card-body">
              <ul>
                <li><i class="check-icon">✓</i> Tout du niveau Fan</li>
                <li><i class="check-icon">✓</i> Activation E-cash WydadPay</li>
                <li><i class="check-icon">✓</i> 5% de réduction boutique</li>
                <li><i class="check-icon">✓</i> Priorité billetterie (Niveau 3)</li>
                <li class="disabled"><i class="cross-icon">✗</i> Accès événements VIP</li>
              </ul>
              <button (click)="selectLevel('BRONZE')" class="btn-select bg-[#cd7f32] text-white hover:bg-[#b06a28]">S'inscrire</button>
            </div>
          </div>

          <!-- ARGENT -->
          <div class="pricing-card">
            <div class="card-header bg-gray-400 text-white">
              <h3 class="text-xl font-bold uppercase">Argent</h3>
              <div class="price">600 MAD <span>/ an</span></div>
            </div>
            <div class="card-body">
              <ul>
                <li><i class="check-icon">✓</i> Tout du niveau Bronze</li>
                <li><i class="check-icon">✓</i> 10% de réduction boutique</li>
                <li><i class="check-icon">✓</i> Priorité billetterie (Niveau 2)</li>
                <li><i class="check-icon">✓</i> Invitation à l'AG (Observateur)</li>
                <li class="disabled"><i class="cross-icon">✗</i> Droit de vote à l'AG</li>
              </ul>
              <button (click)="selectLevel('ARGENT')" class="btn-select bg-gray-500 text-white hover:bg-gray-600">S'inscrire</button>
            </div>
          </div>

          <!-- OR -->
          <div class="pricing-card lg:col-start-2">
            <div class="card-header bg-yellow-500 text-white">
              <h3 class="text-xl font-bold uppercase">Or</h3>
              <div class="price">1500 MAD <span>/ an</span></div>
            </div>
            <div class="card-body">
              <ul>
                <li><i class="check-icon">✓</i> Tout du niveau Argent</li>
                <li><i class="check-icon">✓</i> 15% de réduction boutique</li>
                <li><i class="check-icon">✓</i> Priorité absolue billetterie (Niveau 1)</li>
                <li><i class="check-icon">✓</i> Accès Tribune VIP (2 matchs/an)</li>
                <li><i class="check-icon">✓</i> Droit de vote à l'AG</li>
              </ul>
              <button (click)="selectLevel('OR')" class="btn-select bg-yellow-500 text-white hover:bg-yellow-600">S'inscrire</button>
            </div>
          </div>

          <!-- PLATINE -->
          <div class="pricing-card">
            <div class="card-header bg-black text-white">
              <h3 class="text-xl font-bold uppercase">Platine</h3>
              <div class="price">5000 MAD <span>/ an</span></div>
            </div>
            <div class="card-body">
              <ul>
                <li><i class="check-icon">✓</i> Tout du niveau Or</li>
                <li><i class="check-icon">✓</i> 25% de réduction boutique</li>
                <li><i class="check-icon">✓</i> Abonnement Annuel VIP inclus</li>
                <li><i class="check-icon">✓</i> Rencontres exclusives avec les joueurs</li>
                <li><i class="check-icon">✓</i> Maillot officiel dédicacé offert</li>
              </ul>
              <button (click)="selectLevel('PLATINE')" class="btn-select bg-black text-white hover:bg-gray-800 border border-gray-700">S'inscrire</button>
            </div>
          </div>

        </div>
      </div>
    </div>
  `,
  styles: [`
    .header {
      background: linear-gradient(90deg, #b71c1c, #8e0000);
      color: white;
      padding: 4rem 2rem;
      text-align: center;
    }
    .header h1 {
      font-family: 'Oswald', sans-serif;
      font-size: 3rem;
      text-transform: uppercase;
      letter-spacing: 1px;
      margin-bottom: 1rem;
    }
    .header p {
      font-size: 1.1rem;
      opacity: 0.9;
      max-width: 600px;
      margin: 0 auto;
    }

    .pricing-card {
      background: white;
      border-radius: 12px;
      overflow: hidden;
      box-shadow: 0 4px 15px rgba(0,0,0,0.05);
      border: 1px solid #eaeaea;
      display: flex;
      flex-direction: column;
      position: relative;
    }
    
    .card-header {
      padding: 2rem;
      text-align: center;
    }
    .price {
      font-size: 2.5rem;
      font-weight: 800;
      margin-top: 0.5rem;
      font-family: 'Oswald', sans-serif;
    }
    .price span {
      font-size: 1rem;
      font-weight: 500;
      opacity: 0.8;
    }

    .card-body {
      padding: 2rem;
      flex: 1;
      display: flex;
      flex-direction: column;
    }
    .card-body ul {
      list-style: none;
      padding: 0;
      margin: 0 0 2rem 0;
      flex: 1;
    }
    .card-body li {
      margin-bottom: 1rem;
      font-size: 0.95rem;
      display: flex;
      align-items: flex-start;
      gap: 0.75rem;
      color: #444;
    }
    .card-body li.disabled {
      color: #aaa;
      text-decoration: line-through;
    }
    .check-icon {
      color: #2e7d32;
      font-style: normal;
      font-weight: bold;
    }
    .cross-icon {
      color: #d32f2f;
      font-style: normal;
      font-weight: bold;
    }

    .btn-select {
      width: 100%;
      padding: 1rem;
      border-radius: 8px;
      font-family: 'Oswald', sans-serif;
      text-transform: uppercase;
      font-size: 1.1rem;
      font-weight: bold;
      letter-spacing: 0.5px;
      transition: all 0.2s;
    }
  `]
})
export class AdhesionComponent {
  constructor(private router: Router) {}

  selectLevel(level: string) {
    // Naviguer vers la page d'inscription avec le niveau pré-sélectionné
    this.router.navigate(['/register'], { queryParams: { level } });
  }
}
