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
 *
 * admin-parametres 2026-09-03 — refonte majeure :
 *   - Sponsors : upload image (redim auto selon tier) OU URL externe + select tier
 *   - Backgrounds visiteur : carrousel multi-photos par page (8 pages cibles)
 *   - Stade : champ coverImageUrl (photo de couverture)
 *   - Bandeau garde-fou ADMIN en haut : aucun achat possible depuis ce panneau
 */
@Component({
  selector: 'app-admin-settings',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-6">

      <!-- ═══ BANDEAU GARDE-FOU ADMIN (rappel permanent) ═══
           L'ADMIN NE PEUT PAS ACHETER. Aucun endpoint d'achat ne lui est ouvert. -->
      <div class="bg-red-900/30 border-2 border-red-600/50 rounded-lg p-4 flex items-start gap-3">
        <span class="text-2xl shrink-0">🛡️</span>
        <div class="text-sm">
          <p class="font-display font-bold text-red-200 uppercase tracking-wider mb-1">Rôle ADMIN — gestion uniquement</p>
          <p class="text-red-300/80">
            Vous gérez la configuration du site depuis cet écran : coordonnées, sponsors, réseaux sociaux, photos, stade.
            <strong class="text-red-200">Aucun achat n'est possible avec un compte ADMIN</strong> (panier, billet, abonnement, don).
            Si vous voyez un bouton d'achat accessible, déclarez-le comme un bug.
          </p>
        </div>
      </div>

      <div>
        <h2 class="text-2xl font-display font-bold uppercase tracking-wider text-white">Paramètres du Club</h2>
        <p class="text-sm text-gray-400 mt-1">
          Configuration métier du site : coordonnées du club, libellés de compétitions, sponsors, réseaux sociaux, photos, stade.
          Ces contenus alimentent le footer, la billetterie, le classement et les pages publiques.
        </p>
      </div>

      <div *ngIf="loading" class="flex justify-center py-20">
        <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-wydad-red"></div>
      </div>

      <div *ngIf="error" class="bg-red-500/10 border border-red-500/30 rounded-lg p-4 text-red-300 text-sm">{{ error }}</div>

      <div *ngIf="!loading" class="space-y-4">
        <!-- Cles JSON (club_info, competitions) -->
        <div *ngFor="let s of settings" class="bg-white/5 border border-white/10 rounded-lg p-5">
          <div class="flex justify-between items-center mb-3">
            <h3 class="font-display font-bold uppercase tracking-wider text-wydad-gold text-sm">{{ labelFor(s.key) }}</h3>
            <span class="text-xs text-gray-500 font-mono">{{ s.key }}</span>
          </div>

          <textarea
            [(ngModel)]="drafts[s.key]"
            (ngModelChange)="validate(s.key)"
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

        <!-- Page « Stade » (stadium_info) : alimente la page publique /stade
             admin-parametres 2026-09-03 : ajout coverImageUrl (photo de couverture) -->
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

          <!-- Photo de couverture (admin-parametres 2026-09-03) -->
          <div class="mt-4 pt-4 border-t border-white/[0.06]">
            <label class="block text-xs font-display font-bold uppercase tracking-wider text-gray-300 mb-2">
              Photo de couverture (affichée en haut de /stade)
            </label>
            <div *ngIf="stadiumDraft.coverImageUrl" class="flex items-center gap-3 mb-3">
              <img [src]="apiService.getMediaUrl(stadiumDraft.coverImageUrl)" alt="cover"
                   class="h-20 w-auto max-w-[200px] object-cover rounded border border-white/10">
              <button (click)="stadiumDraft.coverImageUrl = ''" type="button"
                      class="text-xs text-red-400 hover:text-red-300 underline">Retirer</button>
            </div>
            <input type="file" accept="image/jpeg,image/png,image/webp"
                   (change)="uploadStadiumCover($event)"
                   [disabled]="stadiumCoverUploading"
                   class="block w-full text-xs text-gray-400 file:mr-3 file:py-2 file:px-4 file:border-0 file:bg-wydad-red file:text-white file:uppercase file:font-bold file:tracking-wider file:cursor-pointer hover:file:bg-red-700 disabled:opacity-40">
            <p *ngIf="stadiumCoverUploading" class="text-xs text-gray-500 mt-1">Upload en cours…</p>
            <p *ngIf="!stadiumDraft.coverImageUrl && !stadiumCoverUploading" class="text-xs text-gray-500 mt-1">
              Aucun fichier. Formats acceptés : JPEG, PNG, WEBP (5 Mo max).
            </p>
          </div>

          <div class="flex justify-end mt-3">
            <button (click)="saveStadium()" [disabled]="!canSaveStadium() || stadiumSaving"
                    class="px-4 py-2 bg-wydad-red disabled:opacity-40 disabled:cursor-not-allowed hover:bg-red-700 text-white uppercase text-xs font-bold tracking-wider">
              {{ stadiumSaving ? 'Enregistrement...' : 'Enregistrer le stade' }}
            </button>
          </div>
        </div>

        <!-- B.7 : Sponsors & partenaires (admin-parametres 2026-09-03)
             Upload image avec redim auto selon tier OU URL externe -->
        <div class="bg-white/5 border border-white/10 rounded-lg p-5">
          <h3 class="font-display font-bold uppercase tracking-wider text-wydad-gold text-sm mb-1">Sponsors &amp; partenaires</h3>
          <p class="text-xs text-gray-600 mb-4">
            Affichés dans le footer public. Le logo uploadé est redimensionné automatiquement selon le niveau choisi.
            Désactiver un sponsor le retire de l'affichage sans le supprimer.
          </p>

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

          <!-- Formulaire d'ajout (admin-parametres 2026-09-03 : upload + select tier) -->
          <form (ngSubmit)="createSponsor()" class="mt-5 pt-4 border-t border-white/[0.06] space-y-3">
            <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
              <input [(ngModel)]="sponsorDraft.name" name="sp-name" required placeholder="Nom du sponsor *" class="admin-input !text-sm">
              <select [(ngModel)]="sponsorDraft.tier" name="sp-tier" required class="admin-input !text-sm">
                <option value="" disabled>Niveau de partenariat *</option>
                <option value="MAIN_SPONSOR">MAIN_SPONSOR (sponsor principal — grande taille)</option>
                <option value="OFFICIAL_PARTNER">OFFICIAL_PARTNER (partenaire officiel)</option>
                <option value="SUPPLIER">SUPPLIER (fournisseur — petite taille)</option>
              </select>
            </div>

            <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
              <input [(ngModel)]="sponsorDraft.websiteUrl" name="sp-site" placeholder="Site web (optionnel)" class="admin-input !text-sm">
              <input [(ngModel)]="sponsorDraft.displayOrder" name="sp-order" type="number" placeholder="Ordre d'affichage" class="admin-input !text-sm">
            </div>

            <!-- Source du logo : 2 modes mutuellement exclusifs -->
            <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
              <div>
                <label class="block text-[10px] font-display font-bold uppercase tracking-widest text-gray-400 mb-1">
                  📤 Uploader un logo (redimensionné auto)
                </label>
                <input type="file" accept="image/jpeg,image/png,image/webp"
                       (change)="onSponsorFileSelected($event)"
                       [disabled]="!!sponsorDraft.logoUrl || sponsorUploading"
                       class="block w-full text-xs text-gray-400 file:mr-3 file:py-2 file:px-4 file:border-0 file:bg-wydad-gold file:text-black file:uppercase file:font-bold file:tracking-wider file:cursor-pointer hover:file:bg-yellow-600 disabled:opacity-40">
                <p *ngIf="sponsorUploading" class="text-xs text-wydad-gold mt-1">Upload + redimensionnement en cours…</p>
                <p *ngIf="sponsorDraft.logoUrl && sponsorDraft.logoUrl.startsWith('/api/')" class="text-xs text-green-400 mt-1">
                  ✓ Logo uploadé : {{ sponsorDraft.logoUrl }}
                </p>
              </div>
              <div>
                <label class="block text-[10px] font-display font-bold uppercase tracking-widest text-gray-400 mb-1">
                  🔗 OU URL externe (https://…)
                </label>
                <input [(ngModel)]="sponsorDraft.logoUrl" name="sp-logo" placeholder="https://exemple.com/logo.png"
                       [disabled]="sponsorUploading"
                       class="admin-input !text-sm">
              </div>
            </div>

            <div class="flex justify-end gap-2">
              <button type="button" (click)="resetSponsorDraft()"
                      class="px-4 py-2 bg-white/5 hover:bg-white/10 text-gray-300 uppercase text-xs font-bold tracking-wider">
                Réinitialiser
              </button>
              <button type="submit" [disabled]="!canCreateSponsor() || submittingSponsor"
                      class="px-4 py-2 bg-wydad-gold disabled:opacity-40 disabled:cursor-not-allowed hover:bg-yellow-600 text-black uppercase text-xs font-bold tracking-wider">
                {{ submittingSponsor ? 'Ajout...' : 'Ajouter le sponsor' }}
              </button>
            </div>
          </form>
        </div>

        <!-- B.9 : Reseaux sociaux officiels (saisie ADMIN, lecture publique footer)
             Le rendu utilise désormais SocialIconComponent (SVG Simple Icons CC0). -->
        <div class="bg-white/5 border border-white/10 rounded-lg p-5">
          <h3 class="font-display font-bold uppercase tracking-wider text-wydad-gold text-sm mb-1">Réseaux sociaux officiels</h3>
          <p class="text-xs text-gray-600 mb-4">Affichés dans le footer public avec de vrais logos SVG (Facebook, Instagram, YouTube, X…). Saisissez uniquement les URLs officielles du club.</p>

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

        <!-- ═══ BACKGROUNDS VISITEUR (admin-parametres 2026-09-03 — NOUVEAU) ═══
             Carrousel multi-photos par page (8 pages cibles). Le composant
             <app-background-carousel> côté public fetch /api/content/backgrounds/public. -->
        <div class="bg-white/5 border border-white/10 rounded-lg p-5">
          <h3 class="font-display font-bold uppercase tracking-wider text-wydad-gold text-sm mb-1">
            Backgrounds visiteur (carrousels)
          </h3>
          <p class="text-xs text-gray-600 mb-4">
            Photos affichées en haut de chaque page publique. Uploadez plusieurs images par page, elles défileront automatiquement.
            Formats : JPEG, PNG, WEBP. Pas de limite de taille mais restez raisonnable (&lt; 5 Mo par image).
          </p>

          <!-- Liste par page -->
          <div class="mb-4">
            <label class="block text-[10px] font-display font-bold uppercase tracking-widest text-gray-400 mb-2">Page cible</label>
            <select [(ngModel)]="bgFilterPage" (ngModelChange)="filterBackgrounds()"
                    class="admin-input !text-sm max-w-xs">
              <option value="">— Toutes les pages —</option>
              <option *ngFor="let p of BG_PAGES" [value]="p.key">{{ p.label }}</option>
            </select>
          </div>

          <div *ngIf="filteredBackgrounds.length === 0" class="text-sm text-gray-500 py-2">
            Aucun background enregistré pour cette page.
          </div>

          <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
            <div *ngFor="let bg of filteredBackgrounds" class="bg-black/30 border border-white/10 rounded-lg overflow-hidden">
              <div class="aspect-video bg-black/50 relative">
                <img [src]="apiService.getMediaUrl(bg.imageUrl)" [alt]="bg.caption || bg.pageKey"
                     class="w-full h-full object-cover"
                     onerror="this.style.opacity='0.2'">
                <span *ngIf="!bg.active"
                      class="absolute top-2 right-2 text-[9px] uppercase tracking-widest px-2 py-0.5 rounded-full bg-red-900/80 text-red-200">
                  Inactif
                </span>
              </div>
              <div class="p-3 space-y-2">
                <div class="text-xs">
                  <span class="text-[10px] uppercase tracking-widest px-2 py-0.5 rounded-full bg-white/10 text-gray-300">{{ bg.pageKey }}</span>
                  <span class="ml-2 text-gray-500">Ordre {{ bg.displayOrder }}</span>
                </div>
                <p *ngIf="bg.caption" class="text-xs text-gray-300 truncate">{{ bg.caption }}</p>
                <div class="flex gap-1">
                  <button (click)="toggleBackground(bg)"
                          class="flex-1 px-2 py-1.5 text-[10px] uppercase font-bold tracking-wider rounded"
                          [class.bg-green-700]="!bg.active" [class.hover:bg-green-600]="!bg.active"
                          [class.bg-gray-700]="bg.active" [class.hover:bg-gray-600]="bg.active"
                          class:text-white="true">
                    {{ bg.active ? 'Désactiver' : 'Activer' }}
                  </button>
                  <button (click)="moveBackground(bg, -1)" [disabled]="bg.displayOrder <= 0"
                          class="px-2 py-1.5 text-[10px] uppercase font-bold tracking-wider rounded bg-white/10 hover:bg-white/20 text-white disabled:opacity-30">
                    ↑
                  </button>
                  <button (click)="moveBackground(bg, 1)"
                          class="px-2 py-1.5 text-[10px] uppercase font-bold tracking-wider rounded bg-white/10 hover:bg-white/20 text-white">
                    ↓
                  </button>
                  <button (click)="deleteBackground(bg)"
                          class="px-2 py-1.5 text-[10px] uppercase font-bold tracking-wider rounded bg-red-800 hover:bg-red-700 text-white">
                    ✕
                  </button>
                </div>
              </div>
            </div>
          </div>

          <!-- Formulaire d'upload multi-photo -->
          <form (ngSubmit)="createBackground()" class="mt-5 pt-4 border-t border-white/[0.06] space-y-3">
            <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
              <div>
                <label class="block text-[10px] font-display font-bold uppercase tracking-widest text-gray-400 mb-1">Page cible *</label>
                <select [(ngModel)]="bgDraft.pageKey" name="bg-page" required class="admin-input !text-sm">
                  <option value="" disabled>Choisir une page</option>
                  <option *ngFor="let p of BG_PAGES" [value]="p.key">{{ p.label }}</option>
                </select>
              </div>
              <div>
                <label class="block text-[10px] font-display font-bold uppercase tracking-widest text-gray-400 mb-1">Ordre d'affichage</label>
                <input [(ngModel)]="bgDraft.displayOrder" name="bg-order" type="number" min="0"
                       placeholder="0 = premier" class="admin-input !text-sm">
              </div>
            </div>

            <div>
              <label class="block text-[10px] font-display font-bold uppercase tracking-widest text-gray-400 mb-1">Légende (optionnel)</label>
              <input [(ngModel)]="bgDraft.caption" name="bg-caption" placeholder="Ex. Wydad vs Raja - finale 2022"
                     class="admin-input !text-sm">
            </div>

            <div>
              <label class="block text-[10px] font-display font-bold uppercase tracking-widest text-gray-400 mb-1">Image *</label>
              <input type="file" accept="image/jpeg,image/png,image/webp"
                     (change)="onBgFileSelected($event)"
                     [disabled]="bgUploading"
                     class="block w-full text-xs text-gray-400 file:mr-3 file:py-2 file:px-4 file:border-0 file:bg-wydad-gold file:text-black file:uppercase file:font-bold file:tracking-wider file:cursor-pointer hover:file:bg-yellow-600 disabled:opacity-40">
              <p *ngIf="bgUploading" class="text-xs text-wydad-gold mt-1">Upload en cours…</p>
              <p *ngIf="bgDraft.imageUrl && !bgUploading" class="text-xs text-green-400 mt-1">✓ Image prête : {{ bgDraft.imageUrl }}</p>
            </div>

            <div class="flex justify-end gap-2">
              <button type="button" (click)="resetBgDraft()"
                      class="px-4 py-2 bg-white/5 hover:bg-white/10 text-gray-300 uppercase text-xs font-bold tracking-wider">
                Réinitialiser
              </button>
              <button type="submit" [disabled]="!canCreateBackground() || submittingBg"
                      class="px-4 py-2 bg-wydad-gold disabled:opacity-40 disabled:cursor-not-allowed hover:bg-yellow-600 text-black uppercase text-xs font-bold tracking-wider">
                {{ submittingBg ? 'Ajout...' : 'Ajouter le background' }}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  `
})
export class AdminSettingsComponent implements OnInit {
  /** Liste des pages cibles pour les backgrounds visiteur (8 pages publiques). */
  readonly BG_PAGES = [
    { key: 'HOME', label: 'Accueil (/)' },
    { key: 'BILLETTERIE', label: 'Billetterie (/billetterie)' },
    { key: 'PALMARES', label: 'Palmarès (/palmares)' },
    { key: 'STADE', label: 'Stade (/stade)' },
    { key: 'EFFECTIF', label: 'Effectif (/effectif)' },
    { key: 'LEGENDES', label: 'Légendes (/legendes)' },
    { key: 'ACTUALITES', label: 'Actualités (/actualites)' },
    { key: 'CLASSEMENT', label: 'Classement (/classement)' }
  ];

  settings: { key: string; value: any }[] = [];
  drafts: Record<string, string> = {};
  validationErrors: Record<string, boolean> = {};
  loading = true;
  error = '';

  // B.7 : gestion des sponsors
  sponsors: any[] = [];
  sponsorDraft: any = { name: '', logoUrl: '', websiteUrl: '', tier: '', displayOrder: 0 };
  submittingSponsor = false;
  sponsorUploading = false;

  // B.9 : reseaux sociaux officiels (cle de configuration "social_links")
  socialLinks: any[] = [];
  socialDraft: any = { platform: '', url: '' };

  // Page « Stade » (cle de configuration "stadium_info")
  stadiumDraft: any = {
    name: '', city: '', capacity: null as number | null, openedYear: null as number | null,
    address: '', accessInfo: '', history: '', coverImageUrl: ''
  };
  stadiumSaving = false;
  stadiumCoverUploading = false;

  // Backgrounds visiteur (admin-parametres 2026-09-03)
  backgrounds: any[] = [];
  filteredBackgrounds: any[] = [];
  bgFilterPage = '';
  bgDraft: any = { pageKey: '', imageUrl: '', caption: '', displayOrder: 0 };
  bgUploading = false;
  submittingBg = false;

  // Référence publique à ApiService (utilisée dans le template pour getMediaUrl)
  constructor(public apiService: ApiService, private toast: ToastService) {}

  ngOnInit() {
    this.loadSettings();
    this.loadSponsors();
    this.loadSocialLinks();
    this.loadStadium();
    this.loadBackgrounds();
  }

  // ══════════════════════ STADE ══════════════════════

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
      history: this.stadiumDraft.history?.trim() || null,
      coverImageUrl: this.stadiumDraft.coverImageUrl?.trim() || null
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

  /** Upload de la photo de couverture du stade — passe par /api/content/media/upload (sans tier = brut). */
  uploadStadiumCover(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.stadiumCoverUploading = true;
    this.apiService.uploadMedia(file).subscribe({
      next: (res) => {
        this.stadiumDraft.coverImageUrl = res.url || res.imageUrl;
        this.stadiumCoverUploading = false;
        this.toast.success('Photo de couverture uploadée. N\'oubliez pas d\'enregistrer le stade.');
        // reset input pour permettre de re-uploader la même image
        input.value = '';
      },
      error: (err) => {
        this.stadiumCoverUploading = false;
        this.toast.error(err?.error?.message || "Échec de l'upload de la photo.");
        input.value = '';
      }
    });
  }

  // ══════════════════════ RESEAUX SOCIAUX ══════════════════════

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

  // ══════════════════════ SPONSORS ══════════════════════

  loadSponsors() {
    this.apiService.getAllSponsors().subscribe({
      next: (list) => (this.sponsors = list || []),
      error: () => (this.sponsors = [])
    });
  }

  canCreateSponsor(): boolean {
    return !!(this.sponsorDraft.name?.trim()
              && this.sponsorDraft.logoUrl?.trim()
              && this.sponsorDraft.tier?.trim());
  }

  /** Upload d'un logo avec tier — passe par /api/content/media/upload?tier=xxx pour redim auto. */
  onSponsorFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    if (!this.sponsorDraft.tier) {
      this.toast.error('Choisissez d\'abord un niveau de partenariat.');
      input.value = '';
      return;
    }
    this.sponsorUploading = true;
    this.apiService.uploadMediaForSponsor(file, this.sponsorDraft.tier).subscribe({
      next: (res) => {
        this.sponsorDraft.logoUrl = res.url || res.imageUrl;
        this.sponsorUploading = false;
        this.toast.success(`Logo redimensionné pour le tier ${this.sponsorDraft.tier}.`);
      },
      error: (err) => {
        this.sponsorUploading = false;
        this.toast.error(err?.error?.message || "Échec de l'upload du logo.");
        input.value = '';
      }
    });
  }

  resetSponsorDraft() {
    this.sponsorDraft = { name: '', logoUrl: '', websiteUrl: '', tier: '', displayOrder: 0 };
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
        this.resetSponsorDraft();
        this.loadSponsors();
      },
      error: (err) => this.toast.error(err?.error?.message || "Échec de l'ajout du sponsor."),
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

  // ══════════════════════ BACKGROUNDS VISITEUR ══════════════════════

  loadBackgrounds() {
    this.apiService.getAllBackgrounds().subscribe({
      next: (list) => {
        this.backgrounds = list || [];
        this.filterBackgrounds();
      },
      error: () => {
        this.backgrounds = [];
        this.filteredBackgrounds = [];
      }
    });
  }

  filterBackgrounds() {
    if (!this.bgFilterPage) {
      this.filteredBackgrounds = [...this.backgrounds];
    } else {
      this.filteredBackgrounds = this.backgrounds.filter((b: any) => b.pageKey === this.bgFilterPage);
    }
  }

  canCreateBackground(): boolean {
    return !!(this.bgDraft.pageKey && this.bgDraft.imageUrl);
  }

  /** Upload d'une image de background (sans tier — on garde l'original). */
  onBgFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.bgUploading = true;
    this.apiService.uploadMedia(file).subscribe({
      next: (res) => {
        this.bgDraft.imageUrl = res.url || res.imageUrl;
        this.bgUploading = false;
        this.toast.success('Image uploadée.');
      },
      error: (err) => {
        this.bgUploading = false;
        this.toast.error(err?.error?.message || "Échec de l'upload de l'image.");
        input.value = '';
      }
    });
  }

  resetBgDraft() {
    this.bgDraft = { pageKey: this.bgFilterPage || '', imageUrl: '', caption: '', displayOrder: 0 };
  }

  createBackground() {
    if (!this.canCreateBackground()) return;
    this.submittingBg = true;
    const body = {
      pageKey: this.bgDraft.pageKey,
      imageUrl: this.bgDraft.imageUrl,
      caption: this.bgDraft.caption?.trim() || null,
      displayOrder: Number(this.bgDraft.displayOrder) || 0,
      active: true
    };
    this.apiService.createBackgroundImage(body).subscribe({
      next: () => {
        this.toast.success('Background ajouté. Il sera visible immédiatement côté public.');
        this.resetBgDraft();
        this.loadBackgrounds();
      },
      error: (err) => this.toast.error(err?.error?.message || "Échec de l'ajout du background."),
      complete: () => (this.submittingBg = false)
    });
  }

  toggleBackground(bg: any) {
    this.apiService.updateBackgroundImage(bg.id, { active: !bg.active }).subscribe({
      next: () => this.loadBackgrounds(),
      error: () => this.toast.error('Échec de la modification.')
    });
  }

  moveBackground(bg: any, delta: number) {
    const newOrder = Math.max(0, (bg.displayOrder || 0) + delta);
    this.apiService.updateBackgroundImage(bg.id, { displayOrder: newOrder }).subscribe({
      next: () => this.loadBackgrounds(),
      error: () => this.toast.error('Échec du changement d\'ordre.')
    });
  }

  deleteBackground(bg: any) {
    const label = bg.caption ? bg.caption : `le background #${bg.id} (${bg.pageKey})`;
    if (!confirm(`Supprimer définitivement ${label} ?`)) return;
    this.apiService.deleteBackgroundImage(bg.id).subscribe({
      next: () => {
        this.toast.success('Background supprimé.');
        this.loadBackgrounds();
      },
      error: () => this.toast.error('Échec de la suppression.')
    });
  }

  // ══════════════════════ CLES JSON (club_info, competitions) ══════════════════════

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
        this.toast.success('Paramètre enregistré.');
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
