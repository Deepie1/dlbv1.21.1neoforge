#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Panneau de suivi du serveur de test Minecraft (Deep Lucky Block).

POURQUOI : le serveur Minecraft parle le protocole Minecraft, pas HTTP. Le
panneau de previsualisation du navigateur ne peut donc RIEN afficher du jeu.
Ce petit serveur HTTP se place a cote : il lit run/logs/latest.log en direct et
rend l'etat du pipeline de structures (gel/degel de l'eau, phases, watchdog)
dans une page auto-rafraichie.

Il permet aussi de LANCER une structure (meme commande que /dlbtest) via RCON,
avec une liste blanche stricte de commandes.
"""
import http.server
import json
import os
import re
import socket
import struct
import threading
import urllib.parse

ROOT = "/home/user/projet/mod_project"
LOG = os.path.join(ROOT, "run", "logs", "latest.log")
PORT = 8899
RCON_HOST, RCON_PORT, RCON_PW = "127.0.0.1", 25575, "dlbtest"

HIGHLIGHT = [
    ("DLB-WATER", "#38bdf8"),
    ("DLB-LAGPROBE", "#fbbf24"),
    ("DLB-SETTLE", "#a78bfa"),
    ("DLB-CLAMP", "#64748b"),
    ("DLB-STRUCTURE", "#94a3b8"),
    ("STRUCT5", "#4ade80"),
    ("STRUCT4", "#4ade80"),
    ("Watchdog", "#f87171"),
    ("ERROR", "#f87171"),
    ("Can't keep up", "#fb923c"),
]

# Liste blanche : seules ces commandes peuvent etre envoyees au serveur.
ALLOWED = re.compile(
    r"^(execute positioned -?\d+ -?\d+ -?\d+ run dlbtest structure \d+"
    r"|dlbtest (ids|water status|water thaw)"
    r"|list|tps)$"
)


def rcon(cmd, timeout=10):
    with socket.create_connection((RCON_HOST, RCON_PORT), timeout) as s:
        s.settimeout(timeout)

        def send(i, t, body):
            data = struct.pack("<ii", i, t) + body + b"\x00\x00"
            s.sendall(struct.pack("<i", len(data)) + data)

        def recv():
            ln = struct.unpack("<i", s.recv(4))[0]
            return s.recv(ln)

        send(0, 3, RCON_PW.encode())
        recv()
        send(1, 2, cmd.encode())
        r = recv()
        return r[12:].decode("utf8", "replace").strip()


def read_tail(n):
    try:
        with open(LOG, "rb") as f:
            f.seek(0, os.SEEK_END)
            size = f.tell()
            block = min(size, 400_000)
            f.seek(size - block)
            data = f.read(block).decode("utf8", "replace")
        return data.splitlines()[-n:]
    except FileNotFoundError:
        return ["(log introuvable : le serveur n'a pas encore demarre)"]


def state():
    lines = read_tail(4000)
    st = {
        "running": any("Done (" in l or "RCON running" in l for l in lines[-600:]),
        "rcon": False,
    }
    try:
        st["rcon"] = bool(rcon("list"))
    except Exception:
        st["rcon"] = False
    last = {}
    for key, pat in (("water", "DLB-WATER"), ("settle", "DLB-SETTLE"), ("probe", "DLB-LAGPROBE"),
                     ("phase", "DLB-STRUCTURE"), ("struct", "STRUCT5"), ("warn", "Can't keep up")):
        for l in reversed(lines):
            if pat in l:
                last[key] = l.split("] ", 1)[-1][:200]
                break
    st["last"] = last
    st["watchdog"] = any("Watchdog" in l and "shutting down" not in l for l in lines[-800:])
    return st


PAGE = """<!doctype html><meta charset=utf-8><title>Deep Lucky Block - suivi serveur</title>
<style>
 body{background:#0b1220;color:#e2e8f0;font:13px/1.45 ui-monospace,Consolas,monospace;margin:0}
 header{padding:10px 14px;background:#111c33;border-bottom:1px solid #1e293b;display:flex;gap:18px;align-items:center;flex-wrap:wrap}
 h1{font-size:15px;margin:0;font-weight:600}
 .pill{padding:2px 9px;border-radius:999px;font-size:12px}
 .ok{background:#064e3b;color:#6ee7b7}.ko{background:#7f1d1d;color:#fca5a5}
 button{background:#1d4ed8;color:#fff;border:0;border-radius:6px;padding:6px 11px;font:inherit;cursor:pointer}
 button:hover{background:#2563eb}
 pre{margin:0;padding:12px 14px;white-space:pre-wrap;word-break:break-word}
 .box{border-top:1px solid #1e293b}
 #log{height:60vh;overflow:auto}
 .key{color:#93c5fd}
</style>
<header>
 <h1>Deep Lucky Block &mdash; serveur de test</h1>
 <span id=run class="pill ko">serveur&nbsp;?</span>
 <span id=rcon class="pill ko">rcon&nbsp;?</span>
 <button onclick="cmd('execute positioned 400 90 400 run dlbtest structure 17')">Observatoire @400</button>
 <button onclick="cmd('execute positioned 400 90 400 run dlbtest structure 5')">Everest @400</button>
 <button onclick="cmd('dlbtest water status')">Etat de l'eau</button>
 <button onclick="cmd('list')">Joueurs</button>
 <span id=msg style="color:#fbbf24"></span>
</header>
<div class=box><pre id=sum>(chargement...)</pre></div>
<div class=box id=log><pre id=lines></pre></div>
<script>
const H=%s;
function col(l){for(const [k,c] of H){if(l.includes(k))return c}return '#cbd5e1'}
async function tick(){
  let s=await (await fetch('/state')).json();
  document.getElementById('run').className='pill '+(s.running?'ok':'ko');
  document.getElementById('run').textContent='serveur '+(s.running?'en ligne':'démarrage…');
  document.getElementById('rcon').className='pill '+(s.rcon?'ok':'ko');
  document.getElementById('rcon').textContent='rcon '+(s.rcon?'prêt':'…');
  let k=s.last||{}, out=[];
  for(const [n,t] of [['water','eau'],['settle','stabilisation'],['probe','sonde de tick'],['phase','dernière phase'],['struct','structure'],['warn','retard']])
    if(k[n]) out.push('<span class=key>'+t+'</span> : '+k[n]);
  document.getElementById('sum').innerHTML=out.join('\\n')||'(rien encore)';
  let r=await (await fetch('/log?n=600')).text();
  document.getElementById('lines').innerHTML=r.split('\\n').map(l=>'<span style="color:'+col(l)+'">'+(l.replace(/&/g,'&amp;').replace(/</g,'&lt;'))+'</span>').join('\\n');
  let el=document.getElementById('log'); el.scrollTop=el.scrollHeight;
}
async function cmd(c){
  document.getElementById('msg').textContent='envoi…';
  try{let r=await (await fetch('/cmd?c='+encodeURIComponent(c))).text();document.getElementById('msg').textContent=r;}
  catch(e){document.getElementById('msg').textContent='erreur';}
  setTimeout(()=>{document.getElementById('msg').textContent=''},6000);
}
setInterval(tick,2000); tick();
</script>
""" % json.dumps(HIGHLIGHT)


class Handler(http.server.BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def _send(self, code, body, ctype="text/plain; charset=utf-8"):
        b = body.encode("utf8")
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(b)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(b)

    def do_GET(self):
        u = urllib.parse.urlparse(self.path)
        q = urllib.parse.parse_qs(u.query)
        if u.path == "/":
            return self._send(200, PAGE, "text/html; charset=utf-8")
        if u.path == "/log":
            n = int(q.get("n", ["400"])[0])
            return self._send(200, "\n".join(read_tail(n)))
        if u.path == "/state":
            return self._send(200, json.dumps(state()), "application/json")
        if u.path == "/cmd":
            c = q.get("c", [""])[0].strip()
            if not ALLOWED.match(c):
                return self._send(403, "commande refusee (liste blanche)")
            try:
                return self._send(200, rcon(c) or "(envoye)")
            except Exception as e:
                return self._send(500, "rcon indisponible : %s" % e)
        return self._send(404, "?")


if __name__ == "__main__":
    srv = http.server.ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    print("panneau de suivi sur http://0.0.0.0:%d" % PORT, flush=True)
    srv.serve_forever()
