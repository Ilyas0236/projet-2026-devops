import { Component, HostListener, OnInit } from '@angular/core';
import { RouterOutlet, RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../services/api.service';
import { ToastContainerComponent } from '../../components/toast-container/toast-container.component';
import { ConfirmDialogComponent } from '../../components/confirm-dialog/confirm-dialog.component';

@Component({
  selector: 'app-public-layout',
  standalone: true,
  imports: [RouterOutlet, RouterModule, CommonModule, ToastContainerComponent, ConfirmDialogComponent],
  templateUrl: './public-layout.component.html',
  styleUrls: ['./public-layout.component.scss']
})
export class PublicLayoutComponent implements OnInit {
  isScrolled = false;
  isMobileMenuOpen = false;

  // Coordonnees du club — source de verite : configuration club (ADMIN)
  clubInfo: any = null;

  // Sponsors actifs (B.7) — source de verite : ADMIN via content-service
  sponsors: any[] = [];

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
  }

  @HostListener('window:scroll', [])
  onWindowScroll() {
    this.isScrolled = window.scrollY > 50;
  }

  toggleMobileMenu() {
    this.isMobileMenuOpen = !this.isMobileMenuOpen;
  }
}
