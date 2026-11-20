package deepluckyblock.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import deepluckyblock.enchantments.DeepElement;
import deepluckyblock.enchantments.SkillNames;
import deepluckyblock.enchantments.WeaponSkill;

import java.util.ArrayList;
import java.util.List;

/**
 * T421 — {@code /dlbskill} : une arme, UNE compétence, les éléments dans l'ordre.
 *
 * <p><b>Pourquoi cette commande.</b> Une compétence sort sur 5 % des armes lootées, et
 * l'élément y est tiré au hasard : vérifier le halo « prêt », le nom propre de la compétence
 * ou les cubes du Vide demandait de casser des blocs jusqu'à tomber sur le bon élément.
 * Ici chaque appel donne une épée portant exactement une compétence, et <b>passe à l'élément
 * suivant</b> : treize appels couvrent les treize éléments porteurs, sans trou ni doublon.</p>
 *
 * <p><b>Dans l'ordre, pas au hasard.</b> Le curseur avance d'un élément par appel, dans
 * l'ordre de la table des signatures, puis revient au premier. La <i>signature</i> reste
 * tirée au sort à l'intérieur de l'élément : c'est bien « 1 skill random d'un élément ».</p>
 *
 * <p>Le curseur est gardé par joueur : deux joueurs qui testent en même temps ne se volent
 * pas leur place dans la liste.</p>
 */
@EventBusSubscriber(modid = "deep_lucky_block")
public final class DlbSkillCommand {

    private DlbSkillCommand() {}

    /** Position dans la liste des éléments, par joueur. */
    private static final java.util.Map<java.util.UUID, Integer> CURSOR =
            new java.util.concurrent.ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("dlbskill");
        root.requires(src -> src.hasPermission(2));
        root.executes(DlbSkillCommand::giveNext)
                .then(Commands.literal("next").executes(DlbSkillCommand::giveNext))
                .then(Commands.literal("check").executes(DlbSkillCommand::check))
                .then(Commands.literal("reset").executes(DlbSkillCommand::reset));
        event.getDispatcher().register(root);
    }

    /**
     * T423 — {@code /dlbskill check} : pourquoi CET objet ne lance rien.
     *
     * <p>« Le halo ne change pas et le clic ne fait rien » peut avoir quatre causes
     * distinctes : l'objet ne sait pas lancer de signature, il n'en porte aucune, elle est
     * en recharge, ou le halo n'a pas encore été posé. Les deviner une par une coûte cher ;
     * cette commande les lit toutes d'un coup sur l'objet tenu en main.</p>
     */
    private static int check(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("\u00a7cRien dans la main principale."));
            return 0;
        }
        long now = player.serverLevel().getGameTime();
        boolean peutLancer = WeaponSkill.canCastSignature(held);
        // T425 : la lecture BRUTE, pas la liste deja arbitree — c'est ici qu'on veut voir
        // une eventuelle competence en trop AVANT que le nettoyage ne la retire.
        java.util.List<WeaponSkill.Found> lues = WeaponSkill.scan(held);
        WeaponSkill.Entry gardee = WeaponSkill.retainedOn(held);
        WeaponSkill.Entry premier = WeaponSkill.firstOn(held);
        long pret = WeaponSkill.readyAt(held);
        boolean charge = WeaponSkill.isCharged(held, now);

        net.minecraft.nbt.CompoundTag tag = deepluckyblock.util.DeepNbt.tag(held);
        String force = deepluckyblock.client.EnchantAura.GLINT_FORCE_NBT_KEY;
        String tier = deepluckyblock.client.EnchantAura.GLINT_TIER_NBT_KEY;
        String couleur = (tag != null && tag.contains(force))
                ? String.format("#%06X", tag.getInt(force) & 0xFFFFFF) : "aucune";
        String palier = (tag != null && tag.contains(tier))
                ? String.valueOf(tag.getInt(tier)) : "4 (defaut)";
        boolean halo = tag != null && tag.getBoolean(WeaponSkill.GLINT_ON_KEY);

        StringBuilder sb = new StringBuilder();
        sb.append("\u00a7b[/dlbskill check] \u00a7f").append(held.getHoverName().getString()).append('\n');
        sb.append("\u00a77objet sait lancer une signature : ")
          .append(peutLancer ? "\u00a7aoui"
                  : "\u00a7cnon \u00a77(il faut une \u00e9p\u00e9e, hache, pioche, pelle, masse ou houe)").append("\n");
        sb.append("\u00a77comp\u00e9tences port\u00e9es : \u00a7f").append(lues.size());
        if (lues.size() > 1) {
            sb.append(" \u00a7c(une seule par objet : les autres vont \u00eatre retir\u00e9es)");
        }
        for (WeaponSkill.Found f : lues) {
            boolean retenue = gardee != null && gardee.enchantPath().equals(f.entry().enchantPath());
            sb.append("\n   ").append(retenue ? "\u00a7a\u25b6 " : "\u00a78\u00d7 ")
              .append(SkillNames.ofSkill(f.entry().effect(), f.entry().enchantPath(), true))
              .append(" \u00a78(").append(f.entry().enchantPath())
              .append(", niveau ").append(f.level())
              .append(", ").append(f.entry().cast().name()).append(')')
              .append(retenue ? " \u00a72retenue" : " \u00a7cen trop");
        }
        sb.append("\n\u00a77signature lan\u00e7able au clic droit sur un mob : ")
          .append(premier != null ? "\u00a7aoui" : "\u00a7cnon");
        sb.append("\n\u00a77pr\u00eate : ").append(charge ? "\u00a7aoui" : "\u00a7cnon \u00a77(encore "
                + Math.max(0, (pret - now) / 20) + " s)");
        sb.append("\n\u00a77halo : couleur \u00a7f").append(couleur)
          .append(" \u00a77palier \u00a7f").append(palier)
          .append(" \u00a77drapeau pr\u00eat \u00a7f").append(halo);
        final String txt = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(txt), false);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        CURSOR.remove(player.getUUID());
        ctx.getSource().sendSuccess(() -> Component.literal(
                "\u00a7b[/dlbskill] \u00a7fCurseur remis au premier \u00e9l\u00e9ment."), false);
        return 1;
    }

    private static int giveNext(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "\u00a7c/dlbskill doit \u00eatre lanc\u00e9e par un joueur."));
            return 0;
        }
        List<DeepElement> elements = new ArrayList<>(WeaponSkill.elementsWithSignature());
        if (elements.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal(
                    "\u00a7cAucun \u00e9l\u00e9ment ne porte de signature."));
            return 0;
        }

        int index = CURSOR.merge(player.getUUID(), 1, (old, one) -> old + 1) - 1;
        DeepElement element = elements.get(Math.floorMod(index, elements.size()));

        List<String> pool = WeaponSkill.signaturePathsOf(element);
        if (pool.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal(
                    "\u00a7cL'\u00e9l\u00e9ment " + element.displayName + " n'a aucune signature."));
            return 0;
        }
        String path = pool.get(player.getRandom().nextInt(pool.size()));

        Enchantment ench = deepluckyblock.util.DeepEnchant.byId("deep_lucky_block:" + path);
        if (ench == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "\u00a7cEnchantement introuvable : deep_lucky_block:" + path));
            return 0;
        }

        // Une \u00e9p\u00e9e en diamant : WeaponSkill.canCastSignature l'accepte (SwordItem), donc la
        // comp\u00e9tence part r\u00e9ellement au clic droit sur un mob. Un outil qui ne sait pas la
        // lancer donnerait une arme muette et ferait croire \u00e0 une panne.
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        deepluckyblock.util.DeepEnchant.setLevel(sword, ench, 3);
        // T429 : poser l'enchantement ne suffit plus a donner la competence — c'est tout
        // l'objet du correctif. Cette epee de test doit donc recevoir la meme marque que
        // celle delivree par le tirage a 5 %, sinon /dlbskill livrerait une arme muette.
        WeaponSkill.markGranted(sword, path);

        WeaponSkill.Entry entry = WeaponSkill.firstOn(sword);
        String skillName = (entry == null)
                ? path
                : SkillNames.ofSkill(entry.effect(), entry.enchantPath(), true);

        sword.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(
                "\u00a7b" + element.displayName + " \u00a78\u00b7 \u00a7f" + skillName));
        if (!player.getInventory().add(sword)) player.drop(sword, false);

        int total = elements.size();
        int human = Math.floorMod(index, total) + 1;
        final String line = "\u00a7b[/dlbskill " + human + "/" + total + "] \u00a7f\u00c9l\u00e9ment \u00a7e"
                + element.displayName + "\u00a7f \u00b7 comp\u00e9tence \u00a7e" + skillName
                + "\u00a7f \u00b7 enchantement \u00a77" + path
                + (entry != null ? "\u00a7f \u00b7 recharge \u00a7e" + entry.cooldownSeconds() + " s" : "")
                + "\n\u00a77Clic droit sur un mob pour la lancer. Relancez la commande pour l'\u00e9l\u00e9ment suivant.";
        ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        return 1;
    }
}
