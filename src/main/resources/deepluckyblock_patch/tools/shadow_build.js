/* ===========================================================================================
 * shadow_build.js — MIROIR JS de deepluckyblock.client.ShadowSignatureVfx
 *
 * Même rôle que thunder_build.js pour la foudre : rejouer la géométrie Java dans un navigateur
 * pour la relire sans lancer Minecraft. Toute divergence avec le .java est un BOGUE du miroir.
 *
 * Repère : le Java travaille en (f = axe attaquant→cible, lateral = perpendiculaire, y = hauteur)
 * puis projette avec dx/dz. L'aperçu fixe dx = 1, dz = 0, l'origine à l'impact, donc :
 *        x = f          y = y          z = lateral
 * et l'orientation du repère, -atan2(dz, dx), vaut ZÉRO. C'est pourquoi `base` disparaît de
 * toutes les expressions de lacet ci-dessous — et c'est la seule simplification autorisée.
 *
 * ATTENTION — ce que l'aperçu ne modélise PAS : le tamis du moteur (pixelDensity < 0.999 &&
 * alpha < 200 décime les cubes), cubeVeil, ni le plafond de 7000 cubes par frame. Un rendu
 * correct ici ne prouve donc RIEN en jeu ; il sert à relire la chorégraphie, pas à la valider.
 * C'est précisément pour cela que opq() existe : il reproduit le plancher d'opacité du Java.
 *
 * CE MIROIR N'EST PAS UNE TRANSCRIPTION LIGNE À LIGNE : piece/rot/drawSpectralHand/
 * drawWitherSkull sont des CLOSURES ici, des méthodes statiques là-bas.
 * =========================================================================================== */

function buildShadow(variant, t, salt = 4211) {
  const out = [];
  if (t <= 0 || t >= 1) return out;

  // ---- Parité stricte avec les utilitaires du .java -------------------------------------
  const TAU = 6.2831855;
  const HALF_PI = 1.5707964;
  const clamp01 = (v) => (v < 0 ? 0 : v > 1 ? 1 : v);
  const smooth = (x) => { x = clamp01(x); return x * x * (3 - 2 * x); };
  const fract = (x) => x - Math.floor(x);
  const SOLID_FLOOR = 208;
  // opaque() : on ne fait JAMAIS disparaître une ombre par fondu d'alpha — elle serait décimée
  // en pointillés par le tamis. Soit le cube passe le seuil, soit il n'est pas émis.
  const opq = (v) => { const i = Math.round(v); return i <= 10 ? 0 : (i < SOLID_FLOOR ? SOLID_FLOOR : Math.min(255, i)); };
  // hash() : arithmétique 32 bits identique au Java (Math.imul + décalage non signé).
  const hash = (seed, n) => {
    let h = (seed ^ Math.imul(n, 0x45D9F3B)) | 0;
    h ^= h >>> 16; h = Math.imul(h, 0x45D9F3B); h ^= h >>> 16;
    return (h & 0xFFFF) / 65535;
  };

  // ---- Palette (DeepElement.SHADOW : 0x4D318E / 0x000000 / 0x7100A7) ---------------------
  // VOID_INK n'est PAS 0x000000 : le noir pur se confond avec le ciel de nuit et le fond des
  // grottes, et la forme disparaît. 0x0B0814 lit comme un trou tout en restant détachable.
  const VOID_INK = 0x0B0814, UMBRA = 0x18102C, PENUMBRA = 0x2C1C50,
        PURPLE = 0x4D318E, VIOLET = 0x7100A7, ORCHID = 0xA85FE0,
        PALE = 0xD9BFFF, SPARK = 0xF3E9FF;
  // Os : quatre valeurs, c'est ce qui donne le volume — pas la teinte.
  const BONE_HI = 0xE9E3D2, BONE = 0xC2BAA6, BONE_MID = 0x8F8877,
        BONE_DARK = 0x5C5749, BONE_DEEP = 0x332F27;
  const ROT_C = 0x6C7A58, ROT_DARK = 0x3A412F, ASH = 0x4A4455, SCAR = 0x241B33;

  // GROUND_Y = -0.86 et non -0.42 comme la foudre : une ombre qui flotte à un demi-bloc du
  // sol se lit immédiatement comme un bug, alors qu'une ombre un peu enfouie reste juste.
  const GROUND_Y = -0.86, SLAB_H = 0.030;
  const REACH_SPAN = 9.0;
  const RIFT_H = 2.70, RIFT_W = 1.15;
  const DOME_R = 7.0, SUN_Y = 10.5, SUN_R = 2.55;

  // ---- Émission --------------------------------------------------------------------------
  const box = (f, lat, y, hf, hy, hl, rgb, a) => {
    if (a <= 0 || hf <= 0 || hy <= 0 || hl <= 0) return;
    out.push({ x: f, y: y, z: lat, hx: hf, hy: hy, hz: hl, rgb: rgb, a: Math.min(255, a), yaw: 0, pitch: 0, roll: 0 });
  };
  const rot = (f, lat, y, hf, hy, hl, yaw, pitch, roll, rgb, a) => {
    if (a <= 0 || hf <= 0 || hy <= 0 || hl <= 0) return;
    out.push({ x: f, y: y, z: lat, hx: hf, hy: hy, hz: hl, rgb: rgb, a: Math.min(255, a), yaw: yaw, pitch: pitch, roll: roll });
  };

  // =========================================================================================
  //  I. ALLONGE SPECTRALE
  // =========================================================================================

  // Pièce de la main. Repère local : u vers l'avant du dos de la main, v latéral, h du poignet
  // vers les doigts. La rotation de couchage transporte h vers l'avant et u vers le haut.
  // ROULIS = -(lie + localRoll), dérivé de rotWrite et non deviné.
  // L'ECHELLE s'applique aux POSITIONS LOCALES ET aux demi-aretes. Elle etait recue par
  // drawSpectralHand puis jamais utilisee : la main sortait a l'echelle 1 quoi qu'on passe.
  const handPiece = (wristF, wristL, wristY, lie, cl, sl_, scale, u, v, h, hu, hh, hv, localRoll, rgb, alpha) => {
    if (alpha <= 1) return;
    const f = wristF + (u * cl + h * sl_) * scale;
    const y = wristY + (h * cl - u * sl_) * scale;
    rot(f, wristL + v * scale, y, hu * scale, hh * scale, hv * scale, 0, 0, -(lie + localRoll), rgb, opq(alpha));
  };

  // La main spectrale : 78 pièces, articulées, et surtout PLIABLES À PLAT. Les doigts sont des
  // chaînes cinématiques — chaque phalange part du bout de la précédente avec son angle cumulé,
  // ce qui donne un vrai poing là où un pliage à angle unique ferait un éventail.
  const drawSpectralHand = (wristF, wristL, wristY, lie, grip, scale, alpha, salt, strobe) => {
    if (alpha <= 1) return;
    const cl = Math.cos(lie), sl_ = Math.sin(lie);
    const P = (u, v, h, hu, hh, hv, lr, rgb, a) =>
      handPiece(wristF, wristL, wristY, lie, cl, sl_, scale, u, v, h, hu, hh, hv, lr, rgb, a);

    // avant-bras
    for (let i = 0; i < 5; i++) {
      const u = i / 4.0;
      P(0, 0, -0.30 - u * 0.62, 0.105, 0.11, 0.17 - 0.025 * u, 0,
        i === 0 ? UMBRA : (i < 3 ? PENUMBRA : VOID_INK), alpha * (1 - 0.10 * u));
    }
    for (let s = -1; s <= 1; s += 2) P(0.055, s * 0.11, -0.56, 0.022, 0.30, 0.022, 0, PURPLE, alpha * 0.85);

    // poignet
    P(0, 0, -0.10, 0.115, 0.10, 0.185, 0, UMBRA, alpha);
    P(0, 0, -0.10, 0.125, 0.035, 0.200, 0, PURPLE, alpha * 0.80);

    // paume : trois plans, du plus profond au plus clair
    P(0, 0, 0.13, 0.115, 0.235, 0.265, 0, VOID_INK, alpha);
    P(-0.085, 0, 0.13, 0.040, 0.225, 0.255, 0, UMBRA, alpha);
    P(0.098, 0, 0.13, 0.035, 0.215, 0.245, 0, PENUMBRA, alpha);
    // les quatre liserés du contour : c'est EUX qui dessinent la main
    P(0, 0.275, 0.13, 0.120, 0.230, 0.028, 0, PURPLE, alpha * 0.90);
    P(0, -0.275, 0.13, 0.120, 0.230, 0.028, 0, PURPLE, alpha * 0.90);
    P(0, 0, 0.375, 0.118, 0.028, 0.268, 0, VIOLET, alpha * 0.90);
    // braise de la paume : elle s'allume quand la main se ferme
    const emberA = alpha * (0.25 + 0.75 * grip);
    const es = (0.055 + 0.085 * grip) * (0.85 + 0.3 * hash(salt + strobe * 7, 3));
    P(0.075, 0, 0.15, es, es, es, 0, VIOLET, emberA);
    P(0.090, 0, 0.15, es * 0.55, es * 0.55, es * 0.55, 0, PALE, emberA);

    // les quatre doigts — longueurs réelles : majeur le plus long, auriculaire court. Des
    // doigts de longueur égale ne se lisent pas comme une main mais comme un peigne.
    const fingerLat = [0.195, 0.065, -0.065, -0.195];
    const fingerLen = [0.90, 1.00, 0.94, 0.74];
    const fingerSpread = [0.22, 0.07, -0.07, -0.20];
    for (let fdx = 0; fdx < 4; fdx++) {
      const L = fingerLen[fdx];
      const seg3 = [L * 0.42, L * 0.33, L * 0.25];
      const rad = [0.072, 0.062, 0.050];
      const lag = clamp01((grip - fdx * 0.07) / 0.80);
      const bend = [lag * 1.05, lag * 1.30, lag * 1.45];
      let curF = 0, curH = 0.375, ang = -0.16 + fingerSpread[fdx] * 0.30;
      for (let p = 0; p < 3; p++) {
        ang += bend[p];
        const half = seg3[p] * 0.5;
        const pf = curF + Math.sin(ang) * half, ph = curH + Math.cos(ang) * half;
        const body = p === 0 ? UMBRA : (p === 1 ? PENUMBRA : VOID_INK);
        P(pf, fingerLat[fdx], ph, rad[p], half, rad[p], ang, body, alpha);
        P(pf, fingerLat[fdx] + (fdx < 2 ? 1 : -1) * (rad[p] + 0.014), ph,
          rad[p] * 0.55, half * 0.92, 0.013, ang, PURPLE, alpha * 0.85);
        curF += Math.sin(ang) * seg3[p];
        curH += Math.cos(ang) * seg3[p];
        if (p < 2) P(curF, fingerLat[fdx], curH, rad[p] * 0.95, rad[p] * 0.80, rad[p] * 0.95, ang, PENUMBRA, alpha * 0.95);
      }
      P(curF + Math.sin(ang) * 0.055, fingerLat[fdx], curH + Math.cos(ang) * 0.055, 0.028, 0.065, 0.028, ang, ORCHID, alpha);
      P(curF + Math.sin(ang) * 0.105, fingerLat[fdx], curH + Math.cos(ang) * 0.105, 0.016, 0.040, 0.016, ang, PALE, alpha);
    }

    // le pouce : deux phalanges, implanté bas et de côté
    {
      const lag = clamp01(grip / 0.85);
      const seg2 = [0.30, 0.24], rad = [0.082, 0.068];
      let curF = 0.055, curH = 0.02, ang = 0.95 - lag * 0.70, lat = 0.255;
      for (let p = 0; p < 2; p++) {
        const half = seg2[p] * 0.5;
        const pf = curF + Math.sin(ang) * half * 0.35;
        const ph = curH + Math.cos(ang) * half;
        const plat = lat + Math.sin(ang) * half * 0.78;
        P(pf, plat, ph, rad[p], half, rad[p], ang * 0.35, p === 0 ? UMBRA : VOID_INK, alpha);
        curH += Math.cos(ang) * seg2[p];
        lat += Math.sin(ang) * seg2[p] * 0.78;
        ang += 0.55 + lag * 0.55;
      }
      P(curF, lat, curH, 0.026, 0.058, 0.026, 0, ORCHID, alpha);
    }
  };

  const renderGrasp = (alpha) => {
    const a = Math.round(alpha * smooth(t / 0.035) * (1 - smooth((t - 0.93) / 0.07)));
    if (a <= 4) return;
    const flow = smooth(t / 0.13);
    const spine = clamp01((t - 0.13) / 0.14);
    const peel = smooth((t - 0.27) / 0.18);
    const curl = smooth((t - 0.45) / 0.13);
    const pull = smooth((t - 0.58) / 0.24);
    const layDown = smooth((t - 0.82) / 0.12);
    const strobe = Math.floor(t * 24);
    const handF = -REACH_SPAN * 0.06 - (REACH_SPAN * 0.64) * pull;

    // 1. LE COULOIR — une ombre portée s'évase avec la distance à sa source : étroite à la
    //    pointe, large au pied. C'est ce qui lui donne une direction lisible même immobile.
    {
      const ribs = 34;
      const front = flow * REACH_SPAN;
      const back = pull * REACH_SPAN * 0.58;
      for (let i = 0; i < ribs; i++) {
        const u = (i + 0.5) / ribs;
        const dist = u * REACH_SPAN;
        if (dist > front || dist < back) continue;
        const h1 = hash(salt + 11, i * 37), h2 = hash(salt + 12, i * 53);
        let w = 0.22 + 0.74 * u + 0.16 * h1;
        const edge = clamp01((front - dist) / 0.9);
        w *= 0.30 + 0.70 * edge;
        const fade = clamp01((dist - back) / 1.1);
        const av = a * (0.80 + 0.20 * h2) * fade;
        if (av <= 6) continue;
        box(-dist, 0, GROUND_Y, 0.145, SLAB_H, w, PURPLE, opq(av * 0.55));
        box(-dist, 0, GROUND_Y + 0.012, 0.140, SLAB_H, w * 0.76, UMBRA, opq(av * 0.85));
        box(-dist, 0, GROUND_Y + 0.022, 0.135, SLAB_H, w * 0.46, VOID_INK, opq(av));
        if (i % 3 === 0) {
          const sp = (0.14 + 0.40 * hash(salt + 13, i * 71)) * (0.4 + 0.6 * u);
          const side = (i % 6 === 0) ? 1 : -1;
          box(-dist, side * (w + sp * 0.5), GROUND_Y + 0.006, 0.075, SLAB_H, sp * 0.5, PENUMBRA, opq(av * 0.70));
        }
      }
      // le front : sans un bord net, le couloir « apparaît » au lieu d'avancer
      if (flow > 0.02 && flow < 0.999) {
        const tipF = -front;
        for (let k = 0; k < 7; k++) {
          const off = (k - 3) * 0.13;
          const hh = 0.10 + 0.16 * (1 - Math.abs(k - 3) / 3.0);
          box(tipF + Math.abs(off) * 0.9, off, GROUND_Y + 0.034, 0.09, SLAB_H, hh, ORCHID, opq(a * 0.9));
        }
      }
    }

    // 2. L'ÉCHINE — onze vertèbres en vague qui s'éloigne
    if (spine > 0.01) {
      const verts = 11;
      for (let i = 0; i < verts; i++) {
        const u = (i + 0.5) / verts;
        const dist = u * REACH_SPAN * 0.92;
        if (dist < pull * REACH_SPAN * 0.58) continue;
        const local = clamp01((spine - u * 0.55) / 0.45);
        const rise = Math.sin(local * Math.PI) * (0.30 + 0.34 * (1 - u));
        if (rise < 0.01) continue;
        const av = a * (0.55 + 0.45 * local);
        const yy = GROUND_Y + rise;
        box(-dist, 0, yy, 0.085, rise * 0.52, 0.115, UMBRA, opq(av));
        box(-dist, 0, yy + rise * 0.50, 0.055, 0.070, 0.075, PURPLE, opq(av));
        box(-dist, 0, yy + rise * 0.72, 0.030, 0.055, 0.030, ORCHID, opq(av * 0.95));
        for (let s = -1; s <= 1; s += 2)
          box(-dist, s * 0.145, yy + rise * 0.18, 0.040, 0.038, 0.085, PENUMBRA, opq(av * 0.80));
      }
    }

    // 3. LES LAMES — elles disent que le couloir n'est pas décoratif mais tranchant
    if (pull > 0.01) {
      const blades = 9;
      for (let i = 0; i < blades; i++) {
        const u = (i + 0.5) / blades;
        const dist = handF - 0.55 - u * 3.2;
        // Le couloir s'arrete a REACH_SPAN : au-dela, une lame se leve dans le vide.
        if (dist > -0.2 || dist < -REACH_SPAN) continue;
        const local = clamp01((pull - u * 0.42) / 0.30);
        if (local <= 0.01) continue;
        const life = Math.sin(local * Math.PI);
        const hgt = (0.55 + 0.85 * hash(salt + 21, i * 29)) * life;
        if (hgt < 0.04) continue;
        const side = (i % 2 === 0) ? 1 : -1;
        const lat = side * (0.30 + 0.55 * hash(salt + 22, i * 31));
        const lean = side * 0.42 * life;
        const av = a * (0.60 + 0.40 * life);
        rot(dist, lat, GROUND_Y + hgt * 0.5, 0.055, hgt * 0.5, 0.165, 0, 0, lean, VOID_INK, opq(av));
        rot(dist, lat, GROUND_Y + hgt * 0.62, 0.038, hgt * 0.40, 0.105, 0, 0, lean, PURPLE, opq(av * 0.90));
        rot(dist, lat, GROUND_Y + hgt * 0.92, 0.026, hgt * 0.16, 0.055, 0, 0, lean, ORCHID, opq(av));
      }
    }

    // 4. LA MAIN — le même paramètre la lève puis la recouche
    if (peel > 0.001) {
      const up = peel * (1 - layDown);
      const lie = (1 - up) * HALF_PI;
      const grip = curl * (1 - layDown * 0.55);
      const handA = a * (0.35 + 0.65 * up + 0.35 * curl);
      // ECHELLE 1,45 : du poignet au majeur la main fait 2,2 blocs, la taille d'un mob.
      drawSpectralHand(handF, 0, GROUND_Y, lie, grip, 1.45, Math.min(255, handA), salt, strobe);
    }

    // 5. LA CENDRE — elle donne au couloir une épaisseur d'air, sinon c'est un autocollant
    for (let i = 0; i < 26; i++) {
      const h1 = hash(salt + 31, i * 41), h2 = hash(salt + 32, i * 43), h3 = hash(salt + 33, i * 47);
      const age = (t - h1 * 0.55) / 0.45;
      if (age <= 0 || age >= 1) continue;
      const dist = 0.3 + 8.2 * h2;
      if (dist > flow * REACH_SPAN) continue;
      const lat = (h3 - 0.5) * (0.5 + 1.3 * (dist / REACH_SPAN));
      const yy = GROUND_Y + 0.06 + age * (0.8 + 1.5 * h1);
      const s = 0.030 * (1 - age * 0.55);
      box(-dist, lat, yy, s, s, s, h3 > 0.72 ? ORCHID : (h3 > 0.38 ? PURPLE : ASH), opq(a * (1 - age) * 0.85));
    }
  };

  // =========================================================================================
  //  II. FAILLE DU FLÉTRISSEUR
  // =========================================================================================

  // Pièce du crâne : position tournée du lacet, boîte orientée en conséquence. Le lacet de la
  // BOÎTE est l'opposé de celui des positions (même piège que le marteau de Thor).
  const skullPieceRoll = (atF, atL, atY, yaw, scale, lf, ll, ly, hf, hy, hl, roll, rgb, alpha) => {
    if (alpha <= 1) return;
    const c = Math.cos(yaw), s = Math.sin(yaw);
    rot(atF + (lf * c - ll * s) * scale, atL + (lf * s + ll * c) * scale, atY + ly * scale,
        hf * scale, hy * scale, hl * scale, -yaw, 0, roll, rgb, opq(alpha));
  };

  // Crâne du Flétrisseur : 96 pièces. C'est le seul objet RECONNAISSABLE de la famille, donc
  // celui qui doit tenir de près. Tranches emboîtées, jamais de panneau rapporté ; les orbites
  // sont des CREUX (boîte sombre enfoncée + braise au fond), pas des taches peintes.
  const drawWitherSkull = (atF, atL, atY, yaw, scale, jaw, alpha, salt, strobe) => {
    if (alpha <= 1 || scale <= 0.01) return;
    const S = (lf, ll, ly, hf, hy, hl, rgb, a) => skullPieceRoll(atF, atL, atY, yaw, scale, lf, ll, ly, hf, hy, hl, 0, rgb, a);
    const SR = (lf, ll, ly, hf, hy, hl, roll, rgb, a) => skullPieceRoll(atF, atL, atY, yaw, scale, lf, ll, ly, hf, hy, hl, roll, rgb, a);
    // Pièce de mâchoire : la position pivote de +j autour de l'articulation, la boîte suit.
    const J = (pivF, pivH, j, lf, ll, ly, hf, hy, hl, rgb, a) => {
      const rf = lf - pivF, rh = ly - pivH, c = Math.cos(j), s = Math.sin(j);
      SR(pivF + rf * c - rh * s, ll, pivH + rf * s + rh * c, hf, hy, hl, j, rgb, a);
    };

    // boîte crânienne : cinq tranches, de la plus étroite au sommet à la plus large aux tempes
    S(0, 0, 0.60, 0.34, 0.055, 0.30, BONE_HI, alpha);
    S(0, 0, 0.47, 0.40, 0.085, 0.37, BONE_HI, alpha);
    S(0, 0, 0.28, 0.44, 0.115, 0.42, BONE, alpha);
    S(0, 0, 0.06, 0.45, 0.115, 0.43, BONE, alpha);
    S(-0.02, 0, -0.14, 0.40, 0.095, 0.38, BONE_MID, alpha);
    S(-0.40, 0, 0.22, 0.075, 0.30, 0.34, BONE_DARK, alpha);
    for (let i = 0; i < 6; i++) {
      const u = i / 5.0;
      S(0.26 - u * 0.68, 0, 0.63 - u * 0.10 - (u > 0.7 ? (u - 0.7) * 0.5 : 0), 0.055, 0.030, 0.028, BONE_DARK, alpha);
    }
    for (let s = -1; s <= 1; s += 2)
      for (let i = 0; i < 4; i++) {
        const u = i / 3.0;
        S(0.12 - u * 0.42, s * (0.38 - u * 0.05), 0.50 - u * 0.26, 0.055, 0.024, 0.028, BONE_DARK, alpha * 0.92);
      }

    // le front : une arête saillante au-dessus des orbites
    S(0.40, 0, 0.34, 0.075, 0.075, 0.40, BONE_HI, alpha);
    S(0.43, 0, 0.46, 0.055, 0.055, 0.33, BONE_HI, alpha);

    // les orbites : creusées, pas peintes. La braise au fond est le SEUL point surexposé du
    // modèle, donc le point de fixation du regard.
    const burn = 0.70 + 0.30 * hash(salt + strobe * 11, 5);
    for (let s = -1; s <= 1; s += 2) {
      const ol = s * 0.195;
      S(0.40, ol, 0.23, 0.085, 0.115, 0.125, BONE_DEEP, alpha);
      S(0.34, ol, 0.23, 0.055, 0.135, 0.145, BONE_DEEP, alpha);
      S(0.44, ol, 0.375, 0.055, 0.030, 0.145, BONE_HI, alpha);
      S(0.44, ol + s * 0.135, 0.23, 0.055, 0.120, 0.030, BONE, alpha);
      S(0.44, ol, 0.095, 0.055, 0.030, 0.130, BONE_MID, alpha);
      S(0.37, ol, 0.23, 0.040, 0.065 * burn, 0.070 * burn, VIOLET, alpha);
      S(0.39, ol, 0.23, 0.030, 0.035 * burn, 0.038 * burn, PALE, alpha);
    }

    // cavité nasale
    S(0.40, 0, 0.06, 0.075, 0.075, 0.045, BONE_DEEP, alpha);
    S(0.41, 0, 0.14, 0.065, 0.038, 0.022, BONE_DEEP, alpha);
    // pommettes : c'est ce qui fait « crâne » et pas « boîte »
    for (let s = -1; s <= 1; s += 2) {
      S(0.30, s * 0.355, 0.045, 0.125, 0.055, 0.070, BONE, alpha);
      S(0.10, s * 0.395, 0.02, 0.105, 0.045, 0.055, BONE_MID, alpha);
    }
    // maxillaire et dents
    S(0.33, 0, -0.09, 0.135, 0.055, 0.300, BONE, alpha);
    for (let i = 0; i < 7; i++) {
      const lat = (i - 3) * 0.078;
      const len = (i === 3 || i === 2 || i === 4) ? 0.075 : 0.055;
      S(0.34, lat, -0.145 - len * 0.5, 0.045, len * 0.5, 0.032, i % 2 === 0 ? BONE_HI : BONE, alpha);
    }

    // la mâchoire : elle pivote autour de l'articulation, à l'arrière
    {
      const j = jaw * 0.62, pivF = -0.12, pivH = -0.16;
      J(pivF, pivH, j, 0.26, 0, -0.10, 0.165, 0.055, 0.290, BONE, alpha);
      J(pivF, pivH, j, 0.26, 0, -0.165, 0.155, 0.030, 0.270, BONE_MID, alpha);
      J(pivF, pivH, j, 0.405, 0, -0.135, 0.045, 0.075, 0.230, BONE_HI, alpha);
      for (let s = -1; s <= 1; s += 2) {
        J(pivF, pivH, j, 0.26, s * 0.265, -0.105, 0.155, 0.050, 0.035, BONE_MID, alpha);
        J(pivF, pivH, j, 0.02, s * 0.245, -0.02, 0.045, 0.095, 0.040, BONE_DARK, alpha);
      }
      for (let i = 0; i < 6; i++) {
        const lat = (i - 2.5) * 0.082;
        const len = (i === 2 || i === 3) ? 0.070 : 0.050;
        J(pivF, pivH, j, 0.34, lat, -0.040 + len * 0.5, 0.042, len * 0.5, 0.030, i % 2 === 0 ? BONE_HI : BONE, alpha);
      }
      // la gueule : quand elle s'ouvre on voit du NOIR, pas un vide entre deux os
      if (jaw > 0.05) {
        S(0.26, 0, -0.17 - jaw * 0.14, 0.115, 0.075 + jaw * 0.13, 0.215, VOID_INK, alpha);
        S(0.20, 0, -0.17 - jaw * 0.14, 0.065, 0.055 + jaw * 0.10, 0.150, VIOLET, alpha * (0.35 + 0.65 * jaw));
      }
    }

    // les cornes : trois segments recourbés. Le segment pointe vers (-sin ang, cos ang), donc
    // le roulis vaut +ang ; au signe opposé la corne devenait un escalier.
    for (let s = -1; s <= 1; s += 2) {
      let f0 = -0.10, h0 = 0.62, ang = -0.45;
      const lat = s * 0.26, seg3 = [0.30, 0.26, 0.20], rad = [0.070, 0.055, 0.038];
      for (let p = 0; p < 3; p++) {
        const half = seg3[p] * 0.5;
        SR(f0 - Math.sin(ang) * half, lat + s * p * 0.035, h0 + Math.cos(ang) * half,
           rad[p], half, rad[p], ang, p === 0 ? UMBRA : (p === 1 ? PENUMBRA : PURPLE), alpha);
        f0 -= Math.sin(ang) * seg3[p];
        h0 += Math.cos(ang) * seg3[p];
        ang += 0.62;
      }
      S(f0, lat + s * 0.105, h0, 0.028, 0.045, 0.028, ORCHID, alpha);
    }
  };

  const renderRift = (alpha) => {
    const a = Math.round(alpha * smooth(t / 0.03) * (1 - smooth((t - 0.94) / 0.06)));
    if (a <= 4) return;
    const crack = smooth(t / 0.07);
    const open = smooth((t - 0.07) / 0.13);
    const skull = smooth((t - 0.20) / 0.18);
    const breath = clamp01((t - 0.38) / 0.17);
    const inhale = clamp01((t - 0.55) / 0.19);
    const shut = smooth((t - 0.74) / 0.11);
    const scarT = clamp01((t - 0.85) / 0.15);
    const strobe = Math.floor(t * 28);

    const lensH = RIFT_H * (0.35 + 0.65 * crack) * (1 - 0.25 * shut);
    const lensW = RIFT_W * open * (1 - shut);
    const midY = GROUND_Y + RIFT_H * 0.92;

    // 1. L'AMANDE — une faille se dessine comme un BORD, pas comme une surface.
    if (lensH > 0.05) {
      const steps = 26;
      if (lensW > 0.02) {
        for (let i = 0; i < steps; i++) {
          const u = (i + 0.5) / steps, s = (u - 0.5) * 2;
          const prof = Math.sqrt(Math.max(0, 1 - s * s));
          const lat = s * lensW, hgt = lensH * prof;
          if (hgt < 0.04) continue;
          // le fond bouge : sans mouvement interne, un trou noir est un autocollant
          const jig = (hash(salt + strobe * 13, i * 37) - 0.5) * 0.045;
          box(0, lat, midY + jig, 0.055, hgt, lensW / steps * 1.25, VOID_INK, opq(a));
          if (i % 2 === 0) {
            const vh = hgt * (0.25 + 0.45 * hash(salt + 41, i * 53));
            const vy = midY + (hash(salt + 42, i * 59) - 0.5) * hgt;
            box(0.012, lat, vy, 0.040, vh, lensW / steps * 0.8, PENUMBRA, opq(a * 0.75));
          }
        }
      }
      for (let i = 0; i <= steps; i++) {
        const u = i / steps, s = (u - 0.5) * 2;
        const prof = Math.sqrt(Math.max(0, 1 - s * s));
        const lat = s * Math.max(lensW, 0.02), hgt = lensH * prof;
        const flick = 0.72 + 0.28 * hash(salt + strobe * 17, i * 29);
        for (let sgn = -1; sgn <= 1; sgn += 2) {
          const yy = midY + sgn * hgt;
          box(0, lat, yy, 0.062, 0.040, 0.052, VIOLET, opq(a * flick));
          box(0.030, lat, yy, 0.026, 0.024, 0.030, ORCHID, opq(a * flick));
        }
      }
      for (let sgn = -1; sgn <= 1; sgn += 2)
        box(0, sgn * (lensW + 0.03), midY, 0.055, 0.075, 0.055, PALE, opq(a));
      if (open < 0.85) box(0.035, 0, midY, 0.030, lensH * 1.02, 0.022, SPARK, opq(a * (1 - open)));
    }

    // 2. LE CRÂNE
    if (skull > 0.01 && shut < 0.92) {
      const outp = skull * (1 - inhale * 0.92);
      const sc = 0.42 + 1.05 * outp;
      const adv = 0.10 + 1.45 * outp;
      const yaw = Math.sin(t * 3.4 + salt * 0.37) * 0.30 + (1 - outp) * 0.9;
      const bob = Math.sin(t * 7.1) * 0.065 * outp;
      const jaw = breath * (1 - inhale) * 0.55;
      drawWitherSkull(adv, 0, midY + bob, yaw, sc, jaw, a * (0.35 + 0.65 * outp), salt, strobe);
    }

    // 3. LE SOUFFLE — chaque bouffée a sa naissance, donc le souffle COULE
    if (breath > 0.01 && inhale < 0.85) {
      for (let i = 0; i < 54; i++) {
        const h1 = hash(salt + 51, i * 37), h2 = hash(salt + 52, i * 41), h3 = hash(salt + 53, i * 43);
        const age = (breath - h1 * 0.72) / 0.42;
        if (age <= 0 || age >= 1) continue;
        const reach = 0.9 + age * 4.6, spread = 0.10 + age * 0.95;
        const ang = h2 * TAU, rr = spread * (0.25 + 0.75 * h3);
        const s = (0.075 + 0.075 * h2) * (1 - age * 0.40);
        box(1.1 + reach, Math.cos(ang) * rr, midY - 0.35 + Math.sin(ang) * rr - age * 0.5, s, s, s,
            h3 > 0.78 ? ROT_C : (h3 > 0.44 ? ROT_DARK : UMBRA), opq(a * (1 - age) * (1 - inhale)));
      }
      for (let k = 0; k < 3; k++) {
        const age = (breath - (0.12 + k * 0.17)) / 0.70;
        if (age <= 0 || age >= 1) continue;
        const ang = (k - 1) * 0.42, adv = 1.3 + age * 5.4;
        drawWitherSkull(adv, Math.sin(ang) * adv * 0.35,
          midY - 0.25 - age * 0.9 + Math.sin(age * 9 + k) * 0.18,
          ang + age * 2.6, 0.30 * (1 - age * 0.35), 0.42, a * (1 - age) * 0.85, salt + k * 701, strobe);
      }
    }

    // 4. L'INSPIRATION — c'est le RACCOURCISSEMENT qui fait l'aspiration, pas le déplacement
    if (inhale > 0.01 && shut < 0.95) {
      for (let i = 0; i < 22; i++) {
        const h1 = hash(salt + 61, i * 37), h2 = hash(salt + 62, i * 41), h3 = hash(salt + 63, i * 43);
        const age = (inhale - h1 * 0.45) / 0.55;
        if (age <= 0 || age >= 1) continue;
        const ang = h2 * TAU;
        const far = 7.8 * (1 - age);
        const near = Math.max(0.25, far - (1.4 + 2.2 * h3));
        const yy = midY + (h3 - 0.5) * 3.4 * (1 - age * 0.6);
        for (let s = 0; s < 7; s++) {
          const u = (s + 0.5) / 7, d = near + (far - near) * u, ss = 0.055 * (1 - u * 0.55);
          box(Math.cos(ang) * d, Math.sin(ang) * d, yy + (yy - midY) * u * 0.25,
              ss * 1.8, ss, ss * 1.8, u < 0.3 ? ORCHID : (u < 0.7 ? PURPLE : UMBRA), opq(a * (1 - age) * 0.9));
        }
      }
    }

    // 5. L'ONDE DE FERMETURE
    if (shut > 0.02) {
      const r = shut * 6.4, thin = 1 - shut;
      for (let i = 0; i < 40; i++) {
        const ang = i * TAU / 40 + salt * 0.013;
        const rr = r * (1 + 0.10 * Math.sin(ang * 5 + t * 9));
        const hgt = 0.14 + 0.46 * thin;
        box(Math.cos(ang) * rr, Math.sin(ang) * rr, GROUND_Y + hgt * 0.5, 0.085, hgt * 0.5, 0.085,
            i % 3 === 0 ? ORCHID : PURPLE, opq(a * thin));
        if (i % 2 === 0)
          box(Math.cos(ang) * rr * 0.86, Math.sin(ang) * rr * 0.86, GROUND_Y + 0.02, 0.100, SLAB_H, 0.100, UMBRA, opq(a * thin * 0.85));
      }
    }

    // 6. LA CICATRICE
    if (scarT > 0.01) {
      for (let i = 0; i < 24; i++) {
        const h1 = hash(salt + 71, i * 37), h2 = hash(salt + 72, i * 41);
        const yy = midY + (h1 - 0.5) * RIFT_H * 1.7;
        const drop = scarT * scarT * (1.6 + 2.2 * h2);
        const s = 0.045 * (1 - scarT * 0.6);
        box((h2 - 0.5) * 0.22, (h1 - 0.5) * 0.30, yy - drop, s, s * 1.6, s, h2 > 0.6 ? SCAR : ASH, opq(a * (1 - scarT)));
      }
      const sh = RIFT_H * (1 - scarT * 0.85);
      if (sh > 0.08) box(0, 0, midY, 0.035, sh, 0.030, SCAR, opq(a * (1 - scarT * 0.5)));
    }
  };

  // =========================================================================================
  //  III. VOILE D'ÉCLIPSE
  // =========================================================================================

  const renderEclipse = (alpha) => {
    const a = Math.round(alpha * smooth(t / 0.04) * (1 - smooth((t - 0.95) / 0.05)));
    if (a <= 4) return;
    const rise = smooth(t / 0.11);
    const corona = smooth((t - 0.11) / 0.13);
    const veil = smooth((t - 0.24) / 0.18);
    const night = clamp01((t - 0.42) / 0.20);
    const diamond = clamp01((t - 0.62) / 0.12);
    const claw = clamp01((t - 0.74) / 0.14);
    const lift = smooth((t - 0.88) / 0.12);
    const strobe = Math.floor(t * 22);

    const sunY = GROUND_Y + (SUN_Y - GROUND_Y) * rise - lift * 3.0;
    const sunR = SUN_R * (0.30 + 0.70 * rise) * (1 - lift * 0.55);

    // 1. LE SOLEIL NOIR — un VRAI disque (balayage en rangées + demi-corde), pas un empilement
    //    d'anneaux, qui laisse toujours des trous au centre.
    if (sunR > 0.05) {
      const rows = 17;
      for (let r = 0; r < rows; r++) {
        const v = (r + 0.5) / rows, sy = (v - 0.5) * 2;
        const halfChord = Math.sqrt(Math.max(0, 1 - sy * sy)) * sunR;
        if (halfChord < 0.03) continue;
        const yy = sunY + sy * sunR;
        box(0, 0, yy, 0.075, sunR / rows * 1.15, halfChord, VOID_INK, opq(a));
        for (let s = -1; s <= 1; s += 2) {
          const flick = 0.74 + 0.26 * hash(salt + strobe * 9, r * 31 + s);
          box(0.010, s * halfChord, yy, 0.070, sunR / rows * 1.05, 0.055, VIOLET, opq(a * flick));
          box(0.055, s * halfChord, yy, 0.030, sunR / rows * 0.8, 0.030, ORCHID, opq(a * flick));
        }
      }
      // les deux pôles, que le balayage en rangées manque toujours
      for (let s = -1; s <= 1; s += 2)
        box(0.010, 0, sunY + s * sunR, 0.070, 0.055, 0.14, VIOLET, opq(a));
    }

    // 2. LA COURONNE — c'est le rapetissement qui fait la flamme, pas la courbure
    if (corona > 0.01) {
      const fil = 36;
      for (let i = 0; i < fil; i++) {
        const ang = i * TAU / fil;
        const h1 = hash(salt + 81, i * 37), h2 = hash(salt + 82, i * 41);
        const breathe = 0.55 + 0.45 * Math.sin(t * (5 + 6 * h1) + h2 * TAU);
        const len = (0.45 + 1.55 * h1) * corona * breathe * (1 - lift * 0.8);
        if (len < 0.06) continue;
        for (let s = 0; s < 5; s++) {
          const u = (s + 0.5) / 5, d = sunR + len * u;
          const aa = ang + u * u * 0.30 * (h2 - 0.5) * 2;
          const ss = (0.070 - 0.045 * u) * (0.8 + 0.4 * h2);
          const rgb = u < 0.25 ? PALE : (u < 0.55 ? ORCHID : (u < 0.8 ? VIOLET : PURPLE));
          box(0, Math.cos(aa) * d, sunY + Math.sin(aa) * d, ss * 0.9, ss, ss, rgb, opq(a * corona * (1 - u * 0.35)));
        }
      }
    }

    // 3. L'ANNEAU DE DIAMANT — l'instant que tout le monde reconnaît d'une éclipse
    if (diamond > 0.01 && diamond < 0.999) {
      const pop = Math.sin(diamond * Math.PI);
      const ang = -0.75 + salt * 0.0007;
      const px = Math.cos(ang) * sunR, py = Math.sin(ang) * sunR;
      const s0 = 0.085 + 0.19 * pop;
      box(0.075, px, sunY + py, 0.055, s0, s0, SPARK, opq(a));
      box(0.095, px, sunY + py, 0.040, s0 * 0.52, s0 * 0.52, 0xFFFFFF, opq(a));
      for (let k = 0; k < 4; k++) {
        const ba = k * HALF_PI + 0.35;
        const bl = (0.35 + 1.35 * pop) * (k % 2 === 0 ? 1.0 : 0.62);
        for (let s = 0; s < 5; s++) {
          const u = (s + 0.5) / 5, ss = 0.055 * (1 - u * 0.75);
          box(0.070, px + Math.cos(ba) * bl * u, sunY + py + Math.sin(ba) * bl * u,
              0.045, ss, ss, u < 0.4 ? PALE : ORCHID, opq(a * (1 - u * 0.5)));
        }
      }
    }

    // 4. LE VOILE — treillis et non surface pleine : une demi-sphère pleine de 7 blocs de rayon
    //    enfermerait la caméra dans une boîte noire et le joueur ne verrait plus le combat.
    if (veil > 0.01) {
      const close = veil * (1 - lift);
      const maxPhi = HALF_PI * close;
      for (let m = 0; m < 9; m++) {
        const th = m * Math.PI / 9, ct = Math.cos(th), st = Math.sin(th);
        for (let i = 0; i <= 11; i++) {
          const phi = maxPhi * i / 11;
          const rr = DOME_R * Math.sin(phi);
          const yy = GROUND_Y + DOME_R * Math.cos(phi) * (0.55 + 0.45 * close);
          const wob = 1 + 0.045 * Math.sin(phi * 7 + t * 5 + m);
          for (let sgn = -1; sgn <= 1; sgn += 2)
            // ORCHID/PURPLE : ce treillis est la FRONTIERE d'un contrat de position.
            // En UMBRA il disparaissait de nuit, et la regle devenait injouable.
            box(ct * rr * wob * sgn, st * rr * wob * sgn, yy, 0.055, 0.055, 0.055,
                (i % 3 === 0) ? ORCHID : PURPLE, opq(a * close * 0.95));
        }
      }
      for (let p = 1; p <= 5; p++) {
        const phi = maxPhi * p / 5;
        const rr = DOME_R * Math.sin(phi);
        const yy = GROUND_Y + DOME_R * Math.cos(phi) * (0.55 + 0.45 * close);
        if (rr < 0.10) continue;
        const n = 14 + p * 5;
        for (let i = 0; i < n; i++) {
          const ang = i * TAU / n + t * (p % 2 === 0 ? 0.35 : -0.35);
          const flick = 0.70 + 0.30 * hash(salt + strobe * 5, p * 97 + i * 13);
          box(Math.cos(ang) * rr, Math.sin(ang) * rr, yy, 0.048, 0.048, 0.048,
              (p === 5) ? ORCHID : VIOLET, opq(a * close * flick));
        }
      }
      // l'ourlet : le dôme doit se POSER, pas flotter
      if (close > 0.55) {
        const hem = (close - 0.55) / 0.45;
        for (let i = 0; i < 52; i++) {
          const ang = i * TAU / 52;
          const rr = DOME_R * (0.96 + 0.05 * Math.sin(ang * 6 + t * 4));
          box(Math.cos(ang) * rr, Math.sin(ang) * rr, GROUND_Y + 0.02, 0.105, SLAB_H, 0.105,
              i % 4 === 0 ? ORCHID : PURPLE, opq(a * hem));
        }
      }
    }

    // 5. LA NUIT INTÉRIEURE
    if (night > 0.01) {
      for (let i = 0; i < 44; i++) {
        const h1 = hash(salt + 91, i * 37), h2 = hash(salt + 92, i * 41), h3 = hash(salt + 93, i * 43);
        const cycle = fract(t * (0.55 + 0.5 * h1) + h2);
        const ang = h2 * TAU, rr = DOME_R * 0.92 * Math.sqrt(h3);
        const yy = GROUND_Y + 0.1 + (1 - cycle) * 5.4;
        const s = 0.030 + 0.022 * h1;
        box(Math.cos(ang) * rr, Math.sin(ang) * rr, yy, s, s, s,
            h3 > 0.80 ? ORCHID : (h3 > 0.45 ? ASH : UMBRA), opq(a * night * (0.45 + 0.55 * cycle)));
      }
      const spin = t * 1.15;
      for (let k = 0; k < 3; k++) {
        const base = spin + k * (TAU / 3);
        for (let i = 0; i < 16; i++) {
          const u = i / 15, ang = base + (u - 0.5) * 1.45;
          const rr = 2.35 + 0.55 * Math.sin(u * Math.PI);
          const s = 0.085 * Math.sin(u * Math.PI) + 0.030;
          box(Math.cos(ang) * rr, Math.sin(ang) * rr, GROUND_Y + 0.025, s, SLAB_H, s,
              i % 4 === 0 ? ORCHID : VIOLET, opq(a * night));
        }
      }
      for (let i = 0; i < 46; i++) {
        const ang = i * TAU / 46 - spin * 0.4;
        box(Math.cos(ang) * 3.65, Math.sin(ang) * 3.65, GROUND_Y + 0.018, 0.070, SLAB_H, 0.070,
            i % 5 === 0 ? PALE : PURPLE, opq(a * night * 0.9));
      }
    }

    // 6. LA GRIFFURE — largeur en sinus : une lame d'épaisseur constante est un tuyau
    if (claw > 0.01) {
      for (let k = 0; k < 3; k++) {
        const age = (claw - k * 0.13) / 0.62;
        if (age <= 0 || age >= 1) continue;
        const reveal = clamp01(age / 0.42);
        const keep = 1 - smooth((age - 0.45) / 0.55);
        const tilt = -0.55 + k * 0.55, phase = -1.15 + k * 0.22;
        const n = 18, shown = Math.max(2, Math.round(n * reveal));
        for (let i = 0; i < shown; i++) {
          const u = i / (n - 1), ang = phase + u * 2.5;
          const rr = 1.5 + 4.6 * Math.sin(0.22 + u * 2.4);
          const yy = GROUND_Y + 1.1 + Math.sin(u * Math.PI) * 3.0 + tilt * (u - 0.5) * 4.0;
          const w = (0.055 + 0.145 * Math.sin(u * Math.PI)) * keep;
          if (w < 0.012) continue;
          box(Math.cos(ang) * rr, Math.sin(ang) * rr, yy, w, w * 1.7, w, VOID_INK, opq(a * keep));
          box(Math.cos(ang) * rr, Math.sin(ang) * rr, yy + w * 1.4, w * 0.55, w * 0.5, w * 0.55, ORCHID, opq(a * keep));
        }
        if (reveal > 0.05 && reveal < 1) {
          const u = reveal, ang = phase + u * 2.5;
          const rr = 1.5 + 4.6 * Math.sin(0.22 + u * 2.4);
          const yy = GROUND_Y + 1.1 + Math.sin(u * Math.PI) * 3.0 + tilt * (u - 0.5) * 4.0;
          box(Math.cos(ang) * rr, Math.sin(ang) * rr, yy, 0.11, 0.15, 0.11, PALE, opq(a));
        }
      }
    }

    // 7. LA LEVÉE — en lambeaux, et toujours par la GÉOMÉTRIE : ils rapetissent et montent,
    //    l'alpha reste plein.
    if (lift > 0.01) {
      for (let i = 0; i < 30; i++) {
        const h1 = hash(salt + 101, i * 37), h2 = hash(salt + 102, i * 41);
        const ang = h1 * TAU;
        const rr = DOME_R * (0.45 + 0.55 * h2) * (1 - lift * 0.35);
        const up = lift * (3 + 5.5 * h1);
        const s = (0.14 + 0.10 * h2) * (1 - lift);
        if (s < 0.015) continue;
        box(Math.cos(ang) * rr, Math.sin(ang) * rr, GROUND_Y + 0.6 + up, s * 0.5, s * 1.5, s * 0.5,
            h2 > 0.65 ? PURPLE : UMBRA, opq(a));
      }
    }
  };

  const ALPHA = 240;
  if (variant === 1) renderRift(ALPHA);
  else if (variant === 2) renderEclipse(ALPHA);
  else renderGrasp(ALPHA);
  return out;
}

if (typeof module !== 'undefined' && module.exports) module.exports = { buildShadow };
