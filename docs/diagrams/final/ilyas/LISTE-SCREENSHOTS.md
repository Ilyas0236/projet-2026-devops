# 📸 Liste des screenshots à générer pour le rapport PFA

> **Objectif** : remplacer les 82 cadres "Screenshot à fournir" du dossier `images/` par de vrais screenshots PNG de l'application Angular en production.
>
> **Méthode** : déployer l'app sur la VM Azure (158.158.74.169) → se logger avec un compte de test → naviguer vers la route → faire une capture d'écran navigateur (1280×800 ou 1440×900) → nommer le fichier `.png` exactement comme dans la liste → écraser celui du dossier `images/`.
>
> **Outil capture** : `Win+Shift+S` (Windows) ou `Cmd+Shift+4` (Mac). Alternative : Outil de capture d'écran Windows. Format : PNG 24 bits.
>
> **Compte de test principal** : `ilyas@wac.ma` / `tamazirt` (ADMIN a accès à toutes les pages).
> **Comptes rôles** : voir tableau en bas.

---

## Section 1 — Espace public (8 screenshots)

| Fichier | Route | Composant | Rôle requis | Contenu à capturer |
|---|---|---|---|---|
| `accueil.png` | `/` | `LandingComponent` | Aucun (public) | Hero WAC + 4 dernières actualités + prochain match + classement botola + partenaires |
| `connexion.png` | `/login` | `LoginComponent` | Aucun (public) | Formulaire email + mot de passe, sans données saisies |
| `inscription.png` | `/inscription` | `RegisterComponent` | Aucun (public) | Formulaire multi-étapes (étape 1 = choix statut) |
| `actualites.png` | `/actualites` | `ArticlesListComponent` | Aucun (public) | Liste paginée des articles avec images |
| `actualite_detail.png` | `/actualites/1` | `ArticleDetailComponent` | Aucun (public) | Article complet (image + corps + date) |
| `matchs.png` | `/matchs` | `MatchesListComponent` | Aucun (public) | Calendrier des matchs (passés + à venir) |
| `billetterie.png` | `/billetterie` | `TicketListComponent` | Aucun (public) | Liste des événements ouverts à la billetterie |
| `billetterie_detail.png` | `/billetterie/1` | `TicketDetailComponent` | Aucun (public) | Plan de salle + sections tarifaires |
| `classements.png` | `/classement` | `ClassementPublicComponent` | Aucun (public) | Tableau classement botola |
| `palmares.png` | `/palmares` | `PalmaresComponent` | Aucun (public) | Galerie des trophées (upload photo + URL) |
| `legendes.png` | `/legendes` | `LegendesComponent` | Aucun (public) | Cartes des légendes du club |
| `stade.png` | `/stade` | `StadeComponent` | Aucun (public) | Vue stade + cover image |
| `transparence.png` | `/transparence` | `TransparencyComponent` | Aucun (public) | Page gouvernance/transparence |
| `not_found.png` | `/inexistante` | `NotFoundComponent` | Aucun (public) | Page 404 |

## Section 2 — Espace supporter / adhérent (7 screenshots)

| Fichier | Route | Composant | Rôle requis | Contenu à capturer |
|---|---|---|---|---|
| `tableau_de_bord.png` | `/adherent` | `DashboardAdherentComponent` | ADHERENT | Dashboard supporter (raccourcis + prochain match) |
| `abonnement.png` | `/abonnement` | `SubscriptionPlansComponent` | ADHERENT | Catalogue plans abonnement (4 plans minimum) |
| `mes_billets.png` | `/profil/billets` | `MyTicketsComponent` | ADHERENT | Liste billets achetés avec QR codes |
| `ma_carte.png` | `/profil/ma-carte` | `MyCardComponent` | ADHERENT (abonné) | Carte d'abonnement PDF en aperçu |
| `mon_profil.png` | `/profil` | `MyProfileComponent` | ADHERENT | Formulaire de profil (photo, nom, email) |
| `mon_ecash.png` | `/profil/ecash` | `MyECashComponent` | ADHERENT | Solde E-Cash + historique transactions |
| `mes_achats.png` | `/profil/achats` | `MyPurchasesComponent` | ADHERENT | Historique des achats (boutique + billetterie) |
| `mon_don.png` | `/profil/dons` | `MyDonationComponent` | ADHERENT | Formulaire don + historique |
| `gestion_utilisateurs.png` | `/profil/parametres` | `SettingsComponent` | ADHERENT | Préférences notifications + langue |

## Section 3 — Espace parent (B.18) (4 screenshots)

| Fichier | Route | Composant | Rôle requis | Contenu à capturer |
|---|---|---|---|---|
| `espace_parent.png` | `/parent` | `DashboardParentComponent` | PARENT | 5 tuiles raccourcis + liste enfants académie |
| `inscription_enfant.png` | `/parent/inscription-enfant` | `InscriptionAcademieComponent` | PARENT | Formulaire 4 étapes (identité, sport, caté, KYC) |
| `planning_enfant.png` | `/parent/enfant/1` | `EnfantPlanningComponent` | PARENT | Planning entraînements + convocations d'un enfant |
| `liste_elections.png` | `/elections` | `ElectionsPublicListComponent` | Aucun (public) | Liste des élections en cours + passées |

## Section 4 — Espaces joueur, staff, entraîneur (6 screenshots)

| Fichier | Route | Composant | Rôle requis | Contenu à capturer |
|---|---|---|---|---|
| `espace_joueur.png` | `/joueur` | `DashboardJoueurComponent` | JOUEUR | 4 onglets (Quotidien/Convos/Mes docs/Comms) + KPI |
| `espace_staff.png` | `/staff` | `DashboardStaffComponent` | STAFF | Sidebar raccourcis + onglet Comms |
| `espace_entraineur.png` | `/entraineur` | `DashboardEntraineurComponent` | ENTRAINEUR | Liste séances + convocations + messagerie |
| `convocation_seance.png` | `/entraineur/seances/nouvelle` | `SessionFormComponent` | ENTRAINEUR | Formulaire création séance |
| `convocation_match.png` | `/staff/convocations/nouvelle` | `ConvocationFormComponent` | STAFF ou ENTRAINEUR | Formulaire convocation match |
| `fiche_joueur.png` | `/joueur/fiche` | `PlayerProfileComponent` | JOUEUR | Fiche complète (poste, numéro, stats) |
| `feuille_match.png` | `/entraineur/feuille-match` | `MatchSheetComponent` | ENTRAINEUR | Feuille de match (titulaires + remplaçants) |
| `mediateque.png` | `/entraineur/mediatheque` | `MediaLibraryComponent` | STAFF ou ENTRAINEUR | Liste vidéos/schémas uploadés |

## Section 5 — Messagerie & communication (3 screenshots)

| Fichier | Route | Composant | Rôle requis | Contenu à capturer |
|---|---|---|---|---|
| `chat_equipe.png` | `/entraineur/team-chat` | `TeamChatComponent` | JOUEUR/STAFF/ENTRAINEUR | Chat groupe discipline avec messages |
| `appels_video.png` | `/entraineur/appels` | `VideoCallFormComponent` | STAFF/ENTRAINEUR | Liste appels programmés |
| `annonces.png` | `/annonces` | `AnnoncesComponent` | ADHERENT | Liste annonces officielles |

## Section 6 — Espace journaliste (B.17) (3 screenshots)

| Fichier | Route | Composant | Rôle requis | Contenu à capturer |
|---|---|---|---|---|
| `espace_journaliste.png` | `/journaliste` | `DashboardJournalisteComponent` | JOURNALISTE | Dashboard journaliste (statut accreditation) |
| `mes_demandes_journaliste.png` | `/journaliste/demandes` | `MesDemandesJournalisteComponent` | JOURNALISTE | Liste demandes + statuts (EN_ATTENTE/VALIDE/REFUSE) |
| `demande_accreditation.png` | `/journaliste/demandes/nouvelle` | `PressAccreditationFormComponent` | JOURNALISTE | Formulaire multipart (photo + média + n° carte) |
| `badge_presse.png` | (PDF généré) | OpenPDF | JOURNALISTE (après validation) | Badge PDF généré en aperçu image (convertir PDF→PNG) |

## Section 7 — Espace président (C.21) (5 screenshots)

| Fichier | Route | Composant | Rôle requis | Contenu à capturer |
|---|---|---|---|---|
| `dashboard_president.png` | `/president` | `PresidentDashboardComponent` | PRESIDENT | Bandeau doré GOL + sélecteur discipline/catég |
| `president_discussions.png` | `/president/discussions` | `PresidentDiscussionsComponent` | PRESIDENT | Vue team-chat lecture seule |
| `annuaire_president.png` | `/president/annuaire` | `AnnPresidentComponent` | PRESIDENT | Annuaire filtré par discipline |
| `recu_salaire.png` | `/president/recus/nouveau` | `SalaryReceiptFormComponent` | PRESIDENT | Formulaire émission reçu salaire/prime |
| `liste_recus.png` | `/president/recus` | `TousRecusComponent` | PRESIDENT | Tableau de tous les reçus émis |
| `appel_president.png` | `/president/video` | `VideoCallFormComponent` | PRESIDENT | Programmation appel vidéo (cible=utilisateurs même discipline) |

## Section 8 — Espace élections (5 screenshots)

| Fichier | Route | Composant | Rôle requis | Contenu à capturer |
|---|---|---|---|---|
| `mes_elections.png` | `/profil/elections` | `MyElectionsComponent` | ADHERENT (abonné) | Liste élections + bandeau 403 VOTE_REQUIRES_MEMBERSHIP si non abonné |
| `vote_election.png` | `/elections/1` | `VoteElectionComponent` | ADHERENT (abonné) | Interface vote (liste candidats + bouton confirmer) |
| `resultats_election.png` | `/elections/1/resultats` | `ElectionResultsComponent` | Aucun (public) | Résultats (graphique barres + % + gagnant) |
| `liste_sondages.png` | `/sondages` | `PollsListComponent` | Aucun (public) | Liste sondages actifs |
| `vote_sondage.png` | `/sondages/1` | `VotePollComponent` | ADHERENT | Interface vote sondage (choix multiple) |

## Section 9 — Boutique (4 screenshots)

| Fichier | Route | Composant | Rôle requis | Contenu à capturer |
|---|---|---|---|---|
| `catalogue_produit.png` | `/boutique` | `ShopCatalogComponent` | ADHERENT | Catalogue avec filtres catégorie |
| `boutique_detail.png` | `/boutique/1` | `ProductDetailComponent` | ADHERENT | Fiche produit (images + tailles + prix) |
| `panier.png` | `/boutique/panier` | `CartComponent` | ADHERENT | Panier avec articles + total |
| `commande.png` | `/boutique/commande` | `OrderCheckoutComponent` | ADHERENT | Récap commande + choix paiement |
| `historique_commandes.png` | `/profil/commandes` | `OrderHistoryComponent` | ADHERENT | Historique avec statuts (EN ATTENTE/PAYEE/EXPEDIEE) |

## Section 10 — Espace fan (gamification) (3 screenshots)

| Fichier | Route | Composant | Rôle requis | Contenu à capturer |
|---|---|---|---|---|
| `espace_fan.png` | `/espace-fan` | `FanDashboardComponent` | ADHERENT | Hero classement + podium top 3 + 4 onglets |
| `leaderboard.png` | `/espace-fan/leaderboard` | `LeaderboardComponent` | ADHERENT | Classement public des supporters |
| `mes_badges.png` | `/espace-fan/badges` | `MyBadgesComponent` | ADHERENT | Grille de badges obtenus + objectifs |
| `mes_points.png` | `/espace-fan/points` | `MyPointsComponent` | ADHERENT | Solde + historique gains |

## Section 11 — Back-office administrateur (29 screenshots)

| Fichier | Route | Composant | Rôle requis | Contenu à capturer |
|---|---|---|---|---|
| `espace_admin.png` | `/admin` | `AdminDashboardComponent` | ADMIN | Dashboard admin (stats globales) |
| `admin_users.png` | `/admin/utilisateurs` | `AdminUsersComponent` | ADMIN | Tableau des 9 rôles + filtres |
| `admin_demandes.png` | `/admin/demandes` | `AdminDemandesComponent` | ADMIN | Liste comptes EN_ATTENTE (Bouton Valider/Refuser) |
| `admin_presse_demandes.png` | `/admin/presse` | `AdminPresseDemandesComponent` | ADMIN | File demandes presse (B.17) |
| `admin_rapports.png` | `/admin/rapports` | `AdminRapportsComponent` | ADMIN | Génération rapports |
| `admin_actualites.png` | `/admin/actualites` | `AdminActualitesComponent` | ADMIN | CRUD articles |
| `admin_matchs.png` | `/admin/matchs` | `AdminMatchsComponent` | ADMIN | CRUD matchs + saisie score |
| `admin_convocations.png` | `/admin/convocations` | `AdminConvocationsComponent` | ADMIN | Liste convocations |
| `admin_classements.png` | `/admin/classements` | `AdminClassementsComponent` | ADMIN | CRUD classements |
| `admin_boutique.png` | `/admin/boutique` | `AdminBoutiqueComponent` | ADMIN | CRUD produits boutique |
| `admin_commandes.png` | `/admin/commandes` | `AdminCommandesComponent` | ADMIN | Liste commandes |
| `admin_achats.png` | `/admin/achats` | `AdminAchatsComponent` | ADMIN | Inventaire transactions (4 onglets CSV/PDF) |
| `admin_billetterie.png` | `/admin/billetterie` | `AdminBilletterieComponent` | ADMIN | CRUD événements billetterie |
| `admin_plans_abonnement.png` | `/admin/plans-abonnement` | `AdminSubscriptionPlansComponent` | ADMIN | CRUD plans abonnement |
| `admin_effectif.png` | `/admin/effectif` | `AdminEffectifComponent` | ADMIN | CRUD effectif joueurs |
| `admin_effectif_public.png` | `/admin/effectif-public` | `AdminJoueursPublicComponent` | ADMIN | Fiches publiques |
| `admin_palmares.png` | `/admin/palmares` | `AdminPalmaresComponent` | ADMIN | CRUD palmares (upload photo ou URL) |
| `admin_legendes.png` | `/admin/legendes` | `AdminLegendesComponent` | ADMIN | CRUD légendes |
| `admin_seances.png` | `/admin/seances` | `AdminSeancesComponent` | ADMIN | CRUD séances entraînement |
| `admin_staff.png` | `/admin/staff` | `AdminStaffComponent` | ADMIN | CRUD fiches staff |
| `admin_academie.png` | `/admin/academie` | `AdminAcademieComponent` | ADMIN | CRUD académie (B.18) |
| `admin_mediatheque.png` | `/admin/mediatheque` | `AdminMediathequeComponent` | ADMIN | CRUD vidéos/schémas |
| `admin_notifications.png` | `/admin/notifications` | `AdminNotificationsComponent` | ADMIN | Push in-app manager |
| `admin_reclamations.png` | `/admin/reclamations` | `AdminReclamationsComponent` | ADMIN | Support tickets |
| `admin_sondages.png` | `/admin/sondages` | `AdminSondagesComponent` | ADMIN | CRUD sondages |
| `admin_elections.png` | `/admin/elections` | `AdminElectionsComponent` | ADMIN | CRUD élections (B.8) |
| `admin_badges.png` | `/admin/badges` | `AdminBadgesComponent` | ADMIN | CRUD badges (seed WAC_*) |
| `admin_defis.png` | `/admin/defis` | `AdminDefisComponent` | ADMIN | CRUD défis temporels (C2) |
| `admin_bareme_points.png` | `/admin/bareme-points` | `AdminPointsRulesComponent` | ADMIN | CRUD barème points (C3) |
| `admin_parametres.png` | `/admin/parametres` | `AdminSettingsComponent` | ADMIN | Paramètres club (sponsors, réseaux, backgrounds) |

## Section 12 — Logos & assets (1 screenshot)

| Fichier | Source | Description |
|---|---|---|
| `nom.png` | `/assets/images/logo-wac.png` | Logo officiel WAC (récupérer depuis le site https://www.wac.ma ou demander au porteur) |

## Section 13 — Diagrammes de collaboration (1)

| Fichier | Source | Description |
|---|---|---|
| `collab-accreditation.png` | (déjà fourni en .puml) | Diagramme de collaboration — pas un screenshot |

---

## 📊 Comptes de test (mot de passe = `tamazirt` pour tous)

| Compte | Email | Rôle | Pages accessibles |
|---|---|---|---|
| Admin | `admin@wac.ma` | ADMIN | Tout (29 pages admin + tous les espaces) |
| Ilyas | `ilyas@wac.ma` | ADHERENT | Espace supporter + fan + parent (si kid) |
| Hakim | `hakim@wac.ma` | JOUEUR | Espace joueur |
| Lopez | `lopez@wac.ma` | ENTRAINEUR | Espace entraîneur + convocations |
| Staf | `staf@gmail.com` ⚠️ | STAFF | Espace staff (NB: pas `staf@wac.ma`) |
| Brahim | `brahim@wac.ma` | PRESIDENT | Espace président (C.21) — discipline FOOTBALL |
| Khalid | `khalid@wac.ma` | JOURNALISTE | Espace journaliste (B.17) |
| Parent | `parent@wac.ma` | PARENT | Espace parent (B.18) — enfants académie |
| Visiteur | (aucun) | VISITEUR | Pages publiques uniquement (guest checkout) |

> ⚠️ **Piège connu** : `staf@gmail.com` (PAS `staf@wac.ma` — le `staf@wac.ma` n'existe pas en prod, leçon du 04/09).

---

## 🛠️ Méthode de capture rapide

### Sur Windows (navigateur Chrome sur la VM)

1. Ouvrir `https://158.158.74.169/` (ou `http://158.158.74.169:4200/` si frontend direct)
2. Se logger avec le bon compte
3. Naviguer vers la route
4. **Attendre 1-2 secondes** que les données se chargent
5. `Win+Shift+S` → sélection rectangulaire → sauvegarder en PNG
6. Renommer en `<nom-fichier>.png` exactement comme dans la liste
7. **Via SCP ou RDP** : déposer dans `C:\Users\USER\Downloads\wac\wydad-digital-parent\docs\diagrams\final\ilyas\images\`
8. Recharger le `.tex` dans le dossier `ilyas/`
9. Recompiler avec pdflatex

### Recommandations capture

- **Résolution** : 1280×800 minimum (1440×900 idéal)
- **Zoom navigateur** : 100 % (pas de zoom écran)
- **Mode** : pas de navigation privée (garder le login)
- **Fenêtre** : taille maximale
- **Attendre** : animations Angular terminées + données affichées
- **Couper** : barre d'URL et bookmarks (juste la page applicative)

---

## ✅ Checklist rapide (82 captures)

- [ ] Section 1 — Public (14) : accueil, connexion, inscription, actualités (2), matchs, billetterie (2), classements, palmares, legendes, stade, transparence, 404
- [ ] Section 2 — Supporter (9) : dashboard, abonnement, mes billets, ma carte, profil, e-cash, mes achats, mon don, paramètres
- [ ] Section 3 — Parent (4) : dashboard, inscription enfant, planning enfant, liste élections
- [ ] Section 4 — Joueur/Staff/Entraineur (8) : 3 dashboards, 2 formulaires convocation, fiche joueur, feuille match, mediatheque
- [ ] Section 5 — Messagerie (3) : chat équipe, appels, annonces
- [ ] Section 6 — Journaliste (4) : dashboard, mes demandes, demande, badge
- [ ] Section 7 — Président (6) : dashboard, discussions, annuaire, reçu, liste reçus, appel
- [ ] Section 8 — Élections (5) : mes élections, vote, résultats, sondages (2)
- [ ] Section 9 — Boutique (5) : catalogue, détail, panier, commande, historique
- [ ] Section 10 — Fan (4) : dashboard, leaderboard, badges, points
- [ ] Section 11 — Admin (29) : tous les onglets admin (cf tableau)
- [ ] Section 12 — Logos (1) : logo WAC
- [ ] Section 13 — Collab (1) : déjà en .puml

**Total : 82 captures à faire** (le `collab-accreditation.png` est déjà un vrai diagramme, pas un screenshot).

---

## 🚀 Workflow final

1. **Faire les 82 captures** en suivant la checklist
2. **Écraser** les fichiers correspondants dans `ilyas/images/`
3. **Compiler** : `pdflatex rapport-pfa-wydad-digital.tex` (deux fois pour la TOC)
4. **Vérifier** que toutes les figures apparaissent (certaines peuvent nécessiter un ajustement de taille)
5. **Convertir** en PDF final pour la soutenance
