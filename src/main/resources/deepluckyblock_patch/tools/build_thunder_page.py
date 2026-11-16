#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Assemble deliverables/DLB_Thunder_Three_Signatures_preview.html.

La page reprend le GABARIT DU BASE BUILDER — celui de Gaia, Poison, Feu, Terre, Vent :
même squelette (aside / variants / controls / timeline / phase / stats / legend / refs /
download + main / canvas / topbar / hint / variant-title), même CSS, simplement reteinté
aux couleurs de la foudre. Les deux morceaux partagés vivent dans le dépôt :

  tools/preview_base.css   — le CSS du gabarit, reteinté
  tools/preview_render.js  — le moteur de rendu câblé sur les identifiants du gabarit

L'archive est embarquée en base64 (bloc DLB-BUNDLE), comme sur les autres livrables : le
bouton de téléchargement fonctionne même dans une visionneuse intégrée.
"""
import base64
import hashlib
import io
import os

ROOT = 'src/main/resources/deepluckyblock_patch/tools/'
ENGINE = io.open(ROOT + 'thunder_build.js', encoding='utf-8').read()
ENGINE = ENGINE.replace(
    "if (typeof module !== 'undefined' && module.exports) module.exports = { buildThunder };",
    '')
CSS = io.open(ROOT + 'preview_base.css', encoding='utf-8').read()
RENDER = io.open(ROOT + 'preview_render.js', encoding='utf-8').read()

ZIP = 'deliverables/DLB_Thunder_Three_Signatures_src.zip'
ZIP_NAME = os.path.basename(ZIP)
raw = io.open(ZIP, 'rb').read() if os.path.exists(ZIP) else b''
B64 = base64.b64encode(raw).decode('ascii')
SHA256 = hashlib.sha256(raw).hexdigest()
ENTRIES = 0
if raw:
    import zipfile
    with zipfile.ZipFile(ZIP) as z:
        ENTRIES = len(z.namelist())

sha = os.environ.get('SHA', 'refs/heads/arena/605e67fb-dlbv1-21-1neoforge')
URL = (f'https://github.com/Deepie1/dlbv1.21.1neoforge/raw/{sha}/'
       f'deliverables/{ZIP_NAME}')

PAGE = f"""<!DOCTYPE html>
<html lang="fr"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Deep Lucky Block — Foudre, trois signatures</title>
<style>
{CSS}
</style></head>
<body>
<noscript><div style="margin:12px;padding:12px;border:1px solid #6a53c0;border-radius:8px;\
background:#140f28;color:#d6c8ff;font:13px system-ui">Le JavaScript est désactivé dans ce \
lecteur : l’animation ne peut pas démarrer. Télécharge l’aperçu et ouvre-le dans un \
navigateur, ou récupère les sources via ce lien direct : <a style="color:#c9b6ff" \
href="{URL}" rel="noopener">{ZIP_NAME}</a></div></noscript>
<aside>
  <div class="eyebrow">Deep Lucky Block · Thunder VFX</div>
  <div class="build">build v7 · 4 novembre 2026 — marteau de Thor · chute libre de 30 blocs \
· aura de 6 blocs sans contour · ciel chargé sur l’arc fulgurant</div>
  <h1>FOUDRE</h1><h2>Trois signatures</h2>
  <p class="intro">Refonte complète de l’élément foudre : un arc qui tombe du ciel et \
rebondit de cible en cible, le marteau de Thor lâché de trente blocs, et un orage qui se \
noue avant d’écraser une zone. Un seul moteur voxel (<code>ThunderSignatureVfx</code>), \
zéro RenderType supplémentaire.</p>
  <div class="rule"></div>
  <span class="label">Signature de l’enchantement</span>
  <div class="variants" role="group" aria-label="Variante Foudre">
    <button class="variant active" data-variant="0"><span class="num">Ⅰ</span><span>\
<b>Arc fulgurant en chaîne</b><small>6,5 s · ciel chargé · 6 relais · fulgurites</small>\
</span></button>
    <button class="variant" data-variant="1"><span class="num">Ⅱ</span><span>\
<b>Marteau de Thor</b><small>12,5 s · chute libre de 30 blocs · aura de 6 blocs</small>\
</span></button>
    <button class="variant" data-variant="2"><span class="num">Ⅲ</span><span>\
<b>Appel de l’orage</b><small>8,5 s · nuage à 8 blocs · mitraille · décharge</small>\
</span></button>
  </div>
  <span class="label">Chorégraphie du modèle Java</span>
  <div class="controls"><button class="btn primary" id="play">Pause</button>\
<button class="btn" id="replay">Rejouer</button>\
<button class="btn" id="dice">Graine</button></div>
  <input class="timeline" id="timeline" type="range" min="0" max="1000" value="0" \
aria-label="Progression de l’animation">
  <div class="phase" id="phase">01 — ionisation</div>
  <div class="stats"><div class="stat"><b id="baseCount">0</b><span>Voxels</span></div>\
<div class="stat"><b id="duration">6,5 s</b><span>Durée en jeu</span></div>\
<div class="stat"><b id="seedStat">7331</b><span>Graine</span></div></div>
  <div class="legend"><span style="--c:#5E3AFF">Canal</span>\
<span style="--c:#8E6BFF">Arc</span><span style="--c:#C9B6FF">Néon</span>\
<span style="--c:#FCFFBA">Chaud</span><span style="--c:#F3C63E">Or</span>\
<span style="--c:#2BE8FF">Runes</span></div>

  <p class="refs" id="beatsRefs"></p>
  <p class="refs" id="gpRefs"></p>
  <p class="refs" id="sfxRefs"></p>
  <p class="refs"><b>Moteur :</b> <code>ThunderSignatureVfx</code> — Arc fulgurant (#16), \
Marteau de Thor (#8, ex-surcadence cyber-X), Appel de l’orage (#4). Les pièces sont émises \
par les émetteurs voxel du manager : aucun RenderType supplémentaire. L’aperçu ne modélise \
ni le tamis d’alpha ni le plafond de 7 000 cubes du moteur — il sert à relire la \
chorégraphie, <b>pas à la valider en jeu</b>.<br>Tests : <b>/dlbvfx play 16</b> · \
<b>/dlbvfx play 8</b> · <b>/dlbvfx play 4</b> · <b>/dlbvfx stop</b></p>

  <a class="download" id="srcZip" href="{URL}" download="{ZIP_NAME}" rel="noopener">\
Télécharger les sources Foudre · ZIP</a>
  <div class="zipnote">ZIP autonome embarqué dans cette page — {ENTRIES} entrées, \
{len(raw)} octets, SHA-256 <code>{SHA256}</code>. Si l’aperçu intégré bloque le \
téléchargement, ouvre cette page dans un onglet du navigateur ou récupère le même fichier \
dans <code>deliverables/</code>.</div>
</aside>
<main>
  <canvas id="scene" tabindex="0" aria-label="Aperçu animé des trois signatures Foudre">\
</canvas>
  <div class="topbar"><span class="chip live">Modèle Java V7 · ThunderSignatureVfx</span>\
<span class="right"><a class="chip zipchip" id="srcZipTop" href="{URL}" \
download="{ZIP_NAME}" rel="noopener">ZIP sources</a>\
<span class="chip" id="visibleCount">0 voxels visibles</span></span></div>
  <div class="hint">Glisser pour orbiter · Molette pour zoomer · Espace pour pause</div>
  <div class="variant-title"><b id="variantName">Arc fulgurant en chaîne</b>\
<span id="variantSub">6 relais · ionisation · fulgurites</span></div>
</main>
<script>
/* ===== Moteur de géométrie — inliné depuis tools/thunder_build.js (miroir du .java) ===== */
{ENGINE}
{RENDER}
</script>
<script>
/* DLB-BUNDLE — archive embarquée : le bouton fonctionne même en visionneuse intégrée. */
(function () {{
  var ZIP_B64 = "{B64}";
  var ZIP_NAME = "{ZIP_NAME}";
  var link = document.getElementById('srcZip');
  var linkTop = document.getElementById('srcZipTop');
  if (!link && !linkTop) return;
  function toBlobUrl() {{
    var bin = atob(ZIP_B64);
    var bytes = new Uint8Array(bin.length);
    for (var i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
    return URL.createObjectURL(new Blob([bytes], {{ type: 'application/zip' }}));
  }}
  var url = null;
  try {{ url = toBlobUrl(); }} catch (err) {{ console.warn('ZIP embarque indisponible', err); }}
  var targets = [link, linkTop].filter(Boolean);
  if (url) targets.forEach(function (el) {{ el.href = url; el.setAttribute('download', ZIP_NAME); }});
  targets.forEach(function (el) {{
    el.addEventListener('click', function (ev) {{
      if (!url) return;
      try {{
        var a = document.createElement('a');
        a.href = url; a.download = ZIP_NAME;
        document.body.appendChild(a); a.click(); a.remove();
        ev.preventDefault();
      }} catch (err) {{ }}
    }});
  }});
}})();
</script>
</body></html>
"""

out = 'deliverables/DLB_Thunder_Three_Signatures_preview.html'
io.open(out, 'w', encoding='utf-8').write(PAGE)
print(f'{out} : {os.path.getsize(out)} o  (ZIP embarqué : {len(raw)} o, {ENTRIES} entrées)')
