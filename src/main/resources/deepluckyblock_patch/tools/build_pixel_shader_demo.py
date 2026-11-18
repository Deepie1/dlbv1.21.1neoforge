#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Assemble deliverables/DLB_Void_Pixel_shader_demo.html.

Meme principe que build_void_shader_demo.py : on ne DECRIT pas le core shader, on
l'EXECUTE. Les deux sources sont lues dans assets/ au moment de la construction et
portees en GLSL ES 3.00 par des substitutions mecaniques -- aucune ligne n'est
reecrite a la main, sans quoi la demo finirait par diverger du jeu en silence.

CE QUE CETTE DEMO PROUVE, ET QUE LA PAGE D'APERCU NE PEUT PAS PROUVER
---------------------------------------------------------------------
L'apercu des trois signatures rejoue la FORMULE du shader en JavaScript sur un canevas
2D : il montre la bonne couleur, mais il la calcule lui-meme. Ici, le .fsh tourne
reellement sur le GPU, avec le meme format de sommet que le jeu (POSITION_TEX_COLOR),
les memes uniformes (ModelViewMat / ProjMat / GameTime) et la meme geometrie -- six
quads par cube, vingt-quatre sommets, et AUCUNE arete modelisee.

Les trois choses qui se verifient a l'oeil sur cette page :
  1. les douze aretes sortent bien du seul test de bord sur les UV de face ;
  2. l'onde arc-en-ciel glisse selon GameTime ET selon la position monde du cube ;
  3. le coeur anthracite reste translucide et grene, sans ombrage directionnel.

Le compteur de cubes et le chronometre d'image sont la pour le dernier point de la
specification : « plusieurs milliers de cubes par seconde a 60+ FPS ». Ils mesurent un
navigateur et non Minecraft, mais ils mesurent le MEME shader sur le MEME nombre de
sommets, ce qui est le seul ordre de grandeur honnete qu'une page puisse donner.
"""
import io
import os

SH = 'src/main/resources/assets/deep_lucky_block/shaders/core/'
OUT = 'deliverables/DLB_Void_Pixel_shader_demo.html'


def to_es300(src, is_vertex):
    """Portage GLSL 150 -> GLSL ES 3.00. Trois substitutions, et pas une de plus."""
    out = src.replace('#version 150', '#version 300 es\nprecision highp float;', 1)
    if '#version 300 es' not in out:
        raise SystemExit('en-tete #version 150 introuvable : portage a revoir')
    if is_vertex:
        for decl, loc in (('in vec3 Position;', 0), ('in vec2 UV0;', 1), ('in vec4 Color;', 2)):
            if decl not in out:
                raise SystemExit('attribut introuvable dans le .vsh : ' + decl)
            out = out.replace(decl, 'layout(location = %d) %s' % (loc, decl), 1)
    return out


def escape_backtick(src):
    return src.replace('\\', '\\\\').replace('`', '\\`').replace('${', '\\${')


for _p in (SH + 'void_pixel.vsh', SH + 'void_pixel.fsh', SH + 'void_pixel.json'):
    assert os.path.exists(_p), 'asset du shader absent : ' + _p

VSH = escape_backtick(to_es300(io.open(SH + 'void_pixel.vsh', encoding='utf-8').read(), True))
FSH = escape_backtick(to_es300(io.open(SH + 'void_pixel.fsh', encoding='utf-8').read(), False))

# Controle de non-regression : le fragment DOIT contenir les trois pieces qui font le
# Pixel 3D. Si l'une disparait, la demo ne prouve plus rien et on refuse de la produire.
_raw_fsh = io.open(SH + 'void_pixel.fsh', encoding='utf-8').read()
for _needle in ('const float EDGE', 'chaosHue', 'hueToRgb', 'bodyGray', 'mode < 6.5'):
    assert _needle in _raw_fsh, 'piece manquante dans void_pixel.fsh : ' + _needle

sha = os.environ.get('SHA', 'refs/heads/arena/605e67fb-dlbv1-21-1neoforge')
ZIP_NAME = 'DLB_Chaos_Three_Signatures_src.zip'
URL = f'https://github.com/Deepie1/dlbv1.21.1neoforge/raw/{sha}/deliverables/{ZIP_NAME}'

PAGE = f"""<!DOCTYPE html>
<html lang="fr"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Deep Lucky Block — void_pixel, le core shader des Pixels 3D</title>
<style>
*{{box-sizing:border-box}}
body{{margin:0;background:#07050f;color:#e7dcff;font:14px/1.55 system-ui,Segoe UI,sans-serif;
 display:grid;grid-template-columns:minmax(300px,380px) 1fr;min-height:100vh}}
aside{{padding:22px 20px;border-right:1px solid #2a1f52;overflow:auto;max-height:100vh}}
.eyebrow{{font:600 11px/1 system-ui;letter-spacing:.16em;text-transform:uppercase;color:#a88cff}}
h1{{font:300 40px/1 Georgia,serif;letter-spacing:.06em;margin:14px 0 2px}}
h2{{font:400 15px/1.3 system-ui;letter-spacing:.2em;text-transform:uppercase;color:#9d8ad6;margin:0 0 16px}}
p{{color:#c3b6e4;font-size:12.5px}}
code{{background:#19123a;padding:1px 4px;border-radius:3px;font-size:11.5px;color:#d9c6ff}}
b{{color:#f0e6ff}}
.rule{{height:1px;background:#2a1f52;margin:16px 0}}
.label{{font:600 10px/1 system-ui;letter-spacing:.18em;text-transform:uppercase;color:#8c7bc0;display:block;margin:16px 0 8px}}
.btn{{background:#1b1340;border:1px solid #3b2a73;color:#e7dcff;border-radius:7px;
 padding:7px 12px;font:600 12px system-ui;cursor:pointer;margin:0 6px 6px 0}}
.btn.on{{background:#4b2a8a;border-color:#7e4fd0}}
input[type=range]{{width:100%;margin:6px 0 2px}}
.stats{{display:flex;gap:8px;margin:12px 0}}
.stat{{flex:1;background:#140e30;border:1px solid #2a1f52;border-radius:8px;padding:8px;text-align:center}}
.stat b{{display:block;font:600 17px system-ui;color:#fff}}
.stat span{{font:600 9px/1 system-ui;letter-spacing:.12em;text-transform:uppercase;color:#8c7bc0}}
main{{position:relative;min-height:100vh}}
canvas{{display:block;width:100%;height:100vh;background:#07050f}}
.topbar{{position:absolute;inset:14px 14px auto 14px;display:flex;gap:8px;pointer-events:none;flex-wrap:wrap}}
.chip{{background:#140e30cc;border:1px solid #3b2a73;border-radius:999px;padding:5px 12px;
 font:600 10px/1 system-ui;letter-spacing:.1em;text-transform:uppercase;color:#cbb8ff;pointer-events:auto}}
.chip.live::before{{content:'';display:inline-block;width:6px;height:6px;border-radius:50%;
 background:#3FE8FF;margin-right:7px;vertical-align:1px}}
.hint{{position:absolute;left:14px;bottom:14px;font:12px system-ui;color:#8c7bc0}}
a.dl{{display:block;text-align:center;background:#4b2a8a;border:1px solid #7e4fd0;color:#fff;
 border-radius:9px;padding:11px;font:600 13px system-ui;text-decoration:none;margin-top:16px}}
pre{{background:#100b26;border:1px solid #2a1f52;border-radius:8px;padding:10px;overflow:auto;
 font:11px/1.45 ui-monospace,Menlo,monospace;color:#bda9ef;max-height:260px}}
</style></head>
<body>
<aside>
  <div class="eyebrow">Deep Lucky Block · core shader</div>
  <h1>void_pixel</h1><h2>Les Pixels 3D, en vrai</h2>
  <p>Cette page <b>ne décrit pas</b> le shader : elle l’exécute. <code>void_pixel.vsh</code>
  et <code>void_pixel.fsh</code> sont lus dans <code>assets/</code> à la construction et
  portés en GLSL ES 3.00 par <b>des substitutions mécaniques</b> — l’en-tête
  <code>#version</code> et les <code>layout(location)</code> des trois attributs. Aucune
  ligne n’est réécrite à la main, sans quoi la démo finirait par diverger du jeu en
  silence.</p>
  <div class="rule"></div>
  <p><b>Ce que vous regardez.</b> Un champ de cubes miniatures, chacun écrit comme en jeu :
  <b>six quads, vingt-quatre sommets</b>, format <code>POSITION_TEX_COLOR</code>. <b>Aucune
  arête n’est modélisée.</b> Les douze arêtes que vous voyez sortent d’un unique test de
  bord sur les UV de face — <code>min(uv, 1−uv) &lt; 0,10</code> — et les quatre bords de
  six faces donnent exactement douze arêtes, chacune vue par ses deux faces adjacentes.</p>
  <p><b>L’onde.</b> La teinte vient de <code>chaosHue(phase + GameTime·48 +
  (u+v)·0,35)</code>, où <code>phase</code> est la position <b>monde</b> du cube, calculée
  une fois côté CPU. <code>chaosHue</code> ne parcourt pas la roue complète mais sa
  <b>moitié froide</b>, entre 0,50 et 0,92 : cyan électrique, bleu, violet profond,
  magenta néon. Seul le mode <b>spectrum</b> en sort — et c’est pour cela qu’il signifie
  quelque chose.</p>
  <p><b>Les huit dérivés.</b> <code>rainbow</code> l’onde froide continue ·
  <code>tinted</code> une teinte imposée dont seul l’éclat pulse · <code>mono</code> la
  luminance seule, le mode de ce qui est encore en ordre · <code>spectrum</code> la roue
  entière · <code>pulse</code> une tête vive qui <i>court</i> le long de l’arête ·
  <code>dual</code> deux teintes selon l’orientation de l’arête, et l’œil lit la cage du
  cube · <code>strobe</code> l’arc-en-ciel quantifié en six crans, la couleur saute au
  lieu de glisser · <code>ember</code> dérive lente et fort scintillement.
  <b>Cliquez-les :</b> c’est le même shader que le jeu, pas une imitation.</p>
  <p><b>Fullbright par construction.</b> Le programme ne reçoit <b>ni lightmap ni
  overlay</b> : il n’y a rien à désactiver pour que les arêtes brillent dans une grotte à
  minuit, elles brillent parce qu’aucune lumière n’est jamais appliquée.</p>
  <p><b>Le corps n’a jamais de couleur — il a un gris.</b> Trente-deux crans, de 0,055 à
  0,855 de luminance, transmis <b>par cube</b>. La rampe ne part pas de zéro (un corps
  noir se confond avec le fond de nuit) et ne monte pas à un (il rivaliserait avec les
  arêtes, qui doivent rester les plus claires de l’image). Un grain procédural de ±3 %
  évite la face plate qu’un quad non texturé trahit immédiatement.</p>
  <p><b>Un seul octet pour le mode ET le gris.</b> Le format de sommet reste
  <code>POSITION_TEX_COLOR</code> : <code>UV0</code> porte les coordonnées de face,
  les quatre canaux de <code>Color</code> sont pris. Le canal vert est donc empaqueté —
  <code>g = mode·32 + gris</code> — soit <b>huit comportements × trente-deux
  luminances</b>, 256 cubes visuellement distincts, pour une division et un modulo par
  sommet. Le bouton <i>gris uniforme</i> bascule entre les deux états que la Sphère
  d’Entropie met en scène : toutes les cellules au cran 16 (l’ordre), ou chacune au
  hasard sur les trente-deux (le désordre).</p>
  <span class="label">Les huit dérivés de l’arête</span>
  <div>
    <button class="btn on" id="bMode0">Rainbow</button>
    <button class="btn" id="bMode1">Tinted</button>
    <button class="btn" id="bMode2">Mono</button>
    <button class="btn" id="bMode3">Spectrum</button>
    <button class="btn" id="bMode4">Pulse</button>
    <button class="btn" id="bMode5">Dual</button>
    <button class="btn" id="bMode6">Strobe</button>
    <button class="btn" id="bMode7">Ember</button>
  </div>
  <span class="label">Le corps</span>
  <div>
    <button class="btn" id="bGray">Gris uniforme (ordre)</button>
    <button class="btn on" id="bSpin">Rotation</button>
    <button class="btn" id="bFreeze">Figer le temps</button>
    <button class="btn" id="bBig">Gros plan</button>
  </div>
  <span class="label">Nombre de cubes</span>
  <input type="range" id="count" min="64" max="12000" step="64" value="4096">
  <div class="stats">
    <div class="stat"><b id="sCubes">4096</b><span>Cubes</span></div>
    <div class="stat"><b id="sTris">0</b><span>Triangles</span></div>
    <div class="stat"><b id="sFps">—</b><span>Images/s</span></div>
  </div>
  <p><b>Le budget.</b> Le curseur monte jusqu’à <b>12 000</b>, le plafond exact de
  <code>VoidPixelManager</code>. À ce nombre, la géométrie pèse <b>288 000 sommets</b> et
  <b>144 000 triangles</b> — et le tout part en <b>un seul lot</b>, un seul
  <code>endBatch</code>, parce qu’une arête en géométrie aurait multiplié ce chiffre par
  huit. Le compteur d’images mesure un navigateur et non Minecraft, mais il mesure le
  <b>même shader</b> sur le <b>même nombre de sommets</b> : c’est le seul ordre de grandeur
  honnête qu’une page puisse donner.</p>
  <span class="label">Le fragment, tel qu’il tourne</span>
  <pre id="fshDump"></pre>
  <a class="dl" href="{URL}" rel="noopener">Télécharger les sources Chaos · ZIP</a>
</aside>
<main>
  <canvas id="gl"></canvas>
  <div class="topbar"><span class="chip live" id="chipState">compilation…</span>
  <span class="chip" id="chipMode">mode · arc-en-ciel</span>
  <span class="chip" id="chipTime">GameTime · 0,0000</span></div>
  <div class="hint">Glisser pour orbiter · Molette pour zoomer</div>
</main>
<script>
const VSH = `{VSH}`;
const FSH = `{FSH}`;
document.getElementById('fshDump').textContent = FSH;

const cv = document.getElementById('gl');
const gl = cv.getContext('webgl2', {{alpha:false, antialias:true}});
const chipState = document.getElementById('chipState');
if (!gl) {{ chipState.textContent = 'WebGL 2 indisponible'; }}

function compile(type, src) {{
  const s = gl.createShader(type);
  gl.shaderSource(s, src); gl.compileShader(s);
  if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(s));
  return s;
}}
let prog = null;
try {{
  prog = gl.createProgram();
  gl.attachShader(prog, compile(gl.VERTEX_SHADER, VSH));
  gl.attachShader(prog, compile(gl.FRAGMENT_SHADER, FSH));
  gl.linkProgram(prog);
  if (!gl.getProgramParameter(prog, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(prog));
  chipState.textContent = 'void_pixel · compilé';
}} catch (e) {{ chipState.textContent = 'erreur : ' + e.message; console.error(e); }}

/* ---- LA GEOMETRIE, ecrite comme VoidPixelManager.emitCube l'ecrit -------------------
   Six quads, quatre sommets chacun, UV de face 0..1 sur chaque quad. Le cube n'a aucune
   arete : c'est tout l'interet. On passe par un index buffer pour transformer les 24
   sommets en 12 triangles, exactement ce que fait le mode QUADS de Minecraft. */
const FACES = [
  [[-1,-1, 1],[ 1,-1, 1],[ 1, 1, 1],[-1, 1, 1]],
  [[ 1,-1,-1],[-1,-1,-1],[-1, 1,-1],[ 1, 1,-1]],
  [[ 1,-1, 1],[ 1,-1,-1],[ 1, 1,-1],[ 1, 1, 1]],
  [[-1,-1,-1],[-1,-1, 1],[-1, 1, 1],[-1, 1,-1]],
  [[-1, 1, 1],[ 1, 1, 1],[ 1, 1,-1],[-1, 1,-1]],
  [[-1,-1,-1],[ 1,-1,-1],[ 1,-1, 1],[-1,-1, 1]]];
const UVQ = [[0,0],[1,0],[1,1],[0,1]];

let VBO = gl.createBuffer(), IBO = gl.createBuffer(), VAO = gl.createVertexArray();
let nCubes = 4096, nIndices = 0, mode = 0, big = false, uniformGray = false;

/* Hachage deterministe : le champ doit etre le meme d'une image a l'autre, sinon on ne
   verrait pas l'onde glisser, on verrait du bruit. */
function hash(a, b) {{
  let h = (Math.imul(a, 374761393) + Math.imul(b, 668265263)) | 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177) | 0;
  return ((h ^ (h >>> 16)) & 0x7FFFFF) / 8388607.0;
}}

function build(n) {{
  const V = new Float32Array(n * 24 * 9);
  const I = new Uint32Array(n * 36);
  let vi = 0, ii = 0;
  const SPAN = big ? 3.0 : 14.0;
  for (let c = 0; c < n; c++) {{
    const cx = (hash(c, 11) - 0.5) * SPAN;
    const cy = (hash(c, 23) - 0.5) * SPAN * 0.55;
    const cz = (hash(c, 37) - 0.5) * SPAN;
    const half = (big ? 0.22 : 0.075) * (0.7 + 0.6 * hash(c, 53));
    /* La phase spatiale, calculee comme dans VoidPixelManager : une combinaison lineaire
       des coordonnees MONDE, et rien d'autre. Aucune couleur n'est calculee ici. */
    let raw = (cx * 0.17 + cy * 0.23 + cz * 0.13) % 1.0; if (raw < 0) raw += 1.0;
    const tint = hash(c, 71);
    // LE GRIS. uniformGray montre l'etat « en ordre » (toutes les faces au meme cran,
    // comme le treillis intact de la Sphere d'Entropie) ; sinon chaque cube tire son cran
    // sur les trente-deux, ce qui est l'etat « en desordre ». Le contraste entre les deux
    // est exactement ce que la deuxieme signature met en scene.
    const gray = uniformGray ? 16 : Math.min(31, (hash(c, 83) * 32) | 0);
    const base = c * 24;
    for (let f = 0; f < 6; f++) {{
      for (let q = 0; q < 4; q++) {{
        const o = FACES[f][q];
        V[vi++] = cx + o[0] * half; V[vi++] = cy + o[1] * half; V[vi++] = cz + o[2] * half;
        V[vi++] = UVQ[q][0]; V[vi++] = UVQ[q][1];
        V[vi++] = raw;                       // Color.r = phase spatiale
        // Color.g = OCTET EMPAQUETE : mode * 32 + gris. Ecrire ici le seul numero de
        // mode, comme le faisait la premiere version de cette page, ferait lire au
        // shader un mode nul et un gris egal au mode : la demo montrerait autre chose
        // que le jeu.
        V[vi++] = (mode * 32 + gray) / 255.0;
        V[vi++] = tint;                      // Color.b = teinte imposee
        V[vi++] = 1.0;                       // Color.a = fondu
      }}
      const o4 = base + f * 4;
      I[ii++] = o4; I[ii++] = o4 + 1; I[ii++] = o4 + 2;
      I[ii++] = o4; I[ii++] = o4 + 2; I[ii++] = o4 + 3;
    }}
  }}
  nIndices = ii;
  gl.bindVertexArray(VAO);
  gl.bindBuffer(gl.ARRAY_BUFFER, VBO);
  gl.bufferData(gl.ARRAY_BUFFER, V, gl.STATIC_DRAW);
  const S = 9 * 4;
  gl.enableVertexAttribArray(0); gl.vertexAttribPointer(0, 3, gl.FLOAT, false, S, 0);
  gl.enableVertexAttribArray(1); gl.vertexAttribPointer(1, 2, gl.FLOAT, false, S, 12);
  gl.enableVertexAttribArray(2); gl.vertexAttribPointer(2, 4, gl.FLOAT, false, S, 20);
  gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, IBO);
  gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, I, gl.STATIC_DRAW);
  gl.bindVertexArray(null);
  document.getElementById('sCubes').textContent = n;
  document.getElementById('sTris').textContent = (n * 12).toLocaleString('fr-FR');
}}

let yaw = 0.6, pitch = 0.32, dist = 16, spin = true, frozen = false, gameTime = 0;
function mat(out, yawA, pitchA, d) {{
  const cy = Math.cos(yawA), sy = Math.sin(yawA), cp = Math.cos(pitchA), sp = Math.sin(pitchA);
  out[0] = cy;       out[1] = sy * sp;  out[2] = -sy * cp; out[3] = 0;
  out[4] = 0;        out[5] = cp;       out[6] = sp;       out[7] = 0;
  out[8] = sy;       out[9] = -cy * sp; out[10] = cy * cp; out[11] = 0;
  out[12] = 0;       out[13] = 0;       out[14] = -d;      out[15] = 1;
}}
function proj(out, fov, asp, zn, zf) {{
  const f = 1 / Math.tan(fov / 2);
  out.fill(0);
  out[0] = f / asp; out[5] = f; out[10] = (zf + zn) / (zn - zf);
  out[11] = -1; out[14] = 2 * zf * zn / (zn - zf);
}}
const MV = new Float32Array(16), PR = new Float32Array(16);

let last = performance.now(), acc = 0, frames = 0;
function frame(now) {{
  const dt = Math.min(0.1, (now - last) / 1000); last = now;
  acc += dt; frames++;
  if (acc > 0.5) {{ document.getElementById('sFps').textContent = Math.round(frames / acc);
                   acc = 0; frames = 0; }}
  if (!frozen) gameTime = (gameTime + dt / 1200) % 1.0;
  if (spin) yaw += dt * 0.18;
  document.getElementById('chipTime').textContent =
    'GameTime · ' + gameTime.toFixed(4).replace('.', ',');

  const dpr = Math.min(2, devicePixelRatio || 1);
  const w = cv.clientWidth * dpr | 0, h = cv.clientHeight * dpr | 0;
  if (cv.width !== w || cv.height !== h) {{ cv.width = w; cv.height = h; }}
  gl.viewport(0, 0, w, h);
  gl.clearColor(0.027, 0.020, 0.059, 1); gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);
  if (!prog) {{ requestAnimationFrame(frame); return; }}
  /* Melange et profondeur comme dans ModRenderTypes.voidPixel : translucide, test de
     profondeur LEQUAL, mais AUCUNE ecriture de profondeur -- deux cubes translucides
     qui s'ecriraient mutuellement se masqueraient par ordre d'arrivee. */
  gl.enable(gl.BLEND); gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
  gl.enable(gl.DEPTH_TEST); gl.depthFunc(gl.LEQUAL); gl.depthMask(false);
  gl.disable(gl.CULL_FACE);

  mat(MV, yaw, pitch, dist);
  proj(PR, 1.22, w / Math.max(1, h), 0.05, 200);
  gl.useProgram(prog);
  gl.uniformMatrix4fv(gl.getUniformLocation(prog, 'ModelViewMat'), false, MV);
  gl.uniformMatrix4fv(gl.getUniformLocation(prog, 'ProjMat'), false, PR);
  gl.uniform1f(gl.getUniformLocation(prog, 'GameTime'), gameTime);
  gl.bindVertexArray(VAO);
  gl.drawElements(gl.TRIANGLES, nIndices, gl.UNSIGNED_INT, 0);
  gl.bindVertexArray(null);
  requestAnimationFrame(frame);
}}

const MODE_NAMES = ['rainbow', 'tinted', 'mono', 'spectrum',
                    'pulse', 'dual', 'strobe', 'ember'];
function setMode(m) {{
  mode = m;
  for (let i = 0; i < 8; i++)
    document.getElementById('bMode' + i).classList.toggle('on', i === m);
  document.getElementById('chipMode').textContent = 'mode · ' + MODE_NAMES[m];
  build(nCubes);
}}
for (let i = 0; i < 8; i++)
  document.getElementById('bMode' + i).onclick = () => setMode(i);
document.getElementById('bGray').onclick = function () {{
  uniformGray = !uniformGray; this.classList.toggle('on', uniformGray);
  this.textContent = uniformGray ? 'Gris tir\u00e9 au hasard (d\u00e9sordre)'
                                 : 'Gris uniforme (ordre)';
  build(nCubes); }};
document.getElementById('bSpin').onclick = function () {{
  spin = !spin; this.classList.toggle('on', spin); }};
document.getElementById('bFreeze').onclick = function () {{
  frozen = !frozen; this.classList.toggle('on', frozen); }};
document.getElementById('bBig').onclick = function () {{
  big = !big; this.classList.toggle('on', big); dist = big ? 5.2 : 16; build(nCubes); }};
document.getElementById('count').oninput = e => {{ nCubes = +e.target.value; build(nCubes); }};
let drag = null;
cv.addEventListener('pointerdown', e => {{ drag = [e.clientX, e.clientY];
  cv.setPointerCapture(e.pointerId); }});
cv.addEventListener('pointermove', e => {{ if (!drag) return;
  yaw += (e.clientX - drag[0]) * 0.008;
  pitch = Math.max(-1.2, Math.min(1.2, pitch + (e.clientY - drag[1]) * 0.006));
  drag = [e.clientX, e.clientY]; }});
cv.addEventListener('pointerup', () => drag = null);
cv.addEventListener('pointercancel', () => drag = null);
cv.addEventListener('wheel', e => {{ e.preventDefault();
  dist = Math.max(1.5, Math.min(60, dist * (e.deltaY > 0 ? 1.08 : 0.92))); }}, {{passive:false}});

if (gl) {{ build(nCubes); requestAnimationFrame(frame); }}
</script>
</body></html>
"""

io.open(OUT, 'w', encoding='utf-8').write(PAGE)
print('%s : %d o' % (OUT, os.path.getsize(OUT)))
