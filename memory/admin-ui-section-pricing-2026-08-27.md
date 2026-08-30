---
name: admin-ui-section-pricing-2026-08-27
description: "Admin UI gère la grille tarifaire des sections via PATCH in-place (commit 15dae9a, déployé 27/08). Remplace l'approche anti-pattern SQL UPDATE."
metadata:
  type: project
  modified: 2026-08-27T01:25:00Z
---

## Admin UI — gestion de la grille tarifaire des sections (commit 15dae9a, 27/08)

**Pourquoi cette PR** : l'utilisateur a refusé que je corrige les prix de sections
via SQL direct (`scripts/fix-section-prices.sh` retiré). Il veut que **l'admin
gère tout via l'interface**, pas de données statiques.

**Le problème résolu** : `PUT /api/ticket/events/{id}` du backend supprimait
puis recréait toutes les sections de l'événement, ce qui **violait la FK
`tickets.section_id`** dès qu'un billet avait été vendu. Donc l'admin ne
pouvait pas corriger un prix via l'UI même si le modal l'avait permis.

**La solution** :
- **Nouveau endpoint** : `PATCH /api/ticket/sections/{id}` (ADMIN)
  - `SectionController` dédié, `SectionPatchRequest` (tous champs optionnels)
  - `EventService.updateSection()` : met à jour in-place, refuse prix≤0 (400)
    et capacité < billets vendus (400)
  - Recalcule `availableSeats` proportionnellement à `capacity` (cohérence FK)
- **Admin UI** : nouveau bloc "Sections & Tarifs" dans le modal billetterie,
  visible uniquement en mode édition, avec champs éditables (name, capacity,
  price, vendus en lecture seule)
- **`saveEvent()`** : appelle PUT event, puis PATCH par section (les deux
  ensemble appliquent match + grille). Si PUT échoue, pas de PATCH.
- **`api.service.ts`** : nouvelle méthode `patchSection()`

**Tests** : 6/6 verts (`SectionPatchSecurityTest`) : anonyme/ADHERENT/STAFF → 403,
ADMIN → 200, prix 0 → 400, section inexistante → 404. Suite ticket-service
complète : 34/34 verts, aucune régression.

**Déploiement prod** : `git pull && mvn -pl ticket-service package && docker
compose build ticket-service && docker compose up -d --no-deps ticket-service`
(idem pour frontend). Vérification : section id=3 de l'event 4 passée à
300 DH avec 32 billets déjà vendus — la BDD n'a pas été altérée en SQL.

**Tests BDD après patch** :
- Event 2 / section 2 "VIP Joueurs" : price=300 DH (catégorie VIP, grille)
- Event 4 / section 3 "Tribune VIP Joueurs" : price=300 DH (catégorie VIP, grille)

**Grille tarifaire de référence** :
- VIP = 300 DH
- TRIBUNE_OFFICIELLE = 100 DH
- ULTRA = 80 DH
- VIRAGE = 50 DH

Voir [[achata-sans-compte-visiteur-b28]] (contexte B.28) et
[[b28-front-deploy-2026-08-27]] (date du fix de la page billetterie).
