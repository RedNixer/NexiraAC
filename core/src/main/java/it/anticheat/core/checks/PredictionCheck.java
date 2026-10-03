package it.anticheat.core.checks;

import it.anticheat.core.Check;
import it.anticheat.core.CheckType;
import it.anticheat.core.PlayerData;
import it.anticheat.core.physics.MovementPredictor;

/** Every move vs the vanilla simulator. H: horizontal, V: vertical, hover: slow fall. */
public class PredictionCheck extends Check {
    @Override public String name() { return "Prediction"; }
    @Override public CheckType type() { return CheckType.MOVEMENT; }
    @Override public String description() { return "Movimento oltre la simulazione vanilla"; }

    @Override
    public int checkMove(PlayerData data, MoveContext ctx) {
        if (ctx.flying || ctx.gliding || ctx.riding || ctx.onLadder) {
            reset(data);
            return 0;
        }
        if (ctx.dtMillis <= 0 || ctx.dtMillis > 600) {
            reset(data);
            return 0;
        }

        MovementPredictor.Input in = new MovementPredictor.Input();
        in.onGround = ctx.onGround;
        in.sprinting = ctx.sprinting;
        in.sneaking = false;
        in.inWater = ctx.inWater;
        in.inLava = ctx.liquidFeet;
        in.onLadder = ctx.onLadder;
        in.inCobweb = ctx.inCobweb;
        in.speedAmp = ctx.speedAmp;
        in.jumpAmp = ctx.jumpAmp;
        in.moveAttr = ctx.moveSpeedAttr > 0 ? ctx.moveSpeedAttr : 0.1;
        in.sprintCeiling = ctx.ceilingAbove && ctx.sprinting;
        in.airTicks = data.airTicks;
        // soul sand senza soul speed: vanilla rallenta parecchio
        double slip = 1.0;
        if (ctx.soulSand && !ctx.soulSpeed && ctx.onGround) slip = 0.5;
        // ghiaccio: attrito ~0.98 vs 0.6, sprint-jump scivola a 8-10 b/s legit
        if (ctx.onIce) slip = 1.5;
        in.slipperiness = slip;

        MovementPredictor.Limit lim = MovementPredictor.predict(in, ctx.dtMillis);

        // ping widens tolerance instead of disabling the check
        double pingMargin = ctx.ping > 250 ? 0.6 : (ctx.ping > 120 ? 0.3 : 0.0);
        int needStreak = ctx.ping > 250 ? 4 : 2;

        // H) horizontal over simulation, judged on 120ms windows like Speed:
        // single-packet samples are too noisy (split packets, ceiling clips)
        data.predPendingDist += ctx.distXZ;
        data.predPendingMs += ctx.dtMillis;
        if (data.predPendingMs < 120) return 0;
        double winDist = data.predPendingDist;
        long winMs = data.predPendingMs;
        data.predPendingDist = 0;
        data.predPendingMs = 0;
        MovementPredictor.Limit wlim = MovementPredictor.predict(in, winMs);
        double winSpeed = winDist / (winMs / 1000.0);
        double winMax = wlim.maxDistXZ / (winMs / 1000.0);
        data.lastPredWinSpeed = winSpeed;
        data.lastPredWinMax = winMax;
        // Soffitto: salti troncati continui = sempre fase boost iniziale
        // (legit 7+ su finestra). Non spegnere: streak doppio + margine.
        double ceilMargin = ctx.ceilingAbove ? 0.5 : 0.0;
        int needH = ctx.ceilingAbove ? needStreak * 2 : needStreak;
        if (winSpeed > winMax + pingMargin + ceilMargin) {
            data.predHStreak++;
            if (data.predHStreak >= needH) {
                data.predHStreak = 0;
                return winSpeed > winMax + pingMargin + ceilMargin + 1.5 ? 5 : 3;
            }
        } else {
            data.predHStreak = Math.max(0, data.predHStreak - 1);
        }

        // V) climbing over simulation.
        // Soffitto sopra la testa: il salto viene troncato dalla collisione,
        // dy diventa irregolare (onGround flickera, dt burst sommano mezzi salti):
        // salire attraverso un blocco solido e comunque impossibile, skip.
        if (ctx.ceilingAbove) {
            data.predVStreak = 0;
        } else if (ctx.dy > lim.maxDyUp + 0.25) {
            data.predVStreak++;
            if (data.predVStreak >= needStreak) {
                data.predVStreak = 0;
                return 5;
            }
        } else {
            data.predVStreak = Math.max(0, data.predVStreak - 1);
        }

        // Hover: airborne 20+ ticks falling far less than expected.
        // Slow falling / levitation: hover vanilla, skip (non resettare il ref).
        if (!ctx.onGround && !ctx.inWater && !ctx.inCobweb && !ctx.slowFall && data.airTicks > 20) {
            if (data.predFallRefTicks == 0) {
                data.predFallRefY = ctx.y;
                data.predFallRefTicks = data.airTicks;
            }
            int refTicks = data.airTicks - data.predFallRefTicks;
            if (refTicks >= 20) {
                double expected = MovementPredictor.expectedFallAfter(refTicks);
                double actual = data.predFallRefY - ctx.y;
                // caduto meno del 30% dell'atteso + quasi fermo orizzontale = hover
                if (expected > 2.0 && actual < expected * 0.3 && ctx.distXZ < 0.4) {
                    data.predHoverStreak++;
                    if (data.predHoverStreak >= 2) {
                        data.predHoverStreak = 0;
                        data.predFallRefTicks = 0;
                        return 4;
                    }
                } else {
                    data.predHoverStreak = 0;
                    // ri-ancora il riferimento per la prossima finestra
                    data.predFallRefY = ctx.y;
                    data.predFallRefTicks = data.airTicks;
                }
            }
        } else {
            data.predHoverStreak = 0;
            data.predFallRefTicks = 0;
        }

        return 0;
    }

    private void reset(PlayerData data) {
        data.predHStreak = 0;
        data.predVStreak = 0;
        data.predHoverStreak = 0;
        data.predFallRefTicks = 0;
    }
}
