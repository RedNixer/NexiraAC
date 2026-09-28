package it.anticheat.core.model;

import java.util.UUID;

public class Violation {
    public final long time = System.currentTimeMillis();
    public final UUID player;
    public final String playerName;
    public final String check;
    public final int vlAdded;
    public final int totalVl;
    public final String details;

    public Violation(UUID player, String playerName, String check, int vlAdded, int totalVl, String details) {
        this.player = player;
        this.playerName = playerName;
        this.check = check;
        this.vlAdded = vlAdded;
        this.totalVl = totalVl;
        this.details = details;
    }
}
