package deepluckyblock.client;

/**
 * Compatibilite retiree volontairement : Deep Lucky Block alimente maintenant
 * directement les lignes Average Luck natives de Lucky Stats.
 *
 * Ce fichier no-op ecrase l'ancienne integration qui enregistrait des lignes HUD
 * "Deep Average Luck", afin qu'une simple copie de l'archive suffise sans devoir
 * supprimer manuellement un ancien fichier du workspace.
 */
public final class LuckyStatsClientIntegration {
    private LuckyStatsClientIntegration() {}
}
