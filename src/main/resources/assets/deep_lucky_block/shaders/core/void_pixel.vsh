#version 150

// T397-PIXEL3D : le vertex shader du Pixel 3D.
//
// Il ne fait presque rien, et c'est voulu : tout le cout d'un systeme a plusieurs
// milliers de cubes est dans le FRAGMENT, pas ici. Les 24 sommets d'un cube sont
// ecrits tels quels par le gestionnaire ; ce programme les projette et transmet
// trois choses au fragment.
//
// LE POINT QUI DECIDE DE TOUT : la phase de l'arc-en-ciel.
//
// L'onde de couleur doit glisser le long des aretes « en fonction du GameTime et de
// la position 3D du cube dans le monde ». Or la Position recue ici est RELATIVE A LA
// CAMERA -- c'est la convention de tout le rendu de monde, et on ne peut pas y
// echapper sans perdre la precision des flottants a 30 millions de blocs de l'origine.
// Calculer la teinte sur cette position ferait glisser toutes les couleurs des que le
// joueur marche : l'effet entier deviendrait solidaire de la camera.
//
// La phase spatiale est donc calculee UNE FOIS PAR CUBE cote CPU (une addition, pas
// une couleur) et transportee dans Color.r. Le fragment la combine avec GameTime.
// Zero uniforme maison, zero conversion RGB cote CPU, et l'onde reste accrochee au
// monde quoi que fasse la camera.
//
// Les trois autres canaux de Color :
//   g = mode de rendu (0 arc-en-ciel, 1 teinte imposee, 2 noir et blanc) ;
//   b = teinte imposee du mode 1, sur un tour complet ;
//   a = fondu global du cube (age / duree de vie).

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 faceUv;
out float phase;
out float mode;
out float tintHue;
out float fade;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    // UV0 porte les coordonnees de FACE, 0..1 sur chacune des six faces. C'est la
    // seule chose dont le fragment a besoin pour savoir s'il est sur une arete : un
    // cube n'a pas besoin de ses 12 aretes en geometrie, il lui faut 6 quads et un
    // test de bord. 24 sommets au lieu de ~200.
    faceUv = UV0;
    phase = Color.r;
    // Color.g est normalise 0..1 par le format ; on le ramene au petit entier ecrit.
    mode = floor(Color.g * 255.0 + 0.5);
    tintHue = Color.b;
    fade = Color.a;
}
