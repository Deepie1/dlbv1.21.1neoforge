package deepluckyblock.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * T376-THUNDER-REFONTE — élément <b>FOUDRE</b> : trois signatures voxel sur un moteur commun.
 *
 * <p><b>Pourquoi cette classe ?</b> La foudre était, après l'eau, la dernière grande famille sans
 * moteur dédié. Ses trois signatures étaient dessinées en ligne dans
 * {@code WeaponWorldVfxManager#impactThunder} — trois traits, une croix et une cage générique — et
 * duraient <b>11 à 14 ticks</b>, soit moins d'une demi-seconde, là où le feu, la terre, le métal,
 * la lumière, le poison, le bois, le vent et l'eau ont tous reçu une chorégraphie de 80 à 170
 * ticks avec son propre fichier. Cette classe met la foudre au même niveau.</p>
 *
 * <p><b>Langage visuel.</b> Un éclair n'est pas une ligne : c'est une <i>polyligne fractale</i>.
 * Sa signature optique tient à trois choses qu'aucune sinusoïde ne reproduit — il <b>casse</b> à
 * angle vif, il se <b>ramifie</b> (et les branches se ramifient à leur tour), et il possède un
 * <b>cœur</b> blanc surexposé entouré d'une gaine violette plus large. Ici chaque trait est donc
 * tracé deux fois : la gaine, puis le cœur. Le déplacement latéral vient d'une somme d'octaves de
 * bruit haché à interpolation <i>linéaire</i> : l'interpolation lisse donnerait une onde, pas une
 * cassure.</p>
 *
 * <p><b>Un piège spécifique à cette famille.</b> Le moteur de voxels décime les cubes dont l'alpha
 * est inférieur à 200 (tamis {@code pixelDensity}). Un éclair est par nature fin : s'il est émis à
 * alpha faible, il n'arrive à l'écran qu'en pointillés aléatoires. Tout ce fichier passe donc par
 * {@link #opaque(float)}, qui relève l'alpha au-dessus du seuil ou n'émet rien du tout. La
 * disparition se joue par la <b>géométrie</b> (longueur, épaisseur, nombre de segments), jamais
 * par un fondu d'alpha.</p>
 *
 * <ul>
 *   <li>{@link #BOLT} — <b>Arc fulgurant en chaîne</b> ({@code CHAIN_VOLT_ARC}, 130 ticks) :
 *       l'air s'ionise en filaments montants, un trait de 22 blocs tombe du ciel avec ses
 *       ramifications, l'impact vitrifie le sol en <i>fulgurites</i>, puis l'arc <b>saute de
 *       cible en cible</b> — six relais successifs, chacun avec son propre tracé.</li>
 *   <li>{@link #OVERCLOCK} — <b>Surcadence Cyber-X</b> ({@code OVERCLOCK_CYBER_X}, 120 ticks) :
 *       quatre pylônes se dressent, une cage cubique se trace arête par arête, de vraies
 *       <i>pistes de circuit imprimé</i> (tracé de Manhattan, pastilles, vias) courent sur ses
 *       faces, des paquets de données y circulent, un histogramme de cadence monte, puis tout
 *       implose dans le porteur.</li>
 *   <li>{@link #ECHO} — <b>Double entaille d'écho</b> ({@code TWIN_ECHO_SLASH}, 96 ticks) : une
 *       lame de foudre balaie en arc, laisse quatre rémanences qui rétrécissent, puis sa
 *       <b>jumelle</b> repasse en miroir sur un plan incliné à 90° ; au croisement les deux
 *       décharges se court-circuitent en gerbe d'arcs.</li>
 * </ul>
 *
 * <p>Le repère est le même que pour l'eau et le vent : {@code f} suit l'axe attaquant → cible,
 * {@code lateral} lui est perpendiculaire, {@code y} est la hauteur, l'origine étant au point
 * d'impact.</p>
 */
public final class ThunderSignatureVfx {

    /** Arc fulgurant en chaîne — {@code CHAIN_VOLT_ARC}. */
    public static final int BOLT = 0;
    /** Surcadence Cyber-X — {@code OVERCLOCK_CYBER_X}. */
    public static final int OVERCLOCK = 1;
    /** Double entaille d'écho — {@code TWIN_ECHO_SLASH}. */
    public static final int ECHO = 2;

    /**
     * Plancher d'opacité. Le tamis du moteur supprime les cubes sous 200 d'alpha quand la densité
     * de pixels est réduite ; 208 passe à coup sûr. Leçon des refontes précédentes : un éclair à
     * alpha 120 n'est pas « plus discret », il est <i>troué</i>.
     */
    private static final int SOLID_FLOOR = 208;

    // ---- Palette. Le violet vient de DeepElement.THUNDER (0x5E3AFF / 0x1E26FF / 0xFCFFBA). ----
    private static final int DEEP = 0x1E26FF;      // bleu de nuit, le pied des filaments
    private static final int VIOLET = 0x5E3AFF;    // violet électrique, teinte maîtresse
    private static final int ARC = 0x8E6BFF;       // gaine des traits
    private static final int NEON = 0xC9B6FF;      // halo proche du cœur
    private static final int HOT = 0xFCFFBA;       // jaune de surexposition
    private static final int CORE = 0xFFFFFF;      // cœur du canal
    // Cyber : la surcadence n'est pas un orage, c'est de l'électronique.
    private static final int CYAN = 0x2BE8FF;
    private static final int MAGENTA = 0xFF3BD4;
    private static final int PCB = 0x1BE08A;       // vert carte de circuit imprimé
    // Sol frappé.
    private static final int GLASS = 0x7C68A0;     // fulgurite : sable vitrifié
    private static final int MOLTEN = 0xFFB347;    // verre encore chaud
    private static final int SCORCH = 0x2A2036;    // brûlure

    private static final float TAU = 6.2831855f;

    /** Hauteur de chute du trait maître. Au-delà, le trait sort du champ de vision courant. */
    private static final float SKY_HEIGHT = 22.0f;
    /** Demi-épaisseur du cœur d'un trait principal. */
    private static final float BOLT_CORE = 0.075f;
    /** Rayon de la couronne de plasma au sol, à pleine extension. */
    private static final float CROWN_RADIUS = 5.10f;
    /** Nombre de relais de la chaîne — le serveur en frappe autant (voir WeaponVfxServerListener). */
    private static final int CHAIN_LINKS = 6;
    /** Demi-arête de la cage de surcadence. */
    private static final float CAGE_HALF = 1.32f;
    /** Rayon de l'arc décrit par les lames d'écho. */
    private static final float ECHO_RADIUS = 3.35f;

    private ThunderSignatureVfx() { }

    /**
     * Point d'entrée commun.
     *
     * @param t       avancement normalisé 0 → 1 de la chorégraphie
     * @param dx,dz   axe horizontal unitaire attaquant → cible
     * @param variant {@link #BOLT}, {@link #OVERCLOCK} ou {@link #ECHO}
     * @param salt    graine stable de l'instance : deux éclairs ne se ressemblent jamais
     */
    public static void render(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                              float cx, float cy, float cz, float t,
                              float dx, float dz, int variant, int alpha, int salt) {
        if (t <= 0.0f || t >= 1.0f || alpha <= 4) return;
        float length = Mth.sqrt(dx * dx + dz * dz);
        if (length < 0.001f) { dx = 0.0f; dz = 1.0f; }
        else { dx /= length; dz /= length; }
        if (variant == OVERCLOCK) renderOverclock(vc, mat, tmp, cx, cy, cz, t, dx, dz, alpha, salt);
        else if (variant == ECHO) renderEcho(vc, mat, tmp, cx, cy, cz, t, dx, dz, alpha, salt);
        else renderBolt(vc, mat, tmp, cx, cy, cz, t, dx, dz, alpha, salt);
    }

    // =================================================================================
    //  I. ARC FULGURANT EN CHAÎNE — 130 ticks
    // =================================================================================

    /**
     * Chronologie (130 ticks) :
     * <ul>
     *   <li>0,00–0,10 <b>ionisation</b> : quatorze filaments montent du sol en serpentant, le
     *       champ se referme en anneau ;</li>
     *   <li>0,10–0,16 <b>le trait maître</b> tombe du ciel, révélé de haut en bas, avec cinq
     *       ramifications qui se ramifient elles-mêmes ;</li>
     *   <li>0,16–0,30 <b>impact</b> : couronne de plasma, arcs rampants, vitrification du sol ;</li>
     *   <li>0,30–0,76 <b>la chaîne</b> : six relais partent l'un après l'autre, chacun avec son
     *       tracé, son flash d'arrivée et sa propre couronne ;</li>
     *   <li>0,76–1,00 <b>rémanence</b> : les fulgurites refroidissent, l'air crépite.</li>
     * </ul>
     */
    private static void renderBolt(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                   float cx, float cy, float cz, float t,
                                   float dx, float dz, int alpha, int salt) {
        int a = Math.round(alpha * smooth(t / 0.04f) * (1.0f - smooth((t - 0.94f) / 0.06f)));
        if (a <= 4) return;

        float ionise = smooth(t / 0.10f);
        float fall = smooth((t - 0.10f) / 0.055f);            // descente du trait maître
        float flash = smooth((t - 0.15f) / 0.02f) * (1.0f - smooth((t - 0.21f) / 0.10f));
        float crown = smooth((t - 0.155f) / 0.17f);
        float vitrify = smooth((t - 0.17f) / 0.22f);
        float dusk = 1.0f - smooth((t - 0.80f) / 0.20f);

        // ---- 1. IONISATION : l'air se charge AVANT la frappe, de bas en haut ----
        // C'est le « leader ascendant » : sans lui l'éclair surgit de nulle part et le joueur
        // n'a aucune anticipation. Les filaments meurent à l'instant exact de la frappe.
        if (ionise > 0.01f && fall < 0.92f) {
            float live = ionise * (1.0f - smooth(fall * 1.4f));
            for (int fil = 0; fil < 14; fil++) {
                float h0 = hash(salt, 100 + fil * 37);
                float ang = fil * TAU / 14.0f + h0 * 0.38f;
                float rad = 0.70f + 2.40f * hash(salt, 160 + fil * 11);
                float top = (1.10f + 2.30f * h0) * live;
                int steps = 7;
                for (int s = 0; s < steps; s++) {
                    float u = s / (float) (steps - 1);
                    if (u > live * 1.15f) break;
                    float wob = 0.26f * Mth.sin(u * 7.0f + h0 * 9.0f + t * 34.0f);
                    float r = rad * (1.0f - 0.22f * u);
                    float y = -0.42f + top * u;
                    float sz = (0.070f - 0.034f * u) * live;
                    if (sz <= 0.004f) continue;
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(ang + wob) * r, Mth.sin(ang + wob) * r, y,
                            sz, sz * 1.45f, sz,
                            mix(DEEP, ARC, u), opaque(a * live * (0.55f + 0.45f * u)));
                }
            }
            // Anneau de champ : le sol se charge, il ne s'allume pas d'un coup.
            int ringCount = 46;
            for (int i = 0; i < ringCount; i++) {
                float ang = i * TAU / ringCount + t * 2.4f;
                float h0 = hash(salt, 400 + i * 13);
                if (h0 > 0.30f + 0.62f * live) continue;      // il se remplit progressivement
                float r = 3.25f * (0.90f + 0.12f * Mth.sin(ang * 5.0f + t * 20.0f));
                float sz = 0.062f + 0.040f * h0;
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(ang) * r, Mth.sin(ang) * r, -0.40f + 0.05f * h0,
                        sz, sz * 0.55f, sz, mix(DEEP, VIOLET, h0), opaque(a * live * 0.85f));
            }
        }

        // ---- 2. LE TRAIT MAÎTRE : polyligne fractale de 22 blocs, révélée de haut en bas ----
        if (fall > 0.0f && t < 0.34f) {
            // Il reste visible un court instant après la frappe, en s'amincissant : c'est la
            // persistance rétinienne du canal de retour, pas un fondu d'alpha.
            float persist = 1.0f - smooth((t - 0.17f) / 0.15f);
            float reveal = fall;
            float thick = (1.0f - 0.55f * (1.0f - persist));
            if (persist > 0.02f) {
                drawBolt(vc, mat, tmp, cx, cy, cz, dx, dz,
                        0.0f, 0.0f, SKY_HEIGHT, 0.0f, 0.0f, 0.0f,
                        salt * 31 + 7, 1.55f, reveal, thick, a * persist, 56, true);
                // Ramifications : cinq départs le long du canal, orientés vers le bas-dehors.
                for (int br = 0; br < 5; br++) {
                    float hb = hash(salt, 900 + br * 59);
                    float u0 = 0.18f + 0.62f * hb;             // où la branche quitte le canal
                    if (reveal < u0 + 0.05f) continue;
                    float bang = hash(salt, 940 + br * 23) * TAU;
                    float blen = 2.10f + 2.90f * hash(salt, 980 + br * 17);
                    float sy = SKY_HEIGHT * (1.0f - u0);
                    float sxF = boltOffset(salt * 31 + 7, u0, 1.55f);
                    float sxL = boltOffset(salt * 31 + 7 + 5000, u0, 1.55f);
                    float ey = Math.max(0.10f, sy - blen * (0.45f + 0.40f * hb));
                    drawBolt(vc, mat, tmp, cx, cy, cz, dx, dz,
                            sxF, sxL, sy,
                            sxF + Mth.cos(bang) * blen, sxL + Mth.sin(bang) * blen, ey,
                            salt * 17 + br * 211, 0.72f,
                            Mth.clamp((reveal - u0) * 3.4f, 0.0f, 1.0f),
                            thick * 0.62f, a * persist * 0.92f, 16, false);
                }
            }
        }

        // ---- 3. FLASH D'IMPACT : une boule de surexposition, brève et franche ----
        if (flash > 0.02f) {
            float r = 0.55f + 1.35f * flash;
            int shells = 3;
            for (int sh = 0; sh < shells; sh++) {
                float sv = sh / (float) (shells - 1);
                float rr = r * (0.42f + 0.58f * sv);
                int count = Math.max(10, Math.round(rr * 13.0f));
                for (int i = 0; i < count; i++) {
                    float h0 = hash(salt, 1500 + sh * 97 + i * 29);
                    float ang = i * TAU / count + sh * 0.5f;
                    float yy = (h0 - 0.5f) * rr * 1.5f;
                    float pr = rr * Mth.sqrt(Math.max(0.0f, 1.0f - (yy / Math.max(0.01f, rr * 0.75f))
                            * (yy / Math.max(0.01f, rr * 0.75f))));
                    float sz = (0.17f - 0.07f * sv) * flash;
                    if (sz <= 0.004f) continue;
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(ang) * pr, Mth.sin(ang) * pr, 0.30f + yy,
                            sz, sz, sz, mix(CORE, HOT, sv * 0.8f), opaque(a * flash));
                }
            }
        }

        // ---- 4. COURONNE DE PLASMA : l'onde au sol, et des arcs qui RAMPENT vers l'extérieur ----
        if (crown > 0.01f) {
            float r = CROWN_RADIUS * crown;
            float thin = 1.0f - crown * 0.55f;
            int count = Math.max(16, Math.round(r * 15.0f));
            for (int i = 0; i < count; i++) {
                float h0 = hash(salt, 2100 + i * 31);
                float ang = i * TAU / count;
                float rr = r * (0.94f + 0.09f * Mth.sin(ang * 7.0f + t * 9.0f) + 0.04f * h0);
                float sz = (0.115f + 0.060f * h0) * thin;
                if (sz <= 0.004f) continue;
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(ang) * rr, Mth.sin(ang) * rr, -0.40f + 0.09f * h0,
                        sz * 1.5f, sz * 0.6f, sz * 1.5f,
                        mix(VIOLET, NEON, h0 * 0.7f), opaque(a * (1.0f - 0.35f * crown)));
            }
            // Huit serpents de surface, tracés fractals rampants : c'est la marque d'un impact
            // électrique au sol, très différente d'une onde de choc mécanique.
            for (int sp = 0; sp < 8; sp++) {
                float hs = hash(salt, 2400 + sp * 67);
                float ang = sp * TAU / 8.0f + hs * 0.5f;
                float reach = r * (0.55f + 0.45f * hs);
                drawBolt(vc, mat, tmp, cx, cy, cz, dx, dz,
                        0.0f, 0.0f, -0.36f,
                        Mth.cos(ang) * reach, Mth.sin(ang) * reach, -0.36f,
                        salt * 13 + sp * 331, 0.58f, crown, 0.52f,
                        a * (1.0f - 0.45f * crown), 12, false);
            }
        }

        // ---- 5. FULGURITES : le sable vitrifié reste, et refroidit ----
        // Détail qui ancre l'impact dans le monde : après la foudre, le sol GARDE une trace.
        if (vitrify > 0.01f) {
            float heat = 1.0f - smooth((t - 0.30f) / 0.34f);   // orange → verre froid
            for (int fg = 0; fg < 7; fg++) {
                float h0 = hash(salt, 3000 + fg * 53);
                float ang = fg * TAU / 7.0f + h0 * 0.7f;
                float rad = 0.65f + 1.55f * h0;
                int joints = 5;
                float grow = vitrify * dusk;
                for (int s = 0; s < joints; s++) {
                    float u = s / (float) (joints - 1);
                    if (u > grow * 1.2f) break;
                    float lean = 0.22f * u * u;
                    float y = -0.44f + (0.52f + 0.70f * h0) * u * grow;
                    float sz = (0.115f - 0.062f * u) * grow;
                    if (sz <= 0.004f) continue;
                    int rgb = mix(GLASS, MOLTEN, heat * (1.0f - u * 0.55f));
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(ang) * (rad + lean), Mth.sin(ang) * (rad + lean), y,
                            sz, sz * 1.3f, sz, rgb, opaque(a * dusk));
                }
            }
            // Auréole de brûlure : disque discontinu, il ne doit pas lire comme une ombre pleine.
            int marks = 38;
            for (int i = 0; i < marks; i++) {
                float h0 = hash(salt, 3400 + i * 41);
                if (h0 > 0.72f) continue;
                float ang = i * TAU / marks + h0;
                float rr = 0.45f + 2.35f * hash(salt, 3500 + i * 19);
                float sz = 0.085f + 0.075f * h0;
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(ang) * rr, Mth.sin(ang) * rr, -0.455f,
                        sz * 1.7f, sz * 0.30f, sz * 1.7f,
                        mix(SCORCH, GLASS, h0 * 0.55f), opaque(a * vitrify * dusk * 0.95f));
            }
        }

        // ---- 6. LA CHAÎNE : six relais successifs, chacun son tracé et son flash ----
        // Le serveur frappe exactement CHAIN_LINKS cibles ; le client dessine les mêmes relais
        // sur un anneau stable tiré de la graine, de sorte que le visuel et les dégâts racontent
        // la même histoire même si le client ignore la position réelle des victimes.
        float prevF = 0.0f;
        float prevL = 0.0f;
        float prevY = 0.55f;
        for (int k = 0; k < CHAIN_LINKS; k++) {
            float born = 0.30f + k * 0.072f;
            float age = (t - born) / 0.115f;
            float hk = hash(salt, 5000 + k * 149);
            float ang = hash(salt, 5100 + k * 73) * TAU;
            float rad = 2.45f + 3.55f * hk;
            float tf = Mth.cos(ang) * rad;
            float tl = Mth.sin(ang) * rad;
            float ty = 0.35f + 0.85f * hash(salt, 5200 + k * 29);
            if (age > 0.0f && age < 1.0f) {
                float draw = smooth(age * 2.2f);
                float keep = 1.0f - smooth((age - 0.45f) / 0.55f);
                drawBolt(vc, mat, tmp, cx, cy, cz, dx, dz,
                        prevF, prevL, prevY, tf, tl, ty,
                        salt * 7 + k * 877, 0.80f, draw, 0.55f + 0.45f * keep,
                        a * (0.55f + 0.45f * keep), 20, true);
                // Flash d'arrivée sur la victime : petite sphère + anneau au sol.
                if (age > 0.40f) {
                    float hit = smooth((age - 0.40f) / 0.22f) * (1.0f - smooth((age - 0.72f) / 0.28f));
                    for (int i = 0; i < 14; i++) {
                        float h0 = hash(salt, 5400 + k * 61 + i * 17);
                        float a2 = i * TAU / 14.0f + k;
                        float rr = (0.30f + 0.42f * hit) * (0.7f + 0.5f * h0);
                        float sz = 0.090f * hit;
                        if (sz <= 0.004f) continue;
                        box(vc, mat, tmp, cx, cy, cz, dx, dz,
                                tf + Mth.cos(a2) * rr, tl + Mth.sin(a2) * rr,
                                ty + (h0 - 0.5f) * 0.70f,
                                sz, sz, sz, mix(HOT, CORE, h0), opaque(a * hit));
                    }
                    int ring = 18;
                    for (int i = 0; i < ring; i++) {
                        float a2 = i * TAU / ring;
                        float rr = 0.95f * hit;
                        box(vc, mat, tmp, cx, cy, cz, dx, dz,
                                tf + Mth.cos(a2) * rr, tl + Mth.sin(a2) * rr, -0.40f,
                                0.075f, 0.030f, 0.075f, VIOLET, opaque(a * hit * 0.9f));
                    }
                }
            }
            // Le relais suivant part de la victime précédente : la chaîne se propage vraiment.
            if (t > born) { prevF = tf; prevL = tl; prevY = ty; }
        }

        // ---- 7. CRÉPITEMENT RÉSIDUEL : l'air reste ionisé bien après le coup ----
        if (t > 0.22f) {
            float fizz = dusk;
            for (int i = 0; i < 40; i++) {
                float h0 = hash(salt, 6000 + i * 83);
                float cyc = fract(t * (1.3f + 1.7f * h0) + h0);
                if (cyc > 0.34f) continue;                     // chaque étincelle ne vit qu'un éclat
                float a2 = hash(salt, 6100 + i * 31) * TAU;
                float rr = 0.50f + 4.30f * hash(salt, 6200 + i * 23);
                float yy = -0.30f + 2.20f * hash(salt, 6300 + i * 13);
                float sz = 0.055f * (1.0f - cyc / 0.34f) * fizz;
                if (sz <= 0.004f) continue;
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(a2) * rr, Mth.sin(a2) * rr, yy,
                        sz, sz * 1.8f, sz, mix(ARC, HOT, h0), opaque(a * fizz));
            }
        }
    }

    // =================================================================================
    //  II. SURCADENCE CYBER-X — 120 ticks
    // =================================================================================

    /**
     * Chronologie (120 ticks) :
     * <ul>
     *   <li>0,00–0,14 <b>amorçage</b> : quatre pylônes jaillissent du sol aux coins ;</li>
     *   <li>0,12–0,38 <b>construction</b> : les douze arêtes de la cage se tracent, puis les
     *       pistes de circuit imprimé apparaissent sur les faces ;</li>
     *   <li>0,38–0,78 <b>surcadence</b> : les paquets de données circulent sur les pistes, le X
     *       tourne, l'histogramme de cadence monte, les anneaux de scan défilent ;</li>
     *   <li>0,78–1,00 <b>décharge</b> : la cage implose dans le porteur et libère une onde.</li>
     * </ul>
     */
    private static void renderOverclock(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                        float cx, float cy, float cz, float t,
                                        float dx, float dz, int alpha, int salt) {
        int a = Math.round(alpha * smooth(t / 0.05f) * (1.0f - smooth((t - 0.95f) / 0.05f)));
        if (a <= 4) return;

        float boot = smooth(t / 0.14f);
        float build = smooth((t - 0.12f) / 0.26f);
        float run = smooth((t - 0.34f) / 0.10f);
        float burst = smooth((t - 0.78f) / 0.09f);
        float collapse = smooth((t - 0.80f) / 0.16f);
        float cage = (1.0f - collapse);
        // L'implosion rapproche tout de l'axe : c'est la géométrie qui éteint la cage.
        float shrink = 1.0f - 0.88f * collapse;

        // ---- 1. LES QUATRE PYLÔNES : l'amorçage, avant toute structure ----
        if (boot > 0.01f && cage > 0.02f) {
            for (int p = 0; p < 4; p++) {
                float ang = p * TAU / 4.0f + 0.7854f;
                float r = CAGE_HALF * 1.42f * shrink;
                float h = (0.30f + 2.50f * boot) * cage;
                int steps = 9;
                for (int s = 0; s < steps; s++) {
                    float u = s / (float) (steps - 1);
                    if (u > boot * 1.1f) break;
                    float y = -0.44f + h * u;
                    float sz = 0.105f - 0.045f * u;
                    int rgb = u > 0.80f ? CYAN : mix(DEEP, CYAN, u);
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(ang) * r, Mth.sin(ang) * r, y,
                            sz, sz * 1.15f, sz, rgb, opaque(a * cage));
                }
            }
        }

        // ---- 2. LA CAGE : douze arêtes tracées une par une ----
        if (build > 0.005f && cage > 0.02f) {
            float e = CAGE_HALF * shrink;
            float lo = -0.44f;
            float hi = -0.44f + 2.68f * shrink;
            for (int edge = 0; edge < 12; edge++) {
                float born = edge / 14.0f;                    // elles s'allument en séquence
                float g = Mth.clamp((build - born) * 5.0f, 0.0f, 1.0f);
                if (g <= 0.0f) continue;
                float f0, l0, y0, f1, l1, y1;
                if (edge < 4) {                                // 4 arêtes du bas
                    float a0 = edge * TAU / 4.0f + 0.7854f;
                    float a1 = (edge + 1) * TAU / 4.0f + 0.7854f;
                    f0 = Mth.cos(a0) * e; l0 = Mth.sin(a0) * e; y0 = lo;
                    f1 = Mth.cos(a1) * e; l1 = Mth.sin(a1) * e; y1 = lo;
                } else if (edge < 8) {                         // 4 arêtes du haut
                    int k = edge - 4;
                    float a0 = k * TAU / 4.0f + 0.7854f;
                    float a1 = (k + 1) * TAU / 4.0f + 0.7854f;
                    f0 = Mth.cos(a0) * e; l0 = Mth.sin(a0) * e; y0 = hi;
                    f1 = Mth.cos(a1) * e; l1 = Mth.sin(a1) * e; y1 = hi;
                } else {                                       // 4 montants
                    int k = edge - 8;
                    float a0 = k * TAU / 4.0f + 0.7854f;
                    f0 = Mth.cos(a0) * e; l0 = Mth.sin(a0) * e; y0 = lo;
                    f1 = f0; l1 = l0; y1 = hi;
                }
                neonEdge(vc, mat, tmp, cx, cy, cz, dx, dz, f0, l0, y0, f1, l1, y1,
                        g, 0.052f, CYAN, a * cage);
            }
        }

        // ---- 3. PISTES DE CIRCUIT IMPRIMÉ : tracé de Manhattan, avec pastilles ----
        // Une piste de PCB ne va jamais en diagonale : elle avance à angle droit et porte des
        // vias ronds à ses coudes. C'est ce détail qui fait lire « électronique » plutôt que
        // « magie » — et il n'existe nulle part ailleurs dans le mod.
        if (build > 0.22f && cage > 0.02f) {
            float trace = Mth.clamp((build - 0.22f) / 0.72f, 0.0f, 1.0f);
            float e = CAGE_HALF * shrink;
            float lo = -0.40f;
            float span = 2.60f * shrink;
            for (int face = 0; face < 4; face++) {
                float fang = face * TAU / 4.0f + 0.7854f;
                float nf = Mth.cos(fang);                      // normale de la face
                float nl = Mth.sin(fang);
                float tf = -nl;                                // tangente de la face
                float tl = nf;
                for (int tr = 0; tr < 3; tr++) {
                    int seed = salt + face * 971 + tr * 131;
                    float u = -0.62f + tr * 0.62f;             // position de départ sur la face
                    float y = lo + 0.30f + tr * 0.22f;
                    // Six segments alternant horizontal / vertical.
                    for (int s = 0; s < 6; s++) {
                        float g = Mth.clamp((trace - s / 7.0f) * 6.0f, 0.0f, 1.0f);
                        if (g <= 0.0f) break;
                        boolean horizontal = (s % 2) == 0;
                        float step = (0.26f + 0.34f * hash(seed, s * 17)) * (horizontal ? 1.0f : 0.85f);
                        if (!horizontal && hash(seed, s * 29) < 0.5f) step = -step;
                        float u2 = horizontal ? u + step : u;
                        float y2 = horizontal ? y : y + step;
                        u2 = Mth.clamp(u2, -0.92f, 0.92f);
                        y2 = Mth.clamp(y2, lo + 0.10f, lo + span);
                        float du = (u2 - u) * g;
                        float dy = (y2 - y) * g;
                        neonEdge(vc, mat, tmp, cx, cy, cz, dx, dz,
                                nf * e + tf * u, nl * e + tl * u, y,
                                nf * e + tf * (u + du), nl * e + tl * (u + du), y + dy,
                                1.0f, 0.030f, PCB, a * cage);
                        u += du;
                        y += dy;
                        // Pastille (via) au coude : un petit carré plus large et plus clair.
                        if (g >= 0.999f) {
                            box(vc, mat, tmp, cx, cy, cz, dx, dz,
                                    nf * e + tf * u, nl * e + tl * u, y,
                                    0.058f, 0.058f, 0.058f, mix(PCB, CYAN, 0.65f),
                                    opaque(a * cage));
                        }
                    }
                }
            }
        }

        // ---- 4. PAQUETS DE DONNÉES : ils COURENT sur les arêtes de la cage ----
        if (run > 0.01f && cage > 0.02f) {
            float e = CAGE_HALF * shrink;
            float lo = -0.44f;
            float hi = -0.44f + 2.68f * shrink;
            for (int p = 0; p < 26; p++) {
                float h0 = hash(salt, 7000 + p * 59);
                float speed = 0.55f + 1.35f * h0;
                float u = fract(t * speed + h0);
                int lane = (int) (hash(salt, 7100 + p * 23) * 8.0f) & 7;
                float f0, l0, y0, f1, l1, y1;
                if (lane < 4) {                                 // tour horizontal (haut ou bas)
                    boolean top = (lane & 1) == 0;
                    float ring = u * TAU;
                    f0 = Mth.cos(ring) * e; l0 = Mth.sin(ring) * e;
                    y0 = top ? hi : lo;
                    f1 = f0; l1 = l0; y1 = y0;
                } else {                                        // montée le long d'un montant
                    float ang = (lane - 4) * TAU / 4.0f + 0.7854f;
                    f0 = Mth.cos(ang) * e; l0 = Mth.sin(ang) * e;
                    y0 = lo + (hi - lo) * u;
                    f1 = f0; l1 = l0; y1 = y0;
                }
                float sz = 0.070f + 0.040f * h0;
                int rgb = h0 > 0.66f ? MAGENTA : (h0 > 0.33f ? CYAN : HOT);
                box(vc, mat, tmp, cx, cy, cz, dx, dz, f0, l0, y0,
                        sz, sz, sz, rgb, opaque(a * run * cage));
                // Traînée : trois copies qui rétrécissent derrière le paquet.
                for (int q = 1; q <= 3; q++) {
                    float back = u - q * 0.022f;
                    if (back < 0.0f) break;
                    float bf, bl, by;
                    if (lane < 4) {
                        float ring = back * TAU;
                        bf = Mth.cos(ring) * e; bl = Mth.sin(ring) * e; by = y0;
                    } else {
                        bf = f0; bl = l0; by = lo + (hi - lo) * back;
                    }
                    float bs = sz * (1.0f - q * 0.26f);
                    box(vc, mat, tmp, cx, cy, cz, dx, dz, bf, bl, by,
                            bs, bs, bs, rgb, opaque(a * run * cage * 0.9f));
                }
            }
        }

        // ---- 5. LE X : la marque de l'effet, deux barres croisées qui tournent ----
        {
            float spin = t * 5.4f;
            float arm = (0.95f + 0.55f * run) * shrink * (0.35f + 0.65f * boot);
            float yMid = -0.44f + 1.34f * shrink;
            for (int bar = 0; bar < 2; bar++) {
                float tilt = bar == 0 ? 0.68f : -0.68f;
                int segs = 15;
                for (int s = 0; s < segs; s++) {
                    float u = s / (float) (segs - 1) * 2.0f - 1.0f;   // -1 .. +1
                    float f = Mth.cos(spin) * arm * u;
                    float l = Mth.sin(spin) * arm * u;
                    float y = yMid + tilt * arm * u;
                    float sz = 0.085f * (1.0f - 0.45f * Math.abs(u));
                    int rgb = Math.abs(u) < 0.22f ? CORE : mix(CYAN, MAGENTA, (u + 1.0f) * 0.5f);
                    box(vc, mat, tmp, cx, cy, cz, dx, dz, f, l, y,
                            sz, sz, sz, rgb, opaque(a * (1.0f - 0.5f * collapse)));
                }
            }
        }

        // ---- 6. HISTOGRAMME DE CADENCE : douze barres qui montent, comme un compteur ----
        if (run > 0.02f && cage > 0.02f) {
            for (int b = 0; b < 12; b++) {
                float hb = hash(salt, 7600 + b * 37);
                float phase = fract(t * 1.25f + b / 12.0f);
                float lvl = (0.25f + 0.75f * hb) * (0.35f + 0.65f * Mth.sin(phase * TAU) * 0.5f + 0.325f);
                float ang = b * TAU / 12.0f;
                float r = CAGE_HALF * 1.72f * shrink;
                int cells = Math.max(1, Math.round(lvl * 7.0f * run));
                for (int c = 0; c < cells; c++) {
                    float y = -0.42f + c * 0.19f;
                    int rgb = c >= 5 ? MAGENTA : (c >= 3 ? HOT : PCB);
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(ang) * r, Mth.sin(ang) * r, y,
                            0.072f, 0.070f, 0.072f, rgb, opaque(a * run * cage));
                }
            }
        }

        // ---- 7. ANNEAUX DE SCAN : trois cercles qui défilent de bas en haut ----
        if (run > 0.02f && cage > 0.02f) {
            for (int s = 0; s < 3; s++) {
                float u = fract(t * 0.95f + s / 3.0f);
                float y = -0.44f + 2.68f * u * shrink;
                float r = CAGE_HALF * (1.05f + 0.22f * Mth.sin(u * TAU)) * shrink;
                int count = Math.max(14, Math.round(r * 16.0f));
                for (int i = 0; i < count; i++) {
                    float ang = i * TAU / count + t * 1.7f;
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(ang) * r, Mth.sin(ang) * r, y,
                            0.052f, 0.030f, 0.052f, mix(CYAN, CORE, 0.35f),
                            opaque(a * run * cage * 0.95f));
                }
            }
        }

        // ---- 8. DÉCHARGE : la cage implose, une onde part au sol ----
        if (burst > 0.01f) {
            float r = 0.40f + 4.60f * burst;
            float thin = 1.0f - 0.62f * burst;
            int count = Math.max(18, Math.round(r * 14.0f));
            for (int i = 0; i < count; i++) {
                float h0 = hash(salt, 8100 + i * 43);
                float ang = i * TAU / count;
                float rr = r * (0.93f + 0.10f * Mth.sin(ang * 6.0f + t * 12.0f));
                float sz = (0.125f + 0.055f * h0) * thin;
                if (sz <= 0.004f) continue;
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(ang) * rr, Mth.sin(ang) * rr, -0.40f + 0.07f * h0,
                        sz * 1.4f, sz * 0.55f, sz * 1.4f,
                        mix(CYAN, MAGENTA, h0 * 0.6f), opaque(a * (1.0f - 0.3f * burst)));
            }
            // Colonne de retour : l'énergie remonte dans le porteur.
            int col = Math.round(22 * (1.0f - burst));
            for (int i = 0; i < col; i++) {
                float h0 = hash(salt, 8300 + i * 29);
                float y = -0.40f + 3.10f * fract(h0 + t * 2.1f);
                float rr = 0.28f * (1.0f - y / 3.2f);
                float ang = h0 * TAU + t * 6.0f;
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(ang) * rr, Mth.sin(ang) * rr, y,
                        0.070f, 0.095f, 0.070f, mix(HOT, CORE, h0), opaque(a * burst));
            }
        }
    }

    // =================================================================================
    //  III. DOUBLE ENTAILLE D'ÉCHO — 96 ticks
    // =================================================================================

    /**
     * Chronologie (96 ticks) :
     * <ul>
     *   <li>0,00–0,16 <b>première entaille</b> : un ruban de foudre balaie sur 210° ;</li>
     *   <li>0,16–0,34 <b>rémanence</b> : quatre fantômes de la lame rétrécissent sur place —
     *       ils s'effacent par la taille, jamais par l'alpha ;</li>
     *   <li>0,30–0,48 <b>la jumelle</b> repasse en miroir sur un plan incliné à 90° ;</li>
     *   <li>0,46–0,74 <b>court-circuit</b> : au croisement des deux plans, une gerbe d'arcs ;</li>
     *   <li>0,74–1,00 <b>dissipation</b> : les étincelles retombent en suivant une balistique.</li>
     * </ul>
     */
    private static void renderEcho(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                   float cx, float cy, float cz, float t,
                                   float dx, float dz, int alpha, int salt) {
        int a = Math.round(alpha * smooth(t / 0.04f) * (1.0f - smooth((t - 0.92f) / 0.08f)));
        if (a <= 4) return;

        float cut1 = smooth(t / 0.16f);
        float cut2 = smooth((t - 0.30f) / 0.18f);
        float cross = smooth((t - 0.46f) / 0.12f) * (1.0f - smooth((t - 0.74f) / 0.26f));
        float out = smooth((t - 0.74f) / 0.26f);

        // ---- 1 & 3. LES DEUX LAMES ----
        // La seconde est inclinée à 90° et tourne en sens inverse : vues de face elles tracent
        // un X dans l'espace. C'est le « double » du nom, rendu lisible géométriquement.
        drawBlade(vc, mat, tmp, cx, cy, cz, dx, dz, cut1, 0.0f, 0.0f, +1.0f,
                1.0f - 0.55f * smooth((t - 0.20f) / 0.30f), a, salt, 0);
        if (cut2 > 0.0f) {
            drawBlade(vc, mat, tmp, cx, cy, cz, dx, dz, cut2, 1.5708f, 1.0f, -1.0f,
                    1.08f * (1.0f - 0.45f * smooth((t - 0.52f) / 0.34f)), a, salt, 1);
        }

        // ---- 2. RÉMANENCES : quatre copies figées de la première lame ----
        if (t > 0.14f && t < 0.46f) {
            float ghost = 1.0f - smooth((t - 0.16f) / 0.28f);
            for (int g = 1; g <= 4; g++) {
                float back = g * 0.055f;
                float gt = Mth.clamp(cut1 - back * 2.2f, 0.0f, 1.0f);
                if (gt <= 0.02f) continue;
                drawBlade(vc, mat, tmp, cx, cy, cz, dx, dz, gt, 0.0f, 0.0f, +1.0f,
                        ghost * (0.74f - 0.15f * g), a, salt, 10 + g);
            }
        }

        // ---- 4. COURT-CIRCUIT : la gerbe d'arcs au croisement des deux plans ----
        if (cross > 0.01f) {
            for (int s = 0; s < 10; s++) {
                float hs = hash(salt, 9000 + s * 71);
                float cyc = fract(t * 2.6f + hs);
                if (cyc > 0.42f) continue;
                float live = 1.0f - cyc / 0.42f;
                float a0 = hash(salt, 9100 + s * 37) * TAU;
                float a1 = a0 + 1.4f + hash(salt, 9200 + s * 19) * 2.2f;
                float r0 = ECHO_RADIUS * (0.35f + 0.55f * hash(salt, 9300 + s * 23));
                float r1 = ECHO_RADIUS * (0.35f + 0.55f * hash(salt, 9400 + s * 29));
                float y0 = 0.25f + 1.35f * hash(salt, 9500 + s * 13);
                float y1 = 0.25f + 1.35f * hash(salt, 9600 + s * 17);
                drawBolt(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(a0) * r0, Mth.sin(a0) * r0, y0,
                        Mth.cos(a1) * r1, Mth.sin(a1) * r1, y1,
                        salt * 23 + s * 457, 0.42f, 1.0f, live,
                        a * cross * live, 10, true);
            }
            // Noyau du croisement : une petite étoile surexposée au centre géométrique.
            float pulse = 0.6f + 0.4f * Mth.sin(t * 46.0f);
            for (int i = 0; i < 16; i++) {
                float h0 = hash(salt, 9800 + i * 31);
                float ang = i * TAU / 16.0f;
                float rr = (0.22f + 0.30f * h0) * pulse * cross;
                float sz = 0.085f * cross;
                if (sz <= 0.004f) continue;
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(ang) * rr, Mth.sin(ang) * rr, 0.95f + (h0 - 0.5f) * 0.55f,
                        sz, sz, sz, mix(CORE, HOT, h0 * 0.7f), opaque(a * cross));
            }
        }

        // ---- 5. DISSIPATION : des étincelles balistiques, elles RETOMBENT ----
        if (out > 0.01f) {
            for (int i = 0; i < 46; i++) {
                float h0 = hash(salt, 10200 + i * 53);
                float h1 = hash(salt, 10300 + i * 29);
                float age = out * (0.65f + 0.70f * h0);
                if (age >= 1.0f) continue;
                float ang = h1 * TAU;
                float sp = 1.35f + 2.45f * h0;
                float r = sp * age;
                // Parabole : vitesse initiale vers le haut, gravité constante.
                float y = 1.05f + (1.95f * h1) * age - 5.2f * age * age;
                if (y < -0.44f) continue;
                float sz = (0.075f - 0.035f * age) * (1.0f - out * 0.4f);
                if (sz <= 0.004f) continue;
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(ang) * r, Mth.sin(ang) * r, y,
                        sz, sz * 1.7f, sz, mix(ARC, HOT, h0), opaque(a * (1.0f - age * 0.5f)));
            }
        }
    }

    /**
     * Une lame d'écho : ruban en arc de 210°, épais au cœur et affûté aux bords, dont la pointe
     * porte un liseré surexposé. {@code plane} incline le plan de coupe, {@code dir} en inverse
     * le sens, {@code scale} pilote sa taille (c'est par là que la rémanence s'efface).
     */
    private static void drawBlade(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                  float cx, float cy, float cz, float dx, float dz,
                                  float sweep, float phase, float tilt, float dir, float scale,
                                  int alpha, int salt, int variant) {
        if (sweep <= 0.0f || scale <= 0.02f) return;
        final float arc = 3.665f;                               // 210°
        final int segments = 44;
        float start = -arc * 0.5f + phase;
        float reach = arc * sweep;
        // `tilt` fait basculer le plan de coupe : 0 = lame à plat, 1 = lame verticale. La
        // coordonnée latérale est versée dans la hauteur à due proportion, si bien que les
        // deux entailles décrivent un X dans l'espace plutôt que deux cercles superposés.
        float flat = 1.0f - tilt;
        for (int s = 0; s < segments; s++) {
            float u = s / (float) (segments - 1);
            if (u > sweep) break;
            float ang = start + dir * reach * u;
            // Le ruban est plus large au milieu de la course : la lame « gonfle » à l'impact.
            float belly = Mth.sin(Mth.clamp(u / Math.max(0.05f, sweep), 0.0f, 1.0f) * 3.14159f);
            float r = ECHO_RADIUS * scale * (0.72f + 0.34f * belly);
            float lat = Mth.sin(ang) * r;
            float f = Mth.cos(ang) * r;
            float l = lat * flat;
            float y = 0.95f + lat * tilt * 0.92f;
            // Épaisseur : trois passes concentriques, du cœur blanc à la gaine violette.
            for (int layer = 0; layer < 3; layer++) {
                float lv = layer / 2.0f;
                float hw = (0.055f + 0.145f * lv) * scale * (0.55f + 0.65f * belly);
                float hh = (0.230f - 0.090f * lv) * scale * (0.50f + 0.70f * belly);
                int rgb = layer == 0 ? CORE : (layer == 1 ? mix(NEON, HOT, 0.35f) : VIOLET);
                float fade = layer == 0 ? 1.0f : (layer == 1 ? 0.95f : 0.88f);
                if (hw <= 0.004f || hh <= 0.004f) continue;
                rot(vc, mat, tmp, cx, cy, cz, dx, dz, f, l, y,
                        hw, hh, hw * 1.65f,
                        -ang, tilt * 1.5708f, 0.0f, rgb, opaque(alpha * fade));
            }
            // Liseré de pointe : la dernière section porte une étincelle plus grosse.
            if (s == segments - 1 || u + 1.0f / segments > sweep) {
                float sz = 0.145f * scale;
                box(vc, mat, tmp, cx, cy, cz, dx, dz, f, l, y,
                        sz, sz, sz, CORE, opaque(alpha));
            }
            // Retombée d'étincelles sous la coupe, une section sur quatre.
            if ((s & 3) == 0 && variant < 10) {
                float hsp = hash(salt, 11000 + variant * 311 + s * 17);
                float drop = fract(hsp + sweep * 1.6f);
                float sz = 0.052f * scale * (1.0f - drop);
                if (sz > 0.004f) {
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            f * (1.0f + 0.06f * hsp), l * (1.0f + 0.06f * hsp),
                            y - drop * 1.25f,
                            sz, sz * 1.6f, sz, mix(ARC, NEON, hsp), opaque(alpha * 0.92f));
                }
            }
        }
    }

    // =================================================================================
    //  Tracé d'éclair : la primitive commune aux trois signatures
    // =================================================================================

    /**
     * Trace une polyligne fractale entre deux points du repère local, en deux passes (gaine puis
     * cœur). C'est LA primitive de la famille : tout ce qui est « électrique » dans ce fichier en
     * sort, du trait maître de 22 blocs aux serpents de surface de 2 blocs.
     *
     * @param amp     amplitude du déplacement fractal, en blocs
     * @param reveal  fraction du trajet déjà tracée (0 → 1), pour la descente progressive
     * @param thick   facteur d'épaisseur ; la disparition passe par là, jamais par l'alpha
     * @param nodes   nombre de nœuds de la polyligne
     * @param sheath  dessiner la gaine large en plus du cœur
     */
    private static void drawBolt(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                 float cx, float cy, float cz, float dx, float dz,
                                 float f0, float l0, float y0,
                                 float f1, float l1, float y1,
                                 int seed, float amp, float reveal, float thick,
                                 float alpha, int nodes, boolean sheath) {
        if (reveal <= 0.0f || thick <= 0.02f || alpha <= 1.0f) return;
        float pf = f0, pl = l0, py = y0;
        for (int n = 1; n <= nodes; n++) {
            float u = n / (float) nodes;
            if (u > reveal) {
                // Dernier tronçon partiel : le trait s'arrête net au front de propagation,
                // il ne « s'évanouit » pas. Un éclair en cours de descente a un bout franc.
                u = reveal;
            }
            float base = u;
            float ef = f0 + (f1 - f0) * base;
            float el = l0 + (l1 - l0) * base;
            float ey = y0 + (y1 - y0) * base;
            // Le déplacement s'annule aux deux extrémités : le trait part et arrive juste.
            float taper = Mth.sin(base * 3.14159f);
            float off1 = boltOffset(seed, base, amp) * taper;
            float off2 = boltOffset(seed + 5000, base, amp) * taper;
            // Repère perpendiculaire au segment : le bruit doit déplacer LATÉRALEMENT.
            float sf = f1 - f0, sl = l1 - l0, sy = y1 - y0;
            float hlen = Mth.sqrt(sf * sf + sl * sl);
            float nf, nl;
            if (hlen < 0.001f) { nf = 1.0f; nl = 0.0f; }
            else { nf = -sl / hlen; nl = sf / hlen; }
            float cf = ef + nf * off1;
            float cl = el + nl * off1;
            float cyy = ey + off2 * (hlen > Math.abs(sy) ? 0.42f : 1.0f);
            // Gaine violette large, halo, puis cœur blanc : trois passes concentriques. C'est
            // ce triple tracé qui donne au canal sa surexposition, impossible à obtenir avec
            // un seul cube par segment.
            if (sheath) {
                seg(vc, mat, tmp, cx, cy, cz, dx, dz, pf, pl, py, cf, cl, cyy,
                        BOLT_CORE * 2.45f * thick, mix(VIOLET, ARC, 0.5f), opaque(alpha * 0.80f));
                seg(vc, mat, tmp, cx, cy, cz, dx, dz, pf, pl, py, cf, cl, cyy,
                        BOLT_CORE * 1.55f * thick, NEON, opaque(alpha * 0.92f));
            }
            seg(vc, mat, tmp, cx, cy, cz, dx, dz, pf, pl, py, cf, cl, cyy,
                    BOLT_CORE * (sheath ? 1.0f : 0.80f) * thick,
                    sheath ? CORE : mix(NEON, CORE, 0.55f), opaque(alpha));
            pf = cf; pl = cl; py = cyy;
            if (u >= reveal) break;
        }
    }

    /**
     * Déplacement fractal : somme de cinq octaves de bruit haché à interpolation <b>linéaire</b>.
     * L'interpolation lisse ({@code smooth}) donnerait une ondulation ; c'est l'interpolation
     * linéaire qui produit les cassures nettes caractéristiques d'une décharge.
     */
    private static float boltOffset(int seed, float u, float amp) {
        float sum = 0.0f;
        float gain = amp;
        int cells = 3;
        for (int octave = 0; octave < 5; octave++) {
            float x = u * cells;
            int i = (int) Math.floor(x);
            float frac = x - i;
            float h0 = hash(seed + octave * 977, i) * 2.0f - 1.0f;
            float h1 = hash(seed + octave * 977, i + 1) * 2.0f - 1.0f;
            sum += (h0 + (h1 - h0) * frac) * gain;
            gain *= 0.50f;
            cells *= 2;
        }
        return sum;
    }

    /**
     * Segment orienté entre deux points du repère local. L'axe X du cube suit la direction
     * {@code (cos roll · cos yaw, sin roll, −cos roll · sin yaw)} : c'est la convention de
     * {@code emitVoxelBoxRotated}, d'où le {@code yaw = atan2(−dz, dx)} et le roulis en guise
     * de tangage.
     */
    private static void seg(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                            float cx, float cy, float cz, float dx, float dz,
                            float f0, float l0, float y0, float f1, float l1, float y1,
                            float thick, int rgb, int alpha) {
        if (alpha <= 0 || thick <= 0.0005f) return;
        float x0 = cx + dx * f0 - dz * l0;
        float z0 = cz + dz * f0 + dx * l0;
        float x1 = cx + dx * f1 - dz * l1;
        float z1 = cz + dz * f1 + dx * l1;
        float vx = x1 - x0, vy = y1 - y0, vz = z1 - z0;
        float len = Mth.sqrt(vx * vx + vy * vy + vz * vz);
        if (len < 0.0008f) return;
        float horiz = Mth.sqrt(vx * vx + vz * vz);
        float yaw = (float) Math.atan2(-vz, vx);
        float roll = (float) Math.atan2(vy, horiz);
        WeaponWorldVfxManager.emitVoxelBoxRotated(vc, mat, tmp,
                (x0 + x1) * 0.5f, cy + (y0 + y1) * 0.5f, (z0 + z1) * 0.5f,
                len * 0.5f, thick, thick, yaw, 0.0f, roll, rgb, alpha);
    }

    /** Arête de néon : un segment droit, révélé progressivement depuis son origine. */
    private static void neonEdge(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                 float cx, float cy, float cz, float dx, float dz,
                                 float f0, float l0, float y0, float f1, float l1, float y1,
                                 float grow, float thick, int rgb, float alpha) {
        if (grow <= 0.0f || alpha <= 1.0f) return;
        float ef = f0 + (f1 - f0) * grow;
        float el = l0 + (l1 - l0) * grow;
        float ey = y0 + (y1 - y0) * grow;
        seg(vc, mat, tmp, cx, cy, cz, dx, dz, f0, l0, y0, ef, el, ey,
                thick * 1.9f, mix(rgb, DEEP, 0.45f), opaque(alpha * 0.82f));
        seg(vc, mat, tmp, cx, cy, cz, dx, dz, f0, l0, y0, ef, el, ey,
                thick, rgb, opaque(alpha));
    }

    // =================================================================================
    //  Utilitaires
    // =================================================================================

    /**
     * Relève l'alpha au-dessus du seuil du tamis, ou n'émet rien. Règle de toute la famille :
     * on ne fait jamais disparaître un élément par fondu d'alpha — il serait décimé en
     * pointillés — mais par la géométrie.
     */
    private static int opaque(float v) {
        int i = Math.round(v);
        if (i <= 10) return 0;
        return i < SOLID_FLOOR ? SOLID_FLOOR : Math.min(255, i);
    }

    /** Cube aligné : {@code f} = axe attaquant→cible, {@code lateral} = perpendiculaire. */
    private static void box(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                            float cx, float cy, float cz, float dx, float dz,
                            float f, float lateral, float y,
                            float hf, float hy, float hl, int rgb, int alpha) {
        if (alpha <= 0 || hf <= 0.0f || hy <= 0.0f || hl <= 0.0f) return;
        float x = cx + dx * f - dz * lateral;
        float z = cz + dz * f + dx * lateral;
        WeaponWorldVfxManager.emitVoxelCube(vc, mat, tmp, x, cy + y, z, hf, hy, hl, rgb, alpha);
    }

    /** Cube orienté (lacet, tangage, roulis) dans le même repère que {@link #box}. */
    private static void rot(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                            float cx, float cy, float cz, float dx, float dz,
                            float f, float lateral, float y,
                            float hf, float hy, float hl,
                            float yaw, float pitch, float roll, int rgb, int alpha) {
        if (alpha <= 0 || hf <= 0.0f || hy <= 0.0f || hl <= 0.0f) return;
        float x = cx + dx * f - dz * lateral;
        float z = cz + dz * f + dx * lateral;
        WeaponWorldVfxManager.emitVoxelBoxRotated(vc, mat, tmp, x, cy + y, z,
                hf, hy, hl, yaw, pitch, roll, rgb, alpha);
    }

    private static int mix(int dark, int light, float u) {
        return WeaponWorldVfxManager.mixRgb(dark, light, clamp(u));
    }

    private static float clamp(float value) { return Mth.clamp(value, 0.0f, 1.0f); }

    private static float fract(float x) {
        return x - (float) Math.floor(x);
    }

    private static float smooth(float x) {
        x = clamp(x);
        return x * x * (3.0f - 2.0f * x);
    }

    private static float hash(int seed, int n) {
        int h = seed ^ (n * 0x45D9F3B);
        h ^= h >>> 16;
        h *= 0x45D9F3B;
        h ^= h >>> 16;
        return (h & 0xFFFF) / 65535.0f;
    }
}
