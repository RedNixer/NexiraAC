package it.anticheat.core;

/** Canale di comunicazione client <-> server. Deve essere identico in tutti i moduli. */
public final class Channel {
    private Channel() {}
    public static final String HELLO = "anticheat:hello";
    public static final int PROTOCOL_VERSION = 1;
    public static final String CLIENT_MOD_ID = "anticheat-client";
}
