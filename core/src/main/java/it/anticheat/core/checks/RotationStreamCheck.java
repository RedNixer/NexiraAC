package it.anticheat.core.checks;

import it.anticheat.core.Check;
import it.anticheat.core.CheckType;
import it.anticheat.core.PlayerData;

/** Raw LOOK packets: one-packet snaps, out-of-range injections, duplicates, off-grid GCD. */
public class RotationStreamCheck extends Check {
    @Override public String name() { return "RotationStream"; }
    @Override public CheckType type() { return CheckType.COMBAT; }
    @Override public String description() { return "Rotazioni raw impossibili (solo Paper+PL)"; }

    /** Sensibilità minima GCD vanilla (mouse più basso): sotto non esiste a mano. */
    private static final double GCD_MIN = 0.0005;

    public int onRotation(PlayerData data, float yaw, float pitch, double distXZ) {
        int out = 0;
        // real clients send cumulative yaw past +-180 when spinning the same
        // way (normalize, never flag it). Pitch can't accumulate: out of
        // range twice in a row is injected.
        yaw = (float) ((((yaw + 180) % 360 + 360) % 360) - 180);
        if (pitch < -90 || pitch > 90) {
            data.rotModStreak++;
            if (data.rotModStreak >= 2) {
                data.rotModStreak = 0;
                return 6;
            }
            data.rotLastYaw = yaw;
            data.rotLastPitch = pitch;
            return 0;
        }
        data.rotModStreak = 0;
        if (!data.rotInit) {
            data.rotInit = true;
            data.rotLastYaw = yaw;
            data.rotLastPitch = pitch;
            return 0;
        }
        double yDiff = Check.yawDiff(data.rotLastYaw, yaw);
        double pDiff = Math.abs(data.rotLastPitch - pitch);

        // single-packet snaps only count near attacks: aimbots snap to hit,
        // flicking at the sky is just playing
        if (yDiff > 90 || pDiff > 60) {
            if (System.currentTimeMillis() - data.lastAttackTime < 2000) {
                data.rotSnapStreak++;
            } else {
                data.rotSnapStreak = 0;
            }
            if (data.rotSnapStreak >= 3) {
                data.rotSnapStreak = 0;
                out = Math.max(out, 5);
            }
        } else {
            data.rotSnapStreak = 0;
        }

        // B) lock: identico mentre ci si muove
        if (yDiff < 0.001 && pDiff < 0.001 && distXZ > 0.05) {
            data.rotLockStreak++;
            if (data.rotLockStreak >= 20) {
                data.rotLockStreak = 0;
                out = Math.max(out, 4);
            }
        } else {
            data.rotLockStreak = 0;
        }

        // D2: duplicato da fermo (il lock B copre il moto, qui il fermo).
        // Soglia 5: il client fermo non manda LOOK, ma i ritrasmessi lag
        // possono duplicare — 5 di fila identici non sono rete.
        // Exempt 500ms post-teleport (pacchetti ritrasmessi, stile Grim).
        if (yDiff < 0.001 && pDiff < 0.001 && distXZ <= 0.05) {
            if (System.currentTimeMillis() < data.rotExemptUntil) {
                data.rotDupStreak = 0;
            } else {
                data.rotDupStreak++;
                if (data.rotDupStreak >= 5) {
                    data.rotDupStreak = 0;
                    out = Math.max(out, 4);
                }
            }
        } else {
            data.rotDupStreak = 0;
        }

        // C) GCD disabilitato: costante fissa 0.0005 = FP su mira umana.
        // TODO: recovery sensibilita per-player (mode su 25 campioni come Grim),
        // poi riattivare. Testato il 04/10: con sens alta flaggava flick normali.
        // (blocco commentato, non cancellato)
        // if (yDiff > 0.01 && yDiff < 30) {
        //     double rest = yDiff % GCD_MIN;
        //     if (rest > GCD_MIN * 0.05 && rest < GCD_MIN * 0.95) {
        //         data.rotGcdStreak++;
        //         if (data.rotGcdStreak >= 8) {
        //             data.rotGcdStreak = 0;
        //             data.rotGcdWindows++;
        //             if (data.rotGcdWindows >= 2) {
        //                 data.rotGcdWindows = 0;
        //                 out = Math.max(out, 4);
        //             }
        //         }
        //     } else {
        //         data.rotGcdStreak = 0;
        //     }
        // }

        data.rotLastYaw = yaw;
        data.rotLastPitch = pitch;
        return out;
    }
}
