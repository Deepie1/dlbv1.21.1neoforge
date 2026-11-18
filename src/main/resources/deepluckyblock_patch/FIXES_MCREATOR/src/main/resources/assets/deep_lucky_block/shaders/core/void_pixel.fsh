#version 150

// T397-PIXEL3D : le fragment shader du Pixel 3D.
//
// Un cube = un coeur en NIVEAU DE GRIS legerement transparent + 12 aretes lumineuses
// et colorees. Les aretes ne sont PAS de la geometrie : elles sont deduites des
// coordonnees de face. Sur chacun des 6 quads, uv va de 0 a 1 ; un fragment est sur
// une arete des qu'il approche d'un bord en u OU en v. Les quatre bords de six faces
// donnent exactement les douze aretes du cube, chacune vue par ses deux faces
// adjacentes -- d'ou leur nettete, sans un seul sommet de plus.
//
// LA REGLE DE L'ELEMENT, ET ELLE EST ABSOLUE : le corps n'a JAMAIS de couleur. Il n'a
// qu'une luminance, entre le noir et le blanc, transmise par cube. Toute la couleur du
// mod passe par les aretes. Un corps colore et une arete coloree se disputent l'oeil et
// la forme devient une tache ; un corps gris sous une arete vive se lit a dix metres.
//
// FULLBRIGHT : ce programme ne recoit ni lightmap ni overlay. Il n'y a donc rien a
// desactiver pour que les aretes brillent dans une grotte a minuit : elles brillent
// parce qu'aucune lumiere n'est jamais appliquee. C'est la raison d'etre du shader.
//
// ES 3.00 refuse les tableaux constants globaux (const vec3[]) : la rampe de teintes
// est donc une fonction, pas une table. Ce n'est pas une preference de style, c'est
// une incompatibilite deja payee sur un autre shader de ce mod.

in vec2 faceUv;
in float phase;
in float mode;
in float tintHue;
in float fade;
in float bodyGray;

uniform float GameTime;

out vec4 fragColor;

// Teinte -> RGB. Formule par valeurs absolues, sans branchement : six segments de la
// roue en trois lignes, ce qui vaut mieux qu'un switch sur un GPU qui execute les
// deux cotes de chaque branche de toute facon.
vec3 hueToRgb(float h) {
    h = fract(h);
    float r = abs(h * 6.0 - 3.0) - 1.0;
    float g = 2.0 - abs(h * 6.0 - 2.0);
    float b = 2.0 - abs(h * 6.0 - 4.0);
    return clamp(vec3(r, g, b), 0.0, 1.0);
}

// La moitie FROIDE de la roue : cyan electrique, bleu, violet profond, magenta neon,
// entre 0.50 et 0.92. C'est la teinte par defaut de l'element. Le vert et le jaune
// n'ont rien a faire dans un chaos prismatique froid -- mais le mode SPECTRUM, lui,
// parcourt la roue entiere, justement parce que le debordement est un evenement.
float chaosHue(float u) {
    return 0.50 + 0.42 * (0.5 + 0.5 * sin(u * 6.2831853));
}

// Bruit scalaire bon marche, reutilise par le grain du corps et par le scintillement
// du mode EMBER. Deux sinus incommensurables suffisent : une vraie texture aurait
// impose un sampler, donc un atlas, donc un bind par passe.
float noise1(float n) {
    return fract(sin(n * 12.9898) * 43758.5453);
}

float grain(vec2 uv) {
    float n = sin(uv.x * 37.0) * 43758.5453 + sin(uv.y * 29.0) * 12543.9321;
    return fract(n) * 0.06 - 0.03;
}

void main() {
    // Epaisseur des aretes : 10 % de la largeur du cube, au milieu de la fourchette
    // demandee (8 a 12 %). Mesure sur la face, donc identique quelle que soit la
    // taille du cube dans le monde -- un gros cube n'a pas des aretes plus epaisses
    // en proportion, il a les memes.
    const float EDGE = 0.10;
    const float TAU = 6.2831853;

    vec2 d = min(faceUv, vec2(1.0) - faceUv);
    float edgeDist = min(d.x, d.y);

    if (edgeDist < EDGE) {
        // --- ARETE : LA SEULE CHOSE QUI A LE DROIT D'ETRE COLOREE -------------------
        // L'onde glisse le long de l'arete : la phase spatiale du cube (accrochee au
        // monde) plus le temps. GameTime boucle sur 24000 ticks en valant 0..1, donc
        // il faut un gros facteur pour obtenir une vitesse visible ; 48.0 donne un
        // tour de roue toutes les cinq cents frames environ.
        float wave = phase + GameTime * 48.0 + (faceUv.x + faceUv.y) * 0.35;

        // Abscisse LE LONG de l'arete, et non en travers : sur un bord vertical c'est v
        // qui court, sur un bord horizontal c'est u. Sans cette distinction, les modes
        // a tete lumineuse feraient clignoter l'arete entiere au lieu d'y faire courir
        // un point, ce qui est exactement l'inverse de l'effet recherche.
        float along = (d.x < d.y) ? faceUv.y : faceUv.x;

        vec3 edgeRgb;
        if (mode < 0.5) {
            // 0 RAINBOW — l'onde froide continue. Le mode par defaut de l'element.
            edgeRgb = hueToRgb(chaosHue(wave));
        } else if (mode < 1.5) {
            // 1 TINTED — teinte imposee : l'onde ne change plus la couleur, seulement
            // son eclat. C'est le mode des details colores -- une gerbe rose reste rose.
            edgeRgb = hueToRgb(tintHue) * (0.72 + 0.28 * sin(wave * TAU));
        } else if (mode < 2.5) {
            // 2 MONO — luminance seule. Dans la Sphere d'Entropie, c'est le mode de ce
            // qui est encore EN ORDRE : la couleur n'arrive qu'avec le desordre, et ce
            // contraste porte a lui seul le sujet de la signature. L'arete prend LA
            // luminance DE SON CUBE et non une valeur fixe : une arete blanche
            // constante rendait les trente-deux crans invisibles des que la cage
            // occupait l'image, et la sphere ordonnee repassait en boule blanche.
            // Elle reste toujours plus claire que son corps (0,34 contre 0,055 au cran
            // zero), donc la cage se lit encore sur la nuit.
            edgeRgb = vec3((0.21 + 0.66 * bodyGray) * (0.82 + 0.18 * sin(wave * TAU)));
        } else if (mode < 3.5) {
            // 3 SPECTRUM — la roue ENTIERE, lentement. Le vert et le jaune, interdits
            // partout ailleurs, apparaissent ici : c'est le mode du debordement, celui
            // des cellules qui ont perdu toute discipline.
            edgeRgb = hueToRgb(wave * 0.6);
        } else if (mode < 4.5) {
            // 4 PULSE — teinte imposee, plus une TETE VIVE qui court le long de l'arete.
            // Le cube cesse d'etre un contour fixe : il a une direction et une vitesse.
            float head = fract(along - GameTime * 220.0 - phase * 3.0);
            float spark = smoothstep(0.80, 1.0, head);
            edgeRgb = mix(hueToRgb(tintHue) * 0.62, vec3(1.0), spark);
        } else if (mode < 5.5) {
            // 5 DUAL — deux teintes opposees sur la roue, separees par l'orientation de
            // l'arete. Un seul cube porte donc deux couleurs franches, et l'oeil lit sa
            // CAGE au lieu de lire un halo : c'est le mode des pieces d'armature.
            // La seconde teinte n'est PAS la complementaire : fract(h + 0.5) renvoie du
            // vert ou du jaune pour la moitie de la palette, et la paire rose/vert vire au
            // blanc des que les deux aretes se croisent a l'ecran. Mesure au banc :
            // l'anneau de choc du Big Bang, cense MASQUER, ressortait en blanc eclatant.
            // Le decalage de 0,26 reste dans la moitie froide quelle que soit l'entree.
            float h2 = tintHue > 0.72 ? tintHue - 0.26 : tintHue + 0.26;
            float h = (d.x < d.y) ? tintHue : h2;
            edgeRgb = hueToRgb(h) * (0.76 + 0.24 * sin(wave * TAU));
        } else if (mode < 6.5) {
            // 6 STROBE — l'arc-en-ciel QUANTIFIE en six crans. La couleur ne glisse plus,
            // elle SAUTE. Lecture immediate : quelque chose est casse.
            float q = floor(fract(wave) * 6.0) / 6.0;
            edgeRgb = hueToRgb(chaosHue(q)) * (0.80 + 0.20 * noise1(floor(wave * 6.0)));
        } else {
            // 7 EMBER — derive lente autour de la teinte imposee, fort scintillement.
            // La braise de la cicatrice : vivante, mais mourante.
            float flick = 0.45 + 0.55 * noise1(floor(wave * 9.0) + phase);
            edgeRgb = hueToRgb(tintHue + 0.07 * sin(wave * TAU * 0.5)) * flick;
        }

        // Les 2 % les plus exterieurs sont adoucis : une arete a bord franc scintille
        // des qu'elle fait moins d'un pixel a l'ecran, et a trente metres tous les
        // cubes en font moins d'un.
        float soft = smoothstep(0.0, EDGE * 0.22, edgeDist);
        soft = mix(1.0, 0.55, 1.0 - soft);
        fragColor = vec4(edgeRgb * soft, fade);
    } else {
        // --- COEUR : UN GRIS, ET RIEN D'AUTRE --------------------------------------
        // bodyGray arrive du sommet sur 32 crans. La rampe ne part pas de zero et ne
        // monte pas a un : un corps a 0.0 serait un trou noir indiscernable du fond de
        // nuit (la lecon payee sur la premiere passe de lisibilite), un corps a 1.0
        // rivaliserait avec les aretes, qui doivent rester les plus claires de l'image.
        float lum = 0.055 + 0.80 * bodyGray;
        vec3 body = vec3(lum) + vec3(grain(faceUv));
        // Un corps clair est aussi un corps plus couvrant : sans cela les gris hauts
        // paraissent delaves et la hierarchie des 32 crans s'ecrase en une bouillie.
        fragColor = vec4(body, (0.58 + 0.30 * bodyGray) * fade);
    }

    if (fragColor.a < 0.01) discard;
}
