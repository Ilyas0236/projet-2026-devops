---
name: v22-factures-pdf
description: Tâche V2.2 — Factures PDF (vue comptable) sur ticket/abonnement/commande. Implémentée et compilée 28/08 ; front UNBUILD en attente d'instruction déploiement.
metadata:
  type: project
---

# V2.2 — Factures PDF (vue comptable) ✅ implémentée 28/08/2026

Trois factures PDF A4 distinctes des documents d'accès (billet/carte), avec émetteur, n° de facture, lignes, total TTC, mentions légales.

## Back

| Service | Service PDF | Endpoint | IDOR |
|---|---|---|---|
| ticket-service | `TicketPdfService.generateInvoicePdf(Ticket)` (A4, n° `BILL-{ticketNumber}`) | `GET /api/ticket/tickets/{id}/invoice` | `assertOwnership` (mêmes gardes que `/pdf`) |
| auth-service | `PdfService.buildSubscriptionInvoicePdfBytes(sub, user)` (A4, n° `WAC-SUB-{id}`) | `GET /api/auth/subscriptions/{id}/invoice` | `loadOwnedSubscription` (helper partagé avec `/pdf`, ADMIN bypass) |
| shop-service | `OrderPdfService.generateInvoicePdf(ShopOrder)` (A4, n° = `orderNumber`) | `GET /api/shop/orders/{orderNumber}/invoice` | `findByOrderNumberAndUserEmail` (non-lucifer : 404 si pas propriétaire) + bypass ADMIN via `X-User-Role` |

**Pièges corrigés** :
- `PdfService.buildSubscriptionPdfBytes` n'avait pas de `document.close()` + `}` de fermeture → compile fail "illegal start of expression" ligne 307. Corrigé.
- `Ticket` n'a pas `getPurchaseDate()` mais `getCreatedAt()` (`@CreationTimestamp`). Utilisé ce dernier dans la facture ticket.

## Front

- `ApiService` : ajout de `getTicketInvoice`, `getSubscriptionInvoice`, `getOrderInvoice` (3 méthodes, `responseType: 'blob'`)
- `mes-achats.component.ts` : 3 méthodes `downloadXxxInvoice()` + 3 flags `downloading*` (un par onglet pour spinner)
- `mes-achats.component.html` : 3 boutons « 🧾 Facture » (1 par onglet : Billets / Abonnement / Commandes)

## Build status
- 3 services back : `mvn compile` OK
- Front : `npm run build` OK (bundle `main-B3E2DMFS.js`)

## Why
L'utilisateur a demandé « attaque chaque chose et vérifie cohérence front/back ok, NE PAS DEPLOYER ». Toutes les modifs sont compilées en local mais NON commitées/déployées. À committer+déployer en V2.2/V3.2 (V3.2 = bouton facture dans /mes-achats, déjà fait en même temps).

## How to apply
Quand l'utilisateur dira « deploy », faire : `git add` des 9 fichiers modifiés + `shop-service/.../OrderPdfService.java`, commit unique `feat(invoice): V2.2 factures PDF + V3.2 boutons facture /mes-achats`, puis `mvn package` × 3 services + `docker compose build && up -d --no-deps` sur la VM.
