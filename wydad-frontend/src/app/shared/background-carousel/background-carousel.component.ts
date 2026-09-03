import { Component, Input, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../services/api.service';

/**
 * admin-parametres 2026-09-03 — Carrousel d'images de fond d'une page vitrine.
 * <p>
 * Fetch {@code GET /api/content/backgrounds/public?pageKey=xxx}, affiche les
 * images avec un fade CSS de 1.2s et bascule automatiquement toutes les
 * 6 secondes. Aucun bundle npm, aucune dépendance externe.
 * <p>
 * Utilisation :
 * {@code <app-background-carousel pageKey="HOME" [height]="480" />}
 */
@Component({
  selector: 'app-background-carousel',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div *ngIf="backgrounds.length > 0" class="bg-carousel" [style.height.px]="height">
      <div *ngFor="let bg of backgrounds; let i = index"
           class="bg-slide"
           [class.active]="i === active"
           [style.backgroundImage]="'url(' + api.getMediaUrl(bg.imageUrl) + ')'"
           [style.opacity]="i === active ? 1 : 0">
      </div>
      <div *ngIf="backgrounds.length > 1" class="bg-dots">
        <button *ngFor="let bg of backgrounds; let i = index"
                (click)="active = i; resetTimer()"
                [class.active]="i === active"
                class="bg-dot"
                [attr.aria-label]="'Slide ' + (i+1)"></button>
      </div>
      <div class="bg-overlay"></div>
    </div>
  `,
  styles: [`
    .bg-carousel { position: relative; width: 100%; overflow: hidden; background: #111; }
    .bg-slide {
      position: absolute;
      inset: 0;
      background-size: cover;
      background-position: center;
      transition: opacity 1.2s ease-in-out;
    }
    .bg-overlay {
      position: absolute; inset: 0;
      background: linear-gradient(to bottom, rgba(0,0,0,0.15), rgba(0,0,0,0.45));
      pointer-events: none;
    }
    .bg-dots {
      position: absolute; bottom: 14px; left: 50%;
      transform: translateX(-50%);
      display: flex; gap: 8px; z-index: 2;
    }
    .bg-dot {
      width: 8px; height: 8px; border-radius: 50%;
      background: rgba(255,255,255,0.5);
      border: 0; cursor: pointer; padding: 0;
      transition: all 0.25s ease;
    }
    .bg-dot.active {
      background: white; width: 24px; border-radius: 4px;
    }
  `]
})
export class BackgroundCarouselComponent implements OnInit, OnDestroy {
  @Input({ required: true }) pageKey!: string;
  @Input() height = 360;
  @Input() intervalMs = 6000;

  api = inject(ApiService);
  backgrounds: any[] = [];
  active = 0;
  private timer: any;

  ngOnInit() {
    this.api.getBackgroundsForPage(this.pageKey).subscribe({
      next: (bgs) => {
        this.backgrounds = Array.isArray(bgs) ? bgs : [];
        if (this.backgrounds.length > 1) {
          this.startTimer();
        }
      },
      error: () => (this.backgrounds = [])
    });
  }

  ngOnDestroy() {
    if (this.timer) clearInterval(this.timer);
  }

  private startTimer() {
    this.timer = setInterval(() => {
      this.active = (this.active + 1) % this.backgrounds.length;
    }, this.intervalMs);
  }

  /** Reset le timer après un clic utilisateur pour éviter un changement brutal. */
  resetTimer() {
    if (this.timer) clearInterval(this.timer);
    if (this.backgrounds.length > 1) this.startTimer();
  }
}
