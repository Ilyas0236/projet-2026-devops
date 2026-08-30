import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../../services/api.service';
import { ToastService } from '../../../services/toast.service';
import { ConfirmService } from '../../../services/confirm.service';

@Component({
  selector: 'app-admin-billetterie',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-billetterie.component.html'
})
export class AdminBilletterieComponent implements OnInit {
  events: any[] = [];
  competitions: any[] = [];
  /** V1.1 — matchs de calendrier (content-service) proposés comme base billetterie. */
  availableMatches: any[] = [];
  /**
   * Grille tarifaire BDD (GET /api/ticket/categories).
   * Pilote les <select> "Catégorie" du formulaire admin : l'admin
   * pioche dans la liste au lieu de saisir le code enum à la main,
   * et le prix par défaut est pré-rempli.
   */
  ticketCategories: { code: string; label: string; defaultPrice: number }[] = [];
  loading = true;
  showModal = false;
  isEdit = false;
  editingId: number | null = null;

  emptyEvent() {
    const comp = this.competitions.find(c => c.sport === 'FOOTBALL');
    return {
      title: '',
      homeTeam: 'Wydad AC',
      awayTeam: '',
      eventDate: '',
      competition: comp?.name || '',
      venue: 'Stade Mohammed V',
      eventType: 'FOOTBALL',
      category: 'SENIOR',
      adversaireLogoUrl: '',
      basePrice: 50,
      totalCapacity: 45000,
      sections: [],
      // V1.1 — référence optionnelle à un match du calendrier.
      // Si l'admin programme la billetterie d'un match existant, ce champ
      // est rempli avec l'id du match : titre/date/lieu/adversaire sont
      // alors pré-remplis automatiquement (cf. onMatchChange()).
      matchId: null as number | null
    };
  }

  newEvent: any = this.emptyEvent();

  api = inject(ApiService);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);

  ngOnInit() {
    // Competitions dynamiques : parametre club 'competitions' (source ADMIN)
    this.api.getCompetitions().subscribe({
      next: (data) => this.competitions = Array.isArray(data) ? data : [],
      error: () => this.competitions = []
    });
    // V1.1 — on charge les matchs de calendrier (content-service) pour le
    // sélecteur "Adosser à un match existant". Si l'API n'est pas joignable,
    // on laisse la liste vide : l'admin peut toujours créer un événement
    // "indépendant" (matchId = null).
    this.api.getMatches().subscribe({
      next: (data) => this.availableMatches = Array.isArray(data) ? data : [],
      error: () => this.availableMatches = []
    });
    // Grille tarifaire BDD (alimente les <select> du formulaire admin).
    this.api.getTicketCategories().subscribe({
      next: (data) => this.ticketCategories = Array.isArray(data) ? data : [],
      error: () => this.ticketCategories = []
    });
    this.loadEvents();
  }

  /**
   * V1.1 — Quand l'admin choisit un match existant dans le sélecteur, on
   * pré-remplit les champs billetterie avec ses métadonnées (date, lieu,
   * adversaire, compétition, discipline, catégorie). L'admin peut ensuite
   * corriger le prix de base et la capacité. Le titre reste libre (le back
   * le reconstitue à la sauvegarde : "Wydad AC vs Adversaire").
   */
  onMatchChange() {
    if (!this.newEvent.matchId) return;
    const m = this.availableMatches.find(x => x.id === Number(this.newEvent.matchId));
    if (!m) return;
    this.newEvent.homeTeam = 'Wydad AC';
    this.newEvent.awayTeam = m.adversaire || this.newEvent.awayTeam;
    this.newEvent.venue = m.lieu || this.newEvent.venue;
    this.newEvent.competition = m.competition || this.newEvent.competition;
    this.newEvent.eventType = (m.sport || 'FOOTBALL').toUpperCase();
    this.newEvent.category = (m.categorie || 'SENIOR').toUpperCase();
    this.newEvent.adversaireLogoUrl = m.adversaireLogoUrl || this.newEvent.adversaireLogoUrl;
    if (m.date) {
      const time = m.heure ? String(m.heure).slice(0, 5) : '20:00';
      this.newEvent.eventDate = `${m.date}T${time}`;
    }
  }

  loadEvents() {
    this.loading = true;
    this.api.getEvents().subscribe({
      next: (data) => {
        this.events = data;
        this.loading = false;
      },
      error: (err) => {
        console.error('Erreur chargement events', err);
        this.loading = false;
      }
    });
  }

  openAddModal() {
    this.isEdit = false;
    this.editingId = null;
    this.newEvent = this.emptyEvent();
    this.showModal = true;
  }

  openEditModal(event: any) {
    this.isEdit = true;
    this.editingId = event.id;
    this.newEvent = {
      title: event.title,
      homeTeam: event.homeTeam,
      awayTeam: event.awayTeam,
      eventDate: event.eventDate ? String(event.eventDate).slice(0, 16) : '',
      competition: event.competition,
      venue: event.venue,
      eventType: event.eventType || 'FOOTBALL',
      category: event.category || 'SENIOR',
      adversaireLogoUrl: event.adversaireLogoUrl || '',
      basePrice: event.basePrice,
      totalCapacity: event.totalCapacity,
      sections: event.sections || [],
      // V1.1 — conserve la FK au match de calendrier lors d'une édition.
      matchId: event.matchId ?? null
    };
    this.showModal = true;
  }

  closeAddModal() {
    this.showModal = false;
  }

  // §16/§21 — logo adverse : image téléversée par l'ADMIN à la création du
  // match, reprise automatiquement sur les billets PDF.
  uploadingLogo = false;

  uploadLogo(event: any) {
    const file = event.target.files[0];
    if (!file) return;
    this.uploadingLogo = true;
    this.api.uploadMedia(file).subscribe({
      next: (res) => {
        this.newEvent.adversaireLogoUrl = res.url;
        this.uploadingLogo = false;
      },
      error: () => {
        this.uploadingLogo = false;
        this.toast.error('Erreur lors de l\'upload du logo.');
      }
    });
  }

  /** Disciplines proposées à la création d'un match (alignées §26). */
  readonly sports = ['FOOTBALL', 'BASKETBALL', 'HANDBALL', 'VOLLEYBALL', 'SWIMMING', 'JUDO', 'ATHLETICS', 'AUTRE'];
  /** Catégories §26 communes à toutes les disciplines. */
  readonly categories = ['U15', 'U17', 'U18', 'U20', 'SENIOR'];

  saveEvent() {
    if (this.isEdit && this.editingId !== null) {
      this.api.updateEvent(this.editingId, this.newEvent).subscribe({
        next: () => {
          // Après le PUT, on PATCH chaque section existante pour appliquer
          // les modifications de prix/capacité (le PUT backend supprime puis
          // recrée les sections si on les inclut, ce qui viole la FK dès
          // qu'un billet est vendu). Le PATCH travaille in-place.
          this.persistSectionChanges(() => {
            this.toast.success('Match mis à jour.');
            this.loadEvents();
            this.closeAddModal();
          });
        },
        error: (err) => {
          console.error('Erreur modification event', err);
          this.toast.error(err.error?.message || 'Erreur lors de la modification du match.');
        }
      });
    } else {
      this.newEvent.title = `${this.newEvent.homeTeam} vs ${this.newEvent.awayTeam}`;
      this.api.createEvent(this.newEvent).subscribe({
        next: (res) => {
          this.toast.success('Match programmé.');
          this.loadEvents();
          this.closeAddModal();
        },
        error: (err) => {
          console.error('Erreur création event', err);
          this.toast.error(err.error?.message || 'Erreur lors de la création du match.');
        }
      });
    }
  }

  /**
   * Envoie un PATCH par section modifiée. Le front n'a aucun moyen de savoir
   * si une section a été "touchée" (prix changé, capacité changée) — on PATCH
   * toutes les sections présentes dans le formulaire. Les champs non édités
   * reprennent simplement leur valeur courante.
   */
  private persistSectionChanges(done: () => void) {
    const sections: any[] = this.newEvent.sections || [];
    if (sections.length === 0) { done(); return; }

    let remaining = sections.length;
    let firstError: any = null;
    sections.forEach((s) => {
      this.api.patchSection(s.id, {
        name: s.name,
        category: s.category,
        price: typeof s.price === 'number' ? s.price : Number(s.price),
        capacity: typeof s.capacity === 'number' ? s.capacity : Number(s.capacity)
      }).subscribe({
        next: () => {
          if (--remaining === 0 && !firstError) done();
        },
        error: (err) => {
          if (!firstError) {
            firstError = err;
            this.toast.error(err.error?.message || 'Erreur lors de la mise à jour d\'une section.');
            console.error('PATCH section failed', err);
          }
        }
      });
    });
  }

  async deleteEvent(id: number) {
    const ok = await this.confirm.confirm({
      title: 'Annuler le match',
      message: 'Êtes-vous sûr de vouloir annuler ce match ? Les billets vendus seront impactés.',
      confirmLabel: 'Annuler le match',
      danger: true
    });
    if (!ok) return;
    this.api.deleteEvent(id).subscribe({
      next: () => {
        this.toast.success('Match annulé.');
        this.loadEvents();
      },
      error: (err) => {
        console.error('Erreur suppression', err);
        this.toast.error(err.error?.message || 'Erreur lors de l\'annulation du match.');
      }
    });
  }

  // ─────────── V3.1 — CRUD sections billetterie (admin) ───────────

  /** Formulaire d'une nouvelle section (rempli à partir de "Ajouter section"). */
  newSection: any = this.emptySection();
  showAddSection = false;

  emptySection() {
    // Catégorie par défaut = première de la grille BDD (VIP par défaut
    // si la grille n'est pas encore chargée). Le prix par défaut est
    // renseigné dynamiquement par onNewSectionCategoryChange() dès que
    // l'admin choisit une valeur dans le <select>.
    const firstCode = this.ticketCategories[0]?.code || 'TRIBUNE_OFFICIELLE';
    const firstDefaultPrice = this.ticketCategories[0]?.defaultPrice || 100;
    return {
      name: '',
      category: firstCode,
      capacity: 100,
      price: firstDefaultPrice
    };
  }

  openAddSection() {
    this.newSection = this.emptySection();
    this.showAddSection = true;
  }

  /**
   * Quand l'admin change la catégorie dans le <select> du formulaire
   * d'ajout d'une section : on pré-remplit le prix avec le prix par
   * défaut de la grille BDD. L'admin peut ensuite l'écraser.
   */
  onNewSectionCategoryChange() {
    const cat = this.ticketCategories.find(c => c.code === this.newSection.category);
    if (cat) {
      this.newSection.price = cat.defaultPrice;
    }
  }

  /**
   * Idem pour les sections existantes (en mode édition) : changer la
   * catégorie dans le <select> ré-initialise le prix sur le défaut BDD.
   */
  onExistingSectionCategoryChange(s: any) {
    const cat = this.ticketCategories.find(c => c.code === s.category);
    if (cat) {
      s.price = cat.defaultPrice;
    }
  }

  cancelAddSection() {
    this.showAddSection = false;
  }

  /** V3.1 — POST /api/ticket/sections?eventId=... */
  createSection() {
    if (!this.editingId) {
      this.toast.error('Aucun événement sélectionné.');
      return;
    }
    if (!this.newSection.name?.trim() || !this.newSection.capacity || !this.newSection.price) {
      this.toast.error('Nom, capacité et prix sont obligatoires.');
      return;
    }
    this.api.createSection(this.editingId, {
      name: this.newSection.name.trim(),
      category: this.newSection.category,
      capacity: Number(this.newSection.capacity),
      price: Number(this.newSection.price)
    }).subscribe({
      next: () => {
        this.toast.success('Section ajoutée.');
        this.showAddSection = false;
        // On recharge l'événement complet pour rafraîchir la grille.
        this.api.getEventById(this.editingId!).subscribe({
          next: (fullEvent) => {
            this.newEvent.sections = fullEvent.sections || [];
            this.newEvent.totalCapacity = fullEvent.totalCapacity;
          },
          error: () => {/* silencieux : on n'a pas réussi à rafraîchir */}
        });
      },
      error: (err) => {
        this.toast.error(err.error?.message || 'Erreur lors de l\'ajout de la section.');
      }
    });
  }

  /** V3.1 — DELETE /api/ticket/sections/{id} (refus si billets vendus). */
  async deleteSection(sectionId: number, sectionName: string) {
    const ok = await this.confirm.confirm({
      title: 'Supprimer la section',
      message: `Supprimer la section « ${sectionName} » ? Action impossible si des billets y sont rattachés.`,
      confirmLabel: 'Supprimer',
      danger: true
    });
    if (!ok) return;
    this.api.deleteSection(sectionId).subscribe({
      next: () => {
        this.toast.success('Section supprimée.');
        if (this.editingId) {
          this.api.getEventById(this.editingId).subscribe({
            next: (fullEvent) => {
              this.newEvent.sections = fullEvent.sections || [];
              this.newEvent.totalCapacity = fullEvent.totalCapacity;
            }
          });
        }
      },
      error: (err) => {
        this.toast.error(err.error?.message || 'Erreur lors de la suppression.');
      }
    });
  }
}
