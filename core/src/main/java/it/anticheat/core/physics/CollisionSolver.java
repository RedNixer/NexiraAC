package it.anticheat.core.physics;

/**
 * Collision solver AABB vs voxel (codice nostro, meccanica vanilla).
 * Risolve per asse in ordine Y -> X -> Z con step 0.6 incluso.
 * Output: posizione finale + onGround CALCOLATO (non dichiarato dal client).
 */
public final class CollisionSolver {
    private CollisionSolver() {}

    public static final double PLAYER_W = 0.6;
    public static final double PLAYER_H = 1.8;
    public static final double STEP_H = 0.6;

    public static class Result {
        public double x, y, z;
        public boolean onGround;   // calcolato: collisione Y verso il basso
        public boolean hitX, hitZ; // muro toccato
        public boolean stepped;    // gradino salito
    }

    /** Muove la AABB da (x,y,z) di (dx,dy,dz) contro il mondo. */
    public static Result move(WorldView world, double x, double y, double z,
                             double dx, double dy, double dz) {
        Result r = new Result();
        // --- Y (<=0 include la quiete: da fermo il pavimento sotto = terra) ---
        double ny = y + dy;
        if (dy <= 0) {
            double floor = collideDown(world, x, y, z, ny);
            if (floor > ny) { // pavimento trovato sopra la destinazione
                ny = floor;
                r.onGround = true;
                dy = 0;
            }
        } else if (dy > 0) {
            double ceil = collideUp(world, x, y, z, ny);
            if (ceil < ny) { // soffitto: tronca
                ny = ceil;
                dy = 0;
            }
        }
        // --- X ---
        double nx = x + dx;
        boolean blockX = collideHoriz(world, nx, ny, z, true);
        // --- Z ---
        double nz = z + dz;
        boolean blockZ = collideHoriz(world, nx, ny, nz, false);
        // --- Step: muro basso + spazio sopra = gradino (max 0.6).
        // Solo se ci si muove davvero: da fermo il muro vicino non e step.
        if ((blockX || blockZ) && dy <= 0.01 && (dx != 0 || dz != 0)) {
            double stepped = tryStep(world, x, ny, z, dx, dz);
            if (!Double.isNaN(stepped)) {
                nx = x + dx;
                nz = z + dz;
                ny = stepped;
                r.stepped = true;
                blockX = false;
                blockZ = false;
            }
        }
        if (blockX) nx = x;
        if (blockZ) nz = z;
        r.x = nx;
        r.y = ny;
        r.z = nz;
        r.hitX = blockX;
        r.hitZ = blockZ;
        return r;
    }

    /** Pavimento piu alto sotto i piedi tra y e ny (solo blocchi supports). */
    private static double collideDown(WorldView world, double x, double y, double z, double ny) {
        int bx0 = (int) Math.floor(x - PLAYER_W / 2);
        int bx1 = (int) Math.floor(x + PLAYER_W / 2);
        int bz0 = (int) Math.floor(z - PLAYER_W / 2);
        int bz1 = (int) Math.floor(z + PLAYER_W / 2);
        // best sotto ny: da fermo (ny==y) il pavimento top==y vince (onGround),
        // in aria il pavimento lontano resta sotto best (niente terra)
        double best = ny - 0.001;
        int byTop = (int) Math.floor(y);
        int byBot = (int) Math.floor(ny) - 1;
        for (int by = byTop; by >= byBot; by--) {
            for (int bx = bx0; bx <= bx1; bx++) {
                for (int bz = bz0; bz <= bz1; bz++) {
                    if (!world.blockAt(bx, by, bz).supports()) continue;
                    double top = by + 1.0 + shapeTop(world.blockAt(bx, by, bz));
                    if (top <= y + 1e-9 && top > best) best = top;
                }
            }
        }
        return best;
    }

    /** Soffitto piu basso sopra la testa tra y+H e ny+H. */
    private static double collideUp(WorldView world, double x, double y, double z, double ny) {
        int bx0 = (int) Math.floor(x - PLAYER_W / 2);
        int bx1 = (int) Math.floor(x + PLAYER_W / 2);
        int bz0 = (int) Math.floor(z - PLAYER_W / 2);
        int bz1 = (int) Math.floor(z + PLAYER_W / 2);
        double head0 = y + PLAYER_H;
        double head1 = ny + PLAYER_H;
        double best = ny;
        int byBot = (int) Math.floor(head0);
        int byTop = (int) Math.floor(head1) + 1;
        for (int by = byBot; by <= byTop; by++) {
            for (int bx = bx0; bx <= bx1; bx++) {
                for (int bz = bz0; bz <= bz1; bz++) {
                    if (!world.blockAt(bx, by, bz).supports()) continue;
                    double bot = by + shapeBottom(world.blockAt(bx, by, bz));
                    if (bot >= head0 - 1e-9 && bot - PLAYER_H < best) best = bot - PLAYER_H;
                }
            }
        }
        return best;
    }

    /** AABB contro blocchi pieni su un asse orizzontale. */
    private static boolean collideHoriz(WorldView world, double x, double y, double z, boolean useX) {
        int bx0 = (int) Math.floor(x - PLAYER_W / 2);
        int bx1 = (int) Math.floor(x + PLAYER_W / 2);
        int bz0 = (int) Math.floor(z - PLAYER_W / 2);
        int bz1 = (int) Math.floor(z + PLAYER_W / 2);
        int by0 = (int) Math.floor(y);
        int by1 = (int) Math.floor(y + PLAYER_H - 1e-6);
        for (int by = by0; by <= by1; by++) {
            for (int bx = bx0; bx <= bx1; bx++) {
                for (int bz = bz0; bz <= bz1; bz++) {
                    BlockKind k = world.blockAt(bx, by, bz);
                    if (k == BlockKind.AIR || k == BlockKind.WATER || k == BlockKind.LAVA
                            || k == BlockKind.COBWEB || k == BlockKind.POWDER_SNOW
                            || k == BlockKind.LADDER || k == BlockKind.VINE
                            || k == BlockKind.SCAFFOLD || k == BlockKind.CARPET) continue;
                    if (k == BlockKind.STAIR || k == BlockKind.SLAB || k == BlockKind.FENCE) {
                        // parziali: collidono solo se il corpo ci entra davvero
                        double top = by + 1.0 + shapeTop(k);
                        if (y >= top - 1e-9) continue;
                    }
                    return true;
                }
            }
        }
        return false;
    }

    /** Prova step: alza fino a 0.6 se sopra e libero. NaN se impossibile. */
    private static double tryStep(WorldView world, double x, double y, double z,
                                  double dx, double dz) {
        double nx = x + dx, nz = z + dz;
        for (double lift = STEP_H; lift >= 0.1; lift -= 0.1) {
            double ty = y + lift;
            if (collideHoriz(world, nx, ty, nz, true)) continue;
            if (collideHoriz(world, nx, ty, nz, false)) continue;
            // testa libera sopra?
            double head = ty + PLAYER_H;
            int bx0 = (int) Math.floor(nx - PLAYER_W / 2);
            int bx1 = (int) Math.floor(nx + PLAYER_W / 2);
            int bz0 = (int) Math.floor(nz - PLAYER_W / 2);
            int bz1 = (int) Math.floor(nz + PLAYER_W / 2);
            boolean ok = true;
            for (int by = (int) Math.floor(ty); by <= (int) Math.floor(head); by++) {
                for (int bx = bx0; bx <= bx1 && ok; bx++) {
                    for (int bz = bz0; bz <= bz1 && ok; bz++) {
                        BlockKind k = world.blockAt(bx, by, bz);
                        if (k.supports() && by + 1.0 + shapeTop(k) > ty + 1e-9
                                && by + shapeBottom(k) < head - 1e-9) {
                            // blocco che occupa davvero lo spazio corpo
                            if (!(k == BlockKind.STAIR || k == BlockKind.SLAB)
                                    || by + 1.0 + shapeTop(k) > ty + 0.05) {
                                ok = false;
                            }
                        }
                    }
                }
            }
            if (ok) return ty;
        }
        return Double.NaN;
    }

    /** Offset cima per forme parziali (slab basso, scale...). */
    private static double shapeTop(BlockKind k) {
        return switch (k) {
            case SLAB -> -0.5;   // lastra bassa: cima a +0.5
            case CARPET -> -0.9375;
            default -> 0.0;
        };
    }

    private static double shapeBottom(BlockKind k) {
        return switch (k) {
            case SLAB -> 0.5;    // lastra alta: fondo a +0.5
            default -> 0.0;
        };
    }
}
