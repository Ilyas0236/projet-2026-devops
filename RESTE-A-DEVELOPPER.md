# Reste à développer — Wydad Digital

> Généré le 2026-08-25, après clôture de la Phase 4.
> Source de vérité : `ROADMAP-DEPLOIEMENT.md` + audit du 2026-08-24.

## État d'avancement global

| Phase | Contenu | État |
|---|---|---|
| Vague 0 | Sécurité S1-S6 (login isActive, refresh token, membershipLevel) | ✅ Terminé |
| Design | Migration des 21 vues sombres vers thème clair blanc/rouge | ✅ Terminé |
| Phase 0 | Socle rôles & statuts (ENTRAINEUR/JOURNALISTE/PRESIDENT, EN_ATTENTE/VALIDE/REFUSE) | ✅ Terminé |
| Phase 1 | Justificatifs KYC via Cloudinary + consultation admin (URL signée) | ✅ Terminé |
| Phase 1 bis / ter | Finalisation validation comptes par documents | ✅ Terminé |
| Phase 2 | Billets VIP joueurs (PDF + QR) | ✅ Terminé |
| Phase 3 | Convocations & médias tactiques Cloudinary + boîte joueur unifiée | ✅ Terminé |
| **Phase 4** | **Messagerie groupe joueurs WebSocket/STOMP** | ✅ **Terminée 25/08** |
| Phase 5 | Appels vidéo/vocaux programmés | ⬜ À faire |
| Phase 5 bis | Espace Président | ⬜ À faire |
| Phase 6 | Tests ISTQB + automatisation (JaCoCo, Testcontainers, Playwright) | 🔄 En cours |
| Phase 7 | Finitions avant mise en production | ⬜ À faire |

**Coût Azure vérifié ce jour : 0,48 $ dépensés sur 100 $ — reste ≈ 99,52 $.**

---

## Phase 5 — Appels vidéo/vocaux programmés ⬜

Objectif : l'entraîneur et le président programment des appels ; les participants reçoivent une notification avec lien/date. Le média transite chez un fournisseur gratuit — aucune charge sur la VM.

### Prérequis (à toi)
1. **Créer un compte LiveKit Cloud** (recommandé) ou Daily.co — gratuit ~10 000 min/mois.
2. Récupérer API Key + Secret → à coller directement dans le `.env` serveur (PAS dans git ni dans le chat).

### Développement (moi)
3. Backend sports-service :
   - Endpoint `POST /api/sports/calls/tokens` : génère un jeton LiveKit signé (rôle/room contrôlés côté serveur).
   - Contrôle d'accès : entraîneur → room de sa catégorie ; président → room adhérents PREMIUM/joueurs/staff ; joueur/adhérent → ne peut que REJOINDRE une room où il est convié.
   - Entité `ScheduledCall` (titre, room, date/heure, organisateur, liste destinataires) + notification in-app avec lien profond `/appel/{id}`.
   - Tests : génération jeton, refus si non-autorisé sur room, notification émise (H2, pattern existant).
4. Frontend Angular :
   - Page « rejoindre l'appel » intégrant le SDK web LiveKit (`livekit-client`, léger).
   - Formulaire entraîneur/président « programmer un appel » (date, heure, cible : un joueur / toute la catégorie / premium).
   - Badge dans la boîte de réception du joueur quand un appel est programmé.
5. E2E : programmer → notifier → rejoindre avec deux comptes test.

⚠️ Si aucun compte cloud créé : la phase peut être développée en mode « stub » (jetons simulés) puis branchée en 30 min le jour où les clés existent.

---

## Phase 5 bis — Espace Président ⬜

Aucune dépendance externe — recommandée pour démarrer demain.

1. Interface dédiée rôle PRESIDENT (socle layout admin existant, thème clair blanc/rouge).
2. Messagerie président → agents (staff administratif) : horodatée, persistée, réutilise le modèle `team_messages` ou table dédiée `president_messages`.
3. Reçus PDF salaires/primes :
   - Génération PDF OpenPDF (déjà utilisé pour les billets VIP Phase 2 — même stack).
   - Upload Cloudinary folder privé séparé `president-receipts/` (type authenticated, URL signée 1 h — même modèle que KYC/médias).
   - Visibilité : le joueur voit SES reçus uniquement (ownership strict + test 403).
4. Lancement de réunions vidéo : réutilise Phase 5 quand elle existe.
5. Tests : création message, génération/upload reçu, ownership reçus, guards rôles.

---

## Phase 6 — Tests (ISTQB + automatisation) 🔄

Déjà fait : infra Karma (33 specs), suites sécurité par service (auth 34/34, content 56, sports 9 classes…).

### Restant
1. **JaCoCo ≥ 70 %** sur logique métier critique :
   - Ajouter le plugin au pom parent + seuil `check` sur sports-service et auth-service d'abord.
   - Combler les trous sur TeamChatService, CloudinaryService, TicketService (les classes qui portent l'argent et les droits).
2. **Testcontainers PostgreSQL** : remplacer les H2 `MODE=PostgreSQL` par de vrais conteneurs Postgres pour les tests d'intégration (audit : dépendance déclarée jamais utilisée). Priorité : auth-service (CHECK enums) et sports-service.
3. **Scénarios ISTQB restants** (liste explicite) :
   - Billet VIP dupliqué / scanné 2× (rejet QR déjà scanné).
   - Accès STANDARD aux appels refusé (dépend Phase 5).
   - Upload trop volumineux (> 25 Mo → 413).
   - Message vide chat refusé (couvert côté service — à prouver via HTTP 400).
   - Refus compte avec motif (admin refuse → email/notification motif visible).
4. **Playwright E2E navigateur** sur parcours critiques :
   - inscription → validation admin → connexion joueur ;
   - réception convocation → accusé lecture → suivi coach ;
   - chat deux onglets temps réel ;
   - billet VIP téléchargé + QR scanné.
5. Étendre specs Angular aux composants critiques restants (login, register, profil KYC, admin-demandes).

---

## Phase 7 — Finitions avant mise en production ⬜

1. Tests bout-en-bout de chaque parcours (création compte → validation → usage réel).
2. Relecture sécurité : guards JWT par rôle sur CHAQUE nouvel endpoint ajouté depuis l'audit (chat WS, médias, convocations batch, appels).
3. Nettoyage données de test AVANT vraie prod :
   - comptes auth_db ids **8–10** (coach.p3, joueur.p3a, joueur.p3b) + fake.coach id 7 ;
   - lignes sports_db : players, staff, sessions, convocations, player_documents, team_messages ;
   - documents KYC de démonstration sur Cloudinary folder `kyc-documents/`.
4. Changer le mot de passe admin seed (`admin@wac.ma`) — **toujours en attente ⚠️**

---

## Checklist prod (blocage ouverture publique)

| # | Action | Statut |
|---|---|---|
| 1 | Changer mot de passe admin seed | ❌ En attente |
| 2 | Supprimer pgAdmin du compose prod ; fermer ports hôte Postgres/Redis (5433/6379) | ❌ Exposés actuellement |
| 3 | HTTPS Caddy + domaine (eu.org/DuckDNS gratuit possible) ; CORS_ALLOWED_ORIGINS vers domaine final | ❌ |
| 4 | Sauvegardes : cron quotidien `pg_dumpall` + copie hors serveur hebdomadaire | ❌ |
| 5 | Monitoring minimal : cron `docker compose ps` + alerte email conteneur unhealthy | ❌ |
| 6 | Régénérer la clé API Cloudinary (secret passé par chat) | ❌ À faire en vrai prod |

---

## Option migration Oracle Cloud Always Free (à terme)

Oracle A1 Flex : 4 OCPU / 24 Go RAM **gratuit à vie** vs ~38–40 $/mois Azure.
Recommandation : rester sur Azure pendant le développement/démo (crédit large), basculer sur Oracle pour la pérennité.

Étapes (détaillées dans ROADMAP-DEPLOIEMENT.md §4.2) : créer compte Oracle → instance A1 4/24 Ubuntu → ports Security List + ufw → build arm64 sur place → transférer `.env` (recréer secrets) → `pg_dumpall` migration → DNS.

## 💡 Économiser le crédit Azure

La VM coûte **~0,045 $/h ≈ 1,08 $/jour**. Options concrètes :

1. **Deallocate la VM quand tu ne développes pas** (le plus efficace) :
   ```
   az vm deallocate -g <groupe-ressources> -n <nom-vm> --no-wait
   ```
   → 0 $ pendant l'arrêt (l'IP publique reste réservée si elle est Standard/SKU statique ; vérifier qu'elle n'est pas Basic dynamique, sinon elle change à chaque démarrage — dans ce cas noter l'IP ou passer en statique ~0,004 $/h).
   Redémarrage : `az vm start ...` (~2 min).
2. **Auto-shutdown quotidien** (portail Azure → VM → Operations → Auto-shutdown) : ex. arrêt automatique 01:00, tu la rallumes quand tu travailles. Économie ~8 h/nuit = ~35 % du coût sans rien faire.
3. Ne PAS downgrader B2s_v2 : les 2 vCPU/8 Go servent aux builds mvn sur la VM ; un downgrade ralentirait la boucle dev→deploy.
4. La bande passante sortante est quasi gratuite à ton usage (0,00004 $ ce mois-ci).

Avec auto-shutdown nocturne + deallocate manuel les jours sans dev : **la durée de vie passe de ~2,5 mois à 4–6 mois** de crédit.
