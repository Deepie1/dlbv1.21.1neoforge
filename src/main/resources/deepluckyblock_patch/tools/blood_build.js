/* ===========================================================================================
 * blood_build.js — MIROIR JS de deepluckyblock.client.BloodSignatureVfx
 *
 * Même rôle que thunder_build.js et shadow_build.js : rejouer la géométrie Java dans un
 * navigateur pour la relire sans lancer Minecraft. Toute divergence avec le .java est un
 * BOGUE du miroir.
 *
 * Repère : le Java travaille en (f = axe attaquant→cible, lateral = perpendiculaire,
 * y = hauteur) puis projette avec dx/dz. L'aperçu fixe dx = 1, dz = 0, l'origine à l'impact,
 * donc        x = f        y = y        z = lateral
 * et l'orientation vaut ZÉRO : contrairement au miroir de l'ombre, aucune boîte du sang n'est
 * tournée (BloodSignatureVfx n'emploie que box(), jamais emitVoxelBoxRotated), donc
 * yaw = pitch = roll = 0 partout. C'est la raison pour laquelle ce miroir n'a ni FOFF/FSGN
 * ni base face caméra.
 *
 * ATTENTION — ce que l'aperçu ne modélise PAS : le tamis du moteur (pixelDensity < 0.999 &&
 * alpha < 200 décime les cubes), cubeVeil, ni le plafond de 7000 cubes par frame. Un rendu
 * correct ici ne prouve donc RIEN en jeu ; il sert à relire la chorégraphie.
 *
 * CE MIROIR N'EST PAS UNE TRANSCRIPTION LIGNE À LIGNE : box/drop/splash/pool/drawHeart sont
 * des CLOSURES ici, des méthodes statiques là-bas.
 * =========================================================================================== */

// linkEndY par defaut 0,25 et non 0,65 : c'est la vraie difference de hauteur entre la main
// d'un porteur (bbHeight x 0,62) et la poitrine d'une victime (bbHeight x 0,50) pour deux
// corps de taille comparable. 0,65 faisait monter le tuyau vers un porteur geant.
function buildBlood(variant, t, salt = 5099, linkLen = 7.0, linkEndY = 0.25, linkBroken = false) {
  const out = [];
  if (t <= 0 || t >= 1) return out;

  // ---- Parité stricte avec les utilitaires du .java -------------------------------------
  const TAU = 6.2831855;
  const HALF_PI = 1.5707964;
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

  // ---- Palette (DeepElement.BLOOD : 0xDD0000 / 0xB20000 / 0xFF0000) ----------------------
  // Six rouges, et TROIS matières étrangères. Un enchantement de sang frappe presque toujours
  // une créature déjà couverte de particules de dégâts rouges : sans acier, sans os et sans
  // le rose pâle des reflets, la signature se noie dans sa propre cible.
  const CLOT = 0x2B0507, VENOUS = 0x5E0A10, GORE = 0x8E0F16,
        ARTERY = 0xC2121B, FRESH = 0xE8252C, SPRAY = 0xFF5A52, PLASMA = 0xFFB9A8;
  const IRON = 0x6E7279, IRON_DK = 0x3A3E44, EDGE_LT = 0xD9DEE6,
        TIMBER = 0x4A3426, TIMBER_LIT = 0x8A6A4C, BONE = 0xE8E0CC;

  // L'aperçu n'a pas de monde à sonder : GROUND_Y garde sa valeur de repli (cf.
  // BloodSignatureVfx.GROUND_Y / WeaponWorldVfxManager.probeGroundOffset).
  const GROUND_Y = -0.86, SLAB_H = 0.030;
  const REACH = 7.0;
  const GRAVITY = 11.0;

  // ---- Émission --------------------------------------------------------------------------
  const box = (f, lateral, y, hf, hy, hl, rgb, a) => {
    if (a <= 0 || hf <= 0 || hy <= 0 || hl <= 0) return;
    out.push({ x: f, y: y, z: lateral, hx: hf, hy: hy, hz: hl,
               rgb: rgb, a: Math.min(255, a), yaw: 0, pitch: 0, roll: 0 });
  };

  /* Une GOUTTE. Jamais un cube seul : corps sombre, méniscus clair au sommet, traînée fine
     au-dessus. C'est la traînée qui donne la vitesse — sans elle, un grain en lévitation. */
  const drop = (f, lateral, y, r, a) => {
    if (r <= 0.004) return;
    box(f, lateral, y, r * 0.86, r, r * 0.86, GORE, opq(a));
    box(f, lateral, y + r * 0.72, r * 0.44, r * 0.34, r * 0.44, PLASMA, opq(a * 0.95));
    box(f, lateral, y + r * 1.55, r * 0.26, r * 0.62, r * 0.26, ARTERY, opq(a * 0.70));
  };

  /* Une FLAQUE. Disque irrégulier de dalles, avec un bord clair : sans méniscus une flaque
     est un autocollant. Contour bruité par deux harmoniques, donc jamais rond. */
  const pool = (f, lateral, radius, a, seed) => {
    if (radius <= 0.03) return;
    const n1 = hash(seed, 3) * TAU, n2 = hash(seed + 1, 5) * TAU;
    const rings = Math.max(2, Math.floor(radius / 0.16));
    for (let r = 0; r < rings; r++) {
      const rv = (r + 0.5) / rings;
      const pts = Math.max(8, Math.floor(10 + 26 * rv));
      for (let i = 0; i < pts; i++) {
        const ang = (i + 0.5) / pts * TAU;
        const wob = 1 + 0.18 * Math.sin(ang * 3 + n1) + 0.11 * Math.sin(ang * 5 + n2);
        const rr = radius * rv * wob;
        const rim = r === rings - 1;
        box(f + Math.cos(ang) * rr, lateral + Math.sin(ang) * rr,
            GROUND_Y + (rim ? 0.016 : 0.008),
            radius / rings * 0.62, SLAB_H, radius / rings * 0.62,
            rim ? ARTERY : (rv > 0.55 ? VENOUS : CLOT), opq(a * 0.92));
      }
    }
  };

  /* Une ÉCLABOUSSURE : la couronne de gouttelettes d'un impact, et le disque qu'elle laisse. */
  const splash = (f, lateral, u, power, a, seed) => {
    if (u <= 0 || u >= 1) return;
    const petals = 9;
    for (let i = 0; i < petals; i++) {
      const h1 = hash(seed, i * 17), h2 = hash(seed + 1, i * 19);
      const ang = (i + h1 * 0.6) / petals * TAU;
      const d = power * (0.25 + 0.70 * h2) * smooth(u);
      const yy = GROUND_Y + 0.02 + power * (0.42 * Math.sin(u * PI)) * (0.5 + h1);
      const sz = 0.022 * (1 - u * 0.55) * (0.7 + 0.6 * h2);
      if (sz <= 0.004) continue;
      box(f + Math.cos(ang) * d, lateral + Math.sin(ang) * d, yy, sz, sz, sz,
          i % 3 === 0 ? FRESH : ARTERY, opq(a * 0.9));
    }
    pool(f, lateral, power * 0.30 * smooth(u), a, seed + 7);
  };

  // =======================================================================================
  //  I. COUPERET DE GUILLOTINE — 150 ticks
  // =======================================================================================
  const renderGuillotine = (alpha) => {
    const a = Math.round(alpha * smooth(t / 0.03));
    if (a <= 4) return;
    const plant = smooth(t / 0.10);
    const lintel = smooth((t - 0.10) / 0.06);
    const hoist = clamp01((t - 0.16) / 0.22);
    const fall = smooth((t - 0.50) / 0.055);
    const bite = smooth((t - 0.555) / 0.065);
    const sheet = clamp01((t - 0.62) / 0.20);
    const sink = smooth((t - 0.82) / 0.18);
    const strobe = Math.floor(t * 30.0);

    // La hisse procède par PALIERS : cinq tractions de chaîne, chacune suivie d'un repos.
    // Un glissement linéaire donnait un ascenseur ; les paliers donnent un treuil.
    // topY plafonne a 3,05 : le couperet mesure 1,40 bloc, le linteau est a 4,60.
    const topY = 3.05, neckY = 1.35;
    let bladeY;
    if (t < 0.16) bladeY = neckY;
    else if (hoist < 1.0) {
      const steps = hoist * 5.0;
      const done = Math.floor(steps);
      const inner = smooth((steps - done) / 0.62);
      bladeY = neckY + (topY - neckY) * ((done + inner) / 5.0);
    } else if (t < 0.50) {
      // Suspens : la lame TREMBLE d'un demi-pixel. Immobile pour de bon, elle se lirait
      // comme une image fixe ; ce frémissement dit qu'elle est retenue.
      bladeY = topY + (hash(salt + 3, strobe) - 0.5) * 0.016;
    } else bladeY = topY - (topY - (GROUND_Y + 0.22)) * fall;

    const structureDrop = sink * 5.4;

    // ---- 1. LES DEUX MONTANTS ----
    const over = 1 + 0.08 * Math.sin(clamp01(t / 0.10) * PI);
    const h = 4.6 * plant * over;
    if (h > 0.02) {
      for (let side = -1; side <= 1; side += 2) {
        const lat = side * 0.98;
        const segs = 13;
        for (let i = 0; i < segs; i++) {
          const u = (i + 0.5) / segs;
          const yy = GROUND_Y + u * h - structureDrop;
          if (yy < GROUND_Y - 0.3) continue;
          box(0, lat, yy, 0.115, h / segs * 0.52, 0.115, TIMBER, opq(a * 0.95));
          box(-0.112, lat, yy, 0.020, h / segs * 0.50, 0.118, TIMBER_LIT, opq(a * 0.85));
          box(0, lat - side * 0.108, yy, 0.030, h / segs * 0.50, 0.022, IRON_DK, opq(a * 0.80));
        }
        for (let k = 0; k < 3; k++) {
          const yy = GROUND_Y + h * (0.22 + 0.30 * k) - structureDrop;
          if (yy < GROUND_Y - 0.3) continue;
          box(0, lat, yy, 0.132, 0.042, 0.132, IRON, opq(a * 0.90));
        }
      }
      if (t < 0.22) {
        const burst = Math.sin(clamp01(t / 0.14) * PI);
        for (let i = 0; i < 14; i++) {
          const h1 = hash(salt + 11, i * 29), h2 = hash(salt + 12, i * 31);
          const ang = h1 * TAU, rr = (0.85 + 0.9 * h2) * burst;
          box(Math.cos(ang) * rr, Math.sin(ang) * rr, GROUND_Y + 0.04 + burst * 0.30 * h2,
              0.040, 0.040, 0.040, TIMBER, opq(a * 0.55 * burst));
        }
      }
    }

    // ---- 2. LE LINTEAU ----
    if (lintel > 0.001) {
      const yy = GROUND_Y + 4.60 - structureDrop, w = 1.18 * lintel;
      box(0, 0, yy, 0.135, 0.115, w, TIMBER, opq(a));
      box(0, 0, yy + 0.108, 0.140, 0.026, w * 0.96, IRON, opq(a * 0.85));
      box(-0.132, 0, yy, 0.020, 0.112, w * 0.98, TIMBER_LIT, opq(a * 0.85));
      box(0, 0, yy - 0.16, 0.055, 0.090, 0.090, IRON_DK, opq(a * 0.90));
    }

    // ---- 3. LA CHAÎNE ---- (maillons alternés : tous dans le même plan = une crémaillère)
    if (lintel > 0.4 && t < 0.52) {
      const top = GROUND_Y + 4.42 - structureDrop, bottom = GROUND_Y + bladeY + 1.42;
      const links = Math.max(1, Math.floor((top - bottom) / 0.17));
      for (let i = 0; i < links; i++) {
        const u = (i + 0.5) / links, yy = bottom + (top - bottom) * u;
        const flat = (i & 1) === 0;
        box(0, 0, yy, flat ? 0.052 : 0.022, 0.062, flat ? 0.022 : 0.052, IRON, opq(a * 0.88));
      }
    }

    // ---- 4. LE COUPERET ---- (biseau obtenu en COLONNES, pas par un roulis)
    if (t > 0.14 || plant > 0.9) {
      const by = GROUND_Y + bladeY;
      // 24 colonnes, et un corps de lame de hauteur constante sous le biseau : un
      // couperet est un TRAPEZE, pas un triangle.
      const cols = 24, halfW = 0.86, bevel = 0.62, bodyH = 0.46;
      for (let i = 0; i < cols; i++) {
        const u = (i + 0.5) / cols;
        const lat = -halfW + 2 * halfW * u;
        const low = by + bevel * u, high = by + bevel + bodyH;
        const mid = (low + high) * 0.5, hh = (high - low) * 0.5;
        if (hh <= 0.004) continue;
        box(0, lat, mid, 0.058, hh, halfW / cols, IRON, opq(a));
        box(-0.050, lat, mid, 0.016, hh * 0.92, halfW / cols * 0.9, IRON_DK, opq(a * 0.85));
        box(0, lat, low + 0.018, 0.062, 0.018, halfW / cols, EDGE_LT, opq(a));
        if (bite > 0.02 && hash(salt + 21, i * 17) < 0.45) {
          const sm = 0.10 + 0.30 * hash(salt + 22, i * 19);
          box(0.058, lat, low + 0.10 + sm * 0.5, 0.012, sm * 0.5, halfW / cols * 0.7,
              VENOUS, opq(a * 0.9));
        }
      }
      const crown = by + bevel + bodyH;
      box(0, 0, crown + 0.15, 0.082, 0.150, halfW * 0.72, IRON_DK, opq(a));
      box(0, 0, crown + 0.31, 0.088, 0.022, halfW * 0.70, IRON, opq(a * 0.9));
      if (t >= 0.50 && fall < 1.0) {
        for (let k = 1; k <= 3; k++) {
          const lag = by + k * 0.52 * (1 - fall);
          if (lag > GROUND_Y + topY) continue;
          box(0, 0, lag, 0.040, 0.030, halfW * (1 - k * 0.18), EDGE_LT, opq(a * (0.42 - k * 0.10)));
        }
      }
    }

    // ---- 5. LA GOUTTE DU SUSPENS ---- (une seule, et c'est pour cela qu'on la voit)
    if (t > 0.40 && t < 0.52) {
      const age = (t - 0.40) / 0.12;
      const y0 = GROUND_Y + topY;
      const yy = y0 - GRAVITY * 0.085 * age * age;
      if (yy > GROUND_Y + 0.02) drop(0, -0.52, yy, 0.044, a);
      else splash(0, -0.52, clamp01((age - 0.55) / 0.45), 0.55, a, salt + 31);
    }

    // ---- 6. L'IMPACT ----
    if (bite > 0.001) {
      const rr = 0.30 + 2.35 * smooth(bite);
      const pts = 30;
      for (let i = 0; i < pts; i++) {
        const ang = (i + 0.5) / pts * TAU;
        const wob = 1 + 0.14 * Math.sin(ang * 3 + salt * 0.01);
        const thin = 1 - bite * 0.55;
        box(Math.cos(ang) * rr * wob, Math.sin(ang) * rr * wob, GROUND_Y + 0.012,
            0.075 * thin, SLAB_H, 0.075 * thin, ARTERY, opq(a * 0.88));
      }
      const jets = 22;
      for (let i = 0; i < jets; i++) {
        const h1 = hash(salt + 41, i * 23), h2 = hash(salt + 42, i * 29);
        const ang = (h1 - 0.5) * 2.6 + (i % 2 === 0 ? 0 : PI);
        const speed = 1.6 + 2.6 * h2, age = bite, d = speed * age;
        const yy = GROUND_Y + neckY - GRAVITY * 0.055 * age * age + 0.25 * age;
        if (yy < GROUND_Y + 0.03) continue;
        const sz = 0.052 * (1 - 0.45 * age);
        box(Math.cos(ang) * d, Math.sin(ang) * d, yy, sz, sz, sz,
            i % 3 === 0 ? FRESH : ARTERY, opq(a));
      }
    }

    // ---- 7. LA NAPPE ---- (colonnes indépendantes : d'un seul tenant ce serait un drap)
    if (sheet > 0.001) {
      const cols = 19;
      for (let i = 0; i < cols; i++) {
        const u = (i + 0.5) / cols;
        const lat = (u - 0.5) * 2.1;
        const phase = clamp01(sheet * 1.35 - Math.abs(u - 0.5) * 0.55);
        if (phase <= 0) continue;
        const rise = Math.sin(phase * PI) * (1.45 - 0.85 * Math.abs(u - 0.5) * 2.0);
        if (rise <= 0.02) continue;
        const segs = 7;
        for (let k = 0; k < segs; k++) {
          const v = (k + 0.5) / segs;
          const yy = GROUND_Y + 0.06 + rise * v;
          const lean = 0.42 * rise * v * v;
          const rgb = v > 0.80 ? SPRAY : (v > 0.45 ? ARTERY : GORE);
          box(lean, lat, yy, 0.038, rise / segs * 0.56, 0.062, rgb, opq(a * 0.95));
        }
        if (phase > 0.55) {
          const age = (phase - 0.55) / 0.45;
          const yy = GROUND_Y + 0.06 + rise - GRAVITY * 0.05 * age * age;
          if (yy > GROUND_Y + 0.04) drop(0.42 * rise + 0.5 * age, lat, yy, 0.034, a);
        }
      }
    }

    // ---- 8. LA TACHE ----
    if (bite > 0.1) {
      pool(0, 0, 1.65 * smooth(clamp01((t - 0.56) / 0.26)) * (1 - sink * 0.75), a, salt + 51);
    }
  };

  // =======================================================================================
  //  II. FIL QUI GOUTTE — 120 ticks
  // =======================================================================================
  const renderEdge = (alpha) => {
    const a = Math.round(alpha * smooth(t / 0.03));
    if (a <= 4) return;
    const trace = smooth(t / 0.08);
    const open = smooth((t - 0.08) / 0.14);
    const shut = smooth((t - 0.85) / 0.15);
    const gape = open * (1 - shut);
    const woundY = 0.95, tilt = 0.52, span = 1.25;

    // ---- 1. L'ENTAILLE ----
    const segs = 21;
    for (let i = 0; i < segs; i++) {
      const u = (i + 0.5) / segs;
      const along = (u - 0.5) * 2.0;
      const lat = along * span;
      const yy = woundY + along * tilt * 0.5;
      const reveal = clamp01((trace - Math.abs(along) * 0.35) * 1.6);
      if (reveal <= 0) continue;
      const lens = Math.sqrt(Math.max(0, 1 - along * along));
      const w = 0.012 + 0.145 * lens * gape;
      // Une plaie fermée est un trait, pas un trait épais : sous 0,035 on ne garde que les lèvres.
      if (w > 0.035) {
        box(0.055, lat, yy, 0.030, w, 0.064, CLOT, opq(a));
        box(0.070, lat, yy, 0.016, w * 0.62, 0.052, VENOUS, opq(a * 0.95));
      }
      for (let s = -1; s <= 1; s += 2) {
        box(0.082, lat, yy + s * w, 0.022, 0.020, 0.062, s > 0 ? FRESH : ARTERY, opq(a * reveal));
      }
      if (gape > 0.3 && hash(salt + 61, i * 13) < 0.55) {
        const bead = 0.016 + 0.018 * hash(salt + 62, i * 17);
        box(0.092, lat, yy - w - 0.014, bead, bead, bead, ARTERY, opq(a));
      }
    }

    // ---- 2. LES RIGOLES ---- (sept vitesses : synchronisées, elles feraient un peigne)
    const runnels = 7;
    for (let i = 0; i < runnels; i++) {
      const h1 = hash(salt + 71, i * 23), h2 = hash(salt + 72, i * 29), h3 = hash(salt + 73, i * 31);
      const along = (i + 0.5) / runnels * 2.0 - 1.0;
      const lat = along * span * 0.88 + (h1 - 0.5) * 0.10;
      const top = woundY + along * tilt * 0.5 - 0.04;
      const start = 0.22 + h1 * 0.14;
      const speed = 0.55 + 0.75 * h2;
      const grow = clamp01((t - start) * speed * 2.2);
      if (grow <= 0) continue;
      const len = (top - GROUND_Y) * grow * (1 - shut * 0.9);
      if (len <= 0.02) continue;
      const ns = Math.max(2, Math.floor(len / 0.085));
      for (let k = 0; k < ns; k++) {
        const v = (k + 0.5) / ns;
        const yy = top - len * v;
        const wig = Math.sin(v * 7.0 + h3 * 6.28) * 0.028 * (1 - v * 0.4);
        const w = (0.026 - 0.011 * v) * (0.8 + 0.4 * h2);
        box(0.086, lat + wig, yy, 0.016, len / ns * 0.56, w,
            v < 0.25 ? ARTERY : (v < 0.7 ? GORE : VENOUS), opq(a * 0.95));
      }
      const swell = fract(grow * 2.6 + h3);
      const bead = 0.018 + 0.026 * swell;
      const by = top - len;
      if (by > GROUND_Y + 0.03) {
        box(0.088, lat, by, bead * 0.8, bead, bead, ARTERY, opq(a));
        box(0.096, lat, by + bead * 0.5, bead * 0.34, bead * 0.30, bead * 0.34, PLASMA, opq(a * 0.9));
      }
    }

    // ---- 3. LES GOUTTES ----
    const drops = 16;
    for (let i = 0; i < drops; i++) {
      const h1 = hash(salt + 81, i * 19), h2 = hash(salt + 82, i * 23);
      const born = 0.30 + h1 * 0.52;
      const age = (t - born) / 0.26;
      if (age <= 0 || age >= 1.6) continue;
      const lat = (h2 - 0.5) * span * 1.7;
      const y0 = woundY - 0.25 - h1 * 0.5;
      const yy = y0 - GRAVITY * 0.085 * age * age;
      if (yy > GROUND_Y + 0.03) drop(0.088, lat, yy, 0.026 + 0.012 * h2, a);
      else {
        const hit = clamp01((age - Math.sqrt((y0 - GROUND_Y) / (GRAVITY * 0.085))) / 0.35);
        if (hit > 0 && hit < 1) splash(0.088, lat, hit, 0.42, a, salt + 91 + i);
      }
    }

    // ---- 4. LA FLAQUE ----
    const grow = smooth(clamp01((t - 0.25) / 0.45));
    const r = 1.15 * grow * (1 - shut * 0.70);
    if (r > 0.04) pool(0.16, 0, r, a, salt + 101);
  };

  // =======================================================================================
  //  III. SIPHON CARMIN — 170 ticks
  // =======================================================================================
  /* T381 : refonte pour la LISIBILITE. Trois objets, trois seulement — l'embout visse dans
     la victime, le tuyau en arc a cotes glissantes, le calice qui se remplit dans la main du
     porteur. Plus de colonne de niveau (invisible en jeu, tonneau dans l'apercu), plus de
     ponction a 95 cm au-dessus de la poitrine, plus de ventre qui racle le sol. */
  const renderSiphon = (alpha) => {
    const a = Math.round(alpha * smooth(t / 0.03));
    if (a <= 4) return;
    const bite  = smooth(t / 0.08);
    const build = smooth((t - 0.08) / 0.12);
    const drain = clamp01((t - 0.20) / 0.60);
    const slack = clamp01((t - 0.80) / 0.10);
    const snap  = Math.max(clamp01((t - 0.90) / 0.10), linkBroken ? 1 : 0);

    const reach = linkLen;
    const mouthY = 0.12, handY = linkEndY;
    const arch = (0.30 + 0.085 * reach) * (1 - 0.30 * drain);

    let poured = 0;
    for (let b = 0; b < 6; b++) poured += smooth((t - (0.20 + b * 0.098) - 0.30) / 0.05);
    const fill = Math.min(1, poured / 6);

    /* ---- A. L'EMBOUT ---- */
    if (bite > 0.001 && snap < 0.85) {
      const set = smooth(bite) * (1 - snap);
      for (let ring = 0; ring < 2; ring++) {
        const rr = (0.26 - ring * 0.07) * set, ff = 0.02 - ring * 0.10;
        for (let k = 0; k < 10; k++) {
          const ang = (k + 0.5) / 10 * TAU;
          box(ff, Math.cos(ang) * rr, mouthY + Math.sin(ang) * rr,
              0.042, 0.048, 0.048, ring === 0 ? IRON_DK : IRON, opq(a * 0.95));
        }
      }
      for (let i = 0; i < 4; i++) {
        const ang = i * HALF_PI + 0.42;
        const lat = Math.cos(ang) * 0.30 * set, yy = mouthY + Math.sin(ang) * 0.30 * set;
        const plunge = 0.16 - 0.13 * set;
        box(plunge + 0.11, lat * 1.30, yy + Math.sin(ang) * 0.09, 0.110, 0.040, 0.040, BONE, opq(a * 0.95));
        box(plunge, lat * 1.05, yy, 0.048, 0.046, 0.046, BONE, opq(a));
        if (set > 0.6) box(plunge - 0.02, lat * 1.10, yy - 0.06, 0.018, 0.034, 0.018, ARTERY, opq(a));
      }
    }

    /* ---- B. LE TUYAU ---- */
    const segs = Math.max(30, Math.round(9.0 * reach)), segLen = reach / segs;
    for (let i = 0; i < segs; i++) {
      const u = (i + 0.5) / segs;
      if (u > build) continue;
      if (u < snap) continue;
      const f = u * (-reach + 0.22);
      let yy = mouthY + (handY - mouthY) * u + Math.sin(u * PI) * arch;
      // Le bec verseur : le tuyau vise le BORD de la coupe (handY + 0,22).
      if (u > 0.90) { const k = smooth((u - 0.90) / 0.10); yy = yy + (handY + 0.22 - yy) * k; }
      const whip = (snap > 0.001 && u < snap + 0.16)
          ? Math.sin((t - 0.90) * 34.0) * 0.30 * (1 - snap) : 0;

      let bulge = 0;
      for (let b = 0; b < 6; b++) {
        const travel = (t - (0.20 + b * 0.098)) / 0.30;
        if (travel <= 0 || travel >= 1) continue;
        const d = Math.abs(u - smooth(travel));
        if (d < 0.075) bulge += 1 - d / 0.075;
      }
      bulge = Math.min(1, bulge);

      const thin = 1 - 0.30 * slack;
      const r = (0.085 + 0.075 * bulge) * thin;
      const body = bulge > 0.30 ? FRESH : GORE;
      const yv = yy + whip * 0.4;

      box(f, whip, yv, segLen * 0.62, r, r, body, opq(a * 0.96));
      box(f, whip, yv + r * 0.80, segLen * 0.58, r * 0.30, r * 0.52, ARTERY, opq(a * 0.85));
      box(f, whip, yv - r * 0.82, segLen * 0.58, r * 0.26, r * 0.50, CLOT, opq(a * 0.80));

      const band = (u * reach * 2.35) - t * 3.1;
      if (band - Math.floor(band) < 0.14) {
        box(f, whip, yv, segLen * 0.80, r * 1.30, r * 1.30, CLOT, opq(a * 0.95));
      }
      if (bulge > 0.55) {
        box(f, whip, yv + r * 1.05, segLen * 0.46, 0.014, r * 1.30, SPRAY, opq(a));
      }
    }

    /* ---- C. LE CALICE ---- */
    {
      const born = smooth((t - 0.16) / 0.06);
      if (born > 0.01) {
        const cf = -reach + 0.22, base = handY - 0.20, lip = handY + 0.26, sc = born;
        for (let k = 0; k < 8; k++) {
          const ang = (k + 0.5) / 8 * TAU;
          box(cf + Math.cos(ang) * 0.18 * sc, Math.sin(ang) * 0.18 * sc, base - 0.32,
              0.050, 0.032, 0.050, IRON_DK, opq(a * 0.90));
        }
        box(cf, 0, base - 0.16, 0.040, 0.090, 0.040, IRON, opq(a * 0.92));
        const walls = 6;
        for (let ring = 0; ring < walls; ring++) {
          const v = ring / (walls - 1), rr = (0.19 + 0.16 * v) * sc;
          const yy = base + (lip - base) * v, pts = 12 + ring;
          for (let k = 0; k < pts; k++) {
            const ang = (k + 0.5) / pts * TAU + ring * 0.21;
            box(cf + Math.cos(ang) * rr, Math.sin(ang) * rr, yy, 0.046, 0.042, 0.046,
                ring === walls - 1 ? EDGE_LT : (ring === 0 ? IRON_DK : IRON), opq(a * 0.95));
          }
        }
        for (let k = 0; k < 7; k++) {
          const ang = (k + 0.5) / 7 * TAU;
          box(cf + Math.cos(ang) * 0.09 * sc, Math.sin(ang) * 0.09 * sc, base - 0.02,
              0.055, 0.030, 0.055, IRON_DK, opq(a * 0.95));
        }
        if (fill > 0.01) {
          // Un VOLUME empile du fond au niveau, pas une galette invisible derriere la paroi.
          const lv = 0.14 + 0.78 * fill, top = base + (lip - base) * lv;
          const layers = Math.max(3, Math.round((top - base) / 0.045));
          for (let ly = 0; ly < layers; ly++) {
            const v = (ly + 0.5) / layers, yy = base + (top - base) * v + 0.02;
            const prof = (yy - base) / Math.max(0.05, lip - base);
            const rr = (0.17 + 0.14 * prof) * sc, surface = ly === layers - 1;
            const pts = surface ? 12 : 9;
            for (let k = 0; k < pts; k++) {
              const ang = (k + 0.5) / pts * TAU + ly * 0.7;
              box(cf + Math.cos(ang) * rr * 0.84, Math.sin(ang) * rr * 0.84, yy,
                  0.066, 0.034, 0.066, surface ? SPRAY : (v > 0.5 ? FRESH : GORE), opq(a * 0.95));
            }
            box(cf, 0, yy, 0.075, 0.034, 0.075, surface ? ARTERY : VENOUS, opq(a * 0.95));
          }
        }
        for (let b = 0; b < 6; b++) {
          const age = (t - (0.20 + b * 0.098 + 0.30)) / 0.05;
          if (age <= 0 || age >= 1) continue;
          for (let k = 0; k < 4; k++) {
            const ang = hash(salt + 211 + b, k * 13) * TAU, d = 0.10 + 0.22 * age;
            drop(cf + Math.cos(ang) * d, Math.sin(ang) * d,
                 lip + 0.30 * age - GRAVITY * 0.06 * age * age, 0.026, Math.round(a * (1 - age)));
          }
        }
      }
    }

    /* ---- D. LA PLAIE QUI GOUTTE ---- */
    if (drain > 0.02) {
      for (let k = 0; k < 4; k++) {
        const h1 = hash(salt + 331, k * 17), fall = (t * 3.1 + h1) % 1;
        const yy = mouthY - 0.10 - GRAVITY * 0.055 * fall * fall;
        if (yy < GROUND_Y + 0.05) continue;
        drop(0.06 + (h1 - 0.5) * 0.12, (hash(salt + 332, k) - 0.5) * 0.26, yy,
             0.026, Math.round(a * 0.85));
      }
      pool(0.05, 0.0, 0.22 + 0.46 * smooth(drain), Math.round(a * 0.80), salt + 341);
    }

    /* ---- E. L'ARRACHEMENT ---- */
    if (snap > 0.001) {
      for (let i = 0; i < 4; i++) {
        const ang = i * HALF_PI + 0.42, d = 0.30 + 1.1 * snap;
        box(0.10 + 0.5 * snap, Math.cos(ang) * d,
            mouthY + Math.sin(ang) * d * 0.6 - GRAVITY * 0.04 * snap * snap,
            0.080, 0.038, 0.038, BONE, opq(a * (1 - snap)));
      }
      for (let k = 0; k < 9; k++) {
        const h1 = hash(salt + 351, k * 23), h2 = hash(salt + 352, k * 29);
        const d = (0.3 + 1.5 * h1) * snap;
        drop(0.05 - d * 0.5, (h2 - 0.5) * 1.8 * snap,
             mouthY + 0.4 * snap - GRAVITY * 0.07 * snap * snap,
             0.028, Math.round(a * (1 - snap * 0.8)));
      }
    }
  };

  const rayToCircle = (ccx, ccz, r, ux, uz) => {
    const b = ccx * ux + ccz * uz;
    const c = ccx * ccx + ccz * ccz - r * r;
    const disc = b * b - c;
    if (disc < 0) return 0;
    const d = b + Math.sqrt(disc);
    return d > 0 ? d : 0;
  };

  /* T382 — le coeur, modele anatomique. L'ancien profil sin(sqrt(v)*2,55)*(0,52-0,10v) etait
     maximal AU MILIEU et retombait aux deux bouts : une amande. Un coeur est large en haut
     (la base et ses gros vaisseaux) et pointu en bas (l'apex), une seule fois. */
  const drawHeart = (hf, hl, hy, s, contract, alpha) => {
    const slices = 20, H = 1.22 * s, AV = 0.74, lean = 0.20, leanL = 0.26, step = H / slices;

    for (let i = 0; i < slices; i++) {
      const v = (i + 0.5) / slices;
      const yy = hy + (v - 0.46) * H;
      const fOff = hf + lean * (v - 0.46) * H;
      const lOff = hl + leanL * (v - 0.46) * H;
      const atrium = v > AV;
      let w;
      if (!atrium) {
        const u = v / AV;
        w = 0.60 * Math.sin(1.5707964 * Math.pow(u, 0.62)) * (0.62 + 0.38 * Math.sqrt(u))
              * (1 - 0.09 * u * u * u * u);
        w *= Math.min(1, Math.sqrt(u / 0.07));
        w *= 1 - contract * (0.26 * Math.sin(u * PI) + 0.10);
      } else {
        const u = (v - AV) / (1 - AV);
        w = 0.546 * (1 - 0.34 * u * u) * (1 - 0.16 * u);
        w *= 1 + contract * 0.10;
      }
      if (w <= 0.012) continue;
      w *= s;

      const c1 = -0.14 * w, r1 = atrium ? 0.74 * w : 0.80 * w;
      const c2 = 0.30 * w, r2 = atrium ? 0.60 * w : 0.64 * w;
      const pts = 28;
      for (let k = 0; k < pts; k++) {
        const ang = (k + 0.5) / pts * TAU;
        const ux = Math.cos(ang), uz = Math.sin(ang) * 0.86;
        const d1 = rayToCircle(c1, 0, r1, ux, uz), d2 = rayToCircle(c2, 0, r2, ux, uz);
        let rr = Math.max(d1, d2);
        if (rr <= 0.004) continue;
        const groove = !atrium && Math.abs(d1 - d2) < 0.060 * w && d1 > 0 && d2 > 0;
        if (groove) rr *= 0.93;
        if (atrium) {
          const ua = (v - AV) / (1 - AV);
          const notch = Math.max(0, 1 - Math.abs(d1 - d2) / (0.42 * w));
          rr *= 1 - 0.62 * ua * ua * notch;
        }
        let lit = (0.70 + 0.30 * Math.cos(ang - 0.9)) * 0.70 + 0.30 * v;
        if (atrium) lit *= 0.84;
        lit *= 0.93 + 0.14 * hash(salt + 141, k * 31 + i * 7);
        const rgb = groove ? VENOUS
                  : (lit > 0.84 ? SPRAY : lit > 0.66 ? FRESH : lit > 0.48 ? ARTERY
                  : lit > 0.32 ? GORE : VENOUS);
        box(fOff + ux * rr, lOff + uz * rr, yy,
            0.050 * s, step * 0.66, 0.050 * s, rgb, opq(alpha * 0.95));
      }
    }

    /* La calotte de l'apex : sans elle le coeur est coupe net par le bas. */
    {
      const v0 = 0.5 / slices, yy = hy + (v0 - 0.46) * H;
      const fOff = hf + lean * (v0 - 0.46) * H, lOff = hl + leanL * (v0 - 0.46) * H;
      for (let k = 0; k < 3; k++) {
        const d = (k + 1) / 3;
        box(fOff - lean * d * 0.10 * H, lOff - leanL * d * 0.10 * H, yy - d * 0.085 * H,
            (0.085 - 0.024 * k) * s, step * 0.55, (0.085 - 0.024 * k) * s,
            k === 2 ? VENOUS : GORE, opq(alpha * 0.95));
      }
    }

    /* Le sillon auriculo-ventriculaire : la ceinture du coeur, et la circonflexe dedans. */
    {
      const v = AV, yy = hy + (v - 0.46) * H;
      const fOff = hf + lean * (v - 0.46) * H, lOff = hl + leanL * (v - 0.46) * H;
      const w = 0.60 * s * 0.91 * (1 - contract * 0.12);
      const c1 = -0.14 * w, r1 = 0.80 * w, c2 = 0.30 * w, r2 = 0.64 * w;
      for (let k = 0; k < 30; k++) {
        const ang = (k + 0.5) / 30 * TAU, ux = Math.cos(ang), uz = Math.sin(ang) * 0.86;
        const rr = Math.max(rayToCircle(c1, 0, r1, ux, uz), rayToCircle(c2, 0, r2, ux, uz));
        if (rr <= 0.004) continue;
        box(fOff + ux * rr * 0.99, lOff + uz * rr * 0.99, yy,
            0.080 * s, 0.036 * s, 0.080 * s, VENOUS, opq(alpha * 0.94));
        if (Math.cos(ang) < -0.1) {
          box(fOff + ux * rr, lOff + uz * rr, yy + 0.012 * s,
              0.046 * s, 0.024 * s, 0.046 * s, contract > 0.05 ? SPRAY : ARTERY, opq(alpha * 0.9));
        }
      }
    }

    /* Les auricules. */
    for (let side = 0; side < 2; side++) {
      const sg = side === 0 ? 1 : -1, v = AV + 0.10;
      const yy = hy + (v - 0.46) * H;
      const fOff = hf + lean * (v - 0.46) * H, lOff = hl + leanL * (v - 0.46) * H;
      for (let k = 0; k < 8; k++) {
        const u = (k + 0.5) / 8, ang = sg * (0.42 + 1.30 * u),
              rad = (0.50 + 0.14 * Math.sin(u * PI)) * s;
        box(fOff + Math.cos(ang) * rad * 0.92, lOff + Math.sin(ang) * rad * 0.80,
            yy + 0.07 * s - u * u * 0.16 * s,
            0.058 * s, 0.052 * s, 0.058 * s, k % 2 === 0 ? GORE : ARTERY, opq(alpha * 0.92));
      }
    }

    /* Les gros vaisseaux. */
    const baseY = hy + (1 - 0.46) * H;
    const baseF = hf + lean * (1 - 0.46) * H, baseL = hl + leanL * (1 - 0.46) * H;

    /* L'aorte : crosse couchee dans le plan de la liaison, sinon on la voit par la tranche. */
    for (let i = 0; i < 20; i++) {
      const u = (i + 0.5) / 20;
      let pf, py;
      if (u < 0.30) { const k = u / 0.30; pf = 0.12 - 0.02 * k; py = -0.08 + k * 0.34; }
      else if (u < 0.76) {
        const k = (u - 0.30) / 0.46, ang = k * PI;
        pf = 0.10 - (1 - Math.cos(ang)) * 0.33;
        py = 0.26 + Math.sin(ang) * 0.24;
      } else { const k = (u - 0.76) / 0.24; pf = -0.56; py = 0.26 - k * 0.16; }
      box(baseF + pf * s, baseL - 0.06 * s, baseY + py * s,
          (0.086 - 0.030 * u) * s, (0.086 - 0.030 * u) * s, (0.086 - 0.030 * u) * s,
          u < 0.45 ? FRESH : ARTERY, opq(alpha * 0.96));
    }
    for (let b = 0; b < 2; b++) {
      const ff = -0.11 - b * 0.17;
      for (let k = 0; k < 2; k++) {
        box(baseF + (ff - (b === 0 ? 0.06 : -0.02) * k) * s, baseL - 0.06 * s,
            baseY + (0.50 + k * 0.075) * s,
            0.044 * s, 0.048 * s, 0.044 * s, FRESH, opq(alpha * 0.92));
      }
    }

    for (let i = 0; i < 11; i++) {
      const u = (i + 0.5) / 11;
      box(baseF + (0.02 + 0.06 * u) * s, baseL + (0.20 - 0.03 * u) * s,
          baseY + (-0.10 + u * 0.42) * s,
          0.078 * s, 0.078 * s, 0.078 * s, GORE, opq(alpha * 0.95));
    }
    for (let side = 0; side < 2; side++) {
      const sg = side === 0 ? 1 : -1;
      for (let k = 0; k < 3; k++) {
        const u = (k + 0.5) / 3;
        box(baseF + (0.08 + sg * u * 0.30) * s, baseL + (0.17 - 0.02 * u) * s,
            baseY + (0.30 + 0.05 * u - u * u * 0.07) * s,
            0.060 * s, 0.060 * s, 0.060 * s, VENOUS, opq(alpha * 0.92));
      }
    }

    for (let i = 0; i < 8; i++) {
      const u = (i + 0.5) / 8;
      box(baseF + (0.32 + 0.03 * u) * s, baseL - 0.20 * s, baseY + (-0.10 + u * 0.34) * s,
          0.068 * s, 0.068 * s, 0.068 * s, GORE, opq(alpha * 0.92));
    }
    for (let i = 0; i < 5; i++) {
      const u = (i + 0.5) / 5;
      box(baseF + 0.34 * s, baseL - 0.17 * s, baseY - (0.10 + u * 0.24) * s,
          0.064 * s, 0.064 * s, 0.064 * s, VENOUS, opq(alpha * 0.90));
    }
    for (let side = 0; side < 2; side++) {
      const sg = side === 0 ? 1 : -1;
      for (let k = 0; k < 2; k++) {
        box(baseF + sg * 0.19 * s, baseL - (0.26 + k * 0.09) * s, baseY - 0.11 * s,
            0.054 * s, 0.054 * s, 0.054 * s, CLOT, opq(alpha * 0.88));
      }
    }

    /* L'interventriculaire anterieure, dans le sillon, de la base a l'apex. */
    {
      const segs = 16;
      for (let i = 0; i < segs; i++) {
        const v = AV - (i + 0.5) / segs * (AV - 0.06), u = v / AV;
        const w = 0.60 * s * Math.sin(1.5707964 * Math.pow(u, 0.62))
                * (0.62 + 0.38 * Math.sqrt(u))
                * (1 - 0.09 * u * u * u * u)
                * Math.min(1, Math.sqrt(u / 0.10))
                * (1 - contract * (0.26 * Math.sin(u * PI) + 0.10));
        const yy = hy + (v - 0.46) * H;
        const fOff = hf + lean * (v - 0.46) * H, lOff = hl + leanL * (v - 0.46) * H;
        const ang = 0.55 + 0.30 * (1 - u) + Math.sin(u * 4.0) * 0.10, rr = w * 0.96;
        box(fOff + Math.cos(ang) * rr, lOff + Math.sin(ang) * rr * 0.86, yy,
            0.038 * s, step * 0.58, 0.038 * s,
            contract > 0.05 ? PLASMA : SPRAY, opq(alpha));
        if (i % 5 === 2) {
          const ang2 = ang - 0.55;
          box(fOff + Math.cos(ang2) * rr, lOff + Math.sin(ang2) * rr * 0.86, yy - step * 0.5,
              0.032 * s, step * 0.5, 0.032 * s, SPRAY, opq(alpha * 0.85));
        }
      }
    }
  };

  const renderTransfusion = (alpha) => {
    const a = Math.round(alpha * smooth(t / 0.03));
    if (a <= 4) return;
    const gather = smooth(t / 0.12);
    // Le lien casse precipite la resorption.
    const fade = Math.max(smooth((t - 0.80) / 0.12), linkBroken ? 1 : 0);
    // Coeur au MILIEU du segment porteur-victime, 1,60 au-dessus de l'ancre : les nombres
    // exacts du serveur (hx/hz = milieu des deux, hy = oy + 1,6).
    const heartF = -linkLen * 0.5, heartY = 1.60;
    const scale = 1.45 * gather * (1 - fade * 0.55);

    // LUB-DUB. Deux coups rapprochés puis un silence : un battement unique et régulier
    // donne un ballon qu'on gonfle, pas un organe.
    let contract = 0, beatIndex = -1, beatAge = 0;
    for (let b = 0; b < 7; b++) {
      const at = 0.16 + b * 0.098;
      const age = (t - at) / 0.098;
      if (age < 0 || age > 1) continue;
      beatIndex = b; beatAge = age;
      const amp = 0.10 + 0.085 * (b / 6.0);
      const lub = Math.sin(clamp01(age / 0.22) * PI);
      const dub = age > 0.30 ? Math.sin(clamp01((age - 0.30) / 0.24) * PI) * 0.62 : 0;
      contract = amp * Math.max(lub, dub);
    }
    const beat = 1 - contract;

    if (gather < 1) {
      for (let i = 0; i < 20; i++) {
        const h1 = hash(salt + 121, i * 17), h2 = hash(salt + 122, i * 19);
        const u = clamp01(gather * 1.4 - h1 * 0.4);
        if (u <= 0) continue;
        const ang = h2 * TAU, rr = (1.5 - 1.4 * u) * (0.5 + h1);
        box(heartF * u + Math.cos(ang) * rr, Math.sin(ang) * rr,
            GROUND_Y + 0.4 + (heartY - GROUND_Y - 0.4) * u,
            0.030, 0.030, 0.030, ARTERY, opq(a * (1 - u * 0.3)));
      }
    }

    if (scale > 0.03) drawHeart(heartF, 0, heartY, scale * beat, contract, a);

    if (beatIndex >= 0 && beatAge < 0.55) {
      const rr = 0.45 + 2.2 * smooth(beatAge / 0.55);
      const thin = 1 - beatAge / 0.55;
      const pts = 22;
      for (let i = 0; i < pts; i++) {
        const ang = (i + 0.5) / pts * TAU;
        box(heartF + Math.cos(ang) * rr, Math.sin(ang) * rr, heartY,
            0.044 * thin, 0.030 * thin, 0.044 * thin, SPRAY, opq(a * 0.75 * thin));
      }
    }

    // ---- LES VAISSEAUX ---- (en arcs : un vaisseau tendu est un câble)
    for (let v = 0; v < 4; v++) {
      const intake = v < 2;
      const side = (v % 2 === 0) ? 1 : -1;
      const endF = intake ? 0 : -linkLen;
      const endY = intake ? 1.05 : linkEndY;
      // Le pas se mesure sur la LONGUEUR REELLE du trajet : les vaisseaux d'entree ne
      // couvrent que 1,30 bloc en f mais descendent de 1,50, d'ou les pointilles.
      const sagMax = intake ? 0.45 : 0.62;
      const run = Math.sqrt((endF - heartF) ** 2 + (endY - heartY) ** 2) + sagMax * 2.0;
      const segs = Math.max(18, Math.round(run / 0.055));
      for (let i = 0; i < segs; i++) {
        const u = (i + 0.5) / segs;
        const f = heartF + (endF - heartF) * u;
        const sag = Math.sin(u * PI) * (intake ? 0.45 : 0.62);
        const yy = heartY + (endY - heartY) * u - sag;
        const lat = side * (0.22 + 0.42 * Math.sin(u * PI));
        let swell = 0;
        if (beatIndex >= 0) {
          const pos = intake ? (1 - beatAge * 1.5) : beatAge * 1.5;
          const d = Math.abs(u - pos);
          if (d < 0.14) swell = (1 - d / 0.14);
        }
        const r = (0.040 + 0.052 * swell) * (1 - fade * 0.8);
        if (r <= 0.006) continue;
        box(f, lat, yy, Math.abs(endF - heartF) / segs * 0.58, r, r,
            swell > 0.4 ? FRESH : (intake ? VENOUS : ARTERY), opq(a * 0.92));
      }
    }

    if (fade > 0.001) {
      for (let i = 0; i < 24; i++) {
        const h1 = hash(salt + 131, i * 23), h2 = hash(salt + 132, i * 29);
        const age = clamp01((fade - h1 * 0.35) * 1.5);
        if (age <= 0) continue;
        const ang = h2 * TAU, rr = 0.5 + 0.7 * h1;
        const yy = heartY - GRAVITY * 0.10 * age * age;
        if (yy < GROUND_Y + 0.05) continue;
        drop(heartF + Math.cos(ang) * rr - linkLen * 0.28 * age, Math.sin(ang) * rr, yy, 0.028, a);
      }
      pool(heartF, 0, 0.85 * fade * (1 - clamp01((t - 0.94) / 0.06)), a, salt + 141);
    }
  };

  // =======================================================================================
  //  V. CARNAGE BERSERK — 180 ticks
  // =======================================================================================
  const renderCarnage = (alpha) => {
    const a = Math.round(alpha * smooth(t / 0.03));
    if (a <= 4) return;
    const wind = clamp01(t / 0.14);
    const apex = clamp01((t - 0.62) / 0.10);
    const settle = smooth((t - 0.90) / 0.10);

    // ---- 1. LA MONTÉE ---- (sans elle, la première taillade arrive sans qu'on sache où regarder)
    if (t < 0.20) {
      const when = [0.015, 0.065, 0.105];
      for (let k = 0; k < 3; k++) {
        const age = (t - when[k]) / 0.055;
        if (age <= 0 || age >= 1) continue;
        const rr = 0.4 + (1.1 + 0.5 * k) * smooth(age);
        const thin = 1 - age;
        const pts = 20 + k * 4;
        for (let i = 0; i < pts; i++) {
          const ang = (i + 0.5) / pts * TAU;
          box(Math.cos(ang) * rr, Math.sin(ang) * rr, GROUND_Y + 0.014,
              0.062 * thin, SLAB_H, 0.062 * thin, k === 2 ? FRESH : ARTERY, opq(a * 0.85 * thin));
        }
      }
      for (let i = 0; i < 9; i++) {
        const h1 = hash(salt + 151, i * 23);
        const ang = h1 * TAU, len = 1.5 * wind * (0.5 + h1);
        const segs = 6;
        for (let k = 0; k < segs; k++) {
          const u = (k + 0.5) / segs;
          box(Math.cos(ang) * len * u, Math.sin(ang) * len * u, GROUND_Y + 0.010,
              0.034 * (1 - u * 0.6), SLAB_H, 0.034 * (1 - u * 0.6), VENOUS, opq(a * 0.75));
        }
      }
    }

    // ---- 2. LA VOLEE DE LAMES ---- (nees en l'air, convergentes et retrecissantes)
    const strikes = 11, flight = 0.14, burst = 0.075;   // 25 ticks : quatre lames en l'air
    const torsoY = GROUND_Y + 0.99;   // 55 % de la hitbox, comme le serveur
    for (let sIdx = 0; sIdx < strikes; sIdx++) {
      const u0 = sIdx / (strikes - 1);   // ecart reel entre impacts : 0,029 -> 0,067
      // « at » = instant de l'IMPACT (colle aux ticks de degat 25 -> 112) ; la lame nait avant.
      const at = 0.14 + 0.48 * (u0 - 0.22 * u0 * (1 - u0) * 2.0);
      const age = (t - (at - flight)) / flight;
      if (age <= 0) continue;
      const h1 = hash(salt + 161, sIdx * 29), h2 = hash(salt + 162, sIdx * 31),
            h3 = hash(salt + 163, sIdx * 37), h4 = hash(salt + 164, sIdx * 41);
      const ang = (h1 + sIdx * 0.3819) * TAU;
      const birthR = 1.7 + 1.6 * h2;
      const birthF = Math.cos(ang) * birthR, birthL = Math.sin(ang) * birthR;
      const birthY = GROUND_Y + 3.5 + 1.7 * h3;
      const aimF = (h3 - 0.5) * 0.26, aimL = (h4 - 0.5) * 0.26,
            aimY = torsoY + (h2 - 0.5) * 0.22;
      const axF = aimF - birthF, axL = aimL - birthL, axY = aimY - birthY;
      const axLen = Math.sqrt(axF * axF + axL * axL + axY * axY);
      if (axLen < 0.001) continue;
      const uF = axF / axLen, uL = axL / axLen, uY = axY / axLen;
      const nLen = Math.sqrt(uF * uF + uY * uY);
      const nF = nLen > 0.001 ? -uY / nLen : 1, nY = nLen > 0.001 ? uF / nLen : 0;

      if (age < 1) {
        const p = age * age * 0.65 + smooth(age) * 0.35;
        const tipF = birthF + axF * p, tipL = birthL + axL * p, tipY = birthY + axY * p;
        const grow = smooth(clamp01(age / 0.20));
        const shrink = 1 - 0.52 * p;              // elle RETRECIT en convergeant
        const bladeLen = (2.40 + 1.10 * h4) * grow * shrink;   // rapport 1:14
        const wMax = (0.085 + 0.032 * h1) * grow * shrink;
        const segs = Math.max(14, Math.min(30, Math.round(bladeLen * 11)));   // un cube / 9 cm
        for (let i = 0; i < segs; i++) {
          const v = (i + 0.5) / segs;             // 0 = pointe, 1 = talon
          const back = v * bladeLen;
          const pf = tipF - uF * back, pl = tipL - uL * back, py = tipY - uY * back;
          if (py < GROUND_Y + 0.02) continue;
          // Pointe franche, dos presque droit, talon effile (le sinus n'avait pas de plat).
          const w = wMax * Math.pow(clamp01(v / 0.18), 0.70)
                         * (1 - 0.72 * Math.pow(clamp01((v - 0.52) / 0.48), 1.5));
          if (w <= 0.006) continue;
          const hf = w * Math.abs(nF) + 0.024, hy = w * Math.abs(nY) + 0.024;
          const rgb = v < 0.12 ? SPRAY : (v < 0.45 ? FRESH : (v < 0.78 ? ARTERY : GORE));
          box(pf, pl, py, hf, hy, 0.020 + w * 0.35, rgb, opq(a * 0.95));   // lisible de profil
          if (v > 0.08 && v < 0.90) {        // le fil SORT du corps, sinon il est noye
            const edge = w + 0.030;
            box(pf + nF * edge, pl, py + nY * edge, 0.020, 0.020, 0.013, PLASMA, opq(a));
          }
          // ---- 3. L'EGOUTTEMENT ---- (la lame saigne pendant sa course)
          if ((i % 5) === 0 && age > 0.25) {
            const dAge = (age - 0.25) / 0.75;
            const dyy = py - GRAVITY * 0.10 * dAge * dAge;
            if (dyy > GROUND_Y + 0.04) drop(pf, pl, dyy, 0.022, a);
          }
        }
        if (grow < 1) {                    // l'etincelle de forge : on voit la lame SE CREER
          const spark = (1 - grow) * (1 - grow);
          for (let i = 0; i < 7; i++) {
            const g1 = hash(salt + 221, sIdx * 23 + i * 7), g2 = hash(salt + 222, sIdx * 29 + i * 11);
            const rr = 0.10 + 0.42 * g1 * (1 - spark), sa = g2 * TAU;
            const sz = (0.030 + 0.030 * g2) * spark;
            if (sz <= 0.006) continue;
            box(birthF + Math.cos(sa) * rr, birthL + Math.sin(sa) * rr, birthY + (g1 - 0.5) * 0.4,
                sz, sz, sz, i % 3 === 0 ? PLASMA : SPRAY, opq(a * 0.9));
          }
        }
        const trail = 9;
        for (let i = 1; i <= trail; i++) {
          const v = i / (trail + 1), q = p * (1 - v);
          const sz = 0.040 * (1 - v) * (1 - v) * grow;
          if (sz <= 0.006) continue;
          box(birthF + axF * q, birthL + axL * q, birthY + axY * q,
              sz, sz, sz * 0.5, i < 4 ? ARTERY : GORE, opq(a * 0.55));
        }
      } else {
        const imp = (t - at) / burst;
        if (imp >= 1) continue;
        const open = smooth(clamp01(imp / 0.22)), close = 1 - imp;
        const gash = 9;
        for (let i = 0; i < gash; i++) {
          const v = (i + 0.5) / gash - 0.5;
          const ext = 0.92 * open * (1 - Math.abs(v) * 0.7);
          const gw = (0.052 + 0.030 * Math.cos(v * PI)) * close;
          if (gw <= 0.006) continue;
          box(aimF + nF * v * ext, aimL, aimY + nY * v * ext,
              gw, gw, gw * 0.8, i === (gash >> 1) ? SPRAY : ARTERY, opq(a));
        }
        for (let i = 0; i < 12; i++) {
          const g1 = hash(salt + 211, sIdx * 17 + i * 13), g2 = hash(salt + 212, sIdx * 19 + i * 11);
          const sp = (0.9 + 1.5 * g1) * imp, sa = g2 * TAU;
          const ef = aimF - uF * sp * 0.55 + Math.cos(sa) * sp * 0.42;
          const el = aimL - uL * sp * 0.55 + Math.sin(sa) * sp * 0.42;
          const ey = aimY - uY * sp * 0.55 + sp * 0.25 - GRAVITY * 0.22 * imp * imp;
          if (ey < GROUND_Y + 0.03) continue;
          const sz = (0.040 + 0.022 * g1) * close;
          if (sz <= 0.006) continue;
          box(ef, el, ey, sz, sz, sz, g1 > 0.6 ? PLASMA : FRESH, opq(a * 0.9));
        }
      }
    }

    // ---- 4. L'APOGÉE ----
    if (apex > 0.001 && apex < 1) {
      const rr = 0.5 + 4.4 * smooth(apex);
      const thin = 1 - apex * 0.8;
      const pts = 46;
      for (let i = 0; i < pts; i++) {
        const ang = (i + 0.5) / pts * TAU;
        const wob = 1 + 0.11 * Math.sin(ang * 5.0 + salt * 0.013);
        box(Math.cos(ang) * rr * wob, Math.sin(ang) * rr * wob, GROUND_Y + 0.016,
            0.090 * thin, SLAB_H, 0.090 * thin, FRESH, opq(a));
        box(Math.cos(ang) * (rr - 0.22) * wob, Math.sin(ang) * (rr - 0.22) * wob,
            GROUND_Y + 0.05 * thin, 0.055 * thin, 0.038 * thin, 0.055 * thin, ARTERY, opq(a * 0.9));
      }
    }

    // ---- 5. LA BRUME ---- (le sol de la scène, jamais un personnage)
    const mist = clamp01((t - 0.30) / 0.30) * (1 - settle);
    if (mist > 0.01) {
      for (let i = 0; i < 34; i++) {
        const h1 = hash(salt + 171, i * 19), h2 = hash(salt + 172, i * 23), h3 = hash(salt + 173, i * 29);
        const ang = h1 * TAU;
        const rr = (0.5 + 3.6 * h2) * Math.min(1, mist * 1.4);
        const rise = (t - 0.30) * (0.35 + 0.45 * h3);
        const yy = GROUND_Y + 0.10 + rise;
        const sz = (0.055 + 0.055 * h3) * mist;
        if (sz <= 0.006) continue;
        box(Math.cos(ang) * rr, Math.sin(ang) * rr, yy, sz, sz * 0.62, sz,
            i % 4 === 0 ? GORE : VENOUS, opq(a * 0.70));
      }
    }

    // ---- 6. LE SOL TREMPÉ ----
    const soak = smooth(clamp01((t - 0.18) / 0.50)) * (1 - settle * 0.8);
    if (soak > 0.02) {
      pool(0, 0, 2.35 * soak, a, salt + 181);
      for (let i = 0; i < 13; i++) {
        const h1 = hash(salt + 191, i * 23), h2 = hash(salt + 192, i * 29);
        const ang = h1 * TAU, rr = 2.4 + 2.0 * h2;
        const r2 = (0.16 + 0.22 * h1) * soak;
        if (r2 <= 0.02) continue;
        pool(Math.cos(ang) * rr, Math.sin(ang) * rr, r2, a, salt + 201 + i);
      }
    }
  };

  const ALPHA = 255;
  switch (variant) {
    case 1: renderEdge(ALPHA); break;
    case 2: renderSiphon(ALPHA); break;
    case 3: renderTransfusion(ALPHA); break;
    case 4: renderCarnage(ALPHA); break;
    default: renderGuillotine(ALPHA); break;
  }
  return out;
}

if (typeof module !== 'undefined' && module.exports) module.exports = { buildBlood };
