import { Component, HostListener, OnInit } from '@angular/core';
import { RouterOutlet, RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-public-layout',
  standalone: true,
  imports: [RouterOutlet, RouterModule, CommonModule],
  templateUrl: './public-layout.component.html',
  styleUrls: ['./public-layout.component.scss']
})
export class PublicLayoutComponent implements OnInit {
  isScrolled = false;
  isMobileMenuOpen = false;

  // Coordonnees du club — source de verite : configuration club (ADMIN)
  clubInfo: any = null;

  constructor(private api: ApiService) {}

  ngOnInit() {
    this.api.getClubSetting('club_info').subscribe({
      next: (info) => (this.clubInfo = info),
      error: () => (this.clubInfo = null)
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
