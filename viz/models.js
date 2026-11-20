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
// ---------------------------------------------------------------------------
//  GROS PIXELS DE NUAGE
//  Un nuage ne se dessine pas comme une arete : il n'a pas de trait a suivre,
//  il a du VOLUME. On remplit donc un ellipsoide avec de gros cubes poses sur
//  un reseau dont le pas est DEDUIT du grain (jamais en dur), et on fait varier
//  la taille de chaque cube : gros au coeur, plus petits en bordure. C'est ce
//  melange de tailles qui donne le moutonnement, la ou des cubes tous egaux
//  donnent une patate lisse.
// ---------------------------------------------------------------------------
function aleaPixel(a, b, c) {
  const s = Math.sin(a * 127.1 + b * 311.7 + c * 74.7) * 43758.5453;
  return s - Math.floor(s);
}

function bouffeeNuage(out, cx, cy, cz, rx, ry, rz, grain, graine, bas, haut, al0,
                      yBas, yEtendue, yMin, yMax) {
  // Pas = DEUX fois le grain : les cubes sont JOINTIFS, pas noyes les uns dans
  // les autres. C'est la condition pour qu'on voie chaque gros pixel au lieu
  // d'une patate lissee. La taille est uniforme DANS une bouffee ; la variete
  // de tailles vient du grain, qui change d'une bouffee a l'autre.
  const pas = grain * 2;
  const nx = Math.ceil(rx / pas), ny = Math.ceil(ry / pas), nz = Math.ceil(rz / pas);
  for (let ix = -nx; ix <= nx; ix++)
    for (let iy = -ny; iy <= ny; iy++)
      for (let iz = -nz; iz <= nz; iz++) {
        const px = ix * pas, py = iy * pas, pz = iz * pas;
        const d = Math.hypot(px / rx, py / ry, pz / rz);
        // Bord bossele, mais COHERENT (fonction lisse de la position) : des
        // lobes larges, pas du bruit point par point qui isolerait des cubes.
        const lobe = 0.11 * Math.sin(px * 6.5 + graine) * Math.cos(pz * 5.5 - graine)
                   + 0.07 * Math.sin(py * 8.0 + graine * 2.1);
        if (d > 1 + lobe) continue;
        // Butee dure sur la fenetre imposee : le nuage vit SOUS les pieds, un
        // cube qui en sortirait est retire, pas rogne. Le reglage du tableau
        // fait que cette butee ne mord presque jamais ; elle garantit la regle.
        if (yMin !== undefined && (cy + py - grain < yMin || cy + py + grain > yMax)) continue;
        // La teinte suit la hauteur REELLE dans le nuage, pas la hauteur dans
        // la bouffee : sinon le sommet d'un lobe bas serait aussi clair que la
        // crete, et l'eclairage se contredirait d'un lobe a l'autre.
        const hGlob = Math.min(1, Math.max(0, (cy + py - yBas) / yEtendue));
        const r = aleaPixel(ix + graine, iy - graine, iz + graine * 2);
        // Le hasard ne sert qu'a departager deux cubes de meme hauteur : trop
        // fort, il seme des blocs sombres au beau milieu du dessus eclaire.
        const f = Math.min(1, Math.max(0, 0.34 + 0.62 * Math.pow(hGlob, 0.78)
                                          + (1 - Math.min(1, d)) * 0.07
                                          + (r - 0.5) * 0.12));
        // Teinte QUANTIFIEE sur cinq valeurs : du pixel-art franc. Un degrade
        // continu recolle tous les cubes en une seule tache blanche.
        cube(out, cx + px, cy + py, cz + pz, grain, grain, grain,
             mixRgb(bas, haut, Math.round(f * 4) / 4), al0);
      }
}

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
//  LA TRAINEE
//
//  Le joueur AVANCE vers +Z. Un effet de sol qui reste colle a ses pieds se
//  lit comme un autocollant : rien ne dit qu'il se deplace. Chaque ornement
//  de sol depose donc derriere lui (z < 0) des empreintes espacees qui
//  s'effacent et s'enfoncent avec la distance. C'est la trainee qui donne le
//  mouvement.
// =============================================================================

const PAS_TRAINEE = 0.52;          // espacement des empreintes, en blocs
const VIE_EMPREINTE = 2.6;         // duree de vie d'une empreinte, en secondes

// --- Le joueur marche, puis s'arrete, puis repart ---------------------------
// La trainee n'est PAS un tapis roulant : c'est une memoire de pas. Tant que
// le joueur avance, il en depose de nouvelles sous ses pieds ; des qu'il
// s'arrete, plus rien n'est depose et les plus anciennes — donc les plus
// lointaines — s'eteignent une a une : la trainee se RETRACTE vers lui. Quand
// il repart, elle se redeploie. Il faut donc une distance parcourue, et non
// un decalage proportionnel au temps.
const CYCLE = 8.5;                 // marche 4,6 s, arret 3,1 s, deux rampes
const RAMPE = 0.4;
const D_CYCLE = 4.6 + RAMPE;       // blocs parcourus par cycle

/** Vitesse normalisee (0 = arrete, 1 = en marche) a l'instant t. */
function vitesseJoueur(t) {
  const m = (typeof window !== 'undefined') ? window.DLB_MARCHE : undefined;
  if (m === 0) return 0;
  if (m === 1) return 1;
  const u = ((t % CYCLE) + CYCLE) % CYCLE;
  if (u < 4.6) return 1;
  if (u < 4.6 + RAMPE) return 1 - (u - 4.6) / RAMPE;      // il freine
  if (u < CYCLE - RAMPE) return 0;                        // il est a l'arret
  return (u - (CYCLE - RAMPE)) / RAMPE;                   // il repart
}

/** Distance parcourue depuis l'origine des temps, en blocs. */
function avanceJoueur(t) {
  const m = (typeof window !== 'undefined') ? window.DLB_MARCHE : undefined;
  if (m === 0) return 0;
  if (m === 1) return t;
  const n = Math.floor(t / CYCLE);
  const u = t - n * CYCLE;
  let d;
  if (u < 4.6) d = u;
  else if (u < 4.6 + RAMPE) { const x = u - 4.6; d = 4.6 + x - x * x / (2 * RAMPE); }
  else if (u < CYCLE - RAMPE) d = 4.6 + RAMPE / 2;
  else { const x = u - (CYCLE - RAMPE); d = 4.6 + RAMPE / 2 + x * x / (2 * RAMPE); }
  return n * D_CYCLE + d;
}

/** Instant auquel le joueur se trouvait a la distance d (inverse de avanceJoueur). */
function instantPour(d) {
  const m = (typeof window !== 'undefined') ? window.DLB_MARCHE : undefined;
  if (m === 0) return -1e9;        // rien ne bouge : tout vieillit
  if (m === 1) return d;
  const n = Math.floor(d / D_CYCLE);
  const r = d - n * D_CYCLE;
  let u;
  if (r < 4.6) u = r;
  else if (r < 4.6 + RAMPE / 2) {
    // d = 4.6 + x - x^2/(2R)  ->  x = R - sqrt(R^2 - 2R(d-4.6))
    const y = r - 4.6;
    u = 4.6 + (RAMPE - Math.sqrt(Math.max(0, RAMPE * RAMPE - 2 * RAMPE * y)));
  } else {
    const y = r - (4.6 + RAMPE / 2);
    u = (CYCLE - RAMPE) + Math.sqrt(Math.max(0, 2 * RAMPE * y));
  }
  return n * CYCLE + u;
}

/** Longueur visible d'un sillon continu : le trajet des `vie` dernieres
 *  secondes, plafonne a `max`. A l'arret il fond jusqu'a zero, au depart il
 *  se redeploie. */
function longueurTrainee(t, max, vie) {
  const d = avanceJoueur(t) - avanceJoueur(Math.max(0, t - vie));
  return Math.min(max, d);
}

/**
 * Empreintes encore vivantes derriere le joueur : { z, k, age }.
 *   z   = recul (negatif), fige a l'endroit ou l'empreinte a ete deposee
 *   k   = facteur d'echelle, l'empreinte se resserre en vieillissant
 *   age = 0 (fraiche) a 1 (eteinte)
 */
function empreintes(t, nb, vie, pas) {
  const ecart = (pas === undefined) ? PAS_TRAINEE : pas;
  const duree = (vie === undefined) ? VIE_EMPREINTE : vie;
  const d = avanceJoueur(t);
  const sortie = [];
  for (let i = 0; i < nb; i++) {
    const D = (Math.floor(d / ecart) - i) * ecart;   // distances de depot
    if (D < 0) continue;
    const age = (t - instantPour(D)) / duree;
    if (age >= 1 || age < 0) continue;
    sortie.push({ z: -(d - D) - 0.26, k: 1 - age * 0.55, age });
  }
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
    jeu: 'pixels',
    jeuNote: 'Rendu en pixels par CosmeticShapes.haloContinu, drapeau F_HALO_CONTINUOUS.',
    accorde: "Arme à aura : trident enchanté en main, ou marque dlbWeaponAura (15 % en mêlée, 40 % à distance). Exclusif : ne sort que si aucun combo cosmétique n'est actif (CosmeticPixelAura l.232, flags == 0).",
    note: "Plaque annulaire de quatre rangées jointives, 0,236 à 0,330 de rayon. Trente-six cannelures gravées en font le tour, vingt dents courtes hérissent le bord extérieur (une sur trois est longue), une comète à traînée parcourt l'anneau et trois lucioles l'escortent. Épais de 2,2 cm : invisible de profil, net de dessus. C'est le détail qui manquait à la version précédente, pas l'épaisseur.",
    build(t) {
      const o = [];
      const y = 2.010 + Math.sin(t * 1.1) * 0.012;
      const rI = 0.236, rE = 0.330, w = 0.046;
      const ph = t * 0.20;
      const comete = (t * 0.17) % 1;

      // 1. la plaque, du bord interieur au bord exterieur
      nappe(o, TAU * rE, rE - rI, w,
        (u, v) => {
          const a = ph + TAU * u, r = rI + (rE - rI) * v;
          return [Math.cos(a) * r, y, Math.sin(a) * r];
        },
        (u, v) => {
          let d = Math.abs(u - comete); if (d > 0.5) d = 1 - d;
          const tete = Math.max(0, 1 - d * 12);
          let q = comete - u; if (q < 0) q += 1;
          const queue = Math.max(0, 1 - q * 6) * 0.70;
          const g = Math.max(tete, queue);
          const cannelure = 0.5 + 0.5 * Math.cos(TAU * u * 36);
          const bord = Math.abs(v - 0.5) * 2;
          const or = mixRgb(0xD99A14, 0xFFF3C8, 0.20 + cannelure * 0.40 + (1 - bord) * 0.34);
          return {
            rgb: mixRgb(or, 0xFFFFFF, g * 0.88),
            a: (188 + cannelure * 34 + g * 56) * (1 - bord * 0.26),
            hy: 0.011
          };
        }, true);

      // 2. denture exterieure : une dent sur trois est longue
      const nd = 20;
      for (let i = 0; i < nd; i++) {
        const a = ph + TAU * i / nd;
        const lg = (i % 3 === 0) ? 0.056 : 0.030;
        const ca = Math.cos(a), sa = Math.sin(a);
        trait(o, [ca * (rE - 0.012), y, sa * (rE - 0.012)],
                 [ca * (rE + lg), y, sa * (rE + lg)], 0.032,
          (u) => ({ rgb: mixRgb(0xFFD764, 0xFFFFFF, u), a: 228 - u * 130, hy: 0.009 }));
      }

      // 3. trois lucioles en escorte, chacune une bille pleine
      for (let i = 0; i < 3; i++) {
        const a = -ph * 1.7 + TAU * i / 3;
        const r = rE + 0.085 + Math.sin(t * 1.9 + i * 2.1) * 0.022;
        bille(o, Math.cos(a) * r, y + 0.034 + Math.sin(t * 2.4 + i) * 0.018, Math.sin(a) * r,
          0.016, 0xFFF3C8, 215, 0xFFC63D);
      }
      return o;
    }
  },

  // --- 2. #110 Avatar : soleil a rayons --------------------------------------
  avatar_110: {
    titre: "#110 Avatar Suprême du Deep Lucky",
    famille: "permanent",
    jeu: 'pixels',
    jeuNote: 'Rendu en pixels par CosmeticShapes.soleilAvatar, drapeau F_COMBO_110_AVATAR.',
    accorde: "Combo #110 — le plus haut du registre. Exclusif face à #097/#098 et à Roi (chaîne else if, CosmeticPixelAura l.242-254).",
    note: "Un soleil à deux couronnes : douze grands rayons triangulaires qui battent, douze petits dards intercalés qui battent en opposition, un disque annulaire gravé au centre et quatre protubérances qui tournent dans l'autre sens. Chaque rayon finit sur un seul cube.",
    build(t) {
      const o = [];
      const ph = t * 0.24, y = 2.034;
      const rI = 0.148, rE = 0.212;

      nappe(o, TAU * rE, rE - rI, 0.046,
        (u, v) => {
          const a = ph + TAU * u, r = rI + (rE - rI) * v;
          return [Math.cos(a) * r, y, Math.sin(a) * r];
        },
        (u, v) => {
          const grain = 0.5 + 0.5 * Math.cos(TAU * u * 24);
          return { rgb: mixRgb(0xE08A00, 0xFFF4C4, v * 0.7 + grain * 0.3),
                   a: 252 - v * 18, hy: 0.011 };
        }, true);

      for (let i = 0; i < 12; i++) {
        const a = ph + TAU * i / 12;
        const bat = 1 + Math.sin(t * 2.0 + i * 0.9) * 0.14;
        const lng = 0.178 * bat;
        const ca = Math.cos(a), sa = Math.sin(a);
        bande(o, 0.042,
          (u) => [ca * (rE - 0.016 + lng * u), y + u * 0.026, sa * (rE - 0.016 + lng * u)],
          (u) => 0.062 * (1 - u) * (1 - u * 0.30),
          () => [-sa, 0, ca],
          (u, v) => ({ rgb: mixRgb(0xFFC21E, 0xFFFFFF, u + (1 - Math.abs(v) * 2) * 0.25),
                       a: 250 - u * 135, hy: 0.011 }));
        // dard court intercale, en opposition de phase
        const b = a + Math.PI / 12;
        const cb = Math.cos(b), sb = Math.sin(b);
        const lc = 0.082 * (1 - Math.sin(t * 2.0 + i * 0.9) * 0.14);
        bande(o, 0.038,
          (u) => [cb * (rE - 0.012 + lc * u), y - u * 0.012, sb * (rE - 0.012 + lc * u)],
          (u) => 0.040 * (1 - u),
          () => [-sb, 0, cb],
          (u) => ({ rgb: mixRgb(0xFF9A00, 0xFFE9A0, u), a: 228 - u * 120, hy: 0.010 }));
      }

      // protuberances : quatre bouffees qui tournent a contresens
      for (let i = 0; i < 4; i++) {
        const a = -ph * 1.6 + TAU * i / 4;
        const r = rE + 0.075 + Math.sin(t * 2.6 + i * 1.6) * 0.030;
        bille(o, Math.cos(a) * r, y + 0.030, Math.sin(a) * r,
          0.018, 0xFFE9A0, 225, 0xFF8A00);
      }
      return o;
    }
  },

  // --- 3. #097 Armure Eternelle : chaine de maillons -------------------------
  eternelle_097: {
    titre: "#097 Armure Éternelle Transcendante",
    famille: "permanent",
    jeu: 'pixels',
    jeuNote: 'Rendu en pixels par CosmeticShapes.chaineEternelle, drapeau F_COMBO_097_ETERNAL.',
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
    jeu: 'pixels',
    jeuNote: 'Rendu en pixels par CosmeticShapes.epeesDieuLame, drapeau F_COMBO_098_SWORD_GOD.',
    accorde: "Combo #098. Ne partage plus rien avec #097 : ce sont deux récompenses différentes, elles doivent se distinguer à l'œil nu.",
    note: "Six ÉPÉES dressées en cercle, pointe en l'air, et chacune est une vraie épée : lame à gouttière centrale claire et tranchants sombres, pointe en losange, quillons recourbés terminés par une boule, fusée tressée en quatre segments, pommeau rond serti. Elles flottent avec un déphasage. Un anneau doré ne dira jamais « Dieu de la Lame ».",
    build(t) {
      const o = [];
      const ph = -t * 0.28, r = 0.268;
      const ACIER = 0xFFBE28, VIF = 0xFFFFFF, SOMBRE = 0xB07A10, CUIR = 0x8A4A08;
      for (let i = 0; i < 6; i++) {
        const a = ph + TAU * i / 6;
        const ca = Math.cos(a), sa = Math.sin(a);
        const tx = -sa, tz = ca;                   // travers de la lame
        const flot = Math.sin(t * 1.6 + i * 1.047) * 0.020;
        const yg = 1.972 + flot;
        const Lb = 0.268, pencher = 0.135;

        // --- lame : gouttiere claire au centre, tranchants sombres -------
        bande(o, 0.036,
          (u) => [ca * (r + pencher * u), yg + 0.034 + Lb * u, sa * (r + pencher * u)],
          (u) => 0.060 * (u > 0.82 ? (1 - u) / 0.18 : 1 - u * u * 0.30),
          () => [tx, 0, tz],
          (u, v) => {
            const d = Math.min(1, Math.abs(v) * 2);
            const gouttiere = Math.pow(1 - d, 2.4);
            return {
              rgb: mixRgb(mixRgb(SOMBRE, ACIER, 0.35 + (1 - d) * 0.45), VIF,
                          gouttiere * 0.70 + (d > 0.86 ? 0.45 : 0)),
              a: 252 - u * 26
            };
          });

        // --- quillons recourbes, boule au bout ---------------------------
        for (const c of [-1, 1]) {
          courbe(o, 0.032,
            (u) => [ca * r + tx * c * 0.082 * u,
                    yg + 0.030 * u * u,
                    sa * r + tz * c * 0.082 * u],
            (u) => ({ rgb: mixRgb(0xFFE9A0, 0xFFB400, u * 0.7), a: 246,
                      hx: 0.017 - u * 0.003, hy: 0.017 - u * 0.003, hz: 0.017 - u * 0.003 }));
          bille(o, ca * r + tx * c * 0.086, yg + 0.032, sa * r + tz * c * 0.086,
            0.015, 0xFFF3C8, 246, 0xFFB400);
        }

        // --- fusee tressee : quatre segments alternes --------------------
        for (let k = 0; k < 4; k++) {
          const y0 = yg - 0.020 - k * 0.019;
          cube(o, ca * r, y0, sa * r, 0.017, 0.010, 0.017,
            mixRgb(CUIR, k % 2 ? 0xD89A3C : 0x5E2F04, 0.5), 240);
        }

        // --- pommeau serti ------------------------------------------------
        bille(o, ca * r, yg - 0.108, sa * r, 0.020,
          mixRgb(0xFFD98A, 0xFFFFFF, (Math.sin(t * 2 + i) + 1) / 2), 248, 0xA85F0C);
      }
      return o;
    }
  },

  // --- 5. #101 Archange : aile emplumee --------------------------------------
  archange_101: {
    titre: "#101 Archange du Jugement Solaire",
    famille: "permanent",
    jeu: 'pixels',
    jeuNote: 'Rendu en pixels par CosmeticShapes.aileSeraphin, drapeau F_COMBO_101_ARCHANGEL.',
    accorde: "Combo #101. Ailes permanentes. Exclusif d'Endurance Ailée (CosmeticPixelAura l.269-275 : if (archangel) … else …).",
    note: "Une VRAIE aile : charpente en trois os (humerus, avant-bras, main), huit rémiges primaires portées par la main, six secondaires sur l'avant-bras, deux rangées de couvertures qui recouvrent leurs bases, et l'alula au poignet. Chaque plume a son rachis clair et son vexille qui s'élargit au tiers avant de s'effiler. Le recouvrement des rangées est ce qui la rend pleine.",
    build(t) { return aileSeraphin(t); }
  },

  // --- 6. Endurance Ailee : voilure rigide -----------------------------------
  winged_endurance: {
    titre: "Endurance Ailée (enchantement d'armure)",
    famille: "permanent",
    jeu: 'pixels',
    jeuNote: 'Rendu en pixels par CosmeticShapes.voilureGivre, drapeau F_ENCH_WINGED_ENDURANCE.',
    accorde: "Enchantement d'armure d'endurance ailée. Ne sort QUE si le joueur ne touche pas le sol ou plane en élytre.",
    note: "Ce n'est PAS une aile à plumes en bleu : c'est une voilure. Longeron droit relevé vers le haut, cinq nervures rigides, membrane en panneaux facettés, bord de fuite tendu en ligne droite. Silhouette triangulaire et dure, là où l'Archange est arrondi et duveteux — aucun cube n'est calculé par le même code.",
    build(t) { return voilureGivre(t); }
  },

  // --- 7. #106 Seigneur de Guerre : sceau au sol -----------------------------
  seigneur_guerre_106: {
    titre: "#106 Seigneur de Guerre de l'Apocalypse",
    famille: "permanent",
    jeu: 'pixels',
    jeuNote: 'Rendu en pixels par CosmeticShapes.sceauGuerre, drapeau F_COMBO_106_WARLORD.',
    accorde: "Combo #106. Ornement de SOL — ne monte jamais vers le torse.",
    note: "Un sceau gravé au sol, entièrement PLAT : double cercle contra-rotatif, croix de guerre, quatre chevrons runiques. Et surtout : le joueur LAISSE SA MARQUE. Quatre sceaux fantômes restent imprimés derrière lui, de plus en plus pâles et resserrés, suivis d'une file de chevrons de braise pointés vers l'avant. On voit d'où il vient, et dans quel sens il va.",
    build(t) {
      const o = [];
      const y = 0.028, ph = -t * 0.16;
      const rExt = 0.505, rInt = 0.292;

      // ---- 1. le sceau sous les pieds ------------------------------------
      nappe(o, TAU * rExt, 0, 0.078,
        (u) => { const a = ph + TAU * u; return [Math.cos(a) * rExt, y, Math.sin(a) * rExt]; },
        (u) => ({ rgb: mixRgb(0xFF6A1E, 0xFFC46B, (Math.sin(TAU * u * 3 + t) + 1) / 2),
                  a: 214, hy: 0.012 }), true);
      nappe(o, TAU * rInt, 0, 0.070,
        (u) => { const a = -ph * 1.7 + TAU * u; return [Math.cos(a) * rInt, y, Math.sin(a) * rInt]; },
        () => ({ rgb: 0xFFA23C, a: 196, hy: 0.011 }), true);
      for (let i = 0; i < 4; i++) {
        const a = ph + TAU * i / 4 + 0.39;
        const ca = Math.cos(a), sa = Math.sin(a);
        trait(o, [ca * rInt, y, sa * rInt], [ca * rExt, y, sa * rExt], 0.062,
          (u) => ({ rgb: mixRgb(0xFFA23C, 0xFF6A1E, u), a: 202, hy: 0.011 }));
        const b = a + TAU / 8;
        const cb = Math.cos(b), sb = Math.sin(b), rm = (rExt + rInt) / 2;
        const puls = 0.5 + 0.5 * Math.sin(t * 1.9 + i * 1.57);
        for (const c of [1, -1]) {
          trait(o, [cb * (rm - 0.055) - sb * 0.060 * c, y, sb * (rm - 0.055) + cb * 0.060 * c],
                   [cb * (rm + 0.062), y, sb * (rm + 0.062)], 0.048,
            () => ({ rgb: mixRgb(0xFF8A33, 0xFFE0B0, puls), a: 150 + puls * 70, hy: 0.010 }));
        }
      }

      // ---- 2. LA TRAINEE : quatre sceaux imprimes derriere le joueur ------
      const emp = empreintes(t, 3, 2.9, 0.58);
      for (let k = 0; k < emp.length; k++) {
        const e = emp[k];
        const R = rExt * e.k * 0.86;
        const al = Math.round(176 * (1 - e.age) * (1 - e.age));
        if (al < 12) continue;
        // le cercle de braise
        nappe(o, TAU * R, 0, 0.074,
          (u) => { const a = ph * 0.6 + TAU * u; return [Math.cos(a) * R, y - 0.004, e.z + Math.sin(a) * R]; },
          (u) => ({ rgb: mixRgb(0xC43A08, 0xFF9A3C, (Math.sin(TAU * u * 3) + 1) / 2),
                    a: al, hy: 0.010 }), true);
        // la croix, reduite a deux branches : une empreinte s'efface
        for (let i = 0; i < 2; i++) {
          const a = TAU * i / 2 + 0.39;
          const ca = Math.cos(a), sa = Math.sin(a);
          trait(o, [ca * R * 0.42, y - 0.004, e.z + sa * R * 0.42],
                   [ca * R, y - 0.004, e.z + sa * R], 0.056,
            () => ({ rgb: 0xE0691A, a: Math.round(al * 0.8), hy: 0.010 }));
        }
      }

      // ---- 3. une file de CHEVRONS de braise, pointe vers l'avant --------
      // (surtout pas deux rails continus : l'Aura de Terreur a deja un
      //  sillon droit, et deux ornements de sol ne doivent pas se ressembler)
      // Les chevrons suivent la meme memoire de pas : ils restent la ou ils
      // ont ete poses et s'eteignent, au lieu de defiler quand on pietine.
      const chev = empreintes(t, 5, 2.4, 0.33);
      for (let k = 0; k < chev.length; k++) {
        const zc = chev[k].z - 0.16;
        const al = Math.round(190 * Math.pow(1 - chev[k].age, 1.6));
        if (al < 14) continue;
        const demi = 0.185 * chev[k].k;
        for (const c of [-1, 1]) {
          trait(o, [0, y - 0.006, zc + 0.075], [c * demi, y - 0.006, zc - 0.060], 0.064,
            (u) => ({ rgb: mixRgb(0xFFB040, 0x7A2A04, u * 0.8),
                      a: al - u * 40, hy: 0.009 }));
        }
      }
      return o;
    }
  },

  // --- 8. #062 Peste : flaques et bulles -------------------------------------
  peste_062: {
    titre: "#062 Peste Ambulante",
    famille: "permanent",
    jeu: 'pixels',
    jeuNote: 'Rendu en pixels par CosmeticShapes.pesteAmbulante, drapeau F_COMBO_062_PLAGUE.',
    accorde: "Combo #062. Ornement de SOL.",
    note: "Aucun cercle : quatre flaques PLEINES au contour irrégulier qui suintent sous les pieds, neuf grappes de bulles qui crèvent, et surtout une COULÉE d'infection — sept éclaboussures de taille décroissante marquent les pas précédents, reliées par un filet de sanie. Le joueur contamine le sol qu'il quitte.",
    build(t) {
      const o = [];
      const flaques = [[0.35, 0.30, 0.108], [2.45, 0.37, 0.090],
                       [4.05, 0.26, 0.098], [5.45, 0.40, 0.076]];
      for (let k = 0; k < flaques.length; k++) {
        const [ang, d, ray] = flaques[k];
        const osc = Math.sin(t * 0.7 + k * 1.3) * 0.012;
        const cx = Math.cos(ang) * (d + osc), cz = Math.sin(ang) * (d + osc);
        disqueH(o, cx, 0.018, cz, ray, 0.062, (fr, th) => ({
          k: 0.70 + 0.30 * (0.5 + 0.5 * Math.sin(th * 3 + k * 2.1 + t * 0.35)),
          rgb: mixRgb(0x2F5E08, 0x7FBF22, fr * 0.8 + 0.2),
          a: 205 - fr * 70, hy: 0.009
        }));
      }
      for (let i = 0; i < 9; i++) {
        const p = (t * 0.33 + i * 0.111) % 1;
        const ang = i * 2.618 + Math.sin(i * 4.7) * 1.4;
        const r = 0.18 + (i % 4) * 0.075;
        const rb = 0.0165 * (0.75 + p * 0.55);
        bille(o, Math.cos(ang) * r, 0.030 + p * 0.305, Math.sin(ang) * r, rb,
          mixRgb(0x9BDC33, 0x3E7A0C, p), 215 * (1 - p * p), 0xD8F2A0);
      }

      // ---- LA COULEE : sept eclaboussures derriere le joueur -------------
      const emp = empreintes(t, 6, 3.2, 0.38);
      for (let k = 0; k < emp.length; k++) {
        const e = emp[k];
        const cote = (k % 2 ? 1 : -1) * (0.090 + (k % 3) * 0.052);
        const R = (0.165 - k * 0.013) * e.k;
        if (R < 0.030) continue;
        const al = 196 * (1 - e.age) * (1 - e.age * 0.5);
        disqueH(o, cote, 0.014, e.z, R, 0.070, (fr, th) => ({
          k: 0.62 + 0.38 * (0.5 + 0.5 * Math.sin(th * 4 + k * 1.7)),
          rgb: mixRgb(0x2A5409, 0x79BE1E, fr * 0.55 + 0.30),
          a: al - fr * 44, hy: 0.009
        }));
        // une bulle survivante sur deux eclaboussures
        if (k % 2 === 0 && al > 70) {
          bille(o, cote, 0.034 + Math.abs(Math.sin(t * 1.6 + k)) * 0.046, e.z,
            0.013, 0x7FBF22, Math.round(al * 0.75), 0xD8F2A0);
        }
      }
      // filet de sanie qui relie les eclaboussures
      const Lf = longueurTrainee(t, 2.05, 3.2);
      if (Lf > 0.10) {
        bande(o, 0.074,
          (u) => [Math.sin(u * 5.4) * 0.080, 0.011, -0.26 - u * Lf],
          (u) => 0.135 * (1 - u * 0.72),
          () => [1, 0, 0],
          (u, v) => ({ rgb: mixRgb(0x53931A, 0x1C3806, u * 0.85 + Math.abs(v) * 0.3),
                       a: Math.round(182 * (1 - u) * (1 - u * 0.6)), hy: 0.008 }));
      }
      return o;
    }
  },

  // --- 9. Roi : couronne a fleurons ------------------------------------------
  king: {
    titre: "Roi (enchantement d'armure)",
    famille: "permanent",
    jeu: 'pixels',
    jeuNote: 'Rendu en pixels par CosmeticShapes.couronneRoi, drapeau F_ENCH_KING.',
    accorde: "Enchantement d'armure « king ». Exclusif face à #110 et #097/#098.",
    note: "Une couronne royale complète, et POSÉE : elle ne tourne pas. Bandeau à double jonc serti de huit pierres alternées — rubis, émeraude — séparées par des perles ; quatre croix pattées et quatre fleurs de lys en alternance ; quatre arches qui montent se rejoindre sur le monde, surmonté de sa croix. Le rubis frontal, plus gros, pulse doucement. Rien ne dépasse le volume du crâne par le bas.",
    build(t) {
      const o = [];
      const r = 0.170, yb = 1.822, hb = 0.066;
      const APEX = yb + hb + 0.200;
      const lueur = (Math.sin(t * 1.5) + 1) / 2;
      const OR = 0xB07A10, ORCLAIR = 0xFFE07A, ORVIF = 0xFFF6D0;

      // --- bandeau : cylindre plein, joncs clairs en haut et en bas --------
      nappe(o, TAU * r, hb, 0.042,
        (u, v) => { const a = TAU * u; return [Math.cos(a) * r, yb + hb * v, Math.sin(a) * r]; },
        (u, v) => {
          const jonc = (v < 0.17 || v > 0.83) ? 1 : 0;
          const grain = 0.5 + 0.5 * Math.cos(TAU * u * 28);
          return { rgb: mixRgb(mixRgb(OR, ORCLAIR, 0.20 + grain * 0.35), ORVIF, jonc * 0.75),
                   a: 246 };
        }, true);

      // --- huit pierres serties, alternees, separees par des perles --------
      for (let i = 0; i < 8; i++) {
        const a = TAU * i / 8 + Math.PI / 8;
        const ca = Math.cos(a), sa = Math.sin(a);
        const rubis = i % 2 === 0;
        const teinte = rubis ? mixRgb(0xC01030, 0xFF7090, lueur * 0.55)
                             : mixRgb(0x0E8A46, 0x74E8A8, lueur * 0.55);
        cube(o, ca * (r + 0.012), yb + hb * 0.5, sa * (r + 0.012),
          0.019, 0.021, 0.019, teinte, 248);
        cube(o, ca * (r + 0.020), yb + hb * 0.5, sa * (r + 0.020),
          0.011, 0.013, 0.011, mixRgb(teinte, 0xFFFFFF, 0.55), 240);
        const b = a + TAU / 16;
        cube(o, Math.cos(b) * (r + 0.010), yb + hb * 0.5, Math.sin(b) * (r + 0.010),
          0.013, 0.013, 0.013, mixRgb(0xFFF6D0, 0xFFFFFF, lueur * 0.4), 244);
      }

      // --- quatre croix pattees et quatre fleurs de lys --------------------
      for (let i = 0; i < 8; i++) {
        const a = TAU * i / 8 - Math.PI / 2;      // une croix pile devant
        const ca = Math.cos(a), sa = Math.sin(a);
        const tx = -sa, tz = ca;                  // direction tangentielle
        const y0 = yb + hb;
        if (i % 2 === 0) {
          // croix pattee : montant + traverse, les deux evases
          bande(o, 0.034,
            (u) => [ca * r, y0 + 0.098 * u, sa * r],
            (u) => 0.046 * (0.55 + 0.45 * Math.abs(u * 2 - 1)),
            () => [tx, 0, tz],
            (u) => ({ rgb: mixRgb(ORCLAIR, ORVIF, u), a: 250 }));
          bande(o, 0.034,
            (u) => [ca * r + tx * (u - 0.5) * 0.098, y0 + 0.058, sa * r + tz * (u - 0.5) * 0.098],
            (u) => 0.040 * (0.55 + 0.45 * Math.abs(u * 2 - 1)),
            () => [0, 1, 0],
            () => ({ rgb: ORCLAIR, a: 248 }));
          // pierre au coeur de la croix ; celle de devant est la plus grosse
          const gros = (i === 0) ? 1.55 : 1.0;
          cube(o, ca * (r + 0.008), y0 + 0.058, sa * (r + 0.008),
            0.014 * gros, 0.014 * gros, 0.014 * gros,
            mixRgb(0xC01030, 0xFF8FA8, 0.25 + lueur * 0.6), 252);
        } else {
          // fleur de lys : petale central droit, deux petales recourbes
          trait(o, [ca * r, y0, sa * r], [ca * r, y0 + 0.086, sa * r], 0.034,
            (u) => ({ rgb: mixRgb(ORCLAIR, ORVIF, u), a: 248,
                      hx: 0.017 * (1 - u * 0.55), hy: 0.017, hz: 0.017 * (1 - u * 0.55) }));
          for (const c of [-1, 1]) {
            courbe(o, 0.030,
              (u) => [ca * r + tx * c * (0.012 + 0.046 * Math.sin(u * 1.9)),
                      y0 + 0.016 + 0.062 * u - 0.020 * u * u,
                      sa * r + tz * c * (0.012 + 0.046 * Math.sin(u * 1.9))],
              (u) => ({ rgb: mixRgb(ORCLAIR, ORVIF, u), a: 244 - u * 40,
                        hx: 0.015, hy: 0.015, hz: 0.015 }));
          }
        }
      }

      // --- quatre arches qui se rejoignent sur le monde --------------------
      for (let i = 0; i < 4; i++) {
        const a = TAU * i / 4 - Math.PI / 2;
        const ca = Math.cos(a), sa = Math.sin(a);
        const depart = [ca * r, yb + hb + 0.100, sa * r];
        courbe(o, 0.038,
          (u) => {
            const rr = r * (1 - u) + 0.012 * Math.sin(Math.PI * u);
            return [ca * rr, depart[1] + (APEX - depart[1]) * Math.pow(u, 0.62), sa * rr];
          },
          (u) => ({ rgb: mixRgb(OR, ORVIF, 0.35 + u * 0.55), a: 250,
                    hx: 0.019 - u * 0.004, hy: 0.019 - u * 0.004, hz: 0.019 - u * 0.004 }));
      }

      // --- le monde (orbe) et sa croix -------------------------------------
      bille(o, 0, APEX + 0.014, 0, 0.024, mixRgb(0x2A6CC8, 0x9CD0FF, lueur), 250, 0xFFE07A);
      nappe(o, TAU * 0.026, 0, 0.022,
        (u) => { const a = TAU * u; return [Math.cos(a) * 0.026, APEX + 0.014, Math.sin(a) * 0.026]; },
        () => ({ rgb: ORVIF, a: 250 }), true);
      trait(o, [0, APEX + 0.034, 0], [0, APEX + 0.088, 0], 0.024,
        (u) => ({ rgb: mixRgb(ORCLAIR, 0xFFFFFF, u), a: 252 }));
      trait(o, [-0.024, APEX + 0.068, 0], [0.024, APEX + 0.068, 0], 0.022,
        () => ({ rgb: ORVIF, a: 250 }));
      return o;
    }
  },

  // --- 10. Esprit Mystique : lemniscate --------------------------------------
  mystic_mind: {
    titre: "Esprit Mystique (enchantement d'armure)",
    famille: "permanent",
    jeu: 'pixels',
    jeuNote: 'Rendu en pixels par CosmeticShapes.espritMystique, drapeau F_ENCH_MYSTIC_MIND.',
    accorde: "Enchantement d'armure « mystic_mind ». Se cumule avec les autres.",
    note: "Un signe d'infini — lemniscate de Bernoulli — qui tourne au-dessus du crâne. Ce n'est plus un fil : le ruban est épais de trois cubes, doublé d'un fil d'ombre, trois nœuds de pensée le parcourent en l'éclairant, quatre runes carrées flottent à l'extérieur et deux satellites tournent en sens inverse. Échantillonné à PAS CONSTANT EN LONGUEUR D'ARC, sans quoi la courbe se resserre au croisement.",
    build(t) {
      const o = [];
      const A = 0.305, y = 2.060, spin = t * 0.42, w = 0.034;
      const courbeLem = (s, ech, dy) => {
        const th = TAU * s, d = 1 + Math.sin(th) * Math.sin(th);
        const px = A * ech * Math.cos(th) / d, pz = A * ech * Math.sin(th) * Math.cos(th) / d;
        return [px * Math.cos(spin) - pz * Math.sin(spin),
                y + dy + Math.sin(th * 2) * 0.026,
                px * Math.sin(spin) + pz * Math.cos(spin)];
      };
      const eclatEn = (u) => {
        let e = 0;
        for (let k = 0; k < 3; k++) {
          let d = Math.abs(u - ((t * 0.21 + k / 3) % 1)); if (d > 0.5) d = 1 - d;
          e = Math.max(e, Math.max(0, 1 - d * 13));
        }
        return e;
      };

      // --- ruban principal : trois rangees de large --------------------
      for (const [ech, dy, ep, al] of [[1.0, 0, 0.020, 215], [1.0, 0.032, 0.015, 180]]) {
        const pts = boucleEgale((s) => courbeLem(s, ech, dy), w * 0.9, 720);
        for (let i = 0; i < pts.length; i++) {
          const u = i / pts.length, e = eclatEn(u);
          cube(o, pts[i][0], pts[i][1], pts[i][2], ep, ep, ep,
            mixRgb(mixRgb(0x7B3BE8, 0xC08BFF, (Math.sin(u * TAU * 2) + 1) / 2), 0xF2E6FF, e),
            al + e * 45);
        }
      }

      // --- quatre runes carrees qui flottent autour --------------------
      for (let k = 0; k < 3; k++) {
        const a = spin * 1.4 + TAU * k / 3;
        const rr = 0.335, yy = y + Math.sin(t * 1.5 + k * 1.6) * 0.040;
        const cx = Math.cos(a) * rr, cz = Math.sin(a) * rr;
        const puls = 0.5 + 0.5 * Math.sin(t * 2.2 + k);
        const cote = 0.040;
        nappe(o, cote * 8, 0, 0.030,   // perimetre REEL : 4 cotes de 2*cote
          (u) => {
            const q = u * 4, f = q % 1, e = Math.floor(q) % 4;
            const sx = [-1, 1, 1, -1][e], sz = [-1, -1, 1, 1][e];
            const nx = [1, 1, -1, -1][e], nz = [-1, 1, 1, -1][e];
            return [cx + (sx + (nx - sx) * f) * cote, yy, cz + (sz + (nz - sz) * f) * cote];
          },
          () => ({ rgb: mixRgb(0x9B5BFF, 0xF2E6FF, puls), a: 160 + puls * 70 }), true);
      }

      // --- deux satellites a contresens --------------------------------
      for (let k = 0; k < 2; k++) {
        const a = -spin * 2.1 + Math.PI * k;
        bille(o, Math.cos(a) * 0.215, y + 0.090 + Math.sin(t * 1.8 + k * 2) * 0.030,
          Math.sin(a) * 0.215, 0.017, 0xE6D2FF, 230, 0x7B3BE8);
      }
      return o;
    }
  },

  // --- 11. Egide Radieuse : coupole ------------------------------------------
  radiant_aegis: {
    titre: "Égide Radieuse (enchantement d'armure)",
    famille: "permanent",
    jeu: 'pixels',
    jeuNote: 'Rendu en pixels par CosmeticShapes.egideRadieuse, drapeau F_ENCH_RADIANT_AEGIS.',
    accorde: "Enchantement d'armure « heart_resonance ». Se cumule avec les autres.",
    note: "La coupole précédente était un casque de chantier : un dôme lisse posé sur le crâne, rien d'autre. Une égide est un BOUCLIER. Six pavois en écu — bord relevé, umbo central serti, quatre rivets — forment un mur qui tourne autour de la tête, et la lumière s'échappe entre eux en six rais. On lit « protection » en silhouette, et plus « chantier ».",
    build(t) {
      const o = [];
      const ph = t * 0.26, R = 0.302, yc = 2.040;
      const N = 6, Lx = 0.196, Hy = 0.232;
      const ACIER = 0x6F9FC8, CLAIR = 0xE8F6FF, CHAUD = 0xFFF2C0;
      const profil = (u) => (u < 0.40 ? 1 : 1 - Math.pow((u - 0.40) / 0.60, 1.7));

      for (let i = 0; i < N; i++) {
        const a = ph + TAU * i / N;
        const ca = Math.cos(a), sa = Math.sin(a);
        const tx = -sa, tz = ca;
        const bat = Math.sin(t * 1.7 + i * 1.05) * 0.012;

        // corps du pavois : u = du haut vers la pointe, v = en travers
        bande(o, 0.046,
          (u) => [ca * (R + bat), yc + Hy * (0.5 - u), sa * (R + bat)],
          (u) => Lx * profil(u),
          () => [tx, 0, tz],
          (u, v) => {
            const bordLat = Math.abs(v) * 2;                       // 0 au centre, 1 au bord
            const bordHaut = (u < 0.10 || u > 0.93) ? 1 : 0;
            const rim = Math.max(bordHaut, bordLat > 0.80 ? 1 : 0);
            return {
              rgb: mixRgb(mixRgb(0x2E5A80, ACIER, 0.35 + (1 - bordLat) * 0.5), CLAIR, rim * 0.85),
              a: rim ? 250 : 168 + (1 - bordLat) * 46
            };
          });

        // umbo serti au centre
        bille(o, ca * (R + 0.026 + bat), yc + Hy * 0.10, sa * (R + 0.026 + bat),
          0.020, mixRgb(CHAUD, 0xFFFFFF, (Math.sin(t * 2.1 + i) + 1) / 2), 252, 0xFFC63D);

        // quatre rivets
        for (const [du, dv] of [[0.14, -0.33], [0.14, 0.33], [0.52, -0.26], [0.52, 0.26]]) {
          const lv = Lx * profil(du) * dv;
          cube(o, ca * (R + 0.016 + bat) + tx * lv, yc + Hy * (0.5 - du), sa * (R + 0.016 + bat) + tz * lv,
            0.012, 0.012, 0.012, CLAIR, 246);
        }

        // rai de lumiere qui s'echappe entre deux pavois
        const b = a + Math.PI / N;
        const cb = Math.cos(b), sb = Math.sin(b);
        const puls = 0.55 + 0.45 * Math.sin(t * 2.4 + i * 1.9);
        bande(o, 0.050,
          (u) => [cb * (R - 0.010 + 0.155 * u * puls), yc + 0.012, sb * (R - 0.010 + 0.155 * u * puls)],
          (u) => 0.050 * (1 - u),
          () => [-sb, 0, cb],
          (u) => ({ rgb: mixRgb(CHAUD, 0xFFFFFF, u), a: (235 - u * 165) * puls, hy: 0.012 }));
      }
      return o;
    }
  },

  // --- 12. Aura de Terreur : ronces crochues ---------------------------------
  dread_aura: {
    titre: "Aura de Terreur (enchantement d'armure)",
    famille: "permanent",
    jeu: 'pixels',
    jeuNote: 'Rendu en pixels par CosmeticShapes.auraTerreur, drapeau F_ENCH_DREAD_AURA.',
    accorde: "Enchantement d'armure « dread_aura ». Ornement de SOL.",
    note: "Huit ronces qui sortent de terre, se courbent vers l'extérieur et se CROCHENT en pointe, plus quatre fissures qui rampent au sol. Derrière le joueur, la terre reste blessée : un sillon de corruption et cinq paires de ronces avortées de plus en plus courtes marquent son passage. Sommet à 0,40 — sous le genou. C'est du relief, à l'opposé du sceau plat du Seigneur de Guerre.",
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

      // ---- LA TRAINEE : la terre reste blessee derriere le passage -------
      // Cinq paires de ronces avortees, de plus en plus courtes, plantees de
      // part et d'autre du sillon de corruption.
      const emp = empreintes(t, 4, 3.0, 0.48);
      for (let k = 0; k < emp.length; k++) {
        const e = emp[k];
        const al = 190 * (1 - e.age) * (1 - e.age * 0.6);
        if (al < 16) continue;
        for (const c of [-1, 1]) {
          const xr = c * (0.115 + k * 0.022);
          const h = (0.175 - k * 0.026) * e.k;
          if (h < 0.030) continue;
          courbe(o, 0.042,
            (u) => [xr + c * 0.055 * u * u, 0.010 + h * Math.pow(u, 0.80),
                    e.z - 0.045 * u],
            (u) => ({ rgb: mixRgb(0x230540, 0x8A36D8, Math.pow(u, 0.7)),
                      a: al - u * 50,
                      hx: 0.023 * (1 - u * 0.40), hy: 0.023 * (1 - u * 0.40),
                      hz: 0.023 * (1 - u * 0.40) }));
        }
      }
      // le sillon lui-meme : une bande de terre corrompue
      const Ls = longueurTrainee(t, 2.05, 3.0);
      if (Ls > 0.10) {
        bande(o, 0.072,
          (u) => [Math.sin(u * 3.1) * 0.040, 0.009, -0.30 - u * Ls],
          (u) => 0.175 * (1 - u * 0.70),
          () => [1, 0, 0],
          (u, v) => ({ rgb: mixRgb(0x4A1086, 0x12021F, u * 0.8 + Math.abs(v) * 0.4),
                       a: Math.round(172 * (1 - u) * (1 - u * 0.5)), hy: 0.008 }));
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
    jeu: 'particules',
    jeuNote: 'PAS ENCORE en pixels : en jeu, EnchantmentParticleFX.crownHalo émet encore des particules vanilla. Cette fiche montre la forme visée.',
    accorde: "#093 Zéphyr (l.348), #091 Renaissance Solaire (l.1493), #083 Empereur de Légion (l.3057).",
    note: "Un diadème qui naît au ras du crâne, s'ouvre et s'efface en montant. Pas un simple jonc : DEUX cerceaux concentriques reliés par douze entretoises, cinq grandes pointes serties d'une gemme, cinq pointes courtes intercalées, et une pluie d'étincelles qui retombe pendant que l'anneau s'élève. Le relief vertical est ce qui le distingue de l'onde de choc, parfaitement plate.",
    build(t) {
      const o = [];
      const p = (t * 0.5) % 1;
      const r = 0.172 + p * 0.330, y = 1.925 + p * 0.455;
      const al = Math.round(252 * Math.pow(1 - p, 0.75));
      const ph = p * 2.1;
      const rI = r - 0.042;

      // --- deux cerceaux concentriques -----------------------------------
      for (const [rr, w, k] of [[r, 0.046, 1.0], [rI, 0.038, 0.78]]) {
        nappe(o, TAU * rr, 0, w,
          (u) => { const a = ph + TAU * u; return [Math.cos(a) * rr, y, Math.sin(a) * rr]; },
          (u) => ({ rgb: mixRgb(0xFFF3C8, 0xFFC63D, p * 0.7 + 0.3 * (0.5 + 0.5 * Math.cos(TAU * u * 18))),
                    a: Math.round(al * k), hy: 0.011 }), true);
      }

      // --- douze entretoises qui relient les deux cerceaux ---------------
      for (let i = 0; i < 12; i++) {
        const a = ph + TAU * i / 12;
        const ca = Math.cos(a), sa = Math.sin(a);
        trait(o, [ca * rI, y, sa * rI], [ca * r, y, sa * r], 0.036,
          () => ({ rgb: 0xFFE07A, a: Math.round(al * 0.85), hy: 0.010 }));
      }

      // --- cinq grandes pointes serties + cinq courtes intercalees -------
      for (let i = 0; i < 5; i++) {
        const a = ph + TAU * i / 5;
        const ca = Math.cos(a), sa = Math.sin(a);
        const h = 0.052 + p * 0.088;
        bande(o, 0.040,
          (u) => [ca * r, y + h * u, sa * r],
          (u) => 0.058 * (1 - u * 0.82),
          () => [-sa, 0, ca],
          (u) => ({ rgb: mixRgb(0xFFC63D, 0xFFFFFF, u), a: al * (1 - u * 0.30) }));
        bille(o, ca * r, y + h + 0.016, sa * r, 0.016,
          mixRgb(0xFFFFFF, 0xFF9500, p), Math.round(al * 0.92), 0xFFF3C8);

        const b = a + Math.PI / 5;
        const cb = Math.cos(b), sb = Math.sin(b);
        bande(o, 0.036,
          (u) => [cb * r, y + h * 0.46 * u, sb * r],
          (u) => 0.040 * (1 - u * 0.80),
          () => [-sb, 0, cb],
          (u) => ({ rgb: mixRgb(0xFFA23C, 0xFFF3C8, u), a: Math.round(al * 0.80) }));
      }

      // --- etincelles qui retombent pendant que l'anneau monte -----------
      for (let i = 0; i < 6; i++) {
        const q = (p + i * 0.167) % 1;
        const a = ph * 0.6 + TAU * i / 6;
        const rr = r * (0.80 + q * 0.30);
        const yy = y - q * 0.300;
        const ae = Math.round(al * (1 - q) * 0.8);
        if (ae < 14) continue;
        const ex = Math.cos(a) * rr, ez = Math.sin(a) * rr;
        // Une etincelle qui retombe passerait devant les yeux : sous le crane,
        // on ne la garde que si elle est deja sortie du cylindre du regard.
        if (yy < CRANE && Math.sqrt(ex * ex + ez * ez) < 0.60) continue;
        bille(o, ex, yy, ez, 0.013, 0xFFF3C8, ae, 0xFFC63D);
      }
      return o;
    }
  },

  wing_ribbons: {
    titre: "wingRibbons — rubans d'ailes",
    famille: "ponctuel",
    jeu: 'particules',
    jeuNote: 'PAS ENCORE en pixels : en jeu, EnchantmentParticleFX.wingRibbons émet encore des particules vanilla. Cette fiche montre la forme visée.',
    accorde: "#081 Ange à la Plume Céleste (l.646).",
    note: "Trois rubans PLATS par épaule, de longueur et de phase différentes, qui se déroulent derrière le dos en vrillant : la direction transversale tourne le long du ruban, si bien qu'il se présente tantôt de face, tantôt de chant. Sept plumes-rubans se détachent du brin principal, et des étincelles suivent la traîne. Strictement derrière le dos (z ≤ −0,18).",
    build(t) {
      const o = [];
      for (const s of [-1, 1]) {
        // Trois banderoles qui s'ECARTENT : meme origine a l'epaule, mais des
        // angles, des longueurs et des ondulations franchement differents.
        // Superposees, elles ne faisaient qu'une tache jaune.
        const brins = [[1.00, 0.0, 0.095, 242, 0.62, 3.4],
                       [0.86, 1.3, 0.070, 208, 1.05, 4.6],
                       [0.70, 2.6, 0.052, 178, 0.22, 5.8]];
        for (let b = 0; b < brins.length; b++) {
          const [lg, dph, larg, al, ouvre, onde] = brins[b];
          const chemin = (u) => [
            s * (0.165 + u * (0.235 + 0.300 * ouvre) * lg
                 + Math.sin(u * onde - t * 2.8 + dph) * 0.115 * u),
            1.470 - u * 0.880 * lg + Math.cos(u * 3.7 - t * 2.3 + dph) * 0.062,
            -0.205 - u * 0.430 * lg - b * 0.055];
          bande(o, 0.050,
            chemin,
            (u) => larg * (1 - u * 0.78),
            (u) => {
              const tw = u * 3.5 - t * 2.1 + dph + (s > 0 ? 0 : 1.1);
              return norm([Math.cos(tw) * s, Math.sin(tw) * 0.75, Math.sin(tw) * 0.18]);
            },
            (u, v) => ({ rgb: mixRgb(0xFFFBEA, 0xFFC94E, u * 0.75 + Math.abs(v) * 0.5),
                         a: al * (1 - u * 0.68) }));

          // plumes-rubans qui se detachent du brin principal
          if (b === 0) {
            for (let i = 0; i < 6; i++) {
              const u = 0.18 + 0.70 * (i / 5);
              const p = chemin(u);
              const d = norm([s * (0.22 + 0.34 * (i / 5)), -0.90, -0.26]);
              const lat = norm(perp(d, [0, 0, 1]));
              plume(o, p, d, 0.215 - i * 0.016, 0.092, lat,
                0xFFC94E, 0xFFFBEA, 216 - i * 10, i % 2 ? 0.76 : 1.0, 0, 0.060);
            }
          }
        }
        // etincelles dans la traine
        for (let i = 0; i < 4; i++) {
          const q = ((t * 0.5 + i * 0.25) % 1);
          bille(o, s * (0.175 + q * 0.330), 1.430 - q * 0.700, -0.235 - q * 0.420,
            0.013, 0xFFF3C8, Math.round(210 * (1 - q)), 0xFFC94E);
        }
      }
      return o;
    }
  },

  double_helix: {
    titre: "doubleHelix — double hélice ascendante",
    famille: "ponctuel",
    jeu: 'particules',
    jeuNote: 'PAS ENCORE en pixels : en jeu, EnchantmentParticleFX.doubleHelix émet encore des particules vanilla. Cette fiche montre la forme visée.',
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
    jeu: 'pixels',
    jeuNote: "T436 : passe des particules aux pixels. CosmeticShapes.coussinNuage, drapeau F_ENCH_CLOUD_CUSHION (cloud_walker, air_walk, cloud_stride), visible en l'air uniquement.",
    accorde: "#063 Pont Dimensionnel (l.572), #090 Faucon Ouragan (l.703).",
    note: "Un COUSSIN : il porte le joueur, donc il vit SOUS ses pieds — de 0,50 bloc dessous à 0,10 bloc dessus, les pieds ne s'y enfoncent que de 0,10 et toute la masse pend en dessous. Seul ornement dessiné en GROS pixels, de trois tailles (0,112, 0,132 et 0,156 bloc) : un nuage n'a pas d'arête à suivre, il a du volume. Dix-neuf bouffées sur trois étages, 1,33 bloc d'envergure. Le grain est choisi par étage d'après son épaisseur — un pixel plus épais que l'étage qui le porte le réduirait à une tôle d'un seul bloc. Pixels jointifs (le pas vaut deux fois le grain) et teintés sur cinq valeurs selon leur hauteur réelle dans le nuage. Derrière, un sillage de volutes qui se délitent en pixels plus petits, et qui se rétracte quand le joueur s'arrête.",
    build(t) {
      const o = [];
      const BAS = 0x6B88AF, HAUT = 0xFFFFFF;
      // Le nuage est un COUSSIN : il porte le joueur, donc il vit SOUS ses pieds.
      // Fenetre imposee : de 0,50 bloc sous les pieds a 0,10 au-dessus. Les pieds
      // ne s'y enfoncent que de 0,10 ; tout le reste de la masse pend dessous.
      // Le grain est choisi PAR ETAGE, d'apres son epaisseur : un pixel plus
      // epais que l'etage qui le porte le reduit a une tole d'un seul bloc.
      // Etage mince dessus -> pixels moyens ; etage epais au milieu -> les plus gros.
      // [dx, dz, rayon horizontal, rayon vertical, hauteur, grain, graine]
      const bouffees = [
        // --- etage 2 : le dessus, la ou on pose les pieds ---
        [ 0.000,  0.000, 0.300, 0.090,  0.028, 0.056, 17.5],
        [ 0.235,  0.140, 0.205, 0.077, -0.005, 0.056, 18.7],
        [-0.240, -0.125, 0.200, 0.076, -0.018, 0.056, 19.9],
        [ 0.035, -0.245, 0.195, 0.074, -0.030, 0.056, 21.1],
        [-0.040,  0.240, 0.192, 0.073, -0.012, 0.056, 22.3],
        // --- etage 1 : la masse mediane, la plus large, les plus gros pixels ---
        [ 0.000,  0.025, 0.360, 0.121, -0.212, 0.078, 11.5],
        [ 0.350, -0.085, 0.265, 0.103, -0.223, 0.078, 12.7],
        [-0.360,  0.065, 0.258, 0.102, -0.226, 0.078, 13.9],
        [ 0.070,  0.380, 0.252, 0.100, -0.228, 0.078, 15.1],
        [-0.060, -0.385, 0.248, 0.098, -0.231, 0.078, 16.3],
        [ 0.290, -0.300, 0.215, 0.090, -0.236, 0.066,  6.7],
        [-0.280, -0.305, 0.210, 0.089, -0.239, 0.066,  7.9],
        [ 0.255,  0.310, 0.212, 0.090, -0.234, 0.066,  9.1],
        [-0.265,  0.300, 0.208, 0.088, -0.237, 0.066, 10.3],
        // --- etage 0 : le dessous, des lobes qui pendent en se resserrant ---
        [ 0.000,  0.010, 0.255, 0.105, -0.352, 0.066,  0.7],
        [ 0.215,  0.060, 0.185, 0.087, -0.344, 0.066,  1.9],
        [-0.220,  0.085, 0.180, 0.085, -0.342, 0.066,  3.1],
        [ 0.090, -0.230, 0.178, 0.085, -0.339, 0.066,  4.3],
        [-0.105,  0.225, 0.175, 0.084, -0.342, 0.066,  5.5]
      ];
      for (let k = 0; k < bouffees.length; k++) {
        const [dx, dz, rh, rv, hy, grain, graine] = bouffees[k];
        // La respiration joue sur les RAYONS, pas sur la hauteur : hy est un
        // ecart aux pieds du joueur, le multiplier ferait sortir de la fenetre.
        const g = 1 + Math.sin(t * 1.35 + k * 1.25) * 0.055;
        const flot = Math.sin(t * 0.95 + k * 0.80) * 0.014;
        bouffeeNuage(o, dx, hy + flot, dz, rh * g, rv * g, rh * g,
                     grain, graine, BAS, HAUT, 238, -0.50, 0.60, -0.50, 0.10);
      }

      // ---- LE SILLAGE : volutes deposees, qui se retractent a l'arret ----
      const emp = empreintes(t, 6, 1.9, 0.375);
      for (let k = 0; k < emp.length; k++) {
        const e = emp[k];
        const R = (0.215 - k * 0.016) * e.k;
        const al = 214 * (1 - e.age * 0.85);
        const cote = Math.sin(k * 1.35 + t * 0.6) * 0.082;
        // Le sillage sort du nuage, donc SOUS les pieds, et remonte en se delitant
        // sans jamais depasser le dessus du coussin.
        const haut = -0.280 + e.age * 0.130;
        // Le grain suit la volute, mais il a un plancher : en dessous, le gros
        // pixel ne rentre plus dans la volute. Une volute qui ne peut plus
        // contenir trois pixels par axe se reduirait a UN cube seul perdu en
        // l'air : on la supprime plutot que de la laisser flotter.
        // Plancher bas : une volute qui se delite a le droit de finir en pixels
        // plus petits, c'est meme ce qui se lit comme une dissipation. Un
        // plancher trop haut tuait la volute des la moitie de sa vie.
        const grain = Math.max(0.038, R * 0.32);
        if (al < 20 || R < grain * 2.9) continue;
        bouffeeNuage(o, cote, haut, e.z, R, R * 0.66, R,
                     grain, 20 + k * 2.3, 0x8EA8C6, 0xF4F9FF, al, haut - R * 0.8, R * 1.9);
      }
      return o;
    }
  },

  tether_beam: {
    titre: "tetherBeam — rayon tendu",
    famille: "ponctuel",
    jeu: 'particules',
    jeuNote: 'PAS ENCORE en pixels : en jeu, EnchantmentParticleFX.tetherBeam émet encore des particules vanilla. Cette fiche montre la forme visée.',
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
    jeu: 'pixels',
    jeuNote: "T436 : passe des particules aux pixels. CosmeticShapes.ondeChoc, drapeau F_ENCH_LANDING_WAVE (crash_landing, meteor_stomp), ancrée au point d'impact pendant 22 ticks.",
    accorde: "La forme la plus utilisée du registre : 43 appels dans EnchantmentComboHandler.",
    note: "AUCUNE traînée : cette onde ne naît qu'à l'atterrissage, le joueur ne la promène pas. En revanche elle porte la mémoire de la chute — un ÉCHO intérieur de un à quatre anneaux concentriques selon la hauteur tombée, avec une gerbe de débris d'autant plus haute. Le nombre de cubes augmente avec le rayon, donc l'onde reste pleine du début à la fin.",
    build(t) {
      const o = [];
      // Hauteur de chute simulee pour l'apercu : 2 a 14 blocs.
      const chute = (typeof window !== 'undefined' && window.DLB_CHUTE !== undefined)
        ? window.DLB_CHUTE : (2 + 12 * (0.5 + 0.5 * Math.sin(t * 0.33)));
      const force = Math.min(1, chute / 14);
      const nEcho = 1 + Math.min(3, Math.floor(chute / 3.5));   // 1 a 4 anneaux

      const onde = (p, rayon0, amplitude, teinte, force2) => {
        const r = rayon0 + p * amplitude, w = 0.068;
        const al = Math.round(248 * Math.pow(1 - p, 1.25) * force2);
        if (al < 10 || r < 0.05) return;
        const n = pasFerme(TAU * r, w);
        for (let i = 0; i < n; i++) {
          const a = TAU * i / n;
          const ca = Math.cos(a), sa = Math.sin(a);
          const bosse = 0.045 * Math.sin(Math.PI * (1 - p)) * (0.8 + 0.2 * Math.sin(a * 7 + t));
          cube(o, ca * r, 0.030 + bosse, sa * r, w / 2, 0.016, w / 2,
            mixRgb(teinte, 0xFF8A20, p), al);
          if (i % 5 === 0) {
            cube(o, ca * (r - 0.030), 0.030 + bosse * 0.45, sa * (r - 0.030),
              0.022, 0.014, 0.022, mixRgb(0xFFC77A, 0x8A5A20, p), Math.round(al * 0.55));
          }
        }
      };

      const p = (t * 0.55) % 1;
      // 1. l'onde principale, d'autant plus large que la chute etait haute
      onde(p, 0.24, 0.72 + force * 0.62, 0xFFE9A0, 1);
      // 2. l'echo interieur : des repliques qui partent du point d'impact,
      //    en retard de phase. Leur NOMBRE dit la hauteur de la chute.
      for (let k = 1; k <= nEcho; k++) {
        const pk = p - k * 0.18;
        if (pk <= 0) continue;
        onde(pk, 0.10, 0.52 + force * 0.30, 0xFFF6D0, 0.78 - k * 0.14);
      }
      // 3. la gerbe de debris au point d'impact, proportionnelle a la chute
      const nd = 4 + Math.round(force * 8);
      for (let i = 0; i < nd; i++) {
        const a = i * 2.399 + t * 0.4;
        const q = ((t * 0.9 + i * 0.137) % 1);
        const h = (0.18 + force * 0.62) * Math.sin(Math.PI * q);
        const rr = 0.07 + q * (0.20 + force * 0.22);
        const al = Math.round(206 * (1 - q) * (0.45 + force * 0.55));
        if (al < 16) continue;
        bille(o, Math.cos(a) * rr, 0.030 + h, Math.sin(a) * rr, 0.016,
          mixRgb(0xFFD089, 0x8A5A20, q), al, 0xFFF3C8);
      }
      return o;
    }
  },

  burst_on_entity: {
    titre: "burstOnEntity — éclat sur la cible",
    famille: "ponctuel",
    jeu: 'particules',
    jeuNote: 'PAS ENCORE en pixels : en jeu, EnchantmentParticleFX.burstOnEntity émet encore des particules vanilla. Cette fiche montre la forme visée.',
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
function plume(out, base, dir, L, larg, lateral, couleur, pointe, alpha, ombre, barbes, w) {
  // Une plume n'est pas un batonnet colore. Elle a un RACHIS clair au centre,
  // deux vexilles ASYMETRIQUES (l'interieur plus etroit que l'exterieur), des
  // STRIES obliques, et quelques BARBES qui depassent du bord. Le grain `w`
  // est donne par l'appelant : une remige d'un metre ne se dessine pas au
  // meme pas qu'une couverture de vingt centimetres.
  const om = (ombre === undefined) ? 1 : ombre;
  const al = (alpha === undefined) ? 240 : alpha;
  const g = (w === undefined) ? 0.062 : w;
  const bout = [base[0] + dir[0] * L, base[1] + dir[1] * L, base[2] + dir[2] * L];

  for (const cote of [-1, 1]) {
    const gain = (cote < 0 ? 0.80 : 1.0) * 0.5;
    bande(out, g,
      (u) => {
        const p = lerp3(base, bout, u);
        const dec = larg * plumeProfil(u) * gain * 0.5 * cote;
        return [p[0] + lateral[0] * dec, p[1] + lateral[1] * dec, p[2] + lateral[2] * dec];
      },
      (u) => larg * plumeProfil(u) * gain,
      () => lateral,
      (u, v) => {
        // distance au rachis, 0 au centre de la plume, 1 au bord du vexille
        const d = Math.min(1, Math.abs(v * 2 + cote));
        const rachis = Math.pow(1 - d, 2.2);
        const strie = 0.5 + 0.5 * Math.cos(u * 26 + v * 11);
        return {
          rgb: mixRgb(mixRgb(couleur, pointe, Math.pow(u, 0.78)), 0xFFFFFF,
                      rachis * 0.50 + strie * 0.10),
          a: (al - u * 40) * om * (1 - d * 0.18)
        };
      });
  }

  const nb = (barbes === undefined) ? 0 : barbes;
  for (let i = 0; i < nb; i++) {
    const u = 0.34 + 0.52 * (i / Math.max(1, nb - 1));
    const p = lerp3(base, bout, u);
    const demi = larg * plumeProfil(u) * 0.5;
    for (const cote of [-1, 1]) {
      const q = [p[0] + lateral[0] * demi * cote * 1.34 + dir[0] * 0.042,
                 p[1] + lateral[1] * demi * cote * 1.34 + dir[1] * 0.042,
                 p[2] + lateral[2] * demi * cote * 1.34 + dir[2] * 0.042];
      trait(out, [p[0] + lateral[0] * demi * cote, p[1] + lateral[1] * demi * cote,
                  p[2] + lateral[2] * demi * cote], q, g * 0.80,
        (x) => ({ rgb: mixRgb(pointe, 0xFFFFFF, x * 0.4), a: (al - 86) * om }));
    }
  }
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
  const paire = [];
  for (const cote of [-1, 1]) paire.push(...uneAileSeraphin(t, cote));
  return paire;
}

function uneAileSeraphin(t, s) {
  // Anatomie : epaule E -> coude C -> poignet P -> main M. L'arche passe
  // AU-DESSUS de la tete, puis la cascade de remiges retombe derriere.
  const o = [];
  const resp = Math.sin(t * 1.15);
  const E = [s * 0.110, 1.400, -0.200];
  const C = [s * 0.235, 1.722 + resp * 0.026, -0.316];
  const P = [s * 0.398, 1.988 + resp * 0.042, -0.398];
  const M = [s * 0.524, 1.930 + resp * 0.048, -0.442];
  const OR = 0xD99420, CHAUD = 0xFFD264, CLAIR = 0xFFF3CF, PALE = 0xFFFBEA;

  // --- 1. la charpente : os visible de l'epaule a la main ------------------
  const bras = (u) => {
    if (u < 0.42) { const w = u / 0.42; return lerp3(E, C, w); }
    if (u < 0.80) { const w = (u - 0.42) / 0.38; return lerp3(C, P, w); }
    const w = (u - 0.80) / 0.20; return lerp3(P, M, w);
  };
  courbe(o, 0.062, bras, (u) => ({
    rgb: mixRgb(OR, CLAIR, 0.30 + u * 0.45),
    a: 250 - u * 30,
    hx: 0.031 - u * 0.013, hy: 0.031 - u * 0.013, hz: 0.031 - u * 0.013
  }));
  // articulations marquees : coude et poignet
  bille(o, C[0], C[1], C[2], 0.030, CLAIR, 250, OR);
  bille(o, P[0], P[1], P[2], 0.026, CLAIR, 250, OR);

  // --- 2. sept remiges primaires, en eventail ------------------------------
  for (let i = 0; i < 7; i++) {
    const f = i / 6;
    const base = lerp3(P, M, f * 0.92);
    const L = 0.590 + 0.600 * Math.pow(f, 0.88) + (i % 2 ? -0.054 : 0.038);
    // Les doigts d'une aile s'ECARTENT : la composante laterale croit vite
    // sur les externes, sinon la cascade se lit comme un seul bloc.
    const d = norm([s * (0.010 + 0.520 * f * f), -0.975, -0.170 - 0.070 * f]);
    const lat = norm(perp(d, [0, 0, 1]));
    plume(o, base, d, L, 0.176, lat, OR, PALE, 244, i % 2 ? 0.72 : 1.0, 2, 0.084);
  }

  // --- 3. quatre secondaires, plus courtes, au-dessus du coude -------------
  for (let i = 0; i < 4; i++) {
    const f = i / 3;
    const base = lerp3(C, P, f * 0.90);
    const L = 0.470 + 0.145 * f;
    const d = norm([s * (0.022 + 0.150 * f), -0.980, -0.185]);
    const lat = norm(perp(d, [0, 0, 1]));
    plume(o, base, d, L, 0.154, lat, OR, CLAIR, 232, i % 2 ? 0.78 : 1.0, 1, 0.082);
  }

  // --- 4. deux rangees de couvertures etagees : le "toit" de l'aile --------
  for (const [rang, lg, al] of [[0.0, 0.215, 236], [1.0, 0.136, 220]]) {
    const n = rang ? 3 : 5;
    for (let i = 0; i < n; i++) {
      const f = i / (n - 1);
      const base = lerp3(C, P, 0.06 + f * 0.84);
      const recul = rang * 0.052;
      const dep = [base[0], base[1] - recul, base[2] - 0.016 * rang];
      const L = lg + 0.040 * Math.sin(Math.PI * f);
      const d = norm([s * (0.030 + 0.070 * f), -0.984, -0.150]);
      const lat = norm(perp(d, [0, 0, 1]));
      plume(o, dep, d, L, 0.130 - rang * 0.016, lat,
            mixRgb(OR, CHAUD, 0.30 + rang * 0.35), CLAIR, al,
            i % 2 ? 0.80 : 1.0, 0, 0.070);
    }
  }

  // --- 5. l'epaulette : la masse de duvet qui ferme l'emplanture -----------
  bande(o, 0.070,
    (u) => lerp3(E, C, u),
    (u) => 0.084 * (1 - u * 0.30),
    () => [0, 0, 1],
    (u, v) => {
      const bord = Math.abs(v) * 2;
      return { rgb: mixRgb(OR, CHAUD, 0.25 + (1 - bord) * 0.45),
               a: 248 - u * 26 };
    });
  // duvet : petites touffes au bord de l'epaulette
  for (let i = 0; i < 3; i++) {
    const u = 0.14 + 0.70 * (i / 2);
    const p = lerp3(E, C, u);
    const d = norm([s * 0.10, -0.98, -0.16]);
    const lat = norm(perp(d, [0, 0, 1]));
    plume(o, [p[0], p[1] - 0.030, p[2] - 0.040], d, 0.108 + (i % 2) * 0.022,
          0.104, lat, mixRgb(OR, CHAUD, 0.5), CLAIR, 214, i % 2 ? 0.82 : 1.0, 0, 0.066);
  }

  // --- 6. deux alula a la main, ecartees comme un pouce --------------------
  for (let i = 0; i < 2; i++) {
    const d = norm([s * (0.46 + i * 0.16), -0.80, -0.36]);
    const lat = norm(perp(d, [0, 0, 1]));
    plume(o, [M[0], M[1] - 0.012 * i, M[2]], d, 0.244 - i * 0.046, 0.118, lat,
          CHAUD, PALE, 234, 1.0, 1, 0.076);
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
  const paire = [];
  for (const cote of [-1, 1]) paire.push(...uneVoilureGivre(t, cote));
  return paire;
}

function uneVoilureGivre(t, s) {
  // Volontairement SANS plume : une membrane cristalline tendue sur des
  // lattes de glace. L'oppose exact de l'aile de l'Archange.
  const o = [];
  const GIVRE = 0x9ACFF5, BLANC = 0xFFFFFF, OMBRE = 0x3E6A92, CYAN = 0xD6F0FF;
  const souffle = Math.sin(t * 1.5);
  const E = [s * 0.118, 1.388, -0.174];
  const P = [s * (0.790 + souffle * 0.022), 1.792 + souffle * 0.040, -0.332];
  const dc = norm([0, -0.880, -0.470]);
  const corde = (u) => 0.455 * (1 - 0.615 * u);
  const sur = (u) => lerp3(E, P, u);

  // --- 1. longeron principal, epais et facette ----------------------------
  courbe(o, 0.062, sur, (u) => ({
    rgb: mixRgb(mixRgb(OMBRE, GIVRE, 0.45 + u * 0.35), BLANC, 0.18),
    a: 252 - u * 22,
    hx: 0.029 - u * 0.012, hy: 0.029 - u * 0.012, hz: 0.029 - u * 0.012
  }));

  // --- 2. cinq panneaux de membrane, alternativement clairs ---------------
  const NP = 5;
  for (let k = 0; k < NP; k++) {
    const u0 = k / NP, u1 = (k + 1) / NP;
    const clair = (k % 2) ? 0.52 : 0.0;
    nappe(o, dist3(sur(u0), sur(u1)), corde((u0 + u1) / 2), 0.082,
      (a, b) => {
        const u = u0 + (u1 - u0) * a, p = sur(u), c = corde(u) * b;
        return [p[0] + dc[0] * c, p[1] + dc[1] * c, p[2] + dc[2] * c];
      },
      (a, b) => {
        const gel = 0.5 + 0.5 * Math.cos(a * 9 + b * 13 + k * 2.1);
        return {
          rgb: mixRgb(mixRgb(OMBRE, GIVRE, 0.30 + clair + b * 0.30 + gel * 0.16), CYAN, 0.20),
          a: 238 - b * 52 + gel * 18,
          hy: 0.015
        };
      });
  }

  // --- 3. six nervures radiales, du longeron au bord de fuite -------------
  for (let i = 0; i < 6; i++) {
    const u = 0.055 + 0.90 * (i / 5);
    const p = sur(u), c = corde(u);
    trait(o, p, [p[0] + dc[0] * c, p[1] + dc[1] * c, p[2] + dc[2] * c], 0.052,
      (w) => ({ rgb: mixRgb(GIVRE, BLANC, 0.55 + w * 0.40), a: 252 - w * 34,
                hx: 0.025 - w * 0.009, hy: 0.025 - w * 0.009, hz: 0.025 - w * 0.009 }));
    // fracture secondaire en Y : ce qui fait lire "glace" et non "tissu"
    const mid = [p[0] + dc[0] * c * 0.46, p[1] + dc[1] * c * 0.46, p[2] + dc[2] * c * 0.46];
    for (const cote of [-1, 1]) {
      const dir = norm([dc[0] + cote * 0.52 * s, dc[1] * 0.70, dc[2] - cote * 0.18]);
      trait(o, mid, [mid[0] + dir[0] * c * 0.34, mid[1] + dir[1] * c * 0.34,
                     mid[2] + dir[2] * c * 0.34], 0.050,
        (w) => ({ rgb: mixRgb(CYAN, BLANC, w * 0.6), a: 180 - w * 90,
                  hx: 0.015, hy: 0.015, hz: 0.015 }));
    }
  }

  // --- 4. bord de fuite feston : il festonne entre chaque nervure ---------
  courbe(o, 0.054,
    (u) => {
      const p = sur(u), c = corde(u) * (1 + 0.075 * Math.sin(u * 19));
      return [p[0] + dc[0] * c, p[1] + dc[1] * c, p[2] + dc[2] * c];
    },
    (u) => ({ rgb: mixRgb(GIVRE, BLANC, 0.40 + 0.35 * Math.sin(u * 19)),
              a: 232, hx: 0.021, hy: 0.021, hz: 0.021 }));

  // --- 4bis. quatre doigts de glace qui percent la membrane et depassent --
  for (let i = 0; i < 4; i++) {
    const u = 0.20 + 0.74 * (i / 3);
    const p = sur(u), c = corde(u) * (1.14 + 0.06 * Math.sin(t * 1.3 + i));
    const bout = [p[0] + dc[0] * c, p[1] + dc[1] * c, p[2] + dc[2] * c];
    courbe(o, 0.054,
      (w) => [p[0] + (bout[0] - p[0]) * w + s * 0.030 * Math.sin(Math.PI * w),
              p[1] + (bout[1] - p[1]) * w,
              p[2] + (bout[2] - p[2]) * w - 0.026 * Math.sin(Math.PI * w)],
      (w) => ({ rgb: mixRgb(mixRgb(OMBRE, GIVRE, 0.5), BLANC, 0.25 + w * 0.45),
                a: 250 - w * 36,
                hx: 0.026 - w * 0.012, hy: 0.026 - w * 0.012, hz: 0.026 - w * 0.012 }));
    bille(o, bout[0] + s * 0.004, bout[1], bout[2], 0.017, BLANC, 242, GIVRE);
  }

  // --- 5. huit stalactites qui pendent du bord, de longueur variable ------
  for (let k = 0; k < 6; k++) {
    const u = 0.105 + 0.835 * (k / 5);
    const p = sur(u), c = corde(u);
    const b = [p[0] + dc[0] * c, p[1] + dc[1] * c, p[2] + dc[2] * c];
    const lg = 0.146 - k * 0.015 + Math.sin(t * 2.1 + k) * 0.012;
    courbe(o, 0.046,
      (w) => [b[0] + s * 0.016 * w * w, b[1] - lg * w, b[2] - 0.020 * w],
      (w) => ({ rgb: mixRgb(CYAN, BLANC, w * 0.75), a: 250 - w * 90,
                hx: 0.024 * (1 - w * 0.75), hy: 0.024, hz: 0.024 * (1 - w * 0.75) }));
    if (k % 2 === 0) {
      bille(o, b[0] + s * 0.016, b[1] - lg, b[2] - 0.020, 0.013, BLANC, 212, GIVRE);
    }
  }

  // --- 6. emplanture : trois ecailles de glace qui ancrent l'aile ---------
  for (let i = 0; i < 3; i++) {
    const u = 0.02 + i * 0.055;
    const p = sur(u), c = corde(u) * (0.30 + i * 0.10);
    bande(o, 0.066,
      (w) => [p[0] + dc[0] * c * w, p[1] + dc[1] * c * w, p[2] + dc[2] * c * w],
      (w) => 0.062 * (1 - w * 0.5),
      () => norm([s, 0, -0.35]),
      (w) => ({ rgb: mixRgb(OMBRE, CYAN, 0.30 + w * 0.5), a: 240 - w * 40 }));
  }

  // --- 7. bille de givre en bout d'aile -----------------------------------
  bille(o, P[0], P[1], P[2], 0.026, BLANC, 246, GIVRE);
  return o;
}

window.DLB_MODELS = MODELS;
window.DLB_H = H;
window.DLB_ZONE_REGARD = ZONE_REGARD;
