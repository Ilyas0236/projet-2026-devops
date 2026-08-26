#!/bin/bash
# Audit parcours VISITEUR (B.27 E2E complet) — appelé sur la VM
# Visiteur = anonyme, sans JWT. Teste UNIQUEMENT les endpoints publics.
# Toute requête authentifiée doit retourner 401 (ou 403 si @PreAuthorize vérifié sans JWT).

BASE=http://localhost:8080
PASS=0
FAIL=0
declare -a BUGS

# Pour un visiteur on n'envoie AUCUN header Authorization.
# Les endpoints publics retournent 200 ; les endpoints protégés retournent 401/403.

call() {
  local label="$1" method="$2" url="$3" expect="$4"
  code=$(curl -s -o /tmp/r -w "%{http_code}" -X $method "$BASE$url")
  if [ "$code" = "$expect" ]; then
    echo "  [OK  $code] $label"
    PASS=$((PASS+1))
  else
    echo "  [FAIL $code, attendu $expect] $label"
    BUGS+=("[$code/$expect] $method $url : $(head -c 200 /tmp/r 2>/dev/null)")
    FAIL=$((FAIL+1))
  fi
}

echo "============================================="
echo "AUDIT PARCOURS VISITEUR (anonyme, sans JWT)"
echo "============================================="

echo "--- ENDPOINTS PUBLICS (doivent retourner 200) ---"
call "Élections publiées"           GET  /api/elections/published 200
call "Dernière élection"            GET  /api/elections/published/latest 204
call "Sondages actifs"              GET  /api/polls/active 200
call "Catalogue events"             GET  /api/ticket/events 200
call "Event à venir"                GET  /api/ticket/events/upcoming 200
call "Catalogue produits"           GET  /api/shop/products 200
call "Leaderboard (public)"         GET  /api/gamification/leaderboard 200

echo "--- CONTENU (lecture publique) ---"
call "Articles"                     GET  /api/content/articles 200
call "Matchs"                       GET  /api/content/matches 200
call "Trophées public"              GET  /api/content/trophies/public 200
call "Légendes public"              GET  /api/content/legends/public 200
call "Sponsors public"              GET  /api/content/sponsors/public 200
call "Settings"                     GET  /api/content/settings 200
call "Rapports financiers"          GET  /api/content/rapports-financiers 200

echo "--- ENDPOINTS PROTÉGÉS (doivent retourner 401 sans JWT) ---"
call "Mon profil (interdit)"        GET  /api/auth/me 401
call "Mon panier (interdit)"        GET  /api/shop/cart 401
call "Mes commandes (interdit)"     GET  /api/shop/orders 401
call "Mon solde (interdit)"         GET  /api/payment/balance 401
call "Mes transactions (interdit)"  GET  /api/payment/transactions 401
call "Mes points (interdit)"        GET  /api/gamification/points/0 401
call "Mes badges (interdit)"        GET  /api/gamification/badges/user/0 401
call "Mes notifs (interdit)"        GET  /api/notification/user/0 401
call "Mes préférences (interdit)"   GET  /api/notification/preferences 401
call "Inbox messagerie (interdit)"  GET  /api/sports/messaging/inbox 401
call "Annonces (interdit)"          GET  /api/sports/messaging/announcements 401
call "Membres équipe (interdit)"    GET  /api/sports/team-chat/FOOTBALL/SENIOR/members 401
call "Mes bulletins (interdit)"     GET  /api/auth/salary-receipts/mine 401
call "Tous les joueurs (interdit)"  GET  /api/sports/players 401
call "Tous les staff (interdit)"    GET  /api/sports/staff 401
call "Convocations (interdit)"      GET  /api/sports/match-convocations/my 401
call "Admin users (interdit)"       GET  /api/auth/admin/users 401
# /api/content/media : @PreAuthorize ADMIN → 403 même sans JWT (filter s'exécute quand même)
call "Médias galerie (interdit)"    GET  /api/content/media 403

echo ""
echo "============================================="
echo "RÉSUMÉ VISITEUR: $PASS OK, $FAIL FAIL"
echo "============================================="
if [ $FAIL -gt 0 ]; then
  echo "Bugs trouvés:"
  for b in "${BUGS[@]}"; do echo "  $b"; done
fi
