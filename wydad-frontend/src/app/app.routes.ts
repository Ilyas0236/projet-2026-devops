import { Routes } from '@angular/router';
import { PublicLayoutComponent } from './layouts/public-layout/public-layout.component';
import { EspaceLayoutComponent } from './layouts/espace-layout/espace-layout.component';
import { HomeComponent } from './pages/home/home.component';
import { AdminLayoutComponent } from './layouts/admin-layout/admin-layout.component';
import { DashboardComponent } from './pages/admin/dashboard/dashboard.component';
import { BoutiqueComponent } from './pages/boutique/boutique.component';
import { BilletterieComponent } from './pages/billetterie/billetterie.component';
import { EffectifComponent } from './components/effectif/effectif.component';
import { PalmaresComponent } from './pages/palmares/palmares.component';
import { AdminBoutiqueComponent } from './pages/admin/admin-boutique/admin-boutique.component';
import { AdminBilletterieComponent } from './pages/admin/admin-billetterie/admin-billetterie.component';
import { AdminSubscriptionPlansComponent } from './pages/admin/admin-subscription-plans/admin-subscription-plans.component';
import { AdminEffectifComponent } from './pages/admin/admin-effectif/admin-effectif.component';
import { AdminUsersComponent } from './pages/admin/admin-users/admin-users.component';
import { AdminDemandesComponent } from './pages/admin/admin-demandes/admin-demandes.component';
import { AdminPresseDemandesComponent } from './pages/admin/admin-presse-demandes/admin-presse-demandes.component';
import { AdminRapportsComponent } from './pages/admin/admin-rapports/admin-rapports.component';
import { AdminActualitesComponent } from './pages/admin/admin-actualites/admin-actualites.component';
import { AdminMatchsComponent } from './pages/admin/admin-matchs/admin-matchs.component';
import { AdminClassementsComponent } from './pages/admin/admin-classements/admin-classements.component';
import { AdminNotificationsComponent } from './pages/admin/admin-notifications/admin-notifications.component';
import { AdminReclamationsComponent } from './pages/admin/admin-reclamations/admin-reclamations.component';
import { AdminSettingsComponent } from './pages/admin/admin-settings/admin-settings.component';
import { AdminMediathequeComponent } from './pages/admin/admin-mediatheque/admin-mediatheque.component';
import { AdminStaffComponent } from './pages/admin/admin-staff/admin-staff.component';
import { AdminAcademieComponent } from './pages/admin/admin-academie/admin-academie.component';
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
import { AbonnementComponent } from './pages/abonnement/abonnement.component';
import { MesAchatsComponent } from './pages/mes-achats/mes-achats.component';
import { MesBilletsComponent } from './pages/mes-billets/mes-billets.component';
import { BoutiqueDetailComponent } from './pages/boutique-detail/boutique-detail.component';
import { CartComponent } from './components/cart/cart.component';
import { MesCommandesComponent } from './pages/mes-commandes/mes-commandes.component';
import { authGuard } from './guards/auth.guard';
import { roleGuard, joueurGuard, staffGuard, parentGuard, presidentGuard, entraineurGuard, journalisteGuard } from './guards/role.guard';
import { NotFoundComponent } from './pages/not-found/not-found.component';
import { adminGuard } from './guards/admin.guard';
import { InscriptionAcademieComponent } from './pages/academie/inscription/inscription.component';
import { DashboardParentComponent } from './pages/academie/dashboard-parent/dashboard-parent.component';
import { DashboardJoueurComponent } from './pages/espace-joueur/dashboard-joueur/dashboard-joueur.component';
import { DashboardStaffComponent } from './pages/espace-staff/dashboard-staff/dashboard-staff.component';
import { PresidentDashboardComponent } from './pages/president/president-dashboard/president-dashboard.component';
import { PresidentDiscussionsComponent } from './pages/president/president-discussions/president-discussions.component';
import { DashboardEntraineurComponent } from './pages/espace-entraineur/dashboard-entraineur/dashboard-entraineur.component';
import { DashboardJournalisteComponent } from './pages/espace-journaliste/dashboard-journaliste/dashboard-journaliste.component';
import { MesDemandesJournalisteComponent } from './pages/espace-journaliste/mes-demandes-journaliste/mes-demandes-journaliste.component';
import { EspaceFanComponent } from './pages/espace-fan/espace-fan.component';
import { SondagesComponent } from './pages/sondages/sondages.component';
import { AdminSondagesComponent } from './pages/admin/admin-sondages/admin-sondages.component';
import { ElectionsComponent } from './pages/elections/elections.component';
import { MesElectionsComponent } from './pages/mes-elections/mes-elections.component';
import { AdminElectionsComponent } from './pages/admin/admin-elections/admin-elections.component';
import { AdminCommandesComponent } from './pages/admin/admin-commandes/admin-commandes.component';
import { AdminAchatsComponent } from './pages/admin/admin-achats/admin-achats.component';
import { AdminBadgesComponent } from './pages/admin/admin-badges/admin-badges.component';
import { AdminSeancesComponent } from './pages/admin/admin-seances/admin-seances.component';
import { AdminJoueursPublicComponent } from './pages/admin/admin-joueurs-public/admin-joueurs-public.component';
import { AdminPalmaresComponent } from './pages/admin/admin-palmares/admin-palmares.component';
import { AdminLegendesComponent } from './pages/admin/admin-legendes/admin-legendes.component';
import { AdminConvocationsComponent } from './pages/admin/admin-convocations/admin-convocations.component';
import { LegendesComponent } from './pages/legendes/legendes.component';
import { StadeComponent } from './pages/stade/stade.component';
import { TransparenceComponent } from './pages/transparence/transparence.component';
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
      { path: 'demandes', component: AdminDemandesComponent },
      { path: 'presse/demandes', component: AdminPresseDemandesComponent },
      { path: 'rapports-financiers', component: AdminRapportsComponent },
      { path: 'utilisateurs', component: AdminUsersComponent },
      { path: 'actualites', component: AdminActualitesComponent },
      { path: 'matchs', component: AdminMatchsComponent },
      { path: 'convocations', component: AdminConvocationsComponent },
      { path: 'classements', component: AdminClassementsComponent },
      { path: 'boutique', component: AdminBoutiqueComponent },
      { path: 'commandes', component: AdminCommandesComponent },
      { path: 'achats', component: AdminAchatsComponent },
      { path: 'billetterie', component: AdminBilletterieComponent },
      { path: 'abonnements/plans', component: AdminSubscriptionPlansComponent },
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
      { path: 'elections', component: AdminElectionsComponent },
      { path: 'badges', component: AdminBadgesComponent },
      { path: 'parametres', component: AdminSettingsComponent }
    ]
  },
  {
    // Espaces connectés (joueur, staff/entraîneur, président, journaliste,
    // parent) : layout dédié SANS header public — comme l'arbre admin,
    // déclaré AVANT le layout public dont le catch-all '**' absorberait
    // sinon toutes ces URLs.
    path: 'joueur',
    component: EspaceLayoutComponent,
    canActivate: [joueurGuard],
    children: [
      { path: 'dashboard', component: DashboardJoueurComponent }
    ]
  },
  {
    path: 'staff',
    component: EspaceLayoutComponent,
    canActivate: [staffGuard],
    children: [
      { path: 'dashboard', component: DashboardStaffComponent },
      // Consultation des dossiers académie par le STAFF (l'ADMIN passe par /admin/academie)
      { path: 'academie', component: AdminAcademieComponent }
    ]
  },
  {
    path: 'entraineur',
    component: EspaceLayoutComponent,
    canActivate: [entraineurGuard],
    children: [
      { path: 'dashboard', component: DashboardEntraineurComponent }
    ]
  },
  {
    // Espace Président (§11-§15) — thème clair, messagerie + reçus + vidéo.
    path: 'president',
    component: EspaceLayoutComponent,
    canActivate: [presidentGuard],
    children: [
      { path: 'dashboard', component: PresidentDashboardComponent },
      { path: 'discussions', component: PresidentDiscussionsComponent }
    ]
  },
  {
    // Espace Journaliste — destination du login (§27).
    path: 'journaliste',
    component: EspaceLayoutComponent,
    canActivate: [journalisteGuard],
    children: [
      { path: 'accueil', component: DashboardJournalisteComponent },
      { path: 'demandes', component: MesDemandesJournalisteComponent }
    ]
  },
  {
    // Espace Parent académies.
    path: 'academie',
    component: EspaceLayoutComponent,
    canActivate: [parentGuard],
    children: [
      { path: 'mes-enfants', component: DashboardParentComponent }
    ]
  },
  {
    path: '',
    component: PublicLayoutComponent,
    children: [
      { path: '', component: HomeComponent, pathMatch: 'full' },
      { path: 'login', component: LoginComponent },
      { path: 'register', component: RegisterComponent },
      { path: 'adhesion', redirectTo: '/abonnement', pathMatch: 'full' },
      { path: 'actualites', component: ActualitesComponent },
      { path: 'actualites/:id', component: ActualiteDetailComponent },
      { path: 'matchs', component: MatchesComponent },
      { path: 'classements', component: ClassementComponent },
      { path: 'boutique', component: BoutiqueComponent },
      { path: 'boutique/:id', component: BoutiqueDetailComponent },
      { path: 'billetterie', component: BilletterieComponent },
      { path: 'billetterie/:id', component: BilletterieDetailComponent },
      { path: 'abonnement', component: AbonnementComponent },
      { path: 'mes-achats', component: MesAchatsComponent, canActivate: [authGuard] },
      { path: 'effectif', component: EffectifComponent },
      { path: 'palmares', component: PalmaresComponent },
      { path: 'legendes', component: LegendesComponent },
      { path: 'stade', component: StadeComponent },
      { path: 'transparence', component: TransparenceComponent },
      { path: 'espace-fan', component: EspaceFanComponent, canActivate: [authGuard] },
      { path: 'sondages', component: SondagesComponent },
      { path: 'elections', component: ElectionsComponent },
      { path: 'mes-elections', component: MesElectionsComponent, canActivate: [authGuard] },
      { path: 'panier', component: CartComponent, canActivate: [authGuard] },
      { path: 'profil', component: ProfilComponent, canActivate: [authGuard] },
      { path: 'profil/carte', component: CarteMembreComponent, canActivate: [authGuard] },
      { path: 'profil/ecash', component: EcashComponent, canActivate: [authGuard] },
      { path: 'profil/billets', component: MesBilletsComponent, canActivate: [authGuard] },
      { path: 'profil/commandes', component: MesCommandesComponent, canActivate: [authGuard] },
      { path: 'don', component: DonComponent, canActivate: [authGuard] },
      // Le backend n'accepte l'inscription académie que PARENT/ADMIN —
      // on aligne la garde frontend pour éviter un 403 après remplissage.
      { path: 'academie/inscription', component: InscriptionAcademieComponent, canActivate: [roleGuard('PARENT', 'ADMIN')] },
      { path: '**', component: NotFoundComponent }
    ]
  }
];