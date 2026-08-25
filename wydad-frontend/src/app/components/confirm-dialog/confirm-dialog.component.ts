import { Component, inject } from '@angular/core';
import { ConfirmService } from '../../services/confirm.service';

/**
 * Dialogue de confirmation global rendu par <app-confirm-dialog> (une fois par layout).
 * Piloté par ConfirmService. Thème neutre sombre : lisible aussi bien sur le
 * site public clair que dans le back-office ADMIN sombre.
 */
@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [],
  template: `
    @if (confirmService.pending(); as pending) {
      <div class="fixed inset-0 z-[110] flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm"
           (click)="confirmService.reject()">
        <div class="bg-gray-900 border border-white/15 rounded-2xl shadow-2xl max-w-md w-full p-6 animate-scale-in"
             (click)="$event.stopPropagation()" role="dialog" aria-modal="true">
          <div class="flex items-start gap-4">
            @if (pending.danger) {
              <div class="w-10 h-10 rounded-full bg-wydad-red/15 flex items-center justify-center shrink-0">
                <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24"
                     fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"
                     class="text-wydad-red-light" aria-hidden="true">
                  <path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z"></path>
                  <line x1="12" y1="9" x2="12" y2="13"></line><line x1="12" y1="17" x2="12.01" y2="17"></line>
                </svg>
              </div>
            }
            <div class="flex-1">
              <h3 class="font-display font-bold text-white text-lg">{{ pending.title }}</h3>
              <p class="mt-1 text-sm text-white/75 leading-relaxed">{{ pending.message }}</p>
            </div>
          </div>
          <div class="mt-6 flex justify-end gap-3">
            <button type="button" (click)="confirmService.reject()"
                    class="px-4 py-2 rounded-lg text-sm text-white/80 hover:text-white
                           bg-white/10 hover:bg-white/15 border border-white/15 transition-colors">
              {{ pending.cancelLabel || 'Annuler' }}
            </button>
            <button type="button" (click)="confirmService.accept()"
                    class="px-4 py-2 rounded-lg text-sm font-medium transition-colors text-white shadow-glow-red/40"
                    [class]="pending.danger
                      ? 'bg-gradient-to-r from-wydad-red to-wydad-red-dark hover:brightness-110'
                      : 'bg-emerald-600 hover:bg-emerald-500'">
              {{ pending.confirmLabel || 'Confirmer' }}
            </button>
          </div>
        </div>
      </div>
    }
  `
})
export class ConfirmDialogComponent {
  readonly confirmService = inject(ConfirmService);
}
