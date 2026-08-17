import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './dashboard.component.html'
})
export class DashboardComponent implements OnInit {
  kpis = [
    { label: 'Adhérents', value: '14,230', change: '+12.5%', isPositive: true, icon: 'users' },
    { label: 'Revenus MAD', value: '845,000', change: '+8.2%', isPositive: true, icon: 'dollar-sign' },
    { label: 'Billets Vendus', value: '28,450', change: '-2.4%', isPositive: false, icon: 'ticket' },
    { label: 'Produits Vendus', value: '1,204', change: '+15.3%', isPositive: true, icon: 'shopping-bag' }
  ];

  months = ['Jan', 'Fév', 'Mar', 'Avr', 'Mai', 'Juin', 'Juil', 'Aoû', 'Sep', 'Oct', 'Nov', 'Déc'];

  upcomingMatches = [
    { opponent: 'RCA', date: '12 Oct, 20:00', competition: 'Botola Pro' },
    { opponent: 'MCO', date: '19 Oct, 18:00', competition: 'Botola Pro' },
    { opponent: 'FAR', date: '26 Oct, 21:00', competition: 'Coupe du Trône' }
  ];

  recentOrders = [
    { id: '#ORD-001', customer: 'Karim B.', product: 'Maillot Domicile (L)', amount: '350 MAD', status: 'Complété' },
    { id: '#ORD-002', customer: 'Youssef A.', product: 'Écharpe Wydad', amount: '120 MAD', status: 'En cours' },
    { id: '#ORD-003', customer: 'Amine M.', product: 'Abonnement Virage', amount: '800 MAD', status: 'Complété' },
    { id: '#ORD-004', customer: 'Hassan T.', product: 'Maillot Extérieur (M)', amount: '350 MAD', status: 'En attente' }
  ];

  constructor() {}

  ngOnInit() {
  }
}
