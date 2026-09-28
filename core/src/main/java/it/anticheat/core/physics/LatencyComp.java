package it.anticheat.core.physics;

/**
 * Compensazione latenza Fase 3, pura Java.
 * Principio: il ping e incertezza, non innocenza. Invece di spegnere i check
 * sopra una soglia (return 0 = esenzione di fatto per chi lagga o simula lag),
 * la tolleranza cresce in modo continuo col ping e lo streak richiesto
 * si allunga. Un cheater a 400ms deve barare più a lungo e più forte
 * per flaggare, ma non e mai invisibile.
 *
 * Stima margine: a P ms di ping, il client può essere avanti di ~P/2 ms
 * di movimento non ancora visto (one-way delay). A 5.6 b/s sono ~P/2*5.6/1000
 * blocchi di incertezza per movimento. Il margine copre quello + rumore.
 */
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
