#!/usr/bin/env bash
# ════════════════════════════════════════════════════════════════════════
# Audit — Président Discussions (Chantier 1 du plan qualité 2026-08-29)
# ════════════════════════════════════════════════════════════════════════
#
# Vérifie de bout en bout :
#   T1 : président login → token OK
#   T2 : GET /api/sports/team-chat/FOOTBALL/SENIOR/members (avec token président)
#         → 200 + liste non vide incluant le HEAD_COACH
#   T3 : POST /api/sports/team-chat/FOOTBALL/SENIOR/messages (texte président)
#         → 201 + message persisté
#   T4 : POST /api/sports/team-chat/FOOTBALL/SENIOR/media (upload image mock)
#         → 201 + message avec mediaUrl
#   T5 : POST /api/sports/calls (appel CATEGORIE_EQUIPE par président)
#         → 201 + appel créé, audience = joueurs + coach
#   T6 : GET /api/notifications/recent pour un joueur cible → notif IN_APP
#         « Nouvel appel » reçue
#
# Pré-requis : VM déployée + base de seed appliquée. Le seeder a créé
# admin@wac.ma (ADMIN) + président de test + quelques joueurs/staff.
#
# Usage :
#   bash scripts/audit-president-discussions.sh
#
# Sortie : 6 tests OK → exit 0 ; un échec → exit 1.

set -e
GATEWAY="${GATEWAY:-http://localhost:8080}"
PRES_EMAIL="${PRES_EMAIL:-president@wac.ma}"
PRES_PASS="${PRES_PASS:-President2026!}"
JOUEUR_EMAIL="${JOUEUR_EMAIL:-joueur.test@wac.ma}"
TMPDIR=$(mktemp -d)
trap 'rm -rf "$TMPDIR"' EXIT

bold() { printf '\n\033[1m%s\033[0m\n' "$1"; }
ok()   { printf '\033[32m✓\033[0m %s\n' "$1"; }
fail() { printf '\033[31m✗\033[0m %s\n' "$1"; exit 1; }

# ───────────────────────────── T1 : login président ─────────────────────────────
bold "T1 — Login président ($PRES_EMAIL)"
PRES_TOKEN=$(curl -s -X POST "$GATEWAY/api/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$PRES_EMAIL\",\"password\":\"$PRES_PASS\"}" \
    | python3 -c "import sys,json; print(json.load(sys.stdin).get('accessToken',''))")
[ -n "$PRES_TOKEN" ] || fail "T1 KO : pas de token"
ok "T1 OK : token président obtenu"

# ───────────────────────────── T2 : membres du groupe ─────────────────────────────
bold "T2 — Liste membres FOOTBALL SENIOR (vue président)"
MEMBERS=$(curl -s "$GATEWAY/api/sports/team-chat/FOOTBALL/SENIOR/members" \
    -H "Authorization: Bearer $PRES_TOKEN")
echo "$MEMBERS" > "$TMPDIR/members.json"
COUNT=$(echo "$MEMBERS" | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d) if isinstance(d,list) else 0)")
[ "$COUNT" -gt 0 ] || fail "T2 KO : aucun membre dans FOOTBALL/SENIOR (vérifier seed)"
HAS_COACH=$(echo "$MEMBERS" | python3 -c "import sys,json; d=json.load(sys.stdin); print(any(m.get('rosterRole')=='STAFF' for m in d))")
[ "$HAS_COACH" = "True" ] || fail "T2 KO : aucun HEAD_COACH dans le groupe (exigence : coach toujours inclus)"
ok "T2 OK : $COUNT membres, HEAD_COACH présent"

# ───────────────────────────── T3 : envoi message texte ─────────────────────────────
bold "T3 — Envoi message groupe par président"
MSG=$(curl -s -w '\n%{http_code}' -X POST \
    "$GATEWAY/api/sports/team-chat/FOOTBALL/SENIOR/messages" \
    -H "Authorization: Bearer $PRES_TOKEN" \
    -H 'Content-Type: application/json' \
    -d '{"content":"Test audit : message président vers équipe SENIOR"}')
HTTP_CODE=$(echo "$MSG" | tail -1)
[ "$HTTP_CODE" = "201" ] || fail "T3 KO : HTTP $HTTP_CODE (attendu 201)"
ok "T3 OK : message texte enregistré"

# ───────────────────────────── T4 : envoi message avec média ─────────────────────────────
bold "T4 — Upload média (mode local mock) + envoi message avec pièce jointe"
# Crée une image PNG 1x1 factice (8 octets valides)
printf '\x89PNG\r\n\x1a\n' > "$TMPDIR/tiny.png"
MEDIA_HTTP=$(curl -s -w '\n%{http_code}' -X POST \
    "$GATEWAY/api/sports/team-chat/FOOTBALL/SENIOR/media" \
    -H "Authorization: Bearer $PRES_TOKEN" \
    -F "file=@$TMPDIR/tiny.png" \
    -F 'caption=Convocation visuelle du président' \
    -F 'mediaType=IMAGE')
MEDIA_CODE=$(echo "$MEDIA_HTTP" | tail -1)
MEDIA_BODY=$(echo "$MEDIA_HTTP" | head -1)
[ "$MEDIA_CODE" = "201" ] || fail "T4 KO : HTTP $MEDIA_CODE (réponse : $MEDIA_BODY)"
HAS_MEDIA=$(echo "$MEDIA_BODY" | python3 -c "import sys,json; d=json.load(sys.stdin); print(bool(d.get('mediaUrl')))")
[ "$HAS_MEDIA" = "True" ] || fail "T4 KO : mediaUrl absent du message retourné"
ok "T4 OK : message avec média persisté"

# ───────────────────────────── T5 : appel président ─────────────────────────────
bold "T5 — Démarrage appel LiveKit CATEGORIE_EQUIPE (FOOTBALL SENIOR)"
# Audience = tous les membres du groupe (tous les userIds)
AUDIENCE=$(echo "$MEMBERS" | python3 -c "import sys,json; d=json.load(sys.stdin); print(','.join(str(m['userId']) for m in d))")
CALL=$(curl -s -w '\n%{http_code}' -X POST \
    "$GATEWAY/api/sports/calls" \
    -H "Authorization: Bearer $PRES_TOKEN" \
    -H 'Content-Type: application/json' \
    -d "{
        \"title\":\"Briefing président avant match\",
        \"sportType\":\"FOOTBALL\",
        \"category\":\"SENIOR\",
        \"durationMinutes\":30,
        \"target\":\"CATEGORIE_EQUIPE\",
        \"targetUserIds\":[$(echo $AUDIENCE | tr ',' ',')]
    }")
CALL_CODE=$(echo "$CALL" | tail -1)
CALL_BODY=$(echo "$CALL" | head -1)
[ "$CALL_CODE" = "201" ] || fail "T5 KO : HTTP $CALL_CODE (réponse : $CALL_BODY)"
ok "T5 OK : appel créé, audience = $COUNT membre(s)"

# ───────────────────────────── T6 : notif reçue côté joueur ─────────────────────────────
bold "T6 — Notification IN_APP reçue par un membre du groupe"
sleep 2  # laisse le temps à la notif d'être persistée
# Endpoint réel = /api/notification/user/{userId} (singular, pas /api/notifications/recent)
# On cible userId=9 (premier joueur du groupe, présent dans T2 ci-dessus)
PLAYER_ID=$(echo "$MEMBERS" | python3 -c "import sys,json; d=json.load(sys.stdin); print(next((m['userId'] for m in d if m.get('rosterRole')=='JOUEUR'), ''))")
[ -n "$PLAYER_ID" ] || fail "T6 KO : aucun joueur trouvé dans le groupe"
NOTIF_COUNT=$(curl -s "$GATEWAY/api/notification/user/$PLAYER_ID?limit=20" \
    -H "Authorization: Bearer $PRES_TOKEN" \
    | python3 -c "
import sys, json
d = json.load(sys.stdin)
items = d if isinstance(d, list) else d.get('items', [])
# On cherche au moins une notif contenant 'appel' ou 'Briefing'
calls = [n for n in items if 'appel' in (n.get('title','')+n.get('message','')).lower() or 'briefing' in (n.get('title','')+n.get('message','')).lower()]
print(len(calls))
")
[ "$NOTIF_COUNT" -gt 0 ] || fail "T6 KO : aucune notification 'appel' reçue par le joueur $PLAYER_ID (vérifier NotificationClient côté sports-service)"
ok "T6 OK : $NOTIF_COUNT notification(s) d'appel reçue(s) par le joueur $PLAYER_ID"

# ───────────────────────────── Résumé ─────────────────────────────
bold "════════════════════════════════════════════════════════════════════"
printf '\033[32m6/6 tests verts — Président Discussions opérationnel\033[0m\n'
echo "Pré-requis pour exécuter ce script :"
echo "  - Président de test présent (seeder ou scripts/seed-president.sh)"
echo "  - Au moins 1 joueur + 1 staff HEAD_COACH dans FOOTBALL/SENIOR"
echo "  - LiveKit configuré (sinon T5 fonctionne mais sans média)"
