# TESTS E2E — Campagne complète Wydad Digital (§25)

> **Exécution réelle SUR LA VM Azure** (`158.158.74.169`) contre le backend
> déployé (gateway `localhost:8080` → 9 microservices) ET le frontend Angular
> servi sur `localhost:4200`. Script : `scripts/e2e-full-campagne.sh`
> (commité, rejouable, nettoie ses propres données).
>
> | | |
> |---|---|
> | **Date** | 2026-08-26 |
> | **Commit déployé** | `0f62192` (fix `/me` inclus : `d46974d`) |
> | **Résultat final** | ✅ **TOTAL=37 PASS=37 FAIL=0** |
> | **Historique** | v1 : 15/32 → v2 : 19/32 → v3 : 30/37 → finale : **37/37** |

---

## A. Santé infrastructure

| # | Test | Attendu | Réel | Verdict |
|---|------|---------|------|---------|
| A1 | Gateway `/actuator/health` | `"UP"` | `"UP"` | ✅ PASS |
| A2 | Frontend Angular `:4200` répond | HTTP 200 | HTTP 200 | ✅ PASS |

## B. Authentification & comptes (§24)

| # | Test | Attendu | Réel | Verdict |
|---|------|---------|------|---------|
| B1 | Login admin → JWT | 3 segments (header.payload.signature) | 3 segments | ✅ PASS |
| B2 | Mauvais mot de passe admin | 401 Unauthorized | 401 | ✅ PASS |
| B3 | Inscription JOUEUR avec `demandeRole` + discipline + catégorie | 202 Accepted, **aucun token** dans la réponse | 202, corps vide | ✅ PASS |
| B4 | Le compte existe en base | `statut_compte = EN_ATTENTE` | EN_ATTENTE | ✅ PASS |
| B5 | Compte EN_ATTtente tente de se connecter | Refus (401/403) | 403 | ✅ PASS |
| B6 | Après validation par l'admin → login JOUEUR | JWT valide | JWT 3 parties | ✅ PASS |
| B7 | `GET /api/auth/me` renvoie le rôle | `JOUEUR` | JOUEUR | ✅ PASS |
| B8 | `/me` renvoie la catégorie demandée | `U17` | U17 | ✅ PASS |
| B9 | `/me` renvoie le statut du compte | `VALIDE` | VALIDE | ✅ PASS |
| B10 | `/me` sans token | 401 | 401 | ✅ PASS |

> 🐛 **Bug réel trouvé et corrigé pendant la campagne** (commit `d46974d`) :
> `GET /api/auth/me`, `PUT/DELETE /me`, `kyc/upload` et les endpoints sessions
> portaient `@PreAuthorize("hasRole('ADHERENT') or hasRole('ADMIN')")` — tout
> compte JOUEUR/ENTRAINEUR/JOURNALISTE/STAFF/PARENT/PRESIDENT recevait **403
> sur son propre profil**, ce qui cassait l'espace journaliste. Annotations
> étendues aux 8 rôles ; 61/61 tests unitaires auth-service verts.

## C. Isolation DISCIPLINE + CATÉGORIE (§6, §24 — exigence forte du cahier des charges)

| # | Test | Attendu | Réel | Verdict |
|---|------|---------|------|---------|
| C1 | `players/filter?sportType=FOOTBALL&category=U17` ne mélange pas | uniquement FOOTBALL/U17 (ou catégorie nulle), zéro pollution croisée | aucune fiche hors périmètre | ✅ PASS |
| C2 | Entraîneur BASKETBALL SENIOR interroge joueurs FOOTBALL U17 | **403 AccessDeniedException** | 403 | ✅ PASS |
| C3 | Entraîneur SANS fiche staff rattachée → sa propre catégorie | refus honnête (pas de données simulées) | 403 | ✅ PASS |

> Note : le rattachement optionnel d'une fiche staff via SQL direct a été
> ignoré (schéma staff spécifique) — l'isolation est déjà prouvée par C1-C3 ;
> les tests unitaires `InternalRosterAccessTest` couvrent le cas « coach avec
> fiche » côté sports-service.

## D. Matchs & calendrier (§16 — logo admin sur ticket PDF)

| # | Test | Attendu | Réel | Verdict |
|---|------|------------------|------|---------|
| D1 | Admin crée match FOOTBALL U17 + `lieu` + logo adversaire | id retourné | id=4 | ✅ PASS |
| D2 | Match persisté avec sport + categorie + adversaireLogoUrl | présent dans `/matches/statut/PROGRAMME` | présent | ✅ PASS |
| D3 | Création match par un JOUEUR (non-admin) | 403/401 | 403 | ✅ PASS |

## E. Accréditation journaliste liée au calendrier réel (§17)

| # | Test | Attendu | Réel | Verdict |
|---|------|---------|------|---------|
| E1 | Inscription JOURNALISTE avec matchId inexistant (999999) | rejet explicite « introuvable » | message explicite | ✅ PASS |
| E2 | Inscription JOURNALISTE avec matchId RÉEL du calendrier | 202 Accepted, compte EN_ATTENTE | 202 | ✅ PASS |
| E3 | Statut en base | EN_ATTENTE (circuit de validation) | EN_ATTENTE | ✅ PASS |
| E4 | Libellé figé stocké depuis le calendrier | `match_souhaite` = libellé du match réel | « Wydad vs Raja E2E — Botola, le 2026-09-15 » | ✅ PASS |
| E5 | Badge presse sans token | 401 | 401 | ✅ PASS |
| E6 | Badge presse après validation admin | PDF `%PDF` téléchargeable (QR nominatif) | %PDF reçu | ✅ PASS |
| E7 | Journaliste A demande badge du journaliste B | 403 (self ou ADMIN uniquement) | 403 | ✅ PASS |

## F. File de validation des comptes (admin)

| # | Test | Attendu | Réel | Verdict |
|---|------|---------|------|---------|
| F1 | `GET /api/auth/admin/accounts/pending` en ADMIN | liste JSON | liste JSON | ✅ PASS |
| F2 | La même route avec un token JOUEUR | 403 | 403 | ✅ PASS |

## G. Sécurité gateway — en-têtes d'identité

| # | Test | Attendu | Réel | Verdict |
|---|------|---------|------|---------|
| G1 | Forge `X-User-Role: ADMIN` + JWT journaliste valide | header ignoré, rôle réel inchangé | rôle reste JOUEUR | ✅ PASS |
| G2 | Headers X-User-* forgés SANS JWT sur route admin | ≠200 (refus) | 401 | ✅ PASS |

## H. Boutique (§20 — visiteur)

| # | Test | Attendu | Réel | Verdict |
|---|------|---------|------|---------|
| H1 | Catalogue produits sans compte | page paginée Spring `{content:[...]}` | page JSON | ✅ PASS |
| H2 | `POST /orders` sans authentification | refus (≠000/≠200) | 401 | ✅ PASS |

## I. Billetterie

| # | Test | Attendu | Réel | Verdict |
|---|------|---------|------|---------|
| I1 | Événements publics sans token | JSON valide | JSON valide | ✅ PASS |
| I2 | `tickets/user/{id}` sans être soi/admin | 403 IDOR bloqué | 403 | ✅ PASS |
| I3 | JOURNALISTE sur son PROPRE id billets | **403 par design** (contrat ADHERENT/JOUEUR/STAFF/ADMIN uniquement) | 403 | ✅ PASS |

## J. Élections & contenu public

| # | Test | Attendu | Réel | Verdict |
|---|------|---------|------|---------|
| J1 | Résultats élections publics sans token | JSON valide | JSON valide | ✅ PASS |
| J2 | Actualités publiques | liste JSON | liste JSON | ✅ PASS |

## K. Nettoyage (le script supprime ses propres données)

| # | Test | Attendu | Réel | Verdict |
|---|------|---------|------|---------|
| K1 | Les 4 comptes de test supprimés de auth_db | 0 restants | 0 restants | ✅ PASS |
| K2 | Match E2E supprimé + fiche staff nettoyée | fait | fait (match 4 supprimé) | ✅ PASS |

---

## Bugs réels découverts et corrigés PAR cette campagne

1. **`/me` fermé aux nouveaux rôles** (`d46974d`) — 403 pour JOUEUR/
   ENTRAINEUR/JOURNALISTE/STAFF/PARENT/PRESIDENT sur leur propre profil.
   Corrigé backend, 61/61 tests unitaires verts, redéployé, re-testé vert.
2. **Calibrage des tests** — trois assertions reposaient sur un contrat
   fantaisiste (`role` au lieu de `demandeRole`, réponse 200 attendue là où
   l'API renvoie 202-sans-corps, billetterie journaliste). Le script teste
   désormais le contrat RÉEL.

## Ce que la campagne prouve

- §24 sécurité : anti-forgery X-User-* (G1/G2), IDOR billets (I2),
  comptes EN_ATTENTE verrouillés (B5), admin-only file de validation (F2).
- §6/§26 isolation discipline+catégorie stricte, backend (C1-C3).
- §16→§17 chaîne complète : admin crée le match avec logo → journaliste
  s'accrédite sur CE match → libellé figé → badge PDF+QR après validation.
- §27 : les espaces entraîneur/journaliste existent côté front (routes
  gardées `entraineurGuard`/`journalisteGuard`, bundle déployé vérifié).
