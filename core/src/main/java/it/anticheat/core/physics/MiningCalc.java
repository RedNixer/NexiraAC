package it.anticheat.core.physics;

/**
 * Tempi di scavo vanilla Fase 4, pura Java.
 * Formula vanilla: tempoMinimo(tick) = hardness * 1.5 * 20 / speedMultiplier,
 * dove speedMultiplier = toolMult * (1 + 0.3*eff^2+...) * haste * aquaAffinity...
 * Qui versione server-side osservabile: niente posizione/acqua dal client,
 * solo tool + efficiency + haste + fatigue (quelli che l'adapter vede davvero).
 *
 * Semplificazioni oneste (documentate, non nascoste):
 * - beacon haste incluso via hasteAmp (stesso effetto pozione);
 * - mining fatigue incluso come divisore (devastante: pena severa);
 * - in acqua senza aqua affinity: vanilla x5 tempo — l'adapter passa inWater,
 *   qui applicato come x5 se noto;
 * - non in aria: vanilla x5 se non a terra — l'adapter passa onGround.
 */
public final class MiningCalc {

    private MiningCalc() {}

    /** Moltiplicatore base attrezzo vanilla (mano=1, oro=12, netherite=9...). */
    public static double toolMult(String tool) {
        if (tool == null) return 1.0;
        switch (tool) {
            case "WOOD": return 2.0;
            case "STONE": return 4.0;
            case "IRON": return 6.0;
            case "DIAMOND": return 8.0;
            case "NETHERITE": return 9.0;
            case "GOLD": return 12.0;
            default: return 1.0; // mano, cesoie base, altro
        }
    }

    /** Bonus efficiency vanilla: +30% composto per livello (1+lvl^2*... ). */
    public static double effMult(int effLvl) {
        if (effLvl <= 0) return 1.0;
        return 1.0 + effLvl * effLvl * 0.3 + 0.3;
    }

    /** Haste: +20% per livello (amp 0 = Haste I). */
    public static double hasteMult(int hasteAmp) {
        if (hasteAmp < 0) return 1.0;
        return 1.0 + 0.2 * (hasteAmp + 1);
    }

    /** Mining fatigue: divisore pesante (III = quasi fermo). */
    public static double fatigueDiv(int fatigueAmp) {
        if (fatigueAmp < 0) return 1.0;
        switch (Math.min(fatigueAmp, 3)) {
            case 0: return 3.0;
            case 1: return 9.0;
            case 2: return 27.0;
            default: return 81.0;
        }
    }

    /**
     * Tempo minimo legittimo in ms per rompere il blocco.
     * Ritorna -1 se insta-break (hardness<=0): nessun giudizio possibile.
     */
    public static long minTimeMs(float hardness, String tool, int effLvl,
            int hasteAmp, int fatigueAmp, boolean inWater, boolean onGround) {
        if (hardness <= 0) return -1;
        double speed = toolMult(tool) * effMult(effLvl) * hasteMult(hasteAmp);
        speed /= fatigueDiv(fatigueAmp);
        if (inWater) speed /= 5.0;
        if (!onGround) speed /= 5.0;
        if (speed <= 0) return Long.MAX_VALUE;
        // il danno per tick e speed/30; tick necessari = ceil(hardness*30/speed)... in tick
        double ticks = Math.ceil(hardness * 1.5 * 20 / speed);
        // best-tool corretto rompe comunque in ticks interi; margine -15% tolleranza rete
        return (long) (ticks * 50 * 0.85);
    }
}
