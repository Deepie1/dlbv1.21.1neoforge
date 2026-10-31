package deepluckyblock.procedures;

import net.minecraft.world.entity.Entity;

public class CrimsonButterflyModelVisualScaleProcedure {

    /**
     * Retourne la taille (scale) du papillon entre 0.1 et 1.0.
     * Basee sur l'UUID de l'entite : meme valeur cote serveur et client.
     *
     * Appele par CrimsonButterflyRenderer a chaque rendu.
     */
    public static double execute(Entity entity) {
        if (entity == null) {
            return 0.5; // taille par defaut
        }

        // UUID unique : genere une taille stable et differente pour chaque papillon
        long uuid = Math.abs(entity.getUUID().getLeastSignificantBits());
        float t = ((uuid % 1000L) / 1000.0F);
        return 0.2F + t * 0.9F; // entre 0.1 et 1.0
    }
}
