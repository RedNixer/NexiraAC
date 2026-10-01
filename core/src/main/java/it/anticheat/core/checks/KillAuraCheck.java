package it.anticheat.core.checks;

import it.anticheat.core.Check;
import it.anticheat.core.CheckType;
import it.anticheat.core.PlayerData;
import it.anticheat.core.physics.LatencyComp;

/** KillAura: rate + cooldown 1.9+ ignorato + angoli impossibili. */
public class KillAuraCheck extends Check {
    @Override public String name() { return "KillAura"; }
    @Override public CheckType type() { return CheckType.COMBAT; }
    @Override public String description() { return "Pattern di attacchi da KillAura"; }

    @Override
    public int checkFight(PlayerData data, FightContext ctx) {
        // CPS umanamente impossibile in modo costante (soglia scalata col ping)
        if (ctx.dtSinceLastAttackMillis >= 0
                && ctx.dtSinceLastAttackMillis < LatencyComp.auraMinDt(ctx.ping)) {
            data.fastAttackStreak++;
            if (data.fastAttackStreak >= 4) return 5;
        } else {
            data.fastAttackStreak = 0;
        }
        // vanilla only deals full damage at full cooldown; staying low is a bot
        if (ctx.attackCooldown >= 0 && ctx.attackCooldown < 0.8) {
            data.cooldownStreak++;
            if (data.cooldownStreak >= 4) {
                data.cooldownStreak = 0;
                return 5;
            }
        } else {
            data.cooldownStreak = 0;
        }
        // scatto angolare impossibile tra due colpi consecutivi
        if (ctx.targetYawDiff > 120 && ctx.dtSinceLastAttackMillis < 150) return 4;
        // snap yaw proprio: rotazioni silenziose scattano a ogni colpo (stile Meteor),
        // i player veri girano fluido anche in mischia. Vale tra colpi a ritmo
        // normale (l'aura colpisce ogni ~500ms), non solo ravvicinati.
        if (ctx.yawSnapDiff >= 0 && ctx.yawSnapDiff > 70
                && ctx.dtSinceLastAttackMillis >= 0 && ctx.dtSinceLastAttackMillis < 2000) {
            data.auraSnapStreak++;
            if (data.auraSnapStreak >= 4) {
                data.auraSnapStreak = 0;
                return 5;
            }
        } else {
            data.auraSnapStreak = Math.max(0, data.auraSnapStreak - 1);
        }
        return 0;
    }
}
