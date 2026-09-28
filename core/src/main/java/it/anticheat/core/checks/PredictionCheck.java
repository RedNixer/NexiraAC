package it.anticheat.core.checks;

import it.anticheat.core.Check;
import it.anticheat.core.CheckType;
import it.anticheat.core.PlayerData;
import it.anticheat.core.physics.MovementPredictor;

/**
 * Prediction Fase 1: confronta ogni movimento col simulatore vanilla
 * invece di usare soglie fisse. Tre segnali indipendenti:
 * H) distanza orizzontale oltre il max simulato (speed/strafe/timer-orizzontale)
 * V) salita verticale oltre il max simulato (fly-step/jetpack)
 * Hover) discesa molto più lenta della caduta attesa (fly/glide lento)
 *
 * Differenze chiave vs i check a soglia:
 * - il tetto è calcolato per-stato (sprint, pozioni, acqua, scale, ghiaccio)
 *   invece di 9.5/12.0 fissi;
 * - il ping ALLARGA la tolleranza invece di spegnere il check (sopra 300
 *   non ritorna più 0: aggiunge margine e richiede streak più lungo).
 */
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
        in.airTicks = data.airTicks;
        // soul sand senza soul speed: vanilla rallenta parecchio
        double slip = 1.0;
        if (ctx.soulSand && !ctx.soulSpeed && ctx.onGround) slip = 0.5;
        in.slipperiness = slip;

        MovementPredictor.Limit lim = MovementPredictor.predict(in, ctx.dtMillis);

        // ping: margine additivo invece di spegnimento (lag = incertezza, non innocenza)
        double pingMargin = ctx.ping > 250 ? 0.6 : (ctx.ping > 120 ? 0.3 : 0.0);
        int needStreak = ctx.ping > 250 ? 4 : 2;

        // H) orizzontale oltre simulazione
        if (ctx.distXZ > lim.maxDistXZ + pingMargin) {
            data.predHStreak++;
            if (data.predHStreak >= needStreak) {
                data.predHStreak = 0;
                return ctx.distXZ > lim.maxDistXZ + pingMargin + 1.5 ? 5 : 3;
            }
        } else {
            data.predHStreak = Math.max(0, data.predHStreak - 1);
        }

        // V) salita oltre simulazione (con supporto check: step su terreno ok)
        if (ctx.dy > lim.maxDyUp + 0.25) {
            data.predVStreak++;
            if (data.predVStreak >= needStreak) {
                data.predVStreak = 0;
                return 5;
            }
        } else {
            data.predVStreak = Math.max(0, data.predVStreak - 1);
        }

        // Hover: in aria da 20+ tick (1s) senza acqua/scale/ragnatela,
        // scende molto meno della caduta attesa = volo stazionario lento
        if (!ctx.onGround && !ctx.inWater && !ctx.inCobweb && data.airTicks > 20) {
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
