#!/bin/bash
# Audit parcours PRESIDENT (B.27 E2E complet) — appelé sur la VM
# Vue d'ensemble club : gouvernance, validation comptes, élections, gestion sports.
# Note : le président N'A PAS accès au back-office admin (auth/admin/*).
# Note 2 : le président voit les joueurs et le staff via /players (ADMIN|PRESIDENT) et
# /staff/user/{id} (ADMIN|PRESIDENT|ENTRAINEUR|STAFF), mais PAS les listings
# /players/filter, /staff/filter, /sessions/filter (réservés STAFF/ENTRAINEUR/JOUEUR).

source /tmp/audit-tokens-extended.sh
BASE=http://localhost:8080
PASS=0
FAIL=0
declare -a BUGS
PID=$PRESIDENT_ID
EMAIL=$EMAIL_PRESIDENT
TOK=$PTOK_PRESIDENT

call() {
  local label="$1" method="$2" url="$3" data="$4" expect="$5"
  if [ -n "$data" ]; then
    code=$(curl -s -o /tmp/r -w "%{http_code}" -X $method -H "Authorization: Bearer $TOK" -H "Content-Type: application/json" -d "$data" "$BASE$url")
  else
    code=$(curl -s -o /tmp/r -w "%{http_code}" -X $method -H "Authorization: Bearer $TOK" "$BASE$url")
  fi
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
echo "AUDIT PARCOURS PRESIDENT ($EMAIL, userId=$PID)"
echo "============================================="

echo "--- PROFIL ---"
call "Mon profil"                   GET  /api/auth/me "" 200
call "Mon attestation"              GET  "/api/auth/attestation?email=$EMAIL" "" 200
call "Modifier mon profil"          PUT  /api/auth/me "{\"email\":\"$EMAIL\",\"firstName\":\"President2\",\"lastName\":\"Test\"}" 200

echo "--- ADMIN BACK-OFFICE (président n'a PAS accès, ADMIN only) ---"
call "Liste users (admin)"          GET  /api/auth/admin/users "" 403
call "Comptes en attente (admin)"   GET  /api/auth/admin/accounts/pending "" 403
call "Valider compte (admin)"       PATCH "/api/auth/admin/accounts/110/validate" "" 403

echo "--- GOUVERNANCE : ÉLECTIONS / SONDAGES (président vote) ---"
call "Élections publiées"           GET  /api/elections/published "" 200
call "Dernière élection"            GET  /api/elections/published/latest "" 204
call "Élections ouvertes (avec vote)" GET /api/elections/open "" 200
call "Sondages actifs"              GET  /api/polls/active "" 200

echo "--- SPORTS : VUES PRÉSIDENT (ADMIN|PRESIDENT) ---"
call "Tous les joueurs"             GET  /api/sports/players "" 200
call "Tous les staff (ADMIN only)"  GET  /api/sports/staff "" 403
call "Liste joueurs FOOTBALL SENIOR" GET  "/api/sports/players/filter?sportType=FOOTBALL&category=SENIOR" "" 200
call "Liste staff FOOTBALL SENIOR"   GET  "/api/sports/staff/filter?sportType=FOOTBALL&category=SENIOR" "" 200
call "Séances de l'équipe"           GET  "/api/sports/sessions/filter?sportType=FOOTBALL&category=SENIOR" "" 200
call "Mon profil staff (président pas staff)" GET /api/sports/staff/user/$PID "" 404
call "Mon profil joueur (président pas joueur)" GET /api/sports/players/user/$PID "" 403

echo "--- CONVOCATIONS MATCH (ENTRAINEUR/STAFF/ADMIN) ---"
call "Mes convocations (JOUEUR only)" GET /api/sports/match-convocations/my "" 403
call "Sélect° joueurs pour match 5" GET /api/sports/match-convocations/match/5/selectable "" 403
call "Soumettre feuille match 5"    POST /api/sports/match-convocations/match/5/submit "" 403
call "Liste submitted (ADMIN)"      GET  /api/sports/match-convocations/admin/submitted "" 403

echo "--- MESSAGERIE (président peut chatter) ---"
call "Inbox"                        GET  /api/sports/messaging/inbox "" 200
call "Annonces"                     GET  /api/sports/messaging/announcements "" 200

echo "--- CHAT DE GROUPE (président pas dans une équipe) ---"
call "Membres équipe"               GET  "/api/sports/team-chat/FOOTBALL/SENIOR/members" "" 403
call "Messages groupe"              GET  "/api/sports/team-chat/FOOTBALL/SENIOR/messages" "" 403

echo "--- BILLETTERIE / BOUTIQUE ---"
call "Catalogue events"             GET  /api/ticket/events "" 200
call "Mes tickets"                  GET  /api/ticket/tickets/user/$PID "" 200
call "Catalogue produits"           GET  /api/shop/products "" 200
call "Mon panier"                   GET  /api/shop/cart "" 200
call "Mes commandes"                GET  /api/shop/orders "" 200
call "Mon solde e-cash"             GET  /api/payment/balance "" 200
call "Mes transactions"             GET  /api/payment/transactions "" 200

echo "--- GAMIFICATION (lecture) ---"
call "Catalogue badges"             GET  /api/gamification/badges "" 200
call "Mes badges"                   GET  /api/gamification/badges/user/$PID "" 200
call "Mes points"                   GET  /api/gamification/points/$PID "" 200
call "Leaderboard"                  GET  /api/gamification/leaderboard "" 200

echo "--- NOTIFICATIONS ---"
call "Mes notifs"                   GET  /api/notification/user/$PID "" 200
call "Compteur non-lus"             GET  /api/notification/user/$PID/unread/count "" 200
call "Mes préférences"              GET  /api/notification/preferences "" 200

echo "--- CONTENU (lecture publique, sauf /media back-office) ---"
call "Articles"                     GET  /api/content/articles "" 200
call "Matchs"                       GET  /api/content/matches "" 200
call "Trophées public"              GET  /api/content/trophies/public "" 200
call "Légendes public"              GET  /api/content/legends/public "" 200
call "Sponsors public"              GET  /api/content/sponsors/public "" 200
call "Médias (galerie admin)"       GET  /api/content/media "" 403
call "Settings"                     GET  /api/content/settings "" 200
call "Rapports financiers"          GET  /api/content/rapports-financiers "" 200

echo "--- APPELS PROGRAMMÉS (président inclus) ---"
call "Mes appels programmés"        GET  /api/sports/calls/mine "" 200

echo "--- REÇUS SALAIRE (président inclus : vue admin + ses propres) ---"
call "Mes bulletins de paie"        GET  /api/auth/salary-receipts/mine "" 200
call "Tous les reçus (président)"   GET  /api/auth/salary-receipts "" 200

echo ""
echo "============================================="
echo "RÉSUMÉ PRESIDENT: $PASS OK, $FAIL FAIL"
echo "============================================="
if [ $FAIL -gt 0 ]; then
  echo "Bugs trouvés:"
  for b in "${BUGS[@]}"; do echo "  $b"; done
fi
