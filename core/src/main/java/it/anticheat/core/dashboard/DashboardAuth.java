package it.anticheat.core.dashboard;

import java.security.SecureRandom;

/**
 * Auth dashboard a token. Il token nasce random al primo avvio e vive su
 * disco (file dashboard.token); rigenerabile da comando. Confronto a
 * tempo costante, niente user/password da indovinare.
 */
public final class DashboardAuth {
    private volatile String token = "";
    private static final SecureRandom RND = new SecureRandom();

    public String get() {
        return token;
    }

    public void set(String t) {
        token = t == null ? "" : t;
    }

    /** Nuovo token 64 hex char. */
    public String regenerate() {
        byte[] b = new byte[32];
        RND.nextBytes(b);
        StringBuilder sb = new StringBuilder(64);
        for (byte x : b) sb.append(String.format("%02x", x));
        token = sb.toString();
        return token;
    }

    /** true se Authorization: Bearer <token> o ?token= corrispondono. */
    public boolean check(String authHeader, String queryToken) {
        String mine = token;
        if (mine == null || mine.isEmpty()) return false;
        String got = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            got = authHeader.substring(7).trim();
        } else if (queryToken != null && !queryToken.isEmpty()) {
            got = queryToken;
        }
        if (got == null) return false;
        if (got.length() != mine.length()) return false;
        int d = 0;
        for (int i = 0; i < got.length(); i++) d |= got.charAt(i) ^ mine.charAt(i);
        return d == 0;
    }
}
