package it.anticheat.core.checks;

import it.anticheat.core.Check;
import it.anticheat.core.CheckType;
import it.anticheat.core.PlayerData;

/**
 * AutoTotem: tre segnali indipendenti.
 * A) Frequenza: 4 pop in 12s (crystal pvp estremo, tollerante).
 * B) Refill: mano vuota al pop + totem in mano 300ms dopo, x3.
 *    A mano e impossibile: apri inventario, prendi totem, chiudi,
 *    sposta in mano in 300ms mentre esplodi non si fa.
 * C) Swap pre-pop (macro che clicca prima): nel core via lastInvClick.
 */
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
