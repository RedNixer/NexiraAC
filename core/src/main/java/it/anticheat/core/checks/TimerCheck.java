package it.anticheat.core.checks;

import it.anticheat.core.Check;
import it.anticheat.core.CheckType;
import it.anticheat.core.PlayerData;

/**
 * Timer: troppi pacchetti movimento al secondo (gioco accelerato).
 * Legittimo max ~20/s (1 per tick). Nota: su Fabric il campionamento
 * (max ~11/s) non puo mai triggerarlo: di fatto solo Paper.
 */
public class TimerCheck extends Check {
    @Override public String name() { return "Timer"; }
    @Override public CheckType type() { return CheckType.MOVEMENT; }
    @Override public String description() { return "Pacchetti movimento oltre 20/s (solo Paper)"; }

    public int onMove(PlayerData data, boolean moved) {
        long now = System.currentTimeMillis();
        if (data.moveWinStart == 0) data.moveWinStart = now;
        if (moved) data.moveWinCount++;
        if (now - data.moveWinStart < 1000) return 0;
        double rate = data.moveWinCount * 1000.0 / (now - data.moveWinStart);
        data.moveWinCount = 0;
        data.moveWinStart = now;
        if (rate > 28) {
            data.timerStreak++;
            if (data.timerStreak >= 2) {
                data.timerStreak = 0;
                return 5;
            }
        } else {
            data.timerStreak = 0;
        }
        return 0;
    }

    /**
     * Versione pacchetti (ProtocolLib): conta i Flying reali, non gli eventi.
     * Finestra e soglie proprie: legit 20/s esatti.
     */
    public int onPacket(PlayerData data) {
        long now = System.currentTimeMillis();
        if (data.pktWinStart == 0) data.pktWinStart = now;
        data.pktWinCount++;
        if (now - data.pktWinStart < 1000) return 0;
        double rate = data.pktWinCount * 1000.0 / (now - data.pktWinStart);
        data.pktWinCount = 0;
        data.pktWinStart = now;
        if (rate > 25) {
            data.pktStreak++;
            if (data.pktStreak >= 2) {
                data.pktStreak = 0;
                return 5;
            }
        } else {
            data.pktStreak = 0;
        }
        return 0;
    }
}
