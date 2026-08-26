#!/bin/bash
# Audit parcours ADHERENT (B.27 E2E complet) — appelé sur la VM
# Profil de base : inscription sans demandeRole → VALIDE direct.
# Capacités : profil, vote, panier, commandes, e-cash, contenu public.

source /tmp/audit-tokens.sh
BASE=http://localhost:8080
PASS=0
FAIL=0
declare -a BUGS
AID=$AID_ADHERENT
EMAIL=$EMAIL_ADHERENT
TOK=$TOK_ADHERENT

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
echo "AUDIT PARCOURS ADHERENT ($EMAIL, userId=$AID)"
echo "============================================="

echo "--- PROFIL ---"
call "Mon profil"                   GET  /api/auth/me "" 200
call "Mon attestation"              GET  "/api/auth/attestation?email=$EMAIL" "" 200
call "Carte membre"                 GET  "/api/auth/member-card?email=$EMAIL" "" 200
call "Modifier mon profil"          PUT  /api/auth/me "{\"email\":\"$EMAIL\",\"firstName\":\"Adherent2\",\"lastName\":\"Test\"}" 200
call "KYC upload"                   POST /api/auth/kyc/upload "{\"email\":\"$EMAIL\",\"documentType\":\"CIN\",\"documentNumber\":\"AB12345\",\"filePath\":\"/tmp/test.pdf\"}" 200

echo "--- ADMIN BACK-OFFICE (ADHERENT n'a PAS accès) ---"
call "Liste users (admin)"          GET  /api/auth/admin/users "" 403
call "Comptes en attente (admin)"   GET  /api/auth/admin/accounts/pending "" 403
call "Reçus salaire (admin)"        GET  /api/auth/salary-receipts "" 403

echo "--- GOUVERNANCE : VOTE ---"
call "Élections publiées"           GET  /api/elections/published "" 200
call "Dernière élection"            GET  /api/elections/published/latest "" 204
call "Sondages actifs"              GET  /api/polls/active "" 200

echo "--- SPORTS (lecture simple) ---"
call "Mes convocations (JOUEUR only)" GET /api/sports/match-convocations/my "" 403
call "Mon profil joueur (ADHERENT pas joueur)" GET /api/sports/players/user/$AID "" 403
call "Mon staff (ADHERENT pas staff)"  GET /api/sports/staff/user/$AID "" 403

echo "--- MESSAGERIE (ADHERENT peut chatter) ---"
call "Inbox"                        GET  /api/sports/messaging/inbox "" 200
call "Annonces"                     GET  /api/sports/messaging/announcements "" 200

echo "--- BILLETTERIE / BOUTIQUE ---"
call "Catalogue events"             GET  /api/ticket/events "" 200
call "Event à venir"                GET  /api/ticket/events/upcoming "" 200
call "Mes tickets"                  GET  /api/ticket/tickets/user/$AID "" 200
call "Catalogue produits"           GET  /api/shop/products "" 200
call "Mon panier"                   GET  /api/shop/cart "" 200
call "Mes commandes"                GET  /api/shop/orders "" 200
call "Mon solde e-cash"             GET  /api/payment/balance "" 200
call "Mes transactions"             GET  /api/payment/transactions "" 200

echo "--- GAMIFICATION (lecture) ---"
call "Catalogue badges"             GET  /api/gamification/badges "" 200
call "Mes badges"                   GET  /api/gamification/badges/user/$AID "" 200
call "Mes points"                   GET  /api/gamification/points/$AID "" 200
call "Leaderboard"                  GET  /api/gamification/leaderboard "" 200

echo "--- NOTIFICATIONS ---"
call "Mes notifs"                   GET  /api/notification/user/$AID "" 200
call "Compteur non-lus"             GET  /api/notification/user/$AID/unread/count "" 200
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

echo "--- REÇUS SALAIRE (mine ADHERENT inclus) ---"
call "Mes bulletins de paie"        GET  /api/auth/salary-receipts/mine "" 200

echo ""
echo "============================================="
echo "RÉSUMÉ ADHERENT: $PASS OK, $FAIL FAIL"
echo "============================================="
if [ $FAIL -gt 0 ]; then
  echo "Bugs trouvés:"
  for b in "${BUGS[@]}"; do echo "  $b"; done
fi
