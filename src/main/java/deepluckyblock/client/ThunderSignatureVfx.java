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
 *   <li>{@link #ECHO} — <b>Appel de l'orage</b> ({@code STORMCALL_JUDGMENT}, 170 ticks) : une
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
    /** Appel de l'orage — {@code STORMCALL_JUDGMENT}. L'indice reste 2, seul le contenu change. */
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
    // Sol frappé.
    private static final int GLASS = 0x7C68A0;     // fulgurite : sable vitrifié
    private static final int MOLTEN = 0xFFB347;    // verre encore chaud
    private static final int SCORCH = 0x2A2036;    // brûlure

    private static final float TAU = 6.2831855f;

    /** Hauteur de chute du trait maître. Au-delà, le trait sort du champ de vision courant. */
    private static final float SKY_HEIGHT = 22.0f;
    /**
     * Demi-épaisseur du cœur d'un trait principal. <b>0,040 et non 0,075</b> : à l'ancienne
     * valeur, la gaine d'un trait mesurait 0,37 bloc de large pour des segments de 0,14 à 0,39
     * bloc de long — chaque segment était donc plus large que long et la décharge se lisait
     * comme un chapelet de cubes. Un trait n'est perçu comme un trait qu'à partir d'un rapport
     * longueur/épaisseur d'environ 3 ; tous les appels de {@link #drawBolt} de ce fichier sont
     * dimensionnés pour le respecter.
     */
    private static final float BOLT_CORE = 0.040f;

    /**
     * Niveau du sol dans le repère local. Rien n'est émis en dessous : une lame verticale
     * centrée à hauteur de poitrine plongeait la moitié de son cercle sous terre, ce qui
     * gaspillait des cubes et donnait une coupe amputée.
     */
    private static final float GROUND_Y = -0.42f;
    /**
     * Pas de la grille du pixel art de la Surcadence. Un cube vaut un pixel, jamais un demi :
     * c'est cette contrainte, et le refus de toute rotation, qui produit le style 8 bits.
     */
    private static final float PIX = 0.1875f;

    /**
     * Palette fermée façon Game Boy Color. Sept teintes, choisies <b>par index</b> : aucune
     * interpolation nulle part, les dégradés se font au tramage en damier.
     */
    private static final int[] GB = {
            0x11224A,   // 0 — abysse
            0x1E63C8,   // 1 — bleu profond
            0x2BE8FF,   // 2 — cyan
            0x7DFF3B,   // 3 — vert console
            0xFFE94A,   // 4 — jaune
            0xFFFFFF,   // 5 — blanc
            0xFF3BD4,   // 6 — magenta
    };

    /** Les trois poses du sprite d'éclair. '#' = teinte vive, 'o' = ombre, '.' = vide. */
    private static final String[][] BOLT_SPRITE = {
            { "...##", "..##o", ".##o.", "####o", "..##o", ".##o.", "##o..", "#o..." },
            { "..##.", ".##o.", "###o.", ".###o", "..##o", ".##o.", "##o..", ".o..." },
            { "..##.", ".###o", "##o..", "####o", "..##o", "..#o.", ".##..", "o...." },
    };

    /**
     * Teinte du cumulonimbus. {@code SCORCH} servait au départ, mais c'est une couleur de
     * <i>brûlure</i> : à 8 blocs de haut et sur 7 blocs de large, le nuage disparaissait dans
     * le ciel. Un gris-violet franc le détache sans le rendre lumineux — un nuage d'orage reste
     * sombre, c'est son ventre qui s'allume.
     */
    private static final int CLOUD_DARK = 0x3B3457;
    private static final int CLOUD_LIT = 0x6A5CA8;

    /** Hauteur du cumulonimbus de l'Appel de l'orage, au-dessus de la cible. */
    private static final float CLOUD_Y = 8.0f;

    /** Rayon du cumulonimbus, et nombre d'éclairs de mitraille avant la décharge. */
    private static final float CLOUD_R = 3.40f;
    private static final int STRIKES = 5;

    /** Rayon de la couronne de plasma au sol, à pleine extension. */
    private static final float CROWN_RADIUS = 5.10f;
    /** Nombre de relais de la chaîne — le serveur en frappe autant (voir WeaponVfxServerListener). */
    private static final int CHAIN_LINKS = 6;
    /** Demi-arête de la cage de surcadence. */
    private static final float CAGE_HALF = 1.32f;

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
        else if (variant == ECHO) renderStormcall(vc, mat, tmp, cx, cy, cz, t, dx, dz, alpha, salt);
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
                        salt * 31 + 7, 1.55f, reveal, thick, a * persist, 44, true);
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
                    // La boule de surexposition était une sphère pleine centrée à 0,30 pour un
                    // rayon vertical de 1,4 : la moitié basse était enterrée, donc perdue. Le
                    // sol la tronque — ce qu'on voit d'un vrai impact est une coupole, pas une bille.
                    float fy = 0.30f + yy;
                    if (fy < GROUND_Y) continue;
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(ang) * pr, Mth.sin(ang) * pr, fy,
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
                        a * (0.55f + 0.45f * keep), 12, true);
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
    //  II. SURCADENCE CYBER-X — 120 ticks, en pixel art 8 bits
    // =================================================================================

    /**
     * Refonte complète en <b>pixel art 8 bits</b>. Les règles du genre sont contraignantes, et
     * c'est précisément leur respect qui produit le style — elles sont toutes appliquées ici :
     * <ul>
     *   <li><b>grille</b> : chaque cube est aligné sur un pas fixe {@link #PIX} et tous ont
     *       exactement la même taille. Un cube vaut un pixel, jamais un demi ;</li>
     *   <li><b>aucune rotation</b> : un sprite 8 bits ne tourne pas, il change de pose. Tout
     *       passe par {@link #box}, jamais par {@link #rot} ;</li>
     *   <li><b>palette fermée</b> de sept teintes choisies par index, sans la moindre
     *       interpolation — les dégradés se font au <b>tramage</b> en damier ;</li>
     *   <li><b>horloge à paliers</b> : l'animation avance par images fixes, à 18 images pour
     *       les 120 ticks. Rien ne glisse, tout tressaute ;</li>
     *   <li><b>tracés entiers</b> : les cercles sont rasterisés au Bresenham, comme sur une
     *       console sans virgule flottante.</li>
     * </ul>
     *
     * Chronologie :
     * <ul>
     *   <li>0,00–0,18 <b>allumage</b> : le cadre se trace pixel par pixel, en balayage ;</li>
     *   <li>0,12–0,82 <b>sprites</b> : quatre éclairs alternent sur trois poses ;</li>
     *   <li>0,15–0,80 <b>jauge de surcharge</b> : une barre se remplit case par case ;</li>
     *   <li>0,25–0,95 <b>anneau</b> : un cercle de Bresenham s'élargit d'un pixel à la fois ;</li>
     *   <li>0,80–1,00 <b>décharge</b> : la jauge se vide, la colonne jaillit, le sol se trame.</li>
     * </ul>
     */
    private static void renderOverclock(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                        float cx, float cy, float cz, float t,
                                        float dx, float dz, int alpha, int salt) {
        int a = Math.round(alpha * (1.0f - smooth((t - 0.94f) / 0.06f)));
        if (a <= 4) return;

        // Horloge à paliers. Tout ce qui bouge se recale dessus : c'est ce qui donne le
        // tressautement caractéristique, impossible à obtenir en interpolant.
        final int FRAMES = 18;
        final int fr = (int) (t * FRAMES);
        final float tq = fr / (float) FRAMES;

        float boot  = smooth(tq / 0.18f);
        float live  = smooth((tq - 0.12f) / 0.10f);
        float burst = smooth((tq - 0.80f) / 0.08f);
        float fade  = 1.0f - smooth((tq - 0.92f) / 0.08f);
        float floorY = GROUND_Y + PIX * 0.5f;

        // ---- 1. LE CADRE : il se trace en balayage, un pixel après l'autre ----
        {
            final int side = 11;
            float half = side * 0.5f * PIX;
            int total = side * 4 - 4;
            int drawn = (int) (total * boot);
            for (int i = 0; i < drawn; i++) {
                int gx, gz;
                if (i < side)                  { gx = i;                 gz = 0; }
                else if (i < side * 2 - 1)     { gx = side - 1;          gz = i - side + 1; }
                else if (i < side * 3 - 2)     { gx = side * 3 - 3 - i;  gz = side - 1; }
                else                           { gx = 0;                 gz = side * 4 - 4 - i; }
                float f = -half + (gx + 0.5f) * PIX;
                float l = -half + (gz + 0.5f) * PIX;
                int tone = ((gx + gz + fr) & 3) == 0 ? 5 : 2;
                pix(vc, mat, tmp, cx, cy, cz, dx, dz, f, l, floorY, GB[tone], a);
            }
            // Les quatre coins restent allumés une fois le cadre bouclé.
            if (boot > 0.98f) {
                for (int c = 0; c < 4; c++) {
                    float f = ((c & 1) == 0 ? -1.0f : 1.0f) * (half - PIX * 0.5f);
                    float l = ((c & 2) == 0 ? -1.0f : 1.0f) * (half - PIX * 0.5f);
                    pix(vc, mat, tmp, cx, cy, cz, dx, dz, f, l, floorY + PIX, GB[4], a);
                }
            }
        }

        // ---- 2. QUATRE SPRITES D'ÉCLAIR : ils changent de pose, ils ne pivotent jamais ----
        if (live > 0.01f && burst < 0.55f) {
            float reach = 1.60f;
            for (int q = 0; q < 4; q++) {
                int pose = (fr + q * 2) % BOLT_SPRITE.length;
                boolean sideways = (q & 1) == 1;
                float f = q == 0 ? reach : (q == 2 ? -reach : 0.0f);
                float l = q == 1 ? reach : (q == 3 ? -reach : 0.0f);
                // Montée d'un demi-pixel toutes les deux images : un tressautement franc.
                float lift = ((fr + q) & 3) * PIX * 0.5f;
                int main = ((fr + q) & 1) == 0 ? GB[4] : GB[2];
                sprite(vc, mat, tmp, cx, cy, cz, dx, dz, BOLT_SPRITE[pose],
                        f, l, GROUND_Y + PIX * 5.5f + lift, sideways,
                        main, GB[1], Math.round(a * live * (1.0f - burst)));
            }
        }

        // ---- 3. LA JAUGE DE SURCHARGE : elle se remplit case par case ----
        if (boot > 0.55f) {
            final int cells = 12;
            float fill = burst > 0.02f ? (1.0f - burst) : clamp((tq - 0.15f) / 0.65f);
            int on = Math.round(cells * fill);
            float y = GROUND_Y + PIX * 13.0f;
            float x0 = -cells * 0.5f * PIX;
            for (int i = 0; i < cells; i++) {
                boolean lit = i < on;
                int tone = !lit ? 0 : (i >= cells - 3 ? 6 : (i >= cells - 6 ? 4 : 3));
                // Une case qui vient de s'allumer clignote une image sur deux.
                if (lit && i == on - 1 && (fr & 1) == 0) tone = 5;
                pix(vc, mat, tmp, cx, cy, cz, dx, dz,
                        x0 + (i + 0.5f) * PIX, 0.0f, y, GB[tone], a);
            }
            pix(vc, mat, tmp, cx, cy, cz, dx, dz, x0 - PIX * 0.5f, 0.0f, y, GB[1], a);
            pix(vc, mat, tmp, cx, cy, cz, dx, dz, x0 + (cells + 0.5f) * PIX, 0.0f, y, GB[1], a);
        }

        // ---- 4. L'ANNEAU : un cercle de Bresenham qui gagne un pixel à la fois ----
        if (tq > 0.25f) {
            float grow = clamp((tq - 0.25f) / 0.70f);
            int rad = 4 + (int) (grow * 7.0f) + (int) (burst * 9.0f);
            pixelCircle(vc, mat, tmp, cx, cy, cz, dx, dz, floorY, rad,
                    burst > 0.1f ? GB[5] : GB[2], GB[3], fr, Math.round(a * fade));
            if (rad > 6) {
                pixelCircle(vc, mat, tmp, cx, cy, cz, dx, dz, floorY, rad - 3,
                        GB[1], GB[1], fr + 1, Math.round(a * fade * 0.8f));
            }
        }

        // ---- 5. LA DÉCHARGE : colonne empilée, puis nappe au sol en tramage ----
        if (burst > 0.01f) {
            int h = (int) (burst * 15.0f);
            for (int i = 0; i < h; i++) {
                int tone = ((i + fr) % 3) == 0 ? 5 : (((i & 1) == 0) ? 4 : 6);
                float yy = GROUND_Y + (i + 0.5f) * PIX;
                // Section de deux pixels sur deux : une colonne, pas un fil.
                for (int c = 0; c < 4; c++) {
                    float f = ((c & 1) == 0 ? 0.5f : -0.5f) * PIX;
                    float l = ((c & 2) == 0 ? 0.5f : -0.5f) * PIX;
                    pix(vc, mat, tmp, cx, cy, cz, dx, dz, f, l, yy, GB[tone],
                            Math.round(a * fade));
                }
            }
            // Le dégradé d'une console se fait au damier, jamais à l'alpha.
            int span = 2 + (int) (burst * 9.0f);
            int inner = Math.max(0, span - 3);
            for (int gx = -span; gx <= span; gx++) {
                for (int gz = -span; gz <= span; gz++) {
                    int d2 = gx * gx + gz * gz;
                    if (d2 > span * span) continue;
                    if (((gx + gz + fr) & 1) != 0) continue;
                    if (d2 < inner * inner && ((gx * 3 + gz * 5 + fr) & 3) != 0) continue;
                    int tone = d2 > (span - 1) * (span - 1) ? 1 : (d2 > inner * inner ? 4 : 5);
                    pix(vc, mat, tmp, cx, cy, cz, dx, dz,
                            gx * PIX, gz * PIX, floorY, GB[tone], Math.round(a * fade));
                }
            }
        }
    }

    // =================================================================================
    //  III. APPEL DE L'ORAGE — 170 ticks
    // =================================================================================

    /**
     * Remplace l'ancienne « double entaille d'écho », qui n'était qu'un balayage d'arme répété.
     * Le principe est désormais une <b>zone</b> : un cumulonimbus se noue à huit blocs au-dessus
     * de la cible, la mitraille d'éclairs, puis s'effondre en une décharge unique dont l'onde
     * court très loin. La cible peut sortir de la zone — c'est ce qui rend la mécanique jouable
     * des deux côtés.
     *
     * Chronologie (170 ticks) :
     * <ul>
     *   <li>0,00–0,16 <b>formation</b> : les bouffées convergent de l'extérieur vers le centre
     *       et le nuage se noue à {@link #CLOUD_Y} blocs ;</li>
     *   <li>0,20–0,62 <b>mitraille</b> : {@link #STRIKES} éclairs tombent l'un après l'autre sur
     *       des points dispersés, chacun avec son flash et sa brûlure ;</li>
     *   <li>0,62–0,78 <b>charge</b> : le nuage se contracte de 30 %, son ventre s'illumine, une
     *       colonne d'appel monte du sol à sa rencontre ;</li>
     *   <li>0,78–0,90 <b>la décharge</b> : sept traits serrés tombent ensemble — un faisceau,
     *       car un unique trait assez épais pour paraître massif ne serait plus un éclair ;</li>
     *   <li>0,84–1,00 <b>onde</b> : l'anneau de choc s'ouvre jusqu'à dix blocs.</li>
     * </ul>
     */
    private static void renderStormcall(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                        float cx, float cy, float cz, float t,
                                        float dx, float dz, int alpha, int salt) {
        int a = Math.round(alpha * smooth(t / 0.05f) * (1.0f - smooth((t - 0.93f) / 0.07f)));
        if (a <= 4) return;

        final int strobe = (int) (t * 30.0f);
        float form   = smooth(t / 0.16f);
        float charge = smooth((t - 0.62f) / 0.14f);
        float blast  = smooth((t - 0.78f) / 0.045f);
        float after  = smooth((t - 0.84f) / 0.16f);

        // ---- 1. LE NUAGE ----
        {
            final int blobs = 112;
            float spin = t * 0.42f;
            // Le ventre s'illumine juste avant chaque départ d'éclair : c'est ce qui annonce
            // le coup, et c'est la seule chose qui prévient la cible qu'elle doit bouger.
            float belly = 0.0f;
            for (int k = 0; k < STRIKES; k++) {
                float ag = (t - (0.20f + k * 0.086f)) / 0.078f;
                if (ag > -0.45f && ag < 0.55f) {
                    belly = Math.max(belly, 1.0f - Math.abs(ag) / 0.55f);
                }
            }
            belly = Math.max(belly, charge * 0.85f);
            float squeeze = 1.0f - 0.30f * charge;
            for (int i = 0; i < blobs; i++) {
                float h1 = hash(salt + 90, i * 37);
                float h2 = hash(salt + 91, i * 53);
                float h3 = hash(salt + 92, i * 71);
                // Racine carrée : sans elle les bouffées s'entassent au centre du disque.
                float rr = CLOUD_R * squeeze * (0.16f + 0.84f * Mth.sqrt(h1));
                float ang = h2 * TAU + spin * (0.55f + 0.85f * h1);
                float born = clamp((form - h3 * 0.55f) / 0.45f);
                if (born <= 0.01f) continue;
                float rIn = rr * (1.0f + 2.40f * (1.0f - born));
                float yy = CLOUD_Y + (h3 - 0.5f) * 0.95f * squeeze;
                float s = (0.25f + 0.30f * h1) * born;
                float glow = hash(salt + strobe * 17, i * 29);
                int rgb = glow > 0.90f ? NEON : (glow > 0.62f ? CLOUD_LIT : CLOUD_DARK);
                // Les bouffées basses prennent la lumière du ventre, les hautes restent sombres.
                float low = clamp((CLOUD_Y - yy) / 0.70f);
                if (belly > 0.02f) rgb = mix(rgb, NEON, belly * low * 0.80f);
                if (blast > 0.05f) rgb = mix(rgb, CORE, blast * 0.65f);
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(ang) * rIn, Mth.sin(ang) * rIn, yy,
                        s, s * 0.60f, s, rgb, opaque(a * born * (0.85f + 0.15f * glow)));
            }
            // Arcs internes : le nuage travaille avant de frapper.
            int arcs = 3 + (int) (charge * 4.0f);
            for (int k = 0; k < arcs; k++) {
                if (hash(salt + strobe * 23, k * 41) < 0.45f) continue;
                float a0 = hash(salt + 93, k * 59) * TAU;
                float a1 = a0 + 1.4f + hash(salt + 94, k * 61) * 2.0f;
                float r0 = CLOUD_R * 0.80f * squeeze;
                drawBolt(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(a0) * r0, Mth.sin(a0) * r0, CLOUD_Y + 0.10f,
                        Mth.cos(a1) * r0, Mth.sin(a1) * r0, CLOUD_Y - 0.20f,
                        salt * 73 + k * 509 + strobe * 11, 0.40f, 1.0f, 0.50f,
                        a * form * (0.55f + 0.45f * charge), 10, true);
            }
        }

        // ---- 2. LA MITRAILLE ----
        for (int k = 0; k < STRIKES; k++) {
            float t0 = 0.20f + k * 0.086f;
            float age = (t - t0) / 0.078f;
            if (age <= 0.0f || age >= 1.0f) continue;
            float hx = hash(salt + 200, k * 61);
            float hz = hash(salt + 201, k * 67);
            float ang = hx * TAU;
            float rad = 0.55f + 2.35f * hz;
            float tf = Mth.cos(ang) * rad;
            float tl = Mth.sin(ang) * rad;
            float reveal = clamp(age / 0.30f);
            float keep = 1.0f - smooth((age - 0.42f) / 0.58f);
            // 20 nœuds sur 8,4 blocs : 0,42 de segment pour 0,14 de gaine, rapport 3,0.
            drawBolt(vc, mat, tmp, cx, cy, cz, dx, dz,
                    tf * 0.30f, tl * 0.30f, CLOUD_Y - 0.60f, tf, tl, GROUND_Y,
                    salt * 97 + k * 811 + strobe * 5, 0.62f, reveal, 1.10f,
                    a * keep, 16, true);
            // Deux ramifications par éclair : un trait nu sur 8 blocs fait pauvre.
            for (int b = 0; b < 2; b++) {
                float bu = 0.30f + 0.38f * hash(salt + 205, k * 23 + b * 29);
                float by = CLOUD_Y - 0.60f - (CLOUD_Y - 0.60f - GROUND_Y) * bu;
                float ba = hash(salt + 206, k * 31 + b * 37) * TAU;
                // Longueur minimale relevée : une ramification trop courte redevient un
                // chapelet de pavés (0,85 bloc sur 7 nœuds donnait un rapport de 1,7).
                float bl = 0.95f + 1.05f * hash(salt + 207, k * 41 + b * 43);
                float fx0 = tf * (1.0f - bu) + tf * bu;
                float fl0 = tl * (1.0f - bu) + tl * bu;
                drawBolt(vc, mat, tmp, cx, cy, cz, dx, dz,
                        fx0, fl0, by,
                        fx0 + Mth.cos(ba) * bl, fl0 + Mth.sin(ba) * bl,
                        Math.max(GROUND_Y, by - bl * 0.85f),
                        salt * 101 + k * 211 + b * 307 + strobe * 3, 0.42f,
                        clamp((reveal - 0.45f) / 0.55f), 0.60f, a * keep * 0.80f, 5, true);
            }
            if (reveal < 0.98f) continue;
            // Flash au point de chute, puis brûlure persistante.
            float pop = 1.0f - smooth((age - 0.30f) / 0.40f);
            if (pop > 0.02f) {
                for (int s = 0; s < 10; s++) {
                    float sa = s * TAU / 10.0f;
                    float sr = (0.18f + 0.80f * (1.0f - pop)) * (0.70f + 0.60f * hash(salt + 202, s * 17));
                    float sz = 0.085f * pop;
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            tf + Mth.cos(sa) * sr, tl + Mth.sin(sa) * sr, GROUND_Y + 0.06f,
                            sz, sz, sz, mix(HOT, CORE, pop), opaque(a * pop));
                }
            }
            for (int s = 0; s < 5; s++) {
                float sa = hash(salt + 203, k * 31 + s * 13) * TAU;
                float sr = 0.22f + 0.45f * hash(salt + 204, k * 37 + s * 19);
                float sz = 0.070f;
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        tf + Mth.cos(sa) * sr, tl + Mth.sin(sa) * sr, GROUND_Y + 0.02f,
                        sz, sz * 0.45f, sz, SCORCH, opaque(a * 0.85f));
            }
        }

        // ---- 3. LA COLONNE D'APPEL : le sol répond au nuage ----
        if (charge > 0.02f && blast < 0.80f) {
            int rings = 5;
            for (int r = 0; r < rings; r++) {
                float u = (r + 1) / (float) rings;
                float lift = charge * 2.60f * u;
                if (lift < 0.05f) continue;
                int n = 8 + r * 2;
                float rad = 0.85f * (1.0f - 0.55f * u) * (1.0f + 0.35f * charge);
                for (int i = 0; i < n; i++) {
                    float ang = i * TAU / n + t * 2.1f * (r % 2 == 0 ? 1.0f : -1.0f);
                    float sz = 0.075f * (1.0f - 0.45f * u);
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(ang) * rad, Mth.sin(ang) * rad, GROUND_Y + lift,
                            sz, sz, sz, mix(VIOLET, NEON, u), opaque(a * charge * (1.0f - 0.35f * u)));
                }
            }
        }

        // ---- 4. LA DÉCHARGE : un faisceau de sept traits, pas un tronc ----
        if (blast > 0.01f) {
            float keep = 1.0f - smooth((t - 0.90f) / 0.10f);
            for (int s = 0; s < 7; s++) {
                float ha = hash(salt + 300, s * 83);
                float ang = s * TAU / 7.0f + ha * 0.55f;
                float off = s == 0 ? 0.0f : 0.17f + 0.15f * ha;
                // 15 nœuds : 0,56 de segment pour 0,21 de gaine, rapport 2,7.
                drawBolt(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(ang) * off * 0.55f, Mth.sin(ang) * off * 0.55f, CLOUD_Y - 0.30f,
                        Mth.cos(ang) * off, Mth.sin(ang) * off, GROUND_Y,
                        salt * 131 + s * 907 + strobe * 7, 0.32f, blast,
                        s == 0 ? 1.70f : 1.30f, a * keep, 11, true);
            }
            // Surexposition au point d'impact, tronquée par le sol.
            float pop = blast * (1.0f - smooth((t - 0.86f) / 0.12f));
            for (int s = 0; s < 26; s++) {
                float h1 = hash(salt + 301, s * 29);
                float h2 = hash(salt + 302, s * 31);
                float ang = h1 * TAU;
                float rr = (0.20f + 1.30f * h2) * pop;
                float yy = GROUND_Y + 0.05f + h2 * 1.15f * pop;
                float sz = (0.13f - 0.06f * h2) * pop;
                if (sz <= 0.005f) continue;
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(ang) * rr, Mth.sin(ang) * rr, yy,
                        sz, sz, sz, mix(CORE, HOT, h1), opaque(a * pop));
            }
        }

        // ---- 5. L'ONDE DE CHOC : la portée réellement touchée par l'AoE ----
        if (after > 0.01f) {
            float rw = 1.10f + after * 8.90f;
            float thin = 1.0f - smooth((after - 0.50f) / 0.50f);
            final int n = 76;
            for (int i = 0; i < n; i++) {
                float ang = i * TAU / n;
                float jit = (hash(salt + 400, i * 13) - 0.5f) * 0.60f;
                float r = rw + jit;
                float sz = (0.15f + 0.11f * hash(salt + 401, i * 19)) * thin;
                if (sz <= 0.006f) continue;
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(ang) * r, Mth.sin(ang) * r, GROUND_Y + 0.05f,
                        sz, sz * 0.60f, sz, mix(VIOLET, NEON, thin), opaque(a * thin));
                // Une crête sur trois se relève : l'onde n'est pas un simple trait au sol.
                if ((i % 3) == 0 && thin > 0.25f) {
                    float hgt = 0.22f + 0.40f * hash(salt + 402, i * 23);
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(ang) * (r - 0.12f), Mth.sin(ang) * (r - 0.12f),
                            GROUND_Y + 0.05f + hgt * thin,
                            sz * 0.75f, hgt * thin, sz * 0.75f, NEON, opaque(a * thin * 0.90f));
                }
            }
        }
    }

    // =================================================================================
    //  Outils du pixel art 8 bits
    // =================================================================================

    /** Un pixel : cube de côté {@link #PIX}, aligné sur la grille, jamais tourné. */
    private static void pix(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                            float cx, float cy, float cz, float dx, float dz,
                            float f, float lateral, float y, int rgb, float alpha) {
        if (y < GROUND_Y) return;
        float h = PIX * 0.5f;
        box(vc, mat, tmp, cx, cy, cz, dx, dz,
                snap(f), snap(lateral), GROUND_Y + snap(y - GROUND_Y), h, h, h, rgb, opaque(alpha));
    }

    /** Aligne une coordonnée sur la grille du pixel art. */
    private static float snap(float v) {
        return Math.round(v / PIX) * PIX;
    }

    /**
     * Affiche une pose de sprite dans un plan vertical. Le sprite n'est jamais pivoté : il est
     * posé soit face à l'axe de frappe, soit de côté, comme une feuille de sprites d'époque.
     */
    private static void sprite(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                               float cx, float cy, float cz, float dx, float dz,
                               String[] rows, float f0, float l0, float y0,
                               boolean sideways, int rgbMain, int rgbShade, int alpha) {
        int rowCount = rows.length;
        for (int r = 0; r < rowCount; r++) {
            String row = rows[r];
            for (int c = 0; c < row.length(); c++) {
                char ch = row.charAt(c);
                if (ch == '.') continue;
                float u = (c - row.length() * 0.5f + 0.5f) * PIX;
                float v = (rowCount * 0.5f - 0.5f - r) * PIX;
                pix(vc, mat, tmp, cx, cy, cz, dx, dz,
                        sideways ? f0 : f0 + u, sideways ? l0 + u : l0, y0 + v,
                        ch == '#' ? rgbMain : rgbShade, alpha);
            }
        }
    }

    /**
     * Cercle rasterisé par l'algorithme de Bresenham, en arithmétique entière — c'est ainsi
     * qu'une console 8 bits trace un cercle, et cela se voit : le contour a des marches.
     */
    private static void pixelCircle(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                    float cx, float cy, float cz, float dx, float dz,
                                    float y, int radius, int rgbA, int rgbB, int phase, int alpha) {
        if (radius <= 0 || alpha <= 0) return;
        int x = radius, z = 0, err = 1 - radius;
        while (x >= z) {
            plot8(vc, mat, tmp, cx, cy, cz, dx, dz, x, z, y, rgbA, rgbB, phase, alpha);
            z++;
            if (err < 0) {
                err += 2 * z + 1;
            } else {
                x--;
                err += 2 * (z - x) + 1;
            }
        }
    }

    /** Les huit symétries d'un octant de Bresenham. */
    private static void plot8(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                              float cx, float cy, float cz, float dx, float dz,
                              int gx, int gz, float y, int rgbA, int rgbB, int phase, int alpha) {
        for (int s = 0; s < 8; s++) {
            int ox = (s & 1) == 0 ? gx : gz;
            int oz = (s & 1) == 0 ? gz : gx;
            if ((s & 2) != 0) ox = -ox;
            if ((s & 4) != 0) oz = -oz;
            int rgb = ((ox + oz + phase) & 3) == 0 ? rgbB : rgbA;
            pix(vc, mat, tmp, cx, cy, cz, dx, dz, ox * PIX, oz * PIX, y, rgb, alpha);
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
            // Un arc rampant est un contournement de SURFACE : le bruit vertical le faisait
            // plonger jusqu'à 0,6 bloc sous terre, où il disparaissait purement et simplement.
            // On le plaque au sol plutôt que de le laisser se perdre dessous.
            if (cyy < GROUND_Y) cyy = GROUND_Y;
            // Gaine violette large, halo, puis cœur blanc : trois passes concentriques. C'est
            // ce triple tracé qui donne au canal sa surexposition, impossible à obtenir avec
            // un seul cube par segment.
            if (sheath) {
                seg(vc, mat, tmp, cx, cy, cz, dx, dz, pf, pl, py, cf, cl, cyy,
                        BOLT_CORE * 2.05f * thick, mix(VIOLET, ARC, 0.5f), opaque(alpha * 0.80f));
                seg(vc, mat, tmp, cx, cy, cz, dx, dz, pf, pl, py, cf, cl, cyy,
                        BOLT_CORE * 1.40f * thick, NEON, opaque(alpha * 0.92f));
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
