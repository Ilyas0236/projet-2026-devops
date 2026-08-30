---
name: v23-v31-messaging-attachments-sections
description: V2.3 (pièces jointes messagerie via Cloudinary) + V3.1 (CRUD complet sections billetterie) implémentés 28/08 ; back+front compilés ; NON déployés.
metadata:
  type: project
---

# V2.3 + V3.1 — pièces jointes messagerie + CRUD sections

## V2.3 — Pièces jointes messagerie (Cloudinary)

### Back
- `communication-service/pom.xml` : ajout `com.cloudinary:cloudinary-http5:2.0.0` (aligner version auth-service / sports-service)
- `application.yml` : ajout `cloudinary.*` (3 vars env, vide = mode dégradé local) + `spring.servlet.multipart: 10MB`
- `model/Message.java` : 5 nouveaux champs `attachmentPublicId/attachmentSecureUrl/attachmentResourceType/attachmentFileName/attachmentSizeBytes` (tous nullables, `ddl-auto: update` les ajoute automatiquement)
- `service/MessageMediaService.java` (NEW, 120 lignes) : `upload(file, senderEmail)` → `UploadResult(publicId, secureUrl, resourceType, fileName, sizeBytes, cloud)` + `signedUrl(publicId, resourceType)` + `detectResourceType(secureUrl)`. Folder `message-attachments/{email}/`, type `authenticated`, limite 10 Mo.
- `service/MessagingService.java` :
  - nouveau record `Attachment(publicId, secureUrl, resourceType, fileName, sizeBytes)`
  - `sendToStaffOrPlayer(recipientUserId, content, attachment)` accepte contenu vide si pièce jointe fournie
  - surcharge sans attachment conserve la rétro-compat
- `controller/MessagingController.java` :
  - `POST /api/sports/messaging/upload` (multipart) → UploadResult
  - `GET /api/sports/messaging/attachment/{messageId}` → `{url, resourceType}` (vérif participant ou ADMIN)
  - `SendRequest` étendu avec 5 champs attachment optionnels

### Front
- `ApiService.sendMessage(toUserId, content, attachment?)` — `attachment?: { publicId, secureUrl, resourceType, fileName, sizeBytes }`
- `ApiService.uploadMessageAttachment(file)` → multipart POST
- `ApiService.getMessageAttachmentUrl(messageId)` → URL signée fraîche
- `espace-joueur/dashboard-joueur` : pièce jointe en attente, bouton 📎, affichage inline image / lien PDF avec taille
- ⚠️ Autres composants (espace-staff, espace-joueur, president-dashboard) : NON migrés, à faire en propagation

## V3.1 — CRUD complet sections billetterie

### Back
- `TicketRepository.countBySectionId(Long)` : nouveau
- `EventService.createSection(eventId, SectionRequest)` : 409 si catégorie déjà présente sur l'event
- `EventService.deleteSection(sectionId)` : 409 si billets vendus, recalcule totalCapacity/availableSeats de l'event après suppression
- `SectionController` :
  - `POST /api/ticket/sections?eventId={id}` (ADMIN)
  - `DELETE /api/ticket/sections/{id}` (ADMIN)
  - `PATCH /api/ticket/sections/{id}` (existant, inchangé)

### Front
- `ApiService.createSection(eventId, body)` : POST avec query param `eventId`
- `ApiService.deleteSection(sectionId)` : DELETE
- `admin-billetterie.component.ts` : `newSection` model + `createSection()` + `deleteSection()` + `sectionCategories[]` (STANDARD/VIP/TRIBUNE/ULTRA/VIRAGE)
- `admin-billetterie.component.html` : bouton `+ Ajouter une section` en mode édition, formulaire inline (nom, catégorie, capacité, prix), bouton ✕ par section

## Build status
- 2 services back : `mvn compile` OK (communication-service, ticket-service)
- Front : `npm run build` OK (bundle `main-*.js`)

## Pièges notés
- Cloudinary : si vars env vides, mode dégradé local avec `publicId="local:..."` et `secureUrl=null` — l'UI ne pourra pas afficher l'image (afficher juste le nom de fichier)
- 5 autres composants UI utilisent `sendMessage()` (espace-staff, espace-joueur, president-dashboard) — l'API est rétro-compatible mais l'UI n'a pas encore le bouton 📎
- `isEdit` dans admin-billetterie ne déclenche pas le rechargement de `newEvent` après un PATCH (PATCH fonctionne mais l'utilisateur doit fermer/rouvrir pour voir l'effet)

## Why
L'utilisateur a demandé « continu tout le travai » après V2.2. V2.3 et V3.1 sont les deux seules tâches restantes. Tout est compilé mais NON déployé (rappel mémoire : pas de déploiement sans instruction explicite « deploy »).

## How to apply
Quand l'utilisateur dira « deploy » :
1. `git add` des 12 fichiers modifiés/créés
2. Commit unique : `feat(messaging,sections): V2.3 pièces jointes + V3.1 CRUD sections complet`
3. `cd communication-service && mvn package -DskipTests` puis pareil pour ticket-service
4. `cd ../.. && docker compose build communication-service ticket-service && docker compose up -d --no-deps communication-service ticket-service`
5. `cd wydad-frontend && npm run build && cd .. && docker compose build wydad-frontend && docker compose up -d --no-deps wydad-frontend`
6. Test E2E : POST /api/sports/messaging/upload avec un user STAFF → message avec image jointe → vérif affichage dans conversation joueur
