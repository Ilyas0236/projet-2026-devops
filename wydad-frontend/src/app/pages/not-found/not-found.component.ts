import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-not-found',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <section class="min-h-[60vh] flex flex-col items-center justify-center text-center px-4 py-16">
      <p class="font-display font-black text-7xl text-wydad-red">404</p>
      <h1 class="mt-4 text-2xl font-bold text-gray-800">Page introuvable</h1>
      <p class="mt-2 text-gray-500">La page que vous recherchez n'existe pas ou a été déplacée.</p>
      <a routerLink="/" class="mt-8 inline-block bg-wydad-red hover:bg-red-700 text-white px-6 py-3 rounded-lg font-medium transition-colors">
        Retour à l'accueil
      </a>
    </section>
  `
})
export class NotFoundComponent {}
