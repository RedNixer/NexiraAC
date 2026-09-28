package it.anticheat.core.checks;

import it.anticheat.core.Check;
import it.anticheat.core.CheckType;
import it.anticheat.core.PlayerData;
import it.anticheat.core.physics.LatencyComp;

/**
 * AimSnap: scatti di rotazione inumani (snap silenziosi di aura/scaffold).
 * Conta yaw >35 gradi o pitch >25 gradi in un singolo movimento su finestra
 * di 2s: 6+ snap = bot, 1-2 isolati = flick umani. I tempi sono registrati
 * sempre in handleMove (servono anche al bow-snap), qui solo valutazione.
 */
public class AimSnapCheck extends Check {
    @Override public String name() { return "AimSnap"; }
    @Override public CheckType type() { return CheckType.COMBAT; }
    @Override public String description() { return "Scatti di mira innaturali"; }

    @Override
    public int checkMove(PlayerData data, MoveContext ctx) {
        long now = System.currentTimeMillis();
        int n = 0;
        for (long t : data.snapTimes) {
            if (now - t <= 2000) n++;
        }
        if (n >= LatencyComp.needStreak(ctx.ping, 6)) {
            data.snapTimes.clear();
            return 4;
        }
        return 0;
    }
}
