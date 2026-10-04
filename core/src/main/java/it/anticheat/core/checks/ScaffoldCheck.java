package it.anticheat.core.checks;

import it.anticheat.core.Check;
import it.anticheat.core.CheckType;
import it.anticheat.core.PlayerData;

/**
 * Scaffold v3: distingue bridging cheat da pillar/speedbridge legittimi.
 * - Pillar: stessa colonna (gap ~0) -> mai flaggato.
 * - Speedbridge (sneak, ~2 b/s): serve ritmo > 6 b/s e streak 10 -> sicuro.
 * - Senza sneak: fascia 2.8-4.5 b/s -> streak 10, flag lieve (+2);
 *   oltre 4.5 b/s -> streak 6, flag +4.
 */
public class ScaffoldCheck extends Check {
    @Override public String name() { return "Scaffold"; }
    @Override public CheckType type() { return CheckType.WORLD; }
    @Override public String description() { return "Bridging cheat (avanzamento impossibile)"; }

    /** Gap minimo tra due piazzamenti (sotto = stessa colonna = pillar). */
    private static final double MIN_GAP = 0.35;
    /** Ritmo max tra piazzamenti (sopra = pause legittime). */
    private static final long MAX_DT = 1500;

    public int onPlace(PlayerData data, boolean onGround, float pitch, double x, double z, boolean sneaking, float yawDeg) {
        long now = System.currentTimeMillis();
        long dt = data.lastPlaceTime == 0 ? -1 : now - data.lastPlaceTime;
        double dx = x - data.lastPlaceX;
        double dz = z - data.lastPlaceZ;
        double gap = (dt < 0 || dt > MAX_DT) ? 0 : Math.sqrt(dx * dx + dz * dz);
        int out = 0;

        // Yaw-snap: rotazioni silenziose scattano di 30-90 gradi a ogni piazza,
        // mentre bridgiando legittimo si gira fluido. Vale anche a terra.
        if (dt > 0 && dt <= MAX_DT) {
            double snap = Check.yawDiff(data.lastPlaceYaw, yawDeg);
            if (snap > 25) {
                data.placeYawStreak++;
                if (data.placeYawStreak >= 4) {
                    data.placeYawStreak = 0;
                    out = 4;
                }
            } else {
                data.placeYawStreak = 0;
            }
        }
        data.lastPlaceTime = now;
        data.lastPlaceX = x;
        data.lastPlaceZ = z;
        data.lastPlaceYaw = yawDeg;

        if (dt < 0 || dt > MAX_DT || pitch < 45) {
            if (data.scaffoldStreak > 0) data.scaffoldStreak--;
            return out;
        }
        if (gap < MIN_GAP) {
            // pillar in colonna
            data.scaffoldStreak = 0;
            return out;
        }
        double rate = gap / (dt / 1000.0); // blocchi/sec di avanzamento
        if (rate < 2.8) {
            // ritmo umano (speedbridge ~2 b/s)
            data.scaffoldStreak = Math.max(0, data.scaffoldStreak - 1);
            return out;
        }
        data.scaffoldStreak++;
        if (onGround) {
            // Meteor piazza anche camminando sul bridge: a terra serve ritmo
            // impossibile a mano (>6 b/s; sprint max 5.6) e streak lunga
            if (rate > 6.0 && data.scaffoldStreak >= 8) {
                data.scaffoldStreak = 0;
                return Math.max(out, 3);
            }
            if (data.scaffoldStreak > 20) data.scaffoldStreak = 20; // tetto
            return out;
        }
        if (sneaking) {
            // speedbridge col sneak: solo ritmi assurdi, streak lunga, flag lieve
            if (rate > 6.0 && data.scaffoldStreak >= 10) {
                data.scaffoldStreak = 0;
                return Math.max(out, 2);
            }
            if (data.scaffoldStreak > 20) data.scaffoldStreak = 20; // tetto
            return out;
        }
        if (rate > 4.5 && data.scaffoldStreak >= 6) {
            data.scaffoldStreak = 0;
            return Math.max(out, 4);
        }
        if (data.scaffoldStreak >= 10) {
            data.scaffoldStreak = 0;
            return Math.max(out, 2);
        }
        return out;
    }

    /** Angolo tra direzione sguardo (yaw/pitch MC) e vettore. Gradi 0-180. */
    public static double lookAngleDiff(double yawDeg, double pitchDeg, double dx, double dy, double dz) {
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        double lx = -Math.sin(yaw) * Math.cos(pitch);
        double ly = -Math.sin(pitch);
        double lz = Math.cos(yaw) * Math.cos(pitch);
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6) return 0;
        double dot = (lx * dx + ly * dy + lz * dz) / len;
        dot = Math.max(-1.0, Math.min(1.0, dot));
        return Math.toDegrees(Math.acos(dot));
    }

    // TODO: rotPlace>55 con speedbridge veloce borderline (04/10).
    // Se arriva un log con flag in bridging dritto legit, alzare a 65
    // o richiedere pitch>60. Non tocco senza dati.
    /**
     * RotationPlace + FarPlace: il player guarda davvero il blocco?
     * Becca gli scaffold con rotazioni silenziose (piazzano guardando altrove)
     * e i piazzamenti oltre la gittata survival (~4.5 + tolleranza).
     */
    public int onPlaceRotation(PlayerData data, double angleDeg, double dist) {
        if (dist > 6.0) {
            data.farPlaceStreak++;
            if (data.farPlaceStreak >= 3) {
                data.farPlaceStreak = 0;
                return 5;
            }
        } else {
            data.farPlaceStreak = 0;
        }
        if (angleDeg > 55) {
            data.rotPlaceStreak++;
            if (data.rotPlaceStreak >= 4) {
                data.rotPlaceStreak = 0;
                return 4;
            }
        } else {
            data.rotPlaceStreak = Math.max(0, data.rotPlaceStreak - 1);
        }
        return 0;
    }
}
