import { Component, HostListener, OnInit } from '@angular/core';
import { RouterOutlet, RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { ToastContainerComponent } from '../../components/toast-container/toast-container.component';
import { ConfirmDialogComponent } from '../../components/confirm-dialog/confirm-dialog.component';

@Component({
  selector: 'app-public-layout',
  standalone: true,
  imports: [RouterOutlet, RouterModule, CommonModule, FormsModule, ToastContainerComponent, ConfirmDialogComponent],
  templateUrl: './public-layout.component.html',
  styleUrls: ['./public-layout.component.scss']
})
export class PublicLayoutComponent implements OnInit {
  isScrolled = false;
  isMobileMenuOpen = false;

  // Coordonnees du club — source de verite : configuration club (ADMIN)
  clubInfo: any = null;

  // Sponsors actifs (B.7) et reseaux sociaux officiels (B.9) —
  // source de verite : ADMIN via content-service
  sponsors: any[] = [];
  socialLinks: any[] = [];

  constructor(private api: ApiService) {}

  ngOnInit() {
    this.api.getClubSetting('club_info').subscribe({
      next: (info) => (this.clubInfo = info),
      error: () => (this.clubInfo = null)
    });

    this.api.getSponsorsPublic().subscribe({
      next: (list) => (this.sponsors = list || []),
      error: () => (this.sponsors = [])
    });

    this.api.getClubSetting('social_links').subscribe({
      next: (links) => (this.socialLinks = Array.isArray(links) ? links : []),
      error: () => (this.socialLinks = [])
    });
  }

  /** Initiale affichée sur l'icône sociale (ex "FACEBOOK" -> "F"). */
  socialInitial(platform: string): string {
    return (platform || '?').charAt(0).toUpperCase();
  }

  // Newsletter publique — inscription anonyme (notification-service).
  newsletterEmail = '';
  newsletterBusy = false;
  newsletterDone = false;
  newsletterMessage = '';
  newsletterError = '';

  subscribeNewsletter() {
    const email = (this.newsletterEmail || '').trim();
    if (!email || this.newsletterBusy) return;
    this.newsletterBusy = true;
    this.newsletterError = '';
    this.api.subscribeNewsletter(email).subscribe({
      next: (res) => {
        this.newsletterBusy = false;
        this.newsletterDone = true;
        this.newsletterMessage = res?.message || 'Inscription confirmée. Merci !';
      },
      error: (err) => {
        this.newsletterBusy = false;
        this.newsletterError =
          err?.error?.message || "Échec de l'inscription — vérifiez votre adresse.";
      }
    });
  }

  @HostListener('window:scroll', [])
  onWindowScroll() {
    this.isScrolled = window.scrollY > 50;
  }

  toggleMobileMenu() {
    this.isMobileMenuOpen = !this.isMobileMenuOpen;
  }
}
