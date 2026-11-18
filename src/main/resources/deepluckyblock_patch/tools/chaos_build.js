/* ===========================================================================================
 * chaos_build.js — MIROIR JS de deepluckyblock.client.ChaosSignatureVfx
 *                  + de deepluckyblock.client.VoidPixelManager
 *
 * Même rôle que void_build.js, blood_build.js et les autres : rejouer la géométrie Java dans
 * un navigateur pour la relire sans lancer Minecraft. Toute divergence avec le .java est un
 * BOGUE du miroir.
 *
 * Repère : le Java travaille en (f = axe attaquant→cible, lateral = perpendiculaire, y =
 * hauteur) puis projette avec dx/dz. L'aperçu fixe dx = 1, dz = 0, l'origine à l'impact, donc
 *      x = f        y = y        z = lateral
 * et l'orientation vaut ZÉRO partout : ChaosSignatureVfx n'emploie que emitVoxelCube.
 *
 * ------------------------------------------------------------------------------------------
 * CE QUE CE MIROIR FAIT EN PLUS DES AUTRES : IL SIMULE LES PIXELS 3D
 * ------------------------------------------------------------------------------------------
 * Les trois signatures sèment des cubes qui ne vivent PAS dans la trame courante : ils
 * naissent, dérivent, tombent et meurent sur plusieurs dizaines de ticks. Un aperçu qui
 * n'appellerait buildChaos(t) que pour l'instant t ne les verrait jamais — or ce sont eux qui
 * portent la couleur de la famille.
 *
 * La parade est le REJEU : à chaque image, on repart du tick 0 et on rejoue toutes les
 * naissances jusqu'au tick courant, en intégrant la même physique que VoidPixelManager
 * (frottement 0,94 PUIS gravité, dans cet ordre). C'est déterministe — le Java tire ses
 * naissances d'un modulo sur (salt, indices, tick), jamais d'un aléatoire — donc l'aperçu
 * montre exactement l'état que le jeu aurait au même tick. Coût mesuré : 200 ticks × ~50
 * sites de naissance, soit moins d'une milliseconde par image.
 *
 * Les pixels ressortent avec trois champs supplémentaires que les autres voxels n'ont pas :
 *      pixel : true        marque le cube comme Pixel 3D
 *      pmode : 0|1|2       MODE_RAINBOW / MODE_TINTED / MODE_MONO
 *      phase : 0..1        phase spatiale, calculée comme côté CPU dans le Java
 * La page s'en sert pour tracer le liseré d'arête arc-en-ciel que le fragment shader déduit
 * des UV en jeu. Ici il n'y a pas de shader : on trace le contour des faces.
 * =========================================================================================== */

const CHAOS_SHAPE = (function () {
  return {
    /* Le sol. Même valeur que les autres familles : l'origine d'une signature est le TORSE
       de la victime (bbHeight × 0,50 côté serveur), donc le plancher est 0,90 plus bas. */
    GROUND_FALLBACK: -0.90,
    /* Durées réelles, en ticks, telles qu'écrites dans WeaponWorldVfxManager. */
    DURATION: [150, 170, 200],
    /* Hauteurs de centre, par signature. La page y accroche sa caméra. */
    YC: [1.05, 1.15, 1.20],
    /* L'instant de la détonation du Big Bang, en fraction de l'animation. */
    BANG: 0.42,
  };
})();

function buildChaos(variant, t, salt = 4421) {
  const out = [];
  if (t <= 0 || t >= 1) return out;

  // ---- Parité stricte avec les utilitaires du .java ---------------------------------------
  const TAU = 6.2831855;
  const clamp01 = (v) => (v < 0 ? 0 : v > 1 ? 1 : v);
  /* smooth(x, from, to) du Java : rampe lissée entre deux bornes, et NON le smooth(x) à une
     seule variable des autres familles. Se tromper ici décale toute la chronologie. */
  const smooth = (x, from, to) => {
    if (to <= from) return x >= to ? 1 : 0;
    const u = clamp01((x - from) / (to - from));
    return u * u * (3 - 2 * u);
  };
  const SOLID = 200;
  const opq = (v) => { const i = Math.round(v); return i <= 10 ? 0 : (i < SOLID ? SOLID : Math.min(255, i)); };

  /* hash(a, b) : transcription EXACTE de ChaosSignatureVfx.hash. Les multiplications
     doivent rester en 32 bits signés — d'où Math.imul et le |0 — sinon JavaScript passe en
     flottant 53 bits et le miroir tire d'autres nombres que le jeu. Erreur déjà payée. */
  const hash = (a, b) => {
    let h = (Math.imul(a, 374761393) + Math.imul(b, 668265263)) | 0;
    h = Math.imul(h ^ (h >>> 13), 1274126177) | 0;
    return ((h ^ (h >>> 16)) & 0x7FFFFF) / 8388607.0;
  };

  // ---- Palette (ChaosSignatureVfx) --------------------------------------------------------
  const BODY = 0x120616, BODY_LIT = 0x2B0B38, FRAME = 0x5B2A68, GLASS = 0x8E58B4,
        EDGE = 0x9B3FD0, BREAK = 0xFF48C8, SPARK = 0x3FE8FF, CORE = 0xFFFFFF;

  const GROUND_Y = CHAOS_SHAPE.GROUND_FALLBACK;
  const DUR = CHAOS_SHAPE.DURATION[variant] || 150;
  const ALPHA = 255;

  // L'aperçu regarde depuis dx = 1, dz = 0 : la projection du Java devient l'identité.
  const dx = 1, dz = 0;

  const cube = (x, y, z, hx, hy, hz, rgb, a) => {
    if (a <= 0 || hx <= 0 || hy <= 0 || hz <= 0) return;
    out.push({ x: x, y: y, z: z, hx: hx, hy: hy, hz: hz,
               rgb: rgb, a: Math.min(255, a), yaw: 0, pitch: 0, roll: 0 });
  };

  // =========================================================================================
  //  LE POOL DE PIXELS 3D — miroir de VoidPixelManager
  // =========================================================================================
  const pixels = [];
  /* spawn() n'est PAS appelé directement par les figures : il l'est par le rejeu, avec le
     tick de naissance en argument. Les figures, elles, déclarent leurs naissances dans
     seedPixels() — la même condition modulo que le Java, au même endroit. */
  const spawn = (born, x, y, z, vx, vy, vz, size, life, mode, hue, grav) => {
    pixels.push({ born: born, x: x, y: y, z: z, vx: vx, vy: vy, vz: vz,
                  size: size, life: life, mode: mode, hue: hue, grav: grav });
  };

  /* Intégration d'un pixel sur n ticks. Ordre repris mot pour mot du gestionnaire :
     position, PUIS frottement, PUIS gravité. L'inverse amortirait la chute elle-même. */
  const advance = (p, ticks) => {
    let x = p.x, y = p.y, z = p.z, vx = p.vx, vy = p.vy, vz = p.vz;
    for (let k = 0; k < ticks; k++) {
      x += vx; y += vy; z += vz;
      vx *= 0.94; vy *= 0.94; vz *= 0.94;
      vy -= 0.0135 * p.grav;
    }
    return [x, y, z];
  };

  // =========================================================================================
  //  I. BOUCLE KALÉIDOSCOPIQUE
  // =========================================================================================
  function kaleido(tt, emit, seedOnly) {
    const ARMS = 6, YC = 1.05;
    const spin = tt * 3.40;
    let chaosAmount = smooth(tt, 0.46, 0.74);
    const relock = smooth(tt, 0.78, 0.82) * (1 - smooth(tt, 0.86, 0.92));
    chaosAmount *= (1 - relock);
    const reach = 0.55 + 2.35 * smooth(tt, 0.04, 0.42);
    const fade = 1 - smooth(tt, 0.90, 1.0);
    const a = Math.round(ALPHA * fade);
    if (a <= 8) return;
    const tick = Math.floor(tt * 150);

    for (let arm = 0; arm < ARMS; arm++) {
      const base = arm * (TAU / ARMS);
      const stagger = (arm * 0.37) % 1.0;
      const broken = chaosAmount * (0.35 + 0.65 * stagger);
      const ang = base + spin + broken * (1.25 * Math.sin(arm * 2.1 + tt * 9.0));
      const armReach = reach * (1 - broken * 0.22 * (1 + Math.sin(arm * 1.7)));
      const ca = Math.cos(ang), sa = Math.sin(ang);

      const nodes = 12;
      for (let n = 1; n <= nodes; n++) {
        const u = n / nodes;
        const dist = armReach * u;
        const lateral = 0.14 * Math.sin(u * 5.6 + arm * 1.3) * (1 + broken * 2.4);
        const px = (ca * dist - sa * lateral) * dx - (sa * dist + ca * lateral) * dz;
        const pz = (ca * dist - sa * lateral) * dz + (sa * dist + ca * lateral) * dx;
        const py = YC + 0.18 * Math.sin(u * 4.1 + tt * 6.0 + arm);
        const half = 0.072 * (1.12 - 0.55 * u);
        const rgb = n <= 3 ? GLASS : (n <= 8 ? FRAME : BODY_LIT);
        if (!seedOnly) {
          emit(px, py, pz, half, half, half, rgb, opq(a));
          /* LE TRIANGLE : la section de la branche. */
          const wing = half * 1.55 * (1 + broken * 0.8);
          const wx = -sa * wing, wz = ca * wing;
          emit(px + wx * dx - wz * dz, py - half * 0.85, pz + wx * dz + wz * dx,
               half * 0.70, half * 0.70, half * 0.70, rgb, opq(a * 0.90));
          emit(px - wx * dx + wz * dz, py - half * 0.85, pz - wx * dz - wz * dx,
               half * 0.70, half * 0.70, half * 0.70, rgb, opq(a * 0.90));
          /* LE LIANT : le demi-pas entre deux noeuds. */
          if (n > 1) {
            const ub = (n - 0.5) / nodes, db = armReach * ub;
            const lb = 0.14 * Math.sin(ub * 5.6 + arm * 1.3) * (1 + broken * 2.4);
            emit((ca * db - sa * lb) * dx - (sa * db + ca * lb) * dz,
                 YC + 0.18 * Math.sin(ub * 4.1 + tt * 6.0 + arm),
                 (ca * db - sa * lb) * dz + (sa * db + ca * lb) * dx,
                 half * 0.62, half * 0.62, half * 0.62, FRAME, opq(a * 0.86));
          }
          if ((n % 3) === 0) {
            emit(px, py + half * 1.55, pz, half * 0.38, half * 0.38, half * 0.38, EDGE, opq(a * 0.92));
          }
        }
        if (n >= 4 && ((salt + arm * 7 + n * 13 + tick) % 9) === 0) {
          const spread = 0.012 + 0.022 * broken;
          spawn(tick, px, py, pz,
                ca * spread * dx - sa * spread * dz,
                0.004 + 0.010 * Math.sin(arm + n),
                ca * spread * dz + sa * spread * dx,
                0.075 + 0.045 * u, 14 + (n & 3) * 4, 0, 0.0, 0.12);
        }
      }

      if (!seedOnly && GROUND_Y > -9.0) {
        for (let n = 2; n <= 9; n++) {
          const u = n / 9.0, dist = armReach * u * 0.62;
          const px = ca * dist * dx - sa * dist * dz;
          const pz = ca * dist * dz + sa * dist * dx;
          const half = 0.055 * (1 - 0.4 * u);
          emit(px, GROUND_Y + 0.04, pz, half, half * 0.35, half, BODY_LIT, opq(a * 0.55));
        }
      }
      if (!seedOnly && broken > 0.05) {
        const dist = armReach * 0.58;
        const px = ca * dist * dx - sa * dist * dz;
        const pz = ca * dist * dz + sa * dist * dx;
        const h = 0.05 + 0.07 * broken;
        emit(px, YC, pz, h, h, h, BREAK, opq(a * (0.45 + 0.55 * broken)));
      }
    }

    /* LA MANDALA : trois anneaux concentriques qui perdent leurs cubes dans un ordre fixe. */
    if (!seedOnly) {
      for (let ring = 0; ring < 3; ring++) {
        const rr = reach * (0.26 + ring * 0.29);
        const ringSpin = spin * (ring % 2 === 0 ? 1.0 : -0.75);
        for (let k = 0; k < 18; k++) {
          if (hash(salt + ring * 77, k * 23) < chaosAmount * 0.85) continue;
          const ang2 = k / 18 * TAU + ringSpin;
          const rx = Math.cos(ang2) * rr, rz2 = Math.sin(ang2) * rr;
          const h = 0.052 - ring * 0.008;
          emit(rx * dx - rz2 * dz, YC + 0.07 * Math.sin(ang2 * 3.0 + tt * 5.0), rx * dz + rz2 * dx,
               h, h, h, (k % 6 === 0) ? EDGE : (ring === 1 ? GLASS : FRAME),
               opq(a * (0.92 - ring * 0.08)));
        }
      }
      /* LES PANNEAUX DE MIROIR : ils s'effacent quand la symetrie casse. */
      const paneAlpha = 1 - chaosAmount;
      if (paneAlpha > 0.05) {
        for (let arm = 0; arm < ARMS; arm++) {
          const mid = (arm + 0.5) * (TAU / ARMS) + spin;
          const cm = Math.cos(mid), sm = Math.sin(mid);
          for (let k = 1; k <= 7; k++) {
            const u = k / 7.0, dist = reach * u * 0.86, h = 0.040 + 0.016 * u;
            emit(cm * dist * dx - sm * dist * dz, YC + 0.22 * Math.sin(u * Math.PI),
                 cm * dist * dz + sm * dist * dx,
                 h, h * 2.1, h, FRAME, opq(a * paneAlpha * 0.78));
          }
        }
      }
      /* LE TUBE : la profondeur verticale de la figure. */
      for (let k = 0; k < 5; k++) {
        const hy = (k - 2) * 0.22, r = 0.13 + 0.05 * Math.abs(hy), ang2 = -spin * 2.2 + k * 1.26;
        emit(Math.cos(ang2) * r * dx - Math.sin(ang2) * r * dz, YC + hy,
             Math.cos(ang2) * r * dz + Math.sin(ang2) * r * dx,
             0.048, 0.048, 0.048, k === 2 ? SPARK : FRAME, opq(a * 0.88));
      }
    }

    const hubSpin = -spin * 1.7;
    if (!seedOnly) {
      for (let k = 0; k < 6; k++) {
        const ang = hubSpin + k * (TAU / 6.0);
        const r = 0.30 + 0.05 * Math.sin(tt * 12.0 + k);
        emit(Math.cos(ang) * r * dx - Math.sin(ang) * r * dz, YC,
             Math.cos(ang) * r * dz + Math.sin(ang) * r * dx,
             0.075, 0.075, 0.075, k % 2 === 0 ? EDGE : SPARK, opq(a));
      }
    }
    if (((salt + tick) & 3) === 0) {
      spawn(tick, 0, YC, 0,
            (hash(salt, 11) - 0.5) * 0.055, (hash(salt, 23) - 0.3) * 0.045,
            (hash(salt, 37) - 0.5) * 0.055, 0.10, 18, 1, 0.52, 0.0);
    }
  }

  // =========================================================================================
  //  II. SPHÈRE D'ENTROPIE
  // =========================================================================================
  function entropySphere(tt, emit, seedOnly) {
    const YC = 1.15, SEGMENTS = 34;
    const radius = 0.35 + 1.52 * smooth(tt, 0.02, 0.30);
    const entropy = clamp01((tt - 0.16) / 0.70);
    const fade = 1 - smooth(tt, 0.88, 1.0);
    const a = Math.round(ALPHA * fade);
    if (a <= 8) return;
    const spin = tt * 1.95;
    const tick = Math.floor(tt * 170);

    for (let ring = 0; ring < 3; ring++) {
      const ringSpin = spin * (1 + ring * 0.33);
      for (let s = 0; s < SEGMENTS; s++) {
        const u = s / SEGMENTS, ang = u * TAU + ringSpin;
        const fallen = entropy > hash(salt + ring * 131, s * 17);
        const ca = Math.cos(ang), sa = Math.sin(ang);
        let lx, ly, lz;
        if (ring === 0) { lx = ca * radius; ly = sa * radius; lz = 0; }
        else if (ring === 1) { lx = 0; ly = ca * radius; lz = sa * radius; }
        else { lx = ca * radius; ly = 0; lz = sa * radius; }
        const px = lx * dx - lz * dz, pz = lx * dz + lz * dx, py = YC + ly;
        if (!fallen) {
          if (!seedOnly) {
            emit(px, py, pz, 0.072, 0.072, 0.072,
                 (s % 5 === 0) ? EDGE : (ring === 0 ? GLASS : FRAME), opq(a));
          }
        } else if (((salt + s * 31 + ring * 7 + tick) % 23) === 0) {
          spawn(tick, px, py, pz,
                (hash(s, ring * 3 + 1) - 0.5) * 0.030,
                (hash(s, ring * 3 + 2) - 0.5) * 0.024,
                (hash(s, ring * 3 + 3) - 0.5) * 0.030,
                0.085, 26 + (s & 7) * 3, 0, 0.0, 0.30);
        }
      }
    }

    /* LES MERIDIENS : six demi-cercles verticaux, meme loi d'erosion. */
    if (!seedOnly) {
      for (let m = 0; m < 6; m++) {
        const lon = m / 6.0 * Math.PI + spin * 0.45;
        const cl = Math.cos(lon), sl2 = Math.sin(lon);
        for (let k = 1; k < 16; k++) {
          if (entropy > hash(salt + 313 + m * 59, k * 41)) continue;
          const lat = k / 16.0 * Math.PI - Math.PI * 0.5;
          const cr = Math.cos(lat) * radius * 0.97;
          const lx = cl * cr, lz = sl2 * cr, ly = Math.sin(lat) * radius * 0.97;
          emit(lx * dx - lz * dz, YC + ly, lx * dz + lz * dx,
               0.045, 0.045, 0.045, (k % 4 === 0) ? EDGE : FRAME, opq(a * 0.80));
        }
      }
      /* LA COQUE INTERNE : la sphere garde son volume de profil. */
      for (let ring = 0; ring < 3; ring++) {
        const rr = radius * 0.52, ringSpin = -spin * (1 + ring * 0.21);
        for (let s2 = 0; s2 < 22; s2++) {
          if (entropy * 1.25 > hash(salt + 517 + ring * 97, s2 * 13)) continue;
          const ang = s2 / 22 * TAU + ringSpin, ca2 = Math.cos(ang), sa2 = Math.sin(ang);
          let lx, ly, lz;
          if (ring === 0) { lx = ca2 * rr; ly = sa2 * rr; lz = 0; }
          else if (ring === 1) { lx = 0; ly = ca2 * rr; lz = sa2 * rr; }
          else { lx = ca2 * rr; ly = 0; lz = sa2 * rr; }
          emit(lx * dx - lz * dz, YC + ly, lx * dz + lz * dx,
               0.050, 0.050, 0.050, (s2 % 7 === 0) ? SPARK : GLASS, opq(a * 0.84));
        }
      }
    }

    const coreR = 0.12 + 0.34 * entropy;
    if (!seedOnly) {
      /* Les douze aretes de l'octaedre. */
      for (let e = 0; e < 12; e++) {
        const pa = e % 3, pb = Math.floor(e / 3) % 2, pc = Math.floor(e / 6);
        const ax = (pa === 0 ? (pb === 0 ? coreR : -coreR) : 0);
        const ay = (pa === 1 ? (pb === 0 ? coreR : -coreR) : 0);
        const az = (pa === 2 ? (pb === 0 ? coreR : -coreR) : 0);
        const bx = (pa === 0 ? 0 : (pc === 0 ? coreR : -coreR));
        const by = (pa === 1 ? 0 : (pa === 0 ? (pc === 0 ? coreR : -coreR) : 0));
        const bz = (pa === 2 ? 0 : (pa === 1 ? (pc === 0 ? coreR : -coreR) : 0));
        for (let k = 1; k <= 3; k++) {
          const u = k / 4.0;
          const ox = ax + (bx - ax) * u, oy = ay + (by - ay) * u, oz = az + (bz - az) * u;
          emit(ox * dx - oz * dz, YC + oy, ox * dz + oz * dx,
               0.030, 0.030, 0.030, EDGE, opq(a * 0.78));
        }
      }
      for (let axis = 0; axis < 6; axis++) {
        const ox = (axis === 0 ? coreR : axis === 1 ? -coreR : 0);
        const oy = (axis === 2 ? coreR : axis === 3 ? -coreR : 0);
        const oz = (axis === 4 ? coreR : axis === 5 ? -coreR : 0);
        const h = 0.055 + 0.045 * entropy;
        emit(ox * dx - oz * dz, YC + oy, ox * dz + oz * dx, h, h, h, BREAK, opq(a));
      }
    }
    if (((salt + tick) % 5) === 0) {
      spawn(tick, 0, YC, 0,
            (hash(salt, 51) - 0.5) * 0.018, (hash(salt, 52) - 0.5) * 0.018,
            (hash(salt, 53) - 0.5) * 0.018, 0.12, 22, 2, 0.0, 0.0);
    }

    if (!seedOnly && GROUND_Y > -9.0) {
      for (let band = 0; band < 2; band++) {
        const marks = band === 0 ? 28 : 20, r = radius * (band === 0 ? 1.08 : 0.64);
        for (let m = 0; m < marks; m++) {
          if (hash(salt + 907 + band * 211, m * 29) < entropy) continue;
          const ang = m / marks * TAU - spin * (band === 0 ? 0.5 : -0.8);
          emit(Math.cos(ang) * r * dx - Math.sin(ang) * r * dz, GROUND_Y + 0.03,
               Math.cos(ang) * r * dz + Math.sin(ang) * r * dx,
               0.070, 0.022, 0.070, band === 0 ? GLASS : FRAME,
               opq(a * (band === 0 ? 0.70 : 0.58)));
        }
      }
    }
  }

  // =========================================================================================
  //  III. CICATRICE DU BIG BANG
  // =========================================================================================
  function bigBang(tt, emit, seedOnly) {
    const YC = 1.20, BANG = CHAOS_SHAPE.BANG;
    const fade = 1 - smooth(tt, 0.90, 1.0);
    const a = Math.round(ALPHA * fade);
    if (a <= 8) return;
    const tick = Math.floor(tt * 200);

    if (tt < BANG) {
      const u = tt / BANG, pull = u * u;
      for (let k = 0; k < 40; k++) {
        const r0 = 1.4 + 2.6 * hash(salt + 11, k * 13);
        const r = r0 * (1 - pull * 0.94);
        const ang = hash(salt + 29, k * 7) * TAU + pull * 6.5;
        const h0 = (hash(salt + 43, k * 19) - 0.5) * 2.4;
        const px = Math.cos(ang) * r * dx - Math.sin(ang) * r * dz;
        const pz = Math.cos(ang) * r * dz + Math.sin(ang) * r * dx;
        const py = YC + h0 * (1 - pull * 0.90);
        const half = 0.055 + 0.040 * (1 - pull);
        if (!seedOnly) {
          emit(px, py, pz, half, half, half, k % 4 === 0 ? GLASS : FRAME, opq(a));
          /* LA COMETE : trois cubes sur le chemin deja parcouru. */
          for (let q = 1; q <= 3; q++) {
            const back = 1 + q * 0.075, rq = r * back, aq = ang - q * 0.085;
            const th = half * (0.72 - q * 0.14);
            emit(Math.cos(aq) * rq * dx - Math.sin(aq) * rq * dz,
                 YC + h0 * (1 - pull * 0.90) * back,
                 Math.cos(aq) * rq * dz + Math.sin(aq) * rq * dx,
                 th, th, th, BODY_LIT, opq(a * (0.80 - q * 0.16)));
          }
        }
        if (((salt + k * 5 + tick) % 7) === 0) {
          spawn(tick, px, py, pz, 0, 0, 0, 0.070, 12, 0, 0.0, 0.0);
        }
      }
      if (!seedOnly) {
        const coreH = 0.05 + 0.13 * pull;
        emit(0, YC, 0, coreH, coreH, coreH, pull > 0.72 ? CORE : BREAK, opq(a));
        /* LA CAGE DE CONFINEMENT : douze cubes qui se referment avec le coeur. */
        const cageR = 0.95 - 0.78 * pull;
        for (let k = 0; k < 12; k++) {
          const ang = k / 12 * TAU + pull * 4.0, tier = ((k % 3) - 1) * 0.55;
          const rr = cageR * (1 - 0.35 * Math.abs(tier));
          emit(Math.cos(ang) * rr * dx - Math.sin(ang) * rr * dz, YC + tier * cageR,
               Math.cos(ang) * rr * dz + Math.sin(ang) * rr * dx,
               0.038, 0.038, 0.038, (k % 3 === 0) ? SPARK : EDGE,
               opq(a * (0.55 + 0.45 * pull)));
        }
      }
      return;
    }

    const e = (tt - BANG) / (1 - BANG);
    const wave = Math.sqrt(e);

    if (!seedOnly) {
      const coreH = 0.18 * (1 - smooth(e, 0.0, 0.22));
      if (coreH > 0.004) emit(0, YC, 0, coreH, coreH, coreH, CORE, opq(a));
    }

    const RAYS = 16;
    for (let k = 0; k < RAYS; k++) {
      const phi = (k + 0.5) / RAYS;
      const cosTheta = 1 - 2 * phi;
      const sinTheta = Math.sqrt(Math.max(0, 1 - cosTheta * cosTheta));
      const golden = k * 2.39996 + salt * 0.017;
      const ux = sinTheta * Math.cos(golden), uy = cosTheta, uz = sinTheta * Math.sin(golden);
      const reach = 4.6 * wave, steps = 12;
      if (!seedOnly) {
        for (let n = 1; n <= steps; n++) {
          const f = n / steps;
          if (f > wave * 1.15) break;
          const dist = reach * f;
          const half = 0.095 * (1 - 0.62 * f);
          const px2 = ux * dist * dx - uz * dist * dz, pz2 = ux * dist * dz + uz * dist * dx;
          emit(px2, YC + uy * dist, pz2,
               half, half, half, n <= 3 ? CORE : (n <= 7 ? BREAK : GLASS), opq(a * (1 - 0.35 * f)));
          /* LES BARBES : le rayon s'evase au lieu de filer. */
          if ((n % 3) === 0) {
            const bw = half * (1.8 + 2.4 * f);
            emit(px2 - uz * bw * dx - ux * bw * dz, YC + uy * dist, pz2 - uz * bw * dz + ux * bw * dx,
                 half * 0.55, half * 0.55, half * 0.55, EDGE, opq(a * (0.72 - 0.30 * f)));
            emit(px2 + uz * bw * dx + ux * bw * dz, YC + uy * dist, pz2 + uz * bw * dz - ux * bw * dx,
                 half * 0.55, half * 0.55, half * 0.55, EDGE, opq(a * (0.72 - 0.30 * f)));
          }
        }
      }
      if (((salt + k * 17 + tick) % 4) === 0) {
        const dist = 4.6 * wave;
        spawn(tick, ux * dist * dx - uz * dist * dz, YC + uy * dist, ux * dist * dz + uz * dist * dx,
              ux * 0.055, uy * 0.055, uz * 0.055, 0.095, 20, 0, 0.0, 0.22);
      }
    }

    if (!seedOnly) {
      const ringR = 5.4 * wave * 1.18;
      if (ringR > 0.4) {
        const ringCubes = 48, thin = 1 - smooth(e, 0.62, 1.0);
        for (let k = 0; k < ringCubes; k++) {
          const ang = k / ringCubes * TAU;
          const px = Math.cos(ang) * ringR * dx - Math.sin(ang) * ringR * dz;
          const pz = Math.cos(ang) * ringR * dz + Math.sin(ang) * ringR * dx;
          const h = (0.16 + 0.06 * Math.sin(k * 1.7)) * thin;
          if (h < 0.01) continue;
          emit(px, YC, pz, h, h * 1.45, h, BODY, opq(a));
          if ((k & 1) === 0) {
            emit(px, YC + h * 1.7, pz, h * 0.46, h * 0.46, h * 0.46,
                 (k & 3) === 0 ? SPARK : EDGE, opq(a * 0.92));
          }
          /* LA COURONNE TRAINANTE : l'epaisseur du tore. */
          const lagR = ringR * 0.88;
          emit(Math.cos(ang) * lagR * dx - Math.sin(ang) * lagR * dz, YC - h * 0.75,
               Math.cos(ang) * lagR * dz + Math.sin(ang) * lagR * dx,
               h * 0.70, h * 0.95, h * 0.70, BODY, opq(a * 0.72));
        }
      }
      if (GROUND_Y > -9.0) {
        const scar = 34;
        for (let k = 0; k < scar; k++) {
          const u = k / (scar - 1);
          const along = (u - 0.5) * 2.0 * 3.2 * clamp01(wave * 1.4);
          const jitter = (hash(salt + 613, k * 11) - 0.5) * 0.55;
          const h = 0.095 * (1 - 0.55 * Math.abs(u - 0.5) * 2.0);
          emit(along * dx - jitter * dz, GROUND_Y + 0.035, along * dz + jitter * dx,
               h, 0.026, h * 0.72, k % 3 === 0 ? EDGE : FRAME, opq(a * 0.88));
        }
      }
    }
  }

  const FIGURE = [kaleido, entropySphere, bigBang][variant] || kaleido;

  // ---- 1. LE REJEU DES NAISSANCES ---------------------------------------------------------
  /* On repasse sur tous les ticks écoulés en mode « graines seules » : aucune géométrie
     n'est produite, seules les naissances sont enregistrées. Le tick courant est exclu, il
     sera traité par la passe de rendu juste en dessous. */
  const nowTick = Math.floor(t * DUR);
  for (let k = 0; k < nowTick; k++) {
    FIGURE((k + 0.5) / DUR, null, true);
  }

  // ---- 2. LA TRAME COURANTE ---------------------------------------------------------------
  FIGURE(t, cube, false);

  // ---- 3. LES PIXELS 3D VIVANTS -----------------------------------------------------------
  /* Pour chaque pixel né avant maintenant, on intègre sa physique et on applique les deux
     courbes du gestionnaire : croissance sur les 10 premiers pour cent, fondu après 72 %. */
  for (const p of pixels) {
    const age = nowTick - p.born;
    if (age < 0 || age >= p.life) continue;
    const life = age / p.life;
    const fade = life < 0.72 ? 1.0 : 1.0 - (life - 0.72) / 0.28;
    const grow = life < 0.10 ? life / 0.10 : 1.0;
    const half = p.size * 0.5 * grow;
    if (half < 0.002) continue;
    const q = advance(p, age);
    /* La phase spatiale, calculée exactement comme dans VoidPixelManager : une combinaison
       linéaire des coordonnées MONDE. L'aperçu n'a pas de caméra à ajouter, donc ce sont
       directement les coordonnées locales — la couleur glisse donc dans l'espace de la même
       façon, c'est tout ce que l'on veut montrer. */
    let raw = (q[0] * 0.17 + q[1] * 0.23 + q[2] * 0.13) % 1.0;
    if (raw < 0) raw += 1.0;
    out.push({ x: q[0], y: q[1], z: q[2], hx: half, hy: half, hz: half,
               /* Le cœur anthracite #23232A du shader, à l'octet près. */
               rgb: 0x23232A, a: Math.round(166 * fade), yaw: 0, pitch: 0, roll: 0,
               pixel: true, pmode: p.mode, phue: p.hue, phase: raw, pfade: fade });
  }

  return out;
}

if (typeof module !== 'undefined' && module.exports) {
  module.exports = { buildChaos, CHAOS_SHAPE };
}
