import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../../services/api.service';
import { ToastService } from '../../../services/toast.service';

/**
 * Panneau d'administration des parametres club (source de verite metier).
 * Edition JSON des cles connues : club_info (coordonnees), competitions
 * (libelles des classements / matchs / billetterie). Toute modification
 * impacte directement le site public (footer, billetterie, classement).
 */
@Component({
  selector: 'app-admin-settings',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-6">
      <div>
        <h2 class="text-2xl font-display font-bold uppercase tracking-wider text-white">Paramètres du Club</h2>
        <p class="text-sm text-gray-400 mt-1">
          Configuration métier du site : coordonnées du club, libellés de compétitions. Ces contenus alimentent le footer, la billetterie et le classement.
        </p>
      </div>

      <div *ngIf="loading" class="flex justify-center py-20">
        <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-wydad-red"></div>
      </div>

      <div *ngIf="error" class="bg-red-500/10 border border-red-500/30 rounded-lg p-4 text-red-300 text-sm">{{ error }}</div>

      <div *ngIf="!loading" class="space-y-4">
        <div *ngFor="let s of settings" class="bg-white/5 border border-white/10 rounded-lg p-5">
          <div class="flex justify-between items-center mb-3">
            <h3 class="font-display font-bold uppercase tracking-wider text-wydad-gold text-sm">{{ labelFor(s.key) }}</h3>
            <span class="text-xs text-gray-500 font-mono">{{ s.key }}</span>
          </div>

          <textarea
            [(ngModel)]="drafts[s.key]"
            rows="8"
            class="admin-input font-mono !text-sm"
            spellcheck="false"></textarea>

          <div class="flex justify-between items-center mt-3">
            <span *ngIf="validationErrors[s.key]" class="text-xs text-red-400">JSON invalide — correction requise avant enregistrement</span>
            <span *ngIf="!validationErrors[s.key]" class="text-xs text-gray-600">Modifiez puis enregistrez. Impact immédiat sur le site public.</span>
            <button (click)="save(s.key)"
                    [disabled]="validationErrors[s.key]"
                    class="px-4 py-2 bg-wydad-red disabled:opacity-40 disabled:cursor-not-allowed hover:bg-red-700 text-white uppercase text-xs font-bold tracking-wider">
              Enregistrer
            </button>
          </div>
        </div>

        <!-- Page « Stade » (stadium_info) : alimente la page publique /stade -->
        <div class="bg-white/5 border border-white/10 rounded-lg p-5">
          <h3 class="font-display font-bold uppercase tracking-wider text-wydad-gold text-sm mb-1">Stade</h3>
          <p class="text-xs text-gray-600 mb-4">Informations affichées sur la page publique « Stade » (/stade).</p>

          <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
            <input [(ngModel)]="stadiumDraft.name" name="std-name" required placeholder="Nom du stade *"
                   class="admin-input !text-sm">
            <input [(ngModel)]="stadiumDraft.city" name="std-city" required placeholder="Ville *"
                   class="admin-input !text-sm">
            <input [(ngModel)]="stadiumDraft.capacity" name="std-capacity" type="number" min="1" required
                   placeholder="Capacité (places) *" class="admin-input !text-sm">
            <input [(ngModel)]="stadiumDraft.openedYear" name="std-year" type="number" min="1900"
                   placeholder="Année d'inauguration" class="admin-input !text-sm">
            <input [(ngModel)]="stadiumDraft.address" name="std-address" placeholder="Adresse"
                   class="admin-input !text-sm md:col-span-2">
            <textarea [(ngModel)]="stadiumDraft.accessInfo" name="std-access" rows="2"
                      placeholder="Accès (tramway, bus…)" class="admin-input !text-sm md:col-span-2"></textarea>
            <textarea [(ngModel)]="stadiumDraft.history" name="std-history" rows="3"
                      placeholder="Histoire du stade" class="admin-input !text-sm md:col-span-2"></textarea>
          </div>
          <div class="flex justify-end mt-3">
            <button (click)="saveStadium()" [disabled]="!canSaveStadium() || stadiumSaving"
                    class="px-4 py-2 bg-wydad-red disabled:opacity-40 disabled:cursor-not-allowed hover:bg-red-700 text-white uppercase text-xs font-bold tracking-wider">
              {{ stadiumSaving ? 'Enregistrement...' : 'Enregistrer le stade' }}
            </button>
          </div>
        </div>

        <!-- B.7 : Sponsors & partenaires (ecriture ADMIN, lecture publique) -->
        <div class="bg-white/5 border border-white/10 rounded-lg p-5">
          <h3 class="font-display font-bold uppercase tracking-wider text-wydad-gold text-sm mb-1">Sponsors &amp; partenaires</h3>
          <p class="text-xs text-gray-600 mb-4">Affichés dans le footer public. Désactiver un sponsor le retire de l'affichage sans le supprimer.</p>

          <div *ngIf="sponsors.length === 0" class="text-sm text-gray-500 py-2">Aucun sponsor enregistré.</div>

          <div *ngFor="let s of sponsors" class="flex flex-col md:flex-row md:items-center gap-3 py-3 border-t border-white/[0.06]">
            <img [src]="s.logoUrl" [alt]="s.name" class="h-8 w-auto max-w-[100px] object-contain rounded bg-white/5 p-1"
                 onerror="this.style.visibility='hidden'">
            <div class="flex-1 min-w-0">
              <div class="text-white text-sm font-bold truncate">{{ s.name }}
                <span class="ml-2 text-[10px] uppercase tracking-widest px-2 py-0.5 rounded-full bg-white/10 text-gray-300">{{ s.tier }}</span>
                <span *ngIf="!s.active" class="ml-1 text-[10px] uppercase tracking-widest px-2 py-0.5 rounded-full bg-red-900/40 text-red-300">Inactif</span>
              </div>
              <div class="text-xs text-gray-500 truncate">Ordre {{ s.displayOrder }}<span *ngIf="s.websiteUrl"> · {{ s.websiteUrl }}</span></div>
            </div>
            <div class="flex gap-2 shrink-0">
              <button (click)="toggleSponsor(s)"
                      class="px-3 py-1.5 text-xs uppercase font-bold tracking-wider rounded"
                      [class.bg-green-700]="!s.active" [class.hover:bg-green-600]="!s.active"
                      [class.bg-gray-700]="s.active" [class.hover:bg-gray-600]="s.active"
                      class:text-white="true">
                {{ s.active ? 'Désactiver' : 'Activer' }}
              </button>
              <button (click)="deleteSponsor(s)"
                      class="px-3 py-1.5 text-xs uppercase font-bold tracking-wider rounded bg-red-800 hover:bg-red-700 text-white">
                Supprimer
              </button>
            </div>
          </div>

          <!-- Formulaire d'ajout -->
          <form (ngSubmit)="createSponsor()" class="mt-5 pt-4 border-t border-white/[0.06] grid grid-cols-1 md:grid-cols-2 gap-3">
            <input [(ngModel)]="sponsorDraft.name" name="sp-name" required placeholder="Nom du sponsor *" class="admin-input !text-sm">
            <input [(ngModel)]="sponsorDraft.logoUrl" name="sp-logo" required placeholder="URL du logo * (https://…)" class="admin-input !text-sm">
            <input [(ngModel)]="sponsorDraft.websiteUrl" name="sp-site" placeholder="Site web (optionnel)" class="admin-input !text-sm">
            <input [(ngModel)]="sponsorDraft.tier" name="sp-tier" required placeholder="Niveau de partenariat * (ex MAIN_SPONSOR)" class="admin-input !text-sm">
            <input [(ngModel)]="sponsorDraft.displayOrder" name="sp-order" type="number" placeholder="Ordre d'affichage" class="admin-input !text-sm">
            <button type="submit" [disabled]="!canCreateSponsor() || submittingSponsor"
                    class="px-4 py-2 bg-wydad-gold disabled:opacity-40 disabled:cursor-not-allowed hover:bg-yellow-600 text-black uppercase text-xs font-bold tracking-wider">
              Ajouter le sponsor
            </button>
          </form>
        </div>

        <!-- B.9 : Reseaux sociaux officiels (saisie ADMIN, lecture publique footer) -->
        <div class="bg-white/5 border border-white/10 rounded-lg p-5">
          <h3 class="font-display font-bold uppercase tracking-wider text-wydad-gold text-sm mb-1">Réseaux sociaux officiels</h3>
          <p class="text-xs text-gray-600 mb-4">Affichés dans le footer public. Saisissez uniquement les URLs officielles du club.</p>

          <div *ngIf="socialLinks.length === 0" class="text-sm text-gray-500 py-2">Aucun réseau social enregistré.</div>

          <div *ngFor="let link of socialLinks; let i = index" class="flex flex-col md:flex-row md:items-center gap-3 py-2 border-t border-white/[0.06]">
            <span class="text-[10px] uppercase tracking-widest px-2 py-0.5 rounded-full bg-white/10 text-gray-300 w-fit">{{ link.platform }}</span>
            <span class="flex-1 text-xs text-gray-400 truncate font-mono">{{ link.url }}</span>
            <button (click)="removeSocialLink(i)"
                    class="px-3 py-1.5 text-xs uppercase font-bold tracking-wider rounded bg-red-800 hover:bg-red-700 text-white shrink-0">
              Retirer
            </button>
          </div>

          <form (ngSubmit)="addSocialLink()" class="mt-5 pt-4 border-t border-white/[0.06] grid grid-cols-1 md:grid-cols-3 gap-3">
            <select [(ngModel)]="socialDraft.platform" name="soc-platform" required class="admin-input !text-sm">
              <option value="" disabled>Plateforme *</option>
              <option value="FACEBOOK">Facebook</option>
              <option value="X">X (Twitter)</option>
              <option value="INSTAGRAM">Instagram</option>
              <option value="YOUTUBE">YouTube</option>
              <option value="TIKTOK">TikTok</option>
              <option value="LINKEDIN">LinkedIn</option>
            </select>
            <input [(ngModel)]="socialDraft.url" name="soc-url" required placeholder="URL officielle * (https://…)" class="admin-input !text-sm">
            <button type="submit" [disabled]="!canAddSocialLink()"
                    class="px-4 py-2 bg-wydad-gold disabled:opacity-40 disabled:cursor-not-allowed hover:bg-yellow-600 text-black uppercase text-xs font-bold tracking-wider">
              Ajouter
            </button>
          </form>
        </div>
      </div>
    </div>
  `
})
export class AdminSettingsComponent implements OnInit {
  settings: { key: string; value: any }[] = [];
  drafts: Record<string, string> = {};
  validationErrors: Record<string, boolean> = {};
  loading = true;
  error = '';

  // B.7 : gestion des sponsors
  sponsors: any[] = [];
  sponsorDraft: any = { name: '', logoUrl: '', websiteUrl: '', tier: '', displayOrder: 0 };
  submittingSponsor = false;

  // B.9 : reseaux sociaux officiels (cle de configuration "social_links")
  socialLinks: any[] = [];
  socialDraft: any = { platform: '', url: '' };

  // Page « Stade » (cle de configuration "stadium_info", prouvée par StadiumInfoSecurityTest)
  stadiumDraft: any = {
    name: '', city: '', capacity: null as number | null, openedYear: null as number | null,
    address: '', accessInfo: '', history: ''
  };
  stadiumSaving = false;

  constructor(private apiService: ApiService, private toast: ToastService) {}

  ngOnInit() {
    this.loadSettings();
    this.loadSponsors();
    this.loadSocialLinks();
    this.loadStadium();
  }

  loadStadium() {
    this.apiService.getClubSetting('stadium_info').subscribe({
      next: (info) => {
        if (info && typeof info === 'object') {
          this.stadiumDraft = { ...this.stadiumDraft, ...info };
        }
      },
      error: () => {/* pas encore saisi : formulaire vierge */}
    });
  }

  canSaveStadium(): boolean {
    return !!this.stadiumDraft.name?.trim() && !!this.stadiumDraft.city?.trim()
      && !!this.stadiumDraft.capacity;
  }

  saveStadium() {
    if (!this.canSaveStadium() || this.stadiumSaving) return;
    this.stadiumSaving = true;
    const payload = {
      name: this.stadiumDraft.name.trim(),
      city: this.stadiumDraft.city.trim(),
      capacity: Number(this.stadiumDraft.capacity),
      openedYear: this.stadiumDraft.openedYear ? Number(this.stadiumDraft.openedYear) : null,
      address: this.stadiumDraft.address?.trim() || null,
      accessInfo: this.stadiumDraft.accessInfo?.trim() || null,
      history: this.stadiumDraft.history?.trim() || null
    };
    this.apiService.updateClubSetting('stadium_info', payload).subscribe({
      next: () => {
        this.stadiumSaving = false;
        this.toast.success('Informations du stade enregistrées — visibles sur /stade.');
      },
      error: () => {
        this.stadiumSaving = false;
        this.toast.error("Échec de l'enregistrement.");
      }
    });
  }

  loadSocialLinks() {
    this.apiService.getClubSetting('social_links').subscribe({
      next: (links) => (this.socialLinks = Array.isArray(links) ? links : []),
      error: () => (this.socialLinks = [])
    });
  }

  canAddSocialLink(): boolean {
    return !!(this.socialDraft.platform && /^https:\/\/.+/.test(this.socialDraft.url || ''));
  }

  addSocialLink() {
    if (!this.canAddSocialLink()) return;
    const links = [...this.socialLinks.filter((l: any) => l.platform !== this.socialDraft.platform),
                   { platform: this.socialDraft.platform, url: this.socialDraft.url.trim() }];
    this.apiService.updateClubSetting('social_links', links).subscribe({
      next: () => {
        this.toast.success('Réseau social enregistré.');
        this.socialDraft = { platform: '', url: '' };
        this.loadSocialLinks();
      },
      error: () => this.toast.error("Échec de l'enregistrement.")
    });
  }

  removeSocialLink(index: number) {
    const links = this.socialLinks.filter((_, i) => i !== index);
    this.apiService.updateClubSetting('social_links', links).subscribe({
      next: () => {
        this.toast.success('Réseau social retiré.');
        this.loadSocialLinks();
      },
      error: () => this.toast.error('Échec de la suppression.')
    });
  }

  loadSponsors() {
    this.apiService.getAllSponsors().subscribe({
      next: (list) => (this.sponsors = list || []),
      error: () => (this.sponsors = [])
    });
  }

  canCreateSponsor(): boolean {
    return !!(this.sponsorDraft.name?.trim() && this.sponsorDraft.logoUrl?.trim() && this.sponsorDraft.tier?.trim());
  }

  createSponsor() {
    if (!this.canCreateSponsor()) return;
    this.submittingSponsor = true;
    const body = {
      name: this.sponsorDraft.name,
      logoUrl: this.sponsorDraft.logoUrl,
      websiteUrl: this.sponsorDraft.websiteUrl || null,
      tier: this.sponsorDraft.tier,
      displayOrder: Number(this.sponsorDraft.displayOrder) || 0
    };
    this.apiService.createSponsor(body).subscribe({
      next: () => {
        this.toast.success('Sponsor ajouté.');
        this.sponsorDraft = { name: '', logoUrl: '', websiteUrl: '', tier: '', displayOrder: 0 };
        this.loadSponsors();
      },
      error: () => this.toast.error("Échec de l'ajout du sponsor."),
      complete: () => (this.submittingSponsor = false)
    });
  }

  /** Active/desactive sans supprimer : un sponsor inactif n'est plus affiche publiquement. */
  toggleSponsor(s: any) {
    this.apiService.updateSponsor(s.id, { active: !s.active }).subscribe({
      next: () => this.loadSponsors(),
      error: () => this.toast.error('Échec de la modification.')
    });
  }

  deleteSponsor(s: any) {
    if (!confirm(`Supprimer définitivement le sponsor "${s.name}" ?`)) return;
    this.apiService.deleteSponsor(s.id).subscribe({
      next: () => {
        this.toast.success('Sponsor supprimé.');
        this.loadSponsors();
      },
      error: () => this.toast.error('Échec de la suppression.')
    });
  }

  loadSettings() {
    this.loading = true;
    this.apiService.getClubSettings().subscribe({
      next: (data) => {
        this.settings = data;
        this.drafts = {};
        this.validationErrors = {};
        data.forEach((s: any) => {
          this.drafts[s.key] = JSON.stringify(s.value, null, 2);
        });
        this.loading = false;
      },
      error: () => {
        this.error = 'Impossible de charger les paramètres.';
        this.loading = false;
      }
    });
  }

  /** Valide le brouillon à chaque frappe (bouton désactivé si invalide). */
  validate(key: string): boolean {
    try {
      const parsed = JSON.parse(this.drafts[key]);
      if (typeof parsed !== 'object' || parsed === null) {
        this.validationErrors[key] = true;
        return false;
      }
      this.validationErrors[key] = false;
      return true;
    } catch {
      this.validationErrors[key] = true;
      return false;
    }
  }

  save(key: string) {
    if (!this.validate(key)) return;
    const payload = JSON.parse(this.drafts[key]);
    this.apiService.updateClubSetting(key, payload).subscribe({
      next: () => {
        const s = this.settings.find(x => x.key === key);
        if (s) s.value = payload;
      },
      error: () => {
        this.toast.error('Échec de l\'enregistrement.');
      }
    });
  }

  labelFor(key: string): string {
    switch (key) {
      case 'club_info': return 'Coordonnées du club';
      case 'competitions': return 'Compétitions (classements, matchs, billetterie)';
      default: return key;
    }
  }
}
