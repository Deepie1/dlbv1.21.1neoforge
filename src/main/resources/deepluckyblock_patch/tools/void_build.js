/* ===========================================================================================
 * void_build.js — MIROIR JS de deepluckyblock.client.VoidSignatureVfx
 *
 * Même rôle que thunder_build.js, shadow_build.js et blood_build.js : rejouer la géométrie
 * Java dans un navigateur pour la relire sans lancer Minecraft. Toute divergence avec le
 * .java est un BOGUE du miroir.
 *
 * Repère : le Java travaille en (f = axe attaquant→cible, lateral = perpendiculaire,
 * y = hauteur) puis projette avec dx/dz. L'aperçu fixe dx = 1, dz = 0, l'origine à l'impact,
 * donc        x = f        y = y        z = lateral
 * et l'orientation vaut ZÉRO partout : VoidSignatureVfx n'emploie que box(), jamais
 * emitVoxelBoxRotated — comme le sang, et contrairement à l'ombre.
 *
 * ATTENTION — ce que l'aperçu ne modélise PAS : le tamis du moteur (pixelDensity < 0.999 &&
 * alpha < 200 décime les cubes), cubeVeil, ni le plafond de 7000 cubes par frame. Un rendu
 * correct ici ne prouve donc RIEN en jeu ; il sert à relire la chorégraphie.
 *
 * CE MIROIR N'EST PAS UNE TRANSCRIPTION LIGNE À LIGNE : box/segment/spaghetti sont des
 * CLOSURES ici, des méthodes statiques là-bas.
 * =========================================================================================== */

/* ---- LES TROIS COTES PARTAGÉES AVEC LA FENÊTRE D'ABYSSE ----------------------------
 * Miroir de VoidSignatureVfx.warpHorizon / riftGape / gateRing. Hors de buildVoid parce
 * que la fenêtre (VoidPortalFx côté jeu, le module PORTAL côté page) doit poser son
 * disque dans l'ouverture que la figure encadre, et pas une ligne plus loin.
 */
const VOID_SHAPE = (function () {
  const clamp01 = (v) => (v < 0 ? 0 : v > 1 ? 1 : v);
  const smooth = (x) => { x = clamp01(x); return x * x * (3 - 2 * x); };
  /* Meme hachage que dans buildVoid, et meme hachage que le Java : chainStation en a
     besoin ICI, parce que la page doit pouvoir placer une station sans construire la
     figure entiere. Deux copies de quatre lignes valent mieux qu'une dependance
     circulaire entre la table de cotes et le moteur de rendu. */
  const hash = (seed, n) => {
    let h = (seed ^ Math.imul(n, 0x45D9F3B)) | 0;
    h ^= h >>> 16; h = Math.imul(h, 0x45D9F3B); h ^= h >>> 16;
    return (h & 0xFFFF) / 65535;
  };
  const TAU = 6.2831855;
  return {
    /* LE SOL. L'origine d'une signature est le TORSE de la victime (bbHeight x 0,50 cote
       serveur) : pour un corps d'1,80 le plancher est 0,90 plus bas, et c'est cette
       valeur-la. L'apercu pose SON quadrillage et les pieds de SON mannequin ici. */
    GROUND_FALLBACK: -0.90,

    /* --- Sphere --- */
    WARP_YC: 0.46,
    warpHorizon(t) {
      const open = smooth((t - 0.10) / 0.24), fall = smooth((t - 0.70) / 0.10);
      return (0.05 + 1.10 * open) * (1 - fall);
    },

    /* --- Prisme : le cristal, son regime, sa tranche equatoriale --- */
    PRISM_YC: 1.12,
    PRISM_W: 1.15,
    PRISM_TILT: 0.34,
    PRISM_FIRE0: 62 / 190,
    PRISM_FIRE_STEP: 9 / 190,
    PRISM_BREAK: 150 / 190,
    PRISM_BURST: 170 / 190,
    prismSpin(t) {
      const spinUp = smooth((t - 0.07) / 0.14);
      const broken = clamp01((t - 150 / 190) / 0.08);
      /* 2,4 + 9,0 faisait pres de deux tours par seconde : l'oeil n'a pas le temps
         de reconstruire l'octaedre et ne voit qu'une cage qui gigote. */
      return t * (2.0 + 4.2 * spinUp) * (1 - broken * 0.85);
    },
    prismBody(t) {
      const into = smooth(t / 0.085), out = smooth((t - 150 / 190) / 0.045);
      return into * (1 - out) * (1 + 0.055 * Math.sin(t * 21));
    },
    /* Plan de la tranche equatoriale : {af,al,ay, bf,bl,by, rayon}. L'equateur d'un
       octaedre qui tourne autour d'un axe incline n'est pas horizontal. */
    prismSlice(t) {
      const spin = this.prismSpin(t);
      const ct = Math.cos(this.PRISM_TILT), st = Math.sin(this.PRISM_TILT);
      const cs = Math.cos(spin), sn = Math.sin(spin);
      return [cs, sn, 0, -sn * ct, cs * ct, st, this.PRISM_W * this.prismBody(t) * 0.84];
    },

    /* --- Faille : une LIGNE qui se propage --- */
    RIFT_HEIGHT: 3.05,
    RIFT_LIP: 0.82,
    RIFT_REACH: 4.30,
    RIFT_CLOSE: 140 / 170,
    riftGape(t) {
      const opening = smooth((t - 0.06) / 0.17), closing = smooth((t - 0.64) / 0.18);
      return opening * (1 - closing);
    },
    riftRun(t) { return smooth((t - 0.012) / 0.085); },
    riftZip(t) { return 1 - smooth((t - 140 / 170) / 0.11); },
    riftHalfLength(t) { return this.RIFT_REACH * this.riftRun(t) * this.riftZip(t); },
    riftCrown(t) { return this.RIFT_HEIGHT * this.riftRun(t); },
    /* Profil de Griffith : maximal au point de nucleation, nul aux pointes. */
    riftProfile(u) { const v = 1 - u * u; return v <= 0 ? 0 : Math.sqrt(v); },

    /* --- LE PIEGE A LOUP. Miroir des constantes et de la cinematique Java. --- */
    RIFT_HINGE: 0.52,
    RIFT_JAW: 2.05,
    RIFT_TEETH: 9,
    RIFT_OPEN: 1.08,
    RIFT_CURVE: 0.52,
    RIFT_SNAP: 0.042,
    RIFT_GHOSTS: 9,
    RIFT_ARMED: 0.30,
    /* Deport horizontal de la pointe, en fraction de RIFT_JAW, pour un angle donne. */
    riftReachOf(ang) {
      let acc = 0;
      for (let i = 0; i < 16; i++) acc += Math.sin(ang - this.RIFT_CURVE * ((i + 0.5) / 16) ** 2);
      return acc / 16;
    },
    /* L'angle de fermeture se CALCULE : l'ecrire a la main ferait rater le contact. */
    get RIFT_SHUT() {
      if (this._shut === undefined) {
        const target = -this.RIFT_HINGE / this.RIFT_JAW;
        let lo = -1.4, hi = 0;
        for (let i = 0; i < 24; i++) {
          const mid = (lo + hi) * 0.5;
          if (this.riftReachOf(mid) > target) hi = mid; else lo = mid;
        }
        this._shut = (lo + hi) * 0.5;
      }
      return this._shut;
    },
    /* Toute la choregraphie du piege : ouverture, armement, neuf morsures manquees,
       claquement en cinq ticks, puis le rebond de l'acier. */
    riftJawAngle(t) {
      const SHUT = this.RIFT_SHUT;
      if (t <= 0.012) return SHUT;
      if (t < this.RIFT_ARMED) {
        const u = clamp01((t - 0.012) / (this.RIFT_ARMED - 0.012));
        return SHUT + (this.RIFT_OPEN - SHUT) * (1 - (1 - u) ** 3);
      }
      if (t < this.RIFT_CLOSE) {
        /* Le frisson divise par trois : une attente doit etre IMMOBILE pour etre tendue,
           et neuf a-coups brouillaient le vrai claquement. */
        const dread = clamp01((t - this.RIFT_ARMED) / (this.RIFT_CLOSE - this.RIFT_ARMED));
        const tremor = (0.008 + 0.020 * dread * dread) * Math.sin(t * (31 + 44 * dread));
        /* TROIS morsures manquees, pas neuf, mais profondes : on les compte. */
        let miss = 0;
        for (let k = 1; k < 9; k += 3) {
          const age = t - (20 + k * 10) / 170;
          if (age < 0) break;
          if (age > 0.072) continue;
          const u = age / 0.072;
          const bite = u < 0.28 ? (u / 0.28) ** 2 : 1 - smooth((u - 0.28) / 0.72);
          if (bite > miss) miss = bite;
        }
        /* ET LE PIEGE SE TEND : sans derive lente et monotone, une attente de quatre
           secondes et demie n'a pas de direction, et paraît deux fois plus longue. */
        const tighten = (this.RIFT_OPEN - SHUT) * 0.16 * smooth(dread);
        return this.RIFT_OPEN - tighten + tremor - (this.RIFT_OPEN - SHUT) * 0.62 * miss;
      }
      const u = clamp01((t - this.RIFT_CLOSE) / this.RIFT_SNAP);
      /* LE CHOC D'ACIER : les deux machoires se percutent, rebondissent et se recollent
         en vibrant. Sans lui la fermeture est une porte de garage. */
      const post = clamp01((t - this.RIFT_CLOSE - this.RIFT_SNAP) / 0.075);
      const ring = Math.sin(post * Math.PI * 6) * 0.26 * (1 - post) * (1 - post);
      /* u^2,2 : en u^3 la fermeture tombait ENTRE deux images. */
      return this.RIFT_OPEN + (SHUT - this.RIFT_OPEN) * (u * u * u ** 0.2) + ring;
    },
    riftJawSpeed(t) {
      return Math.abs(this.riftJawAngle(t + 0.004) - this.riftJawAngle(t - 0.004)) / 0.008;
    },
    /* Un point de la machoire : h = 0 a la charniere, 1 a la pointe. L'arc est INTEGRE,
       pas pose en ligne droite — une machoire droite est une planche. */
    riftJawPoint(h, ang, reach) {
      const n = Math.max(2, Math.ceil(h * 14));
      let f = 0, y = 0;
      for (let i = 0; i < n; i++) {
        const hh = h * (i + 0.5) / n, local = ang - this.RIFT_CURVE * hh * hh;
        f += Math.sin(local); y += Math.cos(local);
      }
      return [f / n * h * reach, y / n * h * reach];
    },

    /* --- Porte --- */
    GATE_PASS0: 34 / 180,
    GATE_PASS_STEP: 18 / 180,
    GATE_SHUT: 150 / 180,
    gateRing(t) {
      const grow = smooth((t - 0.04) / 0.26), shut = smooth((t - 0.86) / 0.14);
      return (0.34 + 1.78 * grow) * (1 - shut);
    },

    /* --- Chaine : les sept stations, et leurs horaires (miroir de chainFire) --- */
    CHAIN_LINKS: 7,
    CHAIN_YC: 0.28,
    CHAIN_TRAVEL: 8 / 200,
    chainFire(i) {
      let tick = 30;
      for (let k = 0; k < i; k++) tick += Math.max(14, 40 - k * 5);
      return tick / 200;
    },
    chainArrive(i) { return i <= 0 ? 0 : this.chainFire(i - 1) + this.CHAIN_TRAVEL; },
    chainStation(i, salt) {
      const ang = i * 2.39996 + (hash(salt, i * 17 + 3) - 0.5) * 0.80;
      const rad = 1.55 * Math.sqrt(i) * (0.85 + 0.30 * hash(salt, i * 29 + 7));
      return [Math.cos(ang) * rad, Math.sin(ang) * rad,
              this.CHAIN_YC + (hash(salt, i * 41 + 11) - 0.5) * 0.30];
    },
    chainBlast(i) { return 1.40 + i * 0.25; },
    chainCell(t) {
      for (let i = this.CHAIN_LINKS - 1; i >= 0; i--) {
        const a = this.chainArrive(i), f = this.chainFire(i);
        if (t < a || t >= f) continue;
        return (1.25 + i * 0.10) * (1 - smooth((t - a) / Math.max(1e-4, f - a))) + 0.12;
      }
      return 0;
    },
    TAU,
  };
})();

function buildVoid(variant, t, salt = 7331) {
  const out = [];
  /* Les CUBES D'ABYSSE. Cote jeu ils partent dans VoidPortalFx.queueCube et le core
     shader les remplit ; ici on les met de cote et la page les dessinera avec la meme
     texture. buildVoid les expose sur le tableau de sortie (out.portalCubes) plutot que
     par un second retour, pour ne pas casser les quatre autres familles qui appellent
     ce moteur avec la meme signature. */
  const cubesOut = [];
  const abyss = (f, lateral, y, half) => { cubesOut.push([f, lateral, y, half]); };
  if (t <= 0 || t >= 1) return out;

  // ---- Parité stricte avec les utilitaires du .java -------------------------------------
  const TAU = 6.2831855;
  const PI = Math.PI;
  const clamp01 = (v) => (v < 0 ? 0 : v > 1 ? 1 : v);
  const smooth = (x) => { x = clamp01(x); return x * x * (3 - 2 * x); };
  const fract = (x) => x - Math.floor(x);
  const SOLID_FLOOR = 208;
  const opq = (v) => { const i = Math.round(v); return i <= 10 ? 0 : (i < SOLID_FLOOR ? SOLID_FLOOR : Math.min(255, i)); };
  const hash = (seed, n) => {
    let h = (seed ^ Math.imul(n, 0x45D9F3B)) | 0;
    h ^= h >>> 16; h = Math.imul(h, 0x45D9F3B); h ^= h >>> 16;
    return (h & 0xFFFF) / 65535;
  };

  // ---- Palette ---------------------------------------------------------------------------
  // DeepElement.VOID vaut (0x444444 / 0x000000 / 0x5B5B5B) : du gris pur, inutilisable.
  // Corps presque noirs + horizon CYAN-BLANC. Le violet n'apparaît qu'à UN endroit (l'autre
  // côté de la faille) et l'orange qu'à la restitution.
  const NOTHING = 0x0E0216, ABYSS = 0x050D25, BASALT = 0x2D0043, SLATE = 0x282077,
        HALO = 0x6C13A4, HORIZON = 0x9518E2, GLACIER = 0xD7B4F7, SINGULAR = 0xFFFFFF;
  const ENDSTONE = 0xD8D2A4, ENDSTONE_DK = 0x8A855F, CHORUS = 0x4B5295, INVERT = 0xB18E8C;

  // L'aperçu n'a pas de monde à sonder : GROUND_Y garde sa valeur de repli.
  const GROUND_Y = VOID_SHAPE.GROUND_FALLBACK, SLAB_H = 0.030, GRAVITY = 9.0;

  // ---- Émission ---------------------------------------------------------------------------
  const box = (f, lateral, y, hf, hy, hl, rgb, a) => {
    if (a <= 0 || hf <= 0 || hy <= 0 || hl <= 0) return;
    out.push({ x: f, y: y, z: lateral, hx: hf, hy: hy, hz: hl,
               rgb: rgb, a: Math.min(255, a), yaw: 0, pitch: 0, roll: 0 });
  };

  /* Un SEGMENT entre deux points. Primitive de base de la famille : le Vide ne dessine que
     des arêtes. Le nombre de cubes suit la longueur réelle — un pas fixe pointillerait les
     tiges longues et gaspillerait sur les courtes. */
  /* Le PAS est explicite : la grille de la Sphere est DENSE LA OU L'ESPACE EST DENSE —
     pointillee au loin, jointive au bord de l'horizon. Ce n'est pas une optimisation,
     c'est une seconde lecture de la compression, en plus de la courbure. Et sans plancher
     de pas, une tige qui s'amincit en disparaissant couterait PLUS cher. */
  const segment = (f0, l0, y0, f1, l1, y1, w, rgb, a, pitch) => {
    if (a <= 0 || w <= 0) return;
    const df = f1 - f0, dl = l1 - l0, dy = y1 - y0;
    const len = Math.sqrt(df * df + dl * dl + dy * dy);
    if (len < 0.0005) return;
    const p = pitch === undefined ? Math.max(0.030, w * 1.15) : pitch;
    const n = Math.max(1, Math.min(26, Math.round(len / Math.max(0.030, p))));
    for (let i = 0; i <= n; i++) {
      const u = i / n;
      box(f0 + df * u, l0 + dl * u, y0 + dy * u, w, w, w, rgb, a);
    }
  };

  /* La SPAGHETTIFICATION. Règle n° 3 de l'élément rendue littérale : cinq maillons dont
     l'écart croît et la section décroît en (r/r0)^1,6. À l'horizon la section est nulle,
     donc RIEN ne traverse. */
  const spaghetti = (ang, r, y, rs, size, a, idx) => {
    if (r <= rs * 0.98) return;
    const over = clamp01((r - rs) / 1.9);
    const stretch = 1 + 2.6 * (1 - over) * (1 - over);
    const thin = Math.pow(over, 1.6);
    for (let k = 0; k < 5; k++) {
      const rr = r + k * 0.085 * stretch;
      const sz = size * thin * (1 - k * 0.13);
      if (sz <= 0.006) continue;
      const along = sz * (0.6 + 1.9 * (1 - over));
      box(Math.cos(ang) * rr, Math.sin(ang) * rr, y, along, sz * 0.75, sz * 0.75,
          k === 0 ? (over < 0.22 ? GLACIER : ENDSTONE)
                  : (((idx + k) & 1) === 0 ? ENDSTONE : ENDSTONE_DK),
          opq(a * (1 - k * 0.10)));
    }
  };


  /* Un TORE. Le repere est EXPLICITE — tangente, normale radiale, verticale locale — et
     recalcule a chaque pas : construit sur les axes du monde, le tube se vrille et l'anneau
     prend l'aspect d'un ruban tordu. Meme piege que la couronne du Prisme. */
  const torus = (of, ol, oy, ring, tube, tilt, phase, a, dashed) => {
    if (ring <= 0.01 || tube <= 0.003) return;
    const around = Math.max(24, Math.min(64, Math.round(ring * 26)));
    const section = dashed ? 4 : 8;
    const ct = Math.cos(tilt), st = Math.sin(tilt);
    for (let i = 0; i < around; i++) {
      const ang = i / around * TAU;
      const ca = Math.cos(ang), sa = Math.sin(ang);
      const rf = ca * ring, rl = sa * ring * ct, ry = sa * ring * st;
      const nfx = ca, nlx = sa * ct, nyx = sa * st;
      const vfx = 0, vlx = -st, vyx = ct;
      for (let s = 0; s < section; s++) {
        const sang = s / section * TAU + phase + ang * 0.5;
        const cs = Math.cos(sang), ss = Math.sin(sang);
        const f = of + rf + (nfx * cs + vfx * ss) * tube;
        const l = ol + rl + (nlx * cs + vlx * ss) * tube;
        const y = oy + ry + (nyx * cs + vyx * ss) * tube;
        const crest = ss > 0.35;
        const flick = 0.74 + 0.26 * Math.sin(ang * 5 + phase * 3 + s * 1.1);
        box(f, l, y, 0.030, 0.030, 0.030,
            crest ? (flick > 0.93 ? GLACIER : HORIZON)
                  : (((i + s) & 1) === 0 ? BASALT : ABYSS),
            opq(a * (crest ? flick : 0.85)));
      }
    }
    /* L'ARETE INTERIEURE : un seul fil clair sur le bord du trou. C'est lui qui dit qu'il
       y a un TROU, et non un disque sombre au milieu d'un anneau clair. */
    const lipN = Math.max(20, Math.round(ring * 22));
    for (let i = 0; i < lipN; i++) {
      const ang = i / lipN * TAU;
      const rr = ring - tube * 0.92;
      if (rr <= 0.02) break;
      const ca = Math.cos(ang), sa = Math.sin(ang);
      const pulse = 0.68 + 0.32 * Math.sin(ang * 3 - phase * 4);
      box(of + ca * rr, ol + sa * rr * ct, oy + sa * rr * st, 0.024, 0.024, 0.024,
          pulse > 0.90 ? SINGULAR : GLACIER, opq(a * pulse));
    }
  };

  /* Un FRAGMENT du monde : TROIS cubes assembles et tournes. Un cube seul qui tombe est un
     pixel qui tombe ; trois cubes decales lisent comme un eclat de matiere, et leur
     rotation prouve qu'ils sont EN CHUTE et non en translation. */
  const fragment = (f, l, y, size, roll, bright, dark, a) => {
    if (size <= 0.008) return;
    const c = Math.cos(roll), s = Math.sin(roll);
    for (let k = 0; k < 3; k++) {
      const off = (k - 1) * size * 0.78;
      const sz = size * (k === 1 ? 1.0 : 0.72);
      box(f + off * c, l + off * s, y + off * s * 0.55, sz, sz, sz,
          k === 1 ? bright : dark, a);
    }
  };

  /* Un NOEUD : un coeur noir en losange, herisse de six pointes claires qui tournent. Le
     coeur est en NOTHING, donc invisible seul — les pointes sont son lisere obligatoire. */
  const node = (f, l, y, size, roll, collapse, a) => {
    if (size <= 0.004) return;
    const fade = 1 - collapse * 0.75;
    for (let k = 0; k < 4; k++) {
      const ang = k * (TAU / 4) + roll;
      box(f + Math.cos(ang) * size * 0.46, l + Math.sin(ang) * size * 0.46, y,
          size * 0.52, size * 0.52, size * 0.52, NOTHING, opq(a * fade));
    }
    box(f, l, y, size * 0.40, size * 0.90, size * 0.40, ABYSS, opq(a * fade));
    const spike = size * (1.55 + 0.45 * Math.sin(roll * 1.7)) * (1 - collapse);
    for (let s = 0; s < 6; s++) {
      let sf = (s === 0 ? 1 : s === 1 ? -1 : 0) * spike;
      let sl = (s === 2 ? 1 : s === 3 ? -1 : 0) * spike;
      const sy = (s === 4 ? 1 : s === 5 ? -1 : 0) * spike;
      if (s < 4) {
        const c = Math.cos(roll), sn = Math.sin(roll);
        const rf = sf * c - sl * sn;
        sl = sf * sn + sl * c;
        sf = rf;
      }
      segment(f, l, y, f + sf, l + sl, y + sy, size * 0.26,
              s >= 4 ? GLACIER : HORIZON, opq(a * 0.92 * fade), 0.040);
    }
  };

  const dist3 = (f0, l0, y0, f1, l1, y1) => {
    const df = f1 - f0, dl = l1 - l0, dy = y1 - y0;
    return Math.sqrt(df * df + dl * dl + dy * dy);
  };

  /* ---- CALCUL : miroir exact des primitives du .java ------------------------------- */

  /* Repere du cristal : inclinaison autour de f, puis rotation autour de la verticale. */
  const spinPoint = (f, l, y, tilt, spin) => {
    const ct = Math.cos(tilt), st = Math.sin(tilt);
    const l1 = l * ct - y * st, y1 = l * st + y * ct;
    const cs = Math.cos(spin), sn = Math.sin(spin);
    return [f * cs - l1 * sn, f * sn + l1 * cs, y1];
  };
  const unspinPoint = (f, l, y, tilt, spin) => {
    const cs = Math.cos(spin), sn = Math.sin(spin);
    const f1 = f * cs + l * sn, l1 = -f * sn + l * cs;
    const ct = Math.cos(tilt), st = Math.sin(tilt);
    return [f1, l1 * ct + y * st, -l1 * st + y * ct];
  };

  /* Rayon / octaedre |f|+|l|+|y| <= w, par decoupe de l'intervalle sur les huit plans.
     Sortie : [tEntree, nf, nl, ny, tSortie, nf, nl, ny], normales SORTANTES. */
  const octaHit = (ox, ol, oy, dxr, dlr, dyr, w) => {
    if (w <= 0.001) return null;
    let near = -1e9, far = 1e9, nearPlane = -1, farPlane = -1;
    for (let plane = 0; plane < 8; plane++) {
      const sf = (plane & 1) === 0 ? 1 : -1;
      const sl = (plane & 2) === 0 ? 1 : -1;
      const sy = (plane & 4) === 0 ? 1 : -1;
      const denom = sf * dxr + sl * dlr + sy * dyr;
      const num = w - (sf * ox + sl * ol + sy * oy);
      if (Math.abs(denom) < 1e-6) { if (num < 0) return null; continue; }
      const hit = num / denom;
      if (denom > 0) { if (hit < far) { far = hit; farPlane = plane; } }
      else { if (hit > near) { near = hit; nearPlane = plane; } }
    }
    if (far <= near || farPlane < 0 || nearPlane < 0) return null;
    const inv = 0.57735027;
    return [near,
            ((nearPlane & 1) === 0 ? 1 : -1) * inv,
            ((nearPlane & 2) === 0 ? 1 : -1) * inv,
            ((nearPlane & 4) === 0 ? 1 : -1) * inv,
            far,
            ((farPlane & 1) === 0 ? 1 : -1) * inv,
            ((farPlane & 2) === 0 ? 1 : -1) * inv,
            ((farPlane & 4) === 0 ? 1 : -1) * inv];
  };

  /* Snell-Descartes vectoriel, reflexion totale comprise. Renvoie {d, out}. */
  const refract = (ix, il, iy, nx, nl, ny, eta) => {
    let cosi = -(ix * nx + il * nl + iy * ny);
    if (cosi < 0) { cosi = -cosi; nx = -nx; nl = -nl; ny = -ny; }
    const k = 1 - eta * eta * (1 - cosi * cosi);
    if (k < 0) return { d: [ix + 2 * cosi * nx, il + 2 * cosi * nl, iy + 2 * cosi * ny], out: false };
    const c = eta * cosi - Math.sqrt(k);
    let d = [eta * ix + c * nx, eta * il + c * nl, eta * iy + c * ny];
    const len = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
    if (len > 1e-5) d = [d[0] / len, d[1] / len, d[2] / len];
    return { d, out: true };
  };

  /* Fleche d'une CHAINETTE : la courbe d'un cable qui pend, pas une parabole. */
  const catenary = (u, slack) => {
    if (slack <= 0.0001) return 0;
    const shape = 2.2 + 6.0 * clamp01(slack);
    return (Math.cosh(shape * 0.5) - Math.cosh(shape * (u - 0.5))) / shape * (0.9 + 2.4 * slack);
  };

  /* Un corps soumis a la pesanteur qui REBONDIT sur le sol au lieu de le traverser. */
  const ballistic = (y0, vy, sec, floorY) => {
    const y = y0 + vy * sec - 0.5 * GRAVITY * sec * sec;
    if (y >= floorY) return y;
    const over = floorY - y;
    return floorY + Math.abs(Math.sin(over * 4.2)) * 0.22 / (1 + over * 2.6);
  };

  /* Le trajet d'un des sept rayons DANS le cristal. Miroir de prismRay(). */
  /* ---- LE DRAGON DE L'END. Miroir exact de dragonRot/dragonBox/dragonSpine/dragon.
     Les boites et les pivots sont ceux d'EnderDragonModel (ModelDragon.addBox) ; rien
     n'est redessine a la main. Le moteur d'apercu applique localRot() dans le meme ordre
     que emitVoxelBoxRotated (roll -> pitch -> yaw), donc une boite tournee ici se lit
     comme elle se lira en jeu. ---- */
  const DRAGON_U = 0.015;          // blocs par unite de modele
  const DRAGON_SEG = 10.0;         // pas entre deux segments de cou ou de queue

  /* Axes locaux : x = l'avant (le nez), y = le haut, z = le cote. Avec cette convention
     « nose » pique du nez, « bank » roule autour de l'axe du nez et « yaw » donne le cap.
     L'inversion vient de l'ordre de composition du manager ; elle est nommee, pas subie. */
  const dragonRot = (x, y, z, nose, bank, yaw) => {
    const cr = Math.cos(nose), sr = Math.sin(nose);
    const cp = Math.cos(bank), sp = Math.sin(bank);
    const cy = Math.cos(yaw), sy = Math.sin(yaw);
    const x1 = x * cr - y * sr, y1 = x * sr + y * cr;
    const y2 = y1 * cp - z * sp, z2 = y1 * sp + z * cp;
    return [x1 * cy + z2 * sy, y2, -x1 * sy + z2 * cy];
  };

  /* Une boite du modele vanilla. (bf,bl,by) = pivot de la piece, (mx,my,mz) = centre de la
     boite en unites de modele dans le repere Minecraft (y vers le BAS, nez vers les z
     negatifs), (sx,sy,sz) = taille. */
  const dragonBox = (bf, bl, by, nose, bank, yaw, mx, my, mz, sx, sy, sz, rgb, a) => {
    if (a <= 0) return;
    const o = dragonRot(-mz * DRAGON_U, -my * DRAGON_U, mx * DRAGON_U, nose, bank, yaw);
    out.push({ x: bf + o[0], y: by + o[1], z: bl + o[2],
               hx: Math.max(0.004, sz * 0.5 * DRAGON_U),
               hy: Math.max(0.004, sy * 0.5 * DRAGON_U),
               hz: Math.max(0.004, sx * 0.5 * DRAGON_U),
               rgb: rgb, a: Math.min(255, a), yaw: yaw, pitch: bank, roll: nose });
  };

  /* Une station de l'echine, a s blocs derriere le museau. Le cap est la TANGENTE a
     l'orbite : plus de « retard angulaire » a regler, la geometrie s'en charge. */
  const dragonSpine = (s, orbit, radius, yBase, tt) => {
    const a = orbit - s / Math.max(0.8, radius);
    const ca = Math.cos(a), sa = Math.sin(a);
    const amp = 0.055 * Math.max(0, Math.min(1, s * 0.55));
    return [ca * radius, sa * radius,
            yBase + Math.sin(s * 1.55 - tt * 5.6) * amp,
            Math.atan2(-ca, -sa)];
  };

  /* Le bout du museau, pour accrocher le souffle. */
  const dragonMuzzle = (orbit, radius, yBase, tt) => {
    const sp = dragonSpine(0, orbit, radius, yBase, tt);
    const o = dragonRot(24.0 * DRAGON_U, 2.0 * DRAGON_U, 0, 0, 0, sp[3]);
    return [sp[0] + o[0], sp[1] + o[2], sp[2] + o[1]];
  };

  /* Une demi-voilure : le panneau « skin » de 56 x 56 unites du modele vanilla. Vanilla
     le peint d'une texture ; ici il n'y a que des cubes. Un panneau plein se lirait comme
     un drap et le damier essaye d'abord se lisait comme un damier : la membrane est donc
     une CAGE — cinq doigts, un bord de fuite, une toile ajouree entre eux. */
  const dragonWing = (pf, pl, py, bank, yaw, side, span, taper0, al, alSoft) => {
    const chord = 56 * span;
    for (let d = 0; d < 5; d++) {
      /* d = 0 est le BOUT, d = 4 l'epaule : la voilure s'effile vers l'exterieur. C'est
         cette oblique qui fait lire « aile » plutot que « echelle ». */
      const mx = (-56 + (d + 0.5) * 56 / 5) * side;
      const ch = chord * (taper0 + (1 - taper0) * (d + 0.5) / 5);
      dragonBox(pf, pl, py, 0, bank, yaw, mx, 0, 2 + ch * 0.5,
                1.6, 1.6, ch, (d === 4) ? HORIZON : SLATE, al);
      dragonBox(pf, pl, py, 0, bank, yaw, mx, 0, 2 + ch * 0.5,
                56 / 5 * 0.88, 0.8, ch * 0.88, BASALT, alSoft);
      /* La marche du bord de fuite : sans elle l'aile s'effiloche en cinq tiges. */
      dragonBox(pf, pl, py, 0, bank, yaw, mx, 0, 2 + ch, 56 / 5, 2.2, 2.2, HALO, al);
    }
    /* Le doigt TERMINAL prolonge l'os au-dela de la derniere cote et ferme la pointe :
       c'est lui qu'on cherche du regard pour savoir ou l'aile s'arrete. */
    if (taper0 < 0.20) {
      for (let k = 1; k <= 3; k++) {
        const h = k / 3;
        dragonBox(pf, pl, py, 0, bank, yaw, (-56 - h * 9) * side, 0,
                  2 + chord * 0.10 * (1 - h),
                  3.4 - h * 2.0, 1.8 - h * 0.9, 3.4 - h * 2.0,
                  k === 3 ? GLACIER : SLATE, al);
      }
    }
  };

  const dragon = (orbit, radius, yBase, tt, gape, glow, a) => {
    const al = opq(a); if (al <= 0) return;
    const alSoft = opq(a * 0.90);
    const segStep = DRAGON_SEG * DRAGON_U;

    /* LA QUEUE : douze fois la boite du cou. Dessinee avant le corps pour que celui-ci la
       recouvre la ou elles se chevauchent — vanilla fait pareil. */
    /* Le pas SUIT l'affinement : a pas constant une queue qui retrecit finit en
       pointille. Les douze segments restent jointifs du bassin a la pointe. */
    let sTail = 98.0 * DRAGON_U;
    for (let i = 0; i < 12; i++) {
      const taper = 1 - 0.062 * i;
      sTail += segStep * taper;
      const sp = dragonSpine(sTail, orbit, radius, yBase, tt);
      dragonBox(sp[0], sp[1], sp[2], 0, 0, sp[3], 0, 0, 0,
                10 * taper, 10 * taper, 10 * taper, (i % 3 === 0) ? SLATE : BASALT, alSoft);
      dragonBox(sp[0], sp[1], sp[2], 0, 0, sp[3], 0, -7 * taper, 0,
                2 * taper, 4 * taper, 6 * taper, (i % 2 === 0) ? HORIZON : HALO, al);
    }

    /* LE CORPS : 24 x 24 x 64. */
    const bsp = dragonSpine(50.0 * DRAGON_U + 16.0 * DRAGON_U, orbit, radius, yBase, tt);
    const bodyYaw = bsp[3], bodyF = bsp[0], bodyL = bsp[1], bodyY = bsp[2];
    dragonBox(bodyF, bodyL, bodyY, 0, 0, bodyYaw, 0, 8, 0, 24, 24, 64, BASALT, al);
    for (let k = 0; k < 3; k++) {
      dragonBox(bodyF, bodyL, bodyY, 0, 0, bodyYaw, 0, -7, -4 + k * 20, 2, 6, 12,
                HORIZON, al);
    }

    /* LES AILES. Le battement tourne autour de l'axe du nez. */
    /* 0,52 rad etait un fremissement ; a 0,95 le battement devient l'animation
       principale de la bete, ce qu'il est chez vanilla aussi. */
    const flap = Math.sin(tt * (4.6 + 3.2 * glow) - 0.6);
    for (let wing = 0; wing < 2; wing++) {
      const side = wing === 0 ? 1 : -1;
      const bank = (0.10 + 0.95 * flap) * side;
      let piv = dragonRot(-2.0 * DRAGON_U, -1.0 * DRAGON_U, -12.0 * DRAGON_U * side,
                          0, 0, bodyYaw);
      const wf = bodyF + piv[0], wl = bodyL + piv[2], wy = bodyY + piv[1];
      dragonBox(wf, wl, wy, 0, bank, bodyYaw, -28 * side, 0, 0, 56, 8, 8, SLATE, al);
      dragonWing(wf, wl, wy, bank, bodyYaw, side, 1.0, 0.55, al, alSoft);
      const tipBank = bank + 0.70 * side * (0.5 + 0.5 * flap);
      piv = dragonRot(0, 0, -56.0 * DRAGON_U * side, 0, bank, bodyYaw);
      const tf = wf + piv[0], tl = wl + piv[2], ty = wy + piv[1];
      dragonBox(tf, tl, ty, 0, tipBank, bodyYaw, -28 * side, 0, 0, 56, 4, 4, SLATE, al);
      /* Le BOUT s'effile presque a rien : sans pointe la voilure se terminait au
         carre, et c'est ce que les captures montraient — une aile sans bout. */
      dragonWing(tf, tl, ty, tipBank, bodyYaw, side, 0.95, 0.08, al, alSoft);
    }

    /* LES PATTES, repliees : la bete vole, elle ne marche pas. */
    const tuck = 0.85;
    for (let leg = 0; leg < 4; leg++) {
      const rear = leg >= 2, side = (leg % 2 === 0) ? 1 : -1;
      const px = (rear ? -16 : -12) * side, py = rear ? 16 : 20, pz = rear ? 42 : 2;
      let piv = dragonRot(-pz * DRAGON_U, (-py + 4) * DRAGON_U, px * DRAGON_U,
                          0, 0, bodyYaw);
      const lf = bodyF + piv[0], ll2 = bodyL + piv[2], ly = bodyY + piv[1];
      const thighW = rear ? 16 : 8, thighH = rear ? 32 : 24;
      dragonBox(lf, ll2, ly, tuck, 0, bodyYaw, 0, thighH * 0.5 - 4, 0,
                thighW, thighH, thighW, BASALT, alSoft);
      piv = dragonRot(0, -thighH * DRAGON_U, 0, tuck, 0, bodyYaw);
      const kf = lf + piv[0], kl = ll2 + piv[2], ky = ly + piv[1];
      const shinW = rear ? 12 : 6, shinH = rear ? 32 : 24;
      dragonBox(kf, kl, ky, -tuck * 1.5, 0, bodyYaw, 0, shinH * 0.5, rear ? 6 : 0,
                shinW, shinH, shinW, SLATE, alSoft);
      piv = dragonRot(0, -shinH * DRAGON_U, 0, -tuck * 1.5, 0, bodyYaw);
      dragonBox(kf + piv[0], kl + piv[2], ky + piv[1], -tuck * 0.4, 0, bodyYaw,
                0, rear ? 2 : 1.4, rear ? -5 : -3,
                rear ? 11 : 6, rear ? 4 : 3, rear ? 14 : 11, HALO, al);
    }

    /* LE COU : cinq fois la meme boite. */
    for (let i = 5; i >= 1; i--) {
      const sp = dragonSpine(i * segStep, orbit, radius, yBase, tt);
      dragonBox(sp[0], sp[1], sp[2], 0, 0, sp[3], 0, 0, 0, 10, 10, 10,
                (i % 2 === 0) ? SLATE : BASALT, alSoft);
      dragonBox(sp[0], sp[1], sp[2], 0, 0, sp[3], 0, -7, 0, 2, 4, 6, HORIZON, al);
    }

    /* LA TETE. */
    const hsp = dragonSpine(0, orbit, radius, yBase, tt);
    const hYaw = hsp[3], hNose = -0.18 * glow;
    const hf2 = hsp[0], hl2 = hsp[1], hy2 = hsp[2];
    dragonBox(hf2, hl2, hy2, hNose, 0, hYaw, 0, 0, -2, 16, 16, 16, BASALT, al);
    dragonBox(hf2, hl2, hy2, hNose, 0, hYaw, 0, 1.5, -16, 12, 5, 16, SLATE, al);
    for (let si = 0; si < 2; si++) {
      const sgn = si === 0 ? -1 : 1;
      dragonBox(hf2, hl2, hy2, hNose, 0, hYaw, sgn * 4, -10, -1, 2, 4, 6, HORIZON, al);
      dragonBox(hf2, hl2, hy2, hNose, 0, hYaw, sgn * 4, -2, -20, 2, 2, 4,
                glow > 0.5 ? SINGULAR : GLACIER, al);
      /* Les yeux sont dans la TEXTURE vanilla, pas dans le modele : il faut les poser a la
         main, sinon le dragon n'a pas de regard et on ne sait pas qu'il vise. */
      dragonBox(hf2, hl2, hy2, hNose, 0, hYaw, sgn * 8, -4, -6, 1.5, 3, 4,
                glow > 0.35 ? SINGULAR : HORIZON, al);
    }
    const jaw = 0.12 + 0.55 * gape;
    const jp = dragonRot(8.0 * DRAGON_U, -4.0 * DRAGON_U, 0, hNose, 0, hYaw);
    dragonBox(hf2 + jp[0], hl2 + jp[2], hy2 + jp[1], hNose - jaw, 0, hYaw,
              0, 2, -8, 12, 4, 16, BASALT, al);

    /* LA CHARGE DU SOUFFLE, dans la gueule. */
    if (glow > 0.03) {
      const mz2 = dragonMuzzle(orbit, radius, yBase, tt);
      const r = 0.045 + 0.085 * glow;
      for (let k = 0; k < 7; k++) {
        const ang = k * TAU / 7 + tt * 9.0;
        box(mz2[0] + Math.cos(ang) * r, mz2[1] + Math.sin(ang) * r,
            mz2[2] - jaw * 0.10 + Math.sin(ang * 2) * r * 0.6,
            0.026, 0.026, 0.026, glow > 0.70 ? SINGULAR : GLACIER, opq(a * glow));
      }
    }
  };

  const prismRay = (k, spin, w) => {
    if (w <= 0.06) return null;
    const PT = VOID_SHAPE.PRISM_TILT;
    /* Le faisceau incident est un CYLINDRE : sept points d'entree, sept faces, sept
       incidences. Par le centre, un corps symetrique renvoie tout dans l'axe. */
    const off = k * (TAU / 7) + 0.40, spread = 0.62 * w;
    const o = unspinPoint(-3, Math.cos(off) * spread, Math.sin(off) * spread, PT, spin);
    const d = unspinPoint(1, 0, 0, PT, spin);
    let hit = octaHit(o[0], o[1], o[2], d[0], d[1], d[2], w);
    if (!hit) return null;
    const n = 1.14 + k * 0.08;
    let px = o[0] + d[0] * hit[0], pl = o[1] + d[1] * hit[0], py = o[2] + d[2] * hit[0];
    const entry = spinPoint(px, pl, py, PT, spin);
    let r = refract(d[0], d[1], d[2], hit[1], hit[2], hit[3], 1 / n);
    let ix = r.d[0], il = r.d[1], iy = r.d[2], escaped = false;
    for (let bounce = 0; bounce < 3 && !escaped; bounce++) {
      hit = octaHit(px + ix * 0.002, pl + il * 0.002, py + iy * 0.002, ix, il, iy, w);
      if (!hit) break;
      px += ix * hit[4]; pl += il * hit[4]; py += iy * hit[4];
      r = refract(ix, il, iy, hit[5], hit[6], hit[7], n);
      ix = r.d[0]; il = r.d[1]; iy = r.d[2]; escaped = r.out;
      if (!escaped && bounce === 2) { ix = hit[5]; il = hit[6]; iy = hit[7]; escaped = true; }
    }
    const P = spinPoint(px, pl, py, PT, spin);
    const D = spinPoint(ix, il, iy, PT, spin);
    const len = Math.sqrt(D[0] * D[0] + D[1] * D[1] + D[2] * D[2]);
    if (len < 1e-4) return null;
    return { e: entry, p: P, d: [D[0] / len, D[1] / len, D[2] / len] };
  };

  // =======================================================================================
  //  I. SPHÈRE DE DISTORSION — 160 ticks
  // =======================================================================================
  const renderWarp = (a) => {
    const rs = VOID_SHAPE.warpHorizon(t);
    const tip = smooth(clamp01((t - 0.46) / 0.24));
    const snap = clamp01((t - 0.70) / 0.22);
    const rebound = snap <= 0 ? 0 : Math.sin(snap * PI * 1.6) * (1 - snap) * 0.55;
    const yc = VOID_SHAPE.WARP_YC;

    // ---- 1. LA GRILLE TÉMOIN --------------------------------------------------------
    const life = clamp01(t / 0.07) * (1 - clamp01((t - 0.88) / 0.12));
    if (life > 0.01) {
      const n = 9, step = 0.78, half = (n - 1) * 0.5 * step;
      for (let pass = 0; pass < 2; pass++) {
        for (let row = 0; row < n; row++) {
          const fixed = -half + row * step;
          const seg = 24;
          let prevF = 0, prevL = 0, prevY = 0, prevIn = false;
          for (let s = 0; s <= seg; s++) {
            const u = -half + (s / seg) * (half * 2);
            const f0 = pass === 0 ? u : fixed;
            const l0 = pass === 0 ? fixed : u;
            const r0 = Math.sqrt(f0 * f0 + l0 * l0);
            if (r0 < 0.0001) continue;
            const pull = rs * rs * 0.62 / Math.max(r0, 0.55);
            const r1 = r0 - pull * (1 + rebound);
            const swallowed = r1 <= rs * 0.98;
            const k = r1 / r0;
            let f1 = f0 * k;
            const l1 = l0 * k;
            // Le disque EVASE et GONDOLE : une feuille d'epaisseur nulle disparait par
            // la tranche, piege deja paye par la nappe de l'Eau.
            const flare = (0.035 + 0.085 * clamp01(r1 / 3.2)) * Math.sin(Math.atan2(l1, f1) * 2 + t * 5);
            const yVert = yc + l1 * 0.72;
            const y1 = yVert + (yc + flare - yVert) * tip;
            let l1m = l1 * tip;
            if (tip > 0.01) {
              const spin = tip * (0.9 + 2.6 / Math.max(0.45, r1)) * (t - 0.46) * 2.3;
              const cs = Math.cos(spin), sn = Math.sin(spin);
              const ff = f1 * cs - l1m * sn;
              l1m = f1 * sn + l1m * cs;
              f1 = ff;
            }
            if (s > 0 && prevIn && !swallowed) {
              const bend = clamp01(pull / 0.55);
              const w = (0.020 + 0.020 * bend) * life;
              const rgb = bend > 0.62 ? GLACIER : (bend > 0.28 ? HORIZON : (bend > 0.10 ? HALO : SLATE));
              segment(prevF, prevL, prevY, f1, l1m, y1, w, rgb,
                      opq(a * (0.52 + 0.48 * bend) * life), 0.040 + 0.105 * (1 - bend));
            }
            prevF = f1; prevL = l1m; prevY = y1; prevIn = !swallowed;
          }
        }
      }
      for (let ix = 0; ix < n; ix++) {
        for (let iz = 0; iz < n; iz++) {
          const f0 = -half + ix * step, l0 = -half + iz * step;
          const r0 = Math.sqrt(f0 * f0 + l0 * l0);
          if (r0 < 0.0001) continue;
          const pull = rs * rs * 0.62 / Math.max(r0, 0.55);
          const r1 = r0 - pull * (1 + rebound);
          if (r1 <= rs * 0.98) continue;
          const k = r1 / r0;
          let f1 = f0 * k;
          const l1 = l0 * k;
          const flare = (0.035 + 0.085 * clamp01(r1 / 3.2)) * Math.sin(Math.atan2(l1, f1) * 2 + t * 5);
          const y1 = yc + l1 * 0.72 * (1 - tip) + flare * tip;
          let l1m = l1 * tip;
          if (tip > 0.01) {
            const spin = tip * (0.9 + 2.6 / Math.max(0.45, r1)) * (t - 0.46) * 2.3;
            const cs = Math.cos(spin), sn = Math.sin(spin);
            const ff = f1 * cs - l1m * sn;
            l1m = f1 * sn + l1m * cs;
            f1 = ff;
          }
          const near = clamp01((1.3 - (r1 - rs)) / 1.3);
          const sz = (0.034 + 0.020 * near) * life;
          box(f1, l1m, y1, sz, sz, sz, near > 0.55 ? GLACIER : HORIZON,
              opq(a * (0.60 + 0.40 * near) * life));
        }
      }
    }

    // ---- 2. L'HORIZON : TROIS GRANDS CERCLES, et RIEN dedans -----------------------
    // Treize paralleles de latitude donnaient une bouillie grise vue de l'exterieur (on
    // voyait la face avant ET la face arriere). Trois cercles orthogonaux disent « sphere »
    // et laissent l'interieur vraiment vide.
    if (rs > 0.03) {
      const pts = Math.max(18, Math.round(rs * 42));
      for (let plane = 0; plane < 3; plane++) {
        for (let j = 0; j < pts; j++) {
          const ang = (j + plane * 0.33) / pts * TAU;
          const ca = Math.cos(ang) * rs, sa = Math.sin(ang) * rs;
          let pf, pl, py;
          if (plane === 0) { pf = ca; pl = sa; py = 0; }
          else if (plane === 1) { pf = ca; pl = 0; py = sa; }
          else { pf = 0; pl = ca; py = sa; }
          const flick = 0.78 + 0.22 * Math.sin(ang * 7 + t * 52 + plane * 2.1);
          box(pf, pl, yc + py, 0.028, 0.028, 0.028,
              flick > 0.93 ? GLACIER : (plane === 0 ? HALO : SLATE), opq(a * 0.92));
        }
      }
    }

    // ---- 3. L'ANNEAU D'EINSTEIN ------------------------------------------------------
    const ringLife = clamp01((t - 0.28) / 0.08) * (1 - clamp01((t - 0.70) / 0.08));
    if (ringLife > 0.01 && rs > 0.08) {
      const pts = 72, rr = rs * 1.14;
      for (let i = 0; i < pts; i++) {
        const ang = (i + 0.5) / pts * TAU;
        const th = 0.016 + 0.010 * Math.sin(ang * 3 + t * 9);
        box(Math.cos(ang) * rr, Math.sin(ang) * rr * tip,
            yc + Math.sin(ang) * rr * 0.72 * (1 - tip), th, th, th, SINGULAR, opq(a * ringLife));
        box(Math.cos(ang) * rr * 1.10, Math.sin(ang) * rr * 1.10 * tip,
            yc + Math.sin(ang) * rr * 1.10 * 0.72 * (1 - tip),
            th * 0.62, th * 0.62, th * 0.62, GLACIER, opq(a * ringLife * 0.85));
      }
    }

    // ---- 4. L'ACCRÉTION ---------------------------------------------------------------
    const acc = clamp01((t - 0.26) / 0.40);
    if (acc > 0.001 && rs > 0.05) {
      for (let i = 0; i < 30; i++) {
        const h1 = hash(salt + 31, i * 13), h2 = hash(salt + 32, i * 17), h3 = hash(salt + 33, i * 19);
        const p = clamp01((acc - h1 * 0.42) / (0.52 + 0.30 * h2));
        if (p <= 0 || p >= 1) continue;
        const r0 = 1.9 + 1.5 * h2;
        const r = rs + (r0 - rs) * Math.exp(-3.1 * p);
        const ang = h3 * TAU + p * (5.4 + 2.2 * h1);
        const yy = yc + (h2 - 0.5) * 1.5 * (1 - p);
        spaghetti(ang, r, yy, rs, 0.070 + 0.032 * h1, a * (1 - 0.25 * p), i);
      }
    }

    // ---- 5. LA RESTITUTION -------------------------------------------------------------
    const give = clamp01((t - 0.78) / 0.22);
    if (give > 0.001) {
      const ease = smooth(give);
      for (let i = 0; i < 40; i++) {
        const h1 = hash(salt + 41, i * 11), h2 = hash(salt + 42, i * 13), h3 = hash(salt + 43, i * 17);
        const spread = 0.42 + 0.55 * h1;
        const d = (0.30 + 4.3 * ease) * (0.45 + 0.75 * h2);
        const ang = h3 * TAU;
        const ff = d + Math.cos(ang) * d * spread * 0.30;
        const ll = Math.sin(ang) * d * spread * 0.52;
        const yy = yc + Math.cos(ang * 1.7 + h1 * 4) * d * spread * 0.46 - GRAVITY * 0.030 * ease * ease;
        if (yy < GROUND_Y + 0.02) continue;
        const sz = (0.052 + 0.030 * h2) * (1 - ease * 0.72);
        if (sz <= 0.006) continue;
        box(ff, ll, yy, sz, sz, sz, h1 > 0.72 ? ENDSTONE : INVERT, opq(a * (1 - ease * 0.3)));
      }
      const rr = 0.4 + 4.8 * ease, pts = 52;
      for (let i = 0; i < pts; i++) {
        const ang = (i + 0.5) / pts * TAU;
        const wob = 1 + 0.09 * Math.sin(ang * 4 + salt * 0.011);
        const th = 0.072 * (1 - ease * 0.80);
        if (th <= 0.006) continue;
        box(Math.cos(ang) * rr * wob, Math.sin(ang) * rr * wob, GROUND_Y + 0.016,
            th, SLAB_H, th, i % 5 === 0 ? INVERT : HORIZON, opq(a * 0.90));
      }
    }
  };

  // =======================================================================================
  //  II. PRISME DU DRAGON — 190 ticks — il DISTRIBUE
  // =======================================================================================
  /* Sept faisceaux, sept corps : les ticks 62 + 9k du serveur, traces par REFRACTION reelle
     (Snell-Descartes, un indice par faisceau, reflexion totale comprise). */
  const renderPrism = (a) => {
    const V = VOID_SHAPE;
    const body = V.prismBody(t), spin = V.prismSpin(t), w = V.PRISM_W * body;
    const broken = clamp01((t - V.PRISM_BREAK) / 0.10);
    const floorY = GROUND_Y + 0.05;
    const drop = clamp01(t / 0.085);
    const stair = (Math.floor(drop * 3) + smooth(fract(drop * 3))) / 3;
    const yc = V.PRISM_YC + (1 - stair) * 4.6;

    /* ---- 1. LE RECUL : sept coups de canon dans sept directions ---- */
    let recF = 0, recL = 0, recY = 0;
    for (let k = 0; k < 7; k++) {
      const fire = V.PRISM_FIRE0 + k * V.PRISM_FIRE_STEP;
      if (t < fire || t > V.PRISM_BREAK) continue;
      const ray = prismRay(k, V.prismSpin(fire), V.PRISM_W * V.prismBody(fire));
      if (!ray) continue;
      const kick = (1 - smooth((t - fire) / 0.085)) * 0.055;
      recF -= ray.d[0] * kick; recL -= ray.d[1] * kick; recY -= ray.d[2] * kick;
    }

    /* ---- 2. LE TRAIT D'ENTREE — tick 42 ---- */
    {
      const come = clamp01((t - 0.175) / 0.046);
      const live = 1 - smooth((t - 0.62) / 0.10);
      if (come > 0 && live > 0.01) {
        const head = -3.4 + (3.4 - w * 0.60) * smooth(come);
        const beat = 0.74 + 0.26 * Math.sin(t * 58);
        const spread = 0.62 * V.PRISM_W;
        for (let f = -3.4; f <= head; f += 0.085) {
          const grow = clamp01((f + 3.4) / 0.9);
          /* 0,140 de large : deux fois l'arete du cristal. Elle reste la chose la plus
             epaisse de la signature, mais une brique n'est pas un trait. */
          const th = (0.024 + 0.032 * grow) * live;
          box(f, 0, yc, th, th, th, grow > 0.82 ? SINGULAR : GLACIER,
              opq(a * (0.55 + 0.45 * grow) * beat * live));
        }
        /* Les sept fils, la ou les sept rayons entrent : sept choses paralleles a
           l'arrivee, sept choses divergentes au depart. */
        /* Un pas de 0,13 pour des cubes de 0,048 : plus de vide que de matiere, et ces
           sept lignes de tirets etaient LE pointille des captures. Le pas passe sous la
           taille du cube, et les fils ne courent plus que sur les deux derniers blocs,
           la ou ils convergent vers les sept points d'entree. */
        const tail = Math.max(-3.4, head - 2.0);
        for (let k = 0; k < 7; k++) {
          const off = k * (TAU / 7) + 0.40;
          const ol = Math.cos(off) * spread, oy = Math.sin(off) * spread;
          for (let f = tail; f <= head; f += 0.038) {
            const grow = clamp01((f + 3.4) / 1.2);
            box(f, ol, yc + oy, 0.020, 0.020, 0.020,
                k % 3 === 0 ? GLACIER : (k % 3 === 1 ? HORIZON : HALO),
                opq(a * (0.30 + 0.45 * grow) * live));
          }
        }
      }
    }

    /* ---- 1 bis. LE DRAGON : la source du souffle, et l'acteur de la figure ---- */
    {
      const enter = smooth((t - 0.105) / 0.075);
      const leave = smooth((t - V.PRISM_BURST) / 0.09);
      const present = enter * (1 - leave);
      if (present > 0.01) {
        let gape = 0, glow = 0;
        for (let k = 0; k < 7; k++) {
          const fire = V.PRISM_FIRE0 + k * V.PRISM_FIRE_STEP;
          const before = clamp01((t - (fire - 0.055)) / 0.055);
          const after = 1 - clamp01((t - fire) / 0.045);
          const g = before * after;
          if (g > glow) glow = g;
          const open = before * (1 - clamp01((t - fire) / 0.085));
          if (open > gape) gape = open;
        }
        const roar = clamp01((t - V.PRISM_BREAK) / 0.045)
                   * (1 - clamp01((t - V.PRISM_BREAK - 0.06) / 0.07));
        gape = Math.max(gape, roar);
        glow = Math.max(glow, roar * 0.65);

        const orbit = t * 4.3 + 0.9;
        const dive = 0.42 * glow;
        const radius = (3.25 - 0.55 * smooth((t - V.PRISM_FIRE0) / 0.30)) * (1 - 0.10 * dive)
                     + leave * 2.6;
        const yBase = yc + 0.95 - dive * 0.55 + (1 - enter) * 3.4 + leave * 1.8;
        dragon(orbit, radius, yBase, t, gape, glow, a * present);

        if (glow > 0.12) {
          const head = dragonMuzzle(orbit, radius, yBase, t);
          /* 76 pas au lieu de 38 : fin ET continu, c'est un pas plus court. */
          const steps = 76;
          for (let k = 1; k <= steps; k++) {
            const u = k / steps;
            const swell = Math.sin(u * PI) * 0.10 * glow;
            const th = (0.021 + 0.008 * glow) * (1 - u * 0.45) + swell * 0.10;
            box(head[0] + (0 - head[0]) * u, head[1] + (0 - head[1]) * u,
                head[2] + (yc - head[2]) * u + Math.sin(u * PI) * 0.18,
                th, th, th,
                u > 0.78 ? SINGULAR : (k % 3 === 0 ? GLACIER : HORIZON),
                opq(a * glow * (0.45 + 0.55 * u) * present));
          }
        }
      }
    }

    /* ---- 2 bis. LES TRAJETS INTERNES : le rayon est coude DANS le verre ---- */
    if (body > 0.05 && t > 0.19) {
      for (let k = 0; k < 7; k++) {
        const fire = V.PRISM_FIRE0 + k * V.PRISM_FIRE_STEP;
        const charge = clamp01((t - 0.19) / Math.max(0.02, fire - 0.19));
        const after = 1 - smooth((t - fire) / 0.22);
        const glow = charge * (0.35 + 0.65 * after);
        if (glow < 0.04) continue;
        const ray = prismRay(k, spin, w);
        if (!ray) continue;
        segment(ray.e[0], ray.e[1], yc + ray.e[2], ray.p[0], ray.p[1], yc + ray.p[2],
                0.022 + 0.014 * glow,
                glow > 0.80 ? SINGULAR : (k % 2 === 0 ? GLACIER : HORIZON),
                opq(a * (0.30 + 0.60 * glow)), 0.055);
      }
    }

    /* ---- 3. LES SEPT FAISCEAUX ---- */
    for (let k = 0; k < 7; k++) {
      const fire = V.PRISM_FIRE0 + k * V.PRISM_FIRE_STEP;
      if (t < fire) continue;
      const age = t - fire;
      const live = 1 - smooth((t - V.PRISM_BREAK + 0.02) / 0.10);
      if (live <= 0.01) continue;
      const ray = prismRay(k, V.prismSpin(fire), V.PRISM_W * V.prismBody(fire));
      if (!ray) continue;
      let reach = 3.9 * smooth(age / 0.055);
      if (reach < 0.08) continue;
      const bf = ray.p[0], bl = ray.p[1], by = V.PRISM_YC + ray.p[2];
      const rd = ray.d;
      let grounded = false;
      if (rd[2] < -0.03) {
        const toFloor = (GROUND_Y + 0.03 - by) / rd[2];
        if (toFloor > 0.05 && toFloor < reach) { reach = toFloor; grounded = true; }
      }
      let ux, ul, uy;
      if (Math.abs(rd[2]) < 0.90) { ux = -rd[1]; ul = rd[0]; uy = 0; }
      else { ux = 1; ul = 0; uy = 0; }
      let un = Math.sqrt(ux * ux + ul * ul + uy * uy);
      if (un < 1e-4) { ux = 1; ul = 0; uy = 0; un = 1; }
      ux /= un; ul /= un; uy /= un;
      const wx = rd[1] * uy - rd[2] * ul, wl = rd[2] * ux - rd[0] * uy, wy = rd[0] * ul - rd[1] * ux;
      /* LE PAS EST PLUS PETIT QUE LE CUBE : seule facon d'obtenir un trait plein.
         L'ancienne couronne de cinq cubes espacee de 0,155 etait large ET trouee. */
      const steps = Math.max(6, Math.min(110, Math.round(reach / 0.042)));
      /* HALO sur un trait de 0,05 bloc disparait : deux tons clairs seulement, sinon
         un faisceau sur trois ne se voit pas et les autres ont l'air pointilles. */
      const tone = (k & 1) === 0 ? GLACIER : HORIZON;
      for (let si = 0; si <= steps; si++) {
        const u = si / steps, d = u * reach;
        const ff = bf + rd[0] * d, ll = bl + rd[1] * d, yy = by + rd[2] * d;
        const core = 0.025 * (1 - 0.30 * u);
        box(ff, ll, yy, core, core, core, si === steps ? SINGULAR : tone,
            opq(a * (0.52 + 0.48 * (1 - u)) * live));
        /* Pas d'etincelle satellite : au banc, un cube decale tous les quatre pas
           dessine une SECONDE ligne trouee, et c'est elle que l'oeil suit. La vrille
           passe par la TEINTE du coeur. */
        if (si % 5 === 0 && si < steps) {
          const shimmer = 0.5 + 0.5 * Math.sin(d * 2.4 + t * 6 + k);
          /* MEME taille que le coeur : grossie, la perle se detachait et le faisceau se
             lisait comme un chapelet. Seule la teinte change. */
          box(ff, ll, yy, core, core, core,
              shimmer > 0.72 ? SINGULAR : GLACIER, opq(a * (0.55 + 0.40 * shimmer) * live));
        }
      }
      const ef = bf + rd[0] * reach, el = bl + rd[1] * reach, ey = by + rd[2] * reach;
      const flash = 1 - clamp01(age / 0.085);
      if (grounded) {
        const rr = 0.14 + 0.52 * clamp01(age / 0.16);
        const ring = Math.max(10, Math.round(rr * 22));
        for (let i = 0; i < ring; i++) {
          const ang = i / ring * TAU;
          box(ef + Math.cos(ang) * rr, el + Math.sin(ang) * rr, GROUND_Y + 0.016,
              0.040, SLAB_H, 0.040, (i & 1) === 0 ? tone : HALO,
              opq(a * (0.80 - 0.55 * clamp01(age / 0.22)) * live));
        }
      }
      if (flash > 0.02) {
        for (let i = 0; i < 6; i++) {
          const ang = i * (TAU / 6) + t * 3, rr = 0.10 + 0.26 * (1 - flash);
          box(ef + Math.cos(ang) * rr, el + Math.sin(ang) * rr, ey + (i % 2 === 0 ? 0.08 : -0.08),
              0.036, 0.036, 0.036, SINGULAR, opq(a * flash * live));
        }
      }
    }

    /* ---- 3 bis. CE QU'IL Y A DANS LE CRISTAL : du RIEN, en neuf cubes ---- */
    if (body > 0.05) {
      const scatter = clamp01((t - V.PRISM_BREAK) / 0.14);
      for (let k = 0; k < 9; k++) {
        const u = (k + 0.5) / 9;
        const phi = Math.acos(1 - 2 * u), theta = k * 2.39996 + t * 1.9;
        const rr = w * 0.46 * (0.45 + 0.55 * hash(salt, k * 71)) * (1 + scatter * 5.5);
        const sinp = Math.sin(phi);
        const px = Math.cos(theta) * sinp * rr, pl = Math.sin(theta) * sinp * rr;
        const py = Math.cos(phi) * rr;
        const half = (0.052 + 0.022 * hash(salt, k * 73)) * (1 - scatter * 0.5);
        if (half <= 0.006) continue;
        let fy = yc + py + recY
               + (scatter > 0 ? -0.5 * GRAVITY * Math.pow(t - V.PRISM_BREAK, 2) * 9 : 0);
        if (fy < floorY) fy = floorY;
        abyss(px + recF, pl + recL, fy, half);
        box(px + recF, pl + recL, fy, half * 1.16, half * 1.16, half * 1.16,
            NOTHING, opq(a * (1 - scatter * 0.35)));
      }
    }

    /* ---- 4. LE CRISTAL : six sommets, douze aretes, et la fracture du tick 150 ---- */
    {
      const vert = [];
      for (let v = 0; v < 6; v++) {
        const vf = v === 0 ? w : (v === 1 ? -w : 0);
        const vl2 = v === 2 ? w : (v === 3 ? -w : 0);
        const vy2 = v === 4 ? w : (v === 5 ? -w : 0);
        const p = spinPoint(vf, vl2, vy2, V.PRISM_TILT, spin);
        vert.push(p[0] + recF, p[1] + recL, yc + p[2] + recY);
      }
      const edges = [0, 2, 2, 1, 1, 3, 3, 0, 0, 4, 2, 4, 1, 4, 3, 4, 0, 5, 2, 5, 1, 5, 3, 5];
      const sec = (t - V.PRISM_BREAK) * 9.5;
      for (let e = 0; e < 12; e++) {
        const i0e = edges[e * 2], i1e = edges[e * 2 + 1];
        let f0 = vert[i0e * 3], l0 = vert[i0e * 3 + 1], y0 = vert[i0e * 3 + 2];
        let f1 = vert[i1e * 3], l1 = vert[i1e * 3 + 1], y1 = vert[i1e * 3 + 2];
        let fade = 1;
        if (broken > 0) {
          const mf = (f0 + f1) * 0.5, ml = (l0 + l1) * 0.5, my = (y0 + y1) * 0.5;
          const out = Math.sqrt(mf * mf + ml * ml) + 0.001;
          const sp = 0.9 + 1.1 * hash(salt, e * 7);
          const nf = mf + mf / out * sp * sec, nl = ml + ml / out * sp * sec;
          const ny = ballistic(my, 1.1 + 1.6 * hash(salt, e * 13), sec, floorY);
          const roll = sec * (1.8 + 3.4 * hash(salt, e * 19));
          const cr = Math.cos(roll), sr = Math.sin(roll);
          const hf = (f1 - f0) * 0.5, hl = (l1 - l0) * 0.5, hy = (y1 - y0) * 0.5;
          const rf = hf * cr - hl * sr, rl = hf * sr + hl * cr;
          f0 = nf - rf; f1 = nf + rf; l0 = nl - rl; l1 = nl + rl; y0 = ny - hy; y1 = ny + hy;
          fade = 1 - smooth((t - V.PRISM_BREAK) / 0.20) * 0.75;
        }
        const equator = e < 4;
        /* 0,080 de large : deux fois le faisceau. L'arete ne doit pas peser plus que
           l'objet. Et la boucle est ecrite en clair : segment() plafonne a 26 cubes, une
           arete de 1,63 a ce pas en demande 34, l'octaedre serait devenu pointille. */
        const th = (equator ? 0.032 : 0.024) * (0.55 + 0.45 * body + 0.45 * broken);
        const tone = broken > 0 ? ((e & 1) === 0 ? BASALT : SLATE) : (equator ? GLACIER : HORIZON);
        const ef2 = f1 - f0, el2 = l1 - l0, ey2 = y1 - y0;
        const elen = Math.sqrt(ef2 * ef2 + el2 * el2 + ey2 * ey2);
        const en = Math.max(2, Math.min(60, Math.round(elen / Math.max(0.020, th * 1.55))));
        const ealpha = opq(a * 0.95 * fade);
        for (let es = 0; es <= en; es++) {
          const eu = es / en;
          box(f0 + ef2 * eu, l0 + el2 * eu, y0 + ey2 * eu, th, th, th, tone, ealpha);
        }
      }
      if (broken <= 0) {
        for (let v = 0; v < 6; v++) {
          const pulse = 0.70 + 0.30 * Math.sin(t * 19 + v * 1.7);
          box(vert[v * 3], vert[v * 3 + 1], vert[v * 3 + 2], 0.052, 0.052, 0.052,
              pulse > 0.92 ? SINGULAR : GLACIER, opq(a * pulse));
        }
      }
    }

    /* ---- 5. LES QUATRE ECLATS ARRACHES AU SOL : ils viennent du plancher, ils y retombent ---- */
    {
      const rise = smooth((t - 0.10) / 0.14);
      for (let i = 0; i < 4; i++) {
        const ang = i * (TAU / 4) + spin * 0.85, orbit = V.PRISM_W * 1.46;
        let sf = Math.cos(ang) * orbit, sl = Math.sin(ang) * orbit;
        const home = yc + Math.sin(spin * 0.7 + i) * 0.10;
        let sy = GROUND_Y + (home - GROUND_Y) * rise;
        if (broken > 0) {
          const fallSec = (t - V.PRISM_BREAK) * 9.5;
          sf += Math.cos(ang) * 1.25 * fallSec;
          sl += Math.sin(ang) * 1.25 * fallSec;
          sy = ballistic(home, 0.7, fallSec, floorY);
        }
        fragment(sf, sl, sy, 0.072, spin * 1.4 + i, ENDSTONE, ENDSTONE_DK,
                 opq(a * (0.35 + 0.65 * rise)));
      }
    }

    /* ---- 6. L'ECLAT — tick 170 ---- */
    {
      const burst = clamp01((t - V.PRISM_BURST) / 0.105);
      if (burst > 0) {
        const rr = 0.3 + 2.3 * smooth(burst);
        const ring = Math.max(22, Math.round(rr * 20));
        for (let i = 0; i < ring; i++) {
          const ang = i / ring * TAU, wob = 1 + 0.06 * Math.sin(ang * 7 + t * 9);
          box(Math.cos(ang) * rr * wob, Math.sin(ang) * rr * wob, GROUND_Y + 0.016,
              0.052, SLAB_H, 0.052, i % 5 === 0 ? INVERT : HORIZON,
              opq(a * (1 - burst) * 0.95));
        }
        for (let i = 0; i < 14; i++) {
          const u = (i / 14 + burst * 1.4) % 1, ang = i * 2.39996 + t * 4;
          const rr2 = 0.18 + 0.52 * u;
          box(Math.cos(ang) * rr2, Math.sin(ang) * rr2, GROUND_Y + 0.05 + u * 3.4,
              0.042, 0.042, 0.042, (i & 1) === 0 ? INVERT : GLACIER,
              opq(a * (1 - u) * (1 - burst * 0.5)));
        }
      }
    }
  };

  // =======================================================================================
  //  III. FAILLE DU NÉANT — 170 ticks — elle TRIE
  // =======================================================================================
  /* Une LIGNE de 8,6 blocs en travers du chemin de l'arme — le corridor du serveur, pas un
     disque. Propagation, profil de Griffith, fibres qui cassent, fermeture eclair. */
  const renderRift = (a) => {
    const V = VOID_SHAPE;
    const run = V.riftRun(t), gape = V.riftGape(t);
    const half = V.riftHalfLength(t), crown = V.riftCrown(t);
    const base = GROUND_Y + 0.03, lipMax = V.RIFT_LIP * gape;

    /* ---- 1. LA FELURE AU SOL : elle precede tout, elle survit a tout ---- */
    {
      const scar = clamp01((t - V.RIFT_CLOSE) / 0.18), reach = V.RIFT_REACH * run;
      const n = 38;
      let prevF = 0, prevL = -reach;
      for (let i = 0; i <= n; i++) {
        const u = -1 + 2 * i / n, ll = u * reach;
        const jag = (hash(salt, i * 31) - 0.5) * 0.18 * (1 - Math.abs(u) * 0.4);
        if (i > 0) {
          segment(prevF, prevL, GROUND_Y + 0.014, jag, ll, GROUND_Y + 0.014, 0.034,
                  scar > 0.5 ? BASALT : (Math.abs(u) < 0.5 ? HORIZON : HALO),
                  opq(a * (0.92 - 0.35 * scar)), 0.048);
        }
        prevF = jag; prevL = ll;
      }
      for (let b = 0; b < 7; b++) {
        const u = (hash(salt, b * 47) - 0.5) * 1.7;
        const side = (b & 1) === 0 ? 1 : -1;
        const len = (0.35 + 0.85 * hash(salt, b * 59)) * (1 - Math.abs(u) * 0.6);
        const ang = (0.5 + 0.7 * hash(salt, b * 71)) * side;
        const sl = u * reach;
        segment(0, sl, GROUND_Y + 0.014, Math.sin(ang) * len, sl + Math.cos(ang) * len * 0.5,
                GROUND_Y + 0.014, 0.026, scar > 0.5 ? ABYSS : SLATE, opq(a * 0.80), 0.055);
      }
    }

    /* Les deux machoires a un angle donne. Appelee une fois pour la position reelle,
       puis RIFT_GHOSTS fois pour les trainees — d'ou le mode « ghost », qui ne garde que
       le bord dente : c'est lui qui porte le mouvement. */
    const riftJaws = (half, base, ang, a, scale, ghost) => {
      if (a <= 1 || half <= 0.05) return;
      for (let side = -1; side <= 1; side += 2) {
        let have = false, pF = 0, pL = 0, pY = 0;
        for (let i = 0; i < V.RIFT_TEETH; i++) {
          /* Les dents d'une machoire tombent a MI-CHEMIN de celles de l'autre : les deux
             rangees s'imbriquent au lieu de se cogner. C'est a ca qu'on reconnait un
             piege a loup avant tout. */
          const shift = side > 0 ? 0 : 0.5;
          const u = -1 + 2 * (i + shift) / (V.RIFT_TEETH - 1 + 0.5);
          const prof = V.riftProfile(u);
          if (prof < 0.10) continue;
          const ll = u * half, reach = V.RIFT_JAW * (0.42 + 0.58 * prof);
          const tip = V.riftJawPoint(1, ang, reach);
          const tipF = side * (V.RIFT_HINGE + tip[0]), tipY = base + tip[1];

          if (!ghost) {
            let pf = side * V.RIFT_HINGE, py = base;
            for (let k = 1; k <= 7; k++) {
              const h = k / 7, p = V.riftJawPoint(h, ang, reach);
              const ff = side * (V.RIFT_HINGE + p[0]), yy = base + p[1];
              segment(pf, ll, py, ff, ll, yy, (0.044 - 0.016 * h) * scale,
                      (i % 2 === 0) ? SLATE : BASALT, opq(a * (0.72 + 0.28 * prof)), 0.038);
              pf = ff; py = yy;
            }
          }

          /* LA DENT : un eclat qui prolonge la tangente, allonge une fois sur deux —
             une rangee reguliere fait un peigne, pas une machoire. */
          const local = ang - V.RIFT_CURVE;
          const tl = 0.40 * ((i % 2 === 0) ? 1 : 0.68) * (0.45 + 0.55 * prof);
          for (let k = 1; k <= 3; k++) {
            const h = k / 3, sz = (0.062 - 0.014 * k) * scale;
            box(tipF + side * Math.sin(local) * tl * h, ll, tipY + Math.cos(local) * tl * h,
                sz, sz, sz, k === 3 ? SINGULAR : GLACIER, opq(a * (0.78 + 0.22 * prof)));
          }

          /* LA LEVRE relie les pointes : sans elle les dents flottent. */
          if (have) segment(pF, pL, pY, tipF, ll, tipY, 0.034 * scale, HORIZON,
                            opq(a * 0.80), 0.042);
          pF = tipF; pL = ll; pY = tipY; have = true;
        }
      }
    };

    if (half > 0.05 && crown > 0.05) {
      /* ---- 2. LES DEUX MACHOIRES ---- */
      const ang = V.riftJawAngle(t), jawSpeed = V.riftJawSpeed(t);
      riftJaws(half, base, ang, a, 1.0, false);

      /* ---- 2 bis. LES TRAINEES DE VITESSE. Pendant le claquement le bord dente
         parcourt deux radians en cinq ticks : sans trace, l'oeil ne voit que la position
         d'arrivee et le coup n'existe pas. Hors claquement, rien a payer. ---- */
      if (jawSpeed > 2.5) {
        const ghost = clamp01((jawSpeed - 2.5) / 22);
        for (let g = 1; g <= V.RIFT_GHOSTS; g++) {
          const past = V.riftJawAngle(Math.max(0, t - g * 0.0042));
          if (Math.abs(past - ang) < 0.012) continue;
          riftJaws(half, base, past, a * ghost * (1 - g / (V.RIFT_GHOSTS + 1)) * 0.55,
                   0.72, true);
        }
      }

      /* ---- 2 ter. LES RESSORTS. Un piege a loup se tend par deux ressorts de torsion
         poses aux deux bouts de la charniere : ils disent l'energie stockee. ---- */
      {
        const load = clamp01((ang - V.RIFT_SHUT) / Math.max(0.1, V.RIFT_OPEN - V.RIFT_SHUT));
        for (let end = -1; end <= 1; end += 2) {
          const ll = end * half * 0.98;
          for (let i = 0; i < 16; i++) {
            const v = i / 15, turn = v * TAU * (1.4 + 1.6 * load);
            const rr = 0.20 * (1 - 0.22 * load);
            box(Math.sin(turn) * rr, ll + end * v * 0.16 * (1 - 0.4 * load),
                base + 0.16 + Math.cos(turn) * rr, 0.030, 0.030, 0.030,
                load > 0.6 ? GLACIER : HALO, opq(a * (0.55 + 0.45 * load)));
          }
        }
      }

      /* ---- 2 quater. L'ONDE DE CHOC : un eclat blanc part du centre de la ligne de
         morsure et la parcourt dans les deux sens a vitesse finie. C'est ce qui donne au
         claquement sa TAILLE — sans lui, la machoire mord en silence. ---- */
      {
        const shock = t - V.RIFT_CLOSE - V.RIFT_SNAP * 0.82;
        if (shock > 0 && shock < 0.10) {
          const front = shock * 42, fade = 1 - clamp01(shock / 0.10);
          for (let i = 0; i < 26; i++) {
            const lu = -1 + 2 * i / 25, prof = V.riftProfile(lu);
            if (prof < 0.08) continue;
            const near = 1 - clamp01(Math.abs(Math.abs(lu) - front) / 0.30);
            if (near <= 0) continue;
            const jt = V.riftJawPoint(1, ang, V.RIFT_JAW * (0.42 + 0.58 * prof));
            const sz = 0.030 + 0.075 * near;
            box(0, lu * half, base + jt[1] * 0.96, sz, sz, sz,
                near > 0.55 ? SINGULAR : GLACIER, opq(a * fade * (0.35 + 0.65 * near)));
          }
        }
      }

      /* ---- 3. LE SPLASH DE VOID. Chaque gerbe est une parabole AVEC SA TRAINEE : sans
         trainee, une gerbe est une pluie de confettis. ---- */
      {
        const bite = t - V.RIFT_CLOSE - V.RIFT_SNAP * 0.75;
        if (bite > 0 && bite < 0.22) {
          const sec = bite * 9.0, fade = 1 - clamp01(bite / 0.22);
          for (let i = 0; i < 34; i++) {
            const seed = i * 173;
            const ll = (hash(salt, seed) - 0.5) * 2 * half * 0.96;
            const prof = V.riftProfile(clamp01(Math.abs(ll) / Math.max(0.2, half)));
            if (prof < 0.10) continue;
            const side = (i & 1) === 0 ? 1 : -1;
            const vf = side * (1.3 + 3.1 * hash(salt, seed + 1)) * (0.45 + 0.55 * prof);
            const vy = (2.6 + 4.2 * hash(salt, seed + 2)) * prof;
            const vl = (hash(salt, seed + 3) - 0.5) * 1.8;
            const y0 = base + V.RIFT_JAW * 0.42 * prof;
            for (let q = 3; q >= 0; q--) {
              const ss = sec - q * 0.055;
              if (ss <= 0) continue;
              const yy = ballistic(y0, vy, ss, GROUND_Y + 0.04);
              const sz = (0.055 - q * 0.009) * (0.5 + 0.5 * prof);
              const rgb = q === 0 ? (hash(salt, seed + 5) > 0.72 ? SINGULAR : GLACIER)
                                  : (q === 1 ? HORIZON : HALO);
              box(vf * ss, ll + vl * ss, yy, sz, sz, sz, rgb,
                  opq(a * fade * (1 - q * 0.19)));
            }
          }
          const ring = sec * 1.35;
          if (ring < 3.4) {
            for (let i = 0; i < 30; i++) {
              const ll = (-1 + 2 * i / 29) * half;
              const prof = V.riftProfile(clamp01(Math.abs(ll) / Math.max(0.2, half)));
              for (let side = -1; side <= 1; side += 2) {
                box(side * ring * (0.35 + 0.65 * prof), ll, GROUND_Y + 0.022,
                    0.046, SLAB_H, 0.046, (i % 4 === 0) ? GLACIER : HORIZON,
                    opq(a * fade * 0.85 * prof));
              }
            }
          }
        }
      }

      /* ---- 4. L'AUTRE COTE — tick 112 ---- */
      {
        const other = clamp01((t - 112 / 170) / 0.10) * (1 - clamp01((t - V.RIFT_CLOSE) / 0.08));
        if (other > 0.01) {
          for (let i = 0; i < 12; i++) {
            const u = (hash(salt, i * 113) - 0.5) * 1.5, prof = V.riftProfile(u);
            if (prof < 0.2) continue;
            const rise = fract(hash(salt, i * 127) + t * 0.9);
            const yy = base + crown * prof * rise, ll = u * half;
            const ff = (hash(salt, i * 131) - 0.5) * lipMax * prof * 1.2;
            const sz = 0.040 * (1 - rise * 0.5);
            box(ff, ll, yy, sz, sz, sz, CHORUS, opq(a * other * (1 - rise)));
          }
        }
      }
    }

    /* ---- 5. LES NEUF ASPIRATIONS — ticks 20 + 10k, vers le PLAN et pas vers le centre ---- */
    for (let k = 0; k < 9; k++) {
      const when = (20 + k * 10) / 170;
      if (t < when) break;
      const age = t - when;
      if (age > 0.16) continue;
      const u01 = clamp01(age / 0.16);

      /* ---- LE COUP, a l'instant exact. Trois objets, trois echelles de temps. ---- */
      /* (1) LA DECHARGE : un arc saute d'une levre a l'autre. Quatre frames. */
      const flash = 1 - clamp01(age / 0.024);
      if (flash > 0) {
        for (let arc = 0; arc < 5; arc++) {
          const lu = (arc + 0.5) / 5 * 1.80 - 0.90;
          const prof = V.riftProfile(lu);
          if (prof < 0.12) continue;
          /* L'arc doit partir d'une rangee de dents et arriver sur l'autre : on mesure
             l'ecartement REEL des machoires, sinon il flotte. */
          const ll = lu * half;
          const span = V.RIFT_HINGE + V.riftJawPoint(1, V.riftJawAngle(t),
                         V.RIFT_JAW * (0.42 + 0.58 * prof))[0];
          let prevFa = -span;
          let prevYa = base + crown * prof * (0.25 + 0.45 * hash(salt, k * 17 + arc));
          for (let b = 1; b <= 9; b++) {
            const v = b / 9;
            const jig = (hash(salt, k * 211 + arc * 13 + b) - 0.5) * 0.30 * prof;
            const ffa = -span + 2 * span * v, yya = prevYa + jig * 0.6;
            segment(prevFa, ll, prevYa, ffa, ll + jig * 0.5, yya,
                    0.026 + 0.020 * flash, flash > 0.55 ? SINGULAR : GLACIER,
                    opq(a * flash), 0.030);
            prevFa = ffa; prevYa = yya;
          }
        }
      }

      /* (2) LA GUEULE SE CRISPE. Le mouvement est porte par les machoires elles-memes
         (la morsure manquee de riftJawAngle) ; il ne reste ici que la traine lumineuse
         qui court le long du bord dente a chaque aspiration. */
      const crisp = 1 - clamp01(age / 0.085);
      if (crisp > 0.02) {
        const sweep = -1 + 2 * clamp01(age / 0.085);
        for (let side = -1; side <= 1; side += 2) {
          for (let i = 0; i < 13; i++) {
            const lu = -1 + 2 * i / 12, prof = V.riftProfile(lu);
            if (prof < 0.10) continue;
            const near = 1 - clamp01(Math.abs(lu - sweep) / 0.42);
            if (near <= 0) continue;
            const jt = V.riftJawPoint(1, V.riftJawAngle(t),
                                      V.RIFT_JAW * (0.42 + 0.58 * prof));
            const sz = 0.034 + 0.030 * near;
            box(side * (V.RIFT_HINGE + jt[0]), lu * half, base + jt[1], sz, sz, sz,
                near > 0.65 ? SINGULAR : GLACIER, opq(a * crisp * near));
          }
        }
      }

      /* (3) LE CHIFFRE : une barre au sol par aspiration subie, et elle RESTE. */
      for (let done = 0; done <= k; done++) {
        const fresh = done === k ? (1 - clamp01(age / 0.10)) : 0;
        box(V.RIFT_LIP * 1.9, (done - 4) * 0.42, GROUND_Y + 0.030 + fresh * 0.10,
            0.055, 0.055 + fresh * 0.04, 0.055,
            fresh > 0.3 ? SINGULAR : (done % 2 === 0 ? GLACIER : HORIZON),
            opq(a * (0.55 + 0.45 * fresh)));
      }
      for (let i = 0; i < 5; i++) {
        const seed = k * 311 + i * 29;
        const side = (i & 1) === 0 ? 1 : -1;
        const f0 = side * (1.6 + 1.8 * hash(salt, seed));
        const ll = (hash(salt, seed + 7) - 0.5) * 2 * V.RIFT_REACH * run;
        const prof = V.riftProfile(clamp01(Math.abs(ll) / Math.max(0.2, V.RIFT_REACH * run)));
        const pull = u01 * u01 * (3 - 2 * u01);
        const ff = f0 * (1 - pull);
        const yy = GROUND_Y + 0.06 + pull * (0.35 + 0.9 * prof) + 0.25 * Math.sin(pull * 3.1);
        if (Math.abs(ff) < lipMax * prof * 0.8) continue;
        fragment(ff, ll, yy, 0.052 * (1 - 0.4 * pull), pull * 7 + i,
                 i % 3 === 0 ? ENDSTONE : HALO, ENDSTONE_DK,
                 opq(a * (0.45 + 0.55 * (1 - pull))));
      }
      for (let i = 0; i < 7; i++) {
        const side = (i & 1) === 0 ? 1 : -1;
        const f0 = side * (0.9 + 2.4 * hash(salt, k * 53 + i));
        const ll = (hash(salt, k * 59 + i * 3) - 0.5) * 2 * V.RIFT_REACH * run;
        box(f0 * (1 - u01), ll, GROUND_Y + 0.020, 0.034, SLAB_H, 0.034,
            i % 4 === 0 ? GLACIER : HALO, opq(a * (1 - u01) * 0.85));
      }
    }

    /* ---- 6. LA FERMETURE — tick 140 : ejection PERPENDICULAIRE, comme le voidPush ---- */
    {
      const shut = clamp01((t - V.RIFT_CLOSE) / 0.17);
      if (shut > 0) {
        const sec = (t - V.RIFT_CLOSE) * 8.5;
        for (let i = 0; i < 14; i++) {
          const side = (i & 1) === 0 ? 1 : -1;
          const ll = (hash(salt, i * 151) - 0.5) * 2 * V.RIFT_REACH * 0.9;
          const speed = 1.5 + 1.6 * hash(salt, i * 157);
          const y0 = base + V.RIFT_HEIGHT * 0.35 * hash(salt, i * 163);
          fragment(side * speed * sec, ll, ballistic(y0, 1.3, sec, GROUND_Y + 0.05),
                   0.054, sec * 5 + i, i % 3 === 0 ? INVERT : GLACIER, BASALT,
                   opq(a * (1 - shut * 0.8)));
        }
      }
    }
  };

  // =======================================================================================
  //  IV. PORTE D'HORIZON — 180 ticks — elle EXPULSE
  // =======================================================================================
  /* Un TOURNIQUET : sept transits, sept vraies paraboles, azimut qui avance d'un septieme
     de tour — l'etoile que le serveur dessine avec des corps. */
  const renderGate = (a) => {
    const V = VOID_SHAPE;
    const ring = V.gateRing(t), floorY = GROUND_Y + 0.04;
    const open = smooth((t - 24 / 180) / 0.12);
    const shut = clamp01((t - V.GATE_SHUT) / 0.12);
    const seedAng = hash(salt, 3) * TAU;

    /* ---- 1. LE TRACE AU SOL — ticks 0, 5, 10, 15 : la boucle exacte du serveur ---- */
    for (let step = 0; step < 4; step++) {
      const when = step * 5 / 180;
      if (t < when) continue;
      const age = clamp01((t - when) / 0.22);
      for (let i = 0; i < 10; i++) {
        const ang = (i + step * 2.5) / 10 * TAU;
        const sz = (0.055 - 0.020 * age) * (1 - 0.5 * open);
        if (sz <= 0.008) continue;
        box(Math.cos(ang) * 2.12, Math.sin(ang) * 2.12, GROUND_Y + 0.016, sz, SLAB_H, sz,
            step === 3 ? GLACIER : HORIZON, opq(a * (0.40 + 0.18 * step) * (1 - age * 0.4)));
      }
    }

    /* ---- 2. L'ANNEAU : la fermeture resorbe l'ame et garde la section ---- */
    {
      const soul = ring * (1 - smooth(shut));
      const tube = (0.17 + 0.035 * Math.sin(t * 13)) * (0.35 + 0.65 * open) * (1 + shut * 0.8);
      if (soul > 0.04 && tube > 0.01) {
        torus(0, 0, floorY + tube * 0.9, soul, tube, 0, t * 3.4, a * (0.55 + 0.45 * open), false);
      }
    }

    /* ---- 3. LA CAGE LENTILLEE : l'air lui-meme tombe dedans, en -k/r ---- */
    if (open > 0.02 && shut < 1) {
      const strength = ring * ring * 0.42 * open * (1 - shut);
      for (let i = 0; i < 16; i++) {
        /* Chaque montant a SA phase : seize colonnes en phase font un rideau, seize
           colonnes dephasees font de l'air qui tombe. */
        const phase = hash(salt, i * 19) * TAU;
        const wave = 0.72 + 0.55 * hash(salt, i * 23);
        const drift = (hash(salt, i * 31) - 0.5) * 0.22;
        const ang = i / 16 * TAU + t * (0.25 + drift);
        const r0 = ring * (1.04 + 0.26 * hash(salt, i * 17));
        let prevF = 0, prevL = 0, prevY = 0;
        for (let h = 0; h <= 22; h++) {
          const u = h / 22, yy = floorY + u * 2.45;
          const bend = strength / Math.max(0.5, r0) / (1 + yy * yy * 0.55);
          const rr = Math.max(ring * 0.22, r0 - bend);
          /* L'ONDULATION, pour de bon : deux harmoniques non commensurables d'amplitudes
             inegales (donc jamais symetrique), une enveloppe en u^1,35, et surtout une
             BOSSE QUI MONTE le long du montant et sort par le haut. C'est elle qu'on
             appelle une vague, et c'est elle qui manquait. */
          const env = Math.pow(u, 1.35);
          const travel = fract(t * 0.31 + i * 0.0625 + hash(salt, i * 37));
          const bq = (u - travel) * 3.6;
          const bump = Math.exp(-bq * bq) * (0.55 + 0.45 * wave);
          const swirl = (Math.sin(phase + u * 5.6 * wave - t * 3.1) * 0.62
                       + Math.sin(phase * 1.7 + u * 2.21 * wave + t * 1.9) * 0.31) * env
                       + bump * 0.46;
          const lateral = (Math.cos(phase + u * 4.3 * wave - t * 2.6) * 0.42
                         + Math.cos(phase * 0.6 + u * 1.63 * wave - t * 1.3) * 0.25) * env
                         + bump * 0.22;
          const ff = Math.cos(ang + lateral * 0.5) * (rr + swirl);
          const ll = Math.sin(ang + lateral * 0.5) * (rr + swirl);
          if (h > 0) {
            const pull = clamp01(bend / 0.9);
            /* Pas 0,026 < taille du cube : les cubes se RECOUVRENT, donc trait plein. */
            segment(prevF, prevL, prevY, ff, ll, yy, 0.028 + 0.020 * pull,
                    pull > 0.55 ? HORIZON : (pull > 0.25 ? HALO : SLATE),
                    opq(a * (0.42 + 0.52 * pull) * open * (1 - shut)), 0.026);
          }
          prevF = ff; prevL = ll; prevY = yy;
        }
        /* La crete qui remonte : elle donne le SENS du courant. */
        const crest = fract(t * 0.42 + i * 0.0625);
        const cy2 = floorY + crest * 2.45;
        const cbend = strength / Math.max(0.5, r0) / (1 + cy2 * cy2 * 0.55);
        const crr = Math.max(ring * 0.22, r0 - cbend)
                  + Math.sin(phase + crest * 5.6 * wave - t * 3.1) * 0.17 * crest;
        box(Math.cos(ang) * crr, Math.sin(ang) * crr, cy2, 0.042, 0.042, 0.042,
            GLACIER, opq(a * open * (1 - shut) * (1 - crest * 0.55)));
      }
    }

    /* ---- 4. LES SEPT TRANSITS — ticks 34 + 18p ---- */
    for (let pass = 0; pass < 7; pass++) {
      const when = V.GATE_PASS0 + pass * V.GATE_PASS_STEP;
      if (t < when - 0.035) break;
      const ang = seedAng + pass * (TAU / 7);
      const ca = Math.cos(ang), sa = Math.sin(ang), inAng = ang + 2.6;
      const age = t - when;
      if (age < 0) {
        const u = clamp01((age + 0.035) / 0.035), rr = ring * (1.9 - 0.9 * u);
        fragment(Math.cos(inAng) * rr, Math.sin(inAng) * rr, floorY + 0.22 - 0.18 * u,
                 0.085, u * 5, ENDSTONE, ENDSTONE_DK, opq(a * 0.9));
        continue;
      }
      if (age < 0.030) {
        const u = age / 0.030;
        for (let i = 0; i < 9; i++) {
          const aa = i / 9 * TAU + u * 5, rr = ring * (0.25 + 0.70 * u);
          box(Math.cos(aa) * rr, Math.sin(aa) * rr, floorY + 0.06 + u * 0.25,
              0.045, 0.045, 0.045, SINGULAR, opq(a * (1 - u)));
        }
        continue;
      }
      const sec = (age - 0.030) * 9;
      const dist = 3.5 * sec;
      const yy = ballistic(floorY + 0.10, 4.4, sec, floorY + 0.05);
      const life = 1 - clamp01((sec - 1.9) / 0.8);
      if (life <= 0.01) continue;
      fragment(ca * dist, sa * dist, yy, 0.082, sec * 6.5 + pass, INVERT, BASALT, opq(a * life));
      for (let i = 1; i <= 9; i++) {
        const back = sec - i * 0.085;
        if (back <= 0) break;
        const by = ballistic(floorY + 0.10, 4.4, back, floorY + 0.05), bd = 3.5 * back;
        const fade = (1 - i / 10) * life;
        box(ca * bd, sa * bd, by, 0.030, 0.030, 0.030, (i & 1) === 0 ? INVERT : HALO,
            opq(a * fade * 0.75));
      }
    }

    /* ---- 5. LA FERMETURE — tick 150 ---- */
    if (shut > 0) {
      const rr = 0.4 + 3.0 * smooth(shut);
      const n = Math.max(24, Math.round(rr * 18));
      for (let i = 0; i < n; i++) {
        const ang = i / n * TAU, wob = 1 + 0.05 * Math.sin(ang * 6 + t * 11);
        box(Math.cos(ang) * rr * wob, Math.sin(ang) * rr * wob, GROUND_Y + 0.016,
            0.046, SLAB_H, 0.046, i % 6 === 0 ? SINGULAR : HORIZON, opq(a * (1 - shut) * 0.90));
      }
      const core = (1 - shut) * 0.42;
      if (core > 0.03) {
        for (let i = 0; i < 16; i++) {
          const aa = i * 2.39996 + t * 7, el = (hash(salt, i * 23) - 0.5) * 2.4;
          const rr2 = core * Math.cos(el);
          box(Math.cos(aa) * rr2, Math.sin(aa) * rr2, floorY + 0.2 + core * Math.sin(el),
              0.040, 0.040, 0.040, (i & 1) === 0 ? GLACIER : HORIZON, opq(a * (1 - shut)));
        }
      }
    }
  };

  /* ============================================================================
     V. L'EFFONDREMENT EN CHAINE — 200 ticks — elle PROPAGE
     ----------------------------------------------------------------------------
     Miroir de VoidSignatureVfx.renderChain. Trois objets independants :
       - LE CIRCUIT, un fil qui court d'une station a la suivante en huit ticks ;
       - LES STATIONS, chacune avec son compte a rebours puis son implosion ;
       - LA DETONATION finale, dont le rayon paie la longueur de la chaine.
     ============================================================================ */
  const renderChain = (a) => {
    const floorY = GROUND_Y;
    const L = VOID_SHAPE.CHAIN_LINKS;

    /* ---- 1. LE CIRCUIT ---- */
    for (let i = 0; i + 1 < L; i++) {
      const leave = VOID_SHAPE.chainFire(i);
      if (t < leave) break;
      const st = VOID_SHAPE.chainStation(i, salt), nx = VOID_SHAPE.chainStation(i + 1, salt);
      const live = smooth(clamp01((t - leave) / VOID_SHAPE.CHAIN_TRAVEL));
      const steps = 22;
      for (let k = 1; k <= steps; k++) {
        const u = k / steps;
        if (u > live) break;
        const ff = st[0] + (nx[0] - st[0]) * u;
        const ll = st[1] + (nx[1] - st[1]) * u;
        const sag = Math.sin(u * TAU * 1.5 + i * 2.1) * 0.22 * (1 - u * 0.4);
        const yy = st[2] + (nx[2] - st[2]) * u + Math.sin(u * PI) * 0.30 + sag * 0.5;
        const side = Math.cos(u * TAU * 1.5 + i * 2.1) * 0.16;
        const head = 1 - clamp01((live - u) / 0.22);
        const age = clamp01((t - leave - VOID_SHAPE.CHAIN_TRAVEL) / 0.10);
        const th = (0.020 + 0.055 * head) * (1 - 0.45 * age);
        const beat = 0.55 + 0.45 * Math.sin(t * 26 - u * 7 + i);
        box(ff + side * (nx[1] - st[1]) * 0.25, ll + side, yy, th, th, th,
            head > 0.60 ? SINGULAR : (head > 0.22 ? GLACIER : HORIZON),
            opq(a * (0.30 + 0.70 * head) * (0.55 + 0.45 * beat)));
      }
    }

    /* ---- 2. LES STATIONS ---- */
    for (let i = 0; i < L; i++) {
      const arrive = VOID_SHAPE.chainArrive(i);
      if (t < arrive) break;
      const fire = VOID_SHAPE.chainFire(i);
      const st = VOID_SHAPE.chainStation(i, salt);
      const sf = st[0], sl = st[1], sy = st[2];

      if (t < fire) {
        /* 2a. LE COMPTE A REBOURS : le rayon EST le temps restant. */
        const w = clamp01((t - arrive) / Math.max(1e-4, fire - arrive));
        const r = (1.25 + i * 0.10) * (1 - smooth(w)) + 0.12;
        const spin = t * (6 + 16 * w) + i * 1.3;
        const ring = 14;
        for (let k = 0; k < ring; k++) {
          const ang = k * TAU / ring + spin;
          const ca = Math.cos(ang), sa = Math.sin(ang);
          const tilt = Math.sin(ang * 2 + t * 3) * 0.22 * (1 - w);
          const half = 0.055 + 0.030 * w;
          abyss(sf + ca * r, sl + sa * r, sy + tilt, half);
          box(sf + ca * r, sl + sa * r, sy + tilt,
              half * 1.18, half * 1.18, half * 1.18, NOTHING, opq(a * 0.85));
        }
        const groundR = r * 1.35, marks = 24;
        for (let k = 0; k < marks; k++) {
          const ang = k * TAU / marks - spin * 0.4;
          const blink = 0.35 + 0.65 * (0.5 + 0.5 * Math.sin(t * 22 + k * 0.9));
          box(sf + Math.cos(ang) * groundR, sl + Math.sin(ang) * groundR, floorY + 0.012,
              0.040, SLAB_H, 0.040, blink > 0.75 ? HORIZON : HALO,
              opq(a * blink * (0.35 + 0.65 * w)));
        }
        for (let beat = 0; beat < 3; beat++) {
          const when = arrive + (fire - arrive) * beat / 3;
          const age = (t - when) / 0.055;
          if (age < 0 || age > 1) continue;
          const rr = 0.25 + age * 1.5, n = 18;
          for (let k = 0; k < n; k++) {
            const ang = k * TAU / n;
            box(sf + Math.cos(ang) * rr, sl + Math.sin(ang) * rr, sy,
                0.030, 0.030, 0.030, GLACIER, opq(a * (1 - age) * 0.75));
          }
        }
      } else {
        /* 2b. L'IMPLOSION, puis le cratere. */
        const age = (t - fire) * 200 / 26;
        const blast = VOID_SHAPE.chainBlast(i);
        if (age < 1) {
          const fall = 1 - age, r = blast * fall * fall * fall;
          const shell = 26 + i * 3;
          for (let k = 0; k < shell; k++) {
            const u = (k + 0.5) / shell;
            const phi = Math.acos(1 - 2 * u), theta = k * 2.39996 + t * 1.2;
            const sinp = Math.sin(phi);
            const px = Math.cos(theta) * sinp * r, pz = Math.sin(theta) * sinp * r;
            const py = Math.cos(phi) * r * 0.80, half = 0.045 + 0.045 * age;
            abyss(sf + px, sl + pz, sy + py, half);
            box(sf + px, sl + pz, sy + py, half * 1.15, half * 1.15, half * 1.15,
                NOTHING, opq(a * (0.55 + 0.45 * age)));
          }
          const flash = clamp01((age - 0.62) / 0.38);
          if (flash > 0) {
            const fr = blast * 0.55 * flash;
            for (let ringIdx = 0; ringIdx < 3; ringIdx++) {
              const n = 30;
              for (let k = 0; k < n; k++) {
                const ang = k * TAU / n, ca = Math.cos(ang) * fr, sa = Math.sin(ang) * fr;
                const px = ringIdx === 0 ? ca : (ringIdx === 1 ? ca : 0);
                const py = ringIdx === 0 ? sa : (ringIdx === 1 ? 0 : ca);
                const pz = ringIdx === 0 ? 0 : (ringIdx === 1 ? sa : sa);
                box(sf + px, sl + pz, sy + py, 0.030, 0.030, 0.030,
                    flash > 0.55 ? GLACIER : SINGULAR, opq(a * (1 - flash) * 0.9));
              }
            }
          }
        }
        const wave = (t - fire) * 200 / 34;
        if (wave < 1) {
          const rr = blast * smooth(wave), n = 44 + i * 4;
          for (let k = 0; k < n; k++) {
            const ang = k * TAU / n + hash(salt, i * 7 + k) * 0.12;
            const jag = 1 + (hash(salt, k * 13 + i) - 0.5) * 0.10;
            box(sf + Math.cos(ang) * rr * jag, sl + Math.sin(ang) * rr * jag, floorY + 0.014,
                0.055, SLAB_H, 0.055, (k & 3) === 0 ? GLACIER : HORIZON,
                opq(a * (1 - wave) * 0.95));
          }
        }
        {
          const scar = clamp01((t - fire) * 200 / 12), n = 20;
          for (let k = 0; k < n; k++) {
            const ang = hash(salt, i * 101 + k) * TAU;
            const rr = VOID_SHAPE.chainBlast(i) * (0.25 + 0.55 * hash(salt, i * 103 + k));
            box(sf + Math.cos(ang) * rr, sl + Math.sin(ang) * rr, floorY + 0.008,
                0.050, SLAB_H * 0.7, 0.050, (k & 1) === 0 ? BASALT : SLATE,
                opq(a * scar * 0.80));
          }
        }
        for (let k = 0; k < 4; k++) {
          const sec = (t - fire) * 10;
          const ang = hash(salt, i * 57 + k * 11) * TAU;
          const speed = 1.7 + 1.5 * hash(salt, i * 59 + k * 13);
          const rr = speed * sec * 0.55;
          const yy = ballistic(sy, 3.4 + 2.0 * hash(salt, i * 61 + k), sec, floorY + 0.06);
          const sz = 0.055 + 0.030 * hash(salt, i * 67 + k);
          fragment(sf + Math.cos(ang) * rr, sl + Math.sin(ang) * rr, yy, sz,
                   sec * 2.4 + k, ENDSTONE, ENDSTONE_DK,
                   opq(a * (1 - clamp01((t - fire) / 0.30) * 0.45)));
        }
      }
    }

    /* ---- 3. LA DETONATION FINALE ---- */
    {
      const last = VOID_SHAPE.chainFire(L - 1);
      const drain = (t - last) * 200 / 10;
      if (drain > 0 && drain < 1) {
        const u = 1 - drain;
        for (let i = 0; i + 1 < L; i++) {
          const st = VOID_SHAPE.chainStation(i, salt), nx = VOID_SHAPE.chainStation(i + 1, salt);
          const seg = (L - 2 - i) / (L - 1);
          const local = clamp01((u - seg) * (L - 1));
          if (local <= 0 || local >= 1) continue;
          box(st[0] + (nx[0] - st[0]) * local, st[1] + (nx[1] - st[1]) * local,
              st[2] + (nx[2] - st[2]) * local + Math.sin(local * PI) * 0.30,
              0.075, 0.075, 0.075, SINGULAR, opq(a));
        }
      }
      const boom = (t - last - 10 / 200) * 200 / 30;
      if (boom > 0) {
        const st = VOID_SHAPE.chainStation(L - 1, salt);
        const grow = smooth(clamp01(boom));
        const fade = 1 - clamp01((boom - 0.55) / 0.45);
        const r = (2.0 + 0.30 * L) * grow;
        if (fade > 0.01) {
          const n = 54;
          for (let k = 0; k < n; k++) {
            const u = (k + 0.5) / n;
            const phi = Math.acos(1 - 2 * u), theta = k * 2.39996 + t * 0.8;
            const sinp = Math.sin(phi);
            const px = Math.cos(theta) * sinp * r, pz = Math.sin(theta) * sinp * r;
            const py = Math.cos(phi) * r * 0.75, half = 0.075 * (1 - grow * 0.4);
            abyss(st[0] + px, st[1] + pz, st[2] + py, half);
            box(st[0] + px, st[1] + pz, st[2] + py, half * 1.2, half * 1.2, half * 1.2,
                NOTHING, opq(a * fade));
          }
          const cols = 16;
          for (let k = 0; k < cols; k++) {
            const ang = k * TAU / cols + t * 0.6;
            const rr = r * (0.55 + 0.45 * hash(salt, k * 23));
            for (let h = 0; h < 1; h += 0.14) {
              box(st[0] + Math.cos(ang) * rr * (1 - h * 0.35),
                  st[1] + Math.sin(ang) * rr * (1 - h * 0.35),
                  st[2] + h * (1.6 + 0.9 * grow),
                  0.040, 0.040, 0.040, INVERT, opq(a * fade * (1 - h) * 0.85));
            }
          }
        }
      }
    }
  };

  const ALPHA = 255;
  switch (variant) {
    case 1: renderPrism(ALPHA); break;
    case 2: renderRift(ALPHA); break;
    case 3: renderGate(ALPHA); break;
    case 4: renderChain(ALPHA); break;
    default: renderWarp(ALPHA); break;
  }
  out.portalCubes = cubesOut;
  return out;
}

if (typeof module !== 'undefined' && module.exports) module.exports = { buildVoid, VOID_SHAPE };
