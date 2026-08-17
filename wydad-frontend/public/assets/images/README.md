# 📁 Images Statiques — Wydad Digital

Ce dossier contient toutes les images statiques utilisées par le frontend.

## Structure

```
public/assets/images/
├── logos/          → Logos du club
├── backgrounds/    → Images de fond (hero, login, etc.)
├── players/        → Photos par défaut des joueurs
├── icons/          → Icônes SVG/PNG personnalisées
└── sponsors/       → Logos des sponsors/partenaires
```

## Convention de nommage

- Tout en **minuscules**
- Mots séparés par des **tirets** (`-`)
- Format : `categorie-description.extension`

## Utilisation dans le code

```html
<img src="assets/images/logos/wydad-logo.png" alt="Logo WAC">
```

> ⚠️ Ne pas mettre `public/` dans le chemin — Angular le résout automatiquement.
