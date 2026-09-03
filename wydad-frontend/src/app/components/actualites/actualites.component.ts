import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { BackgroundCarouselComponent } from '../../shared/background-carousel/background-carousel.component';

@Component({
  selector: 'app-actualites',
  standalone: true,
  imports: [CommonModule, RouterModule, BackgroundCarouselComponent],
  templateUrl: './actualites.component.html',
  styles: []
})
export class ActualitesComponent implements OnInit {
  articles: any[] = [];
  filter = 'ALL';

  constructor(private api: ApiService) {}

  ngOnInit() {
    this.api.getArticles().subscribe({
      next: (data) => (this.articles = data),
      error: () => (this.articles = []),
    });
  }

  filteredArticles() {
    if (this.filter === 'ALL') return this.articles;
    return this.articles.filter((a) => a.sport === this.filter);
  }
}
