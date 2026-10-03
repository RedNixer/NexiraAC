package it.anticheat.core.checks;

import it.anticheat.core.Check;
import it.anticheat.core.CheckType;
import it.anticheat.core.PlayerData;
import it.anticheat.core.physics.LatencyComp;

/** Horizontal speed over 120ms windows + streak, metronome and hop rhythm. */
public class SpeedCheck extends Check {
    @Override public String name() { return "Speed"; }
    @Override public CheckType type() { return CheckType.MOVEMENT; }
    @Override public String description() { return "Velocita orizzontale impossibile"; }

    @Override
    public int checkMove(PlayerData data, MoveContext ctx) {
        if (ctx.flying || ctx.gliding || ctx.riding || ctx.inWater || ctx.onLadder) {
            reset(data);
            data.speedWasGround = ctx.onGround;
            return 0;
        }
        if (ctx.dtMillis <= 0 || ctx.dtMillis > 600) {
            reset(data);
            data.speedWasGround = ctx.onGround;
            return 0;
        }
        // expected knockback subtracted instead of blanking the check
        double[] kb = it.anticheat.core.AnticheatCore.consumeKnockback(data, ctx.ping);
        double kbXZ = Math.sqrt(kb[0] * kb[0] + kb[2] * kb[2]);
        double pingBonus = LatencyComp.margin(ctx.ping) * 2.0;
        long now = System.currentTimeMillis();

        // Hop rhythm: decollo (terra->aria spingendo su) a ritmo meccanico.
        if (!ctx.onGround && data.speedWasGround && ctx.dy > 0.3) {
            data.hopTimes.addLast(now);
            while (data.hopTimes.size() > 6) data.hopTimes.pollFirst();
        }
        data.speedWasGround = ctx.onGround;

        // finestra 250ms per il metronomo: accumula OGNI movimento
        if (data.speedWinStart == 0) data.speedWinStart = now;
        data.speedWinDist += ctx.distXZ;
        data.speedWinMs += ctx.dtMillis;

        data.speedPendingDist += ctx.distXZ;
        data.speedPendingMs += ctx.dtMillis;
        if (data.speedPendingMs < 120) return 0; // accumula, non giudicare

        double speed = data.speedPendingDist / (data.speedPendingMs / 1000.0);
        double winSec = data.speedPendingMs / 1000.0;
        data.speedPendingDist = 0;
        data.speedPendingMs = 0;

        // Pozione Speed e discese alzano il tetto legittimo.
        double potionBonus = ctx.speedAmp >= 0 ? 2.2 * (ctx.speedAmp + 1) : 0;
        double hillBonus = ctx.dy < -1.0 ? 2.0 : 0;
        double lim = 9.5 + potionBonus + hillBonus + pingBonus;
        double hard = 12.0 + potionBonus + hillBonus + pingBonus;
        // attribute movement_speed custom (mod/beacon): scala i tetti.
        // attrFactor normalizza il +30% sprint (contato due volte altrimenti)
        double af = it.anticheat.core.physics.MovementPredictor.attrFactor(
            ctx.moveSpeedAttr, ctx.sprinting);
        if (Math.abs(af - 1.0) > 1e-9) {
            lim *= af;
            hard *= af;
        }
        // knockback-adjusted speed vs limits (winSec salvato prima del reset)
        double kbSpeed = kbXZ / Math.max(0.05, winSec);
        double speedAdj = Math.max(0, speed - kbSpeed);
        if (speedAdj > hard) data.speedStreak += 2;
        else if (speedAdj > lim) data.speedStreak++;
        else {
            data.speedStreak = Math.max(0, data.speedStreak - 1);
        }
        if (data.speedStreak >= LatencyComp.needStreak(ctx.ping, 2)) {
            data.speedStreak = 0;
            return speed > hard ? 6 : 2;
        }

        // Con pozione Speed il metronomo non vale (camminare a 6 b/s e normale):
        // resta solo la finestra scalata sopra.
        if (ctx.speedAmp >= 0) return checkHop(data, ctx, speed);
        int extra = checkMetronome(data, ctx, now);
        if (extra > 0) return extra;
        return checkHop(data, ctx, speed);
    }

    /** Metronomo: 5-6.8 b/s costanti per ~2s SENZA sprintare (Speed Vanilla). */
    private int checkMetronome(PlayerData data, Check.MoveContext ctx, long now) {
        if (now - data.speedWinStart < 250) return 0;
        double w = data.speedWinMs <= 0 ? 0 : data.speedWinDist / (data.speedWinMs / 1000.0);
        data.speedWinDist = 0;
        data.speedWinMs = 0;
        data.speedWinStart = now;
        data.speedBuckets.addLast(w);
        while (data.speedBuckets.size() > 8) data.speedBuckets.pollFirst();
        if (data.speedBuckets.size() < 8 || ctx.sprinting) {
            if (ctx.sprinting) data.speedBuckets.clear();
            return 0;
        }
        double sum = 0;
        for (double v : data.speedBuckets) sum += v;
        double mean = sum / data.speedBuckets.size();
        double var = 0;
        for (double v : data.speedBuckets) var += (v - mean) * (v - mean);
        double std = Math.sqrt(var / data.speedBuckets.size());
        if (mean > 5.0 && mean < 6.8 && std < 0.5) {
            data.speedBuckets.clear();
            return 3;
        }
        return 0;
    }

    /** Hop ritmico: decolli a intervalli quasi identici + velocita alta (su finestra, non singolo pacchetto). */
    private int checkHop(PlayerData data, Check.MoveContext ctx, double winSpeed) {
        if (data.hopTimes.size() < 5) return 0;
        java.util.ArrayList<Long> t = new java.util.ArrayList<>(data.hopTimes);
        java.util.ArrayList<Long> gaps = new java.util.ArrayList<>();
        for (int i = 1; i < t.size(); i++) gaps.add(t.get(i) - t.get(i - 1));
        double sum = 0;
        for (long g : gaps) sum += g;
        double mean = sum / gaps.size();
        double var = 0;
        for (long g : gaps) var += (g - mean) * (g - mean);
        double std = Math.sqrt(var / gaps.size());
        // sprint-jump legit tocca 7-8 a ritmo costante; cheat bhop tiene 9+
        double hopMin = 8.5 + (ctx.speedAmp >= 0 ? 2.2 * (ctx.speedAmp + 1) : 0);
        if (mean > 120 && mean < 700 && std < 50 && winSpeed > hopMin) {
            data.hopTimes.clear();
            return 3;
        }
        return 0;
    }

    private void reset(PlayerData data) {
        data.speedPendingDist = 0;
        data.speedPendingMs = 0;
        data.speedStreak = 0;
        data.speedWinDist = 0;
        data.speedWinMs = 0;
        data.speedWinStart = 0;
        data.speedBuckets.clear();
        data.hopTimes.clear();
    }
}
