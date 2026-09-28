package it.anticheat.core;

public enum ClientStatus {
    MISSING,     // nessuna mod client
    UNVERIFIED,  // mod presente ma versione/hash non ok
    VERIFIED     // mod presente e valida
}
