package deepluckyblock.client;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * T341-MINERS-EYE-XRAY : Enveloppes {@link VertexConsumer} ultra-légères pour la compilation
 * de chunks vanilla de {@code miners_eye} (Œil du Mineur) :
 * <ul>
 *   <li>{@link #faded(VertexConsumer, int, int)} : rend la paroi rocheuse extérieure semi-transparente
 *       (~24 % d'opacité) avec un léger éclairage minimal pour conserver les repères de déplacement en grotte.</li>
 *   <li>{@link #brighten(VertexConsumer, int)} : éclaire proprement les minerais et poches de liquides
 *       enfouis au cœur de la roche (qui auraient sinon une lightmap à 0 dans le noir total).</li>
 * </ul>
 */
public final class MinersEyeVertexConsumers {

    private MinersEyeVertexConsumers() {}

    public static VertexConsumer faded(VertexConsumer delegate, int alpha, int minBlockLight) {
        return new FadedConsumer(delegate, Math.max(0, Math.min(255, alpha)), Math.max(0, Math.min(15, minBlockLight)));
    }

    public static VertexConsumer brighten(VertexConsumer delegate, int minBlockLight) {
        return new BrightConsumer(delegate, Math.max(0, Math.min(15, minBlockLight)));
    }

    static int liftPackedLight(int packedLight, int minBlockLight) {
        int blockCoord = packedLight & 0xFFFF;
        int skyCoord = (packedLight >>> 16) & 0xFFFF;
        int minCoord = minBlockLight << 4;
        if (blockCoord < minCoord) {
            blockCoord = minCoord;
        }
        return (skyCoord << 16) | blockCoord;
    }

    private static final class FadedConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final int alpha;
        private final int minBlockLight;

        FadedConsumer(VertexConsumer delegate, int alpha, int minBlockLight) {
            this.delegate = delegate;
            this.alpha = alpha;
            this.minBlockLight = minBlockLight;
        }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v,
                              int overlayCoords, int uv2, float nx, float ny, float nz) {
            int origA = (color >>> 24) & 0xFF;
            int outA = origA == 0 ? this.alpha : Math.min(origA, this.alpha);
            int fadedColor = (outA << 24) | (color & 0x00FFFFFF);
            int litUv2 = liftPackedLight(uv2, this.minBlockLight);
            this.delegate.addVertex(x, y, z, fadedColor, u, v, overlayCoords, litUv2, nx, ny, nz);
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            this.delegate.setColor(r, g, b, Math.min(a, this.alpha));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            this.delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            int minCoord = this.minBlockLight << 4;
            this.delegate.setUv2(Math.max(u, minCoord), v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            this.delegate.setNormal(x, y, z);
            return this;
        }
    }

    private static final class BrightConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final int minBlockLight;

        BrightConsumer(VertexConsumer delegate, int minBlockLight) {
            this.delegate = delegate;
            this.minBlockLight = minBlockLight;
        }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v,
                              int overlayCoords, int uv2, float nx, float ny, float nz) {
            int litUv2 = liftPackedLight(uv2, this.minBlockLight);
            this.delegate.addVertex(x, y, z, color, u, v, overlayCoords, litUv2, nx, ny, nz);
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            this.delegate.setColor(r, g, b, a);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            this.delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            int minCoord = this.minBlockLight << 4;
            this.delegate.setUv2(Math.max(u, minCoord), v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            this.delegate.setNormal(x, y, z);
            return this;
        }
    }
}
