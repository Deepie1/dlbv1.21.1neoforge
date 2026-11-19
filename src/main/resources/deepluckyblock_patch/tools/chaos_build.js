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
     T401b — LES COUPS : QUINZE GESTES, QUINZE FORMES.
     Miroir exact de ChaosSignatureVfx.kaleidoStrikes / entropyStrikes / bigBangStrikes.

     La premiere version avait UNE routine de frappe recopiee quinze fois, avec pour
     seules differences un angle et une echelle : un metronome, pas une choregraphie.
     Ici aucun temps fort ne partage sa geometrie avec un autre, et chaque geste decoule
     de ce que sa signature EST -- le kaleidoscope reflechit, la sphere erode, la
     singularite tire. Les ticks sont ceux du serveur, a l'unite pres.
     ====================================================================================== */
  /* TAU est deja declare plus haut dans cette fonction (l. 82) : le redeclarer casse
     le module. Son ecart avec le 6.2831853f du Java est de 2e-7, donc invisible. */
  const WIND = 9, BURST = 8;
  const nowT = t * DUR;
  const spin = t * TAU;
  const abs = Math.abs;

  if (variant === 0) {
    /* ---- KALEIDOSCOPE : six reflexions, six gestes -------------------------------- */
    const BEATS_K = [30, 48, 66, 84, 102, 120];
    const aim = 1.05;
    for (let k = 0; k < BEATS_K.length; k++) {
      const since = nowT - BEATS_K[k];
      if (since < -WIND || since > BURST) continue;
      const pre = since < 0;
      const w = pre ? 1.0 + since / WIND : 1.0;
      const r = pre ? 0.0 : since / BURST;
      const base = salt * 0.11 + k;

      if (k === 0) {
        /* I. LES DOUZE CONVERGENTS, puis le choc qui ressort en colonne. */
        if (pre) {
          const e = w * w;
          for (let i = 0; i < 12; i++) {
            const ang = i / 12 * TAU + base;
            const head = 3.90 - 3.70 * e;
            for (let j = 0; j < 5; j++) {
              const rr = head + j * 0.21, h = 0.112 - j * 0.014;
              put(cos(ang) * rr, aim + 0.07 * j, sin(ang) * rr, h, h, h, ang + spin,
                  (i & 1) === 0 ? M_RAINBOW : M_SPECTRUM, j === 0 ? G_FLASH : G_BODY,
                  0.0, a * (0.98 - j * 0.13) * w);
            }
          }
        } else {
          const f = 1.0 - r;
          for (let j = 0; j < 15; j++) {
            const up = aim + j * 0.18 * (0.30 + r * 1.7);
            const rr = 0.11 + j * 0.034, ca = j * 1.1 + spin * 2.0, h = 0.135 * f;
            put(cos(ca) * rr, up, sin(ca) * rr, h, h, h, ca, M_STROBE,
                j < 3 ? G_CORE : G_GLASS, 0.0, a * f * (1.0 - j * 0.042));
          }
        }
      } else if (k === 1) {
        /* II. LES DEUX LAMES CROISEES : ca tranche, et dans deux sens a la fois. */
        const s = pre ? (w - 1.0) : r;
        const fade = 1.0 - abs(s) * 0.42;
        for (let m = 0; m < 2; m++) {
          const dir = m === 0 ? 1.0 : -1.0;
          const cross = s * dir * 2.70;
          const tilt = m === 0 ? 0.30 : -0.30;
          for (let i = -11; i <= 11; i++) {
            const along = i * 0.175;
            const wx = m === 0 ? cross : along;
            const wz = m === 0 ? along : cross;
            const hw = 0.155 - abs(i) * 0.0068;
            put(wx, aim + tilt * along, wz, hw, 0.055, hw, tilt * 3.0 + spin,
                m === 0 ? M_RAINBOW : M_SPECTRUM, i === 0 ? G_FLASH : G_BODY, 0.0, a * fade);
          }
        }
        if (!pre && r < 0.55) {
          const f = 1.0 - r / 0.55, h = 0.36 * f + 0.07;
          put(0, aim, 0, h, h, h, spin * 3.0, M_STROBE, G_CORE, 0.0, a * f);
        }
      } else if (k === 2) {
        /* III. LA CAGE QUI SE REFERME : un ecrasement, sans projectile. */
        const close = pre ? w : 1.0;
        const back = pre ? 0.0 : r;
        const rad = 2.60 - 2.28 * (close * close) + 2.45 * back * back;
        const fade = pre ? w : (1.0 - back);
        for (let wall = 0; wall < 6; wall++) {
          const ang = wall / 6 * TAU + spin * 0.35;
          for (let j = 0; j < 11; j++) {
            put(cos(ang) * rad, 0.10 + j * 0.225, sin(ang) * rad, 0.070, 0.070, 0.118, ang,
                (j % 3 === 0) ? M_DUAL : M_RAINBOW, (j % 3 === 0) ? G_FRAME : G_BODY,
                (j % 3 === 0) ? H_CYAN : 0.0, a * fade * 0.94);
          }
        }
        if (!pre && back < 0.70) {
          const f = 1.0 - back / 0.70;
          for (let i = 0; i < 12; i++) {
            const ca = i / 12 * TAU + spin, h = 0.165 * f;
            put(cos(ca) * 0.24, aim + 0.20 * sin(i * 2.1), sin(ca) * 0.24, h, h, h, ca,
                M_SPECTRUM, G_CORE, 0.0, a * f * 0.92);
          }
        }
      } else if (k === 3) {
        /* IV. LE PRISME ECLATE : un coup entre, douze sortent. */
        if (pre) {
          const e = w * w * w;
          const hh = 4.30 - 4.12 * e;
          for (let j = 0; j < 8; j++) {
            const rr = hh + j * 0.15, h = 0.185 - j * 0.016;
            put(rr * 0.70, aim + 1.75 * (1.0 - e) + j * 0.05, rr * -0.70, h, h, h, spin * 4.0,
                M_SPECTRUM, j === 0 ? G_CORE : G_FLASH, 0.0,
                a * (1.0 - j * 0.10) * clamp01(w * 1.8));
          }
        } else {
          const out2 = r * r * 0.55 + r * 0.45;
          const f = 1.0 - r * 0.80;
          for (let i = 0; i < 12; i++) {
            const ang = i / 12 * TAU + base;
            const pitch = ((i % 3) - 1) * 0.60;
            for (let j = 0; j < 4; j++) {
              const rr = (0.22 + j * 0.32) * (0.35 + out2 * 3.7), h = 0.105 - j * 0.013;
              put(cos(ang) * rr, aim + pitch * rr * 0.48, sin(ang) * rr, h, h, h, ang + spin,
                  (i % 3 === 0) ? M_STROBE : M_RAINBOW, (i % 3 === 0) ? G_GLASS : G_BODY,
                  0.0, a * f * (0.95 - j * 0.14));
            }
          }
        }
      } else if (k === 4) {
        /* V. LA SPIRALE DE REFLETS : le seul geste continu, il visse. */
        const p = pre ? w * 0.58 : 0.58 + r * 0.42;
        const f = pre ? clamp01(w * 2.2) : (1.0 - r * 0.72);
        for (let i = 0; i < 34; i++) {
          const u = i / 33;
          const lead = clamp01(p * 1.30 - u * 0.30);
          if (lead <= 0.01) continue;
          const ang = u * TAU * 2.6 + spin * 1.5 + base;
          const rr = (1.70 - 1.26 * p) * (0.40 + 0.60 * (1.0 - u * 0.5));
          const h = 0.086 + 0.032 * sin(u * 9.0);
          put(cos(ang) * rr, 2.45 - u * 2.30, sin(ang) * rr, h, h, h, ang,
              (i % 4 === 0) ? M_SPECTRUM : M_RAINBOW, (i % 4 === 0) ? G_FRAME : G_BODY,
              0.0, a * f * lead);
        }
      } else {
        /* VI. LE REPLIEMENT : le coup final, le plus large puis le plus haut. */
        if (pre) {
          const e = w * w;
          for (let i = 0; i < 18; i++) {
            const ang = i / 18 * TAU + base;
            for (let j = 0; j < 6; j++) {
              const rr = (4.40 - 4.05 * e) * (0.55 + j * 0.11);
              put(cos(ang) * rr, aim + (1.0 - e) * 0.95 * sin(i * 0.9), sin(ang) * rr,
                  0.092, 0.092, 0.092, ang + spin, M_RAINBOW, j === 0 ? G_FLASH : G_BODY,
                  0.0, a * w * 0.90);
            }
          }
        } else {
          const f = 1.0 - r * 0.84;
          for (let j = 0; j < 24; j++) {
            const up = aim + j * 0.27 * (0.22 + r * 2.1);
            const sway = 0.32 * sin(j * 0.8 + spin * 2.0) * (0.3 + r);
            const ca = j * 0.7 + spin * 3.0, h = 0.175 - j * 0.0046;
            put(cos(ca) * sway, up, sin(ca) * sway, h, h, h, ca,
                (j % 2 === 0) ? M_SPECTRUM : M_STROBE,
                (j < 4) ? G_CORE : (j < 12 ? G_FLASH : G_GLASS), 0.0, a * f * (1.0 - j * 0.026));
          }
        }
      }
    }

  } else if (variant === 1) {
    /* ---- SPHERE D'ENTROPIE : huit facons de se defaire ---------------------------- */
    const BEATS_E = [22, 38, 54, 70, 86, 102, 118, 134];
    const aim = 1.15;
    for (let k = 0; k < BEATS_E.length; k++) {
      const since = nowT - BEATS_E[k];
      if (since < -WIND || since > BURST) continue;
      const pre = since < 0;
      const w = pre ? 1.0 + since / WIND : 1.0;
      const r = pre ? 0.0 : since / BURST;
      const grit = 0.10 + k * 0.13;      /* le desordre monte, comme CHAOS_ENTROPIES */
      const base = salt * 0.09 + k * 1.7;

      if (k === 0) {
        /* 1. LA MORSURE DE TREILLIS. */
        for (let i = 0; i < 12; i++) {
          const ang = i * 2.39996 + base;
          const rr = pre ? (2.45 - 2.05 * w * w) : (0.38 + 2.1 * r * r);
          const f = pre ? w : (1.0 - r);
          const hh = 0.150 * (pre ? 1.0 : 1.0 - 0.42 * r);
          const up = aim + sin(i * 1.7) * 0.50 * rr * 0.42;
          put(cos(ang) * rr, up, sin(ang) * rr, hh, hh, hh, ang + spin,
              M_TINTED, G_MID, H_VIOLET, a * f * 0.95);
          const h2 = hh * 0.60;
          put(cos(ang) * rr * 0.86, up * 0.97, sin(ang) * rr * 0.86, h2, h2, h2, -ang,
              M_RAINBOW, G_BODY, 0.0, a * f * 0.78);
        }
      } else if (k === 1) {
        /* 2. L'ANNEAU DE CISAILLEMENT : plat, large, tremblant. */
        const rad = pre ? (2.95 - 2.52 * w) : (0.42 + 3.0 * r * r);
        const f = pre ? w : (1.0 - r * 0.88);
        for (let i = 0; i < 36; i++) {
          const ang = i / 36 * TAU + spin * 0.8;
          const wob = grit * 0.22 * sin(i * 3.1 + spin * 4.0);
          put(cos(ang) * (rad + wob), aim + wob, sin(ang) * (rad + wob),
              0.145, 0.038, 0.145, ang,
              (i % 5 === 0) ? M_DUAL : M_MONO, (i % 5 === 0) ? G_GLASS : G_MID,
              H_CYAN, a * f * 0.92);
        }
      } else if (k === 2) {
        /* 3. L'EFFONDREMENT DE SECTEUR : le seul geste qui arrive d'un seul cote. */
        const fall = pre ? (1.0 - w) : 0.0;
        const f = pre ? clamp01(w * 1.7) : (1.0 - r);
        for (let i = 0; i < 9; i++) {
          const ang = base + (i - 4) * 0.165;
          for (let j = 0; j < 7; j++) {
            const rr = 0.60 + j * 0.32;
            const drop = fall * (2.60 + j * 0.30);
            const slip = pre ? 0.0 : r * r * 1.3;
            const h = 0.125 - j * 0.008;
            put(cos(ang) * (rr + slip), aim + drop - j * 0.06 + slip * 0.4,
                sin(ang) * (rr + slip), h, h, h, ang + j * 0.4,
                M_PULSE, j < 2 ? G_FLASH : G_MID, H_MAGENTA, a * f * (0.95 - j * 0.08));
          }
        }
      } else if (k === 3) {
        /* 4. LE VRILLAGE : deux helices contraires, le seul geste qui tord. */
        const tight = pre ? w : 1.0 - r * 0.55;
        const f = pre ? w : (1.0 - r * 0.9);
        for (let helix = 0; helix < 2; helix++) {
          const sense = helix === 0 ? 1.0 : -1.0;
          for (let i = 0; i < 24; i++) {
            const u = i / 23;
            const ang = sense * (u * TAU * 2.2 + spin * 2.4) + helix * Math.PI;
            const rr = (2.70 - 2.15 * tight) * (0.45 + 0.55 * sin(u * Math.PI));
            const h = 0.098 - 0.020 * abs(u - 0.5) * 2.0;
            put(cos(ang) * rr, 0.20 + u * 2.15, sin(ang) * rr, h, h, h, ang,
                M_SPECTRUM, (i % 5 === 0) ? G_FLASH : G_BODY, 0.0, a * f * 0.93);
          }
        }
      } else if (k === 4) {
        /* 5. LA PLUIE DE CELLULES : ca tombe droit, l'oeil a besoin d'un axe neuf. */
        const f = pre ? clamp01(w * 1.6) : (1.0 - r * 0.95);
        const drop = pre ? (1.0 - w) : 0.0;
        for (let i = 0; i < 28; i++) {
          const ang = i * 2.39996 + base;
          const rr = 0.30 + (i % 7) * 0.305;
          const stagger = ((i * 37) % 13) / 13;
          const up = aim + 1.05 + drop * (3.4 + stagger * 1.8) - (pre ? 0.0 : r * 1.25);
          const h = 0.108 + 0.030 * stagger;
          put(cos(ang) * rr, up, sin(ang) * rr, h, h, h, ang * 2.0 + spin,
              (i % 6 === 0) ? M_STROBE : M_MONO, (i % 6 === 0) ? G_GLASS : G_MID,
              0.0, a * f * 0.90);
        }
        if (!pre && r < 0.5) {
          const g = 1.0 - r / 0.5;
          for (let i = 0; i < 14; i++) {
            const ca = i / 14 * TAU, sp = 0.30 + r * 1.9, h = 0.088 * g;
            put(cos(ca) * sp, 0.10, sin(ca) * sp, h, h, h, ca, M_MONO, G_DEEP, 0.0, a * g * 0.75);
          }
        }
      } else if (k === 5) {
        /* 6. L'IMPLOSION PONCTUELLE : la seule qui finisse par une absence. */
        if (pre) {
          const e = w * w * w;
          for (let i = 0; i < 40; i++) {
            const ang = i * 2.39996 + base;
            const pitchU = ((i * 11) % 17) / 17;
            const rr = (3.20 - 3.05 * e) * (0.5 + 0.5 * pitchU);
            const h = 0.070 + 0.055 * e;
            put(cos(ang) * rr, aim + (pitchU - 0.5) * 2.4 * (1.0 - e), sin(ang) * rr,
                h, h, h, ang + spin * 2.0, M_EMBER, G_FRAME, H_EMBER_HUE, a * (0.45 + 0.55 * e));
          }
          if (w > 0.80) {
            const g = (w - 0.80) / 0.20, h = 0.10 + 0.45 * g;
            put(0, aim, 0, h, h, h, spin * 6.0, M_SPECTRUM, G_CORE, 0.0, a * g);
          }
        } else {
          const g = 1.0 - r;
          const h1 = 0.62 * g * g;
          put(0, aim, 0, h1, h1, h1, spin * 6.0, M_SPECTRUM, G_CORE, 0.0, a * g);
          const h2 = 0.95 * g * g * g;
          put(0, aim, 0, h2, h2, h2, -spin * 2.0, M_DUAL, G_DEEP, H_VIOLET, a * g * 0.45);
        }
      } else if (k === 6) {
        /* 7. L'ONDE DE DESAGREGATION : le seul geste centrifuge pur. */
        const grow = pre ? w * 0.25 : 0.25 + r * 0.75;
        const f = pre ? w * 0.8 : (1.0 - r * 0.92);
        const rr = 0.25 + grow * 3.1;
        for (let ringIdx = 0; ringIdx < 5; ringIdx++) {
          const lat = (ringIdx - 2) * 0.38;
          const ringR = rr * sqrt(Math.max(0.05, 1.0 - lat * lat * 0.62));
          const n = 8 + ringIdx * 2;
          for (let i = 0; i < n; i++) {
            const ang = i / n * TAU + ringIdx * 0.6 + spin;
            const jit = grit * 0.30 * sin(i * 5.3 + ringIdx);
            const h = 0.100 * (1.0 - grow * 0.35);
            put(cos(ang) * (ringR + jit), aim + lat * rr * 0.85, sin(ang) * (ringR + jit),
                h, h, h, ang, M_RAINBOW, ringIdx === 2 ? G_FLASH : G_BODY, 0.0, a * f * 0.94);
          }
        }
      } else {
        /* 8. LA DISSOLUTION : a ce niveau d'entropie, il n'y a plus de forme. */
        const f = pre ? w : (1.0 - r);
        for (let i = 0; i < 46; i++) {
          const ang = i * 2.39996 + base;
          const seedU = ((i * 29) % 23) / 23;
          const cloud = pre ? (1.0 - w) * 2.1 + 0.50 : 0.50 + r * 2.7;
          const rr = cloud * (0.35 + 0.65 * seedU);
          const up = aim + (seedU - 0.42) * 1.5 * cloud + (pre ? 0.0 : r * r * 1.7);
          const h = (0.055 + 0.075 * seedU) * (pre ? 1.0 : 1.0 - 0.4 * r);
          put(cos(ang) * rr, up, sin(ang) * rr, h, h, h, ang * 3.0 + spin,
              (i % 4 === 0) ? M_DUAL : M_TINTED, (i % 4 === 0) ? G_FRAME : G_DEEP,
              H_ROSE, a * f * 0.88);
        }
      }
    }

  } else {
    /* ---- BIG BANG : quatorze tractions, puis UNE detonation ----------------------- */
    const aim = 1.20;

    /* LES TRACTIONS : la singularite TIRE. Elles s'etirent vers le foyer et se
       resserrent d'une traction a l'autre ; sans eclat, pour ne pas ecraser la suite. */
    for (let k = 0; k < 14; k++) {
      const since = nowT - (6 + k * 6);
      if (since < -3.0 || since > 5.0) continue;
      const u = clamp01((since + 3.0) / 8.0);
      const f = sin(u * Math.PI);
      const grip = 3.40 - k * 0.165;
      for (let i = 0; i < 5; i++) {
        const ang = k * 2.39996 + i * 1.2566 + salt * 0.07;
        for (let j = 0; j < 10; j++) {
          const rr = grip * (1.0 - u * 0.82) * (0.26 + j * 0.090);
          const h = 0.090 - j * 0.0052;
          put(cos(ang) * rr, aim + 0.60 * (1.0 - u) * sin(i * 1.9 + k) + j * 0.028,
              sin(ang) * rr, h, h, h, ang, M_TINTED, j < 2 ? G_FRAME : G_DEEP, H_BLUE,
              a * f * (0.92 - j * 0.062));
        }
      }
    }

    /* LA DETONATION, en trois temps qui ne se ressemblent pas. */
    const bang = nowT - 84;
    if (bang >= -16.0 && bang <= 26.0) {
      if (bang < 0.0) {
        /* TEMPS 1 : LA COMPRESSION. */
        const c = 1.0 + bang / 16.0;
        const e = c * c * c;
        for (let i = 0; i < 60; i++) {
          const ang = i * 2.39996 + salt * 0.05;
          const lat = ((i * 13) % 19) / 19 - 0.5;
          const rr = (5.40 - 5.10 * e) * (0.55 + 0.45 * cos(lat * 3.0));
          const h = 0.085 + 0.075 * e;
          put(cos(ang) * rr, aim + lat * 3.4 * (1.0 - e * 0.7), sin(ang) * rr,
              h, h, h, ang + spin * 2.0, (i % 5 === 0) ? M_DUAL : M_TINTED,
              (i % 5 === 0) ? G_GLASS : G_MID, H_BLUE, a * (0.40 + 0.60 * e));
        }
        if (c > 0.72) {
          const g = (c - 0.72) / 0.28, h = 0.12 + 0.70 * g * g;
          put(0, aim, 0, h, h, h, spin * 8.0, M_SPECTRUM, G_CORE, 0.0, a * g);
        }
      } else {
        /* TEMPS 2 : L'ECLAIR, quatre ticks. */
        if (bang < 4.0) {
          const g = 1.0 - bang / 4.0, h0 = 1.35 * g + 0.25;
          put(0, aim, 0, h0, h0, h0, spin * 9.0, M_SPECTRUM, G_CORE, 0.0, a * g);
          for (let i = 0; i < 16; i++) {
            const ang = i / 16 * TAU;
            const lat = ((i % 4) - 1.5) * 0.55;
            for (let j = 1; j < 9; j++) {
              const rr = j * (0.55 + (1.0 - g) * 0.9), h = 0.26 - j * 0.022;
              put(cos(ang) * rr, aim + lat * j * 0.22, sin(ang) * rr, h, h, h, ang,
                  M_STROBE, j < 3 ? G_FLASH : G_GLASS, 0.0, a * g * (1.0 - j * 0.09));
            }
          }
        }
        /* TEMPS 3 : L'ONDE DE CHOC, lente, pour qu'on la suive. */
        const s = bang / 26.0;
        const wave = 0.6 + s * 7.4;
        const f = (1.0 - s) * (1.0 - s * 0.35);
        const n = 44;
        for (let i = 0; i < n; i++) {
          const ang = i / n * TAU + spin * 0.25;
          const hw = 0.195 * f + 0.035;
          put(cos(ang) * wave, 0.12 + 0.30 * s, sin(ang) * wave, hw, 0.070, hw, ang,
              (i % 4 === 0) ? M_EMBER : M_RAINBOW, (i % 4 === 0) ? G_FLASH : G_BODY,
              H_EMBER_HUE, a * f * 0.95);
        }
        for (let ringIdx = 0; ringIdx < 4; ringIdx++) {
          const lat = 0.25 + ringIdx * 0.22;
          const capR = wave * 0.62 * sqrt(Math.max(0.04, 1.0 - lat * lat));
          const m = 10 + ringIdx * 3;
          for (let i = 0; i < m; i++) {
            const ang = i / m * TAU + ringIdx * 0.5 - spin * 0.4;
            const h = 0.115 * f + 0.02;
            put(cos(ang) * capR, aim + lat * wave * 0.72, sin(ang) * capR, h, h, h, ang,
                M_RAINBOW, G_MID, 0.0, a * f * 0.80);
          }
        }
      }
    }
  }

  return out;
}

if (typeof module !== 'undefined') module.exports = { buildChaos, CHAOS_SHAPE };
