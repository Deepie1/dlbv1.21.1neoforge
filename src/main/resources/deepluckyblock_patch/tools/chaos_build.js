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
    DURATION: [150, 170, 200, 170, 200],
    /* Hauteurs de centre, par signature. La page y accroche sa caméra. */
    YC: [1.05, 1.15, 1.20, 1.10, 0.95],
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
    /* T406 — LA GALETTE DEVIENT UNE COUPOLE.

       L'ancienne version etait un disque plat de 2,8 blocs flottant a 1,05 au-dessus du
       mob : en jeu, elle passait pour un tapis pose sur la tete. Le kaleidoscope est
       maintenant un DOME de huit blocs de rayon dont le mob occupe le centre exact. Il
       garde toute l'optique de la version plate — six secteurs, douze parois, reflexion
       dans chaque cellule — mais pliee sur une demi-sphere. Et le zenith sert : a chaque
       temps fort les eclats y montent, s'y agglomerent en goutte, qui tombe et explose.

       Miroir exact de ChaosSignatureVfx.renderKaleido. */
    const HALF_PI = PI * 0.5;
    const SECTORS = 6, WEDGE = PI / 6, CELL = PI / 3;
    const DOME_R = 8.0, DOME_H = 6.70;
    const SHARDS = 9, PER = 5;

    const floorY = GROUND > -9.0 ? GROUND + 0.04 : -0.60;
    /* LA COTE D'IMPACT : le torse. Le repere a son origine au centre du corps, donc le
       mob occupe de -0.90 a +0.90 ; une goutte qui descend jusqu'au plancher traverse la
       cible au lieu de la frapper. */
    const HIT_Y = 0.35;

    const open = smooth(tt, 0.01, 0.20);
    const close = smooth(tt, 0.90, 1.0);
    const live = 1.0 - 0.82 * close;
    const radius = DOME_R * open;
    const hgt = DOME_H * open;
    const spin = tt * 1.35;

    const chaos = smooth(tt, 0.46, 0.62) * (1 - smooth(tt, 0.70, 0.80));
    const relock = smooth(tt, 0.78, 0.83) * (1 - smooth(tt, 0.86, 0.93));
    /* LE RECUL DE LA COUPOLE : capture de la version precedente -- 1472 cubes a l'ecran
       dont 36 pour l'explosion, soit 2 % de l'image. Ce n'est pas la taille qui fait
       exister un evenement dans une figure saturee, c'est le CONTRASTE. */
    let domeDim = 1.0;

    /* LES SIX GOUTTES — memes temps forts que la partition serveur. La chute est en
       carre : une goutte TOMBE, elle ne descend pas a vitesse constante. */
    const nowT = tt * 150.0;
    let gather = 0.0, dropY = -1.0, dropGrow = 0.0, burst = 0.0, burstU = 1.0, dropId = 0;
    for (let d = 0; d < 6; d++) {
      const since = nowT - (30 + d * 18);
      /* TREIZE TICKS, pas vingt-six : les temps forts sont espaces de dix-huit, une
         fenetre plus longue que l'intervalle ne retombe jamais a zero. */
      const g = smooth(since, -9.0, -4.0) * (1 - smooth(since, 0.0, 4.0));
      if (g > gather) gather = g;
      if (since >= -9.0 && since < 0.5) {
        dropId = d;
        dropGrow = smooth(since, -9.0, -5.0);
        const fall = clamp01((since + 5.0) / 5.0);
        dropY = 1.0 - fall * fall;
      }
      /* L'AGE et l'INTENSITE separes : une explosion est instantanee a l'allumage. */
      if (since >= 0.0 && since < 14.0) {
        const u = since / 14.0;
        const inten = (1.0 - u) * (1.0 - 0.35 * u);
        if (inten > burst) { burst = inten; burstU = u; dropId = d; }
      }
    }
    domeDim = 1.0 - 0.52 * burst;

    if (!emitPixels) {
      /* LES ECLATS. Le parametre qui portait le rayon porte maintenant la LATITUDE : le
         motif est enroule sur la demi-sphere au lieu d'etre pose a plat. */
      for (let sh = 0; sh < SHARDS; sh++) {
        const lat0 = 0.08 + 0.78 * hash(salt + sh, 1);
        const aC = 0.055 + (WEDGE - 0.11) * hash(salt + sh, 2);
        const dirL = (hash(salt + sh, 3) - 0.5) * 0.34;
        const dirA = (hash(salt + sh, 4) - 0.5) * 0.17;
        const size = 0.086 + 0.062 * hash(salt + sh, 5);
        const k1 = 1 + (sh % 3), k2 = 1 + ((sh * 7) % 4);
        const wobL = 0.055 * sin(TAU * (k1 * tt + hash(salt + sh, 6)));
        const wobA = 0.080 * sin(TAU * (k2 * tt + hash(salt + sh, 7)));

        let mode, hue, gray;
        const kind = (sh + salt) % 4;
        if (kind === 0) { mode = M_DUAL; hue = H_CYAN; gray = G_FRAME; }
        else if (kind === 1) { mode = M_RAINBOW; hue = 0.0; gray = G_BODY; }
        else if (kind === 2) { mode = M_PULSE; hue = H_VIOLET; gray = G_MID; }
        else { mode = M_TINTED; hue = H_MAGENTA; gray = G_DEEP; }
        if (chaos > 0.45 && ((sh * 5 + salt) % 3) === 0) { mode = M_STROBE; gray = G_GLASS; }
        /* LA PART MOBILE : trois eclats sur neuf montent, les six autres tiennent la
           coupole. Le contraste entre les deux est ce qui rend le ramassement lisible. */
        const lifts = (sh % 3) === 0;

        for (let c = 0; c < PER; c++) {
          const u = c / (PER - 1) - 0.5;
          let l0 = lat0 + dirL * u + wobL;
          l0 = l0 < 0.03 ? 0.03 : l0 > 0.97 ? 0.97 : l0;
          let a0 = aC + dirA * u + wobA;
          a0 = a0 < 0.012 ? 0.012 : a0 > WEDGE - 0.012 ? WEDGE - 0.012 : a0;
          /* LA CONCENTRATION : la latitude est tiree vers 1, les cinquante-quatre eclats
             et leurs reflets convergent au sommet. La goutte est faite de la matiere de
             la figure, elle n'apparait pas de nulle part. */
          const lg = lifts ? l0 + (1.0 - l0) * gather * 0.88 : l0;
          const phi = lg * HALF_PI;
          const ring = cos(phi);
          const up = floorY + sin(phi) * hgt;

          for (let sct = 0; sct < SECTORS; sct++) {
            for (let m = 0; m < 2; m++) {
              let local = (m === 0) ? a0 : (CELL - a0);
              let rr = ring;
              if (chaos > 0.001) {
                const cid = sh * 97 + sct * 13 + m * 7;
                local += chaos * 0.26 * (hash(salt + cid, 11) - 0.5);
                rr += chaos * 0.16 * (hash(salt + cid, 12) - 0.5);
              }
              const ang = spin + sct * CELL + local;
              const dist = (rr < 0 ? 0 : rr > 1.08 ? 1.08 : rr) * radius;
              const grow = clamp01(open * 1.5 - (1.0 - lg) * 0.25);
              const sz = size * grow * (1.0 + (lifts ? 0.85 : 0.16) * gather) * live;
              cube(cos(ang) * dist, up, sin(ang) * dist, sz, ang, mode,
                   relock > 0.35 ? G_FLASH : gray, hue,
                   a * (0.82 + 0.18 * relock) * grow * live * domeDim);
            }
          }
        }
      }

      /* LES DOUZE COTES. Les anciennes parois de miroir, relevees : chacune part du sol
         et monte au zenith. C'est la piece qui fait lire « DOME » et non « boule ». */
      for (let w = 0; w < 12; w++) {
        const fold = (w % 2) === 0;
        const ang = spin + w * WEDGE;
        const steps = 32;
        for (let k = 0; k <= steps; k++) {
          if (chaos > 0.25 && hash(salt + w * 31, k * 17) < chaos * 0.55) continue;
          const phi = (k / steps) * HALF_PI;
          const dist = cos(phi) * radius * 1.01;
          const up = floorY + sin(phi) * hgt;
          cube(cos(ang) * dist, up, sin(ang) * dist,
               (fold ? 0.112 : 0.074) * live, ang,
               fold ? M_DUAL : M_MONO, fold ? G_GLASS : G_FRAME,
               fold ? H_BLUE : 0.0, a * (fold ? 0.98 : 0.78) * open * live * domeDim);
        }
      }

      /* LES PARALLELES. Sept cercles horizontaux ; le nombre de cubes suit le rayon, a
         densite constante — un cercle qui s'epaissit en montant trahirait la courbure. */
      for (let ring = 0; ring < 7; ring++) {
        const phi = ((ring + 0.5) / 7) * HALF_PI;
        const dist = cos(phi) * radius;
        const up = floorY + sin(phi) * hgt;
        const marks = Math.max(9, Math.round(dist * 7.0));
        for (let k = 0; k < marks; k++) {
          if (chaos > 0.3 && hash(salt + 400 + ring, k) < chaos * 0.6) continue;
          const ang = spin * (ring % 2 === 0 ? 1.0 : -0.65) + k / marks * TAU;
          cube(cos(ang) * dist, up, sin(ang) * dist, 0.052 * live, ang,
               M_MONO, G_MID, 0.0, a * 0.62 * open * live * domeDim);
        }
      }

      /* LA CERCLURE AU SOL : le pied du dome. C'est a elle qu'on mesure les huit blocs. */
      const rim0 = 96;
      for (let k = 0; k < rim0; k++) {
        const ang = spin * 0.8 + k / rim0 * TAU;
        const dist = radius * 1.02;
        cube(cos(ang) * dist, floorY + 0.06, sin(ang) * dist, 0.098 * live, ang,
             M_DUAL, G_GLASS, H_ROSE, a * 0.95 * open * live);
      }

      /* LE ZENITH — la clef de voute. Sans elle les douze cotes se croisent dans le vide
         et le sommet a l'air inacheve. Elle enfle quand la goutte se charge. */
      {
        const keyS = (0.13 + 0.40 * gather) * live;
        const keyY = floorY + hgt;
        for (let k = 0; k < 7; k++) {
          const ang = spin * 2.1 + k / 7 * TAU;
          const dist = (0.16 + 0.42 * (1.0 - gather)) * open;
          cube(cos(ang) * dist, keyY, sin(ang) * dist, keyS, ang,
               gather > 0.4 ? M_STROBE : M_DUAL, G_FLASH, H_CYAN,
               a * (0.70 + 0.30 * gather) * open * live);
        }
      }

      /* LA GOUTTE : un noyau serre, une colonne de chute, une taille qui augmente en
         descendant — une masse qui arrive, pas un point qui glisse. */
      if (dropGrow > 0.01 && dropY >= 0.0) {
        const zen = floorY + hgt;
        const dY = HIT_Y + dropY * (zen - HIT_Y);
        const speed = 1.0 - dropY;
        const core = (0.26 + 0.34 * dropGrow) * (1.0 + 0.60 * speed);
        for (let k = 0; k < 8; k++) {
          const ang = k / 8 * TAU + nowT * 0.55;
          const dist = core * 0.85;
          cube(cos(ang) * dist, dY, sin(ang) * dist, core * 0.62, ang,
               M_PULSE, G_FLASH, H_CYAN, a * 0.98);
        }
        put(0, dY, 0, core, core * 1.45, core, 0, M_DUAL, G_CORE, H_BLUE, a * 1.0);
        for (let k = 1; k <= 10; k++) {
          const back = k / 10;
          const ty = dY + back * speed * (zen - HIT_Y) * 0.52;
          if (ty > zen) continue;
          cube(0, ty, 0, core * (1.0 - 0.80 * back) * 0.70, 0,
               M_TINTED, G_GLASS, H_CYAN, a * (1.0 - back) * 0.72);
        }
      }

      /* L'EXPLOSION SUR LE MOB : une onde depuis le corps, plus une colonne de retour.
         Centree sur le mob et non sur le sol, parce que c'est LUI qui est frappe. */
      if (burst > 0.01) {
        /* TROIS BLOCS ET DEMI, pas cinq et demi : a 5,4 l'onde couvrait le dome entier
           et se lisait comme un effondrement de la figure, pas comme un coup au centre. */
        /* LE RAYON SUIT LA RACINE DE L'AGE : violent puis ralenti. Indexe sur
           l'intensite, il se figeait a son maximum onze ticks durant. */
        const egrow = sqrt(burstU);
        const er = 0.55 + 2.95 * egrow;
        const eh = 0.80 + 1.80 * egrow;
        const fade = burst;
        /* LE NOYAU : une onde sans point de depart brillant n'a pas de cause visible. */
        const nuke = 0.80 * burst * burst;
        for (let k = 0; k < 8; k++) {
          const ang = k / 8 * TAU + nowT * 0.9;
          const dist = nuke * 0.70;
          cube(cos(ang) * dist, HIT_Y + 0.12 * sin(ang * 2.0), sin(ang) * dist,
               nuke, ang, M_STROBE, G_CORE, H_CYAN, a * (0.55 + 0.45 * fade));
        }
        for (let k = 0; k < 60; k++) {
          const ang = k / 60 * TAU + dropId * 0.41;
          const phi = (hash(salt + 900 + dropId, k) * 1.10 - 0.22) * HALF_PI * 0.80;
          const dist = er * cos(phi) * (0.80 + 0.40 * hash(salt + 930, k));
          const up = HIT_Y + sin(phi) * eh;
          cube(cos(ang) * dist, up, sin(ang) * dist, 0.14 + 0.20 * burst, ang,
               (k % 3 === 0) ? M_STROBE : M_PULSE,
               (k % 3 === 0) ? G_FLASH : G_FRAME, H_CYAN,
               a * (0.12 + 0.88 * fade));
        }
        /* L'ANNEAU DE CHOC AU SOL : une explosion en l'air n'a pas de point d'appui. */
        for (let k = 0; k < 44; k++) {
          const ang = k / 44 * TAU - dropId * 0.29;
          const dist = er * 1.18;
          const hh = 0.10 + 0.12 * burst;
          put(cos(ang) * dist, floorY + 0.07, sin(ang) * dist, hh, 0.055, hh, ang,
              M_STROBE, G_FLASH, H_CYAN, a * (0.10 + 0.90 * fade));
        }
        for (let k = 0; k < 9; k++) {
          const up = HIT_Y - 0.62 + k / 9 * eh * 1.30;
          const wob = 0.14 * sin(nowT * 0.8 + k);
          cube(wob, up, -wob, 0.27 * (1.0 - k / 11) * (0.4 + 0.6 * burst), 0,
               M_DUAL, G_CORE, H_BLUE, a * (0.95 - 0.6 * (k / 9)));
        }
      }

      /* LE DISQUE AU SOL : l'ombre claire de la coupole, qui s'illumine a chaque goutte. */
      if (GROUND > -9.0) {
        for (let k = 0; k < 52; k++) {
          const ang = -spin * 0.5 + k / 52 * TAU;
          const dist = radius * (0.30 + 0.70 * hash(salt + 77, k));
          put(cos(ang) * dist, GROUND + 0.03, sin(ang) * dist,
              0.090, 0.016, 0.090, 0,
              burst > 0.3 ? M_PULSE : M_MONO,
              burst > 0.3 ? G_FRAME : G_MASK, H_CYAN,
              a * (0.55 + 0.40 * burst) * open * live);
        }
      }
      return;
    }

    /* --- naissances de pixels libres pour ce tick --- */
    const rim = 96;
    const tick = Math.round(tt * DUR);
    if (chaos > 0.30 && ((salt + tick) % 3) === 0) {
      const k = (salt + tick * 7) % rim;
      const ang = spin * 0.8 + k / rim * TAU;
      const phi = hash(salt + 55, k) * HALF_PI * 0.75;
      const dist = radius * 1.02 * cos(phi);
      spawn(cos(ang) * dist, floorY + sin(phi) * hgt, sin(ang) * dist,
            cos(ang) * 0.075, 0.040, sin(ang) * 0.075, 0.105, 26, M_STROBE, 0.0, 0.22, G_GLASS);
    }
    if (burst > 0.5 && ((salt + tick) % 2) === 0) {
      const ang = hash(salt, tick) * TAU;
      spawn(0, HIT_Y, 0, cos(ang) * 0.11, 0.22, sin(ang) * 0.11,
            0.085, 22, M_PULSE, H_CYAN, 0.16, G_FLASH);
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

  /* =======================================================================================
     IV. CASCADE DE BIFURCATION — miroir de ChaosSignatureVfx.renderFeigenbaum().
     La figure n'est pas dessinee : on itere x = r*x*(1-x) et on pose un cube a chaque
     valeur visitee. Les fenetres de periodicite apparaissent toutes seules — on ne
     pourrait pas les inventer a la main.
     ======================================================================================= */
  /* =======================================================================================
     IV. CASCADE DE BIFURCATION — miroir de ChaosSignatureVfx.renderFeigenbaum().
     r N'EST PLUS UNE DIRECTION DE L'ESPACE, C'EST LE TEMPS. La premiere version tracait
     le diagramme tel qu'on le publie : r en abscisse, x en hauteur. Figure exacte, et
     GRAPHIQUE -- mise en volume, epaissie pour ne pas disparaitre par la tranche, elle
     devenait une nappe informe. Ici r monte avec le temps, l'orbite est calculee a un
     seul r, et chaque valeur de l'attracteur devient le RAYON d'un anneau autour de la
     victime : un anneau, puis deux, puis quatre, puis le brouillard -- et vers 3,83 la
     fenetre de periode trois fait revenir TROIS anneaux nets en plein chaos.
     ======================================================================================= */
  const FEIGEN_SAMPLES = 96;
  const FEIGEN_ORBIT = new Float32Array(FEIGEN_SAMPLES);
  const FEIGEN_SORT = new Float32Array(FEIGEN_SAMPLES);
  const FEIGEN_CLUSTER = new Float32Array(FEIGEN_SAMPLES);
  const FEIGEN_BRANCH = new Int32Array(FEIGEN_SAMPLES);

  /* r(t) : LES PALIERS SONT CALES SUR LES SIX VAGUES DE COUPS. A la vague k l'orbite a
     exactement 2^k valeurs -- donc 2^k anneaux -- et la vague compte 2^k impacts. Les
     fenetres de periode 4, 8 et 16 sont minuscules (3,449->3,544 ; 3,544->3,564 ;
     3,564->3,569) : une rampe reguliere les ferait defiler en un quart de seconde. */
  const FEIGEN_T  = [0, 24, 48, 70, 88, 102, 112, 132, 148, 170];
  const FEIGEN_RV = [2.700, 2.920, 3.200, 3.490, 3.5550, 3.5660, 3.600, 3.8350, 3.8420, 3.990];
  function feigenR(tick) {
    if (tick <= FEIGEN_T[0]) return FEIGEN_RV[0];
    for (let i = 1; i < FEIGEN_T.length; i++) {
      if (tick <= FEIGEN_T[i]) {
        const u = (tick - FEIGEN_T[i - 1]) / (FEIGEN_T[i] - FEIGEN_T[i - 1]);
        return FEIGEN_RV[i - 1] + (FEIGEN_RV[i] - FEIGEN_RV[i - 1]) * u;
      }
    }
    return FEIGEN_RV[FEIGEN_RV.length - 1];
  }

  function feigenbaum(tt, emitPixels) {
    if (emitPixels) return;
    const aim = 1.10;
    const r = feigenR(tt * 170.0);   /* r monte par paliers cales sur les vagues */

    let x = 0.42 + 0.11 * hash(salt, 7);
    for (let i = 0; i < 220; i++) x = r * x * (1.0 - x);
    for (let i = 0; i < FEIGEN_SAMPLES; i++) {
      x = r * x * (1.0 - x);
      FEIGEN_ORBIT[i] = x < 0.015 ? 0.015 : (x > 0.985 ? 0.985 : x);
    }

    /* LE MICROSCOPE. Les fenetres de la cascade sont minuscules (periode 4 : 3,449 ->
       3,544 ; periode 8 : 3,544 -> 3,564 ; periode 16 : 3,564 -> 3,569). Un rayon
       proportionnel a x les ecraserait toutes sur l'epaisseur d'un cube. Le rayon mesure
       donc la POSITION DE x DANS L'ATTRACTEUR DU MOMENT : l'instrument suit sa cible en
       zoomant, comme on presente l'autosimilarite du diagramme. */
    let lo = 1.0, hi = 0.0;
    for (let i = 0; i < FEIGEN_SAMPLES; i++) {
      const v = FEIGEN_ORBIT[i];
      if (v < lo) lo = v;
      if (v > hi) hi = v;
    }
    const mid = (lo + hi) * 0.5, span = hi - lo;

    /* COMBIEN DE BRANCHES ? On trie l'orbite et on regroupe les valeurs confondues : une
       periode 4 ne donne que quatre valeurs distinctes, repetees vingt-quatre fois. Le
       compte n'est pas decrete, il est MESURE sur l'orbite. */
    FEIGEN_SORT.set(FEIGEN_ORBIT);
    FEIGEN_SORT.sort();
    const MERGE = 2.0e-5;
    let branches = 0;
    for (let i = 0; i < FEIGEN_SAMPLES; i++) {
      if (branches === 0 || FEIGEN_SORT[i] - FEIGEN_CLUSTER[branches - 1] > MERGE) {
        FEIGEN_CLUSTER[branches++] = FEIGEN_SORT[i];
      }
    }
    for (let i = 0; i < FEIGEN_SAMPLES; i++) {
      let b = branches - 1;
      for (let c = 0; c < branches; c++) {
        if (FEIGEN_ORBIT[i] <= FEIGEN_CLUSTER[c] + MERGE) { b = c; break; }
      }
      FEIGEN_BRANCH[i] = b;
    }

    /* LE REGULATEUR : le rayon part de la vraie position dans l'attracteur, puis glisse
       vers le RANG de la branche quand les paquets autosimilaires deviennent trop serres
       pour etre comptes. L'ordre et le nombre restent exacts. */
    const regul = clamp01((r - 3.40) / 0.16);
    /* `open` fait ECLORE le dedoublement au lieu de le faire claquer. */
    const open = clamp01(span / 0.28);
    const R_MID = 1.55, R_HALF = 1.05;

    const close = sstep(tt, 0.82, 0.95);
    const live = 1.0 - 0.80 * close;
    const spin2 = tt * 1.15;
    const tilt = 0.20 + 0.13 * sin(tt * TAU * 0.5);

    /* UN ANNEAU PAR BRANCHE. Le nombre d'anneaux n'est pas un parametre : c'est le
       nombre de valeurs distinctes que l'orbite vient de prendre. Chaque anneau recoit un
       nombre de cubes proportionnel a son rayon, sinon le plus petit serait un amas. */
    if (branches <= 20) {
      for (let b = 0; b < branches; b++) {
        const metric = span > 1.0e-5 ? (FEIGEN_CLUSTER[b] - mid) / span * 2.0 : 0.0;
        const rank = branches > 1 ? b / (branches - 1) * 2.0 - 1.0 : 0.0;
        const norm = metric + (rank - metric) * regul;
        const rad = (R_MID + norm * R_HALF * open) * live;
        const hue = 0.46 + 0.23 * (norm + 1.0);
        /* Budget : 260 cubes pour la couronne entiere ; au-dela de huit branches les
           anneaux se font pointilles -- on approche du point d'accumulation. */
        const n = Math.max(7, Math.min(44, Math.round(Math.min(rad * 23.0, 260.0 / branches))));
        for (let j = 0; j < n; j++) {
          const th = j / n * TAU + spin2 + b * 0.37;
          const px = cos(th) * rad, pz = sin(th) * rad;
          const py = aim + pz * tilt + 0.055 * sin(th * 3.0 + b);
          put(px, py, pz, 0.098, 0.132, 0.098, th,
              (j % 6 === 0) ? M_DUAL : M_TINTED, (j % 6 === 0) ? G_FRAME : G_BODY,
              hue, a * 0.92);
          if (j % 3 === 0) {
            put(px, GROUND + 0.07, pz, 0.072, 0.028, 0.072, th,
                M_MONO, G_MASK, 0.0, a * 0.40);
          }
        }
      }
    } else {
      /* LE BROUILLARD : au-dela de vingt branches il n'y a plus rien a compter. */
      for (let i = 0; i < FEIGEN_SAMPLES; i++) {
        const metric = span > 1.0e-5 ? (FEIGEN_ORBIT[i] - mid) / span * 2.0 : 0.0;
        const rank = FEIGEN_BRANCH[i] / (branches - 1) * 2.0 - 1.0;
        const norm = metric + (rank - metric) * regul;
        const rad = (R_MID + norm * R_HALF * open) * live;
        const th = i / FEIGEN_SAMPLES * TAU * 3.0 + spin2;
        const px = cos(th) * rad, pz = sin(th) * rad;
        const py = aim + pz * tilt + 0.085 * sin(th * 3.0 + i * 0.7);
        put(px, py, pz, 0.098, 0.132, 0.098, th,
            (i % 8 === 0) ? M_DUAL : M_TINTED, (i % 8 === 0) ? G_FRAME : G_BODY,
            0.46 + 0.23 * (norm + 1.0), a * 0.92);
        if (i % 3 === 0) {
          put(px, GROUND + 0.07, pz, 0.072, 0.028, 0.072, th,
              M_MONO, G_MASK, 0.0, a * 0.40);
        }
      }
    }

    /* LES MONTANTS : douze barreaux du sol a l'anneau le plus large. */
    const outer = (R_MID + R_HALF * open) * live;
    for (let p = 0; p < 12; p++) {
      const th = p / 12 * TAU + spin2;
      const px = cos(th) * outer, pz = sin(th) * outer;
      const top = aim + pz * tilt;
      for (let j = 0; j < 5; j++) {
        const u = j / 4;
        const py = GROUND + 0.10 + (top - GROUND - 0.10) * u;
        put(px, py, pz, 0.050, 0.050, 0.050, th,
            M_MONO, G_DEEP, 0.0, a * 0.30 * (0.35 + 0.65 * u));
      }
    }

    /* LE COEUR, qui bat de plus en plus vite a mesure que r approche du chaos. */
    const heat = clamp01((r - 3.40) / (3.99 - 3.40));
    const beat = 0.5 + 0.5 * sin(tt * TAU * (3.0 + heat * 14.0));
    const hc = (0.13 + 0.07 * beat) * (1.0 - 0.5 * close);
    put(0.0, aim, 0.0, hc, hc, hc, tt * 6.0,
        heat > 0.5 ? M_STROBE : M_PULSE, G_CORE, 0.0, a * (0.55 + 0.35 * beat));
  }

  /* =======================================================================================
     V. LE GRAIN DE TROP — miroir de ChaosSignatureVfx.renderSandpile().
     Un vrai automate de Bak-Tang-Wiesenfeld : une case a quatre grains se vide sur ses
     quatre voisines. Les losanges concentriques ne sont pas dessines, ils arrivent.
     ======================================================================================= */
  const PILE_N = 19, PILE_GRAINS = 900;
  /* L'automate ne depend QUE du nombre de grains : sans ce cache on le relancerait en
     entier a chaque trame pour un resultat identique, et la page tomberait a genoux. */
  let pileCache = null, pileCacheGrains = -1;
  function sandpileGrid(grains) {
    if (pileCache && pileCacheGrains === grains) return pileCache;
    const g = new Int32Array(PILE_N * PILE_N);
    const mid = PILE_N >> 1;
    for (let n = 0; n < grains; n++) {
      g[mid * PILE_N + mid]++;
      let moved = true, guard = 0;
      while (moved && guard++ < 9000) {
        moved = false;
        for (let y = 0; y < PILE_N; y++) {
          for (let x = 0; x < PILE_N; x++) {
            const i = y * PILE_N + x;
            if (g[i] < 4) continue;
            g[i] -= 4; moved = true;
            if (x > 0) g[i - 1]++;
            if (x < PILE_N - 1) g[i + 1]++;
            if (y > 0) g[i - PILE_N]++;
            if (y < PILE_N - 1) g[i + PILE_N]++;
          }
        }
      }
    }
    pileCache = g; pileCacheGrains = grains;
    return g;
  }

  function sandpile(tt, emitPixels) {
    if (emitPixels) return;
    const tick = tt * 200.0;
    const build = clamp01(tick / 150.0);
    const fallen = tick >= 150.0;
    const after = fallen ? clamp01((tick - 150.0) / 50.0) : 0.0;
    const grains = Math.max(1, Math.round(PILE_GRAINS * build * build));
    const g = sandpileGrid(grains);
    const cell = 0.30, mid = (PILE_N - 1) * 0.5;

    for (let y = 0; y < PILE_N; y++) {
      for (let x = 0; x < PILE_N; x++) {
        const hh = g[y * PILE_N + x];
        if (hh <= 0) continue;
        const fx = (x - mid) * cell, lz = (y - mid) * cell;
        const dist = Math.hypot(fx, lz);
        for (let k = 0; k < hh; k++) {
          /* Depart immediat : after*after donnerait une avalanche qui demarre en
             douceur, c'est-a-dire pas une avalanche. */
          /* LE FRONT RAYONNE DEPUIS LE POINT DE CHUTE : une case ne lache que
             lorsque le front l'atteint, et le front sort du centre, la ou le grain est
             tombe. C'est le titre rendu visible. */
          const front = after * 7.5;
          const local = fallen ? clamp01((front - dist) / 1.30) : 0.0;
          const blow = fallen ? sqrt(local) * (0.60 + 0.40 * local) * (1.0 + k * 0.22) : 0.0;
          const hop = fallen ? sin(Math.min(1.0, blow) * TAU * 0.5) * (0.9 + k * 0.40) : 0.0;
          const dirx = dist > 0.001 ? fx / dist : 0.3;
          const dirz = dist > 0.001 ? lz / dist : 0.3;
          const px = fx + dirx * blow * 4.2;
          const pz = lz + dirz * blow * 4.2;
          let py = GROUND + 0.10 + k * 0.19 + hop;
          if (py < GROUND + 0.08) py = GROUND + 0.08;
          /* Une case qui n'a pas encore lache garde sa couleur de repos, sans quoi
             toute la plaque virerait a la braise d'un coup et le front serait invisible. */
          const gone = fallen && local > 0.02;
          const stress = build * build * (k >= 2 ? 1.0 : 0.25);
          const shake = gone ? 0.0 : stress * 0.045 * sin(tt * 62.0 + x * 2.3 + y * 1.7 + k);
          const fade = gone ? (1.0 - local * 0.92) : (0.55 + 0.45 * build);
          /* La hauteur est portee par la COULEUR : vue de dessus, une plaque de trois
             crans n'a pas de relief, donc sans code couleur le motif ne se lit pas. */
          put(px + shake, py, pz + shake, 0.125, 0.095, 0.125, 0.0,
              gone ? M_EMBER : ((hh === 3) ? M_PULSE : M_TINTED),
              (hh === 3) ? G_MID : (hh === 2 ? G_BODY : G_DEEP),
              gone ? H_EMBER_HUE : ((hh === 3) ? H_ROSE : H_VIOLET), a * fade * 0.95);
        }
      }
    }

    /* LES EBOULEMENTS DE DEMONSTRATION. Six cases lachent sous les yeux pendant la
       montee : la source blanchit et QUATRE grains partent vers ses quatre voisines.
       C'est la regle entiere de l'automate, montree au lieu d'etre ecrite. */
    if (!fallen) {
      for (let ev = 0; ev < 6; ev++) {
        const begin = 16.0 + ev * 21.0;
        const age = tick - begin;
        if (age < 0.0 || age > 13.0) continue;
        const u = age / 13.0;
        const pop = sin(u * TAU * 0.5);
        const sx = 3 + Math.floor(hash(ev * 13 + 1, salt) * (PILE_N - 6));
        const sy = 3 + Math.floor(hash(ev * 13 + 2, salt) * (PILE_N - 6));
        const bx = (sx - mid) * cell, bz = (sy - mid) * cell;
        const by = GROUND + 0.10 + 2.0 * 0.19;
        put(bx, by, bz, 0.145, 0.110, 0.145, 0.0, M_STROBE, G_CORE, 0.0, a * pop * 0.95);
        for (let d = 0; d < 4; d++) {
          const ox = (d === 0) ? 1.0 : (d === 1 ? -1.0 : 0.0);
          const oz = (d === 2) ? 1.0 : (d === 3 ? -1.0 : 0.0);
          const slide = u * cell;
          put(bx + ox * slide, by + 0.16 * pop, bz + oz * slide,
              0.062, 0.062, 0.062, u * 6.0, M_PULSE, G_FLASH, H_ROSE, a * pop * 0.90);
        }
      }
    }

    /* LE GRAIN QUI VA TOMBER : gros, seul, avec son fil a plomb et sa cible au sol.
       Au premier jet il faisait quatorze centiemes de bloc au milieu de sept cents
       cubes de plaque -- l'objet qui donne son nom a l'animation etait invisible. */
    if (!fallen) {
      const drop = 4.6 - 4.1 * build;
      const gy = GROUND + 1.0 + drop;
      const gh = 0.20 + 0.09 * build;
      put(0.0, gy, 0.0, gh, gh, gh, tt * 14.0,
          M_STROBE, G_CORE, 0.0, a * (0.55 + 0.45 * build));
      const sh = gh * 1.95;
      put(0.0, gy, 0.0, sh, sh, sh, -tt * 9.0,
          M_PULSE, G_MASK, H_ROSE, a * (0.20 + 0.26 * build));
      const topY = GROUND + 0.70;
      for (let i = 1; i < 10; i++) {
        const u = i / 10, yy = topY + (gy - topY) * u;
        put(0.0, yy, 0.0, 0.038, 0.038, 0.038, 0.0,
            M_MONO, G_MASK, 0.0, a * 0.34 * (1.0 - u * 0.55));
      }
      const ring = 1.05 - 0.62 * build;
      for (let i = 0; i < 14; i++) {
        const ang = i / 14 * TAU + tt * 1.4;
        put(cos(ang) * ring, GROUND + 0.78, sin(ang) * ring, 0.052, 0.052, 0.052, ang,
            M_PULSE, G_GLASS, H_ROSE, a * (0.30 + 0.45 * build));
      }
    }

    /* L'ONDE DE DECHARGE au sol. */
    if (fallen && after < 0.85) {
      const wave = 0.5 + after * 7.0;
      const f = 1.0 - after / 0.85;
      for (let i = 0; i < 40; i++) {
        const ang = i / 40 * TAU;
        const hw = 0.155 * f + 0.03;
        put(cos(ang) * wave, GROUND + 0.10, sin(ang) * wave, hw, 0.055, hw, ang,
            (i % 4 === 0) ? M_EMBER : M_MONO, (i % 4 === 0) ? G_FLASH : G_MID,
            H_EMBER_HUE, a * f * 0.92);
      }
    }
  }

  const FN = [kaleido, entropy, bigbang, feigenbaum, sandpile];
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

  } else if (variant === 3) {
    /* ---- CASCADE : SIX VAGUES, SIX GESTES, ET TOUS FRAPPENT LA CIBLE -------------
       Le premier jet faisait tomber les impacts SUR LE DIAGRAMME, au point de
       l'attracteur correspondant au rang du coup. L'idee etait jolie et le resultat
       n'attaquait rien : la matiere se deployait sans jamais traverser le corps vise.
       Une figure n'est pas une attaque. Ici les soixante-trois coups arrivent tous sur
       la cible, a hauteur de torse, et repartent de l'autre cote.
         0 : 1 coup   la lance        longitudinale, de part en part
         1 : 2 coups  les machoires   verticale, haut et bas, sur le torse
         2 : 4 coups  les anneaux     quatre cercles verticaux qui se croisent dedans
         3 : 8 coups  la gerbe        frontale, elle entre devant et sort derriere
         4 : 16 coups le tourbillon   rotatif, deux tours puis la coupe
         5 : 32 coups la grele        verticale saturee, plus une onde au sol      */
    const BEATS_F = [24, 48, 70, 88, 102, 112];
    const aim = 1.10;
    for (let k = 0; k < BEATS_F.length; k++) {
      const since = nowT - BEATS_F[k];
      const windK = 8.0 - k * 0.9, burstK = 7.0 - k * 0.7;
      if (since < -windK || since > burstK) continue;
      const pre = since < 0;
      const w = pre ? 1.0 + since / windK : 1.0;
      const r01 = pre ? 0.0 : since / burstK;
      const e = w * w;
      const fade = 1.0 - r01 * 0.88;

      if (k === 0) {
        /* LA LANCE : le seul geste LONGITUDINAL de la signature. */
        if (pre) {
          const tip = -7.0 + 7.0 * e;
          for (let j = 0; j < 20; j++) {
            const back = tip - j * 0.33, hh = 0.17 - j * 0.0065;
            put(back, aim, 0.0, hh * 1.9, hh, hh, spin * 0.5,
                (j === 0) ? M_STROBE : M_DUAL, (j === 0) ? G_CORE : G_FRAME,
                H_CYAN, a * w * (1.0 - j * 0.042));
          }
        } else {
          const head = -1.2 + 5.8 * r01;             /* elle TRAVERSE */
          for (let j = 0; j < 24; j++) {
            const back = head - j * 0.30, hh = 0.155 - j * 0.0050;
            put(back, aim, 0.0, hh * 2.1, hh, hh, spin,
                (j < 2) ? M_STROBE : M_SPECTRUM, (j < 2) ? G_CORE : G_GLASS,
                H_CYAN, a * fade * (1.0 - j * 0.035));
          }
          for (let j = 0; j < 16; j++) {
            const ca = j / 16 * TAU, rr = 0.20 + 1.45 * sqrt(r01);
            put(-0.10, aim + sin(ca) * rr, cos(ca) * rr, 0.085, 0.085, 0.085, ca,
                M_DUAL, G_FRAME, H_CYAN, a * fade * 0.85);
          }
        }

      } else if (k === 1) {
        /* LES MACHOIRES : deux lames plates qui se referment sur le torse. */
        for (let jaw = 0; jaw < 2; jaw++) {
          const dir = (jaw === 0) ? 1.0 : -1.0;
          const gap = pre ? (3.40 * (1.0 - e)) : 0.0;
          const yy = aim + dir * gap;
          for (let j = 0; j < 30; j++) {
            const u = j / 29 - 0.5;
            const bite = pre ? 1.0 : (1.0 - 0.55 * r01);
            put(u * 2.90 * bite, yy, dir * 0.18 * (pre ? 1.0 : (1.0 - r01)),
                0.075, 0.050, 0.165, 0.0,
                (j % 6 === 0) ? M_STROBE : M_SPECTRUM,
                (j % 6 === 0) ? G_CORE : G_FRAME, H_MAGENTA,
                a * (pre ? w : fade) * 0.95);
          }
        }
        if (!pre) {
          for (let j = 0; j < 34; j++) {
            const ca = j / 34 * TAU + spin, rr = 0.25 + 2.55 * sqrt(r01);
            const hw = 0.115 * fade + 0.02;
            put(cos(ca) * rr, aim, sin(ca) * rr, hw, 0.045, hw, ca,
                M_STROBE, G_GLASS, H_MAGENTA, a * fade * 0.90);
          }
        }

      } else if (k === 2) {
        /* LES ANNEAUX : quatre cercles verticaux qui se croisent DANS le corps. */
        const rad = pre ? (3.30 - 2.95 * e) : (0.35 + 2.10 * r01);
        for (let ring = 0; ring < 4; ring++) {
          const az = ring * (TAU / 8.0) + salt * 0.03;
          const ca0 = cos(az), sa0 = sin(az);
          for (let j = 0; j < 18; j++) {
            const p = j / 18 * TAU, ox = cos(p) * rad, oy = sin(p) * rad;
            put(ox * ca0, aim + oy, ox * sa0, 0.095, 0.095, 0.095, p + spin,
                (ring % 2 === 0) ? M_DUAL : M_PULSE,
                (j % 5 === 0) ? G_GLASS : G_FRAME,
                (ring % 2 === 0) ? H_BLUE : H_VIOLET, a * (pre ? w : fade) * 0.92);
          }
        }
        if (!pre) {
          const cf = 1.0 - clamp01(r01 / 0.5);
          if (cf > 0.0) {
            const hc = 0.14 + 0.46 * cf;
            put(0.0, aim, 0.0, hc, hc, hc, spin * 6.0, M_STROBE, G_CORE, 0.0, a * cf);
          }
        }

      } else if (k === 3) {
        /* LA GERBE FRONTALE : elle entre par devant et ressort derriere. */
        for (let i = 0; i < 8; i++) {
          const ca = i / 8 * TAU + 0.21;
          const travel = pre ? (-4.30 + 4.10 * e) : (-0.20 + 4.00 * r01);
          for (let j = 0; j < 7; j++) {
            const back = travel - j * 0.27;
            const sp = 1.15 * Math.min(1.0, Math.max(0.06, (0.30 - back) * 0.42));
            const hh = 0.125 - j * 0.011;
            put(back, aim + sin(ca) * sp, cos(ca) * sp, hh * 1.7, hh, hh, ca,
                (j === 0) ? M_STROBE : M_SPECTRUM, (j === 0) ? G_CORE : G_MID,
                H_ROSE, a * (pre ? w : fade) * (1.0 - j * 0.09));
          }
        }

      } else if (k === 4) {
        /* LE TOURBILLON : seize arcs qui enroulent la cible avant de la couper. */
        const wind = pre ? (1.0 - e) : 0.0;
        for (let i = 0; i < 16; i++) {
          const base = i / 16 * TAU;
          for (let j = 0; j < 9; j++) {
            const u = j / 8;
            const ang = base + u * 2.6 + wind * TAU * 2.0 + spin * 2.2;
            const rr = pre ? (2.60 * wind + 0.45 + u * 0.5)
                           : (0.40 + 3.20 * r01 + u * 0.35);
            const yy = aim + (u - 0.5) * (pre ? 1.30 : 0.55);
            const hw = 0.105 - j * 0.006;
            put(cos(ang) * rr, yy, sin(ang) * rr, hw, 0.062, hw, ang,
                (j === 0) ? M_STROBE : M_EMBER, (j === 0) ? G_FLASH : G_MID,
                H_EMBER_HUE, a * (pre ? w : fade) * (0.95 - j * 0.055));
          }
        }

      } else {
        /* LA GRELE : a trente-deux, on ne vise plus, on noie. */
        for (let i = 0; i < 32; i++) {
          const ang = i * 2.39996 + salt * 0.05;
          const rr = 0.18 + 1.30 * hash(i, salt + 17);
          const lag = hash(i * 7, salt + 23) * 0.55;
          const fall = pre ? clamp01((e - lag) / 0.45) : 1.0;
          const yy = aim + 3.60 * (1.0 - fall) + 0.55 - r01 * 1.50;
          for (let j = 0; j < 4; j++) {
            const hh = 0.105 - j * 0.016;
            put(cos(ang) * rr, yy + j * 0.30, sin(ang) * rr, hh, hh * 1.6, hh, ang,
                (j === 0) ? M_STROBE : M_SPECTRUM, (j === 0) ? G_CORE : G_GLASS,
                0.0, a * (pre ? w : fade) * (0.95 - j * 0.14));
          }
        }
        if (!pre) {
          const wv = 0.4 + 3.6 * sqrt(r01);
          for (let j = 0; j < 30; j++) {
            const ca = j / 30 * TAU, hw = 0.12 * fade + 0.02;
            put(cos(ca) * wv, 0.10, sin(ca) * wv, hw, 0.05, hw, ca,
                M_SPECTRUM, G_FLASH, 0.0, a * fade * 0.85);
          }
        }
      }
    }

    /* LA FENETRE DE PERIODE TROIS — 132 / 140 / 148. En plein chaos l'ordre revient :
       la carte logistique retrouve vraiment une periode exacte vers r = 3,83. Trois
       anneaux nets se detachent de la couronne et se REFERMENT sur la victime.
       La premiere version plantait ici une colonne verticale au-dessus de la cible :
       une tour qui flottait sans rapport avec la figure. */
    for (let s2 = 0; s2 < 3; s2++) {
      const since = nowT - (132 + s2 * 8);
      if (since < -5.0 || since > 6.0) continue;
      const pre = since < 0;
      const w = pre ? 1.0 + since / 5.0 : 1.0;
      const r01 = pre ? 0.0 : since / 6.0;
      const fade = pre ? w : (1.0 - r01 * 0.88);
      for (let ring = 0; ring < 3; ring++) {
        const base = 0.75 + ring * 0.95;
        const rad = pre ? base : base * (1.0 - r01 * 0.92);
        for (let j = 0; j < 22; j++) {
          const th = j / 22 * TAU + ring * 0.42 + spin * 0.8;
          put(cos(th) * rad, aim + sin(th * 2.0 + ring) * 0.11, sin(th) * rad,
              0.105, 0.140, 0.105, th,
              (j % 7 === 0) ? M_STROBE : M_DUAL, (j % 7 === 0) ? G_CORE : G_FRAME,
              0.46 + ring * 0.21, a * fade * 0.95);
        }
      }
      if (!pre && r01 > 0.55) {
        const g = 1.0 - (r01 - 0.55) / 0.45;
        const hc = 0.16 + 0.42 * g;
        put(0.0, aim, 0.0, hc, hc, hc, spin * 7.0, M_STROBE, G_CORE, 0.0, a * g * 0.95);
      }
    }

  } else if (variant === 4) {
    /* ---- GRAIN DE TROP : trois frolements, puis une seule fois tout ---------------
       Les trois premiers sont VOLONTAIREMENT maigres : ils disent que ca tient encore.
       Le quatrieme dure vingt-cinq ticks au lieu de six, et c'est le seul evenement. */
    const BEATS_S = [52, 92, 126, 150];
    for (let k = 0; k < 3; k++) {
      const since = nowT - BEATS_S[k];
      if (since < 0.0 || since > 6.0) continue;
      const u = since / 6.0;
      const f = (1.0 - u) * (1.0 - u);
      const side = k * 2.39996 + salt * 0.11;
      for (let i = 0; i < 14; i++) {
        const along = i / 13;
        const ang = side + (along - 0.5) * 0.55;
        const rr = 0.45 + along * (1.3 + u * 2.4);
        const hw = 0.105 - along * 0.030;
        put(cos(ang) * rr, 0.55 - along * 0.42 + 0.18 * (1.0 - u), sin(ang) * rr,
            hw, 0.070, hw, ang, M_MONO, i < 3 ? G_MID : G_DEEP, 0.0, a * f * 0.85);
      }
    }

    const since = nowT - BEATS_S[3];
    if (since >= -6.0 && since <= 25.0) {
      if (since < 0.0) {
        /* LE DERNIER SOUFFLE : six ticks ou rien ne bouge, et c'est ce silence qui fait
           le coup. */
        const w = 1.0 + since / 6.0, g2 = w * w * w, h = 0.10 + 0.55 * g2;
        put(0.0, 1.25, 0.0, h, h, h, spin * 10.0, M_STROBE, G_CORE, 0.0, a * g2);
      } else {
        const u = since / 25.0;
        const f = (1.0 - u) * (1.0 - u * 0.45);
        /* 1. LE CRATERE. */
        for (let i = 0; i < 28; i++) {
          const ang = i / 28 * TAU + spin * 0.3;
          const rr = 0.35 + 1.05 * sqrt(u);
          const hw = 0.175 * f + 0.03;
          put(cos(ang) * rr, 0.12 + 0.55 * (1.0 - u), sin(ang) * rr, hw, 0.090, hw, ang,
              M_EMBER, (i % 3 === 0) ? G_FLASH : G_MID, H_EMBER_HUE, a * f * 0.95);
        }
        /* 2. LA COULEE : huit langues qui descendent les flancs et vont loin. */
        for (let lane = 0; lane < 8; lane++) {
          const ang = lane / 8 * TAU + salt * 0.05;
          for (let i = 0; i < 16; i++) {
            const along = i / 15;
            const lead = clamp01(u * 2.2 - along * 0.9);
            if (lead <= 0.02) continue;
            const rr = 0.5 + along * (1.6 + u * 6.2);
            const wob = 0.22 * sin(along * 7.0 + lane);
            const hw = 0.150 - along * 0.055;
            put(cos(ang) * rr - sin(ang) * wob,
                0.14 + 0.55 * (1.0 - along) * (1.0 - u * 0.6),
                sin(ang) * rr + cos(ang) * wob, hw, 0.085, hw, ang,
                (i % 5 === 0) ? M_EMBER : M_MONO,
                (i % 5 === 0) ? G_FLASH : (along < 0.4 ? G_MID : G_DEEP),
                H_EMBER_HUE, a * f * lead * 0.95);
          }
        }
        /* 3. LA POUSSIERE : elle monte pendant que tout descend. */
        for (let i = 0; i < 34; i++) {
          const ang = i * 2.39996 + salt * 0.09;
          const seedU = ((i * 17) % 23) / 23;
          const rr = (0.4 + seedU * 2.6) * (0.4 + u * 1.9);
          const h = 0.060 + 0.055 * seedU;
          put(cos(ang) * rr, 0.20 + u * (1.4 + seedU * 2.6), sin(ang) * rr, h, h, h,
              ang * 3.0 + spin, M_TINTED, G_DEEP, H_EMBER_HUE,
              a * f * (0.40 + 0.45 * seedU));
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
