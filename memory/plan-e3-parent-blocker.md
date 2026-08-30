---
name: plan-e3-parent-blocker
description: Blocker E.3 — notification parent à la convocation joueur. Pas de FK parent-enfant dans Player ni User ; le champ reste à concevoir avec le propriétaire.
metadata:
  type: project
---

# Blocker E.3 — Notifier le parent d'un joueur convoqué

**Contexte** : plan qualité §E.3 demande de notifier le parent en plus du
joueur quand ce dernier est convoqué. Cible : cloche `/parent/dashboard`.

**Constat** (vérifié 2026-08-28) :
- `sports-service/.../model/Player.java` : aucun champ `parentUserId` /
  `parentEmail` / `parentOf`.
- `auth-service/.../model/User.java` : aucun champ `parentOf` /
  `parentUserId` (recherche `parentOf|parentUserId|parent_id|parentEmail`).
- Seul `AcademyMember` (jeunes de l'académie, statut junior) avait une
  logique parent évoquée ; les joueurs SENIOR/U18/U20 n'ont aucun lien
  formalisé vers un compte parent.

**Conséquence** : impossible d'envoyer une notification ciblée au parent
depuis `MatchConvocationService` sans une migration schéma (ajout d'un
champ `parentUserId` ou table de liaison `parent_player`).

**Décision** : hook E.3 NON implémenté. À débattre avec le propriétaire :
- (a) ajouter `parentUserId` sur `Player` (nullable, null pour SENIOR) ;
- (b) créer une table `parent_links(parent_id, player_id, relation)` ;
- (c) ne pas implémenter (les parents n'ont pas de rôle "Parent" sur
  joueurs pros, c'est un concept académique).

**Tant que non décidé** : la cloche du parent ne reçoit rien de spécifique
aux convocations. Ils voient les notifs globales (résultats élections,
annonces club) via le broadcast.

**Lien plan** : [[site-qualite-plan-2026-08-28]] §E.3.
