import { CommonModule } from '@angular/common';
import { Component, Input, Output, EventEmitter } from '@angular/core';

/** Bandeau d'erreur reutilisable avec bouton Reessayer. */
@Component({
  selector: 'app-error-banner',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="flex items-center gap-4 bg-red-50 border border-red-200 text-red-700 rounded-xl px-5 py-4 my-4">
      <svg class="w-6 h-6 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5"
              d="M12 9v4m0 4h.01M10.29 3.86l-8.02 13.88A1.997 1.997 0 004 21h16a2 2 0 001.73-3.26L13.71 3.86a2 2 0 00-3.42 0z"/>
      </svg>
      <div class="flex-1">
        <p class="font-medium">{{ message }}</p>
        <p *ngIf="detail" class="text-sm text-red-600/80 mt-0.5">{{ detail }}</p>
      </div>
      <button (click)="retry.emit()"
              class="flex-shrink-0 bg-red-600 hover:bg-red-700 text-white px-4 py-2 rounded-lg text-sm font-medium transition-colors">
        Réessayer
        </button>
    </div>
  `
})
export class ErrorBannerComponent {
  @Input() message = 'Une erreur est survenue lors du chargement.';
  @Input() detail: string | null = null;
  @Output() retry = new EventEmitter<void>();
}
