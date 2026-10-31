# Audit de gravite des NBT de structures (20/09/2026) — comptage EXACT des blocs
# soumis a la gravite par structure, lu dans la palette NBT (pas par grep :
# "minecraft:sand" est aussi le prefixe de "minecraft:sandstone").
# Usage : python3 tools/nbt_gravity_scan.py src/main/resources/data/deep_lucky_block/structure
import gzip, struct, os, sys, json

class R:
    def __init__(s, b): s.b=b; s.i=0
    def u1(s):
        v=s.b[s.i]; s.i+=1; return v
    def i1(s):
        v=struct.unpack_from('>b',s.b,s.i)[0]; s.i+=1; return v
    def i2(s):
        v=struct.unpack_from('>h',s.b,s.i)[0]; s.i+=2; return v
    def i4(s):
        v=struct.unpack_from('>i',s.b,s.i)[0]; s.i+=4; return v
    def i8(s):
        v=struct.unpack_from('>q',s.b,s.i)[0]; s.i+=8; return v
    def f4(s):
        v=struct.unpack_from('>f',s.b,s.i)[0]; s.i+=4; return v
    def f8(s):
        v=struct.unpack_from('>d',s.b,s.i)[0]; s.i+=8; return v
    def st(s):
        n=struct.unpack_from('>H',s.b,s.i)[0]; s.i+=2
        v=s.b[s.i:s.i+n].decode('utf-8','replace'); s.i+=n; return v
    def payload(s,t):
        if t==1: return s.i1()
        if t==2: return s.i2()
        if t==3: return s.i4()
        if t==4: return s.i8()
        if t==5: return s.f4()
        if t==6: return s.f8()
        if t==7:
            n=s.i4(); v=s.b[s.i:s.i+n]; s.i+=n; return v
        if t==8: return s.st()
        if t==9:
            et=s.u1(); n=s.i4()
            return [s.payload(et) for _ in range(n)]
        if t==10:
            d={}
            while True:
                tt=s.u1()
                if tt==0: break
                nm=s.st(); d[nm]=s.payload(tt)
            return d
        if t==11:
            n=s.i4()
            if n<0: raise ValueError('neg')
            return [s.i4() for _ in range(n)]
        if t==12:
            n=s.i4(); return [s.i8() for _ in range(n)]
        raise ValueError('tag %d'%t)
    def root(s):
        if s.u1()!=10: raise ValueError('pas de compound racine')
        s.st()
        return s.payload(10)

GRAV = {"sand","red_sand","gravel","suspicious_sand","suspicious_gravel","anvil","chipped_anvil",
        "damaged_anvil","dragon_egg","white_concrete_powder","orange_concrete_powder","light_gray_concrete_powder",
        "gray_concrete_powder","black_concrete_powder","brown_concrete_powder","red_concrete_powder",
        "yellow_concrete_powder","lime_concrete_powder","green_concrete_powder","cyan_concrete_powder",
        "light_blue_concrete_powder","blue_concrete_powder","purple_concrete_powder","magenta_concrete_powder","pink_concrete_powder",
        "scaffolding","pointed_dripstone"}

def load(path):
    raw=open(path,'rb').read()
    try: raw=gzip.decompress(raw)
    except Exception: pass
    return R(raw).root()

d=sys.argv[1]
out={}
for f in sorted(os.listdir(d)):
    if not f.endswith('.nbt'): continue
    p=os.path.join(d,f)
    if os.path.getsize(p)<50000: continue
    try:
        r=load(p)
    except Exception as e:
        out[f]={'err':str(e)}; continue
    pal=[x.get('Name','?') for x in r.get('palette',[])]
    blocks=r.get('blocks',[])
    counts={}
    for b in blocks:
        st=b.get('state',0)
        nm=pal[st] if isinstance(st,int) and 0<=st<len(pal) else '?'
        counts[nm]=counts.get(nm,0)+1
    g={k[len('minecraft:'):]:v for k,v in counts.items() if k.startswith('minecraft:') and k[len('minecraft:'):] in GRAV}
    bos=sum(v for k,v in counts.items() if k.split(':')[0]!='minecraft:')
    ents=r.get('entities',[]) or []
    entk={}
    for e in ents:
        n=e.get('nbt',{}).get('id','?')
        if isinstance(n,bytes): n=n.decode('utf-8','replace')
        entk[n]=entk.get(n,0)+1
    out[f]={'blocs':len(blocks),'palette':len(pal),'gravity':g,'nb_grav':sum(g.values()),
            'blocs_mod':bos,'entites':len(ents),'entites_types':dict(sorted(entk.items(), key=lambda x:-x[1])[:6]),
            'size':[r.get('size',[None]*3)]}
print(json.dumps(out, indent=1)[:6000])
