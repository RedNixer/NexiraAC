package it.anticheat.fabric.physics;

import it.anticheat.core.physics.BlockKind;

/**
 * Mojang -> BlockKind via nomi registry (zero ref inventate).
 * Unico punto di traduzione blocchi su Fabric.
 */
public final class FabricBlocks {
    private FabricBlocks() {}

    /** Da stringa registry lowercase ("minecraft:oak_stairs"). */
    public static BlockKind kindOf(String registryName) {
        if (registryName == null) return BlockKind.OTHER;
        String n = registryName;
        try {
            if (n.endsWith("air") || n.contains("void_air") || n.contains("cave_air")) return BlockKind.AIR;
            if (n.contains("water")) return BlockKind.WATER;
            if (n.contains("lava")) return BlockKind.LAVA;
            if (n.contains("bubble")) return BlockKind.BUBBLE;
            if (n.contains("cobweb")) return BlockKind.COBWEB;
            if (n.contains("powder_snow")) return BlockKind.POWDER_SNOW;
            if (n.contains("scaffold")) return BlockKind.SCAFFOLD;
            if (n.contains("ladder")) return BlockKind.LADDER;
            if (n.contains("vine")) return BlockKind.VINE;
            if (n.contains("stair")) return BlockKind.STAIR;
            if (n.contains("slab")) return BlockKind.SLAB;
            if (n.contains("fence") || n.contains("wall")) return BlockKind.FENCE;
            if (n.contains("carpet") || n.contains("pressure_plate") || n.contains("button")
                    || n.contains("snow") && !n.contains("powder")) return BlockKind.CARPET;
            if (n.contains("ice") || n.contains("frosted")) return BlockKind.ICE;
            if (n.contains("slime")) return BlockKind.SLIME;
            if (n.contains("honey")) return BlockKind.HONEY;
            if (n.contains("soul_sand")) return BlockKind.SOUL_SAND;
            if (n.contains("hay")) return BlockKind.HAY;
            if (n.contains("bed")) return BlockKind.BED;
            if (n.contains("cactus")) return BlockKind.CACTUS;
            return BlockKind.OTHER;
        } catch (Throwable t) {
            return BlockKind.OTHER;
        }
    }

    /** Da BlockState (toString contiene il registry name). */
    public static BlockKind kindOfState(Object state) {
        if (state == null) return BlockKind.OTHER;
        try {
            String s = state.toString().toLowerCase(java.util.Locale.ROOT);
            int a = s.indexOf("minecraft:");
            if (a >= 0) {
                int e = a;
                while (e < s.length() && (Character.isLetterOrDigit(s.charAt(e))
                        || s.charAt(e) == ':' || s.charAt(e) == '_' || s.charAt(e) == '/')) e++;
                return kindOf(s.substring(a, e));
            }
            return kindOf(s);
        } catch (Throwable t) {
            return BlockKind.OTHER;
        }
    }
}
