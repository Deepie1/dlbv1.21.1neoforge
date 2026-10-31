package deepluckyblock.util;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * ElementalHeartConditions — LA condition de régénération spécifique à
 * chaque type de cœur élémentaire (demande utilisateur, ask #9) :
 * "chaque type de cœur = UNE seule condition de régénération ; un cœur
 * perdu hors de cette condition NE régénère PAS tant qu'elle n'est pas
 * remplie".
 *
 * <p>Fonction PURE de {@link Player} : ne lit que des données déjà
 * synchronisées client/serveur (santé, position, bloc sous les pieds,
 * état "en feu"/"dans l'eau", cycle jour/nuit, météo, luminosité locale).
 * Peut donc être appelée à l'IDENTIQUE côté serveur ({@code
 * EquipmentExpansionHandler}, pour la vraie logique de jeu) et côté
 * client ({@code ElementalHeartOverlay}, pour l'indice visuel HUD),
 * SANS paquet réseau dédié : les deux tournent le même calcul déterministe
 * sur les mêmes entrées synchronisées.
 */
public final class ElementalHeartConditions {

    private ElementalHeartConditions() {}

    /**
     * Ordre FIXE des éléments — réutilisé partout (HUD, bonus PV, perte/regen)
     * pour rester cohérent. Ne pas réordonner sans mettre à jour les autres
     * classes qui itèrent dans cet ordre.
     */
    public static final String[] ELEMENT_IDS = {
        "fire_heart", "water_heart", "earth_heart", "wind_heart",
        "electric_heart", "shadow_heart", "light_heart"
    };

    /**
     * true si la condition de régénération du cœur {@code elementId} est
     * actuellement remplie pour cette entité (joueur OU mob porteur du
     * plastron enchanté -- généralisé pour que les mobs équipés bénéficient
     * eux aussi du bonus/de la régénération de cœurs élémentaires).
     */
    public static boolean conditionMet(LivingEntity p, String elementId) {
        if (p == null || elementId == null) return false;
        Level level = p.level();
        BlockPos pos = p.blockPosition();
        return switch (elementId) {
            // FIRE : dans le feu, dans la lave, ou pres d'une source de
            // chaleur (Nether / bloc en feu / magma / lave a portee immediate).
            case "fire_heart" -> p.isOnFire() || p.isInLava()
                    || level.dimension() == Level.NETHER
                    || nearHeatSource(level, pos);
            // WATER : le joueur doit etre dans l'eau (touche le fluide,
            // pas seulement mouille par la pluie).
            case "water_heart" -> p.isInWater();
            // EARTH : sous terre (aucune vue du ciel) -- coherent avec le
            // theme "terre/roche", regenere en profondeur, pas en surface.
            case "earth_heart" -> !level.canSeeSky(pos);
            // WIND : en l'air (ne touche pas le sol) OU tres haut en
            // altitude -- theme "vent/altitude".
            case "wind_heart" -> !p.onGround() && !p.isInWater() || p.getY() > 150;
            // ELECTRIC : orage en cours ET le joueur voit le ciel (expose
            // a la foudre) -- theme "orage/eclair".
            case "electric_heart" -> level.isThundering() && level.canSeeSky(pos);
            // SHADOW : uniquement la nuit (et pas dans une dimension a
            // temps fixe comme le Nether/End, ou "nuit" n'a pas de sens).
            case "shadow_heart" -> level.isNight();
            // LIGHT : zone tres eclairee (lumiere de bloc/ciel elevee),
            // typiquement en plein jour a l'exterieur.
            case "light_heart" -> level.getMaxLocalRawBrightness(pos) >= 14;
            default -> false;
        };
    }

    /** Bloc en feu/lave/magma dans un rayon de 2 blocs -- "source de chaleur". */
    private static boolean nearHeatSource(Level level, BlockPos center) {
        BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    mut.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockState s = level.getBlockState(mut);
                    if (s.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                            || s.is(net.minecraft.world.level.block.Blocks.LAVA)
                            || s.is(net.minecraft.world.level.block.Blocks.FIRE)
                            || s.is(net.minecraft.world.level.block.Blocks.CAMPFIRE)
                            || s.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE)) {
                        return true;
                    }
                    FluidState f = level.getFluidState(mut);
                    if (f.is(FluidTags.LAVA)) return true;
                }
            }
        }
        return false;
    }

    /** Nom court FR utilisé dans les logs/tooltips ("dans l'eau", "la nuit"...). */
    public static String describe(String elementId) {
        return switch (elementId) {
            case "fire_heart" -> "dans le feu/lave ou pres d'une source de chaleur";
            case "water_heart" -> "immerge dans l'eau";
            case "earth_heart" -> "sous terre (hors de vue du ciel)";
            case "wind_heart" -> "en l'air ou en tres haute altitude";
            case "electric_heart" -> "en plein orage, a decouvert";
            case "shadow_heart" -> "la nuit";
            case "light_heart" -> "dans une zone tres eclairee";
            default -> "condition inconnue";
        };
    }
}
