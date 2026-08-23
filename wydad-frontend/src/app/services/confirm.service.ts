import { Injectable, signal } from '@angular/core';

export interface ConfirmOptions {
  title: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  danger?: boolean;
}

interface PendingConfirm extends ConfirmOptions {
  resolve: (value: boolean) => void;
}

/**
 * Dialogue de confirmation modale promise-based.
 * Remplace les confirm() natifs : `if (await confirm.confirm({...})) { ... }`.
 */
@Injectable({ providedIn: 'root' })
export class ConfirmService {
  readonly pending = signal<PendingConfirm | null>(null);

  confirm(options: ConfirmOptions): Promise<boolean> {
    // Un seul dialogue à la fois : le précédent est résolu à false.
    this.pending()?.resolve(false);
    return new Promise<boolean>(resolve => {
      this.pending.set({ ...options, resolve });
    });
  }

  accept(): void {
    this.pending()?.resolve(true);
    this.pending.set(null);
  }

  reject(): void {
    this.pending()?.resolve(false);
    this.pending.set(null);
  }
}
