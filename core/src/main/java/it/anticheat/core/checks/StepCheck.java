package it.anticheat.core.checks;

import it.anticheat.core.Check;
import it.anticheat.core.CheckType;
import it.anticheat.core.PlayerData;
import it.anticheat.core.physics.LatencyComp;

/** Step: salita di un gradino impossibile in un singolo movimento (salto max ~0.42). */
public class StepCheck extends Check {
    @Override public String name() { return "Step"; }
    @Override public CheckType type() { return CheckType.MOVEMENT; }
    @Override public String description() { return "Gradini troppo alti in un colpo solo"; }

    @Override
    public int checkMove(PlayerData data, MoveContext ctx) {
        if (ctx.flying || ctx.gliding || ctx.riding || ctx.inWater || ctx.onLadder) {
            data.stepStreak = 0;
            return 0;
        }
        // da terra, un salto legittimo alza al massimo ~0.45 per movimento
        // (+margine ping: col lag la Y arriva a scatti, mai spegnere il check).
        // Streak 2: il singolo picco da burst-lag non flagga piu.
        if (ctx.onGround && ctx.dy > 0.7 + LatencyComp.margin(ctx.ping)
                && ctx.dy < 3.0 && ctx.distXZ > 0.05) {
            data.stepStreak++;
            if (data.stepStreak >= 2) {
                data.stepStreak = 0;
                return 3;
            }
        } else {
            data.stepStreak = Math.max(0, data.stepStreak - 1);
        }
        return 0;
    }
}
