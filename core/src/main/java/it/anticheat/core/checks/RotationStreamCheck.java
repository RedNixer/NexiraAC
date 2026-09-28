package it.anticheat.core.checks;

import it.anticheat.core.Check;
import it.anticheat.core.CheckType;
import it.anticheat.core.PlayerData;

/**
 * RotationStream Fase 2/P3: rotazioni RAW dai pacchetti LOOK, non dagli eventi.
 * Gli eventi Bukkit arrivano campionati e arrotondati; il flusso pacchetti
 * mostra quello che il client manda davvero.
 *
 * A) Snap raw: salto yaw >90 gradi (o pitch >60) in UN singolo pacchetto.
 *    A mano la rotazione e distribuita su più pacchetti; uno snap istantaneo
 *    e silent-rotation che scatta solo al momento del colpo.
 * B) Lock raw: yaw/pitch bit-identici per 20+ pacchetti di fila mentre la
 *    posizione cambia (aim che segue da solo a livello pacchetto).
 * C) GCD zero: delta yaw con resto GCD nullo su scala sensibilità (i movimenti
 *    umani passano sempre per la griglia GCD del mouse; un delta fuori griglia
 *    ripetuto = rotazione iniettata, non mouse reale). Soglia: 8/12 fuori.
 * D1) Modulo-360 (Passo 2): yaw fuori [-180,180] o pitch fuori [-90,90]
 *    anche una volta sola = pacchetto iniettato. Il client vanilla fa clamp
 *    sempre: fuori range e impossibile a mano, zero FP possibili.
 * D2) Duplicate-LOOK (Passo 2): stesso yaw/pitch 5 pacchetti di fila DA FERMO
 *    = pacchetto finto per coprire silent-aim (il lock B copre già il moto).
 */
public class RotationStreamCheck extends Check {
    @Override public String name() { return "RotationStream"; }
    @Override public CheckType type() { return CheckType.COMBAT; }
    @Override public String description() { return "Rotazioni raw impossibili (solo Paper+PL)"; }

    /** Sensibilità minima GCD vanilla (mouse più basso): sotto non esiste a mano. */
    private static final double GCD_MIN = 0.0005;

    public int onRotation(PlayerData data, float yaw, float pitch, double distXZ) {
        int out = 0;
        // D1: fuori range protocollo = iniettato, subito (zero FP)
        if (yaw < -180 || yaw > 180 || pitch < -90 || pitch > 90) {
            return 6;
        }
        if (!data.rotInit) {
            data.rotInit = true;
            data.rotLastYaw = yaw;
            data.rotLastPitch = pitch;
            return 0;
        }
        double yDiff = Check.yawDiff(data.rotLastYaw, yaw);
        double pDiff = Math.abs(data.rotLastPitch - pitch);

        // A) snap istantaneo in un pacchetto
        if (yDiff > 90 || pDiff > 60) {
            data.rotSnapStreak++;
            if (data.rotSnapStreak >= 2) {
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
        if (yDiff < 0.001 && pDiff < 0.001 && distXZ <= 0.05) {
            data.rotDupStreak++;
            if (data.rotDupStreak >= 5) {
                data.rotDupStreak = 0;
                out = Math.max(out, 4);
            }
        } else {
            data.rotDupStreak = 0;
        }

        // C) GCD: delta fuori griglia mouse ripetuti (solo se si muove la mira)
        if (yDiff > 0.01 && yDiff < 30) {
            double rest = yDiff % GCD_MIN;
            // resto né ~0 né ~GCD_MIN = fuori griglia
            if (rest > GCD_MIN * 0.05 && rest < GCD_MIN * 0.95) {
                data.rotGcdStreak++;
                if (data.rotGcdStreak >= 8) {
                    data.rotGcdStreak = 0;
                    // conta finestre: 2 finestre = pattern stabile, non rumore
                    data.rotGcdWindows++;
                    if (data.rotGcdWindows >= 2) {
                        data.rotGcdWindows = 0;
                        out = Math.max(out, 4);
                    }
                }
            } else {
                data.rotGcdStreak = 0;
            }
        }

        data.rotLastYaw = yaw;
        data.rotLastPitch = pitch;
        return out;
    }
}
