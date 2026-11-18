/* ===========================================================================================
 * chaos_build.js — MIROIR JS de deepluckyblock.client.ChaosSignatureVfx
 *                  + de deepluckyblock.client.VoidPixelManager
 *
 * Même rôle que void_build.js, blood_build.js et les autres : rejouer la géométrie Java dans
 * un navigateur pour la relire sans lancer Minecraft. Toute divergence avec le .java est un
 * BOGUE du miroir.
 *
 * Repère : le Java travaille en (f = axe attaquant→cible, lat = perpendiculaire, up =
 * hauteur) puis projette avec dx/dz. L'aperçu fixe dx = 1, dz = 0, l'origine à l'impact, donc
 *      x = f        y = up        z = lat
 *
 * ------------------------------------------------------------------------------------------
 * CE QUI A CHANGÉ, ET QUI CHANGE TOUT : IL N'Y A PLUS QUE DES PIXELS 3D
 * ------------------------------------------------------------------------------------------
 * La version précédente peignait les armatures en violets unis (emitVoxelCube) et réservait
 * les Pixels 3D aux étincelles. Ce n'est plus le cas : le fichier Java n'appelle PLUS JAMAIS
 * emitVoxelCube. Tout — perles, parois, treillis, débris, cicatrice — passe par
 * VoidPixelManager, donc par le core shader, donc par :
 *
 *      un CORPS en niveau de gris   (32 crans, aucun ton coloré, jamais)
 *      des ARÊTES colorées          (8 modes, teinte qui défile)
 *
 * Chaque cube sort donc d'ici avec, en plus des champs habituels :
 *      pixel : true        toujours vrai désormais
 *      pmode : 0..7        RAINBOW TINTED MONO SPECTRUM PULSE DUAL STROBE EMBER
 *      phue  : 0..1        teinte imposée, utilisée par TINTED / DUAL / PULSE / EMBER
 *      phase : 0..1        phase spatiale, calculée comme côté CPU dans le Java
 *      pfade : 0..1        opacité de l'arête (le champ `a`, lui, porte celle du CORPS,
 *                          qui est plus faible : c'est la formule exacte du .fsh)
 *      gray  : 0..31       le cran de gris, pour le diagnostic au banc
 *
 * ------------------------------------------------------------------------------------------
 * LES PIXELS LIBRES SONT REJOUÉS DEPUIS LE TICK 0
 * ------------------------------------------------------------------------------------------
 * Les cubes semés par spawnPixel ne vivent PAS dans la trame courante : ils naissent,
 * dérivent, tombent et meurent sur plusieurs dizaines de ticks. Un aperçu qui n'appellerait
 * buildChaos(t) que pour l'instant t ne les verrait jamais. On repart donc du tick 0 à chaque
 * image en intégrant la même physique que VoidPixelManager — position, PUIS frottement 0,94,
 * PUIS gravité, dans cet ordre.
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
    /* L'instant de la détonation du Big Bang. SEIZE pour cent, et plus quarante-deux :
       l'aspiration est une inspiration, pas le sujet. */
    BANG: 0.16,
  };
})();

/* Les huit modes d'arête, mêmes numéros que VoidPixelManager. */
const M_RAINBOW = 0, M_TINTED = 1, M_MONO = 2, M_SPECTRUM = 3,
      M_PULSE = 4, M_DUAL = 5, M_STROBE = 6, M_EMBER = 7;

/* L'échelle de gris. Neuf crans nommés sur trente-deux ; le cran 0 n'est jamais employé,
   il se confond avec le fond de nuit. */
const G_MASK = 2, G_DEEP = 6, G_BODY = 11, G_MID = 16,
      G_FRAME = 21, G_GLASS = 25, G_FLASH = 29, G_CORE = 31;

/* Les teintes d'arête, en position sur la roue. */
const H_CYAN = 0.52, H_BLUE = 0.62, H_VIOLET = 0.74,
      H_MAGENTA = 0.86, H_ROSE = 0.93, H_EMBER_HUE = 0.07;

/* Rampe lissee, derivee nulle aux deux bornes -- miroir de ChaosSignatureVfx.smooth(). */
function sstep(x, from, to) {
  if (to <= from) return x >= to ? 1 : 0;
  const u = x < from ? 0 : x > to ? 1 : (x - from) / (to - from);
  return u * u * (3 - 2 * u);
}

function buildChaos(variant, t, salt = 4421) {
  const out = [];
  if (t <= 0 || t >= 1) return out;

  const TAU = 6.2831855;
  const PI = 3.14159265;
  const GOLD = 2.3999632;
  const clamp01 = (v) => (v < 0 ? 0 : v > 1 ? 1 : v);

  /* smooth(x, from, to) du Java : rampe lissée entre DEUX BORNES, et non le smooth(x) à une
     seule variable des autres familles. Se tromper ici décale toute la chronologie. */
  const smooth = (x, from, to) => {
    if (to <= from) return x >= to ? 1 : 0;
    const u = clamp01((x - from) / (to - from));
    return u * u * (3 - 2 * u);
  };
  /* hash(a, b) : Math.imul et |0 obligatoires, sinon JS part en flottant 53 bits et le
     miroir diverge du Java dès le premier dépassement. */
  const hash = (a, b) => {
    let h = (Math.imul(a, 374761393) + Math.imul(b, 668265263)) | 0;
    h = Math.imul(h ^ (h >>> 13), 1274126177) | 0;
    return ((h ^ (h >>> 16)) & 0x7FFFFF) / 8388607.0;
  };
  const lerp = (d, a, b) => a + d * (b - a);
  const sin = Math.sin, cos = Math.cos, sqrt = Math.sqrt;

  const DUR = CHAOS_SHAPE.DURATION[variant];
  const GROUND = CHAOS_SHAPE.GROUND_FALLBACK;
  /* ENVELOPPE D'ENTREE ET DE SORTIE — miroir de ChaosSignatureVfx.FADE_IN / FADE_OUT.
     L'entree est courte (6 %) parce qu'une signature d'impact doit repondre au coup ;
     la sortie est presque trois fois plus longue (16 %) parce que l'oeil pardonne une
     arrivee rapide et jamais un arrachage. smoothstep aux deux bouts : derivee nulle,
     donc ni depart ni arret visibles, seulement une acceleration puis un ralenti. */
  const FADE_IN = 0.06, FADE_OUT = 0.16;
  const env = sstep(t, 0.0, FADE_IN) * (1.0 - sstep(t, 1.0 - FADE_OUT, 1.0));
  const a = env;
  /* Ticks restants : la seule borne qui garantisse qu'un pixel ne survive pas a sa
     signature. Miroir de ChaosSignatureVfx.envRemain. */
  const envRemain = Math.max(0, Math.round((1.0 - t) * DUR));

  /* ---- LE POINT DE SORTIE UNIQUE ---------------------------------------------------------
     Mirroir de ChaosSignatureVfx.put() + de la formule de couleur du .fsh. Le corps reçoit
     un gris et SON alpha (plus faible) ; l'arête reçoit la teinte et l'alpha plein. */
  /* L'enveloppe en vigueur pour les prochaines emissions. Elle vaut `env` pour la
     geometrie de la signature et 1 pour les pixels libres, qui n'en dependent plus une
     fois nes -- exactement comme ChaosSignatureVfx.envAlpha, que VoidPixelManager
     n'applique jamais aux pixels deja en vol. */
  let putEnv = env;
  function put(f, up, lat, hx, hy, hz, spin, mode, gray, hue, alpha) {
    alpha *= putEnv;
    if (alpha <= 0.004) return;
    const g = gray < 0 ? 0 : gray > 31 ? 31 : gray;
    const gn = g / 31;
    const lum = Math.round((0.055 + 0.80 * gn) * 255);
    let raw = (f * 0.17 + up * 0.23 + lat * 0.13) % 1.0;
    if (raw < 0) raw += 1.0;
    out.push({
      x: f, y: up, z: lat, hx: hx, hy: hy, hz: hz,
      rgb: (lum << 16) | (lum << 8) | lum,
      a: Math.round(alpha * (0.58 + 0.30 * gn) * 255),
      yaw: spin, pitch: 0, roll: 0,
      pixel: true, pmode: mode, phue: hue, phase: raw, pfade: alpha, gray: g,
    });
  }
  const cube = (f, up, lat, half, spin, mode, gray, hue, alpha) =>
    put(f, up, lat, half, half, half, spin, mode, gray, hue, alpha);

  /* ---- LE VIVIER DE PIXELS LIBRES --------------------------------------------------------
     Rejeu déterministe depuis le tick 0, physique identique à VoidPixelManager.onClientTick. */
  const live = [];
  function spawn(x, y, z, vx, vy, vz, size, life, mode, hue, grav, gray) {
    /* Un pixel ne a 95 % de la choregraphie avec quarante ticks de vie reste seul en
       l'air une seconde apres que tout le reste a disparu. On raccourcit sa vie dans la
       meme proportion que l'enveloppe : miroir exact de ChaosSignatureVfx.pixel(). */
    if (env <= 0.02 || envRemain < 12) return;
    life = Math.min(life, envRemain);
    live.push({ x, y, z, vx, vy, vz, size, life, age: 0, mode, hue, grav, gray });
  }
  function stepPixels() {
    for (let i = live.length - 1; i >= 0; i--) {
      const p = live[i];
      p.age++;
      if (p.age >= p.life) { live.splice(i, 1); continue; }
      p.x += p.vx; p.y += p.vy; p.z += p.vz;
      p.vx *= 0.94; p.vy *= 0.94; p.vz *= 0.94;
      p.vy -= 0.0135 * p.grav;
    }
  }
  function flushPixels() {
    putEnv = 1.0;                 /* un pixel en vol ignore l'enveloppe de sa signature */
    for (const p of live) {
      const l = p.age / p.life;
      /* Fondu de rien a rien, miroir exact de VoidPixelManager. */
      const fade = sstep(l, 0.0, 0.08) * (1.0 - sstep(l, 0.70, 1.0));
      const grow = l < 0.10 ? l / 0.10 : 1.0;
      const h = p.size * 0.5 * grow;
      if (h < 0.002 || fade <= 0.01) continue;
      cube(p.x, p.y, p.z, h, p.age * 0.07, p.mode, p.gray, p.hue, fade);
    }
    putEnv = env;
  }

  /* ========================================================================================
   *  I. BOUCLE KALÉIDOSCOPIQUE — un GROUPE DIÉDRAL d'ordre six
   * ====================================================================================== */
  function kaleido(tt, emitPixels) {
    const YC = 1.05, SECTORS = 6, WEDGE = PI / 6, CELL = PI / 3;
    /* NEUF ÉCLATS DE CINQ CUBES, et non quarante perles isolées. Mesuré au banc :
       trente-quatre perles indépendantes copiées douze fois donnent bien une figure
       rigoureusement symétrique — et elle se lit comme un confetti. L'œil ne vérifie pas
       une symétrie sur des points : il lui faut une FORME dont il puisse suivre le
       contour et reconnaître le retournement. */
    const SHARDS = 9, PER = 5;
    const open = smooth(tt, 0.01, 0.26);
    const radius = 0.42 + 2.42 * open;
    const spin = tt * 2.05;
    const chaos = smooth(tt, 0.46, 0.62) * (1 - smooth(tt, 0.70, 0.80));
    const relock = smooth(tt, 0.78, 0.83) * (1 - smooth(tt, 0.86, 0.93));

    if (!emitPixels) {
      for (let sh = 0; sh < SHARDS; sh++) {
        const band = 0.16 + 0.78 * hash(salt + sh, 1);
        const aC = 0.055 + (WEDGE - 0.11) * hash(salt + sh, 2);
        const dirR = (hash(salt + sh, 3) - 0.5) * 0.40;
        const dirA = (hash(salt + sh, 4) - 0.5) * 0.17;
        const size = 0.036 + 0.030 * hash(salt + sh, 5);
        const k1 = 1 + (sh % 3), k2 = 1 + ((sh * 7) % 4);
        const wobR = 0.070 * sin(TAU * (k1 * tt + hash(salt + sh, 6)));
        const wobA = 0.080 * sin(TAU * (k2 * tt + hash(salt + sh, 7)));

        let mode, hue, gray;
        const kind = (sh + salt) % 4;
        if (kind === 0) { mode = M_DUAL; hue = H_CYAN; gray = G_FRAME; }
        else if (kind === 1) { mode = M_RAINBOW; hue = 0.0; gray = G_BODY; }
        else if (kind === 2) { mode = M_PULSE; hue = H_VIOLET; gray = G_MID; }
        else { mode = M_TINTED; hue = H_MAGENTA; gray = G_DEEP; }
        if (chaos > 0.45 && ((sh * 5 + salt) % 3) === 0) { mode = M_STROBE; gray = G_GLASS; }

        for (let c = 0; c < PER; c++) {
          const u = c / (PER - 1) - 0.5;
          const r0 = Math.max(0.08, Math.min(1.0, band + dirR * u + wobR));
          const a0 = Math.max(0.012, Math.min(WEDGE - 0.012, aC + dirA * u + wobA));

          for (let sct = 0; sct < SECTORS; sct++) {
            for (let m = 0; m < 2; m++) {
              /* LA RÉFLEXION : m = 0 l'éclat, m = 1 son image dans le miroir porté par
                 l'axe à WEDGE. Les deux pavent la cellule de 60°, six cellules font le
                 tour — groupe diédral d'ordre six, douze copies par motif. */
              let local = (m === 0) ? a0 : (CELL - a0);
              let rr = r0;
              if (chaos > 0.001) {
                const cid = sh * 97 + sct * 13 + m * 7;
                local += chaos * 0.26 * (hash(salt + cid, 11) - 0.5);
                rr += chaos * 0.22 * (hash(salt + cid, 12) - 0.5);
              }
              const ang = spin + sct * CELL + local;
              const rad = rr * radius;
              /* LA GALETTE EST PLATE : vingt centimètres d'épaisseur suffisaient à
                 brouiller la symétrie dès que la vue s'écartait de la verticale. */
              const up = YC + 0.045 * (1 - rr) * (1 - rr);
              const grow = clamp01(open * 1.5 - rr * 0.30);
              cube(cos(ang) * rad, up, sin(ang) * rad, size * grow, ang, mode,
                   relock > 0.35 ? G_FLASH : gray, hue, a * (0.82 + 0.18 * relock) * grow);
            }
          }
        }
      }

      /* LES PAROIS DE MIROIR — six fentes de pliage, six axes de réflexion. C'est la
         pièce qui transforme « motif symétrique » en « instrument d'optique ». */
      for (let w = 0; w < 12; w++) {
        const fold = (w % 2) === 0;
        const ang = spin + w * WEDGE;
        const steps = 30;
        for (let k = 1; k <= steps; k++) {
          const rr = k / steps;
          if (chaos > 0.25 && hash(salt + w * 31, k * 17) < chaos * 0.55) continue;
          const rad = rr * radius * 1.02;
          cube(cos(ang) * rad, YC + 0.045 * (1 - rr) * (1 - rr), sin(ang) * rad,
               fold ? 0.040 : 0.026, ang, fold ? M_DUAL : M_MONO,
               fold ? G_GLASS : G_FRAME, fold ? H_BLUE : 0.0,
               a * (fold ? 0.98 : 0.80) * open);
        }
      }

      /* LES ANNEAUX CONCENTRIQUES — la lecture radiale du motif. */
      for (let ring = 0; ring < 3; ring++) {
        const rr = 0.34 + ring * 0.27;
        const marks = 34 + ring * 14;
        for (let k = 0; k < marks; k++) {
          if (chaos > 0.3 && hash(salt + 400 + ring, k) < chaos * 0.6) continue;
          const ang = spin * (ring % 2 === 0 ? 1.0 : -0.65) + k / marks * TAU;
          const rad = rr * radius;
          cube(cos(ang) * rad, YC + 0.045 * (1 - rr) * (1 - rr), sin(ang) * rad,
               0.019, ang, M_MONO, G_MID, 0.0, a * 0.62 * open);
        }
      }

      /* LA CERCLURE — CLAIRE et non masquante : un cercle presque noir sur fond de nuit
         ne ferme rien du tout. */
      const rim0 = 86;
      for (let k = 0; k < rim0; k++) {
        const ang = spin * 0.8 + k / rim0 * TAU;
        const rad = radius * 1.07;
        cube(cos(ang) * rad, YC, sin(ang) * rad, 0.036, ang, M_DUAL, G_GLASS, H_ROSE,
             a * 0.95 * open);
      }

      if (GROUND > -9.0) {
        for (let k = 0; k < 44; k++) {
          const ang = -spin * 0.5 + k / 44 * TAU;
          const rad = radius * (0.5 + 0.55 * hash(salt + 77, k));
          put(cos(ang) * rad, GROUND + 0.03, sin(ang) * rad,
              0.040, 0.016, 0.040, 0, M_MONO, G_MASK, 0.0, a * 0.55 * open);
        }
      }
      return;
    }

    /* --- naissances de pixels libres pour ce tick --- */
    const rim = 86;
    const tick = Math.round(tt * DUR);
    if (chaos > 0.30 && ((salt + tick) % 3) === 0) {
      const k = (salt + tick * 7) % rim;
      const ang = spin * 0.8 + k / rim * TAU;
      const rad = radius * 1.07;
      spawn(cos(ang) * rad, YC, sin(ang) * rad,
            cos(ang) * 0.055, 0.030, sin(ang) * 0.055, 0.085, 26, M_STROBE, 0.0, 0.22, G_GLASS);
    }
    if (relock > 0.5 && ((salt + tick) % 2) === 0) {
      const ang = hash(salt, tick) * TAU;
      const rad = radius * 0.7;
      spawn(cos(ang) * rad, YC, sin(ang) * rad, 0, 0.085, 0, 0.07, 20, M_MONO, 0.0, 0.10, G_FLASH);
    }
  }

  /* ========================================================================================
   *  II. SPHÈRE D'ENTROPIE — un treillis parfait, traversé par un front de désordre
   * ====================================================================================== */
  function entropy(tt, emitPixels) {
    const YC = 1.15, LAT = 16;
    /* Rayon final ramené de 1,82 à 1,56 : à 1,82 les cellules sont espacées de 34 cm
       pour une largeur de 17 — donc un pointillé, et un pointillé ne se lit jamais
       comme une surface ordonnée. */
    const radius = 0.38 + 1.18 * smooth(tt, 0.01, 0.24);
    const S = clamp01((tt - 0.05) / 0.66);
    const spin = tt * 1.15;

    for (let i = 0; i < LAT; i++) {
      const th = (i + 0.5) / LAT * PI;
      const sinTh = sin(th), cosTh = cos(th);
      const ring = Math.max(4, Math.round(LAT * 2.15 * sinTh));
      for (let k = 0; k < ring; k++) {
        const ph = k / ring * TAU + spin + i * 0.21;
        const ux = sinTh * cos(ph), uy = cosTh, uz = sinTh * sin(ph);
        const cid = i * 131 + k * 7;
        /* LE FRONT : il balaie l'axe f, avec un bord déchiqueté par le hachage. */
        const thr = 0.62 * (1 - (ux + 1) * 0.5) + 0.38 * hash(salt + cid, 3);
        const d = clamp01((S - thr) / 0.30);
        const off = 0.62 * d * d;
        const jx = (hash(salt + cid, 4) - 0.5) * off;
        const jy = (hash(salt + cid, 5) - 0.5) * off;
        const jz = (hash(salt + cid, 6) - 0.5) * off;

        if (d >= 0.995) {
          if (emitPixels && ((cid + Math.round(tt * DUR)) % 23) === 0) {
            spawn(ux * radius + jx, YC + uy * radius + jy, uz * radius + jz,
                  ux * 0.022, 0.012, uz * 0.022, 0.075, 30, M_SPECTRUM, 0.0, 0.34,
                  Math.floor(hash(salt + cid, 7) * 31));
          }
          continue;
        }
        if (emitPixels) continue;

        const size = 0.085 * (1 + 1.05 * d * (hash(salt + cid, 8) - 0.45));
        /* LE GRIS : du cran 16 pour tout le monde, vers un tirage uniforme sur 32.
           C'est la ligne qui porte le sujet — l'histogramme s'aplatit sous les yeux. */
        const gray = Math.round(lerp(d, 6, 15 + hash(salt + cid, 9) * 16));
        let mode;
        if (d < 0.02) mode = M_MONO;
        else if (d < 0.30) mode = M_RAINBOW;
        else if (d < 0.62) mode = M_SPECTRUM;
        else mode = M_STROBE;
        cube(ux * radius + jx, YC + uy * radius + jy, uz * radius + jz, size,
             ph + d * 2.4, mode, gray, H_CYAN, a * (1 - 0.25 * d));
      }
    }
    if (emitPixels) return;

    /* LES TROIS GRANDS CERCLES : la mémoire de la forme initiale. Ils cèdent en dernier. */
    for (let c = 0; c < 3; c++) {
      const seg = 44;
      for (let k = 0; k < seg; k++) {
        const ang = k / seg * TAU + spin * (1 + c * 0.3);
        let ux, uy, uz;
        if (c === 0) { ux = cos(ang); uy = sin(ang); uz = 0; }
        else if (c === 1) { ux = cos(ang); uy = 0; uz = sin(ang); }
        else { ux = 0; uy = cos(ang); uz = sin(ang); }
        if (S > 0.55 + 0.42 * hash(salt + 600 + c * 29, k)) continue;
        const rr = radius * 1.015;
        cube(ux * rr, YC + uy * rr, uz * rr, 0.034, ang, M_DUAL, G_GLASS, H_BLUE, a * 0.95);
      }
    }

    /* LE NOYAU : toujours au spectre complet, le désordre y est déjà total. */
    const coreR = 0.10 + 0.40 * S;
    for (let v = 0; v < 6; v++) {
      const ux = (v === 0 ? 1 : v === 1 ? -1 : 0);
      const uy = (v === 2 ? 1 : v === 3 ? -1 : 0);
      const uz = (v === 4 ? 1 : v === 5 ? -1 : 0);
      for (let k = 0; k <= 6; k++) {
        const u = k / 6, rr = coreR * u;
        cube(ux * rr, YC + uy * rr, uz * rr, 0.030 + 0.030 * (1 - u), tt * 6.0,
             M_SPECTRUM, Math.round(lerp(u, 31, 12)), 0.0, a);
      }
    }

    /* LE FANTÔME : le treillis d'origine réapparaît une seconde, pour qu'on sache ce
       qui a été perdu. */
    const ghost = smooth(tt, 0.88, 0.91) * (1 - smooth(tt, 0.93, 0.97));
    if (ghost > 0.01) {
      for (let i = 0; i < LAT; i += 2) {
        const th = (i + 0.5) / LAT * PI;
        const ring = Math.max(4, Math.round(LAT * 2.15 * sin(th)));
        for (let k = 0; k < ring; k += 2) {
          const ph = k / ring * TAU + spin + i * 0.21;
          const ux = sin(th) * cos(ph), uy = cos(th), uz = sin(th) * sin(ph);
          cube(ux * radius, YC + uy * radius, uz * radius, 0.060, 0,
               M_MONO, G_MID, 0.0, a * ghost * 0.55);
        }
      }
    }

    if (GROUND > -9.0) {
      for (let band = 0; band < 2; band++) {
        const marks = band === 0 ? 30 : 20;
        const rr = radius * (band === 0 ? 1.10 : 0.62);
        for (let m = 0; m < marks; m++) {
          if (hash(salt + 907 + band * 211, m * 29) < S) continue;
          const ang = m / marks * TAU - spin * (band === 0 ? 0.5 : -0.8);
          put(cos(ang) * rr, GROUND + 0.03, sin(ang) * rr, 0.060, 0.018, 0.060, 0,
              M_MONO, band === 0 ? G_FRAME : G_BODY, 0.0, a * 0.62);
        }
      }
    }
  }

  /* ========================================================================================
   *  III. CICATRICE DU BIG BANG — un point, puis un univers, puis une marque
   * ====================================================================================== */
  function bigbang(tt, emitPixels) {
    const YC = 1.20, BANG = CHAOS_SHAPE.BANG, DEBRIS = 170, TRAIL = 5, NODES = 14;

    /* 1. LA SINGULARITÉ (0 → 16 %) */
    if (!emitPixels && tt < BANG + 0.04) {
      const pull = clamp01(tt / BANG);
      const fade = 1 - smooth(tt, BANG, BANG + 0.04);
      for (let j = 0; j < 46; j++) {
        const u = 1 - pull * pull * (0.55 + 0.45 * hash(salt + j, 1));
        const y0 = 1 - 2 * (j + 0.5) / 46;
        const rr = sqrt(Math.max(0, 1 - y0 * y0));
        const phi = j * GOLD + pull * 5.0;
        const rad = 0.5 + 2.6 * u;
        cube(cos(phi) * rr * rad, YC + y0 * rad, sin(phi) * rr * rad,
             0.040 + 0.030 * (1 - u), phi, M_PULSE, G_MID, H_BLUE, a * fade);
        for (let k = 1; k <= 2; k++) {
          const rk = rad + k * 0.16;
          cube(cos(phi) * rr * rk, YC + y0 * rk, sin(phi) * rr * rk,
               0.026 - k * 0.006, phi, M_TINTED, G_DEEP, H_BLUE, a * fade * (0.55 - k * 0.16));
        }
      }
      cube(0, YC, 0, 0.05 + 0.10 * pull, 0, M_MONO, Math.round(lerp(pull, 14, 31)), 0.0, a);
      if (pull > 0.55) {
        const cr = 0.72 * (1 - pull) + 0.08;
        for (let e = 0; e < 12; e++) {
          const ang = e / 12 * TAU + tt * 9.0;
          cube(cos(ang) * cr, YC + sin(ang * 2) * cr * 0.5, sin(ang) * cr,
               0.030, ang, M_DUAL, G_FRAME, H_ROSE, a * fade);
        }
      }
    }

    /* 2. LA BOULE DE FEU (16 → 24 %) */
    const flash = smooth(tt, BANG, BANG + 0.035) * (1 - smooth(tt, BANG + 0.05, 0.24));
    if (!emitPixels && flash > 0.01) {
      const fr = 0.18 + 1.5 * smooth(tt, BANG, 0.24);
      for (let j = 0; j < 90; j++) {
        const y0 = 1 - 2 * (j + 0.5) / 90;
        const rr = sqrt(Math.max(0, 1 - y0 * y0));
        const phi = j * GOLD;
        cube(cos(phi) * rr * fr, YC + y0 * fr, sin(phi) * rr * fr,
             0.075 * flash + 0.020, phi, M_MONO, G_CORE, 0.0, a * flash);
      }
    }

    /* 3. LA COQUILLE EN EXPANSION (18 % → fin) */
    if (tt > BANG) {
      const wave = sqrt(clamp01((tt - BANG) / (1 - BANG)));
      const R = 4.8 * wave;
      const web = smooth(tt, 0.58, 0.86);
      const dim = 1 - smooth(tt, 0.80, 1.0) * 0.75;

      if (!emitPixels) {
        for (let j = 0; j < DEBRIS; j++) {
          const y0 = 1 - 2 * (j + 0.5) / DEBRIS;
          const rr0 = sqrt(Math.max(0, 1 - y0 * y0));
          const phi = j * GOLD;
          let ux = cos(phi) * rr0, uy = y0, uz = sin(phi) * rr0;

          if (web > 0.001) {
            const n = j % NODES;
            const ny0 = 1 - 2 * (n + 0.5) / NODES;
            const nrr = sqrt(Math.max(0, 1 - ny0 * ny0));
            const nphi = n * GOLD * 3.0 + 0.7;
            ux = lerp(web * 0.72, ux, cos(nphi) * nrr);
            uy = lerp(web * 0.72, uy, ny0);
            uz = lerp(web * 0.72, uz, sin(nphi) * nrr);
          }
          const thick = 1 - 0.22 * hash(salt + j, 2);
          const heat = clamp01(1.6 - wave * 1.9 - 0.3 * hash(salt + j, 3));
          /* Bande SPECTRUM resserrée : avec 0,26 la quasi-totalité de la coquille
             tombait dans la roue complète et l'élément perdait son identité froide. */
          const mode = heat > 0.55 ? M_MONO : (heat > 0.38 ? M_SPECTRUM : M_RAINBOW);
          const gray = Math.round(lerp(heat, 9 + hash(salt + j, 4) * 13, 31));

          /* LA TRAÎNÉE RADIALE : elle est DERRIÈRE, donc à un rayon plus PETIT. Les cent
             cinquante traînées pointent toutes vers le même point : c'est l'image du
             big bang, et c'est ce qui manquait à la version précédente. */
          for (let k = 0; k <= TRAIL; k++) {
            /* Premier réglage mesuré au banc : pas de 0,070, opacité 0,62, corps au gris 6
               — les traînées étaient là, et INVISIBLES. Or elles sont la seule chose qui
               distingue « big bang » de « nuage de points qui grandit ». */
            const rk = R * thick * (1 - k * 0.085);
            if (rk < 0.05) break;
            const sz = (0.052 + 0.030 * hash(salt + j, 5)) * (1 - k * 0.13);
            const alp = a * dim * (k === 0 ? 1.0 : 0.84 - k * 0.11);
            cube(ux * rk, YC + uy * rk, uz * rk, sz, phi + k * 0.3,
                 k === 0 ? mode : M_TINTED,
                 k === 0 ? gray : Math.max(4, gray - k * 3), H_VIOLET, alp);
          }

          /* LE PONT vers le nœud : c'est lui qui fait le FILAMENT, donc le réseau. */
          if (web > 0.25 && (j % 2) === 0) {
            const n = j % NODES;
            const ny0 = 1 - 2 * (n + 0.5) / NODES;
            const nrr = sqrt(Math.max(0, 1 - ny0 * ny0));
            const nphi = n * GOLD * 3.0 + 0.7;
            for (let b = 1; b <= 2; b++) {
              const u = b / 3;
              const bx = lerp(u, ux, cos(nphi) * nrr);
              const by = lerp(u, uy, ny0);
              const bz = lerp(u, uz, sin(nphi) * nrr);
              const rk = R * thick * 0.97;
              cube(bx * rk, YC + by * rk, bz * rk, 0.026, 0, M_TINTED, G_MASK, H_MAGENTA,
                   a * dim * web * 0.75);
            }
          }
        }

        /* L'ONDE DE CHOC : un anneau presque noir, qui MASQUE et donne l'échelle. */
        const ringN = 76, ringR = R * 1.16;
        for (let k = 0; k < ringN; k++) {
          const ang = k / ringN * TAU;
          const h = 0.30 * (1 - wave);
          put(cos(ang) * ringR, YC - 0.30, sin(ang) * ringR, 0.11, 0.040 + h, 0.11, ang,
              M_DUAL, G_MASK, H_ROSE, a * dim * (1 - wave * 0.45));
        }
      } else {
        const tick = Math.round(tt * DUR);
        if (((salt + tick) % 2) === 0) {
          const j = (salt * 7 + tick * 13) % DEBRIS;
          const y0 = 1 - 2 * (j + 0.5) / DEBRIS;
          const rr0 = sqrt(Math.max(0, 1 - y0 * y0));
          const phi = j * GOLD;
          const ux = cos(phi) * rr0, uz = sin(phi) * rr0;
          spawn(ux * R, YC + y0 * R, uz * R, ux * 0.075, y0 * 0.075, uz * 0.075,
                0.090, 34, M_SPECTRUM, 0.0, 0.18, Math.floor(hash(salt + j, 9) * 31));
        }
      }
    }

    /* 4. LA CICATRICE (80 % → fin) */
    const scar = smooth(tt, 0.78, 0.88);
    if (scar > 0.01 && GROUND > -9.0) {
      if (!emitPixels) {
        for (let m = 0; m < 42; m++) {
          const ang = hash(salt + 331, m) * TAU;
          const rad = 0.5 + 4.3 * hash(salt + 332, m);
          const flick = 0.45 + 0.55 * hash(salt + 333, m + Math.round(tt * 20));
          put(cos(ang) * rad, GROUND + 0.04, sin(ang) * rad, 0.075, 0.024, 0.075, ang,
              M_EMBER, Math.round(lerp(flick, 4, 18)), H_EMBER_HUE, a * scar * flick);
        }
      } else {
        const tick = Math.round(tt * DUR);
        if (((salt + tick) % 5) === 0) {
          const m = tick % 42;
          const ang = hash(salt + 331, m) * TAU;
          const rad = 0.5 + 4.3 * hash(salt + 332, m);
          spawn(cos(ang) * rad, GROUND + 0.10, sin(ang) * rad, 0, 0.026, 0,
                0.065, 30, M_EMBER, H_EMBER_HUE, -0.10, G_DEEP);
        }
      }
    }
  }

  const FN = [kaleido, entropy, bigbang];
  const fn = FN[variant] || kaleido;

  /* --- LE REJEU. On repasse tous les ticks écoulés pour que les pixels libres existent. */
  const nowTick = Math.min(DUR - 1, Math.floor(t * DUR));
  for (let tick = 0; tick <= nowTick; tick++) {
    fn(tick / DUR, true);
    stepPixels();
  }
  flushPixels();

  /* --- LA TRAME COURANTE. */
  fn(t, false);

  /* ======================================================================================
     T401 — LES COUPS. Miroir exact de ChaosSignatureVfx.renderStrikes().

     Ce que cette couche repare : les trois signatures se deploient AUTOUR d'un point sans
     jamais le toucher, si bien qu'on voyait un decor somptueux et un mob qui perdait des
     coeurs sans cause visible. Sur chaque temps ou le serveur inflige un degat — et
     seulement sur ceux-la — une lance de cubes fond sur le centre, puis eclate en
     couronne. Les nombres ci-dessous sont les ticks de applyKaleidoGameplay /
     applyEntropySphereGameplay / applyBigBangGameplay, pas une approximation graphique.
     ====================================================================================== */
  const BEATS = [[30, 48, 66, 84, 102, 120],
                 [22, 38, 54, 70, 86, 102, 118, 134],
                 [84]][variant] || [];
  const STRIKE_WIND = 9, STRIKE_BURST = 8;
  const aim = variant === 2 ? 1.20 : (variant === 1 ? 1.15 : 1.05);
  /* UN COUP SE MESURE A SA FIGURE : la cicatrice du big bang fait cinq blocs de rayon,
     un eclat d'un bloc quatre-vingts y disparait -- verifie au banc. */
  const blast = variant === 2 ? 2.30 : (variant === 1 ? 1.90 : 1.0);
  const nowT = t * DUR;
  for (let b = 0; b < BEATS.length; b++) {
    const since = nowT - BEATS[b];
    if (since < -STRIKE_WIND || since > STRIKE_BURST) continue;
    const ang = b * 2.39996 + salt * 0.137;
    const ux = cos(ang), uz = sin(ang);
    const px = -uz, pz = ux;
    if (since < 0) {
      /* LA DESCENTE : trois brins, pas une file. Mesure au banc avant correction : une
         file de huit cubes pesait dix-sept cubes contre mille cent soixante-quatorze de
         kaleidoscope. Le coup existait deja et ne se voyait pas -- c'est la toute la
         difference entre « il n'y a pas d'attaque » et « on ne la voit pas ». */
      const k = 1.0 + since / STRIKE_WIND;
      const ease = k * k;
      const head = 3.60 - 3.25 * ease;
      for (let s = -1; s <= 1; s++) {
        const off = s * 0.17;
        for (let i = 0; i < 16; i++) {
          const r = head + i * 0.17;
          if (r > 3.90) continue;
          const fade = (0.98 - i * 0.045) * clamp01(k * 2.4);
          if (fade <= 0.02) continue;
          const h = (0.150 - i * 0.0062) * (0.72 + 0.52 * ease) * (s === 0 ? 1.0 : 0.74);
          put(ux * r + px * off, aim + 0.46 * (1.0 - ease) + i * 0.030, uz * r + pz * off,
              h, h, h, ang + t * 3.1 + s,
              (i & 1) === 0 ? M_RAINBOW : M_SPECTRUM,
              i === 0 ? G_FLASH : (i < 4 ? G_BODY : G_DEEP), 0.0, a * fade);
        }
      }
      /* LA POINTE : la masse du coup. */
      for (let i = 0; i < 5; i++) {
        const r = head - i * 0.11;
        const h = 0.205 - i * 0.022;
        put(ux * r, aim + 0.46 * (1.0 - ease), uz * r, h, h, h, ang * 1.7 + t * 5.0,
            M_SPECTRUM, i === 0 ? G_CORE : G_FLASH, 0.0,
            a * clamp01(k * 2.0) * (0.95 - i * 0.11));
      }
    } else {
      /* L'ECLAT : trois couronnes, une gerbe, un noyau. Un seul anneau plat disparait
         vu par la tranche -- deja paye sur la fenetre de la Porte. */
      const k = since / STRIKE_BURST;
      const open = sqrt(k);
      /* Decroissance LINEAIRE : au carre, l'eclat a perdu soixante pour cent de son
         alpha au tiers de sa vie, et l'oeil n'y voit plus un coup mais un soupir. */
      const fade = 1.0 - k * 0.88;
      const rr = (0.30 + 1.55 * open) * blast;
      for (let i = 0; i < 26; i++) {
        const ca = i / 26 * Math.PI * 2 + ang;
        const lift = 0.40 * sin(i * 1.7 + b) * (1.0 - k);
        const h = 0.112 * blast * (1.0 - 0.40 * k);
        put(cos(ca) * rr, aim + lift, sin(ca) * rr, h, h, h, ca + t * 2.2,
            (i % 3 === 0) ? M_STROBE : M_RAINBOW,
            (i % 3 === 0) ? G_GLASS : G_BODY, 0.0, a * fade * 0.95);
      }
      for (let i = 0; i < 18; i++) {
        const ca = i / 18 * Math.PI * 2;
        const rv = rr * 0.86;
        const h = 0.095 * blast * (1.0 - 0.40 * k);
        put(ux * cos(ca) * rv, aim + sin(ca) * rv, uz * cos(ca) * rv,
            h, h, h, ca * 1.3 + t * 2.6, M_SPECTRUM, G_MID, 0.0, a * fade * 0.88);
      }
      for (let i = 0; i < 12; i++) {
        const spin = i / 12 * Math.PI * 2 + ang * 2.0;
        const reach = rr * (0.55 + 0.85 * ((i * 7 % 5) / 4));
        const h = 0.085 * blast * (1.0 - 0.5 * k);
        put(ux * reach * 0.80 + cos(spin) * rr * 0.34,
            aim + 0.25 + 1.35 * open * (0.45 + (i % 3) * 0.28),
            uz * reach * 0.80 + sin(spin) * rr * 0.34,
            h, h, h, spin + t * 3.4, M_RAINBOW, G_FRAME, 0.0, a * fade * 0.80);
      }
      if (k < 0.60) {
        const core = 1.0 - k / 0.60;
        const h1 = (0.34 * core + 0.09) * blast;
        put(0, aim, 0, h1, h1, h1, t * 4.7, M_SPECTRUM, G_CORE, 0.0, a * core * 0.98);
        const h2 = (0.52 * core + 0.12) * blast;
        put(0, aim, 0, h2, h2, h2, -t * 3.1, M_STROBE, G_GLASS, 0.0, a * core * 0.55);
      }
    }
  }

  return out;
}

if (typeof module !== 'undefined') module.exports = { buildChaos, CHAOS_SHAPE };
