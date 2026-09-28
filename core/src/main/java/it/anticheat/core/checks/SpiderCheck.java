package it.anticheat.core.checks;

import it.anticheat.core.Check;
import it.anticheat.core.CheckType;
import it.anticheat.core.PlayerData;
import it.anticheat.core.physics.LatencyComp;

/** Spider: salita costante a muro senza saltare ne scale (sperimentale). */
public class SpiderCheck extends Check {
    @Override public String name() { return "Spider"; }
    @Override public CheckType type() { return CheckType.MOVEMENT; }
    @Override public String description() { return "Scalata muri senza salti (sperimentale)"; }
    @Override public boolean experimental() { return true; }

    @Override
    public int checkMove(PlayerData data, MoveContext ctx) {
        if (ctx.onGround || ctx.flying || ctx.gliding || ctx.riding || ctx.inWater) {
            data.spiderStreak = 0;
            return 0;
        }
        // dy piccolo-positivo sostenuto in aria, senza scale: climb cheat.
        // I salti veri hanno dy > 0.35 al decollo e poi scendono.
        if (!ctx.onLadder && ctx.dy > 0.1 && ctx.dy < 0.35) {
            data.spiderStreak++;
            if (data.spiderStreak >= LatencyComp.needStreak(ctx.ping, 8)) {
                data.spiderStreak = 0;
                return 4;
            }
        } else {
            data.spiderStreak = 0;
        }
        return 0;
    }
}
