import { Component, inject } from '@angular/core';
import { ToastService } from '../../services/toast.service';

/**
 * Rendu des toasts (coin bas-droit). À placer une fois dans chaque layout racine
 * (public-layout, admin-layout).
 */
@Component({
  selector: 'app-toast-container',
  standalone: true,
  imports: [],
  template: `
    <div class="fixed bottom-6 right-6 z-[100] flex flex-col gap-3 max-w-sm">
      @for (toast of toastService.toasts(); track toast.id) {
        <div
          class="flex items-start gap-3 px-4 py-3 rounded-xl shadow-2xl border backdrop-blur-md animate-[slideIn_.25s_ease-out]"
          [class]="styleFor(toast.type).box"
          role="status">
          <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24"
               fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"
               [class]="styleFor(toast.type).icon" [attr.aria-hidden]="true">
            @switch (toast.type) {
              @case ('success') {
                <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path><polyline points="22 4 12 14.01 9 11.01"></polyline>
              }
              @case ('error') {
                <circle cx="12" cy="12" r="10"></circle><line x1="15" y1="9" x2="9" y2="15"></line><line x1="9" y1="9" x2="15" y2="15"></line>
              }
              @default {
                <circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line>
              }
            }
          </svg>
          <p class="text-sm leading-snug flex-1 text-white">{{ toast.message }}</p>
          <button type="button" (click)="toastService.dismiss(toast.id)"
                  class="text-white/70 hover:text-white transition-colors -mt-0.5"
                  aria-label="Fermer">
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24"
                 fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>
          </button>
        </div>
      }
    </div>
  `,
  styles: `
    @keyframes slideIn {
      from { transform: translateX(24px); opacity: 0; }
      to { transform: translateX(0); opacity: 1; }
    }
  `
})
export class ToastContainerComponent {
  readonly toastService = inject(ToastService);

  styleFor(type: string): { box: string; icon: string } {
    switch (type) {
      case 'success':
        return {
          box: 'bg-gray-900/95 text-white border-emerald-500/40',
          icon: 'text-emerald-400 shrink-0'
        };
      case 'error':
        return {
          box: 'bg-gray-900/95 text-white border-wydad-red/60',
          icon: 'text-wydad-red-light shrink-0'
        };
      default:
        return {
          box: 'bg-gray-900/95 text-white border-white/15',
          icon: 'text-wydad-gold-light shrink-0'
        };
    }
  }
}
