package it.anticheat.core.checks;

import it.anticheat.core.Check;
import it.anticheat.core.CheckType;
import it.anticheat.core.PlayerData;
import it.anticheat.core.physics.LatencyComp;

/**
 * AimLock: mira congelata mentre ci si muove (aim che segue da solo).
 * Se la mira resta identica (+-0.5 gradi) per 3+ colpi mentre l'attaccante
 * si e spostato di oltre 1.5 blocchi, a mano avrebbe mancato: e lock.
 * Fermo con mob che avanza = legittimo (spostamento < 1.5).
 */
public class AimLockCheck extends Check {
    @Override public String name() { return "AimLock"; }
    @Override public CheckType type() { return CheckType.COMBAT; }
    @Override public String description() { return "Mira congelata che segue da sola"; }

    @Override
    public int checkFight(PlayerData data, FightContext ctx) {
        double dx = ctx.ax - data.lastHitX;
        double dy = ctx.ay - data.lastHitY;
        double dz = ctx.az - data.lastHitZ;
        double moved = Math.sqrt(dx * dx + dy * dy + dz * dz);
        data.lastHitX = ctx.ax;
        data.lastHitY = ctx.ay;
        data.lastHitZ = ctx.az;
        if (!data.hitInit) {
            data.hitInit = true;
            data.lastHitYaw = ctx.attackerYaw;
            data.lastHitPitch = ctx.attackerPitch;
            return 0;
        }
        double yDiff = Check.yawDiff(data.lastHitYaw, ctx.attackerYaw);
        double pDiff = Math.abs(data.lastHitPitch - ctx.attackerPitch);
        data.lastHitYaw = ctx.attackerYaw;
        data.lastHitPitch = ctx.attackerPitch;
        if (yDiff < 0.5 && pDiff < 0.5 && moved > 1.5) {
            data.lockStreak++;
            if (data.lockStreak >= LatencyComp.needStreak(ctx.ping, 3)) {
                data.lockStreak = 0;
                return 5;
            }
        } else {
            data.lockStreak = 0;
        }
        return 0;
    }
}
