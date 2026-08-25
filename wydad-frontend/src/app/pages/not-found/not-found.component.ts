import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-not-found',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <section class="light-page min-h-[70vh] flex flex-col items-center justify-center text-center px-4 py-16">
      <p class="font-display font-black text-8xl text-wydad-red animate-fade-in-up" style="opacity:0;">404</p>
      <span class="club-underline"></span>
      <h1 class="mt-6 text-2xl font-bold text-ink-primary animate-fade-in-up" style="animation-delay:0.15s; opacity:0;">Page introuvable</h1>
      <p class="mt-2 text-ink-secondary animate-fade-in-up" style="animation-delay:0.25s; opacity:0;">La page que vous recherchez n'existe pas ou a été déplacée.</p>
      <a routerLink="/" class="paper-btn-primary mt-8 animate-fade-in-up" style="animation-delay:0.35s; opacity:0;">
        Retour à l'accueil
      </a>
    </section>
  `
})
export class NotFoundComponent {}
