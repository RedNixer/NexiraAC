package it.anticheat.core.checks;

import it.anticheat.core.Check;
import it.anticheat.core.CheckType;
import it.anticheat.core.PlayerData;

/**
 * Interact Passo 3: azioni impossibili sui pacchetti USE_ENTITY.
 * Puro Java, alimentato dal bridge (Paper+PL).
 *
 * C1) Uso+colpo: ATTACK mentre si usa item (mangiare, arco teso, scudo).
 *     Vanilla blocca l'attacco in quello stato: x3 = flag.
 * F1) Self-hit: ATTACK contro sé stessi = client rotto/iniettato, subito.
 * C2) Doppia interazione: due entità diverse entro 50ms = impossibile a mano
 *     (killaura multi-target scriteriata). Streak 3 coppie.
 * F2) Interact-range: USE (non attacco) oltre 4.5 blocchi = reach su
 *     interazioni (stand, item frame, trade da lontano). x3.
 */
public class InteractCheck extends Check {
    @Override public String name() { return "Interact"; }
    @Override public CheckType type() { return CheckType.COMBAT; }
    @Override public String description() { return "Interazioni impossibili (solo Paper+PL)"; }

    /** ATTACK: uso-item + self-hit. */
    public int onAttack(PlayerData data, boolean usingItem, boolean selfHit) {
        if (selfHit) return 6;
        if (usingItem) {
            data.useAttackStreak++;
            if (data.useAttackStreak >= 3) {
                data.useAttackStreak = 0;
                return 4;
            }
        } else {
            data.useAttackStreak = 0;
        }
        return 0;
    }

    /** Qualsiasi USE/ATTACK: doppia entità entro 50ms. */
    public int onInteract(PlayerData data, int entityId) {
        long now = System.currentTimeMillis();
        int out = 0;
        if (data.lastInteractId != entityId && data.lastInteractTime != 0
                && now - data.lastInteractTime < 50) {
            data.multiInteractStreak++;
            if (data.multiInteractStreak >= 3) {
                data.multiInteractStreak = 0;
                out = 5;
            }
        } else if (data.lastInteractId == entityId
                && now - data.lastInteractTime > 2000) {
            // stessa entita dopo pausa lunga: azzera (nuovo fight)
            data.multiInteractStreak = 0;
        }
        data.lastInteractId = entityId;
        data.lastInteractTime = now;
        return out;
    }

    /** USE non-attacco oltre gittata. */
    public int onUseRange(PlayerData data, double dist) {
        if (dist > 4.5) {
            data.useRangeStreak++;
            if (data.useRangeStreak >= 3) {
                data.useRangeStreak = 0;
                return 4;
            }
        } else {
            data.useRangeStreak = 0;
        }
        return 0;
    }
}
