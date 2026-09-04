# 📁 Dossier `ilyas/` — PFA Wydad Digital (livrable complet)

## Contenu

```
ilyas/
├── README.md                  (ce fichier)
├── LISTE-SCREENSHOTS.md       (82 captures à faire, instructions)
├── rapport-pfa-wydad-digital.tex  (2446 lignes, code LaTeX complet)
└── images/                    (126 PNG — 44 vrais diagrammes + 82 cadres screenshots)
```

## Comment utiliser ce dossier

### 1. Compiler le rapport (avec les cadres actuels)

```bash
cd ilyas
pdflatex -interaction=nonstopmode rapport-pfa-wydad-digital.tex
pdflatex -interaction=nonstopmode rapport-pfa-wydad-digital.tex
```

Le PDF est généré. Les interfaces UI afficheront un cadre "Screenshot à fournir".

### 2. Remplacer les 82 cadres par de vrais screenshots

Suivre la checklist dans `LISTE-SCREENSHOTS.md` :

- 14 captures pour l'espace public
- 9 captures pour l'espace supporter
- 4 captures pour l'espace parent
- 8 captures pour les espaces joueur/staff/entraineur
- 3 captures pour la messagerie
- 4 captures pour l'espace journaliste
- 6 captures pour l'espace président
- 5 captures pour les élections
- 5 captures pour la boutique
- 4 captures pour l'espace fan
- 29 captures pour le back-office admin
- 1 logo WAC

**Total : 82 captures** à faire en naviguant sur l'app en production.

### 3. Recompiler après remplacement

```bash
cd ilyas
pdflatex -interaction=nonstopmode rapport-pfa-wydad-digital.tex
pdflatex -interaction=nonstopmode rapport-pfa-wydad-digital.tex
```

Toutes les figures sont maintenant de vrais screenshots.

## Structure du rapport (2446 lignes)

1. **Page de garde** (EMSI, Ilyas Ait Maina, Dr. Saadia Drissi, jury)
2. **Dédicaces** + **Remerciements**
3. **Table des matières** + **Liste des figures** + **Liste des tableaux**
4. **Introduction générale** (contexte, problématique, objectifs, méthode)
5. **Chapitre 1** : Présentation du cadre du projet (WAC, existant, solution)
6. **Chapitre 2** : Spécification des besoins (acteurs, UC, cas principaux)
7. **Chapitre 3** : Conception du système (architecture, diagrammes, MLD)
8. **Chapitre 4** : Réalisation du système (back, front, Docker, CI/CD, Azure)
9. **Conclusion générale** + **Bibliographie/Nétographie** + **Annexes**

## Statistiques

- **135 insertions d'images** dans le `.tex`
- **126 images uniques** (9 répétées)
- **44 vrais diagrammes** (UML) + **82 cadres "Screenshot à fournir"**
- **48 begin / 48 end** équilibrés
- **1539 / 1539 accolades** équilibrées
- **19 chapters**, **5 sections principales**

## Comptes de test (mot de passe = `tamazirt`)

| Rôle | Email | Accès |
|---|---|---|
| ADMIN | `admin@wac.ma` | Tout |
| JOUEUR | `hakim@wac.ma` | Espace joueur |
| ENTRAINEUR | `lopez@wac.ma` | Espace entraîneur |
| STAFF | `staf@gmail.com` ⚠️ | Espace staff |
| PRESIDENT | `brahim@wac.ma` | Espace président (FOOTBALL) |
| JOURNALISTE | `khalid@wac.ma` | Espace journaliste |
| PARENT | `parent@wac.ma` | Espace parent |
| ADHERENT | `ilyas@wac.ma` | Espace supporter + fan |

⚠️ Piège : `staf@gmail.com` (PAS `staf@wac.ma`).

## Notes

- Le `.tex` est compilable en l'état (toutes les images sont là, même les cadres).
- Pour la soutenance, le PDF actuel est tout à fait présentable.
- Les 82 captures amélioreront le rendu visuel mais ne sont pas bloquantes.
