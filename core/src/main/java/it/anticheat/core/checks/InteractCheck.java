package it.anticheat.core.checks;

import it.anticheat.core.Check;
import it.anticheat.core.CheckType;
import it.anticheat.core.PlayerData;

/** Impossible USE_ENTITY sequences: mid-use hits, self-hits, multi-entity, over-range use. */
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
