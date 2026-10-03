package it.anticheat.core.physics;

/** Vanilla movement limits, pure Java. Per-move (scaled by real dt), not per-tick. */
public final class MovementPredictor {

    private MovementPredictor() {}

    /** Costanti fisiche vanilla Java Edition. */
    public static final double GRAVITY = 0.08;
    public static final double AIR_DRAG_Y = 0.98;
    public static final double AIR_DRAG_XZ = 0.91;
    public static final double GROUND_DRAG_XZ = 0.91;
    public static final double JUMP_VY = 0.42;
    public static final double JUMP_BOOST_PER_LEVEL = 0.1;
    public static final double STEP_MAX = 0.6;
    public static final double SPRINT_MULT = 1.3;
    public static final double SNEAK_MULT = 0.3;
    public static final double WEB_FALL_MAX = 0.05;
    public static final double LAVA_FALL_MAX = 0.06;

    /** Input movimento noto dal contesto (quello che il server vede davvero). */
    public static class Input {
        public boolean onGround;
        public boolean sprinting;
        public boolean sneaking;
        public boolean inWater;
        public boolean inLava;
        public boolean onLadder;
        public boolean inCobweb;
        public boolean gliding;
        public boolean riding;
        public boolean flying;
        /** Moltiplicatore attrito blocco sotto (1.0 default, ~1.3 ghiaccio). */
        public double slipperiness = 1.0;
        /** Amplifier Jump Boost, -1 = nessuno. */
        public int jumpAmp = -1;
        /** Amplifier Speed, -1 = nessuna. */
        public int speedAmp = -1;
        /** Amplifier Slowness, -1 = nessuna. */
        public int slowAmp = -1;
        /** Ticks di volo/nuoto continuo già accumulati (per cap aria/acqua). */
        public int airTicks = 0;
        /** Attribute movement_speed (0.1 vanilla). */
        public double moveAttr = 0.1;
        /** Soffitto h2 + sprint-jump: meccanica vanilla ~2x (wiki). */
        public boolean sprintCeiling = false;
    }

    /** Risultato: spostamento massimo legittimo per il dt dato. */
    public static class Limit {
        /** Max distanza orizzontale legittima (blocchi) nel dt. */
        public double maxDistXZ;
        /** Max salita verticale legittima (blocchi) nel dt. */
        public double maxDyUp;
        /** Min discesa verticale legittima (sotto = caduta troppo lenta = hover). */
        public double minDyDown;
    }

    /** Vanilla walk speeds: 4.317 walk, 5.612 sprint, 1.311 sneak. */
    public static double baseSpeed(boolean sprinting, boolean sneaking, int speedAmp, int slowAmp) {
        return baseSpeed(sprinting, sneaking, speedAmp, slowAmp, 0.1);
    }

    /** Come sopra ma scalato sull'attribute movement_speed reale (base 0.1). */
    public static double baseSpeed(boolean sprinting, boolean sneaking, int speedAmp, int slowAmp, double moveAttr) {
        double base = sprinting ? 5.612 : (sneaking ? 1.311 : 4.317);
        base *= attrFactor(moveAttr, sprinting);
        if (speedAmp >= 0) base *= 1.0 + 0.2 * (speedAmp + 1);
        if (slowAmp >= 0) base *= Math.max(0.1, 1.0 - 0.15 * (slowAmp + 1));
        return base;
    }

    /**
     * Fattore attribute normalizzato: getValue() include gia il +30% sprint
     * (modifier minecraft:sprinting), mentre 5.612 lo contiene di suo.
     * Senza divisione conteresti sprint due volte (tetto 30% generoso).
     */
    public static double attrFactor(double moveAttr, boolean sprinting) {
        if (moveAttr <= 0) return 1.0;
        double f = moveAttr / 0.1;
        if (sprinting) f /= 1.3;
        return f;
    }

    /** Max legal move for the given input and dt. */
    public static Limit predict(Input in, long dtMillis) {
        Limit l = new Limit();
        double dtSec = Math.max(0.01, Math.min(1.0, dtMillis / 1000.0));

        if (in.flying || in.riding || in.gliding) {
            l.maxDistXZ = 50.0 * dtSec + 2.0;
            l.maxDyUp = 20.0 * dtSec + 1.0;
            l.minDyDown = -20.0 * dtSec - 1.0;
            return l;
        }
        if (in.onLadder) {
            l.maxDistXZ = 3.0 * dtSec + 0.5;
            l.maxDyUp = Math.max(0.25, 3.5 * dtSec);
            l.minDyDown = -Math.max(0.4, 5.0 * dtSec);
            return l;
        }
        if (in.inCobweb) {
            l.maxDistXZ = 2.5 * dtSec + 0.5;
            l.maxDyUp = 2.0 * dtSec + 0.5;
            l.minDyDown = -Math.max(0.4, 2.0 * dtSec);
            return l;
        }
        if (in.inWater) {
            double swim = in.sprinting ? 5.5 : 3.5;
            l.maxDistXZ = swim * dtSec + 0.8;
            l.maxDyUp = Math.max(0.4, 4.0 * dtSec);
            l.minDyDown = -Math.max(0.4, 3.0 * dtSec);
            return l;
        }
        if (in.inLava) {
            l.maxDistXZ = 2.0 * dtSec + 0.6;
            l.maxDyUp = Math.max(0.3, 2.5 * dtSec);
            l.minDyDown = -Math.max(0.4, 2.5 * dtSec);
            return l;
        }

        // Terra/aria: base + bonus sprint-jump.
        // Un salto da sprint copre ~5.6 b/s medi sul salto intero; su finestre
        // corte il burst istantaneo tocca ~7. Aggiungo margine fisso 1.2.
        double base = baseSpeed(in.sprinting, in.sneaking, in.speedAmp, in.slowAmp, in.moveAttr);
        if (in.sprinting && !in.onGround) base += 0.8;
        // Soffitto h2 + sprint-jump: salti bassi rapidissimi, vanilla ~2x sprint
        // (minecraft.wiki/w/Sprinting). Senza: ogni tunnel h2 flagga.
        if (in.sprintCeiling && in.sprinting) base *= 1.9;
        base *= in.slipperiness;
        // finestre <120ms: media rumorosa per pacchetti spezzati -> +10%
        if (dtMillis < 120) base *= 1.1;
        l.maxDistXZ = base * dtSec + 0.12;

        // Salita: salto vanilla 0.42/tick primo tick, poi gravita.
        double jump = JUMP_VY + (in.jumpAmp >= 0 ? JUMP_BOOST_PER_LEVEL * (in.jumpAmp + 1) : 0);
        if (in.onGround) {
            l.maxDyUp = Math.max(0.45, jump + 0.15);
        } else {
            // in aria: può solo continuare un salto iniziato (vy residua)
            // stima generosa: jump pieno se airTicks basso, poi decade
            double residua = jump * Math.pow(AIR_DRAG_Y, Math.min(in.airTicks, 12));
            l.maxDyUp = Math.max(0.15, residua + 0.1);
        }
        // Step: gradino istantaneo max 0.6 + margine
        if (in.onGround) l.maxDyUp = Math.max(l.maxDyUp, STEP_MAX + 0.15);

        // Discesa: gravita terminale ~3.9 b/s -> -0.195 per 50ms.
        // Sotto = caduta troppo lenta (hover/glide cheat).
        l.minDyDown = -Math.max(0.5, 4.2 * dtSec);
        return l;
    }

    /** Expected fallen blocks after N freefall ticks. */
    public static double expectedFallAfter(int airTicks) {
        double vy = 0;
        double fallen = 0;
        for (int i = 0; i < airTicks; i++) {
            vy = (vy - GRAVITY) * AIR_DRAG_Y;
            if (vy < -3.9) vy = -3.9;
            fallen -= vy;
        }
        return fallen;
    }
}
