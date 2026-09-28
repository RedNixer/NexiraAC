package it.anticheat.core.checks;

import it.anticheat.core.Check;
import it.anticheat.core.CheckType;
import it.anticheat.core.PlayerData;

/** Step: salita di un gradino impossibile in un singolo movimento (salto max ~0.42). */
public class StepCheck extends Check {
    @Override public String name() { return "Step"; }
    @Override public CheckType type() { return CheckType.MOVEMENT; }
    @Override public String description() { return "Gradini troppo alti in un colpo solo"; }

    @Override
    public int checkMove(PlayerData data, MoveContext ctx) {
        if (ctx.flying || ctx.gliding || ctx.riding || ctx.inWater || ctx.onLadder) return 0;
        if (ctx.ping > 300) return 0;
        // da terra, un salto legittimo alza al massimo ~0.45 per movimento
        if (ctx.onGround && ctx.dy > 0.7 && ctx.dy < 3.0 && ctx.distXZ > 0.05) return 3;
        return 0;
    }
}
