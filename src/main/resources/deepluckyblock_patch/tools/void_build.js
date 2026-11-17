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
      return t * (2.4 + 9.0 * spinUp) * (1 - broken * 0.85);
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

    /* --- Porte --- */
    GATE_PASS0: 34 / 180,
    GATE_PASS_STEP: 18 / 180,
    GATE_SHUT: 150 / 180,
    gateRing(t) {
      const grow = smooth((t - 0.04) / 0.26), shut = smooth((t - 0.86) / 0.14);
      return (0.34 + 1.78 * grow) * (1 - shut);
    },

    /* --- Trame --- */
    LATTICE_R: 3.00,
    LATTICE_YC: 0.28,
    LATTICE_TENSION0: 40 / 200,
    LATTICE_TENSION_STEP: 10 / 200,
    LATTICE_CONVERGE: 160 / 200,
    LATTICE_SNAP: 184 / 200,
    latticeRest(t) {
      const pull = smooth((t - 40 / 200) / 0.50), conv = smooth((t - 160 / 200) / 0.12);
      return (1 - 0.46 * pull) * (1 - 0.30 * conv);
    },
    latticeCell(t) {
      const open = smooth((t - 0.30) / 0.22), shut = smooth((t - 184 / 200) / 0.035);
      const pull = smooth((t - 40 / 200) / 0.50), conv = smooth((t - 160 / 200) / 0.12);
      const ring = this.LATTICE_R * (1 - 0.42 * pull) * (1 - 0.52 * conv);
      return ring * 0.52 * open * (1 - shut);
    },
    TAU,
  };
})();

function buildVoid(variant, t, salt = 7331) {
  const out = [];
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
      const kick = (1 - smooth((t - fire) / 0.085)) * 0.11;
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
          const th = (0.030 + 0.040 * grow) * live;
          box(f, 0, yc, th, th, th, grow > 0.82 ? SINGULAR : GLACIER,
              opq(a * (0.55 + 0.45 * grow) * beat * live));
        }
        /* Les sept fils, la ou les sept rayons entrent : sept choses paralleles a
           l'arrivee, sept choses divergentes au depart. */
        for (let k = 0; k < 7; k++) {
          const off = k * (TAU / 7) + 0.40;
          const ol = Math.cos(off) * spread, oy = Math.sin(off) * spread;
          for (let f = -3.4; f <= head; f += 0.13) {
            const grow = clamp01((f + 3.4) / 1.2);
            box(f, ol, yc + oy, 0.024, 0.024, 0.024,
                k % 3 === 0 ? GLACIER : (k % 3 === 1 ? HORIZON : HALO),
                opq(a * (0.30 + 0.45 * grow) * live));
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
      const steps = Math.max(3, Math.min(40, Math.round(reach / 0.155)));
      const tone = k % 3 === 0 ? GLACIER : (k % 3 === 1 ? HORIZON : HALO);
      const rad = 0.052 + 0.012 * Math.sin(t * 24 + k);
      for (let si = 0; si <= steps; si++) {
        const u = si / steps, d = u * reach;
        const ff = bf + rd[0] * d, ll = bl + rd[1] * d, yy = by + rd[2] * d;
        const taper = 1 - 0.45 * u;
        for (let c = 0; c < 5; c++) {
          const ang = c * (TAU / 5) + d * 2.4 + t * 6;
          const ca = Math.cos(ang) * rad * taper, sa = Math.sin(ang) * rad * taper;
          box(ff + ux * ca + wx * sa, ll + ul * ca + wl * sa, yy + uy * ca + wy * sa,
              0.026, 0.026, 0.026, si === steps ? SINGULAR : tone,
              opq(a * (0.52 + 0.48 * (1 - u)) * live));
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
        const th = (equator ? 0.040 : 0.029) * (0.55 + 0.45 * body + 0.45 * broken);
        const tone = broken > 0 ? ((e & 1) === 0 ? BASALT : SLATE) : (equator ? GLACIER : HORIZON);
        segment(f0, l0, y0, f1, l1, y1, th, tone, opq(a * 0.95 * fade), equator ? 0.052 : 0.062);
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

    if (half > 0.05 && crown > 0.05) {
      /* ---- 2. LES DEUX LEVRES, et leurs nervures ---- */
      const n = 34;
      for (let side = -1; side <= 1; side += 2) {
        let prevF = 0, prevL = 0, prevY = 0;
        for (let i = 0; i <= n; i++) {
          const u = -1 + 2 * i / n, prof = V.riftProfile(u);
          const ll = u * half, yy = base + crown * prof;
          const jag = (hash(salt, i * 37 + (side > 0 ? 0 : 500)) - 0.5) * 0.10 * prof;
          const ff = side * (lipMax * prof * 0.92 + 0.035) + jag;
          if (i > 0) {
            const bright = prof * (0.55 + 0.45 * Math.sin(u * 9 - t * 14));
            segment(prevF, prevL, prevY, ff, ll, yy, 0.036 + 0.018 * prof,
                    bright > 0.62 ? SINGULAR : (prof > 0.45 ? GLACIER : HORIZON),
                    opq(a * (0.68 + 0.32 * prof)), 0.050);
          }
          prevF = ff; prevL = ll; prevY = yy;
        }
        for (let level = 1; level <= 4; level++) {
          const h = level / 5;
          let prevF2 = 0, prevL2 = 0, started = false;
          for (let i = 0; i <= n; i++) {
            const u = -1 + 2 * i / n, prof = V.riftProfile(u);
            if (prof <= h) { started = false; continue; }
            const inner = Math.sqrt(Math.max(0, 1 - (h / prof) * (h / prof)));
            const ff = side * lipMax * prof * inner * 0.88, ll = u * half, yy = base + crown * h;
            if (started) {
              segment(prevF2, prevL2, yy, ff, ll, yy, 0.024,
                      (level & 1) === 0 ? BASALT : SLATE, opq(a * 0.72), 0.070);
            }
            prevF2 = ff; prevL2 = ll; started = true;
          }
        }
      }

      /* ---- 3. LES FIBRES : de la matiere etiree qui se necrose et CASSE ---- */
      for (let i = 0; i < 9; i++) {
        const u = (hash(salt, i * 83) - 0.5) * 1.74, prof = V.riftProfile(u);
        if (prof < 0.12) continue;
        const limit = 0.22 + 0.62 * hash(salt, i * 97);
        const strain = clamp01(gape / limit);
        if (strain >= 1) continue;
        const h = (0.15 + 0.70 * hash(salt, i * 101)) * prof;
        const ll = u * half, yy = base + crown * h, span = lipMax * prof * 0.92;
        for (let si = 0; si <= 7; si++) {
          const v = -1 + 2 * si / 7;
          const neck = 1 - 0.85 * strain * (1 - v * v);
          const th = 0.028 * neck;
          if (th < 0.006) continue;
          box(v * span, ll + v * 0.04, yy + (1 - v * v) * 0.05 * strain, th, th, th,
              strain > 0.66 ? GLACIER : HORIZON, opq(a * (0.55 + 0.45 * (1 - strain))));
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
        const ang = i / 16 * TAU + t * 0.25;
        const ca = Math.cos(ang), sa = Math.sin(ang);
        const r0 = ring * (1.04 + 0.26 * hash(salt, i * 17));
        let prevF = 0, prevL = 0, prevY = 0;
        for (let h = 0; h <= 10; h++) {
          const u = h / 10, yy = floorY + u * 2.45;
          const bend = strength / Math.max(0.5, r0) / (1 + yy * yy * 0.55);
          const rr = Math.max(ring * 0.22, r0 - bend);
          const ff = ca * rr, ll = sa * rr;
          if (h > 0) {
            const pull = clamp01(bend / 0.9);
            segment(prevF, prevL, prevY, ff, ll, yy, 0.014 + 0.012 * pull,
                    pull > 0.55 ? HORIZON : (pull > 0.25 ? HALO : SLATE),
                    opq(a * (0.30 + 0.55 * pull) * open * (1 - shut)), 0.115 - 0.045 * pull);
          }
          prevF = ff; prevL = ll; prevY = yy;
        }
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

  // =======================================================================================
  //  V. CONSTELLATION NULLE — 200 ticks — elle LIE
  // =======================================================================================
  /* Une vraie SIMULATION masses-ressorts, rejouee depuis zero a chaque image, a pas fixe :
     onze ressorts, onze impulsions aux ticks du serveur, et la trame OSCILLE. */
  const latticeSim = (tt) => {
    const V = VOID_SHAPE, n = 7;
    const edges = [0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 0, 0, 3, 1, 4, 2, 5, 3, 6];
    const px = [], pl = [], py = [], vf = [], vl = [], vy = [], rest = [];
    for (let i = 0; i < n; i++) {
      const ang = i * (TAU / n) + (hash(salt, i * 17) - 0.5) * 0.62;
      const r = V.LATTICE_R * (0.72 + 0.46 * hash(salt, i * 31));
      px.push(Math.cos(ang) * r); pl.push(Math.sin(ang) * r);
      py.push(V.LATTICE_YC + (hash(salt, i * 53) - 0.5) * 1.35);
      vf.push(0); vl.push(0); vy.push(0);
    }
    for (let e = 0; e < 11; e++) {
      const i0 = edges[e * 2], i1 = edges[e * 2 + 1];
      rest.push(dist3(px[i0], pl[i0], py[i0], px[i1], pl[i1], py[i1]));
    }
    const total = 240, dt = 10 / total, dtT = 1 / total;
    const steps = Math.min(total, Math.max(0, Math.round(tt * total)));
    const af = new Array(n), al = new Array(n), ay = new Array(n);
    for (let s = 0; s < steps; s++) {
      const now = s * dtT;
      for (let i = 0; i < n; i++) { af[i] = 0; al[i] = 0; ay[i] = 0; }
      const broken = now >= V.LATTICE_SNAP;
      if (!broken) {
        const target = V.latticeRest(now);
        for (let e = 0; e < 11; e++) {
          const i0 = edges[e * 2], i1 = edges[e * 2 + 1];
          const ddf = px[i1] - px[i0], ddl = pl[i1] - pl[i0], ddy = py[i1] - py[i0];
          const len = Math.sqrt(ddf * ddf + ddl * ddl + ddy * ddy);
          if (len < 0.0005) continue;
          const force = 5.4 * (len - rest[e] * target) / len;
          af[i0] += ddf * force; al[i0] += ddl * force; ay[i0] += ddy * force;
          af[i1] -= ddf * force; al[i1] -= ddl * force; ay[i1] -= ddy * force;
        }
        for (let e = 0; e < 11; e++) {
          const when = V.LATTICE_TENSION0 + e * V.LATTICE_TENSION_STEP;
          if (when < now || when >= now + dtT) continue;
          let bf = 0, bl = 0;
          for (let i = 0; i < n; i++) { bf += px[i]; bl += pl[i]; }
          bf /= n; bl /= n;
          for (let i = 0; i < n; i++) {
            const ddf = bf - px[i], ddl = bl - pl[i];
            const d = Math.sqrt(ddf * ddf + ddl * ddl);
            if (d < 0.05) continue;
            vf[i] += ddf / d * 1.05;
            vl[i] += ddl / d * 1.05;
            vy[i] += (hash(salt, e * 13 + i) - 0.4) * 0.55;
          }
        }
        if (V.LATTICE_CONVERGE >= now && V.LATTICE_CONVERGE < now + dtT) {
          for (let i = 0; i < n; i++) {
            const d = Math.sqrt(px[i] * px[i] + pl[i] * pl[i]) + 0.001;
            vf[i] -= px[i] / d * 1.8; vl[i] -= pl[i] / d * 1.8; vy[i] += 0.45;
          }
        }
      } else if (V.LATTICE_SNAP >= now - dtT && V.LATTICE_SNAP < now) {
        for (let i = 0; i < n; i++) {
          const d = Math.sqrt(px[i] * px[i] + pl[i] * pl[i]) + 0.001;
          vf[i] += px[i] / d * (2.1 + hash(salt, i * 61));
          vl[i] += pl[i] / d * (2.1 + hash(salt, i * 67));
          vy[i] += 1.1 * hash(salt, i * 71);
        }
      }
      const damp = broken ? 0.995 : 0.955;
      for (let i = 0; i < n; i++) {
        vf[i] = (vf[i] + af[i] * dt) * damp;
        vl[i] = (vl[i] + al[i] * dt) * damp;
        vy[i] = (vy[i] + (ay[i] - (broken ? GRAVITY * 0.25 : 0)) * dt) * damp;
        px[i] += vf[i] * dt; pl[i] += vl[i] * dt; py[i] += vy[i] * dt;
        const lowest = GROUND_Y + 0.55;
        if (py[i] < lowest) { py[i] = lowest; if (vy[i] < 0) vy[i] = -vy[i] * 0.35; }
      }
    }
    return { px, pl, py, rest, edges };
  };

  const renderLattice = (a) => {
    const V = VOID_SHAPE;
    const sim = latticeSim(t);
    const px = sim.px, pl = sim.pl, py = sim.py, rest = sim.rest, edges = sim.edges;
    const snapped = clamp01((t - V.LATTICE_SNAP) / 0.08);
    const sec = (t - V.LATTICE_SNAP) * 10;

    /* ---- 1. LES SEPT NOEUDS, ET LEUR MARQUE AU SOL ---- */
    let tensions = 0;
    for (let e = 0; e < 11; e++) {
      if (t >= V.LATTICE_TENSION0 + e * V.LATTICE_TENSION_STEP) tensions++;
    }
    for (let i = 0; i < 7; i++) {
      const wake = clamp01((t - i * 4 / 200) / 0.03);
      if (wake <= 0) continue;
      const load = tensions / 11;
      node(px[i], pl[i], py[i], 0.140 * wake, t * 3.4 + i * 1.3, snapped, a * wake);
      for (let k = 1; k <= 3; k++) {
        const u = k / 4, yy = py[i] + (GROUND_Y + 0.05 - py[i]) * u;
        box(px[i], pl[i], yy, 0.022, 0.022, 0.022, SLATE, opq(a * wake * (0.55 - 0.12 * k)));
      }
      for (let k = 0; k < 12; k++) {
        const ang = k / 12 * TAU + t * 0.8;
        const rr = 0.40 + 0.06 * Math.sin(ang * 3 + t * 6);
        box(px[i] + Math.cos(ang) * rr, pl[i] + Math.sin(ang) * rr, GROUND_Y + 0.014,
            0.032, SLAB_H, 0.032,
            load > 0.66 ? GLACIER : (load > 0.33 ? HORIZON : HALO),
            opq(a * wake * (0.40 + 0.55 * load) * (1 - snapped * 0.7)));
      }
    }

    /* ---- 2. LES ONZE ARETES : chainette si molle, onde stationnaire si tendue ---- */
    for (let e = 0; e < 11; e++) {
      const i0 = edges[e * 2], i1 = edges[e * 2 + 1];
      const wake = clamp01((t - Math.max(i0, i1) * 4 / 200 - 0.02) / 0.05);
      if (wake <= 0) continue;
      const f0 = px[i0], l0 = pl[i0], y0 = py[i0];
      const f1 = px[i1], l1 = pl[i1], y1 = py[i1];
      const len = dist3(f0, l0, y0, f1, l1, y1);
      const target = rest[e] * V.latticeRest(t);
      const strain = target > 0.01 ? (len - target) / target : 0;
      const slack = strain < 0 ? -strain : 0;
      const taut = clamp01(strain * 2.6);
      let prevF = f0, prevL = l0, prevY = y0;
      for (let si = 1; si <= 12; si++) {
        const u = si / 12;
        let ff = f0 + (f1 - f0) * u, ll = l0 + (l1 - l0) * u, yy = y0 + (y1 - y0) * u;
        yy -= catenary(u, slack) * len * 0.42;
        if (taut > 0.02) {
          const wave = Math.sin(u * Math.PI * 3 + t * 46) * 0.045 * taut;
          yy += wave; ff += wave * 0.5;
        }
        if (snapped > 0) {
          const frac = u < 0.5 ? u / 0.5 : (1 - u) / 0.5;
          const back = Math.min(1, sec * 1.6);
          const whip = Math.sin(frac * Math.PI * 2 - sec * 9) * 0.30 * (1 - back);
          const shrink = 1 - back * 0.9;
          const anchorF = u < 0.5 ? f0 : f1, anchorL = u < 0.5 ? l0 : l1, anchorY = u < 0.5 ? y0 : y1;
          ff = anchorF + (ff - anchorF) * shrink;
          ll = anchorL + (ll - anchorL) * shrink;
          yy = anchorY + (yy - anchorY) * shrink + whip;
        }
        const tone = snapped > 0 ? SLATE
          : (taut > 0.72 ? SINGULAR : (taut > 0.34 ? GLACIER : (slack > 0.08 ? SLATE : HORIZON)));
        segment(prevF, prevL, prevY, ff, ll, yy, (0.018 + 0.016 * taut) * wake, tone,
                opq(a * wake * (0.42 + 0.58 * taut) * (1 - snapped * 0.55)), 0.050);
        prevF = ff; prevL = ll; prevY = yy;
      }
    }

    /* ---- 3. LES PULSATIONS : ce qui arrive a l'un arrive a tous, et on le voit arriver ---- */
    for (let e = 0; e < 11; e++) {
      const when = V.LATTICE_TENSION0 + e * V.LATTICE_TENSION_STEP;
      if (t < when) break;
      const age = t - when;
      if (age > 0.05) continue;
      const u = age / 0.05;
      for (let k = 0; k < 11; k++) {
        const i0 = edges[k * 2], i1 = edges[k * 2 + 1];
        for (let dirn = 0; dirn < 2; dirn++) {
          const uu = dirn === 0 ? u : 1 - u;
          box(px[i0] + (px[i1] - px[i0]) * uu, pl[i0] + (pl[i1] - pl[i0]) * uu,
              py[i0] + (py[i1] - py[i0]) * uu, 0.044, 0.044, 0.044, SINGULAR,
              opq(a * (1 - u) * 0.95));
        }
      }
    }

    /* ---- 4. LA RUPTURE — tick 184 ---- */
    if (snapped > 0) {
      for (let e = 0; e < 11; e++) {
        const i0 = edges[e * 2], i1 = edges[e * 2 + 1];
        const mf = (px[i0] + px[i1]) * 0.5, ml = (pl[i0] + pl[i1]) * 0.5, my = (py[i0] + py[i1]) * 0.5;
        const out = Math.sqrt(mf * mf + ml * ml) + 0.001;
        const sp = 1.2 + 1.4 * hash(salt, e * 37);
        fragment(mf + mf / out * sp * sec, ml + ml / out * sp * sec,
                 ballistic(my, 0.9, sec, GROUND_Y + 0.05), 0.044, sec * 6 + e,
                 GLACIER, SLATE, opq(a * (1 - clamp01(sec / 1.6))));
      }
    }
  };

  const ALPHA = 255;
  switch (variant) {
    case 1: renderPrism(ALPHA); break;
    case 2: renderRift(ALPHA); break;
    case 3: renderGate(ALPHA); break;
    case 4: renderLattice(ALPHA); break;
    default: renderWarp(ALPHA); break;
  }
  return out;
}

if (typeof module !== 'undefined' && module.exports) module.exports = { buildVoid, VOID_SHAPE };
