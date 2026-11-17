#version 150

// T390-VOID-PORTAL : calque de rendertype_end_portal.fsh (1.21.1), repeint.
//
// Structure identique au vanilla : un fond echantillonne dans Sampler0, puis
// EndPortalLayers couches du meme champ d'etoiles (Sampler1), chacune tournee,
// translatee et mise a l'echelle differemment, ajoutees les unes aux autres.
// Comme texProj0 vient de l'ECRAN (voir le .vsh), chaque couche derive a sa
// propre vitesse : c'est la parallaxe.
//
// DEUX ecarts volontaires avec le vanilla :
//
//   1. Le fond. Vanilla multiplie end_sky.png par COLORS[0], ce qui l'ecrase a
//      quasi-noir. Ici void_sky.png vaut deja #050D25 (Deep Abyssal Void), donc on
//      le prend tel quel : noir absolu a l'oeil, mais avec le bleu de nuit qui
//      empeche la surface de se confondre avec un trou de rendu.
//
//   2. La palette. COLORS[] ne reprend plus les verts-cyan de l'End mais les
//      quatre gammes du Vide : Deep Abyssal Void, charbon, Call of the Void et la
//      serie bleu/mauve delavee. Chaque entree garde la TEINTE de son hexa ; seule
//      la luminosite suit une rampe croissante de la couche la plus lointaine
//      (indice 0, etoiles minuscules et serrees) a la plus proche (indice 14,
//      grosses etoiles rares). Sans cette rampe, quinze couches additives
//      saturent en blanc et toute la palette disparait.
//
// Les quinze couches sont additives les unes par rapport aux autres (color += ...)
// mais le fragment sort OPAQUE : le quad est un trou dans le monde, pas un voile.

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

uniform float GameTime;
uniform int EndPortalLayers;

in vec4 texProj0;

out vec4 fragColor;

const vec3[] COLORS = vec3[](
    vec3(0.013514, 0.035135, 0.100000),  // #050D25 Deep Abyssal Void, le fond du gouffre
    vec3(0.070000, 0.010000, 0.110000),  // #0e0216 Call of the Void, noir viole
    vec3(0.094030, 0.000000, 0.140000),  // #2d0043 Call of the Void, prune
    vec3(0.130000, 0.130000, 0.130000),  // #1c1c1c charbon 2
    vec3(0.057143, 0.045714, 0.170000),  // #282077 indigo delave
    vec3(0.195699, 0.049462, 0.200000),  // #5b175d Call of the Void, mauve
    vec3(0.170000, 0.170000, 0.170000),  // #3d3d3d charbon 3
    vec3(0.115772, 0.126577, 0.230000),  // #4B5295 bleu ardoise
    vec3(0.197561, 0.034756, 0.300000),  // #6c13a4 Call of the Void, violet
    vec3(0.174452, 0.182839, 0.260000),  // #686D9B bleu poussiere
    vec3(0.240000, 0.240000, 0.240000),  // #5c5c5c charbon 4
    vec3(0.290088, 0.046726, 0.440000),  // #9518e2 Call of the Void, violet neon
    vec3(0.320000, 0.272516, 0.299355),  // #9B8491 mauve grise
    vec3(0.420000, 0.354956, 0.323363),  // #E2BFAE sable pale
    vec3(0.408761, 0.065841, 0.620000),  // #9518e2 violet neon, la couche la plus PROCHE
    vec3(0.600000, 0.600000, 0.600000)   // #8c8c8c charbon 5, reserve si EndPortalLayers passe a 16
);

const mat4 SCALE_TRANSLATE = mat4(
    0.5, 0.0, 0.0, 0.25,
    0.0, 0.5, 0.0, 0.25,
    0.0, 0.0, 1.0, 0.0,
    0.0, 0.0, 0.0, 1.0
);

// Vanilla importe mat2_rotate_z de matrix.glsl ; on l'ecrit en clair, meme raison
// que pour projection.glsl dans le .vsh : aucun include a resoudre.
mat2 rotate_z(float angle) {
    float s = sin(angle);
    float c = cos(angle);
    return mat2(c, -s, s, c);
}

mat4 void_layer(float layer) {
    // Le portail de l'End ne fait defiler ses couches qu'a la verticale, a vive
    // allure. Le Vide, lui, doit DERIVER : on garde la montee (ralentie de moitie)
    // et on ajoute une derive laterale, plus rapide pour les couches proches.
    mat4 translate = mat4(
        1.0, 0.0, 0.0, 17.0 / layer + GameTime * (0.55 + layer * 0.08),
        0.0, 1.0, 0.0, (2.0 + layer / 1.5) * (GameTime * 0.75),
        0.0, 0.0, 1.0, 0.0,
        0.0, 0.0, 0.0, 1.0
    );

    mat2 rotate = rotate_z(radians((layer * layer * 4321.0 + layer * 9.0) * 2.0));

    // layer 1  -> x8.5 : la texture se repete beaucoup, les etoiles sont minuscules
    //                    et serrees, c'est le fond du gouffre.
    // layer 15 -> x1.5 : peu de repetitions, grosses etoiles rares, au premier plan.
    mat2 scale = mat2((4.5 - layer / 4.0) * 2.0);

    return mat4(scale * rotate) * translate * SCALE_TRANSLATE;
}

void main() {
    vec3 color = textureProj(Sampler0, texProj0).rgb;

    for (int i = 0; i < EndPortalLayers; i++) {
        color += textureProj(Sampler1, texProj0 * void_layer(float(i + 1))).rgb * COLORS[i];
    }

    fragColor = vec4(color, 1.0);
}
