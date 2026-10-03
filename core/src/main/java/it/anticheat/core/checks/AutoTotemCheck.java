package it.anticheat.core.checks;

import it.anticheat.core.Check;
import it.anticheat.core.CheckType;
import it.anticheat.core.PlayerData;

/** Pop rate, 300ms post-pop refill, pre-pop inventory swap. */
public class AutoTotemCheck extends Check {
    @Override public String name() { return "AutoTotem"; }
    @Override public CheckType type() { return CheckType.COMBAT; }
    @Override public String description() { return "Totem istantanei sospetti (beta)"; }

    public int onPop(PlayerData data) {
        long now = System.currentTimeMillis();
        data.totemPops.addLast(now);
        while (data.totemPops.size() > 6) data.totemPops.pollFirst();
        if (data.totemPops.size() < 4) return 0;
        // 4 totem in meno di 12 secondi = ritmo quasi impossibile a mano
        Long first = data.totemPops.peekFirst();
        if (first != null && now - first < 12_000) {
            data.totemPops.clear();
            return 3;
        }
        return 0;
    }

    /**
     * B) Refill dopo il pop: se la mano era vuota al pop e 300ms dopo c'e
     * un totem, solo una macro lo fa in tempo. x3 = flag.
     */
    public int onRefill(PlayerData data, boolean hadTotemAtPop, boolean hasTotemNow) {
        if (!hadTotemAtPop && hasTotemNow) {
            data.totemRefillStreak++;
            if (data.totemRefillStreak >= 3) {
                data.totemRefillStreak = 0;
                return 5;
            }
        } else {
            data.totemRefillStreak = 0;
        }
        return 0;
    }
}
