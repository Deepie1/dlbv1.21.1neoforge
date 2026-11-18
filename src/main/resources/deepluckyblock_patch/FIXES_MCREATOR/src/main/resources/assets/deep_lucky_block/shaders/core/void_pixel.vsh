#version 150

// T397-PIXEL3D : le vertex shader du Pixel 3D.
//
// Il ne fait presque rien, et c'est voulu : tout le cout d'un systeme a plusieurs
// milliers de cubes est dans le FRAGMENT, pas ici. Les 24 sommets d'un cube sont
// ecrits tels quels par le gestionnaire ; ce programme les projette et transmet
// quatre choses au fragment.
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
// T397-GRIS — LE CANAL VERT PORTE DEUX CHOSES, ET VOICI POURQUOI
// ---------------------------------------------------------------
// La consigne est « faces en niveaux de gris, couleur UNIQUEMENT sur les aretes ». Il
// faut donc un gris PAR CUBE : un corps unique pour des milliers de cubes redonne le
// nuage de taches uniforme qu'on cherche precisement a eviter, et c'est le contraste
// entre un cube presque noir et un cube presque blanc qui dessine le volume quand
// aucune lumiere n'est appliquee.
//
// Mais le format de sommet est POSITION_TEX_COLOR et il ne bougera pas : UV0 porte deja
// les coordonnees de face (d'ou sortent les douze aretes), et les quatre canaux de Color
// sont pris. Ajouter un attribut aurait impose un format maison, donc un RenderType
// maison de plus, donc un deuxieme chemin a maintenir pour UN scalaire.
//
// Le canal vert est donc un OCTET EMPAQUETE :   g = mode * 32 + gris
//      mode 0..7   le comportement colorimetrique de l'arete (huit derives)
//      gris 0..31  la luminance du corps, 32 crans entre le noir et le blanc
// Trente-deux crans de gris sont tres au-dela du discernable sur une face de sept
// centimetres, et huit modes couvrent tout ce que les trois signatures demandent.
// L'empaquetage coute une division et un modulo par sommet. Le format ne bouge pas.

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
out float bodyGray;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    // UV0 porte les coordonnees de FACE, 0..1 sur chacune des six faces. C'est la
    // seule chose dont le fragment a besoin pour savoir s'il est sur une arete : un
    // cube n'a pas besoin de ses 12 aretes en geometrie, il lui faut 6 quads et un
    // test de bord. 24 sommets au lieu de ~200.
    faceUv = UV0;
    phase = Color.r;

    // Desempaquetage du canal vert. Le +0.5 avant le floor est obligatoire : Color.g
    // arrive normalise 0..1 et le produit par 255 tombe a 31.99999 pour l'octet 32 sur
    // certaines cartes, ce qui ferait basculer le mode d'un cran sur un pixel sur mille.
    float packed = floor(Color.g * 255.0 + 0.5);
    mode = floor(packed / 32.0);
    bodyGray = (packed - mode * 32.0) / 31.0;

    tintHue = Color.b;
    fade = Color.a;
}
