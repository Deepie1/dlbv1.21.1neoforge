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
for(const v2 of [true,false]){
  const vus=[];
  const GL=new Proxy({},{get(_,p){
    if(p==='VERTEX_SHADER')return 35633; if(p==='FRAGMENT_SHADER')return 35632;
    if(p==='COMPILE_STATUS'||p==='LINK_STATUS')return 1;
    if(p==='getShaderParameter'||p==='getProgramParameter')return ()=>true;
    if(p==='getExtension')return ()=>({});
    if(p==='createShader')return t=>({t});
    if(p==='shaderSource')return (sh,src)=>{vus.push([sh.t,src]);};
    if(p==='getUniformLocation')return ()=>({});
    if(p==='createBuffer'||p==='createProgram')return ()=>({});
    return ()=>{};
  }});
  const noeuds={};
  function elem(id){ if(noeuds[id])return noeuds[id];
    const n={id,children:[],style:{},className:'',checked:true,value:'',textContent:'',innerHTML:'',
      appendChild(c){this.children.push(c);},addEventListener(){},
      getBoundingClientRect:()=>({left:0,top:0,width:900,height:600}),
      getContext:k=>((k==='webgl2')===v2)?GL:null,
      clientWidth:900,clientHeight:600,width:900,height:600};
    if(id==='mode')n.value='alt'; if(id==='vex')n.value='3'; noeuds[id]=n; return n;}
  global.document={getElementById:elem,createElement:()=>({children:[],style:{},innerHTML:'',
    appendChild(){},set onclick(f){},get onclick(){return null;}})};
  global.window={addEventListener(){},devicePixelRatio:1};
  global.atob=b=>Buffer.from(b,'base64').toString('binary');
  let boom=null;
  try{ eval(js); }catch(e){ boom=e.message; }
  const suf=v2?'2':'1';
  for(const [t,src] of vus) out[(t===35633?'vs':'fs')+suf]=src;
  out['exception'+suf]=boom;
  out['erreur'+suf]=(noeuds['err']&&noeuds['err'].style.display==='flex')?noeuds['err'].textContent:null;
  out['etapes'+suf]=noeuds['steps']?noeuds['steps'].children.length:0;
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

    if echecs:
        for e in echecs:
            print('ECHEC  %s' % e)
        print('scanviewcheck : %d verrou(x) rompu(s)' % len(echecs))
        return 1

    oracle = 'avec glslangValidator' if os.environ.get('GLSLANG') else 'regle d\'ordre seule'
    print('scanviewcheck : 7 verrous tiennent -- generateur fonctionnel, page autonome, '
          'panneau d\'etapes present, colonne non mesuree distinguee d\'un trou, '
          'page chargee sans exception en WebGL2 ET WebGL1, shaders valides (%s), '
          'repli WebGL1 en place' % oracle)
    return 0


if __name__ == '__main__':
    d = sys.argv[1] if len(sys.argv) > 1 else os.path.join(RACINE, 'rapports_scan_20261123_TEST2')
    sys.exit(controler(d))
