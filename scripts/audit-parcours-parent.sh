#!/bin/bash
# Audit parcours PARENT (B.27 E2E complet) — appelé sur la VM
# Endpoints spécifiques : académie (enfants), paiement, billetterie famille, gouvernance.

source /tmp/audit-tokens-extended.sh
BASE=http://localhost:8080
PASS=0
FAIL=0
declare -a BUGS
PID=$PARENT_ID
EMAIL=$EMAIL_PARENT
TOK=$PTOK_PARENT

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
echo "AUDIT PARCOURS PARENT ($EMAIL, userId=$PID)"
echo "============================================="

echo "--- PROFIL ---"
call "Mon profil"                   GET  /api/auth/me "" 200
call "Modifier mon profil"          PUT  /api/auth/me "{\"email\":\"$EMAIL\",\"firstName\":\"Parent2\",\"lastName\":\"Test\"}" 200
call "KYC upload"                   POST /api/auth/kyc/upload "{\"email\":\"$EMAIL\",\"documentType\":\"CIN\",\"documentNumber\":\"AA12345\",\"filePath\":\"/tmp/test.pdf\"}" 200
call "Mes bulletins de paie (parent? pas staff)" GET /api/auth/salary-receipts/mine "" 200   # endpoint ADHERENT-like, parent inclus
call "Mon attestation"              GET  "/api/auth/attestation?email=$EMAIL" "" 200

echo "--- ACADÉMIE (enfants) — endpoint signature B.6 ---"
call "Inscrire un enfant"           POST /api/sports/academy/register "{\"parentUserId\":$PID,\"childFullName\":\"Enfant Test\",\"childBirthDate\":\"2015-05-15\",\"sportType\":\"FOOTBALL\"}" 201
call "Mes enfants"                  GET  /api/sports/academy/parent/$PID "" 200
call "Enfants d'un autre parent"    GET  /api/sports/academy/parent/999 "" 403   # anti-IDOR
call "Tous dossiers (admin/staff)"  GET  /api/sports/academy/all "" 403   # parent n'est pas STAFF/ADMIN
call "Documents enfant 1"           GET  /api/sports/academy/1/documents "" 200
call "Update statut enfant (admin/staff)" PATCH /api/sports/academy/1/status "" 403   # parent n'a pas le droit

echo "--- SPORTS (lecture parent) ---"
call "Liste joueurs FOOTBALL SENIOR" GET  "/api/sports/players/filter?sportType=FOOTBALL&category=SENIOR" "" 403   # parent pas d'équipe
call "Mon profil joueur (parent pas joueur)" GET /api/sports/players/user/$PID "" 403   # IDOR check avant 404
call "Mon staff (parent pas staff)"  GET  /api/sports/staff/user/$PID "" 403
call "Mes convocations (parent pas joueur)" GET /api/sports/my-space/convocations "" 403
call "Séances équipe"               GET  "/api/sports/sessions/filter?sportType=FOOTBALL&category=SENIOR" "" 403   # parent n'a pas de fiche équipe

echo "--- MESSAGERIE (parent peut chatter) ---"
call "Inbox"                        GET  /api/sports/messaging/inbox "" 200
call "Annonces"                     GET  /api/sports/messaging/announcements "" 200

echo "--- CHAT DE GROUPE (parent pas dans une équipe) ---"
call "Membres équipe"               GET  "/api/sports/team-chat/FOOTBALL/SENIOR/members" "" 403   # parent n'a pas de fiche roster
call "Messages groupe"              GET  "/api/sports/team-chat/FOOTBALL/SENIOR/messages" "" 403

echo "--- BILLETTERIE / BOUTIQUE (lecture + achat) ---"
call "Catalogue events"             GET  /api/ticket/events "" 200
call "Event à venir"                GET  /api/ticket/events/upcoming "" 200
call "Mes tickets"                  GET  /api/ticket/tickets/user/$PID "" 200   # tous rôles authentifiés
call "Catalogue produits"           GET  /api/shop/products "" 200
call "Mon panier"                   GET  /api/shop/cart "" 200
call "Mes commandes"                GET  /api/shop/orders "" 200
call "Mon solde e-cash"             GET  /api/payment/balance "" 200
call "Mes transactions"             GET  /api/payment/transactions "" 200

echo "--- ÉLECTIONS / SONDAGES (parent vote comme tout citoyen) ---"
call "Élections publiées"           GET  /api/elections/published "" 200
call "Dernière élection"            GET  /api/elections/published/latest "" 204
call "Sondages actifs"              GET  /api/polls/active "" 200

echo "--- GAMIFICATION (lecture) ---"
call "Catalogue badges"             GET  /api/gamification/badges "" 200
call "Mes badges"                   GET  /api/gamification/badges/user/$PID "" 200
call "Mes points"                   GET  /api/gamification/points/$PID "" 200
call "Leaderboard"                  GET  /api/gamification/leaderboard "" 200

echo "--- NOTIFICATIONS ---"
call "Mes notifs"                   GET  /api/notification/user/$PID "" 200
call "Compteur non-lus"             GET  /api/notification/user/$PID/unread/count "" 200
call "Mes préférences"              GET  /api/notification/preferences "" 200

echo "--- CONTENU (lecture publique) ---"
call "Articles"                     GET  /api/content/articles "" 200
call "Matchs"                       GET  /api/content/matches "" 200
call "Trophées public"              GET  /api/content/trophies/public "" 200
call "Légendes public"              GET  /api/content/legends/public "" 200
call "Sponsors public"              GET  /api/content/sponsors/public "" 200
call "Médias (galerie admin)"       GET  /api/content/media "" 403
call "Settings"                     GET  /api/content/settings "" 200
call "Rapports financiers"          GET  /api/content/rapports-financiers "" 200

echo "--- REÇUS SALAIRE (parent inclus dans tous rôles) ---"
call "Mes bulletins de paie"        GET  /api/auth/salary-receipts/mine "" 200

echo ""
echo "============================================="
echo "RÉSUMÉ PARENT: $PASS OK, $FAIL FAIL"
echo "============================================="
if [ $FAIL -gt 0 ]; then
  echo "Bugs trouvés:"
  for b in "${BUGS[@]}"; do echo "  $b"; done
fi
