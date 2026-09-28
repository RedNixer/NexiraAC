package it.anticheat.core.checks;

import it.anticheat.core.Check;
import it.anticheat.core.CheckType;
import it.anticheat.core.PlayerData;

/** Jesus: in piedi sul liquido senza nuotare (piedi asciutti + liquido sotto). */
public class JesusCheck extends Check {
    @Override public String name() { return "Jesus"; }
    @Override public CheckType type() { return CheckType.MOVEMENT; }
    @Override public String description() { return "Cammina sui liquidi"; }

    @Override
    public int checkMove(PlayerData data, MoveContext ctx) {
        if (ctx.flying || ctx.gliding || ctx.riding) return 0;
        if (ctx.ping > 350) return 0;
        // piedi asciutti + liquido sotto + dichiara terra + non nuota = Jesus.
        // Chi sta DENTRO l'acqua (piedi nel liquido) e escluso: niente FP guadi.
        if (ctx.onGround && ctx.liquidBelow && !ctx.liquidFeet && !ctx.swimming) {
            data.jesusStreak++;
            if (data.jesusStreak >= 6) {
                data.jesusStreak = 0;
                return 4;
            }
        } else {
            data.jesusStreak = 0;
        }
        return 0;
    }
}
