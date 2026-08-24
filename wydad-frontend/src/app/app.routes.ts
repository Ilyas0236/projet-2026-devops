import { Routes } from '@angular/router';
import { PublicLayoutComponent } from './layouts/public-layout/public-layout.component';
import { HomeComponent } from './pages/home/home.component';
import { AdminLayoutComponent } from './layouts/admin-layout/admin-layout.component';
import { DashboardComponent } from './pages/admin/dashboard/dashboard.component';
import { BoutiqueComponent } from './pages/boutique/boutique.component';
import { BilletterieComponent } from './pages/billetterie/billetterie.component';
import { EffectifComponent } from './components/effectif/effectif.component';
import { PalmaresComponent } from './pages/palmares/palmares.component';
import { AdminBoutiqueComponent } from './pages/admin/admin-boutique/admin-boutique.component';
import { AdminBilletterieComponent } from './pages/admin/admin-billetterie/admin-billetterie.component';
import { AdminEffectifComponent } from './pages/admin/admin-effectif/admin-effectif.component';
import { AdminUsersComponent } from './pages/admin/admin-users/admin-users.component';
import { AdminActualitesComponent } from './pages/admin/admin-actualites/admin-actualites.component';
import { AdminMatchsComponent } from './pages/admin/admin-matchs/admin-matchs.component';
import { AdminClassementsComponent } from './pages/admin/admin-classements/admin-classements.component';
import { AdminNotificationsComponent } from './pages/admin/admin-notifications/admin-notifications.component';
import { AdminReclamationsComponent } from './pages/admin/admin-reclamations/admin-reclamations.component';
import { AdminSettingsComponent } from './pages/admin/admin-settings/admin-settings.component';
import { AdminMediathequeComponent } from './pages/admin/admin-mediatheque/admin-mediatheque.component';
import { AdminStaffComponent } from './pages/admin/admin-staff/admin-staff.component';
import { AdminAcademieComponent } from './pages/admin/admin-academie/admin-academie.component';
import { AdhesionComponent } from './pages/adhesion/adhesion.component';
import { LoginComponent } from './components/login/login.component';
import { RegisterComponent } from './components/register/register.component';
import { ActualitesComponent } from './components/actualites/actualites.component';
import { ActualiteDetailComponent } from './components/actualites/actualite-detail.component';
import { MatchesComponent } from './components/matches/matches.component';
import { ClassementComponent } from './components/classement/classement.component';
import { ProfilComponent } from './components/profil/profil.component';
import { CarteMembreComponent } from './components/carte-membre/carte-membre.component';
import { DonComponent } from './components/don/don.component';
import { EcashComponent } from './components/ecash/ecash.component';
import { BilletterieDetailComponent } from './pages/billetterie-detail/billetterie-detail.component';
import { MesBilletsComponent } from './pages/mes-billets/mes-billets.component';
import { BoutiqueDetailComponent } from './pages/boutique-detail/boutique-detail.component';
import { CartComponent } from './components/cart/cart.component';
import { MesCommandesComponent } from './pages/mes-commandes/mes-commandes.component';
import { authGuard } from './guards/auth.guard';
import { joueurGuard, staffGuard, parentGuard } from './guards/role.guard';
import { NotFoundComponent } from './pages/not-found/not-found.component';
import { adminGuard } from './guards/admin.guard';
import { InscriptionAcademieComponent } from './pages/academie/inscription/inscription.component';
import { DashboardParentComponent } from './pages/academie/dashboard-parent/dashboard-parent.component';
import { DashboardJoueurComponent } from './pages/espace-joueur/dashboard-joueur/dashboard-joueur.component';
import { DashboardStaffComponent } from './pages/espace-staff/dashboard-staff/dashboard-staff.component';
import { EspaceFanComponent } from './pages/espace-fan/espace-fan.component';
import { SondagesComponent } from './pages/sondages/sondages.component';
import { AdminSondagesComponent } from './pages/admin/admin-sondages/admin-sondages.component';
import { AdminCommandesComponent } from './pages/admin/admin-commandes/admin-commandes.component';
import { AdminBadgesComponent } from './pages/admin/admin-badges/admin-badges.component';
import { AdminSeancesComponent } from './pages/admin/admin-seances/admin-seances.component';
import { AdminJoueursPublicComponent } from './pages/admin/admin-joueurs-public/admin-joueurs-public.component';
import { AdminPalmaresComponent } from './pages/admin/admin-palmares/admin-palmares.component';
import { AdminLegendesComponent } from './pages/admin/admin-legendes/admin-legendes.component';
import { LegendesComponent } from './pages/legendes/legendes.component';
export const routes: Routes = [
  {
    // L'arbre admin DOIT être déclaré avant le layout public : celui-ci
    // contient le catch-all '**' qui sinon absorberait toutes les URLs
    // /admin/* (Angular matche les routes dans l'ordre de déclaration).
    path: 'admin',
    component: AdminLayoutComponent,
    canActivate: [adminGuard],
    children: [
      { path: '', component: DashboardComponent, pathMatch: 'full' },
      { path: 'utilisateurs', component: AdminUsersComponent },
      { path: 'actualites', component: AdminActualitesComponent },
      { path: 'matchs', component: AdminMatchsComponent },
      { path: 'classements', component: AdminClassementsComponent },
      { path: 'boutique', component: AdminBoutiqueComponent },
      { path: 'commandes', component: AdminCommandesComponent },
      { path: 'billetterie', component: AdminBilletterieComponent },
      { path: 'effectif', component: AdminEffectifComponent },
      { path: 'effectif-public', component: AdminJoueursPublicComponent },
      { path: 'palmares', component: AdminPalmaresComponent },
      { path: 'legendes', component: AdminLegendesComponent },
      { path: 'seances', component: AdminSeancesComponent },
      { path: 'staff', component: AdminStaffComponent },
      { path: 'academie', component: AdminAcademieComponent },
      { path: 'mediatheque', component: AdminMediathequeComponent },
      { path: 'notifications', component: AdminNotificationsComponent },
      { path: 'reclamations', component: AdminReclamationsComponent },
      { path: 'sondages', component: AdminSondagesComponent },
      { path: 'badges', component: AdminBadgesComponent },
      { path: 'parametres', component: AdminSettingsComponent }
    ]
  },
  {
    path: '',
    component: PublicLayoutComponent,
    children: [
      { path: '', component: HomeComponent, pathMatch: 'full' },
      { path: 'login', component: LoginComponent },
      { path: 'register', component: RegisterComponent },
      { path: 'adhesion', component: AdhesionComponent },
      { path: 'actualites', component: ActualitesComponent },
      { path: 'actualites/:id', component: ActualiteDetailComponent },
      { path: 'matchs', component: MatchesComponent },
      { path: 'classements', component: ClassementComponent },
      { path: 'boutique', component: BoutiqueComponent },
      { path: 'boutique/:id', component: BoutiqueDetailComponent },
      { path: 'billetterie', component: BilletterieComponent },
      { path: 'billetterie/:id', component: BilletterieDetailComponent },
      { path: 'effectif', component: EffectifComponent },
      { path: 'palmares', component: PalmaresComponent },
      { path: 'legendes', component: LegendesComponent },
      { path: 'espace-fan', component: EspaceFanComponent, canActivate: [authGuard] },
      { path: 'sondages', component: SondagesComponent },
      { path: 'panier', component: CartComponent, canActivate: [authGuard] },
      { path: 'profil', component: ProfilComponent, canActivate: [authGuard] },
      { path: 'profil/carte', component: CarteMembreComponent, canActivate: [authGuard] },
      { path: 'profil/ecash', component: EcashComponent, canActivate: [authGuard] },
      { path: 'profil/billets', component: MesBilletsComponent, canActivate: [authGuard] },
      { path: 'profil/commandes', component: MesCommandesComponent, canActivate: [authGuard] },
      { path: 'don', component: DonComponent, canActivate: [authGuard] },
      { path: 'academie/inscription', component: InscriptionAcademieComponent, canActivate: [authGuard] },
      { path: 'academie/mes-enfants', component: DashboardParentComponent, canActivate: [parentGuard] },
      { path: 'joueur/dashboard', component: DashboardJoueurComponent, canActivate: [joueurGuard] },
      { path: 'staff/dashboard', component: DashboardStaffComponent, canActivate: [staffGuard] },
      // Consultation des dossiers académie par le STAFF (l'ADMIN passe par /admin/academie)
      { path: 'staff/academie', component: AdminAcademieComponent, canActivate: [staffGuard] },
      { path: '**', component: NotFoundComponent }
    ]
  }
];