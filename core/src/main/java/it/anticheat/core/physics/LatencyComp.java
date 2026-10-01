package it.anticheat.core.physics;

/** Ping is uncertainty, not innocence: margins grow, checks never switch off. */
public final class LatencyComp {

    private LatencyComp() {}

    /** Margine additivo in blocchi per il ping dato. */
    public static double margin(int pingMs) {
        if (pingMs <= 0) return 0;
        // one-way ~ping/2 a 5.6 b/s + base 0.1, tetto 1.2
        double m = 0.1 + (pingMs / 2.0) * 5.6 / 1000.0;
        return Math.min(1.2, m);
    }

    /** Streak richiesto per flaggare al ping dato (base 2, fino a 5). */
    public static int needStreak(int pingMs, int base) {
        if (pingMs <= 120) return base;
        if (pingMs <= 250) return base + 1;
        if (pingMs <= 400) return base + 2;
        return base + 3;
    }

    /** Limite reach scalato continuo (3.0 base + margine ping, tetto 4.5). */
    public static double reachLimit(int pingMs) {
        return Math.min(4.5, 3.0 + margin(pingMs) + 0.2);
    }

    /** Finestra max tra colpi killaura scalata (lag = colpi raggruppati). */
    public static long auraMinDt(int pingMs) {
        // sotto questo dt tra colpi e impossibile anche col lag
        return pingMs > 250 ? 35 : 55;
    }
}
