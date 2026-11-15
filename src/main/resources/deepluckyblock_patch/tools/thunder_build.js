/* ===========================================================================================
 * thunder_build.js — MIROIR JS de deepluckyblock.client.ThunderSignatureVfx
 *
 * Même rôle que water_build.js pour l'eau : rejouer la géométrie Java dans un navigateur pour
 * la relire sans lancer Minecraft. Toute divergence avec le .java est un BOGUE du miroir.
 *
 * Repère : le Java travaille en (f = axe attaquant→cible, lateral = perpendiculaire, y = hauteur)
 * puis projette avec dx/dz. L'aperçu fixe dx = 1, dz = 0, l'origine à l'impact, donc :
 *        x = f          y = y          z = lateral
 *
 * Rotations : convention de emitVoxelBoxRotated — roll (autour de Z, élévation de l'axe X),
 * puis pitch (autour de X, vrille), puis yaw (autour de Y, cap). L'axe X local vaut bien
 * (cos roll·cos yaw, sin roll, −cos roll·sin yaw).
 *
 * ATTENTION — ce que l'aperçu ne modélise PAS : le tamis du moteur (pixelDensity < 0.999 &&
 * alpha < 200 décime les cubes), cubeVeil, ni le plafond de 7000 cubes par frame. Un rendu
 * correct ici ne prouve donc RIEN en jeu ; il sert à relire la chorégraphie, pas à la valider.
 * C'est précisément pour cela que opq() existe : il reproduit le plancher d'opacité du Java.
 * =========================================================================================== */

function buildThunder(variant, t, salt = 7331) {
  const out = [];
  if (t <= 0 || t >= 1) return out;

  // ---- Parité stricte avec les utilitaires du .java -------------------------------------
  const TAU = 6.2831855;
  const clamp01 = (v) => (v < 0 ? 0 : v > 1 ? 1 : v);
  const smooth = (x) => { x = clamp01(x); return x * x * (3 - 2 * x); };
  const fract = (x) => x - Math.floor(x);
  const SOLID_FLOOR = 208;
  // opaque() : on ne fait JAMAIS disparaître un éclair par fondu d'alpha — il serait décimé en
  // pointillés par le tamis. Soit le cube passe au-dessus du seuil, soit il n'est pas émis.
  const opq = (v) => { const i = Math.round(v); return i <= 10 ? 0 : (i < SOLID_FLOOR ? SOLID_FLOOR : Math.min(255, i)); };
  // hash() : arithmétique 32 bits identique au Java (Math.imul + décalage non signé).
  const hash = (seed, n) => {
    let h = (seed ^ Math.imul(n, 0x45D9F3B)) | 0;
    h ^= h >>> 16; h = Math.imul(h, 0x45D9F3B); h ^= h >>> 16;
    return (h & 0xFFFF) / 65535;
  };
  const mix = (a, b, u) => {
    const k = clamp01(u);
    const r = (((a >> 16) & 255) * (1 - k) + ((b >> 16) & 255) * k) | 0;
    const g = (((a >> 8) & 255) * (1 - k) + ((b >> 8) & 255) * k) | 0;
    const bl = ((a & 255) * (1 - k) + (b & 255) * k) | 0;
    return (r << 16) | (g << 8) | bl;
  };

  // ---- Palette (DeepElement.THUNDER) -----------------------------------------------------
  const DEEP = 0x1E26FF, VIOLET = 0x5E3AFF, ARC = 0x8E6BFF, NEON = 0xC9B6FF,
        HOT = 0xFCFFBA, CORE = 0xFFFFFF, CYAN = 0x2BE8FF, MAGENTA = 0xFF3BD4,
        PCB = 0x1BE08A, GLASS = 0x7C68A0, MOLTEN = 0xFFB347, SCORCH = 0x2A2036;

  const SKY_HEIGHT = 22.0, BOLT_CORE = 0.075, CROWN_RADIUS = 5.10,
        CHAIN_LINKS = 6, CAGE_HALF = 1.32, ECHO_RADIUS = 3.35;

  // ---- Émission --------------------------------------------------------------------------
  // box() : cube aligné. rot() : cube orienté. Les deux refusent un alpha nul ou une demi-arête
  // négative, exactement comme le Java.
  const box = (f, lat, y, hf, hy, hl, rgb, a) => {
    if (a <= 0 || hf <= 0 || hy <= 0 || hl <= 0) return;
    out.push({ x: f, y: y, z: lat, hx: hf, hy: hy, hz: hl, rgb: rgb, a: Math.min(255, a), yaw: 0, pitch: 0, roll: 0 });
  };
  const rot = (f, lat, y, hf, hy, hl, yaw, pitch, roll, rgb, a) => {
    if (a <= 0 || hf <= 0 || hy <= 0 || hl <= 0) return;
    out.push({ x: f, y: y, z: lat, hx: hf, hy: hy, hz: hl, rgb: rgb, a: Math.min(255, a), yaw: yaw, pitch: pitch, roll: roll });
  };

  // seg() : segment orienté entre deux points — miroir exact du seg() Java (dx=1, dz=0).
  const seg = (f0, l0, y0, f1, l1, y1, thick, rgb, a) => {
    if (a <= 0 || thick <= 0.0005) return;
    const x0 = f0, z0 = l0, x1 = f1, z1 = l1;
    const vx = x1 - x0, vy = y1 - y0, vz = z1 - z0;
    const len = Math.sqrt(vx * vx + vy * vy + vz * vz);
    if (len < 0.0008) return;
    const horiz = Math.sqrt(vx * vx + vz * vz);
    const yaw = Math.atan2(-vz, vx);
    const roll = Math.atan2(vy, horiz);
    out.push({ x: (x0 + x1) * 0.5, y: (y0 + y1) * 0.5, z: (z0 + z1) * 0.5,
               hx: len * 0.5, hy: thick, hz: thick, rgb: rgb, a: Math.min(255, a),
               yaw: yaw, pitch: 0, roll: roll });
  };

  // boltOffset() : 5 octaves de bruit haché à interpolation LINÉAIRE. C'est l'interpolation
  // linéaire qui casse le trait ; une interpolation lisse donnerait une ondulation molle.
  const boltOffset = (seed, u, amp) => {
    let sum = 0, gain = amp, cells = 3;
    for (let octave = 0; octave < 5; octave++) {
      const x = u * cells, i = Math.floor(x), frac = x - i;
      const h0 = hash(seed + octave * 977, i) * 2 - 1;
      const h1 = hash(seed + octave * 977, i + 1) * 2 - 1;
      sum += (h0 + (h1 - h0) * frac) * gain;
      gain *= 0.50; cells *= 2;
    }
    return sum;
  };

  // drawBolt() : LA primitive de la famille. Polyligne fractale, 3 passes concentriques.
  const drawBolt = (f0, l0, y0, f1, l1, y1, seed, amp, reveal, thick, alpha, nodes, sheath) => {
    if (reveal <= 0 || thick <= 0.02 || alpha <= 1) return;
    let pf = f0, pl = l0, py = y0;
    for (let n = 1; n <= nodes; n++) {
      let u = n / nodes;
      if (u > reveal) u = reveal;       // bout franc : un éclair en descente ne s'évanouit pas
      const base = u;
      const ef = f0 + (f1 - f0) * base, el = l0 + (l1 - l0) * base, ey = y0 + (y1 - y0) * base;
      const taper = Math.sin(base * 3.14159);
      const off1 = boltOffset(seed, base, amp) * taper;
      const off2 = boltOffset(seed + 5000, base, amp) * taper;
      const sf = f1 - f0, sl = l1 - l0, sy = y1 - y0;
      const hlen = Math.sqrt(sf * sf + sl * sl);
      let nf, nl;
      if (hlen < 0.001) { nf = 1; nl = 0; } else { nf = -sl / hlen; nl = sf / hlen; }
      const cf = ef + nf * off1, cl = el + nl * off1;
      const cyy = ey + off2 * (hlen > Math.abs(sy) ? 0.42 : 1.0);
      if (sheath) {
        seg(pf, pl, py, cf, cl, cyy, BOLT_CORE * 2.45 * thick, mix(VIOLET, ARC, 0.5), opq(alpha * 0.80));
        seg(pf, pl, py, cf, cl, cyy, BOLT_CORE * 1.55 * thick, NEON, opq(alpha * 0.92));
      }
      seg(pf, pl, py, cf, cl, cyy, BOLT_CORE * (sheath ? 1.0 : 0.80) * thick,
          sheath ? CORE : mix(NEON, CORE, 0.55), opq(alpha));
      pf = cf; pl = cl; py = cyy;
      if (u >= reveal) break;
    }
  };

  const neonEdge = (f0, l0, y0, f1, l1, y1, grow, thick, rgb, alpha) => {
    if (grow <= 0 || alpha <= 1) return;
    const ef = f0 + (f1 - f0) * grow, el = l0 + (l1 - l0) * grow, ey = y0 + (y1 - y0) * grow;
    seg(f0, l0, y0, ef, el, ey, thick * 1.9, mix(rgb, DEEP, 0.45), opq(alpha * 0.82));
    seg(f0, l0, y0, ef, el, ey, thick, rgb, opq(alpha));
  };

  // drawBlade() : ruban en arc de 210°. `tilt` verse la coordonnée latérale dans la hauteur
  // (0 = lame à plat, 1 = lame verticale) — c'est ce qui fait que les deux entailles décrivent
  // un X dans l'espace au lieu de deux cercles superposés.
  const drawBlade = (sweep, phase, tilt, dir, scale, alpha, bladeVariant) => {
    if (sweep <= 0 || scale <= 0.02) return;
    const arc = 3.665, segments = 44;
    const start = -arc * 0.5 + phase, reach = arc * sweep;
    const flat = 1.0 - tilt;
    for (let s = 0; s < segments; s++) {
      const u = s / (segments - 1);
      if (u > sweep) break;
      const ang = start + dir * reach * u;
      const belly = Math.sin(clamp01(u / Math.max(0.05, sweep)) * 3.14159);
      const r = ECHO_RADIUS * scale * (0.72 + 0.34 * belly);
      const lat = Math.sin(ang) * r;
      const f = Math.cos(ang) * r;
      const l = lat * flat;
      const y = 0.95 + lat * tilt * 0.92;
      for (let layer = 0; layer < 3; layer++) {
        const lv = layer / 2;
        const hw = (0.055 + 0.145 * lv) * scale * (0.55 + 0.65 * belly);
        const hh = (0.230 - 0.090 * lv) * scale * (0.50 + 0.70 * belly);
        const rgb = layer === 0 ? CORE : (layer === 1 ? mix(NEON, HOT, 0.35) : VIOLET);
        const fade = layer === 0 ? 1.0 : (layer === 1 ? 0.95 : 0.88);
        if (hw <= 0.004 || hh <= 0.004) continue;
        rot(f, l, y, hw, hh, hw * 1.65, -ang, tilt * 1.5708, 0, rgb, opq(alpha * fade));
      }
      if (s === segments - 1 || u + 1 / segments > sweep) {
        const sz = 0.145 * scale;
        box(f, l, y, sz, sz, sz, CORE, opq(alpha));
      }
      if ((s & 3) === 0 && bladeVariant < 10) {
        const hsp = hash(salt, 11000 + bladeVariant * 311 + s * 17);
        const drop = fract(hsp + sweep * 1.6);
        const sz = 0.052 * scale * (1 - drop);
        if (sz > 0.004) {
          box(f * (1 + 0.06 * hsp), l * (1 + 0.06 * hsp), y - drop * 1.25,
              sz, sz * 1.6, sz, mix(ARC, NEON, hsp), opq(alpha * 0.92));
        }
      }
    }
  };

  // =========================================================================================
  //  I. ARC FULGURANT EN CHAÎNE — 130 ticks
  // =========================================================================================
  if (variant === 0) {
    const a = Math.round(255 * smooth(t / 0.04) * (1 - smooth((t - 0.94) / 0.06)));
    if (a <= 4) return out;

    const ionise = smooth(t / 0.10);
    const fall = smooth((t - 0.10) / 0.055);
    const flash = smooth((t - 0.15) / 0.02) * (1 - smooth((t - 0.21) / 0.10));
    const crown = smooth((t - 0.155) / 0.17);
    const vitrify = smooth((t - 0.17) / 0.22);
    const dusk = 1 - smooth((t - 0.80) / 0.20);

    // 1. IONISATION — le « leader ascendant » : sans lui l'éclair surgit de nulle part.
    if (ionise > 0.01 && fall < 0.92) {
      const live = ionise * (1 - smooth(fall * 1.4));
      for (let fil = 0; fil < 14; fil++) {
        const h0 = hash(salt, 100 + fil * 37);
        const ang = fil * TAU / 14 + h0 * 0.38;
        const rad = 0.70 + 2.40 * hash(salt, 160 + fil * 11);
        const top = (1.10 + 2.30 * h0) * live;
        const steps = 7;
        for (let s = 0; s < steps; s++) {
          const u = s / (steps - 1);
          if (u > live * 1.15) break;
          const wob = 0.26 * Math.sin(u * 7 + h0 * 9 + t * 34);
          const r = rad * (1 - 0.22 * u);
          const y = -0.42 + top * u;
          const sz = (0.070 - 0.034 * u) * live;
          if (sz <= 0.004) continue;
          box(Math.cos(ang + wob) * r, Math.sin(ang + wob) * r, y,
              sz, sz * 1.45, sz, mix(DEEP, ARC, u), opq(a * live * (0.55 + 0.45 * u)));
        }
      }
      const ringCount = 46;
      for (let i = 0; i < ringCount; i++) {
        const ang = i * TAU / ringCount + t * 2.4;
        const h0 = hash(salt, 400 + i * 13);
        if (h0 > 0.30 + 0.62 * live) continue;
        const r = 3.25 * (0.90 + 0.12 * Math.sin(ang * 5 + t * 20));
        const sz = 0.062 + 0.040 * h0;
        box(Math.cos(ang) * r, Math.sin(ang) * r, -0.40 + 0.05 * h0,
            sz, sz * 0.55, sz, mix(DEEP, VIOLET, h0), opq(a * live * 0.85));
      }
    }

    // 2. LE TRAIT MAÎTRE — 22 blocs, révélé de haut en bas, + 5 ramifications.
    if (fall > 0 && t < 0.34) {
      const persist = 1 - smooth((t - 0.17) / 0.15);
      const reveal = fall;
      const thick = 1 - 0.55 * (1 - persist);
      if (persist > 0.02) {
        drawBolt(0, 0, SKY_HEIGHT, 0, 0, 0, salt * 31 + 7, 1.55, reveal, thick, a * persist, 56, true);
        for (let br = 0; br < 5; br++) {
          const hb = hash(salt, 900 + br * 59);
          const u0 = 0.18 + 0.62 * hb;
          if (reveal < u0 + 0.05) continue;
          const bang = hash(salt, 940 + br * 23) * TAU;
          const blen = 2.10 + 2.90 * hash(salt, 980 + br * 17);
          const sy = SKY_HEIGHT * (1 - u0);
          const sxF = boltOffset(salt * 31 + 7, u0, 1.55);
          const sxL = boltOffset(salt * 31 + 7 + 5000, u0, 1.55);
          const ey = Math.max(0.10, sy - blen * (0.45 + 0.40 * hb));
          drawBolt(sxF, sxL, sy, sxF + Math.cos(bang) * blen, sxL + Math.sin(bang) * blen, ey,
                   salt * 17 + br * 211, 0.72, clamp01((reveal - u0) * 3.4),
                   thick * 0.62, a * persist * 0.92, 16, false);
        }
      }
    }

    // 3. FLASH D'IMPACT.
    if (flash > 0.02) {
      const r = 0.55 + 1.35 * flash, shells = 3;
      for (let sh = 0; sh < shells; sh++) {
        const sv = sh / (shells - 1);
        const rr = r * (0.42 + 0.58 * sv);
        const count = Math.max(10, Math.round(rr * 13));
        for (let i = 0; i < count; i++) {
          const h0 = hash(salt, 1500 + sh * 97 + i * 29);
          const ang = i * TAU / count + sh * 0.5;
          const yy = (h0 - 0.5) * rr * 1.5;
          const denom = Math.max(0.01, rr * 0.75);
          const pr = rr * Math.sqrt(Math.max(0, 1 - (yy / denom) * (yy / denom)));
          const sz = (0.17 - 0.07 * sv) * flash;
          if (sz <= 0.004) continue;
          box(Math.cos(ang) * pr, Math.sin(ang) * pr, 0.30 + yy,
              sz, sz, sz, mix(CORE, HOT, sv * 0.8), opq(a * flash));
        }
      }
    }

    // 4. COURONNE DE PLASMA + 8 serpents de surface.
    if (crown > 0.01) {
      const r = CROWN_RADIUS * crown;
      const thin = 1 - crown * 0.55;
      const count = Math.max(16, Math.round(r * 15));
      for (let i = 0; i < count; i++) {
        const h0 = hash(salt, 2100 + i * 31);
        const ang = i * TAU / count;
        const rr = r * (0.94 + 0.09 * Math.sin(ang * 7 + t * 9) + 0.04 * h0);
        const sz = (0.115 + 0.060 * h0) * thin;
        if (sz <= 0.004) continue;
        box(Math.cos(ang) * rr, Math.sin(ang) * rr, -0.40 + 0.09 * h0,
            sz * 1.5, sz * 0.6, sz * 1.5, mix(VIOLET, NEON, h0 * 0.7), opq(a * (1 - 0.35 * crown)));
      }
      for (let sp = 0; sp < 8; sp++) {
        const hs = hash(salt, 2400 + sp * 67);
        const ang = sp * TAU / 8 + hs * 0.5;
        const reach = r * (0.55 + 0.45 * hs);
        drawBolt(0, 0, -0.36, Math.cos(ang) * reach, Math.sin(ang) * reach, -0.36,
                 salt * 13 + sp * 331, 0.58, crown, 0.52, a * (1 - 0.45 * crown), 12, false);
      }
    }

    // 5. FULGURITES — après la foudre, le sol GARDE une trace.
    if (vitrify > 0.01) {
      const heat = 1 - smooth((t - 0.30) / 0.34);
      for (let fg = 0; fg < 7; fg++) {
        const h0 = hash(salt, 3000 + fg * 53);
        const ang = fg * TAU / 7 + h0 * 0.7;
        const rad = 0.65 + 1.55 * h0;
        const joints = 5, grow = vitrify * dusk;
        for (let s = 0; s < joints; s++) {
          const u = s / (joints - 1);
          if (u > grow * 1.2) break;
          const lean = 0.22 * u * u;
          const y = -0.44 + (0.52 + 0.70 * h0) * u * grow;
          const sz = (0.115 - 0.062 * u) * grow;
          if (sz <= 0.004) continue;
          box(Math.cos(ang) * (rad + lean), Math.sin(ang) * (rad + lean), y,
              sz, sz * 1.3, sz, mix(GLASS, MOLTEN, heat * (1 - u * 0.55)), opq(a * dusk));
        }
      }
      const marks = 38;
      for (let i = 0; i < marks; i++) {
        const h0 = hash(salt, 3400 + i * 41);
        if (h0 > 0.72) continue;
        const ang = i * TAU / marks + h0;
        const rr = 0.45 + 2.35 * hash(salt, 3500 + i * 19);
        const sz = 0.085 + 0.075 * h0;
        box(Math.cos(ang) * rr, Math.sin(ang) * rr, -0.455,
            sz * 1.7, sz * 0.30, sz * 1.7, mix(SCORCH, GLASS, h0 * 0.55), opq(a * vitrify * dusk * 0.95));
      }
    }

    // 6. LA CHAÎNE — six relais, le suivant part de la victime précédente.
    let prevF = 0, prevL = 0, prevY = 0.55;
    for (let k = 0; k < CHAIN_LINKS; k++) {
      const born = 0.30 + k * 0.072;
      const age = (t - born) / 0.115;
      const hk = hash(salt, 5000 + k * 149);
      const ang = hash(salt, 5100 + k * 73) * TAU;
      const rad = 2.45 + 3.55 * hk;
      const tf = Math.cos(ang) * rad, tl = Math.sin(ang) * rad;
      const ty = 0.35 + 0.85 * hash(salt, 5200 + k * 29);
      if (age > 0 && age < 1) {
        const draw = smooth(age * 2.2);
        const keep = 1 - smooth((age - 0.45) / 0.55);
        drawBolt(prevF, prevL, prevY, tf, tl, ty, salt * 7 + k * 877, 0.80, draw,
                 0.55 + 0.45 * keep, a * (0.55 + 0.45 * keep), 20, true);
        if (age > 0.40) {
          const hit = smooth((age - 0.40) / 0.22) * (1 - smooth((age - 0.72) / 0.28));
          for (let i = 0; i < 14; i++) {
            const h0 = hash(salt, 5400 + k * 61 + i * 17);
            const a2 = i * TAU / 14 + k;
            const rr = (0.30 + 0.42 * hit) * (0.7 + 0.5 * h0);
            const sz = 0.090 * hit;
            if (sz <= 0.004) continue;
            box(tf + Math.cos(a2) * rr, tl + Math.sin(a2) * rr, ty + (h0 - 0.5) * 0.70,
                sz, sz, sz, mix(HOT, CORE, h0), opq(a * hit));
          }
          const ring = 18;
          for (let i = 0; i < ring; i++) {
            const a2 = i * TAU / ring;
            const rr = 0.95 * hit;
            box(tf + Math.cos(a2) * rr, tl + Math.sin(a2) * rr, -0.40,
                0.075, 0.030, 0.075, VIOLET, opq(a * hit * 0.9));
          }
        }
      }
      if (t > born) { prevF = tf; prevL = tl; prevY = ty; }
    }

    // 7. CRÉPITEMENT RÉSIDUEL — l'air reste ionisé bien après le coup.
    if (t > 0.22) {
      const fizz = dusk;
      for (let i = 0; i < 40; i++) {
        const h0 = hash(salt, 6000 + i * 83);
        const cyc = fract(t * (1.3 + 1.7 * h0) + h0);
        if (cyc > 0.34) continue;
        const a2 = hash(salt, 6100 + i * 31) * TAU;
        const rr = 0.50 + 4.30 * hash(salt, 6200 + i * 23);
        const yy = -0.30 + 2.20 * hash(salt, 6300 + i * 13);
        const sz = 0.055 * (1 - cyc / 0.34) * fizz;
        if (sz <= 0.004) continue;
        box(Math.cos(a2) * rr, Math.sin(a2) * rr, yy, sz, sz * 1.8, sz, mix(ARC, HOT, h0), opq(a * fizz));
      }
    }
    return out;
  }

  // =========================================================================================
  //  II. SURCADENCE CYBER-X — 120 ticks
  // =========================================================================================
  if (variant === 1) {
    const a = Math.round(255 * smooth(t / 0.05) * (1 - smooth((t - 0.95) / 0.05)));
    if (a <= 4) return out;

    const boot = smooth(t / 0.14);
    const build = smooth((t - 0.12) / 0.26);
    const run = smooth((t - 0.34) / 0.10);
    const burst = smooth((t - 0.78) / 0.09);
    const collapse = smooth((t - 0.80) / 0.16);
    const cage = 1 - collapse;
    const shrink = 1 - 0.88 * collapse;   // l'implosion éteint la cage par la GÉOMÉTRIE

    // 1. LES QUATRE PYLÔNES.
    if (boot > 0.01 && cage > 0.02) {
      for (let p = 0; p < 4; p++) {
        const ang = p * TAU / 4 + 0.7854;
        const r = CAGE_HALF * 1.42 * shrink;
        const h = (0.30 + 2.50 * boot) * cage;
        const steps = 9;
        for (let s = 0; s < steps; s++) {
          const u = s / (steps - 1);
          if (u > boot * 1.1) break;
          const y = -0.44 + h * u;
          const sz = 0.105 - 0.045 * u;
          const rgb = u > 0.80 ? CYAN : mix(DEEP, CYAN, u);
          box(Math.cos(ang) * r, Math.sin(ang) * r, y, sz, sz * 1.15, sz, rgb, opq(a * cage));
        }
      }
    }

    // 2. LA CAGE — douze arêtes tracées une par une.
    if (build > 0.005 && cage > 0.02) {
      const e = CAGE_HALF * shrink, lo = -0.44, hi = -0.44 + 2.68 * shrink;
      for (let edge = 0; edge < 12; edge++) {
        const born = edge / 14;
        const g = clamp01((build - born) * 5);
        if (g <= 0) continue;
        let f0, l0, y0, f1, l1, y1;
        if (edge < 4) {
          const a0 = edge * TAU / 4 + 0.7854, a1 = (edge + 1) * TAU / 4 + 0.7854;
          f0 = Math.cos(a0) * e; l0 = Math.sin(a0) * e; y0 = lo;
          f1 = Math.cos(a1) * e; l1 = Math.sin(a1) * e; y1 = lo;
        } else if (edge < 8) {
          const k = edge - 4;
          const a0 = k * TAU / 4 + 0.7854, a1 = (k + 1) * TAU / 4 + 0.7854;
          f0 = Math.cos(a0) * e; l0 = Math.sin(a0) * e; y0 = hi;
          f1 = Math.cos(a1) * e; l1 = Math.sin(a1) * e; y1 = hi;
        } else {
          const k = edge - 8;
          const a0 = k * TAU / 4 + 0.7854;
          f0 = Math.cos(a0) * e; l0 = Math.sin(a0) * e; y0 = lo;
          f1 = f0; l1 = l0; y1 = hi;
        }
        neonEdge(f0, l0, y0, f1, l1, y1, g, 0.052, CYAN, a * cage);
      }
    }

    // 3. PISTES DE CIRCUIT IMPRIMÉ — tracé de Manhattan, jamais de diagonale, vias aux coudes.
    if (build > 0.22 && cage > 0.02) {
      const trace = clamp01((build - 0.22) / 0.72);
      const e = CAGE_HALF * shrink, lo = -0.40, span = 2.60 * shrink;
      for (let face = 0; face < 4; face++) {
        const fang = face * TAU / 4 + 0.7854;
        const nf = Math.cos(fang), nl = Math.sin(fang);
        const tf = -nl, tl = nf;
        for (let tr = 0; tr < 3; tr++) {
          const seed = salt + face * 971 + tr * 131;
          let u = -0.62 + tr * 0.62;
          let y = lo + 0.30 + tr * 0.22;
          for (let s = 0; s < 6; s++) {
            const g = clamp01((trace - s / 7) * 6);
            if (g <= 0) break;
            const horizontal = (s % 2) === 0;
            let step = (0.26 + 0.34 * hash(seed, s * 17)) * (horizontal ? 1.0 : 0.85);
            if (!horizontal && hash(seed, s * 29) < 0.5) step = -step;
            let u2 = horizontal ? u + step : u;
            let y2 = horizontal ? y : y + step;
            u2 = Math.max(-0.92, Math.min(0.92, u2));
            y2 = Math.max(lo + 0.10, Math.min(lo + span, y2));
            const du = (u2 - u) * g, dy = (y2 - y) * g;
            neonEdge(nf * e + tf * u, nl * e + tl * u, y,
                     nf * e + tf * (u + du), nl * e + tl * (u + du), y + dy,
                     1.0, 0.030, PCB, a * cage);
            u += du; y += dy;
            if (g >= 0.999) {
              box(nf * e + tf * u, nl * e + tl * u, y, 0.058, 0.058, 0.058,
                  mix(PCB, CYAN, 0.65), opq(a * cage));
            }
          }
        }
      }
    }

    // 4. PAQUETS DE DONNÉES — ils courent sur les arêtes, avec une traînée de 3 copies.
    if (run > 0.01 && cage > 0.02) {
      const e = CAGE_HALF * shrink, lo = -0.44, hi = -0.44 + 2.68 * shrink;
      for (let p = 0; p < 26; p++) {
        const h0 = hash(salt, 7000 + p * 59);
        const speed = 0.55 + 1.35 * h0;
        const u = fract(t * speed + h0);
        const lane = ((hash(salt, 7100 + p * 23) * 8) | 0) & 7;
        let f0, l0, y0;
        if (lane < 4) {
          const top = (lane & 1) === 0;
          const ring = u * TAU;
          f0 = Math.cos(ring) * e; l0 = Math.sin(ring) * e; y0 = top ? hi : lo;
        } else {
          const ang = (lane - 4) * TAU / 4 + 0.7854;
          f0 = Math.cos(ang) * e; l0 = Math.sin(ang) * e; y0 = lo + (hi - lo) * u;
        }
        const sz = 0.070 + 0.040 * h0;
        const rgb = h0 > 0.66 ? MAGENTA : (h0 > 0.33 ? CYAN : HOT);
        box(f0, l0, y0, sz, sz, sz, rgb, opq(a * run * cage));
        for (let q = 1; q <= 3; q++) {
          const back = u - q * 0.022;
          if (back < 0) break;
          let bf, bl, by;
          if (lane < 4) { const ring = back * TAU; bf = Math.cos(ring) * e; bl = Math.sin(ring) * e; by = y0; }
          else { bf = f0; bl = l0; by = lo + (hi - lo) * back; }
          const bs = sz * (1 - q * 0.26);
          box(bf, bl, by, bs, bs, bs, rgb, opq(a * run * cage * 0.9));
        }
      }
    }

    // 5. LE X — la marque de l'effet, deux barres croisées qui tournent.
    {
      const spin = t * 5.4;
      const arm = (0.95 + 0.55 * run) * shrink * (0.35 + 0.65 * boot);
      const yMid = -0.44 + 1.34 * shrink;
      for (let bar = 0; bar < 2; bar++) {
        const tilt = bar === 0 ? 0.68 : -0.68;
        const segs = 15;
        for (let s = 0; s < segs; s++) {
          const u = s / (segs - 1) * 2 - 1;
          const f = Math.cos(spin) * arm * u;
          const l = Math.sin(spin) * arm * u;
          const y = yMid + tilt * arm * u;
          const sz = 0.085 * (1 - 0.45 * Math.abs(u));
          const rgb = Math.abs(u) < 0.22 ? CORE : mix(CYAN, MAGENTA, (u + 1) * 0.5);
          box(f, l, y, sz, sz, sz, rgb, opq(a * (1 - 0.5 * collapse)));
        }
      }
    }

    // 6. HISTOGRAMME DE CADENCE.
    if (run > 0.02 && cage > 0.02) {
      for (let b = 0; b < 12; b++) {
        const hb = hash(salt, 7600 + b * 37);
        const phase = fract(t * 1.25 + b / 12);
        const lvl = (0.25 + 0.75 * hb) * (0.35 + 0.65 * Math.sin(phase * TAU) * 0.5 + 0.325);
        const ang = b * TAU / 12;
        const r = CAGE_HALF * 1.72 * shrink;
        const cells = Math.max(1, Math.round(lvl * 7 * run));
        for (let c = 0; c < cells; c++) {
          const y = -0.42 + c * 0.19;
          const rgb = c >= 5 ? MAGENTA : (c >= 3 ? HOT : PCB);
          box(Math.cos(ang) * r, Math.sin(ang) * r, y, 0.072, 0.070, 0.072, rgb, opq(a * run * cage));
        }
      }
    }

    // 7. ANNEAUX DE SCAN.
    if (run > 0.02 && cage > 0.02) {
      for (let s = 0; s < 3; s++) {
        const u = fract(t * 0.95 + s / 3);
        const y = -0.44 + 2.68 * u * shrink;
        const r = CAGE_HALF * (1.05 + 0.22 * Math.sin(u * TAU)) * shrink;
        const count = Math.max(14, Math.round(r * 16));
        for (let i = 0; i < count; i++) {
          const ang = i * TAU / count + t * 1.7;
          box(Math.cos(ang) * r, Math.sin(ang) * r, y, 0.052, 0.030, 0.052,
              mix(CYAN, CORE, 0.35), opq(a * run * cage * 0.95));
        }
      }
    }

    // 8. DÉCHARGE — la cage implose, une onde part au sol, l'énergie remonte dans le porteur.
    if (burst > 0.01) {
      const r = 0.40 + 4.60 * burst;
      const thin = 1 - 0.62 * burst;
      const count = Math.max(18, Math.round(r * 14));
      for (let i = 0; i < count; i++) {
        const h0 = hash(salt, 8100 + i * 43);
        const ang = i * TAU / count;
        const rr = r * (0.93 + 0.10 * Math.sin(ang * 6 + t * 12));
        const sz = (0.125 + 0.055 * h0) * thin;
        if (sz <= 0.004) continue;
        box(Math.cos(ang) * rr, Math.sin(ang) * rr, -0.40 + 0.07 * h0,
            sz * 1.4, sz * 0.55, sz * 1.4, mix(CYAN, MAGENTA, h0 * 0.6), opq(a * (1 - 0.3 * burst)));
      }
      const col = Math.round(22 * (1 - burst));
      for (let i = 0; i < col; i++) {
        const h0 = hash(salt, 8300 + i * 29);
        const y = -0.40 + 3.10 * fract(h0 + t * 2.1);
        const rr = 0.28 * (1 - y / 3.2);
        const ang = h0 * TAU + t * 6;
        box(Math.cos(ang) * rr, Math.sin(ang) * rr, y, 0.070, 0.095, 0.070,
            mix(HOT, CORE, h0), opq(a * burst));
      }
    }
    return out;
  }

  // =========================================================================================
  //  III. DOUBLE ENTAILLE D'ÉCHO — 96 ticks
  // =========================================================================================
  {
    const a = Math.round(255 * smooth(t / 0.04) * (1 - smooth((t - 0.92) / 0.08)));
    if (a <= 4) return out;

    const cut1 = smooth(t / 0.16);
    const cut2 = smooth((t - 0.30) / 0.18);
    const cross = smooth((t - 0.46) / 0.12) * (1 - smooth((t - 0.74) / 0.26));
    const outp = smooth((t - 0.74) / 0.26);

    // 1 & 3. LES DEUX LAMES — la seconde inclinée à 90° et en sens inverse : un X dans l'espace.
    drawBlade(cut1, 0, 0, +1, 1 - 0.55 * smooth((t - 0.20) / 0.30), a, 0);
    if (cut2 > 0) {
      drawBlade(cut2, 1.5708, 1, -1, 1.08 * (1 - 0.45 * smooth((t - 0.52) / 0.34)), a, 1);
    }

    // 2. RÉMANENCES — elles s'effacent par la TAILLE, jamais par l'alpha.
    if (t > 0.14 && t < 0.46) {
      const ghost = 1 - smooth((t - 0.16) / 0.28);
      for (let g = 1; g <= 4; g++) {
        const back = g * 0.055;
        const gt = clamp01(cut1 - back * 2.2);
        if (gt <= 0.02) continue;
        drawBlade(gt, 0, 0, +1, ghost * (0.74 - 0.15 * g), a, 10 + g);
      }
    }

    // 4. COURT-CIRCUIT — gerbe d'arcs au croisement des deux plans + noyau surexposé.
    if (cross > 0.01) {
      for (let s = 0; s < 10; s++) {
        const hs = hash(salt, 9000 + s * 71);
        const cyc = fract(t * 2.6 + hs);
        if (cyc > 0.42) continue;
        const live = 1 - cyc / 0.42;
        const a0 = hash(salt, 9100 + s * 37) * TAU;
        const a1 = a0 + 1.4 + hash(salt, 9200 + s * 19) * 2.2;
        const r0 = ECHO_RADIUS * (0.35 + 0.55 * hash(salt, 9300 + s * 23));
        const r1 = ECHO_RADIUS * (0.35 + 0.55 * hash(salt, 9400 + s * 29));
        const y0 = 0.25 + 1.35 * hash(salt, 9500 + s * 13);
        const y1 = 0.25 + 1.35 * hash(salt, 9600 + s * 17);
        drawBolt(Math.cos(a0) * r0, Math.sin(a0) * r0, y0,
                 Math.cos(a1) * r1, Math.sin(a1) * r1, y1,
                 salt * 23 + s * 457, 0.42, 1.0, live, a * cross * live, 10, true);
      }
      const pulse = 0.6 + 0.4 * Math.sin(t * 46);
      for (let i = 0; i < 16; i++) {
        const h0 = hash(salt, 9800 + i * 31);
        const ang = i * TAU / 16;
        const rr = (0.22 + 0.30 * h0) * pulse * cross;
        const sz = 0.085 * cross;
        if (sz <= 0.004) continue;
        box(Math.cos(ang) * rr, Math.sin(ang) * rr, 0.95 + (h0 - 0.5) * 0.55,
            sz, sz, sz, mix(CORE, HOT, h0 * 0.7), opq(a * cross));
      }
    }

    // 5. DISSIPATION — étincelles BALISTIQUES : elles retombent, parabole et gravité.
    if (outp > 0.01) {
      for (let i = 0; i < 46; i++) {
        const h0 = hash(salt, 10200 + i * 53);
        const h1 = hash(salt, 10300 + i * 29);
        const age = outp * (0.65 + 0.70 * h0);
        if (age >= 1) continue;
        const ang = h1 * TAU;
        const sp = 1.35 + 2.45 * h0;
        const r = sp * age;
        const y = 1.05 + (1.95 * h1) * age - 5.2 * age * age;
        if (y < -0.44) continue;
        const sz = (0.075 - 0.035 * age) * (1 - outp * 0.4);
        if (sz <= 0.004) continue;
        box(Math.cos(ang) * r, Math.sin(ang) * r, y, sz, sz * 1.7, sz,
            mix(ARC, HOT, h0), opq(a * (1 - age * 0.5)));
      }
    }
    return out;
  }
}

if (typeof module !== 'undefined' && module.exports) module.exports = { buildThunder };
