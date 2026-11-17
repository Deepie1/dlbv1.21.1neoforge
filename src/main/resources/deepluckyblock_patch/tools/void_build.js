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
  const NOTHING = 0x04030A, ABYSS = 0x0C0A18, BASALT = 0x1A1726, SLATE = 0x2E2B40,
        HALO = 0x4A5E78, HORIZON = 0x7FD4E8, GLACIER = 0xBFF2FF, SINGULAR = 0xFFFFFF;
  const ENDSTONE = 0xD8D2A4, ENDSTONE_DK = 0x8A855F, CHORUS = 0x8E5BA6, INVERT = 0xFF9A4A;

  // L'aperçu n'a pas de monde à sonder : GROUND_Y garde sa valeur de repli.
  const GROUND_Y = -0.86, SLAB_H = 0.030, GRAVITY = 9.0;

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

  // =======================================================================================
  //  I. SPHÈRE DE DISTORSION — 160 ticks
  // =======================================================================================
  const renderWarp = (a) => {
    const open = smooth(clamp01((t - 0.10) / 0.24));
    const fall = smooth(clamp01((t - 0.70) / 0.10));
    const rs = (0.05 + 1.10 * open) * (1 - fall);
    const tip = smooth(clamp01((t - 0.46) / 0.24));
    const snap = clamp01((t - 0.70) / 0.22);
    const rebound = snap <= 0 ? 0 : Math.sin(snap * PI * 1.6) * (1 - snap) * 0.55;
    const yc = 0.46;

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
  //  II. PRISME DU DRAGON — 190 ticks
  // =======================================================================================
  const renderPrism = (a) => {
    const yc = 1.12;
    const drop = clamp01(t / 0.10);
    const stair = (Math.floor(drop * 3) + smooth(fract(drop * 3))) / 3;
    const py = yc + (1 - stair) * 4.2;

    const spinUp = smooth(clamp01((t - 0.08) / 0.14));
    const broken = clamp01((t - 0.74) / 0.14);
    const spin = t * (2.4 + 9.0 * spinUp) * (1 - broken * 0.85);
    const tiltAxis = 0.34;
    const scale = 0.72 * (1 + 0.10 * Math.sin(t * 17) * spinUp);

    // ---- 1. L'OCTAÈDRE EN FIL DE FER ---------------------------------------------------
    const vert = [];
    for (let i = 0; i < 4; i++) {
      const ang = spin + i * (TAU / 4);
      vert.push([Math.cos(ang) * scale, Math.sin(ang) * tiltAxis * scale, Math.sin(ang) * scale * 0.42]);
    }
    vert.push([0, 0, scale * 1.34]);
    vert.push([0, 0, -scale * 1.34]);
    const edges = [[0, 1], [1, 2], [2, 3], [3, 0], [0, 4], [1, 4], [2, 4], [3, 4],
                   [0, 5], [1, 5], [2, 5], [3, 5]];
    for (let e = 0; e < edges.length; e++) {
      const i0 = edges[e][0], i1 = edges[e][1];
      const push = broken * (0.55 + 0.85 * hash(salt + 51, e * 23));
      const mfx = (vert[i0][0] + vert[i1][0]) * 0.5;
      const mly = (vert[i0][1] + vert[i1][1]) * 0.5;
      const mzz = (vert[i0][2] + vert[i1][2]) * 0.5;
      const ml = Math.max(0.0001, Math.sqrt(mfx * mfx + mly * mly + mzz * mzz));
      const ofx = mfx / ml * push, ofl = mly / ml * push, ofy = mzz / ml * push;
      const rgb = e < 4 ? HORIZON : (e < 8 ? HALO : SLATE);
      segment(vert[i0][0] + ofx, vert[i0][1] + ofl, py + vert[i0][2] + ofy,
              vert[i1][0] + ofx, vert[i1][1] + ofl, py + vert[i1][2] + ofy,
              0.026, rgb, opq(a * (1 - broken * 0.55)));
    }
    for (let i = 0; i < 6; i++) {
      const push = broken * 0.70;
      const vl = Math.max(0.0001, Math.sqrt(vert[i][0] ** 2 + vert[i][1] ** 2 + vert[i][2] ** 2));
      box(vert[i][0] * (1 + push / vl), vert[i][1] * (1 + push / vl),
          py + vert[i][2] * (1 + push / vl), 0.045, 0.045, 0.045, GLACIER, opq(a));
    }
    if (broken < 0.18) {
      const core = 0.085 + 0.030 * Math.sin(t * 23);
      box(0, 0, py, core, core, core, CHORUS, opq(a));
    } else {
      const hole = 0.14 + 0.26 * (broken - 0.18), pts = 26;
      for (let i = 0; i < pts; i++) {
        const ang = (i + 0.5) / pts * TAU;
        box(Math.cos(ang) * hole, 0, py + Math.sin(ang) * hole, 0.022, 0.022, 0.022,
            SINGULAR, opq(a * (1 - broken * 0.4)));
        box(0, Math.cos(ang) * hole, py + Math.sin(ang) * hole, 0.022, 0.022, 0.022,
            GLACIER, opq(a * (1 - broken * 0.4)));
      }
    }

    // ---- 2. LES QUATRE ÉCLATS EN ORBITE ------------------------------------------------
    const lift = smooth(clamp01((t - 0.12) / 0.12));
    const thrown = clamp01((t - 0.86) / 0.14);
    for (let i = 0; i < 4; i++) {
      const h1 = hash(salt + 61, i * 29);
      const ang = spin * 0.6 + i * (TAU / 4) + 0.4;
      const orbit = 1.35 + 0.22 * h1;
      let fy = GROUND_Y + (py - GROUND_Y) * lift + 0.10 * Math.sin(t * 11 + i);
      let ff = Math.cos(ang) * orbit * lift, ll = Math.sin(ang) * orbit * lift;
      if (thrown > 0) {
        const d = thrown * (3.4 + 1.2 * h1);
        ff += Math.cos(ang) * d; ll += Math.sin(ang) * d;
        fy = fy + thrown * 0.9 - GRAVITY * 0.10 * thrown * thrown;
        if (fy < GROUND_Y + 0.05) fy = GROUND_Y + 0.05;
      }
      const sz = 0.090 + 0.040 * h1;
      box(ff, ll, fy, sz, sz * 0.62, sz, ENDSTONE, opq(a));
      box(ff + sz * 0.7, ll - sz * 0.5, fy - sz * 0.4, sz * 0.55, sz * 0.45, sz * 0.55, ENDSTONE_DK, opq(a));
      box(ff - sz * 0.6, ll + sz * 0.6, fy + sz * 0.3, sz * 0.40, sz * 0.40, sz * 0.40, ENDSTONE, opq(a * 0.92));
    }

    // ---- 3. LE TRAIT D'ENTRÉE ------------------------------------------------------------
    const inb = clamp01((t - 0.22) / 0.07) * (1 - clamp01((t - 0.60) / 0.10));
    if (inb > 0.01) {
      const seg = 30;
      for (let i = 0; i < seg; i++) {
        const u = (i + 0.5) / seg;
        const ff = -4.6 + 4.6 * u;
        const pk = 0.5 + 0.5 * Math.sin(u * 22 - t * 70);
        const w = (0.028 + 0.020 * pk) * inb;
        box(ff, 0, py, w, w, w, pk > 0.78 ? SINGULAR : GLACIER, opq(a * inb));
      }
    }

    // ---- 4. LA RÉFRACTION : SEPT FAISCEAUX CREUX -----------------------------------------
    const wings = smooth(clamp01((t - 0.50) / 0.18)) * (1 - clamp01((t - 0.74) / 0.10));
    for (let b = 0; b < 7; b++) {
      const born = 0.32 + b * 0.016;
      const lifeB = clamp01((t - born) / 0.12) * (1 - clamp01((t - 0.78) / 0.10));
      if (lifeB <= 0.01) continue;
      const spreadAng = (b - 3) * 0.30;
      // LE COU : le faisceau central ne se rabat pas (sin 0 = 0) et cassait la silhouette.
      const neck = b === 3;
      const planeTip = 0.30 * Math.sin((t - 0.32) * 3.2);
      let len = (1.6 + 4.4 * smooth(lifeB)) * (0.78 + 0.22 * Math.cos(spreadAng));
      if (neck) len *= 1 - 0.52 * wings;
      const seg = Math.max(10, Math.round(len * 4.5));
      const perpF = -Math.sin(spreadAng), perpL = Math.cos(spreadAng);
      for (let s = 0; s < seg; s++) {
        const u = (s + 0.5) / seg, d = u * len;
        let ff = d * Math.cos(spreadAng);
        const ll = d * Math.sin(spreadAng);
        let yy = py + d * planeTip * Math.sin(spreadAng * 1.6);
        if (wings > 0.01) {
          const sweep = wings * (0.55 + 0.30 * u);
          if (neck) { yy += Math.sin(u * PI * 0.55) * d * wings * 1.55; }
          else {
            ff += -Math.abs(Math.sin(spreadAng)) * d * sweep * 1.15;
            yy += Math.sin(u * PI * 0.72) * d * sweep * 0.72;
          }
        }
        const rad = (0.072 + 0.030 * Math.sin(u * 9 - t * 30)) * (1 - u * 0.45);
        const ring = u < 0.08 ? 8 : 6;
        for (let k = 0; k < ring; k++) {
          const ang = (k + (s & 1) * 0.5) / ring * TAU;
          const ofl = Math.cos(ang) * rad, ofy = Math.sin(ang) * rad;
          box(ff + perpF * ofl, ll + perpL * ofl, yy + ofy, 0.020, 0.020, 0.020,
              k === 0 ? GLACIER : ((b + k) % 3 === 0 ? HORIZON : HALO),
              opq(a * lifeB * (0.70 + 0.30 * (1 - u))));
        }
        if (s === seg - 1) box(ff, ll, yy, 0.055, 0.055, 0.055, SINGULAR, opq(a * lifeB));
      }
      if (wings > 0.02 && b < 6) {
        const nextAng = (b + 1 - 3) * 0.30, dots = 26;
        for (let k = 0; k < dots; k++) {
          const u = (k + 0.5) / dots, d = u * len * 0.92;
          const sweep = wings * (0.55 + 0.30 * u);
          const sa = spreadAng + (nextAng - spreadAng) * 0.5;
          const ff = d * Math.cos(sa) - Math.abs(Math.sin(sa)) * d * sweep * 1.15;
          const ll = d * Math.sin(sa);
          const yy = py + Math.sin(u * PI * 0.72) * d * sweep * 0.72
                   + d * 0.30 * Math.sin((t - 0.32) * 3.2) * Math.sin(sa * 1.6);
          const sz = 0.046 * wings * (1 - u * 0.4);
          if (sz <= 0.005) continue;
          box(ff, ll, yy, sz, sz, sz, (k & 3) === 0 ? HORIZON : SLATE, opq(a * wings * 0.80));
        }
      }
    }

    // ---- 5. L'ÉCLAT ------------------------------------------------------------------------
    const burst = clamp01((t - 0.88) / 0.12);
    if (burst > 0.001) {
      const ease = smooth(burst), rr = 0.5 + 4.6 * ease, pts = 56;
      for (let i = 0; i < pts; i++) {
        const ang = (i + 0.5) / pts * TAU;
        const th = 0.078 * (1 - ease * 0.82);
        if (th <= 0.006) continue;
        box(Math.cos(ang) * rr, Math.sin(ang) * rr, GROUND_Y + 0.016, th, SLAB_H, th,
            i % 7 === 0 ? INVERT : HORIZON, opq(a * 0.92));
      }
      for (let i = 0; i < 26; i++) {
        const h1 = hash(salt + 71, i * 13), h2 = hash(salt + 72, i * 17);
        const u = fract(h1 + ease * 1.3);
        const yy = GROUND_Y + u * 4.4;
        const rad = (0.18 + 0.55 * h2) * (1 - u * 0.45);
        const ang = h2 * TAU + u * 2.6;
        const sz = 0.036 * (1 - u * 0.6);
        if (sz <= 0.005) continue;
        box(Math.cos(ang) * rad, Math.sin(ang) * rad, yy, sz, sz, sz,
            h1 > 0.65 ? INVERT : GLACIER, opq(a * (1 - u * 0.5)));
      }
    }
  };

  // =======================================================================================
  //  III. FAILLE DU NÉANT — 170 ticks
  // =======================================================================================
  const renderRift = (a) => {
    const yBase = GROUND_Y + 0.05, height = 3.05;
    const opening = smooth(clamp01((t - 0.06) / 0.17));
    const closing = smooth(clamp01((t - 0.64) / 0.18));
    const gape = opening * (1 - closing);
    const scar = clamp01((t - 0.80) / 0.20);

    // ---- 1. LA FÊLURE puis LES DEUX LÈVRES -------------------------------------------
    const steps = 46;
    for (let i = 0; i <= steps; i++) {
      const u = i / steps;
      const yy = yBase + u * height;
      const profile = Math.pow(Math.sin(u * PI), 0.62);
      const w = 0.82 * gape * profile;
      // Deux hachages DIFFÉRENTS pour les deux lèvres : c'est tout le sujet. Avec le même,
      // les dents s'emboîtent et on lit une fermeture éclair.
      const toothL = (hash(salt + 81, i * 13) - 0.5) * 0.14 * profile;
      const toothR = (hash(salt + 82, i * 29) - 0.5) * 0.14 * profile;
      const slip = closing * 0.085 * Math.sin(u * 23 + salt * 0.017);
      const lL = -w + toothL + slip, lR = w + toothR - slip;
      const wander = 0.16 * Math.sin(u * 4.3 + salt * 0.013);
      const lipW = 0.030 + 0.020 * profile;
      const lipRgb = scar > 0.02 ? (scar > 0.62 ? BASALT : (scar > 0.30 ? HALO : HORIZON)) : GLACIER;
      box(wander, lL, yy, lipW, lipW * 1.25, lipW, lipRgb, opq(a));
      box(wander, lL + 0.030, yy, lipW * 0.70, lipW, lipW * 0.70, NOTHING, opq(a * 0.95));
      box(wander, lR, yy, lipW, lipW * 1.25, lipW, lipRgb, opq(a));
      box(wander, lR - 0.030, yy, lipW * 0.70, lipW, lipW * 0.70, NOTHING, opq(a * 0.95));
      if (gape < 0.14 || scar > 0.02) {
        const th = 0.013 * (1 - scar * 0.85);
        if (th > 0.003) box(wander, (lL + lR) * 0.5, yy, th, th * 2.4, th,
                            scar > 0.55 ? HORIZON : SINGULAR, opq(a));
      }
      if ((i % 7) === 3 && gape > 0.25) {
        const h1 = hash(salt + 83, i * 31);
        const outd = 0.10 + 0.26 * h1;
        const side = (i & 1) === 0 ? -1 : 1;
        box(wander + (h1 - 0.5) * 0.14, (side < 0 ? lL : lR) + side * outd,
            yy + (h1 - 0.5) * 0.12, 0.018, 0.030, 0.018, HORIZON, opq(a * 0.85));
      }
    }

    // ---- 2. L'AUTRE CÔTÉ ---------------------------------------------------------------
    const windows = [[0.40, 0.055], [0.49, 0.030], [0.57, 0.070]];
    for (let wi = 0; wi < windows.length; wi++) {
      const w0 = windows[wi][0], wd = windows[wi][1];
      if (t < w0 || t > w0 + wd) continue;
      const fade = Math.sin(((t - w0) / wd) * PI);
      const rows = 9;
      for (let r = 0; r < rows; r++) {
        const u = (r + 0.5) / rows;
        const yy = yBase + u * height;
        const wMax = 0.82 * gape * Math.pow(Math.sin(u * PI), 0.62);
        if (wMax < 0.05) continue;
        const cols = Math.max(2, Math.round(wMax * 14));
        for (let c = 0; c < cols; c++) {
          const ll = -wMax + ((c + 0.5) / cols) * wMax * 2;
          if (fract(yy * 1.7 + wi * 0.37 + t * 0.8) > 0.30) continue;
          box(0.16 * Math.sin(u * 4.3 + salt * 0.013) + 0.06, ll, yy,
              0.016, 0.014, 0.016, CHORUS, opq(a * fade * 0.95));
        }
      }
    }

    // ---- 3. LA CREVASSE AU SOL -----------------------------------------------------------
    const run = smooth(clamp01((t - 0.15) / 0.24));
    const heal = clamp01((t - 0.84) / 0.16);
    if (run > 0.01 && heal < 1) {
      const reach = 4.6 * run, st = 40;
      for (let side = -1; side <= 1; side += 2) {
        for (let i = 1; i <= st; i++) {
          const u = i / st, d = u * reach;
          if (d < 0.08) continue;
          if (u > 1 - heal) continue;
          const wander = 0.30 * Math.sin(d * 1.9 + side * 1.1 + salt * 0.011);
          const gap = (0.20 + 0.16 * Math.sin(d * 3.1)) * (1 - u * 0.62) * gape;
          if (gap < 0.012) continue;
          const jag = (hash(salt + 91, i * 17 + (side + 1) * 500) - 0.5) * 0.10;
          for (let lip = -1; lip <= 1; lip += 2) {
            box(wander + lip * gap + jag, side * d, GROUND_Y + 0.014, 0.052, SLAB_H, 0.062,
                heal > 0.1 ? BASALT : HORIZON, opq(a * 0.95));
          }
          if ((i % 3) === 0) {
            const h1 = hash(salt + 92, i * 23 + (side + 1) * 700);
            const tipIn = smooth(clamp01((run - u) * 2.2));
            const sink = tipIn * (0.22 + 0.30 * h1);
            box(wander + (h1 - 0.5) * 0.42, side * d + (h1 - 0.5) * 0.3, GROUND_Y + 0.05 - sink,
                0.085, 0.028 + 0.030 * tipIn, 0.085, h1 > 0.5 ? ENDSTONE : ENDSTONE_DK,
                opq(a * (1 - tipIn * 0.55)));
          }
          if (i === Math.floor(st / 3) || i === Math.floor((st * 2) / 3)) {
            const bl = 0.9 + 0.7 * hash(salt + 93, i), bs = 10;
            for (let k = 1; k <= bs; k++) {
              const bu = k / bs, bd = bu * bl * run;
              box(wander + side * bd * 0.86, side * d + bd * 0.52, GROUND_Y + 0.014,
                  0.040 * (1 - bu * 0.6), SLAB_H, 0.040 * (1 - bu * 0.6),
                  HALO, opq(a * 0.80 * (1 - bu * 0.5)));
            }
          }
        }
      }
    }

    // ---- 4. L'ASPIRATION ------------------------------------------------------------------
    const suck = clamp01((t - 0.26) / 0.36);
    if (suck > 0.001) {
      for (let i = 0; i < 22; i++) {
        const h1 = hash(salt + 101, i * 13), h2 = hash(salt + 102, i * 17), h3 = hash(salt + 103, i * 19);
        const p = clamp01((suck - h1 * 0.40) / (0.46 + 0.34 * h2));
        if (p <= 0 || p >= 1) continue;
        const f0 = (h2 - 0.5) * 5.0, l0 = (h3 - 0.5) * 8.2;
        const glide = smooth(clamp01(p / 0.72));
        let ff = f0 * (1 - glide);
        const ll = l0 * (1 - glide * 0.28);
        let yy = GROUND_Y + 0.07;
        const tipOver = clamp01((p - 0.68) / 0.32);
        if (tipOver > 0) {
          yy += tipOver * 0.55 - GRAVITY * 0.055 * tipOver * tipOver;
          ff *= (1 - tipOver);
        }
        const sz = (0.080 + 0.040 * h1) * (1 - tipOver * 0.92);
        if (sz <= 0.006) continue;
        const roll = p * (6 + 7 * h3);
        box(ff, ll, yy, sz, sz * 0.72, sz, ENDSTONE, opq(a * (1 - tipOver * 0.4)));
        box(ff + Math.cos(roll) * sz * 0.7, ll + Math.sin(roll) * sz * 0.7, yy - sz * 0.45,
            sz * 0.48, sz * 0.40, sz * 0.48, ENDSTONE_DK, opq(a * (1 - tipOver * 0.4)));
      }
    }

    // ---- 5. LA CICATRICE ---------------------------------------------------------------------
    if (scar > 0.02) {
      for (let i = 0; i < 14; i++) {
        const h1 = hash(salt + 111, i * 13), h2 = hash(salt + 112, i * 17);
        const u = fract(h1 + scar * 1.1);
        const yy = yBase + u * height * 1.25;
        const sz = 0.026 * (1 - u * 0.7) * (1 - scar * 0.4);
        if (sz <= 0.005) continue;
        box(0.16 * Math.sin(u * 4.3 + salt * 0.013) + (h2 - 0.5) * 0.20,
            (h1 - 0.5) * 0.30, yy, sz, sz, sz, h2 > 0.70 ? INVERT : HORIZON,
            opq(a * (1 - u * 0.6)));
      }
    }
  };


  // =======================================================================================
  //  IV. PORTE D'HORIZON — 180 ticks
  // =======================================================================================
  /* Un TORE pose au sol, et ce qui y tombe ressort ailleurs. C'est la seule forme fermee a
     TROU de la famille : une sphere n'a pas d'autre cote, un anneau si. */
  const renderGate = (a) => {
    const yFloor = GROUND_Y + 0.04;
    const grow = smooth(clamp01((t - 0.04) / 0.26));
    const shut = smooth(clamp01((t - 0.86) / 0.14));
    const ring = (0.34 + 1.78 * grow) * (1 - shut);
    const spin = t * 3.4 + salt * 0.0013;

    /* ---- 1. LE TRACE AU SOL : l'empreinte existe avant la porte. ---- */
    {
      const draw = clamp01(t / 0.12);
      if (draw < 1) {
        const n = 76;
        const upTo = Math.max(1, Math.round(n * draw));
        for (let i = 0; i <= upTo; i++) {
          const ang = i / n * TAU;
          const head = i / Math.max(1, upTo);
          box(Math.cos(ang) * 2.12, Math.sin(ang) * 2.12, yFloor, 0.034, SLAB_H, 0.034,
              head > 0.92 ? SINGULAR : (head > 0.70 ? GLACIER : HALO),
              opq(a * (0.45 + 0.55 * head)));
        }
      }
    }

    /* ---- 2. LE TORE DU BAS ---- */
    if (ring > 0.02) {
      const tube = 0.145 * (0.35 + 0.65 * grow);
      torus(0, 0, yFloor + tube * 1.15, ring, tube, 0, spin, a, false);
    }

    /* ---- 3. LA MEMBRANE : spirale logarithmique a DEUX brins, qui DESCEND. Pas un
       disque : un escalier. Un disque plein boucherait le trou, c'est-a-dire le sujet. ---- */
    {
      const life = clamp01((t - 0.22) / 0.10) * (1 - clamp01((t - 0.78) / 0.12));
      if (life > 0.01 && ring > 0.25) {
        for (let brin = 0; brin < 2; brin++) {
          const phase = brin * Math.PI;
          const steps = 54;
          let prevF = 0, prevL = 0, prevY = 0;
          for (let i = 0; i <= steps; i++) {
            const u = i / steps;
            const theta = phase - spin * 1.7 + u * 4.6;
            const rr = ring * 0.94 * Math.exp(-2.35 * u);
            const yy = yFloor + 0.10 - 0.52 * u * u * life;
            const f = Math.cos(theta) * rr, l = Math.sin(theta) * rr;
            if (i > 0) {
              const fade = (1 - u * 0.45) * life;
              const rgb = u < 0.18 ? HORIZON : (u < 0.55 ? HALO : ABYSS);
              segment(prevF, prevL, prevY, f, l, yy, 0.030 + 0.018 * (1 - u),
                      rgb, opq(a * fade), 0.072);
            }
            prevF = f; prevL = l; prevY = yy;
          }
        }
      }
    }

    /* ---- 4. LE TORE DU HAUT, INCLINE. L'inclinaison interdit de le lire comme un echo. ---- */
    const outGrow = smooth(clamp01((t - 0.30) / 0.16)) * (1 - shut);
    const outRing = ring * 0.62 * outGrow;
    const outY = yFloor + 2.25;
    const tilt = 0.52;
    if (outRing > 0.04) torus(0, 0, outY, outRing, 0.105, tilt, -spin * 1.35, a * 0.95, true);

    /* ---- 5. LE TRANSIT. Trois vies par fragment : il TOMBE, il N'EXISTE PAS, il RESSORT.
       La phase morte est ce qui prouve qu'il a traverse quelque chose. ---- */
    for (let k = 0; k < 7; k++) {
      const born = 0.34 + k * 0.072;
      const age = (t - born) / 0.30;
      if (age <= 0 || age >= 1) continue;
      const angIn = hash(salt + 31, k * 7) * TAU;
      const rIn = ring * (0.42 + 0.44 * hash(salt + 32, k * 13));
      const angOut = hash(salt + 33, k * 17) * TAU;
      const rOut = outRing * (0.30 + 0.50 * hash(salt + 34, k * 19));
      const size = 0.075 + 0.045 * hash(salt + 35, k * 23);

      if (age < 0.38) {
        const u = age / 0.38;
        fragment(Math.cos(angIn) * rIn, Math.sin(angIn) * rIn,
                 yFloor + 1.60 - 1.62 * u * u, size * (1 - u * 0.25), u * 9 + k,
                 ENDSTONE, ENDSTONE_DK, opq(a));
      } else if (age < 0.52) {
        continue;
      } else {
        const u = (age - 0.52) / 0.48;
        const rr = rOut + u * 1.45;
        const yy = outY + 0.42 * Math.sin(u * Math.PI * 0.80) - 1.15 * u * u;
        if (u < 0.14) {
          const flash = 1 - u / 0.14;
          for (let s = 0; s < 3; s++) {
            const sa = angOut + (s - 1) * 0.16;
            const w = 0.055 * flash + 0.020;
            box(Math.cos(sa) * outRing, Math.sin(sa) * outRing, outY, w, w, w,
                SINGULAR, opq(a * flash));
          }
        }
        fragment(Math.cos(angOut) * rr, Math.sin(angOut) * rr, yy,
                 size * (1 - u * 0.35), u * 11 + k, INVERT, ENDSTONE_DK,
                 opq(a * (1 - u * 0.5)));
      }
    }

    /* ---- 6. LES QUATRE MONTANTS. Un tube plein retomberait sur le conduit du Siphon,
       qui appartient au Sang : quatre tiges tres fines en pointille large. ---- */
    if (outGrow > 0.06) {
      for (let k = 0; k < 4; k++) {
        const ang = k * (TAU / 4) + spin * 0.4;
        const climb = clamp01((t - 0.34) / 0.14);
        const yTop = yFloor + 0.12 + (outY - yFloor - 0.12) * climb;
        segment(Math.cos(ang) * ring * 0.96, Math.sin(ang) * ring * 0.96, yFloor + 0.12,
                Math.cos(ang) * outRing * 0.96, Math.sin(ang) * outRing * 0.96, yTop,
                0.022, HALO, opq(a * 0.80 * outGrow), 0.195);
      }
    }
  };

  // =======================================================================================
  //  V. CONSTELLATION NULLE — 200 ticks
  // =======================================================================================
  /* Sept noeuds, onze aretes, et la trame qui se dechire. Seul objet NON CENTRE de la
     famille, et seul capable de dire visuellement « ces choses-la sont SOLIDAIRES ». */
  const renderLattice = (a) => {
    const N = 7;
    const edges = [[0,1],[1,2],[2,3],[3,4],[4,5],[5,6],[6,0],[0,3],[1,4],[2,5],[3,6]];
    const converge = smooth(clamp01((t - 0.56) / 0.30));
    /* La DECHIRURE passe AVANT le point : si la trame se resorbait d'abord, les aretes
       auraient une longueur nulle au moment de rompre et personne ne verrait rien rompre. */
    const tear = clamp01((t - 0.86) / 0.07);
    const collapse = smooth(clamp01((t - 0.92) / 0.08));

    const nf = [], nl = [], ny = [];
    let bf = 0, bl = 0, by = 0;
    for (let i = 0; i < N; i++) {
      const ang = (i / N) * TAU + (hash(salt + 41, i * 7) - 0.5) * 0.70;
      const rad = 1.25 + 1.55 * hash(salt + 42, i * 11);
      nf.push(Math.cos(ang) * rad); nl.push(Math.sin(ang) * rad);
      ny.push(0.10 + 2.05 * hash(salt + 43, i * 13));
      bf += nf[i]; bl += nl[i]; by += ny[i];
    }
    bf /= N; bl /= N; by /= N;

    const pf = [], pl = [], py = [];
    for (let i = 0; i < N; i++) {
      const pull = converge * 0.46 + collapse * 0.54;
      const drift = (1 - converge) * 0.055;
      pf.push(nf[i] + (bf - nf[i]) * pull + Math.sin(t * 5.1 + i * 1.7) * drift);
      pl.push(nl[i] + (bl - nl[i]) * pull + Math.cos(t * 4.3 + i * 2.3) * drift);
      py.push(ny[i] + (by - ny[i]) * pull + Math.sin(t * 6.2 + i * 0.9) * drift * 0.7);
    }

    /* ---- 1. LES ARETES. Elles PENDENT avant de se tendre : une arete droite d'emblee est
       un trait de regle, elle ne pese rien. La catenaire donne la matiere. ---- */
    for (let e = 0; e < edges.length; e++) {
      const i = edges[e][0], j = edges[e][1];
      const born = 0.10 + Math.max(i, j) * 0.012;
      if (t < born) continue;
      const tenseAt = 0.38 + hash(salt + 51, e * 29) * 0.30;
      const tense = smooth(clamp01((t - tenseAt) / 0.085));
      const sagAmount = (1 - tense) * 0.28;
      const len = dist3(pf[i], pl[i], py[i], pf[j], pl[j], py[j]);
      if (len < 0.02) continue;

      const cutIn = tear * 0.5;
      const steps = Math.max(6, Math.min(22, Math.round(len * 7)));
      let prevF = 0, prevL = 0, prevY = 0, havePrev = false;
      for (let s = 0; s <= steps; s++) {
        const u = s / steps;
        if (tear > 0.001 && Math.abs(u - 0.5) < cutIn) { havePrev = false; continue; }
        const f = pf[i] + (pf[j] - pf[i]) * u;
        const l = pl[i] + (pl[j] - pl[i]) * u;
        const k = (u - 0.5) * 2;
        const sag = (Math.cosh(1.65) - Math.cosh(1.65 * k)) / (Math.cosh(1.65) - 1);
        const y = py[i] + (py[j] - py[i]) * u - sag * sagAmount * len * 0.42;
        if (havePrev) {
          /* DEUX tons quand la trame est tendue : une fois resserrees, onze aretes d'un
             seul ton ne font plus qu'une masse grise. La parite les redecoupe. */
          const rgb = tense > 0.55 ? ((e & 1) === 0 ? GLACIER : HORIZON)
                                   : (tense > 0.12 ? HORIZON : HALO);
          segment(prevF, prevL, prevY, f, l, y, 0.023 + 0.016 * tense, rgb,
                  opq(a * (0.66 + 0.34 * tense) * (1 - tear * 0.4)), 0.052);
        }
        prevF = f; prevL = l; prevY = y; havePrev = true;
      }

      /* L'ECLAT qui parcourt l'arete au moment ou elle se tend : le seul endroit du
         fichier ou quelque chose se propage LE LONG d'une arete. */
      const sparkU = (t - tenseAt) / 0.085;
      if (sparkU > 0 && sparkU < 1) {
        const f = pf[i] + (pf[j] - pf[i]) * sparkU;
        const l = pl[i] + (pl[j] - pl[i]) * sparkU;
        const k = (sparkU - 0.5) * 2;
        const sag = (Math.cosh(1.65) - Math.cosh(1.65 * k)) / (Math.cosh(1.65) - 1);
        const y = py[i] + (py[j] - py[i]) * sparkU
                - sag * (1 - smooth(sparkU)) * 0.28 * len * 0.42;
        box(f, l, y, 0.055, 0.055, 0.055, SINGULAR, opq(a));
      }
    }

    /* ---- 2. LES NOEUDS ---- */
    for (let i = 0; i < N; i++) {
      const wake = smooth(clamp01((t - i * 0.0135) / 0.040));
      if (wake <= 0.01) continue;
      node(pf[i], pl[i], py[i], (0.095 + 0.030 * hash(salt + 44, i * 17)) * wake,
           t * 2.1 + i * 0.9, collapse, a);
    }

    /* ---- 3. LE POINT ---- */
    if (collapse > 0.02) {
      const puff = collapse * (1 - tear);
      const w = 0.085 * puff + 0.02;
      box(bf, bl, by, w, w, w, SINGULAR, opq(a));
      for (let s = 0; s < 6; s++) {
        const sf = (s === 0 ? 1 : s === 1 ? -1 : 0) * 0.20 * puff;
        const sl = (s === 2 ? 1 : s === 3 ? -1 : 0) * 0.20 * puff;
        const sy = (s === 4 ? 1 : s === 5 ? -1 : 0) * 0.20 * puff;
        segment(bf, bl, by, bf + sf, bl + sl, by + sy, 0.022, GLACIER,
                opq(a * puff), 0.045);
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

if (typeof module !== 'undefined' && module.exports) module.exports = { buildVoid };
