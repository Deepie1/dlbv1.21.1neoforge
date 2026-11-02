package deepluckyblock.client;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Injecte les traductions (combos, infobulles, commandes et événements) dans le Language actif.
 *
 * Strictement client-side : jamais chargé sur serveur dédié.
 * Les traductions déjà présentes dans Language restent prioritaires ; l'injection
 * garantit qu'en anglais (en_us, en_gb...) comme en français (fr_fr, fr_ca...),
 * aucune clé de combo ou d'événement ne tombe jamais en clé brute ni dans la mauvaise langue,
 * même après changement de langue en plein jeu ou régénération MCreator.
 */
@EventBusSubscriber(modid = "deep_lucky_block", value = Dist.CLIENT)
public final class ClientTranslationInjector {

    private static final Map<String, String> ENGLISH = createEnglish();
    private static final Map<String, String> FRENCH = createFrench();

    private ClientTranslationInjector() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(ClientTranslationInjector::injectNow);
    }

    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        // Réinjecte après F3+T et après chaque changement de langue.
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager -> injectNow());
    }

    @EventBusSubscriber(modid = "deep_lucky_block", value = Dist.CLIENT)
    public static final class GameBusGuard {
        private GameBusGuard() {}

        @SubscribeEvent
        public static void onClientTickPre(ClientTickEvent.Pre event) {
            // Si LanguageManager.onResourceManagerReload s'est exécuté APRÈS notre listener
            // lors d'un changement de langue en jeu, on ré-enveloppe immédiatement Language.
            if (!(Language.getInstance() instanceof InjectedLanguage)) {
                injectNow();
            }
        }
    }

    public static void ensureInjected() {
        if (!(Language.getInstance() instanceof InjectedLanguage)) {
            injectNow();
        }
    }

    private static boolean isFrenchActive() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null && minecraft.options != null) {
                String code = minecraft.options.languageCode;
                return code != null && code.toLowerCase(Locale.ROOT).startsWith("fr");
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static void injectNow() {
        Language current = Language.getInstance();
        if (current instanceof InjectedLanguage injected) {
            current = injected.delegate;
        }
        Language.inject(new InjectedLanguage(current));
    }

    private static void loadJsonResource(Map<String, String> target, String classpathResource) {
        try (InputStream stream = ClientTranslationInjector.class.getResourceAsStream(classpathResource)) {
            if (stream == null) return;
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonObject root = new Gson().fromJson(reader, JsonObject.class);
                if (root == null) return;
                for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                    JsonElement v = e.getValue();
                    if (v instanceof JsonPrimitive prim && prim.isString()) {
                        target.putIfAbsent(e.getKey(), prim.getAsString());
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Délègue tout le rendu au langage Minecraft courant et résout dynamiquement
     * la langue active (FR vs EN) à chaque lecture pour supporter le changement
     * de langue à chaud sans jamais figer l'ancienne langue.
     */
    private static final class InjectedLanguage extends Language {
        private final Language delegate;

        private InjectedLanguage(Language delegate) {
            this.delegate = delegate;
        }

        private Map<String, String> activeMap() {
            return isFrenchActive() ? FRENCH : ENGLISH;
        }

        @Override
        public String getOrDefault(String key, String fallback) {
            Map<String, String> preferred = activeMap();
            if (preferred.containsKey(key)) {
                return preferred.get(key);
            }
            if (delegate.has(key)) return delegate.getOrDefault(key, fallback);
            return fallback;
        }

        @Override
        public boolean has(String key) {
            return delegate.has(key) || activeMap().containsKey(key);
        }

        @Override
        public boolean isDefaultRightToLeft() {
            return delegate.isDefaultRightToLeft();
        }

        @Override
        public FormattedCharSequence getVisualOrder(FormattedText text) {
            return delegate.getVisualOrder(text);
        }
    }

    private static Map<String, String> createEnglish() {
        Map<String, String> map = new HashMap<>();
        map.put("event.deep_lucky_block.acid_rain.action", "☠ The air burns your lungs... ☠");
        map.put("event.deep_lucky_block.acid_rain.title", "The air turns acrid...");
        map.put("event.deep_lucky_block.angry_bees.action", "🐝 The buzzing is everywhere!");
        map.put("event.deep_lucky_block.angry_bees.narration", "BZZZZZ! They are not happy...");
        map.put("event.deep_lucky_block.angry_bees.title", "A buzz swells in the air...");
        map.put("event.deep_lucky_block.anvil_rain.action", "⚠ ANVIL RAIN! LOOK UP!");
        map.put("event.deep_lucky_block.anvil_rain.title", "Better watch the sky...");
        map.put("event.deep_lucky_block.arena.action", "⚔ The walls have no exit!");
        map.put("event.deep_lucky_block.arena.narration", "An arena materializes... something is waiting inside.");
        map.put("event.deep_lucky_block.arena_of_the_forgotten.title", "Walls rise to cage you...");
        map.put("event.deep_lucky_block.blind_trap.narration", "Everything goes dark...");
        map.put("event.deep_lucky_block.blind_trap.title", "The light drains away...");
        map.put("event.deep_lucky_block.bob.action", "🎣 The line has found its catch!");
        map.put("event.deep_lucky_block.bob.narration", "Bob 'The Eternal Fisher' is here... and he looks angry.");
        map.put("event.deep_lucky_block.bob_the_eternal_fisher.title", "A fishing line snaps taut...");
        map.put("event.deep_lucky_block.cat_invasion.care_message", "Take good care of them for me, please.");
        map.put("event.deep_lucky_block.cat_invasion.title", "Soft paws pad closer...");
        map.put("event.deep_lucky_block.cat_invasion_dewey.title", "Two golden eyes watch from the dark...");
        map.put("event.deep_lucky_block.cavern.action", "⚠ The darkness below is awake!");
        map.put("event.deep_lucky_block.cavern.narration", "The ground opens... and the darkness breathes.");
        map.put("event.deep_lucky_block.cavern_of_despair.title", "The floor gives way...");
        map.put("event.deep_lucky_block.chaos_terraform.action", "☠ Reality is losing its shape!");
        map.put("event.deep_lucky_block.chaos_terraform.narration", "Reality warps... the ground betrays you.");
        map.put("event.deep_lucky_block.chaos_terraform.title", "The land begins to warp...");
        map.put("event.deep_lucky_block.charged_creeper_execution.title", "A blue circle begins to hiss...");
        map.put("event.deep_lucky_block.chest_mimic_field.title", "Too many locks click open at once...");
        map.put("event.deep_lucky_block.cobweb_brood.title", "The landscape begins to grow threads...");
        map.put("event.deep_lucky_block.creeper_party.action", "Don't move...");
        map.put("event.deep_lucky_block.creeper_party.title", "A surprise is brewing...");
        map.put("event.deep_lucky_block.cursed_armor.narration", "Your armor refuses to let go...");
        map.put("event.deep_lucky_block.cursed_armor.title", "Your gear clings too tight...");
        map.put("event.deep_lucky_block.cursed_bell.action", "The bell tolls again...");
        map.put("event.deep_lucky_block.cursed_bell.title", "A bell starts to toll...");
        map.put("event.deep_lucky_block.cursed_tomb.action", "💀 The dead remember you!");
        map.put("event.deep_lucky_block.cursed_tomb.narration", "An ancient family tomb tears through the ground...");
        map.put("event.deep_lucky_block.cursed_tomb.title", "An old tomb crawls upward...");
        map.put("event.deep_lucky_block.dewey.heal_message", "Dewey heals you while he stays close.");
        map.put("event.deep_lucky_block.dragon_illusion.action", "Something enormous is directly above you...");
        map.put("event.deep_lucky_block.dragon_illusion.title", "Vast wings beat above...");
        map.put("event.deep_lucky_block.eldritch_whispers.narration", "Ancient whispers flood your mind...");
        map.put("event.deep_lucky_block.eldritch_whispers.title", "Old voices fill your mind...");
        map.put("event.deep_lucky_block.end_crystal_minefield.title", "Glass stars ignite across the horizon...");
        map.put("event.deep_lucky_block.end_knight.action", "⚔ %s figures step through the tears!");
        map.put("event.deep_lucky_block.end_knight.narration", "%s armored shapes materialize around you...");
        map.put("event.deep_lucky_block.end_knight_ambush.title", "Tears open in the air...");
        map.put("event.deep_lucky_block.fake_diamond.action", "✦ DIAMONDS!!! ✦");
        map.put("event.deep_lucky_block.fake_diamond.title", "Gems twinkle nearby...");
        map.put("event.deep_lucky_block.family.auntie_helene", "Auntie Helene");
        map.put("event.deep_lucky_block.family.auntie_josiane", "Auntie Josiane");
        map.put("event.deep_lucky_block.family.auntie_monique", "Auntie Monique");
        map.put("event.deep_lucky_block.family.cousin_brigitte", "Cousin Brigitte");
        map.put("event.deep_lucky_block.family.cousin_didier", "Cousin Didier");
        map.put("event.deep_lucky_block.family.cousin_george", "Cousin George");
        map.put("event.deep_lucky_block.family.cousin_kevin", "Cousin Kevin");
        map.put("event.deep_lucky_block.family.grandma_odette", "Grandma Odette");
        map.put("event.deep_lucky_block.family.grandpa_albert", "Grandpa Albert");
        map.put("event.deep_lucky_block.family.grandpa_marcel", "Grandpa Marcel");
        map.put("event.deep_lucky_block.family.grandpa_roger", "Grandpa Roger");
        map.put("event.deep_lucky_block.family.granny_lucienne", "Granny Lucienne");
        map.put("event.deep_lucky_block.family.uncle_bernard", "Uncle Bernard");
        map.put("event.deep_lucky_block.family.uncle_fernand", "Uncle Fernand");
        map.put("event.deep_lucky_block.family.uncle_jean_pierre", "Uncle Jean-Pierre");
        map.put("event.deep_lucky_block.food_rot.title", "Something in your bags begins to spoil...");
        map.put("event.deep_lucky_block.gravity_well.narration", "The ground no longer wants you...");
        map.put("event.deep_lucky_block.gravity_well.title", "The world tugs at you...");
        map.put("event.deep_lucky_block.inventory_shuffle.narration", "Something is rummaging through your inventory...");
        map.put("event.deep_lucky_block.inventory_shuffle.title", "Your bags stir on their own...");
        map.put("event.deep_lucky_block.last_platform.title", "The world drops away below you...");
        map.put("event.deep_lucky_block.lava_cage.action", "The heat keeps rising...");
        map.put("event.deep_lucky_block.lava_cage.title", "Glass walls close in...");
        map.put("event.deep_lucky_block.magma_cube_monsoon.title", "Burning shapes gather in the clouds...");
        map.put("event.deep_lucky_block.mining_fatigue.narration", "Your arms feel like lead...");
        map.put("event.deep_lucky_block.mining_fatigue.title", "Your arms turn to lead...");
        map.put("event.deep_lucky_block.mining_rollback.action", "☠ Your tools refuse to obey!");
        map.put("event.deep_lucky_block.mining_rollback.narration", "Green mist... your tools suddenly feel useless.");
        map.put("event.deep_lucky_block.mining_rollback.title", "Your tools suddenly feel wrong...");
        map.put("event.deep_lucky_block.mob_invasion.title", "Footsteps gather from every direction...");
        map.put("event.deep_lucky_block.molten_footsteps.action", "Don't stop moving...");
        map.put("event.deep_lucky_block.molten_footsteps.title", "The ground grows warm...");
        map.put("event.deep_lucky_block.name.deep_friend", "Deep's Friend");
        map.put("event.deep_lucky_block.name.diamond", "✦ Diamond ✦");
        map.put("event.deep_lucky_block.name.end_knight", "End Knight");
        map.put("event.deep_lucky_block.name.enraged_wolf", "Enraged Wolf");
        map.put("event.deep_lucky_block.name.eternal_fisher", "Bob 'The Eternal Fisher'");
        map.put("event.deep_lucky_block.name.eternal_rod", "✦ Eternal Fisher's Rod ✦");
        map.put("event.deep_lucky_block.name.lucky_snow_golem", "Lucky Snow Golem");
        map.put("event.deep_lucky_block.name.mysterious_loot", "✦ Mysterious Loot ✦");
        map.put("event.deep_lucky_block.name.ninja_assassin", "Ninja Assassin");
        map.put("event.deep_lucky_block.name.shadow_disciple", "Shadow Disciple");
        map.put("event.deep_lucky_block.name.silent_blade", "Silent Blade");
        map.put("event.deep_lucky_block.name.sky_blade", "✦ Celestial Sky Blade ✦");
        map.put("event.deep_lucky_block.name.team_claquette", "THE SLIPPER SQUAD");
        map.put("event.deep_lucky_block.name.ultimate_reward", "✦ ULTIMATE SANCTUARY REWARD ✦");
        map.put("event.deep_lucky_block.nether_breach.title", "The ground splits with a foreign heat...");
        map.put("event.deep_lucky_block.nether_warp.action", "🔥 The portal has you now!");
        map.put("event.deep_lucky_block.nether_warp.narration", "The portal closes behind you...");
        map.put("event.deep_lucky_block.nether_warp.no_safe_spot", "⚠ The portal cannot find a destination...");
        map.put("event.deep_lucky_block.nether_warp.refused", "⚠ The portal spits you back out...");
        map.put("event.deep_lucky_block.nether_warp.title", "A portal drags at you...");
        map.put("event.deep_lucky_block.ninja.action", "🥷 Something is following you...");
        map.put("event.deep_lucky_block.ninja.narration", "Eyes watch from the shadows...");
        map.put("event.deep_lucky_block.ninja_assassin.title", "Unseen eyes are watching...");
        map.put("event.deep_lucky_block.pb_block_grave.action", "⚠ The shadows overhead are growing!");
        map.put("event.deep_lucky_block.pb_block_grave.title", "Stone rains from above...");
        map.put("event.deep_lucky_block.pb_block_shower.action", "⚠ Debris is everywhere!");
        map.put("event.deep_lucky_block.pb_block_shower.title", "Debris showers down...");
        map.put("event.deep_lucky_block.pb_buffed_down.action", "⚠ Strength is draining away!");
        map.put("event.deep_lucky_block.pb_buffed_down.title", "Strength flees the area...");
        map.put("event.deep_lucky_block.pb_classic.action", "The towers keep growing!");
        map.put("event.deep_lucky_block.pb_classic.title", "Odd towers sprout up...");
        map.put("event.deep_lucky_block.pb_crazy_port.action", "⚠ Reality has moved!");
        map.put("event.deep_lucky_block.pb_crazy_port.title", "Reality yanks you away...");
        map.put("event.deep_lucky_block.pb_creeper_soul.action", "⚠ %s shockwaves!");
        map.put("event.deep_lucky_block.pb_creeper_soul.title", "The air crackles hot...");
        map.put("event.deep_lucky_block.pb_crush.action", "⚠ The pressure is unbearable!");
        map.put("event.deep_lucky_block.pb_crush.title", "An unseen weight presses...");
        map.put("event.deep_lucky_block.pb_danger_call.action", "⚠ %s ticking shapes overhead!");
        map.put("event.deep_lucky_block.pb_danger_call.title", "Ticking fills the air...");
        map.put("event.deep_lucky_block.pb_double_snake.action", "The world bends in two places!");
        map.put("event.deep_lucky_block.pb_double_snake.title", "Twin shapes wind above...");
        map.put("event.deep_lucky_block.pb_dryness.action", "⚠ The land is turning to dust!");
        map.put("event.deep_lucky_block.pb_dryness.title", "The land grows parched...");
        map.put("event.deep_lucky_block.pb_end.action", "The horizon no longer looks real...");
        map.put("event.deep_lucky_block.pb_height_noise.action", "⚠ THE GROUND IS MOVING!");
        map.put("event.deep_lucky_block.pb_height_noise.title", "The ground heaves upward...");
        map.put("event.deep_lucky_block.pb_hell.action", "The air itself is burning...");
        map.put("event.deep_lucky_block.pb_hell_on_earth.title", "Heat wells up from below...");
        map.put("event.deep_lucky_block.pb_ice_age.action", "⚠ The cold is inside you!");
        map.put("event.deep_lucky_block.pb_ice_age.title", "A deep frost settles...");
        map.put("event.deep_lucky_block.pb_in_the_end.title", "Reality frays at the edges...");
        map.put("event.deep_lucky_block.pb_lifeless.action", "Everything has gone silent...");
        map.put("event.deep_lucky_block.pb_lifeless.title", "Life drains from the land...");
        map.put("event.deep_lucky_block.pb_lightning.action", "⚡ %s flashes split the sky!");
        map.put("event.deep_lucky_block.pb_lightning.title", "The storm tears open...");
        map.put("event.deep_lucky_block.pb_mad_geometry.action", "⚠ Reality is growing corners!");
        map.put("event.deep_lucky_block.pb_mad_geometry.title", "Strange shapes bloom...");
        map.put("event.deep_lucky_block.pb_madder_geometry.action", "⚠ The shapes will not stop!");
        map.put("event.deep_lucky_block.pb_madder_geometry.title", "Madness takes solid form...");
        map.put("event.deep_lucky_block.pb_megaton.action", "⚠ The sky is falling!");
        map.put("event.deep_lucky_block.pb_megaton.title", "Heavy things plummet...");
        map.put("event.deep_lucky_block.pb_mob_towers.action", "☠ They are climbing over each other!");
        map.put("event.deep_lucky_block.pb_mob_towers.title", "They climb upon each other...");
        map.put("event.deep_lucky_block.pb_mobs.action", "☠ %s hostile shapes are closing in!");
        map.put("event.deep_lucky_block.pb_mobs.title", "They are closing in...");
        map.put("event.deep_lucky_block.pb_replace.action", "⚠ The world is being rewritten!");
        map.put("event.deep_lucky_block.pb_replace.title", "The earth is rewritten...");
        map.put("event.deep_lucky_block.pb_sand.action", "The heat is unbearable!");
        map.put("event.deep_lucky_block.pb_sand_for_dessert.title", "Dust chokes the air...");
        map.put("event.deep_lucky_block.pb_target.action", "⚠ The marks are not empty!");
        map.put("event.deep_lucky_block.pb_target.title", "Marks bloom on the ground...");
        map.put("event.deep_lucky_block.pb_tele_random.action", "⚠ Space has lost its grip!");
        map.put("event.deep_lucky_block.pb_tele_random.title", "Space folds around you...");
        map.put("event.deep_lucky_block.pb_tnt.action", "⚠ %s fuses are burning!");
        map.put("event.deep_lucky_block.pb_tnt_splosion.title", "Fuses spark all around...");
        map.put("event.deep_lucky_block.pb_transform.action", "⚠ The ground is changing!");
        map.put("event.deep_lucky_block.pb_transform.title", "The ground shifts its skin...");
        map.put("event.deep_lucky_block.pb_trapped_tribe.action", "⚠ The sky has teeth!");
        map.put("event.deep_lucky_block.pb_trapped_tribe.title", "Steel whispers down...");
        map.put("event.deep_lucky_block.pb_tunnel.action", "⚠ Something has opened a path below!");
        map.put("event.deep_lucky_block.pb_tunnel_bore.title", "Something bores below...");
        map.put("event.deep_lucky_block.pb_void.action", "⚠ DO NOT LOOK DOWN!");
        map.put("event.deep_lucky_block.pb_void.title", "The ground opens beneath...");
        map.put("event.deep_lucky_block.pb_world_snake.action", "Something is carving through the world!");
        map.put("event.deep_lucky_block.pb_world_snake.title", "Something coils above...");
        map.put("event.deep_lucky_block.phantom_rain.action", "☁ %s shapes are diving!");
        map.put("event.deep_lucky_block.phantom_rain.narration", "%s shapes fall out of the sky...");
        map.put("event.deep_lucky_block.phantom_rain.title", "Wings circle overhead...");
        map.put("event.deep_lucky_block.pillager_ambush.action", "⚔ Crossbows in every direction!");
        map.put("event.deep_lucky_block.pillager_ambush.narration", "Figures rush out from nowhere!");
        map.put("event.deep_lucky_block.pillager_ambush.title", "Crossbows cock nearby...");
        map.put("event.deep_lucky_block.poison_cloud.narration", "A sickly green cloud escapes...");
        map.put("event.deep_lucky_block.poison_cloud.title", "A bitter fog creeps in...");
        map.put("event.deep_lucky_block.poisoned_gift.action", "✦ AN ULTIMATE REWARD APPEARED! CLAIM IT! ✦");
        map.put("event.deep_lucky_block.poisoned_gift.clicked", "CONGRATULATIONS! REWARD CLAIMED!");
        map.put("event.deep_lucky_block.poisoned_gift.narration", "Fortune has left something behind...");
        map.put("event.deep_lucky_block.poisoned_gift.opened", "💥 The gift bites back. 💥");
        map.put("event.deep_lucky_block.poisoned_gift.title", "Congratulations! Your reward is waiting...");
        map.put("event.deep_lucky_block.sand_hourglass.title", "Time begins to pour above you...");
        map.put("event.deep_lucky_block.shadow_disciples.title", "Your equipment casts more than one shadow...");
        map.put("event.deep_lucky_block.shadow_mirror.action", "👤 Your shadow is no longer yours!");
        map.put("event.deep_lucky_block.shadow_mirror.narration", "A familiar silhouette steps out of the dark...");
        map.put("event.deep_lucky_block.shadow_mirror_spawn.title", "Your shadow steps free...");
        map.put("event.deep_lucky_block.sign.by", "BY");
        map.put("event.deep_lucky_block.sign.state", "THE STATE");
        map.put("event.deep_lucky_block.sign.taxed", "TAXED");
        map.put("event.deep_lucky_block.silverfish.action", "🐛 The stone is moving!");
        map.put("event.deep_lucky_block.silverfish.narration", "The stone cracks... and starts crawling.");
        map.put("event.deep_lucky_block.silverfish_infestation.title", "The stone starts to writhe...");
        map.put("event.deep_lucky_block.skeleton_firing_squad.title", "Two rings of bones take aim...");
        map.put("event.deep_lucky_block.sky_loot.action", "✦ Something brilliant is falling nearby... ✦");
        map.put("event.deep_lucky_block.sky_loot.narration", "A streak of gold cuts across the sky...");
        map.put("event.deep_lucky_block.sky_loot.no_ground", "⚠ The falling light vanished before landing.");
        map.put("event.deep_lucky_block.sky_loot.pickup", "Something clicks beneath your feet...");
        map.put("event.deep_lucky_block.sky_loot.pickup_narration", "That sound came from below...");
        map.put("event.deep_lucky_block.sky_loot.title", "A gleam drops from above...");
        map.put("event.deep_lucky_block.slime_king.action", "👑 Something enormous has awakened! 👑");
        map.put("event.deep_lucky_block.slime_king.narration", "The ground trembles... a crowned shape rises!");
        map.put("event.deep_lucky_block.slime_king_boss.title", "Something huge stirs below...");
        map.put("event.deep_lucky_block.solar_eclipse.action", "The daylight is being swallowed...");
        map.put("event.deep_lucky_block.solar_eclipse.end", "✦ Daylight returns!");
        map.put("event.deep_lucky_block.solar_eclipse.title", "The sun begins to fade...");
        map.put("event.deep_lucky_block.spectral_arrow_shower.title", "Arrows hum overhead...");
        map.put("event.deep_lucky_block.spectral_arrows.action", "⚠ THE SKY IS WHISTLING!");
        map.put("event.deep_lucky_block.the_french_tax.narration", "The state has claimed %s.");
        map.put("event.deep_lucky_block.the_french_tax.nothing", "nothing");
        map.put("event.deep_lucky_block.the_french_tax.taken", "💸 The tax collector took: %s");
        map.put("event.deep_lucky_block.the_french_tax.title", "You hear coins jingling...");
        map.put("event.deep_lucky_block.tnt_sky_serpent.title", "A distant line begins to fall...");
        map.put("event.deep_lucky_block.void_pocket.action", "⚠ THE GROUND IS GONE!");
        map.put("event.deep_lucky_block.void_pocket.narration", "The ground collapses... something glows far below.");
        map.put("event.deep_lucky_block.void_pocket.title", "The ground feels too thin...");
        map.put("event.deep_lucky_block.warden.action", "⚠ SOMETHING IS WAKING UP... ⚠");
        map.put("event.deep_lucky_block.warden.heard", "It heard you...");
        map.put("event.deep_lucky_block.warden_scream.title", "The earth shudders deep...");
        map.put("event.deep_lucky_block.wither_curse.narration", "A dark aura closes around you...");
        map.put("event.deep_lucky_block.wither_curse.title", "A chill bites your bones...");
        map.put("event.deep_lucky_block.wolf_pack.action", "🐺 %s pairs of eyes surround you!");
        map.put("event.deep_lucky_block.wolf_pack.narration", "GRRRR! The pack closes in!");
        map.put("event.deep_lucky_block.wolf_pack.title", "Howls close in around you...");
        map.put("event.deep_lucky_block.youve_been_slimed.action", "YOU'VE BEEN SLIMED!");
        map.put("event.deep_lucky_block.youve_been_slimed.narration", "Slime everywhere... yuck.");
        map.put("event.deep_lucky_block.youve_been_slimed.title", "Something wet and green...");
        loadJsonResource(map, "/deepluckyblock_patch/lang/en_us.json");
        loadJsonResource(map, "/assets/deep_lucky_block/lang/en_us.json");
        return Map.copyOf(map);
    }

    private static Map<String, String> createFrench() {
        Map<String, String> map = new HashMap<>();
        map.put("event.deep_lucky_block.acid_rain.action", "☠ L’air vous brûle les poumons... ☠");
        map.put("event.deep_lucky_block.acid_rain.title", "L’air devient âcre...");
        map.put("event.deep_lucky_block.angry_bees.action", "🐝 Le bourdonnement est partout !");
        map.put("event.deep_lucky_block.angry_bees.narration", "BZZZZZ ! Elles n’ont pas l’air contentes...");
        map.put("event.deep_lucky_block.angry_bees.title", "Un bourdonnement enfle dans l’air...");
        map.put("event.deep_lucky_block.anvil_rain.action", "⚠ PLUIE D’ENCLUMES ! REGARDEZ EN L’AIR !");
        map.put("event.deep_lucky_block.anvil_rain.title", "Vous feriez mieux de regarder le ciel...");
        map.put("event.deep_lucky_block.arena.action", "⚔ Ces murs n’ont aucune sortie !");
        map.put("event.deep_lucky_block.arena.narration", "Une arène se matérialise... quelque chose attend à l’intérieur.");
        map.put("event.deep_lucky_block.arena_of_the_forgotten.title", "Des murs s’élèvent pour vous enfermer...");
        map.put("event.deep_lucky_block.blind_trap.narration", "Tout devient noir...");
        map.put("event.deep_lucky_block.blind_trap.title", "La lumière s’éteint...");
        map.put("event.deep_lucky_block.bob.action", "🎣 La ligne a trouvé sa prise !");
        map.put("event.deep_lucky_block.bob.narration", "Bob « le Pêcheur éternel » est là... et il semble furieux.");
        map.put("event.deep_lucky_block.bob_the_eternal_fisher.title", "Une ligne de pêche se tend brusquement...");
        map.put("event.deep_lucky_block.cat_invasion.care_message", "Prends bien soin d’eux pour moi, s’il te plaît.");
        map.put("event.deep_lucky_block.cat_invasion.title", "De petites pattes approchent...");
        map.put("event.deep_lucky_block.cat_invasion_dewey.title", "Deux yeux dorés vous observent dans l’obscurité...");
        map.put("event.deep_lucky_block.cavern.action", "⚠ L’obscurité souterraine s’est réveillée !");
        map.put("event.deep_lucky_block.cavern.narration", "Le sol s’ouvre... et l’obscurité respire.");
        map.put("event.deep_lucky_block.cavern_of_despair.title", "Le sol se dérobe...");
        map.put("event.deep_lucky_block.chaos_terraform.action", "☠ La réalité perd sa forme !");
        map.put("event.deep_lucky_block.chaos_terraform.narration", "La réalité se déforme... le sol vous trahit.");
        map.put("event.deep_lucky_block.chaos_terraform.title", "La terre commence à se déformer...");
        map.put("event.deep_lucky_block.charged_creeper_execution.title", "Un cercle bleu se met à siffler...");
        map.put("event.deep_lucky_block.chest_mimic_field.title", "Trop de serrures s’ouvrent en même temps...");
        map.put("event.deep_lucky_block.cobweb_brood.title", "Le paysage commence à se couvrir de fils...");
        map.put("event.deep_lucky_block.creeper_party.action", "Ne bougez plus...");
        map.put("event.deep_lucky_block.creeper_party.title", "Une surprise se prépare...");
        map.put("event.deep_lucky_block.cursed_armor.narration", "Votre armure refuse de vous lâcher...");
        map.put("event.deep_lucky_block.cursed_armor.title", "Votre équipement se resserre...");
        map.put("event.deep_lucky_block.cursed_bell.action", "La cloche sonne de nouveau...");
        map.put("event.deep_lucky_block.cursed_bell.title", "Une cloche se met à sonner...");
        map.put("event.deep_lucky_block.cursed_tomb.action", "💀 Les morts se souviennent de vous !");
        map.put("event.deep_lucky_block.cursed_tomb.narration", "Une ancienne tombe familiale déchire le sol...");
        map.put("event.deep_lucky_block.cursed_tomb.title", "Une vieille tombe remonte à la surface...");
        map.put("event.deep_lucky_block.dewey.heal_message", "Dewey te soigne tant qu’il reste près de toi.");
        map.put("event.deep_lucky_block.dragon_illusion.action", "Quelque chose d’énorme se trouve juste au-dessus de vous...");
        map.put("event.deep_lucky_block.dragon_illusion.title", "D’immenses ailes battent au-dessus...");
        map.put("event.deep_lucky_block.eldritch_whispers.narration", "Des murmures anciens envahissent votre esprit...");
        map.put("event.deep_lucky_block.eldritch_whispers.title", "De vieilles voix envahissent votre esprit...");
        map.put("event.deep_lucky_block.end_crystal_minefield.title", "Des étoiles de verre s’allument à l’horizon...");
        map.put("event.deep_lucky_block.end_knight.action", "⚔ %s silhouettes traversent les déchirures !");
        map.put("event.deep_lucky_block.end_knight.narration", "%s silhouettes en armure se matérialisent autour de vous...");
        map.put("event.deep_lucky_block.end_knight_ambush.title", "Des déchirures s’ouvrent dans l’air...");
        map.put("event.deep_lucky_block.fake_diamond.action", "✦ DIAMANTS !!! ✦");
        map.put("event.deep_lucky_block.fake_diamond.title", "Des gemmes scintillent tout près...");
        map.put("event.deep_lucky_block.family.auntie_helene", "Tatie Hélène");
        map.put("event.deep_lucky_block.family.auntie_josiane", "Tatie Josiane");
        map.put("event.deep_lucky_block.family.auntie_monique", "Tatie Monique");
        map.put("event.deep_lucky_block.family.cousin_brigitte", "Cousine Brigitte");
        map.put("event.deep_lucky_block.family.cousin_didier", "Cousin Didier");
        map.put("event.deep_lucky_block.family.cousin_george", "Cousin George");
        map.put("event.deep_lucky_block.family.cousin_kevin", "Cousin Kevin");
        map.put("event.deep_lucky_block.family.grandma_odette", "Mamie Odette");
        map.put("event.deep_lucky_block.family.grandpa_albert", "Papi Albert");
        map.put("event.deep_lucky_block.family.grandpa_marcel", "Papi Marcel");
        map.put("event.deep_lucky_block.family.grandpa_roger", "Papi Roger");
        map.put("event.deep_lucky_block.family.granny_lucienne", "Mamie Lucienne");
        map.put("event.deep_lucky_block.family.uncle_bernard", "Oncle Bernard");
        map.put("event.deep_lucky_block.family.uncle_fernand", "Oncle Fernand");
        map.put("event.deep_lucky_block.family.uncle_jean_pierre", "Oncle Jean-Pierre");
        map.put("event.deep_lucky_block.food_rot.title", "Quelque chose pourrit dans vos sacs...");
        map.put("event.deep_lucky_block.gravity_well.narration", "Le sol ne veut plus de vous...");
        map.put("event.deep_lucky_block.gravity_well.title", "Le monde vous attire...");
        map.put("event.deep_lucky_block.inventory_shuffle.narration", "Quelque chose fouille dans votre inventaire...");
        map.put("event.deep_lucky_block.inventory_shuffle.title", "Votre sac remue tout seul...");
        map.put("event.deep_lucky_block.last_platform.title", "Le monde disparaît sous vos pieds...");
        map.put("event.deep_lucky_block.lava_cage.action", "La chaleur continue de monter...");
        map.put("event.deep_lucky_block.lava_cage.title", "Des parois de verre se referment...");
        map.put("event.deep_lucky_block.magma_cube_monsoon.title", "Des formes brûlantes se rassemblent dans les nuages...");
        map.put("event.deep_lucky_block.mining_fatigue.narration", "Vos bras semblent faits de plomb...");
        map.put("event.deep_lucky_block.mining_fatigue.title", "Vos bras deviennent lourds comme du plomb...");
        map.put("event.deep_lucky_block.mining_rollback.action", "☠ Vos outils refusent d’obéir !");
        map.put("event.deep_lucky_block.mining_rollback.narration", "Une brume verte... vos outils semblent soudain inutiles.");
        map.put("event.deep_lucky_block.mining_rollback.title", "Vos outils semblent soudain anormaux...");
        map.put("event.deep_lucky_block.mob_invasion.title", "Des pas convergent de toutes les directions...");
        map.put("event.deep_lucky_block.molten_footsteps.action", "Ne vous arrêtez surtout pas...");
        map.put("event.deep_lucky_block.molten_footsteps.title", "Le sol se réchauffe...");
        map.put("event.deep_lucky_block.name.deep_friend", "Copain de Deep");
        map.put("event.deep_lucky_block.name.diamond", "✦ Diamant ✦");
        map.put("event.deep_lucky_block.name.end_knight", "Chevalier de l’End");
        map.put("event.deep_lucky_block.name.enraged_wolf", "Loup enragé");
        map.put("event.deep_lucky_block.name.eternal_fisher", "Bob « le Pêcheur éternel »");
        map.put("event.deep_lucky_block.name.eternal_rod", "✦ Canne du Pêcheur éternel ✦");
        map.put("event.deep_lucky_block.name.lucky_snow_golem", "Golem de neige chanceux");
        map.put("event.deep_lucky_block.name.mysterious_loot", "✦ Butin mystérieux ✦");
        map.put("event.deep_lucky_block.name.ninja_assassin", "Assassin ninja");
        map.put("event.deep_lucky_block.name.shadow_disciple", "Disciple des ombres");
        map.put("event.deep_lucky_block.name.silent_blade", "Lame silencieuse");
        map.put("event.deep_lucky_block.name.sky_blade", "✦ Lame céleste ✦");
        map.put("event.deep_lucky_block.name.team_claquette", "LA TEAM CLAQUETTE");
        map.put("event.deep_lucky_block.name.ultimate_reward", "✦ RÉCOMPENSE ULTIME DU SANCTUAIRE ✦");
        map.put("event.deep_lucky_block.nether_breach.title", "Le sol se fend sous une chaleur étrangère...");
        map.put("event.deep_lucky_block.nether_warp.action", "🔥 Le portail vous tient !");
        map.put("event.deep_lucky_block.nether_warp.narration", "Le portail se referme derrière vous...");
        map.put("event.deep_lucky_block.nether_warp.no_safe_spot", "⚠ Le portail ne trouve aucune destination...");
        map.put("event.deep_lucky_block.nether_warp.refused", "⚠ Le portail vous recrache...");
        map.put("event.deep_lucky_block.nether_warp.title", "Un portail vous attire...");
        map.put("event.deep_lucky_block.ninja.action", "🥷 Quelque chose vous suit...");
        map.put("event.deep_lucky_block.ninja.narration", "Des yeux vous observent depuis les ombres...");
        map.put("event.deep_lucky_block.ninja_assassin.title", "Des yeux invisibles vous observent...");
        map.put("event.deep_lucky_block.pb_block_grave.action", "⚠ Les ombres au-dessus de vous grandissent !");
        map.put("event.deep_lucky_block.pb_block_grave.title", "Des pierres pleuvent du ciel...");
        map.put("event.deep_lucky_block.pb_block_shower.action", "⚠ Des débris tombent partout !");
        map.put("event.deep_lucky_block.pb_block_shower.title", "Des débris tombent en pluie...");
        map.put("event.deep_lucky_block.pb_buffed_down.action", "⚠ Toute force vous abandonne !");
        map.put("event.deep_lucky_block.pb_buffed_down.title", "Toute force abandonne les environs...");
        map.put("event.deep_lucky_block.pb_classic.action", "Les tours continuent de grandir !");
        map.put("event.deep_lucky_block.pb_classic.title", "D’étranges tours jaillissent...");
        map.put("event.deep_lucky_block.pb_crazy_port.action", "⚠ La réalité s’est déplacée !");
        map.put("event.deep_lucky_block.pb_crazy_port.title", "La réalité vous arrache à cet endroit...");
        map.put("event.deep_lucky_block.pb_creeper_soul.action", "⚠ %s ondes de choc !");
        map.put("event.deep_lucky_block.pb_creeper_soul.title", "L’air crépite de chaleur...");
        map.put("event.deep_lucky_block.pb_crush.action", "⚠ La pression est insupportable !");
        map.put("event.deep_lucky_block.pb_crush.title", "Un poids invisible vous écrase...");
        map.put("event.deep_lucky_block.pb_danger_call.action", "⚠ %s formes font tic-tac au-dessus !");
        map.put("event.deep_lucky_block.pb_danger_call.title", "Un tic-tac emplit l’air...");
        map.put("event.deep_lucky_block.pb_double_snake.action", "Le monde se déforme à deux endroits !");
        map.put("event.deep_lucky_block.pb_double_snake.title", "Deux formes serpentent dans le ciel...");
        map.put("event.deep_lucky_block.pb_dryness.action", "⚠ La terre se transforme en poussière !");
        map.put("event.deep_lucky_block.pb_dryness.title", "La terre s’assèche...");
        map.put("event.deep_lucky_block.pb_end.action", "L’horizon ne semble plus réel...");
        map.put("event.deep_lucky_block.pb_height_noise.action", "⚠ LE SOL EST EN TRAIN DE BOUGER !");
        map.put("event.deep_lucky_block.pb_height_noise.title", "Le sol se soulève...");
        map.put("event.deep_lucky_block.pb_hell.action", "L’air lui-même est en feu...");
        map.put("event.deep_lucky_block.pb_hell_on_earth.title", "La chaleur remonte des profondeurs...");
        map.put("event.deep_lucky_block.pb_ice_age.action", "⚠ Le froid est en vous !");
        map.put("event.deep_lucky_block.pb_ice_age.title", "Un froid profond s’installe...");
        map.put("event.deep_lucky_block.pb_in_the_end.title", "La réalité s’effiloche...");
        map.put("event.deep_lucky_block.pb_lifeless.action", "Tout est devenu silencieux...");
        map.put("event.deep_lucky_block.pb_lifeless.title", "La vie quitte la terre...");
        map.put("event.deep_lucky_block.pb_lightning.action", "⚡ %s éclairs déchirent le ciel !");
        map.put("event.deep_lucky_block.pb_lightning.title", "La tempête déchire le ciel...");
        map.put("event.deep_lucky_block.pb_mad_geometry.action", "⚠ La réalité se couvre d’angles !");
        map.put("event.deep_lucky_block.pb_mad_geometry.title", "Des formes étranges surgissent...");
        map.put("event.deep_lucky_block.pb_madder_geometry.action", "⚠ Les formes ne s’arrêtent plus !");
        map.put("event.deep_lucky_block.pb_madder_geometry.title", "La folie prend forme...");
        map.put("event.deep_lucky_block.pb_megaton.action", "⚠ Le ciel vous tombe dessus !");
        map.put("event.deep_lucky_block.pb_megaton.title", "De lourdes masses chutent...");
        map.put("event.deep_lucky_block.pb_mob_towers.action", "☠ Ils grimpent les uns sur les autres !");
        map.put("event.deep_lucky_block.pb_mob_towers.title", "Ils grimpent les uns sur les autres...");
        map.put("event.deep_lucky_block.pb_mobs.action", "☠ %s silhouettes hostiles se rapprochent !");
        map.put("event.deep_lucky_block.pb_mobs.title", "Ils se rapprochent...");
        map.put("event.deep_lucky_block.pb_replace.action", "⚠ Le monde est en train d’être réécrit !");
        map.put("event.deep_lucky_block.pb_replace.title", "La terre est réécrite...");
        map.put("event.deep_lucky_block.pb_sand.action", "La chaleur est insupportable !");
        map.put("event.deep_lucky_block.pb_sand_for_dessert.title", "La poussière étouffe l’air...");
        map.put("event.deep_lucky_block.pb_target.action", "⚠ Les marques ne sont pas vides !");
        map.put("event.deep_lucky_block.pb_target.title", "Des marques apparaissent sur le sol...");
        map.put("event.deep_lucky_block.pb_tele_random.action", "⚠ L’espace a perdu prise !");
        map.put("event.deep_lucky_block.pb_tele_random.title", "L’espace se replie autour de vous...");
        map.put("event.deep_lucky_block.pb_tnt.action", "⚠ %s mèches sont allumées !");
        map.put("event.deep_lucky_block.pb_tnt_splosion.title", "Des mèches crépitent tout autour...");
        map.put("event.deep_lucky_block.pb_transform.action", "⚠ Le sol est en train de changer !");
        map.put("event.deep_lucky_block.pb_transform.title", "Le sol change de peau...");
        map.put("event.deep_lucky_block.pb_trapped_tribe.action", "⚠ Le ciel a des crocs !");
        map.put("event.deep_lucky_block.pb_trapped_tribe.title", "L’acier murmure au-dessus...");
        map.put("event.deep_lucky_block.pb_tunnel.action", "⚠ Quelque chose a ouvert un passage sous terre !");
        map.put("event.deep_lucky_block.pb_tunnel_bore.title", "Quelque chose creuse sous vos pieds...");
        map.put("event.deep_lucky_block.pb_void.action", "⚠ NE REGARDEZ PAS EN BAS !");
        map.put("event.deep_lucky_block.pb_void.title", "Le sol s’ouvre sous vos pieds...");
        map.put("event.deep_lucky_block.pb_world_snake.action", "Quelque chose se fraie un chemin dans le monde !");
        map.put("event.deep_lucky_block.pb_world_snake.title", "Quelque chose ondule au-dessus...");
        map.put("event.deep_lucky_block.phantom_rain.action", "☁ %s silhouettes plongent !");
        map.put("event.deep_lucky_block.phantom_rain.narration", "%s silhouettes tombent du ciel...");
        map.put("event.deep_lucky_block.phantom_rain.title", "Des ailes tournent au-dessus de vous...");
        map.put("event.deep_lucky_block.pillager_ambush.action", "⚔ Des arbalètes dans toutes les directions !");
        map.put("event.deep_lucky_block.pillager_ambush.narration", "Des silhouettes surgissent de nulle part !");
        map.put("event.deep_lucky_block.pillager_ambush.title", "Des arbalètes s’arment tout près...");
        map.put("event.deep_lucky_block.poison_cloud.narration", "Un nuage vert et malsain s’échappe...");
        map.put("event.deep_lucky_block.poison_cloud.title", "Un brouillard amer s’insinue...");
        map.put("event.deep_lucky_block.poisoned_gift.action", "✦ UNE RÉCOMPENSE ULTIME EST APPARUE ! PRENEZ-LA ! ✦");
        map.put("event.deep_lucky_block.poisoned_gift.clicked", "FÉLICITATIONS ! RÉCOMPENSE RÉCUPÉRÉE !");
        map.put("event.deep_lucky_block.poisoned_gift.narration", "La fortune a laissé quelque chose derrière elle...");
        map.put("event.deep_lucky_block.poisoned_gift.opened", "💥 Le cadeau mord en retour. 💥");
        map.put("event.deep_lucky_block.poisoned_gift.title", "Félicitations ! Votre récompense vous attend...");
        map.put("event.deep_lucky_block.sand_hourglass.title", "Le temps commence à couler au-dessus de vous...");
        map.put("event.deep_lucky_block.shadow_disciples.title", "Votre équipement projette plusieurs ombres...");
        map.put("event.deep_lucky_block.shadow_mirror.action", "👤 Votre ombre ne vous appartient plus !");
        map.put("event.deep_lucky_block.shadow_mirror.narration", "Une silhouette familière sort de l’obscurité...");
        map.put("event.deep_lucky_block.shadow_mirror_spawn.title", "Votre ombre se libère...");
        map.put("event.deep_lucky_block.sign.by", "PAR");
        map.put("event.deep_lucky_block.sign.state", "L’ÉTAT");
        map.put("event.deep_lucky_block.sign.taxed", "TAXÉ");
        map.put("event.deep_lucky_block.silverfish.action", "🐛 La pierre bouge !");
        map.put("event.deep_lucky_block.silverfish.narration", "La pierre se fissure... puis se met à grouiller.");
        map.put("event.deep_lucky_block.silverfish_infestation.title", "La pierre commence à grouiller...");
        map.put("event.deep_lucky_block.skeleton_firing_squad.title", "Deux cercles d’os vous mettent en joue...");
        map.put("event.deep_lucky_block.sky_loot.action", "✦ Quelque chose de brillant tombe tout près... ✦");
        map.put("event.deep_lucky_block.sky_loot.narration", "Une traînée dorée fend le ciel...");
        map.put("event.deep_lucky_block.sky_loot.no_ground", "⚠ La lumière a disparu avant de toucher le sol.");
        map.put("event.deep_lucky_block.sky_loot.pickup", "Quelque chose clique sous vos pieds...");
        map.put("event.deep_lucky_block.sky_loot.pickup_narration", "Ce bruit venait d’en dessous...");
        map.put("event.deep_lucky_block.sky_loot.title", "Une lueur tombe du ciel...");
        map.put("event.deep_lucky_block.slime_king.action", "👑 Quelque chose d’énorme s’est réveillé ! 👑");
        map.put("event.deep_lucky_block.slime_king.narration", "Le sol tremble... une silhouette couronnée se lève !");
        map.put("event.deep_lucky_block.slime_king_boss.title", "Quelque chose d’énorme s’agite sous terre...");
        map.put("event.deep_lucky_block.solar_eclipse.action", "La lumière du jour se fait dévorer...");
        map.put("event.deep_lucky_block.solar_eclipse.end", "✦ La lumière du jour revient !");
        map.put("event.deep_lucky_block.solar_eclipse.title", "Le soleil commence à disparaître...");
        map.put("event.deep_lucky_block.spectral_arrow_shower.title", "Des flèches sifflent au-dessus...");
        map.put("event.deep_lucky_block.spectral_arrows.action", "⚠ LE CIEL SE MET À SIFFLER !");
        map.put("event.deep_lucky_block.the_french_tax.narration", "L’État a réquisitionné %s.");
        map.put("event.deep_lucky_block.the_french_tax.nothing", "rien");
        map.put("event.deep_lucky_block.the_french_tax.taken", "💸 Le percepteur a pris : %s");
        map.put("event.deep_lucky_block.the_french_tax.title", "Vous entendez des pièces s’entrechoquer...");
        map.put("event.deep_lucky_block.tnt_sky_serpent.title", "Une ligne lointaine commence à tomber...");
        map.put("event.deep_lucky_block.void_pocket.action", "⚠ LE SOL A DISPARU !");
        map.put("event.deep_lucky_block.void_pocket.narration", "Le sol s’effondre... quelque chose brille tout en bas.");
        map.put("event.deep_lucky_block.void_pocket.title", "Le sol semble beaucoup trop fragile...");
        map.put("event.deep_lucky_block.warden.action", "⚠ QUELQUE CHOSE SE RÉVEILLE... ⚠");
        map.put("event.deep_lucky_block.warden.heard", "Il vous a entendu...");
        map.put("event.deep_lucky_block.warden_scream.title", "La terre tremble en profondeur...");
        map.put("event.deep_lucky_block.wither_curse.narration", "Une aura sombre se referme autour de vous...");
        map.put("event.deep_lucky_block.wither_curse.title", "Un froid mord vos os...");
        map.put("event.deep_lucky_block.wolf_pack.action", "🐺 %s paires d’yeux vous encerclent !");
        map.put("event.deep_lucky_block.wolf_pack.narration", "GRRRR ! La meute se rapproche !");
        map.put("event.deep_lucky_block.wolf_pack.title", "Des hurlements se rapprochent...");
        map.put("event.deep_lucky_block.youve_been_slimed.action", "VOUS ÊTES COUVERT DE SLIME !");
        map.put("event.deep_lucky_block.youve_been_slimed.narration", "Du slime partout... beurk.");
        map.put("event.deep_lucky_block.youve_been_slimed.title", "Quelque chose d’humide et de vert...");
        loadJsonResource(map, "/deepluckyblock_patch/lang/fr_fr.json");
        loadJsonResource(map, "/assets/deep_lucky_block/lang/fr_fr.json");
        return Map.copyOf(map);
    }
}
