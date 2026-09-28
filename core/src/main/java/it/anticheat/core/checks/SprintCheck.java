package it.anticheat.core.checks;

import it.anticheat.core.Check;
import it.anticheat.core.CheckType;
import it.anticheat.core.PlayerData;

/** Sprint impossibile: sprint con fame bassa, cecita o mentre si usa item/scudo. */
public class SprintCheck extends Check {
    @Override public String name() { return "Sprint"; }
    @Override public CheckType type() { return CheckType.MOVEMENT; }
    @Override public String description() { return "Sprint in condizioni impossibili"; }

    @Override
    public int checkMove(PlayerData data, MoveContext ctx) {
        if (!ctx.sprinting) {
            data.sprintStreak = 0;
            return 0;
        }
        if (ctx.ping > 350) return 0;
        if (ctx.hungry || ctx.blind || ctx.blocking || ctx.usingItem) {
            data.sprintStreak++;
            if (data.sprintStreak >= 3) {
                data.sprintStreak = 0;
                return 3;
            }
        } else {
            data.sprintStreak = 0;
        }
        return 0;
    }
}
