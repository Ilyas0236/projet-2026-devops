---
name: rapport-pfa-2026-08-27
description: Rapport LaTeX complet du PFA EMSI (Wydad Digital) + codes PlantUML séparés dans diagrammes-puml/*.puml.md
metadata:
  type: project
---

Rapport LaTeX complet pour le PFA EMSI 2025-2026 d'Ilyas Ait Maina, sur la plateforme Wydad Digital (11 microservices Spring Boot + Angular 19 + Docker + Azure).

**Pourquoi** : livraison demandée par l'étudiant, structure conforme au template EMSI (page de garde → dédicaces → remerciements → TOC → 4 chapitres → conclusion → biblio → annexes), marges 2,5 cm, Times 12pt, interligne 1,5, numérotation romaine pour le liminaire puis arabe dès l'introduction.

**Comment l'utiliser** : tout est dans `rapport-pfa/`. Compiler avec `pdflatex main.tex` (2 passes) ou importer le dossier dans Overleaf. Les diagrammes UML sont dans `diagrammes-puml/*.puml.md` (format Markdown + bloc ```plantuml) — copier/coller sur https://www.plantuml.com/plantuml/uml/ pour générer les PNG, à ranger dans `images/`. Les captures d'écran (29 attendues) sont référencées par nom de fichier listé dans le tableau 4.1 de `chapitres/chapitre4.tex`.

**Pièges** :
- Page de garde : cadre vide pour le logo EMSI (à remplacer par `\includegraphics{images/logo_emsi.png}`) et encadrés à compléter (tuteur pédagogique, jury, dates)
- Aucun fichier `logo_emsi.png` fourni — l'étudiant doit l'ajouter
- Aucune capture d'écran fournie — toutes les `\includegraphics{...}` du chapitre 4 attendent des images à insérer
- Les codes PlantUML sont volontairement séparés du `.tex` (à l'étudiant de générer les PNG)
- Encadrés "Tuteur pédagogique" / "Organisme d'accueil" / "Jury" sont des placeholders à remplir

**Liens** : [[wydad-official-social-links]], [[deployement-azure]], [[phase0-roles-statuts]], [[phase3-convocations-medias]], [[phase4-messagerie]], [[phase5-appels-livekit]], [[elections-president-b8]]
