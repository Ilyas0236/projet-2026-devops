#!/bin/bash
# Fix section prices to 0 — applique la grille "prix à l'unité" par catégorie.
#
# Pourquoi ce script existe :
# Lors de la création d'events de test (avant que la validation @Positive
# ne soit ajoutée), des sections ont été insérées en BDD avec price=0.
# Conséquence : un visiteur non-adhérent qui achète un ticket à l'unité
# sur ces events paie 0 DH, ce qui n'est pas un comportement normal
# (la carte d'abonnement et le ticket à l'unité sont deux produits
# indépendants ; l'abonnement couvre 15 matchs de la saison, le ticket
# à l'unité se paye match par match).
#
# Grille appliquée (prix par match, cohérent avec abonnement +33% environ) :
#   VIP                   = 300 MAD
#   TRIBUNE_OFFICIELLE    = 100 MAD
#   TRIBUNE_HONNEUR       = 100 MAD
#   ULTRA                 =  80 MAD
#   VIRAGE_NORD / VIRAGE_SUD = 50 MAD
#   autres                = event.basePrice
#
# Usage : bash scripts/fix-section-prices.sh
set -e

# Exécutable depuis la VM : docker exec sur le conteneur postgres
docker exec wydad-postgres psql -U wydad -d ticket_db -c "
UPDATE sections s
SET price = CASE s.category
  WHEN 'VIP' THEN 300
  WHEN 'TRIBUNE_OFFICIELLE' THEN 100
  WHEN 'TRIBUNE_HONNEUR' THEN 100
  WHEN 'ULTRA' THEN 80
  WHEN 'VIRAGE_NORD' THEN 50
  WHEN 'VIRAGE_SUD' THEN 50
  ELSE (SELECT base_price FROM events WHERE id = s.event_id)
END
WHERE s.price = 0 OR s.price IS NULL;
" | grep -E "UPDATE|^$"

echo "Vérification :"
docker exec wydad-postgres psql -U wydad -d ticket_db -c "
SELECT e.id, e.title, s.id AS section_id, s.name, s.category, s.price
FROM events e JOIN sections s ON s.event_id = e.id
ORDER BY e.id, s.id;
"
