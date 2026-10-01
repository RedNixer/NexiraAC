package it.anticheat.core.checks;

import it.anticheat.core.Check;
import it.anticheat.core.CheckType;
import it.anticheat.core.PlayerData;

/** Combat packet order: hits without movement/swing, spoofed ground. Bridge feeds, this judges. */
public class PacketOrderCheck extends Check {
    @Override public String name() { return "PacketOrder"; }
    @Override public CheckType type() { return CheckType.COMBAT; }
    @Override public String description() { return "Sequenza pacchetti impossibile (solo Paper+PL)"; }

    /** Flying/movimento ricevuto: apre la finestra per attacchi legittimi. */
    public int onFlying(PlayerData data) {
        data.pktLastFlying = System.currentTimeMillis();
        return 0;
    }

    /** Swing (ARM_ANIMATION) ricevuto. */
    public void onSwing(PlayerData data) {
        data.pktLastSwing = System.currentTimeMillis();
    }

    /** ATTACK via pacchetto: valuta ordine + swing. */
    public int onPacketAttack(PlayerData data) {
        long now = System.currentTimeMillis();
        int out = 0;
        // A) nessun movimento prima del colpo: pacchetto iniettato
        if (data.pktLastFlying == 0 || now - data.pktLastFlying > 1000) {
            data.pktOrderStreak++;
            if (data.pktOrderStreak >= 3) {
                data.pktOrderStreak = 0;
                out = Math.max(out, 5);
            }
        } else {
            data.pktOrderStreak = 0;
        }
        // B) nessuno swing prima del colpo: killaura silenziosa
        if (data.pktLastSwing == 0 || now - data.pktLastSwing > 1000) {
            data.pktNoSwingStreak++;
            if (data.pktNoSwingStreak >= 4) {
                data.pktNoSwingStreak = 0;
                out = Math.max(out, 4);
            }
        } else {
            data.pktNoSwingStreak = 0;
        }
        return out;
    }

    /** Pacchetto GROUND=true con Y che scende: NoFall packet. */
    public int onPacketGround(PlayerData data, boolean packetGround, double dy) {
        if (packetGround && dy < -0.5) {
            data.pktGroundStreak++;
            if (data.pktGroundStreak >= 5) {
                data.pktGroundStreak = 0;
                return 5;
            }
        } else {
            data.pktGroundStreak = Math.max(0, data.pktGroundStreak - 1);
        }
        return 0;
    }
}
