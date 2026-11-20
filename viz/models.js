// =============================================================================
//  Modeles d'ornements du Deep Lucky Block, reconstruits en petits cubes.
//
//  Chaque valeur vient de CosmeticPixelAura.java / EnchantmentComboHandler.java.
//  Rien n'est invente : les rayons, nombres de points, couleurs, alphas et
//  tailles sont ceux du jeu. Les formes qui n'existent aujourd'hui qu'en
//  particules vanilla sont transposees ici en cubes, a l'identique de ce que
//  fait deja emitVoxelCube pour les ornements permanents.
// =============================================================================

// --- outils fideles au code Java --------------------------------------------

function mixRgb(a, b, t) {
  const ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
  const br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
  return (Math.round(ar + (br - ar) * t) << 16)
       | (Math.round(ag + (bg - ag) * t) << 8)
       | Math.round(ab + (bb - ab) * t);
}

/** cube : centre + DEMI-extensions, comme emitVoxelCube(cx,cy,cz,hx,hy,hz,rgb,alpha). */
function cube(out, x, y, z, hx, hy, hz, rgb, alpha) {
  out.push({ x, y, z, hx, hy, hz, rgb, a: alpha / 255 });
}

/**
 * haloRing — copie exacte de CosmeticPixelAura.haloRing.
 * step = 0.035*size ; ondulation verticale sin(ang*2+phase)*0.035 ;
 * degrade ANGULAIRE triangulaire lisse A->B->A ; alpha - (i%3)*12 ;
 * pixel d'epaisseur interne si radius > 0.5 et i pair.
 */
function haloRing(out, cx, cy, cz, radius, phase, points, rgbA, rgbB, alpha, size) {
  const step = 0.035 * size;
  for (let i = 0; i < points; i++) {
    const ang = phase + Math.PI * 2 * i / points;
    const x = cx + Math.cos(ang) * radius;
    const z = cz + Math.sin(ang) * radius;
    const y = cy + Math.sin(ang * 2 + phase) * 0.035;
    const t = i / points;
    let tri = (t < 0.5) ? (t * 2) : (2 - t * 2);
    tri = tri * tri * (3 - 2 * tri);
    const rgb = mixRgb(rgbA, rgbB, tri);
    const a = alpha - (i % 3) * 12;
    cube(out, x, y, z, step, step * 0.80, step, rgb, a);
    if (radius > 0.5 && (i & 1) === 0) {
      cube(out,
        cx + Math.cos(ang) * (radius - step * 2.2), y + step * 0.6,
        cz + Math.sin(ang) * (radius - step * 2.2),
        step * 0.55, step * 0.45, step * 0.55,
        mixRgb(rgb, rgbB, 0.5), Math.round(a * 0.7));
    }
  }
}

/**
 * wing — copie exacte de CosmeticPixelAura.wing (5 colonnes x 3 rangees + plume
 * de pointe par colonne = 20 cubes par aile). side = -1 ou +1.
 */
function wing(out, height, side, time, outerRgb, innerRgb, scale) {
  const shoulderY = height * 0.66;
  const backOffset = 0.16;
  const flap = Math.sin(time * 2.6);
  const cols = 5, rows = 3;
  for (let c = 0; c < cols; c++) {
    const cu = c / (cols - 1);
    const span = (0.20 + cu * 0.72) * scale;
    const sweepBack = backOffset + cu * 0.30;
    const colFlap = flap * (0.06 + cu * 0.16);
    for (let r = 0; r < rows; r++) {
      const ru = r / (rows - 1);
      const lift = 0.34 - ru * 0.30 + colFlap;
      const size = (0.058 + 0.030 * (1 - cu)) * (0.85 + 0.30 * ru) * scale;
      const outerEdge = (r === 0) || (c === cols - 1);
      cube(out, span * side, shoulderY + lift, -sweepBack,
        size, size * 0.55, size * 0.85,
        outerEdge ? innerRgb : outerRgb, outerEdge ? 235 : 150);
    }
    cube(out, (0.20 + cu * 0.72) * scale * side,
      shoulderY + 0.40 + colFlap + 0.05 * c, -(backOffset + cu * 0.30),
      0.034 * scale, 0.026 * scale, 0.050 * scale, innerRgb, 200);
  }
}

const H = 1.8; // hauteur du joueur (getBbHeight)

// --- les 12 ornements PERMANENTS (deja en cubes dans le jeu) -----------------

const MODELS = {

  halo_continu: {
    titre: "Halo générique continu",
    famille: "permanent",
    accorde: "Trident enchanté en main, ou arme portant le marqueur dlbWeaponAura (mêlée 15 %, distance 40 %)",
    note: "Trois anneaux à la fois, avec une respiration verticale sin(t×1.35)×0.035. Ne sort que si AUCUN autre ornement n'est actif.",
    build(t) {
      const o = [], bob = Math.sin(t * 1.35) * 0.035;
      haloRing(o, 0, H + 0.36 + bob, 0, 0.46, t * 0.62, 14, 0xFFF3C8, 0xFFC63D, 240, 1.6);
      haloRing(o, 0, H + 0.50 + bob * 0.6, 0, 0.33, -t * 0.85, 10, 0xFFFFFF, 0xFFE9A0, 215, 1.35);
      haloRing(o, 0, 0.06, 0, 0.58, -t * 0.33, 10, 0xFFE9A0, 0xFFC63D, 150, 1.0);
      return o;
    }
  },

  avatar_110: {
    titre: "#110 Avatar Suprême du Deep Lucky",
    famille: "permanent",
    accorde: "Combo ULTRA_RARE : épée nétherite peerless 5 · bouclier perfect_parry 3 · casque food_is_heaven 1 · plastron heart_resonance 1 · jambières giant_step 5 · bottes cloud_walker 1",
    note: "Couronne double, la plus lumineuse du mod. Les deux anneaux tournent en sens inverse.",
    build(t) {
      const o = [];
      haloRing(o, 0, H + 0.30, 0, 0.54, t * 0.55, 16, 0xFFF6D0, 0xFFC63D, 250, 1.8);
      haloRing(o, 0, H + 0.44, 0, 0.40, -t * 0.72, 12, 0xFFFFFF, 0xFFE9A0, 235, 1.5);
      return o;
    }
  },

  eternelle_097: {
    titre: "#097 Armure Éternelle Transcendante",
    famille: "permanent",
    accorde: "Combo ULTRA_RARE : casque king 3 · plastron divine_protection 3 · jambières giant_step 3 · bottes cloud_walker 1",
    note: "Couronne simple dorée.",
    build(t) {
      const o = [];
      haloRing(o, 0, H + 0.28, 0, 0.40, t * 0.5, 12, 0xFFE9A0, 0xFFB020, 235, 1.5);
      return o;
    }
  },

  dieu_lame_098: {
    titre: "#098 Dieu de la Lame Suprême",
    famille: "permanent",
    accorde: "Combo ULTRA_RARE : épée nétherite supreme_sharpness 10 · casque king 3 · plastron heart_resonance 1 · jambières leap_power 3 · bottes windrunner 1",
    note: "Partage EXACTEMENT la branche de rendu de #097 : le même anneau, aux mêmes valeurs.",
    build(t) {
      const o = [];
      haloRing(o, 0, H + 0.28, 0, 0.40, t * 0.5, 12, 0xFFE9A0, 0xFFB020, 235, 1.5);
      return o;
    }
  },

  archange_101: {
    titre: "#101 Archange du Jugement Solaire",
    famille: "permanent",
    accorde: "Combo ULTRA_RARE : épée nétherite annihilating_sweep 3 · bouclier shield_quake 1 · casque radiant_ward 2 · plastron light_heart 2 · bottes lightness 3",
    note: "Deux ailes en membrane de pixels : 5 colonnes × 3 rangées + 5 plumes de pointe = 20 cubes par aile, 40 au total. Portées en permanence, au sol comme en vol.",
    build(t) {
      const o = [];
      wing(o, H, -1, t, 0xFFE9A0, 0xFFFBEA, 1.0);
      wing(o, H, +1, t, 0xFFE9A0, 0xFFFBEA, 1.0);
      return o;
    }
  },

  winged_endurance: {
    titre: "Endurance Ailée (enchantement d'armure)",
    famille: "permanent",
    accorde: "Enchantement wing_toughness_armor sur une pièce d'ARMURE",
    note: "Mêmes ailes que l'Archange mais 18 % plus petites (×0.82), en bleu glacier, et elles ne sortent QU'EN L'AIR ou en vol d'élytres.",
    build(t) {
      const o = [];
      wing(o, H, -1, t, 0xCFE8FF, 0xFFFFFF, 0.82);
      wing(o, H, +1, t, 0xCFE8FF, 0xFFFFFF, 0.82);
      return o;
    }
  },

  seigneur_guerre_106: {
    titre: "#106 Seigneur de Guerre de l'Apocalypse",
    famille: "permanent",
    accorde: "Combo ULTRA_RARE : épée nétherite wither_blade 1 · bouclier perfect_parry 2 · casque commander 1 · plastron fire_heart 2 · jambières dash 2 · bottes blaze_walker 1",
    note: "Anneau au sol, le plus large du mod (rayon 0.85).",
    build(t) {
      const o = [];
      haloRing(o, 0, 0.10, 0, 0.85, -t * 0.45, 12, 0xFF7A2E, 0xFFC46B, 195, 1.2);
      return o;
    }
  },

  peste_062: {
    titre: "#062 Peste Ambulante",
    famille: "permanent",
    accorde: "Combo RARE : bottes diamant super_stinky_feet 1 · plastron diamant dread_aura 1",
    note: "Le SEUL ornement permanent accessible hors ULTRA_RARE : deux pièces en diamant suffisent. Anneau au sol translucide.",
    build(t) {
      const o = [];
      haloRing(o, 0, 0.14, 0, 0.95, t * 0.30, 10, 0x6BAF1E, 0x3E7A0C, 150, 1.1);
      return o;
    }
  },

  king: {
    titre: "Roi (enchantement d'armure)",
    famille: "permanent",
    accorde: "Enchantement king sur une pièce d'ARMURE",
    note: "Troisième et dernier maillon de la chaîne de couronnes : s'efface devant #110 et #097/#098.",
    build(t) {
      const o = [];
      haloRing(o, 0, H + 0.26, 0, 0.34, t * 0.42, 10, 0xFFD34D, 0xFF9500, 225, 1.4);
      return o;
    }
  },

  mystic_mind: {
    titre: "Esprit Mystique (enchantement d'armure)",
    famille: "permanent",
    accorde: "Enchantement mystic_mind sur une pièce d'ARMURE",
    note: "Se cumule librement avec les autres couronnes. Tourne à l'envers.",
    build(t) {
      const o = [];
      haloRing(o, 0, H + 0.34, 0, 0.32, -t * 0.40, 10, 0xC08BFF, 0x7B3BE8, 215, 1.3);
      return o;
    }
  },

  radiant_aegis: {
    titre: "Égide Radieuse (enchantement d'armure)",
    famille: "permanent",
    accorde: "Enchantement heart_resonance sur une pièce d'ARMURE",
    note: "La couronne la plus basse : seulement 0.20 au-dessus de la tête.",
    build(t) {
      const o = [];
      haloRing(o, 0, H + 0.20, 0, 0.36, t * 0.36, 10, 0xFFF7D9, 0xBFE8FF, 210, 1.3);
      return o;
    }
  },

  dread_aura: {
    titre: "Aura de Terreur (enchantement d'armure)",
    famille: "permanent",
    accorde: "Enchantement dread_aura sur une pièce d'ARMURE",
    note: "Anneau bas, à mi-mollet (y + 0.32), le plus sombre du lot.",
    build(t) {
      const o = [];
      haloRing(o, 0, 0.32, 0, 0.66, -t * 0.38, 8, 0x3A0A66, 0x7B2BD6, 165, 1.0);
      return o;
    }
  },

  // --- les formes PONCTUELLES, aujourd'hui en particules vanilla -------------
  // Transposees ici en cubes. Les parametres (rayon, nombre de points, couleur)
  // sont ceux des appels reels ; seul le rendu change : cube au lieu de particule.

  crown_halo: {
    titre: "crownHalo — couronne ponctuelle",
    famille: "ponctuel",
    accorde: "#093 Avatar du Zéphyr · #091 Renaissance Solaire · #083 Empereur de Légion",
    note: "Aujourd'hui en particules. Reconstruite ici en 12 cubes dorés au-dessus de la tête, palette GOLD_DIVINE.",
    build(t) {
      const o = [], pts = 12, r = 0.38;
      for (let i = 0; i < pts; i++) {
        const ang = t * 0.8 + Math.PI * 2 * i / pts;
        const pulse = 0.85 + 0.15 * Math.sin(t * 3 + i);
        cube(o, Math.cos(ang) * r, H + 0.32 + Math.sin(ang * 3 + t) * 0.03, Math.sin(ang) * r,
          0.030 * pulse, 0.046 * pulse, 0.030 * pulse, mixRgb(0xFFF3B0, 0xFFC63D, i / pts), 230);
      }
      return o;
    }
  },

  wing_ribbons: {
    titre: "wingRibbons — rubans d'ailes",
    famille: "ponctuel",
    accorde: "#081 Ange à la Plume Céleste",
    note: "Aujourd'hui en particules derrière les épaules. Reconstruits ici en deux rubans de 9 cubes qui ondulent.",
    build(t) {
      const o = [];
      for (let side = -1; side <= 1; side += 2) {
        for (let i = 0; i < 9; i++) {
          const u = i / 8;
          const sway = Math.sin(t * 2.2 - i * 0.55) * 0.14 * u;
          cube(o, side * (0.22 + u * 0.46) + sway * side,
            H * 0.70 + 0.12 - u * 0.44 + Math.sin(t * 1.8 - i * 0.4) * 0.05,
            -0.18 - u * 0.26,
            0.028, 0.040, 0.022, mixRgb(0xFFF3B0, 0xFFFBEA, u), 220 - i * 10);
        }
      }
      return o;
    }
  },

  double_helix: {
    titre: "doubleHelix — double hélice ascendante",
    famille: "ponctuel",
    accorde: "#086 Berserker Immortel (sous 35 % PV)",
    note: "Aujourd'hui en particules CRIMSON_BLOOD. Deux brins opposés qui montent autour du corps.",
    build(t) {
      const o = [], pts = 16;
      for (let brin = 0; brin < 2; brin++) {
        for (let i = 0; i < pts; i++) {
          const u = i / (pts - 1);
          const ang = t * 1.6 + brin * Math.PI + u * Math.PI * 2.4;
          cube(o, Math.cos(ang) * 0.42, 0.10 + u * (H - 0.1), Math.sin(ang) * 0.42,
            0.030, 0.030, 0.030,
            mixRgb(brin ? 0xDD0000 : 0xFF4A4A, 0x7A0010, u), 235 - Math.round(u * 60));
        }
      }
      return o;
    }
  },

  cloud_cushion: {
    titre: "cloudCushion — coussin de nuages",
    famille: "ponctuel",
    accorde: "#063 Pont Dimensionnel · #090 Faucon Ouragan",
    note: "Aujourd'hui en particules CLOUD sous les pieds. Reconstruit en amas de cubes blancs de tailles inégales.",
    build(t) {
      const o = [];
      for (let i = 0; i < 26; i++) {
        const ang = i * 2.39996 + t * 0.4;
        const r = 0.14 + 0.46 * Math.sqrt(i / 26);
        const s = 0.045 + 0.035 * Math.abs(Math.sin(i * 1.7));
        cube(o, Math.cos(ang) * r, 0.04 + Math.sin(t * 1.6 + i) * 0.022, Math.sin(ang) * r,
          s, s * 0.62, s, mixRgb(0xFFFFFF, 0xCFE8FF, (i % 5) / 4), 200 - (i % 4) * 18);
      }
      return o;
    }
  },

  tether_beam: {
    titre: "tetherBeam — rayon tendu",
    famille: "ponctuel",
    accorde: "#099 Seigneur de Sang Immortel · #084 Faucheur de Tempête · #074 Archer d'Orage en Chaîne",
    note: "Aujourd'hui en particules entre deux positions. Reconstruit en chapelet de cubes, de la main vers une cible à 3 blocs.",
    build(t) {
      const o = [], n = 22;
      const ax = 0.42, ay = H * 0.62, az = 0.10;
      const bx = 0.42, by = H * 0.55, bz = 3.0;
      for (let i = 0; i < n; i++) {
        const u = i / (n - 1);
        const wob = Math.sin(u * 14 + t * 6) * 0.035 * Math.sin(u * Math.PI);
        cube(o, ax + (bx - ax) * u + wob, ay + (by - ay) * u + wob * 0.6, az + (bz - az) * u,
          0.026, 0.026, 0.038, mixRgb(0x5E3AFF, 0xBFC9FF, Math.sin(u * Math.PI)), 240 - Math.round(u * 70));
      }
      return o;
    }
  },

  shockwave_ring: {
    titre: "shockwaveRing — anneau d'onde de choc",
    famille: "ponctuel",
    accorde: "43 appels : la forme la plus employée du système de combos",
    note: "Aujourd'hui en particules. Anneau qui s'élargit au sol ; reconstruit en 20 cubes aplatis.",
    build(t) {
      const o = [], pts = 20;
      const phase = (t * 0.6) % 1;
      const r = 0.35 + phase * 1.35;
      const fade = Math.round(235 * (1 - phase));
      for (let i = 0; i < pts; i++) {
        const ang = Math.PI * 2 * i / pts;
        cube(o, Math.cos(ang) * r, 0.05, Math.sin(ang) * r,
          0.040, 0.022, 0.040, mixRgb(0xFFF3B0, 0xFF7A2E, phase), Math.max(20, fade));
      }
      return o;
    }
  },

  burst_on_entity: {
    titre: "burstOnEntity — explosion ponctuelle",
    famille: "ponctuel",
    accorde: "44 appels : la forme la plus employée, à égalité avec l'anneau",
    note: "Aujourd'hui en particules sur le buste. Reconstruite en 24 cubes projetés en sphère depuis le torse.",
    build(t) {
      const o = [], n = 24;
      const phase = (t * 0.9) % 1;
      for (let i = 0; i < n; i++) {
        const ang = i * 2.39996;
        const yy = 1 - 2 * (i + 0.5) / n;
        const rr = Math.sqrt(Math.max(0, 1 - yy * yy));
        const d = 0.12 + phase * 0.70;
        cube(o, Math.cos(ang) * rr * d, H * 0.55 + yy * d, Math.sin(ang) * rr * d,
          0.034 * (1 - phase * 0.5), 0.034 * (1 - phase * 0.5), 0.034 * (1 - phase * 0.5),
          mixRgb(0xFFFFFF, 0x38BDF8, phase), Math.max(20, Math.round(240 * (1 - phase))));
      }
      return o;
    }
  }
};

window.DLB_MODELS = MODELS;
window.DLB_H = H;
