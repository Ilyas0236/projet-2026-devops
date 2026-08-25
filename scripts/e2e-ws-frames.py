"""Construit les frames STOMP pour l'E2E chat WS (lance sur la VM)."""
import json

ta = open("/tmp/ja").read().strip()  # token joueur 9
tb = open("/tmp/jb").read().strip()  # token joueur 10


def w(path, frame):
    # SockJS xhr_send attend un tableau JSON de strings ; chaque string est
    # une trame STOMP terminee par NUL.
    open(path, "w").write(json.dumps([frame]))


# En-tete STOMP sensible a la casse : l intercepteur lit "Authorization".
w("/tmp/ca.json", "CONNECT\naccept-version:1.2\nheart-beat:0,0\nAuthorization:" + ta + "\n\n\x00")
w("/tmp/suba.json", "SUBSCRIBE\nid:s0\ndestination:/topic/chat/football/u19\n\n\x00")
w("/tmp/cb.json", "CONNECT\naccept-version:1.2\nheart-beat:0,0\nAuthorization:" + tb + "\n\n\x00")
w("/tmp/sendb.json", "SEND\ndestination:/app/chat/FOOTBALL/U19/send\ncontent-type:application/json\n\n" + json.dumps({"content": "E2E WS final - Allez Wydad !"}) + "\x00")

print("frames ok")
