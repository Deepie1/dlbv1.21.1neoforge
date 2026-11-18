#version 150

// T397-PIXEL3D : le fragment shader du Pixel 3D.
//
// Un cube = un coeur anthracite legerement transparent + 12 aretes lumineuses qui
// defilent en arc-en-ciel. Les aretes ne sont PAS de la geometrie : elles sont
// deduites des coordonnees de face. Sur chacun des 6 quads, uv va de 0 a 1 ; un
// fragment est sur une arete des qu'il approche d'un bord en u OU en v. Les quatre
// bords de six faces donnent exactement les douze aretes du cube, chacune vue par
// ses deux faces adjacentes -- d'ou leur nettete, sans un seul sommet de plus.
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

// La palette demandee n'est pas la roue complete : cyan electrique, magenta neon,
// violet profond, bleu. Ces quatre teintes occupent la moitie froide de la roue,
// entre 0.50 (cyan) et 0.92 (rose). Une roue complete y aurait ajoute du vert et du
// jaune, qui n'ont rien a faire dans un effet de chaos prismatique froid.
float chaosHue(float u) {
    return 0.50 + 0.42 * (0.5 + 0.5 * sin(u * 6.2831853));
}

// Grain pixel-art tres leger sur le coeur. Deux sinus incommensurables suffisent :
// une vraie texture aurait impose un sampler, donc un atlas, donc un bind par passe.
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

    vec2 d = min(faceUv, vec2(1.0) - faceUv);
    float edgeDist = min(d.x, d.y);

    if (edgeDist < EDGE) {
        // --- ARETE ---
        // L'onde glisse le long de l'arete : la phase spatiale du cube (accrochee au
        // monde) plus le temps. GameTime boucle sur 24000 ticks en valant 0..1, donc
        // il faut un gros facteur pour obtenir une vitesse visible ; 48.0 donne un
        // tour de roue toutes les cinq cents frames environ.
        float wave = phase + GameTime * 48.0 + (faceUv.x + faceUv.y) * 0.35;
        vec3 edgeRgb;
        if (mode < 0.5) {
            edgeRgb = hueToRgb(chaosHue(wave));
        } else if (mode < 1.5) {
            // Teinte imposee : l'onde ne change plus la couleur, seulement son eclat.
            // C'est le mode des details colores -- une gerbe rouge reste rouge.
            edgeRgb = hueToRgb(tintHue) * (0.72 + 0.28 * sin(wave * 6.2831853));
        } else {
            // Noir et blanc : la meme onde, en luminance seule.
            float v = 0.70 + 0.30 * sin(wave * 6.2831853);
            edgeRgb = vec3(v);
        }
        // Les 2 % les plus exterieurs sont adoucis : une arete a bord franc scintille
        // des qu'elle fait moins d'un pixel a l'ecran, et a trente metres tous les
        // cubes en font moins d'un.
        float soft = smoothstep(0.0, EDGE * 0.22, edgeDist);
        soft = mix(1.0, 0.55, 1.0 - soft);
        fragColor = vec4(edgeRgb * soft, fade);
    } else {
        // --- COEUR ---
        // Anthracite #23232A, legerement transparent. Le grain evite la face plate
        // qui trahit immediatement un quad non texture.
        float g = grain(faceUv);
        vec3 body = vec3(0.1373, 0.1373, 0.1647) + vec3(g);
        fragColor = vec4(body, 0.65 * fade);
    }

    if (fragColor.a < 0.01) discard;
}
