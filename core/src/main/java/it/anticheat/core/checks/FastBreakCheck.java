package it.anticheat.core.checks;

import it.anticheat.core.Check;
import it.anticheat.core.CheckType;
import it.anticheat.core.PlayerData;
import it.anticheat.core.physics.MiningCalc;

/**
 * FastBreak: segnali indipendenti (bucket unico, dettagli diversi).
 * A) Durata danno->rottura: un blocco duro rotto <150ms dopo il primo danno
 *    e impossibile a mano (il nuker spacca istantaneo). Il piu affidabile.
 * B) Intervallo tra rotture: 6 rotture di fila a <90ms (macro/nuker paced).
 * D) Swap-timing (AutoTool): cambio hotbar <100ms prima del danno, x6.
 * M) Mine-timing: blocco duro rotto <200ms dopo primo danno senza Haste, x5.
 * I blocchi insta-break (torce, erba) sono esclusi a monte dagli adapter.
 */
public class FastBreakCheck extends Check {
    @Override public String name() { return "FastBreak"; }
    @Override public CheckType type() { return CheckType.WORLD; }
    @Override public String description() { return "Rottura blocchi troppo veloce (Nuker)"; }

    /** Primo danno al blocco. Chiave "mondo:x:y:z" dall'adapter (null = ignora durata). */
    public void onDamage(PlayerData data, String key) {
        if (key == null) return;
        if (data.breakDamage.size() > 200) data.breakDamage.clear();
        data.breakDamage.putIfAbsent(key, System.currentTimeMillis());
    }

    /** Swap hotbar appena prima di scavare (AutoTool): <100ms e meccanico. */
    public int onSwapDig(PlayerData data, long heldAgoMs) {
        if (heldAgoMs >= 0 && heldAgoMs < 100) {
            data.swapStreak++;
            if (data.swapStreak >= 6) {
                data.swapStreak = 0;
                return 3;
            }
        } else {
            data.swapStreak = 0;
        }
        return 0;
    }

    /**
     * Mine-timing: blocco duro (hardness>=1.5) rotto in fretta senza Haste.
     * Gli adapter passano hasHaste reale (Paper: effetto pozione). Senza Haste
     * neanche eff5 scende sotto ~200ms: sotto e cheat quasi certo.
     */
    public int onMineTiming(PlayerData data, float hardness, long dtMs, boolean hasHaste) {
        if (hasHaste || hardness < 1.5f || dtMs < 0) {
            data.mineStreak = 0;
            return 0;
        }
        if (dtMs < 200) {
            data.mineStreak++;
            if (data.mineStreak >= 5) {
                data.mineStreak = 0;
                return 3;
            }
        } else {
            data.mineStreak = 0;
        }
        return 0;
    }

    /**
     * DPS Fase 4: dt reale danno->rottura vs tempo minimo vanilla per
     * attrezzo/incanti/effetti. Becca SpeedMine blando (~70%) che i check
     * a soglia fissa non vedono: scavare al 70% del tempo minimo per 4
     * blocchi di fila non e umano, e tool maxato.
     */
    public int onDps(PlayerData data, float hardness, long dtMs, String tool,
            int effLvl, int hasteAmp, int fatigueAmp, boolean inWater, boolean onGround) {
        if (dtMs < 0) {
            data.dpsStreak = 0;
            return 0;
        }
        long min = MiningCalc.minTimeMs(hardness, tool, effLvl,
            hasteAmp, fatigueAmp, inWater, onGround);
        if (min < 0) {
            data.dpsStreak = 0;
            return 0; // insta-break: nessun giudizio
        }
        if (dtMs < min) {
            data.dpsStreak++;
            if (data.dpsStreak >= 4) {
                data.dpsStreak = 0;
                return dtMs < min / 2 ? 5 : 3;
            }
        } else {
            data.dpsStreak = 0;
        }
        return 0;
    }

    public int onBreak(PlayerData data, String key) {
        long now = System.currentTimeMillis();
        // A) durata
        if (key != null) {
            Long start = data.breakDamage.remove(key);
            if (start != null) {
                if (now - start < 150) {
                    data.fastBreakDurStreak++;
                    if (data.fastBreakDurStreak >= 3) {
                        data.fastBreakDurStreak = 0;
                        return 5;
                    }
                } else {
                    data.fastBreakDurStreak = 0;
                }
            }
        }
        // C) no-swing: rottura senza swing vicino (PacketMine spacca di nascosto).
        //    Scavando a mano si swinga in continuazione, quindi e sempre fresco.
        //    Deque vuota = nessuno swing mai visto = sospetto come stale.
        Long lastSwing = data.clickTimes.peekLast();
        long sinceSwing = lastSwing == null ? Long.MAX_VALUE : now - lastSwing;
        if (sinceSwing > 1000) {
            data.noSwingStreak++;
            if (data.noSwingStreak >= 3) {
                data.noSwingStreak = 0;
                return 4;
            }
        } else {
            data.noSwingStreak = 0;
        }
        // B) intervallo
        long dt = data.lastBreakTime == 0 ? -1 : now - data.lastBreakTime;
        data.lastBreakTime = now;
        if (dt < 0) return 0;
        if (dt < 90) {
            data.fastBreakStreak++;
            if (data.fastBreakStreak >= 6) {
                data.fastBreakStreak = 0;
                return 4;
            }
        } else {
            data.fastBreakStreak = 0;
        }
        return 0;
    }
}
