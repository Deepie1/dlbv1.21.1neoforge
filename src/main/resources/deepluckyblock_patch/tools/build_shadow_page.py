#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Assemble deliverables/DLB_Shadow_Three_Signatures_preview.html.

Meme GABARIT que les pages Gaia / Poison / Feu / Terre / Vent / Eau / Foudre : meme
squelette (aside / variants / controls / timeline / phase / stats / legend / refs /
download + main / canvas / topbar / hint / variant-title), meme CSS, simplement reteinte.
Les deux morceaux partages vivent dans le depot :

  tools/preview_base.css   — le CSS du gabarit
  tools/preview_render.js  — le moteur de rendu cable sur les identifiants du gabarit

Le moteur de rendu est generique SAUF son en-tete de donnees (SUBS / DUR / NAMES / BEATS /
SFX / GP) et l'appel a buildThunder. Les deux sont remplaces ici, par decoupe sur un
marqueur litteral : decouper par index de lignes echoue silencieusement des que le fichier
partage bouge, d'ou les assert.

L'archive est embarquee en base64 (bloc DLB-BUNDLE) : le bouton de telechargement
fonctionne meme dans une visionneuse integree.

Variable d'environnement SHA : viser refs/heads/<branche>, JAMAIS un SHA de commit — la
page est generee AVANT son propre commit, donc un SHA fige pointerait dans le vide.
"""
import base64
import hashlib
import io
import os

ROOT = 'src/main/resources/deepluckyblock_patch/tools/'
ENGINE = io.open(ROOT + 'shadow_build.js', encoding='utf-8').read()
ENGINE = ENGINE.replace(
    "if (typeof module !== 'undefined' && module.exports) module.exports = { buildShadow };",
    '')
CSS = io.open(ROOT + 'preview_base.css', encoding='utf-8').read()
RENDER = io.open(ROOT + 'preview_render.js', encoding='utf-8').read()

# --- Remplacement de l'en-tete de donnees du moteur partage ---------------------------
MARK = "\nconst cv=document.getElementById('scene')"
assert RENDER.count(MARK) == 1, 'marqueur de decoupe introuvable dans preview_render.js'
_, TAIL = RENDER.split(MARK, 1)

HEAD = r"""/* ===== Rendu ===== */
const SUBS=['couloir de 9 blocs \u00b7 main articul\u00e9e \u00b7 traction','cr\u00e2ne de d\u00e9mon \u00b7 souffle \u00b7 aspiration de 8 blocs','soleil noir \u00e0 23 blocs \u00b7 d\u00f4me de 7 \u00b7 nuit forc\u00e9e'];
const DUR=[118,160,200], NAMES=['Allonge Spectrale','Faille du Fl\u00e9trisseur',"Voile d'\u00c9clipse"];
const BEATS=[
 [['0 \u2192 15','la coul\u00e9e : l\u2019ombre se d\u00e9tache du sol et court sur <b>9 blocs</b>, bord dentel\u00e9'],
  ['15 \u2192 32','l\u2019\u00e9chine : <b>11 vert\u00e8bres</b> se soul\u00e8vent en vague qui s\u2019\u00e9loigne'],
  ['32 \u2192 53','le d\u00e9collement : la main pivote <i>autour du poignet</i> et se redresse'],
  ['53 \u2192 68','la poigne : les phalanges se replient une \u00e0 une, la braise s\u2019allume'],
  ['68 \u2192 97','<b>la traction</b> : la main revient, 4 vagues de lames se l\u00e8vent derri\u00e8re elle'],
  ['97 \u2192 118','le retour au sol : m\u00eame rotation \u00e0 l\u2019envers, tout part en cendre']],
 [['0 \u2192 11','la f\u00ealure : un trait vertical d\u2019un dixi\u00e8me de bloc, qui clignote'],
  ['11 \u2192 32','l\u2019ouverture : la f\u00ealure s\u2019\u00e9carte en <b>amande</b>, l\u00e8vres violettes'],
  ['32 \u2192 61','<b>le cr\u00e2ne de d\u00e9mon</b> : ~210 pi\u00e8ces, museau, arcades obliques, cornes annel\u00e9es'],
  ['61 \u2192 88','le souffle : gueule grande ouverte, c\u00f4ne de fl\u00e9trissure + <b>3 cr\u00e2nes mineurs</b>'],
  ['88 \u2192 118','<b>l\u2019inspiration</b> : 22 traits convergent depuis 8 blocs \u00e0 la ronde'],
  ['118 \u2192 136','la fermeture : l\u2019amande se pince, l\u2019onde part au sol'],
  ['136 \u2192 160','la cicatrice : une balafre de cendre s\u2019effrite']],
 [['0 \u2192 22','la mont\u00e9e : le <b>soleil noir</b> s\u2019\u00e9l\u00e8ve \u00e0 <b>23 blocs</b>, les \u00e9toiles s\u2019allument'],
  ['22 \u2192 48','la couronne : <b>48 filaments</b> + 3 protub\u00e9rances en arc \u2014 seule lumi\u00e8re'],
  ['48 \u2192 84','<b>le voile tombe</b> : d\u00f4me de 7 blocs reli\u00e9 au disque par <b>9 rayons cr\u00e9pusculaires</b> et une colonne en spirale'],
  ['84 \u2192 124','la nuit : cendre qui tombe, braises qui montent, sceau \u00e0 <b>3 croissants</b> et 8 runes'],
  ['124 \u2192 148','<b>l\u2019anneau de diamant</b> : le limbe \u00e9clate, une onde de choc part au sol'],
  ['148 \u2192 176','<b>la griffure</b> : 3 lames courbes traversent le d\u00f4me'],
  ['176 \u2192 200','la lev\u00e9e : le voile part en lambeaux vers le haut']]];
/* Conception sonore. L'ombre n'a pas de registre propre dans la banque vanilla : elle
   emprunte au Gardien, a l'Enderman et au Flétrisseur. Regle unique ici — on previent
   TOUJOURS avant de frapper, parce qu'un effet qu'on ne voit pas venir dans le noir est
   une punition, pas une signature. Chaque constante a ete verifiee par grep contre les
   usages reels du depot : quatre noms pressentis (soul_escape, phantom.bite,
   sculk.clicking, wither.shoot) n'existent pas en 1.21 et ont ete remplaces. */
const SFX=[
 "<b>Entr\u00e9e \u2014</b> <code>enderman.stare</code> et <code>chorus_fruit.teleport</code> \u00e0 0,52 de hauteur : l\u2019ombre glisse avant qu\u2019on la voie. <b>Coul\u00e9e \u2014</b> 5 paliers d\u2019encre et d\u2019\u00e2mes qui avancent le long du couloir. <b>Lames \u2014</b> <code>player.attack.sweep</code> grave (0,62) \u00e0 chaque victime lac\u00e9r\u00e9e. <b>Traction \u2014</b> <code>enderman.teleport</code> \u00e0 0,58 : le grave dit le poids de ce qu\u2019on tire. <b>Serrages \u2014</b> <code>chest.locked</code>, trois fois, espac\u00e9s de 12 ticks. <b>Sortie \u2014</b> <code>chorus_fruit.teleport</code> aigu sur le drain.",
 "<b>Ouverture \u2014</b> <code>warden.sonic_charge</code>, puis <code>portal.trigger</code> \u00e0 0,55 au tick 12. <b>Le cr\u00e2ne \u2014</b> <code>wither.spawn</code> aigu (1,55) au tick 32 : court, sec, on sait que quelque chose est sorti. <b>Souffle \u2014</b> <code>wither.ambient</code> \u00e0 0,75. <b>Aspiration \u2014</b> <b>4 <code>enderman.teleport</code></b> dont la hauteur monte de 0,48 \u00e0 0,66 : on <i>entend</i> la faille se remplir. <b>Fermeture \u2014</b> <code>warden.sonic_boom</code> \u00e0 1,40.",
 "<b>Mont\u00e9e \u2014</b> <b>5 <code>warden.heartbeat</code></b> de plus en plus rapproch\u00e9s et aigus : l\u2019attente devient la tension. <b>Pose du voile \u2014</b> <code>beacon.deactivate</code> + <code>ambient.cave</code> \u00e0 pleine puissance, au tick 48. <b>Battements \u2014</b> un <code>warden.heartbeat</code> toutes les 18 frames, du tick 68 au tick 140. <b>Anneau de diamant \u2014</b> <code>warden.sonic_charge</code> au tick 140, en m\u00eame temps que le dernier battement : l\u2019avertissement et le b\u00e9lier ne font qu\u2019un son. <b>Griffures \u2014</b> <code>warden.sonic_boom</code> + <code>player.attack.sweep</code> \u00e0 0,52 par victime. <b>Sortie \u2014</b> <code>portal.travel</code> \u00e0 1,40."];
const GP=[
 "<b>Contact.</b> Le couloir est un volume r\u00e9el \u2014 <b>9 blocs de long, 2,7 de large</b>, trac\u00e9 de la cible vers le porteur, avec filtre de projection sur l\u2019axe : ce n\u2019est pas un disque, on frappe <i>en ligne</i>. Quatre vagues de lames aux ticks 70, 79, 88 et 97, une seule fois par cible, 62 % de la base. <b>Timer.</b> La cible est <b>clou\u00e9e 72 ticks</b> (sans \u00e9tourdissement : elle se d\u00e9fend encore), puis <b>ramen\u00e9e</b> vers le porteur au tick 70 \u2014 arr\u00eat \u00e0 2 blocs, sinon elle rebondit dans lui. <b>Cible seule :</b> trois serrages de 34 % aux ticks 76, 88 et 100. <b>Buff :</b> Absorption 11 s, niveau selon le nombre drain\u00e9. <b>Effets :</b> Lenteur II + Faiblesse sur le couloir, C\u00e9cit\u00e9 en plus sur la cible tract\u00e9e.",
 "<b>Contact.</b> Souffle en <b>c\u00f4ne de 100\u00b0</b> sur 6,5 blocs au tick 62 \u2014 derri\u00e8re la faille, rien. 55 % de la base, Fl\u00e9trissure et Faim. <b>Timer.</b> <b>Aspiration</b> aux ticks 90, 98, 106 et 114 : tout ce qui est dans les <b>8 blocs</b> est tir\u00e9 vers la faille, avec composante verticale (une traction \u00e0 plat est absorb\u00e9e par la premi\u00e8re marche). Fermeture au tick 122. <b>Cible seule :</b> Fl\u00e9trissure <b>II</b> pour le double de la dur\u00e9e, et la fermeture passe de 68 % \u00e0 <b>105 %</b>. <b>Buff :</b> 1 PV rendu par victime aspir\u00e9e, plafond 4, plus R\u00e9g\u00e9n\u00e9ration. La signature ne tue pas la foule \u2014 elle la <i>rassemble</i>.",
 "<b>Contact.</b> <b>Contrat de position.</b> Le d\u00f4me de <b>7 blocs</b> s\u2019ancre au point d\u2019impact et n\u2019en bouge plus : ce qui reste dedans souffre, ce qui en sort est tranquille. <b>Timer.</b> <b>5 battements</b> aux ticks 68, 86, 104, 122 et 140 \u2014 30 % de la base, C\u00e9cit\u00e9 et Lenteur, une marque un battement sur deux. Puis <b>les griffures au tick 152</b> : 80 % de la base. <b>Cible seule :</b> les trois griffures <b>convergent</b> au lieu de se r\u00e9partir \u2014 <code>\u00d73</code>. <b>Buff :</b> tant que le porteur reste <i>sous</i> le d\u00f4me, Vision nocturne et Force renouvel\u00e9es \u00e0 chaque battement. L\u2019\u00e9clipse est une ar\u00e8ne, pas une fuite."];
"""

RENDER = HEAD + MARK + TAIL
assert RENDER.count('buildThunder') == 1, 'appel buildThunder introuvable'
RENDER = RENDER.replace('buildThunder', 'buildShadow')

# --- Cadrage. Le gabarit partage fait cadrer la variante 0 par boltShot (la chute de
#     l'eclair) et la variante 1 par hammerShot (la chute du marteau) : herite tel quel,
#     il filmait l'ombre avec la camera de la foudre. Les trois signatures n'ont rien de
#     commun en echelle -- un couloir de 9 blocs couche au sol, une faille de 2,7 blocs,
#     et une eclipse dont le disque est a 23 blocs -- donc un cadrage unique en
#     sacrifierait forcement une. La vue de l'eclipse S'OUVRE pendant la montee du
#     disque, comme ferait une camera qui suit un objet qui s'eleve.
CAM_OLD = """function updateCam(){CY=Math.cos(camYaw);SY=Math.sin(camYaw);CP=Math.cos(camPitch);SP=Math.sin(camPitch);
 let span;
 if(variant===1){const s=hammerShot(t);span=s.span;VCY=s.center;}
 else if(variant===0){const s=boltShot(t);span=s.span;VCY=s.center;}
 else{span=23.0;VCY=VIEW_CENTER_BY[variant];}
 SCALE=Math.min(W/span,H/(span*0.80))*zoom;}"""
CAM_NEW = """/* boltShot et hammerShot ci-dessus appartiennent au gabarit partage
   (foudre) et ne servent pas ici : l'ombre a son propre cadrage. */
function shadowShot(tt){
 if(variant===0) return {span:16.5, center:1.05};
 if(variant===1) return {span:15.0, center:2.10};
 const k=smoothstep((tt-0.015)/0.115);
 return {span:19.0+21.0*k, center:1.60+9.40*k};}
function updateCam(){CY=Math.cos(camYaw);SY=Math.sin(camYaw);CP=Math.cos(camPitch);SP=Math.sin(camPitch);
 const s=shadowShot(t); VCY=s.center;
 SCALE=Math.min(W/s.span,H/(s.span*0.80))*zoom;}"""
assert RENDER.count(CAM_OLD) == 1, 'bloc updateCam introuvable dans preview_render.js'
RENDER = RENDER.replace(CAM_OLD, CAM_NEW)

ZIP = 'deliverables/DLB_Shadow_Three_Signatures_src.zip'
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
<title>Deep Lucky Block — Ombre, trois signatures</title>
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
  <div class="eyebrow">Deep Lucky Block · Shadow VFX</div>
  <div class="build">T377 · 4 novembre 2026 — dixième famille · main articulée pliable à \
plat · crâne de démon · éclipse à 23 blocs</div>
  <h1>OMBRE</h1><h2>Trois signatures</h2>
  <p class="intro">Refonte complète de l’élément ombre : une ombre qui se détache du sol et \
va chercher sa proie, une faille qui recrache un crâne puis inspire, et une éclipse qui \
pose une arène de nuit. Un seul moteur voxel (<code>ShadowSignatureVfx</code>), zéro \
RenderType supplémentaire. <b>La règle qui gouverne tout ce fichier :</b> du noir sur un \
monde sombre ne produit aucune image, donc toute masse ici est bordée d’un liseré — c’est \
le liseré qu’on voit, le noir n’est que le trou qu’il entoure.</p>
  <div class="rule"></div>
  <span class="label">Signature de l’enchantement</span>
  <div class="variants" role="group" aria-label="Variante Ombre">
    <button class="variant active" data-variant="0"><span class="num">Ⅰ</span><span>\
<b>Allonge Spectrale</b><small>5,9 s · couloir de 9 blocs · main articulée</small>\
</span></button>
    <button class="variant" data-variant="1"><span class="num">Ⅱ</span><span>\
<b>Faille du Flétrisseur</b><small>8,0 s · crâne de démon · aspiration de 8 blocs</small>\
</span></button>
    <button class="variant" data-variant="2"><span class="num">Ⅲ</span><span>\
<b>Voile d’Éclipse</b><small>10,0 s · soleil noir à 23 blocs · nuit forcée</small>\
</span></button>
  </div>
  <span class="label">Chorégraphie du modèle Java</span>
  <div class="controls"><button class="btn primary" id="play">Pause</button>\
<button class="btn" id="replay">Rejouer</button>\
<button class="btn" id="dice">Graine</button></div>
  <input class="timeline" id="timeline" type="range" min="0" max="1000" value="0" \
aria-label="Progression de l’animation">
  <div class="phase" id="phase">01 — la coulée</div>
  <div class="stats"><div class="stat"><b id="baseCount">0</b><span>Voxels</span></div>\
<div class="stat"><b id="duration">5,9 s</b><span>Durée en jeu</span></div>\
<div class="stat"><b id="seedStat">4211</b><span>Graine</span></div></div>
  <div class="legend"><span style="--c:#0B0814">Trou</span>\
<span style="--c:#2C1C50">Pénombre</span><span style="--c:#4D318E">Primaire</span>\
<span style="--c:#7100A7">Accent</span><span style="--c:#A85FE0">Liseré</span>\
<span style="--c:#C2BAA6">Os</span></div>

  <p class="refs" id="beatsRefs"></p>
  <p class="refs" id="gpRefs"></p>
  <p class="refs" id="sfxRefs"></p>
  <p class="refs"><b>Moteur :</b> <code>ShadowSignatureVfx</code> — Allonge Spectrale (#3), \
Faille du Flétrisseur (#24), Voile d’Éclipse (#33). Les pièces sont émises par les \
émetteurs voxel du manager : aucun RenderType supplémentaire. <b>Attention :</b> l’aperçu \
ne modélise ni le tamis d’alpha ni le plafond de 7 000 cubes du moteur — il sert à relire \
la chorégraphie, <b>pas à la valider en jeu</b>.<br>Tests : <b>/anim shadow</b> · \
<b>/dlbvfx play 3</b> · <b>/dlbvfx play 24</b> · <b>/dlbvfx play 33</b> · \
<b>/dlbvfx stop</b></p>

  <a class="download" id="srcZip" href="{URL}" download="{ZIP_NAME}" rel="noopener">\
Télécharger les sources Ombre · ZIP</a>
  <div class="zipnote">ZIP autonome embarqué dans cette page — {ENTRIES} entrées, \
{len(raw)} octets, SHA-256 <code>{SHA256}</code>. Si l’aperçu intégré bloque le \
téléchargement, ouvre cette page dans un onglet du navigateur ou récupère le même fichier \
dans <code>deliverables/</code>.</div>
</aside>
<main>
  <canvas id="scene" tabindex="0" aria-label="Aperçu animé des trois signatures Ombre">\
</canvas>
  <div class="topbar"><span class="chip live">Modèle Java T377 · ShadowSignatureVfx</span>\
<span class="right"><a class="chip zipchip" id="srcZipTop" href="{URL}" \
download="{ZIP_NAME}" rel="noopener">ZIP sources</a>\
<span class="chip" id="visibleCount">0 voxels visibles</span></span></div>
  <div class="hint">Glisser pour orbiter · Molette pour zoomer · Espace pour pause</div>
  <div class="variant-title"><b id="variantName">Allonge Spectrale</b>\
<span id="variantSub">couloir de 9 blocs · main articulée · traction</span></div>
</main>
<script>
/* ===== Moteur de géométrie — inliné depuis tools/shadow_build.js (miroir du .java) ===== */
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

out = 'deliverables/DLB_Shadow_Three_Signatures_preview.html'
io.open(out, 'w', encoding='utf-8').write(PAGE)
print(f'{out} : {os.path.getsize(out)} o  (ZIP embarqué : {len(raw)} o, {ENTRIES} entrées)')
