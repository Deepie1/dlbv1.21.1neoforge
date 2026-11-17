#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Assemble deliverables/DLB_Void_Portal_shader_demo.html.

POURQUOI CE LIVRABLE EXISTE
---------------------------
L'apercu voxel (build_void_page.py) rejoue la choregraphie des cinq signatures, mais il
ne sait PAS montrer le core shader : son moteur dessine des cubes de couleur, pas des
couches de texture projetees. Or le shader est precisement la partie qu'on ne peut pas
juger sur parole.

Cette page execute donc le VRAI shader. Le .vsh et le .fsh sont lus depuis
assets/deep_lucky_block/shaders/core/ et portes en GLSL ES 3.00 par DEUX substitutions
mecaniques (#version, et la taille explicite du const array, qu'ES exige). Les deux
textures sont les vraies, embarquees en base64. La geometrie est celle de VoidPortalFx :
une ellipse de 22 secteurs, dans l'un de ses TROIS plans (face camera pour la sphere, au
sol pour la porte, dans les levres pour la faille), aux cotes que la figure lui impose.

Ce qui s'affiche ici est donc ce qui s'affichera en jeu, au filtrage de texture pres.

A LANCER DEPUIS LA RACINE DU DEPOT.
"""
import base64
import io
import os
import re
import sys

SH = "src/main/resources/assets/deep_lucky_block/shaders/core"
TX = "src/main/resources/assets/deep_lucky_block/textures/effect"
CSS = "src/main/resources/deepluckyblock_patch/tools/preview_base.css"
OUT = "deliverables/DLB_Void_Portal_shader_demo.html"


def read(path):
    return io.open(path, encoding="utf-8").read()


def b64(path):
    return "data:image/png;base64," + base64.b64encode(io.open(path, "rb").read()).decode("ascii")


def to_es300(src, is_vertex):
    """Portage GLSL 150 -> GLSL ES 3.00. Deux substitutions, et pas une de plus."""
    out = src.replace("#version 150", "#version 300 es\nprecision highp float;", 1)
    if is_vertex:
        out = out.replace("in vec3 Position;", "layout(location = 0) in vec3 Position;", 1)
    else:
        out = out.replace("const vec3[] COLORS = vec3[](", "const vec3 COLORS[16] = vec3[16](", 1)
        if "COLORS[16]" not in out:
            raise SystemExit("la declaration de COLORS a change : portage a revoir")
    return out


def escape_backtick(src):
    return src.replace("\\", "\\\\").replace("`", "\\`").replace("${", "\\${")


def count_colors(fsh):
    block = fsh[fsh.index("COLORS"):fsh.index(");", fsh.index("COLORS"))]
    # Le constructeur s'ecrit « vec3[]( » : il ne matche pas « vec3( », donc rien a retrancher.
    return len(re.findall(r"vec3\(", block))


def main():
    for p in (SH + "/void_portal.vsh", SH + "/void_portal.fsh",
              TX + "/void_sky.png", TX + "/void_stars.png", CSS):
        if not os.path.exists(p):
            print("  ! fichier absent : " + p)
            return 1

    vsh = read(SH + "/void_portal.vsh")
    fsh = read(SH + "/void_portal.fsh")
    n_colors = count_colors(fsh)
    if n_colors != 16:
        print("  ! COLORS compte %d entrees, 16 attendues" % n_colors)
        return 1

    html = TEMPLATE
    html = html.replace("__CSS__", read(CSS))
    html = html.replace("__VSH__", escape_backtick(to_es300(vsh, True)))
    html = html.replace("__FSH__", escape_backtick(to_es300(fsh, False)))
    html = html.replace("__VSH_SRC__", vsh.replace("&", "&amp;").replace("<", "&lt;"))
    html = html.replace("__FSH_SRC__", fsh.replace("&", "&amp;").replace("<", "&lt;"))
    html = html.replace("__SKY__", b64(TX + "/void_sky.png"))
    html = html.replace("__STARS__", b64(TX + "/void_stars.png"))

    os.makedirs("deliverables", exist_ok=True)
    io.open(OUT, "w", encoding="utf-8").write(html)
    print("%s : %d octets (COLORS = %d entrees)" % (OUT, len(html), n_colors))
    return 0


TEMPLATE = r"""<!DOCTYPE html>
<html lang="fr"><head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>DLB — void_portal · le core shader du Vide</title>
<style>
__CSS__
main{position:relative;display:grid;place-items:center;min-width:0}
canvas{display:block;width:100%;height:100%}
.hud{position:absolute;left:18px;bottom:16px;display:flex;gap:7px;flex-wrap:wrap;
     font-size:11px;color:#a99cd0}
.hud code{background:#17122e;border:1px solid #352c5e;border-radius:5px;padding:3px 7px;color:#d6c9ff}
.srow{display:grid;grid-template-columns:76px 1fr 54px;align-items:center;gap:8px;margin:0 0 9px}
.srow span{font-size:10px;letter-spacing:.1em;text-transform:uppercase;color:#9b90bd}
.srow b{font-size:11px;color:#d9cdff;text-align:right;font-weight:600}
.srow input{width:100%;accent-color:#8e6bff}
.swatch{display:grid;grid-template-columns:repeat(8,1fr);gap:3px;margin:0 0 16px}
.swatch i{display:block;height:17px;border-radius:3px;background:var(--c);border:1px solid #ffffff14}
details{margin:14px 0 0;border:1px solid #2c2450;border-radius:7px;background:#100d22}
summary{cursor:pointer;padding:9px 11px;font-size:11px;letter-spacing:.07em;
        text-transform:uppercase;color:#bcaae6}
pre{margin:0;padding:0 12px 12px;overflow:auto;max-height:260px;font-size:10.5px;
    line-height:1.5;color:#b9aee0;white-space:pre}
.warn{border-left:2px solid #9b7dff;padding-left:10px;color:#a99cd0;font-size:12px}
</style></head>
<body>
<aside>
  <div class="eyebrow">Deep Lucky Block · Void VFX</div>
  <div class="build">T390 · core shader <code style="color:#d6c9ff">void_portal</code></div>
  <h1>LE TROU</h1><h2>Parallaxe d’écran</h2>
  <p class="intro">Les cinq signatures du Vide ne dessinent que des <b>bords</b> : l’intérieur
  de chaque figure est laissé rigoureusement vide, et le noir du monde passe au travers.
  C’est juste — mais ça repose entièrement sur ce qu’il y a derrière. Devant un mur de
  pierre claire, l’anneau de la Porte d’Horizon encadre… de la pierre claire.</p>
  <p class="intro">Ce shader comble ce manque sans trahir la règle : il ne dessine pas de
  matière, il <b>remplace ce qu’on voit à travers</b>. Quinze couches d’étoiles additives,
  tournées et translatées différemment, dont les coordonnées de texture viennent de
  l’<b>écran</b> et non du modèle. C’est le procédé exact du portail de l’End.</p>
  <div class="rule"></div>

  <span class="label">Le plan, et la figure qui l’encadre</span>
  <div class="variants" id="plans">
    <button class="variant active" data-plan="0"><span class="num">I</span>
      <span><b>Panneau-réclame</b><small>Sphère de Distorsion · r 1,08 · face caméra</small></span></button>
    <button class="variant" data-plan="1"><span class="num">II</span>
      <span><b>Au sol</b><small>Porte d’Horizon · r 1,87 · la mare dans l’anneau</small></span></button>
    <button class="variant" data-plan="2"><span class="num">III</span>
      <span><b>Dans les lèvres</b><small>Faille du Néant · 0,66 × 1,34 · disparaît par la tranche</small></span></button>
  </div>

  <span class="label">Réglages</span>
  <div class="srow"><span>Couches</span><input id="layers" type="range" min="1" max="15" value="15"><b id="vlayers">15</b></div>
  <div class="srow"><span>Orbite</span><input id="yaw" type="range" min="-180" max="180" value="0"><b id="vyaw">0°</b></div>
  <div class="srow"><span>Visée</span><input id="aim" type="range" min="-60" max="60" value="0"><b id="vaim">0,0</b></div>
  <div class="srow"><span>Dérive</span><input id="warp" type="range" min="1" max="24" value="1"><b id="vwarp">×1</b></div>
  <div class="controls">
    <button class="btn primary" id="play">Pause</button>
    <button class="btn" id="reset">Recentrer</button>
  </div>

  <span class="label">COLORS[] — les quinze couches</span>
  <div class="swatch" id="swatch"></div>

  <p class="refs"><b>Ce que vous regardez est le vrai shader.</b> Le <code>.vsh</code> et le
  <code>.fsh</code> de <code>assets/deep_lucky_block/shaders/core/</code> sont portés en
  GLSL&nbsp;ES&nbsp;3.00 par deux substitutions mécaniques (la ligne <code>#version</code>,
  et la taille explicite du tableau constant qu’ES exige), puis compilés tels quels. Les
  deux textures sont les vraies, embarquées en base64. La géométrie est celle de
  <code>VoidPortalFx</code> : une ellipse de 22 secteurs, dans l’un des <b>trois plans</b>
  ci-dessus, aux cotes que la figure lui impose.</p>

  <p class="refs"><b>Trois fenêtres, pas cinq.</b> Le Prisme et la Trame n’en ont pas : la
  fenêtre répare les figures qui encadrent un grand vide, et ces deux-là n’ont pas
  d’intérieur. Un trou sans lèvre pour l’encadrer n’est qu’un disque noir — c’est-à-dire
  un objet, exactement ce que la règle du bord interdit.</p>

  <p class="refs warn"><b>La seule chose à vérifier ici :</b> déplacez la <b>visée</b>. La
  fenêtre glisse sur l’écran — et le champ d’étoiles, lui, ne la suit pas. C’est toute la
  différence entre une texture collée sur un quad et un portail. La <b>dérive</b> est à ×1
  par défaut, c’est-à-dire la vitesse réelle de <code>GameTime</code> en jeu : lente.</p>

  <details><summary>void_portal.fsh</summary><pre>__FSH_SRC__</pre></details>
  <details><summary>void_portal.vsh</summary><pre>__VSH_SRC__</pre></details>
</aside>
<main><canvas id="c"></canvas>
  <div class="hud"><code id="fps">—</code><code id="info">—</code></div>
</main>

<script>
const SKY_SRC = "__SKY__", STARS_SRC = "__STARS__";
const VSH = `__VSH__`;
const FSH = `__FSH__`;

/* Le décor : volontairement CLAIR. Un trou noir sur fond noir ne prouve rien. */
const BG_V = `#version 300 es
precision highp float;
layout(location = 0) in vec3 Position;
uniform mat4 MVP;
out vec3 wpos;
void main(){ gl_Position = MVP * vec4(Position,1.0); wpos = Position; }`;
const BG_F = `#version 300 es
precision highp float;
in vec3 wpos; out vec4 o;
void main(){
  float g = mod(floor(wpos.x)+floor(wpos.z),2.0);
  vec3 grass = mix(vec3(0.40,0.50,0.27), vec3(0.48,0.58,0.32), g);
  float gw = mod(floor(wpos.x)+floor(wpos.y),2.0);
  vec3 stone = mix(vec3(0.60,0.60,0.63), vec3(0.69,0.69,0.71), gw);
  o = vec4(wpos.y > 0.02 ? stone : grass, 1.0);
}`;

const cv = document.getElementById('c');
const gl = cv.getContext('webgl2', {antialias:true});
if(!gl){ document.getElementById('info').textContent = 'WebGL 2 indisponible'; }

function sh(t,s){ const o=gl.createShader(t); gl.shaderSource(o,s); gl.compileShader(o);
  if(!gl.getShaderParameter(o,gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(o)); return o; }
function prog(v,f){ const p=gl.createProgram(); gl.attachShader(p,sh(gl.VERTEX_SHADER,v));
  gl.attachShader(p,sh(gl.FRAGMENT_SHADER,f)); gl.linkProgram(p);
  if(!gl.getProgramParameter(p,gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(p)); return p; }
const PORTAL = prog(VSH,FSH), BG = prog(BG_V,BG_F);

function tex(src){ const t=gl.createTexture(); gl.bindTexture(gl.TEXTURE_2D,t);
  gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,1,1,0,gl.RGBA,gl.UNSIGNED_BYTE,new Uint8Array([5,13,37,255]));
  for(const [k,v] of [[gl.TEXTURE_MIN_FILTER,gl.NEAREST],[gl.TEXTURE_MAG_FILTER,gl.NEAREST],
                      [gl.TEXTURE_WRAP_S,gl.REPEAT],[gl.TEXTURE_WRAP_T,gl.REPEAT]])
    gl.texParameteri(gl.TEXTURE_2D,k,v);
  const im=new Image(); im.onload=()=>{ gl.bindTexture(gl.TEXTURE_2D,t);
    gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,gl.RGBA,gl.UNSIGNED_BYTE,im); };
  im.src=src; return t; }
const T_SKY=tex(SKY_SRC), T_STARS=tex(STARS_SRC);

const vbo = gl.createBuffer();
function upload(a){ gl.bindBuffer(gl.ARRAY_BUFFER,vbo);
  gl.bufferData(gl.ARRAY_BUFFER,new Float32Array(a),gl.DYNAMIC_DRAW);
  gl.enableVertexAttribArray(0); gl.vertexAttribPointer(0,3,gl.FLOAT,false,0,0); }

const mul=(a,b)=>{const r=new Array(16);for(let i=0;i<4;i++)for(let j=0;j<4;j++){
  let s=0;for(let k=0;k<4;k++)s+=a[k*4+j]*b[i*4+k];r[i*4+j]=s;}return r;};
const sub=(a,b)=>[a[0]-b[0],a[1]-b[1],a[2]-b[2]];
const dot=(a,b)=>a[0]*b[0]+a[1]*b[1]+a[2]*b[2];
const cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
const norm=a=>{const l=Math.hypot(a[0],a[1],a[2])||1;return[a[0]/l,a[1]/l,a[2]/l];};
const persp=(f,a,n,q)=>{const t=1/Math.tan(f/2);
  return [t/a,0,0,0, 0,t,0,0, 0,0,(q+n)/(n-q),-1, 0,0,2*q*n/(n-q),0];};
const lookAt=(e,c,u)=>{const z=norm(sub(e,c)),x=norm(cross(u,z)),y=cross(z,x);
  return [x[0],y[0],z[0],0, x[1],y[1],z[1],0, x[2],y[2],z[2],0, -dot(x,e),-dot(y,e),-dot(z,e),1];};

/* Les trois fenêtres de VoidPortalFx, à leurs vraies cotes et à leur vraie hauteur.
   L'origine de l'effet est AUX PIEDS de la victime, comme en jeu. */
const PLANS = [
  {name:'panneau-réclame', y:0.46, a:1.08, b:1.08, mode:0},
  {name:'au sol',          y:0.06, a:1.87, b:1.87, mode:1},
  {name:'dans les lèvres', y:1.58, a:0.66, b:1.34, mode:2}];

const DIST=7.2;
let plan=0, yaw=0, pitch=0.17, aim=0, layers=15, warp=1,
    gameTime=0, playing=true, last=0, frames=0, acc=0;

function render(ts){
  const w = cv.clientWidth|0, h = cv.clientHeight|0;
  if(cv.width!==w||cv.height!==h){ cv.width=w; cv.height=h; }
  /* GameTime vanilla : un 24000e par tick, vingt ticks par seconde. La dérive est un
     multiplicateur D'AFFICHAGE, pas une valeur du jeu : à ×1 c'est exactement la
     vitesse réelle, et elle est lente. */
  if(playing && last) gameTime += Math.min(0.05,(ts-last)/1000) * (20/24000) * warp;
  if(last){ acc += ts-last; frames++; if(acc>500){
    document.getElementById('fps').textContent = (1000*frames/acc).toFixed(0)+' i/s';
    acc=0; frames=0; } }
  last = ts;

  const P0=PLANS[plan], TARGET=[0,P0.y,0];
  const asp=w/Math.max(1,h);
  const eye=[TARGET[0]+Math.sin(yaw)*Math.cos(pitch)*DIST,
             Math.max(0.35, TARGET[1]+Math.sin(pitch)*DIST),
             TARGET[2]+Math.cos(yaw)*Math.cos(pitch)*DIST];
  const AIM=[TARGET[0]+Math.cos(yaw)*aim, TARGET[1], TARGET[2]-Math.sin(yaw)*aim];
  const P=persp(1.20,asp,0.05,220), V=lookAt(eye,AIM,[0,1,0]);

  gl.viewport(0,0,w,h);
  gl.clearColor(0.55,0.68,0.92,1); gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);
  gl.enable(gl.DEPTH_TEST); gl.depthFunc(gl.LEQUAL); gl.disable(gl.CULL_FACE); gl.disable(gl.BLEND);

  gl.useProgram(BG);
  gl.uniformMatrix4fv(gl.getUniformLocation(BG,'MVP'),false,new Float32Array(mul(P,V)));
  const R=16, g=[];
  g.push(-R,0,-R, R,0,-R, R,0,R,  -R,0,-R, R,0,R, -R,0,R);
  g.push(-6,0,-5, 6,0,-5, 6,7,-5,  -6,0,-5, 6,7,-5, -6,7,-5);
  upload(g); gl.drawArrays(gl.TRIANGLES,0,g.length/3);

  /* Les deux vecteurs de base, comme dans VoidPortalFx.onRenderLevelStage. */
  const fwd=norm(sub(TARGET,eye));
  let axA, axB;
  if(P0.mode===1){ axA=[1,0,0]; axB=[0,0,1]; }
  /* La fente s'ecarte LATERALEMENT et sa normale est l'axe de l'attaque. Ici la camera
     au repos regarde le long de -z : l'attaque est donc z, le lateral est x, et la fente
     est de face a l'ouverture puis s'ecrase en trait quand on tourne d'un quart. */
  else if(P0.mode===2){ axA=[1,0,0]; axB=[0,1,0]; }
  else { axA=norm(cross(fwd,[0,1,0])); axB=cross(axA,fwd); }

  const d=[], N=22;
  const pt=a=>[TARGET[0]+axA[0]*Math.cos(a)*P0.a+axB[0]*Math.sin(a)*P0.b,
               TARGET[1]+axA[1]*Math.cos(a)*P0.a+axB[1]*Math.sin(a)*P0.b,
               TARGET[2]+axA[2]*Math.cos(a)*P0.a+axB[2]*Math.sin(a)*P0.b];
  for(let s=0;s<N;s++){
    const A=pt(s*2*Math.PI/N), B=pt((s+1)*2*Math.PI/N);
    d.push(TARGET[0],TARGET[1],TARGET[2], A[0],A[1],A[2], B[0],B[1],B[2]);
  }
  gl.useProgram(PORTAL);
  gl.uniformMatrix4fv(gl.getUniformLocation(PORTAL,'ProjMat'),false,new Float32Array(P));
  gl.uniformMatrix4fv(gl.getUniformLocation(PORTAL,'ModelViewMat'),false,new Float32Array(V));
  gl.uniform1f(gl.getUniformLocation(PORTAL,'GameTime'), gameTime % 1.0);
  gl.uniform1i(gl.getUniformLocation(PORTAL,'EndPortalLayers'), layers);
  gl.uniform1i(gl.getUniformLocation(PORTAL,'Sampler0'),0);
  gl.uniform1i(gl.getUniformLocation(PORTAL,'Sampler1'),1);
  gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D,T_SKY);
  gl.activeTexture(gl.TEXTURE1); gl.bindTexture(gl.TEXTURE_2D,T_STARS);
  upload(d); gl.drawArrays(gl.TRIANGLES,0,d.length/3);

  document.getElementById('info').textContent =
    P0.name+' · '+P0.a.toFixed(2).replace('.',',')+' × '+P0.b.toFixed(2).replace('.',',')
    +' bl · '+layers+' couches · GameTime '+(gameTime%1).toFixed(4);
  requestAnimationFrame(render);
}
requestAnimationFrame(render);

/* ---- commandes ---- */
const $ = id => document.getElementById(id);
$('layers').oninput = e => { layers = +e.target.value; $('vlayers').textContent = layers; };
$('yaw').oninput    = e => { yaw = +e.target.value*Math.PI/180; $('vyaw').textContent = e.target.value+'°'; };
$('aim').oninput    = e => { aim = +e.target.value/10; $('vaim').textContent = aim.toFixed(1).replace('.',','); };
$('warp').oninput   = e => { warp = +e.target.value; $('vwarp').textContent = '×'+warp; };
$('play').onclick   = () => { playing = !playing; $('play').textContent = playing ? 'Pause' : 'Reprendre'; };
document.querySelectorAll('#plans .variant').forEach(b => b.onclick = () => {
  plan = +b.dataset.plan;
  document.querySelectorAll('#plans .variant').forEach(x => x.classList.toggle('active', x===b));
  pitch = plan===1 ? 0.42 : 0.17;            /* une mare se regarde d'un peu plus haut */
});
$('reset').onclick  = () => { yaw=0; aim=0; pitch=(plan===1?0.42:0.17); layers=15; warp=1;
  for(const [k,v] of [['yaw',0],['aim',0],['layers',15],['warp',1]]) $(k).value=v;
  $('vyaw').textContent='0°'; $('vaim').textContent='0,0';
  $('vlayers').textContent='15'; $('vwarp').textContent='×1'; };

let drag=null;
cv.addEventListener('pointerdown', e => { drag=[e.clientX,e.clientY]; cv.setPointerCapture(e.pointerId); });
cv.addEventListener('pointerup',   () => { drag=null; });
cv.addEventListener('pointermove', e => { if(!drag) return;
  yaw   -= (e.clientX-drag[0])*0.006;
  pitch  = Math.max(-0.3, Math.min(1.25, pitch + (e.clientY-drag[1])*0.004));
  drag=[e.clientX,e.clientY];
  $('yaw').value = Math.round(((yaw*180/Math.PI)%360+540)%360-180);
  $('vyaw').textContent = $('yaw').value+'°'; });

/* ---- nuancier : relu dans le fsh embarque, donc jamais desynchronise ---- */
const cols = [...FSH.matchAll(/vec3\(([\d.]+), ([\d.]+), ([\d.]+)\)/g)].slice(0,16)
  .map(m => '#'+[1,2,3].map(i => Math.round(Math.min(1,+m[i]*1.9)*255).toString(16).padStart(2,'0')).join(''));
$('swatch').innerHTML = cols.map(c => '<i style="--c:'+c+'"></i>').join('');
</script></body></html>
"""


if __name__ == "__main__":
    sys.exit(main())
