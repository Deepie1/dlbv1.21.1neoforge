package deepluckyblock.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * T377-SHADOW-REFONTE — élément <b>OMBRE</b> : trois signatures voxel sur un moteur commun.
 *
 * <p><b>Pourquoi cette classe ?</b> L'ombre était, après la foudre, la dernière famille à trois
 * signatures encore dessinée en ligne dans {@code WeaponWorldVfxManager#impactShadow} : des bras
 * radiaux, une vrille générique, et des durées de <b>14 à 18 ticks</b> — moins d'une seconde — là
 * où le feu, la terre, le métal, la lumière, le poison, le bois, le vent, l'eau et la foudre ont
 * tous reçu une chorégraphie de 80 à 250 ticks avec son propre fichier. Cette classe met l'ombre
 * au même niveau.</p>
 *
 * <h2>Le problème propre à cet élément</h2>
 *
 * <p>Les neuf familles précédentes se voient parce qu'elles <b>émettent</b> de la lumière : une
 * flamme, un arc, un éclat de métal se détachent de tout fond. L'ombre, non. Du noir sur un monde
 * déjà sombre ne produit <i>aucune image</i> : c'est le piège central, et le rendu générique y
 * tombait — ses bras noirs étaient littéralement invisibles la nuit et dans les grottes, c'est-à-dire
 * là où on joue.</p>
 *
 * <p>Trois règles en découlent, et tout ce fichier les applique :</p>
 * <ol>
 *   <li><b>Jamais de noir sans liseré.</b> Toute masse sombre est bordée d'un trait violet ou
 *       orchidée. C'est le liseré qu'on voit ; le noir n'est que le trou qu'il entoure. Un disque
 *       noir sans bord n'est pas un disque, c'est une absence de pixels.</li>
 *   <li><b>La silhouette avant la matière.</b> Une forme d'ombre se lit à son contour, pas à son
 *       volume : les modèles d'ici sont construits en couches de <i>valeur</i> croissante vers
 *       l'extérieur, l'inverse d'un objet éclairé.</li>
 *   <li><b>La disparition se joue par la GÉOMÉTRIE.</b> Héritée des refontes précédentes et
 *       d'autant plus vraie ici : le tamis {@code pixelDensity} décime les cubes sous 200 d'alpha.
 *       Une ombre qu'on fait fondre en baissant l'alpha ne s'estompe pas, elle se <i>troue</i>.
 *       Tout passe par {@link #opaque(float)} ; on efface en rétrécissant, jamais en pâlissant.</li>
 * </ol>
 *
 * <h2>Les trois signatures</h2>
 * <ul>
 *   <li>{@link #GRASP} — <b>Allonge Spectrale</b> ({@code SPECTRAL_REACH}, 118 ticks) :
 *       l'ombre du porteur se <b>détache du sol</b> et court jusqu'à la cible comme une ombre
 *       d'arrière-saison ; une colonne vertébrale la remonte, puis une <b>main</b> — à plat sur
 *       le sol, puis <i>décollée</i> par rotation autour du poignet — se referme et ramène la
 *       cible le long du couloir. L'allonge est le sujet : rien ici n'est radial.</li>
 *   <li>{@link #RIFT} — <b>Faille du Flétrisseur</b> ({@code WITHER_SHADOW_RIFT}, 160 ticks) :
 *       une fente verticale s'ouvre en amande, un <b>crâne</b> en sort, souffle la flétrissure,
 *       puis la faille <b>inspire</b> — tout est tiré vers elle — et se referme d'un coup.</li>
 *   <li>{@link #ECLIPSE} — <b>Voile d'Éclipse</b> ({@code ECLIPSE_VEIL}, 200 ticks) : un soleil
 *       noir monte, sa couronne se déploie, et un <b>voile</b> en demi-sphère descend sur
 *       l'arène. À l'intérieur il fait nuit. L'anneau de diamant annonce la fin : trois
 *       <b>griffures</b> traversent le dôme, qui se déchire.</li>
 * </ul>
 *
 * <p>Le repère est celui des familles précédentes : {@code f} suit l'axe attaquant → cible,
 * {@code lateral} lui est perpendiculaire, {@code y} est la hauteur, l'origine au point d'impact.</p>
 */
public final class ShadowSignatureVfx {

    /** Allonge Spectrale — {@code SPECTRAL_REACH}. */
    public static final int GRASP = 0;
    /** Faille du Flétrisseur — {@code WITHER_SHADOW_RIFT}. */
    public static final int RIFT = 1;
    /** Voile d'Éclipse — {@code ECLIPSE_VEIL}. */
    public static final int ECLIPSE = 2;

    /**
     * Plancher d'opacité, identique à celui de la foudre. Le tamis du moteur supprime les cubes
     * sous 200 d'alpha dès que la densité de pixels est réduite ; 208 passe à coup sûr.
     */
    private static final int SOLID_FLOOR = 208;

    // ---- Palette. Tirée de DeepElement.SHADOW (0x4D318E / 0x000000 / 0x7100A7). -------------
    /**
     * Le « noir » de l'ombre n'est pas 0x000000. Le noir pur se confond avec le ciel de nuit, le
     * fond des grottes et toute silhouette non éclairée : la forme disparaît entièrement. 0x0B0814
     * est assez sombre pour lire comme un trou et assez bleuté pour se détacher du noir du monde.
     */
    private static final int VOID_INK = 0x0B0814;
    private static final int UMBRA = 0x18102C;       // corps des masses, un cran au-dessus du trou
    private static final int PENUMBRA = 0x2C1C50;    // bord intérieur, transition
    private static final int PURPLE = 0x4D318E;      // teinte maîtresse (primaire de l'élément)
    private static final int VIOLET = 0x7100A7;      // accent de l'élément, les liserés chauds
    private static final int ORCHID = 0xA85FE0;      // liseré vif, arêtes
    private static final int PALE = 0xD9BFFF;        // surexposition, cœur des éclats
    private static final int SPARK = 0xF3E9FF;       // le plus clair, réservé aux points

    // ---- Os du crâne. Quatre valeurs : c'est ce qui donne le volume, pas la teinte. ---------
    private static final int BONE_HI = 0xE9E3D2;     // arête éclairée
    private static final int BONE = 0xC2BAA6;        // face principale
    private static final int BONE_MID = 0x8F8877;    // face latérale
    private static final int BONE_DARK = 0x5C5749;   // creux, sutures
    private static final int BONE_DEEP = 0x332F27;   // fond des orbites

    /** Flétrissure : un gris-vert éteint. Ce n'est pas du poison (vert vif), c'est de la pourriture. */
    private static final int ROT = 0x6C7A58;
    private static final int ROT_DARK = 0x3A412F;
    /** Cendre : ce que l'ombre laisse en se retirant. */
    private static final int ASH = 0x4A4455;
    private static final int SCAR = 0x241B33;

    private static final float TAU = 6.2831855f;
    private static final float HALF_PI = 1.5707964f;

    /**
     * Niveau du sol dans le repère local. <b>−0,86 et non −0,42</b> comme la foudre : l'origine
     * d'un impact est à la mi-hauteur du mob, donc le sol réel est entre 0,6 et 1,0 bloc plus bas.
     * La foudre s'en accommode, ses effets au sol sont des lueurs. Une ombre, non : une ombre qui
     * flotte à un demi-bloc du sol est immédiatement lue comme un bug. On descend donc jusqu'à
     * −0,86, quitte à ce qu'elle s'enfonce un peu sous un mob de petite taille — une ombre
     * enfouie est <i>invisible là où elle s'enfonce</i> et juste partout ailleurs, alors qu'une
     * ombre flottante est fausse partout.
     */
    private static final float GROUND_Y = -0.86f;

    /** Demi-épaisseur d'une dalle plaquée au sol. Assez fine pour être plate, assez épaisse pour exister. */
    private static final float SLAB_H = 0.030f;

    /** Longueur du couloir de l'Allonge, en blocs, mesurée vers l'ARRIÈRE (vers le porteur). */
    private static final float REACH_SPAN = 9.0f;

    /** Demi-hauteur de l'amande de la Faille, et sa demi-largeur à pleine ouverture. */
    private static final float RIFT_H = 2.70f;
    private static final float RIFT_W = 1.15f;

    /** Rayon du dôme du Voile, et hauteur du soleil noir. */
    private static final float DOME_R = 7.0f;
    /**
     * Hauteur du soleil noir, dans le repere local (le sol est a {@link #GROUND_Y}). Vingt-deux
     * moins moins zero virgule quatre-vingt-six font <b>pres de vingt-trois blocs au-dessus du
     * mob</b>. A onze blocs, l'eclipse se jouait a hauteur de toit : on la voyait de cote, et
     * elle se confondait avec le dome. Il faut lever la tete pour la voir, sinon ce n'est pas
     * une eclipse, c'est une lampe.
     */
    private static final float SUN_Y = 22.0f;
    /**
     * Rayon du disque. Il grandit avec la distance, sinon un objet trois fois plus loin devient
     * trois fois plus petit et tout le benefice de la hauteur est perdu.
     */
    private static final float SUN_R = 4.60f;

    private ShadowSignatureVfx() { }

    /**
     * Point d'entrée commun.
     *
     * @param t       avancement normalisé 0 → 1 de la chorégraphie
     * @param dx,dz   axe horizontal unitaire attaquant → cible
     * @param variant {@link #GRASP}, {@link #RIFT} ou {@link #ECLIPSE}
     * @param salt    graine stable de l'instance : deux ombres ne se ressemblent jamais
     */
    public static void render(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                              float cx, float cy, float cz, float t,
                              float dx, float dz, int variant, int alpha, int salt) {
        if (t <= 0.0f || t >= 1.0f || alpha <= 4) return;
        float length = Mth.sqrt(dx * dx + dz * dz);
        if (length < 0.001f) { dx = 0.0f; dz = 1.0f; }
        else { dx /= length; dz /= length; }
        if (variant == RIFT) renderRift(vc, mat, tmp, cx, cy, cz, t, dx, dz, alpha, salt);
        else if (variant == ECLIPSE) renderEclipse(vc, mat, tmp, cx, cy, cz, t, dx, dz, alpha, salt);
        else renderGrasp(vc, mat, tmp, cx, cy, cz, t, dx, dz, alpha, salt);
    }

    // =================================================================================
    //  I. ALLONGE SPECTRALE — 118 ticks
    // =================================================================================

    /**
     * Chronologie (118 ticks) :
     * <ul>
     *   <li>0,00–0,13 <b>la coulée</b> : une ombre plate part du porteur et court au sol
     *       jusqu'à la cible, bord dentelé, front net ;</li>
     *   <li>0,13–0,27 <b>l'échine</b> : onze vertèbres se soulèvent le long du couloir, en
     *       vague qui s'éloigne ;</li>
     *   <li>0,27–0,45 <b>le décollement</b> : au bout du couloir, une main dessinée <i>à plat</i>
     *       pivote autour de son poignet et se redresse ;</li>
     *   <li>0,45–0,58 <b>la poigne</b> : les phalanges se replient une à une ;</li>
     *   <li>0,58–0,82 <b>la traction</b> : la main revient vers le porteur, le couloir se
     *       rétracte derrière elle et des lames d'ombre se lèvent sur son passage ;</li>
     *   <li>0,82–1,00 <b>le retour au sol</b> : la main se recouche par la même rotation et
     *       tout part en cendre.</li>
     * </ul>
     *
     * <p>Le couloir est tracé vers l'ARRIÈRE ({@code f} négatif) : l'origine de l'effet est la
     * cible, et le porteur est derrière. C'est la seule signature de ce mod dont la géométrie
     * est entièrement orientée — rien n'y est radial, parce que le sujet est l'allonge.</p>
     */
    private static void renderGrasp(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                    float cx, float cy, float cz, float t,
                                    float dx, float dz, int alpha, int salt) {
        int a = Math.round(alpha * smooth(t / 0.035f) * (1.0f - smooth((t - 0.93f) / 0.07f)));
        if (a <= 4) return;

        final float flow = smooth(t / 0.13f);                       // avancée du front
        final float spine = clamp((t - 0.13f) / 0.14f);
        final float peel = smooth((t - 0.27f) / 0.18f);             // 0 = couchée, 1 = debout
        final float curl = smooth((t - 0.45f) / 0.13f);             // fermeture des doigts
        final float pull = smooth((t - 0.58f) / 0.24f);             // retour vers le porteur
        final float layDown = smooth((t - 0.82f) / 0.12f);          // elle se recouche
        final int strobe = (int) (t * 24.0f);

        // Position du poignet le long du couloir. Il part du bout (le plus loin du porteur,
        // donc f le plus négatif... non : le bout EST la cible, f = 0) — relisons. L'origine est
        // la cible ; le couloir va de f = 0 (cible) à f = −REACH_SPAN (porteur). La main se lève
        // donc À la cible, puis recule vers le porteur en la tirant.
        final float handF = -REACH_SPAN * 0.06f - (REACH_SPAN * 0.64f) * pull;

        // ---- 1. LE COULOIR D'OMBRE ----
        // Une ombre portée n'a pas une largeur constante : elle s'évase avec la distance à la
        // source. On part donc large près du porteur et on affine vers la pointe, ce qui donne
        // au couloir une direction lisible même immobile.
        {
            final int ribs = 34;
            float front = flow * REACH_SPAN;                        // longueur révélée
            float back = pull * REACH_SPAN * 0.58f;                 // le couloir se mange par l'arrière
            for (int i = 0; i < ribs; i++) {
                float u = (i + 0.5f) / ribs;                        // 0 = cible, 1 = porteur
                float dist = u * REACH_SPAN;
                if (dist > front) continue;
                if (dist < back) continue;
                float h1 = hash(salt + 11, i * 37);
                float h2 = hash(salt + 12, i * 53);
                // Largeur : étroite à la pointe, large au pied. Plus une dentelure de bord.
                float w = 0.22f + 0.74f * u + 0.16f * h1;
                // Le front avance en biseau : les trois dernières côtes sont rognées.
                float edge = clamp((front - dist) / 0.9f);
                w *= 0.30f + 0.70f * edge;
                float fade = clamp((dist - back) / 1.1f);
                float av = a * (0.80f + 0.20f * h2) * fade;
                if (av <= 6.0f) continue;
                // Le corps : plat, sombre, bordé. Trois dalles concentriques, de la plus large
                // (liseré violet) à la plus étroite (trou). C'est le liseré qu'on voit.
                box(vc, mat, tmp, cx, cy, cz, dx, dz, -dist, 0.0f, GROUND_Y,
                        0.145f, SLAB_H, w, PURPLE, opaque(av * 0.55f));
                box(vc, mat, tmp, cx, cy, cz, dx, dz, -dist, 0.0f, GROUND_Y + 0.012f,
                        0.140f, SLAB_H, w * 0.76f, UMBRA, opaque(av * 0.85f));
                box(vc, mat, tmp, cx, cy, cz, dx, dz, -dist, 0.0f, GROUND_Y + 0.022f,
                        0.135f, SLAB_H, w * 0.46f, VOID_INK, opaque(av));
                // Dentelure : une écharde de part et d'autre, une côte sur trois.
                if (i % 3 == 0) {
                    float sp = (0.14f + 0.40f * hash(salt + 13, i * 71)) * (0.4f + 0.6f * u);
                    int side = (i % 6 == 0) ? 1 : -1;
                    box(vc, mat, tmp, cx, cy, cz, dx, dz, -dist, side * (w + sp * 0.5f),
                            GROUND_Y + 0.006f, 0.075f, SLAB_H, sp * 0.5f,
                            PENUMBRA, opaque(av * 0.70f));
                }
            }
            // Le front lui-même : une pointe claire. Sans elle, le couloir « apparaît » au lieu
            // d'avancer — l'œil a besoin d'un bord net pour lire un mouvement.
            if (flow > 0.02f && flow < 0.999f) {
                float tipF = -front;
                for (int k = 0; k < 7; k++) {
                    float off = (k - 3) * 0.13f;
                    float hh = 0.10f + 0.16f * (1.0f - Math.abs(k - 3) / 3.0f);
                    box(vc, mat, tmp, cx, cy, cz, dx, dz, tipF + Math.abs(off) * 0.9f, off,
                            GROUND_Y + 0.034f, 0.09f, SLAB_H, hh, ORCHID, opaque(a * 0.9f));
                }
            }
        }

        // ---- 2. L'ÉCHINE ----
        // Onze vertèbres qui se soulèvent en vague. Chacune est un petit empilement : corps,
        // apophyse, et deux ailerons. C'est peu de cubes et ça donne un squelette crédible.
        if (spine > 0.01f) {
            final int verts = 11;
            for (int i = 0; i < verts; i++) {
                float u = (i + 0.5f) / verts;
                float dist = u * REACH_SPAN * 0.92f;
                if (dist < pull * REACH_SPAN * 0.58f) continue;
                // La vague part de la cible et s'éloigne : chaque vertèbre a son propre retard.
                float local = clamp((spine - u * 0.55f) / 0.45f);
                float rise = Mth.sin(local * Mth.PI) * (0.30f + 0.34f * (1.0f - u));
                if (rise < 0.01f) continue;
                float av = a * (0.55f + 0.45f * local);
                float yy = GROUND_Y + rise;
                box(vc, mat, tmp, cx, cy, cz, dx, dz, -dist, 0.0f, yy,
                        0.085f, rise * 0.52f, 0.115f, UMBRA, opaque(av));
                box(vc, mat, tmp, cx, cy, cz, dx, dz, -dist, 0.0f, yy + rise * 0.50f,
                        0.055f, 0.070f, 0.075f, PURPLE, opaque(av));
                box(vc, mat, tmp, cx, cy, cz, dx, dz, -dist, 0.0f, yy + rise * 0.72f,
                        0.030f, 0.055f, 0.030f, ORCHID, opaque(av * 0.95f));
                for (int s = -1; s <= 1; s += 2) {
                    box(vc, mat, tmp, cx, cy, cz, dx, dz, -dist, s * 0.145f, yy + rise * 0.18f,
                            0.040f, 0.038f, 0.085f, PENUMBRA, opaque(av * 0.80f));
                }
            }
        }

        // ---- 3. LES LAMES DU COULOIR ----
        // Pendant la traction, des lames se lèvent derrière la main : c'est ce qui dit que le
        // couloir n'est pas décoratif mais tranchant, et c'est la lecture visuelle de l'AoE.
        if (pull > 0.01f) {
            final int blades = 9;
            for (int i = 0; i < blades; i++) {
                float u = (i + 0.5f) / blades;
                float dist = handF - 0.55f - u * 3.2f;
                if (dist > -0.2f) continue;
                // Le couloir s’arrete a REACH_SPAN : au-dela, une lame se leve dans le vide,
                // devant le porteur, sans ombre sous elle.
                if (dist < -REACH_SPAN) continue;
                float local = clamp((pull - u * 0.42f) / 0.30f);
                if (local <= 0.01f) continue;
                float life = Mth.sin(local * Mth.PI);
                float hgt = (0.55f + 0.85f * hash(salt + 21, i * 29)) * life;
                if (hgt < 0.04f) continue;
                int side = (i % 2 == 0) ? 1 : -1;
                float lat = side * (0.30f + 0.55f * hash(salt + 22, i * 31));
                float lean = side * 0.42f * life;
                float av = a * (0.60f + 0.40f * life);
                // Une lame = trois tranches de valeur croissante vers l'arête.
                rot(vc, mat, tmp, cx, cy, cz, dx, dz, dist, lat, GROUND_Y + hgt * 0.5f,
                        0.055f, hgt * 0.5f, 0.165f, -(float) Math.atan2(dz, dx), 0.0f, lean,
                        VOID_INK, opaque(av));
                rot(vc, mat, tmp, cx, cy, cz, dx, dz, dist, lat, GROUND_Y + hgt * 0.62f,
                        0.038f, hgt * 0.40f, 0.105f, -(float) Math.atan2(dz, dx), 0.0f, lean,
                        PURPLE, opaque(av * 0.90f));
                rot(vc, mat, tmp, cx, cy, cz, dx, dz, dist, lat, GROUND_Y + hgt * 0.92f,
                        0.026f, hgt * 0.16f, 0.055f, -(float) Math.atan2(dz, dx), 0.0f, lean,
                        ORCHID, opaque(av));
            }
        }

        // ---- 4. LA MAIN ----
        // Angle de décollement : PI/2 = couchée à plat, 0 = debout. Elle se lève, puis se
        // recouche. Le même paramètre fait les deux, dans les deux sens.
        if (peel > 0.001f) {
            float up = peel * (1.0f - layDown);
            float lie = (1.0f - up) * HALF_PI;
            float grip = curl * (1.0f - layDown * 0.55f);
            float handA = a * (0.35f + 0.65f * up + 0.35f * curl);
            // ÉCHELLE 2,45. A 1,45 la main faisait 2,2 blocs — la taille du mob, ce qui
            // paraissait la bonne reference sur le papier mais se lisait comme un accessoire
            // au bout d’un couloir de neuf blocs. A 2,45 elle fait pres de quatre blocs du
            // poignet au majeur : elle DOMINE sa proie, ce qui est le sujet de la signature.
            drawSpectralHand(vc, mat, tmp, cx, cy, cz, dx, dz,
                    handF, 0.0f, GROUND_Y, lie, grip, 2.45f, Math.min(255.0f, handA), salt, strobe);
        }

        // ---- 5. LA CENDRE ----
        // Elle ne sert pas à décorer : elle donne une épaisseur d'air au couloir, qui sans elle
        // reste un autocollant posé sur le sol.
        {
            int motes = 26;
            for (int i = 0; i < motes; i++) {
                float h1 = hash(salt + 31, i * 41);
                float h2 = hash(salt + 32, i * 43);
                float h3 = hash(salt + 33, i * 47);
                float born = h1 * 0.55f;
                float age = (t - born) / 0.45f;
                if (age <= 0.0f || age >= 1.0f) continue;
                float dist = (0.3f + 8.2f * h2);
                if (dist > flow * REACH_SPAN) continue;
                float lat = (h3 - 0.5f) * (0.5f + 1.3f * (dist / REACH_SPAN));
                float yy = GROUND_Y + 0.06f + age * (0.8f + 1.5f * h1);
                float s = 0.030f * (1.0f - age * 0.55f);
                int rgb = h3 > 0.72f ? ORCHID : (h3 > 0.38f ? PURPLE : ASH);
                box(vc, mat, tmp, cx, cy, cz, dx, dz, -dist, lat, yy, s, s, s,
                        rgb, opaque(a * (1.0f - age) * 0.85f));
            }
        }
    }

    /**
     * La main spectrale. <b>78 pièces</b>, articulées, et surtout <i>pliables à plat</i>.
     *
     * <p>Le sujet de cette signature est qu'une ombre est une chose <b>à deux dimensions</b> qui
     * se redresse. Tout le modèle est donc construit autour d'un angle unique, {@code lie} :
     * à PI/2 la main est une silhouette posée sur le sol, à 0 elle est debout. La rotation se
     * fait autour du poignet, et elle s'applique à la fois aux <i>positions</i> (calculées ici)
     * et à l'<i>orientation</i> de chaque boîte (passée en roulis). Appliquer l'une sans l'autre
     * donnait une main dont les doigts se levaient mais restaient couchés sur eux-mêmes.</p>
     *
     * <p>Les doigts sont des <b>chaînes cinématiques</b> : chaque phalange part du bout de la
     * précédente, avec son propre angle cumulé. C'est ce qui donne une vraie fermeture — un
     * pliage à angle unique aurait fait un éventail, pas un poing.</p>
     *
     * @param lie  PI/2 = couchée à plat sur le sol, 0 = dressée
     * @param grip 0 = ouverte, 1 = refermée
     */
    private static void drawSpectralHand(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                         float cx, float cy, float cz, float dx, float dz,
                                         float wristF, float wristL, float wristY,
                                         float lie, float grip, float scale,
                                         float alpha, int salt, int strobe) {
        if (alpha <= 1.0f) return;
        final float base = -(float) Math.atan2(dz, dx);
        final float cl = Mth.cos(lie), sl = Mth.sin(lie);

        // --- l'avant-bras : il sort du sol derrière le poignet ---
        for (int i = 0; i < 5; i++) {
            float u = i / 4.0f;
            float hgt = -0.30f - u * 0.62f;              // vers l'arrière/le bas dans le repère main
            float w = 0.17f - 0.025f * u;
            handPiece(vc, mat, tmp, cx, cy, cz, dx, dz, base, wristF, wristL, wristY, lie, cl, sl, scale,
                    0.0f, 0.0f, hgt, 0.105f, 0.11f, w, 0.0f,
                    i == 0 ? UMBRA : (i < 3 ? PENUMBRA : VOID_INK), alpha * (1.0f - 0.10f * u));
        }
        // tendons de l'avant-bras : deux liserés clairs qui disent l'effort
        for (int s = -1; s <= 1; s += 2) {
            handPiece(vc, mat, tmp, cx, cy, cz, dx, dz, base, wristF, wristL, wristY, lie, cl, sl, scale,
                    0.055f, s * 0.11f, -0.56f, 0.022f, 0.30f, 0.022f, 0.0f,
                    PURPLE, alpha * 0.85f);
        }

        // --- le poignet ---
        handPiece(vc, mat, tmp, cx, cy, cz, dx, dz, base, wristF, wristL, wristY, lie, cl, sl, scale,
                0.0f, 0.0f, -0.10f, 0.115f, 0.10f, 0.185f, 0.0f, UMBRA, alpha);
        handPiece(vc, mat, tmp, cx, cy, cz, dx, dz, base, wristF, wristL, wristY, lie, cl, sl, scale,
                0.0f, 0.0f, -0.10f, 0.125f, 0.035f, 0.200f, 0.0f, PURPLE, alpha * 0.80f);

        // --- la paume : trois plans, du plus profond au plus clair ---
        handPiece(vc, mat, tmp, cx, cy, cz, dx, dz, base, wristF, wristL, wristY, lie, cl, sl, scale,
                0.0f, 0.0f, 0.13f, 0.115f, 0.235f, 0.265f, 0.0f, VOID_INK, alpha);
        handPiece(vc, mat, tmp, cx, cy, cz, dx, dz, base, wristF, wristL, wristY, lie, cl, sl, scale,
                -0.085f, 0.0f, 0.13f, 0.040f, 0.225f, 0.255f, 0.0f, UMBRA, alpha);
        handPiece(vc, mat, tmp, cx, cy, cz, dx, dz, base, wristF, wristL, wristY, lie, cl, sl, scale,
                0.098f, 0.0f, 0.13f, 0.035f, 0.215f, 0.245f, 0.0f, PENUMBRA, alpha);
        // Les quatre liserés du contour de paume : c'est eux qui dessinent la main.
        handPiece(vc, mat, tmp, cx, cy, cz, dx, dz, base, wristF, wristL, wristY, lie, cl, sl, scale,
                0.0f, 0.275f, 0.13f, 0.120f, 0.230f, 0.028f, 0.0f, PURPLE, alpha * 0.90f);
        handPiece(vc, mat, tmp, cx, cy, cz, dx, dz, base, wristF, wristL, wristY, lie, cl, sl, scale,
                0.0f, -0.275f, 0.13f, 0.120f, 0.230f, 0.028f, 0.0f, PURPLE, alpha * 0.90f);
        handPiece(vc, mat, tmp, cx, cy, cz, dx, dz, base, wristF, wristL, wristY, lie, cl, sl, scale,
                0.0f, 0.0f, 0.375f, 0.118f, 0.028f, 0.268f, 0.0f, VIOLET, alpha * 0.90f);
        // Le creux de la paume : une braise comprimée qui s'allume quand la main se ferme.
        float emberA = alpha * (0.25f + 0.75f * grip);
        float es = (0.055f + 0.085f * grip) * (0.85f + 0.3f * hash(salt + strobe * 7, 3));
        handPiece(vc, mat, tmp, cx, cy, cz, dx, dz, base, wristF, wristL, wristY, lie, cl, sl, scale,
                0.075f, 0.0f, 0.15f, es, es, es, 0.0f, VIOLET, emberA);
        handPiece(vc, mat, tmp, cx, cy, cz, dx, dz, base, wristF, wristL, wristY, lie, cl, sl, scale,
                0.090f, 0.0f, 0.15f, es * 0.55f, es * 0.55f, es * 0.55f, 0.0f, PALE, emberA);

        // --- les quatre doigts ---
        // Longueurs réelles d'une main : majeur le plus long, index et annulaire proches,
        // auriculaire nettement plus court. Une main dont les doigts font tous la même
        // longueur ne se lit pas comme une main, mais comme un peigne.
        final float[] fingerLat = {0.195f, 0.065f, -0.065f, -0.195f};
        final float[] fingerLen = {0.90f, 1.00f, 0.94f, 0.74f};
        final float[] fingerSpread = {0.22f, 0.07f, -0.07f, -0.20f};
        for (int fdx = 0; fdx < 4; fdx++) {
            float L = fingerLen[fdx];
            // Trois phalanges, de plus en plus courtes et fines.
            float[] seg = {L * 0.42f, L * 0.33f, L * 0.25f};
            float[] rad = {0.072f, 0.062f, 0.050f};
            // Fermeture : la première phalange plie peu, la dernière beaucoup. Chaque doigt a
            // son propre retard, sinon le poing se ferme d'un bloc et paraît mécanique.
            float lag = clamp((grip - fdx * 0.07f) / 0.80f);
            float[] bend = {lag * 1.05f, lag * 1.30f, lag * 1.45f};
            float curF = 0.0f, curH = 0.375f, ang = -0.16f + fingerSpread[fdx] * 0.30f;
            for (int p = 0; p < 3; p++) {
                ang += bend[p];
                float half = seg[p] * 0.5f;
                // Centre de la phalange : on avance de la demi-longueur dans sa direction.
                float sf = Mth.sin(ang) * half;
                float sh = Mth.cos(ang) * half;
                float pf = curF + sf, ph = curH + sh;
                int body = p == 0 ? UMBRA : (p == 1 ? PENUMBRA : VOID_INK);
                handPiece(vc, mat, tmp, cx, cy, cz, dx, dz, base, wristF, wristL, wristY, lie, cl, sl, scale,
                        pf, fingerLat[fdx], ph, rad[p], half, rad[p], ang, body, alpha);
                // liseré d'arête, côté externe : le contour du doigt
                handPiece(vc, mat, tmp, cx, cy, cz, dx, dz, base, wristF, wristL, wristY, lie, cl, sl, scale,
                        pf, fingerLat[fdx] + (fdx < 2 ? 1 : -1) * (rad[p] + 0.014f), ph,
                        rad[p] * 0.55f, half * 0.92f, 0.013f, ang, PURPLE, alpha * 0.85f);
                // articulation : un nœud plus clair entre deux phalanges
                curF += Mth.sin(ang) * seg[p];
                curH += Mth.cos(ang) * seg[p];
                if (p < 2) {
                    handPiece(vc, mat, tmp, cx, cy, cz, dx, dz, base, wristF, wristL, wristY, lie, cl, sl, scale,
                            curF, fingerLat[fdx], curH, rad[p] * 0.95f, rad[p] * 0.80f,
                            rad[p] * 0.95f, ang, PENUMBRA, alpha * 0.95f);
                }
            }
            // la griffe : une pointe orchidée, c'est elle qui accroche l'œil
            handPiece(vc, mat, tmp, cx, cy, cz, dx, dz, base, wristF, wristL, wristY, lie, cl, sl, scale,
                    curF + Mth.sin(ang) * 0.055f, fingerLat[fdx], curH + Mth.cos(ang) * 0.055f,
                    0.028f, 0.065f, 0.028f, ang, ORCHID, alpha);
            handPiece(vc, mat, tmp, cx, cy, cz, dx, dz, base, wristF, wristL, wristY, lie, cl, sl, scale,
                    curF + Mth.sin(ang) * 0.105f, fingerLat[fdx], curH + Mth.cos(ang) * 0.105f,
                    0.016f, 0.040f, 0.016f, ang, PALE, alpha);
        }

        // --- le pouce : deux phalanges, implanté bas et de côté, incliné vers la paume ---
        {
            float lag = clamp(grip / 0.85f);
            float[] seg = {0.30f, 0.24f};
            float[] rad = {0.082f, 0.068f};
            float curF = 0.055f, curH = 0.02f, ang = 0.95f - lag * 0.70f;
            float lat = 0.255f;
            for (int p = 0; p < 2; p++) {
                float half = seg[p] * 0.5f;
                float pf = curF + Mth.sin(ang) * half * 0.35f;
                float ph = curH + Mth.cos(ang) * half;
                float plat = lat + Mth.sin(ang) * half * 0.78f;
                handPiece(vc, mat, tmp, cx, cy, cz, dx, dz, base, wristF, wristL, wristY, lie, cl, sl, scale,
                        pf, plat, ph, rad[p], half, rad[p], ang * 0.35f,
                        p == 0 ? UMBRA : VOID_INK, alpha);
                curH += Mth.cos(ang) * seg[p];
                lat += Mth.sin(ang) * seg[p] * 0.78f;
                ang += 0.55f + lag * 0.55f;
            }
            handPiece(vc, mat, tmp, cx, cy, cz, dx, dz, base, wristF, wristL, wristY, lie, cl, sl, scale,
                    curF, lat, curH, 0.026f, 0.058f, 0.026f, 0.0f, ORCHID, alpha);
        }
    }

    /**
     * Place une pièce de la main. Le couple {@code (cl, sl)} est le cosinus et le sinus de
     * l'angle de couchage, passés tels quels pour ne pas les recalculer 78 fois par image.
     *
     * <p>Repère local de la main : {@code u} vers l'avant du dos de la main, {@code v} latéral,
     * {@code h} du poignet vers les doigts. La rotation de couchage transporte {@code h} vers
     * l'avant et {@code u} vers le haut.</p>
     */
    private static void handPiece(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                  float cx, float cy, float cz, float dx, float dz, float base,
                                  float wristF, float wristL, float wristY,
                                  float lie, float cl, float sl, float scale,
                                  float u, float v, float h,
                                  float hu, float hh, float hv,
                                  float localRoll, int rgb, float alpha) {
        if (alpha <= 1.0f) return;
        // L'ECHELLE s'applique aux POSITIONS LOCALES ET aux demi-aretes, comme dans le
        // piece() du marteau de Thor. Elle etait recue par drawSpectralHand puis jamais
        // utilisee : la main sortait a l'echelle 1 quelle que soit la valeur passee, et
        // changer le parametre ne produisait rigoureusement aucun effet — un reglage mort,
        // le genre de defaut qu'on ne voit pas parce que « ca marche ».
        // DEMI-TOUR. La main se levait DOS a la cible : debout, l'axe u (dos de la main)
        // pointait vers +f, donc la paume regardait le porteur et la main saisissait a
        // l'envers. On applique une rotation de 180 degres autour de la VERTICALE du monde :
        // elle inverse l'avant et le lateral, laisse la hauteur intacte, et se compose au
        // lacet de chaque boite par +PI (le lacet est applique en dernier dans l'ordre
        // roll -> pitch -> yaw, donc l'ajouter revient a multiplier a gauche par Ry(PI) :
        // c'est bien un solide qu'on retourne, pas un miroir, et la chiralite est preservee).
        float f = wristF - (u * cl + h * sl) * scale;
        float y = wristY + (h * cl - u * sl) * scale;
        // ROULIS — derive de rotWrite, et non devine. Un roulis de theta envoie l’axe Y local
        // de la boite sur (−sin theta, cos theta) dans le plan (f, hauteur). La position, elle,
        // subit la matrice [[cos lie, sin lie], [−sin lie, cos lie]], soit une rotation de
        // −lie ; et l’axe h d’une phalange doit pointer vers (sin ang, cos ang), soit −ang.
        // D’ou −(lie + localRoll). Avec le signe oppose, la main se levait correctement mais
        // chaque doigt restait couche sur lui-meme — exactement le defaut du marteau de Thor,
        // dont les plaques se placaient bien mais s’orientaient a l’envers.
        rot(vc, mat, tmp, cx, cy, cz, dx, dz, f, wristL - v * scale, y,
                hu * scale, hh * scale, hv * scale,
                base + Mth.PI, 0.0f, -(lie + localRoll), rgb, opaque(alpha));
    }

    // =================================================================================
    //  II. FAILLE DU FLÉTRISSEUR — 160 ticks
    // =================================================================================

    /**
     * Chronologie (160 ticks) :
     * <ul>
     *   <li>0,00–0,07 <b>la fêlure</b> : un trait vertical d'un dixième de bloc, qui clignote ;</li>
     *   <li>0,07–0,20 <b>l'ouverture</b> : la fêlure s'écarte en amande, lèvres violettes,
     *       intérieur plein noir ;</li>
     *   <li>0,20–0,38 <b>le crâne</b> : il sort de l'amande en grandissant et en tournant ;</li>
     *   <li>0,38–0,55 <b>le souffle</b> : mâchoire ouverte, cône de flétrissure projeté
     *       vers l'avant, et trois crânes mineurs qui partent en vrille ;</li>
     *   <li>0,55–0,74 <b>l'inspiration</b> : la faille aspire — vingt traits convergent depuis
     *       huit blocs à la ronde, le crâne est ravalé ;</li>
     *   <li>0,74–0,85 <b>la fermeture</b> : l'amande se pince, l'onde part au sol ;</li>
     *   <li>0,85–1,00 <b>la cicatrice</b> : une balafre de cendre s'effrite.</li>
     * </ul>
     */
    private static void renderRift(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                   float cx, float cy, float cz, float t,
                                   float dx, float dz, int alpha, int salt) {
        int a = Math.round(alpha * smooth(t / 0.03f) * (1.0f - smooth((t - 0.94f) / 0.06f)));
        if (a <= 4) return;

        final float crack = smooth(t / 0.07f);
        final float open = smooth((t - 0.07f) / 0.13f);
        final float skull = smooth((t - 0.20f) / 0.18f);
        final float breath = clamp((t - 0.38f) / 0.17f);
        final float inhale = clamp((t - 0.55f) / 0.19f);
        final float shut = smooth((t - 0.74f) / 0.11f);
        final float scarT = clamp((t - 0.85f) / 0.15f);
        final int strobe = (int) (t * 28.0f);

        // Largeur et hauteur courantes de l'amande. Elle s'ouvre, tient, puis se pince.
        final float lensH = RIFT_H * (0.35f + 0.65f * crack) * (1.0f - 0.25f * shut);
        final float lensW = RIFT_W * open * (1.0f - shut);
        final float midY = GROUND_Y + RIFT_H * 0.92f;

        // ---- 1. L'AMANDE ----
        // Une faille ne se dessine pas comme une surface : elle se dessine comme un BORD.
        // Les lèvres sont donc tracées avec soin — deux arcs de 26 segments — et l'intérieur
        // n'est qu'un remplissage sombre qui sert de fond au bord.
        if (lensH > 0.05f) {
            final int steps = 26;
            // intérieur : des lamelles verticales, de plus en plus courtes vers les pointes
            if (lensW > 0.02f) {
                for (int i = 0; i < steps; i++) {
                    float u = (i + 0.5f) / steps;                 // 0..1 le long de la largeur
                    float s = (u - 0.5f) * 2.0f;                  // -1..1
                    float prof = Mth.sqrt(Math.max(0.0f, 1.0f - s * s));
                    float lat = s * lensW;
                    float hgt = lensH * prof;
                    if (hgt < 0.04f) continue;
                    // Le fond bouge : sans mouvement interne, un trou noir est un autocollant.
                    float jig = (hash(salt + strobe * 13, i * 37) - 0.5f) * 0.045f;
                    box(vc, mat, tmp, cx, cy, cz, dx, dz, 0.0f, lat, midY + jig,
                            0.055f, hgt, lensW / steps * 1.25f, VOID_INK, opaque(a));
                    // une veine violette sur deux lamelles : le vide n'est pas uniforme
                    if (i % 2 == 0) {
                        float vh = hgt * (0.25f + 0.45f * hash(salt + 41, i * 53));
                        float vy = midY + (hash(salt + 42, i * 59) - 0.5f) * hgt;
                        box(vc, mat, tmp, cx, cy, cz, dx, dz, 0.012f, lat, vy,
                                0.040f, vh, lensW / steps * 0.8f, PENUMBRA, opaque(a * 0.75f));
                    }
                }
            }
            // lèvres : l'arc avant et l'arc arrière, en trois valeurs
            for (int i = 0; i <= steps; i++) {
                float u = i / (float) steps;
                float s = (u - 0.5f) * 2.0f;
                float prof = Mth.sqrt(Math.max(0.0f, 1.0f - s * s));
                float lat = s * Math.max(lensW, 0.02f);
                float hgt = lensH * prof;
                float flick = 0.72f + 0.28f * hash(salt + strobe * 17, i * 29);
                for (int sgn = -1; sgn <= 1; sgn += 2) {
                    float yy = midY + sgn * hgt;
                    box(vc, mat, tmp, cx, cy, cz, dx, dz, 0.0f, lat, yy,
                            0.062f, 0.040f, 0.052f, VIOLET, opaque(a * flick));
                    box(vc, mat, tmp, cx, cy, cz, dx, dz, 0.030f, lat, yy,
                            0.026f, 0.024f, 0.030f, ORCHID, opaque(a * flick));
                }
            }
            // les deux pointes : surexposées, elles tiennent la forme
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                box(vc, mat, tmp, cx, cy, cz, dx, dz, 0.0f, sgn * (lensW + 0.03f), midY,
                        0.055f, 0.075f, 0.055f, PALE, opaque(a));
            }
            // la fêlure d'origine reste visible au centre tant que l'amande est étroite
            if (open < 0.85f) {
                box(vc, mat, tmp, cx, cy, cz, dx, dz, 0.035f, 0.0f, midY,
                        0.030f, lensH * 1.02f, 0.022f, SPARK, opaque(a * (1.0f - open)));
            }
        }

        // ---- 2. LE CRÂNE ----
        if (skull > 0.01f && shut < 0.92f) {
            // Il sort de la faille : il avance vers l'attaquant, grandit, puis recule au moment
            // de l'inspiration. Son lacet oscille, jamais aligné, sinon il a l'air posé.
            float out = skull * (1.0f - inhale * 0.92f);
            float sc = 0.42f + 1.05f * out;
            float adv = 0.10f + 1.45f * out;
            float yaw = Mth.sin(t * 3.4f + salt * 0.37f) * 0.30f + (1.0f - out) * 0.9f;
            float bob = Mth.sin(t * 7.1f) * 0.065f * out;
            float jaw = breath * (1.0f - inhale) * 0.55f;
            drawDemonSkull(vc, mat, tmp, cx, cy, cz, dx, dz,
                    adv, 0.0f, midY + bob, yaw, sc, jaw,
                    a * (0.35f + 0.65f * out), salt, strobe, true);
        }

        // ---- 3. LE SOUFFLE ----
        // Un cône qui s'évase. Chaque bouffée a sa propre naissance, donc le souffle « coule »
        // au lieu d'apparaître d'un bloc.
        if (breath > 0.01f && inhale < 0.85f) {
            final int puffs = 54;
            for (int i = 0; i < puffs; i++) {
                float h1 = hash(salt + 51, i * 37);
                float h2 = hash(salt + 52, i * 41);
                float h3 = hash(salt + 53, i * 43);
                float born = h1 * 0.72f;
                float age = (breath - born) / 0.42f;
                if (age <= 0.0f || age >= 1.0f) continue;
                float reach = 0.9f + age * 4.6f;
                float spread = 0.10f + age * 0.95f;
                float ang = h2 * TAU;
                float rr = spread * (0.25f + 0.75f * h3);
                float s = (0.075f + 0.075f * h2) * (1.0f - age * 0.40f);
                int rgb = h3 > 0.78f ? ROT : (h3 > 0.44f ? ROT_DARK : UMBRA);
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        1.1f + reach, Mth.cos(ang) * rr, midY - 0.35f + Mth.sin(ang) * rr - age * 0.5f,
                        s, s, s, rgb, opaque(a * (1.0f - age) * (1.0f - inhale)));
            }
            // trois crânes mineurs qui partent en vrille : la signature du Flétrisseur
            for (int k = 0; k < 3; k++) {
                float born = 0.12f + k * 0.17f;
                float age = (breath - born) / 0.70f;
                if (age <= 0.0f || age >= 1.0f) continue;
                float ang = (k - 1) * 0.42f;
                float adv = 1.3f + age * 5.4f;
                drawDemonSkull(vc, mat, tmp, cx, cy, cz, dx, dz,
                        adv, Mth.sin(ang) * adv * 0.35f,
                        midY - 0.25f - age * 0.9f + Mth.sin(age * 9.0f + k) * 0.18f,
                        ang + age * 2.6f, 0.30f * (1.0f - age * 0.35f), 0.42f,
                        a * (1.0f - age) * 0.85f, salt + k * 701, strobe, false);
            }
        }

        // ---- 4. L'INSPIRATION ----
        // Vingt traits qui convergent. Chacun RACCOURCIT par l'extérieur pendant qu'il
        // approche : c'est ce raccourcissement qui fait l'aspiration, pas le déplacement.
        if (inhale > 0.01f && shut < 0.95f) {
            final int streaks = 22;
            for (int i = 0; i < streaks; i++) {
                float h1 = hash(salt + 61, i * 37);
                float h2 = hash(salt + 62, i * 41);
                float h3 = hash(salt + 63, i * 43);
                float born = h1 * 0.45f;
                float age = (inhale - born) / 0.55f;
                if (age <= 0.0f || age >= 1.0f) continue;
                float ang = h2 * TAU;
                float far = 7.8f * (1.0f - age);
                float near = Math.max(0.25f, far - (1.4f + 2.2f * h3));
                float yy = midY + (h3 - 0.5f) * 3.4f * (1.0f - age * 0.6f);
                int segs = 7;
                for (int s = 0; s < segs; s++) {
                    float u = (s + 0.5f) / segs;
                    float d = near + (far - near) * u;
                    float ss = 0.055f * (1.0f - u * 0.55f);
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(ang) * d, Mth.sin(ang) * d, yy + (yy - midY) * u * 0.25f,
                            ss * 1.8f, ss, ss * 1.8f,
                            u < 0.3f ? ORCHID : (u < 0.7f ? PURPLE : UMBRA),
                            opaque(a * (1.0f - age) * 0.9f));
                }
            }
        }

        // ---- 5. L'ONDE DE FERMETURE ----
        if (shut > 0.02f) {
            float r = shut * 6.4f;
            int n = 40;
            float thin = 1.0f - shut;
            for (int i = 0; i < n; i++) {
                float ang = i * TAU / n + salt * 0.013f;
                float wob = 1.0f + 0.10f * Mth.sin(ang * 5.0f + t * 9.0f);
                float rr = r * wob;
                float hgt = 0.14f + 0.46f * thin;
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(ang) * rr, Mth.sin(ang) * rr, GROUND_Y + hgt * 0.5f,
                        0.085f, hgt * 0.5f, 0.085f, i % 3 == 0 ? ORCHID : PURPLE,
                        opaque(a * thin));
                if (i % 2 == 0) {
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(ang) * rr * 0.86f, Mth.sin(ang) * rr * 0.86f, GROUND_Y + 0.02f,
                            0.100f, SLAB_H, 0.100f, UMBRA, opaque(a * thin * 0.85f));
                }
            }
        }

        // ---- 6. LA CICATRICE ----
        if (scarT > 0.01f) {
            int flakes = 24;
            for (int i = 0; i < flakes; i++) {
                float h1 = hash(salt + 71, i * 37);
                float h2 = hash(salt + 72, i * 41);
                float yy = midY + (h1 - 0.5f) * RIFT_H * 1.7f;
                float drop = scarT * scarT * (1.6f + 2.2f * h2);
                float s = 0.045f * (1.0f - scarT * 0.6f);
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        (h2 - 0.5f) * 0.22f, (h1 - 0.5f) * 0.30f, yy - drop,
                        s, s * 1.6f, s, h2 > 0.6f ? SCAR : ASH,
                        opaque(a * (1.0f - scarT)));
            }
            // la balafre elle-même, verticale, qui s'amincit
            float sh = RIFT_H * (1.0f - scarT * 0.85f);
            if (sh > 0.08f) {
                box(vc, mat, tmp, cx, cy, cz, dx, dz, 0.0f, 0.0f, midY,
                        0.035f, sh, 0.030f, SCAR, opaque(a * (1.0f - scarT * 0.5f)));
            }
        }
    }

    /**
     * Crâne de démon. <b>Environ deux cent dix pièces</b> au niveau de détail plein, et c'est
     * volontairement beaucoup : c'est le seul objet <i>reconnaissable</i> de toute la famille,
     * donc le seul qui doive tenir quand la caméra s'approche.
     *
     * <p><b>Ce qui distingue un crâne de démon d'un crâne ordinaire</b>, et qui est donc
     * modélisé ici explicitement : (1) le <i>museau allongé</i> — un crâne humain est une
     * sphère, un crâne de démon est un prédateur, la mâchoire avance ; (2) les <i>arcades
     * sourcilières obliques</i>, qui descendent vers le nez et donnent à elles seules
     * l'expression de colère — sans elles on obtient un animal, pas un démon ; (3) les
     * <i>cornes annelées</i> à six segments, qui partent vers l'arrière avant de se relever ;
     * (4) les <i>crocs qui dépassent de la gueule fermée</i>, en haut comme en bas.</p>
     *
     * <p><b>Construction.</b> Tranches emboîtées, jamais de panneaux rapportés : un panneau
     * plaqué sur une face se détache comme une feuille dès que la caméra tourne. Les orbites
     * sont des <b>creux</b> — cavité sombre enfoncée dans la masse, puis braise au fond, puis
     * pupille fendue — et non des taches peintes sur le front.</p>
     *
     * <p><b>Les valeurs.</b> Quatre tons d'os suffisent si on les place correctement : le plus
     * clair sur les arêtes supérieures, le moyen sur les faces vues de trois quarts, le sombre
     * dans les sutures, le très sombre au fond des orbites. C'est ce qui donne le volume ; la
     * teinte, elle, ne change jamais.</p>
     *
     * @param jaw  ouverture de la mâchoire, 0 = fermée
     * @param full plein détail. Les trois crânes mineurs du souffle sont émis à {@code 0,30}
     *             d'échelle : à cette taille les fissures, les anneaux de corne et les dents
     *             secondaires tombent sous le tamis du moteur, et les calculer est du temps
     *             perdu quatre fois par image.
     */
    private static void drawDemonSkull(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                       float cx, float cy, float cz, float dx, float dz,
                                       float atF, float atL, float atY,
                                       float yaw, float scale, float jaw,
                                       float alpha, int salt, int strobe, boolean full) {
        if (alpha <= 1.0f || scale <= 0.01f) return;

        // Battement de la braise. Une lueur fixe se lit comme une peinture ; une lueur qui
        // respire se lit comme quelque chose de vivant enfermé dans l'os.
        final float glow = 0.68f + 0.32f * hash(salt + strobe * 13, 5);

        // =============================== LA BOÎTE CRÂNIENNE ===============================
        // Six tranches. Un crâne de démon est plus long que large : on part étroit au sommet,
        // on élargit aux tempes, on resserre vers la base.
        skullPiece(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                -0.08f, 0.0f, 0.615f, 0.300f, 0.050f, 0.255f, BONE_HI, alpha);
        skullPiece(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                -0.07f, 0.0f, 0.500f, 0.350f, 0.075f, 0.320f, BONE_HI, alpha);
        skullPiece(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                -0.06f, 0.0f, 0.335f, 0.390f, 0.100f, 0.375f, BONE, alpha);
        skullPiece(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                -0.05f, 0.0f, 0.140f, 0.400f, 0.105f, 0.395f, BONE, alpha);
        skullPiece(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                -0.07f, 0.0f, -0.040f, 0.370f, 0.090f, 0.355f, BONE_MID, alpha);
        skullPiece(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                -0.12f, 0.0f, -0.185f, 0.300f, 0.075f, 0.290f, BONE_MID, alpha);
        // l'occiput : il ferme la tête derrière, et il est toujours dans l'ombre
        skullPiece(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                -0.44f, 0.0f, 0.200f, 0.070f, 0.280f, 0.300f, BONE_DARK, alpha);
        skullPiece(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                -0.46f, 0.0f, -0.030f, 0.055f, 0.090f, 0.150f, BONE_DEEP, alpha);

        // --- la crête sagittale : cinq épines le long du sommet, de plus en plus hautes vers
        //     l'arrière. C'est elle qui empêche le crâne de ressembler à un œuf. ---
        for (int i = 0; i < 5; i++) {
            float u = i / 4.0f;
            float lf = 0.10f - u * 0.46f;
            float hgt = 0.045f + u * 0.085f;
            skullPieceRoll(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                    lf, 0.0f, 0.650f + u * 0.030f + hgt, 0.040f, hgt, 0.038f,
                    -0.25f - u * 0.20f, i % 2 == 0 ? BONE_HI : BONE, alpha);
        }
        // --- les fosses temporales : deux creux sombres derrière les orbites. Un crâne sans
        //     creux latéraux paraît gonflé. ---
        for (int s = -1; s <= 1; s += 2) {
            skullPiece(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                    -0.16f, s * 0.355f, 0.250f, 0.150f, 0.130f, 0.055f, BONE_DARK, alpha);
            skullPiece(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                    -0.16f, s * 0.390f, 0.250f, 0.120f, 0.100f, 0.030f, BONE_DEEP, alpha * 0.9f);
        }

        // ================================ LES ARCADES ====================================
        // LE trait du démon. Deux barres massives, inclinées vers le bas et vers l'intérieur :
        // l'œil lit l'angle avant de lire la forme, et un V renversé au-dessus des orbites se
        // lit comme de la colère même sur une silhouette de trois pixels. Horizontales, elles
        // donneraient un bovin ; relevées, un batracien.
        for (int s = -1; s <= 1; s += 2) {
            skullPieceRoll(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                    0.215f, s * 0.215f, 0.300f, 0.075f, 0.150f, 0.115f, 0.62f, BONE_HI, alpha);
            skullPieceRoll(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                    0.195f, s * 0.335f, 0.345f, 0.065f, 0.130f, 0.070f, 0.48f, BONE, alpha);
            // l'arête vive sur le dessus de l'arcade : le liseré qui la détache du front
            skullPieceRoll(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                    0.230f, s * 0.255f, 0.395f, 0.055f, 0.028f, 0.135f, 0.58f, BONE_HI, alpha);
        }
        // le glabelle : le pont d'os entre les deux arcades, saillant
        skullPiece(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                0.225f, 0.0f, 0.330f, 0.080f, 0.090f, 0.085f, BONE_HI, alpha);

        // ================================= LES ORBITES ===================================
        // Creusées en quatre couches, de la plus extérieure à la plus profonde. Une orbite
        // peinte en noir sur le front ne creuse rien : c'est l'empilement qui fait le trou.
        // ORDRE DE PROFONDEUR. Premiere version : une cavite BONE_DEEP de 0,075 de demi-epaisseur
        // ENGLOBAIT la braise placee derriere elle, et le rebord — une boite pleine — bouchait
        // le tout. Resultat : un carre sombre, aucun regard. Les couches sont donc desormais
        // EMPILEES SANS CHEVAUCHEMENT le long de lf (le fond derriere, la braise devant lui,
        // la pupille devant elle), et le rebord est un ANNEAU de quatre barres : un cadre ne
        // peut pas masquer ce qu'il encadre.
        for (int s = -1; s <= 1; s += 2) {
            final float ol = s * 0.235f;           // lateral du centre de l'orbite
            final float oy = 0.200f;               // hauteur du centre
            // 1. le fond du trou, tout au fond
            skullPiece(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                    0.215f, ol, oy, 0.075f, 0.105f, 0.098f, VOID_INK, alpha);
            // 2. la braise, DEVANT le fond
            skullPiece(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                    0.272f, ol, oy, 0.030f, 0.082f, 0.076f, VIOLET, alpha * glow);
            skullPiece(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                    0.294f, ol, oy, 0.022f, 0.058f, 0.052f, ORCHID, alpha * glow);
            // 3. la PUPILLE FENDUE, verticale, devant la braise : le detail qui fait qu'on
            //    voit un REGARD et non deux lampes. Ronde, elle donnerait un hibou.
            skullPiece(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                    0.310f, ol, oy, 0.018f, 0.054f, 0.013f, PALE, alpha * glow);
            skullPiece(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                    0.320f, ol, oy, 0.012f, 0.030f, 0.008f, SPARK, alpha * glow);
            // 4. le rebord osseux : QUATRE BARRES, pas une boite pleine
            skullPiece(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                    0.296f, ol, oy + 0.128f, 0.050f, 0.032f, 0.132f, BONE_MID, alpha);
            skullPiece(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                    0.296f, ol, oy - 0.122f, 0.050f, 0.030f, 0.132f, BONE_HI, alpha);
            skullPiece(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                    0.296f, ol + s * 0.112f, oy, 0.050f, 0.112f, 0.030f, BONE_MID, alpha);
            skullPiece(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                    0.296f, ol - s * 0.108f, oy, 0.050f, 0.112f, 0.028f, BONE, alpha);
            if (full) {
                // la larme de braise : elle coule de l'orbite sur la pommette
                for (int k = 0; k < 3; k++) {
                    skullPiece(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                            0.300f, s * (0.225f + k * 0.010f), 0.060f - k * 0.055f,
                            0.016f, 0.030f, 0.016f, k == 0 ? ORCHID : PURPLE,
                            alpha * glow * (0.85f - k * 0.22f));
                }
            }
        }

        // ================================= LE MUSEAU =====================================
        // Il AVANCE. C'est la deuxième chose qui sépare un démon d'un humain, et elle se joue
        // entièrement sur l'axe lf : quatre tranches qui s'allongent et se rétrécissent.
        // Le museau s'arrete SOUS les orbites. Plus haut, il venait les couvrir par
        // l'interieur et mangeait la moitie du regard.
        skullPiece(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                0.330f, 0.0f, 0.085f, 0.095f, 0.090f, 0.165f, BONE, alpha);
        skullPiece(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                0.440f, 0.0f, 0.075f, 0.090f, 0.088f, 0.155f, BONE, alpha);
        skullPiece(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                0.540f, 0.0f, 0.065f, 0.075f, 0.085f, 0.145f, BONE_MID, alpha);
        skullPiece(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                0.610f, 0.0f, 0.020f, 0.055f, 0.070f, 0.115f, BONE_MID, alpha);
        // l'arête nasale : le liseré clair qui court sur le dessus du museau
        for (int i = 0; i < 4; i++) {
            float u = i / 3.0f;
            skullPieceRoll(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                    0.330f + u * 0.280f, 0.0f, 0.255f - u * 0.155f,
                    0.055f, 0.026f, 0.050f - u * 0.016f, 0.42f, BONE_HI, alpha);
        }
        // les naseaux : deux trous, creusés comme les orbites
        for (int s = -1; s <= 1; s += 2) {
            skullPiece(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                    0.600f, s * 0.062f, 0.095f, 0.045f, 0.050f, 0.042f, BONE_DEEP, alpha);
            skullPiece(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                    0.625f, s * 0.062f, 0.095f, 0.030f, 0.036f, 0.028f, VOID_INK, alpha);
            if (full) {
                skullPiece(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                        0.632f, s * 0.062f, 0.095f, 0.020f, 0.022f, 0.018f, PURPLE,
                        alpha * glow * 0.75f);
            }
        }
        // les pommettes : elles élargissent le bas du crâne — c'est ce qui fait « crâne »
        for (int s = -1; s <= 1; s += 2) {
            skullPieceRoll(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                    0.270f, s * 0.325f, 0.015f, 0.145f, 0.055f, 0.075f, -0.22f, BONE_HI, alpha);
            skullPiece(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                    0.120f, s * 0.350f, -0.020f, 0.110f, 0.065f, 0.060f, BONE_MID, alpha);
        }

        // ============================ LE MAXILLAIRE SUPÉRIEUR ============================
        skullPiece(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                0.400f, 0.0f, -0.075f, 0.195f, 0.055f, 0.185f, BONE, alpha);
        // les DEUX GRANDES CANINES, qui dépassent de la gueule même fermée. Trois tronçons
        // décroissants chacune : une dent d'un seul cube n'est pas une dent, c'est un piquet.
        for (int s = -1; s <= 1; s += 2) {
            float[] tl = {0.075f, 0.060f, 0.045f};
            float[] tr = {0.040f, 0.030f, 0.018f};
            float hh = -0.125f;
            for (int p = 0; p < 3; p++) {
                hh -= tl[p] * 0.5f;
                skullPieceRoll(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                        0.470f + p * 0.012f, s * 0.145f, hh, tr[p], tl[p] * 0.5f, tr[p],
                        -0.10f, p == 0 ? BONE_HI : (p == 1 ? BONE : BONE_HI), alpha);
                hh -= tl[p] * 0.5f;
            }
        }
        // les incisives et molaires
        if (full) {
            for (int i = 0; i < 9; i++) {
                float lat = (i - 4) * 0.072f;
                if (Math.abs(Math.abs(lat) - 0.145f) < 0.02f) continue;   // place des canines
                float len = 0.048f + 0.020f * (1.0f - Math.abs(lat) * 3.0f);
                if (len < 0.022f) len = 0.022f;
                skullPiece(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                        0.430f, lat, -0.135f - len * 0.5f, 0.042f, len * 0.5f, 0.030f,
                        i % 2 == 0 ? BONE_HI : BONE, alpha);
            }
        }

        // ============================== LA MÂCHOIRE ======================================
        // Elle pivote autour du condyle, à l'arrière. Tout ce qui suit passe par jawPiece,
        // qui applique la rotation AUX POSITIONS ET À L'ORIENTATION : sans le second terme le
        // menton descend en bloc et se détache du reste du crâne.
        {
            float j = jaw * 0.78f;                       // un démon ouvre plus grand
            float pivF = -0.14f, pivH = -0.175f;
            // la branche horizontale, en deux épaisseurs
            jawPiece(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                    pivF, pivH, j, 0.280f, 0.0f, -0.115f, 0.300f, 0.055f, 0.270f, BONE, alpha);
            jawPiece(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                    pivF, pivH, j, 0.280f, 0.0f, -0.185f, 0.280f, 0.030f, 0.245f, BONE_MID, alpha);
            // le menton, fendu en deux : la fente est un creux, pas un trait peint
            jawPiece(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                    pivF, pivH, j, 0.520f, 0.0f, -0.150f, 0.060f, 0.080f, 0.195f, BONE_HI, alpha);
            jawPiece(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                    pivF, pivH, j, 0.545f, 0.0f, -0.150f, 0.040f, 0.060f, 0.022f, BONE_DARK, alpha);
            for (int s = -1; s <= 1; s += 2) {
                // les branches montantes, qui remontent vers l'articulation
                jawPiece(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                        pivF, pivH, j, 0.250f, s * 0.255f, -0.125f, 0.270f, 0.050f, 0.040f,
                        BONE_MID, alpha);
                jawPiece(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                        pivF, pivH, j, -0.060f, s * 0.240f, -0.030f, 0.050f, 0.110f, 0.045f,
                        BONE_DARK, alpha);
                // le condyle : la boule d'articulation, visible quand la gueule s'ouvre
                jawPiece(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                        pivF, pivH, j, -0.130f, s * 0.235f, 0.075f, 0.045f, 0.045f, 0.050f,
                        BONE_HI, alpha);
                // LES DEUX GRANDES CANINES INFÉRIEURES, qui remontent devant le museau
                float[] tl = {0.070f, 0.055f, 0.040f};
                float[] tr = {0.038f, 0.028f, 0.017f};
                float hh = -0.060f;
                for (int p = 0; p < 3; p++) {
                    hh += tl[p] * 0.5f;
                    jawPiece(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                            pivF, pivH, j, 0.455f + p * 0.010f, s * 0.150f, hh,
                            tr[p], tl[p] * 0.5f, tr[p],
                            p == 1 ? BONE : BONE_HI, alpha);
                    hh += tl[p] * 0.5f;
                }
            }
            if (full) {
                for (int i = 0; i < 8; i++) {
                    float lat = (i - 3.5f) * 0.074f;
                    if (Math.abs(Math.abs(lat) - 0.148f) < 0.025f) continue;
                    float len = 0.044f;
                    jawPiece(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                            pivF, pivH, j, 0.420f, lat, -0.065f + len * 0.5f,
                            0.040f, len * 0.5f, 0.028f, i % 2 == 0 ? BONE_HI : BONE, alpha);
                }
            }
            // la gueule : quand elle s'ouvre on doit voir du NOIR puis une gorge en braise,
            // et non un vide entre deux os — un vide se lit comme un défaut de modèle.
            if (jaw > 0.04f) {
                skullPiece(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                        0.270f, 0.0f, -0.185f - jaw * 0.165f, 0.175f, 0.075f + jaw * 0.155f,
                        0.195f, VOID_INK, alpha);
                skullPiece(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                        0.140f, 0.0f, -0.185f - jaw * 0.150f, 0.090f, 0.055f + jaw * 0.120f,
                        0.125f, PURPLE, alpha * (0.30f + 0.70f * jaw));
                skullPiece(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                        0.085f, 0.0f, -0.185f - jaw * 0.140f, 0.055f, 0.038f + jaw * 0.085f,
                        0.080f, VIOLET, alpha * (0.25f + 0.75f * jaw));
                skullPiece(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                        0.040f, 0.0f, -0.185f - jaw * 0.130f, 0.035f, 0.026f + jaw * 0.055f,
                        0.050f, ORCHID, alpha * jaw);
            }
        }

        // ================================ LES CORNES =====================================
        // Six segments annelés par corne. Elles partent des tempes vers l'ARRIÈRE, se couchent,
        // puis se relèvent et pointent vers l'avant : une corne qui monte tout droit donne un
        // taureau, c'est le retournement qui donne un démon. Le segment pointe vers
        // (−sin ang, cos ang) dans le plan (avant, hauteur) ; le roulis vaut donc +ang — au
        // signe opposé la corne se place en arc mais chaque tronçon reste vertical, et l'arc
        // devient un escalier.
        for (int s = -1; s <= 1; s += 2) {
            float f0 = -0.05f, h0 = 0.52f, lat = s * 0.300f, ang = -0.62f;
            float[] seg = {0.26f, 0.25f, 0.24f, 0.22f, 0.19f, 0.15f};
            float[] rad = {0.098f, 0.088f, 0.076f, 0.062f, 0.046f, 0.028f};
            float[] turn = {0.46f, 0.52f, 0.56f, 0.60f, 0.62f, 0.0f};
            for (int p = 0; p < 6; p++) {
                float half = seg[p] * 0.5f;
                float pf = f0 - Mth.sin(ang) * half;
                float ph = h0 + Mth.cos(ang) * half;
                float plat = lat + s * p * 0.022f;
                int rgb = p == 0 ? UMBRA : (p == 1 ? PENUMBRA : (p < 4 ? PURPLE : VIOLET));
                skullPieceRoll(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                        pf, plat, ph, rad[p], half, rad[p], ang, rgb, alpha);
                // L'ANNEAU : une bague légèrement plus large au départ de chaque tronçon.
                // C'est ce relief qui fait la corne de bouc ; lisse, elle fait la défense.
                if (full && p < 5) {
                    skullPieceRoll(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                            f0 - Mth.sin(ang) * 0.02f, plat, h0 + Mth.cos(ang) * 0.02f,
                            rad[p] * 1.22f, 0.018f, rad[p] * 1.22f, ang,
                            p < 2 ? PENUMBRA : ORCHID, alpha * 0.92f);
                }
                f0 -= Mth.sin(ang) * seg[p];
                h0 += Mth.cos(ang) * seg[p];
                ang += turn[p];
            }
            // la pointe, surexposée : c'est elle qui accroche l'œil de loin
            skullPieceRoll(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                    f0 - Mth.sin(ang) * 0.045f, lat + s * 0.132f, h0 + Mth.cos(ang) * 0.045f,
                    0.020f, 0.048f, 0.020f, ang, PALE, alpha);

            // --- les cornes secondaires : deux petites, sur la joue, pointant vers l'avant.
            //     Elles remplissent le vide entre la mâchoire et la grande corne. ---
            if (full) {
                float gf = 0.08f, gh = 0.095f, ga = 1.15f;
                for (int p = 0; p < 3; p++) {
                    float hl = 0.085f - p * 0.018f;
                    float rr = 0.040f - p * 0.010f;
                    float pf = gf - Mth.sin(ga) * hl * 0.5f;
                    float ph = gh + Mth.cos(ga) * hl * 0.5f;
                    skullPieceRoll(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                            pf, s * 0.355f, ph, rr, hl * 0.5f, rr, ga,
                            p == 2 ? ORCHID : PURPLE, alpha);
                    gf -= Mth.sin(ga) * hl;
                    gh += Mth.cos(ga) * hl;
                    ga += 0.30f;
                }
            }
        }

        // ============================ LES FISSURES DE BRAISE =============================
        // De la lumière qui sort de l'intérieur de l'os. Sans elles, le crâne est un objet ;
        // avec elles, il est un contenant — et ce qu'il contient est la raison pour laquelle
        // on ne veut pas être là.
        if (full) {
            final float[][] cracks = {
                    {-0.100f, 0.290f, 0.520f, 0.085f},
                    {-0.230f, -0.330f, 0.400f, 0.070f},
                    {0.060f, 0.370f, 0.230f, 0.060f},
                    {-0.300f, 0.180f, 0.090f, 0.055f},
                    {0.180f, -0.390f, 0.130f, 0.050f},
                    {-0.180f, -0.250f, 0.560f, 0.065f},
            };
            for (int i = 0; i < cracks.length; i++) {
                float[] c = cracks[i];
                float puls = 0.55f + 0.45f * hash(salt + i * 17, strobe + i);
                skullPieceRoll(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                        c[0], c[1], c[2], 0.020f, c[3], 0.020f,
                        (i % 2 == 0 ? 0.6f : -0.5f), VIOLET, alpha * puls);
                skullPieceRoll(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                        c[0], c[1], c[2], 0.012f, c[3] * 0.62f, 0.012f,
                        (i % 2 == 0 ? 0.6f : -0.5f), PALE, alpha * puls * 0.9f);
            }
        }
    }

    /** Pièce du crâne : position tournée du lacet, boîte orientée en conséquence. */
    private static void skullPiece(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                   float cx, float cy, float cz, float dx, float dz,
                                   float atF, float atL, float atY, float yaw, float scale,
                                   float lf, float ll, float ly,
                                   float hf, float hy, float hl, int rgb, float alpha) {
        skullPieceRoll(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                lf, ll, ly, hf, hy, hl, 0.0f, rgb, alpha);
    }

    /** Même chose, avec un roulis local (cornes, dents inclinées). */
    private static void skullPieceRoll(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                       float cx, float cy, float cz, float dx, float dz,
                                       float atF, float atL, float atY, float yaw, float scale,
                                       float lf, float ll, float ly,
                                       float hf, float hy, float hl, float roll,
                                       int rgb, float alpha) {
        if (alpha <= 1.0f) return;
        float c = Mth.cos(yaw), s = Mth.sin(yaw);
        // Le lacet de la BOÎTE est l'opposé de celui des positions, et doit intégrer
        // l'orientation du repère de l'effet — même piège que le marteau de Thor, où les
        // pièces se plaçaient correctement mais restaient orientées sur les axes du monde.
        float boxYaw = -(yaw + (float) Math.atan2(dz, dx));
        rot(vc, mat, tmp, cx, cy, cz, dx, dz,
                atF + (lf * c - ll * s) * scale, atL + (lf * s + ll * c) * scale, atY + ly * scale,
                hf * scale, hy * scale, hl * scale, boxYaw, 0.0f, roll, rgb, opaque(alpha));
    }

    /**
     * Pièce de la mâchoire : la position locale est d'abord pivotée de {@code j} autour de
     * l'articulation {@code (pivF, pivH)}, puis traitée comme une pièce de crâne ordinaire.
     */
    private static void jawPiece(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                 float cx, float cy, float cz, float dx, float dz,
                                 float atF, float atL, float atY, float yaw, float scale,
                                 float pivF, float pivH, float j,
                                 float lf, float ll, float ly,
                                 float hf, float hy, float hl, int rgb, float alpha) {
        float rf = lf - pivF, rh = ly - pivH;
        float c = Mth.cos(j), s = Mth.sin(j);
        float nf = pivF + rf * c - rh * s;
        float nh = pivH + rf * s + rh * c;
        // Meme rotation que la position (+j), sinon la machoire descend en bloc sans pivoter
        // et le menton se detache du reste du crane.
        skullPieceRoll(vc, mat, tmp, cx, cy, cz, dx, dz, atF, atL, atY, yaw, scale,
                nf, ll, nh, hf, hy, hl, j, rgb, alpha);
    }

    // =================================================================================
    //  III. VOILE D'ÉCLIPSE — 200 ticks
    // =================================================================================

    /**
     * Chronologie (200 ticks) :
     * <ul>
     *   <li>0,00–0,11 <b>la montée</b> : le disque noir s'élève du sol jusqu'à <b>vingt-trois
     *       blocs</b>, en grossissant, pendant que les étoiles s'allument ;</li>
     *   <li>0,11–0,24 <b>la couronne</b> : trente-six filaments se déploient autour du disque
     *       et ondulent — c'est la seule source de lumière de la scène ;</li>
     *   <li>0,24–0,42 <b>le voile tombe</b> : une demi-sphère de sept blocs de rayon descend
     *       sur l'arène, tracée en méridiens et parallèles, pendant que neuf rayons
     *       crépusculaires et une colonne en spirale relient le disque au sol ;</li>
     *   <li>0,42–0,62 <b>la nuit</b> : à l'intérieur, la cendre tombe et un sceau à trois
     *       croissants tourne au sol ;</li>
     *   <li>0,62–0,74 <b>l'anneau de diamant</b> : un seul point du limbe éclate, et une onde
     *       de choc part au sol un tiers de temps plus tard ;</li>
     *   <li>0,74–0,88 <b>la griffure</b> : trois lames courbes traversent le dôme ;</li>
     *   <li>0,88–1,00 <b>la levée</b> : le voile se déchire par le haut, le disque retombe.</li>
     * </ul>
     *
     * <p><b>Pourquoi un dôme en treillis et non en surface pleine ?</b> Une demi-sphère pleine
     * de sept blocs de rayon demande des milliers de cubes et, surtout, elle enferme la caméra
     * dans une boîte noire : le joueur ne voit plus le combat. Un treillis de neuf méridiens et
     * cinq parallèles donne la même lecture — on est dedans, c'est fermé — tout en laissant
     * passer le regard. C'est la même leçon que l'anneau de la foudre : la forme se lit à ses
     * lignes de construction, pas à son remplissage.</p>
     */
    private static void renderEclipse(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                      float cx, float cy, float cz, float t,
                                      float dx, float dz, int alpha, int salt) {
        int a = Math.round(alpha * smooth(t / 0.04f) * (1.0f - smooth((t - 0.95f) / 0.05f)));
        if (a <= 4) return;

        final float rise = smooth(t / 0.11f);
        final float corona = smooth((t - 0.11f) / 0.13f);
        final float veil = smooth((t - 0.24f) / 0.18f);
        final float night = clamp((t - 0.42f) / 0.20f);
        final float diamond = clamp((t - 0.62f) / 0.12f);
        final float claw = clamp((t - 0.74f) / 0.14f);
        final float lift = smooth((t - 0.88f) / 0.12f);
        final int strobe = (int) (t * 22.0f);

        final float sunY = GROUND_Y + (SUN_Y - GROUND_Y) * rise - lift * 3.0f;
        final float sunR = SUN_R * (0.30f + 0.70f * rise) * (1.0f - lift * 0.55f);

        // ---- 0. LES ÉTOILES ----
        // Le serveur force le monde à minuit pendant toute la signature. Côté client, un ciel
        // noir sans étoiles est juste un écran éteint : ce sont elles qui disent « il fait
        // nuit » plutôt que « le rendu a lâché ». Elles sont posées sur une calotte très large
        // et ne bougent pas — ce qui bouge, c'est leur scintillement.
        if (rise > 0.02f) {
            final int stars = 54;
            for (int i = 0; i < stars; i++) {
                float h1 = hash(salt + 301, i * 29);
                float h2 = hash(salt + 302, i * 31);
                float h3 = hash(salt + 303, i * 37);
                float ang = h1 * TAU;
                float phi = 0.18f + h2 * 1.05f;
                float dist = 17.0f + 11.0f * h3;
                float yy = GROUND_Y + 6.0f + Mth.cos(phi) * dist * 0.95f;
                if (yy < GROUND_Y + 4.0f) continue;
                float twinkle = 0.45f + 0.55f * hash(salt + i, strobe + i * 3);
                float s = (0.040f + 0.055f * h3) * twinkle;
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(ang) * Mth.sin(phi) * dist, Mth.sin(ang) * Mth.sin(phi) * dist, yy,
                        s, s, s, h3 > 0.78f ? SPARK : (h3 > 0.45f ? PALE : ORCHID),
                        opaque(a * rise * (1.0f - lift) * twinkle * 0.9f));
            }
        }

        // ---- 1. LE SOLEIL NOIR ----
        // Un disque vertical, face à l'attaquant, à VINGT-TROIS BLOCS du sol. Le remplissage
        // est un vrai disque — on balaie la hauteur et on calcule la demi-corde — et non un
        // empilement d'anneaux, qui laisse toujours des trous au centre.
        if (sunR > 0.05f) {
            final int rows = 25;
            for (int r = 0; r < rows; r++) {
                float v = (r + 0.5f) / rows;
                float sy = (v - 0.5f) * 2.0f;                    // -1..1
                float halfChord = Mth.sqrt(Math.max(0.0f, 1.0f - sy * sy)) * sunR;
                if (halfChord < 0.03f) continue;
                float yy = sunY + sy * sunR;
                box(vc, mat, tmp, cx, cy, cz, dx, dz, 0.0f, 0.0f, yy,
                        0.075f, sunR / rows * 1.15f, halfChord, VOID_INK, opaque(a));
                for (int s = -1; s <= 1; s += 2) {
                    float flick = 0.74f + 0.26f * hash(salt + strobe * 9, r * 31 + s);
                    box(vc, mat, tmp, cx, cy, cz, dx, dz, 0.010f, s * halfChord, yy,
                            0.070f, sunR / rows * 1.05f, 0.075f, VIOLET, opaque(a * flick));
                    box(vc, mat, tmp, cx, cy, cz, dx, dz, 0.055f, s * halfChord, yy,
                            0.030f, sunR / rows * 0.8f, 0.040f, ORCHID, opaque(a * flick));
                }
            }
            for (int s = -1; s <= 1; s += 2) {
                box(vc, mat, tmp, cx, cy, cz, dx, dz, 0.010f, 0.0f, sunY + s * sunR,
                        0.070f, 0.075f, 0.19f, VIOLET, opaque(a));
            }
        }

        // ---- 2. LA COURONNE ----
        // Quarante-huit filaments. Chacun est une chaîne de six cubes qui s'éloignent du limbe
        // en rapetissant : c'est le rapetissement qui fait la flamme, pas la courbure.
        if (corona > 0.01f) {
            final int fil = 48;
            for (int i = 0; i < fil; i++) {
                float ang = i * TAU / fil;
                float h1 = hash(salt + 81, i * 37);
                float h2 = hash(salt + 82, i * 41);
                float breathe = 0.55f + 0.45f * Mth.sin(t * (5.0f + 6.0f * h1) + h2 * TAU);
                float len = (0.70f + 2.60f * h1) * corona * breathe * (1.0f - lift * 0.8f);
                if (len < 0.06f) continue;
                int segs = 6;
                for (int s = 0; s < segs; s++) {
                    float u = (s + 0.5f) / segs;
                    float d = sunR + len * u;
                    float shear = u * u * 0.30f * (h2 - 0.5f) * 2.0f;
                    float aa = ang + shear;
                    float ss = (0.095f - 0.060f * u) * (0.8f + 0.4f * h2);
                    int rgb = u < 0.25f ? PALE : (u < 0.55f ? ORCHID : (u < 0.8f ? VIOLET : PURPLE));
                    box(vc, mat, tmp, cx, cy, cz, dx, dz, 0.0f,
                            Mth.cos(aa) * d, sunY + Mth.sin(aa) * d,
                            ss * 0.9f, ss, ss, rgb, opaque(a * corona * (1.0f - u * 0.35f)));
                }
            }
            // LES PROTUBÉRANCES : trois grandes langues qui jaillissent du limbe et retombent
            // en arc. Les filaments donnent le halo ; les protubérances donnent l'échelle, et
            // c'est l'échelle qui rend une étoile effrayante.
            for (int k = 0; k < 3; k++) {
                float base = k * (TAU / 3.0f) + salt * 0.0013f + t * 0.25f;
                float life = clamp((corona - k * 0.12f) * 1.4f) * (1.0f - lift);
                if (life < 0.02f) continue;
                int n = 14;
                for (int i = 0; i < n; i++) {
                    float u = (i + 0.5f) / n;
                    float arc = Mth.sin(u * Mth.PI);
                    float d = sunR * (1.0f + 0.95f * arc);
                    float aa = base + (u - 0.5f) * 1.25f;
                    float ss = (0.14f - 0.085f * u) * arc + 0.030f;
                    box(vc, mat, tmp, cx, cy, cz, dx, dz, 0.0f,
                            Mth.cos(aa) * d, sunY + Mth.sin(aa) * d,
                            ss * 0.8f, ss, ss,
                            u < 0.3f ? PALE : (u < 0.7f ? ORCHID : VIOLET),
                            opaque(a * life * (0.55f + 0.45f * arc)));
                }
            }
        }

        // ---- 3. LES RAYONS CRÉPUSCULAIRES ----
        // Le soleil est à vingt-trois blocs, le dôme en fait sept : sans rien entre les deux,
        // on voit deux objets séparés et non un seul événement. Neuf faisceaux descendent donc
        // du limbe jusqu'à l'ourlet, en éventail. Ils sont BRISÉS — chaque faisceau saute des
        // segments — parce qu'un trait plein ferait un tuyau de lumière, et qu'on veut de la
        // poussière traversée.
        if (veil > 0.01f) {
            final int shafts = 9;
            final float reach = veil * (1.0f - lift * 0.85f);
            for (int k = 0; k < shafts; k++) {
                float ang = k * TAU / shafts + t * 0.18f;
                float h1 = hash(salt + 211, k * 53);
                int n = 16;
                for (int i = 0; i < n; i++) {
                    float u = (i + 0.5f) / n;
                    if (u > reach) break;
                    // un segment sur cinq manque, et le motif dérive avec le temps
                    if (hash(salt + k * 7, i * 3 + strobe / 3) < 0.22f) continue;
                    float yy = sunY - (sunY - (GROUND_Y + 0.25f)) * u;
                    float spread = sunR * 0.80f + (DOME_R * 0.92f - sunR * 0.80f) * u;
                    float ss = (0.075f + 0.055f * h1) * (1.0f - u * 0.45f);
                    int rgb = u < 0.3f ? ORCHID : (u < 0.65f ? VIOLET : PURPLE);
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(ang) * spread, Mth.sin(ang) * spread, yy,
                            ss, ss * 1.9f, ss, rgb,
                            opaque(a * veil * (0.75f - u * 0.35f)));
                }
            }
            // LA COLONNE : une spirale serrée sur l'axe, du soleil au sceau. C'est le fil qui
            // dit que le disque là-haut et le cercle au sol sont la même chose.
            int turns = 26;
            for (int i = 0; i < turns; i++) {
                float u = (i + 0.5f) / turns;
                if (u > reach) break;
                float yy = sunY - (sunY - (GROUND_Y + 0.15f)) * u;
                float rr = 0.35f + 1.15f * Mth.sin(u * Mth.PI);
                float ang = u * 9.0f + t * 2.2f;
                float ss = 0.070f * (1.0f - u * 0.30f);
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(ang) * rr, Mth.sin(ang) * rr, yy, ss, ss, ss,
                        i % 3 == 0 ? PALE : VIOLET, opaque(a * veil * 0.85f));
            }
        }

        // ---- 4. L'ANNEAU DE DIAMANT ----
        // Un seul point du limbe, surexposé, avec quatre branches en croix, puis une ONDE DE
        // CHOC qui part au sol. C'est l'instant que tout le monde reconnaît d'une éclipse, et
        // il ne dure qu'une poignée de ticks.
        if (diamond > 0.01f && diamond < 0.999f) {
            float pop = Mth.sin(diamond * Mth.PI);
            float ang = -0.75f + salt * 0.0007f;
            float px = Mth.cos(ang) * sunR, py = Mth.sin(ang) * sunR;
            float s0 = 0.130f + 0.30f * pop;
            box(vc, mat, tmp, cx, cy, cz, dx, dz, 0.075f, px, sunY + py,
                    0.055f, s0, s0, SPARK, opaque(a));
            box(vc, mat, tmp, cx, cy, cz, dx, dz, 0.095f, px, sunY + py,
                    0.040f, s0 * 0.52f, s0 * 0.52f, 0xFFFFFF, opaque(a));
            for (int k = 0; k < 4; k++) {
                float ba = k * HALF_PI + 0.35f;
                float bl = (0.55f + 2.20f * pop) * (k % 2 == 0 ? 1.0f : 0.62f);
                int segs = 7;
                for (int s = 0; s < segs; s++) {
                    float u = (s + 0.5f) / segs;
                    float ss = 0.070f * (1.0f - u * 0.75f);
                    box(vc, mat, tmp, cx, cy, cz, dx, dz, 0.070f,
                            px + Mth.cos(ba) * bl * u, sunY + py + Mth.sin(ba) * bl * u,
                            0.045f, ss, ss, u < 0.4f ? PALE : ORCHID, opaque(a * (1.0f - u * 0.5f)));
                }
            }
            // l'onde de choc au sol, en retard d'un tiers : la lumière part d'en haut, le sol
            // répond après. Simultanés, les deux événements n'en font qu'un et on perd le lien.
            float wave = clamp((diamond - 0.30f) / 0.70f);
            if (wave > 0.001f) {
                int n = 64;
                float rr = DOME_R * 1.05f * wave;
                float fade = 1.0f - wave * 0.75f;
                for (int i = 0; i < n; i++) {
                    float aa = i * TAU / n;
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(aa) * rr, Mth.sin(aa) * rr, GROUND_Y + 0.03f,
                            0.115f, SLAB_H * (1.0f + 2.5f * fade), 0.115f,
                            i % 3 == 0 ? SPARK : PALE, opaque(a * fade));
                }
            }
        }

        // ---- 5. LE VOILE ----
        if (veil > 0.01f) {
            float close = veil * (1.0f - lift);
            // Le dôme descend : à close = 0 il est une calotte plate très haut, à close = 1 il
            // touche le sol. On interpole l'angle polaire maximal, pas le rayon : c'est ce qui
            // donne l'impression qu'il se referme plutôt qu'il ne rétrécit.
            float maxPhi = HALF_PI * close;
            final int meridians = 11;
            final int parallels = 6;
            for (int m = 0; m < meridians; m++) {
                float th = m * Mth.PI / meridians;
                float ct = Mth.cos(th), st = Mth.sin(th);
                int steps = 13;
                for (int i = 0; i <= steps; i++) {
                    float phi = maxPhi * i / steps;
                    float rr = DOME_R * Mth.sin(phi);
                    float yy = GROUND_Y + DOME_R * Mth.cos(phi) * (0.55f + 0.45f * close);
                    float wob = 1.0f + 0.045f * Mth.sin(phi * 7.0f + t * 5.0f + m);
                    for (int sgn = -1; sgn <= 1; sgn += 2) {
                        box(vc, mat, tmp, cx, cy, cz, dx, dz,
                                ct * rr * wob * sgn, st * rr * wob * sgn, yy,
                                0.055f, 0.055f, 0.055f,
                                // ORCHID/PURPLE et non PURPLE/UMBRA : ce treillis est la
                                // FRONTIERE d’un contrat de position — rester dedans fait mal,
                                // en sortir sauve. Un joueur qui ne voit pas la limite ne peut
                                // pas jouer la regle. En UMBRA il disparaissait de nuit.
                                (i % 3 == 0) ? ORCHID : PURPLE, opaque(a * close * 0.95f));
                    }
                }
            }
            for (int p = 1; p <= parallels; p++) {
                float phi = maxPhi * p / parallels;
                float rr = DOME_R * Mth.sin(phi);
                float yy = GROUND_Y + DOME_R * Mth.cos(phi) * (0.55f + 0.45f * close);
                if (rr < 0.10f) continue;
                int n = 14 + p * 5;
                for (int i = 0; i < n; i++) {
                    float ang = i * TAU / n + t * (p % 2 == 0 ? 0.35f : -0.35f);
                    float flick = 0.70f + 0.30f * hash(salt + strobe * 5, p * 97 + i * 13);
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(ang) * rr, Mth.sin(ang) * rr, yy,
                            0.048f, 0.048f, 0.048f,
                            (p == parallels) ? ORCHID : VIOLET, opaque(a * close * flick));
                }
            }
            // l'ourlet au sol : le dôme doit se poser, pas flotter
            if (close > 0.55f) {
                float hem = (close - 0.55f) / 0.45f;
                int n = 52;
                for (int i = 0; i < n; i++) {
                    float ang = i * TAU / n;
                    float rr = DOME_R * (0.96f + 0.05f * Mth.sin(ang * 6.0f + t * 4.0f));
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(ang) * rr, Mth.sin(ang) * rr, GROUND_Y + 0.02f,
                            0.105f, SLAB_H, 0.105f, i % 4 == 0 ? ORCHID : PURPLE,
                            opaque(a * hem));
                }
            }
            // LES ARCS RAMPANTS : de brèves décharges qui courent à la surface du dôme, comme
            // sur un bouclier qui encaisse. Chacune ne vit que quelques ticks et choisit un
            // méridien au hasard — c'est la brièveté qui fait l'électricité.
            if (close > 0.45f) {
                for (int k = 0; k < 5; k++) {
                    float seed = hash(salt + 401 + k * 13, strobe / 2);
                    if (seed < 0.45f) continue;
                    float th = seed * TAU;
                    float ct = Mth.cos(th), st = Mth.sin(th);
                    float p0 = hash(salt + 402 + k, strobe / 2) * 0.55f;
                    int n = 7;
                    for (int i = 0; i < n; i++) {
                        float phi = (p0 + i * 0.075f) * HALF_PI * close;
                        float rr = DOME_R * Mth.sin(phi) * (1.0f + 0.03f * Mth.sin(i * 2.1f));
                        float yy = GROUND_Y + DOME_R * Mth.cos(phi) * (0.55f + 0.45f * close);
                        float jig = (hash(salt + k * 31, i * 7 + strobe) - 0.5f) * 0.22f;
                        box(vc, mat, tmp, cx, cy, cz, dx, dz,
                                (ct + jig) * rr, (st - jig) * rr, yy,
                                0.065f, 0.065f, 0.065f, i % 2 == 0 ? SPARK : PALE,
                                opaque(a * close * 0.95f));
                    }
                }
            }
        }

        // ---- 6. LA NUIT INTÉRIEURE ----
        if (night > 0.01f) {
            // cendre : elle tombe lentement et uniquement DANS le dôme
            int motes = 62;
            for (int i = 0; i < motes; i++) {
                float h1 = hash(salt + 91, i * 37);
                float h2 = hash(salt + 92, i * 41);
                float h3 = hash(salt + 93, i * 43);
                float cycle = fract(t * (0.55f + 0.5f * h1) + h2);
                float ang = h2 * TAU;
                float rr = DOME_R * 0.92f * Mth.sqrt(h3);
                float yy = GROUND_Y + 0.1f + (1.0f - cycle) * 5.4f;
                float s = 0.030f + 0.022f * h1;
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(ang) * rr, Mth.sin(ang) * rr, yy, s, s, s,
                        h3 > 0.80f ? ORCHID : (h3 > 0.45f ? ASH : UMBRA),
                        opaque(a * night * (0.45f + 0.55f * cycle)));
            }
            // LES BRAISES MONTANTES : l'inverse de la cendre. Elles partent du sceau et sont
            // aspirées vers le disque. Deux flux de sens contraire valent mieux qu'un seul
            // deux fois plus dense : c'est le croisement qui donne la profondeur.
            int embers = 30;
            for (int i = 0; i < embers; i++) {
                float h1 = hash(salt + 95, i * 47);
                float h2 = hash(salt + 96, i * 53);
                float cycle = fract(t * (0.42f + 0.34f * h1) + h2);
                float ang = h2 * TAU + cycle * 2.1f;
                float rr = (0.6f + 3.4f * h1) * (1.0f - cycle * 0.72f);
                float yy = GROUND_Y + 0.2f + cycle * (sunY - GROUND_Y) * 0.92f;
                float s = (0.055f + 0.030f * h1) * (1.0f - cycle * 0.55f);
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(ang) * rr, Mth.sin(ang) * rr, yy, s, s, s,
                        cycle > 0.62f ? SPARK : (cycle > 0.3f ? PALE : ORCHID),
                        opaque(a * night * (1.0f - cycle * 0.45f)));
            }
            // le sceau : trois croissants qui tournent, plus un cercle de garde
            float spin = t * 1.15f;
            for (int k = 0; k < 3; k++) {
                float base = spin + k * (TAU / 3.0f);
                int n = 16;
                for (int i = 0; i < n; i++) {
                    float u = i / (float) (n - 1);
                    float ang = base + (u - 0.5f) * 1.45f;
                    float rr = 2.35f + 0.55f * Mth.sin(u * Mth.PI);
                    float s = 0.085f * Mth.sin(u * Mth.PI) + 0.030f;
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(ang) * rr, Mth.sin(ang) * rr, GROUND_Y + 0.025f,
                            s, SLAB_H, s, i % 4 == 0 ? ORCHID : VIOLET, opaque(a * night));
                }
            }
            int n = 46;
            for (int i = 0; i < n; i++) {
                float ang = i * TAU / n - spin * 0.4f;
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(ang) * 3.65f, Mth.sin(ang) * 3.65f, GROUND_Y + 0.018f,
                        0.070f, SLAB_H, 0.070f, i % 5 == 0 ? PALE : PURPLE,
                        opaque(a * night * 0.9f));
            }
            // LES RUNES : huit glyphes sur le cercle de garde, tournant à contresens. Chacun
            // est une petite croix irrégulière — assez pour qu'on lise « écriture » sans
            // qu'aucun ne signifie quoi que ce soit.
            for (int k = 0; k < 8; k++) {
                float ang = k * TAU / 8.0f + spin * 0.55f;
                float rr = 5.15f;
                float bx = Mth.cos(ang) * rr, bz = Mth.sin(ang) * rr;
                float puls = 0.55f + 0.45f * hash(salt + k * 19, strobe + k);
                int bars = 3 + (k % 3);
                for (int i = 0; i < bars; i++) {
                    float off = (i - (bars - 1) * 0.5f) * 0.16f;
                    float len = 0.14f + 0.10f * hash(salt + k * 23, i * 11);
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            bx + Mth.cos(ang + HALF_PI) * off, bz + Mth.sin(ang + HALF_PI) * off,
                            GROUND_Y + 0.030f, len, SLAB_H, 0.045f,
                            i == 0 ? PALE : ORCHID, opaque(a * night * puls));
                }
            }
        }

        // ---- 7. LA GRIFFURE ----
        // Trois lames courbes qui balaient le dôme. Chacune est un arc de cercle incliné :
        // on l'échantillonne en 18 points, et sa largeur suit un sinus pour qu'elle soit
        // pointue aux deux bouts. Une lame d'épaisseur constante est un tuyau, pas une griffe.
        if (claw > 0.01f) {
            for (int k = 0; k < 3; k++) {
                float born = k * 0.13f;
                float age = (claw - born) / 0.62f;
                if (age <= 0.0f || age >= 1.0f) continue;
                float reveal = clamp(age / 0.42f);
                float keep = 1.0f - smooth((age - 0.45f) / 0.55f);
                float tilt = -0.55f + k * 0.55f;
                float phase = -1.15f + k * 0.22f;
                int n = 18;
                int shown = Math.max(2, Math.round(n * reveal));
                for (int i = 0; i < shown; i++) {
                    float u = i / (float) (n - 1);
                    float ang = phase + u * 2.5f;
                    float rr = 1.5f + 4.6f * Mth.sin(0.22f + u * 2.4f);
                    float yy = GROUND_Y + 1.1f + Mth.sin(u * Mth.PI) * 3.0f + tilt * (u - 0.5f) * 4.0f;
                    float w = (0.055f + 0.145f * Mth.sin(u * Mth.PI)) * keep;
                    if (w < 0.012f) continue;
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(ang) * rr, Mth.sin(ang) * rr, yy,
                            w, w * 1.7f, w, VOID_INK, opaque(a * keep));
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(ang) * rr, Mth.sin(ang) * rr, yy + w * 1.4f,
                            w * 0.55f, w * 0.5f, w * 0.55f, ORCHID, opaque(a * keep));
                }
                // la pointe de la griffe : surexposée, elle marque la fin du trait
                if (reveal > 0.05f && reveal < 1.0f) {
                    float u = reveal;
                    float ang = phase + u * 2.5f;
                    float rr = 1.5f + 4.6f * Mth.sin(0.22f + u * 2.4f);
                    float yy = GROUND_Y + 1.1f + Mth.sin(u * Mth.PI) * 3.0f + tilt * (u - 0.5f) * 4.0f;
                    box(vc, mat, tmp, cx, cy, cz, dx, dz,
                            Mth.cos(ang) * rr, Mth.sin(ang) * rr, yy,
                            0.11f, 0.15f, 0.11f, PALE, opaque(a));
                }
            }
        }

        // ---- 8. LA LEVÉE ----
        // Le voile ne s'efface pas : il part en lambeaux vers le haut. Encore une fois, la
        // disparition est GÉOMÉTRIQUE — les lambeaux rapetissent et montent, l'alpha reste plein.
        if (lift > 0.01f) {
            int rags = 30;
            for (int i = 0; i < rags; i++) {
                float h1 = hash(salt + 101, i * 37);
                float h2 = hash(salt + 102, i * 41);
                float ang = h1 * TAU;
                float rr = DOME_R * (0.45f + 0.55f * h2) * (1.0f - lift * 0.35f);
                float up = lift * (3.0f + 5.5f * h1);
                float s = (0.14f + 0.10f * h2) * (1.0f - lift);
                if (s < 0.015f) continue;
                box(vc, mat, tmp, cx, cy, cz, dx, dz,
                        Mth.cos(ang) * rr, Mth.sin(ang) * rr, GROUND_Y + 0.6f + up,
                        s * 0.5f, s * 1.5f, s * 0.5f, h2 > 0.65f ? PURPLE : UMBRA, opaque(a));
            }
        }
    }

    // =================================================================================
    //  Utilitaires
    // =================================================================================

    /**
     * Relève l'alpha au-dessus du seuil du tamis, ou n'émet rien. Règle de toute la famille :
     * on ne fait jamais disparaître une ombre par fondu d'alpha — elle serait décimée en
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
