package it.anticheat.core.checks;

import it.anticheat.core.Check;
import it.anticheat.core.CheckType;
import it.anticheat.core.PlayerData;

/** Multitask: colpisce mentre ha lo scudo alzato (sperimentale). */
public class MultitaskCheck extends Check {
    @Override public String name() { return "Multitask"; }
    @Override public CheckType type() { return CheckType.COMBAT; }
    @Override public String description() { return "Attacco con scudo alzato (sperimentale)"; }
    @Override public boolean experimental() { return true; }

    @Override
    public int checkFight(PlayerData data, FightContext ctx) {
        if (!ctx.blocking) {
            data.multitaskStreak = 0;
            return 0;
        }
        data.multitaskStreak++;
        if (data.multitaskStreak >= 3) {
            data.multitaskStreak = 0;
            return 3;
        }
        return 0;
    }
}
