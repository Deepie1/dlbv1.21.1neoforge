#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""scanviewcheck -- verrous sur la visionneuse 3D du scan (tools/scanview.py).

POURQUOI CE BANC EXISTE
    La page livree en T480 affichait « Fragment shader is not compiled. » et rien
    d'autre : un rectangle noir. Le harness de l'epoque executait pourtant tout le
    JS sous node... avec un faux contexte WebGL dont getShaderParameter renvoyait
    toujours true. Il validait donc du GLSL que PERSONNE n'avait compile.

    La faute : en GLSL ES 3.00 (WebGL2) un fragment shader n'a AUCUNE precision par
    defaut pour float. « precision highp float; » doit preceder TOUTE declaration
    employant un type flottant, y compris la sortie « out vec4 oC; ». Placee apres,
    le compilateur refuse :
        'float' : type requires declaration of default precision qualifier
    Le vertex shader, lui, a une precision par defaut : il compilait, d'ou un
    diagnostic trompeur si l'on ne regarde que « ca marche chez moi ».

    Ce banc refait donc le chemin complet : il GENERE la page, en EXTRAIT les
    sources exactes remises a gl.shaderSource (en simulant WebGL2 puis WebGL1), et
    les passe au crible. Si un vrai glslangValidator est present il l'utilise comme
    oracle ; sinon il applique la regle d'ordre, qui a ete calibree contre cet
    oracle sur des shaders connus-bons et connus-mauvais.

USAGE
    python3 tools/scanviewcheck.py [dossier_de_rapports]
    GLSLANG=/chemin/glslangValidator python3 tools/scanviewcheck.py   (oracle reel)
"""

import base64
import json
import os
import re
import subprocess
import sys
import tempfile

RACINE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SCANVIEW = os.path.join(RACINE, 'tools', 'scanview.py')

# Declarations qui exigent une precision par defaut deja posee.
DECL = re.compile(
    r'(?:^|[;\n{])\s*(?:(?:out|in|varying|uniform|attribute)\s+)?'
    r'(?:float|vec2|vec3|vec4|mat2|mat3|mat4)\s+[A-Za-z_]')
PRECISION = re.compile(r'precision\s+(?:highp|mediump|lowp)\s+float\s*;')


def ordre_precision(src):
    """Renvoie None si l'ordre est bon, sinon le texte du probleme.

    Regle : dans un fragment shader GLSL ES 3.00, la precision par defaut de float
    doit etre posee avant la premiere declaration qui utilise un type flottant.
    """
    if '#version 300 es' not in src:
        return None  # GLSL ES 1.00 : le fragment shader a une precision par defaut
    mp = PRECISION.search(src)
    md = DECL.search(src)
    if md is None:
        return None
    if mp is None:
        return ('aucun « precision ... float; » alors que le shader declare un type '
                'flottant a l\'offset %d' % md.start())
    if mp.start() > md.start():
        extrait = src[md.start():md.start() + 40].strip().replace('\n', ' ')
        return ('« precision ... float; » arrive a l\'offset %d, APRES la declaration '
                '« %s » a l\'offset %d' % (mp.start(), extrait, md.start()))
    return None


def _node():
    for c in ('node', 'nodejs'):
        try:
            subprocess.run([c, '--version'], capture_output=True, timeout=30, check=True)
            return c
        except Exception:
            continue
    return None


CAPTURE_JS = r'''
const fs=require('fs');
const h=fs.readFileSync(process.argv[2],'utf8');
const js=h.slice(h.indexOf('<script>')+8,h.lastIndexOf('</script>'));
const out={};
// T484 : trois passes. Les deux premieres rendent normalement (WebGL2 puis
// WebGL1). La TROISIEME simule le defaut signale par l'utilisateur : le pilote
// accepte tout mais ne peint RIEN. Un harnais qui ne sait pas produire un
// canevas noir ne pourra jamais prouver que la page s'en releve.
// T485 : quatre passes. Deux rendus normaux, puis les DEUX pannes distinctes --
// elles n'appellent pas la meme reponse. Signal MOU (le pilote accepte tout mais
// ne peint rien) : la 3D doit RESTER, avec un avertissement. Panne DURE (aucun
// contexte) : il n'y a plus de 3D a preserver, la carte 2D doit prendre le relais.
const PASSES=[{v2:true,peint:true,suf:'2'},
              {v2:false,peint:true,suf:'1'},
              {v2:true,peint:false,suf:'N'},
              {v2:true,peint:true,sansGL:true,suf:'Z'}];
for(const cfg of PASSES){
  const v2=cfg.v2, vus=[];
  const geo={pos:null,mvp:null,vex:1,y0:0};
  const GL=new Proxy({},{get(_,p){
    if(p==='VERTEX_SHADER')return 35633; if(p==='FRAGMENT_SHADER')return 35632;
    if(p==='COMPILE_STATUS'||p==='LINK_STATUS')return 1;
    if(p==='RGBA')return 6408; if(p==='UNSIGNED_BYTE')return 5121;
    if(p==='getShaderParameter'||p==='getProgramParameter')return ()=>true;
    if(p==='getError')return ()=>0;
    if(p==='getExtension')return ()=>({});
    if(p==='createShader')return t=>({t});
    if(p==='shaderSource')return (sh,src)=>{vus.push([sh.t,src]);};
    if(p==='getUniformLocation')return (prog,nom)=>({n:nom});
    if(p==='createBuffer'||p==='createProgram')return ()=>({});
    // Le coeur de la passe N : on rend un canevas entierement noir.
    // T486 : on retient la matrice et les sommets pour verifier, par le calcul,
    // que la camera cadre vraiment le terrain. Un ecran noir peut survenir sans
    // la moindre erreur GL si la matrice projette tout hors du volume de vue.
    if(p==='uniformMatrix4fv')return (loc,tr,m)=>{ if(loc&&loc.n==='uMVP'&&!geo.mvp) geo.mvp=Array.from(m); };
    if(p==='uniform1f')return (loc,v)=>{ if(loc&&loc.n==='uVex')geo.vex=v; if(loc&&loc.n==='uY0')geo.y0=v; };
    if(p==='bufferData')return (cible,data)=>{
      if(cible===34962 && data && data.length>100000 && !geo.pos) geo.pos=data; };
    if(p==='ARRAY_BUFFER')return 34962;
    if(p==='readPixels')return (x,y,w,hh,f,t,px)=>{
      const val=cfg.peint?200:0;
      for(let i=0;i<px.length;i++)px[i]=(i%4===3)?255:val;};
    return ()=>{};
  }});
  const noeuds={};
  // Chaque canevas recoit SON contexte 2D, sinon on ne distingue pas la coupe
  // verticale de la carte de secours.
  const d2={};
  function mk2d(tag){
    const b=d2[tag]||(d2[tag]={n:{},txt:[]});
    return new Proxy({},{get(_,p){
      if(p==='canvas')return null;
      if(p==='createImageData')return (w,hh)=>({width:w,height:hh,
        data:new Uint8ClampedArray(Math.max(1,w*hh*4))});
      if(p==='setTransform'||p==='save'||p==='restore')return ()=>{};
      if(p==='fillText')return (t)=>{ b.n.fillText=(b.n.fillText||0)+1; b.txt.push(String(t)); };
      return (...a)=>{ b.n[p]=(b.n[p]||0)+1; };
    },set(){return true;}});
  }
  function elem(id){ if(noeuds[id])return noeuds[id];
    const n={id,children:[],style:{},className:'',checked:true,value:'',textContent:'',innerHTML:'',
      appendChild(c){this.children.push(c);},addEventListener(){},
      classList:{contains:()=>false,add(){},remove(){}},
      getBoundingClientRect:()=>({left:0,top:0,width:900,height:600}),
      getContext:k=>(k==='2d')?mk2d(id)
                   :(cfg.sansGL?null:(((k==='webgl2')===v2)?GL:null)),
      cloneNode(){return elem(id+'_c');},
      parentNode:{replaceChild(){}},
      clientWidth:900,clientHeight:600,width:900,height:600};
    // Le faux DOM doit refleter l'etat INITIAL du vrai HTML : ces deux noeuds
    // naissent masques (class="off"). Les naitre visibles rendrait impossible
    // de distinguer "jamais touche" de "reellement affiche".
    if(id==='carteCv'||id==='diagGl')n.className='off';
    if(id==='mode')n.value='alt'; if(id==='vex')n.value='3';
    if(id==='cpAxe')n.value='z'; if(id==='cpPos')n.value='0';
    noeuds[id]=n; return n;}
  global.document={getElementById:elem,createElement:t=>({children:[],style:{},innerHTML:'',
    width:0,height:0,appendChild(){},getContext:k=>mk2d('tmp'),
    set onclick(f){},get onclick(){return null;}})};
  global.window={addEventListener(){},devicePixelRatio:1};
  global.atob=b=>Buffer.from(b,'base64').toString('binary');
  let boom=null;
  try{ eval(js); }catch(e){ boom=e.message; }
  const suf=cfg.suf;
  for(const [t,src] of vus) out[(t===35633?'vs':'fs')+suf]=src;
  out['exception'+suf]=boom;
  out['erreur'+suf]=(noeuds['err']&&noeuds['err'].style.display==='flex')?noeuds['err'].textContent:null;
  out['etapes'+suf]=noeuds['steps']?noeuds['steps'].children.length:0;
  out['coupe'+suf]=(d2['cpCv']||{n:{}}).n; out['coupetxt'+suf]=(d2['cpCv']||{txt:[]}).txt;
  out['carte'+suf]=(d2['carteCv']||{n:{}}).n;
  out['cartetxt'+suf]=(d2['carteCv']||{txt:[]}).txt;
  out['tampon'+suf]=(d2['tmp']||{n:{}}).n;
  out['carteOn'+suf]=noeuds['carteCv']?noeuds['carteCv'].className:'(absent)';
  out['diag'+suf]=noeuds['diagGl']?String(noeuds['diagGl'].textContent):'';
  out['diagOn'+suf]=noeuds['diagGl']?noeuds['diagGl'].className:'(absent)';
  out['avisOn'+suf]=noeuds['avisGl']?noeuds['avisGl'].className:'(absent)';
  // fraction des sommets qui tombent reellement dans le volume de vue
  if(geo.pos&&geo.mvp){
    let dans=0,tot=0;
    for(let i=0;i<geo.pos.length;i+=3*97){
      const x=geo.pos[i], y=(geo.pos[i+1]-geo.y0)*geo.vex, z=geo.pos[i+2], o=[0,0,0,0];
      for(let r=0;r<4;r++) o[r]=geo.mvp[r]*x+geo.mvp[4+r]*y+geo.mvp[8+r]*z+geo.mvp[12+r];
      tot++;
      if(o[3]>0 && Math.abs(o[0])<=o[3] && Math.abs(o[1])<=o[3] && o[2]>=-o[3] && o[2]<=o[3]) dans++;
    }
    out['cadrage'+suf]=tot?dans/tot:0; out['cadrageN'+suf]=tot;
  } else { out['cadrage'+suf]=null; }
  out['fatal'+suf]=(noeuds['err']&&noeuds['err'].style.display==='flex')
                   ?String(noeuds['err'].textContent):null;
}
process.stdout.write(JSON.stringify(out));
'''


def controler(dossier):
    echecs = []
    if not os.path.isfile(SCANVIEW):
        print('ECHEC  tools/scanview.py est introuvable')
        return 1

    # --- VERROU 1 : le generateur compile et tourne.
    try:
        subprocess.run([sys.executable, '-m', 'py_compile', SCANVIEW],
                       capture_output=True, timeout=120, check=True)
    except Exception as exc:
        print('ECHEC  VERROU 1 : tools/scanview.py ne compile pas (%s)' % exc)
        return 1

    tmp = tempfile.mkdtemp(prefix='scanviewcheck')
    page = os.path.join(tmp, 'p.html')
    try:
        r = subprocess.run([sys.executable, SCANVIEW, dossier, '-o', page],
                           capture_output=True, timeout=900)
    except Exception as exc:
        print('ECHEC  VERROU 1 : la generation a leve %s' % exc)
        return 1
    if r.returncode != 0 or not os.path.isfile(page):
        print('ECHEC  VERROU 1 : la generation a echoue\n%s'
              % r.stderr.decode('utf-8', 'replace')[:800])
        return 1
    html = open(page, encoding='utf-8').read()

    # --- VERROU 2 : page autonome. Un CDN rend la page inutilisable hors ligne et
    #     dans un apercu sans reseau -- or elle est faite pour etre auto-hebergee.
    for motif in ('src="http', "src='http", 'href="http', "href='http"):
        if motif in html:
            echecs.append('VERROU 2 : la page reference une ressource externe (%s) -- '
                          'elle doit etre autonome' % motif)

    # --- VERROU 3 : le panneau de selection d'etape est a gauche et liste les etapes.
    if 'Etape du pipeline' not in html or 'id="steps"' not in html:
        echecs.append('VERROU 3 : le panneau de selection d\'etape a disparu')

    # --- VERROU 4 : une colonne non mesuree ne doit jamais passer pour un trou.
    if "n'est pas un trou du terrain" not in html:
        echecs.append('VERROU 4 : la page ne distingue plus une colonne non mesuree '
                      'd\'un trou du terrain -- c\'est la confusion qui a coute un '
                      'tour d\'enquete entier')

    node = _node()
    if node is None:
        echecs.append('VERROU 5 : node est introuvable, les shaders n\'ont pas pu etre '
                      'extraits ni compilés -- ce banc ne prouve alors presque rien')
    else:
        js = os.path.join(tmp, 'capte.js')
        with open(js, 'w', encoding='utf-8') as fh:
            fh.write(CAPTURE_JS)
        r = subprocess.run([node, js, page], capture_output=True, timeout=600)
        if r.returncode != 0:
            echecs.append('VERROU 5 : extraction des shaders impossible : %s'
                          % r.stderr.decode('utf-8', 'replace')[:400])
        else:
            import json
            info = json.loads(r.stdout.decode('utf-8'))

            # --- VERROU 5 : la page se charge sans exception, dans les DEUX chemins.
            for suf, nom in (('2', 'WebGL2'), ('1', 'WebGL1')):
                if info.get('exception' + suf):
                    echecs.append('VERROU 5 : la page leve une exception en %s : %s'
                                  % (nom, info['exception' + suf]))
                if info.get('erreur' + suf):
                    echecs.append('VERROU 5 : la page affiche un ecran d\'erreur en %s : %s'
                                  % (nom, info['erreur' + suf]))
                if info.get('etapes' + suf, 0) < 1:
                    echecs.append('VERROU 5 : aucun bouton d\'etape construit en %s' % nom)

            # --- VERROU 6 : les shaders reellement remis au pilote doivent compiler.
            glslang = os.environ.get('GLSLANG')
            if glslang and not os.path.isfile(glslang):
                glslang = None
            for cle in ('vs2', 'fs2', 'vs1', 'fs1'):
                src = info.get(cle)
                if not src:
                    echecs.append('VERROU 6 : shader %s absent -- la page ne le remet '
                                  'plus au pilote' % cle)
                    continue
                if cle.startswith('fs'):
                    pb = ordre_precision(src)
                    if pb:
                        echecs.append(
                            'VERROU 6 : fragment shader %s invalide en GLSL ES 3.00 : %s. '
                            'Le pilote repondra « Fragment shader is not compiled » et '
                            'la page restera noire.' % (cle, pb))
                if glslang:
                    ext = '.vert' if cle.startswith('vs') else '.frag'
                    f = os.path.join(tmp, cle + ext)
                    with open(f, 'w', encoding='utf-8') as fh:
                        fh.write(src)
                    g = subprocess.run([glslang, f], capture_output=True, timeout=120)
                    sortie = (g.stdout + g.stderr).decode('utf-8', 'replace')
                    if 'ERROR' in sortie:
                        prem = [l for l in sortie.splitlines() if 'ERROR' in l][:2]
                        echecs.append('VERROU 6 : glslangValidator refuse %s : %s'
                                      % (cle, ' / '.join(prem)))

            # --- VERROU 7 : un refus de shader ne doit pas finir en rectangle noir.
            if 'cloneNode' not in html or 'construire(' not in html:
                echecs.append('VERROU 7 : le repli WebGL1 a disparu -- un refus de '
                              'shader redonnerait un ecran noir muet')

            # --- VERROU 8 : LA COUPE VERTICALE SE DESSINE VRAIMENT.
            # Une vue du dessus ne montre que le sommet des colonnes : elle est
            # aveugle a ce qui se passe dans l'epaisseur, donc au terrain reste
            # DANS la structure. La coupe est la seule vue qui le revele ; une
            # coupe presente dans le HTML mais qui ne trace rien ne vaut rien.
            if 'id="cpCv"' not in html:
                echecs.append('VERROU 8 : la coupe verticale a disparu de la page')
            for suf, nom in (('2', 'WebGL2'), ('1', 'WebGL1')):
                d2 = info.get('coupe' + suf) or {}
                txt = info.get('coupetxt' + suf) or []
                netapes = info.get('etapes' + suf, 0)
                traces = d2.get('stroke', 0)
                # Il faut au MOINS un trait par etape : sinon le panneau n'affiche
                # qu'une ligne de reference et le joueur ne compare rien. Un seuil
                # a 2 serait satisfait par la seule ligne de sol.
                if traces < max(2, netapes):
                    echecs.append('VERROU 8 : la coupe ne trace pas un profil par '
                                  'etape en %s (%d traits pour %d etapes)'
                                  % (nom, traces, netapes))
                if d2.get('lineTo', 0) < 200:
                    echecs.append('VERROU 8 : les profils de la coupe sont vides en %s '
                                  '(%d segments) -- rien n\'est suivi le long de la ligne'
                                  % (nom, d2.get('lineTo', 0)))
                # La graduation doit nommer le sol de reference : sans altitude
                # ecrite, une coupe n'est qu'un gribouillis sans echelle.
                if not any(t.startswith('Y=') for t in txt):
                    echecs.append('VERROU 8 : la coupe n\'affiche aucune altitude en %s '
                                  '-- elle est sans echelle' % nom)

            # --- VERROU 9 : une panne ne doit jamais etre muette, et la 3D ne doit
        # JAMAIS etre escamotee sur un simple soupcon.
        # RETARGETE (T485). La version precedente EXIGEAIT le basculement
        # automatique en 2D des que les pixels semblaient vides. Resultat chez
        # l'utilisateur : une page 2D a la place de la vue 3D manipulable qu'il
        # demandait, sur la foi d'un test de pixels faillible. On distingue
        # desormais les deux pannes, et ce qui etait protege avant reste protege :
        # la panne est toujours signalee et le diagnostic toujours disponible.
        diagN = (info.get('diagN') or '')
        # (a) SIGNAL MOU : contexte present, rien de peint -> la 3D reste.
        if info.get('carteOnN') == '':
            echecs.append('VERROU 9 : la carte 2D s\'impose sur un simple soupcon de '
                          'pixels vides -- la vue 3D demandee est escamotee')
        if info.get('avisOnN') != '':
            echecs.append('VERROU 9 : aucun avertissement visible quand la 3D ne peint '
                          'rien -- la panne reste muette')
        if 'PIXELS PEINTS' not in diagN or 'AUCUN' not in diagN:
            echecs.append('VERROU 9 : le diagnostic ne dit pas que rien n\'a ete peint '
                          '(%r)' % diagN[:120])
        # (b) PANNE DURE : aucun contexte -> il n'y a plus de 3D a preserver, la
        # carte 2D doit prendre le relais, remplie et visible.
        carteZ = info.get('carteZ') or {}
        tamponZ = info.get('tamponZ') or {}
        if not info.get('fatalZ'):
            echecs.append('VERROU 9 : aucun message quand le navigateur ne donne aucun '
                          'contexte WebGL')
        if min(carteZ.get('drawImage', 0), tamponZ.get('putImageData', 0)) < 1:
            echecs.append('VERROU 9 : sans contexte WebGL la carte 2D n\'est pas remplie '
                          '(putImageData=%d, drawImage=%d) -- la page ne montre aucune '
                          'donnee' % (tamponZ.get('putImageData', 0),
                                      carteZ.get('drawImage', 0)))
        if info.get('carteZ') is None or info.get('carteOnZ') == 'off':
            echecs.append('VERROU 9 : sans contexte WebGL la carte 2D reste masquee')
        # (c) l'autre sens : quand la 3D peint, ni repli ni avertissement.
        if info.get('carteOn2') == '':
            echecs.append('VERROU 9 : la carte 2D s\'impose meme quand la 3D peint '
                          '-- le detecteur ne discrimine pas')
        if info.get('avisOn2') == '':
            echecs.append('VERROU 9 : un avertissement s\'affiche alors que la 3D peint '
                          '-- fausse alerte')

        # --- VERROU 11 : LA CAMERA CADRE-T-ELLE LE TERRAIN ?
        # Le vrai defaut de l'ecran noir : gl_Position = uMVP * v suppose la
        # convention vecteur-colonne, donc M = P . V ; le code ecrivait V . P.
        # Resultat : 0 sommet sur 2 291 dans le volume de vue, tout decoupe par
        # la carte graphique, programme accepte, getError() a 0. AUCUN verrou
        # textuel ne pouvait voir ca. Celui-ci refait la projection et compte.
        for suf, nom in (('2', 'WebGL2'), ('1', 'WebGL1')):
            cad = info.get('cadrage' + suf)
            if cad is None:
                echecs.append('VERROU 11 : impossible de verifier le cadrage en %s '
                              '(matrice ou sommets non captures)' % nom)
            elif cad < 0.30:
                echecs.append('VERROU 11 : la camera ne cadre pas le terrain en %s -- '
                              '%.1f %% des sommets seulement tombent dans le volume '
                              'de vue (%d testes). L\'ecran sera noir sans erreur GL.'
                              % (nom, 100.0 * cad, info.get('cadrageN' + suf) or 0))



    # --- VERROU 10 : le defaut le plus visible en jeu doit etre MESURE.
    # L'utilisateur voyait l'ancien terrain au milieu de la structure pendant que
    # la page n'en disait rien et se terminait sur une ligne verte. Un rapport qui
    # tait ce que le joueur voit est pire qu'absent : il rassure a tort.
    brut = open(SCANVIEW, encoding='utf-8').read()
    # Un commentaire qui CITE la phrase bannie n'est pas la phrase : sans ce
    # filtre, le commentaire expliquant le retrait declenchait le verrou.
    src = '\n'.join(l for l in brut.split('\n') if not l.strip().startswith('#'))
    if 'Ancien terrain laisse dans la structure' not in src:
        echecs.append('VERROU 10 : la page ne mesure plus l\'ancien terrain reste dans '
                      'l\'ouvrage -- le defaut le plus visible en jeu serait tu')
    if 'le raccord de terrain a fait son travail' in src:
        echecs.append('VERROU 10 : la page conclut « le raccord de terrain a fait son '
                      'travail » a partir du seul tablier plat -- conclusion plus large '
                      'que la mesure qui la porte')
    # L'empreinte doit couvrir TOUTE la pose, pas sa derniere fenetre.
    if 'def _empreinte' in src:
        bloc = src[src.index('def _empreinte'):]
        bloc = bloc[:bloc.index('\nreturn') if '\nreturn' in bloc else 2000]
        if 'etapes[-2]' in bloc:
            echecs.append('VERROU 10 : l\'empreinte repart des deux dernieres captures '
                          '-- elle ne voit que la fin de la pose (1 750 colonnes au lieu '
                          'de 43 000 sur le run du 24 nov.)')


    # --- VERROU 12 : UN COMPTEUR GLOBAL NE DIT PAS DE QUEL COTE IL MANQUE.
    # L'utilisateur a entoure le bas de la vue en ecrivant « MARGE » : il voyait
    # du terrain raccorde au nord, a l'ouest et a l'est, et rien au sud. La page
    # annoncait bien « N colonnes non mesurees », chiffre exact et inutile : il
    # ne disait pas OU. Le diagnostic doit ventiler la marge par cote et refuser
    # de conclure la ou la mesure s'arrete avant la fin de la rampe.
    _k = html.index('const DIAG = ')
    _diags = json.loads(html[_k + 13:html.index(chr(10), _k)].rstrip(';'))
    marge = [e for e in _diags if 'Marge' in e.get('t', '')]
    if not marge:
        echecs.append('VERROU 12 : la page ne mesure pas la marge de terrain par cote '
                      '-- un total de colonnes non mesurees ne dit pas de quel cote '
                      'le raccord est invisible')
    else:
        txt = ' '.join('%s %s %s' % (e.get('t', ''), e.get('d', ''), e.get('o', ''))
                       for e in marge)
        for cote in ('nord', 'sud', 'ouest', 'est'):
            if cote not in txt:
                echecs.append('VERROU 12 : le cote %s est absent de la mesure de marge '
                              '(%r)' % (cote, txt[:160]))
        if 'sud' not in ' '.join(e.get('t', '') for e in marge):
            echecs.append('VERROU 12 : sur le run de reference la marge sud est nulle '
                          '(mesure arretee a z=527 pour une emprise qui va a z=543) ; '
                          'le titre du diagnostic doit nommer le cote en cause')
        if max(e.get('g', 0) for e in marge) < 3:
            echecs.append('VERROU 12 : une marge trop courte pour juger le raccord est '
                          'signalee en mineur -- or elle interdit toute conclusion sur '
                          'ce cote')
    # --- VERROU 13 : FORMAT COMPOSITE ET CALQUE DU SPREAD DE NEIGE.
    # Le scan T496 a reuni grilles/cartes/rapport en un seul fichier d'etape.
    # Cette fixture verifie l'entree reelle, les marqueurs inconnus/eau, l'encodage
    # des milli-unites et la presence du calque dans la page, pas une simple mention.
    compose = os.path.join(tmp, 'composite')
    os.makedirs(compose, exist_ok=True)
    def ligne_grille(vals):
        return '         |x=0' + ' ' * 59 + '|\n' + '         +' + '-' * 64 + '+\n' + \
               'z=     0 ' + ''.join('%4s' % v for v in vals) + '|\n'
    entete_fixture = (
        'etape : 1_TEST neige\n'
        'instant : t+0 ms\n'
        'colonnes lues : 16 / 16   non mesurables (chunk absent) : 0\n'
        'chunks charges : 1 / 1\n'
        'zone scannee        : x 0..15   z 0..0   (16 x 1 blocs, 1 x 1 chunks)\n'
        'emprise structure   : x 0..15   z 0..0\n'
        'sol de reference y=64\n')
    rapport = entete_fixture + ' 9a. ySolid\n' + ligne_grille([64] * 16)
    rapport += ' 9b. ySurface\n' + ligne_grille([64] * 16)
    rapport += ' 9f. TAUX BRUT DU SPREAD DE NEIGE PAR COLONNE\n'
    rapport += ligne_grille([0, 105, 210, 315, 420, 1000, 'NEUT', '????', '----'] + [0] * 7)
    rapport += ' 10. PALETTE DES BLOCS\n'
    with open(os.path.join(compose, '01_1_TEST.txt'), 'w', encoding='utf-8') as f:
        f.write(rapport)
    with open(os.path.join(compose, '00_SOMMAIRE.txt'), 'w', encoding='utf-8') as f:
        f.write("ce sommaire n'est pas une etape\n")
    snow_page = os.path.join(tmp, 'composite.html')
    run = subprocess.run([sys.executable, SCANVIEW, compose, '-o', snow_page],
                         capture_output=True, timeout=120)
    if run.returncode != 0 or not os.path.isfile(snow_page):
        echecs.append('VERROU 13 : le visualisateur refuse le fichier composite fusionne')
    else:
        snow_html = open(snow_page, encoding='utf-8').read()
        dm = re.search(r'const DATA = (\{.*\});', snow_html)
        if not dm:
            echecs.append('VERROU 13 : les donnees du rapport composite ne sont pas embarquees')
        else:
            data = json.loads(dm.group(1))
            raw = base64.b64decode(data['etapes'][0]['sn'])
            values = [int.from_bytes(raw[i:i + 2], 'big') for i in range(0, len(raw), 2)]
            attendu = [0, 105, 210, 315, 420, 1000, 65534, 65533, 65535] + [0] * 7
            if len(values) != 16 or values != attendu:
                echecs.append('VERROU 13 : parsing/encodage du taux neige faux : %r' % values[:9])
        for marqueur, pourquoi in (
                ('value="snow"', 'le calque neige a disparu du menu'),
                ('else if(m==="snow")', 'la 3D ne colore plus le calque neige'),
                ("snowMode?neigeRGB", "la carte 2D de secours n'affiche plus le taux neige")):
            if marqueur not in snow_html:
                echecs.append('VERROU 13 : %s' % pourquoi)
    brut_scanview = open(SCANVIEW, encoding='utf-8').read()
    for marqueur, pourquoi in (
            ("cel == 'NEUT'", 'eau/glace ne sont plus distinguees'),
            ("cel == '????'", 'un profil absent peut devenir une valeur plausible'),
            ("cel == '----'", 'un chunk absent peut devenir une valeur plausible'),
            ('min(65532, max(0, snow))', 'les milli-unites ne sont pas bornees/encodees comme prevu')):
        if marqueur not in brut_scanview:
            echecs.append('VERROU 13 : lecteur de spread : %s' % pourquoi)

    if echecs:
        for e in echecs:
            print('ECHEC  %s' % e)
        print('scanviewcheck : %d verrou(x) rompu(s)' % len(echecs))
        return 1

    oracle = 'avec glslangValidator' if os.environ.get('GLSLANG') else 'regle d\'ordre seule'
    print('scanviewcheck : 13 verrous tiennent -- generateur fonctionnel, page autonome, '
          'panneau d\'etapes present, colonne non mesuree distinguee d\'un trou, '
          'page chargee sans exception en WebGL2 ET WebGL1, shaders valides (%s), '
          'repli WebGL1 en place, coupe verticale qui trace et gradue, '
          '3D jamais escamotee sur un soupcon, avertissement si elle ne peint rien, '
          'carte 2D en relais quand il n y a aucun contexte WebGL, '
          'ancien terrain restant dans l ouvrage mesure et annonce, '
          'camera qui cadre reellement le terrain, marge de terrain ventilee par cote, '
          'rapport composite + calque directionnel de neige dans la visionneuse' % oracle)
    return 0


if __name__ == '__main__':
    d = sys.argv[1] if len(sys.argv) > 1 else os.path.join(RACINE, 'rapports_scan_20261123_TEST2')
    sys.exit(controler(d))
