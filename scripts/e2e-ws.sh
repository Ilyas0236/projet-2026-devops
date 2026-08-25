#!/bin/bash
# E2E Phase 4 : deux sessions SockJS sur la VM, A ecoute /topic/chat/football/u19,
# B envoie /app/chat/FOOTBALL/U19/send. Preuve : frame MESSAGE dans pollA.log
# + message persiste visible via l'API REST.
set -e
GW=http://localhost:8080
BASE=$GW/ws/team-chat

JA=$(curl -s -X POST $GW/api/auth/login -H "Content-Type: application/json" \
  -d '{"email":"joueur.p3a@wac.ma","password":"JoueurP3a!x"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["accessToken"])')
JB=$(curl -s -X POST $GW/api/auth/login -H "Content-Type: application/json" \
  -d '{"email":"joueur.p3b@wac.ma","password":"JoueurP3b!x"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["accessToken"])')
echo "$JA" > /tmp/ja; echo "$JB" > /tmp/jb
echo "tokens ok"

python3 /tmp/e2e-ws-frames.py

rm -f /tmp/pollA.log /tmp/pollB.log
pkill -f sessA/xhr 2>/dev/null || true
pkill -f sessB/xhr 2>/dev/null || true

curl -s -X POST $BASE/000/sessA/xhr > /dev/null
curl -s -X POST $BASE/000/sessB/xhr > /dev/null

nohup bash -c 'while true; do R=$(curl -s --max-time 25 -X POST '"$BASE"'/000/sessA/xhr); [ -n "$R" ] && echo "$R" >> /tmp/pollA.log; done' > /dev/null 2>&1 &
PA=$!
sleep 1
nohup bash -c 'while true; do R=$(curl -s --max-time 25 -X POST '"$BASE"'/000/sessB/xhr); [ -n "$R" ] && echo "$R" >> /tmp/pollB.log; done' > /dev/null 2>&1 &
PB=$!
sleep 1

HDR="Content-Type: application/json"
curl -s -X POST $BASE/000/sessA/xhr_send -H "$HDR" -d @/tmp/ca.json; echo ""
sleep 2
curl -s -X POST $BASE/000/sessA/xhr_send -H "$HDR" -d @/tmp/suba.json; echo ""
curl -s -X POST $BASE/000/sessB/xhr_send -H "$HDR" -d @/tmp/cb.json; echo ""
sleep 2
curl -s -X POST $BASE/000/sessB/xhr_send -H "$HDR" -d @/tmp/sendb.json; echo ""
sleep 5

kill $PA $PB 2>/dev/null || true
echo "=== POLL A (recu par joueur 9) ==="
cat /tmp/pollA.log
echo ""
echo "=== HISTORIQUE REST (dernier message) ==="
curl -s $GW/api/sports/team-chat/FOOTBALL/U19/messages -H "Authorization: Bearer $(cat /tmp/ja)" | python3 -c 'import sys,json;m=json.load(sys.stdin)[-1];print(m["senderName"],":",m["content"])'
