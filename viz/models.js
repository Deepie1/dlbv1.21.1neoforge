// =============================================================================
//  ORNEMENTS COSMETIQUES DU DEEP LUCKY BLOCK — MODELES EN PETITS CUBES
//
//  REECRITURE COMPLETE. Les modeles precedents reproduisaient fidelement le
//  code Java, y compris son defaut : le nombre de cubes d'un anneau etait une
//  CONSTANTE EN DUR (10, 12, 14, 16...) sans aucun rapport avec sa
//  circonference. Un anneau de rayon 0,95 avec 10 cubes de 0,077 de large est
//  vide a 87 %. Ce n'etait pas un halo, c'etaient dix points sur un cercle.
//
//  REGLE N.1 — AUCUN ECART. Le nombre de cubes se DEDUIT de la circonference :
//      n = ceil(2*PI*r / (w * 0.9))
//  Le facteur 0,9 impose un recouvrement de 10 % : meme en escalier, deux
//  cubes voisins se touchent toujours. Aucune forme de ce fichier ne choisit
//  son nombre de cubes a la main.
//
//  REGLE N.2 — FIN. Un halo est un TRAIT, pas un tube. Largeur de reference
//  w = 0,042 bloc (= 0,67 pixel Minecraft). Les anneaux sont plus larges que
//  hauts (h = 0,55 w) : vus de cote ils disparaissent presque, vus d'en haut
//  ils sont nets. C'est ce qui fait lire un anneau comme un halo.
//
//  REGLE N.3 — NE JAMAIS GENER LE JOUEUR. Ces effets sont CONSTANTS : ils
//  tournent en permanence autour de lui. Rien ne doit traverser son champ de
//  vision. La zone interdite est definie par ZONE_REGARD ci-dessous et chaque
//  modele est verifie contre elle par le banc. Un ornement de tete reste
//  au-dessus du crane (y >= 1.95), un ornement de sol reste sous le genou
//  (y <= 0.45), un ornement de dos reste derriere (z <= -0.10).
//
//  REGLE N.4 — CHACUN SA SILHOUETTE. Douze anneaux de rayons differents, ce
//  sont douze fois le meme objet. Chaque ornement a ici une forme qu'on
//  reconnait de loin : couronne, lames, torsade, coupole, piques, ailes...
// =============================================================================

const TAU = Math.PI * 2;
const H = 1.8;                 // hauteur du joueur
const YEUX = 1.62;             // hauteur des yeux
const CRANE = 1.80;            // sommet du crane

// ORIENTATION. Le joueur REGARDE VERS +Z. Donc :
//    z > 0  = devant lui   (rayon tendu, cible d'un eclat)
//    z < 0  = derriere lui (ailes, rubans)
// Le premier jet avait les ailes a z negatif ET un viewer ou le regard pointait
// vers -Z : les ailes etaient donc plantees DEVANT le visage. C'est exactement
// le << ca me passe devant les yeux >> a ne jamais produire.
//
/** Zone que rien de constant ne doit traverser : le champ de vision. */
// Plafond a 1.80 = le sommet du crane : au-dessus, rien ne peut masquer la vue,
// c'est pourquoi une couronne posee a 1.845 est legitime.
const ZONE_REGARD = { rayon: 0.55, bas: 1.10, haut: CRANE, devant: -0.05 };

/** Largeur de reference d'un trait de halo, en blocs. */
const W = 0.042;

// --- outils ------------------------------------------------------------------

function mixRgb(a, b, t) {
  const ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
  const br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
  return (Math.round(ar + (br - ar) * t) << 16)
       | (Math.round(ag + (bg - ag) * t) << 8)
       | Math.round(ab + (bb - ab) * t);
}

/** cube : centre + DEMI-extensions, comme emitVoxelCube(cx,cy,cz,hx,hy,hz,rgb,a). */
function cube(out, x, y, z, hx, hy, hz, rgb, alpha) {
  out.push({ x, y, z, hx, hy, hz, rgb, a: Math.max(0, Math.min(1, alpha / 255)) });
}

/** Nombre de cubes pour couvrir une longueur sans trou. */
function pas(longueur, w) {
  return Math.max(3, Math.ceil(longueur / (w * 0.9)));
}

/**
 * Anneau PLEIN et FIN.
 *
 * @param o  { y, r, w, h, rgbA, rgbB, alpha, phase, arc, depart, glint, glintRgb, bob }
 *           arc    : portion du cercle en tours (1 = complet, 0.5 = demi)
 *           glint  : position 0..1 d'un eclat qui parcourt l'anneau (null = aucun)
 */
function anneau(out, o) {
  const r = o.r, w = o.w || W, h = o.h !== undefined ? o.h : (o.w || W) * 0.55;
  const arc = o.arc === undefined ? 1 : o.arc;
  const depart = o.depart || 0;
  const n = pas(TAU * r * arc, w);
  const rgbA = o.rgbA, rgbB = o.rgbB === undefined ? o.rgbA : o.rgbB;
  for (let i = 0; i < n; i++) {
    const u = n === 1 ? 0 : i / (n - (arc === 1 ? 0 : 1));
    const ang = (o.phase || 0) + depart + TAU * arc * u;
    // Degrade doux A -> B -> A sur le tour complet (jamais de saut de couleur).
    let tri = u < 0.5 ? u * 2 : 2 - u * 2;
    tri = tri * tri * (3 - 2 * tri);
    let rgb = mixRgb(rgbA, rgbB, tri);
    let a = o.alpha;
    let hx = w / 2, hy = h / 2;
    if (o.glint !== undefined && o.glint !== null) {
      // Un eclat qui fait lentement le tour : du detail sans aucun trou.
      let d = Math.abs(u - o.glint);
      if (d > 0.5) d = 1 - d;
      const g = Math.max(0, 1 - d * 9);
      if (g > 0) {
        rgb = mixRgb(rgb, o.glintRgb || 0xFFFFFF, g * 0.85);
        a = Math.min(255, a + g * 70);
        hx *= 1 + g * 0.35; hy *= 1 + g * 0.30;   // un eclat brille, il n'epaissit pas
      }
    }
    const y = o.y + (o.bob ? Math.sin(ang * 3 + (o.phase || 0)) * o.bob : 0);
    cube(out, Math.cos(ang) * r, y, Math.sin(ang) * r, hx, hy, hx, rgb, a);
  }
}

/** Segment plein entre deux points : la brique de toutes les formes droites. */
function segment(out, x1, y1, z1, x2, y2, z2, w, rgbA, rgbB, aA, aB) {
  const dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
  const n = pas(Math.hypot(dx, dy, dz), w);
  for (let i = 0; i < n; i++) {
    const u = n === 1 ? 0 : i / (n - 1);
    cube(out, x1 + dx * u, y1 + dy * u, z1 + dz * u,
      w / 2, w / 2, w / 2,
      mixRgb(rgbA, rgbB === undefined ? rgbA : rgbB, u),
      aA + (((aB === undefined) ? aA : aB) - aA) * u);
  }
}

/** Arc meridien : du plan horizontal vers le zenith (pour les coupoles). */
function meridien(out, cy, r, azimut, du, au, w, rgbA, rgbB, alpha) {
  const n = pas(r * Math.abs(au - du), w);
  for (let i = 0; i < n; i++) {
    const u = i / (n - 1);
    const e = du + (au - du) * u;
    const rr = Math.cos(e) * r;
    cube(out, Math.cos(azimut) * rr, cy + Math.sin(e) * r, Math.sin(azimut) * rr,
      w / 2, w / 2, w / 2, mixRgb(rgbA, rgbB, u), alpha);
  }
}

// =============================================================================
//  LES 12 ORNEMENTS PERMANENTS
// =============================================================================

const MODELS = {

  // --- 1. halo generique -----------------------------------------------------
  halo_continu: {
    titre: "Halo générique continu",
    famille: "permanent",
    accorde: "Arme à aura : trident enchanté en main, ou marque dlbWeaponAura (15 % en mêlée, 40 % à distance). Exclusif : ne sort que si aucun combo cosmétique n'est actif.",
    note: "UN anneau, fin et plein, posé à plat au-dessus du crâne. Les trois anneaux d'avant — dont un AU SOL, à 1,9 bloc de celui de la tête — n'avaient aucun sens : un halo est un objet unique. Un éclat en fait lentement le tour, c'est tout le détail dont il a besoin.",
    build(t) {
      const o = [];
      const bob = Math.sin(t * 1.2) * 0.018;
      anneau(o, {
        y: 2.00 + bob, r: 0.28, w: 0.042,
        rgbA: 0xFFF3C8, rgbB: 0xFFC63D, alpha: 240,
        phase: t * 0.45, glint: (t * 0.19) % 1, glintRgb: 0xFFFFFF
      });
      return o;
    }
  },

  // --- 2. #110 Avatar --------------------------------------------------------
  avatar_110: {
    titre: "#110 Avatar Suprême du Deep Lucky",
    famille: "permanent",
    accorde: "Combo #110 — le plus haut du registre. Exclusif face à #097/#098 et à Roi (chaîne else if).",
    note: "Couronne solaire : l'anneau plein, et douze rayons qui s'en échappent vers l'extérieur en s'affinant. L'ancienne version empilait deux anneaux concentriques — de loin, un seul trait épais. Les rayons donnent une silhouette qu'on reconnaît immédiatement.",
    build(t) {
      const o = [];
      const ph = t * 0.30;
      anneau(o, { y: 2.02, r: 0.30, w: 0.046, rgbA: 0xFFF6D0, rgbB: 0xFFC63D,
                  alpha: 250, phase: ph, glint: (t * 0.14) % 1 });
      for (let i = 0; i < 12; i++) {
        const ang = ph + TAU * i / 12;
        const lg = (i % 3 === 0) ? 0.17 : 0.10;       // rayons longs et courts alternés
        const puls = 1 + Math.sin(t * 2.2 + i) * 0.10;
        segment(o,
          Math.cos(ang) * 0.31, 2.02, Math.sin(ang) * 0.31,
          Math.cos(ang) * (0.31 + lg * puls), 2.02 + lg * 0.45 * puls, Math.sin(ang) * (0.31 + lg * puls),
          0.034, 0xFFE9A0, 0xFFFFFF, 235, 110);
      }
      return o;
    }
  },

  // --- 3. #097 Armure Eternelle ---------------------------------------------
  eternelle_097: {
    titre: "#097 Armure Éternelle Transcendante",
    famille: "permanent",
    accorde: "Combo #097. Partageait jusqu'ici EXACTEMENT la même branche de code que #098 — deux combos distincts, un seul visuel.",
    note: "Torsade sans fin : deux fils pleins qui s'enroulent l'un autour de l'autre, sans début ni fin visible. C'est l'éternité, pas un cercle de plus. Le second fil est en opposition de phase sur la verticale.",
    build(t) {
      const o = [];
      const r = 0.26, ph = t * 0.38;
      for (const fil of [0, 1]) {
        const n = pas(TAU * r, 0.038);
        for (let i = 0; i < n; i++) {
          const u = i / n;
          const ang = ph + TAU * u;
          const dy = Math.sin(TAU * u * 3 + fil * Math.PI) * 0.040;
          const rr = r + Math.cos(TAU * u * 3 + fil * Math.PI) * 0.022;
          cube(o, Math.cos(ang) * rr, 2.00 + dy, Math.sin(ang) * rr,
            0.019, 0.019, 0.019,
            mixRgb(fil ? 0xFFB020 : 0xFFE9A0, 0xFFF8E0, (Math.sin(TAU * u * 2 + t) + 1) / 2),
            fil ? 215 : 240);
        }
      }
      return o;
    }
  },

  // --- 4. #098 Dieu de la Lame ----------------------------------------------
  dieu_lame_098: {
    titre: "#098 Dieu de la Lame Suprême",
    famille: "permanent",
    accorde: "Combo #098. Doit cesser de partager la branche de #097 : ce sont deux récompenses différentes.",
    note: "Six lames en étoile, pas un anneau. Chaque lame est une rangée pleine qui s'affine jusqu'à la pointe, dos épais et tranchant fin. La couronne tourne lentement et les lames respirent en alternance.",
    build(t) {
      const o = [];
      const ph = -t * 0.34;
      for (let i = 0; i < 6; i++) {
        const ang = ph + TAU * i / 6;
        const respire = 1 + Math.sin(t * 1.8 + i * 1.05) * 0.13;
        const lg = 0.30 * respire;
        const n = pas(lg, 0.040);
        for (let k = 0; k < n; k++) {
          const u = k / (n - 1);
          const w = 0.052 * (1 - u * 0.78);           // dos large -> pointe fine
          const d = 0.10 + lg * u;
          cube(o, Math.cos(ang) * d, 2.01 + u * 0.035, Math.sin(ang) * d,
            w / 2, w / 2 * 0.5, w / 2,
            mixRgb(0xFFE9A0, 0xFFFFFF, u), 245 - u * 55);
        }
      }
      return o;
    }
  },

  // --- 5. #101 Archange : ailes ---------------------------------------------
  archange_101: {
    titre: "#101 Archange du Jugement Solaire",
    famille: "permanent",
    accorde: "Combo #101. Ailes permanentes, quel que soit l'état du joueur.",
    note: "Ailes à membrane PLEINE : chaque barbe est une colonne continue, les barbes se touchent. L'ancienne aile était une grille de 5 × 3 points espacés — on voyait le ciel au travers. Tout est derrière le dos (z ≤ -0,16) : rien ne passe devant les yeux.",
    build(t) { return ailes(t, 1.0, 0xFFE9A0, 0xFFFBEA, 6, 0.60); }
  },

  // --- 6. Endurance Ailee ----------------------------------------------------
  winged_endurance: {
    titre: "Endurance Ailée (enchantement d'armure)",
    famille: "permanent",
    accorde: "Enchantement d'armure toughness/endurance ailée. Ne sort QUE si le joueur ne touche pas le sol ou plane en élytre.",
    note: "Mêmes règles, silhouette différente : ailes courtes et arrondies, cinq barbes, bleu givre. Il ne s'agit plus de la même aile en plus petit — la découpe du bord est franchement distincte de celle de l'Archange.",
    build(t) { return ailes(t, 0.74, 0xCFE8FF, 0xFFFFFF, 5, 0.40, true); }
  },

  // --- 7. #106 Seigneur de Guerre -------------------------------------------
  seigneur_guerre_106: {
    titre: "#106 Seigneur de Guerre de l'Apocalypse",
    famille: "permanent",
    accorde: "Combo #106. Ornement de SOL — ne monte jamais vers le torse.",
    note: "Cercle de ban : l'anneau plein au sol, et quatre piques plantées aux points cardinaux. Tout reste sous 0,30 bloc — un ornement de pieds n'a rien à faire à hauteur de tête, et réciproquement.",
    build(t) {
      const o = [];
      const ph = -t * 0.26;
      anneau(o, { y: 0.045, r: 0.58, w: 0.044, h: 0.030,
                  rgbA: 0xFF7A2E, rgbB: 0xFFC46B, alpha: 205, phase: ph,
                  glint: (t * 0.11) % 1, glintRgb: 0xFFD9A0 });
      for (let i = 0; i < 4; i++) {
        const ang = ph + TAU * i / 4 + 0.39;
        const haut = 0.20 + Math.sin(t * 1.5 + i * 1.57) * 0.045;
        segment(o, Math.cos(ang) * 0.58, 0.045, Math.sin(ang) * 0.58,
          Math.cos(ang) * 0.54, 0.045 + haut, Math.sin(ang) * 0.54,
          0.038, 0xFFC46B, 0xFF7A2E, 220, 90);
      }
      return o;
    }
  },

  // --- 8. #062 Peste ---------------------------------------------------------
  peste_062: {
    titre: "#062 Peste Ambulante",
    famille: "permanent",
    accorde: "Combo #062. Ornement de SOL.",
    note: "Marécage, pas un anneau : trois arcs pleins décalés qui ne se referment jamais, et des bulles qui crèvent en montant. Rien ne dépasse 0,40 bloc. C'est la seule forme du lot qui soit volontairement irrégulière — une peste n'est pas symétrique.",
    build(t) {
      const o = [];
      const arcs = [[0.30, 0.62, 0.0], [0.44, 0.48, 2.1], [0.56, 0.38, 4.3]];
      for (let k = 0; k < arcs.length; k++) {
        const [r, portion, dep] = arcs[k];
        anneau(o, {
          y: 0.035 + k * 0.012, r, w: 0.040, h: 0.026,
          rgbA: 0x6BAF1E, rgbB: 0x3E7A0C, alpha: 170 - k * 18,
          arc: portion, depart: dep, phase: t * (0.17 + k * 0.05) * (k % 2 ? -1 : 1)
        });
      }
      // Bulles : de petites GRAPPES pleines, jamais un pixel isole perdu en l'air.
      for (let i = 0; i < 4; i++) {
        const p = ((t * 0.42 + i * 0.25) % 1);
        const ang = i * 2.51 + Math.sin(i * 7.3) * 2;
        const r = 0.24 + (i % 3) * 0.13;
        const d = 0.032 * (1 - p * 0.40);
        const bx = Math.cos(ang) * r, by = 0.03 + p * 0.30, bz = Math.sin(ang) * r;
        const al = Math.round(195 * (1 - p));
        const rgb = mixRgb(0x8FD02A, 0x3E7A0C, p);
        // petit amas de 5 cubes jointifs : un noyau et ses quatre contacts
        cube(o, bx, by, bz, d, d, d, rgb, al);
        for (const [dx, dy, dz] of [[1,0,0],[-1,0,0],[0,0,1],[0,0,-1]]) {
          cube(o, bx + dx * d * 1.6, by + dy * d * 1.6, bz + dz * d * 1.6,
            d * 0.72, d * 0.72, d * 0.72, mixRgb(rgb, 0xDFF5A8, 0.4), Math.round(al * 0.8));
        }
      }
      return o;
    }
  },

  // --- 9. Roi ----------------------------------------------------------------
  king: {
    titre: "Roi (enchantement d'armure)",
    famille: "permanent",
    accorde: "Enchantement d'armure « king ». Exclusif face à #110 et #097/#098.",
    note: "Une vraie couronne POSÉE sur le crâne, pas un anneau qui flotte : bandeau plein à 1,84, cinq pointes triangulaires, et elle NE TOURNE PAS. Une couronne qui tourne sur la tête de son porteur n'a aucun sens.",
    build(t) {
      const o = [];
      const puls = (Math.sin(t * 1.6) + 1) / 2;
      anneau(o, { y: 1.845, r: 0.166, w: 0.040, h: 0.044,
                  rgbA: 0xFFD34D, rgbB: 0xFF9500, alpha: 235,
                  glint: (t * 0.13) % 1, glintRgb: 0xFFF6D0 });
      for (let i = 0; i < 5; i++) {
        const ang = TAU * i / 5 - Math.PI / 2;        // une pointe pile devant
        const haut = 0.085 + (i === 0 ? 0.030 : 0);   // la pointe frontale est plus haute
        // pointe triangulaire : trois etages qui retrecissent
        for (let e = 0; e < 3; e++) {
          const u = e / 2;
          const w = 0.044 * (1 - u * 0.62);
          cube(o, Math.cos(ang) * 0.166, 1.872 + haut * u, Math.sin(ang) * 0.166,
            w / 2, 0.018, w / 2, mixRgb(0xFFD34D, 0xFFF6D0, u * (0.35 + puls * 0.4)),
            238 - e * 14);
        }
      }
      return o;
    }
  },

  // --- 10. Esprit Mystique ---------------------------------------------------
  mystic_mind: {
    titre: "Esprit Mystique (enchantement d'armure)",
    famille: "permanent",
    accorde: "Enchantement d'armure « mystic_mind ». Se cumule avec les autres.",
    note: "Deux demi-arcs pleins, croisés et contra-rotatifs, qui dessinent une sphère de pensée au-dessus du crâne. Ils ne se referment jamais : l'esprit tourne, il ne boucle pas. Plancher à 1,97 — aucun passage devant les yeux.",
    build(t) {
      const o = [];
      for (const [sens, inclin, rgbA, rgbB, al] of [
        [1, 0.0, 0xC08BFF, 0x7B3BE8, 225],
        [-1, Math.PI / 2, 0xE2C4FF, 0x9A5BF0, 200]]) {
        const r = 0.27, n = pas(TAU * r * 0.5, 0.036);
        for (let i = 0; i < n; i++) {
          const u = i / (n - 1);
          const a = Math.PI * u;                        // demi-tour
          const ph = t * 0.6 * sens;
          // cercle incline : on tourne le demi-arc autour de l'axe vertical
          const px = Math.cos(a) * r, py = Math.sin(a) * r * 0.62;
          const x = px * Math.cos(inclin + ph), z = px * Math.sin(inclin + ph);
          cube(o, x, 2.03 + py, z, 0.018, 0.018, 0.018,
            mixRgb(rgbA, rgbB, Math.sin(a) ), al);
        }
      }
      return o;
    }
  },

  // --- 11. Egide Radieuse ----------------------------------------------------
  radiant_aegis: {
    titre: "Égide Radieuse (enchantement d'armure)",
    famille: "permanent",
    accorde: "Enchantement d'armure « heart_resonance ». Se cumule.",
    note: "Une coupole, pas un disque : l'anneau équatorial plein, et trois méridiens qui montent au zénith. Une égide est un bouclier — il fallait que ça se voie en silhouette. Base à 1,98, sommet à 2,27.",
    build(t) {
      const o = [];
      const ph = t * 0.33;
      anneau(o, { y: 1.985, r: 0.285, w: 0.040, rgbA: 0xFFF7D9, rgbB: 0xBFE8FF,
                  alpha: 220, phase: ph, glint: (t * 0.16) % 1 });
      for (let i = 0; i < 3; i++) {
        meridien(o, 1.985, 0.285, ph + TAU * i / 3, 0.0, Math.PI / 2,
          0.034, 0xBFE8FF, 0xFFFFFF, 205);
      }
      // la clef de voute
      const p = (Math.sin(t * 1.9) + 1) / 2;
      cube(o, 0, 2.285, 0, 0.026 + p * 0.008, 0.026 + p * 0.008, 0.026 + p * 0.008,
        mixRgb(0xFFF7D9, 0xFFFFFF, p), 240);
      return o;
    }
  },

  // --- 12. Aura de Terreur ---------------------------------------------------
  dread_aura: {
    titre: "Aura de Terreur (enchantement d'armure)",
    famille: "permanent",
    accorde: "Enchantement d'armure « dread_aura ». Ornement de SOL.",
    note: "Herse : dix piques pleines qui sortent de terre, reliées par un filet d'anneau à leur base. Le sommet culmine à 0,42 — sous le genou. L'ancienne version était un anneau flottant à 0,32 avec 86 % de vide, qui ne terrorisait personne.",
    build(t) {
      const o = [];
      const ph = -t * 0.22;
      anneau(o, { y: 0.030, r: 0.50, w: 0.038, h: 0.024,
                  rgbA: 0x3A0A66, rgbB: 0x7B2BD6, alpha: 180, phase: ph });
      for (let i = 0; i < 10; i++) {
        const ang = ph + TAU * i / 10;
        const haut = 0.26 + Math.sin(t * 1.3 + i * 0.63) * 0.09;
        const pen = 0.055;                                   // piques inclinees vers l'exterieur
        segment(o, Math.cos(ang) * 0.50, 0.030, Math.sin(ang) * 0.50,
          Math.cos(ang) * (0.50 + pen), 0.030 + haut, Math.sin(ang) * (0.50 + pen),
          0.034, 0x7B2BD6, 0x3A0A66, 200, 60);
      }
      return o;
    }
  },

  // ===========================================================================
  //  LES 7 ORNEMENTS PONCTUELS (aujourd'hui en particules vanilla)
  // ===========================================================================

  crown_halo: {
    titre: "crownHalo — couronne ponctuelle",
    famille: "ponctuel",
    accorde: "#093 Zéphyr (l.348), #091 Renaissance Solaire (l.1493), #083 Empereur de Légion (l.3057).",
    note: "Anneau plein qui naît au ras du crâne, s'ouvre et s'efface en montant. Un seul anneau, jamais superposé : la lecture reste nette même à trois déclenchements rapprochés.",
    build(t) {
      const o = [];
      const p = (t * 0.55) % 1;
      const r = 0.16 + p * 0.46;
      anneau(o, { y: 1.95 + p * 0.42, r, w: 0.044 * (1 - p * 0.35),
                  rgbA: 0xFFF3C8, rgbB: 0xFFC63D,
                  alpha: Math.round(250 * (1 - p) ** 0.7), phase: p * 2.4 });
      return o;
    }
  },

  wing_ribbons: {
    titre: "wingRibbons — rubans d'ailes",
    famille: "ponctuel",
    accorde: "#081 Ange à la Plume Céleste (l.646).",
    note: "Deux rubans pleins qui se déroulent derrière les épaules et ondulent. Strictement derrière (z ≤ -0,18) : un ruban qui passe devant la caméra est exactement ce qu'il ne faut pas faire.",
    build(t) {
      const o = [];
      for (const s of [-1, 1]) {
        const n = pas(1.25, 0.040);
        for (let i = 0; i < n; i++) {
          const u = i / (n - 1);
          const onde = Math.sin(u * 5.2 - t * 3.2) * 0.14 * u;
          cube(o,
            s * (0.17 + u * 0.33) + onde * s,
            1.46 - u * 0.78 + Math.cos(u * 4 - t * 2.6) * 0.05,
            -0.20 - u * 0.42,
            0.020, 0.020, 0.020,
            mixRgb(0xFFFBEA, 0xFFD36B, u), Math.round(235 * (1 - u * 0.65)));
        }
      }
      return o;
    }
  },

  double_helix: {
    titre: "doubleHelix — double hélice ascendante",
    famille: "ponctuel",
    accorde: "#086 Berserker Immortel (l.667).",
    note: "Deux hélices pleines qui montent le long du corps. Rayon 0,72 : largement en dehors du champ de vision, elles encadrent le joueur sans jamais lui passer devant le nez.",
    build(t) {
      const o = [];
      const tours = 1.0, monte = 2.05, rayon = 0.72;
      // Longueur reelle d'une helice : hypotenuse de la montee et du developpe.
      const longueur = Math.hypot(monte, tours * TAU * rayon);
      for (const fil of [0, 1]) {
        const n = pas(longueur, 0.052);
        for (let i = 0; i < n; i++) {
          const u = i / (n - 1);
          const a = u * TAU * tours + fil * Math.PI - t * 1.7;
          cube(o, Math.cos(a) * rayon, 0.05 + u * monte, Math.sin(a) * rayon,
            0.026, 0.026, 0.026,
            mixRgb(fil ? 0xDD0000 : 0xFF6A3D, 0xFFE0C0, u),
            Math.round(230 * (1 - u * 0.5)));
        }
      }
      return o;
    }
  },

  cloud_cushion: {
    titre: "cloudCushion — coussin de nuages",
    famille: "ponctuel",
    accorde: "#063 Pont Dimensionnel (l.572), #090 Faucon Ouragan (l.703).",
    note: "Un vrai coussin : des anneaux pleins emboîtés sous les semelles, qui forment une galette continue. Hauteur maximale 0,14 — le joueur a l'air posé dessus, pas entouré de confettis.",
    build(t) {
      const o = [];
      const anneaux = [[0.10, 0.075], [0.22, 0.095], [0.34, 0.075], [0.45, 0.050]];
      for (let k = 0; k < anneaux.length; k++) {
        const [r, h] = anneaux[k];
        const gonfle = 1 + Math.sin(t * 1.4 + k * 0.9) * 0.07;
        anneau(o, {
          y: h * gonfle, r: r * gonfle, w: 0.050, h: 0.046,
          rgbA: 0xFFFFFF, rgbB: 0xD8E8F5, alpha: 205 - k * 22,
          phase: t * 0.2 * (k % 2 ? -1 : 1)
        });
      }
      return o;
    }
  },

  tether_beam: {
    titre: "tetherBeam — rayon tendu",
    famille: "ponctuel",
    accorde: "#099 Seigneur de Sang (l.1513), #084 Faucheur de Tempête (l.1963), #074 Archer d'Orage (l.3389).",
    note: "Faisceau plein entre la main et la cible, avec une onde qui le parcourt. Il part de la MAIN (hauteur 1,15) et file vers l'avant : il ne traverse jamais la tête du porteur.",
    build(t) {
      const o = [];
      const n = pas(2.6, 0.040);
      for (let i = 0; i < n; i++) {
        const u = i / (n - 1);
        const onde = Math.sin(u * 14 - t * 7) * 0.030 * Math.sin(Math.PI * u);
        const d = 0.038 * (1 + Math.sin(u * 9 - t * 7) * 0.22);
        cube(o, 0.30 + onde, 1.15 + u * 0.22 + onde * 0.5, 0.22 + u * 2.45,
          d / 2, d / 2, d / 2, mixRgb(0xDD0000, 0xFFC9C9, Math.sin(Math.PI * u)),
          Math.round(245 - Math.abs(u - 0.5) * 90));
      }
      return o;
    }
  },

  shockwave_ring: {
    titre: "shockwaveRing — onde de choc",
    famille: "ponctuel",
    accorde: "La forme la plus utilisée du registre : 43 appels dans EnchantmentComboHandler.",
    note: "Anneau plein qui s'étend au sol en s'aplatissant. Le nombre de cubes augmente avec le rayon — c'est tout l'intérêt de la règle : l'onde reste pleine du début à la fin, au lieu de se désagréger en pointillés comme le faisait la version à 24 particules fixes.",
    build(t) {
      const o = [];
      const p = (t * 0.6) % 1;
      const r = 0.25 + p * 1.35;
      anneau(o, { y: 0.05 + Math.sin(p * Math.PI) * 0.07, r,
                  w: 0.052 - p * 0.010, h: 0.034 - p * 0.010,
                  rgbA: 0xFFE9A0, rgbB: 0xFF9A30,
                  alpha: Math.round(240 * (1 - p) ** 1.3) });
      return o;
    }
  },

  burst_on_entity: {
    titre: "burstOnEntity — éclat sur la cible",
    famille: "ponctuel",
    accorde: "La plus fréquente du registre : 44 appels. Se joue SUR LA CIBLE, jamais sur le porteur.",
    note: "Coquille sphérique pleine qui s'ouvre sur le mob touché, à 1,9 bloc devant le joueur. Les méridiens sont calculés pour que la coque reste continue à toutes les latitudes — c'est là que le nombre fixe de particules échouait le plus visiblement.",
    build(t) {
      const o = [];
      // Armille : trois grands cercles orthogonaux, pleins. Une coque spherique
      // complete coutait 2 248 cubes pour la meme lecture a l'oeil.
      const p = (t * 0.8) % 1;
      const r = 0.12 + p * 0.46;
      const cz = 1.90, cy = 1.00;                 // SUR LA CIBLE, devant le joueur
      const w = 0.050, al = Math.round(235 * (1 - p) ** 1.2);
      const rgb = mixRgb(0xFFF3B0, 0xFF4A10, p);
      const n = pas(TAU * r, w);
      for (let plan = 0; plan < 3; plan++) {
        for (let k = 0; k < n; k++) {
          const a = TAU * k / n + t * (plan + 1) * 0.6;
          const c1 = Math.cos(a) * r, c2 = Math.sin(a) * r;
          const px = plan === 0 ? c1 : plan === 1 ? c1 : 0;
          const py = plan === 0 ? 0 : plan === 1 ? c2 : c1;
          const pz = plan === 0 ? c2 : plan === 1 ? 0 : c2;
          cube(o, px, cy + py, cz + pz, w / 2, w / 2, w / 2, rgb, al);
        }
      }
      // Pas de noyau isole au centre : il n'aurait aucun voisin, et un pixel
      // seul au milieu du vide est precisement ce qu'on cherche a eliminer.
      return o;
    }
  }
};

// --- ailes : membrane pleine, strictement derriere le dos --------------------

/**
 * @param ech     echelle d'ensemble
 * @param barbes  nombre de barbes (chacune une colonne CONTINUE)
 * @param envergure portee laterale en blocs
 * @param rond    bord arrondi (Endurance) au lieu de pointu (Archange)
 */
function ailes(t, ech, rgbExt, rgbInt, barbes, envergure, rond) {
  const o = [];
  const battement = Math.sin(t * 2.4);
  const epaule = 1.42;
  for (const s of [-1, 1]) {
    for (let c = 0; c < barbes; c++) {
      const u = barbes === 1 ? 0 : c / (barbes - 1);
      // Longueur de la barbe : profil d'aile. Pointu = pic decale, rond = demi-cercle.
      const profil = rond
        ? Math.sin(Math.PI * (0.25 + u * 0.70)) * 0.95
        : Math.pow(Math.sin(Math.PI * (0.18 + u * 0.74)), 0.75);
      const lg = (0.52 + 0.46 * profil) * ech;
      const ouvre = 0.30 + 0.12 * battement;
      // depart : le long de l'omoplate ; arrivee : vers l'arriere et le bas
      const x0 = s * (0.16 + u * 0.05) * ech;
      const y0 = epaule + (0.16 - u * 0.30) * ech;
      const z0 = -0.17 - u * 0.05;
      const dir = (0.55 + u * 0.55) * ouvre * 3.1;
      const x1 = x0 + s * envergure * ech * (0.35 + u * 0.75);
      const y1 = y0 + (0.30 - u * 0.95) * ech - battement * 0.07 * (0.3 + u);
      const z1 = z0 - (0.22 + u * 0.30) * ech - dir * 0.045;
      segment(o, x0, y0, z0, x1, y1, z1, 0.055 * ech,
        c === 0 || c === barbes - 1 ? rgbExt : rgbInt,
        rgbExt, 240, 165);
      // plume de pointe : prolonge la barbe sans laisser de trou
      segment(o, x1, y1, z1,
        x1 + s * 0.10 * ech, y1 - 0.13 * ech, z1 - 0.05 * ech,
        0.030 * ech, rgbExt, rgbInt, 170, 70);
    }
    // bord d'attaque : relie toutes les barbes -> aucune fente entre elles
    const n = pas(envergure * ech * 1.05, 0.052 * ech);
    for (let i = 0; i < n; i++) {
      const u = i / (n - 1);
      cube(o, s * (0.16 + envergure * ech * u * 1.05) * 1.0,
        epaule + 0.16 * ech + Math.sin(u * 2.0) * 0.10 * ech - battement * 0.05 * u,
        -0.17 - u * 0.26 * ech,
        0.026 * ech, 0.026 * ech, 0.026 * ech,
        mixRgb(rgbExt, rgbInt, u), 235);
    }
  }
  return o;
}

window.DLB_MODELS = MODELS;
window.DLB_H = H;
window.DLB_ZONE_REGARD = ZONE_REGARD;
