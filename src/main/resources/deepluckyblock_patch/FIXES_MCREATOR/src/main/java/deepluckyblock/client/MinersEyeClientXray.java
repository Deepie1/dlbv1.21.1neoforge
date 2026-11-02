package deepluckyblock.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import deepluckyblock.util.DeepEnchant;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.textures.FluidSpriteCache;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * T341-MINERS-EYE-XRAY : Vision X-Ray locale hybride 100 % client pour {@code miners_eye} (Œil du Mineur) :
 * <ul>
 *   <li>Rayon sphérique dans toutes les directions : Niv. I = 6, II = 12, III = 18, IV = 24,
 *       Niv. V (max) = <b>30 blocs</b>.</li>
 *   <li>Ouvert au moddé : conserve tous les minerais ({@code #c:ores} / {@link Tags.Blocks#ORES} + heuristiques
 *       {@code *_ore*}, {@code raw_*_block}, {@code ancient_debris}) ainsi que l'eau, la lave et <b>tous les
 *       liquides moddés</b> ({@code !FluidState.isEmpty()}).</li>
 *   <li><b>Mode Hybride (Option 3)</b> :
 *       <ol>
 *         <li>En moteur Vanilla NeoForge : rend réellement la roche semi-transparente (~24 % d'opacité sur la
 *             paroi extérieure, intérieur roche-contre-roche masqué pour 0 empilement et 144+ FPS) via Mixins
 *             de compilation de sections, avec rafraîchissement différentiel uniquement sur la coquille sphérique.</li>
 *         <li>Si Sodium / Embeddium / Rubidium est détecté (ou si le compilateur vanilla est court-circuité) :
 *             bascule automatiquement vers une passe X-Ray 3D texturée directe ({@link RenderLevelStageEvent})
 *             dessinant les vrais modèles 3D de minerais et fluides à travers la roche en un seul lot GPU.</li>
 *       </ol>
 *   </li>
 * </ul>
 */
@EventBusSubscriber(modid = "deep_lucky_block", value = Dist.CLIENT)
public final class MinersEyeClientXray {

    public static final int MAX_RADIUS_BLOCKS = 30;
    public static final int FADED_SHELL_ALPHA = 62; // ~24% d'opacité sur la paroi de grotte/sol

    private static final Direction[] DIRS = Direction.values();
    private static final Map<Block, Boolean> ORE_BLOCK_CACHE = new ConcurrentHashMap<>(512);

    private static volatile boolean sodiumLikeDetected = false;
    private static volatile boolean modCheckDone = false;
    private static volatile boolean vanillaCompilerSeen = false;

    // État partagé sans verrou avec les threads de SectionCompiler
    private static volatile int activeLevel = 0;
    private static volatile int activeRadius = 0;
    private static volatile int activeRadiusSq = 0;
    private static volatile int anchorX = 0;
    private static volatile int anchorY = 0;
    private static volatile int anchorZ = 0;
    private static volatile String activeDimension = "";

    // Suivi de déplacement pour rebuild différentiel (Mode 1 Vanilla)
    private static double lastBakedX = Double.NaN;
    private static double lastBakedY = Double.NaN;
    private static double lastBakedZ = Double.NaN;
    private static int lastBakedRadius = 0;

    // Cache de scan rapide par LevelChunkSection (Mode 2 Fallback Sodium/Embeddium)
    private record FallbackEntry(int x, int y, int z, BlockState state, byte faceMask, boolean fluid, int distSq) {}
    private static volatile List<FallbackEntry> fallbackEntries = List.of();
    private static int fallbackScanCooldown = 0;
    private static double lastScanX = Double.NaN;
    private static double lastScanY = Double.NaN;
    private static double lastScanZ = Double.NaN;
    private static int lastScanRadius = 0;

    private static RenderType xraySeeThroughType;

    private MinersEyeClientXray() {}

    /** Appelé par {@code SectionCompilerXrayMixin} dès qu'une section passe par le compilateur vanilla. */
    public static void markVanillaSectionCompilerSeen() {
        vanillaCompilerSeen = true;
    }

    public static boolean isSodiumLikeLoaded() {
        if (!modCheckDone) {
            try {
                ModList ml = ModList.get();
                if (ml != null) {
                    sodiumLikeDetected = ml.isLoaded("sodium")
                            || ml.isLoaded("embeddium")
                            || ml.isLoaded("rubidium");
                    modCheckDone = true;
                }
            } catch (Throwable ignored) {
            }
        }
        return sodiumLikeDetected;
    }

    /** Vrai quand le maillage semi-transparent vanilla doit s'appliquer dans SectionCompiler / Block. */
    public static boolean isVanillaMeshXrayActive() {
        return activeLevel > 0 && activeRadius > 0 && !isSodiumLikeLoaded();
    }

    /** Vrai quand la passe 3D à travers la roche prend le relais (Sodium/Embeddium ou compilateur custom). */
    public static boolean shouldUseOverlayFallback() {
        return activeLevel > 0 && activeRadius > 0 && (isSodiumLikeLoaded() || !vanillaCompilerSeen);
    }

    /** Rayon sphérique : 6 / 12 / 18 / 24 / 30 blocs (plafonné à 30 blocs dans toutes les directions). */
    public static int computeRadiusForLevel(int level) {
        if (level <= 0) return 0;
        return Math.min(MAX_RADIUS_BLOCKS, 6 * level);
    }

    public static boolean isPosInSphere(BlockPos pos) {
        return isPosInSphere(pos.getX(), pos.getY(), pos.getZ());
    }

    public static boolean isPosInSphere(int x, int y, int z) {
        int rSq = activeRadiusSq;
        if (rSq <= 0) return false;
        int dx = x - anchorX;
        int dy = y - anchorY;
        int dz = z - anchorZ;
        return (dx * dx + dy * dy + dz * dz) <= rSq;
    }

    /**
     * Détermine si un bloc doit rester 100 % visible (minerai vanilla/moddé, eau, lave ou tout liquide moddé).
     * Tous les autres blocs solides dans la sphère deviennent semi-transparents.
     */
    public static boolean isPreservedBlock(BlockState state) {
        if (state == null || state.isAir()) return false;
        if (!state.getFluidState().isEmpty()) return true;
        return isOreBlock(state);
    }

    public static boolean isOreBlock(BlockState state) {
        if (state == null || state.isAir()) return false;
        Block block = state.getBlock();
        Boolean cached = ORE_BLOCK_CACHE.get(block);
        if (cached != null) return cached;
        boolean result = computeIsOreBlock(state, block);
        ORE_BLOCK_CACHE.put(block, result);
        return result;
    }

    private static boolean computeIsOreBlock(BlockState state, Block block) {
        if (block == Blocks.ANCIENT_DEBRIS) return true;
        if (state.is(Tags.Blocks.ORES)
                || state.is(BlockTags.COAL_ORES)
                || state.is(BlockTags.IRON_ORES)
                || state.is(BlockTags.COPPER_ORES)
                || state.is(BlockTags.GOLD_ORES)
                || state.is(BlockTags.REDSTONE_ORES)
                || state.is(BlockTags.LAPIS_ORES)
                || state.is(BlockTags.DIAMOND_ORES)
                || state.is(BlockTags.EMERALD_ORES)
                || state.is(Tags.Blocks.STORAGE_BLOCKS_RAW_COPPER)
                || state.is(Tags.Blocks.STORAGE_BLOCKS_RAW_IRON)
                || state.is(Tags.Blocks.STORAGE_BLOCKS_RAW_GOLD)) {
            return true;
        }
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
        if (key == null) return false;
        String path = key.getPath().toLowerCase(Locale.ROOT);
        return path.endsWith("_ore")
                || path.startsWith("ore_")
                || path.contains("_ore_")
                || path.endsWith("_ores")
                || path.contains("ancient_debris")
                || (path.startsWith("raw_") && path.endsWith("_block"));
    }

    /** Vrai si un bloc rocheux est en contact direct avec de l'air ou un bloc non-occultant (coquille de grotte/sol). */
    public static boolean hasExposedCaveSurface(BlockAndTintGetter region, BlockPos pos) {
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        for (Direction d : DIRS) {
            mpos.setWithOffset(pos, d);
            BlockState nb = region.getBlockState(mpos);
            if (nb.isAir() || (!nb.canOcclude() && nb.getFluidState().isEmpty())) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        ORE_BLOCK_CACHE.clear();
    }

    @SubscribeEvent
    public static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || !player.isAlive() || player.isSpectator()) {
            if (activeLevel != 0) {
                disableXray(mc);
            }
            return;
        }

        int lvl = readHelmetMinersEyeLevel(player);
        if (lvl <= 0) {
            if (activeLevel != 0) {
                disableXray(mc);
            }
            return;
        }

        int radius = computeRadiusForLevel(lvl);
        String dim = level.dimension().location().toString();
        double eyeX = player.getX();
        double eyeY = player.getEyeY();
        double eyeZ = player.getZ();

        boolean dimChanged = !dim.equals(activeDimension);
        boolean radiusChanged = (radius != activeRadius) || (activeLevel == 0);
        activeDimension = dim;
        activeLevel = lvl;

        if (!isSodiumLikeLoaded()) {
            // Mode 1 : Vraie roche semi-transparente dans le maillage de chunk
            double movedSq = Double.isNaN(lastBakedX)
                    ? Double.MAX_VALUE
                    : sq(eyeX - lastBakedX) + sq(eyeY - lastBakedY) + sq(eyeZ - lastBakedZ);
            if (dimChanged || radiusChanged || movedSq >= 9.0) { // Seuil de 3.0 blocs
                double oldX = lastBakedX, oldY = lastBakedY, oldZ = lastBakedZ;
                int oldR = lastBakedRadius;

                anchorX = (int) Math.floor(eyeX);
                anchorY = (int) Math.floor(eyeY);
                anchorZ = (int) Math.floor(eyeZ);
                activeRadius = radius;
                activeRadiusSq = radius * radius;

                lastBakedX = eyeX;
                lastBakedY = eyeY;
                lastBakedZ = eyeZ;
                lastBakedRadius = radius;

                if (dimChanged || Double.isNaN(oldX) || oldR <= 0) {
                    markSphereSectionsDirty(mc, anchorX, anchorY, anchorZ, radius + 2);
                } else if (oldR != radius) {
                    markSphereSectionsDirty(mc, (int) Math.floor(oldX), (int) Math.floor(oldY), (int) Math.floor(oldZ), Math.max(oldR, radius) + 2);
                } else {
                    markShellTransitionDirty(mc, oldX, oldY, oldZ, eyeX, eyeY, eyeZ, radius);
                }
            }
        }

        // Mode 2 : Scan rapide par palette LevelChunkSection si Sodium/Embeddium est présent (ou avant 1er compile)
        if (shouldUseOverlayFallback()) {
            anchorX = (int) Math.floor(eyeX);
            anchorY = (int) Math.floor(eyeY);
            anchorZ = (int) Math.floor(eyeZ);
            activeRadius = radius;
            activeRadiusSq = radius * radius;

            if (fallbackScanCooldown > 0) fallbackScanCooldown--;
            double scanMovedSq = Double.isNaN(lastScanX)
                    ? Double.MAX_VALUE
                    : sq(eyeX - lastScanX) + sq(eyeY - lastScanY) + sq(eyeZ - lastScanZ);
            if (dimChanged || radius != lastScanRadius || scanMovedSq >= 6.25 || fallbackScanCooldown <= 0) {
                fallbackScanCooldown = 8;
                lastScanX = eyeX;
                lastScanY = eyeY;
                lastScanZ = eyeZ;
                lastScanRadius = radius;
                rebuildFallbackEntries(level, anchorX, anchorY, anchorZ, radius);
            }
        } else if (!fallbackEntries.isEmpty()) {
            fallbackEntries = List.of();
        }
    }

    private static void disableXray(Minecraft mc) {
        int prevX = anchorX, prevY = anchorY, prevZ = anchorZ, prevR = activeRadius;
        activeLevel = 0;
        activeRadius = 0;
        activeRadiusSq = 0;
        lastBakedX = Double.NaN;
        lastBakedY = Double.NaN;
        lastBakedZ = Double.NaN;
        lastBakedRadius = 0;
        lastScanX = Double.NaN;
        lastScanRadius = 0;
        fallbackEntries = List.of();
        if (prevR > 0 && mc != null && mc.level != null && !isSodiumLikeLoaded()) {
            markSphereSectionsDirty(mc, prevX, prevY, prevZ, prevR + 2);
        }
    }

    private static int readHelmetMinersEyeLevel(LocalPlayer player) {
        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        if (helmet == null || helmet.isEmpty()) return 0;
        Enchantment ench = DeepEnchant.byId("deep_lucky_block:miners_eye");
        if (ench == null) return 0;
        return DeepEnchant.getLevel(helmet, ench);
    }

    private static void markSphereSectionsDirty(Minecraft mc, int cx, int cy, int cz, int rad) {
        if (mc.levelRenderer == null || mc.level == null) return;
        int minSx = (cx - rad) >> 4, maxSx = (cx + rad) >> 4;
        int minSy = Math.max(mc.level.getMinSection(), (cy - rad) >> 4);
        int maxSy = Math.min(mc.level.getMaxSection() - 1, (cy + rad) >> 4);
        int minSz = (cz - rad) >> 4, maxSz = (cz + rad) >> 4;
        double maxDistSq = sq(rad + 14.0);

        for (int sx = minSx; sx <= maxSx; sx++) {
            double scx = (sx << 4) + 8.0;
            for (int sy = minSy; sy <= maxSy; sy++) {
                double scy = (sy << 4) + 8.0;
                for (int sz = minSz; sz <= maxSz; sz++) {
                    double scz = (sz << 4) + 8.0;
                    if (sq(scx - cx) + sq(scy - cy) + sq(scz - cz) <= maxDistSq) {
                        mc.levelRenderer.setSectionDirty(sx, sy, sz);
                    }
                }
            }
        }
    }

    /**
     * Optimisation clé à rayon 30 : quand le joueur avance de ~3 blocs, seuls les blocs proches du bord
     * de l'ancienne ou de la nouvelle sphère changent d'état (l'intérieur reste dans la sphère !).
     * On ne recompile donc que les sections intersectant la coquille de transition.
     */
    private static void markShellTransitionDirty(Minecraft mc,
                                                  double oldX, double oldY, double oldZ,
                                                  double newX, double newY, double newZ,
                                                  int rad) {
        if (mc.levelRenderer == null || mc.level == null) return;
        int minX = (int) Math.floor(Math.min(oldX, newX)) - rad - 2;
        int maxX = (int) Math.floor(Math.max(oldX, newX)) + rad + 2;
        int minY = (int) Math.floor(Math.min(oldY, newY)) - rad - 2;
        int maxY = (int) Math.floor(Math.max(oldY, newY)) + rad + 2;
        int minZ = (int) Math.floor(Math.min(oldZ, newZ)) - rad - 2;
        int maxZ = (int) Math.floor(Math.max(oldZ, newZ)) + rad + 2;

        int minSx = minX >> 4, maxSx = maxX >> 4;
        int minSy = Math.max(mc.level.getMinSection(), minY >> 4);
        int maxSy = Math.min(mc.level.getMaxSection() - 1, maxY >> 4);
        int minSz = minZ >> 4, maxSz = maxZ >> 4;

        double innerBand = Math.max(0.0, rad - 15.0);
        double outerBand = rad + 15.0;
        double innerSq = innerBand * innerBand;
        double outerSq = outerBand * outerBand;

        for (int sx = minSx; sx <= maxSx; sx++) {
            double scx = (sx << 4) + 8.0;
            for (int sy = minSy; sy <= maxSy; sy++) {
                double scy = (sy << 4) + 8.0;
                for (int sz = minSz; sz <= maxSz; sz++) {
                    double scz = (sz << 4) + 8.0;
                    double dOld = sq(scx - oldX) + sq(scy - oldY) + sq(scz - oldZ);
                    double dNew = sq(scx - newX) + sq(scy - newY) + sq(scz - newZ);
                    boolean onOldShell = dOld >= innerSq && dOld <= outerSq;
                    boolean onNewShell = dNew >= innerSq && dNew <= outerSq;
                    if (onOldShell || onNewShell) {
                        mc.levelRenderer.setSectionDirty(sx, sy, sz);
                    }
                }
            }
        }
    }

    // ==================== FALLBACK 3D SODIUM / EMBEDDIUM ====================

    private static void rebuildFallbackEntries(ClientLevel level, int cx, int cy, int cz, int rad) {
        int rSq = rad * rad;
        int minX = cx - rad, maxX = cx + rad;
        int minY = Math.max(level.getMinBuildHeight(), cy - rad);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, cy + rad);
        int minZ = cz - rad, maxZ = cz + rad;

        int minCx = minX >> 4, maxCx = maxX >> 4;
        int minCz = minZ >> 4, maxCz = maxZ >> 4;
        int minSecY = minY >> 4, maxSecY = maxY >> 4;

        List<FallbackEntry> found = new ArrayList<>(512);
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();

        for (int chunkX = minCx; chunkX <= maxCx; chunkX++) {
            for (int chunkZ = minCz; chunkZ <= maxCz; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) continue;
                ChunkAccess chunk = level.getChunk(chunkX, chunkZ);
                LevelChunkSection[] sections = chunk.getSections();
                int xStart = Math.max(minX, chunkX << 4);
                int xEnd = Math.min(maxX, (chunkX << 4) + 15);
                int zStart = Math.max(minZ, chunkZ << 4);
                int zEnd = Math.min(maxZ, (chunkZ << 4) + 15);

                for (int secY = minSecY; secY <= maxSecY; secY++) {
                    int secIdx = level.getSectionIndexFromSectionY(secY);
                    if (secIdx < 0 || secIdx >= sections.length) continue;
                    LevelChunkSection sec = sections[secIdx];
                    if (sec == null || sec.hasOnlyAir() || !sec.maybeHas(MinersEyeClientXray::isPreservedBlock)) {
                        continue;
                    }
                    int yStart = Math.max(minY, secY << 4);
                    int yEnd = Math.min(maxY, (secY << 4) + 15);

                    for (int y = yStart; y <= yEnd; y++) {
                        int dy = y - cy;
                        int dy2 = dy * dy;
                        if (dy2 > rSq) continue;
                        for (int z = zStart; z <= zEnd; z++) {
                            int dz = z - cz;
                            int dyz2 = dy2 + dz * dz;
                            if (dyz2 > rSq) continue;
                            for (int x = xStart; x <= xEnd; x++) {
                                int dx = x - cx;
                                int distSq = dyz2 + dx * dx;
                                if (distSq > rSq) continue;

                                BlockState st = sec.getBlockState(x & 15, y & 15, z & 15);
                                if (!isPreservedBlock(st)) continue;

                                boolean isFluid = !st.getFluidState().isEmpty() && !isOreBlock(st);
                                byte mask = computeVisibleFaces(level, mpos, x, y, z, st, isFluid);
                                if (mask != 0) {
                                    found.add(new FallbackEntry(x, y, z, st, mask, isFluid, distSq));
                                }
                            }
                        }
                    }
                }
            }
        }

        if (found.size() > 5000) {
            found.sort(Comparator.comparingInt(FallbackEntry::distSq));
            found = new ArrayList<>(found.subList(0, 5000));
        }
        fallbackEntries = found;
    }

    private static byte computeVisibleFaces(ClientLevel level, BlockPos.MutableBlockPos mpos,
                                            int x, int y, int z, BlockState self, boolean isFluid) {
        byte mask = 0;
        FluidState selfFluid = self.getFluidState();
        for (Direction d : DIRS) {
            mpos.set(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ());
            BlockState nb = level.getBlockState(mpos);
            if (isFluid) {
                if (!nb.getFluidState().getType().isSame(selfFluid.getType())) {
                    mask |= (byte) (1 << d.ordinal());
                }
            } else {
                if (nb.getBlock() != self.getBlock()) {
                    mask |= (byte) (1 << d.ordinal());
                }
            }
        }
        return mask;
    }

    private static RenderType getOrCreateXrayRenderType() {
        if (xraySeeThroughType == null) {
            RenderType.CompositeState state = RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(TextureAtlas.LOCATION_BLOCKS, false, true))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.CULL)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setOutputState(RenderStateShard.PARTICLES_TARGET)
                    .createCompositeState(false);
            xraySeeThroughType = RenderType.create(
                    "dlb_miners_eye_xray",
                    DefaultVertexFormat.NEW_ENTITY,
                    VertexFormat.Mode.QUADS,
                    4096,
                    false,
                    false,
                    state);
        }
        return xraySeeThroughType;
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (!shouldUseOverlayFallback()) return;

        List<FallbackEntry> entries = fallbackEntries;
        if (entries.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        Camera cam = event.getCamera();
        Vec3 camPos = cam.getPosition();
        double camX = camPos.x, camY = camPos.y, camZ = camPos.z;
        float maxR = Math.max(1.0f, activeRadius);
        float maxRSq = maxR * maxR;

        PoseStack poseStack = event.getPoseStack();
        Matrix4f mat = poseStack.last().pose();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType rt = getOrCreateXrayRenderType();
        VertexConsumer vc = buffers.getBuffer(rt);

        BlockRenderDispatcher dispatcher = mc.getBlockRenderer();
        RandomSource rand = RandomSource.create(42L);
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        Vector3f tmp = new Vector3f();

        for (FallbackEntry e : entries) {
            double dx = (e.x + 0.5) - camX;
            double dy = (e.y + 0.5) - camY;
            double dz = (e.z + 0.5) - camZ;
            float dSq = (float) (dx * dx + dy * dy + dz * dz);
            if (dSq > maxRSq) continue;

            float dist = (float) Math.sqrt(dSq);
            float edgeFade = dist <= maxR * 0.78f
                    ? 1.0f
                    : Math.max(0.0f, (maxR - dist) / (maxR * 0.22f));
            int alpha = Math.max(20, Math.min(235, Math.round((e.fluid ? 185.0f : 232.0f) * edgeFade)));

            float x0 = (float) (e.x - camX);
            float y0 = (float) (e.y - camY);
            float z0 = (float) (e.z - camZ);
            mpos.set(e.x, e.y, e.z);

            if (e.fluid) {
                FluidState fs = e.state.getFluidState();
                if (fs.isEmpty()) continue;
                TextureAtlasSprite[] sprites = FluidSpriteCache.getFluidSprites(level, mpos, fs);
                TextureAtlasSprite sprite = (sprites != null && sprites.length > 0 && sprites[0] != null)
                        ? sprites[0]
                        : dispatcher.getBlockModel(Blocks.WATER.defaultBlockState()).getParticleIcon();
                int tint = IClientFluidTypeExtensions.of(fs).getTintColor(fs, level, mpos);
                int tr = (tint >>> 16) & 0xFF;
                int tg = (tint >>> 8) & 0xFF;
                int tb = tint & 0xFF;
                if (tr == 0 && tg == 0 && tb == 0) {
                    tr = tg = tb = 255;
                }
                float topH = Math.max(0.12f, Math.min(1.0f, fs.getHeight(level, mpos)));
                emitBoxFaces(vc, mat, tmp, x0, y0, z0, topH, e.faceMask, sprite, null, e.state, rand, tr, tg, tb, alpha);
            } else {
                BakedModel model = dispatcher.getBlockModel(e.state);
                TextureAtlasSprite fallbackSprite = model.getParticleIcon();
                emitBoxFaces(vc, mat, tmp, x0, y0, z0, 1.0f, e.faceMask, fallbackSprite, model, e.state, rand, 255, 255, 255, alpha);
            }
        }

        buffers.endBatch(rt);
    }

    private static void emitBoxFaces(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                     float x0, float y0, float z0, float topH,
                                     byte faceMask, TextureAtlasSprite defaultSprite,
                                     BakedModel model, BlockState state, RandomSource rand,
                                     int baseR, int baseG, int baseB, int alpha) {
        float x1 = x0 + 1.0f;
        float y1 = y0 + topH;
        float z1 = z0 + 1.0f;
        int fullLight = 0x00F000F0;

        for (Direction d : DIRS) {
            if ((faceMask & (1 << d.ordinal())) == 0) continue;
            TextureAtlasSprite sp = defaultSprite;
            if (model != null) {
                rand.setSeed(42L);
                List<BakedQuad> quads = model.getQuads(state, d, rand, ModelData.EMPTY, null);
                if (!quads.isEmpty() && quads.get(0).getSprite() != null) {
                    sp = quads.get(0).getSprite();
                }
            }
            if (sp == null) continue;

            float shade = switch (d) {
                case DOWN -> 0.62f;
                case UP -> 1.00f;
                case NORTH, SOUTH -> 0.84f;
                case WEST, EAST -> 0.72f;
            };
            int r = Math.min(255, Math.round(baseR * shade));
            int g = Math.min(255, Math.round(baseG * shade));
            int b = Math.min(255, Math.round(baseB * shade));
            float u0 = sp.getU0(), u1 = sp.getU1();
            float v0 = sp.getV0(), v1 = sp.getV1();
            float vTop = sp.getV0() + (sp.getV1() - sp.getV0()) * topH;

            switch (d) {
                case DOWN -> {
                    putVert(vc, mat, tmp, x0, y0, z1, r, g, b, alpha, u0, v1, fullLight, 0, -1, 0);
                    putVert(vc, mat, tmp, x0, y0, z0, r, g, b, alpha, u0, v0, fullLight, 0, -1, 0);
                    putVert(vc, mat, tmp, x1, y0, z0, r, g, b, alpha, u1, v0, fullLight, 0, -1, 0);
                    putVert(vc, mat, tmp, x1, y0, z1, r, g, b, alpha, u1, v1, fullLight, 0, -1, 0);
                }
                case UP -> {
                    putVert(vc, mat, tmp, x0, y1, z0, r, g, b, alpha, u0, v0, fullLight, 0, 1, 0);
                    putVert(vc, mat, tmp, x0, y1, z1, r, g, b, alpha, u0, v1, fullLight, 0, 1, 0);
                    putVert(vc, mat, tmp, x1, y1, z1, r, g, b, alpha, u1, v1, fullLight, 0, 1, 0);
                    putVert(vc, mat, tmp, x1, y1, z0, r, g, b, alpha, u1, v0, fullLight, 0, 1, 0);
                }
                case NORTH -> {
                    putVert(vc, mat, tmp, x1, y1, z0, r, g, b, alpha, u0, v0, fullLight, 0, 0, -1);
                    putVert(vc, mat, tmp, x1, y0, z0, r, g, b, alpha, u0, vTop, fullLight, 0, 0, -1);
                    putVert(vc, mat, tmp, x0, y0, z0, r, g, b, alpha, u1, vTop, fullLight, 0, 0, -1);
                    putVert(vc, mat, tmp, x0, y1, z0, r, g, b, alpha, u1, v0, fullLight, 0, 0, -1);
                }
                case SOUTH -> {
                    putVert(vc, mat, tmp, x0, y1, z1, r, g, b, alpha, u0, v0, fullLight, 0, 0, 1);
                    putVert(vc, mat, tmp, x0, y0, z1, r, g, b, alpha, u0, vTop, fullLight, 0, 0, 1);
                    putVert(vc, mat, tmp, x1, y0, z1, r, g, b, alpha, u1, vTop, fullLight, 0, 0, 1);
                    putVert(vc, mat, tmp, x1, y1, z1, r, g, b, alpha, u1, v0, fullLight, 0, 0, 1);
                }
                case WEST -> {
                    putVert(vc, mat, tmp, x0, y1, z0, r, g, b, alpha, u0, v0, fullLight, -1, 0, 0);
                    putVert(vc, mat, tmp, x0, y0, z0, r, g, b, alpha, u0, vTop, fullLight, -1, 0, 0);
                    putVert(vc, mat, tmp, x0, y0, z1, r, g, b, alpha, u1, vTop, fullLight, -1, 0, 0);
                    putVert(vc, mat, tmp, x0, y1, z1, r, g, b, alpha, u1, v0, fullLight, -1, 0, 0);
                }
                case EAST -> {
                    putVert(vc, mat, tmp, x1, y1, z1, r, g, b, alpha, u0, v0, fullLight, 1, 0, 0);
                    putVert(vc, mat, tmp, x1, y0, z1, r, g, b, alpha, u0, vTop, fullLight, 1, 0, 0);
                    putVert(vc, mat, tmp, x1, y0, z0, r, g, b, alpha, u1, vTop, fullLight, 1, 0, 0);
                    putVert(vc, mat, tmp, x1, y1, z0, r, g, b, alpha, u1, v0, fullLight, 1, 0, 0);
                }
            }
        }
    }

    private static void putVert(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                float x, float y, float z,
                                int r, int g, int b, int a,
                                float u, float v, int light,
                                float nx, float ny, float nz) {
        mat.transformPosition(x, y, z, tmp);
        int argb = ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
        vc.addVertex(tmp.x(), tmp.y(), tmp.z(), argb, u, v, OverlayTexture.NO_OVERLAY, light, nx, ny, nz);
    }

    private static double sq(double v) {
        return v * v;
    }
}
