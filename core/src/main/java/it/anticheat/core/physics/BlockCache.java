package it.anticheat.core.physics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache 3D dei blocchi attorno al player (raggio 2 = 5x4x5 = 100 voci max).
 * Gli adapter la riempiono una volta per move; il TickEngine legge gratis.
 * Chiave long packed (bx+2048, by+2048, bz+2048 in 12+12+12 bit... no:
 * mondi alti: uso string-free packing a 21 bit per asse con offset).
 */
public final class BlockCache implements WorldView {
    private final Map<Long, BlockKind> map = new ConcurrentHashMap<>();
    private volatile long gen = 0;

    /** Riempie la cache attorno a (x,y,z). Chiamato dagli adapter. */
    public void fill(double x, double y, double z, BlockProbe probe) {
        map.clear();
        gen++;
        int cx = (int) Math.floor(x);
        int cy = (int) Math.floor(y);
        int cz = (int) Math.floor(z);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    int bx = cx + dx, by = cy + dy, bz = cz + dz;
                    try {
                        BlockKind k = probe.kindAt(bx, by, bz);
                        map.put(key(bx, by, bz), k == null ? BlockKind.OTHER : k);
                    } catch (Throwable ignored) {
                        map.put(key(bx, by, bz), BlockKind.OTHER);
                    }
                }
            }
        }
    }

    /** Sorgente blocchi (l'adapter legge il mondo vero). */
    public interface BlockProbe {
        BlockKind kindAt(int bx, int by, int bz);
    }

    @Override
    public BlockKind blockAt(int bx, int by, int bz) {
        BlockKind k = map.get(key(bx, by, bz));
        return k == null ? BlockKind.OTHER : k;
    }

    public long generation() {
        return gen;
    }

    public int size() {
        return map.size();
    }

    private static long key(int bx, int by, int bz) {
        // 21 bit per asse con bias 2^20: copre +-1M, basta per overworld
        long x = ((long) bx + 1048576L) & 0x1FFFFFL;
        long y = ((long) by + 1048576L) & 0x1FFFFFL;
        long z = ((long) bz + 1048576L) & 0x1FFFFFL;
        return (x << 42) | (y << 21) | z;
    }
}
