package it.anticheat.core.checks;

import it.anticheat.core.Check;
import it.anticheat.core.CheckType;
import it.anticheat.core.PlayerData;
import it.anticheat.core.physics.LatencyComp;

/** KillAura: rate + cooldown 1.9+ ignorato + angoli + anti-smooth (aura fluida). */
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
        // i player veri girano fluido anche in mischia. Soglia 90° streak 5:
        // flick umani da 70-90° in mischia 1v2 sono normali.
        if (ctx.yawSnapDiff >= 0 && ctx.yawSnapDiff > 90
                && ctx.dtSinceLastAttackMillis >= 0 && ctx.dtSinceLastAttackMillis < 1000) {
            data.auraSnapStreak++;
            if (data.auraSnapStreak >= 5) {
                data.auraSnapStreak = 0;
                return 5;
            }
        } else {
            data.auraSnapStreak = Math.max(0, data.auraSnapStreak - 1);
        }
        // ANTI-SMOOTH SOLO PvP (Vulcan docet: vs mob i ritmici flaggano
        // player metodici — menare un mob fermo a ritmo e legit).
        // Vs mob restano: rate estremo, cooldown basso, snap, reach, wall.
        if (!ctx.targetIsPlayer) {
            data.hitDts.clear();
            data.perfectCdStreak = 0;
            data.targetSwitchStreak = 0;
            if (ctx.targetId >= 0) data.lastTargetId = ctx.targetId;
            return 0;
        }
        // Ritmo metronomo: umani std 100ms+, bot dt quasi identici.
        if (ctx.dtSinceLastAttackMillis > 0) {
            data.hitDts.addLast(ctx.dtSinceLastAttackMillis);
            while (data.hitDts.size() > 8) data.hitDts.pollFirst();
        }
        if (data.hitDts.size() >= 6) {
            java.util.ArrayList<Long> dts = new java.util.ArrayList<>(data.hitDts);
            double sum = 0;
            for (long v : dts) sum += v;
            double mean = sum / dts.size();
            double var = 0;
            for (long v : dts) var += (v - mean) * (v - mean);
            double std = Math.sqrt(var / dts.size());
            // ritmo 4-20 colpi/s? no: aura 1.9+ = 250-1200ms tra colpi, std <30 = metronomo
            if (mean > 250 && mean < 1200 && std < 30) {
                data.hitDts.clear();
                return 4;
            }
        }
        // cooldown sempre perfetto (0.95-1.0) x5: il bot aspetta il pieno al
        // millisecondo, l'umano anticipa/ritarda sempre qualche colpo
        if (ctx.attackCooldown >= 0.95 && ctx.attackCooldown <= 1.0) {
            data.perfectCdStreak++;
            if (data.perfectCdStreak >= 5) {
                data.perfectCdStreak = 0;
                data.hitDts.clear();
                return 4;
            }
        } else if (ctx.attackCooldown >= 0) {
            data.perfectCdStreak = 0;
        }
        // switch target: bersaglio diverso a ogni colpo x4 in fight ravvicinato
        // (1v1 umano non switcha mai, l'aura multitarget sì)
        if (ctx.targetId >= 0 && data.lastTargetId >= 0 && ctx.targetId != data.lastTargetId
                && ctx.dtSinceLastAttackMillis >= 0 && ctx.dtSinceLastAttackMillis < 3000) {
            data.targetSwitchStreak++;
            if (data.targetSwitchStreak >= 4) {
                data.targetSwitchStreak = 0;
                return 5;
            }
        } else if (ctx.targetId == data.lastTargetId) {
            data.targetSwitchStreak = 0;
        }
        if (ctx.targetId >= 0) data.lastTargetId = ctx.targetId;
        return 0;
    }
}
