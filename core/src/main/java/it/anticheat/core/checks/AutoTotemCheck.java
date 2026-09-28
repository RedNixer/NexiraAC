package it.anticheat.core.checks;

import it.anticheat.core.Check;
import it.anticheat.core.CheckType;
import it.anticheat.core.PlayerData;

/**
 * AutoTotem (beta): totem che si ripoppano a ritmo sospetto.
 * Soglie molto tolleranti: in PvP con le crystal si poppa spesso anche legittimi.
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
}
