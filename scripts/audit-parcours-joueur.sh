#!/bin/bash
# Audit parcours JOUEUR (B.27 E2E complet) — appelé sur la VM
# Tous les endpoints utilisés par le frontend dans l'espace joueur.

source /tmp/audit-tokens.sh
JID=110
EMAIL=$EMAIL_J
TOK=$JTOK
BASE=http://localhost:8080
PASS=0
FAIL=0
declare -a BUGS

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
echo "AUDIT PARCOURS JOUEUR ($EMAIL, userId=$JID)"
echo "============================================="

echo "--- ESPACE JOUEUR (sports/my-space) ---"
call "Mes convocations"             GET  /api/sports/my-space/convocations "" 200
call "Ma présence"                  GET  /api/sports/my-space/presence "" 200
call "Mes documents"                GET  /api/sports/my-space/documents "" 200
call "Mes stats"                    GET  /api/sports/my-space/stats "" 200

echo "--- MA FICHE JOUEUR ---"
call "Ma fiche (par userId)"        GET  /api/sports/players/user/$JID "" 200
call "Liste FOOTBALL SENIOR"        GET  "/api/sports/players/filter?sportType=FOOTBALL&category=SENIOR" "" 200
call "Staff encadrant"              GET  "/api/sports/staff/filter?sportType=FOOTBALL&category=SENIOR" "" 200
call "Mon staff (par userId)"       GET  /api/sports/staff/user/$JID "" 200
call "Séances de l'équipe"          GET  "/api/sports/sessions/filter?sportType=FOOTBALL&category=SENIOR" "" 200

echo "--- MATCHS / CONVOCATIONS MATCH ---"
call "Matchs du club"               GET  /api/content/matches "" 200
call "Matchs mes matchs (joueur)"   GET  /api/content/matches/mine "" 200
call "Matchs statut PROGRAMME"      GET  /api/content/matches/statut/PROGRAMME "" 200
call "Classement Botola"            GET  /api/content/classements/Botola "" 200
call "Tous classements"             GET  /api/content/classements "" 200

echo "--- MESSAGERIE JOUEUR↔STAFF/JOUEUR (B.5) ---"
call "Inbox messages"               GET  /api/sports/messaging/inbox "" 200
call "Conversations (avec staff 102)" GET /api/sports/messaging/conversation/102 "" 200
call "Conversations (avec joueur 110)" GET /api/sports/messaging/conversation/110 "" 200
call "Annonces visibles"            GET  /api/sports/messaging/announcements "" 200

echo "--- CHAT DE GROUPE (équipe) ---"
call "Membres équipe"               GET  "/api/sports/team-chat/FOOTBALL/SENIOR/members" "" 200
call "Messages groupe"              GET  "/api/sports/team-chat/FOOTBALL/SENIOR/messages" "" 200

echo "--- CONVOCATIONS MATCH (B.3.a / B.9) ---"
call "Mes convocations match"       GET  /api/sports/match-convocations/my "" 200
call "Convocations publiques match 5" GET /api/sports/match-convocations/public/match/5 "" 200
call "Match convoc selectable match 5" GET /api/sports/match-convocations/match/5/selectable "" 200

echo "--- E-CASH (B.10) ---"
call "Mon solde"                    GET  /api/payment/balance "" 200
call "Mes transactions"             GET  /api/payment/transactions "" 200
call "Historique reçus"             GET  /api/auth/salary-receipts/mine "" 200

echo "--- BILLETTERIE (B.4 / B.27) ---"
call "Liste events"                 GET  /api/ticket/events "" 200
call "Event à venir"                GET  /api/ticket/events/upcoming "" 200
call "Event 2 détails"              GET  /api/ticket/events/2 "" 200
call "Mes tickets"                  GET  /api/ticket/tickets/user/$JID "" 200

echo "--- BOUTIQUE (B.7) ---"
call "Mon panier"                   GET  /api/shop/cart "" 200
call "Catalogue produits"           GET  /api/shop/products "" 200
call "Mes commandes"                GET  /api/shop/orders "" 200

echo "--- GAMIFICATION (B.8) ---"
call "Mes points"                   GET  /api/gamification/points/$JID "" 200
call "Mes badges"                   GET  /api/gamification/badges/user/$JID "" 200
call "Catalogue badges"             GET  /api/gamification/badges "" 200
call "Leaderboard"                  GET  /api/gamification/leaderboard "" 200
call "Mes prédictions"              GET  /api/gamification/predictions/user/$JID "" 200

echo "--- NOTIFICATIONS (B.11) ---"
call "Mes notifications"            GET  /api/notification/user/$JID "" 200
call "Compteur non-lus"             GET  /api/notification/user/$JID/unread/count "" 200
call "Mes préférences"              GET  /api/notification/preferences "" 200

echo "--- ÉLECTIONS / SONDAGES (B.8) ---"
call "Élections publiées"           GET  /api/elections/published "" 200
call "Dernière élection"            GET  /api/elections/published/latest "" 200
call "Sondages actifs"              GET  /api/polls/active "" 200

echo "--- CONTENU (B.1, B.5) ---"
call "Articles"                     GET  /api/content/articles "" 200
call "Trophées public"              GET  /api/content/trophies/public "" 200
call "Légendes public"              GET  /api/content/legends/public "" 200
call "Sponsors public"              GET  /api/content/sponsors/public "" 200
call "Settings"                     GET  /api/content/settings "" 200
call "Médias (galerie)"             GET  /api/content/media "" 200
call "Rapports financiers"          GET  /api/content/rapports-financiers "" 200

echo "--- PROFIL AUTH (B.12) ---"
call "Mes appels LiveKit"           GET  /api/sports/calls/mine "" 200
call "Statut média"                 GET  /api/sports/calls/media-status "" 200

echo ""
echo "============================================="
echo "RÉSUMÉ JOUEUR: $PASS OK, $FAIL FAIL"
echo "============================================="
if [ $FAIL -gt 0 ]; then
  echo "Bugs trouvés:"
  for b in "${BUGS[@]}"; do echo "  $b"; done
fi
