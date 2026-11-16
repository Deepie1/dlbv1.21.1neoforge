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
        GLASS = 0x7C68A0, MOLTEN = 0xFFB347, SCORCH = 0x2A2036;

  // BOLT_CORE 0,040 et non 0,075 : a l'ancienne valeur la gaine faisait 0,37 bloc de large
  // pour des segments de 0,14 a 0,39 de long — plus large que long, donc un chapelet de cubes.
  // GROUND_Y : rien n'est emis sous le sol.
  const GROUND_Y = -0.42;
  // Palette du marteau : un metal ne se lit que par ses VALEURS — face eclairee, face
  // moyenne, ombre, et une arete plus claire que tout le reste.
  const GOLD_HI = 0xFFF4CB, GOLD = 0xF3C63E, GOLD_MID = 0xC68B1C,
        GOLD_DARK = 0x87560F, GOLD_DEEP = 0x4A2F07, RUNE = 0x9BF2FF, GRIP = 0x47301C;
  // Demi-cote de la zone frappee : 3x3 blocs.
  const ZONE_RADIUS = 6.0;   // rayon de l'aura au sol, en blocs
  // SCORCH est une couleur de BRULURE : a 8 blocs de haut le nuage disparaissait dans le ciel.
  const CLOUD_DARK = 0x3B3457, CLOUD_LIT = 0x6A5CA8;
  const CLOUD_Y = 8.0, CLOUD_R = 3.40, STRIKES = 5;
  // CAGE_HALF a disparu avec la cage : la Surcadence n'a plus qu'un trait vertical.
  const SKY_HEIGHT = 22.0, BOLT_CORE = 0.040, CROWN_RADIUS = 5.10,
        CHAIN_LINKS = 6;

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

  // Pose une piece du marteau. Les coordonnees sont LOCALES a l'objet : le lacet s'applique
  // a la position ET a la boite, donc le marteau tourne d'un seul tenant.
  const piece = (lf, ll, ly, hf, hy, hl, yaw, roll, baseY, scale, rgb, alpha) => {
    if (alpha <= 1) return;
    const c = Math.cos(yaw), s = Math.sin(yaw);
    // Lacet de la boite = oppose de celui des positions (cf. le commentaire cote Java).
    // L'apercu fixe dx = 1, dz = 0, donc l'orientation du repere vaut zero ici.
    const boxYaw = -yaw;
    rot((lf * c - ll * s) * scale, (lf * s + ll * c) * scale, baseY + ly * scale,
           hf * scale, hy * scale, hl * scale, boxYaw, 0, roll, rgb, opq(alpha));
  };

  // Mjollnir : tete massive, manche court, detail partout. Tranches emboitees pour la
  // silhouette, details rentres dans le volume, manche en prisme octogonal.
  const drawHammer = (baseY, yaw, scale, alpha, charge, salt, t) => {
    if (alpha <= 1) return;

    // semelle, la plus etroite
    piece(0, 0, -0.57, 0.79, 0.075, 0.41, yaw, 0, baseY, scale, GOLD_DARK, alpha);
    // chanfrein bas
    piece(0, 0, -0.455, 0.89, 0.065, 0.495, yaw, 0, baseY, scale, GOLD_MID, alpha);
    // masse principale
    piece(0, 0, 0, 0.96, 0.395, 0.56, yaw, 0, baseY, scale, GOLD, alpha);
    // chanfrein haut
    piece(0, 0, 0.455, 0.89, 0.065, 0.495, yaw, 0, baseY, scale, GOLD, alpha);
    // dessus, face eclairee
    piece(0, 0, 0.57, 0.79, 0.075, 0.41, yaw, 0, baseY, scale, GOLD_HI, alpha);
    // nervure faitiere
    piece(0, 0, 0.65, 0.56, 0.045, 0.1, yaw, 0, baseY, scale, GOLD_HI, alpha);
    // clou de faitiere
    piece(-0.34, 0, 0.69, 0.06, 0.055, 0.06, yaw, 0, baseY, scale, GOLD_HI, alpha);
    piece(0.34, 0, 0.69, 0.06, 0.055, 0.06, yaw, 0, baseY, scale, GOLD_HI, alpha);
    // patin
    piece(0, -0.23, -0.635, 0.7, 0.04, 0.07, yaw, 0, baseY, scale, GOLD_DEEP, alpha);
    piece(0, 0.23, -0.635, 0.7, 0.04, 0.07, yaw, 0, baseY, scale, GOLD_DEEP, alpha);
    // panneau en retrait
    piece(-0.9408, 0, 0, 0.035, 0.265, 0.385, yaw, 0, baseY, scale, GOLD_MID, alpha);
    // traverse haute du cadre
    piece(-0.9792, 0, 0.355, 0.05, 0.05, 0.47, yaw, 0, baseY, scale, GOLD_HI, alpha);
    // traverse basse du cadre
    piece(-0.9792, 0, -0.355, 0.05, 0.05, 0.47, yaw, 0, baseY, scale, GOLD_MID, alpha);
    // montant du cadre
    piece(-0.9792, -0.47, 0, 0.05, 0.355, 0.05, yaw, 0, baseY, scale, GOLD_MID, alpha);
    piece(-0.9792, 0.47, 0, 0.05, 0.355, 0.05, yaw, 0, baseY, scale, GOLD_HI, alpha);
    // rivet
    piece(-0.9936, -0.375, -0.255, 0.045, 0.05, 0.05, yaw, 0, baseY, scale, GOLD_HI, alpha);
    piece(-0.9936, -0.375, 0.255, 0.045, 0.05, 0.05, yaw, 0, baseY, scale, GOLD_HI, alpha);
    piece(-0.9936, 0.375, -0.255, 0.045, 0.05, 0.05, yaw, 0, baseY, scale, GOLD_HI, alpha);
    piece(-0.9936, 0.375, 0.255, 0.045, 0.05, 0.05, yaw, 0, baseY, scale, GOLD_HI, alpha);
    // bossage de frappe
    piece(-1.008, 0, 0, 0.06, 0.135, 0.135, yaw, 0, baseY, scale, GOLD_HI, alpha);
    // pointe du bossage
    piece(-1.0416, 0, 0, 0.03, 0.07, 0.07, yaw, 0, baseY, scale, GOLD, alpha);
    // cannelure
    piece(-0.9696, -0.25, 0, 0.045, 0.165, 0.035, yaw, 0, baseY, scale, GOLD_DARK, alpha);
    piece(-0.9696, 0.25, 0, 0.045, 0.165, 0.035, yaw, 0, baseY, scale, GOLD_DARK, alpha);
    // panneau en retrait
    piece(0.9408, 0, 0, 0.035, 0.265, 0.385, yaw, 0, baseY, scale, GOLD_MID, alpha);
    // traverse haute du cadre
    piece(0.9792, 0, 0.355, 0.05, 0.05, 0.47, yaw, 0, baseY, scale, GOLD_HI, alpha);
    // traverse basse du cadre
    piece(0.9792, 0, -0.355, 0.05, 0.05, 0.47, yaw, 0, baseY, scale, GOLD_MID, alpha);
    // montant du cadre
    piece(0.9792, -0.47, 0, 0.05, 0.355, 0.05, yaw, 0, baseY, scale, GOLD_MID, alpha);
    piece(0.9792, 0.47, 0, 0.05, 0.355, 0.05, yaw, 0, baseY, scale, GOLD_HI, alpha);
    // rivet
    piece(0.9936, -0.375, -0.255, 0.045, 0.05, 0.05, yaw, 0, baseY, scale, GOLD_HI, alpha);
    piece(0.9936, -0.375, 0.255, 0.045, 0.05, 0.05, yaw, 0, baseY, scale, GOLD_HI, alpha);
    piece(0.9936, 0.375, -0.255, 0.045, 0.05, 0.05, yaw, 0, baseY, scale, GOLD_HI, alpha);
    piece(0.9936, 0.375, 0.255, 0.045, 0.05, 0.05, yaw, 0, baseY, scale, GOLD_HI, alpha);
    // bossage de frappe
    piece(1.008, 0, 0, 0.06, 0.135, 0.135, yaw, 0, baseY, scale, GOLD_HI, alpha);
    // pointe du bossage
    piece(1.0416, 0, 0, 0.03, 0.07, 0.07, yaw, 0, baseY, scale, GOLD, alpha);
    // cannelure
    piece(0.9696, -0.25, 0, 0.045, 0.165, 0.035, yaw, 0, baseY, scale, GOLD_DARK, alpha);
    piece(0.9696, 0.25, 0, 0.045, 0.165, 0.035, yaw, 0, baseY, scale, GOLD_DARK, alpha);
    // joue creusee
    piece(0, -0.546, 0, 0.67, 0.265, 0.035, yaw, 0, baseY, scale, GOLD_MID, alpha);
    // filet haut
    piece(0, -0.5572, 0.3, 0.71, 0.035, 0.025, yaw, 0, baseY, scale, GOLD_HI, alpha);
    // filet bas
    piece(0, -0.5572, -0.3, 0.71, 0.035, 0.025, yaw, 0, baseY, scale, GOLD_DARK, alpha);
    // filet vertical
    piece(-0.71, -0.5572, 0, 0.035, 0.3, 0.025, yaw, 0, baseY, scale, GOLD_DARK, alpha);
    piece(0.71, -0.5572, 0, 0.035, 0.3, 0.025, yaw, 0, baseY, scale, GOLD_HI, alpha);
    // rune, fut
    piece(0, -0.5768, 0, 0.055, 0.195, 0.025, yaw, 0, baseY, scale, RUNE, alpha * 0.95);
    // rune, traverse
    piece(0, -0.5768, 0.035, 0.195, 0.055, 0.025, yaw, 0, baseY, scale, RUNE, alpha * 0.95);
    // rune, point
    piece(-0.235, -0.5768, 0.035, 0.05, 0.05, 0.025, yaw, 0, baseY, scale, RUNE, alpha * 0.8);
    // poincon
    piece(-0.43, -0.5684, -0.145, 0.045, 0.045, 0.022, yaw, 0, baseY, scale, GOLD_HI, alpha * 0.9);
    // rune, point
    piece(0.235, -0.5768, 0.035, 0.05, 0.05, 0.025, yaw, 0, baseY, scale, RUNE, alpha * 0.8);
    // poincon
    piece(0.43, -0.5684, -0.145, 0.045, 0.045, 0.022, yaw, 0, baseY, scale, GOLD_HI, alpha * 0.9);
    // joue creusee
    piece(0, 0.546, 0, 0.67, 0.265, 0.035, yaw, 0, baseY, scale, GOLD_MID, alpha);
    // filet haut
    piece(0, 0.5572, 0.3, 0.71, 0.035, 0.025, yaw, 0, baseY, scale, GOLD_HI, alpha);
    // filet bas
    piece(0, 0.5572, -0.3, 0.71, 0.035, 0.025, yaw, 0, baseY, scale, GOLD_DARK, alpha);
    // filet vertical
    piece(-0.71, 0.5572, 0, 0.035, 0.3, 0.025, yaw, 0, baseY, scale, GOLD_DARK, alpha);
    piece(0.71, 0.5572, 0, 0.035, 0.3, 0.025, yaw, 0, baseY, scale, GOLD_HI, alpha);
    // rune, fut
    piece(0, 0.5768, 0, 0.055, 0.195, 0.025, yaw, 0, baseY, scale, RUNE, alpha * 0.95);
    // rune, traverse
    piece(0, 0.5768, 0.035, 0.195, 0.055, 0.025, yaw, 0, baseY, scale, RUNE, alpha * 0.95);
    // rune, point
    piece(-0.235, 0.5768, 0.035, 0.05, 0.05, 0.025, yaw, 0, baseY, scale, RUNE, alpha * 0.8);
    // poincon
    piece(-0.43, 0.5684, -0.145, 0.045, 0.045, 0.022, yaw, 0, baseY, scale, GOLD_HI, alpha * 0.9);
    // rune, point
    piece(0.235, 0.5768, 0.035, 0.05, 0.05, 0.025, yaw, 0, baseY, scale, RUNE, alpha * 0.8);
    // poincon
    piece(0.43, 0.5684, -0.145, 0.045, 0.045, 0.022, yaw, 0, baseY, scale, GOLD_HI, alpha * 0.9);
    // evasement
    piece(0, 0, 0.76, 0.3, 0.13, 0.3, yaw, 0, baseY, scale, GOLD, alpha);
    // collier
    piece(0, 0, 0.905, 0.235, 0.055, 0.235, yaw, 0, baseY, scale, GOLD_HI, alpha);
    // rivet de jonction
    piece(-0.305, 0, 0.76, 0.045, 0.05, 0.05, yaw, 0, baseY, scale, GOLD_HI, alpha);
    piece(0, -0.305, 0.76, 0.05, 0.05, 0.045, yaw, 0, baseY, scale, GOLD_MID, alpha);
    piece(0.305, 0, 0.76, 0.045, 0.05, 0.05, yaw, 0, baseY, scale, GOLD_HI, alpha);
    piece(0, 0.305, 0.76, 0.05, 0.05, 0.045, yaw, 0, baseY, scale, GOLD_MID, alpha);
    // manche, pan droit
    piece(0, 0, 1.175, 0.13, 0.235, 0.13, yaw, 0, baseY, scale, GOLD_MID, alpha);
    // manche, pan incline
    piece(0, 0, 1.175, 0.09, 0.2338, 0.09, yaw + 0.7854, 0, baseY, scale, GOLD, alpha);
    // manche, pan droit
    piece(0, 0, 1.76, 0.138, 0.39, 0.138, yaw, 0, baseY, scale, GRIP, alpha);
    // manche, pan incline
    piece(0, 0, 1.76, 0.096, 0.3881, 0.096, yaw + 0.7854, 0, baseY, scale, GOLD_DEEP, alpha);
    // manche, pan droit
    piece(0, 0, 2.265, 0.13, 0.145, 0.13, yaw, 0, baseY, scale, GOLD_MID, alpha);
    // manche, pan incline
    piece(0, 0, 2.265, 0.09, 0.1443, 0.09, yaw + 0.7854, 0, baseY, scale, GOLD, alpha);
    // tour de laniere
    piece(0, 0, 1.43, 0.15, 0.038, 0.15, yaw, 0, baseY, scale, GRIP, alpha);
    piece(0, 0, 1.595, 0.15, 0.038, 0.15, yaw, 0, baseY, scale, GOLD_DEEP, alpha);
    piece(0, 0, 1.76, 0.15, 0.038, 0.15, yaw, 0, baseY, scale, GRIP, alpha);
    piece(0, 0, 1.925, 0.15, 0.038, 0.15, yaw, 0, baseY, scale, GOLD_DEEP, alpha);
    piece(0, 0, 2.09, 0.15, 0.038, 0.15, yaw, 0, baseY, scale, GRIP, alpha);
    // pommeau
    piece(0, 0, 2.47, 0.23, 0.115, 0.23, yaw, 0, baseY, scale, GOLD, alpha);
    // couronne du pommeau
    piece(0, 0, 2.6, 0.165, 0.055, 0.165, yaw, 0, baseY, scale, GOLD_HI, alpha);
    // gemme du pommeau
    piece(-0.235, 0, 2.47, 0.03, 0.07, 0.07, yaw, 0, baseY, scale, RUNE, alpha * 0.9);
    piece(0, -0.235, 2.47, 0.07, 0.07, 0.03, yaw, 0, baseY, scale, RUNE, alpha * 0.9);
    piece(0.235, 0, 2.47, 0.03, 0.07, 0.07, yaw, 0, baseY, scale, RUNE, alpha * 0.9);
    piece(0, 0.235, 2.47, 0.07, 0.07, 0.03, yaw, 0, baseY, scale, RUNE, alpha * 0.9);
    // anneau de suspension
    piece(0.205, 0, 2.735, 0.052, 0.052, 0.052, yaw, 0, baseY, scale, GOLD_MID, alpha);
    piece(0.145, 0.145, 2.735, 0.052, 0.052, 0.052, yaw, 0, baseY, scale, GOLD_MID, alpha);
    piece(0, 0.205, 2.735, 0.052, 0.052, 0.052, yaw, 0, baseY, scale, GOLD_MID, alpha);
    piece(-0.145, 0.145, 2.735, 0.052, 0.052, 0.052, yaw, 0, baseY, scale, GOLD_MID, alpha);
    piece(-0.205, 0, 2.735, 0.052, 0.052, 0.052, yaw, 0, baseY, scale, GOLD_MID, alpha);
    piece(-0.145, -0.145, 2.735, 0.052, 0.052, 0.052, yaw, 0, baseY, scale, GOLD_MID, alpha);
    piece(-0, -0.205, 2.735, 0.052, 0.052, 0.052, yaw, 0, baseY, scale, GOLD_MID, alpha);
    piece(0.145, -0.145, 2.735, 0.052, 0.052, 0.052, yaw, 0, baseY, scale, GOLD_MID, alpha);

    // ---- la charge : des arcs courent sur la tete ----
    if (charge > 0.05) {
      const arcs = 2 + Math.floor(charge * 4.0);
      for (let k = 0; k < arcs; k++) {
        const sd = salt * 17 + k * 311 + Math.floor(t * 90.0);
        const a0 = hash(sd, 7) * TAU, a1 = hash(sd, 11) * TAU;
        const c0 = Math.cos(yaw + a0), s0 = Math.sin(yaw + a0);
        const c1 = Math.cos(yaw + a1), s1 = Math.sin(yaw + a1);
        const r0 = 0.70 * scale, r1 = 0.70 * scale;
        drawBolt(c0 * r0, s0 * r0, baseY + (hash(sd, 13) - 0.5) * 1.1 * scale,
                 c1 * r1, s1 * r1, baseY + (hash(sd, 17) - 0.5) * 1.1 * scale,
                 sd, 0.28 * scale, 1.0, 0.75, alpha * charge, 6, false);
      }
    }
  };

  // Le sol sous tension : un disque de 6 blocs de rayon, SANS contour. Des foyers tires au
  // hasard, chacun avec sa periode et sa phase, donc une zone asymetrique dont le bord
  // s'eteint par perte de densite au lieu d'etre trace.
  const shockZone = (t, live, alpha, salt) => {
    if (live <= 0.01 || alpha <= 1) return;
    const R = ZONE_RADIUS, y = GROUND_Y + 0.02;

    const SITES = 58;
    for (let i = 0; i < SITES; i++) {
      const sd = salt * 131 + i * 7919;
      const rr = R * Math.pow(hash(sd, 3), 0.72);
      const ang = hash(sd, 5) * TAU;
      const f = Math.cos(ang) * rr, l = Math.sin(ang) * rr;

      const period = 0.075 + 0.235 * hash(sd, 7);
      const cyc = fract(t / period + hash(sd, 11));
      const duty = 0.17 + 0.17 * hash(sd, 13);
      if (cyc > duty) continue;

      const life = cyc / duty;
      const edge = 1 - 0.55 * (rr / R);
      const g = (1 - life * life) * edge;
      if (g < 0.05) continue;

      const bs = sd + Math.floor(t / period) * 7717;
      const hgt = 0.30 + 0.95 * hash(bs, 17) * (0.55 + 0.45 * edge);
      const tipF = f + (hash(bs, 19) - 0.5) * 0.55;
      const tipL = l + (hash(bs, 23) - 0.5) * 0.55;
      drawBolt(f, l, y, tipF, tipL, y + hgt, bs, 0.16, 1.0, 0.42,
               alpha * live * g * 0.95, 4, false);

      box(f, l, y + 0.015, 0.085, 0.028, 0.085, 0xFFFFFF, opq(alpha * live * g));
      box(f + (hash(bs, 29) - 0.5) * 0.42, l + (hash(bs, 31) - 0.5) * 0.42, y + 0.012,
          0.062, 0.022, 0.062, 0x4FC8FF, opq(alpha * live * g * 0.75));
    }

    const frame = Math.floor(t * 34.0);
    for (let i = 0; i < 22; i++) {
      const sd = salt * 31 + i * 193 + frame * 53;
      const rr = R * Math.pow(hash(sd, 19), 0.65);
      const ang = hash(sd, 23) * TAU;
      const g = (0.45 + 0.55 * hash(sd, 37)) * (1 - 0.60 * (rr / R));
      box(Math.cos(ang) * rr, Math.sin(ang) * rr, y + hash(sd, 29) * 0.20,
          0.040, 0.040, 0.040, hash(sd, 31) > 0.5 ? 0xFFFFFF : 0x7FE0FF,
          opq(alpha * live * g));
    }
  };

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
      // Un arc rampant est un contournement de SURFACE : on le plaque au sol au lieu de le
      // laisser plonger sous terre, ou il disparait purement et simplement.
      let cyy = ey + off2 * (hlen > Math.abs(sy) ? 0.42 : 1.0);
      if (cyy < GROUND_Y) cyy = GROUND_Y;
      if (sheath) {
        seg(pf, pl, py, cf, cl, cyy, BOLT_CORE * 2.05 * thick, mix(VIOLET, ARC, 0.5), opq(alpha * 0.80));
        seg(pf, pl, py, cf, cl, cyy, BOLT_CORE * 1.40 * thick, NEON, opq(alpha * 0.92));
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
        drawBolt(0, 0, SKY_HEIGHT, 0, 0, 0, salt * 31 + 7, 1.55, reveal, thick, a * persist, 44, true);
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
          // Spheres pleine centree a 0,30 pour un rayon vertical de 1,4 : la moitie basse etait
          // enterree. Le sol la tronque — un impact donne une coupole, pas une bille.
          const fy = 0.30 + yy;
          if (fy < GROUND_Y) continue;
          box(Math.cos(ang) * pr, Math.sin(ang) * pr, fy,
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
                 0.55 + 0.45 * keep, a * (0.55 + 0.45 * keep), 12, true);
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
  //  II. MARTEAU DE THOR — 250 ticks, la frappe puis le sol sous tension
  // =========================================================================================
  // Le marteau est un OBJET, pas un effet : corps, plaques de frappe, rivets, rainures, runes
  // serties, laniere et pommeau. L'impact visuel est a t = 0,16, soit le tick 40 — exactement
  // quand le serveur applique les degats.
  if (variant === 1) {
    const a = Math.round(255 * (1 - smooth((t - 0.96) / 0.04)));
    if (a <= 4) return out;

    const IMPACT = 0.16, SCALE = 1.30, REST = GROUND_Y + 0.64 * SCALE;
    const appear = smooth(t / 0.05);
    const fall = Math.max(0, Math.min(1, (t - 0.105) / (IMPACT - 0.105)));
    const hit = smooth((t - IMPACT) / 0.012);
    const gone = smooth((t - IMPACT - 0.02) / 0.065);
    const live = smooth((t - IMPACT) / 0.015) * (1 - smooth((t - 0.93) / 0.05));

    if (gone < 0.999) {
      const ease = fall * fall * fall;               // chute en cube : brutale a la fin
      const sky = GROUND_Y + 30.0;   // appelé à 30 blocs, avalés en cube
      const hover = Math.sin(t * 7) * 0.14 * (1 - fall);
      let baseY = sky + hover - (sky - REST) * ease;
      baseY += gone * 2.40;
      const yaw = 0.9 + t * 1.25 + fall * 2.6;
      const charge = Math.min(1, appear * (0.25 + 0.75 * fall) + hit * 0.8);
      drawHammer(baseY, yaw, SCALE, a * appear * (1 - gone), charge, salt, t);

      if (fall < 0.98) {
        for (let k = 0; k < 3; k++) {
          const sd = salt * 13 + k * 409 + Math.floor(t * 70);
          if (hash(sd, 3) < 0.35) continue;
          const ang = hash(sd, 5) * TAU, rr = 1.4 + 1.5 * hash(sd, 7);
          drawBolt(Math.cos(ang) * rr, Math.sin(ang) * rr, baseY + 1.6 + hash(sd, 11) * 1.4,
                   0, 0, baseY + 0.3, sd, 0.42, 1.0, 0.70, a * appear * 0.85, 7, false);
        }
      }
    }

    if (hit > 0.01 && live > 0) {
      const age = Math.max(0, Math.min(1, (t - IMPACT) / 0.09));
      const ring = 0.8 + age * (ZONE_RADIUS - 0.8), ra = (1 - age) * a;
      if (ra > 2) {
        for (let i = 0; i < 40; i++) {
          const ang = i * TAU / 40, wob = 1 + 0.10 * hash(salt + 3, i * 17);
          box(Math.cos(ang) * ring * wob, Math.sin(ang) * ring * wob,
              GROUND_Y + 0.05 + age * 0.30,
              0.085, 0.055 + 0.10 * (1 - age), 0.085,
              age < 0.4 ? 0xFFFFFF : 0xFFE89A, opq(ra * 0.9));
        }
        const sparks = Math.floor(14 * (1 - age));
        for (let i = 0; i < sparks; i++) {
          const sd = salt * 7 + i * 131;
          const ang = hash(sd, 3) * TAU, rr = hash(sd, 5) * 1.3;
          box(Math.cos(ang) * rr, Math.sin(ang) * rr,
              GROUND_Y + age * (1.4 + 2.2 * hash(sd, 7)),
              0.055, 0.075, 0.055, 0xFFF4CB, opq(ra));
        }
      }
    }

    // ni dalle, ni damier, ni contour : le sol ne se delimite pas
    if (live > 0.01) {
      shockZone(t, live, a, salt);
    }
    return out;
  }

  // =========================================================================================
  //  III. APPEL DE L'ORAGE — 170 ticks
  // =========================================================================================
  {
    const a = Math.round(255 * smooth(t / 0.05) * (1 - smooth((t - 0.93) / 0.07)));
    if (a <= 4) return out;

    const strobe = Math.floor(t * 30);
    const form = smooth(t / 0.16);
    const charge = smooth((t - 0.62) / 0.14);
    const blast = smooth((t - 0.78) / 0.045);
    const after = smooth((t - 0.84) / 0.16);

    // 1. LE NUAGE.
    {
      const blobs = 112;
      const spin = t * 0.42;
      // Le ventre s'illumine juste avant chaque depart d'eclair : c'est ce qui annonce le coup.
      let belly = 0;
      for (let k = 0; k < STRIKES; k++) {
        const ag = (t - (0.20 + k * 0.086)) / 0.078;
        if (ag > -0.45 && ag < 0.55) belly = Math.max(belly, 1 - Math.abs(ag) / 0.55);
      }
      belly = Math.max(belly, charge * 0.85);
      const squeeze = 1 - 0.30 * charge;
      for (let i = 0; i < blobs; i++) {
        const h1 = hash(salt + 90, i * 37);
        const h2 = hash(salt + 91, i * 53);
        const h3 = hash(salt + 92, i * 71);
        const rr = CLOUD_R * squeeze * (0.16 + 0.84 * Math.sqrt(h1));
        const ang = h2 * TAU + spin * (0.55 + 0.85 * h1);
        const born = clamp01((form - h3 * 0.55) / 0.45);
        if (born <= 0.01) continue;
        const rIn = rr * (1 + 2.40 * (1 - born));
        const yy = CLOUD_Y + (h3 - 0.5) * 0.95 * squeeze;
        const s = (0.25 + 0.30 * h1) * born;
        const glow = hash(salt + strobe * 17, i * 29);
        let rgb = glow > 0.90 ? NEON : (glow > 0.62 ? CLOUD_LIT : CLOUD_DARK);
        const low = clamp01((CLOUD_Y - yy) / 0.70);
        if (belly > 0.02) rgb = mix(rgb, NEON, belly * low * 0.80);
        if (blast > 0.05) rgb = mix(rgb, CORE, blast * 0.65);
        box(Math.cos(ang) * rIn, Math.sin(ang) * rIn, yy, s, s * 0.60, s,
            rgb, opq(a * born * (0.85 + 0.15 * glow)));
      }
      const arcs = 3 + Math.floor(charge * 4);
      for (let k = 0; k < arcs; k++) {
        if (hash(salt + strobe * 23, k * 41) < 0.45) continue;
        const a0 = hash(salt + 93, k * 59) * TAU;
        const a1 = a0 + 1.4 + hash(salt + 94, k * 61) * 2.0;
        const r0 = CLOUD_R * 0.80 * squeeze;
        drawBolt(Math.cos(a0) * r0, Math.sin(a0) * r0, CLOUD_Y + 0.10,
                 Math.cos(a1) * r0, Math.sin(a1) * r0, CLOUD_Y - 0.20,
                 salt * 73 + k * 509 + strobe * 11, 0.40, 1.0, 0.50,
                 a * form * (0.55 + 0.45 * charge), 10, true);
      }
    }

    // 2. LA MITRAILLE.
    for (let k = 0; k < STRIKES; k++) {
      const t0 = 0.20 + k * 0.086;
      const age = (t - t0) / 0.078;
      if (age <= 0 || age >= 1) continue;
      const ang = hash(salt + 200, k * 61) * TAU;
      const rad = 0.55 + 2.35 * hash(salt + 201, k * 67);
      const tf = Math.cos(ang) * rad;
      const tl = Math.sin(ang) * rad;
      const reveal = clamp01(age / 0.30);
      const keep = 1 - smooth((age - 0.42) / 0.58);
      drawBolt(tf * 0.30, tl * 0.30, CLOUD_Y - 0.60, tf, tl, GROUND_Y,
               salt * 97 + k * 811 + strobe * 5, 0.62, reveal, 1.10, a * keep, 16, true);
      // Deux ramifications par eclair : un trait nu sur 8 blocs fait pauvre.
      for (let b = 0; b < 2; b++) {
        const bu = 0.30 + 0.38 * hash(salt + 205, k * 23 + b * 29);
        const by = CLOUD_Y - 0.60 - (CLOUD_Y - 0.60 - GROUND_Y) * bu;
        const ba = hash(salt + 206, k * 31 + b * 37) * TAU;
        // Longueur minimale relevee : trop courte, une ramification redevient un chapelet.
        const bl = 0.95 + 1.05 * hash(salt + 207, k * 41 + b * 43);
        drawBolt(tf, tl, by, tf + Math.cos(ba) * bl, tl + Math.sin(ba) * bl,
                 Math.max(GROUND_Y, by - bl * 0.85),
                 salt * 101 + k * 211 + b * 307 + strobe * 3, 0.42,
                 clamp01((reveal - 0.45) / 0.55), 0.60, a * keep * 0.80, 5, true);
      }
      if (reveal < 0.98) continue;
      const pop = 1 - smooth((age - 0.30) / 0.40);
      if (pop > 0.02) {
        for (let s = 0; s < 10; s++) {
          const sa = s * TAU / 10;
          const sr = (0.18 + 0.80 * (1 - pop)) * (0.70 + 0.60 * hash(salt + 202, s * 17));
          const sz = 0.085 * pop;
          box(tf + Math.cos(sa) * sr, tl + Math.sin(sa) * sr, GROUND_Y + 0.06,
              sz, sz, sz, mix(HOT, CORE, pop), opq(a * pop));
        }
      }
      for (let s = 0; s < 5; s++) {
        const sa = hash(salt + 203, k * 31 + s * 13) * TAU;
        const sr = 0.22 + 0.45 * hash(salt + 204, k * 37 + s * 19);
        const sz = 0.070;
        box(tf + Math.cos(sa) * sr, tl + Math.sin(sa) * sr, GROUND_Y + 0.02,
            sz, sz * 0.45, sz, SCORCH, opq(a * 0.85));
      }
    }

    // 3. LA COLONNE D'APPEL.
    if (charge > 0.02 && blast < 0.80) {
      const rings = 5;
      for (let r = 0; r < rings; r++) {
        const u = (r + 1) / rings;
        const lift = charge * 2.60 * u;
        if (lift < 0.05) continue;
        const n = 8 + r * 2;
        const rad = 0.85 * (1 - 0.55 * u) * (1 + 0.35 * charge);
        for (let i = 0; i < n; i++) {
          const ang = i * TAU / n + t * 2.1 * (r % 2 === 0 ? 1 : -1);
          const sz = 0.075 * (1 - 0.45 * u);
          box(Math.cos(ang) * rad, Math.sin(ang) * rad, GROUND_Y + lift, sz, sz, sz,
              mix(VIOLET, NEON, u), opq(a * charge * (1 - 0.35 * u)));
        }
      }
    }

    // 4. LA DECHARGE : un faisceau de sept traits, pas un tronc.
    if (blast > 0.01) {
      const keep = 1 - smooth((t - 0.90) / 0.10);
      for (let s = 0; s < 7; s++) {
        const ha = hash(salt + 300, s * 83);
        const ang = s * TAU / 7 + ha * 0.55;
        const off = s === 0 ? 0 : 0.17 + 0.15 * ha;
        drawBolt(Math.cos(ang) * off * 0.55, Math.sin(ang) * off * 0.55, CLOUD_Y - 0.30,
                 Math.cos(ang) * off, Math.sin(ang) * off, GROUND_Y,
                 salt * 131 + s * 907 + strobe * 7, 0.32, blast,
                 s === 0 ? 1.70 : 1.30, a * keep, 11, true);
      }
      const pop = blast * (1 - smooth((t - 0.86) / 0.12));
      for (let s = 0; s < 26; s++) {
        const h1 = hash(salt + 301, s * 29);
        const h2 = hash(salt + 302, s * 31);
        const ang = h1 * TAU;
        const rr = (0.20 + 1.30 * h2) * pop;
        const yy = GROUND_Y + 0.05 + h2 * 1.15 * pop;
        const sz = (0.13 - 0.06 * h2) * pop;
        if (sz <= 0.005) continue;
        box(Math.cos(ang) * rr, Math.sin(ang) * rr, yy, sz, sz, sz,
            mix(CORE, HOT, h1), opq(a * pop));
      }
    }

    // 5. L'ONDE DE CHOC : la portee reellement touchee par l'AoE.
    if (after > 0.01) {
      const rw = 1.10 + after * 8.90;
      const thin = 1 - smooth((after - 0.50) / 0.50);
      const n = 76;
      for (let i = 0; i < n; i++) {
        const ang = i * TAU / n;
        const jit = (hash(salt + 400, i * 13) - 0.5) * 0.60;
        const r = rw + jit;
        const sz = (0.15 + 0.11 * hash(salt + 401, i * 19)) * thin;
        if (sz <= 0.006) continue;
        box(Math.cos(ang) * r, Math.sin(ang) * r, GROUND_Y + 0.05, sz, sz * 0.60, sz,
            mix(VIOLET, NEON, thin), opq(a * thin));
        if ((i % 3) === 0 && thin > 0.25) {
          const hgt = 0.22 + 0.40 * hash(salt + 402, i * 23);
          box(Math.cos(ang) * (r - 0.12), Math.sin(ang) * (r - 0.12),
              GROUND_Y + 0.05 + hgt * thin, sz * 0.75, hgt * thin, sz * 0.75,
              NEON, opq(a * thin * 0.90));
        }
      }
    }
    return out;
  }
}

if (typeof module !== 'undefined' && module.exports) module.exports = { buildThunder };
