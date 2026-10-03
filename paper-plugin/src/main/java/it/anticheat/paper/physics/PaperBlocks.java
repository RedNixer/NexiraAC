package it.anticheat.paper.physics;

import it.anticheat.core.physics.BlockKind;
import org.bukkit.Material;

/**
 * Bukkit -> BlockKind via nomi (zero enum inventati: contains/endsWith).
 * Unico punto di traduzione blocchi su Paper.
 */
public final class PaperBlocks {
    private PaperBlocks() {}

    public static BlockKind kindOf(Material m) {
        if (m == null) return BlockKind.OTHER;
        if (m.isAir()) return BlockKind.AIR;
        try {
            String n = m.name();
            if (n.contains("WATER")) return BlockKind.WATER;
            if (n.contains("LAVA")) return BlockKind.LAVA;
            if (n.contains("BUBBLE")) return BlockKind.BUBBLE;
            if (n.contains("COBWEB")) return BlockKind.COBWEB;
            if (n.contains("POWDER_SNOW")) return BlockKind.POWDER_SNOW;
            if (n.contains("SCAFFOLD")) return BlockKind.SCAFFOLD;
            if (n.equals("LADDER")) return BlockKind.LADDER;
            if (n.contains("VINE")) return n.equals("VINE") || n.equals("TWISTING_VINES")
                || n.equals("WEEPING_VINES") || n.equals("CAVE_VINES") ? BlockKind.VINE : BlockKind.SOLID;
            if (n.contains("STAIR")) return BlockKind.STAIR;
            if (n.contains("SLAB")) return BlockKind.SLAB;
            if (n.contains("FENCE") || n.contains("WALL")) return BlockKind.FENCE;
            if (n.contains("CARPET") || n.contains("SNOW") && !n.contains("POWDER")
                    || n.contains("PRESSURE_PLATE") || n.contains("BUTTON")) return BlockKind.CARPET;
            if (n.contains("ICE") || n.contains("FROSTED")) return BlockKind.ICE;
            if (n.contains("SLIME")) return BlockKind.SLIME;
            if (n.contains("HONEY")) return BlockKind.HONEY;
            if (n.contains("SOUL_SAND") && !n.contains("SOIL")) return BlockKind.SOUL_SAND;
            if (n.contains("HAY")) return BlockKind.HAY;
            if (n.endsWith("_BED")) return BlockKind.BED;
            if (n.contains("CACTUS")) return BlockKind.CACTUS;
            return m.isSolid() ? BlockKind.SOLID : BlockKind.OTHER;
        } catch (Throwable t) {
            return BlockKind.OTHER;
        }
    }
}
