# Mémoire — Wydad Digital (sous-dossier memory/)

- [V2.2 Factures PDF](v22-factures-pdf.md) — 3 services back + front 28/08 ; non déployé ; piège : `buildSubscriptionPdfBytes` sans `document.close()`, `Ticket.getCreatedAt()` pas `getPurchaseDate()`
- [V2.3+V3.1 Pièces jointes + sections](v23-v31-messaging-attachments-sections.md) — 28/08, communication-service (Cloudinary) + ticket-service (CRUD) + front (admin-billetterie + dashboard-joueur) ; compilé mais non déployé
- [Admin UI grille tarifaire sections 27/08](admin-ui-section-pricing-2026-08-27.md) — commit 15dae9a, PATCH /sections/{id} in-place ; VIP=300, TRIBUNE=100, ULTRA=80, VIRAGE=50
- [B.12 photo carte + privileges admin 30/08](abonnement-admin-photo-2026-08-30.md) — déployé E2E 9/9, commits ef82d9a/ba901a2/ac3f606 ; piège gateway `/api/admin/**` non routé, `mvn clean package` obligatoire (pas juste `package`)
- [PUT/DELETE admin CORS 30/08](admin-put-delete-cors-2026-08-30.md) — fix Éditer/Suppr qui plantaient en silence (commit d715b25) ; piège `CORS_ALLOWED_ORIGINS` .env pas relu sans `--force-recreate`, origine doit matcher le site réel (https://VM, pas http://VM:4200)
- [Rapport PFA 27/08](rapport-pfa-2026-08-27.md) — \documentclass{report} FR, chapitres 1-9, figures TikZ, livré dans `rapport-pfa/`
