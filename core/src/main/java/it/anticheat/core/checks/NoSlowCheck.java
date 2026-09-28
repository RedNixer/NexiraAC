package it.anticheat.core.checks;

import it.anticheat.core.Check;
import it.anticheat.core.CheckType;
import it.anticheat.core.PlayerData;

/** NoSlow: veloce dove dovresti essere lento. Tre segnali indipendenti. */
public class NoSlowCheck extends Check {
    @Override public String name() { return "NoSlow"; }
    @Override public CheckType type() { return CheckType.MOVEMENT; }
    @Override public String description() { return "Nessun rallentamento (item/web/soulsand)"; }

    @Override
    public int checkMove(PlayerData data, MoveContext ctx) {
        if (ctx.flying || ctx.gliding || ctx.riding || ctx.inWater) {
            data.noSlowStreak = 0;
            return 0;
        }
        if (ctx.ping > 350 || ctx.dtMillis <= 0 || ctx.dtMillis > 600) return 0;
        double speed = ctx.distXZ / (ctx.dtMillis / 1000.0);
        // A) item/scudo: mangiando/arco vanilla cammini a ~1.3-2.5
        boolean itemSlow = (ctx.blocking || ctx.usingItem) && speed > 4.5;
        // B) ragnatela/neve: dentro vai a <1 b/s, oltre 2.5 e cheat
        boolean webSlow = ctx.inCobweb && speed > 2.5;
        // C) soul sand senza soul speed: a terra rallenta (~2 b/s);
        //    saltellare legit supera i 4 di rado e mai 4 volte di fila a terra
        boolean soulSlow = ctx.soulSand && !ctx.soulSpeed && ctx.onGround && speed > 4.0;
        if (itemSlow || webSlow || soulSlow) {
            data.noSlowStreak++;
            if (data.noSlowStreak >= 4) {
                data.noSlowStreak = 0;
                return 3;
            }
        } else {
            data.noSlowStreak = 0;
        }
        return 0;
    }
}
