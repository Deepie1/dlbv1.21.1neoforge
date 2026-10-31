package deepluckyblock.procedures;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;

/**
 * ═══════════════════════════════════════════════════════════════
 * 🌀 FirarainPotionDrinkProcedure — appelée quand le joueur FINIT
 *    de boire la Firarain Potion (trigger "when player finishes
 *    using item" de l'item dans MCreator).
 * ═══════════════════════════════════════════════════════════════
 *
 * ⚙️ INSTALLATION (important !) :
 * 1. Dans MCreator : ton élément Procedure "FirarainPotionDrinkProcedure"
 *    est sélectionné sur le trigger "when player finishes using item"
 *    de l'item, et l'item est sauvegardé ✔
 * 2. CLIC DROIT sur l'élément Procedure dans la liste workspace
 *    → "Lock code" 🔒 (sinon MCreator régénère/écrase ce fichier
 *    à chaque build !)
 * 3. Remplace tout le contenu du fichier par celui-ci
 *    (src/main/java/deepluckyblock/procedures/FirarainPotionDrinkProcedure.java)
 * 4. Build.
 *
 * Les 2 signatures execute() coexistent exprès : selon la version de
 * MCreator, l'item généré appelle execute(world, x, y, z, entity)
 * ou execute(world, x, y, z, entity, itemstack) — les deux compilent.
 */
public class FirarainPotionDrinkProcedure {

    /** Appelée par l'item si le trigger passe aussi l'ItemStack. */
    public static void execute(LevelAccessor world, double x, double y, double z,
                               Entity entity, ItemStack itemstack) {
        execute(world, x, y, z, entity);
    }

    /** Appelée par l'item si le trigger ne passe que world/coords/entity. */
    public static void execute(LevelAccessor world, double x, double y, double z,
                               Entity entity) {
        // ⚠️ Ne pas mettre la logique ici : elle est centralisée dans
        // FirarainProcedure (ouverture du GUI + téléportation),
        // pour être réutilisable par d'autres triggers si besoin.
        FirarainProcedure.execute(world, x, y, z, entity);
    }
}