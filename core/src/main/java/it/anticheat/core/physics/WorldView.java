package it.anticheat.core.physics;

/**
 * Vista mondo per il TickEngine. Gli adapter (Paper/Fabric) implementano
 * questa interfaccia leggendo i blocchi veri; il core vede solo BlockKind.
 * Coordinate blocco intere (floor di x/y/z).
 */
public interface WorldView {
    /** Tipo di blocco a (bx, by, bz). Mai null: fallback OTHER. */
    BlockKind blockAt(int bx, int by, int bz);

    /** Blocco solido sopra la testa entro 2.5 (salti troncati)? */
    default boolean ceilingAbove(double x, double y, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int by1 = (int) Math.floor(y + 1.9);
        int by2 = (int) Math.floor(y + 2.4);
        return blockAt(bx, by1, bz).supports() || blockAt(bx, by2, bz).supports();
    }

    /** Supporto sotto i piedi (onGround calcolato, non dichiarato)? */
    default boolean groundBelow(double x, double y, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int by1 = (int) Math.floor(y - 0.51);
        int by2 = (int) Math.floor(y - 1.01);
        return blockAt(bx, by1, bz).supports() || blockAt(bx, by2, bz).supports();
    }

    /** Attrito del blocco sotto i piedi (0.6 default, 0.98 ghiaccio). */
    default double slipBelow(double x, double y, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int by = (int) Math.floor(y - 0.51);
        return blockAt(bx, by, bz).slipperiness();
    }
}
