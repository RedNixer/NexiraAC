package it.anticheat.core.checks;

import it.anticheat.core.Check;
import it.anticheat.core.CheckType;
import it.anticheat.core.PlayerData;
import java.util.ArrayList;
import java.util.List;

/**
 * AutoClicker basato su CPS + regolarita, valutato SOLO in contesto combat
 * (colpo subito/inferto negli ultimi 4s). Fuori dal combat non flagga mai:
 * scavare, costruire e drag-bridging restano sicuri.
 *
 * Regole (a cascata):
 * 1) CPS >= limite per 2 finestre da ~1s -> macro palese (+4 VL).
 *    Vale OVUNQUE (anche a vuoto): nessun umano supera il limite in modo
 *    sostenuto scavando o cliccando (tenere premuto fa ~4-5 CPS).
 * 2) CPS >= 12 con click molto regolari, solo in combat -> macro con jitter (+3 VL)
 * 3) Click quasi perfetti (std bassissima) a ritmo medio, solo in combat (+3 VL)
 * La sensibilita GUI sposta le soglie di regolarita, /ac cps sposta il limite.
 */
public class AutoClickerCheck extends Check {
    @Override public String name() { return "AutoClicker"; }
    @Override public CheckType type() { return CheckType.COMBAT; }
    @Override public String description() { return "CPS e regolarita da macro (solo combat)"; }

    public int onClick(PlayerData data, double maxStd, int cpsLimit, boolean inCombat) {
        long now = System.currentTimeMillis();
        data.clickTimes.addLast(now);
        while (data.clickTimes.size() > 60) data.clickTimes.pollFirst();

        double fastStd = maxStd + 2.0;
        double perfectStd = Math.max(2.0, maxStd - 1.5);

        // CPS nell'ultimo secondo
        int n1s = 0;
        for (long t : data.clickTimes) {
            if (now - t <= 1000) n1s++;
        }

        // 1) CPS oltre il limite sostenuto (streak su ~2 finestre, non su 2 click).
        //    Senza gate combat: a questi ritmi nessun input umano e legittimo.
        //    Isteresi: sotto il limite ma vicino (limite-5) tiene lo streak,
        //    cosi una macro che oscilla intorno alla soglia flagga comunque.
        if (n1s >= 12 && n1s >= cpsLimit) {
            if (now - data.lastCpsFlag > 800) {
                data.clickCpsStreak++;
                data.lastCpsFlag = now;
            }
            if (data.clickCpsStreak >= 2) {
                data.clickCpsStreak = 0;
                return 4;
            }
            return 0;
        }
        if (n1s < cpsLimit - 5) data.clickCpsStreak = 0;

        if (data.clickTimes.size() < 15) return 0;
        List<Long> times = new ArrayList<>(data.clickTimes);
        int from = Math.max(0, times.size() - 20);
        List<Long> gaps = new ArrayList<>();
        for (int i = from + 1; i < times.size(); i++) gaps.add(times.get(i) - times.get(i - 1));
        if (gaps.size() < 10) return 0;
        double sum = 0;
        for (long g : gaps) sum += g;
        double mean = sum / gaps.size();
        double var = 0;
        for (long g : gaps) var += (g - mean) * (g - mean);
        double std = Math.sqrt(var / gaps.size());

        // 3a) macro perfetta OVUNQUE (es. metronomo 6.6 CPS di Meteor): 20+ click
        //     quasi identici a ritmo medio. Scavare ha mean ~250 (escluso),
        //     i click umani hanno std alta (esclusi).
        if (times.size() >= 20 && mean > 80 && mean < 200 && std < 2.0) return 3;

        // 2) e 3) solo in combat: fuori non si giudica la regolarita
        // (scavare/costruire sono ritmici anche legittimi).
        if (!inCombat) return 0;

        // 2) veloce + regolare
        if (mean > 40 && mean < 400 && n1s >= 12 && std < fastStd) return 3;
        // 3) quasi perfetto a ritmo medio (macro lenta senza jitter)
        if (mean > 40 && mean < 200 && std < perfectStd) return 3;
        return 0;
    }
}
