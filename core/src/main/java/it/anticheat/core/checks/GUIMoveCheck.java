package it.anticheat.core.checks;

import it.anticheat.core.Check;
import it.anticheat.core.CheckType;
import it.anticheat.core.PlayerData;
import it.anticheat.core.physics.LatencyComp;

/** GUIMove: si muove a terra con l'inventario aperto (vanilla sta fermo). */
public class GUIMoveCheck extends Check {
    @Override public String name() { return "GUIMove"; }
    @Override public CheckType type() { return CheckType.PLAYER; }
    @Override public String description() { return "Movimento con inventario aperto"; }

    @Override
    public int checkMove(PlayerData data, MoveContext ctx) {
        // riding/gliding esclusi: il veicolo continua a muoverti da solo con GUI aperta
        if (!data.invOpen || !ctx.onGround || ctx.flying || ctx.gliding || ctx.riding) {
            data.guiMoveStreak = 0;
            return 0;
        }
        // camminata = ~0.21/move, sprint ~0.28: sopra 0.18 ci si muove davvero
        if (ctx.distXZ > 0.18) {
            data.guiMoveStreak++;
            if (data.guiMoveStreak >= LatencyComp.needStreak(ctx.ping, 5)) {
                data.guiMoveStreak = 0;
                return 3;
            }
        } else {
            data.guiMoveStreak = 0;
        }
        return 0;
    }
}
