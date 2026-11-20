// =============================================================================
//  ORNEMENTS COSMETIQUES DU DEEP LUCKY BLOCK — MODELES EN PETITS CUBES
//  Version 3.
//
//  CE QUI N'ALLAIT PAS DANS LA VERSION 2, ET QUI EST CORRIGE ICI :
//
//  (a) UN SEUL OBJET REPEINT. La v2 avait une fonction anneau() appelee NEUF
//      fois et une fonction ailes() appelee DEUX fois. Changer le rayon et la
//      couleur ne fait pas un modele different : l'Archange et l'Endurance
//      Ailee etaient litteralement la meme aile, en or et en bleu. Dans ce
//      fichier, PLUS AUCUNE forme n'est partagee entre deux ornements. Ce qui
//      est partage, ce sont uniquement les outils de dessin (remplir un trait,
//      remplir une bande, remplir un disque) — comme un pinceau est partage
//      entre deux tableaux.
//
//  (b) DES AILES QUI N'EN SONT PAS. Six traits en eventail ne sont pas une
//      aile. Une vraie aile a une charpente (humerus, avant-bras, main) et
//      QUATRE rangees de plumes qui se recouvrent : primaires, secondaires,
//      grandes couvertures, moyennes couvertures, plus l'alula du poignet.
//      C'est ce recouvrement qui la rend pleine et belle. L'Archange est
//      construit comme ca. L'Endurance Ailee, elle, n'est pas une aile a
//      plumes du tout : c'est une voilure rigide a panneaux et nervures,
//      relevee vers le haut. Deux silhouettes, pas deux teintes.
//
//  (c) DES ESPACEMENTS NON MAITRISES. Le nombre de cubes etait une constante
//      en dur, sans rapport avec la longueur de la forme : les anneaux etaient
//      vides de 41 % a 87 %. Ici, aucune forme ne choisit son nombre de cubes.
//
// -----------------------------------------------------------------------------
//  REGLE N.1 — AUCUN ECART. Le pas d'echantillonnage se DEDUIT de la longueur.
//      ouverte : n = ceil(L / (0.9 w)) + 1   -> l'ecart vaut L/(n-1) <= 0.9 w
//      fermee  : n = ceil(L / (0.9 w))       -> l'ecart vaut L/n     <= 0.9 w
//      Le +1 de la forme ouverte etait absent en v2 : c'etait un trou latent
//      sur chaque trait. Deux cubes voisins se recouvrent donc toujours d'au
//      moins 10 %, meme en escalier.
//
//  REGLE N.2 — FIN. Un halo est un TRAIT, pas un tube. Largeur de reference
//      0,042 bloc (= 0,67 pixel Minecraft) ; les formes a plat sont ecrasees
//      (demi-hauteur 0,010) : nettes vues d'en haut, invisibles de profil.
//
//  REGLE N.3 — NE JAMAIS GENER LE JOUEUR. Ces effets sont CONSTANTS. Rien ne
//      doit traverser le champ de vision, decrit par ZONE_REGARD. Un ornement
//      de tete reste au-dessus du crane, un ornement de sol reste sous le
//      genou, un ornement de dos reste derriere le dos.
//
//  REGLE N.4 — CHACUN SA SILHOUETTE, RECONNAISSABLE DE LOIN ET SANS COULEUR.
//      Halo = plaque annulaire. #110 = soleil a rayons. #097 = chaine de
//      maillons. #098 = cercle d'epees dressees. #101 = aile emplumee.
//      Endurance = voilure a panneaux. #106 = sceau plat. #062 = flaques et
//      bulles. Roi = couronne a fleurons. Mystique = lemniscate. Egide =
//      coupole a meridiens. Terreur = ronces crochues.
// =============================================================================

const TAU = Math.PI * 2;
const H = 1.8;                 // hauteur du joueur
const YEUX = 1.62;             // hauteur des yeux
const CRANE = 1.80;            // sommet du crane

// ORIENTATION — convention ecrite une fois pour toutes.
// Le joueur REGARDE VERS +Z. Donc z > 0 = devant lui, z < 0 = derriere lui.
// En v1 les ailes etaient a z < 0 pendant que le viewer orientait le regard
// vers -Z : elles etaient plantees DEVANT le visage. Les deux fichiers etaient
// coherents chacun de son cote, et faux ensemble.
/** Volume que rien de constant ne doit occuper : le champ de vision. */
const ZONE_REGARD = { rayon: 0.55, bas: 1.10, haut: CRANE, devant: -0.05 };

/** Largeur de reference d'un trait, en blocs. */
const W = 0.042;

// =============================================================================
//  OUTILS DE DESSIN (partages) — ils ne definissent aucune forme.
// =============================================================================

function mixRgb(a, b, t) {
  t = t < 0 ? 0 : t > 1 ? 1 : t;
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

/** Nombre d'echantillons pour une ligne OUVERTE de longueur L. Ecart = L/(n-1). */
function pasOuvert(L, w) { return Math.ceil(L / (w * 0.9)) + 1; }
/** Nombre d'echantillons pour une boucle FERMEE de longueur L. Ecart = L/n. */
function pasFerme(L, w) { return Math.max(3, Math.ceil(L / (w * 0.9))); }

function norm(v) { const l = Math.hypot(v[0], v[1], v[2]) || 1; return [v[0] / l, v[1] / l, v[2] / l]; }
function lerp3(a, b, u) { return [a[0] + (b[0] - a[0]) * u, a[1] + (b[1] - a[1]) * u, a[2] + (b[2] - a[2]) * u]; }
function dist3(a, b) { return Math.hypot(b[0] - a[0], b[1] - a[1], b[2] - a[2]); }
/** Composante de `ref` orthogonale a `axe`, normalisee. */
function perp(axe, ref) {
  const d = axe[0] * ref[0] + axe[1] * ref[1] + axe[2] * ref[2];
  return norm([ref[0] - axe[0] * d, ref[1] - axe[1] * d, ref[2] - axe[2] * d]);
}

/** Trait plein de a vers b. f(u,p) -> { rgb, a, hx?, hy?, hz? } */
function trait(out, a, b, w, f) {
  const n = Math.max(2, pasOuvert(dist3(a, b), w));
  for (let i = 0; i < n; i++) {
    const u = i / (n - 1);
    const p = lerp3(a, b, u);
    const d = f(u, p);
    if (d) cube(out, p[0], p[1], p[2],
      d.hx !== undefined ? d.hx : w / 2, d.hy !== undefined ? d.hy : w / 2,
      d.hz !== undefined ? d.hz : w / 2, d.rgb, d.a);
  }
}

/**
 * Reechantillonne une courbe parametree a PAS CONSTANT EN LONGUEUR D'ARC.
 *
 * Donner une longueur approchee ne suffit pas : ce qui compte est la vitesse
 * LOCALE du parametre. Une ronce en u^0.82 part trois fois plus vite qu'elle
 * ne finit, une helice avance plus vite que sa montee, une lemniscate se
 * resserre au croisement. Dans les trois cas, echantillonner u regulierement
 * ouvre un trou la ou la courbe accelere. On mesure donc la courbe, puis on
 * la reparcourt a pas constant.
 *
 * Retourne [{ u, p }] : u = parametre d'origine (pour les couleurs et les
 * largeurs), p = point reechantillonne.
 */
function echantillonne(g, w, N) {
  N = N || 96;
  const brut = [];
  for (let i = 0; i <= N; i++) brut.push(g(i / N));
  const cum = [0];
  for (let i = 1; i <= N; i++) cum.push(cum[i - 1] + dist3(brut[i - 1], brut[i]));
  const L = cum[N];
  const n = Math.max(2, Math.ceil(L / (w * 0.9)) + 1);
  const sortie = [];
  let k = 0;
  for (let i = 0; i < n; i++) {
    const s = L * i / (n - 1);
    while (k < N - 1 && cum[k + 1] < s) k++;
    const seg = cum[k + 1] - cum[k];
    const f = seg > 1e-9 ? (s - cum[k]) / seg : 0;
    sortie.push({ u: (k + f) / N, p: lerp3(brut[k], brut[k + 1], f) });
  }
  return sortie;
}

/** Courbe pleine quelconque. g(u) -> [x,y,z]. */
function courbe(out, w, g, f) {
  for (const e of echantillonne(g, w)) {
    const d = f(e.u, e.p);
    if (d) cube(out, e.p[0], e.p[1], e.p[2],
      d.hx !== undefined ? d.hx : w / 2, d.hy !== undefined ? d.hy : w / 2,
      d.hz !== undefined ? d.hz : w / 2, d.rgb, d.a);
  }
}

/**
 * Nappe pleine : g(u,v) -> [x,y,z] sur une grille assez dense pour ne pas
 * laisser de trou. boucleU = la direction u se referme sur elle-meme.
 */
function nappe(out, Lu, Lv, w, g, f, boucleU) {
  const nu = boucleU ? pasFerme(Lu, w) : Math.max(2, pasOuvert(Lu, w));
  const nv = Math.max(1, pasOuvert(Lv, w));
  for (let i = 0; i < nu; i++) {
    const u = boucleU ? i / nu : i / (nu - 1);
    for (let j = 0; j < nv; j++) {
      const v = nv === 1 ? 0.5 : j / (nv - 1);
      const p = g(u, v);
      const d = f(u, v, p);
      if (d) cube(out, p[0], p[1], p[2],
        d.hx !== undefined ? d.hx : w / 2, d.hy !== undefined ? d.hy : w / 2,
        d.hz !== undefined ? d.hz : w / 2, d.rgb, d.a);
    }
  }
}

/**
 * Bande pleine a LARGEUR VARIABLE : l'outil des plumes, des lames et des
 * rubans. axe(u) donne le centre, largeur(u) la largeur a cet endroit,
 * lateral(u) la direction (unitaire) dans laquelle cette largeur s'etale.
 * Le nombre de cubes en travers suit la largeur locale : une pointe effilee
 * finit sur UN cube, sans trou ni gaspillage.
 */
function bande(out, w, axe, largeur, lateral, f) {
  for (const s of echantillonne(axe, w)) {
    const u = s.u, c = s.p, lg = Math.max(0, largeur(u)), e = lateral(u);
    // Une largeur plus petite qu'un demi-cube tient dans UN cube : c'est ce qui
    // permet a une pointe de plume de finir net, sans doublon ni trou.
    const nv = lg <= w * 0.5 ? 1 : pasOuvert(lg, w);
    for (let j = 0; j < nv; j++) {
      const v = nv === 1 ? 0 : j / (nv - 1) - 0.5;
      const p = [c[0] + e[0] * lg * v, c[1] + e[1] * lg * v, c[2] + e[2] * lg * v];
      const d = f(u, v, p);
      if (d) cube(out, p[0], p[1], p[2],
        d.hx !== undefined ? d.hx : w / 2, d.hy !== undefined ? d.hy : w / 2,
        d.hz !== undefined ? d.hz : w / 2, d.rgb, d.a);
    }
  }
}

/** Disque horizontal PLEIN (flaques, nuages). f(fr,theta) -> {rgb,a,k?} */
function disqueH(out, cx, cy, cz, rayon, w, f) {
  const nr = Math.max(1, pasOuvert(rayon, w));
  for (let i = 0; i < nr; i++) {
    const fr = nr === 1 ? 0 : i / (nr - 1);
    const nc = Math.max(1, Math.ceil(TAU * rayon * fr / (w * 0.9)));
    for (let j = 0; j < nc; j++) {
      const th = TAU * j / nc;
      const d = f(fr, th);
      if (!d) continue;
      const r = rayon * fr * (d.k === undefined ? 1 : d.k);
      cube(out, cx + Math.cos(th) * r, cy, cz + Math.sin(th) * r,
        w / 2, d.hy !== undefined ? d.hy : w / 2, w / 2, d.rgb, d.a);
    }
  }
}

/** Petite bille pleine : un noyau et ses six contacts. Jamais un cube seul. */
function bille(out, cx, cy, cz, r, rgb, alpha, rgbBord) {
  cube(out, cx, cy, cz, r, r, r, rgb, alpha);
  const v = [[1, 0, 0], [-1, 0, 0], [0, 1, 0], [0, -1, 0], [0, 0, 1], [0, 0, -1]];
  for (const [dx, dy, dz] of v)
    cube(out, cx + dx * r * 1.45, cy + dy * r * 1.45, cz + dz * r * 1.45,
      r * 0.72, r * 0.72, r * 0.72, rgbBord === undefined ? rgb : rgbBord, alpha * 0.82);
}

/**
 * Reechantillonne une boucle parametree a PAS CONSTANT en longueur d'arc.
 * Indispensable des que la vitesse du parametre n'est pas uniforme (une
 * lemniscate parcourue a theta constant se serre au croisement et s'ecarte
 * aux extremites : c'est exactement un trou).
 */
function boucleEgale(g, pas, echantillons) {
  const brut = [];
  for (let i = 0; i < echantillons; i++) brut.push(g(i / echantillons));
  const sortie = [];
  let ancre = brut[0];
  sortie.push(ancre);
  for (let i = 1; i < brut.length; i++) {
    if (dist3(ancre, brut[i]) >= pas) { ancre = brut[i]; sortie.push(ancre); }
  }
  // Si le dernier point colle au premier, on l'enleve : la boucle se referme.
  if (sortie.length > 2 && dist3(sortie[sortie.length - 1], sortie[0]) < pas * 0.55) sortie.pop();
  return sortie;
}

// =============================================================================
//  LES 12 ORNEMENTS PERMANENTS
// =============================================================================

const MODELS = {

  // --- 1. halo generique -----------------------------------------------------
  halo_continu: {
    titre: "Halo générique continu",
    famille: "permanent",
    accorde: "Arme à aura : trident enchanté en main, ou marque dlbWeaponAura (15 % en mêlée, 40 % à distance). Exclusif : ne sort que si aucun combo cosmétique n'est actif (CosmeticPixelAura l.232, flags == 0).",
    note: "Une PLAQUE annulaire, pas un fil de fer. Trois rangées jointives entre 0,238 et 0,300 de rayon : vu d'en haut c'est un disque percé plein, vu de profil une ligne de 2 cm. C'est ça, un halo. La v2 posait un seul collier de cubes sur un cercle, et la v1 en empilait trois dont un AU SOL, à 1,9 bloc de la tête.",
    build(t) {
      const o = [];
      const y = 2.005 + Math.sin(t * 1.1) * 0.012;
      const rInt = 0.238, rExt = 0.300, w = 0.046;
      const chaud = (t * 0.15) % 1;           // le point chaud fait lentement le tour
      nappe(o, TAU * rExt, rExt - rInt, w,
        (u, v) => {
          const a = TAU * u, r = rInt + (rExt - rInt) * v;
          return [Math.cos(a) * r, y, Math.sin(a) * r];
        },
        (u, v) => {
          let d = Math.abs(u - chaud); if (d > 0.5) d = 1 - d;
          const g = Math.max(0, 1 - d * 4.2);
          const bord = Math.abs(v - 0.5) * 2;           // 0 au milieu, 1 aux bords
          return {
            rgb: mixRgb(mixRgb(0xFFC63D, 0xFFF8DC, 1 - bord * 0.8), 0xFFFFFF, g * 0.7),
            a: (206 + g * 49) * (1 - bord * 0.40),
            hy: 0.010
          };
        }, true);
      return o;
    }
  },

  // --- 2. #110 Avatar : soleil a rayons --------------------------------------
  avatar_110: {
    titre: "#110 Avatar Suprême du Deep Lucky",
    famille: "permanent",
    accorde: "Combo #110 — le plus haut du registre. Exclusif face à #097/#098 et à Roi (chaîne else if, CosmeticPixelAura l.242-254).",
    note: "Un soleil : disque annulaire serré au centre, et douze rayons TRIANGULAIRES qui en jaillissent, longs et courts en alternance, chacun pointant jusqu'à un seul cube. Aucun rapport avec la plaque lisse du halo générique : ici la silhouette est dentelée.",
    build(t) {
      const o = [];
      const ph = t * 0.26, y = 2.030;
      const rInt = 0.158, rExt = 0.206;
      nappe(o, TAU * rExt, rExt - rInt, 0.050,
        (u, v) => {
          const a = ph + TAU * u, r = rInt + (rExt - rInt) * v;
          return [Math.cos(a) * r, y, Math.sin(a) * r];
        },
        (u, v) => ({ rgb: mixRgb(0xFFA800, 0xFFF4C4, v), a: 252 - v * 22, hy: 0.011 }), true);
      for (let i = 0; i < 12; i++) {
        const a = ph + TAU * i / 12;
        const bat = 1 + Math.sin(t * 2.0 + i * 0.9) * 0.10;
        const lng = (i % 2 ? 0.082 : 0.164) * bat;
        const base = 0.058;
        bande(o, 0.042,
          (u) => {
            const r = rExt - 0.016 + lng * u;
            return [Math.cos(a) * r, y + u * 0.024, Math.sin(a) * r];
          },
          (u) => base * (1 - u) * (1 - u * 0.35),       // triangle : finit sur un cube
          () => [-Math.sin(a), 0, Math.cos(a)],
          (u) => ({ rgb: mixRgb(0xFFD54A, 0xFFFFFF, u), a: 248 - u * 130, hy: 0.011 }));
      }
      return o;
    }
  },

  // --- 3. #097 Armure Eternelle : chaine de maillons -------------------------
  eternelle_097: {
    titre: "#097 Armure Éternelle Transcendante",
    famille: "permanent",
    accorde: "Combo #097. Partageait jusqu'ici la même branche de code que #098 — deux combos distincts pour un seul visuel.",
    note: "Une CHAÎNE fermée : quatorze maillons ovales enfilés les uns dans les autres, inclinés alternativement à la verticale et à l'horizontale comme un vrai maillage. « Armure éternelle » : une boucle de métal sans début ni fin. Pas un anneau de plus — un objet articulé.",
    build(t) {
      const o = [];
      const R = 0.248, y = 2.015, ph = t * 0.20;
      const demiLong = 0.058, demiCourt = 0.033;
      const nMail = 14;
      for (let k = 0; k < nMail; k++) {
        const a = ph + TAU * k / nMail;
        const c = [Math.cos(a) * R, y, Math.sin(a) * R];
        const tg = [-Math.sin(a), 0, Math.cos(a)];           // le long du collier
        const rd = [Math.cos(a), 0, Math.sin(a)];            // vers l'exterieur
        const e2 = (k % 2) ? rd : [0, 1, 0];                 // maillons croises
        // perimetre approche d'une ellipse (Ramanujan)
        const A = demiLong, B = demiCourt;
        const peri = Math.PI * (3 * (A + B) - Math.sqrt((3 * A + B) * (A + 3 * B)));
        const n = pasFerme(peri, 0.028);
        for (let i = 0; i < n; i++) {
          const b = TAU * i / n;
          const ca = Math.cos(b) * A, sa = Math.sin(b) * B;
          const chaud = (Math.sin(b * 2 + t * 1.6 + k) + 1) / 2;
          cube(o,
            c[0] + tg[0] * ca + e2[0] * sa,
            c[1] + tg[1] * ca + e2[1] * sa,
            c[2] + tg[2] * ca + e2[2] * sa,
            0.014, 0.014, 0.014,
            mixRgb(k % 2 ? 0xC98A12 : 0xFFD98A, 0xFFF8E0, chaud * 0.55),
            k % 2 ? 224 : 244);
        }
      }
      return o;
    }
  },

  // --- 4. #098 Dieu de la Lame : cercle d'epees dressees ---------------------
  dieu_lame_098: {
    titre: "#098 Dieu de la Lame Suprême",
    famille: "permanent",
    accorde: "Combo #098. Ne partage plus rien avec #097 : ce sont deux récompenses différentes, elles doivent se distinguer à l'œil nu.",
    note: "Six ÉPÉES dressées en cercle autour du crâne, pointe en l'air : lame qui s'affine jusqu'à un cube, garde en croix, poignée et pommeau. Elles flottent avec un léger déphasage. On lit « Dieu de la Lame » en une seconde — ce qu'un anneau doré ne dira jamais.",
    build(t) {
      const o = [];
      const ph = -t * 0.28, r = 0.268;
      for (let i = 0; i < 6; i++) {
        const a = ph + TAU * i / 6;
        const ca = Math.cos(a), sa = Math.sin(a);
        const flot = Math.sin(t * 1.6 + i * 1.047) * 0.020;
        const yg = 1.932 + flot;                   // hauteur de la garde
        const Lb = 0.232, pencher = 0.055;         // la pointe s'ecarte un peu
        // lame
        bande(o, 0.038,
          (u) => [ca * (r + pencher * u), yg + 0.030 + Lb * u, sa * (r + pencher * u)],
          (u) => 0.056 * (1 - u * u * 0.90),
          () => [-sa, 0, ca],
          (u, v) => ({
            rgb: mixRgb(0xFFBE28, 0xFFFFFF, 1 - Math.min(1, Math.abs(v) * 1.9)),
            a: 250 - u * 62
          }));
        // garde en croix
        trait(o, [ca * r - sa * 0.072, yg, sa * r + ca * 0.072],
                 [ca * r + sa * 0.072, yg, sa * r - ca * 0.072], 0.034,
          (u) => ({ rgb: mixRgb(0xFFE9A0, 0xFFB400, Math.abs(u - 0.5) * 2), a: 242 }));
        // poignee + pommeau
        trait(o, [ca * r, yg - 0.072, sa * r], [ca * r, yg, sa * r], 0.030,
          (u) => ({ rgb: mixRgb(0xA85F0C, 0xFFD98A, u), a: 234,
                    hx: u < 0.18 ? 0.021 : 0.015, hy: 0.015, hz: u < 0.18 ? 0.021 : 0.015 }));
      }
      return o;
    }
  },

  // --- 5. #101 Archange : aile emplumee --------------------------------------
  archange_101: {
    titre: "#101 Archange du Jugement Solaire",
    famille: "permanent",
    accorde: "Combo #101. Ailes permanentes. Exclusif d'Endurance Ailée (CosmeticPixelAura l.269-275 : if (archangel) … else …).",
    note: "Une VRAIE aile : charpente en trois os (humerus, avant-bras, main), huit rémiges primaires portées par la main, six secondaires sur l'avant-bras, deux rangées de couvertures qui recouvrent leurs bases, et l'alula au poignet. Chaque plume a son rachis clair et son vexille qui s'élargit au tiers avant de s'effiler. Le recouvrement des rangées est ce qui la rend pleine.",
    build(t) { return aileSeraphin(t); }
  },

  // --- 6. Endurance Ailee : voilure rigide -----------------------------------
  winged_endurance: {
    titre: "Endurance Ailée (enchantement d'armure)",
    famille: "permanent",
    accorde: "Enchantement d'armure d'endurance ailée. Ne sort QUE si le joueur ne touche pas le sol ou plane en élytre.",
    note: "Ce n'est PAS une aile à plumes en bleu : c'est une voilure. Longeron droit relevé vers le haut, cinq nervures rigides, membrane en panneaux facettés, bord de fuite tendu en ligne droite. Silhouette triangulaire et dure, là où l'Archange est arrondi et duveteux — aucun cube n'est calculé par le même code.",
    build(t) { return voilureGivre(t); }
  },

  // --- 7. #106 Seigneur de Guerre : sceau au sol -----------------------------
  seigneur_guerre_106: {
    titre: "#106 Seigneur de Guerre de l'Apocalypse",
    famille: "permanent",
    accorde: "Combo #106. Ornement de SOL — ne monte jamais vers le torse.",
    note: "Un sceau gravé au sol, entièrement PLAT (rien au-dessus de 0,05 bloc) : double cercle contra-rotatif, croix de guerre qui relie les deux, et quatre chevrons runiques dans l'entre-deux. Volontairement sans relief, pour ne pas se confondre avec les ronces de l'Aura de Terreur, qui est l'autre ornement de sol.",
    build(t) {
      const o = [];
      const y = 0.028, ph = -t * 0.16;
      const rExt = 0.505, rInt = 0.292;
      // cercle exterieur
      nappe(o, TAU * rExt, 0, 0.078,
        (u) => { const a = ph + TAU * u; return [Math.cos(a) * rExt, y, Math.sin(a) * rExt]; },
        (u) => ({ rgb: mixRgb(0xFF6A1E, 0xFFC46B, (Math.sin(TAU * u * 3 + t) + 1) / 2),
                  a: 210, hy: 0.011 }), true);
      // cercle interieur, en sens inverse
      nappe(o, TAU * rInt, 0, 0.070,
        (u) => { const a = -ph * 1.7 + TAU * u; return [Math.cos(a) * rInt, y, Math.sin(a) * rInt]; },
        () => ({ rgb: 0xFFA23C, a: 190, hy: 0.010 }), true);
      for (let i = 0; i < 4; i++) {
        const a = ph + TAU * i / 4 + 0.39;
        const ca = Math.cos(a), sa = Math.sin(a);
        // branche de la croix
        trait(o, [ca * rInt, y, sa * rInt], [ca * rExt, y, sa * rExt], 0.062,
          (u) => ({ rgb: mixRgb(0xFFA23C, 0xFF6A1E, u), a: 198, hy: 0.010 }));
        // chevron runique entre les deux branches
        const b = a + TAU / 8;
        const cb = Math.cos(b), sb = Math.sin(b), rm = (rExt + rInt) / 2;
        const puls = 0.5 + 0.5 * Math.sin(t * 1.9 + i * 1.57);
        trait(o, [cb * (rm - 0.055) - sb * 0.060, y, sb * (rm - 0.055) + cb * 0.060],
                 [cb * (rm + 0.062), y, sb * (rm + 0.062)], 0.048,
          () => ({ rgb: mixRgb(0xFF8A33, 0xFFE0B0, puls), a: 150 + puls * 70, hy: 0.010 }));
        trait(o, [cb * (rm - 0.055) + sb * 0.060, y, sb * (rm - 0.055) - cb * 0.060],
                 [cb * (rm + 0.062), y, sb * (rm + 0.062)], 0.048,
          () => ({ rgb: mixRgb(0xFF8A33, 0xFFE0B0, puls), a: 150 + puls * 70, hy: 0.010 }));
      }
      return o;
    }
  },

  // --- 8. #062 Peste : flaques et bulles -------------------------------------
  peste_062: {
    titre: "#062 Peste Ambulante",
    famille: "permanent",
    accorde: "Combo #062. Ornement de SOL.",
    note: "Aucun cercle : quatre flaques PLEINES au contour irrégulier qui suintent sous les pieds, et neuf grappes de bulles qui montent, grossissent et crèvent. Chaque bulle est une bille de sept cubes jointifs — jamais un pixel seul perdu en l'air. Une peste n'est pas symétrique, c'est le seul ornement du lot qui l'assume.",
    build(t) {
      const o = [];
      const flaques = [[0.35, 0.30, 0.108], [2.45, 0.37, 0.090],
                       [4.05, 0.26, 0.098], [5.45, 0.40, 0.076]];
      for (let k = 0; k < flaques.length; k++) {
        const [ang, d, ray] = flaques[k];
        const osc = Math.sin(t * 0.7 + k * 1.3) * 0.012;
        const cx = Math.cos(ang) * (d + osc), cz = Math.sin(ang) * (d + osc);
        disqueH(o, cx, 0.018, cz, ray, 0.062, (fr, th) => ({
          // contour irregulier : le rayon local ne depasse jamais 1 -> aucun trou
          k: 0.70 + 0.30 * (0.5 + 0.5 * Math.sin(th * 3 + k * 2.1 + t * 0.35)),
          rgb: mixRgb(0x2F5E08, 0x7FBF22, fr * 0.8 + 0.2),
          a: 205 - fr * 70, hy: 0.009
        }));
      }
      for (let i = 0; i < 9; i++) {
        const p = (t * 0.33 + i * 0.111) % 1;
        const ang = i * 2.618 + Math.sin(i * 4.7) * 1.4;
        const r = 0.18 + (i % 4) * 0.075;
        const rb = 0.0165 * (0.75 + p * 0.55);          // la bulle gonfle en montant
        bille(o, Math.cos(ang) * r, 0.030 + p * 0.305, Math.sin(ang) * r, rb,
          mixRgb(0x9BDC33, 0x3E7A0C, p), 215 * (1 - p * p), 0xD8F2A0);
      }
      return o;
    }
  },

  // --- 9. Roi : couronne a fleurons ------------------------------------------
  king: {
    titre: "Roi (enchantement d'armure)",
    famille: "permanent",
    accorde: "Enchantement d'armure « king ». Exclusif face à #110 et #097/#098.",
    note: "Une couronne POSÉE sur le crâne et qui NE TOURNE PAS — une couronne qui pivote sur la tête de son porteur n'a aucun sens. Bandeau cylindrique plein de 6 cm de haut, cinq fleurons trapézoïdaux, une perle au sommet de chacun, et quatre gemmes serties dans le bandeau.",
    build(t) {
      const o = [];
      const r = 0.168, yb = 1.828, hb = 0.060;
      const lueur = (Math.sin(t * 1.5) + 1) / 2;
      // bandeau : cylindre plein (nappe verticale fermee)
      nappe(o, TAU * r, hb, 0.046,
        (u, v) => { const a = TAU * u; return [Math.cos(a) * r, yb + hb * v, Math.sin(a) * r]; },
        (u, v) => ({ rgb: mixRgb(0xA8700C, 0xFFE07A, 0.25 + v * 0.75), a: 244 }), true);
      for (let i = 0; i < 5; i++) {
        const a = TAU * i / 5 - Math.PI / 2;            // un fleuron pile devant
        const ca = Math.cos(a), sa = Math.sin(a);
        const hf = 0.072 + (i === 0 ? 0.026 : 0);       // celui de devant domine
        // fleuron : trapeze plein qui se resserre vers le haut
        bande(o, 0.042,
          (u) => [ca * r, yb + hb + hf * u, sa * r],
          (u) => 0.082 * (1 - u * 0.72),
          () => [-sa, 0, ca],
          (u) => ({ rgb: mixRgb(0xFFC93A, 0xFFF6D0, u * (0.4 + lueur * 0.6)), a: 246 - u * 16 }));
        // perle au sommet, collee au fleuron
        cube(o, ca * r, yb + hb + hf + 0.019, sa * r, 0.019, 0.019, 0.019,
          mixRgb(0xFFF6D0, 0xFFFFFF, lueur), 250);
        // gemme sertie dans le bandeau, entre deux fleurons
        const b = a + Math.PI / 5;
        cube(o, Math.cos(b) * (r + 0.009), yb + hb * 0.5, Math.sin(b) * (r + 0.009),
          0.017, 0.019, 0.017, mixRgb(0xE03A5A, 0xFF9AB0, lueur * 0.7), 240);
      }
      return o;
    }
  },

  // --- 10. Esprit Mystique : lemniscate --------------------------------------
  mystic_mind: {
    titre: "Esprit Mystique (enchantement d'armure)",
    famille: "permanent",
    accorde: "Enchantement d'armure « mystic_mind ». Se cumule avec les autres.",
    note: "Un signe d'infini — lemniscate de Bernoulli — qui tourne lentement au-dessus du crâne, avec trois nœuds de pensée qui le parcourent. Échantillonné à PAS CONSTANT EN LONGUEUR D'ARC : parcourue naïvement, une lemniscate se resserre au croisement et s'écarte aux pointes, ce qui aurait rouvert exactement le défaut d'espacement qu'on corrige.",
    build(t) {
      const o = [];
      const A = 0.305, y = 2.060, spin = t * 0.42, w = 0.038;
      const pts = boucleEgale((s) => {
        const th = TAU * s, d = 1 + Math.sin(th) * Math.sin(th);
        const px = A * Math.cos(th) / d, pz = A * Math.sin(th) * Math.cos(th) / d;
        return [px * Math.cos(spin) - pz * Math.sin(spin),
                y + Math.sin(th * 2) * 0.026,
                px * Math.sin(spin) + pz * Math.cos(spin)];
      }, w * 0.9, 720);
      for (let i = 0; i < pts.length; i++) {
        const u = i / pts.length;
        let eclat = 0;
        for (let k = 0; k < 3; k++) {
          let d = Math.abs(u - ((t * 0.21 + k / 3) % 1)); if (d > 0.5) d = 1 - d;
          eclat = Math.max(eclat, Math.max(0, 1 - d * 13));
        }
        cube(o, pts[i][0], pts[i][1], pts[i][2], 0.019, 0.019, 0.019,
          mixRgb(mixRgb(0x7B3BE8, 0xC08BFF, (Math.sin(u * TAU * 2) + 1) / 2), 0xF2E6FF, eclat),
          210 + eclat * 45);
      }
      return o;
    }
  },

  // --- 11. Egide Radieuse : coupole ------------------------------------------
  radiant_aegis: {
    titre: "Égide Radieuse (enchantement d'armure)",
    famille: "permanent",
    accorde: "Enchantement d'armure « heart_resonance ». Se cumule.",
    note: "Une coupole, donc un volume : cercle équatorial, parallèle à 45°, cinq méridiens qui montent au zénith et une clef de voûte. Une égide est un bouclier — ça doit se voir en silhouette, de profil comme de face. Base à 1,985, sommet à 2,29.",
    build(t) {
      const o = [];
      const y0 = 1.985, R = 0.298, ph = t * 0.30;
      // equateur
      nappe(o, TAU * R, 0, 0.058,
        (u) => { const a = ph + TAU * u; return [Math.cos(a) * R, y0, Math.sin(a) * R]; },
        (u) => ({ rgb: mixRgb(0xBFE8FF, 0xFFF7D9, (Math.sin(TAU * u * 2 + t) + 1) / 2),
                  a: 224, hy: 0.011 }), true);
      // parallele a 45 degres
      const e45 = Math.PI / 4, r45 = Math.cos(e45) * R;
      nappe(o, TAU * r45, 0, 0.056,
        (u) => { const a = -ph + TAU * u; return [Math.cos(a) * r45, y0 + Math.sin(e45) * R, Math.sin(a) * r45]; },
        () => ({ rgb: 0xDCF2FF, a: 198, hy: 0.011 }), true);
      // meridiens
      for (let i = 0; i < 5; i++) {
        const az = ph + TAU * i / 5;
        courbe(o, 0.052,
          (u) => {
            const e = u * Math.PI / 2;
            return [Math.cos(az) * Math.cos(e) * R, y0 + Math.sin(e) * R, Math.sin(az) * Math.cos(e) * R];
          },
          (u) => ({ rgb: mixRgb(0xBFE8FF, 0xFFFFFF, u), a: 210 - u * 30 }));
      }
      // clef de voute, posee sur les meridiens
      const p = (Math.sin(t * 1.9) + 1) / 2;
      cube(o, 0, y0 + R + 0.018, 0, 0.024 + p * 0.006, 0.024 + p * 0.006, 0.024 + p * 0.006,
        mixRgb(0xFFF7D9, 0xFFFFFF, p), 245);
      return o;
    }
  },

  // --- 12. Aura de Terreur : ronces crochues ---------------------------------
  dread_aura: {
    titre: "Aura de Terreur (enchantement d'armure)",
    famille: "permanent",
    accorde: "Enchantement d'armure « dread_aura ». Ornement de SOL.",
    note: "Neuf ronces qui sortent de terre, se courbent vers l'extérieur et se CROCHENT en pointe, plus cinq fissures qui rampent au sol depuis leur pied. Épaisses à la base, effilées au crochet. Sommet à 0,40 — sous le genou. C'est du relief, à l'opposé du sceau plat du Seigneur de Guerre.",
    build(t) {
      const o = [];
      const ph = -t * 0.19;
      for (let i = 0; i < 8; i++) {
        const a = ph + TAU * i / 8;
        const ca = Math.cos(a), sa = Math.sin(a);
        const r0 = 0.355, haut = 0.300 + Math.sin(t * 1.25 + i * 0.70) * 0.070;
        // courbe de la ronce : monte, s'ecarte, puis se crochete
        courbe(o, 0.030,
          (u) => {
            const dr = 0.145 * Math.sin(u * 1.95) + 0.060 * u * u;
            return [ca * (r0 + dr), 0.012 + haut * Math.pow(u, 0.82), sa * (r0 + dr)];
          },
          (u) => ({
            rgb: mixRgb(0x2C0752, 0xA24BFF, Math.pow(u, 0.7)),
            a: 215 - u * 55,
            hx: 0.028 * (1 - u * 0.38), hy: 0.028 * (1 - u * 0.38), hz: 0.028 * (1 - u * 0.38)
          }));
        // fissure au sol, partant du pied d'une ronce sur deux
        if (i % 2 === 0) {
          trait(o, [ca * r0, 0.010, sa * r0], [ca * (r0 + 0.235), 0.010, sa * (r0 + 0.235)], 0.030,
            (u) => ({
              rgb: mixRgb(0x6B1FB5, 0x1B0433, u),
              a: 175 * (1 - u * 0.85),
              hx: 0.020 * (1 - u * 0.34), hy: 0.008, hz: 0.020 * (1 - u * 0.34)
            }));
        }
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
    note: "Un diadème qui naît au ras du crâne, s'ouvre et s'efface en montant : l'anneau porte cinq pointes qui se dressent au fur et à mesure. Ce relief vertical est ce qui le distingue de l'onde de choc, qui reste parfaitement plate.",
    build(t) {
      const o = [];
      const p = (t * 0.5) % 1;
      const r = 0.172 + p * 0.330, y = 1.925 + p * 0.455;
      const al = Math.round(252 * Math.pow(1 - p, 0.75));
      nappe(o, TAU * r, 0, 0.046,
        (u) => { const a = p * 2.1 + TAU * u; return [Math.cos(a) * r, y, Math.sin(a) * r]; },
        () => ({ rgb: mixRgb(0xFFF3C8, 0xFFC63D, p), a: al, hy: 0.011 }), true);
      for (let i = 0; i < 5; i++) {
        const a = p * 2.1 + TAU * i / 5;
        const ca = Math.cos(a), sa = Math.sin(a);
        bande(o, 0.040,
          (u) => [ca * r, y + (0.045 + p * 0.070) * u, sa * r],
          (u) => 0.055 * (1 - u * 0.85),
          () => [-sa, 0, ca],
          (u) => ({ rgb: mixRgb(0xFFC63D, 0xFFFFFF, u), a: al * (1 - u * 0.35) }));
      }
      return o;
    }
  },

  wing_ribbons: {
    titre: "wingRibbons — rubans d'ailes",
    famille: "ponctuel",
    accorde: "#081 Ange à la Plume Céleste (l.646).",
    note: "Deux rubans PLATS qui se déroulent derrière les épaules. La largeur n'est pas un trait épaissi : c'est une bande dont la direction transversale tourne le long du ruban, si bien qu'il se présente tantôt de face, tantôt de chant. Strictement derrière le dos (z ≤ −0,18).",
    build(t) {
      const o = [];
      for (const s of [-1, 1]) {
        bande(o, 0.054,
          (u) => [s * (0.165 + u * 0.300 + Math.sin(u * 4.6 - t * 2.8) * 0.095 * u),
                  1.455 - u * 0.760 + Math.cos(u * 3.7 - t * 2.3) * 0.055,
                  -0.205 - u * 0.400],
          (u) => 0.090 * (1 - u * 0.78),
          (u) => {
            const tw = u * 3.5 - t * 2.1 + (s > 0 ? 0 : 1.1);
            return norm([Math.cos(tw) * s, Math.sin(tw) * 0.75, Math.sin(tw) * 0.18]);
          },
          (u, v) => ({ rgb: mixRgb(0xFFFBEA, 0xFFC94E, u * 0.75 + Math.abs(v) * 0.5),
                       a: 240 * (1 - u * 0.68) }));
      }
      return o;
    }
  },

  double_helix: {
    titre: "doubleHelix — double hélice ascendante",
    famille: "ponctuel",
    accorde: "#086 Berserker Immortel (l.667).",
    note: "Deux brins et leurs BARREAUX, comme un brin d'ADN : sans barreaux, deux spirales côte à côte ne sont que deux spirales. Mais un barreau traverse l'axe, donc le visage : ceux-ci sont posés sous le genou et au-dessus du crâne, et la portion qui longe la tête reçoit des barbes tournées vers l'EXTÉRIEUR. Rayon 0,66, soit au-delà du champ de vision.",
    build(t) {
      const o = [];
      const tours = 0.8, monte = 2.00, R = 0.66, y0 = 0.055;
      for (const fil of [0, 1]) {
        courbe(o, 0.062,
          (u) => {
            const a = u * TAU * tours + fil * Math.PI - t * 1.5;
            return [Math.cos(a) * R, y0 + u * monte, Math.sin(a) * R];
          },
          (u) => ({ rgb: mixRgb(fil ? 0xC00000 : 0xFF7A3D, 0xFFE0C0, u),
                    a: Math.round(234 * (1 - u * 0.45)) }));
      }
      // Barreaux complets UNIQUEMENT la ou l'axe est hors du champ de vision :
      // sous 1,05 bloc et au-dessus du crane. Au milieu, un barreau passerait
      // devant les yeux — c'est exactement ce qu'un effet ne doit jamais faire.
      for (const u of [0.085, 0.245, 0.405, 0.930]) {
        const y = y0 + u * monte;
        if (y > ZONE_REGARD.bas - 0.05 && y < ZONE_REGARD.haut + 0.06) continue;
        const a = u * TAU * tours - t * 1.5;
        trait(o, [Math.cos(a) * R, y, Math.sin(a) * R],
                 [Math.cos(a + Math.PI) * R, y, Math.sin(a + Math.PI) * R], 0.076,
          (v) => ({ rgb: mixRgb(0xFF9A5A, 0xFFE9C8, Math.sin(Math.PI * v)),
                    a: Math.round(152 * (1 - u * 0.45) * (0.40 + 0.60 * Math.sin(Math.PI * v))) }));
      }
      // Barbes vers l'exterieur sur la portion qui longe la tete.
      for (const fil of [0, 1]) for (let k = 0; k < 4; k++) {
        const u = 0.53 + k * 0.085;
        const a = u * TAU * tours + fil * Math.PI - t * 1.5;
        const ca = Math.cos(a), sa = Math.sin(a), y = y0 + u * monte;
        trait(o, [ca * R, y, sa * R], [ca * (R + 0.105), y + 0.030, sa * (R + 0.105)], 0.050,
          (v) => ({ rgb: mixRgb(0xFF7A3D, 0xFFE9C8, v), a: Math.round(190 * (1 - v * 0.7)) }));
      }
      return o;
    }
  },

  cloud_cushion: {
    titre: "cloudCushion — coussin de nuages",
    famille: "ponctuel",
    accorde: "#063 Pont Dimensionnel (l.572), #090 Faucon Ouragan (l.703).",
    note: "Cinq bouffées PLEINES qui se recouvrent pour former une galette sous les semelles — un volume, pas des anneaux emboîtés. Chaque bouffée est un empilement de deux disques pleins qui respire indépendamment. Hauteur maximale 0,15 : le joueur a l'air posé dessus.",
    build(t) {
      const o = [];
      const bouffees = [[0, 0, 0.125], [0.235, 0.055, 0.098], [-0.215, 0.085, 0.092],
                        [0.070, -0.225, 0.090], [-0.090, 0.215, 0.086]];
      for (let k = 0; k < bouffees.length; k++) {
        const [dx, dz, R] = bouffees[k];
        const g = 1 + Math.sin(t * 1.35 + k * 1.25) * 0.085;
        for (const [dy, ech] of [[0.040, 1.0], [0.098, 0.70]]) {
          disqueH(o, dx, dy * g, dz, R * g * ech, 0.072, (fr) => ({
            rgb: mixRgb(0xFFFFFF, 0xC9DCEE, fr * 0.55 + (1 - ech) * 0.3),
            a: (212 - fr * 48) * (ech < 1 ? 0.88 : 1), hy: 0.030
          }));
        }
      }
      return o;
    }
  },

  tether_beam: {
    titre: "tetherBeam — rayon tendu",
    famille: "ponctuel",
    accorde: "#099 Seigneur de Sang (l.1513), #084 Faucheur de Tempête (l.1963), #074 Archer d'Orage (l.3389).",
    note: "Un noyau rectiligne, une spirale qui l'enrobe sur trois tours et trois nœuds de charge qui filent vers la cible. Il part de la MAIN (y 1,02) et s'éloigne vers +Z : dès le quatrième cube il est hors du champ de vision, et il ne traverse jamais la tête du porteur.",
    build(t) {
      const o = [];
      const A = [0.352, 0.968, 0.115], B = [0.352, 1.240, 2.560];
      const L = dist3(A, B);
      trait(o, A, B, 0.054, (u) => ({
        rgb: mixRgb(0xC00000, 0xFFD2D2, Math.sin(Math.PI * u) * 0.9),
        a: Math.round(250 - Math.abs(u - 0.5) * 70)
      }));
      const ax = norm([B[0] - A[0], B[1] - A[1], B[2] - A[2]]);
      const e1 = perp(ax, [0, 1, 0]), e2 = norm([
        ax[1] * e1[2] - ax[2] * e1[1], ax[2] * e1[0] - ax[0] * e1[2], ax[0] * e1[1] - ax[1] * e1[0]]);
      const rs = 0.052, tours = 3;
      courbe(o, 0.040,
        (u) => {
          const a = u * TAU * tours - t * 6.5;
          const c = lerp3(A, B, u), ca = Math.cos(a) * rs, sa = Math.sin(a) * rs;
          return [c[0] + e1[0] * ca + e2[0] * sa, c[1] + e1[1] * ca + e2[1] * sa,
                  c[2] + e1[2] * ca + e2[2] * sa];
        },
        (u) => ({ rgb: mixRgb(0xFF5A5A, 0xFFF0F0, u), a: Math.round(205 * (0.45 + 0.55 * Math.sin(Math.PI * u))) }));
      for (let k = 0; k < 3; k++) {
        const u = ((t * 0.65 + k / 3) % 1);
        const c = lerp3(A, B, u);
        bille(o, c[0], c[1], c[2], 0.026, 0xFFE2E2, 235, 0xFF7070);
      }
      return o;
    }
  },

  shockwave_ring: {
    titre: "shockwaveRing — onde de choc",
    famille: "ponctuel",
    accorde: "La forme la plus utilisée du registre : 43 appels dans EnchantmentComboHandler.",
    note: "Un bourrelet plat qui s'étend au sol, avec sa jupe de poussière accrochée dessous. Le nombre de cubes AUGMENTE avec le rayon — c'est tout l'intérêt de la règle : l'onde reste pleine du début à la fin, au lieu de se désagréger en pointillés comme le faisait la version à nombre fixe de particules.",
    build(t) {
      const o = [];
      const p = (t * 0.55) % 1;
      const r = 0.24 + p * 0.92, w = 0.068;
      const al = Math.round(244 * Math.pow(1 - p, 1.25));
      const n = pasFerme(TAU * r, w);
      for (let i = 0; i < n; i++) {
        const a = TAU * i / n;
        const ca = Math.cos(a), sa = Math.sin(a);
        const bosse = 0.045 * Math.sin(Math.PI * (1 - p)) * (0.8 + 0.2 * Math.sin(a * 7 + t));
        cube(o, ca * r, 0.030 + bosse, sa * r, w / 2, 0.016, w / 2,
          mixRgb(0xFFE9A0, 0xFF8A20, p), al);
        if (i % 5 === 0) {                     // jupe de poussiere, collee au bourrelet
          cube(o, ca * (r - 0.030), 0.030 + bosse * 0.45, sa * (r - 0.030),
            0.022, 0.014, 0.022, mixRgb(0xFFC77A, 0x8A5A20, p), Math.round(al * 0.55));
        }
      }
      return o;
    }
  },

  burst_on_entity: {
    titre: "burstOnEntity — éclat sur la cible",
    famille: "ponctuel",
    accorde: "La plus fréquente du registre : 44 appels. Se joue SUR LA CIBLE, jamais sur le porteur.",
    note: "Une étoile d'impact : onze dards qui jaillissent du point touché dans onze directions réparties en spirale de Fibonacci, plus le noyau incandescent. Rendu sur la cible, à 1,9 bloc devant le joueur. Une coque sphérique complète coûtait 2 248 cubes pour une lecture moins nette.",
    build(t) {
      const o = [];
      const p = (t * 0.75) % 1;
      const cx = 0, cy = 1.00, cz = 1.90;
      const L = 0.095 + p * 0.330;
      const al = Math.round(240 * Math.pow(1 - p, 1.1));
      const N = 11, phi = Math.PI * (3 - Math.sqrt(5));
      bille(o, cx, cy, cz, 0.030 * (1 - p * 0.4), mixRgb(0xFFF3B0, 0xFF4A10, p), al, 0xFFFFFF);
      for (let i = 0; i < N; i++) {
        const yv = 1 - (i + 0.5) * 2 / N;
        const ra = Math.sqrt(Math.max(0, 1 - yv * yv));
        const th = phi * i + t * 0.9;
        const d = [Math.cos(th) * ra, yv, Math.sin(th) * ra];
        courbe(o, 0.032,
          (u) => [cx + d[0] * (0.022 + L * u), cy + d[1] * (0.022 + L * u), cz + d[2] * (0.022 + L * u)],
          (u) => ({ rgb: mixRgb(0xFFF3B0, 0xFF4A10, Math.max(p, u)),
                    a: Math.round(al * (1 - u * 0.55)),
                    hx: 0.021 * (1 - u * 0.30), hy: 0.021 * (1 - u * 0.30), hz: 0.021 * (1 - u * 0.30) }));
      }
      return o;
    }
  }
};

// =============================================================================
//  #101 — AILE DE SERAPHIN
//
//  Charpente : epaule -> coude -> poignet -> bout de la main. Sur la main
//  s'inserent les remiges PRIMAIRES (les longues), sur l'avant-bras les
//  SECONDAIRES (les courtes) ; les GRANDES COUVERTURES recouvrent la base des
//  secondaires, les MOYENNES recouvrent la base des grandes, et le bouquet de
//  l'ALULA ferme le poignet. C'est cet etagement — et non une rangee unique
//  de traits — qui donne a une aile sa masse et son dessin.
//
//  Toute la piece est a z <= -0.16 : strictement derriere le dos.
// =============================================================================

/** Une plume : rachis clair au milieu, vexille qui s'elargit au tiers puis s'effile. */
function plume(out, base, pointe, larg, w, rgbSombre, rgbClair, aBase, aPointe) {
  const axe = norm([pointe[0] - base[0], pointe[1] - base[1], pointe[2] - base[2]]);
  const lat = perp(axe, [axe[1] * 0 - axe[2] * 1, 0, 1 * axe[0] - 0 * axe[1]]);
  bande(out, w,
    (u) => lerp3(base, pointe, u),
    (u) => larg * plumeProfil(u),
    () => lat,
    (u, v) => {
      const rachis = 1 - Math.min(1, Math.abs(v) * 2.4);     // la nervure centrale est claire
      return {
        rgb: mixRgb(mixRgb(rgbSombre, rgbClair, u * 0.55), 0xFFFFFF, rachis * 0.45),
        a: aBase + (aPointe - aBase) * u
      };
    });
}

function plumeProfil(u) {
  // Profil d'un vexille de plume. Etroit a la hampe (cette partie est de
  // toute facon recouverte par la plume voisine : la dessiner large ne
  // produirait que du gaspillage et un paquet informe), large sur la moitie
  // exposee, puis effile jusqu'a un seul cube.
  const ouvre = Math.min(1, Math.max(0, (u - 0.08) / 0.40));
  const s = ouvre * ouvre * (3 - 2 * ouvre);
  return (0.26 + 0.74 * s) * (1 - Math.pow(u, 3) * 0.94);
}

function aileSeraphin(t) {
  const o = [];
  const resp = Math.sin(t * 1.15);                 // respiration lente, pas un battement
  const OR = 0xD99420, CHAUD = 0xFFD264, CLAIR = 0xFFF3CF, PALE = 0xFFFBEA;
  for (const s of [-1, 1]) {
    // Charpente en ARCHE : l'epaule est basse, le coude monte a hauteur de
    // crane et le poignet passe AU-DESSUS de la tete. C'est cette montee qui
    // fait l'aile d'ange ; a plat, on n'obtient qu'un croissant colle aux
    // omoplates — exactement ce que donnait la version precedente.
    // Le dos du torse est a z = -0.113. Toute la piece est repoussee derriere
    // lui : l'epaulette s'etale en largeur et mordait de 1,5 cm sur l'avant.
    const E = [s * 0.110, 1.400, -0.200];                                  // epaule
    const C = [s * 0.235, 1.722 + resp * 0.026, -0.316];                   // coude
    const P = [s * 0.398, 1.988 + resp * 0.042, -0.398];                   // poignet
    const M = [s * 0.524, 1.930 + resp * 0.048, -0.442];                   // bout de la main

    // --- bord d'attaque : l'os, en bande pleine ----------------------------
    for (const [a, b, lg] of [[E, C, 0.060], [C, P, 0.050], [P, M, 0.038]]) {
      const ax = norm([b[0] - a[0], b[1] - a[1], b[2] - a[2]]);
      const lat = perp(ax, [0, 0, 1]);
      bande(o, 0.074,
        (u) => lerp3(a, b, u), () => lg, () => lat,
        (u, v) => ({ rgb: mixRgb(OR, CLAIR, 0.30 + Math.abs(v) * 1.2), a: 250 }));
    }

    // --- 7 remiges primaires : la longue cascade portee par la main --------
    for (let i = 0; i < 7; i++) {
      const f = i / 6;
      const base = lerp3(P, M, f * 0.98);
      // La plus externe est la plus longue : c'est elle qui donne la ligne.
      // Un leger decalage une plume sur deux evite un bord de fuite coupe au
      // couteau, que l'oeil lit comme une decoupe de carton.
      const L = 0.600 + 0.560 * Math.pow(f, 0.92) + (i % 2 ? -0.032 : 0.024);
      const d = norm([s * (0.015 + 0.235 * f), -0.975, -0.170 - 0.040 * f]);
      const bout = [base[0] + d[0] * L, base[1] + d[1] * L, base[2] + d[2] * L];
      // une plume sur deux legerement plus sombre : c'est l'ombre de
      // recouvrement qui fait qu'on compte les plumes au lieu de voir un bloc
      const ombre = i % 2 ? 0.72 : 1.0;
      plume(o, base, bout, 0.086, 0.085,
        mixRgb(0x6B4408, OR, ombre), mixRgb(CHAUD, PALE, ombre), 248, 168);
    }

    // --- 5 remiges secondaires : le rideau court de l'avant-bras -----------
    for (let i = 0; i < 4; i++) {
      const f = i / 3;
      const base = lerp3(C, P, 0.06 + f * 0.88);
      const L = 0.470 + 0.145 * f;
      const d = norm([s * (0.022 + 0.062 * f), -0.980, -0.185]);
      const bout = [base[0] + d[0] * L, base[1] + d[1] * L, base[2] + d[2] * L];
      const ombre = i % 2 ? 0.74 : 1.0;
      plume(o, base, bout, 0.080, 0.084,
        mixRgb(0x6B4408, OR, ombre), mixRgb(CHAUD, CLAIR, ombre), 246, 196);
    }

    // --- 6 grandes couvertures : elles coiffent la base des secondaires ----
    for (let i = 0; i < 5; i++) {
      const f = i / 4;
      const base = lerp3(C, P, 0.04 + f * 0.90);
      const L = 0.215 + 0.040 * Math.sin(Math.PI * f);
      const d = norm([s * 0.030, -0.975, -0.195]);
      plume(o, [base[0], base[1] - 0.008, base[2] + 0.014],
        [base[0] + d[0] * L, base[1] + d[1] * L - 0.008, base[2] + d[2] * L + 0.014],
        0.062, 0.078, 0xB07813, CLAIR, 252, 222);
    }

    // --- epaulette : la masse de petites couvertures a l'emplanture --------
    const axEp = norm([C[0] - E[0], C[1] - E[1], C[2] - E[2]]);
    bande(o, 0.084,
      (u) => lerp3(E, C, 0.05 + u * 0.88),
      (u) => 0.112 * Math.sin(Math.PI * (0.24 + 0.66 * u)),
      () => perp(axEp, [0, 0, 1]),
      (u, v) => ({ rgb: mixRgb(0xA8710F, CLAIR, 0.22 + Math.abs(v) * 1.3), a: 246 }));

    // --- alula : le petit bouquet du poignet -------------------------------
    for (let i = 0; i < 2; i++) {
      const base = [P[0] + s * 0.028 * (i + 1), P[1] - 0.012 * i, P[2] - 0.014];
      const d = norm([s * (0.52 + 0.14 * i), -0.74, -0.42]);
      const L = 0.145 - i * 0.022;
      plume(o, base, [base[0] + d[0] * L, base[1] + d[1] * L, base[2] + d[2] * L],
        0.048, 0.072, 0xE8A32A, 0xFFFFFF, 250, 200);
    }
  }
  return o;
}

// =============================================================================
//  ENDURANCE AILEE — VOILURE DE GIVRE
//
//  Volontairement a l'oppose de l'aile de l'Archange, et pas une seule ligne
//  de code en commun :
//     - le longeron monte vers le HAUT (l'aile de seraphin descend) ;
//     - le bord de fuite est une DROITE tendue (l'autre est festonne) ;
//     - la surface est une membrane facettee en panneaux, sans aucune plume ;
//     - cinq nervures rigides la traversent, visibles comme des arretes.
//  Silhouette triangulaire et dure : on ne peut pas les confondre, meme en
//  noir et blanc.
// =============================================================================

function voilureGivre(t) {
  const o = [];
  const souffle = Math.sin(t * 1.5);
  const GIVRE = 0x9ACFF5, BLANC = 0xFFFFFF, OMBRE = 0x3E6A92, CYAN = 0xD6F0FF;
  const NP = 5;                                       // nombre de panneaux
  for (const s of [-1, 1]) {
    const E = [s * 0.118, 1.388, -0.174];                                   // emplanture
    const P = [s * (0.790 + souffle * 0.022), 1.792 + souffle * 0.040, -0.332]; // bout du longeron
    const dc = norm([0, -0.880, -0.470]);               // direction de la corde
    const corde = (u) => 0.455 * (1 - 0.615 * u);

    // --- panneaux rigides, un par un : c'est le facettage qui fait la glace
    for (let k = 0; k < NP; k++) {
      const u0 = k / NP, u1 = (k + 1) / NP;
      const clair = k % 2 ? 0.0 : 0.30;
      bande(o, 0.078,
        (u) => {
          const uu = u0 + (u1 - u0) * u, c = lerp3(E, P, uu), h = corde(uu) * 0.5;
          return [c[0] + dc[0] * h, c[1] + dc[1] * h, c[2] + dc[2] * h];
        },
        (u) => corde(u0 + (u1 - u0) * u) * 0.92,
        () => dc,
        (u, v) => ({
          rgb: mixRgb(mixRgb(OMBRE, GIVRE, 0.34 + clair), BLANC, Math.max(0, 0.5 - Math.abs(v)) * 0.75),
          a: 112 + clair * 150 + (0.5 - Math.abs(v)) * 54
        }));
    }

    // --- longeron : l'arete d'attaque, franche et brillante ----------------
    trait(o, E, P, 0.050, (u) => ({ rgb: mixRgb(CYAN, BLANC, u), a: 252 }));

    // --- nervures : les aretes entre panneaux, bien plus claires -----------
    for (let k = 0; k <= NP; k++) {
      const u = k / NP, c = lerp3(E, P, u), cl = corde(u);
      trait(o, c, [c[0] + dc[0] * cl, c[1] + dc[1] * cl, c[2] + dc[2] * cl], 0.040,
        (v) => ({ rgb: mixRgb(BLANC, CYAN, v), a: 244 - v * 70 }));
    }

    // --- bord de fuite : la droite tendue qui ferme le triangle ------------
    const F0 = [E[0] + dc[0] * corde(0), E[1] + dc[1] * corde(0), E[2] + dc[2] * corde(0)];
    const F1 = [P[0] + dc[0] * corde(1), P[1] + dc[1] * corde(1), P[2] + dc[2] * corde(1)];
    trait(o, F0, F1, 0.044, (u) => ({ rgb: mixRgb(GIVRE, BLANC, 0.25 + u * 0.6), a: 228 }));

    // --- stalactites sous le bord de fuite : le detail qui dit << givre >> -
    for (let k = 0; k < 5; k++) {
      const u = 0.10 + k * 0.20;
      const b = lerp3(F0, F1, u);
      const lg = 0.115 - k * 0.016 + Math.sin(t * 2.1 + k) * 0.010;
      bande(o, 0.036,
        (v) => [b[0], b[1] - lg * v, b[2] - lg * v * 0.16],
        (v) => 0.040 * (1 - v) * (1 - v),
        () => norm([P[0] - E[0], 0, P[2] - E[2]]),
        (v) => ({ rgb: mixRgb(CYAN, BLANC, v), a: 230 - v * 120 }));
    }

    // --- pointe de givre au bout du longeron -------------------------------
    const ax = norm([P[0] - E[0], P[1] - E[1], P[2] - E[2]]);
    bille(o, P[0] + ax[0] * 0.030, P[1] + ax[1] * 0.030, P[2] + ax[2] * 0.030,
      0.021, BLANC, 252, CYAN);
  }
  return o;
}

window.DLB_MODELS = MODELS;
window.DLB_H = H;
window.DLB_ZONE_REGARD = ZONE_REGARD;
