package it.anticheat.core.checks;

import it.anticheat.core.Check;
import it.anticheat.core.CheckType;
import it.anticheat.core.PlayerData;
import it.anticheat.core.physics.LatencyComp;

/** Jesus: in piedi sul liquido senza nuotare (piedi asciutti + liquido sotto). */
public class JesusCheck extends Check {
    @Override public String name() { return "Jesus"; }
    @Override public CheckType type() { return CheckType.MOVEMENT; }
    @Override public String description() { return "Cammina sui liquidi"; }

    @Override
    public int checkMove(PlayerData data, MoveContext ctx) {
        if (ctx.flying || ctx.gliding || ctx.riding) return 0;
        if (ctx.onGround && ctx.liquidBelow && !ctx.liquidFeet && !ctx.swimming) {
            data.jesusStreak++;
            if (data.jesusStreak >= LatencyComp.needStreak(ctx.ping, 6)) {
                data.jesusStreak = 0;
                return 4;
            }
        } else {
            data.jesusStreak = 0;
        }
        return 0;
    }
}
