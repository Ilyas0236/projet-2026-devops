import { Routes } from '@angular/router';
import { PublicLayoutComponent } from './layouts/public-layout/public-layout.component';
import { HomeComponent } from './pages/home/home.component';
import { AdminLayoutComponent } from './layouts/admin-layout/admin-layout.component';
import { DashboardComponent } from './pages/admin/dashboard/dashboard.component';
import { BoutiqueComponent } from './pages/boutique/boutique.component';
import { BilletterieComponent } from './pages/billetterie/billetterie.component';
import { EffectifComponent } from './components/effectif/effectif.component';
import { AdminBoutiqueComponent } from './pages/admin/admin-boutique/admin-boutique.component';
import { AdminBilletterieComponent } from './pages/admin/admin-billetterie/admin-billetterie.component';
import { AdminEffectifComponent } from './pages/admin/admin-effectif/admin-effectif.component';
import { AdminUsersComponent } from './pages/admin/admin-users/admin-users.component';
import { AdminActualitesComponent } from './pages/admin/admin-actualites/admin-actualites.component';
import { AdminMatchsComponent } from './pages/admin/admin-matchs/admin-matchs.component';
import { AdminClassementsComponent } from './pages/admin/admin-classements/admin-classements.component';
import { AdminNotificationsComponent } from './pages/admin/admin-notifications/admin-notifications.component';
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
import { NotFoundComponent } from './pages/not-found/not-found.component';
import { adminGuard } from './guards/admin.guard';
import { InscriptionAcademieComponent } from './pages/academie/inscription/inscription.component';
import { DashboardParentComponent } from './pages/academie/dashboard-parent/dashboard-parent.component';
import { DashboardJoueurComponent } from './pages/espace-joueur/dashboard-joueur/dashboard-joueur.component';
import { DashboardStaffComponent } from './pages/espace-staff/dashboard-staff/dashboard-staff.component';
import { EspaceFanComponent } from './pages/espace-fan/espace-fan.component';
export const routes: Routes = [
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
      { path: 'espace-fan', component: EspaceFanComponent },
      { path: 'panier', component: CartComponent, canActivate: [authGuard] },
      { path: 'profil', component: ProfilComponent, canActivate: [authGuard] },
      { path: 'profil/carte', component: CarteMembreComponent, canActivate: [authGuard] },
      { path: 'profil/ecash', component: EcashComponent, canActivate: [authGuard] },
      { path: 'profil/billets', component: MesBilletsComponent, canActivate: [authGuard] },
      { path: 'profil/commandes', component: MesCommandesComponent, canActivate: [authGuard] },
      { path: 'don', component: DonComponent, canActivate: [authGuard] },
      { path: 'academie/inscription', component: InscriptionAcademieComponent, canActivate: [authGuard] },
      { path: 'academie/mes-enfants', component: DashboardParentComponent, canActivate: [authGuard] },
      { path: 'joueur/dashboard', component: DashboardJoueurComponent, canActivate: [authGuard] },
      { path: 'staff/dashboard', component: DashboardStaffComponent, canActivate: [authGuard] },
      { path: '**', component: NotFoundComponent }
    ]
  },
  {
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
      { path: 'billetterie', component: AdminBilletterieComponent },
      { path: 'effectif', component: AdminEffectifComponent },
      { path: 'notifications', component: AdminNotificationsComponent }
    ]
  }
];