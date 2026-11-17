#version 150

// T390-VOID-PORTAL : calque de rendertype_end_portal.vsh (1.21.1).
//
// Le vertex shader ne fait que DEUX choses, et c'est tout le secret de l'effet :
//   1. projeter le sommet normalement ;
//   2. recopier cette position projetee dans texProj0.
// Les coordonnees de texture ne viennent donc PAS du modele mais de l'ECRAN. Quand la
// camera tourne, la geometrie bouge, mais le champ d'etoiles, lui, reste accroche au
// viewport : c'est ce decalage qui donne la parallaxe du portail de l'End.
//
// Vanilla fait appel a #moj_import <projection.glsl>. On recopie la fonction au lieu de
// l'importer : un mod qui importe un include vanilla depend de la resolution de chemin
// (avec ou sans namespace) qui a change entre les versions. Zero import, zero surprise.

in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 texProj0;

vec4 projection_from_position(vec4 position) {
    vec4 projection = position * 0.5;
    projection.xy = vec2(projection.x + projection.w, projection.y + projection.w);
    projection.zw = position.zw;
    return projection;
}

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texProj0 = projection_from_position(gl_Position);
}
