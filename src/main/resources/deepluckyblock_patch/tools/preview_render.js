/* ===== Rendu ===== */
const SUBS=['6 relais · ionisation · fulgurites','30 blocs de chute · aura de 6 blocs','nuage à 8 blocs · mitraille · décharge'];
const DUR=[130,250,170], NAMES=['Arc fulgurant en chaîne','Marteau de Thor',"Appel de l'orage"];
const BEATS=[
 [['0 → 13','ionisation : 14 filaments montent, le sol se charge'],
  ['13 → 21','le trait maître tombe du ciel — 22 blocs, 5 ramifications'],
  ['21 → 39','impact : flash, couronne de plasma, 8 serpents rampants'],
  ['39 → 99','la chaîne : 6 relais, chacun part de la victime précédente'],
  ['99 → 130','fulgurites : le sol garde une trace, l’air crépite']],
 [['0 → 8','l’appel : le marteau se matérialise à <b>30 blocs</b> ; l’air haut se charge'],
  ['8 → 50','<b>la chute libre</b> : 42 ticks, accélération constante, 1,43 bloc/tick à l’arrivée'],
  ['50','<b>l’impact</b> : flash, et l’onde balaie les 6 blocs de l’aura'],
  ['50 → 70','le retrait : le marteau remonte et s’efface'],
  ['50 → 250','<b>le sol sous tension</b> : 10 s, <b>6 blocs de rayon</b>, sans contour']],
 [['0 → 27','formation : les bouffées convergent, le nuage se noue à <b>8 blocs</b>'],
  ['34 → 105','mitraille : <b>5 éclairs</b> tombent l’un après l’autre, chacun sa brûlure'],
  ['105 → 133','charge : le nuage se contracte de 30 %, une colonne monte à sa rencontre'],
  ['133 → 153','la décharge : <b>7 traits serrés</b> tombent ensemble'],
  ['143 → 170','onde : l’anneau de choc s’ouvre jusqu’à <b>10 blocs</b>']]];
const GP=[
 "Frappe principale au tick 20, puis repérage de <b>6 relais</b> (rayon 7, jamais un joueur) — chaque saut retire 18 % de puissance. <b>Cible seule :</b> l’énergie des six relais lui revient en trois décharges.",
 "Au tick 50, un carré de <b>3×3 blocs</b> centré sur la cible prend <b>2× les dégâts de l’arme</b> — enchantements et effets d’attaque compris, puisque la valeur vient de l’événement de dégâts. Le sol reste ensuite <b>électrifié 10 s</b> sur un <b>disque de 6 blocs de rayon</b> : tout mob <i>ou joueur</i>, l’attaquant excepté, prend une décharge chaque seconde, <b>décroissante du centre vers le bord</b> — plein tarif au point de chute, 15 % à la limite. Pas de contour : la zone s’éteint par perte de densité. Une arme de <b>contrôle de terrain</b>.",
 "<b>5 frappes</b> de 30 % entre les ticks 34 et 105 (rayon 2,6 sous le nuage), puis la <b>décharge au tick 140</b> : <code>×2,60</code> sur qui se tient au centre, et <code>×0,22</code> répercutés sur <b>10 blocs</b> de rayon. Le nuage reste où il s’est formé : sortir de la zone, c’est éviter le gros."];

const cv=document.getElementById('scene'), ctx=cv.getContext('2d',{alpha:false});
let W=0,H=0,DPR=1, variant=0, t=0, playing=true, salt=7331;
let camYaw=0.62, camPitch=0.30, zoom=1, drag=null;

function resize(){DPR=Math.min(2,window.devicePixelRatio||1);
 const r=cv.getBoundingClientRect(); W=cv.width=Math.max(1,r.width*DPR|0); H=cv.height=Math.max(1,r.height*DPR|0);}
new ResizeObserver(resize).observe(cv); resize();

const FACES=[
 {n:[ 1,0,0],v:[[1,-1,-1],[1,1,-1],[1,1,1],[1,-1,1]]},
 {n:[-1,0,0],v:[[-1,-1,-1],[-1,-1,1],[-1,1,1],[-1,1,-1]]},
 {n:[0, 1,0],v:[[-1,1,-1],[-1,1,1],[1,1,1],[1,1,-1]]},
 {n:[0,-1,0],v:[[-1,-1,-1],[1,-1,-1],[1,-1,1],[-1,-1,1]]},
 {n:[0,0, 1],v:[[-1,-1,1],[1,-1,1],[1,1,1],[-1,1,1]]},
 {n:[0,0,-1],v:[[-1,-1,-1],[-1,1,-1],[1,1,-1],[1,-1,-1]]}];

/* Rotation locale : roll (autour de Z) → pitch (autour de X) → yaw (autour de Y).
   C'est la convention de emitVoxelBoxRotated : l'axe X local vaut
   (cos roll·cos yaw, sin roll, −cos roll·sin yaw). */
function localRot(x,y,z,v){
 const cr=Math.cos(v.roll||0),sr=Math.sin(v.roll||0),
       cp=Math.cos(v.pitch||0),sp=Math.sin(v.pitch||0),
       cyw=Math.cos(v.yaw||0),syw=Math.sin(v.yaw||0);
 const x1=x*cr-y*sr, y1=x*sr+y*cr, z1=z;
 const y2=y1*cp-z1*sp, z2=y1*sp+z1*cp;
 return [x1*cyw+z2*syw, y2, -x1*syw+z2*cyw];
}
let CY=1,SY=0,CP=1,SP=0,SCALE=60;
const VIEW_CENTER_BY=[1.05,2.60,4.20];   // l'orage se lit de 0 à 9 blocs de haut
/* Le marteau tombe de 30 blocs et laisse une aura de 12 blocs de large : aucun cadrage fixe
   ne peut servir les deux. La vue s'ouvre donc sur toute la colonne pendant la chute, puis
   se resserre sur le sol une fois le coup porte — exactement ce que ferait une camera. */
function smoothstep(x){x=Math.max(0,Math.min(1,x));return x*x*(3-2*x);}
/* L'arc fulgurant se joue maintenant sur toute la hauteur : le ciel se charge entre 14 et
   31 blocs, puis la chaine court au sol. Un cadrage unique sacrifierait l'un ou l'autre, donc
   la vue s'ouvre sur l'orage puis redescend suivre les relais. */
function boltShot(tt){const k=smoothstep((tt-0.215)/0.125);
 return{center:10.5+(1.40-10.5)*k, span:31.0+(14.0-31.0)*k};}
function hammerShot(tt){
 /* On rejoue ici la meme courbe de chute que le mod, pour que la vue SUIVE le marteau au
    lieu de basculer d'un plan a l'autre : un raccord sec laisse l'impact hors cadre. */
 const GY=-0.42, REST=GY+0.64*1.30, sky=GY+30.0;
 const fall=Math.max(0,Math.min(1,(tt-0.030)/0.170)), ease=fall*fall;
 const hy=sky-(sky-REST)*ease;              // hauteur courante du marteau
 const k=smoothstep((hy-1.6)/12.0);         // 1 tant qu'il est haut, 0 une fois pose
 return{center:1.10+hy*0.55*k, span:15.5+24.5*k};}
let VCY=1.05;
function updateCam(){CY=Math.cos(camYaw);SY=Math.sin(camYaw);CP=Math.cos(camPitch);SP=Math.sin(camPitch);
 let span;
 if(variant===1){const s=hammerShot(t);span=s.span;VCY=s.center;}
 else if(variant===0){const s=boltShot(t);span=s.span;VCY=s.center;}
 else{span=23.0;VCY=VIEW_CENTER_BY[variant];}
 SCALE=Math.min(W/span,H/(span*0.80))*zoom;}
function project(x,y,z){const yc=y-VCY,rx=x*CY-z*SY,rz=x*SY+z*CY,ry=yc*CP-rz*SP;
 return[W*0.5+rx*SCALE,H*0.56-ry*SCALE,yc*SP+rz*CP];}
function camNormal(n){const rx=n[0]*CY-n[2]*SY,rz=n[0]*SY+n[2]*CY;return[rx,n[1]*CP-rz*SP,n[1]*SP+rz*CP];}

const cache=new Map();
function rgbOf(n){let c=cache.get(n);if(!c){c=[n>>16&255,n>>8&255,n&255];cache.set(n,c)}return c}
function shade(n,k,a){const c=rgbOf(n);
 return'rgba('+Math.min(255,c[0]*k|0)+','+Math.min(255,c[1]*k|0)+','+Math.min(255,c[2]*k|0)+','+a+')'}

function ground(){
 ctx.strokeStyle='#2a2058';ctx.lineWidth=DPR;
 const R=6.2,N=12;
 for(let i=0;i<=N;i++){const u=-R+2*R*i/N;
  let p=project(u,-0.47,-R),q=project(u,-0.47,R);
  ctx.beginPath();ctx.moveTo(p[0],p[1]);ctx.lineTo(q[0],q[1]);ctx.stroke();
  p=project(-R,-0.47,u);q=project(R,-0.47,u);
  ctx.beginPath();ctx.moveTo(p[0],p[1]);ctx.lineTo(q[0],q[1]);ctx.stroke();}
}
function glow(vox){ctx.save();ctx.globalCompositeOperation='lighter';
 for(let i=0;i<vox.length;i+=9){const v=vox[i],c=rgbOf(v.rgb),p=project(v.x,v.y,v.z);
  const r=Math.max(7*DPR,Math.min(34*DPR,Math.max(v.hx,v.hy,v.hz)*SCALE*3.4));
  const g=ctx.createRadialGradient(p[0],p[1],0,p[0],p[1],r);
  g.addColorStop(0,'rgba('+c[0]+','+c[1]+','+c[2]+',0.085)');
  g.addColorStop(1,'rgba('+c[0]+','+c[1]+','+c[2]+',0)');
  ctx.fillStyle=g;ctx.fillRect(p[0]-r,p[1]-r,r*2,r*2)}ctx.restore()}

function render(){
 updateCam();
 ctx.fillStyle='#05040d';ctx.fillRect(0,0,W,H);
 ground();
 const vox=buildThunder(variant,t,salt);
 glow(vox);
 // Peintre PAR FACE, et non par cube. Les trois passes concentriques d'un trait d'eclair
 // (gaine violette large et plate, halo, coeur blanc fin et haut) partagent exactement le
 // meme centre : un tri par cube les laisse a egalite, l'ordre d'insertion l'emporte et la
 // gaine recouvre toujours le coeur. Trier les faces individuellement rend au coeur blanc
 // les parties par lesquelles il depasse — ce que le tampon de profondeur fait en jeu.
 const quads=[];
 ctx.lineJoin='round';
 for(const v of vox){
  const px=Math.max(v.hx,v.hy,v.hz)*SCALE*2, al=v.a/255;
  if(px<1.5){const p=project(v.x,v.y,v.z);
   quads.push({d:p[2],pt:p,fill:shade(v.rgb,1.05,al)});continue}
  for(const f of FACES){
   const ln=localRot(f.n[0],f.n[1],f.n[2],v), n=camNormal(ln);
   if(n[2]<=0.015)continue;
   const pts=[];let d=0;
   for(const q of f.v){const o=localRot(q[0]*v.hx,q[1]*v.hy,q[2]*v.hz,v);
    const p=project(v.x+o[0],v.y+o[1],v.z+o[2]);pts.push(p);d+=p[2]}
   // Eclairage : une face tournee vers la lumiere est plus claire, le cube garde son volume.
   const k=0.60+0.55*Math.max(0,ln[1]*0.55+ln[0]*0.30+n[2]*0.42);
   quads.push({d:d*0.25,pts:pts,fill:shade(v.rgb,k,al)});
  }
 }
 quads.sort((a,b)=>a.d-b.d);
 for(const q of quads){
  ctx.fillStyle=q.fill;
  if(q.pt){ctx.fillRect(q.pt[0],q.pt[1],1.7,1.7);continue}
  ctx.beginPath();ctx.moveTo(q.pts[0][0],q.pts[0][1]);
  for(let i=1;i<4;i++)ctx.lineTo(q.pts[i][0],q.pts[i][1]);
  ctx.closePath();ctx.fill();
 }
 document.getElementById('baseCount').textContent=vox.length;
 document.getElementById('visibleCount').textContent=vox.length+' voxels visibles';
 setPhase();
}

let last=performance.now();
function loop(now){
 const dt=Math.min(0.05,(now-last)/1000);last=now;
 if(playing){t+=dt/(DUR[variant]/20);if(t>=1)t=0;
  document.getElementById('timeline').value=Math.round(t*1000);}
 render();requestAnimationFrame(loop);
}

/* Le gabarit affiche UNE ligne de phase, mise a jour en continu. On retrouve la phase
   courante en relisant les bornes de la chronologie, qui sont deja ecrites en ticks. */
function beatAt(tick){
 const list=BEATS[variant]; let best=0;
 for(let i=0;i<list.length;i++){
  const m=list[i][0].match(/\d+/g); if(!m) continue;
  if(tick>=+m[0]) best=i;
 }
 return best;
}
function setPhase(){
 const tick=Math.round(t*DUR[variant]), i=beatAt(tick);
 document.getElementById('phase').innerHTML=
   String(i+1).padStart(2,'0')+' — '+BEATS[variant][i][1]
   +' <span style="opacity:.55">· tick '+tick+'</span>';
}

function syncPanel(){
 document.getElementById('variantName').textContent=NAMES[variant];
 document.getElementById('gpRefs').innerHTML=GP[variant];
 document.getElementById('variantSub').textContent=SUBS[variant];
 document.getElementById('duration').textContent=
   (DUR[variant]/20).toFixed(1).replace('.',',')+' s';
 document.getElementById('beatsRefs').innerHTML='<b>Chronologie :</b> '
   +BEATS[variant].map(b=>b[0]+' — '+b[1]).join(' · ');
 document.querySelectorAll('.variant').forEach(b=>b.classList.toggle('active',+b.dataset.variant===variant));
}
document.querySelectorAll('.variant').forEach(b=>b.onclick=()=>{
 variant=+b.dataset.variant;t=0;syncPanel();});
function setPlay(on){playing=on;
 document.getElementById('play').textContent=on?'Pause':'Lire';}
document.getElementById('play').onclick=()=>setPlay(!playing);
document.getElementById('replay').onclick=()=>{t=0;setPlay(true);};
addEventListener('keydown',e=>{
 if(e.code==='Space'){e.preventDefault();setPlay(!playing);}
 if(e.key&&e.key.toLowerCase()==='r'){t=0;setPlay(true);}});
document.getElementById('timeline').oninput=e=>{t=+e.target.value/1000;playing=false;
 setPlay(false);};
document.getElementById('dice').onclick=()=>{salt=(Math.random()*100000)|0;
 document.getElementById('seedStat').textContent=salt;t=0;};
cv.addEventListener('pointerdown',e=>{drag={x:e.clientX,y:e.clientY};cv.setPointerCapture(e.pointerId)});
cv.addEventListener('pointermove',e=>{if(!drag)return;
 camYaw+=(e.clientX-drag.x)*0.008;
 camPitch=Math.max(-0.25,Math.min(1.25,camPitch+(e.clientY-drag.y)*0.006));
 drag={x:e.clientX,y:e.clientY}});
cv.addEventListener('pointerup',()=>drag=null);
cv.addEventListener('pointercancel',()=>drag=null);
cv.addEventListener('wheel',e=>{e.preventDefault();
 zoom=Math.max(0.45,Math.min(3.2,zoom*(e.deltaY>0?0.92:1.08)))},{passive:false});
syncPanel();requestAnimationFrame(loop);
